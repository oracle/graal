local graal_common = import '../../../ci/ci_common/common.jsonnet';
local base = import '../ci.jsonnet';
local utils = import '../../../ci/ci_common/common-utils.libsonnet';

local devkits = graal_common.devkits;

local _base_env(env) = if std.endsWith(env, '-llvm') then std.substr(env, 0, std.length(env) - 5) else env;

local _graal_host_jvm_config(env) = if std.endsWith(_base_env(env), '-ce') then 'graal-core' else 'graal-enterprise';

local espresso_configs = ['jvm-ce', 'jvm-ee', 'native-ce', 'native-ee'];
local benchmark_suites = ['dacapo', 'renaissance', 'scala-dacapo'];

{
  local that = self,

  // platform-specific snippets
  common: graal_common.deps.sulong + graal_common.deps.espresso + {
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

  linux_amd64: self.common + self.linux + graal_common.linux_amd64,
  linux_aarch64: self.common + self.linux + graal_common.linux_aarch64,

  x52: {
    capabilities+: ['no_frequency_scaling', 'tmpfs25g', 'x52'],
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

  espresso_jdk_21: {
    downloads+: {
      "ESPRESSO_JAVA_HOME": graal_common.labsjdk21.downloads["JAVA_HOME"],
    },
  },
  espresso_jdk_21_llvm: {
    downloads+: {
      "ESPRESSO_LLVM_JAVA_HOME": graal_common.labsjdk21LLVM.downloads["LLVM_JAVA_HOME"],
    },
  },

  espresso_jdk_25: {
    downloads+: {
      "ESPRESSO_JAVA_HOME": graal_common.labsjdk25.downloads["JAVA_HOME"],
    },
  },
  espresso_jdk_25_llvm: {
    downloads+: {
      "ESPRESSO_LLVM_JAVA_HOME": graal_common.labsjdk25LLVM.downloads["LLVM_JAVA_HOME"],
    },
  },

  espresso_jdkLatest_llvm: {
    downloads+: {
      "ESPRESSO_LLVM_JAVA_HOME": graal_common.labsjdkLatestLLVM.downloads["LLVM_JAVA_HOME"],
    },
  },

  predicates(with_compiler, with_native_image, with_vm, with_espresso=true): {
    assert !with_native_image || with_compiler,
    guard+: {
      includes: [
        "<graal>/.git/**",  # This ensure the .git directory is preserved in apply-predicates
        "<graal>/pyproject.toml",
        "<graal>/common.json",
        "<graal>/ci.jsonnet",
        "<graal>/ci/**",
        "<graal>/sdk/**",
        "<graal>/truffle/**",
        "<graal>/espresso-shared/**",
      ] + base.basic_guard_includes + (if with_espresso then [
        "<graal>/espresso/**",
        "<graal>/tools/**",
        "<graal>/regex/**",
        "<graal>/sulong/**",
      ] else []) + (if with_compiler then [
        "<graal>/compiler/**",
      ] + base.compiler_guard_includes else []) + (if with_native_image then [
        "<graal>/substratevm/**",
      ] + base.nativeimage_guard_includes else []) + (if with_vm then [
        "<graal>/vm/**",
      ] + base.vm_guard_includes else []),
    },
    setup+: [
      ['mx', 'sversions'],
      ['apply-predicates', '--delete-excluded', '--process-hidden', '--pattern-root', '..'] # we are the espresso directory
        + (if std.objectHasAll(self.guard, 'excludes') then ['--exclude=' + e for e in  self.guard.excludes] else [])
        + ['--include=' + e for e in  self.guard.includes]
    ],
  },

  // generic targets
  tier1:           {targets+: ['tier1'],               notify_groups:: ['espresso']},
  tier2:           {targets+: ['tier2'],               notify_groups:: ['espresso']},
  tier3:           {targets+: ['tier3'],               notify_groups:: ['espresso']},
  tier4:           {targets+: ['tier4'],               notify_groups:: ['espresso']},
  postMerge:       {targets+: ['post-merge'],          notify_groups:: ['espresso']},
  dailyBench:      {targets+: ['bench', 'daily'],      notify_groups:: ['espresso']},
  daily:           {targets+: ['daily'],               notify_groups:: ['espresso']},
  weekly:          {targets+: ['weekly'],              notify_groups:: ['espresso']},
  monthly:         {targets+: ['monthly'],             notify_groups:: ['espresso']},
  weeklyBench:     {targets+: ['bench', 'weekly'],     notify_groups:: ['espresso']},
  monthlyBench:    {targets+: ['bench', 'monthly'],    notify_groups:: ['espresso']},
  onDemand:        {targets+: ['on-demand']},
  onDemandBench:   {targets+: ['bench', 'on-demand']},

  linux_amd64_21:    self.espresso_jdk_21 + graal_common.labsjdkLatest + self.espresso_jdk_21_llvm + self.linux_amd64,
  darwin_amd64_21:   self.espresso_jdk_21 + graal_common.labsjdkLatest + self.espresso_jdk_21_llvm + self.darwin_amd64,
  linux_aarch64_21:  self.espresso_jdk_21 + graal_common.labsjdkLatest                             + self.linux_aarch64,
  darwin_aarch64_21: self.espresso_jdk_21 + graal_common.labsjdkLatest                             + self.darwin_aarch64,
  windows_21:        self.espresso_jdk_21 + graal_common.labsjdkLatest                             + self.windows + devkits["windows-jdk-latest"],

  linux_amd64_25:    self.espresso_jdk_25 + graal_common.labsjdkLatest + self.espresso_jdk_25_llvm + self.linux_amd64,
  darwin_amd64_25:   self.espresso_jdk_25 + graal_common.labsjdkLatest + self.espresso_jdk_25_llvm + self.darwin_amd64,
  linux_aarch64_25:  self.espresso_jdk_25 + graal_common.labsjdkLatest                             + self.linux_aarch64,
  darwin_aarch64_25: self.espresso_jdk_25 + graal_common.labsjdkLatest                             + self.darwin_aarch64,
  windows_25:        self.espresso_jdk_25 + graal_common.labsjdkLatest                             + self.windows + devkits["windows-jdk-latest"],

  linux_amd64_latest:                       graal_common.labsjdkLatest + self.espresso_jdkLatest_llvm + self.linux_amd64,
  linux_aarch64_latest:                     graal_common.labsjdkLatest                                + self.linux_aarch64,

  linux_amd64_graalvm21: self.espresso_jdk_21 + graal_common.graalvmee21 + self.espresso_jdk_21_llvm  + self.linux_amd64,



  // precise targets and capabilities
  jdk21_tier1_linux_amd64       : self.tier1         + self.linux_amd64_21,
  jdk21_tier2_linux_amd64       : self.tier2         + self.linux_amd64_21,
  jdk21_tier3_linux_amd64       : self.tier3         + self.linux_amd64_21,
  jdk21_tier3_linux_aarch64     : self.tier3         + self.linux_aarch64_21,
  jdk21_tier3_darwin_aarch64    : self.tier3         + self.darwin_aarch64_21,
  jdk21_tier4_linux_amd64       : self.tier4         + self.linux_amd64_21,
  jdk21_bench_linux             : self.bench         + self.linux_amd64_21 + self.x52,
  jdk21_bench_darwin            : self.bench         + self.darwin_amd64_21,
  jdk21_bench_windows           : self.bench         + self.windows_21,
  jdk21_daily_linux_amd64       : self.daily         + self.linux_amd64_21,
  jdk21_daily_linux_aarch64     : self.daily         + self.linux_aarch64_21,
  jdk21_daily_darwin_amd64      : self.daily         + self.darwin_amd64_21,
  jdk21_daily_darwin_aarch64    : self.daily         + self.darwin_aarch64_21,
  jdk21_daily_windows_amd64     : self.daily         + self.windows_21,
  jdk21_daily_bench_linux       : self.dailyBench    + self.linux_amd64_21 + self.x52,
  graalvm21_daily_bench_linux   : self.dailyBench    + self.linux_amd64_graalvm21 + self.x52,
  jdk21_daily_bench_darwin      : self.dailyBench    + self.darwin_amd64_21,
  jdk21_daily_bench_windows     : self.dailyBench    + self.windows_21,
  jdk21_weekly_linux_amd64      : self.weekly        + self.linux_amd64_21,
  jdk21_weekly_linux_aarch64    : self.weekly        + self.linux_aarch64_21,
  jdk21_weekly_darwin_amd64     : self.weekly        + self.darwin_amd64_21,
  jdk21_weekly_darwin_aarch64   : self.weekly        + self.darwin_aarch64_21,
  jdk21_weekly_windows_amd64    : self.weekly        + self.windows_21,
  jdk21_monthly_linux_amd64     : self.monthly       + self.linux_amd64_21,
  jdk21_monthly_linux_aarch64   : self.monthly       + self.linux_aarch64_21,
  jdk21_monthly_darwin_amd64    : self.monthly       + self.darwin_amd64_21,
  jdk21_monthly_darwin_aarch64  : self.monthly       + self.darwin_aarch64_21,
  jdk21_monthly_windows_amd64   : self.monthly       + self.windows_21,
  jdk21_weekly_bench_linux      : self.weeklyBench   + self.linux_amd64_21 + self.x52,
  graalvm21_weekly_bench_linux  : self.weeklyBench    + self.linux_amd64_graalvm21 + self.x52,
  jdk21_weekly_bench_darwin     : self.weeklyBench   + self.darwin_amd64_21,
  jdk21_weekly_bench_windows    : self.weeklyBench   + self.windows_21,
  jdk21_monthly_bench_linux     : self.monthlyBench  + self.linux_amd64_21 + self.x52,
  graalvm21_monthly_bench_linux : self.monthlyBench    + self.linux_amd64_graalvm21 + self.x52,
  jdk21_on_demand_linux         : self.onDemand      + self.linux_amd64_21,
  jdk21_on_demand_darwin        : self.onDemand      + self.darwin_amd64_21,
  jdk21_on_demand_windows       : self.onDemand      + self.windows_21,
  jdk21_on_demand_bench_linux   : self.onDemandBench + self.linux_amd64_21 + self.x52,
  jdk21_on_demand_bench_darwin  : self.onDemandBench + self.darwin_amd64_21,
  jdk21_on_demand_bench_windows : self.onDemandBench + self.windows_21,

  jdk25_tier1_linux_amd64       : self.tier1          + self.linux_amd64_25,
  jdk25_tier2_linux_amd64       : self.tier2          + self.linux_amd64_25,
  jdk25_tier3_linux_amd64       : self.tier3         + self.linux_amd64_25,
  jdk25_tier3_linux_aarch64     : self.tier3         + self.linux_aarch64_25,
  jdk25_tier3_darwin_aarch64    : self.tier3         + self.darwin_aarch64_25,
  jdk25_tier4_linux_amd64       : self.tier4         + self.linux_amd64_25,
  jdk25_tier4_linux_aarch64     : self.tier4         + self.linux_aarch64_25,
  jdk25_daily_linux_amd64       : self.daily         + self.linux_amd64_25,
  jdk25_daily_linux_aarch64     : self.daily         + self.linux_aarch64_25,
  jdk25_daily_darwin_amd64      : self.daily         + self.darwin_amd64_25,
  jdk25_daily_darwin_aarch64    : self.daily         + self.darwin_aarch64_25,
  jdk25_daily_windows_amd64     : self.daily         + self.windows_25,
  jdk25_weekly_linux_amd64      : self.weekly        + self.linux_amd64_25,

  jdkLatest_tier1_linux_amd64   : self.tier1         + self.linux_amd64_latest,
  jdkLatest_tier2_linux_amd64   : self.tier2         + self.linux_amd64_latest,
  jdkLatest_tier3_linux_amd64   : self.tier3         + self.linux_amd64_latest,
  jdkLatest_daily_linux_amd64   : self.daily         + self.linux_amd64_latest,
  jdkLatest_daily_linux_aarch64 : self.daily         + self.linux_aarch64_latest,
  jdkLatest_weekly_linux_amd64  : self.weekly        + self.linux_amd64_latest,
  jdkLatest_weekly_linux_aarch64: self.weekly        + self.linux_aarch64_latest,

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

  build_espresso(env, debug=false, extra_mx_args=[], default_env_traget=true, extra_targets=[], extra_dynamic_imports=[]): {
    local standalone = if std.startsWith(env, 'jvm') then 'ESPRESSO_JVM_STANDALONE' else 'ESPRESSO_NATIVE_STANDALONE',
    local targets = (if default_env_traget then [standalone] else []) + extra_targets,
    local targets_args = if std.length(targets) > 0 then ['--targets=' + std.join(',', targets)] else [],
    local extra_dynamic_imports_args = if std.length(extra_dynamic_imports) > 0 then ['--dynamicimports', std.join(',', extra_dynamic_imports)] else [],
    run+: [
      ['mx'] + extra_dynamic_imports_args + ['sversions'],
      that._mx(env, (if debug then ['--debug-images'] else []) + extra_mx_args + extra_dynamic_imports_args + ['build'] + targets_args),
    ] + if default_env_traget then [
      ['set-export', 'ESPRESSO_HOME', that._mx(env, ['--quiet', '--no-warning'] + extra_mx_args + extra_dynamic_imports_args + ['path', '--output', standalone])]
    ] else [],
  },

  // LD_DEBUG=unused is a workaround for: symbol lookup error: jre/lib/amd64/libnio.so: undefined symbol: fstatat64
  maybe_set_ld_debug_flag(env): if std.startsWith(env, 'jvm') then [['set-export', 'LD_DEBUG', 'unused']] else [],

  espresso_gate(allow_warnings, tags, ld_debug=false, mx_args=[], imports=null, gate_args=[], timelimit='15:00', name=null, coverage=false): {
    local mx_cmd =
      ['mx']
      + mx_args
      + (if imports != null then ['--dynamicimports=' + imports] else []),
    run+: [
      if ld_debug then ['set-export', 'LD_DEBUG', 'unused'] else ['unset', 'LD_DEBUG'],
      mx_cmd + ['--strict-compliance', 'gate', '--strict-mode', '--tags', tags]
             + (if allow_warnings then ['--no-warning-as-error'] else [])
             + (if coverage then ['--jacoco-omit-excluded', '--jacoco-relativize-paths', '--jacoco-omit-src-gen', '--jacocout=coverage', '--jacoco-format=lcov'] else [])
             + gate_args,
    ],
  }
  + (if timelimit != null then {timelimit: timelimit} else {})
  + (if name != null then {name: name} else {})
  + (if coverage then {
    teardown+: [
      ['mx', 'sversions', '--print-repositories', '--json', '|', 'coverage-uploader.py', '--associated-repos', '-'],
    ],
  } else {})
  + graal_common.deps.spotbugs,

  host_jvm(env, java_version): if std.startsWith(env, 'jvm') && utils.contains(env, '-unchained-') then 'java-home'
    else if utils.contains(env, '-ee-') then 'graalvm-ee' else 'graalvm-ce',
  host_jvm_config(env): if std.startsWith(env, 'jvm') then (if utils.contains(env, '-unchained-') then 'default' else 'jvm') else 'native',
  bench_vm_selection_args(env, host_jvm=null, host_jvm_config=null, guest_jvm='espresso', espresso_jvm_config='default'): if std.startsWith(env, 'jvm') then [
    '--jvm=' + if host_jvm == null then that.host_jvm(env, self.jdk_version) else host_jvm,
    '--jvm-config=' + if host_jvm_config == null then that.host_jvm_config(env) else host_jvm_config,
    '--guest',
    '--jvm=' + guest_jvm,
    '--jvm-config=' + espresso_jvm_config,
  ] else [
    '--jvm=espresso-native-standalone',
    '--jvm-config=' + espresso_jvm_config,
  ],

  espresso_benchmark(env, suite, host_jvm=null, host_jvm_config=null, guest_jvm='espresso', guest_jvm_config='default', fork_file=null, extra_args=[], timelimit='3:00:00'):
    self.build_espresso(env, default_env_traget=false) +
    {
      run+: that.maybe_set_ld_debug_flag(env) + [
        that._mx(env, ['benchmark', '--results-file', 'bench-results.json'] +
          (if (fork_file != null) then ['--fork-count-file', fork_file] else []) + [
            suite,
            '--',
          ] + that.bench_vm_selection_args(env, host_jvm=host_jvm, host_jvm_config=host_jvm_config, guest_jvm=guest_jvm, espresso_jvm_config=guest_jvm_config) + [
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
      timelimit=if std.endsWith(_base_env(env), 'ce') then '1:10:00' else '1:00:00'
    ),


  graal_benchmark(env, suite, host_jvm='server', host_jvm_config=_graal_host_jvm_config(env), extra_args=[]):
    self.build_espresso(env, default_env_traget=false) +
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

  dacapo_stable(env): 'dacapo:*[fop,lusearch,luindex,sunflow,xalan]',

  # exclude scalatest, which goes into deopt loop and becomes slower on every subsequent operation
  scala_dacapo_fast: 'scala-dacapo:*[apparat,factorie,kiama,scalac,scaladoc,scalap,scalariform,scalaxb,tmt]',

  local _builds = [
    // Gates
    that.jdk21_tier1_linux_amd64 + that.eclipse + that.jdt + that.predicates(false, false, false) + that.espresso_gate(allow_warnings=false, tags='style,fullbuild,imports', timelimit='35:00', name='gate-espresso-style-jdk21onLatest-linux-amd64'),
  ],

  builds: utils.add_defined_in(_builds, std.thisFile),
}
