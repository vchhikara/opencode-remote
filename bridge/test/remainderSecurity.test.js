const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const net = require('node:net');
const { startBridge, waitForPort, connectClient, nextMessage, waitForClose, stopBridge } = require('./helpers');

async function freePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.on('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const port = server.address().port;
      server.close(() => resolve(port));
    });
  });
}

async function authenticate(port, token) {
  const ws = connectClient(port);
  await new Promise((resolve, reject) => {
    ws.once('open', resolve);
    ws.once('error', reject);
  });
  const closed = waitForClose(ws);
  ws.send(JSON.stringify({ eventType: 'CONNECT', payload: { token, deviceName: 'qa' } }));
  return { ws, closed };
}

test('repeated invalid authentication is rate-limited per socket peer', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-auth-ratelimit-'));
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root);
  try {
    await waitForPort(port);
    for (let i = 0; i < 10; i++) {
      const attempt = await authenticate(port, 'invalid-token');
      assert.equal(await attempt.closed, i === 9 ? 4008 : 4001);
    }
    const blocked = connectClient(port);
    const closed = waitForClose(blocked);
    assert.equal(await closed, 4008);
  } finally {
    await stopBridge(child);
  }
});

test('oversized files are rejected without sending their content', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-file-budget-'));
  fs.writeFileSync(path.join(root, 'large.bin'), Buffer.alloc(8 * 1024 * 1024 + 1));
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root);
  try {
    await waitForPort(port);
    const { ws } = await authenticate(port, 'pairing-secret');
    assert.equal((await nextMessage(ws)).eventType, 'CONNECTED');
    await nextMessage(ws); // WORKSPACE_LIST
    ws.send(JSON.stringify({ eventType: 'FETCH_FILE', payload: { path: 'large.bin', requestId: 'too-large' } }));
    const error = await nextMessage(ws);
    assert.equal(error.eventType, 'ERROR');
    assert.equal(error.payload.operation, 'FETCH_FILE');
    assert.equal(error.payload.requestId, 'too-large');
    assert.match(error.payload.message, /8 MiB/);
    ws.close();
  } finally {
    await stopBridge(child);
  }
});
