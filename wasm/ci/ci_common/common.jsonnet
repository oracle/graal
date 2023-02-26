local root_ci = import '../ci.jsonnet';

local wasm_suite_root = root_ci.wasm_suite_root;

local graal_suite_root = root_ci.graal_suite_root;

{
  local common = (import "../../../ci/ci_common/common.jsonnet"),

  devkits:: common.devkits,

  gate:: {
    targets+: ['gate'],
  },

  bench:: {
    targets+: ['bench', 'daily'],
  },

  local linux_common = {
    packages+: {
      "01:binutils": '>=2.30',
      gcc: '==8.3.0',
      'gcc-build-essentials': '==8.3.0', # GCC 4.9.0 fails on cluster
      make: '>=3.83',
      llvm: '==8.0.1',
      nodejs: '==8.9.4',
    },
  },

  linux_amd64:: common.linux_amd64 + linux_common,
  linux_aarch64:: common.linux_aarch64 + linux_common,

  darwin_aarch64:: common.darwin_aarch64,
  darwin_amd64:: common.darwin_amd64 + {
    capabilities+: ['darwin_catalina'],
  },

  local windows_common = {
    packages+: $.devkits["windows-jdk" + self.jdk_version].packages,
  },

  windows_amd64:: common.windows_amd64 + windows_common,

  wabt:: {
    downloads+: {
      WABT_DIR: {name: 'wabt', version: '1.0.32', platformspecific: true},
    },
    environment+: {
      WABT_DIR: '$WABT_DIR/bin',
    },
  },

  emsdk:: {
    downloads+: {
      EMSDK_DIR: {name: 'emsdk', version: '1.39.13', platformspecific: true},
    },
    environment+: {
      EMCC_DIR: '$EMSDK_DIR/emscripten/master/'
    }
  },

  ocamlbuild:: {
    downloads+: {
      OCAML_DIR: {name: 'ocamlbuild', version: '0.14.0', platformspecific: true},
    },
    environment+: {
      PATH: "$OCAML_DIR/bin:$PATH",
      OCAMLLIB: "$OCAML_DIR/lib/ocaml"
    },
  },

  nodejs:: {
    downloads+: {
      NODE: {name: 'node', version: 'v16.13.2', platformspecific: true},
    },
    environment+: {
      NODE_DIR: '${NODE}/bin',
      PATH: '${NODE}/bin:${PATH}',
    },
  },

  local gate_cmd      = ['mx', 'gate', '--strict-mode', '--tags', '${GATE_TAGS}'],
  local gate_cmd_full = ['mx', '--dynamicimports', graal_suite_root, 'gate', '--strict-mode', '--tags', '${GATE_TAGS}'],

  common:: {
    name_suffix:: (if std.objectHasAll(self, 'jdk_version') then '-jdk' + self.jdk_version else '') + '-' + self.os + '-' + self.arch,
  },

  setup_common:: self.common + {
    setup+: [
      ['cd', wasm_suite_root],
      ['mx', 'sversions'],
    ],
  },

  setup_emsdk:: self.setup_common + {
    setup+: [
      ['set-export', 'ROOT_DIR', ['pwd']],
      ['set-export', 'EM_CONFIG', '$ROOT_DIR/.emscripten-config'],
      ['mx', 'emscripten-init', '$EM_CONFIG', '$EMSDK_DIR']
    ],
  },

  gate_graalwasm:: self.setup_common + {
    run+: [
      gate_cmd,
    ],
    timelimit: '45:00',
  },

  gate_graalwasm_full:: self.setup_common + {
    run+: [
      gate_cmd_full
    ],
    timelimit: '1:00:00',
  },

  gate_graalwasm_emsdk_full:: self.setup_emsdk + {
    run+: [
      gate_cmd_full
    ],
    timelimit: '45:00',
  },

  bench_graalwasm_emsdk_full:: self.setup_emsdk + {
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

  gate_eclipse_jdt              :: self.gate  + common.deps.eclipse + common.deps.jdt,
  gate_wabt                     :: self.gate  + self.wabt,
  gate_wabt_emsdk               :: self.gate  + self.wabt    + self.emsdk,
  gate_wabt_emsdk_ocamlbuild    :: self.gate  + self.wabt    + self.emsdk + self.ocamlbuild,

  bench_wabt_emsdk              :: self.bench + self.wabt    + self.emsdk,
  bench_wabt_emsdk_nodejs       :: self.bench + self.wabt    + self.emsdk + self.nodejs,

}
