# SUSFS Rule Matrix Test Tools

Corresponding source: [../susfs4ksu-lkm](../susfs4ksu-lkm). All device-side tools are statically compiled with the NDK:

```bash
CC=$NDK/toolchains/llvm/prebuilt/<host>/bin/aarch64-linux-android31-clang
$CC -static -O2 -o susfs_ctl susfs_ctl.c
$CC -static -O2 -o susfs_version susfs_version.c
$CC -static -O2 -o map_probe map_probe.c
adb push susfs_ctl susfs_version map_probe /data/local/tmp/
adb push susfs_matrix2.sh /data/local/tmp/
adb shell 'chmod 755 /data/local/tmp/susfs_ctl /data/local/tmp/susfs_version /data/local/tmp/map_probe /data/local/tmp/susfs_matrix2.sh'
```

Load the module first, then run the matrix as root:

```sh
su -c 'ksud insmod /data/local/tmp/susfs_guard_lkm-6.18.21.ko expose_proc=1'
su -c 'sh /data/local/tmp/susfs_matrix2.sh'
```

## Results (PYB110 / 6.18.21 / full module)

Most recent on-device matrix: **24 PASS / 0 FAIL / 2 SKIP / 2 NOTE**.

- **PASS**: sus_path (stat/readdir for app uid 10000, visible to root, restored after del), sus_kstat static spoofing and deletion, open_redirect (scheme 0 redirect for non-su/root, scheme 2 redirect for non-su with root unaffected), hide_modules (/proc/modules, kallsyms, /sys/module, restored after del), **sus_map (after the app maps the test file, both the maps and smaps lines are hidden, dmesg `hid show_map` / `hid show_smap`, no BTI)**, enable_log, avc_spoof, opath, uname spoof/restore, hide_mounts prefix add/del.
- **NOTE**: `hide_modules`' `/sys/module/<name>` is also hidden from root; the current SukiSU `u:r:ksu:s0` is not recognized as `su_ctx`, and root via `su` still works normally. `sus_map` only takes effect for apps for which `susfs_is_current_proc_umounted_app()` is true.
- **SKIP**: `spoof_cmdline` (persists `/proc/cmdline`, requires reloading the module to restore, not done); actual mount hiding in `sus_mount` (arbitrary bind mounts are not marked, `recorded=0`).

Usage of `susfs_ctl`:

```sh
susfs_ctl uname <release> <version>
susfs_ctl hide_mounts 0|1
susfs_ctl map <path>
susfs_version
```

`susfs_map` rules are add-only, with no proc clearing command; after testing the map rules, just reload the module to clear them.
