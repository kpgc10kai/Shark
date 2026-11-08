# vivo `vr.ko` Bypass Reference

This directory records only public methods and the conclusions of this port; it contains no third-party repository code.

## Sources

- <https://github.com/janewas/cve-2026-98260-vivo-gki-research>
- <https://github.com/DONGDERS/Ghostlock-vr>
- <https://github.com/YuKongA/ghostlock-app/issues/206>

## Chosen Approach

The W1c approach of `DONGDERS/Ghostlock-vr`:

- Perform a single zero write to `__tracepoint_sys_exit->funcs`;
- The tracepoint's registration chain is then permanently severed;
- No modification or flashing of `vendor_boot` is required;
- It does not rely on directly locating `vr.ko`'s runtime function addresses.

`docs/04-vivo-antroot-vr.md` in `janewas/cve-2026-98260-vivo-gki-research`
also confirms that vivo `vr.ko` checks and kills the privilege-escalation process from `__tracepoint_sys_exit`.

## V2505A Adaptation

Confirmed from the target device's OTA kernel image:

```text
__tracepoint_sys_exit = 0xffffffc082594840
sizeof(struct tracepoint) = 0x50
tracepoint.funcs = +0x48
```

Runtime target:

```c
__tracepoint_sys_exit + kaslr_slide + 0x48
```

After the full chain successfully performs the zero write at this address, the child process is no longer killed by `vr.ko`'s `sys_exit`
probe, and the KernelSU late-load succeeds.
