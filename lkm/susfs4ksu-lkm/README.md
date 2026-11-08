[x64 device compatibility](https://github.com/ymuuuu/susfs4ksu-lkm-x64)

# susfs4ksu-lkm

A **loadable kernel module (LKM) port** of SUSFS: it lets susfs run without recompiling the kernel.

## Loading

Pick the `.ko` that matches the device kernel, e.g. a 5.15 device uses `susfs_guard_lkm-android13-5.15.ko`.

**KernelSU devices**:

```sh
ksud insmod /data/adb/loader/susfs_guard_lkm.ko
```

**Non-KernelSU devices**: use the loader shipped with the release, `susfs_insmod`:

```sh
susfs_insmod /data/adb/loader/susfs_guard_lkm.ko
```

Unload:

```sh
rmmod susfs_guard_lkm
```

## Flashing as a KernelSU module

To skip picking the `.ko` by hand, each release also ships a pre-packaged module, `susfs_guard_lkm-module-<tag>-arm64.zip`: flash it in the KernelSU manager and reboot. The module automatically selects and loads the matching `.ko` for the device kernel (`/proc/sys/kernel/osrelease`) (with `expose_proc=1`); it only loads the module and sets no susfs rules, and skips if susfs is already present. arm64 only, KernelSU-family managers only. `expose_proc=1` creates the `/proc/susfs_*` control nodes, through which sus rules are issued. To remove: disable or delete this module in the manager (takes effect after a reboot), or `rmmod susfs_guard_lkm` to unload it immediately.

By default it loads during the module's `post-fs-data` stage. To load earlier instead, in the shared `post-fs-data.d` stage, run as root:

```sh
touch /data/adb/modules/susfs_guard_lkm/early_load
```

Takes effect after a reboot. Deleting the file and rebooting restores the default loading; upgrades preserve this setting. Both stages share the loader, skip when SUSFS is already present, and honour safe mode and the module disabled/deleted markers. Uninstalling only removes the shared scripts owned by this module; it does not unload a running LKM on the spot.

Enable early loading when SUSFS rules or raw kstat snapshots must be configured before the module's post-fs-data script and mounts. This makes the SUSFS interfaces available in time, so those operations can run in the boot stage they require. If the manager applies SUSFS settings before the shared scripts, an even earlier loading stage is still needed. The module only loads the LKM; settings are applied by the manager.

## Interface documentation

All `/proc` control nodes: see **[PROC_INTERFACES.md](PROC_INTERFACES.md)**.

## License

GPL-2.0, see [LICENSE](LICENSE). Ported from: [susfs4ksu](https://gitlab.com/simonpunk/susfs4ksu) (feature logic and hook points),
[SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra) (`patch_memory` / `lsm_hook` / `symbol_resolver`).

## Disclaimer

For technical research and study only. Use at your own risk.
