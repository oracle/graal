{
  local jdk8 = {
    downloads: {
      JAVA_HOME: {name: 'labsjdk', version: '8u172-jvmci-0.48', platformspecific: true},
      JDT: {name: 'ecj', version: '4.5.1', platformspecific: false},
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

  local unittest = {
    run+: [
      ['mx', 'gate'],
    ],
    timelimit: '15:00',
  },

  builds: [
    jdk8 + linux + gate + unittest + {name: 'espresso-unittest-linux-amd64'},
  ],
}

