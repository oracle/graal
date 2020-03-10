local base = {

  local jdks = (import "common.json").jdks,

  jdk8: {
    downloads+: {
      JAVA_HOME: jdks.oraclejdk8,
    },
  },

  extra_jdk11: {
      downloads+: {
      EXTRA_JAVA_HOMES: jdks["labsjdk-ce-11"],
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
      MX_PYTHON_VERSION: "3",
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

  windows : self.common + {
    packages : {
      msvc : "==10.0"
    },
    capabilities : ['windows', 'amd64']
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
};

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
};

local gate_cmd = ['mx', '--strict-compliance', 'gate', '--strict-mode', '--tags', '${GATE_TAGS}'];

local gate_espresso = {
  setup+: [
    ['mx', 'sversions'],
  ],
  run+: [
    gate_cmd,
  ],
  timelimit: '15:00',
};

local _mx(env, args) = ['mx', '--env', env] + args;

local clone_repo(repo) = ['git', 'clone', '-b', 'slimbeans', '--depth', '1', ['mx', 'urlrewrite', 'https://github.com/oracle/' + repo], '../' + repo];

local clone_graal(env) = {
  local maybe_clone_graal_enterprise = if std.endsWith(env, 'ee') then [clone_repo('graal-enterprise')] else [],
  setup+: [
    clone_repo('graal'),
  ] + maybe_clone_graal_enterprise,
};

local build_espresso(env) = {
  run+: [
    ['mx', 'sversions'],
    _mx(env, ['build']),
  ],
};

local maybe_set_ld_debug_flag(env) = if std.startsWith(env, 'jvm') then [['set-export', 'LD_DEBUG', 'unused']] else [];

local run_espresso(env, args) = {
  run+: maybe_set_ld_debug_flag(env) + [
    _mx(env, ['espresso'] + args),
  ],
};

local hello_world_args = ['-cp', 'mxbuild/dists/jdk1.8/espresso-playground.jar', 'com.oracle.truffle.espresso.playground.HelloWorld'];

local clone_build_run(env, args) =
  clone_graal(env) +
  build_espresso(env) +
  run_espresso(env, args);


local espresso_benchmark(env, suite) =
  clone_graal(env) +
  build_espresso(env) +
  {
    run+: maybe_set_ld_debug_flag(env) + [
        _mx(env, ['--kill-with-sigquit', 'benchmark', '--results-file', 'bench-results.json', suite, '--', '--jvm=espresso', '--jvm-config=launcher', '--vm.Xss32m']),
        ['bench-uploader.py', 'bench-results.json'],
    ],
    timelimit: '1:00:00',
  };

local jdk8_gate_windows           = base.jdk8 + base.gate   + base.windows;
local jdk8_gate_darwin            = base.jdk8 + base.gate       + base.darwin;
local jdk8_gate_linux             = base.jdk8 + base.gate       + base.linux;
local jdk8_gate_linux_eclipse_jdt = base.jdk8 + base.gate       + base.linux + base.eclipse + base.jdt;
local jdk8_weekly_linux           = base.jdk8 + base.weekly     + base.linux;
local jdk8_daily_bench_linux      = base.jdk8 + base.dailyBench + base.linux + base.x52;

local espresso_configs = ['jvm-ce', 'jvm-ee', 'native-ce', 'native-ee'];
local benchmark_suites = ['dacapo', 'renaissance', 'scala-dacapo'];

{
  builds: [
    // JaCoCo coverage
    jdk8_weekly_linux             + gate_coverage        + {environment+: {GATE_TAGS: 'build,unittest'}}  + {name: 'espresso-gate-coverage-jdk8-linux-amd64'},

    // Gates
    jdk8_gate_linux + base.extra_jdk11 + gate_espresso   + {environment+: {GATE_TAGS: 'jackpot'}}         + {name: 'espresso-gate-jackpot-jdk8-linux-amd64'},
    jdk8_gate_linux_eclipse_jdt   + gate_espresso        + {environment+: {GATE_TAGS: 'style,fullbuild'}} + {name: 'espresso-gate-style-fullbuild-jdk8-linux-amd64'},

    jdk8_gate_linux               + gate_espresso        + {environment+: {GATE_TAGS: 'build,unittest'}}  + {name: 'espresso-gate-unittest-jdk8-linux-amd64'},

    jdk8_gate_linux               + gate_espresso        + {environment+: {GATE_TAGS      : 'build,unittest_with_compilation',
                                                                           DYNAMIC_IMPORTS: '/compiler'},
                                                            timelimit: '1:00:00'}                         + {name: 'espresso-gate-unittest-compilation-jdk8-linux-amd64'},

    // LD_DEBUG=unused is a workaround for: symbol lookup error: jre/lib/amd64/libnio.so: undefined symbol: fstatat64
    jdk8_gate_linux               + gate_espresso        + {environment+: {GATE_TAGS: 'build,meta', LD_DEBUG: 'unused'}}
                                                                                                          + {name: 'espresso-meta-hello-world-linux-amd64'},                                                                                                   

    // Hello World! should run in all supported configurations.
    jdk8_gate_linux               + clone_build_run('jvm-ce', hello_world_args)                           + {name: 'espresso-gate-jvm-ce-hello-world-jdk8-linux-amd64'},
    jdk8_gate_linux               + clone_build_run('native-ce', hello_world_args)                        + {name: 'espresso-gate-native-ce-hello-world-jdk8-linux-amd64'},
    jdk8_gate_linux               + clone_build_run('native-ee', hello_world_args)                        + {name: 'espresso-gate-native-ee-hello-world-jdk8-linux-amd64'},
    jdk8_gate_darwin              + clone_build_run('native-ce', hello_world_args)                        + {name: 'espresso-gate-native-ce-hello-world-jdk8-darwin-amd64'},
    jdk8_gate_darwin              + clone_build_run('native-ee', hello_world_args)                        + {name: 'espresso-gate-native-ee-hello-world-jdk8-darwin-amd64'},
    jdk8_gate_windows             + clone_build_run('native-ee', hello_world_args)                        + {name: 'espresso-gate-native-ce-hello-world-jdk8-windows-amd64'},

    // Benchmarks
    jdk8_daily_bench_linux        + espresso_benchmark('native-ce', 'scala-dacapo:*')                     + {name: 'espresso-bench-native-ce-scala-dacapo-jdk8-linux-amd64'},
    jdk8_daily_bench_linux        + espresso_benchmark('native-ee', 'scala-dacapo:*')                     + {name: 'espresso-bench-native-ee-scala-dacapo-jdk8-linux-amd64'},
  ],
}
