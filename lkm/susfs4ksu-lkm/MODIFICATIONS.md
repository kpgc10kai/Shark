# Modification notes

Upstream: [`inforcqb/susfs4ksu-lkm`](https://github.com/inforcqb/susfs4ksu-lkm) (GPL-2.0, see `LICENSE`). Baseline `b69dee4`, rebased to `28b2e85` on 2026-10-27 (`sus_map: expose rules through /proc/susfs_map`).

The PYB110 kernel enables `CONFIG_ARM64_BTI_KERNEL=y`. kprobe replaces the `bti c` at a function entry with a breakpoint, and `invoke_syscall` / `seq_read` / `show_map` perform a BTI check before jumping indirectly through the function pointer, so the result is `Oops - BTI`. Three files are changed in this directory:

## kernel/susfs_supercall.c

- Removed the kprobe on `__arm64_sys_reboot`.
- Switched to a module wrapper around `sys_call_table[__NR_reboot]`: it reads `regs->regs[0..3]` directly (a syscall-table wrapper receives a `struct pt_regs *`, so the kprobe's `real_regs` indirection is not needed), and on a magic match hands the command to task_work and returns 0; otherwise it calls the saved original syscall function.
- On unload, restores the table entry via `susfs_fp_remove()` + `susfs_fp_drain()`.

## kernel/susfs_hide_syms.c

- Removed the kprobes on `s_show` / `m_show`.
- After resolving `kallsyms_op.show` / `modules_op.show`, replaces them with a module wrapper via `ksu_patch_text()`; the wrapper calls the original show first and, when a hide rule matches, rolls `seq_file->count` back to its pre-call value, achieving the effect of "this line is not emitted".
- The old kprobe structs are kept as `__maybe_unused` for reference only and are no longer registered.

## kernel/sus_map.c

- Removed the kprobes on `show_map` / `show_smap` (they are the `seq_operations.show` of `/proc/<pid>/maps` / `smaps`, called indirectly by `seq_read`).
- Resolves `proc_pid_maps_op` / `proc_pid_smaps_op` and replaces them with a module wrapper via `ksu_patch_text()`; the wrapper calls the original show first and rolls `seq_file->count` back when a rule matches.
- `show_map_vma` is the inner function called directly by `show_map`/`show_smap`; its kprobe is kept, since the BTI check does not go through it.
- Merged with the upstream `28b2e85` `/proc/susfs_map`: the two outer kprobes are still skipped, and the wrappers are now armed in `sus_map_register_probes()` together with the new `/proc` node; `map_registered` and the smaps warning now track the wrappers' armed state.

## Verification

- 6.18.21 build: vermagic `6.18.21-4k SMP preempt mod_unload modversions aarch64`; merged build 3,373,024 B, sha256 `c28d54130041a255d56e68fa2c02d2750ed25feb390b3b95fabe3334f1eada9f`.
- On-device: SUSFS version `v2.3.0`, `susfs supercall: armed via sys_call_table`; the susfs/ksu lines in `/proc/kallsyms` / `/proc/modules` are hidden; the `/proc/susfs_*` rule nodes work normally; a plain `reboot(2)` goes through the original function and returns `EINVAL`, with no BTI.
- sus_map: after an app process maps a test file, reading `/proc/self/maps` and `/proc/self/smaps` hides the mapping line in both places (dmesg `hid show_map` / `hid show_smap`), with no BTI.
- Rule matrix: sus_path / kstat / open_redirect / hide_modules / sus_map / uname / opath / enable_log / avc_spoof pass; see `../susfs-tools/README.md` for details.
- Merged build re-checked on-device (2026-10-27): hot-replace loading works, version handshake v2.3.0; the `/proc/susfs_map` rule hides maps/smaps from uid 10000; opening the BakaSU manager produces no BTI/Oops.
- **Wrapper CFI**: every wrapper that indirectly calls an original kernel function is marked `__nocfi`, passing the maintainer's `cfi_sites.py` gate.
- **init function BTI**: `susfs_self_hide_nodes` was static, so the compiler emitted no landing pad; when a change in module load address triggered a linker veneer it produced `Oops - BTI`. Made a global symbol, its entry now generates `bti c`.
- show wrapper hardening (after review): the saved original is published before the wrapper pointer, with `smp_wmb()`; on unload the pointers are cleared after restoring, via `susfs_fp_drain()`; the hide decision was moved earlier and the `seq_file->count` rollback removed. Re-checked on-device: loading/rules/`rmmod` all work.

## openat2 wrapper hardening (2026-10-28)

An on-device Oops (`lr : susfs_opath_openat2+0x48/0xac`, `pc` a userspace pointer) traced to `susfs_or_entry_hook` being written as a wild pointer. The root cause was that, with a stale table entry, a new load chained the old wrapper as the original and later executed into the `.bss` of the already-freed module.

- `susfs_fp_hook.c`: validates the table entry before install — it must be the syscall's plain / `.cfi_jt` entry, a wrapper from another live module, or kernel text accepted by `kernel_text_address()`; this module itself, freed addresses and wild pointers are all rejected with `-EBUSY`. remove restores the native / compat tables separately and reads them back to confirm, no longer skipping compat just because native is not ours.
- `susfs_opath.c` / `susfs_or_entry.c`: before calling, the five wrappers compare `susfs_or_entry_hook` with `or_entry_redirect`; on mismatch they treat it as no hook and continue to the original.
- `__nocfi noinline` keeps the `cfi_sites.py` gate passing; UNDEF is still 114, with full coverage in `syms.txt`.
- On-device: 3 rounds of `rmmod` / `susfs_insmod` cycles pass, `opath 5/5`, `kstat 14/14`, no `refusing to chain` / panic. ko 3,456,440 B, sha256 `4c944d3f84b386bb03c0aadb4527c4ec74c12fe511c5511884073160c3a88304`.

## Known issues

- Arbitrary bind mounts are not marked by `hide_mounts` (`recorded=0`); actual mount hiding is unverified.
- `hide_modules` also hides `/sys/module/<name>` from root: the current SukiSU `u:r:ksu:s0` is not recognised as `su_ctx`.
- `sus_map` only takes effect for apps for which `susfs_is_current_proc_umounted_app()` is true; processes granted root via `su` are not hidden (in testing, `su 10000` with uid 10000 hits it).
