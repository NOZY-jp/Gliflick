# Warder barrier shape

Figma reference: file `KUASBGX7fk25F7B14yKKwH`, revised Frame 3 `14:97`, rough concept Group 7 `15:193`.

The reusable vector is [warder_barrier.svg](../app/src/main/res/raw/warder_barrier.svg). It redraws the idea rather than tracing the rough Figma paths:

- A filled circular core with radius 18.
- Eight isosceles triangles whose apex is at the core center. Their equal side length is 3:2 relative to the core radius (27:18), the apex angle is 22.5°, and the directions are spaced 45° apart.
- A concentric barrier arc spanning 120°. The rough reference only sketches about 90°; the SVG extends the arc symmetrically by 15° at each end. The endpoints are flat, with no rounded caps.
- The arc radius is 68 and stroke width is 7, chosen from the rough reference's proportions because only its sweep angle was specified.
- The shape uses Figma Frame 3's `#C7C7BC` and has a transparent background. The frame background is `#EFEFE5`.

The SVG is rendered into a high-resolution transparent bitmap for the Android Canvas scene and placed in the Frame 3 title backdrop at `(504, 1229)` in the 1080-unit design coordinate system. It fades with the other background marks during menu completion. The native SVG remains the source of truth.
