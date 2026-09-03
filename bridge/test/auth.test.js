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
