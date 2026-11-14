"""Validate Shark exploit site offsets against a GKI boot image.

Usage:
    python kernel_profile_check.py <boot.img> [--site 0xADDR ...]

The script extracts the kernel Image from a v4 boot image, resolves virtual
addresses assuming the standard arm64 kernel base (0xffffffc080000000), and
reports the instruction and branch target at each requested site. This is the
first step of adding a new kernel profile: confirm that every site is a real
`BL` inside kernel text before wiring it into a plant.
"""

import argparse
import struct

KERNEL_BASE = 0xFFFFFFC080000000
PAGE = 4096


def load_kernel(path):
    with open(path, "rb") as f:
        head = f.read(64)
        if head[:8] != b"ANDROID!":
            # raw kernel Image (e.g. extracted prebuilt)
            f.seek(0)
            kernel = f.read()
            return kernel, 0, _banner(kernel)
        kernel_size = struct.unpack_from("<I", head, 8)[0]
        header_version = struct.unpack_from("<I", head, 40)[0]
        if kernel_size == 0:
            raise SystemExit("boot image has no kernel (init_boot?)")
        f.seek(PAGE)
        kernel = f.read(kernel_size)
    return kernel, header_version, _banner(kernel)


def _banner(kernel):
    i = kernel.find(b"Linux version ")
    if i < 0:
        return ""
    return kernel[i:kernel.find(b"\x00", i)].decode("utf-8", "replace")


def decode_bl(kernel, vaddr):
    off = vaddr - KERNEL_BASE
    if off < 0 or off + 4 > len(kernel):
        return None
    insn = struct.unpack_from("<I", kernel, off)[0]
    # BL: 100101 imm26
    if (insn & 0xFC000000) != 0x94000000:
        return {"offset": off, "insn": insn, "kind": "not-BL", "target": None}
    imm = insn & 0x03FFFFFF
    if imm & 0x02000000:
        imm -= 0x04000000
    target = (vaddr + (imm << 2)) & 0xFFFFFFFFFFFFFFFF
    return {"offset": off, "insn": insn, "kind": "BL", "target": target}


def scan_bl(kernel, center, radius=0x400):
    """List BL targets around a site, useful for spotting allocator calls."""
    start = max(0, center - radius)
    end = min(len(kernel), center + radius)
    counts = {}
    for off in range(start, end - 4, 4):
        insn = struct.unpack_from("<I", kernel, off)[0]
        if (insn & 0xFC000000) == 0x94000000:
            imm = insn & 0x03FFFFFF
            if imm & 0x02000000:
                imm -= 0x04000000
            target = (KERNEL_BASE + off + (imm << 2)) & 0xFFFFFFFFFFFFFFFF
            counts[target] = counts.get(target, 0) + 1
    return sorted(counts.items(), key=lambda kv: -kv[1])[:6]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("boot")
    ap.add_argument("--site", action="append", default=[], help="site virtual address (hex)")
    args = ap.parse_args()

    kernel, hv, banner = load_kernel(args.boot)
    print(f"header_version : {hv}")
    print(f"kernel bytes   : {len(kernel)}")
    print(f"banner         : {banner}")
    sites = args.site or []
    for s in sites:
        vaddr = int(s, 16)
        for probe, label in ((vaddr - 4, "site-4"), (vaddr, "site")):
            res = decode_bl(kernel, probe)
            if res is None:
                print(f"{s} ({label}): outside kernel image")
                continue
            print(f"{s} ({label}): off=0x{res['offset']:x} insn=0x{res['insn']:08x} {res['kind']}"
                  + (f" -> 0x{res['target']:x}" if res["target"] else ""))


if __name__ == "__main__":
    main()
