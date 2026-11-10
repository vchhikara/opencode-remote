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

async function receiveType(ws, type) {
  for (let i = 0; i < 12; i++) {
    const frame = await nextMessage(ws);
    if (frame.eventType === type) return frame;
  }
  throw new Error(`Missing expected ${type} frame`);
}

test('current-device rotation replaces only that token and preserves the live socket', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-rotation-'));
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root);
  try {
    await waitForPort(port);
    const { ws } = await authenticate(port, 'pairing-secret');
    const paired = await receiveType(ws, 'CONNECTED');
    await receiveType(ws, 'WORKSPACE_LIST');
    const oldToken = paired.payload.issuedToken;
    ws.send(JSON.stringify({ eventType: 'ROTATE_TOKEN' }));
    const rotated = await receiveType(ws, 'TOKEN_ROTATED');
    const newToken = rotated.payload.issuedToken;
    assert.match(newToken, /^[a-f0-9]{64}$/);
    assert.notEqual(newToken, oldToken);
    assert.equal(rotated.payload.deviceId, paired.payload.deviceId);
    const store = fs.readFileSync(path.join(root, '.device-tokens.json'), 'utf8');
    assert.ok(!store.includes(oldToken) && !store.includes(newToken));
    ws.send(JSON.stringify({ eventType: 'FETCH_WORKSPACES' }));
    assert.equal((await receiveType(ws, 'WORKSPACE_LIST')).eventType, 'WORKSPACE_LIST');
    const rejected = await authenticate(port, oldToken);
    assert.equal(await rejected.closed, 4001);
    const accepted = await authenticate(port, newToken);
    assert.equal((await receiveType(accepted.ws, 'CONNECTED')).eventType, 'CONNECTED');
    accepted.ws.close();
    ws.close();
  } finally {
    await stopBridge(child);
  }
});

test('only the controlling device may switch workspace until it releases the lease', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-lease-'));
  const first = path.join(root, 'first');
  const second = path.join(root, 'second');
  fs.mkdirSync(first);
  fs.mkdirSync(second);
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root, { PAIRING_MAX_DEVICES: '2' });
  try {
    await waitForPort(port);
    const clientA = await authenticate(port, 'pairing-secret');
    await receiveType(clientA.ws, 'CONNECTED');
    await receiveType(clientA.ws, 'WORKSPACE_LIST');
    const clientB = await authenticate(port, 'pairing-secret');
    await receiveType(clientB.ws, 'CONNECTED');
    await receiveType(clientB.ws, 'WORKSPACE_LIST');
    clientA.ws.send(JSON.stringify({ eventType: 'OPEN_WORKSPACE', payload: first }));
    assert.equal((await receiveType(clientA.ws, 'WORKSPACE_OPENED')).payload.path, first);
    clientB.ws.send(JSON.stringify({ eventType: 'OPEN_WORKSPACE', payload: second }));
    const blocked = await receiveType(clientB.ws, 'ERROR');
    assert.equal(blocked.payload.operation, 'OPEN_WORKSPACE');
    clientA.ws.send(JSON.stringify({ eventType: 'RELEASE_CONTROL' }));
    assert.equal((await receiveType(clientA.ws, 'CONTROL_RELEASED')).payload.deviceId != null, true);
    clientB.ws.send(JSON.stringify({ eventType: 'OPEN_WORKSPACE', payload: second }));
    assert.equal((await receiveType(clientB.ws, 'WORKSPACE_OPENED')).payload.path, second);
    clientA.ws.close();
    clientB.ws.close();
  } finally {
    await stopBridge(child);
  }
});

test('explicit runtime snapshot provides the current workspace and empty queues', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-runtime-snapshot-'));
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root);
  try {
    await waitForPort(port);
    const { ws } = await authenticate(port, 'pairing-secret');
    await receiveType(ws, 'CONNECTED');
    await receiveType(ws, 'WORKSPACE_LIST');
    ws.send(JSON.stringify({ eventType: 'FETCH_RUNTIME_STATE' }));
    const snapshot = await receiveType(ws, 'RUNTIME_STATE');
    assert.equal(snapshot.payload.workspace, root);
    assert.equal(snapshot.payload.sessionId, null);
    assert.deepEqual(snapshot.payload.tasks, []);
    assert.deepEqual(snapshot.payload.diffs, []);
    assert.deepEqual(snapshot.payload.permissions, []);
    assert.deepEqual(snapshot.payload.questions, []);
    ws.close();
  } finally {
    await stopBridge(child);
  }
});

test('non-string event types cannot terminate the bridge process', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-malformed-type-'));
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root);
  try {
    await waitForPort(port);
    const malformed = connectClient(port);
    await new Promise(resolve => malformed.once('open', resolve));
    const closed = waitForClose(malformed);
    malformed.send(JSON.stringify({ eventType: {
      toString: null, valueOf: null
    }, payload: null }));
    assert.equal(await closed, 1003);
    assert.equal(child.exitCode, null, 'bridge must remain alive');
    const good = await authenticate(port, 'pairing-secret');
    assert.equal((await nextMessage(good.ws)).eventType, 'CONNECTED');
    good.ws.close();
  } finally {
    await stopBridge(child);
  }
});
test('two-phase rotation keeps old bearer valid until persisted-token acknowledgement', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-two-phase-'));
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root);
  try {
    await waitForPort(port);
    const paired = await authenticate(port, 'pairing-secret');
    const connected = await receiveType(paired.ws, 'CONNECTED');
    const oldToken = connected.payload.issuedToken;
    await receiveType(paired.ws, 'WORKSPACE_LIST');

    paired.ws.send(JSON.stringify({ eventType: 'ROTATE_TOKEN_PREPARE' }));
    const prepared = await receiveType(paired.ws, 'TOKEN_ROTATION_PREPARED');
    const newToken = prepared.payload.issuedToken;
    assert.match(newToken, /^[a-f0-9]{64}$/);
    assert.notEqual(oldToken, newToken);

    const oldStillWorks = await authenticate(port, oldToken);
    await receiveType(oldStillWorks.ws, 'CONNECTED');
    oldStillWorks.ws.close();

    paired.ws.send(JSON.stringify({ eventType: 'ROTATE_TOKEN_ACK' }));
    assert.equal((await receiveType(paired.ws, 'TOKEN_ROTATION_COMMITTED')).payload.deviceId,
      connected.payload.deviceId);
    const oldDenied = await authenticate(port, oldToken);
    assert.equal(await oldDenied.closed, 4001);
    const newWorks = await authenticate(port, newToken);
    await receiveType(newWorks.ws, 'CONNECTED');
    newWorks.ws.close();
    paired.ws.close();
  } finally {
    await stopBridge(child);
  }
});

test('connecting with staged replacement after a process crash confirms rotation', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-rotation-recovery-'));
  const firstPort = await freePort();
  let child = startBridge(firstPort, 'pairing-secret', root);
  let oldToken, replacementToken;
  try {
    await waitForPort(firstPort);
    const paired = await authenticate(firstPort, 'pairing-secret');
    oldToken = (await receiveType(paired.ws, 'CONNECTED')).payload.issuedToken;
    await receiveType(paired.ws, 'WORKSPACE_LIST');
    paired.ws.send(JSON.stringify({ eventType: 'ROTATE_TOKEN_PREPARE' }));
    replacementToken = (await receiveType(paired.ws, 'TOKEN_ROTATION_PREPARED')).payload.issuedToken;
    paired.ws.close();
  } finally {
    await stopBridge(child);
  }
  const nextPort = await freePort();
  child = startBridge(nextPort, 'different-startup-secret', root);
  try {
    await waitForPort(nextPort);
    const recovered = await authenticate(nextPort, replacementToken);
    assert.equal((await receiveType(recovered.ws, 'CONNECTED')).eventType, 'CONNECTED');
    const obsolete = await authenticate(nextPort, oldToken);
    assert.equal(await obsolete.closed, 4001);
    recovered.ws.close();
  } finally {
    await stopBridge(child);
  }
});

test('connection cap rejects a 65th simultaneous socket', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-connection-cap-'));
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root);
  const sockets = [];
  try {
    await waitForPort(port);
    const first = await authenticate(port, 'pairing-secret');
    sockets.push(first.ws);
    const issued = (await receiveType(first.ws, 'CONNECTED')).payload.issuedToken;
    await receiveType(first.ws, 'WORKSPACE_LIST');
    for (let i = 1; i < 64; i++) {
      const next = await authenticate(port, issued);
      sockets.push(next.ws);
      await receiveType(next.ws, 'CONNECTED');
      await receiveType(next.ws, 'WORKSPACE_LIST');
    }
    const overflow = connectClient(port);
    sockets.push(overflow);
    const closed = waitForClose(overflow);
    assert.equal(await closed, 1013);
  } finally {
    for (const ws of sockets) {
      if (ws.readyState === ws.OPEN || ws.readyState === ws.CONNECTING) ws.terminate();
    }
    await stopBridge(child);
  }
});

test('inbound WebSocket frame beyond 16 MiB is rejected', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-inbound-limit-'));
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root);
  try {
    await waitForPort(port);
    const { ws } = await authenticate(port, 'pairing-secret');
    await receiveType(ws, 'CONNECTED');
    await receiveType(ws, 'WORKSPACE_LIST');
    const closed = waitForClose(ws);
    ws.send(Buffer.alloc(16 * 1024 * 1024 + 1, 0x41));
    assert.equal(await closed, 1009);
  } finally {
    await stopBridge(child);
  }
});

test('deeply nested file trees return a bounded error', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-depth-limit-'));
  let cursor = root;
  for (let i = 0; i < 26; i++) {
    cursor = path.join(cursor, 'd');
    fs.mkdirSync(cursor);
  }
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root);
  try {
    await waitForPort(port);
    const { ws } = await authenticate(port, 'pairing-secret');
    await receiveType(ws, 'CONNECTED');
    await receiveType(ws, 'WORKSPACE_LIST');
    ws.send(JSON.stringify({ eventType: 'FETCH_FILE_TREE', payload: {
      requestId: 'depth-budget'
    } }));
    const error = await receiveType(ws, 'ERROR');
    assert.equal(error.payload.operation, 'FETCH_FILE_TREE');
    assert.equal(error.payload.requestId, 'depth-budget');
    assert.match(error.payload.message, /depth limit exceeded/i);
    ws.close();
  } finally {
    await stopBridge(child);
  }
});

test('pairing persists atomically and retries after a writable-store failure', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-pairing-atomic-'));
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root);
  try {
    await waitForPort(port);
    fs.chmodSync(root, 0o500);
    const failure = await authenticate(port, 'pairing-secret');
    assert.equal(await failure.closed, 1011);
    assert.equal(fs.existsSync(path.join(root, '.device-tokens.json')), false);
    fs.chmodSync(root, 0o700);
    const retry = await authenticate(port, 'pairing-secret');
    assert.equal((await receiveType(retry.ws, 'CONNECTED')).eventType, 'CONNECTED');
    retry.ws.close();
  } finally {
    fs.chmodSync(root, 0o700);
    await stopBridge(child);
  }
});

test('failed revocation leaves old verifier usable and reports an error', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-revoke-atomic-'));
  const port = await freePort();
  const child = startBridge(port, 'pairing-secret', root);
  try {
    await waitForPort(port);
    const authenticated = await authenticate(port, 'pairing-secret');
    const connected = await receiveType(authenticated.ws, 'CONNECTED');
    await receiveType(authenticated.ws, 'WORKSPACE_LIST');
    fs.chmodSync(root, 0o500);
    authenticated.ws.send(JSON.stringify({ eventType: 'REVOKE_TOKEN',
      payload: connected.payload.deviceId }));
    const error = await receiveType(authenticated.ws, 'ERROR');
    assert.equal(error.payload.operation, 'REVOKE_TOKEN');
    fs.chmodSync(root, 0o700);
    const stillValid = await authenticate(port, connected.payload.issuedToken);
    assert.equal((await receiveType(stillValid.ws, 'CONNECTED')).eventType, 'CONNECTED');
    stillValid.ws.close();
    authenticated.ws.send(JSON.stringify({ eventType: 'REVOKE_TOKEN',
      payload: connected.payload.deviceId }));
    assert.equal((await receiveType(authenticated.ws, 'TOKEN_REVOKED')).payload.deviceId,
      connected.payload.deviceId);
    authenticated.ws.close();
    const revoked = await authenticate(port, connected.payload.issuedToken);
    assert.equal(await revoked.closed, 4001);
  } finally {
    fs.chmodSync(root, 0o700);
    await stopBridge(child);
  }
});
