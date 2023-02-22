local graal_common = import "../../ci/ci_common/common.jsonnet";
local wasm_common = import 'ci_common/common.jsonnet';

local jdks = {
  jdk17:: graal_common.labsjdk17,
  jdk20:: graal_common.labsjdk20,
};

jdks + wasm_common +
{
  wasm_suite_root:: 'wasm',

  graal_suite_root:: '/compiler',

  local gate_name_prefix = 'gate-graalwasm-',

  builds: [
    # Gates.
    $.jdk17 + $.linux_amd64     + $.gate_eclipse_jdt  + $.gate_graalwasm             + {environment+: {GATE_TAGS: 'style,fullbuild'}}                    + {name: gate_name_prefix + 'style-fullbuild' + self.name_suffix},
    $.jdk20 + $.linux_amd64     + $.gate_eclipse_jdt  + $.gate_graalwasm             + {environment+: {GATE_TAGS: 'style,fullbuild'}}                    + {name: gate_name_prefix + 'style-fullbuild' + self.name_suffix},

    $.jdk17 + $.linux_amd64     + $.gate_wabt         + $.gate_graalwasm_full        + {environment+: {GATE_TAGS: 'build,wasmtest'}}                     + {name: gate_name_prefix + 'unittest' + self.name_suffix},
    $.jdk20 + $.linux_amd64     + $.gate_wabt         + $.gate_graalwasm_full        + {environment+: {GATE_TAGS: 'build,wasmtest'}}                     + {name: gate_name_prefix + 'unittest' + self.name_suffix},
    $.jdk17 + $.windows_amd64   + $.gate_wabt         + $.gate_graalwasm_full        + {environment+: {GATE_TAGS: 'build,wasmtest'}}                     + {name: gate_name_prefix + 'unittest' + self.name_suffix},
    $.jdk17 + $.darwin_aarch64  + $.gate_wabt         + $.gate_graalwasm_full        + {environment+: {GATE_TAGS: 'build,wasmtest'}}                     + {name: gate_name_prefix + 'unittest' + self.name_suffix},

    $.jdk17 + $.linux_amd64     + $.gate_wabt_emsdk   + $.gate_graalwasm_emsdk_full  + {environment+: {GATE_TAGS: 'buildall,wasmextratest'}}             + {name: gate_name_prefix + 'extra-unittest' + self.name_suffix},
    $.jdk17 + $.linux_amd64     + $.gate_wabt_emsdk   + $.gate_graalwasm_emsdk_full  + {environment+: {GATE_TAGS: 'buildall,wasmbenchtest'}}             + {name: gate_name_prefix + 'benchtest' + self.name_suffix},

    # Benchmark jobs.
    $.jdk17 + $.linux_amd64     + $.bench_wabt_emsdk  + $.bench_graalwasm_emsdk_full + {
      name: 'bench-graalwasm-c-micro' + self.name_suffix,
      environment+: {
        BENCH_RUNNER: 'run-c-micro-benchmarks',
        BENCH_VM: 'server',
        BENCH_VM_CONFIG: 'graal-core',
      },
      notify_groups:: ['wasm'],
    },
  ],
}
