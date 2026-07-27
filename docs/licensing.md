# Licensing

## Summary

| Component | License | Why |
|-----------|---------|-----|
| **Watch app (this repo)** | **Apache-2.0** | Separate work; talks to Gadgetbridge only across the public wire protocol → not a derivative. |
| **Gadgetbridge (phone side)** | AGPLv3, used **unmodified** | We never fork/modify/distribute it → **no obligation on us**. |

## Why the watch app can be Apache-2.0

**Gadgetbridge is GNU AGPLv3**, but we **use it completely unmodified — we never fork, patch, or
distribute it.** Copyleft attaches to *derivative works* (copying/linking Gadgetbridge code or shipping
a modified Gadgetbridge); it does not reach a separately authored app that merely communicates with
Gadgetbridge across an **arm's-length, documented wire protocol** (NUS + the Bangle.js JSON dialect)
and shares no Gadgetbridge source. So the watch app is **not a derivative** and may carry any license —
we choose **Apache-2.0**.

To keep that clearly true, the watch app is a **separate, independent implementation**: written
against the public Bangle.js wire protocol, with behavior cross-checked against Gadgetbridge's
published protocol — **no Gadgetbridge source code is copied into this repo**.

## Notes
- **We do not fork Gadgetbridge.** The watch speaks a protocol Gadgetbridge already supports (Bangle.js
  over NUS), so there is no reason to — and no AGPL obligation is triggered.
- **AGPL/GPL vs Google Play:** GPL/AGPL apps conflict with Play's anti-tamper terms, which is why
  Gadgetbridge itself ships via **F-Droid**. Not relevant to our Apache-2.0 watch app.

## Sources
[Gadgetbridge LICENSE (AGPLv3)](https://codeberg.org/Freeyourgadget/Gadgetbridge/blob/master/LICENSE) ·
[AGPL-3.0 text](https://www.gnu.org/licenses/agpl-3.0.html) ·
[Common AGPL misconceptions](https://danb.me/blog/common-agpl-misconceptions/)
