const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { startBridge, waitForPort, connectAndAuth, nextMessage, stopBridge } = require('./helpers.js');

// Covers the C2/C3 DTO-shape migration: FileNodeDto must carry
// {name, path, isDirectory, children?} — not the old {name, isFolder}.
test('FETCH_FILE_TREE returns the FileNodeDto shape with threaded paths', async () => {
  const port = 20000 + Math.floor(Math.random() * 2000);
  const token = 'testtoken123';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-tree-'));
  fs.mkdirSync(path.join(wsRoot, 'sub'));
  fs.writeFileSync(path.join(wsRoot, 'sub', 'nested.txt'), 'x');
  fs.writeFileSync(path.join(wsRoot, 'top.txt'), 'y');

  const child = startBridge(port, token, wsRoot);
  await waitForPort(port);
  const { ws } = await connectAndAuth(port, token);

  ws.send(JSON.stringify({ eventType: 'FETCH_FILE_TREE', payload: null }));
  const msg = await nextMessage(ws);
  assert.strictEqual(msg.eventType, 'FILE_TREE');

  const root = msg.payload;
  assert.ok('name' in root, 'root node missing "name"');
  assert.ok('path' in root, 'root node missing "path"');
  assert.ok('isDirectory' in root, 'root node missing "isDirectory"');
  assert.ok(!('isFolder' in root), 'root node still carries the old "isFolder" field');

  const sub = root.children.find(c => c.name === 'sub');
  assert.ok(sub, 'expected a "sub" child node');
  assert.strictEqual(sub.isDirectory, true);
  assert.strictEqual(sub.path, 'sub');

  const nested = sub.children.find(c => c.name === 'nested.txt');
  assert.ok(nested, 'expected "nested.txt" inside sub/');
  assert.strictEqual(nested.isDirectory, false);
  assert.strictEqual(nested.path, 'sub/nested.txt', 'nested file path was not threaded correctly');

  ws.close();
  await stopBridge(child);
});
