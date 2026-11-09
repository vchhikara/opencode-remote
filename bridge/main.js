const { WebSocketServer, WebSocket } = require('ws');
const { Bonjour } = require('bonjour-service');
const { exec, spawn } = require('child_process');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const os = require('os');
const QRCode = require('qrcode');
const { listAllSessions } = require('./sessionStore');

const PORT = parseInt(process.argv[2] || process.env.PORT || '8080', 10);
const BIND_HOST = process.env.BRIDGE_HOST || '0.0.0.0';
const MAX_WS_PAYLOAD = 16 * 1024 * 1024;
const MAX_CLIENTS = 64;
const MAX_BUFFERED_BYTES = 8 * 1024 * 1024;
const AUTH_TIMEOUT_MS = 10000;
const AUTH_FAILURE_LIMIT = 10;
const AUTH_FAILURE_WINDOW_MS = 60_000;
const MAX_AUTH_FAILURE_ENTRIES = 2048;
const MAX_FILE_CONTENT_BYTES = 8 * 1024 * 1024;
const MAX_TREE_NODES = 10000;
const MAX_TREE_DEPTH = 24;
const OPCODE = process.env.OPCODE || 'opencode';
const AUTH_TOKEN = process.env.BRIDGE_TOKEN || crypto.randomBytes(32).toString('hex');
// Pairing credentials are usable only during startup's short window.
// Additional devices require a bridge restart or an explicit multi-device cap.
const PAIRING_WINDOW_MS = Math.min(300000, Math.max(0, Number(process.env.PAIRING_WINDOW_MS ?? 120000)));
const PAIRING_MAX_DEVICES = Math.min(10, Math.max(0, Number(process.env.PAIRING_MAX_DEVICES ?? 1)));
const pairingDeadline = Date.now() + PAIRING_WINDOW_MS;
let pairingsRemaining = PAIRING_MAX_DEVICES;
console.log(`  Auth token: ${AUTH_TOKEN}`);

// --- Per-device tokens (Task 8.1) ---
// AUTH_TOKEN above is now only a one-time *pairing* secret (what the QR code
// carries), not something every future connection is checked against
// forever. The first CONNECT that presents it gets issued a distinct
// per-device token, persisted here; every later CONNECT authenticates with
// that device's own token instead. This is what makes per-device revocation
// (Task 8.2) meaningful — revoking one device's token doesn't affect any
// other paired device or require re-pairing everyone.
const DEVICE_STORE_PATH = process.env.DEVICE_STORE_PATH || path.join(os.homedir(), '.opencode-remote-devices.json');
let deviceTokens = loadDeviceTokens();

// A SHA-256 verifier is stored instead of a reusable bearer token. Existing
// plaintext-key stores are migrated in place without invalidating devices.
function deviceTokenKey(token) {
  return 'sha256:' + crypto.createHash('sha256').update(token).digest('hex');
}

function validDeviceRecord(token, record) {
  return typeof token === 'string' && /^(?:sha256:)?[a-f0-9]{64}$/.test(token)
    && record !== null && typeof record === 'object' && !Array.isArray(record)
    && typeof record.deviceId === 'string'
    && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(record.deviceId)
    && typeof record.deviceName === 'string'
    && typeof record.issuedAt === 'number' && Number.isSafeInteger(record.issuedAt)
    && record.issuedAt > 0;
}

function loadDeviceTokens() {
  const tokens = Object.create(null);
  try {
    const stored = JSON.parse(fs.readFileSync(DEVICE_STORE_PATH, 'utf-8'));
    let migrated = false;
    if (stored === null || typeof stored !== 'object' || Array.isArray(stored)) {
      throw new Error('device store must be an object');
    }
    for (const [token, record] of Object.entries(stored)) {
      if (validDeviceRecord(token, record)) {
        if (token.startsWith('sha256:')) tokens[token] = record;
        else {
          tokens[deviceTokenKey(token)] = record;
          migrated = true;
        }
      }
    }
    // Repair existing modes and atomically upgrade legacy plaintext token keys.
    fs.chmodSync(DEVICE_STORE_PATH, 0o600);
    if (migrated) persistDeviceTokens(tokens);
  } catch (e) {
    if (e.code !== 'ENOENT') {
      // Fail closed on corrupt stores or permission errors, rather than issuing
      // new credentials against an inconsistent persisted authorization state.
      throw new Error(`Cannot safely load device store: ${e.message}`);
    }
  }
  return tokens;
}

function persistDeviceTokens(tokens) {
  const tmp = `${DEVICE_STORE_PATH}.${process.pid}.tmp`;
  try {
    fs.writeFileSync(tmp, JSON.stringify(tokens, null, 2), { mode: 0o600, flag: 'wx' });
    fs.chmodSync(tmp, 0o600);
    fs.renameSync(tmp, DEVICE_STORE_PATH);
  } finally {
    if (fs.existsSync(tmp)) fs.unlinkSync(tmp);
  }
}
function saveDeviceTokens() { persistDeviceTokens(deviceTokens); }

function issueDeviceToken(deviceName) {
  const token = crypto.randomBytes(32).toString('hex');
  const deviceId = crypto.randomUUID();
  deviceTokens[deviceTokenKey(token)] = { deviceId, deviceName, issuedAt: Date.now() };
  saveDeviceTokens();
  return { token, deviceId };
}

function timingSafeTokenEquals(a, b) {
  const bufA = Buffer.from(String(a || ''));
  const bufB = Buffer.from(String(b || ''));
  return bufA.length === bufB.length && bufA.length > 0 && crypto.timingSafeEqual(bufA, bufB);
}

// Rate-limit rejected authentication attempts by the socket's actual peer IP.
// Ignore X-Forwarded-For because a client can forge it unless a trusted proxy is configured.
const authFailures = new Map();
function rejectAuthFrom(ip) {
  const now = Date.now();
  const previous = authFailures.get(ip);
  const count = previous && now - previous.started < AUTH_FAILURE_WINDOW_MS ? previous.count + 1 : 1;
  authFailures.delete(ip);
  authFailures.set(ip, { count, started: count === 1 ? now : previous.started });
  if (authFailures.size > MAX_AUTH_FAILURE_ENTRIES) authFailures.delete(authFailures.keys().next().value);
  return count >= AUTH_FAILURE_LIMIT;
}
function isAuthBlocked(ip) {
  const state = authFailures.get(ip);
  if (!state) return false;
  if (Date.now() - state.started >= AUTH_FAILURE_WINDOW_MS) {
    authFailures.delete(ip);
    return false;
  }
  return state.count >= AUTH_FAILURE_LIMIT;
}

// Best-effort short disconnect catch-up for previously paired devices.
// Not an acknowledgement-backed durable push service.
const notificationHistory = [];
const deliveredNotificationSeq = new Map();
let notificationSeq = 0;
const MAX_NOTIFICATION_HISTORY = 100;
function replayNotifications(ws) {
  const last = deliveredNotificationSeq.get(ws.deviceId) || 0;
  for (const payload of notificationHistory) {
    if (payload.seq <= last) continue;
    if (ws.readyState !== WebSocket.OPEN || ws.bufferedAmount > MAX_BUFFERED_BYTES) return;
    ws.send(JSON.stringify({ eventType: 'NOTIFY', payload }));
    deliveredNotificationSeq.set(ws.deviceId, payload.seq);
  }
}

const clients = new Set();
// One controlling device at a time. Multiple devices can observe read-only state,
// but cannot concurrently redirect the shared OpenCode server workspace/session.
const MUTATING_ACTIONS = new Set([
  'OPEN_WORKSPACE', 'ADD_WORKSPACE', 'OPEN_SESSION_GLOBAL', 'SWITCH_SESSION',
  'NEW_SESSION', 'FORK_SESSION', 'PROMPT', 'TERMINAL', 'TERMINAL_RESIZE',
  'PERMISSION_REPLY', 'QUESTION_REPLY', 'QUESTION_REJECT', 'GIT', 'KILL_TASK',
  'ACCEPT_DIFF', 'REJECT_DIFF', 'REVOKE_TOKEN', 'ROTATE_TOKEN'
]);
const CONTEXT_SWITCH_ACTIONS = new Set([
  'OPEN_WORKSPACE', 'OPEN_SESSION_GLOBAL', 'SWITCH_SESSION',
  'NEW_SESSION', 'FORK_SESSION', 'PROMPT'
]);
let controllerDeviceId = null;
function releaseControllerIfIdle() {
  if (Object.keys(tasks).length > 0) return;
  if (![...clients].some(client => client.authenticated && client.readyState === WebSocket.OPEN
      && client.deviceId === controllerDeviceId)) controllerDeviceId = null;
}
let sessions = {};
let activeWorkspace = process.cwd();
let pendingDiffs = {};
let tasks = {};
// Pending interactive requests observed on the active OpenCode SSE feed.
// Replayed to reconnecting clients; these maps are not a durable event log.
const pendingPermissions = new Map();
const pendingQuestions = new Map();
const WORKSPACE_ROOT = process.env.WORKSPACE_ROOT || process.cwd();

// --- Audit log (Task 8.4.1) ---
// Append-only JSON-lines log of every approved command/edit: permission
// decisions (Phase 2), git commands executed, terminal commands executed.
// Path is env-configurable (test isolation), following the DEVICE_STORE_PATH
// pattern; defaults to a file under WORKSPACE_ROOT.
const AUDIT_LOG_PATH = process.env.AUDIT_LOG_PATH || path.join(WORKSPACE_ROOT, '.opencode-remote-audit.log');

function appendAuditLog(kind, detail, ws) {
  const entry = {
    timestamp: new Date().toISOString(),
    kind,
    detail,
    deviceId: (ws && ws.deviceId) || null,
    deviceName: (ws && ws.deviceName) || null
  };
  try {
    fs.appendFileSync(AUDIT_LOG_PATH, JSON.stringify(entry) + '\n', { mode: 0o600 });
    fs.chmodSync(AUDIT_LOG_PATH, 0o600);
  } catch (e) {
    console.log(`  ! failed to append audit log: ${e.message}`);
  }
}

// --- Multi-workspace tracking (Task 6.1) ---
// Decision (6.1.2), grounded in the Phase 0 0.2.7 finding that `/project`
// exists but opencode serve binds to one project directory for its whole
// process lifetime (confirmed by this bridge's own pre-existing behavior:
// startOpenCodeServer() must be restarted, not reconfigured, to move to a
// new directory — see OPEN_WORKSPACE below): this bridge uses "one
// opencode serve + one persisted session per workspace" rather than trying
// to scope multiple concurrent workspaces through a single server's
// `/project` API. `activeWorkspace` stays the single source of truth for
// path resolution (resolveInsideWorkspace, pushFileTree, etc. untouched);
// `workspaces` is purely additional bookkeeping of every directory the
// user has opened or added, for the workspace list UI.
function makeWorkspaceEntry(p) {
  return { id: p, name: path.basename(p), path: p };
}
let workspaces = [makeWorkspaceEntry(activeWorkspace)];

// Shared by OPEN_WORKSPACE, ADD_WORKSPACE, and OPEN_SESSION_GLOBAL — each
// previously carried (or, for OPEN_SESSION_GLOBAL, would have carried) its
// own inline copy of "resolve this path, confirm it's inside WORKSPACE_ROOT,
// confirm it's an existing directory". Returns { resolved } on success or
// { error } (a user-facing message, not a thrown exception) on failure.
function resolveWithinWorkspaceRoot(dir) {
  const lexical = path.resolve(dir);
  const rootResolved = fs.realpathSync(WORKSPACE_ROOT);
  if (!fs.existsSync(lexical)) return { error: `Workspace not found: ${dir}` };
  const resolved = fs.realpathSync(lexical);
  const inRoot = resolved === rootResolved || resolved.startsWith(rootResolved + path.sep);
  if (!inRoot) return { error: `Workspace outside allowed root: ${dir}` };
  if (!fs.statSync(resolved).isDirectory()) return { error: `Workspace not found: ${dir}` };
  return { resolved };
}

// --- OpenCode server (persistent, one session reused across prompts) ---
// Previously every PROMPT spawned a fresh `opencode run` subprocess with no
// session id, so nothing survived between prompts and a hung/unreachable model
// provider meant the process just sat there forever with no error surfaced.
// Now a single `opencode serve` process stays up for the bridge's lifetime and
// prompts are sent into one reused session via its HTTP API.
const OC_SERVE_PORT = parseInt(process.env.OC_SERVE_PORT || '4096', 10);
const OC_PROMPT_TIMEOUT_MS = parseInt(process.env.OC_PROMPT_TIMEOUT_MS || '120000', 10);
const ocBaseUrl = `http://127.0.0.1:${OC_SERVE_PORT}`;
let ocServeProc = null;
let ocReadyPromise = null;
let ocSessionId = null;

// Session-id persistence across bridge restarts (Task 3.2). Stored as a small
// JSON file inside the workspace, keyed by nothing else — one bridge process
// serves one workspace at a time, so one file is enough. A bridge crash/
// restart then resumes the same opencode session instead of silently
// starting a fresh one.
function sessionStateFilePath() {
  return path.join(activeWorkspace, '.opencode-remote-session.json');
}

function persistSessionId() {
  try {
    fs.writeFileSync(sessionStateFilePath(), JSON.stringify({ sessionId: ocSessionId }));
  } catch (e) {
    console.log(`  ! failed to persist session id: ${e.message}`);
  }
}

function loadPersistedSessionId() {
  try {
    const raw = fs.readFileSync(sessionStateFilePath(), 'utf-8');
    const data = JSON.parse(raw);
    return data.sessionId || null;
  } catch {
    return null;
  }
}

function resolveInsideWorkspace(p) {
  const abs = path.resolve(activeWorkspace, p);
  const root = fs.realpathSync(activeWorkspace);
  // Check the requested path and its canonical destination. A path under
  // the workspace may be a symlink pointing outside the allowed tree.
  if (abs !== root && !abs.startsWith(root + path.sep)) {
    throw new Error('path escapes workspace');
  }
  const real = fs.realpathSync(abs);
  if (real !== root && !real.startsWith(root + path.sep)) {
    throw new Error('symlink escapes workspace');
  }
  return real;
}

function tokenizeGitArgs(s) {
  const args = [];
  const re = /"([^"]*)"|'([^']*)'|(\S+)/g;
  let m;
  while ((m = re.exec(s)) !== null) {
    args.push(m[1] !== undefined ? m[1] : m[2] !== undefined ? m[2] : m[3]);
  }
  return args;
}

// --- State helpers ---
function broadcast(eventType, payload) {
  const msg = JSON.stringify({ eventType, payload });
  for (const ws of clients) {
    if (ws.readyState !== 1 || !ws.authenticated) continue;
    if (ws.bufferedAmount > MAX_BUFFERED_BYTES) {
      ws.close(1013, 'slow consumer');
      continue;
    }
    ws.send(msg);
  }
}

function pushGitStatus(ws) {
  const cwd = activeWorkspace;
  exec('git status --porcelain -b', { cwd }, (err, stdout) => {
    if (err) return;
    const lines = stdout.trim().split('\n');
    const branchLine = lines.find(l => l.startsWith('##'));
    const branch = branchLine ? branchLine.replace('## ', '').split('...')[0] : 'unknown';
    const modified = [], added = [], deleted = [];
    for (const l of lines) {
      if (l.startsWith('##')) continue;
      const s = l.substring(0, 2).trim();
      const f = l.substring(3);
      if (s === 'M') modified.push(f);
      else if (s === 'A') added.push(f);
      else if (s === 'D') deleted.push(f);
      else if (s === '??') added.push(f);
    }
    const aheadMatch = branchLine ? branchLine.match(/ahead (\d+)/) : null;
    const behindMatch = branchLine ? branchLine.match(/behind (\d+)/) : null;
    const canPush = !!(aheadMatch && parseInt(aheadMatch[1], 10) > 0);
    const canPull = !!(behindMatch && parseInt(behindMatch[1], 10) > 0);
    const data = { branch, modifiedFiles: modified, addedFiles: added, deletedFiles: deleted, canPush, canPull };
    if (ws) ws.send(JSON.stringify({ eventType: 'GIT_STATUS', payload: data }));
    else broadcast('GIT_STATUS', data);
  });
}

function pushAgentState(state) {
  broadcast('AGENT_STATE', state);
}

function pushChatMessage(msg, isUser, actionDesc, details) {
  broadcast('CHAT_MESSAGE', { id: Date.now().toString(), text: msg, isUser, actionDescription: actionDesc || '', hasDetails: !!details });
}

// --- Push notification hook (Task 7.1.1) ---
// No FCM project/credentials exist in this environment to send a real push,
// so this hook's body is a placeholder — it broadcasts a NOTIFY frame over
// the same WS connected clients already use (covers the foregrounded-client
// case today) and is the one seam a real FCM send would plug into later
// (swap the body for an actual FCM API call; every call site below stays
// unchanged). Manual on-device background-push delivery is therefore NOT
// claimed Done here — see PROGRESS.md.
function notifyExternal(kind, detail) {
  // Notification details may include provider errors, prompts or local paths.
  // Do not disclose them in plaintext bridge process logs.
  console.log(`  [notify] ${kind}`);
  const payload = { kind, detail, seq: ++notificationSeq };
  notificationHistory.push(payload);
  if (notificationHistory.length > MAX_NOTIFICATION_HISTORY) notificationHistory.shift();
  broadcast('NOTIFY', payload);
  for (const ws of clients) {
    if (ws.authenticated && ws.readyState === WebSocket.OPEN
        && ws.bufferedAmount <= MAX_BUFFERED_BYTES) {
      deliveredNotificationSeq.set(ws.deviceId, notificationSeq);
    }
  }
}

function pushFileDiff(fileName, diffText) {
  pendingDiffs[fileName] = diffText;
  broadcast('FILE_DIFF', { fileName, diffText });
}

function pushTerminalLine(line) {
  broadcast('TERMINAL_OUTPUT', line);
}

// --- PTY-backed terminal (Task 4.1) ---
// [VERIFY LIVE] confirmed via a live probe against opencode serve v1.18.26:
// POST /pty {command, args} creates a PTY and returns {id, ...}; the actual
// I/O transport is a WebSocket at GET /pty/{id}/connect (not SSE/polling) —
// plain-text frames are terminal output, and frames prefixed with a NUL byte
// carry out-of-band JSON control data (e.g. {"cursor":N}) rather than output,
// so those are dropped rather than displayed. Resize is `PUT /pty/{id}` with
// body `{size:{rows,cols}}` (confirmed in the OpenAPI schema).
// activePtyId tracks the most recently started terminal command's PTY so a
// TERMINAL_RESIZE with no explicit ptyId (the common case — this app only
// ever has one terminal open at a time) still resizes the right one.
let activePtyId = null;

async function runTerminalCommand(command) {
  const shellBin = process.platform === 'win32' ? 'cmd' : '/bin/sh';
  const shellFlag = process.platform === 'win32' ? '/c' : '-c';
  if (!ocServeProc) startOpenCodeServer();
  await ocReadyPromise;
  const res = await ocFetch('/pty', {
    method: 'POST',
    body: JSON.stringify({ command: shellBin, args: [shellFlag, command] })
  });
  if (!res.ok) throw new Error(`PTY create failed: HTTP ${res.status}`);
  const data = await res.json();
  const ptyId = data.id;
  activePtyId = ptyId;
  const ptyWs = new WebSocket(`ws://127.0.0.1:${OC_SERVE_PORT}/pty/${ptyId}/connect`);
  ptyWs.on('message', (chunk) => {
    const text = chunk.toString();
    if (text.startsWith(' ')) return; // out-of-band control frame, not output
    pushTerminalLine(text);
  });
  ptyWs.on('error', (err) => pushTerminalLine(`Error: ${err.message}`));
  ptyWs.on('close', () => { if (activePtyId === ptyId) activePtyId = null; });
}

function pushTaskUpdated(task) {
  broadcast('TASK_UPDATED', { id: task.id, name: task.name, port: task.port || null, status: task.status });
}

function pushTaskRemoved(id) {
  broadcast('TASK_REMOVED', id);
}

function runtimeSnapshot() {
  return {
    workspace: activeWorkspace,
    sessionId: ocSessionId,
    tasks: Object.values(tasks).map(t => ({ id: t.id, name: t.name, port: t.port || null, status: t.status })),
    diffs: Object.entries(pendingDiffs).map(([fileName, diffText]) => ({ fileName, diffText })),
    permissions: Array.from(pendingPermissions.values()),
    questions: Array.from(pendingQuestions.values()),
    terminalPtyId: activePtyId
  };
}

// --- OpenCode server lifecycle ---

// Starts (or restarts) the persistent `opencode serve` process bound to
// activeWorkspace's directory. Called at bridge startup and whenever the
// workspace changes (the server's project directory is fixed to its own cwd
// at spawn time, so a workspace switch means a fresh process — and thus a
// fresh session, same as switching directories used to do before).
function startOpenCodeServer() {
  if (ocServeProc) { ocServeProc.kill(); ocServeProc = null; }
  // Resume the last session used in this workspace (Task 3.2) instead of
  // always starting null — ensureSession() will fall back to creating a new
  // one if this id no longer exists server-side.
  ocSessionId = loadPersistedSessionId();
  // SSE requests are scoped to a particular OpenCode server/workspace instance.
  pendingPermissions.clear();
  pendingQuestions.clear();

  const proc = spawn(OPCODE, ['serve', '--port', String(OC_SERVE_PORT), '--hostname', '127.0.0.1'], {
    cwd: activeWorkspace,
    env: process.env
  });
  proc.stderr.on('data', d => console.log(`  [opencode serve] ${d.toString().trim()}`));
  proc.on('error', e => console.log(`  ! failed to start opencode serve: ${e.message}`));
  proc.on('exit', code => {
    if (proc === ocServeProc) {
      console.log(`  ! opencode serve exited unexpectedly (code ${code})`);
      ocServeProc = null;
      ocReadyPromise = null;
    }
  });
  ocServeProc = proc;

  ocReadyPromise = (async () => {
    for (let i = 0; i < 60; i++) {
      try {
        const res = await fetch(`${ocBaseUrl}/config`, { signal: AbortSignal.timeout(1000) });
        if (res.ok) return true;
      } catch {}
      await new Promise(r => setTimeout(r, 500));
    }
    console.log('  ! opencode serve did not become ready within 30s');
    return false;
  })();

  return ocReadyPromise;
}

async function ocFetch(pathname, opts = {}) {
  const timeoutMs = opts.timeoutMs || 15000;
  const res = await fetch(`${ocBaseUrl}${pathname}`, {
    method: opts.method || 'GET',
    headers: { 'Content-Type': 'application/json' },
    body: opts.body,
    signal: AbortSignal.timeout(timeoutMs)
  });
  return res;
}

// --- Live streaming: relay opencode serve's /event SSE feed as WS frames ---
// [VERIFY LIVE, confirmed in PROGRESS.md Phase 0] /event streams
// text/event-stream with `message.part.delta` carrying incremental text and
// `message.part.updated` carrying tool-part state transitions (part.type ===
// 'tool', part.state.status in pending/running/completed/error).
let ocEventStreamStarted = false;

// Parses one SSE event ("data: {...}" line, possibly among other lines in the
// same event block) and relays it as a WS frame via `emit`. Split out from the
// stream-reading loop so tests can feed it canned SSE text directly instead of
// standing up a real HTTP server.
function relaySseEventBlock(rawBlock, emit) {
  for (const line of rawBlock.split('\n')) {
    if (!line.startsWith('data:')) continue;
    const jsonStr = line.slice(5).trim();
    if (!jsonStr) continue;
    let evt;
    try { evt = JSON.parse(jsonStr); } catch { continue; }
    console.log(`  [sse] ${evt.type}`);
    relaySseEvent(evt, emit);
  }
}

function relaySseEvent(evt, emit) {
  const props = evt.properties || {};
  if (evt.type === 'message.part.delta') {
    const part = props.part || {};
    if (part.type === 'text' && typeof part.text === 'string') {
      emit('STREAM_TEXT_DELTA', { sessionId: props.sessionID || part.sessionID, text: part.text });
    }
    return;
  }
  if (evt.type === 'message.part.updated') {
    const part = props.part || {};
    if (part.type === 'tool') {
      const state = part.state || {};
      const sessionId = props.sessionID || part.sessionID;
      if (state.status === 'running' || state.status === 'pending') {
        emit('STREAM_TOOL_CALL', { sessionId, tool: part.tool, input: state.input });
      } else if (state.status === 'completed' || state.status === 'error') {
        emit('STREAM_TOOL_RESULT', { sessionId, tool: part.tool, output: state.output || state.error });
        // [VERIFY LIVE, Task 5.3.1] a completed edit/write tool call carries
        // its patch at state.metadata.filediff = {file, patch, additions,
        // deletions} — confirmed via a live probe prompting a real file edit
        // (PROGRESS.md Phase 5). Push it as a live FILE_DIFF the moment the
        // tool completes, instead of only after the whole prompt finishes.
        const filediff = state.metadata && state.metadata.filediff;
        if (state.status === 'completed' && filediff && filediff.file && typeof filediff.patch === 'string') {
          pushFileDiff(filediff.file, filediff.patch);
        }
      }
    }
    return;
  }
  // [VERIFY LIVE, PROGRESS.md Phase 0] permission/question endpoints and event
  // schema names (permission.asked / permission.v2.asked, question.asked /
  // question.v2.asked) are confirmed from the OpenAPI doc, but a live
  // permission-asked event was not observed in this session's probe (the bash
  // tool ran without requesting approval in that environment) — the envelope
  // key is read defensively (properties, falling back to data, matching the
  // OpenAPI component's own field name) since it could not be empirically
  // confirmed which key the live event actually uses for these two types.
  if (evt.type === 'permission.asked' || evt.type === 'permission.v2.asked') {
    const d = evt.properties || evt.data || {};
    const request = {
      permissionId: d.id,
      sessionId: d.sessionID,
      tool: d.permission || (d.tool && d.tool.callID) || null,
      input: d
    };
    if (typeof d.id === 'string') pendingPermissions.set(d.id, request);
    emit('PERMISSION_REQUEST', request);
    notifyExternal('permission_request', { permissionId: d.id, sessionId: d.sessionID });
    return;
  }
  if (evt.type === 'question.asked' || evt.type === 'question.v2.asked') {
    const d = evt.properties || evt.data || {};
    const request = {
      questionId: d.id,
      sessionId: d.sessionID,
      text: d.text || d.question || null,
      options: d.options || null
    };
    if (typeof d.id === 'string') pendingQuestions.set(d.id, request);
    emit('QUESTION_REQUEST', request);
    return;
  }
  // Other event types (session.updated, session.idle, plugin.added, etc.) are
  // not part of the streaming contract yet — intentionally not relayed.
}

// Opens the real /event SSE stream against the running opencode serve process
// and relays parsed events via `emit`, reconnecting with a fixed delay if the
// stream drops while opencode serve is still up. Not unit-tested directly
// (would require a real HTTP server); relaySseEventBlock/relaySseEvent carry
// the tested logic. Exit criterion for this function is the manual/live check
// described in IMPLEMENTATION_PLAN.md Task 1.1.1.
async function startEventStream(emit) {
  if (ocEventStreamStarted) return;
  ocEventStreamStarted = true;
  for (;;) {
    try {
      const res = await fetch(`${ocBaseUrl}/event`, { headers: { Accept: 'text/event-stream' } });
      if (!res.ok || !res.body) throw new Error(`event stream returned ${res.status}`);
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buf = '';
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        buf += decoder.decode(value, { stream: true });
        let idx;
        while ((idx = buf.indexOf('\n\n')) !== -1) {
          relaySseEventBlock(buf.slice(0, idx), emit);
          buf = buf.slice(idx + 2);
        }
      }
    } catch (e) {
      console.log(`  ! event stream error: ${e.message}`);
    }
    if (!ocServeProc) { ocEventStreamStarted = false; return; }
    await new Promise(r => setTimeout(r, 2000));
  }
}

async function ensureSession() {
  // Trusts a persisted/switched-to session id at face value — opencode's own
  // session storage lives independently of any one `opencode serve` process,
  // so an id from a prior bridge run is expected to still resolve. If it
  // doesn't, the next prompt call surfaces that as a normal error (same path
  // as any other opencode-server error), not a silent fallback.
  if (ocSessionId) return ocSessionId;
  const res = await ocFetch('/session', { method: 'POST', body: JSON.stringify({ title: 'OpenCode Remote' }) });
  if (!res.ok) throw new Error(`Could not create opencode session (${res.status})`);
  const session = await res.json();
  ocSessionId = session.id;
  persistSessionId();
  return ocSessionId;
}

async function runPrompt(prompt) {
  pushAgentState('Thinking...');
  const taskId = `prompt-${Date.now()}`;
  const task = { id: taskId, name: `Prompt: ${prompt.substring(0, 40)}`, status: 'Running' };
  tasks[taskId] = task;
  pushTaskUpdated(task);

  try {
    if (!ocServeProc) startOpenCodeServer();
    await ocReadyPromise;
    startEventStream(broadcast); // idempotent; no-op if already streaming
    const sessionId = await ensureSession();
    task.sessionId = sessionId;

    const res = await ocFetch(`/session/${sessionId}/message`, {
      method: 'POST',
      timeoutMs: OC_PROMPT_TIMEOUT_MS,
      body: JSON.stringify({ parts: [{ type: 'text', text: prompt }] })
    });
    if (!res.ok) {
      const body = await res.text().catch(() => '');
      throw new Error(`opencode server returned ${res.status}: ${body.slice(0, 300)}`);
    }
    const result = await res.json();
    // A 200 here does not mean the prompt succeeded — a provider/auth failure
    // (bad/missing API key, unreachable endpoint) comes back as HTTP 200 with
    // result.info.error set and result.parts empty, not a non-2xx status.
    const serverError = result.info && result.info.error;
    if (serverError) {
      const detail = (serverError.data && serverError.data.message) || serverError.name || 'unknown error';
      pushChatMessage(`Error (${serverError.name || 'provider error'}): ${detail}`, false, 'Error', false);
      notifyExternal('error', { taskId, message: detail });
    } else {
      const text = (result.parts || [])
        .filter(p => p.type === 'text' && p.text)
        .map(p => p.text)
        .join('');
      if (text) pushChatMessage(text.trim(), false, 'Generated response', true);
    }

    // Real per-file diffs from the session, replacing the old hardcoded
    // single-file 'pending_changes.diff' name with the actual changed paths.
    try {
      const diffRes = await ocFetch(`/session/${sessionId}/diff`);
      if (diffRes.ok) {
        const files = await diffRes.json();
        for (const f of files) {
          if (f.file && f.patch) pushFileDiff(f.file, f.patch);
        }
      }
    } catch (e) {
      console.log(`  ! failed to fetch diff: ${e.message}`);
    }
  } catch (e) {
    const message = e.name === 'TimeoutError' || e.name === 'AbortError'
      ? `opencode did not respond within ${Math.round(OC_PROMPT_TIMEOUT_MS / 1000)}s — check the model/provider config on the bridge machine`
      : `Error: ${e.message}`;
    pushChatMessage(message, false, 'Error', false);
    notifyExternal('error', { taskId, message });
  } finally {
    delete tasks[taskId];
    pushTaskRemoved(taskId);
    releaseControllerIfIdle();
    notifyExternal('task_completed', { taskId });
    pushAgentState('Idle');
  }
}

// --- File tree ops ---
function fileTreeToJson(dir, rootName) {
  let nodes = 0;
  function walk(d, rel, depth = 0) {
    if (depth > MAX_TREE_DEPTH) throw new Error('File tree depth limit exceeded');
    const entries = fs.readdirSync(d, { withFileTypes: true });
    const children = [];
    for (const e of entries) {
      if (e.name.startsWith('.') || e.name === 'node_modules') continue;
      if (++nodes > MAX_TREE_NODES) throw new Error('File tree node limit exceeded');
      const full = path.join(d, e.name);
      const rp = rel ? `${rel}/${e.name}` : e.name;
      try {
        if (e.isDirectory()) {
          children.push({ name: e.name, path: rp, isDirectory: true, children: walk(full, rp, depth + 1) });
        } else {
          children.push({ name: e.name, path: rp, isDirectory: false });
        }
      } catch (error) {
        // Resource limits should not be silently interpreted as missing entries.
        if (/^File tree (depth|node) limit exceeded$/.test(error.message)) throw error;
      }
    }
    return children;
  }
  return { name: rootName || path.basename(dir), path: '', isDirectory: true, children: walk(dir, '') };
}

// --- WebSocket server ---
const wss = new WebSocketServer({
  host: BIND_HOST, port: PORT, path: '/ws',
  maxPayload: MAX_WS_PAYLOAD, perMessageDeflate: false
}, () => {
  const nets = os.networkInterfaces();
  const ips = [];
  for (const name of Object.keys(nets)) {
    for (const net of nets[name]) {
      if (net.family === 'IPv4' && !net.internal) ips.push(net.address);
    }
  }
  console.log(`\n  OpenCode Bridge listening on ws://${BIND_HOST}:${PORT}/ws`);

  // E1: advertise this bridge over mDNS so the Android client's NsdHelper can
  // discover it automatically instead of requiring manual IP/QR entry.
  try {
    const bonjour = new Bonjour();
    bonjour.publish({ name: `OpenCode Bridge (${os.hostname()})`, type: 'opencode', protocol: 'tcp', port: PORT, txt: { path: '/ws' } });
    console.log(`  Advertising _opencode._tcp on port ${PORT} via mDNS`);
  } catch (e) {
    console.log(`  mDNS advertisement failed to start: ${e.message}`);
  }

  console.log(`  Companion app connect to:`);
  const printQr = (ip) => {
    const qrString = `ip=${ip};port=${PORT};scheme=ws;key=${AUTH_TOKEN}`;
    console.log(`    ${ip}:${PORT}`);
    QRCode.toString(qrString, { type: 'terminal', small: true }, (err, qr) => {
      if (!err) console.log(qr);
    });
  };
  for (const ip of ips) printQr(ip);
  if (ips.length === 0) printQr('localhost');
  console.log(`  Workspace: ${activeWorkspace}\n`);
  // opencode serve is started lazily on the first PROMPT (see runPrompt) rather
  // than here, so a client that never sends a prompt (browsing files/git only,
  // or a bridge test instance) never spawns it.
});

wss.on('connection', (ws, req) => {
  const clientAddr = req.socket.remoteAddress || 'unknown';
  if (isAuthBlocked(clientAddr)) {
    ws.close(4008, 'authentication rate limit');
    return;
  }
  if (clients.size >= MAX_CLIENTS) {
    ws.close(1013, 'server busy');
    return;
  }
  clients.add(ws);
  ws.authenticated = false;
  ws.isAlive = true;
  ws.on('pong', () => { ws.isAlive = true; });
  const authTimer = setTimeout(() => {
    if (!ws.authenticated) ws.close(4001, 'authentication timeout');
  }, AUTH_TIMEOUT_MS);
  authTimer.unref();
  console.log(`  Client connected: ${clientAddr}`);

  ws.on('message', data => {
    let frame;
    try {
      frame = JSON.parse(data.toString());
    } catch (e) {
      ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: 'Malformed JSON frame' } }));
      return;
    }
    if (!frame || typeof frame !== 'object' || Array.isArray(frame)) {
      ws.close(1003, 'invalid frame');
      return;
    }
    const eventType = frame.eventType;
    const payload = frame.payload;
    console.log(`  < ${eventType}`);

    if (eventType === 'CONNECT') {
      const deviceName = typeof payload?.deviceName === 'string'
        ? payload.deviceName.slice(0, 100) : 'unknown';
      const suppliedToken = payload?.token ?? frame.token ?? '';

      // Case 1: an already-issued per-device token (the normal reconnect path).
      const lookupKey = typeof suppliedToken === 'string' && /^[a-f0-9]{64}$/.test(suppliedToken)
        ? deviceTokenKey(suppliedToken) : null;
      const existingDevice = lookupKey && Object.hasOwn(deviceTokens, lookupKey)
        ? deviceTokens[lookupKey] : undefined;
      if (lookupKey && validDeviceRecord(lookupKey, existingDevice)) {
        ws.authenticated = true;
        clearTimeout(authTimer);
        authFailures.delete(clientAddr);
        ws.deviceToken = suppliedToken;
        ws.deviceTokenKey = lookupKey;
        ws.deviceId = existingDevice.deviceId;
        ws.deviceName = existingDevice.deviceName || deviceName;
        sessions[suppliedToken] = { name: deviceName, workspace: activeWorkspace };
        ws.send(JSON.stringify({ eventType: 'CONNECTED', payload: { sessionId: suppliedToken, deviceId: existingDevice.deviceId, deviceName } }));
        ws.send(JSON.stringify({ eventType: 'WORKSPACE_LIST', payload: workspaces }));
        replayNotifications(ws);
        pushGitStatus(ws);
        return;
      }

      // Case 2: the one-time pairing secret -> issue and persist a fresh
      // per-device token, and tell the client to switch to it.
      if (typeof suppliedToken === 'string' && Date.now() < pairingDeadline
          && pairingsRemaining > 0 && timingSafeTokenEquals(suppliedToken, AUTH_TOKEN)) {
        const { token, deviceId } = issueDeviceToken(deviceName);
        pairingsRemaining -= 1;
        ws.authenticated = true;
        clearTimeout(authTimer);
        authFailures.delete(clientAddr);
        ws.deviceToken = token;
        ws.deviceTokenKey = deviceTokenKey(token);
        ws.deviceId = deviceId;
        ws.deviceName = deviceName;
        // Newly paired devices should not see earlier notifications.
        deliveredNotificationSeq.set(deviceId, notificationSeq);
        sessions[token] = { name: deviceName, workspace: activeWorkspace };
        ws.send(JSON.stringify({ eventType: 'CONNECTED', payload: { sessionId: token, deviceId, deviceName, issuedToken: token } }));
        ws.send(JSON.stringify({ eventType: 'WORKSPACE_LIST', payload: workspaces }));
        replayNotifications(ws);
        pushGitStatus(ws);
        return;
      }

      // Neither a known device token nor the pairing secret.
      ws.close(rejectAuthFrom(clientAddr) ? 4008 : 4001, 'unauthorized');
      return;
    }

    if (!ws.authenticated) {
      ws.close(rejectAuthFrom(clientAddr) ? 4008 : 4001, 'unauthorized');
      return;
    }

    if (eventType === 'RELEASE_CONTROL') {
      if (controllerDeviceId === ws.deviceId && Object.keys(tasks).length === 0) {
        controllerDeviceId = null;
        ws.send(JSON.stringify({ eventType: 'CONTROL_RELEASED', payload: { deviceId: ws.deviceId } }));
      } else {
        ws.send(JSON.stringify({ eventType: 'ERROR', payload: {
          operation: 'RELEASE_CONTROL', message: 'No idle control lease owned by this device' } }));
      }
      return;
    }
    if (MUTATING_ACTIONS.has(eventType)) {
      if (controllerDeviceId !== null && controllerDeviceId !== ws.deviceId) {
        ws.send(JSON.stringify({ eventType: 'ERROR', payload: {
          operation: eventType, message: 'Another device currently controls this bridge' } }));
        return;
      }
      if (CONTEXT_SWITCH_ACTIONS.has(eventType) && Object.keys(tasks).length > 0) {
        ws.send(JSON.stringify({ eventType: 'ERROR', payload: {
          operation: eventType, message: 'Wait for the running prompt to finish before switching context' } }));
        return;
      }
      controllerDeviceId = ws.deviceId;
    }

    switch (eventType) {
      case 'FETCH_RUNTIME_STATE': {
        // A reconnect explicitly requests a full current-state replacement.
        // Deliberately not sent during legacy CONNECT handshakes.
        ws.send(JSON.stringify({ eventType: 'RUNTIME_STATE', payload: runtimeSnapshot() }));
        break;
      }
      case 'FETCH_WORKSPACES': {
        ws.send(JSON.stringify({ eventType: 'WORKSPACE_LIST', payload: workspaces }));
        break;
      }
      case 'OPEN_WORKSPACE': {
        const dir = String(payload);
        try {
          const { resolved, error } = resolveWithinWorkspaceRoot(dir);
          if (error) {
            ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: error } }));
          } else {
            activeWorkspace = resolved;
            if (!workspaces.some(w => w.path === resolved)) workspaces.push(makeWorkspaceEntry(resolved));
            if (ocServeProc) startOpenCodeServer(); // already running -> restart bound to the new dir
            ws.send(JSON.stringify({ eventType: 'WORKSPACE_OPENED', payload: { path: resolved } }));
            broadcast('WORKSPACE_LIST', workspaces);
          }
        } catch (e) {
          ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: e.message } }));
        }
        break;
      }
      case 'ADD_WORKSPACE': {
        // Registers a directory in the workspace list without switching to
        // it (Task 6.1.1) — e.g. the Android "add workspace" action, which
        // shouldn't interrupt whatever is currently open.
        const dir = String(payload);
        try {
          const { resolved, error } = resolveWithinWorkspaceRoot(dir);
          if (error) {
            ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: error } }));
          } else {
            if (!workspaces.some(w => w.path === resolved)) workspaces.push(makeWorkspaceEntry(resolved));
            broadcast('WORKSPACE_LIST', workspaces);
          }
        } catch (e) {
          ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: e.message } }));
        }
        break;
      }
      case 'FETCH_FILE_TREE': {
        // Correlate only modern structured requests. Bare legacy frames keep the
        // original FILE_TREE payload shape for backwards compatibility.
        const structured = payload !== null && typeof payload === 'object' && !Array.isArray(payload);
        const requestId = structured ? payload.requestId : null;
        if (structured && (typeof requestId !== 'string' || requestId.length < 1 || requestId.length > 128)) {
          ws.send(JSON.stringify({ eventType: 'ERROR', payload: {
            operation: 'FETCH_FILE_TREE', message: 'Invalid request ID' } }));
          break;
        }
        try {
          const tree = fileTreeToJson(activeWorkspace, path.basename(activeWorkspace));
          const reply = structured ? { requestId, tree } : tree;
          if (Buffer.byteLength(JSON.stringify(reply)) > MAX_FILE_CONTENT_BYTES) {
            throw new Error('File tree exceeds the 8 MiB direct-response limit');
          }
          ws.send(JSON.stringify({ eventType: 'FILE_TREE', payload: reply }));
        } catch (e) {
          ws.send(JSON.stringify({ eventType: 'ERROR', payload: {
            operation: 'FETCH_FILE_TREE', message: e.message,
            ...(structured ? { requestId } : {}) } }));
        }
        break;
      }
      case 'FETCH_FILE': {
        // Legacy clients send a bare path; correlated requests include an id.
        const structured = payload !== null && typeof payload === 'object' && !Array.isArray(payload);
        const fp = structured ? payload.path : payload;
        const requestId = structured ? payload.requestId : null;
        const validRequest = !structured || (typeof requestId === 'string'
          && requestId.length >= 1 && requestId.length <= 128);
        const sendFileError = (message) => {
          ws.send(JSON.stringify({
            eventType: 'ERROR',
            payload: { message, operation: 'FETCH_FILE',
              ...(validRequest && structured ? { requestId } : {}) }
          }));
        };
        if (!validRequest || typeof fp !== 'string') {
          sendFileError('Invalid file request');
          break;
        }
        try {
          const fullPath = resolveInsideWorkspace(fp);
          const stat = fs.statSync(fullPath);
          if (stat.isFile()) {
            if (stat.size > MAX_FILE_CONTENT_BYTES) {
              sendFileError('File exceeds the 8 MiB direct-response limit');
              break;
            }
            const content = fs.readFileSync(fullPath, 'utf-8');
            const response = structured ? { requestId, path: fp, content } : content;
            ws.send(JSON.stringify({ eventType: 'FILE_CONTENT', payload: response }));
          } else {
            sendFileError(`File not found: ${fp}`);
          }
        } catch (e) {
          sendFileError(e.message);
        }
        break;
      }
      case 'PROMPT': {
        runPrompt(String(payload));
        break;
      }
      case 'ACCEPT_DIFF': {
        const file = String(payload);
        delete pendingDiffs[file];
        break;
      }
      case 'REJECT_DIFF': {
        const file = String(payload);
        delete pendingDiffs[file];
        break;
      }
      case 'LIST_SESSIONS': {
        (async () => {
          try {
            if (!ocServeProc) startOpenCodeServer();
            await ocReadyPromise;
            const res = await ocFetch('/session');
            if (!res.ok) throw new Error(`list sessions returned ${res.status}`);
            const sessions = await res.json();
            ws.send(JSON.stringify({
              eventType: 'SESSION_LIST',
              payload: sessions.map(s => ({ id: s.id, title: s.title, updatedAt: s.time && s.time.updated }))
            }));
          } catch (e) {
            ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: `LIST_SESSIONS failed: ${e.message}` } }));
          }
        })();
        break;
      }
      case 'SWITCH_SESSION': {
        const targetId = String(payload);
        (async () => {
          try {
            if (!ocServeProc) startOpenCodeServer();
            await ocReadyPromise;
            const res = await ocFetch(`/session/${targetId}`);
            if (!res.ok) throw new Error(`session not found (${res.status})`);
            ocSessionId = targetId;
            persistSessionId();
            ws.send(JSON.stringify({ eventType: 'SESSION_SWITCHED', payload: { id: targetId } }));
          } catch (e) {
            ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: `SWITCH_SESSION failed: ${e.message}` } }));
          }
        })();
        break;
      }
      case 'FETCH_ALL_SESSIONS': {
        // Read OpenCode's persisted cross-workspace session DB. Echo the
        // request ID so a late page cannot replace a newer search result.
        const opts = (payload && typeof payload === 'object' && !Array.isArray(payload)) ? payload : {};
        const requestId = opts.requestId;
        if (requestId !== undefined && (typeof requestId !== 'string'
            || requestId.length < 1 || requestId.length > 128)) {
          ws.send(JSON.stringify({ eventType: 'ERROR', payload: {
            message: 'Invalid global-session request ID', operation: 'FETCH_ALL_SESSIONS' } }));
          break;
        }
        try {
          const result = listAllSessions({ limit: opts.limit, cursor: opts.cursor });
          ws.send(JSON.stringify({ eventType: 'ALL_SESSIONS_LIST', payload: {
            ...result, ...(requestId ? { requestId } : {}) } }));
        } catch (e) {
          ws.send(JSON.stringify({ eventType: 'ERROR', payload: {
            message: `FETCH_ALL_SESSIONS failed: ${e.message}`, operation: 'FETCH_ALL_SESSIONS',
            ...(requestId ? { requestId } : {}) } }));
        }
        break;
      }
      case 'OPEN_SESSION_GLOBAL': {
        // Opens a session found via FETCH_ALL_SESSIONS, which may belong to
        // a workspace other than activeWorkspace (or one opencode serve
        // isn't currently bound to at all). Reuses OPEN_WORKSPACE's and
        // SWITCH_SESSION's exact machinery rather than inventing a new
        // session-opening path — see adr/0002-....md, step list under
        // "Selecting a session from the global list".
        const id = payload && payload.id;
        const worktree = payload && payload.worktree;
        (async () => {
          try {
            if (!id || !worktree) {
              ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: 'OPEN_SESSION_GLOBAL requires both id and worktree', operation: 'OPEN_SESSION_GLOBAL' } }));
              return;
            }
            const { resolved, error } = resolveWithinWorkspaceRoot(worktree);
            if (error) {
              ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: error, operation: 'OPEN_SESSION_GLOBAL' } }));
              return;
            }
            activeWorkspace = resolved;
            if (!workspaces.some(w => w.path === resolved)) workspaces.push(makeWorkspaceEntry(resolved));
            // Unlike OPEN_WORKSPACE (which only restarts an already-running
            // serve, since switching workspace alone doesn't require one),
            // this frame is about to validate and use a specific session id
            // against opencode serve, so it must (re)start it bound to the
            // target dir unconditionally — whether or not one was already
            // running, and whether or not it was already bound here.
            startOpenCodeServer();
            await ocReadyPromise;
            const res = await ocFetch(`/session/${id}`);
            if (!res.ok) throw new Error(`session not found (${res.status})`);
            ocSessionId = id;
            persistSessionId();
            ws.send(JSON.stringify({ eventType: 'SESSION_OPENED', payload: { id, worktree: resolved } }));
            broadcast('WORKSPACE_LIST', workspaces);
          } catch (e) {
            ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: `OPEN_SESSION_GLOBAL failed: ${e.message}`, operation: 'OPEN_SESSION_GLOBAL' } }));
          }
        })();
        break;
      }
      case 'NEW_SESSION': {
        const title = (payload && payload.title) || undefined;
        (async () => {
          try {
            if (!ocServeProc) startOpenCodeServer();
            await ocReadyPromise;
            const res = await ocFetch('/session', { method: 'POST', body: JSON.stringify({ title: title || 'OpenCode Remote' }) });
            if (!res.ok) throw new Error(`create session returned ${res.status}`);
            const session = await res.json();
            ocSessionId = session.id;
            persistSessionId();
            ws.send(JSON.stringify({ eventType: 'SESSION_SWITCHED', payload: { id: session.id } }));
          } catch (e) {
            ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: `NEW_SESSION failed: ${e.message}` } }));
          }
        })();
        break;
      }
      case 'FORK_SESSION': {
        // Confirmed endpoint (PROGRESS.md Phase 0): POST /session/{id}/fork
        const sourceId = String(payload);
        (async () => {
          try {
            if (!ocServeProc) startOpenCodeServer();
            await ocReadyPromise;
            const res = await ocFetch(`/session/${sourceId}/fork`, { method: 'POST' });
            if (!res.ok) throw new Error(`fork returned ${res.status}`);
            const forked = await res.json();
            ocSessionId = forked.id;
            persistSessionId();
            ws.send(JSON.stringify({ eventType: 'SESSION_SWITCHED', payload: { id: forked.id } }));
          } catch (e) {
            ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: `FORK_SESSION failed: ${e.message}` } }));
          }
        })();
        break;
      }
      case 'PERMISSION_REPLY': {
        // Confirmed endpoint (PROGRESS.md Phase 0): POST /permission/{requestID}/reply
        const { permissionId, decision } = payload || {};
        if (!permissionId) break;
        appendAuditLog('permission_decision', { permissionId, decision }, ws);
        ocFetch(`/permission/${permissionId}/reply`, {
          method: 'POST',
          body: JSON.stringify({ decision })
        }).then(res => { if (res.ok) pendingPermissions.delete(permissionId); })
          .catch(e => console.log(`  ! PERMISSION_REPLY failed: ${e.message}`));
        break;
      }
      case 'QUESTION_REPLY': {
        // Confirmed endpoint (PROGRESS.md Phase 0): POST /question/{requestID}/reply
        const { questionId, answer } = payload || {};
        if (!questionId) break;
        // Never persist arbitrary answer contents in the audit log.
        appendAuditLog('question_reply', { questionId, answered: true }, ws);
        ocFetch(`/question/${questionId}/reply`, {
          method: 'POST',
          body: JSON.stringify({ answer })
        }).then(res => { if (res.ok) pendingQuestions.delete(questionId); })
          .catch(e => console.log(`  ! QUESTION_REPLY failed: ${e.message}`));
        break;
      }
      case 'QUESTION_REJECT': {
        // Confirmed endpoint (PROGRESS.md Phase 0): POST /question/{requestID}/reject
        const { questionId } = payload || {};
        if (!questionId) break;
        ocFetch(`/question/${questionId}/reject`, { method: 'POST' })
          .then(res => { if (res.ok) pendingQuestions.delete(questionId); })
          .catch(e => console.log(`  ! QUESTION_REJECT failed: ${e.message}`));
        break;
      }
      case 'ACCEPT_HUNK':
      case 'REJECT_HUNK': {
        // [VERIFY LIVE, confirmed in PROGRESS.md Phase 0 finding 0.2.6] opencode
        // serve v1.18.26's OpenAPI doc (probed via GET /doc) has no per-hunk or
        // partial-apply endpoint anywhere — only whole-file diff endpoints exist
        // (GET /session/{id}/diff, GET /vcs/diff, GET /vcs/diff/raw). Task 5.2 is
        // therefore Won't do (unsupported upstream) per PROGRESS.md; this stays a
        // documented no-op rather than a real implementation.
        console.log(`  ! ${eventType} is not supported by this bridge (no per-hunk apply upstream) - ignored`);
        break;
      }
      case 'TERMINAL': {
        const command = String(payload);
        pushTerminalLine(`> ${command}`);
        appendAuditLog('terminal_command', {
          // Raw command text may include passwords, access tokens or private data.
          sha256: crypto.createHash('sha256').update(command).digest('hex'),
          length: command.length
        }, ws);
        runTerminalCommand(command).catch(err => pushTerminalLine(`Error: ${err.message}`));
        break;
      }
      case 'TERMINAL_RESIZE': {
        const { cols, rows, ptyId } = payload || {};
        const targetId = ptyId || activePtyId;
        if (!targetId || !cols || !rows) break;
        (async () => {
          if (!ocServeProc) startOpenCodeServer();
          await ocReadyPromise;
          await ocFetch(`/pty/${targetId}`, { method: 'PUT', body: JSON.stringify({ size: { rows, cols } }) });
        })().catch(e => console.log(`  ! TERMINAL_RESIZE failed: ${e.message}`));
        break;
      }
      case 'ROTATE_TOKEN': {
        const oldKey = ws.deviceTokenKey;
        const existing = oldKey && Object.hasOwn(deviceTokens, oldKey) ? deviceTokens[oldKey] : null;
        if (!existing || existing.deviceId !== ws.deviceId) {
          ws.send(JSON.stringify({ eventType: 'ERROR', payload: {
            operation: 'ROTATE_TOKEN', message: 'Current device credential is invalid' } }));
          break;
        }
        try {
          const newToken = crypto.randomBytes(32).toString('hex');
          const newKey = deviceTokenKey(newToken);
          const replacement = Object.assign(Object.create(null), deviceTokens);
          delete replacement[oldKey];
          replacement[newKey] = { ...existing, issuedAt: Date.now() };
          persistDeviceTokens(replacement); // Must succeed before changing memory or announcing token.
          deviceTokens = replacement;
          delete sessions[ws.deviceToken];
          ws.deviceToken = newToken;
          ws.deviceTokenKey = newKey;
          for (const other of clients) {
            if (other !== ws && other.deviceId === ws.deviceId) {
              other.authenticated = false;
              other.close(4001, 'device token rotated');
            }
          }
          ws.send(JSON.stringify({ eventType: 'TOKEN_ROTATED', payload: {
            deviceId: ws.deviceId, issuedToken: newToken } }));
          appendAuditLog('token_rotated', { deviceId: ws.deviceId }, ws);
        } catch (error) {
          ws.send(JSON.stringify({ eventType: 'ERROR', payload: {
            operation: 'ROTATE_TOKEN', message: 'Could not persist the new device credential' } }));
        }
        break;
      }
      case 'REVOKE_TOKEN': {
        // Task 8.2.1: removes one device's token from the store (identified
        // by deviceId, not the token itself — the UI never needs to see raw
        // tokens) and closes any of its currently-open sockets. The other
        // paired devices and the pairing secret itself are unaffected.
        const targetDeviceId = String(payload);
        const targetEntry = Object.entries(deviceTokens).find(([, info]) => info.deviceId === targetDeviceId);
        if (targetEntry) {
          const [targetToken] = targetEntry;
          delete deviceTokens[targetToken];
          saveDeviceTokens();
          for (const client of clients) {
            if (client.deviceTokenKey === targetToken) {
              client.authenticated = false;
              client.close(4001, 'revoked');
            }
          }
          ws.send(JSON.stringify({ eventType: 'TOKEN_REVOKED', payload: { deviceId: targetDeviceId } }));
        } else {
          ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: 'Unknown device' } }));
        }
        break;
      }
      case 'LIST_DEVICES': {
        // Task 8.2.2 support: lets the Android devices/settings screen show
        // who's paired, without exposing raw tokens.
        const list = Object.entries(deviceTokens).map(([, info]) => ({
          deviceId: info.deviceId,
          deviceName: info.deviceName,
          issuedAt: info.issuedAt
        }));
        ws.send(JSON.stringify({ eventType: 'DEVICE_LIST', payload: list }));
        break;
      }
      case 'FETCH_AUDIT_LOG': {
        // Task 8.4.2 support: reads back recent audit log entries for the
        // Android audit log screen. Returns the last 200 lines, newest last.
        let entries = [];
        try {
          const lines = fs.readFileSync(AUDIT_LOG_PATH, 'utf-8').split('\n').filter(Boolean);
          entries = lines.slice(-200).map(line => {
            try { return JSON.parse(line); } catch { return null; }
          }).filter(Boolean);
        } catch {
          entries = [];
        }
        ws.send(JSON.stringify({ eventType: 'AUDIT_LOG', payload: entries }));
        break;
      }
      case 'KILL_TASK': {
        const tid = String(payload);
        const task = tasks[tid];
        if (task) {
          // Prompt tasks no longer have an OS pid to kill (they run inside the
          // persistent opencode server) — abort the in-flight generation instead.
          if (task.sessionId) {
            ocFetch(`/session/${task.sessionId}/abort`, { method: 'POST' }).catch(() => {});
          }
          delete tasks[tid];
          pushTaskRemoved(tid);
          pushChatMessage('Cancelled', false, 'Cancelled', false);
        }
        break;
      }
      case 'GIT': {
        const gitCmd = String(payload);
        const args = tokenizeGitArgs(gitCmd);
        const ALLOWED_GIT_SUBCOMMANDS = ['status', 'add', 'commit', 'push', 'pull', 'fetch', 'checkout', 'branch', 'stash', 'cherry-pick', 'diff', 'log', 'rev-parse'];
        if (args.length === 0 || !ALLOWED_GIT_SUBCOMMANDS.includes(args[0])) {
          pushTerminalLine(`Error: git subcommand not allowed: ${args[0] || '(empty)'}`);
          break;
        }
        pushTerminalLine(`$ git ${gitCmd}`);
        appendAuditLog('git_command', { command: args[0], argumentCount: args.length - 1 }, ws);
        const child = spawn('git', args, { cwd: activeWorkspace });
        let out = '';
        child.stdout.on('data', d => { out += d.toString(); });
        child.stderr.on('data', d => { out += d.toString(); });
        child.on('close', () => {
          if (out.trim()) pushTerminalLine(out.trim());
          pushGitStatus(null);
        });
        child.on('error', err => pushTerminalLine(`Error: ${err.message}`));
        break;
      }
      default: {
        ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: `Unknown eventType: ${eventType}` } }));
      }
    }
  });

  ws.on('close', () => {
    clearTimeout(authTimer);
    clients.delete(ws);
    releaseControllerIfIdle();
    console.log(`  Client disconnected: ${clientAddr}`);
  });

  ws.on('error', () => { clients.delete(ws); releaseControllerIfIdle(); });
});

const heartbeat = setInterval(() => {
  for (const client of clients) {
    if (!client.isAlive) {
      client.terminate();
      continue;
    }
    client.isAlive = false;
    client.ping();
  }
}, 30000);
heartbeat.unref();
wss.on('close', () => clearInterval(heartbeat));

process.on('SIGINT', () => {
  console.log('\n  Shutting down...');
  if (ocServeProc) ocServeProc.kill();
  wss.close(() => process.exit(0));
});
