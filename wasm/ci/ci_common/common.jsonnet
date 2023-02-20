local root_ci = import '../ci.jsonnet';

local wasm_suite_root = root_ci.wasm_suite_root;

local graal_suite_root = root_ci.graal_suite_root;

{
  local common = (import "../../../ci/ci_common/common.jsonnet"),

  devkits: common.devkits,

  gate: {
    targets+: ['gate'],
  },

  bench: {
    targets+: ['bench', 'daily'],
  },

  linux_amd64: common.linux_amd64 + {
    packages+: {
      "01:binutils": '>=2.30',
      gcc: '==8.3.0',
      'gcc-build-essentials': '==8.3.0', # GCC 4.9.0 fails on cluster
      make: '>=3.83',
      llvm: '==8.0.1',
      nodejs: '==8.9.4',
    },
  },

  windows_amd64: common.windows_amd64,

  wabt: {
    downloads+: {
      WABT_DIR: {name: 'wabt', version: '1.0.23', platformspecific: true},
    },
  },

  emsdk: {
    downloads+: {
      EMSDK_DIR: {name: 'emsdk', version: '1.39.13', platformspecific: true},
    },
    environment+: {
      EMCC_DIR: '$EMSDK_DIR/emscripten/master/'
    }
  },

  ocamlbuild: {
    downloads+: {
      OCAML_DIR: {name: 'ocamlbuild', version: '0.14.0', platformspecific: true},
    },
    environment+: {
      PATH: "$OCAML_DIR/bin:$PATH",
      OCAMLLIB: "$OCAML_DIR/lib/ocaml"
    },
  },

  nodejs: {
    downloads+: {
      NODE: {name: 'node', version: 'v16.13.2', platformspecific: true},
    },
    environment+: {
      NODE_DIR: '${NODE}/bin',
      PATH: '${NODE}/bin:${PATH}',
    },
  },

  local gate_cmd      = ['mx', '--strict-compliance', 'gate', '--strict-mode', '--tags', '${GATE_TAGS}'],
  local gate_cmd_full = ['mx', '--strict-compliance', '--dynamicimports', graal_suite_root, 'gate', '--strict-mode', '--tags', '${GATE_TAGS}'],

  setup_common: {
    setup+: [
      ['cd', wasm_suite_root],
      ['mx', 'sversions'],
    ],
  },

  setup_emsdk: self.setup_common + {
    setup+: [
      ['set-export', 'ROOT_DIR', ['pwd']],
      ['set-export', 'EM_CONFIG', '$ROOT_DIR/.emscripten-config'],
      ['mx', 'emscripten-init', '$EM_CONFIG', '$EMSDK_DIR']
    ],
  },

  gate_graalwasm: self.setup_common + {
    run+: [
      gate_cmd,
    ],
    timelimit: '45:00',
  },

  gate_graalwasm_full: {
    setup+: [
      ['cd', wasm_suite_root],
      ['mx', 'sversions'],
    ],
    run+: [
      gate_cmd_full
    ],
    timelimit: '1:00:00',
  },

  gate_graalwasm_emsdk_full: self.setup_emsdk + {
    run+: [
      gate_cmd_full
    ],
    timelimit: '45:00',
  },

  bench_graalwasm_emsdk_full: self.setup_emsdk + {
    environment+: {
      BENCH_RESULTS_FILE_PATH : 'bench-results.json',
    },
    setup+: [
      ['mx', '--dy', graal_suite_root, 'build', '--all'],
    ],
    run+: [
      [
        'scripts/${BENCH_RUNNER}',
        '${BENCH_RESULTS_FILE_PATH}',
        '${BENCH_VM}',
        '${BENCH_VM_CONFIG}',
        'bench-uploader.py',
      ]
    ],
    logs: ['bench-results.json'],
    capabilities+: ['x52'],
    timelimit: '1:00:00',
  },

  jdk17_gate_linux_amd64_eclipse_jdt              : common.labsjdk17 + self.gate  + self.linux_amd64  + common.deps.eclipse + common.deps.jdt,
  jdk17_gate_linux_amd64_wabt                     : common.labsjdk17 + self.gate  + self.linux_amd64   + self.wabt,
  jdk17_gate_linux_amd64_wabt_emsdk               : common.labsjdk17 + self.gate  + self.linux_amd64   + self.wabt    + self.emsdk,
  jdk17_gate_linux_amd64_wabt_emsdk_ocamlbuild    : common.labsjdk17 + self.gate  + self.linux_amd64   + self.wabt    + self.emsdk + self.ocamlbuild,
  jdk17_bench_linux_amd64_wabt_emsdk              : common.labsjdk17 + self.bench + self.linux_amd64   + self.wabt    + self.emsdk,
  jdk17_bench_linux_amd64_wabt_emsdk_nodejs       : common.labsjdk17 + self.bench + self.linux_amd64   + self.wabt    + self.emsdk + self.nodejs,
  jdk17_gate_windows_amd64_wabt                   : common.labsjdk17 + self.gate  + self.windows_amd64 + self.wabt,
}
