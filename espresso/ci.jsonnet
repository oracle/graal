{
  local graal_common = import '../common.json',
  local common = import 'ci_common/common.jsonnet',
  local jdks = graal_common.jdks,
  local devkits = graal_common.devkits,

  suite_name: 'espresso',

  jdk8: {
    downloads+: {
      JAVA_HOME: jdks.openjdk8,
    },
  },

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

  windows_8 : devkits["windows-openjdk8"] + common.windows,
  windows_11 : devkits["windows-jdk11"] + common.windows,
  windows_17 : devkits["windows-jdk17"] + common.windows,

  builds: common.builds + [
    // Benchmarks
    // AWFY peak perf. benchmarks
    common.jdk8_weekly_bench_linux              + common.espresso_benchmark('jvm-ce', 'awfy:*'                                        , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-jvm-ce-awfy-jdk8-linux-amd64'},
    common.jdk8_weekly_bench_linux              + common.espresso_benchmark('jvm-ce', 'awfy:*'   , guest_jvm_config='safe'            , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-jvm-ce-awfy-safe-jdk8-linux-amd64'},
    common.jdk8_weekly_bench_linux              + common.espresso_benchmark('jvm-ce', 'awfy:*'   , guest_jvm_config='array-based'     , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-jvm-ce-awfy-array-based-jdk8-linux-amd64'},
    common.jdk8_weekly_bench_linux              + common.espresso_benchmark('jvm-ce', 'awfy:*'   , guest_jvm_config='array-based-safe', extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-jvm-ce-awfy-array-based-safe-jdk8-linux-amd64'},
    common.jdk8_weekly_bench_linux              + common.espresso_benchmark('native-ce', 'awfy:*'                                     , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-native-ce-awfy-jdk8-linux-amd64'},
    common.jdk8_weekly_bench_linux              + common.espresso_benchmark('native-ce', 'awfy:*', guest_jvm_config='safe'            , extra_args=['--vm.Xmx1g', '--vm.Xms1g'])         + {name: 'weekly-bench-espresso-native-ce-awfy-safe-jdk8-linux-amd64'},

    // AWFY interpreter benchmarks
    common.jdk8_weekly_bench_linux              + common.espresso_interpreter_benchmark('jvm-ce', 'awfy:*')                                                                              + {name: 'weekly-bench-espresso-jvm-ce-awfy_interpreter-jdk8-linux-amd64'},
    common.jdk8_weekly_bench_linux              + common.espresso_interpreter_benchmark('native-ce', 'awfy:*')                                                                           + {name: 'weekly-bench-espresso-native-ce-awfy_interpreter-jdk8-linux-amd64'},

    // Scala DaCapo warmup benchmarks
    common.jdk8_weekly_bench_linux              + common.scala_dacapo_warmup_benchmark('jvm-ce', 'single-tier'                        , extra_args=['--vm.XX:ReservedCodeCacheSize=1g']) + {name: 'weekly-bench-espresso-jvm-ce-scala_dacapo_warmup-single_tier-jdk8-linux-amd64'},
    common.jdk8_weekly_bench_linux              + common.scala_dacapo_warmup_benchmark('native-ce', 'single-tier')                                                                       + {name: 'weekly-bench-espresso-native-ce-scala_dacapo_warmup-single_tier-jdk8-linux-amd64'},
    common.jdk8_weekly_bench_linux              + common.scala_dacapo_warmup_benchmark('jvm-ce', 'multi-tier'                         , extra_args=['--vm.XX:ReservedCodeCacheSize=1g']) + {name: 'weekly-bench-espresso-jvm-ce-scala_dacapo_warmup-multi_tier-jdk8-linux-amd64'},
    common.jdk8_weekly_bench_linux              + common.scala_dacapo_warmup_benchmark('native-ce', 'multi-tier')                                                                        + {name: 'weekly-bench-espresso-native-ce-scala_dacapo_warmup-multi_tier-jdk8-linux-amd64'},

    // On-demand benchmarks
    // Scala DaCapo warmup benchmarks
    common.jdk8_on_demand_bench_linux           + common.graal_benchmark('jvm-ce', common.scala_dacapo_jvm_warmup)                                                                       + {name: 'ondemand-bench-espresso-jvm-ce-scala_dacapo_warmup-jdk8-linux-amd64'},

    // Memory footprint
    common.jdk8_on_demand_linux                 + common.espresso_minheap_benchmark('jvm-ce', 'awfy:*', 'infinite-overhead')                                                             + {name: 'ondemand-bench-espresso-jvm-ce-awfy-minheap-infinite-ovh-jdk8-linux-amd64'},
    common.jdk8_on_demand_bench_linux           + common.espresso_minheap_benchmark('jvm-ce', 'awfy:*', '1.5-overhead')                                                                  + {name: 'ondemand-bench-espresso-jvm-ce-awfy-minheap-1.5-ovh-jdk8-linux-amd64'},

    // Longer-running benchmarks are on-demand
    // single tier
    common.jdk17_on_demand_bench_linux          + common.scala_dacapo_benchmark('jvm-ce'                , extra_args=['--vm.XX:ReservedCodeCacheSize=1g'])  + {name: 'ondemand-bench-espresso-jvm-ce-scala_dacapo-single_tier-jdk17-linux-amd64'},
    common.jdk17_on_demand_bench_linux          + common.scala_dacapo_benchmark('native-ce')                                                                + {name: 'ondemand-bench-espresso-native-ce-scala_dacapo-single_tier-jdk17-linux-amd64'},
    common.jdk17_on_demand_bench_linux          + common.dacapo_benchmark('jvm-ce'                      , extra_args=['--vm.XX:ReservedCodeCacheSize=1g'])  + {name: 'ondemand-bench-espresso-jvm-ce-dacapo-single_tier-jdk17-linux-amd64'},
    common.jdk17_on_demand_bench_linux          + common.dacapo_benchmark('native-ce')                                                                      + {name: 'ondemand-bench-espresso-native-ce-dacapo-single_tier-jdk17-linux-amd64'},
    // multi tier
    common.jdk17_on_demand_bench_linux          + common.scala_dacapo_benchmark('jvm-ce', 'multi-tier'  , extra_args=['--vm.XX:ReservedCodeCacheSize=1g'])  + {name: 'ondemand-bench-espresso-jvm-ce-scala_dacapo-multi_tier-jdk17-linux-amd64'},
    common.jdk17_on_demand_bench_linux          + common.scala_dacapo_benchmark('native-ce', 'multi-tier')                                                  + {name: 'ondemand-bench-espresso-native-ce-scala_dacapo-multi_tier-jdk17-linux-amd64'},
    common.jdk17_on_demand_bench_linux          + common.dacapo_benchmark('jvm-ce', 'multi-tier'        , extra_args=['--vm.XX:ReservedCodeCacheSize=1g'])  + {name: 'ondemand-bench-espresso-jvm-ce-dacapo-multi_tier-jdk17-linux-amd64'},
    common.jdk17_on_demand_bench_linux          + common.dacapo_benchmark('native-ce', 'multi-tier')                                                        + {name: 'ondemand-bench-espresso-native-ce-dacapo-multi_tier-jdk17-linux-amd64'},
  ]
}
