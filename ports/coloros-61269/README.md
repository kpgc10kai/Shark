# ColorOS 17 / 6.12.69 Dual OTA Build Adaptation

The 6.12.69 line already has two OTA kernel builds, so `libk61269.so` now selects the matching call site at runtime during
the probe stage based on `uname -r`; older models are unaffected.

| Build | release |
| --- | --- |
| Old (PLZ110 etc.) | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k` |
| New (Find X9 Pro 17.0.1.201) | `6.12.69-android16-6-g5fd39602326e-ab16372235-4k` |

## New Build Source and Extraction

- Input: `OPPO_Find_X9_Pro_17.0.1.201_boot.img`, 67,108,864 bytes,
  SHA-256 `b562dd3076e80426198ad46940ba50fe5f7bbca90c101909f832571e075a298d`
- header v4, `kernel_size=20,256,342`, the kernel is at `boot.img[0x1000:]`
  and is an **LZ4 legacy frame** (magic `0x184C2102`); scanning kallsyms directly fails
- after decompression with `lz4 -d`: 42,101,248 bytes,
  SHA-256 `8a42827cc541b10c3c7f3e6774193ed9bed6a905e1bea4bae6f760c43c29bfc5`
- release: `6.12.69-android16-6-g5fd39602326e-ab16372235-4k`

## kallsyms Recovery (differences between 6.12 and 6.6)

`recover.py` has been fixed in two places for this image:

- **A 4-byte alignment gap exists between the markers and the token table**: the original “walk backwards from the token table”
  started at the zero byte at S-4 and aborted immediately; it now scans for the `markers[0]=0` anchor plus incremental u32 validation,
  and requires the end of markers to be ≤16 bytes from the token table
- this image has no `kallsyms_seqs_of_names` array; names are still in the same order as offsets (shift=+0)

Recovery result: `n=132200`, `names 0x152d188..0x1731c6a`, `offsets 0x1732a18`,
`base 0xffffffc080000000`, 0 inversions.

## Constants (old vs. new)

| Item | Old ab15865446 | New ab16372235 |
| --- | --- | --- |
| `STATIC_TIMER_SITE` | `0xffffffc0803718f4` | `0xffffffc08035783c` |
| `TIMER_SITE_LOW21` | `0x1718f4` | `0x15783c` |
| `COPY_PROCESS_LOW21` | `0xe94a4` | `0xf4320` |
| `selinux_state` | `0xffffffc082888890` | same as left |
| `panic_on_warn` | `0xffffffc0825dade0` | same as left |
| `root_user` / `init_user_ns` / `init_ucounts` / `init_groups` | `0x82602170 / 0x82601ef8 / 0x82603b08 / 0x82603638` | same as left |

The new build's site is located at `do_timer_create+0x94` (BL to `kmem_cache_alloc_noprof`;
the other `+0x3fc` is not a timer allocation site), and the task allocation site is at
`copy_process+0x144` (BL to `kmem_cache_alloc_node_noprof`).

Data symbols and BTF are unchanged: `k_itimer 0x100`, `cred 0xb8`, `task_struct 0x1440`
(`cred@0x900`), `tracepoint.funcs@0x48`. Data-section self-check:
`root_user.usage=1/uid=0`, `panic_on_warn=0`.

## Runtime Selection

In `exploit/src/K61269_plant.c`, the two 6.12.69 builds are selected by a runtime variable:

- `select_61269_profile(release)` picks one of the two by build suffix, and the probe prints
  `PROFILE_61269 site=… low21=… task_low21=…`
- an unknown 6.12.69 build FAILs directly in `auto_preflight` (
  `REASON=kernel mismatch got=…`) and does not continue with wrong constants

## Status

Verified on a real device on 2026-11-10: OPPO Find X9 Pro (PLG110, ColorOS 17,
`6.12.69-android16-6-g5fd39602326e-ab16372235-4k`), KernelSU 3.3.1 (uapi 5),
6.1 seconds end to end.
