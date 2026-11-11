# OPPO PKJ110 (Find X8 Ultra) 6.6.118 CVE-2026-98260 Adaptation

Same origin and same physical chain as `rust-kplant`, differing only in per-build constants.
**The existing 6.12.69 / 6.18.21 / V2505A / 6.6.127 entries are left untouched**, and a new
`PKJ110_66118` compile-time branch plus a standalone `libk66118.so` are added.

This is the **android15-6.6** KMI (6.6.118), not the android16-6/android16-5 of 6.12.
It belongs to the same android15-8 GKI as 6.6.127, but the constants were recovered anew for this image.

## Status

Verified on a real device on 2026-11-10: OPPO Find X8 Ultra (PKJ110, system fingerprint
`OPPO/PKJ110/OP5DD3L1:17/AP3A.240617.008/V.4aaf009-2462cbf-2485c5e:user/`),
KernelSU manager 3.3.1-27-gdb6263d3 (32707-6). 3.5 seconds end to end, the module is loaded as an LKM,
and `/proc/modules` shows `kernelsu 184320 1 - Live`.

Key lines from the device log:

```
PLANT_CRED_OK
LATE_LOAD_RC=0
MODULE_LOADED
[4/4] manager setup
UNLOAD_GUARD_GONE
KSUD_INSTALL_RC=0
ANTIROOT_STOP ... stopped
SU_ID_RC=0: uid=0(root) gid=0(root) context=u:r:ksu:s0
Enforcing
kernelsu 184320 1 - Live
SCREENSHOT_STACK_OK=com.oplus.exsystemservice / com.oplus.screenshot
DONE
```

## OTA Source and Extraction

- Full package: `component-ota/26/10/08/0e2c7d2ace89457d8129cd7ee52943d0.zip`
  (`gauss-compota-c-cn.allawnfs.com`, 9,881,994,061 bytes; the signed link expires)
- The ZIP is **ZIP64** (EOCD `cd_offset=0xffffffff`, so the ZIP64 EOCD locator/record must be read);
  5 entries: `payload.bin` (method 0 = stored), `payload_properties.txt`,
  `care_map.pb`, `META-INF/com/android/metadata{,.pb}`
- Read only via HTTP Range: the ZIP tail 4 MiB (EOCD/ZIP64/CD), the payload head 16 MiB
  (header + manifest), and the `boot` data segment of 10,895,493 bytes
- `payload.bin`: payload `CrAU` v2, manifest 358,593 bytes, metadata signature 267 bytes,
  data blob start = `zip_local_header(61) + 24 + 358593 + 267 = 358945`
- manifest: partitions = `DeltaArchiveManifest` field 13; partition_name = 1;
  operations = 8; op's type/data_offset/data_length = 1/2/3;
  **dst_extents is field 6** (this OTA's vendor extension, not the standard field 4);
  block_size = 4096
- `boot` has 48 ops in total (REPLACE_XZ ×18, ZERO ×29, REPLACE_BZ ×1); the data segment is at
  blob `1574031600..1584927093`; rebuilding by dst_extents yields a 100,663,296-byte image
  (the rest is ZERO padding)
- `boot.img`: 100,663,296 bytes, SHA-256
  `990fd4cb473eb19b9a38847af64f066a29c02aa11bda1abb34813a68ec08dbba`
  - header v4, `header_size=1584`, `kernel_size=37,001,728`, `ramdisk_size=0`
  - the kernel Image is at `boot.img[0x1000:]`, SHA-256
    `ee5ee448d2985eeeb7a8b5ad2e1efc34eeb410a8beae0ab484504d739124d613`
  - release: `6.6.118-android15-8-g9bc34d5b0c79-abogki537459655-4k`
    (LLD 18.0.0, kleaf@build-host)

## kallsyms Layout Notes (this image differs from 6.6.127)

`recover.py` encountered two differences while recovering this image, and both have been fixed in the tool:

- `kallsyms_offsets` has a 0 sentinel at the end, so the monotonicity check was relaxed from `bad == 0` to `bad <= 4`
- **the name table has one more header entry than the address table**: `names[i]` actually pairs with `offsets[i-1]` (shift = +1).
  The shift is calibrated automatically from the self-consistency of `__ksymtab_*` PREL32 value/name (shift=+1 for this image;
  a recheck of 6.6.127 gives shift=+0, with constants matching the original README)

## Constants

```c
#define TARGET_KERNEL_RELEASE      "6.6.118-android15-8-g9bc34d5b0c79-abogki537459655-4k"

/* do_timer_create+0xa8 -> site */
#define STATIC_TIMER_SITE          0xffffffc0801a4dfcULL
#define TIMER_SITE_LOW21           0x1a4dfcULL

/* copy_process+0x140 -> site */
#define COPY_PROCESS_LOW21         0x0ab944ULL

/* kallsyms link-time addresses */
#define SELINUX_STATE_STATIC       0xffffffc08239c620ULL
#define PANIC_ON_WARN_STATIC       0xffffffc08214b2ccULL
#define ROOT_USER_STATIC           0xffffffc08216f6e0ULL
#define INIT_USER_NS_STATIC        0xffffffc08216f450ULL
#define INIT_UCOUNTS_STATIC        0xffffffc0821711b0ULL
#define INIT_GROUPS_STATIC         0xffffffc082170c40ULL
```

BTF (shipped with the image) matches 6.6.127: `k_itimer` 0x108, `cred` 0xb8,
`task_struct` 0x12c0 (`cred`@0x820), `selinux_state` 0x80, `tracepoint.funcs`@0x48.
Data symbol self-check: `root_user.usage=1 / uid=0`, `panic_on_warn=0`.

## Load Path

On the ColorOS side, the 6.6.127 `hooks_defend` gate is not used (no kread/whitelist step);
the generic `ksud late-load --kmi android15-6.6 --allow-shell --package-name <pkg>` is used instead;
`finish_manager` continues to handle `oplus_secure_guard_new` and `riskdetect` with the OPPO recipe.

## Target

- Model: OPPO Find X8 Ultra (PKJ110), ColorOS 17
- Kernel: `6.6.118-android15-8-g9bc34d5b0c79-abogki537459655-4k`
- KMI: `android15-6.6`
- Artifact: `libk66118.so` (Cargo feature `pkj110_66118`),
  App drop-down entry `CVE-2026-98260-ColorOS-6.6.118`
