# vivo X200 Ultra (PD2454) 6.6.127 CVE-2026-98260 Port

Same origin and same physical link as `K61269_plant.c` / `rust-kplant`; only the per-build constants differ.
Adds one compile-time branch `KERNEL_66127` and a separate `.so`.

This is the **android15-6.6** KMI, not 6.12's android16-6/android16-5; the existing 6.12
assets for SUSFS and the universal loader do not apply to this chain.

## Status

The constants were statically recovered from the kernel Image of the vivo full OTA's `boot.img`,
and the struct layout was cross-validated with the image's bundled `.BTF`. Verified on a real
device: full chain SUCCESS on 2026-11-11 (5857ms), log-reduced version SUCCESS on 2026-11-12
(7373ms, 143 log lines, no dmesg tail flooding).
This entry is officially released along with the mainline's 6.6.127 support.

## OTA Source and Extraction

- Full package: `https://sysuptxdl.vivo.com.cn/upgrade/oem/files/20261105033848e16bd87038e4ad12a0f8d867fffbdfe5.zip`
- Package size: 12,620,478,166 bytes; `payload.bin` is **stored**, so there is no need to download the whole package.
- OTA metadata: `vivo/PD2454/PD2454:17/CP2A.260605.016/261104205945:user/release-keys`,
  `post-version=PD2454_A_17.2.11.4.W10.V000L1`, `post-sdk-level=37`,
  `hardware-device=PD2454MA`.
- Only the ZIP trailer/central directory, the payload header/manifest, and the three `boot`
  data segments are read via HTTP Range; the boot operation type is 14 = `REPLACE_ZSTD`, the three
  segments total about 12 MB, and the remaining 96 MB is the decompressed boot image.
- `boot.img`: 100,663,296 bytes, SHA-256
  `49b7e794253a04d3dbdffd811adde8cfeb017fc820c5b2731422e2502094aa30`
- Kernel Image: 36,997,632 bytes (`boot.img[0x1000:]`), SHA-256
  `b9210c6bd9703cb60cece82e5c601a429e43d8eaaf668e903a3ba2d009026fc0`
- release: `6.6.127-android15-8-gd489d4ad2a70-abogki567717946-4k`

Protocol details of `payload.bin`: the header is big-endian; this OTA's partition operations are in
field 8 (vendor extension) of `PartitionUpdate`, not the standard field 2; the three `boot`
`data_offset/data_length` pairs are `1070656/11493075`, `12563731/485484`,
`13049215/1079`, the dst_extents are `(0,8192)`, `(8192,8192)`, `(16384,8192)`,
and block_size is 4096; the data blob starts at `payload_data_start + 24 + 36485 + 267`.

## Verification Record

Re-checked on 2026-11-10 with the kernel Image above; `recover.py` output matches the constants in this file:

- kallsyms: `n=110398`, `names 0x136ed90..0x14e7a6f`, `offsets 0x14e86c0`,
  `relative_base 0xffffffc080000000`, 0 inversions
- `do_timer_create=0xffffffc080197f58`, BL `+0xa4`, site
  `0xffffffc080198000` (low21 `0x198000`)
- `copy_process=0xffffffc0800ac650`, BL `+0x13c`, site
  `0xffffffc0800ac790` (low21 `0xac790`)
- `kmem_cache_alloc=0xffffffc080380920`;
  `kmem_cache_alloc_node=0xffffffc080381290`
- `__tracepoint_sys_exit=0xffffffc0822f3760`; `struct tracepoint` 0x50,
  `funcs` @ 0x48 (same `vr.ko` handling as V2505A)
- Static addresses and BTF are covered in the next section

## Target

- Device: vivo X200 Ultra (PD2454), OriginOS 7 (Android 17)
- Kernel: `6.6.127-android15-8-gd489d4ad2a70-abogki567717946-4k`
- KMI: `android15-6.6` (`ksud late-load --kmi android15-6.6`)
- As with V2505A, zero `__tracepoint_sys_exit->funcs` used by vivo `vr.ko` before waking the
  root child process; for 6.6.127 the corresponding address is `0xffffffc0822f3760`,
  with `funcs` at offset `0x48`.

## Constants

```c
#define TARGET_KERNEL_RELEASE      "6.6.127-android15-8-gd489d4ad2a70-abogki567717946-4k"

/* do_timer_create+0xa8 -> site */
#define STATIC_TIMER_SITE          0xffffffc080198000ULL
#define TIMER_SITE_LOW21           0x198000ULL

/* copy_process+0x140 -> site */
#define COPY_PROCESS_LOW21         0x0ac790ULL

/* kallsyms link-time addresses */
#define SELINUX_STATE_STATIC       0xffffffc08239a170ULL
#define PANIC_ON_WARN_STATIC       0xffffffc08214ac0cULL
#define SYS_EXIT_TRACEPOINT_STATIC 0xffffffc0822f3760ULL
#define TRACEPOINT_FUNCS_OFF       0x48ULL
#define ROOT_USER_STATIC           0xffffffc08216f108ULL
#define INIT_USER_NS_STATIC        0xffffffc08216ee78ULL
#define INIT_UCOUNTS_STATIC        0xffffffc082170bd8ULL
#define INIT_GROUPS_STATIC         0xffffffc082170668ULL

/* shared slab/struct offsets (overridden by this profile) */
#define TIMER_REQ                  0x108ULL
#define TIMER_ALLOC                0x140ULL
#define CRED_REQ                   0xb8ULL
#define CRED_ALLOC                 0xc0ULL
#define CRED_UID_OFF               0x8ULL
#define OFF_TASK_CRED              0x820ULL
#define TASK_ALLOC_BYTES           0x12c0ULL
#define TASK_ALLOC_PADDED          0x12c0ULL
```

Struct layout (measured from BTF):

| Object | Value |
| --- | --- |
| `struct k_itimer` | 0x108, `it` (rb node) @ 0x78 |
| `struct cred` | 0xb8; uid/gid @ 0x8, security @ 0x80, user @ 0x88, user_ns @ 0x90, ucounts @ 0x98, group_info @ 0xa0 |
| `struct task_struct` | 0x12c0; `real_cred` @ 0x818, `cred` @ 0x820 |
| `struct selinux_state` | 0x80, `enforcing` @ 0x0 |
| `struct task_security_struct` | 0x18; exec_sid @ 0x8, create_sid @ 0xc |
| `struct tracepoint` | 0x50; `funcs` @ 0x48 |

Fake cred write layout (C plant): `security +0x80`, `user +0x88`,
`user_ns +0x90`, `ucounts +0x98`, `group_info +0xa0`; `uid` is at `+0x8`.

## Recovery Method (Reproducible)

```bash
python3 recover.py kernel.bin      # kernel.bin = boot.img[0x1000 : 0x1000+kernel_size]
```

The script scans both `kmem_cache_alloc` / `kmem_cache_alloc_node` (6.6 naming) and
`*_noprof` (6.12 naming), and validates the BTF header before parsing. This image has no separate
`dup_task_struct` symbol; the task allocation site is `copy_process+0x140`; the script scans
from `copy_process` for the BL to `kmem_cache_alloc_node`.

## Build

```bash
cd exploit/rust-kplant
CARGO_TARGET_DIR=target/k66127 cargo build --release \
    --target aarch64-linux-android --no-default-features --features k66127
cp target/k66127/aarch64-linux-android/release/kplant \
   ../../app/app/src/main/jniLibs/arm64-v8a/libk66127.so
```

`K66127_plant.c` defines `KERNEL_66127` itself; `Cargo.toml` has the matching `k66127 = []`,
and the feature list in `build.rs` adds `K66127` as well. The
`KERNEL_66127` branch in `plant_core.inc` includes kread, gate clearing, worker fork, and log collection.

## App Side

- `ExploitKind.CVE66127("CVE-2026-98260-vivo-6.6.127")`, matched exactly against the full `uname -r`
  string `6.6.127-android15-8-gd489d4ad2a70-abogki567717946-4k`
- Entry: `nativeLibraryDir/libk66127.so run`
- `PYB110_KMI=android15-6.6` (set automatically by the runner per feature); normal mode takes
  `ksud`/LKM from the installed manager APK, and the manager must support 6.6.127 android15-6.6
- **SUSFS not enabled** (there is no guard KO for android15-6.6); **Dynamic Switching not enabled**
  (the existing universal loader/KO is 6.18/6.12); VFS is 6.18 only and is not exposed
- On vivo, the ColorOS steps of unloading the kernel protection / stopping riskdetect / restoring the
  screenshot stack are not performed; the corresponding logs only appear in the Oplus ColorOS entry

## Verification Order

1. Read-only self-check: `libk66127.so probe` → expect `PROBE_KERNEL`, `PROBE_TIMER ... low21=0x198000`,
   `PROBE_TASK ... expected_low21=0xac790`, `PROBE_SLIDE ... aligned=1`,
   `PROBE_LINEAR_MAP`, `PROBE_CRED`, `PROBE_VR_TRACEPOINT=0x... funcs=0x... off=0x48`,
   `PROBE_SCHED_EXIT_TRACEPOINT=0x...`, `PROBE_OK`
2. Full chain: `libk66127.so run`, watch for `PLANT_CRED_OK` / `PARKED=1` / `MODULE_LOADED`
3. Run it again after a cold reboot; then verify that the manager grants authorization normally
4. On failure always leave a holder (`t61269-hold`); **do not force a rerun in the same boot**, cold reboot and retry

## Differences from V2505A

The core chain is the same: both are 98260 posix_timers UAF → fake cred → `ksud late-load`;
the fake cred field layout is also the same. Besides the constants, there are:

- **vr.ko handling**: both V2505A and 6.6.127 must zero `__tracepoint_sys_exit->funcs` before
  waking the root child process; the address/offset differ (6.6.127: `0xffffffc0822f3760` /
  `0x48`).
- **Task allocation site**: V2505A uses the separate `dup_task_struct+0x54`; in 6.6.127
  `dup_task_struct` has been inlined into `copy_process`, and the site is `copy_process+0x140`
  (low21 `0xac790`).
- **Cache/struct sizes**: V2505A's k_itimer 0x100, task_struct 0x1440,
  `task->cred` 0x900; 6.6.127's k_itimer 0x108 (alloc 0x140), task_struct
  0x12c0, `task->cred` 0x820. For this, the C side added the two profile overrides
  `TIMER_REQ` and `TASK_ALLOC_PADDED`.
- **KMI/manager**: V2505A is 6.12/android16-5 KMI, with SUSFS and
  Dynamic Switching already available; 6.6.127 is 6.6/android15-8 KMI (`android15-6.6`),
  which currently has only the normal manager path, with no universal loader and no SUSFS.
- **App selection**: 6.6.127 hides SUSFS/VFS and does not offer Dynamic Switching;
  V2505A keeps SUSFS and Dynamic Switching.

## Limitations

- Main chain only; SUSFS and universal manager switching require a separately provided android15-6.6 KO/loader.
- The manager APK must bundle the 6.6.127 android15-6.6 LKM/ksud; normal mode does not use third-party
  patched KOs.
- The race UAF itself may cause a panic/reboot; for use on your own device only.
