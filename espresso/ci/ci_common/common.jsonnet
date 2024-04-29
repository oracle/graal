local graal_common = import '../../../ci/ci_common/common.jsonnet';
local base = import '../ci.jsonnet';

local devkits = graal_common.devkits;

local _base_env(env) = if std.endsWith(env, '-llvm') then std.substr(env, 0, std.length(env) - 5) else env;

local _graal_host_jvm_config(env) = if std.endsWith(_base_env(env), '-ce') then 'graal-core' else 'graal-enterprise';

local espresso_configs = ['jvm-ce', 'jvm-ee', 'native-ce', 'native-ee'];
local benchmark_suites = ['dacapo', 'renaissance', 'scala-dacapo'];

{
  local that = self,

  // platform-specific snippets
  common: graal_common.deps.sulong + {
    python_version: '3',
    environment+: {
      GRAALVM_CHECK_EXPERIMENTAL_OPTIONS: "true",
    },
    setup+: [
      ['cd', base.suite_name],
    ],
  },

  linux: {
    packages+: {
      ruby: "==3.0.2",
    },
  },

  linux_amd64: self.common + self.linux + graal_common.linux_amd64 + {
    packages+: {
      '00:devtoolset': '==11', # GCC 11.2, make 4.3, binutils 2.36, valgrind 3.17
    },
  },
  linux_aarch64: self.common + self.linux + graal_common.linux_aarch64 + {
    packages+: {
      '00:devtoolset': '==10', # GCC 10.2.1, make 4.2.1, binutils 2.35, valgrind 3.16.1
    },
  },

  e3: {
    capabilities+: ['no_frequency_scaling', 'tmpfs25g', 'e3'],
  },

  darwin_amd64: self.common + graal_common.darwin_amd64 + {
    environment+: {
      // for compatibility with macOS Big Sur
      MACOSX_DEPLOYMENT_TARGET: '11.0',
    },
    capabilities+: ['ram32gb'],
  },

  darwin_aarch64: self.common + graal_common.darwin_aarch64 + {
    environment+: {
      // for compatibility with macOS Big Sur
      MACOSX_DEPLOYMENT_TARGET: '11.0',
    },
  },

  windows: self.common + graal_common.windows_amd64,

  // generic targets
  gate:            {targets+: ['gate'], timelimit: "1:00:00"},
  postMerge:       {targets+: ['post-merge'],          notify_groups:: ['espresso']},
  dailyBench:      {targets+: ['bench', 'daily'],      notify_groups:: ['espresso']},
  daily:           {targets+: ['daily'],               notify_groups:: ['espresso']},
  weekly:          {targets+: ['weekly'],              notify_groups:: ['espresso']},
  monthly:         {targets+: ['monthly'],              notify_groups:: ['espresso']},
  weeklyBench:     {targets+: ['bench', 'weekly'],     notify_groups:: ['espresso']},
  onDemand:        {targets+: ['on-demand']},
  onDemandBench:   {targets+: ['bench', 'on-demand']},

  linux_amd64_21:    graal_common.labsjdk21 + graal_common.labsjdk21LLVM + self.linux_amd64,
  darwin_amd64_21:   graal_common.labsjdk21 + graal_common.labsjdk21LLVM + self.darwin_amd64,
  linux_aarch64_21:  graal_common.labsjdk21                              + self.linux_aarch64,
  darwin_aarch64_21: graal_common.labsjdk21                              + self.darwin_aarch64,
  windows_21:        graal_common.labsjdk21                              + self.windows + devkits["windows-jdk21"],

  // precise targets and capabilities
  jdk21_gate_linux_amd64        : self.gate          + self.linux_amd64_21,
  jdk21_gate_linux_aarch64      : self.gate          + self.linux_aarch64_21,
  jdk21_gate_darwin_amd64       : self.gate          + self.darwin_amd64_21,
  jdk21_gate_darwin_aarch64     : self.gate          + self.darwin_aarch64_21,
  jdk21_gate_windows_amd64      : self.gate          + self.windows_21,
  jdk21_bench_linux             : self.bench         + self.linux_amd64_21 + self.e3,
  jdk21_bench_darwin            : self.bench         + self.darwin_amd64_21,
  jdk21_bench_windows           : self.bench         + self.windows_21,
  jdk21_daily_linux_amd64       : self.daily         + self.linux_amd64_21,
  jdk21_daily_linux_aarch64     : self.daily         + self.linux_aarch64_21,
  jdk21_daily_darwin_amd64      : self.daily         + self.darwin_amd64_21,
  jdk21_daily_darwin_aarch64    : self.daily         + self.darwin_aarch64_21,
  jdk21_daily_windows_amd64     : self.daily         + self.windows_21,
  jdk21_daily_bench_linux       : self.dailyBench    + self.linux_amd64_21 + self.e3,
  jdk21_daily_bench_darwin      : self.dailyBench    + self.darwin_amd64_21,
  jdk21_daily_bench_windows     : self.dailyBench    + self.windows_21,
  jdk21_weekly_linux_amd64      : self.weekly        + self.linux_amd64_21,
  jdk21_weekly_linux_aarch64    : self.weekly        + self.linux_aarch64_21,
  jdk21_weekly_darwin_amd64     : self.weekly        + self.darwin_amd64_21,
  jdk21_weekly_darwin_aarch64   : self.weekly        + self.darwin_aarch64_21,
  jdk21_weekly_windows_amd64    : self.weekly        + self.windows_21,
  jdk21_monthly_linux_amd64     : self.monthly        + self.linux_amd64_21,
  jdk21_monthly_linux_aarch64   : self.monthly        + self.linux_aarch64_21,
  jdk21_monthly_darwin_amd64    : self.monthly        + self.darwin_amd64_21,
  jdk21_monthly_darwin_aarch64  : self.monthly        + self.darwin_aarch64_21,
  jdk21_monthly_windows_amd64   : self.monthly        + self.windows_21,
  jdk21_weekly_bench_linux      : self.weeklyBench   + self.linux_amd64_21 + self.e3,
  jdk21_weekly_bench_darwin     : self.weeklyBench   + self.darwin_amd64_21,
  jdk21_weekly_bench_windows    : self.weeklyBench   + self.windows_21,
  jdk21_on_demand_linux         : self.onDemand      + self.linux_amd64_21,
  jdk21_on_demand_darwin        : self.onDemand      + self.darwin_amd64_21,
  jdk21_on_demand_windows       : self.onDemand      + self.windows_21,
  jdk21_on_demand_bench_linux   : self.onDemandBench + self.linux_amd64_21 + self.e3,
  jdk21_on_demand_bench_darwin  : self.onDemandBench + self.darwin_amd64_21,
  jdk21_on_demand_bench_windows : self.onDemandBench + self.windows_21,

  // shared snippets
  eclipse: graal_common.deps.eclipse,

  jdt: {
    environment+: {
      JDT: "builtin",
    },
  },

  bench_upload: {
    teardown+: [
      ['bench-uploader.py', 'bench-results.json'],
    ]
  },

  // shared functions
  _mx(env, args): ['mx', '--env', env] + args,

  build_espresso(env, debug=false, extra_mx_args=[]): {
    run+: [
      ['mx', 'sversions'],
      that._mx(env, (if debug then ['--debug-images'] else []) + extra_mx_args + ['build']),
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

  host_jvm(env, java_version): 'graalvm-espresso-' + _base_env(env),
  host_jvm_config(env): if std.startsWith(env, 'jvm') then 'jvm' else 'native',

  espresso_benchmark(env, suite, host_jvm=null, host_jvm_config=null, guest_jvm='espresso', guest_jvm_config='default', fork_file=null, extra_args=[], timelimit='3:00:00'):
    self.build_espresso(env) +
    {
      run+: that.maybe_set_ld_debug_flag(env) + [
        that._mx(env, ['benchmark', '--results-file', 'bench-results.json'] +
          (if (fork_file != null) then ['--fork-count-file', fork_file] else []) + [
            suite,
            '--',
            '--jvm=' + if host_jvm == null then that.host_jvm(env, self.jdk_version) else host_jvm,
            '--jvm-config=' + if host_jvm_config == null then that.host_jvm_config(env) else host_jvm_config,
            '--guest',
            '--jvm=' + guest_jvm,
            '--jvm-config=' + guest_jvm_config,
            '--vm.Xss32m'
          ] + extra_args
        ),
      ],
      timelimit: timelimit,
    }
    + self.bench_upload,

  espresso_minheap_benchmark(env, suite, guest_jvm_config):
    self.espresso_benchmark(env, suite, host_jvm='server', host_jvm_config='hosted', guest_jvm='espresso-minheap', guest_jvm_config=guest_jvm_config, extra_args=['--', '--iterations', '1']),

  espresso_interpreter_benchmark(env, suite, host_jvm=null):
    self.espresso_benchmark(env, suite, host_jvm=host_jvm, guest_jvm_config='interpreter', extra_args=['--', '--iterations', '10']),

  scala_dacapo_warmup_benchmark(env, guest_jvm_config='default', extra_args=[]):
    self.espresso_benchmark(
      env,
      self.scala_dacapo_jvm_warmup,
      guest_jvm_config=guest_jvm_config,
      extra_args=extra_args,
      timelimit='5:00:00'
    ),

  scala_dacapo_benchmark(env, guest_jvm_config, extra_args=[]):
    self.espresso_benchmark(
      env,
      self.scala_dacapo_fast,
      guest_jvm_config=guest_jvm_config,
      extra_args=extra_args,
      timelimit=if std.endsWith(_base_env(env), 'ce') then '7:30:00' else '3:00:00'
    ),

  dacapo_benchmark(env, guest_jvm_config, extra_args=[]):
    self.espresso_benchmark(
      env,
      self.dacapo_stable(env),
      guest_jvm_config=guest_jvm_config,
      extra_args=extra_args,
      timelimit=if std.endsWith(_base_env(env), 'ce') then '7:30:00' else '3:00:00'
    ),


  graal_benchmark(env, suite, host_jvm='server', host_jvm_config=_graal_host_jvm_config(env), extra_args=[]):
    self.build_espresso(env) +
    {
      run+: [
        that._mx(env, [
            'benchmark',
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
  scala_dacapo_jvm_warmup: 'scala-dacapo-warmup:*[scalap,scalac,scaladoc,scalaxb]',

  dacapo_stable(env): if std.startsWith(env, 'jvm')
    # exclude pmd and lusearch
    then 'dacapo:*[avrora,h2,fop,jython,luindex,sunflow,xalan]'
    # exclude fop on native
    else if env == 'native-ce'
      # additionally exclude luindex on native-ce: it gets stuck on the first interation
      then 'dacapo:*[avrora,h2,jython,lusearch,pmd,sunflow,xalan]'
      else 'dacapo:*[avrora,h2,jython,luindex,lusearch,pmd,sunflow,xalan]',

  # exclude scalatest, which goes into deopt loop and becomes slower on every subsequent operation
  scala_dacapo_fast: 'scala-dacapo:*[apparat,factorie,kiama,scalac,scaladoc,scalap,scalariform,scalaxb,tmt]',

  builds: [
    // Gates
    that.jdk21_gate_linux_amd64 + that.eclipse + that.jdt + that.espresso_gate(allow_warnings=false, tags='style,fullbuild', timelimit='35:00', name='gate-espresso-style-jdk21-linux-amd64'),
  ],
}
