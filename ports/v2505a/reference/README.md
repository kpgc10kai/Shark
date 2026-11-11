# vivo `vr.ko` Bypass Reference

This directory records only the public methods and the conclusions of this port; it does not contain third-party repository code.

## Sources

- <https://github.com/janewas/cve-2026-98260-vivo-gki-research>
- <https://github.com/DONGDERS/Ghostlock-vr>
- <https://github.com/YuKongA/ghostlock-app/issues/206>

## Chosen Approach

The W1c approach from `DONGDERS/Ghostlock-vr`:

- perform a single zero-write to `__tracepoint_sys_exit->funcs`;
- the tracepoint's registration chain is then permanently disconnected;
- no need to modify or flash `vendor_boot`;
- does not rely on directly locating the runtime function addresses of `vr.ko`.

`docs/04-vivo-antroot-vr.md` in `janewas/cve-2026-98260-vivo-gki-research`
also confirms that vivo `vr.ko` checks and terminates the privilege-escalation process from `__tracepoint_sys_exit`.

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

Once the full chain succeeds with a zero-write at this address, the child process is no longer killed by the `sys_exit`
probe of `vr.ko`, and the KernelSU late-load succeeds.
