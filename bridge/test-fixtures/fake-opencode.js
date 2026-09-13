#!/usr/bin/env node
// A minimal stand-in for the real `opencode` CLI's `serve` subcommand, used
// by bridge/test/stream.test.js (and future live-API-dependent tests) via
// the bridge's OPCODE env var override. Implements just enough of the real
// opencode-serve HTTP surface (confirmed shape recorded in PROGRESS.md Phase
// 0) for the bridge's runPrompt()/startEventStream() to exercise against:
// GET /config, POST /session, POST /session/:id/message,
// GET /session/:id/diff, POST /session/:id/abort, GET /event (SSE),
// POST /pty + PUT /pty/:id + GET /pty/:id/connect (WebSocket — Task 4.1).
const http = require('http');
const { WebSocketServer } = require('ws');

const args = process.argv.slice(2);
const portIdx = args.indexOf('--port');
const port = portIdx !== -1 ? parseInt(args[portIdx + 1], 10) : 4096;

let sessionCounter = 0;
const knownSessions = new Set(); // ids ever returned by POST /session, for GET /session/:id existence checks

// Optional cross-restart persistence, opt-in via env var. The bridge kills
// and respawns this fixture as a subprocess whenever it (re)binds
// opencode-serve to a different workspace directory (see
// startOpenCodeServer() in main.js) — the real `opencode serve` reloads
// session state from its own on-disk storage across such a restart, but
// this in-memory fixture doesn't by default, which is right for every
// existing test (they never exercise a serve restart mid-test) but wrong
// for a test that specifically does (see globalSessions.test.js). Setting
// FAKE_OPENCODE_STATE_FILE makes this instance load/save knownSessions
// there instead of starting empty every time.
const stateFilePath = process.env.FAKE_OPENCODE_STATE_FILE;
if (stateFilePath) {
  try {
    const fs = require('fs');
    const saved = JSON.parse(fs.readFileSync(stateFilePath, 'utf-8'));
    for (const id of saved) knownSessions.add(id);
  } catch {
    // No state yet (first run) — start empty, same as the non-persisted default.
  }
}
function persistKnownSessions() {
  if (!stateFilePath) return;
  try {
    require('fs').writeFileSync(stateFilePath, JSON.stringify([...knownSessions]));
  } catch {
    // Best-effort; a failed persist just means the next restart starts fresh.
  }
}

let ptyCounter = 0;
const knownPtys = new Map(); // id -> { size: {rows, cols} | null }

// Records every inbound request this fixture receives, readable via
// GET /__requests, so tests can assert the bridge called the expected
// upstream endpoint (e.g. PERMISSION_REPLY -> POST /permission/{id}/reply)
// without needing to intercept global.fetch.
const requestLog = [];

// Canned /event stream: one text delta, a tool call (running) followed by its
// result (completed), then a permission-asked event — matches the real
// message.part.delta / message.part.updated(part.type==='tool') /
// permission.asked shapes recorded in PROGRESS.md.
const CANNED_EVENTS = [
  { type: 'message.part.delta', properties: { sessionID: 'ses_fake_1', part: { type: 'text', text: 'Hello from fake', sessionID: 'ses_fake_1' } } },
  { type: 'message.part.updated', properties: { sessionID: 'ses_fake_1', part: { type: 'tool', tool: 'bash', state: { status: 'running', input: { command: 'ls' } } } } },
  { type: 'message.part.updated', properties: { sessionID: 'ses_fake_1', part: { type: 'tool', tool: 'bash', state: { status: 'completed', input: { command: 'ls' }, output: 'file1\nfile2' } } } },
  // Matches the real completed-edit-tool shape confirmed via a live probe
  // (PROGRESS.md Phase 5, Task 5.3.1): state.metadata.filediff carries the patch.
  { type: 'message.part.updated', properties: { sessionID: 'ses_fake_1', part: { type: 'tool', tool: 'edit', state: { status: 'completed', input: { filePath: '/tmp/fake.txt' }, output: 'Edit applied successfully.', metadata: { filediff: { file: '/tmp/fake.txt', patch: '--- a\n+++ b\n@@ -1 +1 @@\n-old\n+new\n', additions: 1, deletions: 1 } } } } } },
  { type: 'permission.asked', properties: { id: 'per_fake_1', sessionID: 'ses_fake_1', permission: 'bash', patterns: [], metadata: {}, always: [] } },
  { type: 'question.asked', properties: { id: 'que_fake_1', sessionID: 'ses_fake_1', text: 'Which approach?', options: ['a', 'b'] } }
];

const server = http.createServer((req, res) => {
  const url = req.url || '';

  if (req.method === 'GET' && url === '/__requests') {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify(requestLog));
    return;
  }

  // GET /event is a long-lived SSE connection — never buffer its body (it has
  // none, and req.on('end') on some Node versions doesn't fire promptly for a
  // connection the server itself intends to hold open for writing).
  if (req.method === 'GET' && url === '/event') {
    dispatch(req, res, url);
    return;
  }

  // Record every other request (method, path, and JSON body if any) before
  // dispatching.
  let rawBody = '';
  req.on('data', d => { rawBody += d; });
  req.on('end', () => {
    let body = null;
    try { body = rawBody ? JSON.parse(rawBody) : null; } catch { body = rawBody; }
    requestLog.push({ method: req.method, url, body });
    dispatch(req, res, url, body);
  });
});

function dispatch(req, res, url, body) {
  if (req.method === 'GET' && url === '/config') {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end('{}');
    return;
  }
  if (req.method === 'GET' && url === '/session') {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify([...knownSessions].map(id => ({ id, title: `Session ${id}`, time: { updated: 1700000000000 } }))));
    return;
  }
  if (req.method === 'POST' && url === '/session') {
    sessionCounter++;
    const id = `ses_fake_${sessionCounter}`;
    knownSessions.add(id);
    persistKnownSessions();
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ id }));
    return;
  }
  if (req.method === 'GET' && /^\/session\/[^/]+$/.test(url)) {
    const id = url.split('/')[2];
    if (knownSessions.has(id)) {
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ id, title: `Session ${id}` }));
    } else {
      res.writeHead(404, { 'content-type': 'application/json' });
      res.end('{}');
    }
    return;
  }
  if (req.method === 'POST' && /^\/session\/[^/]+\/message$/.test(url)) {
    res.writeHead(200, { 'content-type': 'application/json' });
    const text = body && body.parts && body.parts[0] && body.parts[0].text;
    if (text === 'TRIGGER_ERROR') {
      // Matches the real 200-with-result.info.error shape (a provider/auth
      // failure) that PROGRESS.md documents runPrompt() must handle.
      res.end(JSON.stringify({ info: { role: 'assistant', error: { name: 'ProviderError', data: { message: 'fake provider failure' } } }, parts: [] }));
    } else {
      res.end(JSON.stringify({ info: { role: 'assistant', error: null }, parts: [{ type: 'text', text: 'canned reply' }] }));
    }
    return;
  }
  if (req.method === 'GET' && /^\/session\/[^/]+\/diff$/.test(url)) {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end('[]');
    return;
  }
  if (req.method === 'POST' && /^\/session\/[^/]+\/fork$/.test(url)) {
    sessionCounter++;
    const id = `ses_fake_${sessionCounter}`;
    knownSessions.add(id);
    persistKnownSessions();
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ id }));
    return;
  }
  if (req.method === 'POST' && /^\/session\/[^/]+\/abort$/.test(url)) {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end('{}');
    return;
  }
  if (req.method === 'GET' && url === '/event') {
    res.writeHead(200, { 'content-type': 'text/event-stream', 'cache-control': 'no-cache', connection: 'keep-alive' });
    let i = 0;
    const timer = setInterval(() => {
      if (i >= CANNED_EVENTS.length) { clearInterval(timer); return; }
      res.write(`data: ${JSON.stringify(CANNED_EVENTS[i])}\n\n`);
      i++;
    }, 150);
    req.on('close', () => clearInterval(timer));
    return;
  }
  if (req.method === 'POST' && url === '/pty') {
    ptyCounter++;
    const id = `pty_fake_${ptyCounter}`;
    knownPtys.set(id, { size: null });
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ id, status: 'running' }));
    return;
  }
  if (req.method === 'PUT' && /^\/pty\/[^/]+$/.test(url)) {
    const id = url.split('/')[2];
    const pty = knownPtys.get(id);
    if (!pty) { res.writeHead(404, { 'content-type': 'application/json' }); res.end('{}'); return; }
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ id }));
    return;
  }
  if (req.method === 'POST' && /^\/permission\/[^/]+\/reply$/.test(url)) {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end('{}');
    return;
  }
  if (req.method === 'POST' && /^\/question\/[^/]+\/(reply|reject)$/.test(url)) {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end('{}');
    return;
  }
  res.writeHead(404, { 'content-type': 'application/json' });
  res.end('{}');
}

// WebSocket transport for GET /pty/:id/connect — canned output: an
// out-of-band NUL-prefixed control frame (mirrors the real cursor-position
// frame seen in a live probe, PROGRESS.md Phase 4) then the input echoed
// back once, matching a shell echoing keystrokes.
const wss = new WebSocketServer({ noServer: true });
server.on('upgrade', (req, socket, head) => {
  const m = (req.url || '').match(/^\/pty\/([^/]+)\/connect$/);
  if (!m || !knownPtys.has(m[1])) { socket.destroy(); return; }
  wss.handleUpgrade(req, socket, head, (ws) => {
    ws.send(' {"cursor":0}');
    // Two canned output chunks sent on a delay, to prove the bridge relays
    // them incrementally (Task 4.1.1) rather than buffering until close.
    let i = 0;
    const chunks = ['chunk1\r\n', 'chunk2\r\n'];
    const timer = setInterval(() => {
      if (i >= chunks.length) { clearInterval(timer); return; }
      ws.send(chunks[i]);
      i++;
    }, 100);
    ws.on('close', () => clearInterval(timer));
  });
});

server.listen(port, '127.0.0.1');

process.on('SIGTERM', () => process.exit(0));
process.on('SIGINT', () => process.exit(0));
