# OnePlus Ace5 / 13R 6.1.157 tester port

Status: **13R verified on-device; Ace5 offline analysis only**

Verified target:

```text
OnePlus 13R  CPH2691_17.0.0.105
Kernel       6.1.157-android14-11-o-gaf313efe1149
KernelSU     v3.3.1 (32680-5), Working [Jailbreak mode]
SELinux      Enforcing
```

The Ace5 runner uses the same constants but has not yet received a hardware
report.

Expected firmware is exact:

```text
OnePlus Ace5    PKG110_17.0.0.105
OnePlus 13R     CPH2691_17.0.0.105
```

Other builds of the same model are not covered by this runner.

## Source images

```text
OnePlus Ace5 (PKG110)
  boot_PKG110_17.0.0.105.img
  SHA-256 4982883BD96CE82BF34EAF2DE1DF4FF98D7707C8931C5C636B4D50D7E8542ABF
  kernel SHA-256 A3779DC143AE762DF56C7046EF90CE8862B24C027E82870ED30DF438C056E8E6
  6.1.157-android14-11-o-g7cdd565ce1ed

OnePlus 13R (CPH2691)
  boot_CPH2691_17.0.0.105.img
  SHA-256 BCCB6FD1871A27D9C7D285F161BCBFA47E9E73C07B3BB1703B356957E64F190D
  kernel SHA-256 FF48A5A52C28930B382A0A8E28F7A6FC3A302C1EE4D254D60FACE797B943F0E4
  6.1.157-android14-11-o-gaf313efe1149
```

Both are Android boot v4 images with a 35899904-byte kernel at `+0x1000`.

## Applicability

`timer-extract` on both boot images:

```text
vulnerable YES
begin_new_exec: unshare_files, set_mm_exe_file run before posix_cpu_timers_exit
posix_cpu_timer_del: pid_task()==NULL at +0x3f396c, timerqueue_del not called
task BTF 0x12c0, max 0x16c0
selinux cred blob offset 0x0
```

The two devices have **identical constants**:

| value | Ace5 | 13R |
|---|---|---|
| timer site | `0xffffffc00844bc08` / low21 `0x4bc08` | same |
| copy_process low21 | `0x1e7400` | same |
| selinux_state | `0xffffffc00a29bda0` | same |
| root_user / init namespaces | same | same |
| task bytes / cred sizes | `0x12c0` / `0xb0`,`0xc0` | same |
| cred uid / selinux blob | `0x4` / `0x0` | same |

These constants differ from the OP12 (`timer low21 0x4bcac`,
`copy_process 0x1ec6b4`), so the OP12 runner must not be reused.

## Build

The runner accepts both exact release strings through
`PROFILE_KERNEL_MATCH` and `PROFILE_KERNEL_MATCH_2`.

```bash
cd Shark/experiments/ic-timer-lpe/core
CC=<ndk>/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang \
  sh build.sh ../../../ports/ace5-61157/ic-ace5 ace5_61157
```

The same binary is shipped in the tester APK as `libkace5.so`.

## Test on device

Use a clean boot, KernelSU manager installed, and Shizuku running:

```bash
adb push ports/ace5-61157/ic-ace5 /data/local/tmp/ic-ace5
adb shell 'chmod 755 /data/local/tmp/ic-ace5; cd /data/local/tmp; \
  PAYLOAD_CMD="sh /data/local/tmp/ksu_stage.sh" ./ic-ace5 ksu'
```

Do not run this on OP12 or any other kernel. Collect the full log and report
whether the device reached `u:r:ksu:s0`, rebooted, or hung.