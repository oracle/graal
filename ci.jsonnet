# Compiler
local compiler = import 'compiler/ci.jsonnet';

# GraalWasm
local wasm = import 'wasm/ci.jsonnet';

# Espresso
local espresso = import 'espresso/ci.jsonnet';

# Sulong
local sulong = import 'sulong/ci.jsonnet';

# JavaDoc
local javadoc = import "ci_includes/publish-javadoc.jsonnet";

# Add a guard to `build` that prevents it from running in the gate
# for a PR that only touches *.md files, the docs, are config files for GitHub.
local add_excludes_guard(build) = build + {
  guard+: {
    excludes+: ["**.md", "docs/**", ".github/**"]
  }
};

{
  # ensure that entries in common.jsonnet can be resolved
  _checkCommon: (import 'common.jsonnet'),
  ci_resources:: (import 'ci-resources.libsonnet'),
  specVersion: "2",
  builds: [add_excludes_guard(b) for b in (
    compiler.builds +
    wasm.builds +
    espresso.builds +
    sulong.builds +
    javadoc.builds
  )]
}
