// Shared test harness for bridge/test/*.test.js.
// Node's built-in test runner (`node --test`) auto-discovers *.test.js files
// in this directory; this file is a plain module they all `require(...)`.
const { spawn } = require('node:child_process');
const path = require('node:path');
const WebSocket = require('ws');

const BRIDGE_ENTRY = path.join(__dirname, '..', 'main.js');

// Starts the bridge as a child process on `port` with a known token and
// workspace root. `extraEnv` merges in additional env vars (e.g. OPCODE,
// OC_SERVE_PORT to point the bridge's opencode-server integration at a test
// fixture instead of the real CLI). Returns the child process; caller must
// kill it.
function startBridge(port, token, workspaceRoot, extraEnv = {}) {
  // `activeWorkspace` in main.js defaults to `process.cwd()` (it only moves
  // on an OPEN_WORKSPACE command), so the child's cwd must be workspaceRoot
  // itself, not just the WORKSPACE_ROOT confinement env var.
  //
  // DEVICE_STORE_PATH (Task 8.1) defaults to a fixed path under $HOME in
  // main.js, which would leak issued device tokens between test runs (and
  // pollute a real dev machine's paired-device store) — scope it to this
  // test's own workspace root unless the caller already overrode it.
  const deviceStorePath = extraEnv.DEVICE_STORE_PATH || path.join(workspaceRoot, '.device-tokens.json');
  const child = spawn(process.execPath, [BRIDGE_ENTRY, String(port)], {
    cwd: workspaceRoot,
    env: { ...process.env, BRIDGE_TOKEN: token, WORKSPACE_ROOT: workspaceRoot, PORT: String(port), DEVICE_STORE_PATH: deviceStorePath, ...extraEnv }
  });
  return child;
}

// Polls until the bridge's WebSocket port accepts a connection, or rejects
// after timeoutMs.
function waitForPort(port, timeoutMs = 4000) {
  return new Promise((resolve, reject) => {
    const start = Date.now();
    const tryConnect = () => {
      const ws = new WebSocket(`ws://127.0.0.1:${port}/ws`);
      ws.on('open', () => { ws.close(); resolve(); });
      ws.on('error', () => {
        if (Date.now() - start > timeoutMs) reject(new Error('timeout waiting for bridge'));
        else setTimeout(tryConnect, 100);
      });
    };
    tryConnect();
  });
}

// Opens a client WebSocket with a FIFO message queue so messages that arrive
// before nextMessage() is called are not lost (a plain `ws.once('message')`
// pattern drops any message that arrives while no listener is attached).
function connectClient(port) {
  const ws = new WebSocket(`ws://127.0.0.1:${port}/ws`);
  ws._queue = [];
  ws._waiters = [];
  ws.on('message', d => {
    let parsed;
    try { parsed = JSON.parse(d.toString()); } catch (e) { return; }
    if (ws._waiters.length > 0) {
      ws._waiters.shift()(parsed);
    } else {
      ws._queue.push(parsed);
    }
  });
  return ws;
}

// Resolves with the next parsed JSON message (waits forever if none arrive).
function nextMessage(ws) {
  return new Promise((resolve) => {
    if (ws._queue.length > 0) {
      resolve(ws._queue.shift());
    } else {
      ws._waiters.push(resolve);
    }
  });
}

// Resolves with the next parsed JSON message, or null after ms if none
// arrive. Removes its own waiter on timeout so a message that arrives late
// is never delivered to an abandoned caller (which would otherwise starve
// the *next* nextMessage() call in the same test file).
function nextMessageOrTimeoutSafe(ws, ms) {
  return new Promise((resolve) => {
    if (ws._queue.length > 0) {
      resolve(ws._queue.shift());
      return;
    }
    const waiter = (msg) => { clearTimeout(timer); resolve(msg); };
    ws._waiters.push(waiter);
    const timer = setTimeout(() => {
      const idx = ws._waiters.indexOf(waiter);
      if (idx >= 0) ws._waiters.splice(idx, 1);
      resolve(null);
    }, ms);
  });
}

function waitForClose(ws) {
  return new Promise((resolve) => {
    ws.on('close', (code) => resolve(code));
  });
}

// Connects, sends CONNECT with the given token, and resolves once CONNECTED
// and WORKSPACE_LIST have both been received (draining exactly those two
// frames so the caller's queue starts empty).
async function connectAndAuth(port, token, deviceName = 'test-device') {
  const ws = connectClient(port);
  await new Promise(r => ws.on('open', r));
  ws.send(JSON.stringify({ eventType: 'CONNECT', payload: { deviceName, token } }));
  const connected = await nextMessage(ws);
  const workspaceList = await nextMessage(ws);
  return { ws, connected, workspaceList };
}

async function stopBridge(child) {
  child.kill('SIGINT');
  await new Promise(resolve => {
    child.on('exit', resolve);
    setTimeout(resolve, 2000);
  });
}

module.exports = {
  startBridge, waitForPort, connectClient, nextMessage, nextMessageOrTimeoutSafe,
  waitForClose, connectAndAuth, stopBridge
};
