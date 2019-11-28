#GraalWasm
local wasm = import 'wasm/ci.jsonnet';

{
  builds: wasm.builds
}

