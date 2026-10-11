# Pinned Rust crate source archives

These 129 `.crate` archives are the crates.io source packages used by the
Android arm64 OpenGL build of the pinned librashader 0.11.0 snapshot in
`../librashader/`. `SHA256SUMS.txt` records each archive hash; all 129 hashes
were checked against the corresponding package checksums in
`../librashader/Cargo.lock`. They are corresponding source, not APK assets.

The selected dependency closure was obtained with:

```sh
cargo tree --manifest-path ../librashader/librashader-capi/Cargo.toml \
  --locked --offline --no-default-features --features runtime-opengl \
  --target aarch64-linux-android --edges normal,build
```

Cargo builds the locked packages from its registry cache; the checked archives
are retained here so the source for a distributed binary does not depend on
registry availability. Review changes to `Cargo.lock`, the archives, and the
license list together when updating the runtime.
