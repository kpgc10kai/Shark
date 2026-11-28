# Redmi Note 15 4G (spinel) 6.12.69 experimental

Status: **offline profile built; on-device test pending**

## Target

```text
Redmi Note 15 4G (spinel), Indonesia
HyperOS 3.0.302.0.WPGIDXM / Android 16
Kernel 6.12.69-android16-6-g014ca48a80ee-ab15889839-4k
Fingerprint Redmi/spinel_id/spinel:16/BP2A.250605.031.A3/OS3.0.302.0.WPGIDXM:user/release-keys
```

## Firmware

```text
spinel_id_global-ota_full-OS3.0.302.0.WPGIDXM-user-16.0-1cd7cada14.zip
OTA  SHA-256 7EF5C10342FBB3192133AE5AC70F419FAB615B53ACD9C2423264E4150DE252AB
boot SHA-256 D2280FBC1AAFD92B4072DFE1D7D43A15421F3D1410F15C1C60FF495EE219ECB1
```

Firmware was deleted after extracting the profile.

## 98260 analysis

```text
release    6.12.69-android16-6-g014ca48a80ee-ab15889839-4k
vulnerable YES
posix_cpu_timer_del: pid_task()==NULL at +0x5be614
task BTF 0x1440, max 0x1640
```

## Profile constants

```text
timer_site          0xffffffc0803718f4
timer_low21         0x1718f4
copy_process_low21  0xe94a4
selinux_state       0xffffffc082888890
panic_on_warn       0xffffffc0825dade0
root_user           0xffffffc082602170
init_user_ns        0xffffffc082601ef8
init_ucounts        0xffffffc082603b08
init_groups         0xffffffc082603638
off_task_cred       0x900
cred_req/alloc      0xb8 / 0xc0
cred_uid_off        0x8
timer_alloc         0x100
task_alloc          0x1440 / max 0x1640
selinux_blob_off    0x8
```

Implemented as `CVE61269_NOTE15`; runner `libk61269.so`.

## Test build

`Shark-en_2.5.6-exp1` (experimental; not in stable).

Test once on a clean boot, export the full log, and report KSU status,
reboot or hang. Cold-boot repetition is needed before any stable promotion.