# Selected RetroArch Slang shaders

The four files under `assets/retroarch/` are byte-for-byte copies from
`libretro/slang-shaders` commit `e1d75632a205c70f14a4cc947c46d5abb7b3f7f1`:
<https://github.com/libretro/slang-shaders/tree/e1d75632a205c70f14a4cc947c46d5abb7b3f7f1>.

| Built-in preset | Shader author and license stated in source |
| --- | --- |
| `pixel-art-scaling/sharp-bilinear.slangp` | Themaister; public domain. |
| `pixel-art-scaling/sharp-bilinear-scanlines.slangp` | rsn8887 (based on Themaister); public domain. |

SHA-256 of files under `assets/retroarch/`:

```text
059a79b17fe4635420f032cb8c1c42072bf7e9104052f3a6cce564cca17f9b5d  pixel-art-scaling/sharp-bilinear.slangp
89c30132f1530466676a3be76fcdc5af42f808cd56df6571ff65b64e7073ab7e  pixel-art-scaling/shaders/sharp-bilinear.slang
56723e7b562fdf0a11a79a6ef0acb8dd817ec67a6f8093e16c664806c074d4b7  pixel-art-scaling/sharp-bilinear-scanlines.slangp
8d53f1b66db6d5a07fb573c15ae864bf5ad9e69c7bf3a2d31d5703952887c27a  pixel-art-scaling/shaders/sharp-bilinear-scanlines.slang
```

The upstream collection has mixed and sometimes unstated licenses. This review
covers only the four copied files. The frontend packages them in both products
and copies a selected preset and its shader into app-private storage. Their
embedded license notices remain intact.
