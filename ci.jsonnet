# GraalWasm
local wasm = import 'wasm/ci.jsonnet';

# Espresso
local espresso = import 'espresso/ci.jsonnet';

# Sulong
local sulong = import 'sulong/ci.jsonnet';
{
  specVersion: "2",
  builds: wasm.builds + espresso.builds + sulong.builds
}

