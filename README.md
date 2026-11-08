<h1 align="center"><img src="docs/images/app-icon.png" width="56" height="56" align="absmiddle" alt="Shark"> Shark</h1>

<p align="center"><b>One-click temporary root and manager switching for many Android devices</b></p>

<p align="center">
  <a href="https://github.com/Yunnijian/Shark/releases/tag/v2.0"><img src="docs/badges/release.svg" alt="Release"></a>
  <a href="LICENSE"><img src="docs/badges/license.svg" alt="License"></a>
  <img src="docs/badges/android.svg" alt="Android">
  <img src="docs/badges/kotlin.svg" alt="Kotlin">
  <img src="docs/badges/compose.svg" alt="Compose">
  <img src="docs/badges/kernel.svg" alt="Kernel">
  <a href="https://gitee.com/puci1/Shark/releases/tag/v2.0"><img src="docs/badges/gitee.svg" alt="Gitee"></a>
</p>

> Private English copy of [Yunnijian/Shark](https://github.com/Yunnijian/Shark).
> Upstream stays the source of truth; this copy just tracks it in English.

Shark supports OnePlus, OPPO, Realme and iQOO devices without unlocking the
bootloader - all you need to start is an `adb shell`. The app uses Stellar or
Shizuku to drive a device-side exploit; once it holds temporary kernel
privileges it loads the LKM for the manager you picked, ending up with a
KernelSU, KernelSU-Next, BakaSU, SukiSU Ultra or FinaSU root environment.

## Screenshots

<table align="center">
  <tr>
    <td align="center"><img src="docs/screenshots/app-home.jpg" width="180" alt="Home"><br><sub><b>Home</b></sub></td>
    <td align="center"><img src="docs/screenshots/app-manager-dropdown.jpg" width="180" alt="Root manager"><br><sub><b>Root manager</b></sub></td>
    <td align="center"><img src="docs/screenshots/app-extras.jpg" width="180" alt="Extras"><br><sub><b>Extras</b></sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/kernelsu-manager.jpg" width="180" alt="KernelSU manager"><br><sub><b>KernelSU</b></sub></td>
    <td align="center"><img src="docs/screenshots/bakasu-manager.jpg" width="180" alt="BakaSU manager"><br><sub><b>BakaSU</b></sub></td>
    <td align="center"><img src="docs/screenshots/sukisu-manager.jpg" width="180" alt="SukiSU manager"><br><sub><b>SukiSU Ultra</b></sub></td>
  </tr>
</table>

## Supported devices

| Device | OS | Exact kernel build |
| --- | --- | --- |
| OnePlus 16 (PYB110) | ColorOS 17 | `6.18.21-android17-5-gf957accaf563-ab15926176-4k`<br>`6.18.21-android17-5-g7fcbffcad3e1-ab16353812-4k` |
| OnePlus 15 (PLK110) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k` |
| OnePlus 15T (PLZ110) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k` |
| OnePlus Ace7 Pro (PMS110) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k` |
| OnePlus Ace7 (PYF110) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k` |
| OnePlus Ace6T (PLR110) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k` |
| OnePlus Pad3 Pro (OPD2513) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k` |
| OPPO Find X9 Ultra (PMA110) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k` |
| OPPO Pad Mini (OPD2512) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k` |
| OPPO Pad 5 Pro (OPD2511) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k` |
| Realme GT8 Pro (RMX5200) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k` |
| iQOO 15 (V2505A) | OriginOS 6 | `6.12.23-android16-5-g41f4cb1fa7c7-abogki465038634-4k` |

## Supported managers

KernelSU, KernelSU-Next, BakaSU, SukiSU Ultra and FinaSU.

Shark does not ship the manager APKs. Normal mode uses the `ksud` that ships
inside the installed manager APK, so **install whichever manager you intend to
use first**.

## What it does

- **One-click temporary root**: no need to pick an exploit by hand - the app
  matches the 6.18 / 6.12 / V2505A chain automatically from `uname -r`. A
  single tap on "one-click root" performs privilege escalation, `ksud
  late-load`, module installation and manager launch. The temporary root is
  gone after a reboot; just run it again on the next boot.
- **Five managers to choose from**: pick KernelSU, KernelSU-Next, BakaSU,
  SukiSU Ultra or FinaSU directly in the root-manager dropdown. The app is not
  tied to any one of them; you install the manager APK yourself and normal mode
  uses the `ksud` and LKM from that APK.
- **Manager hot-switching**: supported on 6.18 and 6.12. On a clean boot,
  choose `Dynamic Switching` to load the universal KSU LKM (initially
  KernelSU); afterwards use "manager hot-switch" under Extras to pick the
  target manager. Switching syncs the version, features, private ioctls and
  primary manager, and reopens the target manager - no reboot or re-exploit
  needed.
- **Extras**: VFS provider on 6.18; SUSFS on 6.12 / V2505A; plus switches for
  suppressing anti-root popups, a soft reboot after rooting, and unloading the
  kernel security guard. Enable them as needed - a single failure does not
  affect the root that is already in place.
- **Logs and history**: the runtime log scrolls live; every exploit run saves a
  full log to the history page, where you can view, delete, or swipe left to
  export and share it - handy for diagnosing failures or keeping records.
- **Online updates**: checks Gitee Releases on startup, falls back to GitHub;
  when a new version appears it downloads in-app, verifies the SHA-256 and the
  APK signature, then hands it to the system installer.
- **Droidspaces support**: on 6.18 you can fetch the latest `droidspaces_lkm`
  module with one tap and save it to `Download/Shark/`, then flash it from the
  KernelSU manager. The app does not bundle that module.

## Two ways to root

### Normal mode

Pick one manager in the root-manager dropdown; the app loads that manager's own
LKM using the `ksud` bundled in its APK. That gives you the manager's native
root environment.

Switching managers in normal mode still requires a reboot.

### Extended mode

Pick `Dynamic Switching` in the root-manager dropdown. The app loads the
universal KSU LKM with the bundled `libksud_universal.so`; the initial manager
is KernelSU.

Once loaded, "manager hot-switch" appears under Extras: open the dropdown,
choose the target manager and the app switches immediately, reopening the
target manager. Switching only rewrites the manager profile inside the kernel -
no modules are unloaded and no reboot is needed.

The universal KO uses the same name and hooks as stock KSU, so the first load
must happen from a clean boot without KSU; you cannot hot-replace it once
`kernelsu` is already loaded.

## Usage

1. Install the manager APK(s) you intend to use. For Dynamic Switching, install
   all five.
2. Start Stellar or Shizuku and grant Shark access.
3. Choose a normal manager or `Dynamic Switching` in the root-manager dropdown.
4. Enable whichever Extras you need.
5. Tap "one-click root" and wait for `DONE` in the log.
6. Normal mode: open the manager and grant root. Dynamic Switching: pick the
   target manager under Extras and the app switches to it and opens it.

## Project layout

Shark consists of an Android app, a device-side exploit runner and optional
kernel modules.

```text
Shark/
├── app/                         Android app (Gradle project)
│   ├── app/src/main/java/com/pyb110/onekey/
│   │   ├── MainActivity.kt      four tabs, flow control, manager/extras state
│   │   ├── UpdateChecker.kt     Gitee / GitHub update check
│   │   ├── HistoryStore.kt      log persistence, history
│   │   └── DroidspacesModule.kt Droidspaces module download
│   ├── app/src/main/jniLibs/arm64-v8a/
│   │   ├── libt98260.so         6.18 runner
│   │   ├── libt61269.so         6.12 runner
│   │   ├── libv2505a.so         V2505A runner
│   │   ├── libksud_universal.so dual-KMI universal loader
│   │   ├── libsusfs_guard_lkm.so
│   │   ├── libsusfs_guard_lkm_612.so
│   │   └── libhybridmount.so
│   ├── app/src/main/assets/     manual.md, licenses, donation QR
│   └── gradle/, keystore/
├── exploit/
│   ├── rust-t98260/             6.18 Rust runner
│   ├── rust-t61269/             6.12 / V2505A Rust runner
│   └── src/                     C plant (98260 core)
├── lkm/
│   ├── susfs4ksu-lkm/           SUSFS guard sources
│   ├── hybridmount/             VFS provider sources
│   └── susfs-tools/             SUSFS rule tooling
├── docs/screenshots/            README screenshots
├── ports/v2505a/                V2505A notes and verification records
├── third_party/                 third-party licenses and materials
├── NOTICE / LICENSE
└── README.md
```

**Module responsibilities**

- `app/`: Kotlin/Compose UI, Shizuku/Stellar calls, exploit-chain selection,
  extras switches, logging and online updates.
- `exploit/`: Rust single-entry runner and C plant - privilege escalation,
  fake cred, `ksud late-load` and cleanup.
- `lkm/`: sources and build scripts for the SUSFS guard and the Hybrid Mount
  VFS provider.
- `ports/v2505a/`: notes, constants and verification records for the V2505A
  branch.
- `third_party/`: full third-party licenses and reference material.

**Tech stack**

- App: Kotlin, Jetpack Compose, COUI-Kit, Shizuku / Stellar API.
- Device side: Rust + C, Android NDK arm64.
- Kernel side: C / GPL-2.0 LKM; the universal KSU KO lives in the separate
  [KSU-Universal](https://github.com/Yunnijian/KSU-Universal) project.
- Build: Gradle Kotlin DSL, Cargo, Android SDK/NDK, Docker DDK.

## Building it yourself

App:

```bash
cd app
./gradlew :app:assembleRelease
```

Device-side runners:

```bash
cd exploit/rust-t98260
cargo build --release --target aarch64-linux-android

cd ../rust-t61269
cargo build --release --target aarch64-linux-android
cargo build --release --target aarch64-linux-android --features v2505a
```

The universal KO and the dual-KMI loader are built in
[KSU-Universal](https://github.com/Yunnijian/KSU-Universal); Shark only bundles
the prebuilt `libksud_universal.so`.

## Caveats

- Dynamic Switching needs a clean boot the first time; after that, switching
  managers does not require a reboot.
- Normal mode requires the matching manager APK first - `ksud` is not bundled
  with Shark.
- KPM is not enabled yet.
- CVE-2026-98260 is a race-condition UAF: any run can panic the kernel or
  trigger a reboot.
- Use it on your own devices only, at your own risk.

## Credits and license

- Shark's own code is GPL-3.0, see `LICENSE`; third-party components are listed
  in `NOTICE`.
- The universal KSU LKM is based on SukiSU Ultra, GPL-3.0.
- SUSFS guard and Hybrid Mount are GPL-2.0.
- The CVE-2026-98260 design references AzirX1337's public port (MIT).
- The UI uses COUI-Kit (MPL-2.0); remote execution uses the Shizuku API
  (Apache-2.0).

## Releases

- GitHub: https://github.com/Yunnijian/Shark/releases/tag/v2.0
- Gitee: https://gitee.com/puci1/Shark/releases/tag/v2.0
- App: v2.0 (90)
- APK SHA-256: `29dabba7fe144593a057715b30ccf0f1aaf5295b12d2883381f62fa6b10d3e94`

Universal kernel release:

- https://github.com/Yunnijian/KSU-Universal/releases/tag/v1.1.0
- `ksu_universal-android16-6.12.ko`
- `ksu_universal-android17-6.18.ko`
- `ksud_universal`
