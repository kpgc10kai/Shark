# Experimental: persistent ShellServer (upstream-compatible)

Status: **experimental branch only**. Do not tag or publish a release from this
work. `v2.5.1-en.2` stays the public Latest build.

## What upstream actually does

Checked against the upstream 2.5.1 APK:

- `com.shark.kty.shell.ShellBinder` extends
  `moe.shizuku.server.IShizukuService.Stub` (Shizuku server API, version 13).
- `ShellServer` (running as the `shell` user through `app_process`) creates that
  binder and hands it to the app through the Shizuku client provider:
  - authority: `com.shark.kty.shizuku`
  - method: `sendBinder`
  - extra key: `moe.shizuku.privileged.api.intent.extra.BINDER`
- Re-delivery is triggered by watching
  `/sdcard/Android/data/com.shark.kty/files/binder-request` (mtime change).
- `ShellSupervisorService` starts and keeps the process alive with
  `app_process -Djava.class.path=<apk> /system/bin com.shark.kty.shell.ShellServer`.
- `RemoteProcessImpl` implements `IRemoteProcess.Stub`, returning
  `ParcelFileDescriptor` pipes for stdin/stdout/stderr.

Our app already declares `rikka.shizuku.ShizukuProvider`
(`${applicationId}.shizuku`, V3 support) and depends on the Shizuku API/AIDL, so
the existing `PrivService.SHIZUKU` path can consume this binder unchanged.

## Verified details (second pass)

- `ShellServer.main()` does: `Looper.prepareMainLooper()`, creates `ShellBinder`,
  calls `BinderDelivery.send(binder, "com.shark.kty")`, starts `watchRequests()`,
  then `Looper.loop()`.
- `BinderDelivery.send`:
  - authority = `<package>.shizuku`
  - `ActivityManagerApis.getContentProviderExternal(authority, 0, null, authority)`
  - `IContentProvider.call(..., "sendBinder", null, bundle)`
  - extra: `moe.shizuku.privileged.api.intent.extra.BINDER` holding a
    `moe.shizuku.api.BinderContainer` (not a raw IBinder)
  - retries up to 30 times, 300 ms apart
- `ShellServer.watchRequests` polls
  `/sdcard/Android/data/com.shark.kty/files/binder-request` and re-sends the
  binder when its mtime changes.
- App side writes `getExternalFilesDir(null)/binder-request` with the current
  time in millis (fallback: `getFilesDir()`).
- `ShellBinder` does **not** check the caller UID; it returns
  `Process.myUid()` for `getUid()` and version 13 for `getVersion()`.
- `RemoteProcessImpl.start` uses `ProcessBuilder` + `ParcelFileDescriptor`
  pipes; `newProcess` simply forwards to it.
- The starter is the bundled **`libstarter.so`**: it runs
  `pm path com.shark.kty`, sets `CLASSPATH`, and executes
  `app_process ... com.shark.kty.shell.ShellServer` via `setsid`, with stdio
  redirected.
- Our APK already ships `libstarter.so` and declares the Shizuku provider, so
  only the shell classes and supervisor are missing.

## Architecture

```
Shark app (uid app)                         ShellServer (uid shell)
  ShizukuProvider  <-- sendBinder(Binder) --  ShellBinder (IShizukuService.Stub)
  Shizuku API                                  │
  PrivService.SHIZUKU ── newProcess() ───────► RemoteProcessImpl
                                               │ ProcessBuilder
                                               ▼ shell commands / exploit
```

No custom socket and no new private protocol are needed.

## Components to add

1. `com.shark.kty.shell.ShellBinder`
   - Implements the IShizukuService methods the app uses:
     `newProcess`, `getVersion`, `getUid`, `getSELinuxContext`,
     `getSystemProperty`, `setSystemProperty`, permission checks, `exit`
   - Other AIDL methods return safe defaults
   - Custom `TRANSACTION_PROBE` for liveness (upstream uses `0x5348` / magic `0x534b`)

2. `com.shark.kty.shell.RemoteProcessImpl`
   - `ProcessBuilder(cmd).directory(dir).environment(env)`
   - `ParcelFileDescriptor.createPipe()` for stdin/stdout/stderr
   - Pump threads; `waitFor`, `exitValue`, `alive`, `destroy`

3. `com.shark.kty.shell.ShellServer`
   - `main()`: create binder, watch the request file, deliver through
     `ContentProvider.call("sendBinder", …)`
   - Loops forever; the supervisor restarts it if it dies

4. `ShellSupervisorService`
   - Foreground service, "privileged shell keeper"
   - Starts the server through the current ADB backend:
     `CLASSPATH=<apk> setsid app_process /system/bin --nice-name=shark_shell com.shark.kty.shell.ShellServer`
   - Watches the binder; restarts on loss

5. App side
   - Touch `binder-request` on startup / when reconnecting
   - `PrivService.pick()` already prefers Stellar, then Shizuku: once the server
     binder arrives, the SHIZUKU path is used with no code change

## Milestones

1. `ShellBinder` + `RemoteProcessImpl` compile; run `ShellServer` manually with
   `adb shell app_process`; verify `Shizuku.pingBinder()` becomes true.
2. Run `id` and `getenforce` through `PrivService`.
3. Run the exploit through the persistent server.
4. `ShellSupervisorService` starts/restarts the server through wireless ADB.
5. App startup touches the request file and reconnects.
6. Wired activation: PC runs the same `app_process` command.

## Security

- Binder delivery goes through our own `ShizukuProvider` authority.
- The app already limits the provider with `INTERACT_ACROSS_USERS_FULL`.
- `ShellBinder` should verify the caller UID (`Binder.getCallingUid()`) equals
  the app UID before `newProcess`; everything else returns defaults.
- No network port is opened.

## Risks

- `app_process` + hidden API use (`ActivityThread` / ContentProvider call) may
  differ across Android versions
- Shell process lifetime: must survive the ADB shell that started it (`setsid`,
  redirect stdio)
- Background service restrictions on Android 17
- `ShizukuProvider` accepts `sendBinder` from arbitrary callers unless guarded;
  verify caller UID where possible
- Testing only through debug APKs and logcat tags; no public release

## Test plan

- Manual `app_process` start; `adb logcat -s SharkShell`
- `Shizuku.pingBinder()` and `PrivService.pick()` report SHIZUKU
- `PrivService.newProcess(arrayOf("id"), …)` returns uid 2000
- Exploit root flow end to end
- Kill server; supervisor restarts it; app reconnects through the request file
- Wireless pairing first, then repeat with wired ADB

## Progress (experimental branch)

- M1 done: `ShellServer` runs as uid 2000 through `libstarter.so`; the binder is
  delivered to `ShizukuProvider`.
- M2 done: `ShellServer` + `ShellBinder` + `RemoteProcessImpl` execute commands
  (`id`, env, cwd, streams, exit codes).
- M3 done: the app uses the binder through the existing `PrivService.SHIZUKU`
  path; the full root flow succeeded on the OnePlus 15 (`exploit exit=0`).
- M4 done: `ShellSupervisorService` starts the server through wireless ADB,
  Stellar or Shizuku. It still needs one live backend to restart a lost server,
  so the first activation remains manual.
- M4 hardening: `ShellBinder` now verifies the caller UID, reports the real
  SELinux context, and implements `setprop` with key/value validation.
- M4 watchdog: `ShellServer` spawns a detached shell watchdog
  (`/data/local/tmp/shark-watchdog.lock`). Verified on the OnePlus 15: killing
  `shark_shell` twice produced a new server in under 7 seconds each time, with a
  single watchdog and automatic app reconnection.

### Fixes found during bring-up

- `attachApplication` must call back `IShizukuApplication.bindApplication(...)`
  for Shizuku v13; otherwise listeners never fire and the UI stays "not
  authorized".
- Binder re-delivery must retry until it actually succeeds; otherwise a request
  file touched while the app process is restarting is consumed and lost.
- The supervisor needs `android.permission.WAKE_LOCK` (runtime
  `SecurityException` otherwise).
