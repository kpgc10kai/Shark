English fork of [Yunnijian/Shark](https://github.com/Yunnijian/Shark).

- English documentation and English UI strings (Chinese kept in `values-zh`)
- Signed with the fork's own release key, so future releases update in place
- Package `com.shark.kty` (installs alongside the old `com.pyb110.onekey` build)

Root is temporary and disappears after a reboot. The kernel build must match
one of the versions below **exactly**; anything else is rejected.

## What's new in 2.5.1-en.7

- **HONOR 600 (VKJ-NX9) support**: new 6.6.118 profile for MagicOS 11 /
  Android 17, verified on-device by @Windroid234. The reliability fixes
  (trace-ring sizing, split page discovery, order-1 spray drip) are enabled
  only for this profile.
- Promotion of the `experimental/shellserver` features into the stable
  release, including YukiSU / SukiSU Nova and Wireless ADB.
- README cleanup and line-ending normalization.

## What's new in 2.5.1-en.6

- **YukiSU manager support**: Root manager selection now includes YukiSU
  (`org.rouyashiki.yukisu`). Shark resolves its `lib/arm64/libksud.so` payload
  even though YukiSU does not print a KernelSU-style `uapi` version string, and
  the normal late-load path works end-to-end.
- **Seven manager choices**: KernelSU, KernelSU-Next, BakaSU, SukiSU Ultra,
  SukiSU Nova, YukiSU, and FinaSU.
- YukiSU is included in the universal manager switch and the manager
  force-stop list.

## Fork features

- **Wireless ADB pairing** (experimental): pair from Settings, auto-grant
  `WRITE_SECURE_SETTINGS`, and run the root flow over the loopback ADB
  connection — no PC or separate Stellar/Shizuku app
- **Auto root on boot** with an English UI
- All upstream kernel profiles, including the Xiaomi 18 Pro / 18 Fold builds

## Supported devices

### 6.18.21 (Android 17)
- OnePlus 16 (PYB110)
- Xiaomi 18 Pro (M610BB)
- Xiaomi 18 Fold (lhasa) — experimental, offsets taken from upstream and not verified against the matching firmware
- Xiaomi Pad 7S Pro (violin) — experimental, offsets taken from upstream 2.5.1
- Xiaomi Pad 9 Pro Max (yingtian) — experimental, offsets taken from upstream 2.5.1
- OPPO Find X10 Pro Max (PMX110)

### 6.12.69 (Android 16)
- OnePlus 15 (PLK110), OnePlus 15T (PLZ110)
- OnePlus Ace7 Pro (PMS110), OnePlus Ace7 (PYF110)
- OnePlus Ace6T (PLR110), OnePlus Ace6 Ultra (PMB110)
- OnePlus Pad3 Pro (OPD2513)
- OPPO Find X9 Ultra (PMA110), Find X9 Pro (PLG110), Find X9 (PLJ110)
- OPPO Find X9s Pro (PME110EX), Find X10 (PMW110)
- OPPO Pad Mini (OPD2515), OPPO Pad 5 Pro (OPD2511)
- Realme GT8 Pro (RMX5200), Realme Neo8 (RMX8899)

### 6.12.58 / 6.12.38 (Android 16)
- OnePlus Pad3 Pro (OPD2513), OPPO Pad Mini (OPD2515)
- OnePlus Ace6T (PLR110), Realme Neo8 (RMX8899)
- vivo X300 Ultra (V2562) — separate `v2562` chain: the vendor blocks
  `ksud late-load`, so the app wipes the vendor hook table and the module
  COMING notifier, then loads the LKM directly. Ported from the working
  `vivoTimer` timer17 PoC; see `ports/v2562/README.md`.

### 6.6.118 (Android 15)
- OnePlus Ace6 (PLQ110), OnePlus Ace5 Pro (PKR110)
- OnePlus Pad2 Pro (OPD2413)
- OPPO Find X8 Ultra (PKJ110), OPPO Find N5 (PKH110)
- OPPO Pad4 Pro (OPD2409)
- Realme GT8 (RMX6699)
- OPPO K13 Turbo Pro (PLE110)
- HONOR 600 (VKJ-NX9) — MagicOS 11 / Android 17, verified on-device

The complete kernel-version table is in the
[README](https://github.com/kpgc10kai/Shark#supported-devices).

## Release policy

Starting with `2.5.1-en.6`, published APKs are retained as release assets. Older superseded or intermediate builds keep a notice page as a pre-release with the APK removed, so old links point to https://github.com/kpgc10kai/Shark/releases/latest.