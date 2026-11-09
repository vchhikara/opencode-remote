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

async function handshake(port, token) {
  const ws = connectClient(port);
  await new Promise((resolve, reject) => { ws.once('open', resolve); ws.once('error', reject); });
  const closed = waitForClose(ws);
  ws.send(JSON.stringify({ eventType: 'CONNECT', payload: { token, deviceName: 'test' } }));
  return { ws, closed };
}

test('non-issued inherited-property tokens and malformed values are rejected', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-token-reg-'));
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root);
  try {
    await waitForPort(port);
    for (const token of ['constructor', 'toString', '__proto__', 'hasOwnProperty', null, {}, [], 42]) {
      const { closed } = await handshake(port, token);
      assert.equal(await closed, 4001, `unexpectedly accepted ${JSON.stringify(token)}`);
    }
    const { ws } = await handshake(port, 'pairing-secret');
    const connected = await nextMessage(ws);
    assert.equal(connected.eventType, 'CONNECTED');
    assert.match(connected.payload.issuedToken, /^[a-f0-9]{64}$/);
    ws.close();
    const store = path.join(root, '.device-tokens.json');
    assert.equal(fs.statSync(store).mode & 0o777, 0o600, 'device store must be private');
    const stored = fs.readFileSync(store, 'utf8');
    assert.ok(!stored.includes(connected.payload.issuedToken), 'bearer token must never be persisted');
    assert.match(stored, /sha256:[a-f0-9]{64}/, 'store must contain hashed verifiers');
  } finally { await stopBridge(child); }
});

test('invalid persisted token records never authenticate, valid records survive restart', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-store-reg-'));
  const token = 'a'.repeat(64);
  const validToken = 'b'.repeat(64);
  const store = path.join(root, 'devices.json');
  const valid = { deviceId: '4a2298e1-a0f7-448f-a232-c4ca303d8105', deviceName: 'existing', issuedAt: Date.now() };
  fs.writeFileSync(store, JSON.stringify({ [token]: { deviceId: null }, [validToken]: valid }));
  fs.chmodSync(store, 0o644);
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root, { DEVICE_STORE_PATH: store });
  try {
    await waitForPort(port);
    const invalid = await handshake(port, token);
    assert.equal(await invalid.closed, 4001);
    const existing = await handshake(port, validToken);
    assert.equal((await nextMessage(existing.ws)).eventType, 'CONNECTED');
    existing.ws.close();
    assert.equal(fs.statSync(store).mode & 0o777, 0o600, 'startup must fix permissions');
  } finally { await stopBridge(child); }
});

test('pairing secret is single-use by default and obeys expiration', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-pair-reg-'));
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root);
  try {
    await waitForPort(port);
    const first = await handshake(port, 'pairing-secret');
    const msg = await nextMessage(first.ws);
    assert.equal(msg.eventType, 'CONNECTED');
    const issued = msg.payload.issuedToken;
    const reuse = await handshake(port, 'pairing-secret');
    assert.equal(await reuse.closed, 4001, 'spent secret must not issue another token');
    const reconnect = await handshake(port, issued);
    assert.equal((await nextMessage(reconnect.ws)).eventType, 'CONNECTED');
    first.ws.close(); reconnect.ws.close();
  } finally { await stopBridge(child); }

  const expiredRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-pair-expired-'));
  const expiredPort = await freePort();
  const expiredChild = startBridge(expiredPort, 'pairing-secret', expiredRoot, { PAIRING_WINDOW_MS: '0' });
  try {
    await waitForPort(expiredPort);
    const expired = await handshake(expiredPort, 'pairing-secret');
    assert.equal(await expired.closed, 4001);
  } finally { await stopBridge(expiredChild); }
});

test('realpath confinement rejects symlinks escaping the workspace', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-symlink-root-'));
  const outside = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-symlink-outside-'));
  fs.writeFileSync(path.join(outside, 'secret.txt'), 'synthetic secret');
  fs.symlinkSync(outside, path.join(root, 'escape'));
  fs.mkdirSync(path.join(root, 'nested'));
  fs.symlinkSync(path.join(root, 'escape'), path.join(root, 'nested', 'nestedlink'));
  fs.writeFileSync(path.join(root, 'safe.txt'), 'safe text');
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root);
  try {
    await waitForPort(port);
    const { ws } = await handshake(port, 'pairing-secret');
    assert.equal((await nextMessage(ws)).eventType, 'CONNECTED');
    await nextMessage(ws); // WORKSPACE_LIST
    for (const requested of ['escape/secret.txt', 'nested/nestedlink/secret.txt']) {
      ws.send(JSON.stringify({ eventType: 'FETCH_FILE', payload: requested }));
      assert.equal((await nextMessage(ws)).eventType, 'ERROR', requested);
    }
    ws.send(JSON.stringify({ eventType: 'OPEN_WORKSPACE', payload: path.join(root, 'escape') }));
    assert.equal((await nextMessage(ws)).eventType, 'ERROR');
    ws.send(JSON.stringify({ eventType: 'FETCH_FILE', payload: 'safe.txt' }));
    assert.equal((await nextMessage(ws)).eventType, 'FILE_CONTENT');
    ws.close();
  } finally { await stopBridge(child); }
});

test('rejecting a diff review does not modify file bytes', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-diff-dismiss-'));
  fs.writeFileSync(path.join(root, 'safe.txt'), 'original unchanged bytes');
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root);
  try {
    await waitForPort(port);
    const { ws } = await handshake(port, 'pairing-secret');
    await nextMessage(ws);
    await nextMessage(ws);
    ws.send(JSON.stringify({ eventType: 'REJECT_DIFF', payload: 'safe.txt' }));
    ws.send(JSON.stringify({ eventType: 'FETCH_FILE', payload: 'safe.txt' }));
    const response = await nextMessage(ws);
    assert.equal(response.eventType, 'FILE_CONTENT');
    assert.equal(response.payload, 'original unchanged bytes');
    assert.equal(fs.readFileSync(path.join(root, 'safe.txt'), 'utf8'), 'original unchanged bytes');
    ws.close();
  } finally { await stopBridge(child); }
});

test('malformed non-object frames are rejected before authorization', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-frame-reg-'));
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root);
  try {
    await waitForPort(port);
    const ws = connectClient(port);
    await new Promise(resolve => ws.once('open', resolve));
    const close = waitForClose(ws);
    ws.send('null');
    assert.equal(await close, 1003);
    const valid = await handshake(port, 'pairing-secret');
    assert.equal((await nextMessage(valid.ws)).eventType, 'CONNECTED', 'server should remain alive');
    valid.ws.close();
  } finally { await stopBridge(child); }
});

test('correlated FETCH_FILE preserves file request IDs and legacy wire compatibility', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-file-correlation-'));
  fs.writeFileSync(path.join(root, 'a.txt'), 'A');
  fs.writeFileSync(path.join(root, 'b.txt'), 'B');
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root);
  try {
    await waitForPort(port);
    const { ws } = await handshake(port, 'pairing-secret');
    await nextMessage(ws);
    await nextMessage(ws);
    ws.send(JSON.stringify({eventType:'FETCH_FILE', payload:{path:'a.txt', requestId:'request-A'}}));
    ws.send(JSON.stringify({eventType:'FETCH_FILE', payload:{path:'b.txt', requestId:'request-B'}}));
    const first = await nextMessage(ws);
    const second = await nextMessage(ws);
    assert.equal(first.eventType, 'FILE_CONTENT');
    assert.deepEqual(first.payload, {requestId:'request-A',path:'a.txt',content:'A'});
    assert.equal(second.eventType, 'FILE_CONTENT');
    assert.deepEqual(second.payload, {requestId:'request-B',path:'b.txt',content:'B'});
    ws.send(JSON.stringify({eventType:'FETCH_FILE', payload:{path:'missing.txt',requestId:'request-missing'}}));
    const error = await nextMessage(ws);
    assert.equal(error.eventType,'ERROR');
    assert.equal(error.payload.requestId,'request-missing');
    assert.equal(error.payload.operation,'FETCH_FILE');
    ws.send(JSON.stringify({eventType:'FETCH_FILE',payload:'a.txt'}));
    const legacy = await nextMessage(ws);
    assert.equal(legacy.eventType,'FILE_CONTENT');
    assert.equal(legacy.payload,'A');
    ws.close();
  } finally { await stopBridge(child); }
});
