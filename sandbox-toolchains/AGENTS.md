# AGENTS.md

## Scope
- This directory is an `mx` suite for building the sandboxed LLVM, musl, and Rust toolchains used by GraalOS and downstream GraalVM builds.
- Run commands with `mx -p sandbox-toolchains ...` from the git repo root, or run `mx ...` directly from this directory.
- `mx` is not concurrency-safe here. Do not run two `mx` commands against this suite at the same time.

## Main Build Targets
- Prefer targeted builds: `mx build --dependencies <dist>`. Avoid a blanket `mx build` unless you really want the whole suite.
- Primary distributions:
  - `SANDBOXED_MUSL_TOOLCHAIN`: final C/C++/Fortran toolchain bundle.
  - `SANDBOXED_MUSL_TOOLCHAIN_STAGE1`: stage1 toolchain used to build extra libraries and as an intermediate debugging point.
  - `SANDBOXED_RUST_TOOLCHAIN`: Rust install payload plus Rust wrappers.
  - `SANDBOXED_RUST_TOOLCHAIN_INSTALLED`: final Rust toolchain install combined with the final musl toolchain.
- Typical entry points:
  - `mx -p sandbox-toolchains build --dependencies SANDBOXED_MUSL_TOOLCHAIN_STAGE1`
  - `mx -p sandbox-toolchains build --dependencies SANDBOXED_MUSL_TOOLCHAIN`
  - `mx -p sandbox-toolchains build --dependencies SANDBOXED_RUST_TOOLCHAIN_INSTALLED`
- For compiler, linker, sysroot, and wrapper debugging, start with `SANDBOXED_MUSL_TOOLCHAIN_STAGE1`. It builds much faster than the final toolchain and is usually the right first checkpoint.
- Use the final `SANDBOXED_MUSL_TOOLCHAIN` before concluding a change is correct if the change might affect bundled libraries, final launcher packaging, Rust integration, or downstream GraalVM builds.

## Build Pipeline
- The musl toolchain is assembled in stages. The important order is:
  - `sandbox-llvm` / `SANDBOX_LLVM`
  - `bootstrap-compiler-rt-[swcfi|hwcfi|nocfi]`
  - `BOOTSTRAP_RESOURCE_DIR`
  - `sandboxed-musl-[swcfi|hwcfi|nocfi]`
  - `BOOTSTRAP_MUSL_SYSROOT`
  - `llvm-cxx-runtimes-musl-[swcfi|hwcfi|nocfi]` (libc++, libc++abi, libunwind, and OpenMP)
  - `BOOTSTRAP_MUSL_SYSROOT_CXX`
  - `llvm-runtimes-musl-[swcfi|hwcfi|nocfi]` (compiler-rt and flang-rt)
  - `SANDBOXED_MUSL_TOOLCHAIN_STAGE1`
  - extra libraries such as zlib and mimalloc
  - `SANDBOXED_MUSL_TOOLCHAIN`
- The Rust build consumes `RUST_SOURCE`, applies patches from `src/rust/patches`, builds against `SANDBOXED_MUSL_TOOLCHAIN`, and produces `SANDBOXED_RUST_TOOLCHAIN`.
- `SANDBOXED_RUST_TOOLCHAIN_INSTALLED` is the installed Rust toolchain merged with `SANDBOXED_MUSL_TOOLCHAIN/toolchains/*`.

## Output Layout
- Final musl toolchain output lives under `mxbuild/<arch>/SANDBOXED_MUSL_TOOLCHAIN/toolchains/`.
- Final Rust toolchain install lives under `mxbuild/<arch>/SANDBOXED_RUST_TOOLCHAIN_INSTALLED/`.
- Expected top-level toolchain directories in the musl bundle:
  - `toolchains/llvm`
  - `toolchains/musl-swcfi`
  - `toolchains/musl-hwcfi`
  - `toolchains/musl-nocfi`
- The Rust-installed bundle exposes the Rust toolchain at:
  - `llvm/`
  - `musl-swcfi/bin`
  - `musl-hwcfi/bin`

## Rust Source Maintenance
- The Rust build does not compile directly from a live checkout. It starts from the `RUST_SOURCE` tarball and applies patch files from `src/rust/patches`.
- If Rust source changes are needed, update the maintained Rust source tree used for patch generation, then regenerate the patch file in `src/rust/patches` so the mx build continues to apply the change from a clean tarball.
- If patch application starts failing because the extracted work tree no longer matches the expected patched or unpatched state, clean the Rust build output and rebuild.

## Rebuild Hygiene
- The first `sandbox-llvm` build is expensive. Incremental rebuilds are much faster and are the normal workflow.
- When iterating on early toolchain stages, prefer rebuilding up to `SANDBOXED_MUSL_TOOLCHAIN_STAGE1` first. That keeps turnaround much lower than rebuilding the fully packaged final toolchain on every attempt.
- If incremental state looks suspicious, clean manually before retrying. In practice, removing `mxbuild` trees under the affected Graal and Graal Enterprise suites can be enough.
- Be careful with alternate worktrees and symlink-heavy experiments. Shared build outputs across worktrees can contaminate results and make debugging harder.
- Rust rebuilds are also expensive because the suite builds and installs a patched compiler plus cargo/std artifacts.
