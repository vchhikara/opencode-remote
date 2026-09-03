const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { startBridge, waitForPort, connectAndAuth, nextMessageOrTimeoutSafe, stopBridge } = require('./helpers.js');

test('ADD_WORKSPACE registers a directory and FETCH_WORKSPACES returns multiple entries', async () => {
  const port = 38000 + Math.floor(Math.random() * 2000);
  const token = 'wstesttoken';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-ws-'));
  const secondDir = path.join(wsRoot, 'second-project');
  fs.mkdirSync(secondDir);

  const child = startBridge(port, token, wsRoot);
  try {
    await waitForPort(port);
    const { ws } = await connectAndAuth(port, token);
    try {
      ws.send(JSON.stringify({ eventType: 'ADD_WORKSPACE', payload: secondDir }));

      let list = null;
      for (let i = 0; i < 20 && !list; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'WORKSPACE_LIST') list = msg.payload;
      }
      assert.ok(Array.isArray(list), 'expected a WORKSPACE_LIST array');
      assert.ok(list.length >= 2, `expected at least 2 workspaces after ADD_WORKSPACE, got: ${JSON.stringify(list)}`);
      assert.ok(list.some(w => w.path === secondDir), `expected the added workspace in the list, got: ${JSON.stringify(list)}`);

      // FETCH_WORKSPACES afterwards reflects the same registered list.
      ws.send(JSON.stringify({ eventType: 'FETCH_WORKSPACES' }));
      let fetched = null;
      for (let i = 0; i < 20 && !fetched; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'WORKSPACE_LIST') fetched = msg.payload;
      }
      assert.ok(fetched.length >= 2, `expected FETCH_WORKSPACES to still show 2+ entries, got: ${JSON.stringify(fetched)}`);
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }
});

test('ADD_WORKSPACE rejects a directory outside the confined workspace root', async () => {
  const port = 38000 + Math.floor(Math.random() * 2000);
  const token = 'wstesttoken2';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-ws2-'));

  const child = startBridge(port, token, wsRoot);
  try {
    await waitForPort(port);
    const { ws } = await connectAndAuth(port, token);
    try {
      ws.send(JSON.stringify({ eventType: 'ADD_WORKSPACE', payload: '/etc' }));
      const msg = await nextMessageOrTimeoutSafe(ws, 2000);
      assert.ok(msg && msg.eventType === 'ERROR', `expected an ERROR frame, got: ${JSON.stringify(msg)}`);
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }
});
