const { WebSocketServer } = require('ws');
const { Bonjour } = require('bonjour-service');
const { exec, spawn } = require('child_process');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const os = require('os');
const QRCode = require('qrcode');

const PORT = parseInt(process.argv[2] || process.env.PORT || '8080', 10);
const OPCODE = process.env.OPCODE || 'opencode';
const AUTH_TOKEN = process.env.BRIDGE_TOKEN || crypto.randomBytes(32).toString('hex');
console.log(`  Auth token: ${AUTH_TOKEN}`);

const clients = new Set();
let sessions = {};
let activeWorkspace = process.cwd();
let pendingDiffs = {};
let tasks = {};
let opencodeProc = null;
const WORKSPACE_ROOT = process.env.WORKSPACE_ROOT || process.cwd();

function resolveInsideWorkspace(p) {
  const abs = path.resolve(activeWorkspace, p);
  const root = path.resolve(activeWorkspace);
  if (abs !== root && !abs.startsWith(root + path.sep)) {
    throw new Error('path escapes workspace');
  }
  return abs;
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
    if (ws.readyState === 1 && ws.authenticated) ws.send(msg);
  }
}

function pushFileTree(dir) {
  function walk(d, rel) {
    const entries = fs.readdirSync(d, { withFileTypes: true });
    const children = [];
    for (const e of entries) {
      if (e.name.startsWith('.') || e.name === 'node_modules') continue;
      const full = path.join(d, e.name);
      const rp = rel ? `${rel}/${e.name}` : e.name;
      try {
        if (e.isDirectory()) {
          children.push({ id: rp, name: e.name, isFolder: true, children: walk(full, rp) });
        } else {
          const st = fs.statSync(full);
          children.push({ id: rp, name: e.name, isFolder: false, size: st.size });
        }
      } catch {}
    }
    return children;
  }
  try {
    const root = path.basename(dir);
    broadcast({ type: 'FileTree', data: { id: 'root', name: root, isFolder: true, children: walk(dir, '') } });
  } catch (e) {
    broadcast({ type: 'FileTree', data: null, error: e.message });
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

function pushFileDiff(fileName, diffText) {
  pendingDiffs[fileName] = diffText;
  broadcast('FILE_DIFF', { fileName, diffText });
}

function pushTerminalLine(line) {
  broadcast('TERMINAL_OUTPUT', line);
}

function pushTaskUpdated(task) {
  broadcast('TASK_UPDATED', { id: task.id, name: task.name, port: task.port || null, status: task.status });
}

function pushTaskRemoved(id) {
  broadcast('TASK_REMOVED', id);
}

// --- OpenCode subprocess ---
function startOpenCode() {
  if (opencodeProc) { opencodeProc.kill(); opencodeProc = null; }
  pushAgentState('Idle');
}

function runPrompt(prompt) {
  pushAgentState('Thinking...');
  const proc = spawn(OPCODE, ['run', '--format', 'json', '--auto', '--dir', activeWorkspace, prompt], {
    cwd: activeWorkspace,
    env: { ...process.env, OPENCODE_CLI_DISABLE_PROMPT: '1' }
  });
  let buffer = '';
  let diffAccum = '';
  let lastText = '';

  proc.stdout.on('data', chunk => {
    buffer += chunk.toString();
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';
    for (const line of lines) {
      if (!line.trim()) continue;
      try {
        const evt = JSON.parse(line);
        if (evt.type === 'text' && evt.text) {
          lastText += evt.text;
        }
        if (evt.type === 'tool' && evt.tool === 'file.edit') {
          diffAccum += evt.diff || evt.content || '';
        }
        if (evt.type === 'agent' && evt.state) {
          pushAgentState(evt.state);
        }
      } catch {}
    }
  });

  proc.stderr.on('data', chunk => {
    pushTerminalLine(chunk.toString());
  });

  proc.on('close', code => {
    if (lastText) pushChatMessage(lastText.trim(), false, 'Generated code', true);
    if (diffAccum) pushFileDiff('pending_changes.diff', diffAccum);
    pushAgentState('Idle');
  });

  proc.on('error', err => {
    pushChatMessage(`Error: ${err.message}`, false, 'Error', false);
    pushAgentState('Idle');
  });

  opencodeProc = proc;
  const task = { id: String(proc.pid), name: `Prompt: ${prompt.substring(0, 40)}`, pid: proc.pid, status: 'Running' };
  tasks[proc.pid] = task;
  pushTaskUpdated(task);
  proc.on('close', () => {
    delete tasks[proc.pid];
    pushTaskRemoved(String(proc.pid));
  });
}

// --- File tree ops ---
function fileTreeToJson(dir, rootName) {
  function walk(d, rel) {
    const entries = fs.readdirSync(d, { withFileTypes: true });
    const children = [];
    for (const e of entries) {
      if (e.name.startsWith('.') || e.name === 'node_modules') continue;
      const full = path.join(d, e.name);
      const rp = rel ? `${rel}/${e.name}` : e.name;
      try {
        if (e.isDirectory()) {
          children.push({ name: e.name, path: rp, isDirectory: true, children: walk(full, rp) });
        } else {
          children.push({ name: e.name, path: rp, isDirectory: false });
        }
      } catch {}
    }
    return children;
  }
  return { name: rootName || path.basename(dir), path: '', isDirectory: true, children: walk(dir, '') };
}

// --- WebSocket server ---
const wss = new WebSocketServer({ port: PORT }, () => {
  const nets = os.networkInterfaces();
  const ips = [];
  for (const name of Object.keys(nets)) {
    for (const net of nets[name]) {
      if (net.family === 'IPv4' && !net.internal) ips.push(net.address);
    }
  }
  console.log(`\n  OpenCode Bridge listening on ws://0.0.0.0:${PORT}/ws`);

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
    const qrString = `ip=${ip};key=${AUTH_TOKEN}`;
    console.log(`    ${ip}:${PORT}`);
    QRCode.toString(qrString, { type: 'terminal', small: true }, (err, qr) => {
      if (!err) console.log(qr);
    });
  };
  for (const ip of ips) printQr(ip);
  if (ips.length === 0) printQr('localhost');
  console.log(`  Workspace: ${activeWorkspace}\n`);
});

wss.on('connection', (ws, req) => {
  clients.add(ws);
  ws.authenticated = false;
  const clientAddr = req.socket.remoteAddress;
  console.log(`  Client connected: ${clientAddr}`);

  ws.on('message', data => {
    let frame;
    try {
      frame = JSON.parse(data.toString());
    } catch (e) {
      ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: 'Malformed JSON frame' } }));
      return;
    }
    const eventType = frame.eventType;
    const payload = frame.payload;
    console.log(`  < ${eventType}`);

    if (eventType === 'CONNECT') {
      const deviceName = (payload && payload.deviceName) || 'unknown';
      const suppliedToken = (payload && payload.token) || frame.token || '';
      const suppliedBuf = Buffer.from(String(suppliedToken));
      const expectedBuf = Buffer.from(AUTH_TOKEN);
      const tokenOk = suppliedBuf.length === expectedBuf.length && crypto.timingSafeEqual(suppliedBuf, expectedBuf);
      if (!tokenOk) {
        ws.close(4001, 'unauthorized');
        return;
      }
      ws.authenticated = true;
      sessions[AUTH_TOKEN] = { name: deviceName, workspace: activeWorkspace };
      ws.send(JSON.stringify({ eventType: 'CONNECTED', payload: { sessionId: AUTH_TOKEN, deviceName } }));
      ws.send(JSON.stringify({ eventType: 'WORKSPACE_LIST', payload: [{ id: 'default', name: path.basename(activeWorkspace), path: activeWorkspace }] }));
      pushGitStatus(ws);
      return;
    }

    if (!ws.authenticated) {
      ws.close(4001, 'unauthorized');
      return;
    }

    switch (eventType) {
      case 'FETCH_WORKSPACES': {
        ws.send(JSON.stringify({ eventType: 'WORKSPACE_LIST', payload: [{ id: 'default', name: path.basename(activeWorkspace), path: activeWorkspace }] }));
        break;
      }
      case 'OPEN_WORKSPACE': {
        const dir = String(payload);
        try {
          const resolved = path.resolve(dir);
          const rootResolved = path.resolve(WORKSPACE_ROOT);
          const inRoot = resolved === rootResolved || resolved.startsWith(rootResolved + path.sep);
          if (!inRoot) {
            ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: `Workspace outside allowed root: ${dir}` } }));
          } else if (fs.existsSync(resolved) && fs.statSync(resolved).isDirectory()) {
            activeWorkspace = resolved;
            ws.send(JSON.stringify({ eventType: 'WORKSPACE_OPENED', payload: { path: resolved } }));
          } else {
            ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: `Workspace not found: ${dir}` } }));
          }
        } catch (e) {
          ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: e.message } }));
        }
        break;
      }
      case 'FETCH_FILE_TREE': {
        try {
          const tree = fileTreeToJson(activeWorkspace, path.basename(activeWorkspace));
          ws.send(JSON.stringify({ eventType: 'FILE_TREE', payload: tree }));
        } catch (e) {
          ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: e.message } }));
        }
        break;
      }
      case 'FETCH_FILE': {
        const fp = String(payload);
        try {
          const fullPath = resolveInsideWorkspace(fp);
          if (fs.existsSync(fullPath) && fs.statSync(fullPath).isFile()) {
            const content = fs.readFileSync(fullPath, 'utf-8');
            ws.send(JSON.stringify({ eventType: 'FILE_CONTENT', payload: content }));
          } else {
            ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: `File not found: ${fp}` } }));
          }
        } catch (e) {
          ws.send(JSON.stringify({ eventType: 'ERROR', payload: { message: e.message } }));
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
      case 'ACCEPT_HUNK':
      case 'REJECT_HUNK': {
        console.log(`  ! ${eventType} is not supported by this bridge (no per-hunk apply) - ignored`);
        break;
      }
      case 'TERMINAL': {
        const command = String(payload);
        pushTerminalLine(`> ${command}`);
        const shellBin = process.platform === 'win32' ? 'cmd' : '/bin/sh';
        const shellFlag = process.platform === 'win32' ? '/c' : '-c';
        const child = spawn(shellBin, [shellFlag, command], { cwd: activeWorkspace });
        let out = '';
        child.stdout.on('data', d => { out += d.toString(); });
        child.stderr.on('data', d => { out += d.toString(); });
        child.on('close', () => {
          const outLines = out.trim().split('\n').filter(Boolean);
          for (const l of outLines) pushTerminalLine(l);
        });
        child.on('error', err => pushTerminalLine(`Error: ${err.message}`));
        break;
      }
      case 'TERMINAL_RESIZE': {
        // No PTY in this bridge; accepted and ignored (documented no-op).
        break;
      }
      case 'KILL_TASK': {
        const tid = String(payload);
        const task = tasks[tid];
        if (task) {
          try { process.kill(task.pid); } catch {}
          delete tasks[tid];
          pushTaskRemoved(tid);
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
    clients.delete(ws);
    console.log(`  Client disconnected: ${clientAddr}`);
  });

  ws.on('error', () => clients.delete(ws));
});

process.on('SIGINT', () => {
  console.log('\n  Shutting down...');
  if (opencodeProc) opencodeProc.kill();
  wss.close(() => process.exit(0));
});
