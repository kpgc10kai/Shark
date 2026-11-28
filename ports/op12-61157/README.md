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

## Shark integration result (2026-11-28)

A 6.1 runner built from the GPL-3.0 `ic-timer-lpe` core now exists as
`libk61157.so`. The app maps the OP12 release to it and runs it with
`PAYLOAD_CMD=sh /data/local/tmp/ksu_stage.sh`.

Verified end to end on a clean boot:

```text
Shizuku authorized
One-click root
CVE-2026-98260-6.1.157
```

Result:

```text
su -c "id; getenforce"
uid=0(root) ... context=u:r:ksu:s0
Enforcing

kernelsu 225280 1 - Live
```

The manager shows **Working [Jailbreak mode]**, manager v3.3.1, kernel
`6.1.157-android14-11-o-g3d5d51510424`, device OnePlus 12.

Implementation notes:

```text
• core profile IC_PROFILE_OP12_61157
• __tracepoint_sys_exit = 0xffffffc00a1f2f00 (from vmlinux symbols)
• core writes ksu_stage.sh before the fake-cred child
• app passes PAYLOAD_CMD to that script
```

This is a Shark-en-specific 6.1 chain, not an upstream-supported target.

## Cold-boot repetitions (direct runner)

Five clean boots, direct `ic-op12 ksu` (no app UI):

| boot | result | payload time |
|---|---|---|
| 1 | KSU loaded | 5.468 s |
| 2 | reboot during fake | — |
| 3 | KSU loaded | 9.460 s |
| 4 | KSU loaded | 5.795 s |
| 5 | KSU loaded | 9.323 s |

```text
success rate   4/5 (80%)
success range  5.5 - 9.5 s
failure        one cold-boot timer race reboot
```

The app-path run that produced the screenshot completed in 7.222 s.
This is promising, but 5 boots are not enough for a stable designation;
run 10-20 more before promoting OP12 out of experimental.

## Cold-boot repetitions 6-10

| boot | result | payload time |
|---|---|---|
| 6 | KSU loaded | 5.690 s |
| 7 | KSU loaded | 5.523 s |
| 8 | KSU loaded | 8.931 s |
| 9 | KSU loaded | 5.666 s |
| 10 | KSU loaded | 9.054 s |

Cumulative: **9/10 (90%)**, success range 5.5-9.5 s.
The one failure was boot 2 (reboot during fake).

## Cold-boot repetitions 11-12

| boot | result | payload time |
|---|---|---|
| 11 | parked, no root | — |
| 12 | parked, no root | — |

Both failures ended with the core in `sigsuspend`; reboot is the only cleanup.
Cumulative:

```text
success 9/12 (75%)
success range 5.5-9.5 s
failures: 1 reboot during fake, 2 parked runs
```

This is **not stable enough** to promote OP12 out of experimental. The next
work is fake-plant retry/timeout handling, not more blind cold boots.

## Additional cold-boot runs (13-16)

| boot | result | payload time |
|---|---|---|
| 13 | KSU loaded | — |
| 14 | KSU loaded | 5.548 s |
| 15 | parked, no root | — |
| 16 | KSU loaded | 9.031 s |

Cumulative:

```text
success 12/16 (75%)
failures: 1 reboot, 3 parked
```

The parked rate is now the dominant issue. More blind cold boots will not fix
it; the next change must address the burst-miss path that holds a zombie.
