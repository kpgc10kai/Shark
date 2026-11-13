# ColorOS 17 / 6.12.69 多 OTA 构建适配

6.12.69 线已有三个 OTA 内核构建，`libk61269.so` 现在按 `uname -r` 在
probe 阶段运行时选择对应的调用点，老机型不受影响。

| 构建 | release |
| --- | --- |
| 旧（PLZ110 等） | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k` |
| 新（Find X9 Pro 17.0.1.201） | `6.12.69-android16-6-g5fd39602326e-ab16372235-4k` |
| g586bfab（K90 Max 移植内核） | `6.12.69-android16-6-g586bfab1b9c5-abogki536749445-4k` |

## 新构建来源与提取

- 输入：`OPPO_Find_X9_Pro_17.0.1.201_boot.img`，67,108,864 字节，
  SHA-256 `b562dd3076e80426198ad46940ba50fe5f7bbca90c101909f832571e075a298d`
- header v4，`kernel_size=20,256,342`，内核在 `boot.img[0x1000:]`，
  是 **LZ4 legacy 帧**（magic `0x184C2102`），直接扫 kallsyms 会失败
- `lz4 -d` 解开后：42,101,248 字节，
  SHA-256 `8a42827cc541b10c3c7f3e6774193ed9bed6a905e1bea4bae6f760c43c29bfc5`
- release：`6.12.69-android16-6-g5fd39602326e-ab16372235-4k`

## kallsyms 恢复（6.12 与 6.6 的差异）

`recover.py` 已按本镜像修过两处：

- **markers 与 token 表之间有 4 字节对齐间隙**：原来的“从 token 表向前回溯”
  会从 S-4 的零字节起步立即中断；改为扫描 `markers[0]=0` 锚点 + 递增 u32 校验，
  并要求 markers 末尾距 token 表 ≤16 字节
- 本镜像无 `kallsyms_seqs_of_names` 数组，names 仍与 offsets 同序（shift=+0）

恢复结果：`n=132200`、`names 0x152d188..0x1731c6a`、`offsets 0x1732a18`、
`base 0xffffffc080000000`、0 逆序。

## 常量（新旧对比）

| 项 | 旧 ab15865446 | 新 ab16372235 | g586bfab1b9c5 |
| --- | --- | --- | --- |
| `STATIC_TIMER_SITE` | `0xffffffc0803718f4` | `0xffffffc08035783c` | `0xffffffc0801f9260` |
| `TIMER_SITE_LOW21` | `0x1718f4` | `0x15783c` | `0x1f9260` |
| `COPY_PROCESS_LOW21` | `0xe94a4` | `0xf4320` | `0xdda1c` |
| `selinux_state` | `0xffffffc082888890` | 同左 | `0xffffffc082809bc8` |
| `panic_on_warn` | `0xffffffc0825dade0` | 同左 | `0xffffffc08255ade0` |
| `root_user` / `init_user_ns` / `init_ucounts` / `init_groups` | `0x82602170 / 0x82601ef8 / 0x82603b08 / 0x82603638` | 同左 | `0x825821f0 / 0x82581f78 / 0x82583b88 / 0x825836b8` |

新构建的 site 位于 `do_timer_create+0x94`（BL 到 `kmem_cache_alloc_noprof`，
另一处 `+0x3fc` 不是定时器分配点），task 分配点在 `copy_process+0x144`
（BL 到 `kmem_cache_alloc_node_noprof`）。

数据符号与 BTF 均未变：`k_itimer 0x100`、`cred 0xb8`、`task_struct 0x1440`
（`cred@0x900`）、`tracepoint.funcs@0x48`。数据段自检：
`root_user.usage=1/uid=0`、`panic_on_warn=0`。

## 第三构建 g586bfab1b9c5（2026-11-13）

- 输入：`boot(1).img`，67,108,864 字节，SHA-256
  `0af2932fb21374f99d8c770348526d43120dab05cab3bd5151e55150cdcbd9c5`
- header v4，`kernel_size=19,569,799`，内核在 `boot.img[0x1000:]`，
  LZ4 legacy 帧（magic `0x184C2102`）；`lz4 -d` 后 Image 41,576,960 字节，
  SHA-256 `f4f68502fc9af904e0f91894f89446e9604f0e7c01a31dab1e71bbf7c741db57`
- release：`6.12.69-android16-6-g586bfab1b9c5-abogki536749445-4k`；
  kallsyms `n=131357`、`base 0xffffffc080000000`
- `STATIC_TIMER_SITE` 在 `do_timer_create+0xac`（BL 到
  `kmem_cache_alloc_noprof`，目标 `posix_timers_cache`）；第二条分配路径
  `+0x58c` 同用该 cache，victim 扫描仍只认第一条
- task 分配点位于 `dup_task_struct+0x54`（site `0xdda1c`，加载
  `task_struct_cachep`，BL 到 `kmem_cache_alloc_node_noprof`）
- 该构建**数据符号与旧两条不同**，`K61269_plant.c` 已改为 per-profile
  数据地址（timer/copy 站点之外，selinux_state、panic_on_warn、
  root_user、init_* 全部随 profile 选择）
- 数据符号：`selinux_state 0xffffffc082809bc8`、
  `panic_on_warn 0xffffffc08255ade0`、`root_user 0xffffffc0825821f0`、
  `init_user_ns 0xffffffc082581f78`、`init_ucounts 0xffffffc082583b88`、
  `init_groups 0xffffffc0825836b8`；BTF 不变
- 真机验证（2026-11-13）：REDMI K90 Max（2604FRK1EC / MT6993，
  Android 17），adb shell 直接运行 `libk61269.so run`：
  `PROBE_OK → PLANT_CRED_OK → MODULE_LOADED → su uid=0`，
  `/proc/modules` 中 `kernelsu … Live`，SU_ID_RC=0，全套 3 个 plant 均命中

## 运行时选择

`exploit/src/K61269_plant.c` 里的 6.12.69 构建按运行时 release 精确选择：

- `select_61269_profile(release)` 按完整 `uname -r` 匹配（三条），probe 打印
  `PROFILE_61269 site=… low21=… task_low21=…`
- 未知 6.12.69 构建会在 `auto_preflight` 直接 FAIL（
  `REASON=kernel mismatch got=…`），不会用错误常量继续跑

## 状态

2026-11-10 真机验证通过：OPPO Find X9 Pro（PLG110，ColorOS 17，
`6.12.69-android16-6-g5fd39602326e-ab16372235-4k`），KernelSU 3.3.1（uapi 5），
全程 6.1 秒。
