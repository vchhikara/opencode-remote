const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { startBridge, waitForPort, connectAndAuth, nextMessage, stopBridge } = require('./helpers.js');

test('FETCH_FILE reads a nested file inside the workspace', async () => {
  const port = 20000 + Math.floor(Math.random() * 2000);
  const token = 'testtoken123';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-path1-'));
  fs.mkdirSync(path.join(wsRoot, 'sub'));
  fs.writeFileSync(path.join(wsRoot, 'sub', 'nested.txt'), 'nested content\n');

  const child = startBridge(port, token, wsRoot);
  await waitForPort(port);
  const { ws } = await connectAndAuth(port, token);

  ws.send(JSON.stringify({ eventType: 'FETCH_FILE', payload: 'sub/nested.txt' }));
  const msg = await nextMessage(ws);
  assert.strictEqual(msg.eventType, 'FILE_CONTENT');
  assert.ok(String(msg.payload).includes('nested content'), `expected file content, got: ${JSON.stringify(msg.payload)}`);

  ws.close();
  await stopBridge(child);
});

test('FETCH_FILE rejects a relative path-traversal escape', async () => {
  const port = 20000 + Math.floor(Math.random() * 2000);
  const token = 'testtoken123';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-path2-'));

  const child = startBridge(port, token, wsRoot);
  await waitForPort(port);
  const { ws } = await connectAndAuth(port, token);

  ws.send(JSON.stringify({ eventType: 'FETCH_FILE', payload: '../../../../etc/passwd' }));
  const msg = await nextMessage(ws);
  assert.notStrictEqual(msg.eventType, 'FILE_CONTENT');
  if (msg.eventType === 'FILE_CONTENT') {
    assert.ok(!String(msg.payload).includes('root:'), 'traversal payload returned /etc/passwd content');
  }

  ws.close();
  await stopBridge(child);
});

test('FETCH_FILE rejects an absolute path outside the workspace', async () => {
  const port = 20000 + Math.floor(Math.random() * 2000);
  const token = 'testtoken123';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-path3-'));

  const child = startBridge(port, token, wsRoot);
  await waitForPort(port);
  const { ws } = await connectAndAuth(port, token);

  ws.send(JSON.stringify({ eventType: 'FETCH_FILE', payload: '/etc/passwd' }));
  const msg = await nextMessage(ws);
  if (msg.eventType === 'FILE_CONTENT') {
    assert.ok(!String(msg.payload).includes('root:'), 'absolute path payload returned /etc/passwd content');
  }

  ws.close();
  await stopBridge(child);
});

test('OPEN_WORKSPACE rejects a path outside the confined root', async () => {
  const port = 20000 + Math.floor(Math.random() * 2000);
  const token = 'testtoken123';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-path4-'));

  const child = startBridge(port, token, wsRoot);
  await waitForPort(port);
  const { ws } = await connectAndAuth(port, token);

  ws.send(JSON.stringify({ eventType: 'OPEN_WORKSPACE', payload: '/etc' }));
  const msg = await nextMessage(ws);
  // Whatever the exact rejection envelope, it must not be a success that
  // switches the active workspace to /etc.
  assert.notStrictEqual(msg.eventType, 'WORKSPACE_OPENED');

  ws.close();
  await stopBridge(child);
});
