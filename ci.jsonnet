#GraalWasm
local wasm = import 'wasm/ci.jsonnet';

{
  specVersion: "2",
  builds: wasm.builds
}

