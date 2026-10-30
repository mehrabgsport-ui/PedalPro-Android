# RC29 Native AMOLED navigation
- A battery button on every native map switches between standard and AMOLED-optimized styles; preference is remembered locally.
- Genuine Neshan MapLibre vector styles recolor background and polygons black, road line layers bright, other lines dim, labels muted. The recorded rider/route annotations remain intact; GPS is unchanged.
- XYZ raster fallback cannot isolate road geometry from a bitmap; it displays a black base with the GPS track and cyan navigation route only. For full highlighted roads, the configured Neshan vector style is required.
- Real device test of Neshan layer IDs, 3D tilt, style reload and energy savings is pending.
