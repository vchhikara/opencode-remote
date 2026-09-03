const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { startBridge, waitForPort, connectAndAuth, nextMessageOrTimeoutSafe, stopBridge } = require('./helpers.js');

const FAKE_OPENCODE = path.join(__dirname, '..', 'test-fixtures', 'fake-opencode.js');

async function startWithFakeOpencode(wsRootOverride) {
  const port = 30000 + Math.floor(Math.random() * 2000);
  const ocPort = 32000 + Math.floor(Math.random() * 2000);
  const token = 'sessiontesttoken';
  const wsRoot = wsRootOverride || fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-sess-'));
  const child = startBridge(port, token, wsRoot, { OPCODE: FAKE_OPENCODE, OC_SERVE_PORT: String(ocPort) });
  await waitForPort(port);
  return { child, port, ocPort, token, wsRoot };
}

test('NEW_SESSION creates a session and switches to it', async () => {
  const { child, port, token } = await startWithFakeOpencode();
  try {
    const { ws } = await connectAndAuth(port, token);
    try {
      ws.send(JSON.stringify({ eventType: 'NEW_SESSION', payload: { title: 'my session' } }));
      let switched = null;
      for (let i = 0; i < 20 && !switched; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'SESSION_SWITCHED') switched = msg.payload;
      }
      assert.ok(switched, 'expected a SESSION_SWITCHED frame');
      assert.ok(switched.id.startsWith('ses_fake_'), `unexpected session id: ${switched.id}`);
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }
});

test('LIST_SESSIONS returns the sessions known to opencode serve', async () => {
  const { child, port, token } = await startWithFakeOpencode();
  try {
    const { ws } = await connectAndAuth(port, token);
    try {
      // Create one session first so the list isn't empty.
      ws.send(JSON.stringify({ eventType: 'NEW_SESSION', payload: {} }));
      for (let i = 0; i < 20; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'SESSION_SWITCHED') break;
      }

      ws.send(JSON.stringify({ eventType: 'LIST_SESSIONS' }));
      let list = null;
      for (let i = 0; i < 20 && !list; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'SESSION_LIST') list = msg.payload;
      }
      assert.ok(Array.isArray(list), 'expected a SESSION_LIST array');
      assert.ok(list.length >= 1, 'expected at least one session in the list');
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }
});

test('SWITCH_SESSION changes the session used by the next PROMPT', async () => {
  const { child, port, ocPort, token } = await startWithFakeOpencode();
  try {
    const { ws } = await connectAndAuth(port, token);
    try {
      // opencode serve is started lazily by the bridge on its first
      // opencode-related command — LIST_SESSIONS now triggers that too. Do
      // that first so the fake server on ocPort actually exists before we
      // hit it directly below.
      ws.send(JSON.stringify({ eventType: 'LIST_SESSIONS' }));
      for (let i = 0; i < 20; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'SESSION_LIST') break;
      }

      // Create a second session via the fake server directly, then switch to it.
      const createRes = await fetch(`http://127.0.0.1:${ocPort}/session`, { method: 'POST' });
      const created = await createRes.json();

      ws.send(JSON.stringify({ eventType: 'SWITCH_SESSION', payload: created.id }));
      let switched = null;
      for (let i = 0; i < 20 && !switched; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'SESSION_SWITCHED') switched = msg.payload;
      }
      assert.ok(switched, 'expected a SESSION_SWITCHED frame');
      assert.strictEqual(switched.id, created.id);

      // Prompting now should hit .../session/<created.id>/message.
      ws.send(JSON.stringify({ eventType: 'PROMPT', payload: 'say hi' }));
      for (let i = 0; i < 20; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'CHAT_MESSAGE') break;
      }
      const reqRes = await fetch(`http://127.0.0.1:${ocPort}/__requests`);
      const requests = await reqRes.json();
      const found = requests.find(r => r.method === 'POST' && r.url === `/session/${created.id}/message`);
      assert.ok(found, `expected a message POST to the switched session, got: ${JSON.stringify(requests)}`);
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }
});

test('FORK_SESSION creates a forked session and switches to it (Task 3.4 stretch)', async () => {
  const { child, port, token } = await startWithFakeOpencode();
  try {
    const { ws } = await connectAndAuth(port, token);
    try {
      ws.send(JSON.stringify({ eventType: 'NEW_SESSION', payload: {} }));
      let original = null;
      for (let i = 0; i < 20 && !original; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'SESSION_SWITCHED') original = msg.payload;
      }
      assert.ok(original, 'expected the original NEW_SESSION to switch first');

      ws.send(JSON.stringify({ eventType: 'FORK_SESSION', payload: original.id }));
      let forked = null;
      for (let i = 0; i < 20 && !forked; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'SESSION_SWITCHED') forked = msg.payload;
      }
      assert.ok(forked, 'expected a SESSION_SWITCHED frame after FORK_SESSION');
      assert.notStrictEqual(forked.id, original.id, 'forked session should have a different id');
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }
});

test('session id persists across a bridge restart in the same workspace', async () => {
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-sess-persist-'));
  const { child, port, token, ocPort } = await startWithFakeOpencode(wsRoot);
  let firstState;
  try {
    const { ws } = await connectAndAuth(port, token);
    try {
      ws.send(JSON.stringify({ eventType: 'PROMPT', payload: 'say hi' }));
      for (let i = 0; i < 20; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'CHAT_MESSAGE') break;
      }
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }

  const stateFile = path.join(wsRoot, '.opencode-remote-session.json');
  assert.ok(fs.existsSync(stateFile), 'expected a persisted session state file after the first PROMPT');
  firstState = JSON.parse(fs.readFileSync(stateFile, 'utf-8'));
  assert.ok(firstState.sessionId, 'expected a persisted sessionId');

  // Restart the bridge on the same workspace + same fake opencode instance
  // (same ocPort so knownSessions is preserved) and confirm it reuses the id.
  const child2 = startBridge(port, token, wsRoot, { OPCODE: FAKE_OPENCODE, OC_SERVE_PORT: String(ocPort) });
  try {
    await waitForPort(port);
    const { ws: ws2 } = await connectAndAuth(port, token);
    try {
      ws2.send(JSON.stringify({ eventType: 'PROMPT', payload: 'say hi again' }));
      for (let i = 0; i < 20; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws2, 1000);
        if (msg && msg.eventType === 'CHAT_MESSAGE') break;
      }
      const reqRes = await fetch(`http://127.0.0.1:${ocPort}/__requests`);
      const requests = await reqRes.json();
      const found = requests.find(r => r.method === 'POST' && r.url === `/session/${firstState.sessionId}/message`);
      assert.ok(found, `expected the restarted bridge to reuse session ${firstState.sessionId}, got: ${JSON.stringify(requests)}`);
    } finally {
      ws2.close();
    }
  } finally {
    await stopBridge(child2);
  }
});
