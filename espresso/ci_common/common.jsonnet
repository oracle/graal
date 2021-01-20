local base = import '../ci.jsonnet';

local _mx(env, args) = ['mx', '--env', env] + args;

local clone_repo(repo) = ['git', 'clone', '-b', 'espresso_release_branch', '--depth', '1', ['mx', 'urlrewrite', 'https://github.com/oracle/' + repo], '../' + repo];

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

local run_espresso_java(env, args) = {
  run+: maybe_set_ld_debug_flag(env) + [
    _mx(env, ['espresso-java'] + args),
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
  run_espresso(env, args) +
  if std.startsWith(env, 'jvm') then {} else run_espresso_java(env, args);

local _host_jvm(env) = 'graalvm-espresso-' + env;
local _host_jvm_config(env) = if std.startsWith(env, 'jvm') then 'jvm' else 'native';

local _graal_host_jvm_config(env) = if std.endsWith(env, '-ce') then 'graal-core' else 'graal-enterprise';

local espresso_configs = ['jvm-ce', 'jvm-ee', 'native-ce', 'native-ee'];
local benchmark_suites = ['dacapo', 'renaissance', 'scala-dacapo'];


{
  // platform-specific snippets
  common: {
    packages+: {
      '00:pip:logilab-common': '==1.4.4', # forces installation of python2 compliant version of logilab before astroid
      '01:pip:astroid': '==1.1.0',
      'pip:pylint': '==1.1.0',
      'pip:ninja_syntax': '==1.7.2',
      'mx': '5.280.5',
    },
    environment+: {
      GRAALVM_CHECK_EXPERIMENTAL_OPTIONS: "true",
      MX_PYTHON_VERSION: "3",
    },
    setup+: [
      ['cd', base.suite_name],
    ],
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

  // generic targets
  gate:            {targets+: ['gate']},
  postMerge:       {targets+: ['post-merge']},
  bench:           {targets+: ['bench', 'post-merge']},
  dailyBench:      {targets+: ['bench', 'daily']},
  daily:           {targets+: ['daily']},
  weekly:          {targets+: ['weekly']},
  weeklyBench:     {targets+: ['bench', 'weekly']},
  onDemand:        {targets+: ['on-demand']},
  onDemandBench:   {targets+: ['bench', 'on-demand']},

  // precise targets and capabilities
  jdk8_gate_windows           : base.jdk8  + self.gate          + base.windows_8,
  jdk8_gate_darwin            : base.jdk8  + self.gate          + self.darwin,
  jdk8_gate_linux             : base.jdk8  + self.gate          + self.linux,
  jdk8_bench_linux            : base.jdk8  + self.bench         + self.x52,
  jdk8_weekly_linux           : base.jdk8  + self.weekly        + self.linux,
  jdk8_daily_linux            : base.jdk8  + self.daily         + self.linux,
  jdk8_weekly_bench_linux     : base.jdk8  + self.weeklyBench   + self.x52,
  jdk8_on_demand_linux        : base.jdk8  + self.onDemand      + self.linux,
  jdk8_on_demand_bench_linux  : base.jdk8  + self.onDemandBench + self.x52,
  jdk11_gate_linux            : base.jdk11 + self.gate          + self.linux,
  jdk11_gate_windows          : base.jdk11 + self.gate          + base.windows_11,

  // shared snippets
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
  },

  // shared functions
  espresso_gate(allow_warnings, tags, name, timelimit='15:00'): {
    setup+: [
      ['mx', 'sversions'],
    ],
    run+: [
      ['mx', '--strict-compliance', 'gate', '--strict-mode', '--tags', tags] + ( if allow_warnings then ['--no-warning-as-error'] else []),
    ],
    timelimit: timelimit,
    name: name,
  },

  espresso_benchmark(env, suite, host_jvm=_host_jvm(env), host_jvm_config=_host_jvm_config(env), guest_jvm='espresso', guest_jvm_config='default', fork_file=null, extra_args=[]):
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
    self.bench_upload,

  espresso_minheap_benchmark(env, suite, guest_jvm_config):
    self.espresso_benchmark(env, suite, host_jvm='server', host_jvm_config='default', guest_jvm='espresso-minheap', guest_jvm_config=guest_jvm_config, extra_args=['--', '--iterations', '1']),

  espresso_interpreter_benchmark(env, suite):
    self.espresso_benchmark(env, suite, guest_jvm_config='interpreter', extra_args=['--', '--iterations', '1']),

  scala_dacapo_warmup_benchmark(env, guest_jvm_config='default', extra_args=[]):
    self.espresso_benchmark(
      env,
      self.scala_dacapo_jvm_fast(warmup=true),
      host_jvm=_host_jvm(env), host_jvm_config=_host_jvm_config(env),
      guest_jvm='espresso', guest_jvm_config=guest_jvm_config,
      fork_file='mx.espresso/scala-dacapo-warmup-forks.json',
      extra_args=extra_args
    ),

  graal_benchmark(env, suite, host_jvm='server', host_jvm_config=_graal_host_jvm_config(env), extra_args=[]):
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
    self.bench_upload,

  # Scala DaCapo benchmarks that run in both JVM and native modes,
  # Excluding factorie (too slow). kiama and scalariform have transient issues with compilation enabled.
  scala_dacapo_jvm_fast(warmup=false): 'scala-dacapo' + (if warmup then '-warmup' else '') + ':*[scalap,scalac,scaladoc,scalaxb]',

  local that = self,

  builds: [
        // Gates
        that.jdk8_gate_linux + that.eclipse + that.jdt + that.espresso_gate(allow_warnings=false, tags='style,fullbuild,jackpot', name=base.suite_name + '-gate-style-fullbuild-jackpot-jdk8-linux-amd64-2'),
  ],
}