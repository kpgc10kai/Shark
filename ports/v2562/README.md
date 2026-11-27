# vivo X300 Ultra (V2562) 6.12.58 CVE-2026-98260 Adaptation

The X300 Ultra cannot use Shark's 6.12.58 chain as-is: vivo's `vr.ko` /
`hooks_defend` stack vetoes `ksud late-load` with `-EPERM` and kills tampered
tasks. This port carries the ladder that was proven on the device for two weeks
(the `vivoTimer` timer17 PoC, v108) as its own entry point: `libv2562.so`.

`libk61258.so` (OnePlus Pad3 Pro / Pad Mini, `g7704a1ae279b`) is untouched.

## Status

Experimental. Verified end to end on 2026-11-26 from the built `libv2562.so`,
both as the `shell` uid (what Shizuku gives the app) and from the Shark UI
itself:

```
PROFILE_V2562=6.12.58-android16-6-g09eda8c8505a-ab15950084-4k
[stage] manager LKM patch rc=0 missing=0    (manager extract, 228/228 symbols)
ALLOWLIST_PATH=1
[+] LKM staged /data/local/tmp/ksu-p.ko slide=0x1190e00000
PLANT_OK: caps ready (enforce=Enforcing, gate wipe + direct finit running)
[gc] /data/adb o+x rc=0
MODULE_LOADED
[+] ROOT: kernelsu module live
[+] selinux restore rc=0 enforce=1
KSUD_INSTALL_RC=0
ALLOWLIST_STAT 600 2000:2000 /data/local/tmp/.ksu
ALLOWLIST_PERSIST=ok sizes=1576/1576 restored=false
KSU_STAGES_LOG=POST_FS_DATA_RC=0 SERVICES_RC=0 BOOT_COMPLETED_RC=0
uid=0(root) gid=0(root) ... context=u:r:ksu:s0
SU_ID_RC=0
MANAGER_START=com.rifsxd.ksunext/.ui.MainActivity attempt=1
DONE
```

From the UI the run stops after `ALLOWLIST_PERSIST=ok`: the boot-stage replay
that follows restarts zygote (see "Integration points"), which kills the app
driving the run. Root, the persisted grants and the modules are already in
place at that point; the manager is started by the retrying launch only when
the run survives the replay.

Origin device and image:

- vivo X300 Ultra (V2562, SM8750), bootloader locked, SELinux Enforcing
- OriginOS 16.0.7.10 W30 and 17.1.6.41 W30 (same kernel image)
- Kernel `6.12.58-android16-6-g09eda8c8505a-ab15950084-4k`
- Manager: KernelSU-Next v3.4.2 (`com.rifsxd.ksunext`)
- PoC: `timer17` v108, stable over two weeks of daily driving

The plant itself is flaky on this image (roughly half the cold boots): it can
panic during the timer reclaim or the vendor scanner can cold-reboot the phone
seconds after root is delivered. A cold reboot clears it; retry.


## Why this needs a separate chain

| Shark 6.12.58 chain | X300 Ultra |
| --- | --- |
| fake cred, then `ksud late-load` | `ksud`/`init_module` is refused: `hooks_defend.ko` registers a `module_notify_list` COMING notifier that returns `NOTIFY_BAD` (`-EPERM`) before `parse_args()` |
| one zero write to `sys_exit` tracepoint | `vr.ko` registers dynamically; the syscall-entry killer is a kprobe, and the vendor hook table needs 11 qwords nulled |
| no BPF | the `load_module` kprobes live in `hooks_defend.ko`'s module `.bss` (module KASLR), so the child locates their `pre_handler` fields with a `BPF_PROG_TYPE_KPROBE` program reading `kprobe_table` |
| `ksud` loads the manager LKM | the grandchild calls `finit_module` on a symbol-baked LKM with `allow_shell=1` |

## Ladder

1. `vr.ko` vendor-hook dispatch wipe: `VR_HOOK_FUNCS[4..13]`. Hooks 0..3 are the
   cred `rvh` hooks and stay armed (nulling them panicked the Pro).
2. `cred->cap_effective := <canonical kernel pointer>`; the planted node address
   is a near-all-ones mask that passes the write gate and carries
   `CAP_SYS_MODULE`/`CAP_BPF` about half the time, so it is re-rolled until both
   bits are set.
3. `selinux_state.enforcing = 0`. The write is vendor-watched; the window is
   ~130 ms, and the parent re-fires it on demand.
4. The child loads the BPF gatekill program, reads `kprobe_table` and the
   `hooks_defend` module list, and the parent zeroes six `pre_handler` fields.
5. `module_notify_list + 0x40` (chain head) is zeroed, so the COMING notifier
   returns `NOTIFY_DONE`.
6. The grandchild `finit_module`s the staged LKM with `allow_shell=1`.
7. The grandchild, still holding the forged cred, `chmod`s `/data/adb` to
   `0711` (`CAP_FOWNER`) as soon as the module is live; the parent then
   restores the cred, runs `ksud debug su` + `setenforce 1`, and parks.
8. The runner runs `ksud install`, then moves the grant store to
   `/data/local/tmp/.ksu` and proves one real save. See "Integration points"
   for why the late-loaded LKM cannot use `/data/adb/ksu/.allowlist`.
9. Last, it replays the userspace boot stages (`post-fs-data`, `services`,
   `boot-completed`) once per boot, because a direct finit never runs them
   (see below).

`/proc/sys/kernel/kptr_restrict` and `dmesg_restrict` writes are **vendor-trapped**
on this build and are skipped; all LKM symbol addresses are baked in userspace.

## Constants (`g09eda8c8505a-ab15950084`)

```
STATIC_TIMER_SITE       0xffffffc080517744   TIMER_SITE_LOW21   0x117744
COPY_PROCESS_LOW21      0x1d7440             OFF_TASK_CRED      0x900
SELINUX_STATE           0xffffffc0827c69a0   PANIC_ON_WARN      0xffffffc08252afe0
INIT_CRED               0xffffffc082553840   ROOT_USER          0xffffffc082552370
INIT_USER_NS            0xffffffc0825520f8   INIT_UCOUNTS       0xffffffc082553d08
INIT_GROUPS             0xffffffc082553838   SEC_PREP_CREDS     0xffffffc0802a1d84
KPROBE_TABLE            0xffffffc08279fb68   MODULE_NOTIFY_LIST 0xffffffc0825d7728
MODULE_NOTIFY_HEAD_OFF  0x40                 SELINUX_BLOB_OFF   0x18
```

The 228-entry symbol table (`exploit/payload/v2562_syms.txt`) is the kallsyms
extract used by the PoC; the plant adds the runtime slide.

## LKM handling

Several symbols the KernelSU LKM needs (`alt_cb_patch_nops`,
`arm64_use_ng_mappings`, `caches_clean_inval_pou`, ...) are not exported by the
vivo kernel, so the module loader cannot resolve them. `exploit/src/ko_patch.inc`
(the x300p-cn `ko_patch` transform, compiled into the runner) turns every
`SHN_UNDEF` symbol into `SHN_ABS = link_addr + slide`, rewrites the `track_throne`
`init_cred` reference (`ADRP`+`LDR` -> `ADRP`+`ADD`), NOPs the `setenforce` call,
and sets the vermagic to the running release.

Source order:

1. `ksud debug extract-binary android16-6.12_kernelsu.ko` from the installed
   manager, so normal mode keeps manager freedom.
2. If extraction fails or the module needs a symbol the table does not carry,
   the bundled KernelSU-Next image
   `exploit/payload/v2562_ksu.ko` (500,656 bytes, sha256
   `67c5d9e8…c211e7`) is patched instead. It is the exact
   image the PoC validated: patching it with the PoC's slide reproduces the
   proven `ksu-p.ko` byte for byte except for the module name
   (`kernelsu` here, `kernelsv` in the PoC asset).

The payload and the symbol table are embedded in `libv2562.so` and written to
`/data/local/tmp` by the runner, because the shell-uid plant cannot read
app-private assets. The patched image is always `/data/local/tmp/ksu-p.ko`,
which the fake-cred child pre-opens before any tamper.

### Payload provenance and reproducibility

`exploit/payload/v2562_ksu.ko` is not a Shark build. It is the release
`android16-6.12_kernelsu.ko` embedded in the official KernelSU-Next v3.4.2
manager (`com.rifsxd.ksunext`, versionCode 33355), extracted verbatim with
`ksud debug extract-binary android16-6.12_kernelsu.ko`. Upstream source is tag
`v3.4.2` = commit `4c706b5938665866ebe16944a481a7b91e3396af`, and their
`.github/workflows/ddk-lkm-ksu.yml` builds the module for KMI `android16-6.12`
with:

- container `ghcr.io/ylarod/ddk-min:android16-6.12-20260828`
- `cd kernel && CONFIG_KSU=m CC=clang make`
- `llvm-strip -d`, artifact `android16-6.12_kernelsu.ko`

The file is committed unchanged; `ko_patch.inc` is applied in memory on the
device (symbols to absolute addresses, `track_throne` cred, vermagic). To
re-check the hash against a manager install:

```sh
K=$(pm path com.rifsxd.ksunext | sed 's|package:||; s|/base.apk$||')/lib/arm64/libksud.so
printf 'ksud debug extract-binary android16-6.12_kernelsu.ko /data/local/tmp/k.ko\nsha256sum /data/local/tmp/k.ko\n' |
    "$K" debug su
```

It prints the hash in `exploit/payload/v2562_ksu.ko.sha256`, so the payload can
also be checked without a device with `sha256sum -c`.

`libv2562.so` is built from this tree with Rust 1.99.1 and NDK 28.2.13676358:

```sh
cd exploit/rust-kplant
export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/28.2.13676358
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android31-clang
CARGO_TARGET_DIR=target/v2562 cargo build --release --target aarch64-linux-android \
    --no-default-features --features v2562
```

The result is 1,005,840 bytes, sha256
`e3603e3539a190938169631c46bb7826649c7e50b527a07c460364f628a2127c`, identical
across two clean builds on that toolchain (the committed file). `build-all.sh`
rebuilds every runner; the other `lib*.so` in the tree are the untouched
binaries from `main`.

## Integration points

- New feature `v2562` in `exploit/rust-kplant` -> `libv2562.so`.
- `PROFILE_BY_RELEASE` maps the exact release to `ExploitKind.V2562`.
- The runner passes `__plant fast manager=<pkg>`; `fast` stops the PoC's
  soft-reboot watcher, since Shark's Extras toggle owns that.
- `PYB110_KSUD` is mirrored to `/data/local/tmp/ksud` and `ksud-mgr` (the child
  and grandchild re-exec with a fixed env and cannot inherit it).
- `wait_module` accepts `kernelsu` and `kernelsv` and waits up to 300 s.
- vivo ships no `su` on PATH and `/system` is read-only, so every post-plant
  step goes through `ksud debug su` (the PoC's `/data/local/tmp/su` was exactly
  that shim). `wait_su` checks `uid=0` from the same call.
- The manager launch resolves the launcher component *before* `am force-stop`
  and then calls `pm unstop`: a stopped package resolves to no activity, which
  used to strand the manager on the `monkey` fallback.
- The boot stages are replayed once per boot (marker
  `/data/local/tmp/.ksu-stages-<boot_id>`, log `ksu-stages.log`). Without them a
  fresh LKM has an empty in-kernel allowlist, so the manager's Superuser list
  looks wiped and modules stay unmounted.
- The replay is the **last** step for a reason: the installed hide-my-applist
  module (`hma_oss_zygisk`) restarts zygote from its monitor
  (`zygote restart requested...` in `ksu-stages.log`), which kills every app
  process, including the Shark app that started this run. Everything before
  that point is durable, everything after it (SUID check, manager launch,
  `DONE`) may never run. `SYSTEM_RESTART` in `/data/system/dropbox` at the
  moment of the root is this, not a plant failure, and it is not Shark's
  "soft reboot after root" toggle.
- `MANAGER_START=retry ...` in the log is expected: right after the stage
  replay the hide-applist module is re-injecting into system_server and every
  third-party package query fails for a minute or so (`Error: Activity class
  ... does not exist`, `No activities found to run`), so the launch is retried
  every 4 s instead of giving up. `MANAGER_START_FAILED` means the app was
  never reachable; the manager still opens normally from the launcher.
- `su` needs `/data/adb` to be searchable *before* it works. vivo's `su_compat`
  execs `/data/adb/ksud` with the caller's creds, and init leaves `/data/adb`
  `0700 root`; a normal boot lets the installed `deVivo` module `chmod 711` it
  from `post-fs-data`, which a late load never replays. Without it every `su`
  exits 0 having run nothing: `ksud install` no-ops, the boot-stage replay
  no-ops (see `KSU_STAGES_NOOP`) and the manager dies with "The main shell died
  during initialization". Step 7 of the ladder does the `chmod` from the plant,
  while the forged cred still has `CAP_FOWNER`.

- `ksu_cred` is `prepare_creds()` from the *finit task*, so it inherits the
  shell/app uid instead of root. `ksu_load_allow_list()` (module init) and
  `do_persistent_allow_list()` (init task_work under `override_creds`) both
  open the grant store with those creds, and `/data/adb` is `0700 root` (init
  resets it every boot), so both fail with `-EACCES`
  (`save_allow_list create file failed: -13`) and grants cannot persist.
  `escape_to_root_for_init()` does not help: it only switches the SELinux
  domain, the uid stays. The staged LKM is therefore patched to keep the store
  at `/data/local/tmp/.ksu` (`ko_patch` prints `ALLOWLIST_PATH=1`, 20 chars so
  it replaces the 24-char original in place). Same filesystem, persists across
  reboots, traversable by any uid, and the file is owned by the planting uid,
  so the kernel reads and writes it untouched. The runner migrates an existing
  file, keeps `/data/adb/ksu/.allowlist` as a symlink for the manager's
  backup, and re-applies the shell entry as a probe
  (`ALLOWLIST_PERSIST=ok`). The kernel reads the store inside
  `finit_module`, before the runner can migrate, so the first boot with the
  relocated store loads an empty list: the probe detects the shrunk save,
  restores the old file and reports
  `ALLOWLIST_PERSIST=lost-entries-restored-reboot`. Reboot once (or just run
  Shark again after the reboot) and the entries load normally. Do **not** try
  to fix this with cred writes: the vendor hard-reboots within seconds of a
  uid/fsuid field write (verified) and panics deterministically on a
  cred-pointer swap to `init_cred`.
- vivo has no Oplus guard/riskdetect/screenshot services, so those Extras are
  hidden and skipped. SUSFS is not exposed on this chain yet.

## Failure modes

- Every failed plant parks without reaping; a reaped uncontrolled timer node
  panics this kernel. **A cold reboot is the only cleanup after a failed run.**
- `STALE_PLANT` from the runner means a parked plant survived; reboot before
  retrying.
- The `selinux_state` write is watched. If the run parks at the cap plant, the
  child died before the window; reboot and retry on a clean boot.
- If the manager LKM cannot be extracted and the bundled image is missing
  (custom build), the plant prints `FAIL=stage-lkm` and exits before triggering
  anything.
- An empty Superuser list after a run means the boot stages did not replay (or
  this was the first boot with the relocated store, see above): the list the
  manager shows is the kernel's in-memory allowlist, seeded by the load inside
  `finit_module` and by the module `post-fs-data`. The file itself is intact;
  rerunning Shark replays the stages (`KSU_STAGES=already-run` when this boot
  already did), and they seed the list from `/data/local/tmp/.ksu`.
- `ALLOWLIST_PERSIST=fail` means the kernel could not write its grant store at
  all. Root still works, but manager grants will not survive a reboot; check
  the `ALLOWLIST_STAT` lines and `save_allow_list create file failed: -13` in
  dmesg. `lost-entries-restored-reboot` means the save worked but the kernel
  had loaded fewer entries than the file held (the probe then puts the file
  back): expected exactly once, on the first boot after the store moved, fixed
  by the next reboot.

## Provenance

The ladder, constants and thresholds come from the local `vivoTimer` timer17
PoC (v108), which is itself adapted from
[woshigenm/CVE-2026-98260](https://github.com/woshigenm/CVE-2026-98260) (MIT).
`ko_patch.inc` comes from the x300p-cn handoff used by the same PoC.
