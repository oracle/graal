local common = import 'ci_common/common.jsonnet';

{
  local gate_cmd       = ['mx', '--strict-compliance', 'gate', '--strict-mode', '--tags', '${GATE_TAGS}'],
  local gate_cmd_jvmci = ['mx', '--strict-compliance', '--dynamicimports', '/compiler', '--jdk', 'jvmci', 'gate', '--strict-mode', '--tags', '${GATE_TAGS}'],

  local gate_graalwasm = {
    setup+: [
      ['cd', 'wasm'],
      ['mx', 'sversions'],
    ],
    run+: [
      gate_cmd,
    ],
    timelimit: '35:00',
  },

  local gate_graalwasm_jvmci = {
    setup+: [
      ['cd', 'wasm'],
      ['mx', 'sversions'],
    ],
    run+: [
      gate_cmd_jvmci
    ],
    timelimit: '35:00',
  },

  local gate_graalwasm_emsdk_jvmci = {
    setup+: [
      ['set-export', 'ROOT_DIR', ['pwd']],
      ['set-export', 'EM_CONFIG', '$ROOT_DIR/.emscripten-config'],
      ['cd', 'wasm'],
      [
        './generate_em_config',
        '$EM_CONFIG',
        '$EMSDK_DIR/myfastcomp/emscripten-fastcomp/bin/',
        '$EMSDK_DIR/myfastcomp/old-binaryen/',
        '$EMSDK_DIR/fastcomp/emscripten/',
        ['which', 'node'],
      ],
      ['mx', 'sversions'],
    ],
    run+: [
      gate_cmd_jvmci
    ],
    timelimit: '35:00',
  },

  local jdk8_gate_linux_wabt        = common.jdk8 + common.gate + common.linux + common.wabt,
  local jdk8_gate_linux_wabt_emsdk  = common.jdk8 + common.gate + common.linux + common.wabt + common.emsdk,
  local jdk8_gate_linux_eclipse_jdt = common.jdk8 + common.gate + common.linux + common.eclipse + common.jdt,

  builds: [
    jdk8_gate_linux_eclipse_jdt + gate_graalwasm             + {environment+: {GATE_TAGS: 'style,fullbuild'}}         + {name: 'gate-graalwasm-style-fullbuild-linux-amd64'},
    jdk8_gate_linux_wabt        + gate_graalwasm_jvmci       + {environment+: {GATE_TAGS: 'build,wasmtest'}}          + {name: 'gate-graalwasm-unittest-linux-amd64'},
    jdk8_gate_linux_wabt_emsdk  + gate_graalwasm_emsdk_jvmci + {environment+: {GATE_TAGS: 'buildall,wasmextratest'}}  + {name: 'gate-graalwasm-extra-unittest-linux-amd64'},
  ],
}
