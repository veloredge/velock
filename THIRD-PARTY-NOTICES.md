# Third-party notices

VeLock ships two typefaces. Both are licensed under the SIL Open Font License,
Version 1.1, which permits bundling and redistribution inside an application.

| Font | Copyright | License |
| --- | --- | --- |
| Inter | Copyright 2020 The Inter Project Authors (https://github.com/rsms/inter) | [`licenses/Inter-OFL.txt`](licenses/Inter-OFL.txt) |
| Space Grotesk | Copyright 2020 The Space Grotesk Project Authors (https://github.com/floriankarsten/space-grotesk) | [`licenses/SpaceGrotesk-OFL.txt`](licenses/SpaceGrotesk-OFL.txt) |

Only the glyphs and weights the interface actually uses are shipped. The
variable originals were subsetted to Latin and the required punctuation, then
pinned to single instances with `fontTools` (4.63): `inter_regular.ttf` (400),
`inter_medium.ttf` (500) and `display_bold.ttf` (700). This is a permitted
modification under the OFL; the copyright notices inside each font are retained,
no Reserved Font Name is declared by either project, and nothing about the
letterforms changed.

The subsetting is reproducible:

```bash
python -m fontTools.subset Inter[wght].ttf --unicodes="U+0020-007E,U+00A0,U+00B7,U+2013,U+2014,U+2018-201D,U+2022,U+2026" \
        --layout-features="kern,liga,clig,calt,locl,ccmp,mark,mkmk" --name-IDs="*" --drop-tables+=DSIG
python -m fontTools.varLib.instancer subset.ttf wght=400 opsz=14 --output=inter_regular.ttf
```

It takes the bundle from 990 KB to 75 KB.

No other third-party code, libraries or assets are included. The application is
built exclusively on Android framework APIs and contains no dependencies.
