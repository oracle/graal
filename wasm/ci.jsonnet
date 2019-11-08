{
  local base = {
      local labsjdk8 = {name: 'oraclejdk', version: '8u231-jvmci-19.3-b04', platformspecific: true},

      jdk8: {
        downloads+: {
          JAVA_HOME: labsjdk8,
        },
      },

      gate:        {targets+: ['gate']},

      common: {
        packages+: {
          'pip:astroid': '==1.1.0',
          'pip:pylint': '==1.1.0',
          'pip:ninja_syntax': '==1.7.2',
        },
      },

      linux: self.common + {
        packages+: {
          binutils: '>=2.30',
          git: '>=1.8.3',
          gcc: '>=4.9.1',
          'gcc-build-essentials': '>=4.9.1', # GCC 4.9.0 fails on cluster
          make: '>=3.83',
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
  },

  local gate_cmd = ['mx', '--strict-compliance', 'gate', '--strict-mode', '--tags', '${GATE_TAGS}'],

  local gate_graalwasm = {
    setup+: [
      ['cd', 'wasm'],
      ['mx', 'sversions'],
    ],
    run+: [
      gate_cmd,
    ],
    timelimit: '15:00',
  },

  local jdk8_gate_linux_eclipse_jdt = base.jdk8 + base.gate   + base.linux + base.eclipse + base.jdt,

  builds: [
    jdk8_gate_linux_eclipse_jdt + gate_graalwasm         + {environment+: {GATE_TAGS: 'style,fullbuild'}} + {name: 'graalwasm-style-fullbuild-linux-amd64'},
  ]
}