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

- `K66118_plant.c` already carries the exact `g4127` profile:
  `timer_site 0xffffffc080195d48`, `copy_low21 0xab93c`,
  `selinux_state 0xffffffc08236a2e0`, and the matching init symbols.
- The app's `PROFILE_BY_RELEASE` already maps this release to `CVE66118`.
- On device, Shark shows:

  ```text
  Kernel version  6.6.118-android15-8-gc4127a25dcf3-ab15863337-4k
  Exploit         CVE-2026-98260-6.6.118
  Device model    OnePlus 13
  Shizuku         authorized
  ```

## Kernel-side verification

The same kernel release was independently exercised with the upstream MIT
`YuKongA/CVE-2026-98260` timer (profile generated from the CPH2649 boot image):

```text
timer build:
  STATIC_TIMER_SITE  0xffffffc080195d48
  TIMER_SITE_LOW21   0x195d48
  COPY_PROCESS_LOW21 0xab93c
  SELINUX_STATE      0xffffffc08236a2e0
  OFF_TASK_CRED      0x820
  TIMER_ALLOC        0x108
  CRED_UID_OFF       0x8

timer:
  ROOT_SHELL_OK

timer fake:
  /system/bin/su -c id -> uid=0(root) ... u:r:ksu:s0
```

This proves the kernel and the g4127 constants on CPH2649. It does not yet
prove Shark's own `libk66118.so` runner end to end on this device.

## Shark verification

The device was carrying an existing KernelSU session while this was recorded.
For a clean Shark test:

1. Reboot / clear the existing KSU session.
2. Start Shizuku.
3. Run Shark One-click root from a clean boot.
4. Record `PLANT_OK`, manager launch, and a cold-boot result.

Until then, treat OP13 as **profile-recognized / kernel-verified**, not a
fully verified Shark device.
