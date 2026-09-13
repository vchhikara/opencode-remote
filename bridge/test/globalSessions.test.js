const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { DatabaseSync } = require('node:sqlite');
const { startBridge, waitForPort, connectAndAuth, nextMessageOrTimeoutSafe, stopBridge } = require('./helpers.js');

const FAKE_OPENCODE = path.join(__dirname, '..', 'test-fixtures', 'fake-opencode.js');

// Builds a throwaway opencode.db-shaped SQLite file with the given
// project/session rows, for FETCH_ALL_SESSIONS to read via
// OPENCODE_DB_PATH — mirrors the fixture builder in sessionStore.test.js,
// but lives here too since these are separate test processes (the bridge
// runs as its own child process reading its own OPENCODE_DB_PATH env var).
function makeFixtureDb(dbPath, { projects = [], sessions = [] } = {}) {
  const db = new DatabaseSync(dbPath);
  db.exec(`
    CREATE TABLE project (
      id text PRIMARY KEY, worktree text NOT NULL,
      time_created integer NOT NULL, time_updated integer NOT NULL, sandboxes text NOT NULL
    );
    CREATE TABLE session (
      id text PRIMARY KEY, project_id text NOT NULL, title text NOT NULL,
      time_created integer NOT NULL, time_updated integer NOT NULL,
      FOREIGN KEY (project_id) REFERENCES project(id)
    );
  `);
  const insertProject = db.prepare('INSERT INTO project (id, worktree, time_created, time_updated, sandboxes) VALUES (?, ?, ?, ?, ?)');
  for (const p of projects) insertProject.run(p.id, p.worktree, p.time || 1, p.time || 1, '[]');
  const insertSession = db.prepare('INSERT INTO session (id, project_id, title, time_created, time_updated) VALUES (?, ?, ?, ?, ?)');
  for (const s of sessions) insertSession.run(s.id, s.projectId, s.title, s.time, s.time);
  db.close();
}

async function startWithFixtures(dbFixture, wsRootOverride) {
  const port = 34000 + Math.floor(Math.random() * 2000);
  const ocPort = 36000 + Math.floor(Math.random() * 2000);
  const token = 'globalsessiontesttoken';
  const wsRoot = wsRootOverride || fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-globalsess-'));
  const dbDir = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-globalsess-db-'));
  const dbPath = path.join(dbDir, 'opencode.db');
  makeFixtureDb(dbPath, dbFixture);
  const child = startBridge(port, token, wsRoot, {
    OPCODE: FAKE_OPENCODE,
    OC_SERVE_PORT: String(ocPort),
    OPENCODE_DB_PATH: dbPath
  });
  await waitForPort(port);
  return { child, port, ocPort, token, wsRoot };
}

test('FETCH_ALL_SESSIONS returns sessions across multiple projects, most recent first', async () => {
  const otherWorkspace = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-globalsess-other-'));
  const { child, port, token } = await startWithFixtures({
    projects: [
      { id: 'proj_active', worktree: process.cwd() }, // arbitrary reachable dir not used for opening
      { id: 'proj_other', worktree: otherWorkspace }
    ],
    sessions: [
      { id: 'ses_old', projectId: 'proj_active', title: 'Old one', time: 100 },
      { id: 'ses_new', projectId: 'proj_other', title: 'New one', time: 200 }
    ]
  });
  try {
    const { ws } = await connectAndAuth(port, token);
    try {
      ws.send(JSON.stringify({ eventType: 'FETCH_ALL_SESSIONS', payload: {} }));
      let list = null;
      for (let i = 0; i < 20 && !list; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'ALL_SESSIONS_LIST') list = msg.payload;
      }
      assert.ok(list, 'expected an ALL_SESSIONS_LIST frame');
      assert.deepStrictEqual(list.sessions.map(s => s.id), ['ses_new', 'ses_old'], 'expected most-recently-updated first');
      const newRow = list.sessions.find(s => s.id === 'ses_new');
      assert.strictEqual(newRow.worktree, otherWorkspace);
      assert.strictEqual(newRow.reachable, true);
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }
});

test('FETCH_ALL_SESSIONS marks a session whose worktree no longer exists as unreachable, not hidden', async () => {
  const { child, port, token } = await startWithFixtures({
    projects: [{ id: 'proj_gone', worktree: '/definitely/does/not/exist/anywhere/at/all' }],
    sessions: [{ id: 'ses_gone', projectId: 'proj_gone', title: 'Orphaned', time: 100 }]
  });
  try {
    const { ws } = await connectAndAuth(port, token);
    try {
      ws.send(JSON.stringify({ eventType: 'FETCH_ALL_SESSIONS', payload: {} }));
      let list = null;
      for (let i = 0; i < 20 && !list; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'ALL_SESSIONS_LIST') list = msg.payload;
      }
      assert.ok(list, 'expected an ALL_SESSIONS_LIST frame');
      assert.strictEqual(list.sessions.length, 1);
      assert.strictEqual(list.sessions[0].id, 'ses_gone');
      assert.strictEqual(list.sessions[0].reachable, false, 'a missing worktree must not be hidden, only marked unreachable');
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }
});

test('FETCH_ALL_SESSIONS surfaces an ERROR frame (not a crash) when the DB is unreadable', async () => {
  const port = 34000 + Math.floor(Math.random() * 2000);
  const token = 'globalsessiontesttoken2';
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-globalsess-baddb-'));
  const child = startBridge(port, token, wsRoot, {
    OPCODE: FAKE_OPENCODE,
    OPENCODE_DB_PATH: path.join(wsRoot, 'does-not-exist.db')
  });
  try {
    await waitForPort(port);
    const { ws } = await connectAndAuth(port, token);
    try {
      ws.send(JSON.stringify({ eventType: 'FETCH_ALL_SESSIONS', payload: {} }));
      let err = null;
      for (let i = 0; i < 20 && !err; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'ERROR') err = msg.payload;
      }
      assert.ok(err, 'expected an ERROR frame');
      assert.match(err.message, /FETCH_ALL_SESSIONS failed/);
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }
});

test('OPEN_SESSION_GLOBAL switches activeWorkspace and the opencode session together', async () => {
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-globalsess-root2-'));
  const targetWorkspace = path.join(wsRoot, 'target-project');
  fs.mkdirSync(targetWorkspace);
  const port = 34000 + Math.floor(Math.random() * 2000);
  const ocPort = 36000 + Math.floor(Math.random() * 2000);
  const token = 'globalsessiontesttoken3';
  const dbDir = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-globalsess-db-'));
  const dbPath = path.join(dbDir, 'opencode.db');
  makeFixtureDb(dbPath, { projects: [{ id: 'proj_target', worktree: targetWorkspace }], sessions: [] });
  // OPEN_SESSION_GLOBAL restarts opencode-serve bound to the target
  // workspace (see main.js), which kills and respawns this test's fake
  // opencode subprocess. The real opencode serve reloads session state from
  // its own disk storage across such a restart; this fixture only does so
  // when told to via FAKE_OPENCODE_STATE_FILE — required here specifically
  // because this test spans a restart, unlike the others in this file.
  const stateFile = path.join(dbDir, 'fake-opencode-state.json');
  const child = startBridge(port, token, wsRoot, {
    OPCODE: FAKE_OPENCODE,
    OC_SERVE_PORT: String(ocPort),
    OPENCODE_DB_PATH: dbPath,
    FAKE_OPENCODE_STATE_FILE: stateFile
  });
  await waitForPort(port);
  try {
    const { ws } = await connectAndAuth(port, token);
    try {
      // Get a real session id known to the fake opencode server (starts it lazily).
      ws.send(JSON.stringify({ eventType: 'LIST_SESSIONS' }));
      for (let i = 0; i < 20; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'SESSION_LIST') break;
      }
      const createRes = await fetch(`http://127.0.0.1:${ocPort}/session`, { method: 'POST' });
      const created = await createRes.json();

      ws.send(JSON.stringify({ eventType: 'OPEN_SESSION_GLOBAL', payload: { id: created.id, worktree: targetWorkspace } }));
      let opened = null;
      let workspaceList = null;
      for (let i = 0; i < 20 && !(opened && workspaceList); i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'SESSION_OPENED') opened = msg.payload;
        if (msg && msg.eventType === 'WORKSPACE_LIST') workspaceList = msg.payload;
      }
      assert.ok(opened, 'expected a SESSION_OPENED frame');
      assert.strictEqual(opened.id, created.id);
      assert.strictEqual(opened.worktree, targetWorkspace);
      assert.ok(workspaceList.some(w => w.path === targetWorkspace), 'expected the new workspace to be registered');

      // Prompting now should hit the newly-opened session.
      ws.send(JSON.stringify({ eventType: 'PROMPT', payload: 'say hi' }));
      for (let i = 0; i < 20; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'CHAT_MESSAGE') break;
      }
      const reqRes = await fetch(`http://127.0.0.1:${ocPort}/__requests`);
      const requests = await reqRes.json();
      const found = requests.find(r => r.method === 'POST' && r.url === `/session/${created.id}/message`);
      assert.ok(found, `expected a message POST to the opened session, got: ${JSON.stringify(requests)}`);
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }
});

test('OPEN_SESSION_GLOBAL rejects a worktree outside the confined workspace root', async () => {
  const { child, port, token } = await startWithFixtures({ projects: [], sessions: [] });
  try {
    const { ws } = await connectAndAuth(port, token);
    try {
      ws.send(JSON.stringify({ eventType: 'OPEN_SESSION_GLOBAL', payload: { id: 'ses_whatever', worktree: '/etc' } }));
      let err = null;
      for (let i = 0; i < 20 && !err; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'ERROR') err = msg.payload;
      }
      assert.ok(err, 'expected an ERROR frame');
      assert.match(err.message, /outside allowed root/);
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }
});

test('OPEN_SESSION_GLOBAL rejects a worktree that does not exist', async () => {
  const wsRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-globalsess-root-'));
  const { child, port, token } = await startWithFixtures({ projects: [], sessions: [] }, wsRoot);
  try {
    const { ws } = await connectAndAuth(port, token);
    try {
      const missing = path.join(wsRoot, 'nonexistent-subdir');
      ws.send(JSON.stringify({ eventType: 'OPEN_SESSION_GLOBAL', payload: { id: 'ses_whatever', worktree: missing } }));
      let err = null;
      for (let i = 0; i < 20 && !err; i++) {
        const msg = await nextMessageOrTimeoutSafe(ws, 1000);
        if (msg && msg.eventType === 'ERROR') err = msg.payload;
      }
      assert.ok(err, 'expected an ERROR frame');
      assert.match(err.message, /not found/);
    } finally {
      ws.close();
    }
  } finally {
    await stopBridge(child);
  }
});
