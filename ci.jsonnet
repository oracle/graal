local base = {

  local jdks = (import "common.json").jdks,

  jdk8_ce: {
    downloads+: {
      JAVA_HOME: jdks.openjdk8,
    },
  },

  jdk8_ee: {
    downloads+: {
      JAVA_HOME: jdks.oraclejdk8,
    },
  },

  jdk11_ce: {
    downloads+: {
      JAVA_HOME: jdks["labsjdk-ce-11"],
    },
  },

  jdk11_ee: {
    downloads+: {
      JAVA_HOME: jdks["labsjdk-ee-11"],
    },
  },

  extra_jdk11_ce: {
      downloads+: {
      EXTRA_JAVA_HOMES: jdks["labsjdk-ce-11"],
    },
  },

  gate:            {targets+: ['gate']},
  postMerge:       {targets+: ['post-merge']},
  postMergeDeploy: {targets+: ['post-merge', 'deploy']},
  bench:           {targets+: ['bench', 'post-merge']},
  dailyBench:      {targets+: ['bench', 'daily']},
  weekly:          {targets+: ['weekly']},
  weeklyBench:     {targets+: ['bench', 'weekly']},
  onDemand:        {targets+: ['on-demand']},
  onDemandBench:   {targets+: ['bench', 'on-demand']},

  common: {
    packages+: {
      '00:pip:logilab-common': '==1.4.4', # forces installation of python2 compliant version of logilab before astroid
      '01:pip:astroid': '==1.1.0',
      'pip:pylint': '==1.1.0',
      'pip:ninja_syntax': '==1.7.2',
      'mx': 'HEAD',
    },
    environment+: {
      GRAALVM_CHECK_EXPERIMENTAL_OPTIONS: "true",
      MX_PYTHON_VERSION: "3",
      GATE_ARGS: "--strict-mode",
    },
  },

  linux: self.common + {
    packages+: {
      binutils: '>=2.30',
      git: '>=1.8.3',
      gcc: '>=4.9.1',
      'gcc-build-essentials': '>=4.9.1', # GCC 4.9.0 fails on cluster
      make: '>=3.83',
      'sys:cmake': '==3.15.2',
      ruby: "==2.6.5",
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

  windows_8 : self.common + {
    packages : {
      msvc : "==10.0"
    },
    capabilities : ['windows', 'amd64']
  },

  windows_11 : self.common + {
    packages : {
      "devkit:VS2017-15.9.16+1" : "==0"
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

  bench_upload: {
    teardown+: [
      ['bench-uploader.py', 'bench-results.json'],
    ]
  }
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

local gate_cmd = ['mx', '--strict-compliance', 'gate', '${GATE_ARGS}', '--tags', '${GATE_TAGS}'];

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

local hello_world_args = ['-cp', '$ESPRESSO_PLAYGROUND', 'com.oracle.truffle.espresso.playground.HelloWorld'];

local setup_playground(env) = {
  run+: [
    ['set-export', 'ESPRESSO_PLAYGROUND', _mx(env, ['path', 'ESPRESSO_PLAYGROUND'])],
  ],
};

local clone_build_run(env, args) =
  clone_graal(env) +
  build_espresso(env) +
  setup_playground(env) +
  run_espresso(env, args);

local _host_jvm(env) = 'graalvm-espresso-' + env;
local _host_jvm_config(env) = if std.startsWith(env, 'jvm') then 'jvm' else 'native';

local espresso_benchmark(env, suite, host_jvm=_host_jvm(env), host_jvm_config=_host_jvm_config(env), guest_jvm='espresso', guest_jvm_config='default', fork_file=null, extra_args=[]) =
  clone_graal(env) +
  build_espresso(env) +
  {
    run+: maybe_set_ld_debug_flag(env) + [
        _mx(env, ['benchmark', '--results-file', 'bench-results.json'] +
            (if (fork_file != null) then ['--fork-count-file', fork_file] else []) +
            [suite,
            '--',
            '--jvm=' + host_jvm, '--jvm-config=' + host_jvm_config,
            '--guest',
            '--jvm=' + guest_jvm, '--jvm-config=' + guest_jvm_config,
            '--vm.Xss32m'] + extra_args
        ),
    ],
    timelimit: '3:00:00',
  } +
  base.bench_upload;

local _graal_host_jvm_config(env) = if std.endsWith(env, '-ce') then 'graal-core' else 'graal-enterprise';

local graal_benchmark(env, suite, host_jvm='server', host_jvm_config=_graal_host_jvm_config(env), extra_args=[]) =
  clone_graal(env) +
  build_espresso(env) +
  {
    run+: [
        _mx(env, ['benchmark',
            '--results-file', 'bench-results.json',
            suite,
            '--',
            '--jvm=' + host_jvm, '--jvm-config=' + host_jvm_config,
          ] + extra_args
        ),
    ],
    timelimit: '1:00:00',
  } +
  base.bench_upload;

local espresso_minheap_benchmark(env, suite, guest_jvm_config) =
  espresso_benchmark(env, suite, host_jvm='server', host_jvm_config='default', guest_jvm='espresso-minheap', guest_jvm_config=guest_jvm_config, extra_args=['--', '--iterations', '1']);

local espresso_interpreter_benchmark(env, suite) =
  espresso_benchmark(env, suite, guest_jvm_config='interpreter', extra_args=['--', '--iterations', '1']);

# Scala DaCapo benchmarks that run in both JVM and native modes,
# Excluding factorie (too slow). kiama and scalariform have transient issues with compilation enabled.
local scala_dacapo_jvm_fast(warmup=false) = 'scala-dacapo' + (if warmup then '-warmup' else '') + ':*[scalap,scalac,scaladoc,scalaxb]';

local scala_dacapo_warmup_benchmark(env, guest_jvm_config='default', extra_args=[]) =
  espresso_benchmark(
    env,
    scala_dacapo_jvm_fast(warmup=true),
    host_jvm=_host_jvm(env), host_jvm_config=_host_jvm_config(env),
    guest_jvm='espresso', guest_jvm_config=guest_jvm_config,
    fork_file='mx.espresso/scala-dacapo-warmup-forks.json',
    extra_args=extra_args
  );

# GraalVM Installables
local graalvm_installables(ee) = {
  local dynamic_imports = if ee then '/vm-enterprise,/substratevm-enterprise' else '/vm,/substratevm',
  local repo_id = if ee then 'graal-us' else 'lafo',
  local base_cmd_line = ['mx', '--dynamicimports=' + dynamic_imports, '--native-images=espresso', '--exclude-components=nju,npi', '--disable-installables=ni,niee,nil,llp'],
  local graal_repo = if ee then 'graal-enterprise' else 'graal',
  run+: [
    clone_repo(graal_repo),
    base_cmd_line + ['build'],
    base_cmd_line + ['--suite', 'sdk', 'maven-deploy', '--all-distribution-types', '--with-suite-revisions-metadata', '--tag=installable,standalone', '--validate=none', repo_id],
  ],
};

local deploy_base = base.postMergeDeploy + {
  downloads+: {
    MAVEN_HOME: {name: "maven", version: "3.3.9", platformspecific: false}
  },
};

local deploy_unix = deploy_base + {
  environment+: {
    PATH : "$MAVEN_HOME/bin:$JAVA_HOME/bin:$PATH"
  },
};

local deploy_windows = deploy_base + {
  environment+: {
    PATH : "$MAVEN_HOME\\bin;$JAVA_HOME\\bin;$PATH"
  },
};

local jdk8_gate_windows           = base.jdk8_ee  + base.gate          + base.windows_8;
local jdk8_gate_darwin            = base.jdk8_ee  + base.gate          + base.darwin;
local jdk8_gate_linux             = base.jdk8_ee  + base.gate          + base.linux;
local jdk8_gate_linux_eclipse_jdt = base.jdk8_ee  + base.gate          + base.linux + base.eclipse + base.jdt;
local jdk8_bench_linux            = base.jdk8_ee  + base.bench         + base.x52;
local jdk8_weekly_linux           = base.jdk8_ee  + base.weekly        + base.linux;
local jdk8_weekly_bench_linux     = base.jdk8_ee  + base.weeklyBench   + base.x52;
local jdk8_on_demand_linux        = base.jdk8_ee  + base.onDemand      + base.linux;
local jdk8_on_demand_bench_linux  = base.jdk8_ee  + base.onDemandBench + base.x52;
local jdk11_gate_linux            = base.jdk11_ee + base.gate          + base.linux;

local jdk8_deploy_windows         = base.jdk8_ee  + deploy_windows + base.windows_8;
local jdk8_deploy_darwin          = base.jdk8_ee  + deploy_unix    + base.darwin;
local jdk8_deploy_linux           = base.jdk8_ee  + deploy_unix    + base.linux;
local jdk11_deploy_windows        = base.jdk11_ee + deploy_windows + base.windows_11;
local jdk11_deploy_darwin         = base.jdk11_ee + deploy_unix    + base.darwin;
local jdk11_deploy_linux          = base.jdk11_ee + deploy_unix    + base.linux;

local jdk8_deploy_windows_ce      = base.jdk8_ce  + deploy_windows + base.windows_8;
local jdk8_deploy_darwin_ce       = base.jdk8_ce  + deploy_unix    + base.darwin;
local jdk8_deploy_linux_ce        = base.jdk8_ce  + deploy_unix    + base.linux;
local jdk11_deploy_windows_ce     = base.jdk11_ce + deploy_windows + base.windows_11;
local jdk11_deploy_darwin_ce      = base.jdk11_ce + deploy_unix    + base.darwin;
local jdk11_deploy_linux_ce       = base.jdk11_ce + deploy_unix    + base.linux;

local espresso_configs = ['jvm-ce', 'jvm-ee', 'native-ce', 'native-ee'];
local benchmark_suites = ['dacapo', 'renaissance', 'scala-dacapo'];

local awfy = 'awfy:*';

{
  builds: [
    // JaCoCo coverage (disabled)
    // jdk8_weekly_linux             + gate_coverage        + {environment+: {GATE_TAGS: 'build,unittest'}}  + {name: 'espresso-gate-coverage-jdk8-linux-amd64'},

    // Gates
    jdk8_gate_linux + base.extra_jdk11_ce + gate_espresso   + {environment+: {GATE_TAGS: 'jackpot'}}         + {name: 'espresso-gate-jackpot-jdk8-linux-amd64'},
    jdk8_gate_linux_eclipse_jdt   + gate_espresso        + {environment+: {GATE_TAGS: 'style,fullbuild'}} + {name: 'espresso-gate-style-fullbuild-jdk8-linux-amd64'},

    jdk8_gate_linux               + gate_espresso        + {environment+: {GATE_TAGS: 'build,unittest'}}  + {name: 'espresso-gate-unittest-jdk8-linux-amd64'},

    jdk8_gate_linux               + gate_espresso        + {environment+: {GATE_TAGS      : 'build,unittest',
                                                                           GATE_ARGS: '--no-warning-as-error',
                                                                           DYNAMIC_IMPORTS: '/truffleruby'},
                                                            timelimit: '1:00:00'}                         + {name: 'espresso-gate-unittest-with-ruby-jdk8-linux-amd64'},

    jdk8_gate_linux               + gate_espresso        + {environment+: {GATE_TAGS      : 'build,unittest_with_compilation',
                                                                           DYNAMIC_IMPORTS: '/compiler'},
                                                            timelimit: '1:00:00'}                         + {name: 'espresso-gate-unittest-compilation-jdk8-linux-amd64'},

    // LD_DEBUG=unused is a workaround for: symbol lookup error: jre/lib/amd64/libnio.so: undefined symbol: fstatat64
    jdk8_gate_linux               + gate_espresso        + {environment+: {GATE_TAGS: 'build,meta', LD_DEBUG: 'unused'}}
                                                                                                          + {name: 'espresso-meta-hello-world-linux-amd64'},

    // Hello World! should run in all supported configurations.
    jdk8_gate_linux               + clone_build_run('jvm-ce',    hello_world_args)                        + {name: 'espresso-gate-jvm-ce-hello-world-jdk8-linux-amd64'},
    jdk8_gate_linux               + clone_build_run('native-ce', hello_world_args)                        + {name: 'espresso-gate-native-ce-hello-world-jdk8-linux-amd64'},
    jdk8_gate_linux               + clone_build_run('native-ee', hello_world_args)                        + {name: 'espresso-gate-native-ee-hello-world-jdk8-linux-amd64'},
    jdk8_gate_darwin              + clone_build_run('native-ce', hello_world_args)                        + {name: 'espresso-gate-native-ce-hello-world-jdk8-darwin-amd64'},
    jdk8_gate_darwin              + clone_build_run('native-ee', hello_world_args)                        + {name: 'espresso-gate-native-ee-hello-world-jdk8-darwin-amd64'},
    jdk8_gate_windows             + clone_build_run('native-ce', hello_world_args)                        + {name: 'espresso-gate-native-ce-hello-world-jdk8-windows-amd64'},
    jdk8_gate_windows             + clone_build_run('native-ee', hello_world_args)                        + {name: 'espresso-gate-native-ee-hello-world-jdk8-windows-amd64'},
    jdk11_gate_linux              + clone_build_run('jvm-ce',    hello_world_args)                        + {name: 'espresso-gate-jvm-ce-hello-world-jdk11-linux-amd64'},
    jdk11_gate_linux              + clone_build_run('native-ce', hello_world_args)                        + {name: 'espresso-gate-native-ce-hello-world-jdk11-linux-amd64'},
    jdk11_gate_linux              + clone_build_run('native-ee', hello_world_args)                        + {name: 'espresso-gate-native-ee-hello-world-jdk11-linux-amd64'},

    // AWFY peak perf. benchmarks (post-merge)
    jdk8_bench_linux              + espresso_benchmark('jvm-ce', awfy)                                    + {name: 'espresso-bench-jvm-ce-awfy-jdk8-linux-amd64'},
    jdk8_bench_linux              + espresso_benchmark('jvm-ee', awfy)                                    + {name: 'espresso-bench-jvm-ee-awfy-jdk8-linux-amd64'},
    jdk8_bench_linux              + espresso_benchmark('native-ce', awfy)                                 + {name: 'espresso-bench-native-ce-awfy-jdk8-linux-amd64'},
    jdk8_bench_linux              + espresso_benchmark('native-ee', awfy)                                 + {name: 'espresso-bench-native-ee-awfy-jdk8-linux-amd64'},

    // AWFY interpreter benchmarks (post-merge)
    jdk8_bench_linux              + espresso_interpreter_benchmark('jvm-ce', awfy)                        + {name: 'espresso-bench-jvm-ce-awfy_interpreter-jdk8-linux-amd64'},
    jdk8_bench_linux              + espresso_interpreter_benchmark('jvm-ee', awfy)                        + {name: 'espresso-bench-jvm-ee-awfy_interpreter-jdk8-linux-amd64'},
    jdk8_bench_linux              + espresso_interpreter_benchmark('native-ce', awfy)                     + {name: 'espresso-bench-native-ce-awfy_interpreter-jdk8-linux-amd64'},
    jdk8_bench_linux              + espresso_interpreter_benchmark('native-ee', awfy)                     + {name: 'espresso-bench-native-ee-awfy_interpreter-jdk8-linux-amd64'},

    // Scala DaCapo warmup benchmarks (post-merge)
    #jdk8_bench_linux              + scala_dacapo_warmup_benchmark('jvm-ce')      + {name: 'espresso-bench-jvm-ce-scala_dacapo_warmup-jdk8-linux-amd64'},
    #jdk8_bench_linux              + scala_dacapo_warmup_benchmark('jvm-ee')      + {name: 'espresso-bench-jvm-ee-scala_dacapo_warmup-jdk8-linux-amd64'},    
    #jdk8_bench_linux              + scala_dacapo_warmup_benchmark('native-ce')   + {name: 'espresso-bench-native-ce-scala_dacapo_warmup-jdk8-linux-amd64'},
    jdk8_bench_linux              + scala_dacapo_warmup_benchmark('native-ee')   + {name: 'espresso-bench-native-ee-scala_dacapo_warmup-jdk8-linux-amd64'},

    // Scala DaCapo warmup benchmarks --engine.MultiTier (post-merge)
    #jdk8_bench_linux              + scala_dacapo_warmup_benchmark('jvm-ce', 'multi-tier')      + {name: 'espresso-bench-jvm-ce-scala_dacapo_warmup_benchmark_multi_tier-jdk8-linux-amd64'},
    #jdk8_bench_linux              + scala_dacapo_warmup_benchmark('jvm-ee', 'multi-tier')      + {name: 'espresso-bench-jvm-ee-scala_dacapo_warmup_benchmark_multi_tier-jdk8-linux-amd64'},
    #jdk8_bench_linux              + scala_dacapo_warmup_benchmark('native-ce', 'multi-tier')   + {name: 'espresso-bench-native-ce-scala_dacapo_warmup_benchmark_multi_tier-jdk8-linux-amd64'},
    jdk8_bench_linux              + scala_dacapo_warmup_benchmark('native-ee', 'multi-tier')   + {name: 'espresso-bench-native-ee-scala_dacapo_warmup_benchmark_multi_tier-jdk8-linux-amd64'},

    // Scala DaCapo warmup benchmarks (Graal CE/EE baseline) (on-demand)
    jdk8_on_demand_bench_linux           + graal_benchmark('jvm-ce', scala_dacapo_jvm_fast(warmup=true))  + {name: 'bench-graal-ce-scala_dacapo_warmup-jdk8-linux-amd64'},
    jdk8_on_demand_bench_linux           + graal_benchmark('jvm-ee', scala_dacapo_jvm_fast(warmup=true))  + {name: 'bench-graal-ee-scala_dacapo_warmup-jdk8-linux-amd64'},

    // Post-merge deploy
    jdk8_deploy_linux_ce    + graalvm_installables(ee=false)                                              + {name: 'espresso-deploy-installables-ce-jdk8-linux-amd64'},
    jdk8_deploy_darwin_ce   + graalvm_installables(ee=false)                                              + {name: 'espresso-deploy-installables-ce-jdk8-darwin-amd64'},
    jdk8_deploy_windows_ce  + graalvm_installables(ee=false)                                              + {name: 'espresso-deploy-installables-ce-jdk8-windows-amd64'},

    jdk8_deploy_linux       + graalvm_installables(ee=true)                                               + {name: 'espresso-deploy-installables-ee-jdk8-linux-amd64'},
    jdk8_deploy_darwin      + graalvm_installables(ee=true)                                               + {name: 'espresso-deploy-installables-ee-jdk8-darwin-amd64'},
    jdk8_deploy_windows     + graalvm_installables(ee=true)                                               + {name: 'espresso-deploy-installables-ee-jdk8-windows-amd64'},

    jdk11_deploy_linux_ce   + graalvm_installables(ee=false)                                              + {name: 'espresso-deploy-installables-ce-jdk11-linux-amd64'},
    jdk11_deploy_darwin_ce  + graalvm_installables(ee=false)                                              + {name: 'espresso-deploy-installables-ce-jdk11-darwin-amd64'},
    jdk11_deploy_windows_ce + graalvm_installables(ee=false)                                              + {name: 'espresso-deploy-installables-ce-jdk11-windows-amd64'},

    jdk11_deploy_linux      + graalvm_installables(ee=true)                                               + {name: 'espresso-deploy-installables-ee-jdk11-linux-amd64'},
    jdk11_deploy_darwin     + graalvm_installables(ee=true)                                               + {name: 'espresso-deploy-installables-ee-jdk11-darwin-amd64'},
    jdk11_deploy_windows    + graalvm_installables(ee=true)                                               + {name: 'espresso-deploy-installables-ee-jdk11-windows-amd64'},

    // On-demand
    jdk8_on_demand_linux          + espresso_minheap_benchmark('jvm-ce', awfy, 'infinite-overhead')       + {name: 'espresso-jvm-ce-awfy-minheap-infinite-ovh-jdk8-linux-amd64'},
    jdk8_on_demand_bench_linux    + espresso_minheap_benchmark('jvm-ce', awfy, '1.5-overhead')            + {name: 'espresso-bench-jvm-ce-awfy-minheap-1.5-ovh-jdk8-linux-amd64'},
 ],
}
