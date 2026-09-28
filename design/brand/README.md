# rulii brand assets

Vector (SVG) redraws of the rulii logo from rulii.com (`rulii-docs/assets/images/logo.png`
and `rulii-logo.png`). The geometry and gradient colours were measured from those PNGs. The
brand palette is documented in [SOLUTION.md §11.1](../../docs/SOLUTION.md).

| File | Use |
|---|---|
| `rulii-logo.svg` | The logo as on rulii.com, on its deep-purple (`#301549`) square |
| `rulii-logo-on-dark.svg` | Transparent background, white wordmark: dark top bars and the dark theme |
| `rulii-logo-on-light.svg` | Transparent background, deep-purple wordmark: light backgrounds |
| `rulii-mark.svg` | The arcs alone on a rounded deep-purple square: favicon and app icon |

## Construction

- **Arcs:** three quarter-circle strokes, 10 units wide, with butt ends, each running from a
  vertical stem to a horizontal end at x = 84. Each has its own gradient along its length:
  - outer: purple → pink → orange
  - middle: orange → pink → purple
  - inner: magenta → orange
- **Wordmark:** drawn at 422 × 248 units (the size of `rulii-logo.png`) with 35-unit strokes,
  then scaled by 0.24 into the logo. The dot over the first i is orange (`#F5841D`), the dot
  over the second is purple (`#833EAE`).
- **Gradient IDs** carry a per-file prefix (`rl-`, `rld-`, `rll-`, `rlm-`), so several logos
  can be inlined on one page without their gradients clashing.

## Usage notes

- Keep the logo's proportions. Scale it, don't stretch it.
- Minimum size: 28 px tall for the full logo (the wordmark is small relative to the arcs).
  Below that, use `rulii-mark.svg`.
- On mid-tone backgrounds, use the version whose wordmark contrasts best; never recolour
  the arcs.
