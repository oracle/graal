local graal_common = import "../../ci/ci_common/common.jsonnet";
local wasm_common = import 'ci_common/common.jsonnet';
local utils = import "../../ci/ci_common/common-utils.libsonnet";

local jdks = {
  jdk21::     graal_common.labsjdk21,
  jdkLatest:: graal_common.labsjdkLatest,
};

local tools_java_home = {
  downloads+: {
    TOOLS_JAVA_HOME: graal_common.jdks_data['oraclejdk21'],
  },
};

jdks + wasm_common +
{
  wasm_suite_root:: 'wasm',

  graal_suite_root:: '/compiler',

  local _builds = [
    # Gates.
    $.jdkLatest + $.linux_amd64     + $.tier1        + $.gate_graalwasm_style       + tools_java_home                                              + {name: 'gate-graalwasm-style-fullbuild' + self.name_suffix},

    $.jdkLatest + $.linux_amd64     + $.tier2        + $.gate_graalwasm_full        + {environment+: {GATE_TAGS: 'build,wasmtest'}}                + {name: 'gate-graalwasm-unittest' + self.name_suffix},
  ] + [
    $.jdkLatest + platform          + $.tier3        + $.gate_graalwasm_full        + {environment+: {GATE_TAGS: 'build,wasmtest'}}                + {name: 'gate-graalwasm-unittest' + self.name_suffix}
    for platform in [$.linux_aarch64, $.windows_amd64, $.darwin_aarch64]
  ] + [
    $.jdkLatest + $.linux_amd64_ol8 + $.tier2        + $.gate_graalwasm_emsdk_full  + {environment+: {GATE_TAGS: 'buildall,wasmextratest'}}        + {name: 'gate-graalwasm-extra-unittest' + self.name_suffix},
    $.jdkLatest + $.linux_amd64_ol8 + $.tier2        + $.gate_graalwasm_emsdk_full  + {environment+: {GATE_TAGS: 'buildall,wasmbenchtest'}}        + {name: 'gate-graalwasm-benchtest' + self.name_suffix},

    $.jdkLatest + $.linux_amd64_ol8 + $.weekly       + $.gate_graalwasm_coverage    + tools_java_home                                              + {name: 'weekly-graalwasm-coverage' + self.name_suffix},

    # Benchmark jobs.
    $.jdkLatest + $.linux_amd64_ol8 + $.bench_daily  + $.bench_graalwasm_emsdk_full + {
      name: 'bench-graalwasm-c-micro' + self.name_suffix,
      environment+: {
        BENCH_RUNNER: 'run-c-micro-benchmarks',
        BENCH_VM: 'server',
        BENCH_VM_CONFIG: 'graal-core',
      },
    },

    $.jdkLatest + $.linux_amd64_ol8 + $.bench_daily  + $.bench_graalwasm_emsdk_full + {
      name: 'bench-graalwasm-wat-micro' + self.name_suffix,
      environment+: {
        BENCH_RUNNER: 'run-wat-micro-benchmarks',
        BENCH_VM: 'server',
        BENCH_VM_CONFIG: 'graal-core',
      },
    },
  ],

  builds: utils.add_defined_in(_builds, std.thisFile),
}
