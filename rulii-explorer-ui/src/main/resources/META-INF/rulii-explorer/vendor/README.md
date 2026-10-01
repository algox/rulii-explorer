# Vendored third-party libraries

Copied in unmodified, pinned, and loaded by the browser as they ship (no build step). Update a
library by replacing its files here and the entries in `../../../THIRD-PARTY-NOTICES.md`.

| Folder | Library | Version | License | Source |
|---|---|---|---|---|
| `lit/` | Lit (`core/lit-core.min.js`, the official no-build bundle: lit-html, lit-element, reactive-element and the core directives) | 3.3.3 | BSD-3-Clause | https://github.com/lit/dist (tag v3.3.3); https://github.com/lit/lit |
| `d3/` | d3 (`dist/d3.min.js` UMD bundle) | 7.9.0 | ISC | https://github.com/d3/d3/tree/v7.9.0 |
| `elk/` | elkjs (`lib/elk-api.js`, `lib/elk-worker.min.js`) | 0.10.0 | EPL-2.0 | https://github.com/kieler/elkjs/tree/v0.10.0 |
| `fonts/` | Newsreader (400–600, italic 400), Hanken Grotesk (400–700), JetBrains Mono (400–500); latin woff2 subsets as served by Google Fonts | Google Fonts v26 / v12 / v24 | OFL-1.1 (one `OFL-*.txt` per font) | see THIRD-PARTY-NOTICES.md |

SHA-256 of the files as vendored:

```
f607f470475d8ab790754cb70f72cdbe2390c4a57ab59df6cdb360eeb72bd87c  lit/lit-core.min.js
e9201eddf1d41d0b62253295d869ce3cf65768f7102b797f02c7f8c876b4a9d5  fonts/HankenGrotesk-latin.woff2
83c005d49d8a6a50474c73a5a36ac0468076e9c4a29da7bdb14995d80560a5be  fonts/JetBrainsMono-latin.woff2
2bf0dbb1a4a052fffcc447bdab56b12049ee804a8a0d1ee04f5e76a6ab89166a  fonts/Newsreader-italic-latin.woff2
6e4f2958c3a7c4a80acde4e5a679abe7e01bc1e30b92be3c7a8b696ef401d101  fonts/Newsreader-latin.woff2
```

Rules for ELK (EPL-2.0, weak copyleft): ship it unmodified as its own files, keep its notices,
say where its source is, and never let a license of ours restrict anyone's rights to ELK. Our own
code only *uses* ELK through its worker protocol, so it is not a modified work.

The import map in `../index.html` maps the bare specifier `lit` to `lit/lit-core.min.js`; d3 and
ELK are classic UMD scripts, loaded only by the graph views.
