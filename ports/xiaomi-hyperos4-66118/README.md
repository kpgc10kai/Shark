# POCO X7 Pro / F7 HyperOS 4 (6.6.118) experimental port

Status: **verified on POCO X7 Pro (3/3 cold boots); F7 pending**

## Verified result

```text
POCO X7 Pro (rodin), HyperOS 4 OS4.0.1.0.XOJEUXM
Kernel 6.6.118-android15-8-g0908354dac1b-ab16143355-4k
PROFILE_66118 matched; PROBE_OK
KernelSU late-load rc=0; kernelsu 180224 1 - Live
su: uid=0(root) context=u:r:ksu:s0
SELinux Enforcing
exploit exit=0 / DONE
3/3 runs: 5.6 s, 4.9 s, 9.5 s
```

POCO X7 Pro is verified. POCO F7 is a separate device with the same kernel
string; its success is not assumed, and it remains unverified.

## Target

```text
POCO X7 Pro (rodin), EEA
HyperOS 4 OS4.0.1.0.XOJEUXM
Android 17, security patch 2026-11-01
Kernel 6.6.118-android15-8-g0908354dac1b-ab16143355-4k
Fingerprint POCO/rodin_eea/rodin:17/CP2A.260605.016/OS4.0.1.0.XOJEUXM:user/release-keys
```

POCO F7 reports the same full kernel string, so one exact profile may cover
both devices.

## OTA / boot image

```text
OTA   rodin_eea_global-ota_full-OS4.0.1.0.XOJEUXM-user-17.0-2687b032ac.zip
URL   https://bkt-sgp-miui-ota-update-alisgp.oss-ap-southeast-1.aliyuncs.com/OS4.0.1.0.XOJEUXM/rodin_eea_global-ota_full-OS4.0.1.0.XOJEUXM-user-17.0-2687b032ac.zip
OTA   SHA-256 1605CB8D370A1F77ACDD797DD729C97481CE728BE006C7C11FA27F922AB403B2
boot  SHA-256 1F59B78EDB3875A490CEC9071F3EDF99F7CD0E880321166FCB5E8E2ED2339767
```

The OTA boot.img was extracted temporarily and deleted after analysis.
The URL and hashes are retained; no firmware blob is committed.

## 98260 analysis

`timer-extract` on the boot image:

```text
release    6.6.118-android15-8-g0908354dac1b-ab16143355-4k
vulnerable YES
posix_cpu_timer_del: pid_task()==NULL; timerqueue_del not called
task BTF 0x12c0, max 0x16c0
cred uid offset 0x8, selinux blob 0x8
```

## Profile constants

```text
timer_site          0xffffffc080195d50
timer_low21         0x195d50
copy_process_low21  0xab93c
selinux_state       0xffffffc08236a2e0
panic_on_warn       0xffffffc08211ae0c
root_user           0xffffffc08213f1e0
init_user_ns        0xffffffc08213ef50
init_ucounts        0xffffffc082140cb0
init_groups         0xffffffc082140740
off_task_cred       0x820
cred_req            0xb8
cred_alloc          0xc0
timer_alloc         0x108
task_alloc          0x12c0 / max 0x16c0
cred_uid_off        0x8
selinux_blob_off    0x8
```

Implemented as the experimental `CVE66118_HYPEROS4` profile in
`exploit/src/K66118_plant.c`; the runner is `libk66118.so`.

## Tester build

`Shark-en_2.5.4-exp1`:

<https://github.com/kpgc10kai/Shark-en/releases/tag/Shark-en_2.5.4-exp1>

The stable 2.5.4 build disables this experimental profile.

## Next

Clean boot + Shizuku/Wireless ADB + Logs export. Report KSU status, reboot or
hang. Cold-boot repetition is needed before promoting to stable.