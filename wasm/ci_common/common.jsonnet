{
  local jdks = (import "../../common.json").jdks,
  local labsjdk8 = jdks.oraclejdk8,

  jdk8: {
    downloads+: {
      JAVA_HOME: labsjdk8,
    },
  },

  gate: {
    targets+: ['gate'],
  },

  bench: {
    targets+: ['bench', 'post-merge'],
  },

  common: {
    packages+: {
      '00:pip:logilab-common': '==1.4.4',
      '01:pip:astroid': '==1.1.0',
      'pip:pylint': '==1.1.0',
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
    capabilities+: ['linux', 'amd64'],
  },

  eclipse: {
    downloads+: {
      ECLIPSE: {name: 'eclipse', version: '4.5.2.1', platformspecific: true},
    },
    environment+: {
      ECLIPSE_EXE: '$ECLIPSE/eclipse',
    },
  },

  jdt: {
    downloads+: {
      JDT: {name: 'ecj', version: '4.6.1', platformspecific: false},
    },
  },

  wabt: {
    downloads+: {
      WABT_DIR: {name: 'wabt', version: '1.0.12', platformspecific: true},
    },
  },

  emsdk: {
    downloads+: {
      EMSDK_DIR: {name: 'emsdk', version: '1.39.3', platformspecific: true},
    },
    environment+: {
      EMCC_DIR: '$EMSDK_DIR/fastcomp/emscripten/',
      NODE_DIR: '$EMSDK_DIR/node/12.9.1_64bit/',
    },
  },

  local gate_cmd       = ['mx', '--strict-compliance', 'gate', '--strict-mode', '--tags', '${GATE_TAGS}'],
  local gate_cmd_jvmci = ['mx', '--strict-compliance', '--dynamicimports', '/compiler', '--jdk', 'jvmci', 'gate', '--strict-mode', '--tags', '${GATE_TAGS}'],

  setup_common: {
    setup+: [
      ['cd', 'wasm'],
      ['mx', 'sversions'],
    ],
  },

  setup_emsdk: {
    setup+: [
      ['set-export', 'ROOT_DIR', ['pwd']],
      ['set-export', 'EM_CONFIG', '$ROOT_DIR/.emscripten-config'],
      ['cd', '$SUITE'],
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
  },

  gate_graalwasm: self.setup_common + {
    run+: [
      gate_cmd,
    ],
    timelimit: '35:00',
  },

  gate_graalwasm_jvmci: {
    setup+: [
      ['cd', 'wasm'],
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
      ['mx', '--dy', '/compiler', 'build', '--all'],
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

  jdk8_gate_linux_eclipse_jdt : self.jdk8 + self.gate + self.linux + self.eclipse + self.jdt,
  jdk8_gate_linux_wabt        : self.jdk8 + self.gate + self.linux + self.wabt,
  jdk8_gate_linux_wabt_emsdk  : self.jdk8 + self.gate + self.linux + self.wabt + self.emsdk,
  jdk8_bench_linux_wabt_emsdk : self.jdk8 + self.bench + self.linux + self.wabt + self.emsdk,
}
