const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { startBridge, waitForPort, connectAndAuth, nextMessageOrTimeoutSafe, stopBridge } = require('./helpers.js');

const FAKE_OPENCODE = path.join(__dirname, '..', 'test-fixtures', 'fake-opencode.js');

async function startWithFakeOpencode() {
  const port = 34000 + Math.floor(Math.random() * 2000);
  const ocPort = 36000 + Math.floor(Math.random() * 2000);
  const token = 'testtoken123';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-term-'));
  const child = startBridge(port, token, wsRoot, { OPCODE: FAKE_OPENCODE, OC_SERVE_PORT: String(ocPort) });
  await waitForPort(port);
  return { child, port, ocPort, token };
}

test('TERMINAL command creates a PTY and streams output back incrementally', async () => {
  const { child, port, ocPort, token } = await startWithFakeOpencode();
  try {
    const { ws } = await connectAndAuth(port, token);
    try {
      ws.send(JSON.stringify({ eventType: 'TERMINAL', payload: 'echo bridgetest' }));

      // The fake PTY's WS transport sends two canned output chunks on a
      // delay (plus a NUL-prefixed control frame the bridge must filter
      // out). Collect every TERMINAL_OUTPUT frame the bridge relays and
      // confirm several arrived as separate frames — i.e. it isn't
      // buffering everything into one frame at the end (Task 4.1.1).
      const outputs = [];
      for (let i = 0; i < 20 && outputs.length < 3; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'TERMINAL_OUTPUT') outputs.push(msg.payload);
      }
      assert.ok(outputs.length >= 3, `expected multiple incremental TERMINAL_OUTPUT frames, got: ${JSON.stringify(outputs)}`);
      assert.ok(outputs[0].startsWith('> '), 'expected the first frame to echo the command line');
      assert.ok(outputs.includes('chunk1\r\n') && outputs.includes('chunk2\r\n'), `expected both canned chunks to arrive as separate frames, got: ${JSON.stringify(outputs)}`);
      assert.ok(!outputs.some(o => o.includes('cursor')), 'the NUL-prefixed control frame must not be relayed as terminal output');

      const reqRes = await fetch(`http://127.0.0.1:${ocPort}/__requests`);
      const requests = await reqRes.json();
      const created = requests.find(r => r.method === 'POST' && r.url === '/pty');
      assert.ok(created, `expected the bridge to create a PTY via POST /pty, got: ${JSON.stringify(requests)}`);
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }
});

test('TERMINAL_RESIZE calls the PTY resize endpoint for the active PTY', async () => {
  const { child, port, ocPort, token } = await startWithFakeOpencode();
  try {
    const { ws } = await connectAndAuth(port, token);
    try {
      ws.send(JSON.stringify({ eventType: 'TERMINAL', payload: 'echo hi' }));
      // Drain until the PTY's own output starts arriving (not just the
      // bridge's synchronous "> echo hi" echo line) so the PTY create
      // round-trip has definitely completed and activePtyId is set.
      let sawRealOutput = false;
      for (let i = 0; i < 20 && !sawRealOutput; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'TERMINAL_OUTPUT' && msg.payload.startsWith('chunk')) sawRealOutput = true;
      }
      assert.ok(sawRealOutput, 'expected PTY output to arrive before sending TERMINAL_RESIZE');

      ws.send(JSON.stringify({ eventType: 'TERMINAL_RESIZE', payload: { cols: 100, rows: 40 } }));
      // Give the async resize call a moment to land.
      await new Promise(r => setTimeout(r, 500));

      const reqRes = await fetch(`http://127.0.0.1:${ocPort}/__requests`);
      const requests = await reqRes.json();
      const resized = requests.find(r => r.method === 'PUT' && /^\/pty\//.test(r.url) && r.body && r.body.size && r.body.size.cols === 100 && r.body.size.rows === 40);
      assert.ok(resized, `expected a PUT /pty/{id} resize call, got: ${JSON.stringify(requests)}`);
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }
});
