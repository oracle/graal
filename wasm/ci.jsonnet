{
  wasm_suite_root: 'wasm',

  graal_suite_root: '/compiler',

  local common = import 'ci_common/common.jsonnet',

  builds: [
    # Gates.
    common.jdk17_gate_linux_eclipse_jdt  + common.gate_graalwasm              + common.amd64   + {environment+: {GATE_TAGS: 'style,fullbuild'}}                       + {name: 'gate-graalwasm-style-fullbuild-linux-amd64'},
    common.jdk17_gate_linux_wabt         + common.gate_graalwasm_full         + common.amd64   + {environment+: {GATE_TAGS: 'build,wasmtest'}}                        + {name: 'gate-graalwasm-unittest-linux-amd64'}       + common.wasm_unittest,
    common.jdk17_gate_linux_wabt_emsdk   + common.gate_graalwasm_emsdk_full   + common.amd64   + {environment+: {GATE_TAGS: 'buildall,wasmextratest'}}                + {name: 'gate-graalwasm-extra-unittest-linux-amd64'} + common.wasm_unittest,
    common.jdk17_gate_linux_wabt_emsdk   + common.gate_graalwasm_emsdk_full   + common.amd64   + {environment+: {GATE_TAGS: 'buildall,wasmbenchtest'}}                + {name: 'gate-graalwasm-benchtest-linux-amd64'},
    common.jdk17_gate_windows_wabt       + common.gate_graalwasm_full         + common.amd64   + {environment+: {GATE_TAGS: 'build,wasmtest'}}                        + {name: 'gate-graalwasm-unittest-windows-amd64', packages+: common.devkits["windows-jdk17"].packages} + common.wasm_unittest,

    common.jdk11_gate_linux_wabt         + common.gate_graalwasm_full         + common.aarch64 + {environment+: {GATE_TAGS: 'build,wasmtest'}}                        + {name: 'gate-graalwasm-unittest-11-linux-aarch64'}  + common.wasm_unittest,

    # Benchmark jobs.
    common.jdk17_bench_linux_wabt_emsdk  + common.bench_graalwasm_emsdk_full  + common.amd64 + {
      name: 'bench-graalwasm-c-micro-linux-amd64',
      environment+: {
        BENCH_RUNNER: 'run-c-micro-benchmarks',
        BENCH_VM: 'server',
        BENCH_VM_CONFIG: 'graal-core',
      },
    },
  ],
}
