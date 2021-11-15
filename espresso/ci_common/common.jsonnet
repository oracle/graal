local base = import '../ci.jsonnet';

local _host_jvm(env) = 'graalvm-espresso-' + env;
local _host_jvm_config(env) = if std.startsWith(env, 'jvm') then 'jvm' else 'native';

local _graal_host_jvm_config(env) = if std.endsWith(env, '-ce') then 'graal-core' else 'graal-enterprise';

local espresso_configs = ['jvm-ce', 'jvm-ee', 'native-ce', 'native-ee'];
local benchmark_suites = ['dacapo', 'renaissance', 'scala-dacapo'];

{
  local that = self,

  local mx_version = (import "../../graal-common.json").mx_version,

  // platform-specific snippets
  common: {
    packages+: {
      '00:pip:logilab-common': '==1.4.4', # forces installation of python2 compliant version of logilab before astroid
      '01:pip:astroid': '==1.1.0',
      'pip:pylint': '==1.1.0',
      'pip:ninja_syntax': '==1.7.2',
      'mx': mx_version,
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
      '00:devtoolset': '==7', # GCC 7.3.1, make 4.2.1, binutils 2.28, valgrind 3.13.0
      '01:binutils': '>=2.34',
      git: '>=1.8.3',
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

  darwin: self.common + {
    environment+: {
      // for compatibility with macOS High Sierra
      MACOSX_DEPLOYMENT_TARGET: '10.13',
    },
    capabilities: ['darwin', 'amd64'],
  },

  // generic targets
  gate:            {targets+: ['gate'], timelimit: "1:00:00"},
  postMerge:       {targets+: ['post-merge'],          notify_emails: ["gilles.m.duboscq@oracle.com"]},
  bench:           {targets+: ['bench', 'post-merge'], notify_emails: ["gilles.m.duboscq@oracle.com"]},
  dailyBench:      {targets+: ['bench', 'daily'],      notify_emails: ["gilles.m.duboscq@oracle.com"]},
  daily:           {targets+: ['daily'],               notify_emails: ["gilles.m.duboscq@oracle.com"]},
  weekly:          {targets+: ['weekly'],              notify_emails: ["gilles.m.duboscq@oracle.com"]},
  weeklyBench:     {targets+: ['bench', 'weekly'],     notify_emails: ["gilles.m.duboscq@oracle.com"]},
  onDemand:        {targets+: ['on-demand']},
  onDemandBench:   {targets+: ['bench', 'on-demand']},

  // precise targets and capabilities
  jdk8_gate_linux               : base.jdk8   + self.gate          + self.linux,
  jdk8_gate_darwin              : base.jdk8   + self.gate          + self.darwin,
  jdk8_gate_windows             : base.jdk8   + self.gate          + base.windows_8,
  jdk8_bench_linux              : base.jdk8   + self.bench         + self.x52,
  jdk8_bench_darwin             : base.jdk8   + self.bench         + self.darwin,
  jdk8_bench_windows            : base.jdk8   + self.bench         + base.windows_8,
  jdk8_daily_linux              : base.jdk8   + self.daily         + self.linux,
  jdk8_daily_darwin             : base.jdk8   + self.daily         + self.darwin,
  jdk8_daily_windows            : base.jdk8   + self.daily         + base.windows_8,
  jdk8_daily_bench_linux        : base.jdk8   + self.dailyBench    + self.x52,
  jdk8_daily_bench_darwin       : base.jdk8   + self.dailyBench    + self.darwin,
  jdk8_daily_bench_windows      : base.jdk8   + self.dailyBench    + base.windows_8,
  jdk8_weekly_linux             : base.jdk8   + self.weekly        + self.linux,
  jdk8_weekly_darwin            : base.jdk8   + self.weekly        + self.darwin,
  jdk8_weekly_windows           : base.jdk8   + self.weekly        + base.windows_8,
  jdk8_weekly_bench_linux       : base.jdk8   + self.weeklyBench   + self.x52,
  jdk8_weekly_bench_darwin      : base.jdk8   + self.weeklyBench   + self.darwin,
  jdk8_weekly_bench_windows     : base.jdk8   + self.weeklyBench   + base.windows_8,
  jdk8_on_demand_linux          : base.jdk8   + self.onDemand      + self.linux,
  jdk8_on_demand_darwin         : base.jdk8   + self.onDemand      + self.darwin,
  jdk8_on_demand_windows        : base.jdk8   + self.onDemand      + base.windows_8,
  jdk8_on_demand_bench_linux    : base.jdk8   + self.onDemandBench + self.x52,
  jdk8_on_demand_bench_darwin   : base.jdk8   + self.onDemandBench + self.darwin,
  jdk8_on_demand_bench_windows  : base.jdk8   + self.onDemandBench + base.windows_8,
  jdk11_gate_linux              : base.jdk11  + self.gate          + self.linux,
  jdk11_gate_darwin             : base.jdk11  + self.gate          + self.darwin,
  jdk11_gate_windows            : base.jdk11  + self.gate          + base.windows_11,
  jdk11_bench_linux             : base.jdk11  + self.bench         + self.x52,
  jdk11_bench_darwin            : base.jdk11  + self.bench         + self.darwin,
  jdk11_bench_windows           : base.jdk11  + self.bench         + base.windows_11,
  jdk11_daily_linux             : base.jdk11  + self.daily         + self.linux,
  jdk11_daily_darwin            : base.jdk11  + self.daily         + self.darwin,
  jdk11_daily_windows           : base.jdk11  + self.daily         + base.windows_11,
  jdk11_daily_bench_linux       : base.jdk11  + self.dailyBench    + self.x52,
  jdk11_daily_bench_darwin      : base.jdk11  + self.dailyBench    + self.darwin,
  jdk11_daily_bench_windows     : base.jdk11  + self.dailyBench    + base.windows_11,
  jdk11_weekly_linux            : base.jdk11  + self.weekly        + self.linux,
  jdk11_weekly_darwin           : base.jdk11  + self.weekly        + self.darwin,
  jdk11_weekly_windows          : base.jdk11  + self.weekly        + base.windows_11,
  jdk11_weekly_bench_linux      : base.jdk11  + self.weeklyBench   + self.x52,
  jdk11_weekly_bench_darwin     : base.jdk11  + self.weeklyBench   + self.darwin,
  jdk11_weekly_bench_windows    : base.jdk11  + self.weeklyBench   + base.windows_11,
  jdk11_on_demand_linux         : base.jdk11  + self.onDemand      + self.linux,
  jdk11_on_demand_darwin        : base.jdk11  + self.onDemand      + self.darwin,
  jdk11_on_demand_windows       : base.jdk11  + self.onDemand      + base.windows_11,
  jdk11_on_demand_bench_linux   : base.jdk11  + self.onDemandBench + self.x52,
  jdk11_on_demand_bench_darwin  : base.jdk11  + self.onDemandBench + self.darwin,
  jdk11_on_demand_bench_windows : base.jdk11  + self.onDemandBench + base.windows_11,
  jdk17_gate_linux              : base.jdk17  + self.gate          + self.linux,
  jdk17_gate_darwin             : base.jdk17  + self.gate          + self.darwin,
  jdk17_gate_windows            : base.jdk17  + self.gate          + base.windows_17,

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
  _mx(env, args): ['mx', '--env', env] + args,

  build_espresso(env, debug=false): {
    run+: [
      ['mx', 'sversions'],
      that._mx(env, (if debug then ['--debug-images'] else []) + ['build']),
    ],
  },

  // LD_DEBUG=unused is a workaround for: symbol lookup error: jre/lib/amd64/libnio.so: undefined symbol: fstatat64
  maybe_set_ld_debug_flag(env): if std.startsWith(env, 'jvm') then [['set-export', 'LD_DEBUG', 'unused']] else [],

  espresso_gate(allow_warnings, tags, ld_debug=false, mx_args=[], imports=null, gate_args=[], timelimit='15:00', name=null): {
    local mx_cmd =
      ['mx']
      + mx_args
      + (if imports != null then ['--dynamicimports=' + imports] else []),
    run+: [
      if ld_debug then ['set-export', 'LD_DEBUG', 'unused'] else ['unset', 'LD_DEBUG'],
      mx_cmd + ['--strict-compliance', 'gate', '--strict-mode', '--tags', tags] + ( if allow_warnings then ['--no-warning-as-error'] else []) + gate_args,
    ],
  }
  + (if timelimit != null then {timelimit: timelimit} else {})
  + (if name != null then {name: name} else {}),

  espresso_benchmark(env, suite, host_jvm=_host_jvm(env), host_jvm_config=_host_jvm_config(env), guest_jvm='espresso', guest_jvm_config='default', fork_file=null, extra_args=[], timelimit='3:00:00'):
    self.build_espresso(env) +
    {
      run+: that.maybe_set_ld_debug_flag(env) + [
          that._mx(env, ['benchmark', '--results-file', 'bench-results.json'] +
              (if (fork_file != null) then ['--fork-count-file', fork_file] else []) +
              [suite,
              '--',
              '--jvm=' + host_jvm, '--jvm-config=' + host_jvm_config,
              '--guest',
              '--jvm=' + guest_jvm, '--jvm-config=' + guest_jvm_config,
              '--vm.Xss32m'] + extra_args
          ),
      ],
      timelimit: timelimit,
    }
    + self.bench_upload,

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
      fork_file='ci_common/scala-dacapo-warmup-forks.json',
      extra_args=extra_args,
      timelimit='5:00:00'
    ),

  graal_benchmark(env, suite, host_jvm='server', host_jvm_config=_graal_host_jvm_config(env), extra_args=[]):
    self.build_espresso(env) +
    {
      run+: [
          that._mx(env, ['benchmark',
              '--results-file', 'bench-results.json',
              suite,
              '--',
              '--jvm=' + host_jvm, '--jvm-config=' + host_jvm_config,
            ] + extra_args
          ),
      ],
      timelimit: '1:00:00',
    }
    + self.bench_upload,

  # Scala DaCapo benchmarks that run in both JVM and native modes,
  # Excluding factorie (too slow). kiama and scalariform have transient issues with compilation enabled.
  scala_dacapo_jvm_fast(warmup=false): 'scala-dacapo' + (if warmup then '-warmup' else '') + ':*[scalap,scalac,scaladoc,scalaxb]',

  builds: [
        // Gates
        that.jdk8_gate_linux + that.eclipse + that.jdt + that.espresso_gate(allow_warnings=false, tags='style,fullbuild,jackpot', name='gate-espresso-style-jdk8-linux-amd64'),
  ],
}
