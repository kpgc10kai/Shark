# Xiaomi Pad 8 (yupei) 6.6.118 experimental

Status: **profile constants match X7/F7; on-device test pending**

## Target

```text
Xiaomi Pad 8 (yupei), Global
Model 25097RP43G
HyperOS 4.0.2.0.XPZMIXM / Android 17
Kernel 6.6.118-android15-8-g0908354dac1b-ab16143355-4k
```

## Firmware

```text
yupei_global-ota_full-OS4.0.2.0.XPZMIXM-user-17.0-0181c8ee9f.zip
OTA  SHA-256 1B8BDB891FBBA3619380AB95E285BF11FF7467C21746EACFDEEF5AB40ACB163C
boot SHA-256 CCEA0A19DDCD0E245F376A197DFA2191F9722AB5A14AEC3D26DD8D3E4590F2AA
```

Firmware was deleted after extracting the profile.

## 98260 analysis

```text
release    6.6.118-android15-8-g0908354dac1b-ab16143355-4k
vulnerable YES
posix_cpu_timer_del: pid_task()==NULL at +0x1981f0
task BTF 0x12c0
```

## Profile

The constants are identical to the POCO X7 Pro / F7 `g090` profile:

```text
timer_site 0xffffffc080195d50 / low21 0x195d50
copy_low21 0xab93c
selinux    0xffffffc08236a2e0
off_task_cred 0x820
cred_req/alloc 0xb8 / 0xc0
cred_uid 0x8 / selinux_blob 0x8
```

The existing `CVE66118_HYPEROS4` fallback selects this profile for `yupei`.
It is disabled in stable builds; use `Shark-en_2.5.6-exp1` for the test.

## Next

One clean-boot test, full Logs export, report success/reboot/hang. If it
succeeds, add a Pad 8-specific stable mapping after repetition.