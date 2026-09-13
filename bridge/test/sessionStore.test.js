const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { DatabaseSync } = require('node:sqlite');

// sessionStore.js reads OPENCODE_DB_PATH (falls back to the real
// ~/.local/share/opencode/opencode.db) lazily on first call and caches the
// connection/error per path — each test builds its own throwaway DB file and
// points OPENCODE_DB_PATH at it before requiring/using the module fresh, via
// _resetForTests(), so tests never touch the real on-disk DB.
const { listAllSessions, _resetForTests } = require('../sessionStore.js');

function makeTestDb(dbPath, { projects = [], sessions = [] } = {}) {
  const db = new DatabaseSync(dbPath);
  db.exec(`
    CREATE TABLE project (
      id text PRIMARY KEY,
      worktree text NOT NULL,
      time_created integer NOT NULL,
      time_updated integer NOT NULL,
      sandboxes text NOT NULL
    );
    CREATE TABLE session (
      id text PRIMARY KEY,
      project_id text NOT NULL,
      title text NOT NULL,
      time_created integer NOT NULL,
      time_updated integer NOT NULL,
      FOREIGN KEY (project_id) REFERENCES project(id)
    );
  `);
  const insertProject = db.prepare('INSERT INTO project (id, worktree, time_created, time_updated, sandboxes) VALUES (?, ?, ?, ?, ?)');
  for (const p of projects) insertProject.run(p.id, p.worktree, p.time || 1, p.time || 1, '[]');
  const insertSession = db.prepare('INSERT INTO session (id, project_id, title, time_created, time_updated) VALUES (?, ?, ?, ?, ?)');
  for (const s of sessions) insertSession.run(s.id, s.projectId, s.title, s.time, s.time);
  db.close();
}

function withTestDb(fixture, fn) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'sessionstore-test-'));
  const dbPath = path.join(dir, 'opencode.db');
  makeTestDb(dbPath, fixture);
  const prevEnv = process.env.OPENCODE_DB_PATH;
  process.env.OPENCODE_DB_PATH = dbPath;
  _resetForTests();
  try {
    return fn();
  } finally {
    process.env.OPENCODE_DB_PATH = prevEnv;
    _resetForTests();
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

test('listAllSessions returns an empty list against an empty DB', () => {
  withTestDb({}, () => {
    const result = listAllSessions({});
    assert.deepStrictEqual(result.sessions, []);
    assert.strictEqual(result.nextCursor, undefined);
  });
});

test('listAllSessions joins sessions to their project worktree across multiple projects, most recent first', () => {
  const reachableDir = fs.mkdtempSync(path.join(os.tmpdir(), 'sessionstore-reachable-'));
  withTestDb({
    projects: [
      { id: 'proj_a', worktree: reachableDir },
      { id: 'proj_b', worktree: '/definitely/does/not/exist/anywhere' }
    ],
    sessions: [
      { id: 'ses_1', projectId: 'proj_a', title: 'First', time: 100 },
      { id: 'ses_2', projectId: 'proj_b', title: 'Second', time: 300 },
      { id: 'ses_3', projectId: 'proj_a', title: 'Third', time: 200 }
    ]
  }, () => {
    const result = listAllSessions({});
    assert.deepStrictEqual(result.sessions.map(s => s.id), ['ses_2', 'ses_3', 'ses_1']);

    const first = result.sessions.find(s => s.id === 'ses_1');
    assert.strictEqual(first.worktree, reachableDir);
    assert.strictEqual(first.reachable, true, 'existing directory should be reachable');

    const second = result.sessions.find(s => s.id === 'ses_2');
    assert.strictEqual(second.reachable, false, 'nonexistent directory should not be reachable');
  });
  fs.rmSync(reachableDir, { recursive: true, force: true });
});

test('listAllSessions paginates: exact-limit page has no nextCursor, one more row does', () => {
  const sessions = Array.from({ length: 5 }, (_, i) => ({
    id: `ses_${i}`, projectId: 'proj_a', title: `S${i}`, time: 100 + i
  }));
  withTestDb({ projects: [{ id: 'proj_a', worktree: '/tmp/nope' }], sessions }, () => {
    const exactPage = listAllSessions({ limit: 5 });
    assert.strictEqual(exactPage.sessions.length, 5);
    assert.strictEqual(exactPage.nextCursor, undefined, 'a page matching the full row count should not claim more exist');

    const shortPage = listAllSessions({ limit: 3 });
    assert.strictEqual(shortPage.sessions.length, 3);
    assert.ok(shortPage.nextCursor, 'a page smaller than the row count should offer a cursor');

    const secondPage = listAllSessions({ limit: 3, cursor: shortPage.nextCursor });
    assert.strictEqual(secondPage.sessions.length, 2);
    assert.strictEqual(secondPage.nextCursor, undefined);

    const allIds = [...shortPage.sessions, ...secondPage.sessions].map(s => s.id);
    assert.strictEqual(new Set(allIds).size, 5, 'pagination must not skip or repeat rows');
  });
});

test('listAllSessions treats an undecodable cursor as "start from the top" rather than erroring', () => {
  withTestDb({
    projects: [{ id: 'proj_a', worktree: '/tmp/nope' }],
    sessions: [{ id: 'ses_1', projectId: 'proj_a', title: 'Only', time: 100 }]
  }, () => {
    const result = listAllSessions({ cursor: 'not-a-valid-cursor!!!' });
    assert.deepStrictEqual(result.sessions.map(s => s.id), ['ses_1']);
  });
});

test('listAllSessions surfaces a clear error when the DB file does not exist, without throwing a raw driver exception', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'sessionstore-missing-'));
  const prevEnv = process.env.OPENCODE_DB_PATH;
  process.env.OPENCODE_DB_PATH = path.join(dir, 'does-not-exist.db');
  _resetForTests();
  try {
    assert.throws(() => listAllSessions({}), /Could not open OpenCode session database/);
  } finally {
    process.env.OPENCODE_DB_PATH = prevEnv;
    _resetForTests();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
