const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { startBridge, waitForPort, connectAndAuth, nextMessageOrTimeoutSafe, stopBridge } = require('./helpers.js');

const FAKE_OPENCODE = path.join(__dirname, '..', 'test-fixtures', 'fake-opencode.js');

test('PROMPT relays the fake opencode /event SSE feed as STREAM_* frames', async () => {
  const port = 22000 + Math.floor(Math.random() * 2000);
  const ocPort = 24000 + Math.floor(Math.random() * 2000);
  const token = 'streamtesttoken';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-stream-'));
  const child = startBridge(port, token, wsRoot, {
    // main.js does `spawn(OPCODE, ['serve', '--port', OC_SERVE_PORT, ...])`.
    // Pointing OPCODE at the executable fixture (chmod +x, own shebang)
    // means it receives 'serve' as argv[0] and ignores it, reading --port.
    OPCODE: FAKE_OPENCODE,
    OC_SERVE_PORT: String(ocPort)
  });
  await waitForPort(port);

  const { ws } = await connectAndAuth(port, token);
  ws.send(JSON.stringify({ eventType: 'PROMPT', payload: 'say hi' }));

  const seen = { STREAM_TEXT_DELTA: false, STREAM_TOOL_CALL: false, STREAM_TOOL_RESULT: false };
  for (let i = 0; i < 40; i++) {
    const msg = await nextMessageOrTimeoutSafe(ws, 1000);
    if (!msg) continue;
    if (msg.eventType in seen) seen[msg.eventType] = true;
    if (seen.STREAM_TEXT_DELTA && seen.STREAM_TOOL_CALL && seen.STREAM_TOOL_RESULT) break;
  }

  assert.strictEqual(seen.STREAM_TEXT_DELTA, true, 'expected a STREAM_TEXT_DELTA frame');
  assert.strictEqual(seen.STREAM_TOOL_CALL, true, 'expected a STREAM_TOOL_CALL frame');
  assert.strictEqual(seen.STREAM_TOOL_RESULT, true, 'expected a STREAM_TOOL_RESULT frame');

  ws.close();
  await stopBridge(child);
});
