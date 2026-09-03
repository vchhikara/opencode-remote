# Handoff — OpenCode Remote (session continuation)

Read this first in the new session, then read [intent.md](intent.md),
[spec.md](spec.md), [plan.md](plan.md) — those three are the actual
forward-looking plan and are already committed as source of truth. This file
is just "how we got here" and "what to do next."

## Where things stand right now

- Repo: `/home/vipul/My Projects/opencode-remote-main/` — git-initialized
  this engagement (was not a git repo before). Two commits on `main`:
  1. `8b0bd74` — applied the original `OPENCODE_REMOTE_COMPLETION_PLAN.md`
     (Phases A–F: bridge security merge, D5/D8/D9 client fixes, E1–E9, F1–F7
     cleanup). Bridge fully tested at this point (14/14 `node --test`), but
     the Android side had never actually been compiled.
  2. `329d54b` — "Fix Android build: real assembleDebug now succeeds
     (H-BUILD verified)". This is the important one — see below.
- `intent.md`, `spec.md`, `plan.md` (just added, uncommitted as of writing
  this handoff — commit them if you want them locked in) describe a
  **rebuild of the Android app from scratch**, because the user reported
  "the app is not working" after it started actually compiling. The bridge
  is explicitly treated as fixed, trusted infrastructure in that plan — do
  not touch `bridge/main.js` as part of executing it.

## The critical discovery from commit `329d54b`

The Android app had **never been compiled** before this session, despite
multiple prior "BUILD SUCCESSFUL" claims in its history. The specific chain:
`plugins {}` only applied `kotlin.compose` (the Compose *compiler* plugin),
never `org.jetbrains.kotlin.android` (the actual Kotlin/Android language
plugin) — so Gradle silently skipped compiling every `.kt` file and still
reported success, producing an APK with **zero `com.example.*` classes**
(verified by decompiling the dex output). This was caught only by actually
running `./gradlew :app:assembleDebug` on a real toolchain and then
decompiling the result to check — reading the source or the Gradle output
alone did not surface it.

**Lesson directly relevant to the rebuild:** "the build succeeded" is not
evidence the app contains any code. Always verify by decompiling/inspecting
the actual APK contents (`dexdump`/`javap` on the output, not just
Gradle's exit code) at least once per phase in `plan.md`, especially Phase 0.

Other real bugs fixed in the same commit (details in the commit message and
in `spec.md` §7):
- `android.useAndroidX=true` was missing from `gradle.properties`.
- `firebase-ai` had no pinned version (empty-version dependency) — fixed by
  bumping `firebase-bom` to a version that covers it.
- `com.composeunstyled:primitives`/`theming` were hallucinated coordinates —
  the real library is `com.composables:composeunstyled-{button,text-field,
  toggle-switch}`, and even then only 2.5.x is compatible with this
  project's `compileSdk 36`/Kotlin 2.2.20 (2.9.x needs `compileSdk 37`/AGP
  9.1+, and pulls a newer `kotlin-stdlib` than the project's compiler can
  read — worked around with a `resolutionStrategy.force` on kotlin-stdlib).
- `ActivityScreen.kt` (from this session's own earlier E4 rewrite) was
  missing `import androidx.compose.runtime.getValue`, needed for `by
  ...collectAsStateWithLifecycle()`.
- `gradle/wrapper/` (jar + properties) didn't exist in the repo at all —
  `./gradlew` couldn't bootstrap without a system Gradle install. Now
  committed.

Toolchain now available and verified working in this environment:
- JDK 17 at `/usr/lib/jvm/java-17-openjdk-amd64` (installed this session via
  `sudo apt-get install -y openjdk-17-jdk-headless` — the system default
  `java`/`javac` is JDK 25, which AGP rejects; always pass
  `-Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64` to `./gradlew`).
- Android SDK at `/home/vipul/Android/Sdk` (platforms: `android-37.0`;
  build-tools `36.0.0`). `local.properties` (`sdk.dir=...`, gitignored) and
  `app/debug.keystore` (standard `androiddebugkey`/`android` dev creds,
  gitignored) were generated locally to make builds runnable — regenerate
  them if missing rather than assuming they're committed.
- Verified working: `./gradlew :app:assembleDebug` and
  `:app:testDebugUnitTest` both `BUILD SUCCESSFUL`; bridge `node --test`
  14/14; `python3 tools/verify_catalog.py` PASS.

## What the user actually asked for, most recently

> "yeah the app is not working, create an intent.md, spec.md and plan.md for
> just the android app to be built from scratch"

This was **not** accompanied by a description of the actual runtime symptom
(crash on launch? pairing fails silently? blank screen? something else?).
I asked three clarifying things at the end of my `plan.md` response and got
no reply before this handoff was requested:

1. What "not working" actually means at runtime — worth getting a real
   answer before spending Phase 1+ effort, in case it's a small, fixable
   bug rather than something that justifies a full rebuild.
2. Confirm the bridge (`bridge/main.js`) is to be treated as frozen,
   unmodified infrastructure for the rebuild (I assumed yes, since it's the
   one piece that's actually tested).
3. Compose Unstyled vs. plain Material 3 for the new app (I recommended
   dropping Compose Unstyled — it was the single biggest source of
   dependency-version pain in the old app; Material 3 alone is simpler and
   sufficient unless there's a real design reason to keep it).

**Next step in the new session: get the user's answer to (1) at minimum
before starting Phase 0 of `plan.md`.** If they just want to proceed with
the rebuild regardless of the specific symptom, that's fine too — just
don't assume it silently.

## Working conventions established this engagement (carry forward)

- **No more zip/markdown packaging for another AI to apply.** Early in this
  engagement the user explicitly stopped a workflow where deliverables were
  zipped up for a separate Gemini session to re-apply by hand
  ("gemini is done with part3. you do the rest"). All work happens as
  direct edits to the real repo, verified in place, from here on —
  including whatever comes out of executing `plan.md`.
- **Verify by running, not by reading.** This entire session's most
  important finding (the missing Kotlin plugin) was invisible to static
  review and only surfaced by actually building and decompiling. Apply the
  same standard to the rebuild: each phase in `plan.md` has an explicit
  `[H-BUILD]`/`[H-DEVICE]` exit criterion for exactly this reason — don't
  mark a phase done on code-reads alone.
- Real library/dependency coordinates were repeatedly wrong in the
  pre-existing code (hallucinated Maven coordinates, missing versions).
  When adding any new dependency during the rebuild, verify group/artifact/
  version against Maven Central (or Google's Maven) directly before writing
  it into `libs.versions.toml` — don't trust a remembered coordinate or a
  doc page without cross-checking the actual published POM.
