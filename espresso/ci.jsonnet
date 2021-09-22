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

  windows_8 : devkits["windows-openjdk8"] + common.common + {
    capabilities : ['windows', 'amd64']
  },

  windows_11 : devkits["windows-jdk11"] + common.common + {
    capabilities : ['windows', 'amd64']
  },

  windows_17 : devkits["windows-jdk17"] + common.common + {
    capabilities : ['windows', 'amd64']
  },

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
    common.jdk8_on_demand_bench_linux           + common.graal_benchmark('jvm-ce', common.scala_dacapo_jvm_fast(warmup=true))                                                            + {name: 'ondemand-bench-espresso-jvm-ce-scala_dacapo_warmup-jdk8-linux-amd64'},

    // Memory footprint
    common.jdk8_on_demand_linux                 + common.espresso_minheap_benchmark('jvm-ce', 'awfy:*', 'infinite-overhead')                                                             + {name: 'ondemand-bench-espresso-jvm-ce-awfy-minheap-infinite-ovh-jdk8-linux-amd64'},
    common.jdk8_on_demand_bench_linux           + common.espresso_minheap_benchmark('jvm-ce', 'awfy:*', '1.5-overhead')                                                                  + {name: 'ondemand-bench-espresso-jvm-ce-awfy-minheap-1.5-ovh-jdk8-linux-amd64'},
  ]
}
