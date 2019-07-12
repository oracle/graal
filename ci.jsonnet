{

  local linux = common + {
    capabilities: ['linux', 'amd64'],
  },

  local oraclejdk8 = {
    name: 'oraclejdk',
    version: '8u212-jvmci-19.2-b01',
    platformspecific: true,
  },

  local jdk8 = {
    downloads: {
      JAVA_HOME: oraclejdk8,
    },
  },

  local jdt = {
    downloads+: {
      JDT: {name: 'ecj', version: '4.6.1', platformspecific: false},
    },
  },

  local eclipse = {
    downloads+: {
      ECLIPSE: {name: 'eclipse', version: '4.5.2.1', platformspecific: true},
    },
    environment+: {
      ECLIPSE_EXE: '$ECLIPSE/eclipse',
    },
  },

  local gate = {targets: ['gate']},

  local common = {
    packages: {
      'git': '>=1.8.3',
      'pip:astroid': '==1.1.0',
      'pip:pylint': '==1.1.0',

      'make': '>=3.83',
      'gcc-build-essentials': '>=4.9.1', # GCC 4.9.0 fails on cluster
      'binutils': '>=2.30',
    },
    setup+: [
      ['mx', 'sversions'],
    ],
  },

  local espresso_gate = {
    run+: [
      ['mx', '--strict-compliance', 'gate', '--strict-mode', '--tags', '${GATE_TAGS}'],
    ],
    timelimit: '15:00',
  },

  local mx_espresso_svm(ee, args) = ['mx', '--env', 'espresso.svm'] + (


    if ee then ['--dy', '/substratevm-enterprise'] else []


  ) + args,

  local run_espresso_svm(ee, args) = [
    ['set-export', 'GRAALVM_HOME', mx_espresso_svm(ee, ['graalvm-home'])],
    ['set-export', 'ESPRESSO_NATIVE', '${GRAALVM_HOME}/bin/espresso'],
    ['set-export', 'LD_DEBUG', 'unused'],
    ['${ESPRESSO_NATIVE}'] + args,
  ],

  local espresso_svm(ee, args) = {
    run+:
        (if ee then
            [
                ['git', 'clone', '-b', 'slimbeans', '--depth', '1', ['mx', 'urlrewrite', 'https://github.com/oracle/graal-enterprise'], '../graal-enterprise'],
            ] else []
        ) +
    [
      mx_espresso_svm(ee, ['build']),
    ] + run_espresso_svm(ee, args),
  },

  local hello_world = ['-cp', 'mxbuild/dists/jdk1.8/espresso-playground.jar', 'com.oracle.truffle.espresso.playground.HelloWorld'],

  local espresso_svm_hello_world(ee) = espresso_svm(ee, hello_world) + { timelimit: '15:00' },

  local espresso_meta = {
    run+: [
        ['mx', 'build'],
        ['mx', '--dy', '/compiler', '--jdk', 'jvmci', '-J-ea', 'espresso-meta', '-ea'] + hello_world,
    ],
    timelimit: '10:00',
  },

  builds: [
    jdk8 + gate + linux + espresso_gate + {environment+: {GATE_TAGS: 'jackpot'}}         + {name: 'espresso-jackpot-linux-amd64'},
    jdk8 + gate + linux + espresso_gate + eclipse + jdt + {environment+: {GATE_TAGS: 'style,fullbuild'}} + {name: 'espresso-style-fullbuild-linux-amd64'},
    jdk8 + gate + linux + espresso_gate + {environment+: {GATE_TAGS: 'build,unittest'}}  + {name: 'espresso-unittest-linux-amd64'},
    jdk8 + gate + linux + espresso_svm_hello_world(ee=false)                             + {name: 'espresso-svm-hello-world-linux-amd64'},
    jdk8 + gate + linux + espresso_svm_hello_world(ee=true)                              + {name: 'espresso-svm-hello-world-ee-linux-amd64'},
    jdk8 + gate + linux + espresso_meta                                                  + {name: 'espresso-meta-hello-world-linux-amd64'},
  ],
}
