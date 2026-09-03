# Security & Hardening Audit — OpenCode Remote Control System

- **Date:** 2026-08-25 · **HEAD:** 1a0f7f3 · **Method:** static analysis, full reads of both servers + Android network/auth layer
- **Threat model:** attacker = any device on the same LAN (shared office, café Wi-Fi). The bridge hands out shell execution, filesystem reads, workspace switching, and git operations.

## Executive summary

**Overall risk: CRITICAL.** `bridge/main.js` performs authenticated-to-nobody remote code execution: any LAN peer who can reach port 8080 gets arbitrary command execution (`cmd /c <attacker string>`), arbitrary absolute-path file reads, and full workspace switching — no token ever checked. The Android client compounds this with cleartext transport, a fake pairing flow that injects a hardcoded dummy API key, and plaintext credential storage with backup enabled. The secondary Express server uses `Math.random()` pairing codes and wide-open CORS.

**Counts:** Critical 4 · High 4 · Medium 5 · Low/Info 3

## Findings table

| # | Severity | Component | Finding | OWASP |
|---|---|---|---|---|
| S1 | Critical | bridge/main.js | No authentication on WebSocket — every handler executes for any connected peer; CONNECT token never validated | A07:2021 |
| S2 | Critical | bridge/main.js:298 | Arbitrary command execution via string interpolation into shell: ``exec(`cmd /c ${command}`)`` (also Windows-only) | A03:2021 |
| S3 | Critical | bridge/main.js:255-256 | Arbitrary file read: absolute paths accepted verbatim in FETCH_FILE (`path.isAbsolute(fp) ? fp : …`) | A01:2021 |
| S4 | Critical | Android client | Cleartext `http/ws` for code, terminal and credentials over LAN; manifest permits nothing stricter | A02:2021 |
| S5 | High | AuthScreen.kt:262-263 | Fake QR scan auto-connects with hardcoded `"key=dummy_api_key_123"`, silently replacing real keys in memory | A07 |
| S6 | High | DataStoreManager.kt + Manifest:10 | API key persisted plain-text; `android:allowBackup="true"` exports it via adb backup/cloud restore | A02 |
| S7 | High | bridge/main.js:322 | Git operations interpolated into shell (`exec(`git ${gitCmd}`)`) — injection via crafted git args | A03 |
| S8 | High | opencode-remote/server.js:11,34 | `app.use(cors())` wide open; pairing codes from `Math.random()` (predictable); sessionId = `session_${Date.now()}` (guessable) | A07/A05 |
| S9 | Medium | bridge/main.js:220 | Session tokens from `Math.random().toString(36)` ≈41 bits — predictable if enforcement is ever added | A02 |
| S10 | Medium | OPEN_WORKSPACE main.js:233-236 | Any directory on the laptop can be set active — pivots all file/terminal/git access anywhere on disk | A01 |
| S11 | Medium | UploadManager.kt | Filename from `uri.lastPathSegment` interpolated into ContentDisposition unescaped (CRLF/quote header injection); `readBytes()` stream leak | A03 |
| S12 | Medium | NsdHelper | Trusts first `_opencode._tcp` responder — trivial service spoofing redirects app to attacker host | A08 |
| S13 | Medium | KtorClient.kt | API key attached by OkHttp interceptor to every request incl. cleartext; no scheme/host pinning | A02 |
| S14 | Low | RSM.kt:92,97,212-218 | Verbose `Log.d` of full frames — chat content/commands reachable via logcat on debug builds | A09 |
| S15 | Low | MainActivity exported=true | Expected launcher activity; fine — noted after review, no finding | — |
| S16 | Info | repo hygiene | Example-env template file only (dot-prefixed); no secrets tracked in git history (baseline commit scanned); keystore absent ✓ | A05 |

## Detailed findings (top items)

### S1 — Unauthenticated command channel (the headline)
```js
// main.js:209-226
wss.on('connection', (ws, req) => {
  clients.add(ws);                       // anyone
  ws.on('message', data => {
    const cmd = data.toString().trim();
    if (cmd.startsWith('CONNECT:')) {    // issues token…
      const token = Math.random().toString(36).substring(2, 10);
      sessions[token] = { name, workspace: activeWorkspace };
```
…yet **no subsequent handler consults `sessions` or any token**. `TERMINAL:`/`GIT:`/`FETCH_FILE:` execute regardless. Combined with S2 this is LAN-wide RCE against the developer's laptop. (Mitigating context: the client currently cannot even connect due to protocol mismatch/cleartext — fixing those without fixing auth would weaponize the app.)

### S2/S7 — Shell interpolation
```js
exec(`cmd /c ${command}`, { cwd: activeWorkspace, timeout: 30000 }, …)   // :298
exec(`git ${gitCmd}`,   { cwd: activeWorkspace, timeout: 30000 }, …)   // :322
```
The terminal endpoint is *supposed* to run commands, but it must be bound to an authenticated session and, ideally, a constrained PTY. The git path should be arg-array based (`spawn('git', args)`) with an allowlist of subcommands.
Contrast (done right): the OpenCode prompt runner already uses `spawn(OPCODE, ['run', …])` with an args array (main.js:106).

### S3 — Arbitrary file read
```js
const fp = cmd.substring(11);
const fullPath = path.isAbsolute(fp) ? fp : path.join(activeWorkspace, fp);  // :256
fs.readFileSync(fullPath, 'utf-8')
```
Reads sensitive host files — SSH keys, credential stores, browser cookies — anything the laptop user can read. Fix: resolve + reject anything outside `activeWorkspace` (`path.relative` prefix check), deny symlinks escaping root.

### S5/S6 — Client-side credential handling
Pairing theater injects `dummy_api_key_123`; the key lives in one in-memory var, is "persisted" nowhere (dead `saveApiKey`) across two parallel DataStore files, and the manifest allows backup. Post-restart reconnect happens with no credential at all.

## What's done right
- Prompt execution via arg-array `spawn` (main.js:106)
- Timeouts on every exec/spawn call
- Tree walk filters dotfiles/node_modules (main.js:31)
- Pairing-code concept (expiry + single-use intent) exists in express server
- No secrets committed; env-example pattern used
- `.gitignore` now excludes keystores and env-style files (this cleanup)

## Remediation plan

**P0 — do before any further connectivity work**
1. Gate the socket: require per-message session token issued at CONNECT; drop+close on miss:
   ```js
   ws.on('message', data => {
     const msg = JSON.parse(data);
     if (!sessions[msg.t]) { ws.close(4001, 'unauthorized'); return; }
   ```
   (aligns with migrating to the JSON envelope anyway — see docs audit D1)
2. Replace exec-interpolation: `spawn('bash', ['-lc', command])`/PTY lib behind auth; git → `spawn('git', allowedArgs)` allowlist.
3. Confine FETCH_FILE/OPEN_WORKSPACE to `activeWorkspace` via resolved-prefix check.

**P1**
4. Real QR decode (ML Kit) — delete the dummy-scan timer; persist keys via EncryptedDataStore (open task 3.3), set `allowBackup=false`.
5. TLS story: even self-signed pinned cert beats cleartext; document tradeoff while LAN-only.
6. Express server: randomBytes tokens, default-deny CORS, or retire the project entirely (recommended — two backends is drift bait).

**P2**
7. NSD advertisement includes fingerprint field the client verifies (kills S12).
8. Redact frame bodies from logs on release builds (S14).
