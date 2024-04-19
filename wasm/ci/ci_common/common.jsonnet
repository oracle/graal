local root_ci = import '../ci.jsonnet';

local wasm_suite_root = root_ci.wasm_suite_root;

local graal_suite_root = root_ci.graal_suite_root;

{
  local common = (import "../../../ci/ci_common/common.jsonnet"),

  devkits:: common.devkits,

  gate:: {
    targets+: ['gate'],
  },

  daily:: {
    targets+: ['daily'],
    notify_groups:: ['wasm'],
  },

  weekly:: {
    targets+: ['weekly'],
    notify_groups:: ['wasm'],
  },

  bench:: {
    targets+: ['bench'],
  },

  bench_daily:: self.bench + self.daily,
  bench_weekly:: self.bench + self.weekly,

  linux_common:: {
    packages+: {
      llvm: '==8.0.1',
    },
  },

  linux_amd64:: common.linux_amd64 + self.linux_common + {
    packages+: {
      devtoolset: "==11", # GCC 11.2, make 4.3, binutils 2.36, valgrind 3.17
    },
  },
  linux_aarch64:: common.linux_aarch64 + self.linux_common + {
    packages+: {
      devtoolset: "==10", # GCC 10.2.1, make 4.2.1, binutils 2.35, valgrind 3.16.1
    },
  },

  darwin_aarch64:: common.darwin_aarch64,
  darwin_amd64:: common.darwin_amd64,

  windows_common:: {
    packages+: $.devkits["windows-jdk" + self.jdk_version].packages,
  },

  windows_amd64:: common.windows_amd64 + self.windows_common,

  wabt:: {
    downloads+: {
      WABT_DIR: {name: 'wabt', version: '1.0.32', platformspecific: true},
    },
    environment+: {
      WABT_DIR: '$WABT_DIR/bin',
    },
    packages+: if self.os == "linux" then {
      # wabt was built with GCC 8 and needs a newer version of libstdc++.so.6
      # than what is typically available on OL7
      gcc: '==8.3.0',
    } else {},
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

  gate_graalwasm_style:: self.eclipse_jdt + self.gate_graalwasm + {
    environment+: {
      GATE_TAGS: 'style,fullbuild',
    },
  },

  gate_graalwasm_full:: self.wabt + self.setup_common + {
    run+: [
      gate_cmd_full
    ],
    timelimit: '1:00:00',
  },

  gate_graalwasm_emsdk_full:: self.wabt_emsdk + self.setup_emsdk + {
    run+: [
      gate_cmd_full
    ],
    timelimit: '45:00',
  },

  gate_graalwasm_ocaml_full:: self.gate_graalwasm_emsdk_full + self.ocamlbuild,

  gate_graalwasm_coverage:: self.wabt_emsdk + self.setup_emsdk + {
    environment+: {
      GATE_TAGS: 'buildall,coverage',
    },
    run+: [
      gate_cmd_full + ['--jacoco-omit-excluded', '--jacoco-relativize-paths', '--jacoco-omit-src-gen', '--jacoco-format', 'lcov', '--jacocout', 'coverage']
    ],
    teardown+: [
      ['mx', 'sversions', '--print-repositories', '--json', '|', 'coverage-uploader.py', '--associated-repos', '-'],
    ],
    timelimit: '1:30:00',
  },

  bench_graalwasm_emsdk_full:: self.wabt_emsdk + self.setup_emsdk + {
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
    capabilities+: ['e3'],
    timelimit: '1:00:00',
  },

  eclipse_jdt              :: common.deps.pylint + common.deps.eclipse + common.deps.jdt,
  wabt_emsdk               :: self.wabt    + self.emsdk,
  wabt_emsdk_ocamlbuild    :: self.wabt    + self.emsdk + self.ocamlbuild,

}
