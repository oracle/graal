{
  local oraclejdk8 = {
    name: 'oraclejdk',
    version: '8u212-jvmci-19.2-b01',
    platformspecific: true,
  },

  local jdk8 = {
    downloads: {
      JAVA_HOME: oraclejdk8,
      JDT: {name: 'ecj', version: '4.6.1', platformspecific: false},
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

  local linux = common + {
    capabilities: ['linux', 'amd64'],
  },

  local espresso_gate = {
    downloads+: {
      ECLIPSE: {name: 'eclipse', version: '4.5.2.1', platformspecific: true},
    },
    environment+: {
      ECLIPSE_EXE: '$ECLIPSE/eclipse',
    },
    run+: [
      ['mx', '--strict-compliance', 'gate', '--strict-mode', '--tags', '${GATE_TAGS}'],
    ],
    timelimit: '15:00',
  },

  local espresso_svm = {
    run+: [
      ['mx', '--env', 'espresso.svm', 'build'],
      ['set-export', 'GRAALVM_HOME', ['mx', '--env', 'espresso.svm', 'graalvm-home']],
      # Ugly hardcoded path until we can integrate Espresso properly to the VM suite.
      ['${GRAALVM_HOME}/bin/espresso', '-cp', 'mxbuild/dists/jdk1.8/espresso-playground.jar', 'com.oracle.truffle.espresso.playground.HelloWorld'],
    ],
    timelimit: '15:00',
  },

  builds: [
    jdk8 + gate + linux + espresso_gate + {environment+: {GATE_TAGS: 'jackpot'}}         + {name: 'espresso-jackpot-linux-amd64'},
    jdk8 + gate + linux + espresso_gate + {environment+: {GATE_TAGS: 'style,fullbuild'}} + {name: 'espresso-style-fullbuild-linux-amd64'},
    jdk8 + gate + linux + espresso_gate + {environment+: {GATE_TAGS: 'build,unittest'}}  + {name: 'espresso-unittest-linux-amd64'},
    jdk8 + gate + linux + espresso_svm                                                   + {name: 'espresso-svm-linux-amd64'},
  ],
}
