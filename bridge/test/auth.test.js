const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { startBridge, waitForPort, connectClient, nextMessage, waitForClose, stopBridge } = require('./helpers.js');

test('bridge auth gate', async (t) => {
  const port = 20000 + Math.floor(Math.random() * 2000);
  const token = 'testtoken123';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-auth-'));
  const child = startBridge(port, token, wsRoot);
  await waitForPort(port);

  await t.test('a command sent before CONNECT is rejected with close code 4001', async () => {
    const ws = connectClient(port);
    await new Promise(r => ws.on('open', r));
    ws.send(JSON.stringify({ eventType: 'FETCH_WORKSPACES', payload: null }));
    const code = await waitForClose(ws);
    assert.strictEqual(code, 4001);
  });

  await t.test('CONNECT with the wrong token is rejected with close code 4001', async () => {
    const ws = connectClient(port);
    await new Promise(r => ws.on('open', r));
    ws.send(JSON.stringify({ eventType: 'CONNECT', payload: { deviceName: 'phone', token: 'wrongtoken' } }));
    const code = await waitForClose(ws);
    assert.strictEqual(code, 4001);
  });

  let ws;
  await t.test('CONNECT with the correct token yields CONNECTED then WORKSPACE_LIST, and stays open', async () => {
    ws = connectClient(port);
    await new Promise(r => ws.on('open', r));
    ws.send(JSON.stringify({ eventType: 'CONNECT', payload: { deviceName: 'phone', token } }));
    const msg1 = await nextMessage(ws);
    assert.strictEqual(msg1.eventType, 'CONNECTED');
    const msg2 = await nextMessage(ws);
    assert.strictEqual(msg2.eventType, 'WORKSPACE_LIST');
    assert.strictEqual(ws.readyState, WebSocketOpenState(ws));
  });

  await t.test('after a valid CONNECT, a normal command is not rejected', async () => {
    ws.send(JSON.stringify({ eventType: 'FETCH_WORKSPACES', payload: null }));
    const msg = await nextMessage(ws);
    assert.strictEqual(msg.eventType, 'WORKSPACE_LIST');
  });

  ws.close();
  await stopBridge(child);
});

function WebSocketOpenState(ws) {
  return ws.OPEN !== undefined ? ws.OPEN : 1;
}

// Task 8.1: per-device tokens. The pairing secret (BRIDGE_TOKEN) is no
// longer checked against forever — the first CONNECT using it gets issued a
// distinct per-device token, and every subsequent CONNECT authenticates
// with that device's own token.
test('CONNECT with the pairing token issues a distinct per-device token', async () => {
  const port = 44000 + Math.floor(Math.random() * 2000);
  const pairingToken = 'pairingsecret1';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-auth8-'));
  const child = startBridge(port, pairingToken, wsRoot);
  try {
    await waitForPort(port);
    const ws = connectClient(port);
    try {
      await new Promise(r => ws.on('open', r));
      ws.send(JSON.stringify({ eventType: 'CONNECT', payload: { deviceName: 'phone-a', token: pairingToken } }));
      const connected = await nextMessage(ws);
      assert.strictEqual(connected.eventType, 'CONNECTED');
      assert.ok(connected.payload.issuedToken, 'expected an issuedToken in the CONNECTED payload');
      assert.notStrictEqual(connected.payload.issuedToken, pairingToken, 'the issued device token must differ from the pairing secret');
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }
});

test('two devices pairing with the same secret get different tokens and both authenticate', async () => {
  const port = 44000 + Math.floor(Math.random() * 2000);
  const pairingToken = 'pairingsecret2';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-auth8b-'));
  const child = startBridge(port, pairingToken, wsRoot);
  try {
    await waitForPort(port);

    const wsA = connectClient(port);
    const wsB = connectClient(port);
    try {
      await new Promise(r => wsA.on('open', r));
      await new Promise(r => wsB.on('open', r));
      wsA.send(JSON.stringify({ eventType: 'CONNECT', payload: { deviceName: 'device-a', token: pairingToken } }));
      wsB.send(JSON.stringify({ eventType: 'CONNECT', payload: { deviceName: 'device-b', token: pairingToken } }));
      const connectedA = await nextMessage(wsA);
      const connectedB = await nextMessage(wsB);
      const tokenA = connectedA.payload.issuedToken;
      const tokenB = connectedB.payload.issuedToken;
      assert.ok(tokenA && tokenB, 'both devices should get issued tokens');
      assert.notStrictEqual(tokenA, tokenB, 'each device must get a distinct token');

      // Both devices reconnect with their own issued token and succeed.
      const wsA2 = connectClient(port);
      await new Promise(r => wsA2.on('open', r));
      wsA2.send(JSON.stringify({ eventType: 'CONNECT', payload: { deviceName: 'device-a', token: tokenA } }));
      const reconnectedA = await nextMessage(wsA2);
      assert.strictEqual(reconnectedA.eventType, 'CONNECTED');
      wsA2.close();

      const wsB2 = connectClient(port);
      await new Promise(r => wsB2.on('open', r));
      wsB2.send(JSON.stringify({ eventType: 'CONNECT', payload: { deviceName: 'device-b', token: tokenB } }));
      const reconnectedB = await nextMessage(wsB2);
      assert.strictEqual(reconnectedB.eventType, 'CONNECTED');
      wsB2.close();
    } finally {
      wsA.close();
      wsB.close();
    }
  } finally {
    await stopBridge(child);
  }
});

test('REVOKE_TOKEN closes the revoked device and rejects it on reconnect (Task 8.2.1)', async () => {
  const port = 44000 + Math.floor(Math.random() * 2000);
  const pairingToken = 'pairingsecret3';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-auth8c-'));
  const child = startBridge(port, pairingToken, wsRoot);
  try {
    await waitForPort(port);

    // Device to be revoked.
    const wsVictim = connectClient(port);
    await new Promise(r => wsVictim.on('open', r));
    wsVictim.send(JSON.stringify({ eventType: 'CONNECT', payload: { deviceName: 'victim', token: pairingToken } }));
    const connectedVictim = await nextMessage(wsVictim);
    const victimToken = connectedVictim.payload.issuedToken;
    const victimDeviceId = connectedVictim.payload.deviceId;
    assert.ok(victimToken && victimDeviceId);
    await nextMessage(wsVictim); // WORKSPACE_LIST

    // Admin device that issues the revoke.
    const wsAdmin = connectClient(port);
    await new Promise(r => wsAdmin.on('open', r));
    wsAdmin.send(JSON.stringify({ eventType: 'CONNECT', payload: { deviceName: 'admin', token: pairingToken } }));
    await nextMessage(wsAdmin); // CONNECTED
    await nextMessage(wsAdmin); // WORKSPACE_LIST

    const victimClosed = waitForClose(wsVictim);
    wsAdmin.send(JSON.stringify({ eventType: 'REVOKE_TOKEN', payload: victimDeviceId }));
    const revokedCode = await victimClosed;
    assert.strictEqual(revokedCode, 4001, 'expected the revoked device\'s socket to be closed with 4001');

    // A subsequent connect with the same (now-revoked) token must fail.
    const wsRetry = connectClient(port);
    await new Promise(r => wsRetry.on('open', r));
    wsRetry.send(JSON.stringify({ eventType: 'CONNECT', payload: { deviceName: 'victim', token: victimToken } }));
    const retryCode = await waitForClose(wsRetry);
    assert.strictEqual(retryCode, 4001, 'expected the revoked token to be rejected on reconnect');

    wsAdmin.close();
  } finally {
    await stopBridge(child);
  }
});
