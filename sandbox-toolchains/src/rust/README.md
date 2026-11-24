# Sandboxed Rust Toolchain

The toolchain builds Rust 1.100.0 against the GraalOS LLVM implementation.
The compiler uses the `nightly` channel setting to enable the unstable options required by the CFI integration, so `rustc --version` reports `1.100.0-nightly`.
It provides the `x86_64-graalos_swcfi-linux-musl` and `x86_64-graalos_hwcfi-linux-musl` targets for software and hardware control-flow integrity (CFI).
The maintained patch in _patches/rust_1.100.0.patch_ adds these targets, enables the matching LLVM sandbox passes, and preserves GraalOS process-spawn support.

## Build

Use the LabsJDK revision pinned by your checkout.
From the public Graal repository, build the installed toolchain with:

```shell
env -u BOOTSTRAP_GRAALVM mx -p sandbox-toolchains build --build-logs=silent --dependencies SANDBOXED_RUST_TOOLCHAIN_INSTALLED
```

You can set `BOOTSTRAP_GRAALVM` to a compatible GraalVM installation when you want to reuse its LLVM toolchain and Native Image builder.
Leave it unset when you want to build those components from the current sources.
The installed Rust wrappers are under _sandbox-toolchains/mxbuild/linux-amd64/SANDBOXED_RUST_TOOLCHAIN_INSTALLED/musl-swcfi/bin_ and _musl-hwcfi/bin_.

MX fetches checksum-pinned upstream source and Rust 1.99.0 bootstrap archives.
The source archive includes vendored Cargo dependencies.
The builder links the MX-cached bootstrap archives into Rust's local cache before invoking `x.py`, so compiler bootstrapping does not require network access or a host Rust installation.

## Test

From the sibling Graal Enterprise repository, run the toolchain gate with:

```shell
env -u BOOTSTRAP_GRAALVM mx -p sandbox-toolchains-enterprise gate --tags test
```

For a Rust-only check after building, use `--tags rust`.
Set `BINSWEEP` to the absolute path of a Binsweep executable to validate the generated SWCFI binaries as part of the Rust gate.
The Rust tests check compiler and Cargo versions, both CFI targets, Cargo procedural macros, indirect calls, panic unwinding, and optimized and link-time-optimized code generation.

Validate the downstream GraalVM distribution from the Enterprise repository with:

```shell
env -u BOOTSTRAP_GRAALVM mx -p vm-enterprise --env ee-graalos-rust build --build-logs=silent --dependencies GRAALVM
env -u BOOTSTRAP_GRAALVM mx -p vm-enterprise --env ee-graalos-rust graalvm-home
```

Run `mx -p build-tools binsweep-graalvm <graalvm-home>` from the sibling GraalOS repository to check the packaged sandboxed libraries.

## Update Rust

1. Update `RUST_SOURCE` in _mx.sandbox-toolchains/suite.py_ to the upstream `rustc-<version>-src.tar.xz` archive and verify its checksum.
2. Read _src/stage0_ in that release and update the three `RUST_BOOTSTRAP_*` resources and `rust.bootstrapSources` cache paths to its pinned compiler date, version, and checksums.
3. Port the maintained GraalOS patch in a separate Rust source tree and regenerate _patches/rust_<version>.patch_ against the unmodified upstream release.
   Keep both CFI modes enabled and check any new compiler-context serialization requirements.
4. Update the expected compiler and Cargo versions in the Enterprise Rust smoke test.
5. Remove the generated _sandbox-toolchains/mxbuild/linux-amd64/rust/amd64_ build directory before rebuilding a different Rust release.
   An incremental build can reuse a tree from the same release, but does not replace an already extracted source tree when you change the archive.
6. Build and run the checks above.
