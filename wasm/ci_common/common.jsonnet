local root_ci = import '../ci.jsonnet';

local wasm_suite_root = root_ci.wasm_suite_root;

local graal_suite_root = root_ci.graal_suite_root;

{
  local jdks = (import "../../common.json").jdks,
  local labsjdk8 = jdks.oraclejdk8,
  local labsjdk11 = jdks["labsjdk-ce-11"],

  jdk8: {
    downloads+: {
      JAVA_HOME: labsjdk8,
    },
    environment+: {
      JDK_JVMCI_ARGS: '--jdk=jvmci',
    },
  },

  jdk11: {
    downloads+: {
      JAVA_HOME: labsjdk11,
    },
    environment+: {
      JDK_JVMCI_ARGS: '--jdk=',
    },
  },

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
      WABT_DIR: {name: 'wabt', version: '1.0.12', platformspecific: true},
    },
  },

  emsdk: {
    docker: {
      "image": "phx.ocir.io/oraclelabs2/c_graal/buildslave:b_ol7_2",
      "mount_modules": true
    },
    downloads+: {
      EMSDK_DIR: {name: 'emsdk', version: '1.39.13', platformspecific: true},
    },
    environment+: {
      EMCC_DIR: '$EMSDK_DIR/emscripten/master/'
    }
  },

  local gate_cmd       = ['mx', '--strict-compliance', 'gate', '--strict-mode', '--tags', '${GATE_TAGS}'],
  local gate_cmd_jvmci = ['mx', '--strict-compliance', '--dynamicimports', graal_suite_root, '${JDK_JVMCI_ARGS}', 'gate', '--strict-mode', '--tags', '${GATE_TAGS}'],

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
      ['./generate_em_config', '$EM_CONFIG', '$EMSDK_DIR']
    ],
  },

  gate_graalwasm: self.setup_common + {
    run+: [
      gate_cmd,
    ],
    timelimit: '35:00',
  },

  gate_graalwasm_jvmci: {
    setup+: [
      ['cd', wasm_suite_root],
      ['mx', 'sversions'],
    ],
    run+: [
      gate_cmd_jvmci
    ],
    timelimit: '35:00',
  },

  gate_graalwasm_emsdk_jvmci: self.setup_emsdk + {
    run+: [
      gate_cmd_jvmci
    ],
    timelimit: '35:00',
  },

  bench_graalwasm_emsdk_jvmci: self.setup_emsdk + {
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

  jdk8_gate_linux_eclipse_jdt   : self.jdk8 + self.gate + self.linux + self.eclipse + self.jdt,
  jdk8_gate_linux_wabt          : self.jdk8 + self.gate + self.linux + self.wabt,
  jdk8_gate_linux_wabt_emsdk    : self.jdk8 + self.gate + self.linux + self.wabt + self.emsdk,
  jdk8_bench_linux_wabt_emsdk   : self.jdk8 + self.bench + self.linux + self.wabt + self.emsdk,
  jdk8_gate_windows_wabt        : self.jdk8 + self.gate + self.windows + self.wabt,

  jdk11_gate_linux_wabt         : self.jdk11 + self.gate + self.linux + self.wabt,
}
