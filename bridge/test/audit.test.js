const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { startBridge, waitForPort, connectAndAuth, nextMessageOrTimeoutSafe, stopBridge } = require('./helpers.js');

const FAKE_OPENCODE = path.join(__dirname, '..', 'test-fixtures', 'fake-opencode.js');

async function startWithFakeOpencode() {
  const port = 46000 + Math.floor(Math.random() * 2000);
  const ocPort = 48000 + Math.floor(Math.random() * 2000);
  const token = 'audittesttoken';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-audit-'));
  const auditLogPath = path.join(wsRoot, '.opencode-remote-audit.log');
  const child = startBridge(port, token, wsRoot, {
    OPCODE: FAKE_OPENCODE,
    OC_SERVE_PORT: String(ocPort),
    AUDIT_LOG_PATH: auditLogPath
  });
  await waitForPort(port);
  return { child, port, ocPort, token, auditLogPath };
}

function readAuditLines(auditLogPath) {
  if (!fs.existsSync(auditLogPath)) return [];
  return fs.readFileSync(auditLogPath, 'utf-8').split('\n').filter(Boolean).map(l => JSON.parse(l));
}

// Task 8.4.1 exit criterion: triggering a GIT command and a PERMISSION_REPLY
// each append one well-formed JSON line to the log file.
test('a GIT command appends one well-formed JSON line to the audit log', async () => {
  const { child, port, token, auditLogPath } = await startWithFakeOpencode();
  const { ws } = await connectAndAuth(port, token, 'audit-device');

  ws.send(JSON.stringify({ eventType: 'GIT', payload: 'status' }));
  // Give the bridge a moment to spawn git and append the log line.
  await new Promise(r => setTimeout(r, 500));

  const lines = readAuditLines(auditLogPath);
  const found = lines.find(l => l.kind === 'git_command');
  assert.ok(found, `expected a git_command audit entry, got: ${JSON.stringify(lines)}`);
  assert.strictEqual(found.detail.command, 'status');
  assert.ok(found.timestamp, 'expected a timestamp field');
  assert.strictEqual(found.deviceName, 'audit-device');
  assert.ok(found.deviceId, 'expected a deviceId field');

  ws.close();
  await stopBridge(child);
});

test('a PERMISSION_REPLY appends one well-formed JSON line to the audit log', async () => {
  const { child, port, token, auditLogPath } = await startWithFakeOpencode();
  const { ws } = await connectAndAuth(port, token, 'audit-device-2');

  ws.send(JSON.stringify({ eventType: 'PROMPT', payload: 'say hi' }));
  for (let i = 0; i < 50; i++) {
    const msg = await nextMessageOrTimeoutSafe(ws, 1000);
    if (msg && msg.eventType === 'PERMISSION_REQUEST') break;
  }

  ws.send(JSON.stringify({ eventType: 'PERMISSION_REPLY', payload: { permissionId: 'per_fake_1', decision: 'allow' } }));
  await new Promise(r => setTimeout(r, 500));

  const lines = readAuditLines(auditLogPath);
  const found = lines.find(l => l.kind === 'permission_decision');
  assert.ok(found, `expected a permission_decision audit entry, got: ${JSON.stringify(lines)}`);
  assert.strictEqual(found.detail.permissionId, 'per_fake_1');
  assert.strictEqual(found.detail.decision, 'allow');
  assert.ok(found.timestamp, 'expected a timestamp field');

  ws.close();
  await stopBridge(child);
});

test('FETCH_AUDIT_LOG returns the recorded entries', async () => {
  const { child, port, token } = await startWithFakeOpencode();
  const { ws } = await connectAndAuth(port, token, 'audit-device-3');

  ws.send(JSON.stringify({ eventType: 'GIT', payload: 'status' }));
  await new Promise(r => setTimeout(r, 500));

  ws.send(JSON.stringify({ eventType: 'FETCH_AUDIT_LOG', payload: null }));
  let auditLogMsg = null;
  for (let i = 0; i < 20 && !auditLogMsg; i++) {
    const msg = await nextMessageOrTimeoutSafe(ws, 1000);
    if (msg && msg.eventType === 'AUDIT_LOG') auditLogMsg = msg;
  }
  assert.ok(auditLogMsg, 'expected an AUDIT_LOG frame');
  assert.ok(Array.isArray(auditLogMsg.payload));
  assert.ok(auditLogMsg.payload.some(e => e.kind === 'git_command'), `expected a git_command entry in FETCH_AUDIT_LOG response, got: ${JSON.stringify(auditLogMsg.payload)}`);

  ws.close();
  await stopBridge(child);
});
