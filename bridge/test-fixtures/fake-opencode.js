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

// Canned /event stream: one text delta, then a tool call (running) followed
// by its result (completed) — matches the real message.part.delta /
// message.part.updated(part.type==='tool') shapes recorded in PROGRESS.md.
const CANNED_EVENTS = [
  { type: 'message.part.delta', properties: { sessionID: 'ses_fake_1', part: { type: 'text', text: 'Hello from fake', sessionID: 'ses_fake_1' } } },
  { type: 'message.part.updated', properties: { sessionID: 'ses_fake_1', part: { type: 'tool', tool: 'bash', state: { status: 'running', input: { command: 'ls' } } } } },
  { type: 'message.part.updated', properties: { sessionID: 'ses_fake_1', part: { type: 'tool', tool: 'bash', state: { status: 'completed', input: { command: 'ls' }, output: 'file1\nfile2' } } } }
];

const server = http.createServer((req, res) => {
  const url = req.url || '';
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
  res.writeHead(404, { 'content-type': 'application/json' });
  res.end('{}');
});

server.listen(port, '127.0.0.1');

process.on('SIGTERM', () => process.exit(0));
process.on('SIGINT', () => process.exit(0));
