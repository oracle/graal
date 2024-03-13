local graal_common = import "../../ci/ci_common/common.jsonnet";
local wasm_common = import 'ci_common/common.jsonnet';

local jdks = {
  jdk17:: graal_common.labsjdk17,
};

jdks + wasm_common +
{
  wasm_suite_root:: 'wasm',

  graal_suite_root:: '/compiler',

  builds: [
    # Gates.
    $.jdk17 + $.linux_amd64     + $.gate         + $.gate_graalwasm_style                                                                      + {name: 'gate-graalwasm-style-fullbuild' + self.name_suffix},

    $.jdk17 + $.linux_amd64     + $.gate         + $.gate_graalwasm_full        + {environment+: {GATE_TAGS: 'build,wasmtest'}}                + {name: 'gate-graalwasm-unittest' + self.name_suffix},
    $.jdk17 + $.windows_amd64   + $.gate         + $.gate_graalwasm_full        + {environment+: {GATE_TAGS: 'build,wasmtest'}}                + {name: 'gate-graalwasm-unittest' + self.name_suffix},
    $.jdk17 + $.darwin_aarch64  + $.gate         + $.gate_graalwasm_full        + {environment+: {GATE_TAGS: 'build,wasmtest'}}                + {name: 'gate-graalwasm-unittest' + self.name_suffix},

    $.jdk17 + $.linux_amd64     + $.gate         + $.gate_graalwasm_emsdk_full  + {environment+: {GATE_TAGS: 'buildall,wasmextratest'}}        + {name: 'gate-graalwasm-extra-unittest' + self.name_suffix},
    $.jdk17 + $.linux_amd64     + $.gate         + $.gate_graalwasm_emsdk_full  + {environment+: {GATE_TAGS: 'buildall,wasmbenchtest'}}        + {name: 'gate-graalwasm-benchtest' + self.name_suffix},

    $.jdk17 + $.linux_amd64     + $.weekly       + $.gate_graalwasm_coverage                                                                   + {name: 'weekly-graalwasm-coverage' + self.name_suffix},

    # Benchmark jobs.
    $.jdk17 + $.linux_amd64     + $.bench_daily  + $.bench_graalwasm_emsdk_full + {
      name: 'bench-graalwasm-c-micro' + self.name_suffix,
      environment+: {
        BENCH_RUNNER: 'run-c-micro-benchmarks',
        BENCH_VM: 'server',
        BENCH_VM_CONFIG: 'graal-core',
      },
    },
  ],
}
