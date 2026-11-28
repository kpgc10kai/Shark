Shark-en is the English derivative of [Yunnijian/Shark](https://github.com/Yunnijian/Shark).
It is an independent fork, not an official upstream build.

- English documentation and English UI strings (Chinese kept in `values-zh`)
- Signed with the fork's own release key, so future releases update in place
- Package `com.shark.kty` (installs alongside the old `com.pyb110.onekey` build)

Root is temporary and disappears after a reboot. The kernel build must match
one of the versions below **exactly**; anything else is rejected.

## What's new in 2.5.2

- **Renamed the project and app to Shark-en.** The repository, app label and
  release naming now use `Shark-en`.
- **OnePlus 12 (CPH2573) 6.1 support**: the `6.1.157-android14-11-o-g3d5d51510424`
  chain is verified on-device with KernelSU late-load. This is a separate 6.1
  runner; the profile and validation notes are in `ports/op12-61157/`.
- **Community links now use Telegram**:
  <https://t.me/kpgc10kai>
- **Feedback** now points to the Shark-en GitHub page:
  <https://github.com/kpgc10kai/Shark-en>
- **Donation links and QR assets were removed.**
- The previous en.8 device support remains included (OnePlus 13, Xiaomi 18 Pro
  Max, HONOR 600, and the existing 6.6/6.12/6.18 profiles).

## Fork features

- **Wireless ADB pairing** (experimental): pair from Settings, auto-grant
  `WRITE_SECURE_SETTINGS`, and run the root flow over the loopback ADB
  connection — no PC or separate Stellar/Shizuku app
- **Auto root on boot** with an English UI
- All upstream kernel profiles, including the Xiaomi 18 Pro / 18 Fold builds

## Supported devices

### 6.1.157 (Android 16)
- OnePlus 12 (CPH2573) — experimental 6.1 chain, verified on-device

### 6.18.21 (Android 17)
- OnePlus 16 (PYB110)
- Xiaomi 18 Pro Max (madrid) — verified on-device
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

### 6.6.118 (Android 15)
- OnePlus 13 (CPH2649) — verified on-device
- OnePlus Ace6 (PLQ110), OnePlus Ace5 Pro (PKR110)
- OnePlus Pad2 Pro (OPD2413)
- OPPO Find X8 Ultra (PKJ110), OPPO Find N5 (PKH110)
- OPPO Pad4 Pro (OPD2409)
- Realme GT8 (RMX6699)
- OPPO K13 Turbo Pro (PLE110)
- HONOR 600 (VKJ-NX9) — MagicOS 11 / Android 17, verified on-device

The complete kernel-version table is in the
[README](https://github.com/kpgc10kai/Shark-en#supported-devices).

## Release policy

Starting with `2.5.1-en.6`, published APKs are retained as release assets. Older superseded or intermediate builds keep a notice page as a pre-release with the APK removed, so old links point to https://github.com/kpgc10kai/Shark-en/releases/latest.