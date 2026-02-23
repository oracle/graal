# AGENTS.md

## Orientation
- GraalVM is split into `compiler/`, `espresso/`, `espresso-compiler-stub/`, `espresso-shared/`, `regex/`, `sdk/`, `substratevm/`, `sulong/`, `tools/`, `truffle/`, `visualizer/`, `vm/`, `wasm/`, and `web-image/`; each suite is managed with `mx`.
- Read the local `AGENTS.md` or `README.md` for the suite you change.

## Build & Quality
- Run `mx` from the relevant suite directory. Default to the `vm` suite when no better target is obvious, for example `mx -p vm build`.
- Common commands: `mx build`, `mx clean`, `mx unittest`, `mx help`, and `mx help <command>`.

## Docs
- Documentation sources live under `docs/`. Update the matching page for user-facing changes.

## Before Submitting
- [ ] Ensure formatting and checkstyle pass (`mx checkstyle`).
- [ ] Confirm tests or builds relevant to your suite succeed (`mx build`, targeted tests, etc.).
- [ ] Verify documentation updates are included when behavior changes.
- [ ] Flag and ask for extra permission for changes related to security (e.g., touching cryptographic code).
