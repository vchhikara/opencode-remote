const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { startBridge, waitForPort, connectAndAuth, nextMessageOrTimeoutSafe, stopBridge } = require('./helpers.js');

test('TERMINAL command executes and streams output back', async () => {
  const port = 20000 + Math.floor(Math.random() * 2000);
  const token = 'testtoken123';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-term-'));
  const child = startBridge(port, token, wsRoot);
  await waitForPort(port);

  const { ws } = await connectAndAuth(port, token);
  ws.send(JSON.stringify({ eventType: 'TERMINAL', payload: 'echo bridgetest' }));

  // Drain messages until we see the exact echoed output line (not the
  // "> echo bridgetest" prompt-echo line, which also contains the substring).
  let found = false;
  for (let i = 0; i < 20; i++) {
    const msg = await nextMessageOrTimeoutSafe(ws, 3000);
    if (!msg) break;
    if (msg.eventType === 'TERMINAL_OUTPUT' && String(msg.payload).trim() === 'bridgetest') {
      found = true;
      break;
    }
  }
  assert.strictEqual(found, true, 'expected a TERMINAL_OUTPUT message with payload "bridgetest"');

  ws.close();
  await stopBridge(child);
});
