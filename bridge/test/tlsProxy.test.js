const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const https = require('node:https');
const net = require('node:net');
const { execFileSync } = require('node:child_process');
const WebSocket = require('ws');
const { startBridge, waitForPort, stopBridge } = require('./helpers');

async function freePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const port = server.address().port;
      server.close(() => resolve(port));
    });
  });
}

test('WSS reverse proxy accepts trusted TLS and rejects untrusted and wrong-host certificates', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-wss-integration-'));
  const keyFile = path.join(root, 'test.key');
  const certFile = path.join(root, 'test.crt');
  execFileSync('openssl', [
    'req', '-x509', '-nodes', '-newkey', 'rsa:2048',
    '-keyout', keyFile, '-out', certFile, '-days', '1',
    '-subj', '/CN=localhost',
    '-addext', 'subjectAltName=DNS:localhost'
  ], { stdio: 'ignore' });
  const bridgePort = await freePort();
  const child = startBridge(bridgePort, 'synthetic-wss-pairing', root);
  let tlsProxy;
  try {
    await waitForPort(bridgePort);
    tlsProxy = https.createServer({
      key: fs.readFileSync(keyFile), cert: fs.readFileSync(certFile)
    });
    tlsProxy.on('upgrade', (request, socket, head) => {
      const upstream = net.connect(bridgePort, '127.0.0.1');
      upstream.once('connect', () => {
        upstream.write(`GET ${request.url} HTTP/1.1\r\n` +
          request.rawHeaders.reduce((all, value, i, arr) =>
            i % 2 === 0 ? all + `${value}: ${arr[i + 1]}\r\n` : all, '') + '\r\n');
        if (head.length) upstream.write(head);
        socket.pipe(upstream);
        upstream.pipe(socket);
      });
      upstream.on('error', () => socket.destroy());
      socket.on('error', () => upstream.destroy());
    });
    await new Promise(resolve => tlsProxy.listen(0, resolve));
    const port = tlsProxy.address().port;
    const cert = fs.readFileSync(certFile);

    const bad = new WebSocket(`wss://localhost:${port}/ws`, { rejectUnauthorized: true });
    const badFailure = await new Promise(resolve => bad.once('error', resolve));
    assert.match(badFailure.message, /self.signed/i);

    const wrongHost = new WebSocket(`wss://127.0.0.1:${port}/ws`, { ca: cert });
    const hostFailure = await new Promise(resolve => wrongHost.once('error', resolve));
    assert.match(hostFailure.message, /(hostname|IP: 127\.0\.0\.1)/i);

    const trusted = new WebSocket(`wss://localhost:${port}/ws`, { ca: cert });
    await new Promise((resolve, reject) => {
      trusted.once('open', resolve);
      trusted.once('error', reject);
    });
    trusted.send(JSON.stringify({ eventType: 'CONNECT', payload: {
      token: 'synthetic-wss-pairing', deviceName: 'test'
    } }));
    const result = await new Promise((resolve, reject) => {
      trusted.once('message', bytes => resolve(JSON.parse(bytes.toString())));
      trusted.once('error', reject);
    });
    assert.equal(result.eventType, 'CONNECTED');
    trusted.close();
  } finally {
    if (tlsProxy) await new Promise(resolve => tlsProxy.close(resolve));
    await stopBridge(child);
    fs.rmSync(root, { recursive: true, force: true });
  }
});
