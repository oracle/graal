{
  local labsjdk8 = {
    name: 'labsjdk',
    version: '8u192-jvmci-0.53',
    platformspecific: true,
  },

  local jdk8 = {
    downloads: {
      JAVA_HOME: labsjdk8,
      JDT: {name: 'ecj', version: '4.6.1', platformspecific: false},
    },
  },

  local gate = {targets: ['gate']},

  local common = {
    packages: {
      'git': '>=1.8.3',
      'pip:astroid': '==1.1.0',
      'pip:pylint': '==1.1.0',
    },
  },

  local linux = common + {
    capabilities: ['linux', 'amd64'],
  },

  local espresso = {
    downloads+: {
      ECLIPSE: {name: 'eclipse', version: '4.5.2.1', platformspecific: true},
    },
    environment+: {
      ECLIPSE_EXE: '$ECLIPSE/eclipse',
    },
    setup+: [
      ['mx', 'sversions'],
    ],
    run+: [
      ['mx', '--strict-compliance', 'gate', '--strict-mode', '--tags', '${GATE_TAGS}'],
    ],
    timelimit: '15:00',
  },

  builds: [
    jdk8 + gate + linux + espresso + {environment+: {GATE_TAGS: 'style,fullbuild'}} + {name: 'espresso-style-fullbuild-linux-amd64'},
    jdk8 + gate + linux + espresso + {environment+: {GATE_TAGS: 'default'}} + {name: 'espresso-unittest-linux-amd64'},
  ],
}
