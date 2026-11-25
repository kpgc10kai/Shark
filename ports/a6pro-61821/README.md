# OPPO A6 Pro 5G (CPH2781) 6.18.21 test profile

This is an **unverified test profile** prepared from a reporter-supplied
`boot.img`. It is not part of the stable supported-device list yet.

## Target

- Model: OPPO A6 Pro 5G (CPH2781)
- System: ColorOS 17 / Android 17
- Kernel: `6.18.21-android17-5-o-ga78682d7ab51-4k`
- Build fingerprint:
  `OPPO/CPH2781/OP5E1BL1:17/CP2A.260605.016/V.R4T2.3215ebd-b26cc9-b436c3:user/release-keys`
- Security patch: 2026-11-01

## Source image

```text
boot.img SHA-256
CE10F4320B2172F99DA447DBE30E1F138039F1974D778C13DF9A8D7E45B1D5C9

kernel Image SHA-256
146BCDDA39829D139CB954336B7AC9351821253CE40347E1F0EB5B81DBB448A4
```

`boot.img` contains an LZ4-compressed arm64 Image. kallsyms and BTF were
recovered with `ports/coloros-61269/recover.py`.

## Recovered constants

```c
#define TARGET_KERNEL_RELEASE "6.18.21-android17-5-o-ga78682d7ab51-4k"

#define TIMER_SITE_LINK 0xffffffc080211170ULL
#define RB_ALLOC_LINK   0xffffffc08038441cULL
#define SELINUX_LINK    0xffffffc0829d8658ULL
#define USER_NS_LINK    0xffffffc0827511d8ULL
#define ROOT_USER_LINK  0xffffffc082751488ULL
#define GROUPS_LINK     0xffffffc082752b48ULL
#define UCOUNTS_LINK    0xffffffc082753008ULL
#define TASK_SITE_LINK  0xffffffc0800f4c10ULL
```

BTF layout matches the existing 6.18 profiles:

```text
k_itimer      0x180 (node 0xe0)
cred          0xb8  (uid 0x8)
task_struct   0x1280 (cred 0x908)
selinux_state 0x70
tracepoint    funcs 0x38
```

The timer/task trace sites are disassembly-derived. Run `probe` first; the
profile is not considered supported until it passes on hardware.

## Test build

The test APK is built with package suffix `.a6test` so it can be installed
alongside the normal Shark app:

```text
package    com.shark.kty.a6test
version    2.5.1-en.7-a6pro-test (1040)
```

### 1. Read-only probe

Use the standalone runner from the release assets:

```bash
adb push k61821-a6pro-test /data/local/tmp/k61821
adb shell chmod 755 /data/local/tmp/k61821
adb shell /data/local/tmp/k61821 __plant --probe
```

A matching profile prints `PROBE_KERNEL`, `PROFILE...`, `PROBE_OK`. If it
prints `kernel mismatch` or `PROBE_TIMER_FAIL`, stop and send the complete
output.

### 2. App test

```bash
adb install -r shark-a6pro-test.apk
```

Install the manager APK you intend to use, start Stellar or Shizuku, select it
in `Root manager`, and run **One-click root**. Send the complete History/log.

## Status

```text
[ ] probe passed on hardware
[ ] full fake-cred chain passed
[ ] manager late-load passed
[ ] stable supported-device entry
```

Do not add this model to the supported table or ship it in a stable release
until these are checked.
