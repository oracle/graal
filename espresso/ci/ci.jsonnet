{
  local common = import 'ci_common/common.jsonnet',
  local utils = import '../../ci/ci_common/common-utils.libsonnet',

  suite_name: 'espresso',
  basic_guard_includes: [],
  compiler_guard_includes: [],
  nativeimage_guard_includes: [],
  vm_guard_includes: [],

  local _builds = common.builds + [
    // Benchmarks
    // AWFY peak perf. benchmarks
    common.jdk21_weekly_bench_linux    + common.espresso_benchmark('jvm-ce-llvm', 'awfy:*'                                        , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-jvm-ce-awfy-jdk21-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.espresso_benchmark('jvm-ce-llvm', 'awfy:*'   , guest_jvm_config='safe'            , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-jvm-ce-awfy-safe-jdk21-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.espresso_benchmark('jvm-ce-llvm', 'awfy:*'   , guest_jvm_config='array-based'     , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-jvm-ce-awfy-array-based-jdk21-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.espresso_benchmark('jvm-ce-llvm', 'awfy:*'   , guest_jvm_config='array-based-safe', extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-jvm-ce-awfy-array-based-safe-jdk21-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.espresso_benchmark('native-ce-llvm', 'awfy:*'                                     , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-native-ce-awfy-jdk21-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.espresso_benchmark('native-ce-llvm', 'awfy:*', guest_jvm_config='safe'            , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-native-ce-awfy-safe-jdk21-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.espresso_benchmark('native-ce-llvm', 'awfy:*', guest_jvm_config='field-based'     , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-native-ce-awfy-field-based-jdk21-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.espresso_benchmark('native-ce-llvm', 'awfy:*', guest_jvm_config='field-based-safe', extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-native-ce-awfy-field-based-safe-jdk21-linux-amd64'},

    // AWFY interpreter benchmarks
    common.jdk21_weekly_bench_linux    + common.espresso_interpreter_benchmark('jvm-ce-llvm', 'awfy:*')                                                                              + {name: 'weekly-bench-espresso-jvm-ce-awfy_interpreter-jdk21-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.espresso_interpreter_benchmark('native-ce-llvm', 'awfy:*')                                                                           + {name: 'weekly-bench-espresso-native-ce-awfy_interpreter-jdk21-linux-amd64'},

    // Scala DaCapo warmup benchmarks
    common.jdk21_weekly_bench_linux    + common.scala_dacapo_warmup_benchmark('jvm-ce-llvm'   , guest_jvm_config='single-tier'    , extra_args=['--vm.XX:ReservedCodeCacheSize=1g']) + {name: 'weekly-bench-espresso-jvm-ce-scala_dacapo_warmup-single_tier-jdk21-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.scala_dacapo_warmup_benchmark('native-ce-llvm', guest_jvm_config='single-tier')                                                      + {name: 'weekly-bench-espresso-native-ce-scala_dacapo_warmup-single_tier-jdk21-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.scala_dacapo_warmup_benchmark('jvm-ce-llvm'   , guest_jvm_config='multi-tier'     , extra_args=['--vm.XX:ReservedCodeCacheSize=1g']) + {name: 'weekly-bench-espresso-jvm-ce-scala_dacapo_warmup-multi_tier-jdk21-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.scala_dacapo_warmup_benchmark('native-ce-llvm', guest_jvm_config='multi-tier')                                                       + {name: 'weekly-bench-espresso-native-ce-scala_dacapo_warmup-multi_tier-jdk21-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.graal_benchmark('jvm-ce-llvm', common.scala_dacapo_jvm_warmup)                                                                       + {name: 'weekly-bench-espresso-jvm-ce-scala_dacapo_warmup-reference-jdk21-linux-amd64'},

    common.jdk21_weekly_bench_linux    + common.scala_dacapo_warmup_benchmark('jvm-ce-llvm'   , guest_jvm_config='3-compiler-threads', extra_args=['--vm.XX:ReservedCodeCacheSize=1g']) + {name: 'weekly-bench-espresso-jvm-ce-scala_dacapo_warmup-3threads-jdk21-linux-amd64'},
    common.jdk21_weekly_bench_linux    + common.scala_dacapo_warmup_benchmark('native-ce-llvm', guest_jvm_config='3-compiler-threads')                                                  + {name: 'weekly-bench-espresso-native-ce-scala_dacapo_warmup-3threads-jdk21-linux-amd64'},

    // Scala DaCapo benchmarks
    common.jdk21_on_demand_bench_linux + common.scala_dacapo_benchmark('jvm-ce-llvm'          , guest_jvm_config='single-tier'    , extra_args=['--vm.XX:ReservedCodeCacheSize=1g']) + {name: 'ondemand-bench-espresso-jvm-ce-scala_dacapo-single_tier-jdk21-linux-amd64'},
    common.jdk21_on_demand_bench_linux + common.scala_dacapo_benchmark('native-ce-llvm'       , guest_jvm_config='single-tier')                                                      + {name: 'ondemand-bench-espresso-native-ce-scala_dacapo-single_tier-jdk21-linux-amd64'},
    common.jdk21_weekly_bench_linux + common.scala_dacapo_benchmark('jvm-ce-llvm'             , guest_jvm_config='multi-tier'     , extra_args=['--vm.XX:ReservedCodeCacheSize=1g']) + {name: 'weekly-bench-espresso-jvm-ce-scala_dacapo-multi_tier-jdk21-linux-amd64'},
    common.jdk21_weekly_bench_linux + common.scala_dacapo_benchmark('native-ce-llvm'          , guest_jvm_config='multi-tier')                                                       + {name: 'weekly-bench-espresso-native-ce-scala_dacapo-multi_tier-jdk21-linux-amd64'},

    // DaCapo benchmarks
    common.jdk21_on_demand_bench_linux + common.dacapo_benchmark('jvm-ce-llvm'                , guest_jvm_config='single-tier'    , extra_args=['--vm.XX:ReservedCodeCacheSize=1g']) + {name: 'ondemand-bench-espresso-jvm-ce-dacapo-single_tier-jdk21-linux-amd64'},
    common.jdk21_on_demand_bench_linux + common.dacapo_benchmark('native-ce-llvm'             , guest_jvm_config='single-tier')                                                      + {name: 'ondemand-bench-espresso-native-ce-dacapo-single_tier-jdk21-linux-amd64'},
    common.jdk21_weekly_bench_linux + common.dacapo_benchmark('jvm-ce-llvm'                   , guest_jvm_config='multi-tier'     , extra_args=['--vm.XX:ReservedCodeCacheSize=1g']) + {name: 'weekly-bench-espresso-jvm-ce-dacapo-multi_tier-jdk21-linux-amd64'},
    common.jdk21_weekly_bench_linux + common.dacapo_benchmark('native-ce-llvm'                , guest_jvm_config='multi-tier')                                                       + {name: 'weekly-bench-espresso-native-ce-dacapo-multi_tier-jdk21-linux-amd64'},

    // Memory footprint
    common.jdk21_on_demand_linux       + common.espresso_minheap_benchmark('jvm-ce-llvm', 'awfy:*', 'infinite-overhead')                                                             + {name: 'ondemand-bench-espresso-jvm-ce-awfy-minheap-infinite-ovh-jdk21-linux-amd64'},
    common.jdk21_on_demand_bench_linux + common.espresso_minheap_benchmark('jvm-ce-llvm', 'awfy:*', '1.5-overhead')                                                                  + {name: 'ondemand-bench-espresso-jvm-ce-awfy-minheap-1.5-ovh-jdk21-linux-amd64'},
  ],

  builds: utils.add_defined_in(_builds, std.thisFile),
}
