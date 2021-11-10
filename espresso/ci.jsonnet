{
  local graal_common = import '../common.json',
  local common = import 'ci_common/common.jsonnet',
  local jdks = graal_common.jdks,
  local devkits = graal_common.devkits,

  suite_name: 'espresso',

  jdk11: {
    downloads+: {
      JAVA_HOME: jdks["labsjdk-ce-11"],
    },
  },

  jdk17: {
    downloads+: {
      JAVA_HOME: jdks["labsjdk-ce-17"],
    },
  },

  extra_jdk11: {
      downloads+: {
      EXTRA_JAVA_HOMES: jdks["labsjdk-ce-11"],
    },
  },

  windows_11 : devkits["windows-jdk11"] + common.windows,
  windows_17 : devkits["windows-jdk17"] + common.windows,

  builds: common.builds + [
    // Benchmarks
    // AWFY peak perf. benchmarks
    common.jdk11_weekly_bench_linux    + common.espresso_benchmark('jvm-ce', 'awfy:*'               , host_jvm='graalvm-espresso-jvm-ce-java11'                                        , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-jvm-ce-awfy-jdk11-linux-amd64'},
    common.jdk11_weekly_bench_linux    + common.espresso_benchmark('jvm-ce', 'awfy:*'               , host_jvm='graalvm-espresso-jvm-ce-java11'   , guest_jvm_config='safe'            , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-jvm-ce-awfy-safe-jdk11-linux-amd64'},
    common.jdk11_weekly_bench_linux    + common.espresso_benchmark('jvm-ce', 'awfy:*'               , host_jvm='graalvm-espresso-jvm-ce-java11'   , guest_jvm_config='array-based'     , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-jvm-ce-awfy-array-based-jdk11-linux-amd64'},
    common.jdk11_weekly_bench_linux    + common.espresso_benchmark('jvm-ce', 'awfy:*'               , host_jvm='graalvm-espresso-jvm-ce-java11'   , guest_jvm_config='array-based-safe', extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-jvm-ce-awfy-array-based-safe-jdk11-linux-amd64'},
    common.jdk11_weekly_bench_linux    + common.espresso_benchmark('native-ce', 'awfy:*'            , host_jvm='graalvm-espresso-native-ce-java11'                                     , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-native-ce-awfy-jdk11-linux-amd64'},
    common.jdk11_weekly_bench_linux    + common.espresso_benchmark('native-ce', 'awfy:*'            , host_jvm='graalvm-espresso-native-ce-java11', guest_jvm_config='safe'            , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-native-ce-awfy-safe-jdk11-linux-amd64'},

    // AWFY interpreter benchmarks
    common.jdk11_weekly_bench_linux    + common.espresso_interpreter_benchmark('jvm-ce', 'awfy:*'   , host_jvm='graalvm-espresso-jvm-ce-java11')                                                                                          + {name: 'weekly-bench-espresso-jvm-ce-awfy_interpreter-jdk11-linux-amd64'},
    common.jdk11_weekly_bench_linux    + common.espresso_interpreter_benchmark('native-ce', 'awfy:*', host_jvm='graalvm-espresso-native-ce-java11')                                                                                       + {name: 'weekly-bench-espresso-native-ce-awfy_interpreter-jdk11-linux-amd64'},

    // Scala DaCapo warmup benchmarks
    common.jdk11_weekly_bench_linux    + common.scala_dacapo_warmup_benchmark('jvm-ce'              , host_jvm='graalvm-espresso-jvm-ce-java11'   , guest_jvm_config='single-tier'    , extra_args=['--vm.XX:ReservedCodeCacheSize=1g']) + {name: 'weekly-bench-espresso-jvm-ce-scala_dacapo_warmup-single_tier-jdk11-linux-amd64'},
    common.jdk11_weekly_bench_linux    + common.scala_dacapo_warmup_benchmark('native-ce'           , host_jvm='graalvm-espresso-native-ce-java11', guest_jvm_config='single-tier')                                                      + {name: 'weekly-bench-espresso-native-ce-scala_dacapo_warmup-single_tier-jdk11-linux-amd64'},
    common.jdk11_weekly_bench_linux    + common.scala_dacapo_warmup_benchmark('jvm-ce'              , host_jvm='graalvm-espresso-jvm-ce-java11'   , guest_jvm_config='multi-tier'     , extra_args=['--vm.XX:ReservedCodeCacheSize=1g']) + {name: 'weekly-bench-espresso-jvm-ce-scala_dacapo_warmup-multi_tier-jdk11-linux-amd64'},
    common.jdk11_weekly_bench_linux    + common.scala_dacapo_warmup_benchmark('native-ce'           , host_jvm='graalvm-espresso-native-ce-java11', guest_jvm_config='multi-tier' )                                                      + {name: 'weekly-bench-espresso-native-ce-scala_dacapo_warmup-multi_tier-jdk11-linux-amd64'},

    // On-demand benchmarks
    // Scala DaCapo warmup benchmarks
    common.jdk11_on_demand_bench_linux + common.graal_benchmark('jvm-ce', common.scala_dacapo_jvm_fast(warmup=true))                                                                                                                     + {name: 'ondemand-bench-espresso-jvm-ce-scala_dacapo_warmup-jdk11-linux-amd64'},

    // Memory footprint
    common.jdk11_on_demand_linux       + common.espresso_minheap_benchmark('jvm-ce', 'awfy:*', 'infinite-overhead')                                                                                                                      + {name: 'ondemand-bench-espresso-jvm-ce-awfy-minheap-infinite-ovh-jdk11-linux-amd64'},
    common.jdk11_on_demand_bench_linux + common.espresso_minheap_benchmark('jvm-ce', 'awfy:*', '1.5-overhead')                                                                                                                           + {name: 'ondemand-bench-espresso-jvm-ce-awfy-minheap-1.5-ovh-jdk11-linux-amd64'},
  ]
}
