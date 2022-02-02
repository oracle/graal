local root_ci = import '../ci.jsonnet';

local wasm_suite_root = root_ci.wasm_suite_root;

local graal_suite_root = root_ci.graal_suite_root;

{
  local mx = (import "../../graal-common.json").mx_version,
  local common = (import "../../common.jsonnet"),

  devkits: (import "../../common.json").devkits,

  gate: {
    targets+: ['gate'],
  },

  bench: {
    targets+: ['bench', 'post-merge'],
  },

  common: {
    environment+: {
      MX_PYTHON: 'python3',
    },
    packages+: {
      'mx': mx,
      '00:pip:logilab-common': '==1.4.4',
      'pip:pylint': '==1.9.3',
      'pip:ninja_syntax': '==1.7.2',
    },
  },

  linux: self.common + {
    packages+: {
      binutils: '>=2.30',
      git: '>=1.8.3',
      gcc: '==8.3.0',
      'gcc-build-essentials': '==8.3.0', # GCC 4.9.0 fails on cluster
      make: '>=3.83',
      llvm: '==8.0.1',
      nodejs: '==8.9.4',
    },
    capabilities+: ['linux'],
  },

  windows: self.common + {
    capabilities+: ['windows'],
  },

  amd64: {
    capabilities+: ['amd64'],
  },

  aarch64: {
    capabilities+: ['aarch64'],
    timelimit: '1:30:00'
  },

  eclipse: {
    downloads+: {
      ECLIPSE: {name: 'eclipse', version: '4.14.0', platformspecific: true},
    },
    environment+: {
      ECLIPSE_EXE: '$ECLIPSE/eclipse',
    },
  },

  jdt: {
    downloads+: {
      JDT: {name: 'ecj', version: '4.14.0', platformspecific: false},
    },
  },

  wabt: {
    downloads+: {
      WABT_DIR: {name: 'wabt', version: '1.0.23', platformspecific: true},
    },
  },

  emsdk: {
    docker: {
      "image": "phx.ocir.io/oraclelabs2/c_graal/buildslave:buildslave_ol7",
      "mount_modules": true
    },
    downloads+: {
      EMSDK_DIR: {name: 'emsdk', version: '1.39.13', platformspecific: true},
    },
    environment+: {
      EMCC_DIR: '$EMSDK_DIR/emscripten/master/'
    }
  },

  ocamlbuild: {
    docker: {
      "image": "phx.ocir.io/oraclelabs2/c_graal/buildslave:buildslave_ol7",
      "mount_modules": true
    },
    downloads+: {
      OCAML_DIR: {name: 'ocamlbuild', version: '0.14.0', platformspecific: true},
    },
    environment+: {
      PATH: "$OCAML_DIR/bin:$PATH",
      OCAMLLIB: "$OCAML_DIR/lib/ocaml"
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
  },

  wasm_unittest: {
    environment+: {
        "MX_TEST_RESULTS_PATTERN": "es-XXX.json",
        "MX_TEST_RESULT_TAGS": "wasm"
    },
    logs+: ["*/es-*.json"]
  },

  jdk17_gate_linux_eclipse_jdt              : common.labsjdk17 + self.gate  + self.linux   + self.eclipse + self.jdt,
  jdk17_gate_linux_wabt                     : common.labsjdk17 + self.gate  + self.linux   + self.wabt,
  jdk17_gate_linux_wabt_emsdk               : common.labsjdk17 + self.gate  + self.linux   + self.wabt    + self.emsdk,
  jdk17_gate_linux_wabt_emsdk_ocamlbuild    : common.labsjdk17 + self.gate  + self.linux   + self.wabt    + self.emsdk + self.ocamlbuild,
  jdk17_bench_linux_wabt_emsdk              : common.labsjdk17 + self.bench + self.linux   + self.wabt    + self.emsdk,
  jdk17_gate_windows_wabt                   : common.labsjdk17 + self.gate  + self.windows + self.wabt,

  jdk11_gate_linux_wabt                     : common.labsjdk11 + self.gate  + self.linux   + self.wabt,
}
