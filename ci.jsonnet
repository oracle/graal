# Compiler
local compiler = import 'compiler/ci.jsonnet';

# GraalWasm
local wasm = import 'wasm/ci.jsonnet';

# Espresso
local espresso = import 'espresso/ci.jsonnet';

# Sulong
local sulong = import 'sulong/ci.jsonnet';
{
  # ensure that entries in common.jsonnet can be resolved
  _checkCommon: (import 'common.jsonnet'),
  ci_resources:: (import 'ci-resources.libsonnet'),
  specVersion: "2",
  builds: compiler.builds + wasm.builds + espresso.builds + sulong.builds
}
