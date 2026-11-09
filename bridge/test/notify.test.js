const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { startBridge, waitForPort, connectClient, connectAndAuth, nextMessageOrTimeoutSafe, waitForClose, stopBridge } = require('./helpers.js');

const FAKE_OPENCODE = path.join(__dirname, '..', 'test-fixtures', 'fake-opencode.js');

// Task 7.1.1: the bridge's outbound notification hook (notifyExternal) must
// fire for at least the error path, broadcasting a NOTIFY frame — the one
// seam a real FCM send would plug into. Full push delivery to a device is
// integration-only and out of scope for this test (recorded in PROGRESS.md).
test('a failed PROMPT triggers the notifyExternal error hook (NOTIFY frame)', async () => {
  const port = 40000 + Math.floor(Math.random() * 2000);
  const ocPort = 42000 + Math.floor(Math.random() * 2000);
  const token = 'notifytesttoken';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-notify-'));
  const child = startBridge(port, token, wsRoot, { OPCODE: FAKE_OPENCODE, OC_SERVE_PORT: String(ocPort) });
  try {
    await waitForPort(port);
    const { ws } = await connectAndAuth(port, token);
    try {
      ws.send(JSON.stringify({ eventType: 'PROMPT', payload: 'TRIGGER_ERROR' }));

      let notify = null;
      for (let i = 0; i < 20 && !notify; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'NOTIFY' && msg.payload.kind === 'error') notify = msg.payload;
      }
      assert.ok(notify, 'expected a NOTIFY frame with kind "error"');
      assert.ok(notify.detail && typeof notify.detail.message === 'string', `expected an error message in the NOTIFY detail, got: ${JSON.stringify(notify)}`);
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }
});

test('a successful PROMPT triggers the notifyExternal task_completed hook (NOTIFY frame)', async () => {
  const port = 40000 + Math.floor(Math.random() * 2000);
  const ocPort = 42000 + Math.floor(Math.random() * 2000);
  const token = 'notifytesttoken2';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-notify2-'));
  const child = startBridge(port, token, wsRoot, { OPCODE: FAKE_OPENCODE, OC_SERVE_PORT: String(ocPort) });
  try {
    await waitForPort(port);
    const { ws } = await connectAndAuth(port, token);
    try {
      ws.send(JSON.stringify({ eventType: 'PROMPT', payload: 'say hi' }));

      let notify = null;
      for (let i = 0; i < 20 && !notify; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'NOTIFY' && msg.payload.kind === 'task_completed') notify = msg.payload;
      }
      assert.ok(notify, 'expected a NOTIFY frame with kind "task_completed"');
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }
});

test('a disconnected paired device receives bounded missed notifications on reconnect', async () => {
  const port = 40000 + Math.floor(Math.random() * 2000);
  const ocPort = 42000 + Math.floor(Math.random() * 2000);
  const token = 'notification-replay-secret';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-notify-reconnect-'));
  const child = startBridge(port, token, wsRoot, {
    OPCODE: FAKE_OPENCODE, OC_SERVE_PORT: String(ocPort), PAIRING_MAX_DEVICES: '2'
  });
  try {
    await waitForPort(port);
    const active = await connectAndAuth(port, token, 'active');
    const offline = await connectAndAuth(port, token, 'offline');
    const deviceToken = offline.connected.payload.issuedToken;
    assert.ok(deviceToken);
    const offlineClosed = waitForClose(offline.ws);
    offline.ws.close();
    await offlineClosed;

    active.ws.send(JSON.stringify({ eventType: 'PROMPT', payload: 'TRIGGER_ERROR' }));
    let finished = false;
    for (let i = 0; i < 30; i++) {
      const msg = await nextMessageOrTimeoutSafe(active.ws, 1000);
      if (msg?.eventType === 'NOTIFY' && msg.payload.kind === 'task_completed') {
        finished = true;
        break;
      }
    }
    assert.ok(finished, 'active client should observe finished task');

    const reconnected = connectClient(port);
    await new Promise(resolve => reconnected.once('open', resolve));
    reconnected.send(JSON.stringify({ eventType: 'CONNECT', payload: {
      deviceName: 'offline', token: deviceToken
    } }));
    let kinds = [];
    for (let i = 0; i < 12 && kinds.length < 2; i++) {
      const msg = await nextMessageOrTimeoutSafe(reconnected, 1000);
      if (msg?.eventType === 'NOTIFY') kinds.push(msg.payload.kind);
    }
    assert.deepEqual(kinds, ['error', 'task_completed']);
    active.ws.close();
    reconnected.close();
  } finally {
    await stopBridge(child);
  }
});
