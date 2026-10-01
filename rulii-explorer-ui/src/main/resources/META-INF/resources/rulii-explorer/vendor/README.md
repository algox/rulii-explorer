# Vendored third-party libraries

Copied in unmodified, pinned, and loaded by the browser as they ship (no build step). Update a
library by replacing its files here and the entries in `../../../../THIRD-PARTY-NOTICES.md`.

| Folder | Library | Version | License | Source |
|---|---|---|---|---|
| `d3/` | d3 (`dist/d3.min.js` UMD bundle) | 7.9.0 | ISC | https://github.com/d3/d3/tree/v7.9.0 |
| `elk/` | elkjs (`lib/elk-api.js`, `lib/elk-worker.min.js`) | 0.10.0 | EPL-2.0 | https://github.com/kieler/elkjs/tree/v0.10.0 |

Rules for ELK (EPL-2.0, weak copyleft): ship it unmodified as its own files, keep its notices,
say where its source is, and never let a license of ours restrict anyone's rights to ELK. Our own
code only *uses* ELK through its worker protocol, so it is not a modified work.

Lit arrives with the UI foundation (M3).
