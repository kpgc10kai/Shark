# POCO X8 Pro Max HyperOS 4 (6.6.118) experimental

Status: **profile matches OnePlus 13, but fake-cred stage failed on-device**

## Target

`	ext
POCO X8 Pro Max
HyperOS 4 OS4.0.7.0.XPLEUXM
Kernel 6.6.118-android15-8-gc4127a25dcf3-ab15863337-4k
`

## Firmware

`	ext
archive  OS4.0.7.0.XPLEUXM.zip
SHA-256  23AF852F0E68EA6D110903027F6520BAA97D0DB0BCA27D399651EC82265C8C7C
boot.img SHA-256 51F52791CE18A10831604844461B03B51309EF9117E69EF5CFC8FBD2101E4C43
`

The firmware archive and extracted images were deleted after analysis.

## 98260 analysis

``text
release    6.6.118-android15-8-gc4127a25dcf3-ab15863337-4k
vulnerable YES
posix_cpu_timer_del: pid_task()==NULL at +0x1981e8
task BTF 0x12c0
``

Profile constants are identical to the OnePlus 13 g4127 profile:

`	ext
timer_site 0xffffffc080195d48 / low21 0x195d48
copy_low21 0xab93c
selinux    0xffffffc08236a2e0
off_task_cred 0x820
cred_req/alloc 0xb8 / 0xc0
cred_uid 0x8 / selinux_blob 0x8
`

## On-device failure

`	ext
[child] woke uid=2000 euid=2000
cred write did not land
FAIL=cred holder=t61269-hold
`

The kernel/profile selection is correct; the failure is in the fake-credential
UID repair path. Reboot before retrying; a held zombie may remain. A comparison
run with KernelSU v3.3.1 instead of KernelSU Next 3.4.2 was requested.