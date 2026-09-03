const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { startBridge, waitForPort, connectAndAuth, nextMessageOrTimeoutSafe, stopBridge } = require('./helpers.js');

const FAKE_OPENCODE = path.join(__dirname, '..', 'test-fixtures', 'fake-opencode.js');

async function startWithFakeOpencode() {
  const port = 26000 + Math.floor(Math.random() * 2000);
  const ocPort = 28000 + Math.floor(Math.random() * 2000);
  const token = 'permtesttoken';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-perm-'));
  const child = startBridge(port, token, wsRoot, { OPCODE: FAKE_OPENCODE, OC_SERVE_PORT: String(ocPort) });
  await waitForPort(port);
  return { child, port, ocPort, token };
}

test('PROMPT relays permission.asked/question.asked as PERMISSION_REQUEST/QUESTION_REQUEST', async () => {
  const { child, port, token } = await startWithFakeOpencode();
  const { ws } = await connectAndAuth(port, token);
  ws.send(JSON.stringify({ eventType: 'PROMPT', payload: 'say hi' }));

  const seen = { PERMISSION_REQUEST: null, QUESTION_REQUEST: null };
  for (let i = 0; i < 50; i++) {
    const msg = await nextMessageOrTimeoutSafe(ws, 1000);
    if (!msg) continue;
    if (msg.eventType === 'PERMISSION_REQUEST') seen.PERMISSION_REQUEST = msg.payload;
    if (msg.eventType === 'QUESTION_REQUEST') seen.QUESTION_REQUEST = msg.payload;
    if (seen.PERMISSION_REQUEST && seen.QUESTION_REQUEST) break;
  }

  assert.ok(seen.PERMISSION_REQUEST, 'expected a PERMISSION_REQUEST frame');
  assert.strictEqual(seen.PERMISSION_REQUEST.permissionId, 'per_fake_1');
  assert.strictEqual(seen.PERMISSION_REQUEST.sessionId, 'ses_fake_1');

  assert.ok(seen.QUESTION_REQUEST, 'expected a QUESTION_REQUEST frame');
  assert.strictEqual(seen.QUESTION_REQUEST.questionId, 'que_fake_1');
  assert.strictEqual(seen.QUESTION_REQUEST.text, 'Which approach?');

  ws.close();
  await stopBridge(child);
});

test('PERMISSION_REPLY calls POST /permission/{id}/reply on the opencode server', async () => {
  const { child, port, ocPort, token } = await startWithFakeOpencode();
  const { ws } = await connectAndAuth(port, token);
  // Trigger a PROMPT first so opencode serve is started (bridge starts it lazily).
  ws.send(JSON.stringify({ eventType: 'PROMPT', payload: 'say hi' }));
  // Drain until we've seen the permission request (proves the server + session exist).
  for (let i = 0; i < 50; i++) {
    const msg = await nextMessageOrTimeoutSafe(ws, 1000);
    if (msg && msg.eventType === 'PERMISSION_REQUEST') break;
  }

  ws.send(JSON.stringify({ eventType: 'PERMISSION_REPLY', payload: { permissionId: 'per_fake_1', decision: 'allow' } }));
  // Give the bridge's fetch call a moment to land.
  await new Promise(r => setTimeout(r, 500));

  const res = await fetch(`http://127.0.0.1:${ocPort}/__requests`);
  const requests = await res.json();
  const found = requests.find(r => r.method === 'POST' && r.url === '/permission/per_fake_1/reply');
  assert.ok(found, `expected a POST /permission/per_fake_1/reply call, got: ${JSON.stringify(requests)}`);
  assert.strictEqual(found.body.decision, 'allow');

  ws.close();
  await stopBridge(child);
});

test('QUESTION_REPLY calls POST /question/{id}/reply on the opencode server', async () => {
  const { child, port, ocPort, token } = await startWithFakeOpencode();
  const { ws } = await connectAndAuth(port, token);
  ws.send(JSON.stringify({ eventType: 'PROMPT', payload: 'say hi' }));
  for (let i = 0; i < 50; i++) {
    const msg = await nextMessageOrTimeoutSafe(ws, 1000);
    if (msg && msg.eventType === 'QUESTION_REQUEST') break;
  }

  ws.send(JSON.stringify({ eventType: 'QUESTION_REPLY', payload: { questionId: 'que_fake_1', answer: 'a' } }));
  await new Promise(r => setTimeout(r, 500));

  const res = await fetch(`http://127.0.0.1:${ocPort}/__requests`);
  const requests = await res.json();
  const found = requests.find(r => r.method === 'POST' && r.url === '/question/que_fake_1/reply');
  assert.ok(found, `expected a POST /question/que_fake_1/reply call, got: ${JSON.stringify(requests)}`);
  assert.strictEqual(found.body.answer, 'a');

  ws.close();
  await stopBridge(child);
});

test('KILL_TASK pushes a Cancelled chat message', async () => {
  const { child, port, ocPort, token } = await startWithFakeOpencode();
  const { ws } = await connectAndAuth(port, token);
  ws.send(JSON.stringify({ eventType: 'PROMPT', payload: 'say hi' }));

  // Grab the running task id from TASK_UPDATED.
  let taskId = null;
  for (let i = 0; i < 20 && !taskId; i++) {
    const msg = await nextMessageOrTimeoutSafe(ws, 1000);
    if (msg && msg.eventType === 'TASK_UPDATED') taskId = msg.payload.id;
  }
  assert.ok(taskId, 'expected a TASK_UPDATED frame with a task id');

  ws.send(JSON.stringify({ eventType: 'KILL_TASK', payload: taskId }));

  let sawCancelled = false;
  for (let i = 0; i < 20; i++) {
    const msg = await nextMessageOrTimeoutSafe(ws, 1000);
    if (!msg) continue;
    if (msg.eventType === 'CHAT_MESSAGE' && String(msg.payload.text).toLowerCase().includes('cancel')) {
      sawCancelled = true;
      break;
    }
  }
  assert.strictEqual(sawCancelled, true, 'expected a CHAT_MESSAGE mentioning "cancel" after KILL_TASK');

  ws.close();
  await stopBridge(child);
});
