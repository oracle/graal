{
  local common = import 'ci_common/common.jsonnet',
  local utils = import '../../ci/ci_common/common-utils.libsonnet',

  suite_name: 'espresso',
  basic_guard_includes: [],
  compiler_guard_includes: [],
  nativeimage_guard_includes: [],
  vm_guard_includes: [],

  local espresso_compiler_stub_gate = common.eclipse + common.jdt + common.predicates(true, true, false) +
   common.espresso_gate(allow_warnings=false, tags='style,fullbuild', timelimit='35:00', name='gate-espresso-compiler-stub-style-jdkLatest-linux-amd64', imports='/substratevm', mx_args=['--native-images=false']) + {
    setup+: [
      ['cd', "../espresso-compiler-stub"],
    ],
    guard+: {
      includes+: [
        "<graal>/espresso-compiler-stub/**",
      ],
    },
  },

  local espresso_shared_gate = common.eclipse + common.jdt + common.predicates(false, false, false, false) +
   common.espresso_gate(allow_warnings=false, tags='style,fullbuild', timelimit='35:00', name='gate-espresso-shared-style-jdkLatest-linux-amd64') + {
    setup+: [
      ['cd', "../espresso-shared"],
    ],
  },

  local _builds = common.builds + [
    common.jdkLatest_tier1_linux_amd64 + espresso_compiler_stub_gate,
    common.jdkLatest_tier1_linux_amd64 + espresso_shared_gate,
    // Benchmarks
    // AWFY peak perf. benchmarks
    common.jdk21_weekly_bench_linux    + common.espresso_benchmark('jvm-ce-llvm', 'awfy:*'                                        , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-jvm-ce-awfy-jdk21onLatest-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.espresso_benchmark('jvm-ce-llvm', 'awfy:*'   , guest_jvm_config='safe'            , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-jvm-ce-awfy-safe-jdk21onLatest-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.espresso_benchmark('jvm-ce-llvm', 'awfy:*'   , guest_jvm_config='array-based'     , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-jvm-ce-awfy-array-based-jdk21onLatest-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.espresso_benchmark('jvm-ce-llvm', 'awfy:*'   , guest_jvm_config='array-based-safe', extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-jvm-ce-awfy-array-based-safe-jdk21onLatest-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.espresso_benchmark('native-ce-llvm', 'awfy:*'                                     , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-native-ce-awfy-jdk21onLatest-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.espresso_benchmark('native-ce-llvm', 'awfy:*', guest_jvm_config='safe'            , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-native-ce-awfy-safe-jdk21onLatest-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.espresso_benchmark('native-ce-llvm', 'awfy:*', guest_jvm_config='field-based'     , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-native-ce-awfy-field-based-jdk21onLatest-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.espresso_benchmark('native-ce-llvm', 'awfy:*', guest_jvm_config='field-based-safe', extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-native-ce-awfy-field-based-safe-jdk21onLatest-linux-amd64'},

    // AWFY interpreter benchmarks
    common.jdk21_weekly_bench_linux    + common.espresso_interpreter_benchmark('jvm-ce-llvm', 'awfy:*')                                                                              + {name: 'weekly-bench-espresso-jvm-ce-awfy_interpreter-jdk21onLatest-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.espresso_interpreter_benchmark('native-ce-llvm', 'awfy:*')                                                                           + {name: 'weekly-bench-espresso-native-ce-awfy_interpreter-jdk21onLatest-linux-amd64'},

    // Scala DaCapo benchmarks
    common.jdk21_on_demand_bench_linux + common.scala_dacapo_benchmark('jvm-ce-llvm'          , guest_jvm_config='single-tier'    , extra_args=['--vm.XX:ReservedCodeCacheSize=1g']) + {name: 'ondemand-bench-espresso-jvm-ce-scala_dacapo-single_tier-jdk21onLatest-linux-amd64'},
    common.jdk21_on_demand_bench_linux + common.scala_dacapo_benchmark('native-ce-llvm'       , guest_jvm_config='single-tier')                                                      + {name: 'ondemand-bench-espresso-native-ce-scala_dacapo-single_tier-jdk21onLatest-linux-amd64'},
    common.jdk21_monthly_bench_linux   + common.scala_dacapo_benchmark('jvm-ce-llvm'          , guest_jvm_config='multi-tier'     , extra_args=['--vm.XX:ReservedCodeCacheSize=1g']) + {name: 'monthly-bench-espresso-jvm-ce-scala_dacapo-multi_tier-jdk21onLatest-linux-amd64'},
    common.jdk21_monthly_bench_linux   + common.scala_dacapo_benchmark('native-ce-llvm'       , guest_jvm_config='multi-tier')                                                       + {name: 'monthly-bench-espresso-native-ce-scala_dacapo-multi_tier-jdk21onLatest-linux-amd64'},

    // DaCapo benchmarks
    common.jdk21_on_demand_bench_linux + common.dacapo_benchmark('jvm-ce-llvm'                , guest_jvm_config='single-tier'    , extra_args=['--vm.XX:ReservedCodeCacheSize=1g']) + {name: 'ondemand-bench-espresso-jvm-ce-dacapo-single_tier-jdk21onLatest-linux-amd64'},
    common.jdk21_on_demand_bench_linux + common.dacapo_benchmark('native-ce-llvm'             , guest_jvm_config='single-tier')                                                      + {name: 'ondemand-bench-espresso-native-ce-dacapo-single_tier-jdk21onLatest-linux-amd64'},
    common.jdk21_monthly_bench_linux   + common.dacapo_benchmark('jvm-ce-llvm'                , guest_jvm_config='multi-tier'     , extra_args=['--vm.XX:ReservedCodeCacheSize=1g']) + {name: 'monthly-bench-espresso-jvm-ce-dacapo-multi_tier-jdk21onLatest-linux-amd64'},
    common.jdk21_monthly_bench_linux   + common.dacapo_benchmark('native-ce-llvm'             , guest_jvm_config='multi-tier')                                                       + {name: 'monthly-bench-espresso-native-ce-dacapo-multi_tier-jdk21onLatest-linux-amd64'},

    // Memory footprint
    common.jdk21_on_demand_linux       + common.espresso_minheap_benchmark('jvm-ce-llvm', 'awfy:*', 'infinite-overhead')                                                             + {name: 'ondemand-bench-espresso-jvm-ce-awfy-minheap-infinite-ovh-jdk21onLatest-linux-amd64'},
    common.jdk21_on_demand_bench_linux + common.espresso_minheap_benchmark('jvm-ce-llvm', 'awfy:*', '1.5-overhead')                                                                  + {name: 'ondemand-bench-espresso-jvm-ce-awfy-minheap-1.5-ovh-jdk21onLatest-linux-amd64'},
  ],

  builds: utils.add_defined_in(_builds, std.thisFile),
}
