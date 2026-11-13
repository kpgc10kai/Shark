# ColorOS 17 / 6.12.69 Multi-OTA Build Adaptation

The 6.12.69 line already has three OTA kernel builds; `libk61269.so` now selects
the matching call site at runtime by `uname -r` in the probe stage, so older devices are unaffected.

| Build | release |
| --- | --- |
| Old (PLZ110 etc.) | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k` |
| New (Find X9 Pro 17.0.1.201) | `6.12.69-android16-6-g5fd39602326e-ab16372235-4k` |
| g586bfab (K90 Max ported kernel) | `6.12.69-android16-6-g586bfab1b9c5-abogki536749445-4k` |

## New Build Source and Extraction

- Input: `OPPO_Find_X9_Pro_17.0.1.201_boot.img`, 67,108,864 bytes,
  SHA-256 `b562dd3076e80426198ad46940ba50fe5f7bbca90c101909f832571e075a298d`
- header v4, `kernel_size=20,256,342`, the kernel is at `boot.img[0x1000:]`,
  an **LZ4 legacy frame** (magic `0x184C2102`); scanning kallsyms directly will fail
- After `lz4 -d`: 42,101,248 bytes,
  SHA-256 `8a42827cc541b10c3c7f3e6774193ed9bed6a905e1bea4bae6f760c43c29bfc5`
- release: `6.12.69-android16-6-g5fd39602326e-ab16372235-4k`

## kallsyms Recovery (differences between 6.12 and 6.6)

`recover.py` has been fixed in two places for this image:

- **4-byte alignment gap between markers and the token table**: the previous "walk
  back from the token table" approach started at the zero byte at S-4 and aborted
  immediately; changed to scanning for the `markers[0]=0` anchor + an incrementing u32
  check, and requiring markers to end ≤16 bytes before the token table
- This image has no `kallsyms_seqs_of_names` array; names are still in the same order as
  offsets (shift=+0)

Recovery result: `n=132200`, `names 0x152d188..0x1731c6a`, `offsets 0x1732a18`,
`base 0xffffffc080000000`, 0 out of order.

## Constants (old vs. new)

| Item | Old ab15865446 | New ab16372235 | g586bfab1b9c5 |
| --- | --- | --- | --- |
| `STATIC_TIMER_SITE` | `0xffffffc0803718f4` | `0xffffffc08035783c` | `0xffffffc0801f9260` |
| `TIMER_SITE_LOW21` | `0x1718f4` | `0x15783c` | `0x1f9260` |
| `COPY_PROCESS_LOW21` | `0xe94a4` | `0xf4320` | `0xdda1c` |
| `selinux_state` | `0xffffffc082888890` | same as left | `0xffffffc082809bc8` |
| `panic_on_warn` | `0xffffffc0825dade0` | same as left | `0xffffffc08255ade0` |
| `root_user` / `init_user_ns` / `init_ucounts` / `init_groups` | `0x82602170 / 0x82601ef8 / 0x82603b08 / 0x82603638` | same as left | `0x825821f0 / 0x82581f78 / 0x82583b88 / 0x825836b8` |

The site of the new build is at `do_timer_create+0x94` (BL to `kmem_cache_alloc_noprof`;
the other occurrence at `+0x3fc` is not a timer allocation point), and the task
allocation point is at `copy_process+0x144` (BL to `kmem_cache_alloc_node_noprof`).

The data symbols and BTF are both unchanged: `k_itimer 0x100`, `cred 0xb8`, `task_struct 0x1440`
(`cred@0x900`), `tracepoint.funcs@0x48`. Data segment self-check:
`root_user.usage=1/uid=0`, `panic_on_warn=0`.

## Third Build g586bfab1b9c5 (2026-11-13)

- Input: `boot(1).img`, 67,108,864 bytes, SHA-256
  `0af2932fb21374f99d8c770348526d43120dab05cab3bd5151e55150cdcbd9c5`
- header v4, `kernel_size=19,569,799`, the kernel is at `boot.img[0x1000:]`,
  an LZ4 legacy frame (magic `0x184C2102`); after `lz4 -d` the Image is 41,576,960 bytes,
  SHA-256 `f4f68502fc9af904e0f91894f89446e9604f0e7c01a31dab1e71bbf7c741db57`
- release: `6.12.69-android16-6-g586bfab1b9c5-abogki536749445-4k`;
  kallsyms `n=131357`, `base 0xffffffc080000000`
- `STATIC_TIMER_SITE` is at `do_timer_create+0xac` (BL to
  `kmem_cache_alloc_noprof`, target `posix_timers_cache`); the second allocation path
  `+0x58c` uses the same cache, yet the victim scan still honors only the first one
- The task allocation point is at `dup_task_struct+0x54` (site `0xdda1c`, loads
  `task_struct_cachep`, BL to `kmem_cache_alloc_node_noprof`)
- This build's **data symbols differ from the older two**; `K61269_plant.c` has been
  switched to per-profile data addresses (beyond the timer/copy sites, selinux_state,
  panic_on_warn, root_user, and all init_* are selected per profile)
- Data symbols: `selinux_state 0xffffffc082809bc8`,
  `panic_on_warn 0xffffffc08255ade0`, `root_user 0xffffffc0825821f0`,
  `init_user_ns 0xffffffc082581f78`, `init_ucounts 0xffffffc082583b88`,
  `init_groups 0xffffffc0825836b8`; BTF unchanged
- On-device verification (2026-11-13): REDMI K90 Max (2604FRK1EC / MT6993,
  Android 17), running `libk61269.so run` directly in adb shell:
  `PROBE_OK → PLANT_CRED_OK → MODULE_LOADED → su uid=0`,
  `kernelsu … Live` in `/proc/modules`, SU_ID_RC=0, and all 3 plants in the suite hit

## Runtime Selection

The 6.12.69 builds in `exploit/src/K61269_plant.c` are selected exactly by runtime release:

- `select_61269_profile(release)` matches on the full `uname -r` (three entries), and the probe prints
  `PROFILE_61269 site=… low21=… task_low21=…`
- An unknown 6.12.69 build fails directly at `auto_preflight`
  (`REASON=kernel mismatch got=…`) instead of continuing to run with wrong constants

## Status

Verified on-device and passed on 2026-11-10: OPPO Find X9 Pro (PLG110, ColorOS 17,
`6.12.69-android16-6-g5fd39602326e-ab16372235-4k`), KernelSU 3.3.1 (uapi 5),
6.1 seconds end to end.
