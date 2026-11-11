# LKM Build

Device kernel: `6.18.21-android17-5-gf957accaf563-ab15926176-4k`.

## Environment

- GKI source: `android17-6.18-2026-06_r9` (SUBLEVEL=21)
- KDIR: `gki_defconfig` + `modules_prepare` + `security/selinux/built-in.a`
- DDK: `ghcr.io/ylarod/ddk-min:android17-6.18-20260828` (clang r584948c)

All seven scripts assume they run inside the DDK container with the KMI source and KDIR mounted:

```bash
docker run --rm \
  -v <gki-6.18.21>:/work/gki-6.18.21 \
  -v <kdir>:/work/kdir \
  -v <kernelsu-source>:/work/ksun \
  -v <bakasu-source>:/work/resukisu \
  -v <finasu-source>:/work/finasu \
  -v <repo>/lkm/susfs4ksu-lkm:/work/susfs4ksu-lkm \
  -v <out>:/out \
  ghcr.io/ylarod/ddk-min:android17-6.18-20260828 \
  bash /work/build-next.sh
```

## Outputs

| Script | Output | After strip |
| --- | --- | --- |
| `build-kernelsu.sh` | Official KernelSU main `db6263d3` (UAPI6, 32707) | 382,232 B |
| `build-next.sh` | KernelSU Next dev `da0b3f37` (33442) | 489,264 B |
| `build-bakasu.sh` | BakaSU 35253 (4.2.0-rc3-82-g5785bdb8) | 435,616 B |
| `build-sukisu.sh` | SukiSU Ultra v4.3.0 `e67b14e6` (41100) | 545,040 B |
| `build-finasu.sh` | FinaSU master `9b32bde4` (v1.3.2 / 33090) | 496,744 B |
| `build-susfs.sh` | SUSFS guard LKM (vendored) | 3,456,440 B (not stripped) |
| `build-hybridmount.sh` | Hybrid Mount VFS provider (vendored) | 390,952 B |

All of them use vermagic `6.18.21-4k SMP preempt mod_unload modversions aarch64`.

## SUSFS guard LKM (experimental)

Source is vendored in [susfs4ksu-lkm](susfs4ksu-lkm/) (upstream `inforcqb/susfs4ksu-lkm`, baseline `b69dee4`, rebased to `28b2e85` on 2026-10-27, GPL-2.0).
Three changes were made for `CONFIG_ARM64_BTI_KERNEL=y` on PYB110; see [MODIFICATIONS.md](susfs4ksu-lkm/MODIFICATIONS.md) for details:

- supercall: the kprobe on `__arm64_sys_reboot` was replaced with a `sys_call_table[__NR_reboot]` module wrapper
- hide_syms: the kprobe on `s_show`/`m_show` was replaced with a `seq_operations.show` wrapper (rolls back `seq_file->count` after calling the original function)
- sus_map: the kprobe on `show_map`/`show_smap` was replaced with `proc_pid_maps_op`/`proc_pid_smaps_op.show` wrappers; `show_map_vma` is called directly and keeps its kprobe

`bash lkm/build-susfs.sh` (inside the container) produces `susfs_guard_lkm-6.18.21.ko`. Load it with:

```sh
ksud insmod susfs_guard_lkm-6.18.21.ko expose_proc=1
```

**Verified**: version `v2.3.0`, supercall, hide_syms, sus_map's maps/smaps line hiding, sus_path, kstat, open_redirect, hide_modules, uname, opath, and the log toggle; the rule matrix and tools are in [susfs-tools](susfs-tools/).

**Known limitations**: `sus_map` only takes effect for apps for which `susfs_is_current_proc_umounted_app()` returns true; processes granted root by `su` are not hidden. Arbitrary bind mounts of `hide_mounts` are not marked (`recorded=0`), and actual mount hiding is unverified. The `hide_modules` `/sys/module/<name>` is hidden from root as well (SukiSU's `u:r:ksu:s0` is not recognized as `su_ctx`).

## Hybrid Mount VFS provider

Source is vendored in [hybridmount](hybridmount/) (upstream `Hybrid-Mount/meta-hybrid_mount`, GPL-2.0, baseline in [PROVENANCE](hybridmount/PROVENANCE)).
The upstream matrix in `src/vfs/lkm_target.rs` does not include 6.18, so it is compiled here directly against the 6.18.21 GKI:

```bash
docker run --rm \
  -v <repo>/lkm/hybridmount:/work/hybridmount \
  -v <repo>/lkm/build-hybridmount.sh:/work/build-hybridmount.sh:ro \
  -v <kdir>:/work/kdir \
  -v <out>:/out \
  -e KDIR=/work/kdir/android17-6.18 \
  ghcr.io/ylarod/ddk-min:android17-6.18-20260828 \
  bash /work/build-hybridmount.sh
```

After loading, `/sys/module/hybridmount` appears, and the metamodule's `vfs-doctor` reports provider `hm`, `responds=true`. KERNEL_SECURITY_CHECK logs `ko:[hybridmount.ko] hash not found` (logged only, not blocked).

## Notes

- The DDK image has `KDIR=/opt/ddk/kdir/android17-6.18` (6.18.32) built in; you must override it explicitly with `-e KDIR=/work/kdir/android17-6.18`, otherwise the vermagic produced is 6.18.32. When using a local kdir, you also need to mount the GKI source at the path recorded in its Makefile (`/work/gki-6.18.21`).
- You cannot just change vermagic to reuse an official ko from another minor version; a mismatched ABI/CFI crashes after loading.
- The module load parameter only needs `allow_shell=1`; official / Next / BakaSU all define that parameter.
- For the manager and ksud, use the official release / CI artifacts directly rather than recompiling locally (a debug-signed manager also conflicts with the old release signature).
