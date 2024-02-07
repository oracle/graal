local graal_common = import "../../ci/ci_common/common.jsonnet";
local wasm_common = import 'ci_common/common.jsonnet';

local jdks = {
  jdk21:: graal_common.labsjdk21,
};

jdks + wasm_common +
{
  wasm_suite_root:: 'wasm',

  graal_suite_root:: '/compiler',

  builds: [
    # Benchmark jobs.
    $.jdk21 + $.linux_amd64     + $.bench_daily  + $.bench_graalwasm_emsdk_full + {
      name: 'bench-graalwasm-c-micro' + self.name_suffix,
      environment+: {
        BENCH_RUNNER: 'run-c-micro-benchmarks',
        BENCH_VM: 'server',
        BENCH_VM_CONFIG: 'graal-core',
      },
    },
  ],
}
