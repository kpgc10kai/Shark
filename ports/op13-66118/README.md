# OnePlus 13 (CPH2649) — 6.6.118 g4127

Status: **Shark runner verified on-device (2026-11-27)**

```text
device           CPH2649 (OnePlus 13, India)
product          CPH2649IN
build            CPH2649_17.0.0.102(EX01)
fingerprint      OnePlus/CPH2649IN/OP5D55L1:17/CP2A.260605.016/V.R4T3.53091cb-2c7fb6d-2c7fb73:user/release-keys
security_patch   2026-10-01
kernel           6.6.118-android15-8-gc4127a25dcf3-ab15863337-4k
soc              SM8750
```

## Current state

- `K66118_plant.c` carries the exact `g4127` profile:
  `timer_site 0xffffffc080195d48`, `copy_low21 0xab93c`,
  `selinux_state 0xffffffc08236a2e0`, and the matching init symbols.
- `PROFILE_BY_RELEASE` maps this release to `CVE66118`.
- Shark shows the correct device and exploit on-device.

## Clean-stock verification

The device was restored to stock, rebooted, and a separate local test app was
disabled. Shizuku was started, then Shark One-click root was run.

```text
Device model    OnePlus 13
Kernel version  6.6.118-android15-8-gc4127a25dcf3-ab15863337-4k
Exploit         CVE-2026-98260-6.6.118
Shizuku         authorized
Root manager    KernelSU
```

Result:

```text
ps: libk66118.so running

su -c "id; getenforce"
uid=0(root) gid=0(root) groups=0(root) context=u:r:ksu:s0
Enforcing

pm list packages:
package:me.weishu.kernelsu
```

## Kernel-side cross-check

The same kernel release was independently exercised with the upstream MIT
`YuKongA/CVE-2026-98260` timer, using a profile generated from the CPH2649
boot image:

```text
timer build:
  STATIC_TIMER_SITE  0xffffffc080195d48
  TIMER_SITE_LOW21   0x195d48
  COPY_PROCESS_LOW21 0xab93c
  SELINUX_STATE      0xffffffc08236a2e0
  OFF_TASK_CRED      0x820
  TIMER_ALLOC        0x108

timer:       ROOT_SHELL_OK
timer fake:  /system/bin/su -c id -> u:r:ksu:s0
```

Root is temporary; reboot clears the KernelSU session.
