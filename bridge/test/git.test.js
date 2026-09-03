const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { execSync } = require('node:child_process');
const { startBridge, waitForPort, connectAndAuth, nextMessageOrTimeoutSafe, stopBridge } = require('./helpers.js');

test('GIT command rejects shell-injection payloads', async () => {
  const port = 20000 + Math.floor(Math.random() * 2000);
  const token = 'testtoken123';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-git-'));
  execSync('git init -q', { cwd: wsRoot });
  execSync('git config user.email a@a.com && git config user.name a', { cwd: wsRoot });
  fs.writeFileSync(path.join(wsRoot, 'f.txt'), 'hello\n');
  execSync('git add . && git commit -q -m init', { cwd: wsRoot });

  const child = startBridge(port, token, wsRoot);
  await waitForPort(port);
  const { ws } = await connectAndAuth(port, token);

  const sentinel = path.join(wsRoot, 'sentinel_should_not_exist.txt');
  ws.send(JSON.stringify({ eventType: 'GIT', payload: `status; touch ${sentinel}` }));
  // Drain everything for a couple seconds; the sentinel file must never appear.
  for (let i = 0; i < 10; i++) {
    const msg = await nextMessageOrTimeoutSafe(ws, 300);
    if (!msg) break;
  }
  assert.strictEqual(fs.existsSync(sentinel), false, 'shell-injection sentinel file was created — GIT command is not sanitized');

  ws.close();
  await stopBridge(child);
});

test('GIT command rejects a disallowed subcommand (rm -rf /)', async () => {
  const port = 20000 + Math.floor(Math.random() * 2000);
  const token = 'testtoken123';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-git2-'));
  execSync('git init -q', { cwd: wsRoot });

  const child = startBridge(port, token, wsRoot);
  await waitForPort(port);
  const { ws } = await connectAndAuth(port, token);

  ws.send(JSON.stringify({ eventType: 'GIT', payload: 'rm -rf /' }));
  const msg = await nextMessageOrTimeoutSafe(ws, 3000);
  assert.ok(msg, 'expected a response message for the disallowed GIT subcommand');
  assert.strictEqual(msg.eventType, 'TERMINAL_OUTPUT');
  assert.ok(/not allowed/i.test(String(msg.payload)), `expected a "not allowed" message, got: ${JSON.stringify(msg.payload)}`);

  ws.close();
  await stopBridge(child);
});
