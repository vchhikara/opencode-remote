// Read-only access to OpenCode's own session storage, independent of any
// `opencode serve` process the bridge itself may or may not have running.
// See adr/0002-global-cross-workspace-session-search.md for the design this
// implements.
//
// OpenCode persists sessions in a SQLite DB at
// ~/.local/share/opencode/opencode.db (WAL journal mode, confirmed via
// `PRAGMA journal_mode`), with a `session` table (project_id FK, title,
// time_updated, ...) joined to `project` (worktree = absolute filesystem
// path). WAL mode means a read-only connection here never blocks on, or is
// blocked by, opencode serve's own writer connection.
//
// Uses node:sqlite (built into Node 22, confirmed working against this
// project's Node version) rather than adding a new npm dependency.

const { DatabaseSync } = require('node:sqlite');
const fs = require('fs');
const path = require('path');
const os = require('os');

const DEFAULT_DB_PATH = path.join(os.homedir(), '.local', 'share', 'opencode', 'opencode.db');

let db = null;
let dbOpenError = null;
let dbPathUsed = null;

// Lazily opens (once) the read-only connection. Cached as a module-level
// singleton — this file's exported functions are called per-request, not
// once at startup, so re-opening every call would be wasteful for a local
// file read but still cheap; caching avoids it regardless. If the DB is
// missing/locked/corrupt, the error is cached too so every call surfaces the
// same clear message instead of a raw exception crossing the WS boundary.
function getDb() {
  const dbPath = process.env.OPENCODE_DB_PATH || DEFAULT_DB_PATH;
  if (db && dbPathUsed === dbPath) return db;
  if (dbOpenError && dbPathUsed === dbPath) throw dbOpenError;

  dbPathUsed = dbPath;
  try {
    db = new DatabaseSync(dbPath, { readOnly: true });
    dbOpenError = null;
    return db;
  } catch (e) {
    db = null;
    dbOpenError = new Error(`Could not open OpenCode session database at ${dbPath}: ${e.message}`);
    throw dbOpenError;
  }
}

// Test-only: forces the next getDb() call to re-evaluate OPENCODE_DB_PATH
// and re-open, instead of reusing a cached connection/error from a prior
// test's env. Not used by production code paths.
function _resetForTests() {
  db = null;
  dbOpenError = null;
  dbPathUsed = null;
}

function isReachable(worktree) {
  try {
    return fs.existsSync(worktree) && fs.statSync(worktree).isDirectory();
  } catch {
    return false;
  }
}

// Cursor encodes the last row's (time_updated, id) — plain OFFSET pagination
// would skip/repeat rows if a session is created or updated between page
// fetches, since ORDER BY time_updated DESC is not a stable total order on
// its own (ties), and rows can shift position under a live-updating table.
function encodeCursor(row) {
  return Buffer.from(JSON.stringify({ t: row.time_updated, id: row.id }), 'utf-8').toString('base64url');
}

function decodeCursor(cursor) {
  try {
    const parsed = JSON.parse(Buffer.from(cursor, 'base64url').toString('utf-8'));
    if (typeof parsed.t !== 'number' || typeof parsed.id !== 'string') return null;
    return parsed;
  } catch {
    return null;
  }
}

const DEFAULT_LIMIT = 50;
const MAX_LIMIT = 200;

/**
 * Returns { sessions, nextCursor? } — the global, cross-workspace session
 * list, most recently updated first, independent of the currently-active
 * workspace or whether an `opencode serve` process is even running.
 *
 * @param {{ limit?: number, cursor?: string }} opts
 */
function listAllSessions({ limit, cursor } = {}) {
  const database = getDb();
  const effectiveLimit = Math.min(Math.max(1, limit || DEFAULT_LIMIT), MAX_LIMIT);

  let where = '';
  const params = [];
  if (cursor) {
    const decoded = decodeCursor(cursor);
    if (decoded) {
      // Strict "further down the same DESC(time_updated, id) order" predicate.
      where = 'WHERE (s.time_updated < ?) OR (s.time_updated = ? AND s.id < ?)';
      params.push(decoded.t, decoded.t, decoded.id);
    }
    // An undecodable cursor is treated as "start from the top" rather than
    // an error — a stale/corrupt cursor shouldn't hard-fail the list.
  }

  const stmt = database.prepare(`
    SELECT s.id, s.title, p.worktree, s.time_updated
    FROM session s JOIN project p ON s.project_id = p.id
    ${where}
    ORDER BY s.time_updated DESC, s.id DESC
    LIMIT ?
  `);
  const rows = stmt.all(...params, effectiveLimit + 1);

  const hasMore = rows.length > effectiveLimit;
  const page = hasMore ? rows.slice(0, effectiveLimit) : rows;

  const sessionsOut = page.map(row => ({
    id: row.id,
    title: row.title,
    worktree: row.worktree,
    updatedAt: row.time_updated,
    reachable: isReachable(row.worktree)
  }));

  const result = { sessions: sessionsOut };
  if (hasMore) result.nextCursor = encodeCursor(page[page.length - 1]);
  return result;
}

module.exports = { listAllSessions, _resetForTests, DEFAULT_DB_PATH };
