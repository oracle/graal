{
  local base = {

      local labsjdk8 = {name: 'oraclejdk', version: '8u241-jvmci-19.3-b07', platformspecific: true},
      local labsjdk_ce_11 = {name : 'labsjdk', version : 'ce-11.0.5+10-jvmci-19.3-b05', platformspecific: true},

      jdk8: {
        downloads+: {
          JAVA_HOME: labsjdk8,
        },
      },

      extra_jdk11: {
         downloads+: {
          EXTRA_JAVA_HOMES: labsjdk_ce_11,
        },
      },

      gate:        {targets+: ['gate']},
      postMerge:   {targets+: ['post-merge']},
      bench:       {targets+: ['bench', 'post-merge']},
      dailyBench:  {targets+: ['bench', 'daily']},
      weeklyBench: {targets+: ['bench', 'weekly']},
      weekly:      {targets+: ['weekly']},

      common: {
        packages+: {
          '00:pip:logilab-common': '==1.4.4', # forces installation of python2 compliant version of logilab before astroid
          '01:pip:astroid': '==1.1.0',
          'pip:pylint': '==1.1.0',
          'pip:ninja_syntax': '==1.7.2',
        },
        environment+: {
          GRAALVM_CHECK_EXPERIMENTAL_OPTIONS: "true",
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

      ol65: self.linux + {
        capabilities+: ['ol65'],
      },

      x52: self.linux + {
        capabilities+: ['no_frequency_scaling', 'tmpfs25g', 'x52'],
      },

      sparc: self.common + {
        capabilities: ['solaris', 'sparcv9'],
      },

      darwin: self.common + {
        environment+: {
          // for compatibility with macOS El Capitan
          MACOSX_DEPLOYMENT_TARGET: '10.11',
        },
        capabilities: ['darwin', 'amd64'],
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
  },

  local gate_cmd = ['mx', '--strict-compliance', 'gate', '--strict-mode', '--tags', '${GATE_TAGS}'],

  local gate_coverage = base.eclipse + {
    setup+: [
      ['mx', 'sversions'],
    ],
    run+: [
      ['mx', '--jacoco-whitelist-package', 'com.oracle.truffle.espresso', '--jacoco-exclude-annotation', '@GeneratedBy', '--strict-compliance', 'gate', '--strict-mode', '--tags', '${GATE_TAGS}', '--jacocout', 'html'],
      ['mx', '--jacoco-whitelist-package', 'com.oracle.truffle.espresso', '--jacoco-exclude-annotation', '@GeneratedBy', 'sonarqube-upload', "-Dsonar.host.url=$SONAR_HOST_URL", "-Dsonar.projectKey=com.oracle.truffle.espresso", "-Dsonar.projectName=GraalVM - Espresso", '--exclude-generated'],
      ['mx', '--jacoco-whitelist-package', 'com.oracle.truffle.espresso', '--jacoco-exclude-annotation', '@GeneratedBy', 'coverage-upload']
    ],
    timelimit: '30:00',
  },

  local gate_espresso = {
    setup+: [
      ['mx', 'sversions'],
    ],
    run+: [
      gate_cmd,
    ],
    timelimit: '15:00',
  },

  local mx_espresso_svm(ee, args) = ['mx', '--env', (if ee then 'native-ee' else 'native-ce')] + args,

  local run_espresso_native(ee, args) = [
    ['set-export', 'GRAALVM_HOME', mx_espresso_svm(ee, ['graalvm-home'])],
    ['set-export', 'ESPRESSO', '${GRAALVM_HOME}/bin/espresso'],
    ['set-export', 'LD_DEBUG', 'unused'],
    ['${ESPRESSO}'] + args,
  ],

  local hello_world_args = ['-cp', 'mxbuild/dists/jdk1.8/espresso-playground.jar', 'com.oracle.truffle.espresso.playground.HelloWorld'],

  local gate_espresso_svm = {
    setup+: [
      ['mx', 'sversions'],
    ],
    run+: [
      mx_espresso_svm(false, ['build']),
    ] +
      run_espresso_native(false, hello_world_args),
  },

  local gate_espresso_svm_ee = {
    setup+: [
      ['git', 'clone', '-b', 'slimbeans', '--depth', '1', ['mx', 'urlrewrite', 'https://github.com/oracle/graal-enterprise'], '../graal-enterprise'],
      ['mx', 'sversions'],
    ],
    run+: [
      mx_espresso_svm(true, ['build']),
    ] +
      run_espresso_native(true, hello_world_args),
  },

  local jdk8_gate_darwin            = base.jdk8 + base.gate   + base.darwin,
  local jdk8_gate_linux             = base.jdk8 + base.gate   + base.linux,
  local jdk8_gate_linux_eclipse_jdt = base.jdk8 + base.gate   + base.linux + base.eclipse + base.jdt,
  local jdk8_weekly_linux           = base.jdk8 + base.weekly + base.linux,

  builds: [
    jdk8_weekly_linux             + gate_coverage        + {environment+: {GATE_TAGS: 'build,unittest'}}  + {name: 'espresso-coverage-linux-amd64'},

    jdk8_gate_linux + base.extra_jdk11 + gate_espresso   + {environment+: {GATE_TAGS: 'jackpot'}}         + {name: 'espresso-jackpot-linux-amd64'},
    jdk8_gate_linux_eclipse_jdt   + gate_espresso        + {environment+: {GATE_TAGS: 'style,fullbuild'}} + {name: 'espresso-style-fullbuild-linux-amd64'},

    jdk8_gate_linux               + gate_espresso        + {environment+: {GATE_TAGS: 'build,unittest'}}  + {name: 'espresso-unittest-linux-amd64'},

    // LD_DEBUG=unused is a workaround for: symbol lookup error: jre/lib/amd64/libnio.so: undefined symbol: fstatat64
    jdk8_gate_linux               + gate_espresso        + {environment+: {GATE_TAGS: 'build,meta', LD_DEBUG: 'unused'}}
                                                                                                          + {name: 'espresso-meta-hello-world-linux-amd64'},

    jdk8_gate_linux               + gate_espresso_svm                                                     + {name: 'espresso-svm-hello-world-linux-amd64'},
    jdk8_gate_linux               + gate_espresso_svm_ee                                                  + {name: 'espresso-svm-ee-hello-world-linux-amd64'},

    jdk8_gate_darwin              + gate_espresso_svm                                                     + {name: 'espresso-svm-hello-world-darwin-amd64'},
    jdk8_gate_darwin              + gate_espresso_svm_ee                                                  + {name: 'espresso-svm-ee-hello-world-darwin-amd64'},
  ],
}
