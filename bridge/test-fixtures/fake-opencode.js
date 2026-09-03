#!/usr/bin/env node
// A minimal stand-in for the real `opencode` CLI's `serve` subcommand, used
// by bridge/test/stream.test.js (and future live-API-dependent tests) via
// the bridge's OPCODE env var override. Implements just enough of the real
// opencode-serve HTTP surface (confirmed shape recorded in PROGRESS.md Phase
// 0) for the bridge's runPrompt()/startEventStream() to exercise against:
// GET /config, POST /session, POST /session/:id/message,
// GET /session/:id/diff, POST /session/:id/abort, GET /event (SSE).
const http = require('http');

const args = process.argv.slice(2);
const portIdx = args.indexOf('--port');
const port = portIdx !== -1 ? parseInt(args[portIdx + 1], 10) : 4096;

let sessionCounter = 0;

// Records every inbound request this fixture receives, readable via
// GET /__requests, so tests can assert the bridge called the expected
// upstream endpoint (e.g. PERMISSION_REPLY -> POST /permission/{id}/reply)
// without needing to intercept global.fetch.
const requestLog = [];

// Canned /event stream: one text delta, a tool call (running) followed by its
// result (completed), then a permission-asked event — matches the real
// message.part.delta / message.part.updated(part.type==='tool') /
// permission.asked shapes recorded in PROGRESS.md.
const CANNED_EVENTS = [
  { type: 'message.part.delta', properties: { sessionID: 'ses_fake_1', part: { type: 'text', text: 'Hello from fake', sessionID: 'ses_fake_1' } } },
  { type: 'message.part.updated', properties: { sessionID: 'ses_fake_1', part: { type: 'tool', tool: 'bash', state: { status: 'running', input: { command: 'ls' } } } } },
  { type: 'message.part.updated', properties: { sessionID: 'ses_fake_1', part: { type: 'tool', tool: 'bash', state: { status: 'completed', input: { command: 'ls' }, output: 'file1\nfile2' } } } },
  { type: 'permission.asked', properties: { id: 'per_fake_1', sessionID: 'ses_fake_1', permission: 'bash', patterns: [], metadata: {}, always: [] } },
  { type: 'question.asked', properties: { id: 'que_fake_1', sessionID: 'ses_fake_1', text: 'Which approach?', options: ['a', 'b'] } }
];

const server = http.createServer((req, res) => {
  const url = req.url || '';

  if (req.method === 'GET' && url === '/__requests') {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify(requestLog));
    return;
  }

  // GET /event is a long-lived SSE connection — never buffer its body (it has
  // none, and req.on('end') on some Node versions doesn't fire promptly for a
  // connection the server itself intends to hold open for writing).
  if (req.method === 'GET' && url === '/event') {
    dispatch(req, res, url);
    return;
  }

  // Record every other request (method, path, and JSON body if any) before
  // dispatching.
  let rawBody = '';
  req.on('data', d => { rawBody += d; });
  req.on('end', () => {
    let body = null;
    try { body = rawBody ? JSON.parse(rawBody) : null; } catch { body = rawBody; }
    requestLog.push({ method: req.method, url, body });
    dispatch(req, res, url);
  });
});

function dispatch(req, res, url) {
  if (req.method === 'GET' && url === '/config') {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end('{}');
    return;
  }
  if (req.method === 'POST' && url === '/session') {
    sessionCounter++;
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ id: `ses_fake_${sessionCounter}` }));
    return;
  }
  if (req.method === 'POST' && /^\/session\/[^/]+\/message$/.test(url)) {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ info: { role: 'assistant', error: null }, parts: [{ type: 'text', text: 'canned reply' }] }));
    return;
  }
  if (req.method === 'GET' && /^\/session\/[^/]+\/diff$/.test(url)) {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end('[]');
    return;
  }
  if (req.method === 'POST' && /^\/session\/[^/]+\/abort$/.test(url)) {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end('{}');
    return;
  }
  if (req.method === 'GET' && url === '/event') {
    res.writeHead(200, { 'content-type': 'text/event-stream', 'cache-control': 'no-cache', connection: 'keep-alive' });
    let i = 0;
    const timer = setInterval(() => {
      if (i >= CANNED_EVENTS.length) { clearInterval(timer); return; }
      res.write(`data: ${JSON.stringify(CANNED_EVENTS[i])}\n\n`);
      i++;
    }, 150);
    req.on('close', () => clearInterval(timer));
    return;
  }
  if (req.method === 'POST' && /^\/permission\/[^/]+\/reply$/.test(url)) {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end('{}');
    return;
  }
  if (req.method === 'POST' && /^\/question\/[^/]+\/(reply|reject)$/.test(url)) {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end('{}');
    return;
  }
  res.writeHead(404, { 'content-type': 'application/json' });
  res.end('{}');
}

server.listen(port, '127.0.0.1');

process.on('SIGTERM', () => process.exit(0));
process.on('SIGINT', () => process.exit(0));
