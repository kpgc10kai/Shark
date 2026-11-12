<h1 align="center"><img src="docs/images/app-icon.png" width="56" height="56" align="absmiddle" alt="Shark"> Shark</h1>

<p align="center"><b>One-tap temporary root and manager-switching tool for multiple Android devices</b></p>

<p align="center">
  <a href="https://github.com/kpgc10kai/Shark/releases"><img src="docs/badges/release.svg" alt="Release"></a>
  <a href="LICENSE"><img src="docs/badges/license.svg" alt="License"></a>
  <img src="docs/badges/android.svg" alt="Android">
  <img src="docs/badges/kotlin.svg" alt="Kotlin">
  <img src="docs/badges/compose.svg" alt="Compose">
  <img src="docs/badges/kernel.svg" alt="Kernel">
  <a href="https://github.com/Yunnijian/Shark"><img src="docs/badges/gitee.svg" alt="Gitee"></a>
</p>

> **Unofficial English fork of [Yunnijian/Shark](https://github.com/Yunnijian/Shark).**
> This repository is not affiliated with, endorsed by, or maintained by the
> upstream author. Upstream is the source of truth; this fork adds English
> documentation and UI strings on top of it, and
> `.github/workflows/upstream-sync.yml` follows upstream automatically
> (build-gated, conflicts reported as issues).
>
> No warranty. Only use it on devices you own, and read
> [Switching between the upstream and this fork](#switching-between-the-upstream-and-this-fork)
> before installing.

Shark supports OnePlus, OPPO, Realme, iQOO, and other devices, with no BL unlock
required; all you need to begin is adb shell. Through Stellar or Shizuku, the app
drives the on-device exploit, gains temporary kernel privileges, then loads the LKM
for the selected manager, ultimately providing a KernelSU, KernelSU-Next, BakaSU,
SukiSU Ultra, or FinaSU root environment.

## Interface

<table align="center">
  <tr>
    <td align="center"><img src="docs/screenshots/app-home.jpg" width="180" alt="Home"><br><sub><b>Home</b></sub></td>
    <td align="center"><img src="docs/screenshots/app-manager-dropdown.jpg" width="180" alt="ROOT Manager"><br><sub><b>ROOT Manager</b></sub></td>
    <td align="center"><img src="docs/screenshots/app-extras.jpg" width="180" alt="Extras"><br><sub><b>Extras</b></sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/kernelsu-manager.jpg" width="180" alt="KernelSU manager"><br><sub><b>KernelSU</b></sub></td>
    <td align="center"><img src="docs/screenshots/bakasu-manager.jpg" width="180" alt="BakaSU manager"><br><sub><b>BakaSU</b></sub></td>
    <td align="center"><img src="docs/screenshots/sukisu-manager.jpg" width="180" alt="SukiSU manager"><br><sub><b>SukiSU Ultra</b></sub></td>
  </tr>
</table>

## Supported devices

| Device | System | Full kernel version |
| --- | --- | --- |
| OnePlus 16 (PYB110) | ColorOS 17 | `6.18.21-android17-5-gf957accaf563-ab15926176-4k`<br>`6.18.21-android17-5-g7fcbffcad3e1-ab16353812-4k` |
| OnePlus 15 (PLK110) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k`<br>`6.12.69-android16-6-g5fd39602326e-ab16372235-4k` |
| OnePlus 15T (PLZ110) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k`<br>`6.12.69-android16-6-g5fd39602326e-ab16372235-4k` |
| OnePlus Ace7 Pro (PMS110) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k`<br>`6.12.69-android16-6-g5fd39602326e-ab16372235-4k` |
| OnePlus Ace7 (PYF110) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k`<br>`6.12.69-android16-6-g5fd39602326e-ab16372235-4k` |
| OnePlus Ace6T (PLR110) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k`<br>`6.12.69-android16-6-g5fd39602326e-ab16372235-4k`<br>`6.12.38-android16-5-g844001fb8721-ab14552068-4k` |
| OnePlus Ace6 Ultra (PMB110) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k`<br>`6.12.69-android16-6-g5fd39602326e-ab16372235-4k` |
| OnePlus Ace6 (PLQ110) | ColorOS 17 | `6.6.118-android15-8-g9bc34d5b0c79-abogki537459655-4k` |
| OnePlus Ace5 Pro (PKR110) | ColorOS 17 | `6.6.118-android15-8-g9bc34d5b0c79-abogki537459655-4k` |
| OnePlus Pad3 Pro (OPD2513) | ColorOS 16/17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k`<br>`6.12.69-android16-6-g5fd39602326e-ab16372235-4k`<br>`6.12.58-android16-6-g7704a1ae279b-ab15213644-4k` |
| OnePlus Pad2 Pro (OPD2413) | ColorOS 17 | `6.6.118-android15-8-gc4127a25dcf3-ab15863337-4k` |
| OPPO Find X9 Ultra (PMA110) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k`<br>`6.12.69-android16-6-g5fd39602326e-ab16372235-4k` |
| OPPO Find X9 Pro (PLG110) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k`<br>`6.12.69-android16-6-g5fd39602326e-ab16372235-4k` |
| OPPO Find X9 (PLJ110) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k`<br>`6.12.69-android16-6-g5fd39602326e-ab16372235-4k` |
| OPPO Find X9s Pro (PME110EX) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k`<br>`6.12.69-android16-6-g5fd39602326e-ab16372235-4k` |
| OPPO Find X10 (PMX110) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k`<br>`6.12.69-android16-6-g5fd39602326e-ab16372235-4k` |
| OPPO Find X8 Ultra (PKJ110) | ColorOS 17 | `6.6.118-android15-8-g9bc34d5b0c79-abogki537459655-4k` |
| OPPO Find N5 (PKH110) | ColorOS 17 | `6.6.118-android15-8-gc4127a25dcf3-ab15863337-4k` |
| OPPO Pad Mini (OPD2515) | ColorOS 16/17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k`<br>`6.12.69-android16-6-g5fd39602326e-ab16372235-4k`<br>`6.12.58-android16-6-g7704a1ae279b-ab15213644-4k` |
| OPPO Pad 5 Pro (OPD2511) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k`<br>`6.12.69-android16-6-g5fd39602326e-ab16372235-4k` |
| OPPO Pad4 Pro (OPD2409) | ColorOS 17 | `6.6.118-android15-8-gc4127a25dcf3-ab15863337-4k` |
| Realme GT8 Pro (RMX5200) | ColorOS 17 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k`<br>`6.12.69-android16-6-g5fd39602326e-ab16372235-4k` |
| Realme GT8 (RMX6699) | ColorOS 17 | `6.6.118-android15-8-g9bc34d5b0c79-abogki537459655-4k` |
| OPPO K13 Turbo Pro (PLE110) | ColorOS 17 | `6.6.118-android15-8-g9bc34d5b0c79-abogki537459655-4k` |
| Realme Neo8 (RMX8899) | ColorOS 16/17 | `6.12.38-android16-5-g844001fb8721-ab14552068-4k`<br>`6.12.69-android16-6-g242e1a07f878-ab15865446-4k`<br>`6.12.69-android16-6-g5fd39602326e-ab16372235-4k` |
| vivo / iQOO devices | OriginOS 6/7 | `6.12.58-android16-6-g690108013dcc-abogki576129896-4k`<br>`6.12.58-android16-6-g0a092cc0037a-abogki521987229-4k`<br>`6.12.58-android16-6-gdd6fd5c98918-abogki543627742-4k`<br>`6.12.23-android16-5-g41f4cb1fa7c7-abogki465038634-4k`<br>`6.6.127-android15-8-gd489d4ad2a70-abogki567717946-4k` |

## Supported managers

Five options are supported: KernelSU, KernelSU-Next, BakaSU, SukiSU Ultra, and FinaSU.

Manager APKs are not distributed with Shark. Normal mode uses the `ksud` inside the
installed manager APK, so **install the manager you intend to use first**.

## What it can do

- **One-tap temporary root**: After opening the app, no manual exploit selection is
  needed; it uses `uname -r` to automatically match one of seven chains: 6.18.21 /
  6.12.69 / 6.12.38 / 6.12.58 / 6.12.23 / 6.6.118 / 6.6.127. A single tap on “One-tap
  Jailbreak” completes privilege escalation, `ksud late-load`, module installation,
  and manager launch. Temporary root disappears after a reboot; just run it again on
  the next boot.
- **Five manager choices**: Pick KernelSU, KernelSU-Next, BakaSU, SukiSU Ultra, or
  FinaSU directly from the ROOT Manager dropdown. The app is not tied to a single
  manager; the manager APK must be installed separately, and normal mode uses the
  `ksud` and LKM from that APK.
- **Hot manager switching**: Supported on 6.18 and 6.12. On the first clean boot,
  select `Dynamic Switching` to load the universal KSU LKM (initially KernelSU);
  afterward, choose the target manager under “Hot Manager Switching” in Extras.
  Switching synchronizes the version number, feature, private ioctl, and primary manager,
  and automatically reopens the target manager; no reboot or re-exploitation is
  required.
- **Extras**: On 6.18, a VFS provider can be loaded; on 6.12 / V2505A, SUSFS can be
  loaded. Other toggles suppress anti-root dialogs, perform a soft reboot after
  jailbreaking, and unload kernel security protections. Enable them as needed; a
  failure in one item does not affect root that is already active.
- **Logs and history**: Runtime logs scroll in real time. Each jailbreak saves a
  complete log to the History page, where it can be viewed, deleted, or swiped left
  to export and share, making it easier to troubleshoot failures or keep records.
- **Online updates**: Gitee Releases is checked at startup, falling back to GitHub if
  that fails. When a new version is found, it is downloaded in-app, its SHA-256 and
  APK signature are verified, and it is then handed to the system installer.
- **Droidspaces support**: On 6.18, the latest `droidspaces_lkm` module can be
  fetched with one tap and saved to `Download/Shark/`, after which it can be flashed
  in the KernelSU manager; the app does not bundle this module.

## Two root methods

### Normal mode

Choose a manager in ROOT Manager; the app uses the `ksud` bundled with that
manager's APK to load its own LKM. This is the manager's native root environment.

In normal mode, changing managers still requires a reboot.

### Extended mode

Select `Dynamic Switching` in ROOT Manager. The app uses the bundled
`libksud_universal.so` to load the universal KSU LKM; the initial manager is KernelSU.

After loading completes, “Hot Manager Switching” appears in Extras: open the
dropdown and choose the target manager; the app applies the switch and automatically
reopens the target manager. Switching only changes the manager profile in the kernel;
it does not uninstall modules or reboot.

The universal KO and stock KSU share the same name and hooks, so the first load must
happen on a clean boot with no KSU loaded; hot replacement is not possible once
`kernelsu` is loaded.

## Usage

1. Install the manager APK you want to use. If you plan to use Dynamic Switching,
   installing all five is recommended.
2. Start Stellar or Shizuku and grant access to Shark.
3. Select a normal manager or `Dynamic Switching` in the ROOT Manager dropdown.
4. Enable the Extras you need.
5. Tap “One-tap Jailbreak” and wait for `DONE` to appear in the logs.
6. In normal mode, open the manager and grant root; with Dynamic Switching, select
   the target manager in Extras, and the app switches to it and opens it automatically.

## Project structure

Shark consists of the Android app, the on-device exploit runner, and optional kernel modules.

```text
Shark/
├── app/                         Android app (Gradle project)
│   ├── app/src/main/java/com/shark/kty/
│   │   ├── MainActivity.kt      Four tabs, flow control, manager and Extras state
│   │   ├── UpdateChecker.kt     Gitee / GitHub update checks
│   │   ├── HistoryStore.kt      Log persistence, history records
│   │   └── DroidspacesModule.kt Droidspaces module download
│   ├── app/src/main/jniLibs/arm64-v8a/
│   │   ├── libk61821.so         6.18.21 runner
│   │   ├── libk61269.so         6.12.69 runner
│   │   ├── libk61238.so         6.12.38 runner
│   │   ├── libk61258.so         6.12.58 runner
│   │   ├── libk61223.so         6.12.23 runner
│   │   ├── libk66118.so         6.6.118 runner
│   │   ├── libk66127.so         6.6.127 runner
│   │   ├── libksud_universal.so Dual-KMI universal loader
│   │   ├── libsusfs_guard_lkm.so
│   │   ├── libsusfs_guard_lkm_612.so
│   │   └── libhybridmount.so
│   ├── app/src/main/assets/     manual.md, licenses, donation QR code
│   └── gradle/, keystore/
├── exploit/
│   ├── rust-k61821/             6.18.21 Rust runner
│   ├── rust-kplant/             6.12 / 6.6 Rust runner (feature selects the K source file)
│   └── src/                     plant_core.inc + K<version>_plant.c (one per kernel)
├── lkm/
│   ├── susfs4ksu-lkm/           SUSFS guard source
│   ├── hybridmount/             VFS provider source
│   └── susfs-tools/             SUSFS rule tools
├── docs/screenshots/            README screenshots
├── ports/v2505a/                V2505A notes and verification records
├── ports/coloros-61269/         6.12.69 dual-OTA build notes and recovery tools
├── ports/pkj110-66118/          PKJ110 6.6.118 notes and verification records
├── ports/pd2454-66127/          vivo 6.6.127 notes and OTA extraction tools
├── third_party/                 Third-party licenses and materials
├── NOTICE / LICENSE
└── README.md
```

**Module responsibilities**

- `app/`: Kotlin/Compose UI, Shizuku/Stellar calls, exploit chain selection,
  Extras toggles, logging, and online updates.
- `exploit/`: Rust single-entry runner and C plant, responsible for privilege
  escalation, fake cred, `ksud late-load`, and cleanup.
- `lkm/`: Source and build scripts for the SUSFS guard and Hybrid Mount VFS provider.
- `ports/v2505a/`: Notes, constants, and verification records for the V2505A branch.
- `ports/pd2454-66127/`: notes, constants and OTA extraction tools for the vivo 6.6.127 branch.
- `third_party/`: Full third-party license texts and reference materials.

**Tech stack**

- App: Kotlin, Jetpack Compose, COUI-Kit, Shizuku / Stellar API.
- Device side: Rust + C, Android NDK arm64.
- Kernel side: C / GPL-2.0 LKM; the universal KSU KO is in a separate project
  [KSU-Universal](https://github.com/Yunnijian/KSU-Universal).
- Build: Gradle Kotlin DSL, Cargo, Android SDK/NDK, Docker DDK.

## Building from source

App:

```bash
cd app
./gradlew :app:assembleRelease
```

On-device runner (one `libk<version>.so` per kernel, installed into jniLibs automatically):

```bash
export ANDROID_NDK_HOME=/path/to/ndk
./exploit/build-all.sh
```

You can also build a single target:

```bash
cd exploit/rust-kplant
cargo build --release --target aarch64-linux-android \
    --no-default-features --features k66118
```

Available features: `k61269`, `k61238`, `k61258`, `k61223`, `k66118`;
6.18.21 is built separately by `rust-k61821`.

The universal KO and dual-KMI loader are built in
[KSU-Universal](https://github.com/Yunnijian/KSU-Universal); Shark only bundles the
compiled `libksud_universal.so`.

## Switching between the upstream and this fork

This fork and the upstream build share the package name (`com.shark.kty`) but use
**different signing keys**, so Android refuses to update one over the other:

```
INSTALL_FAILED_UPDATE_INCOMPATIBLE
```

To switch in either direction:

1. Back up anything you care about.
2. Uninstall the current build.
3. Install the build you want from the matching releases page
   ([this fork](https://github.com/kpgc10kai/Shark/releases) or
   [upstream](https://github.com/Yunnijian/Shark/releases)).

Uninstalling clears the app's settings, the selected manager and extras, and the
run history. Root is temporary anyway and disappears on reboot.

The older `com.pyb110.onekey` build is a separate application. It can stay
installed alongside this one, but settings and history are not migrated.

## Notes

- The first Dynamic Switching load requires a clean boot; afterward, switching
  managers does not require a reboot.
- Normal mode requires the corresponding manager APK to be installed first; ksud is
  not bundled with Shark.
- KPM is not enabled yet.
- CVE-2026-98260 is a race-condition UAF; any run can cause a kernel panic or reboot.
- Use only on your own devices, at your own risk.

## Sources and license

- Shark's own code is GPL-3.0; see `LICENSE`. Third-party components are listed in `NOTICE`.
- The universal KSU LKM is based on SukiSU Ultra, GPL-3.0.
- SUSFS guard and Hybrid Mount are GPL-2.0.
- The CVE-2026-98260 design references the public port by AzirX1337 (MIT).
- The UI uses COUI-Kit (MPL-2.0), and remote execution uses the Shizuku API (Apache-2.0).

## Release

- This fork: https://github.com/kpgc10kai/Shark/releases
- Upstream: https://github.com/Yunnijian/Shark/releases/tag/v2.2.3
- Based on upstream v2.2.3 (upstream versionCode 95); each fork release stamps its
  own `2.2.3-en.N` version and is signed with the fork's release key
- Every fork release ships the signed APK together with its SHA-256 file

Universal kernel release:

- https://github.com/Yunnijian/KSU-Universal/releases/tag/v1.1.0
- `ksu_universal-android16-6.12.ko`
- `ksu_universal-android17-6.18.ko`
- `ksud_universal`
