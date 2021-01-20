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

  extra_jdk11: {
      downloads+: {
      EXTRA_JAVA_HOMES: jdks["labsjdk-ce-11"],
    },
  },

  windows_8 : devkits["windows-openjdk8"] + self.common + {
    capabilities : ['windows', 'amd64']
  },

  windows_11 : devkits["windows-jdk11"] + self.common + {
    capabilities : ['windows', 'amd64']
  },

  builds: common.builds + [
    // Benchmarks
    // AWFY peak perf. benchmarks
    common.jdk8_weekly_bench_linux              + common.espresso_benchmark('jvm-ce', 'awfy:*')                                    + {name: 'espresso-bench-jvm-ce-awfy-jdk8-linux-amd64'},
    common.jdk8_weekly_bench_linux              + common.espresso_benchmark('native-ce', 'awfy:*')                                 + {name: 'espresso-bench-native-ce-awfy-jdk8-linux-amd64'},

    // AWFY interpreter benchmarks
    common.jdk8_weekly_bench_linux              + common.espresso_interpreter_benchmark('jvm-ce', 'awfy:*')                        + {name: 'espresso-bench-jvm-ce-awfy_interpreter-jdk8-linux-amd64'},
    common.jdk8_weekly_bench_linux              + common.espresso_interpreter_benchmark('native-ce', 'awfy:*')                     + {name: 'espresso-bench-native-ce-awfy_interpreter-jdk8-linux-amd64'},

    // Scala DaCapo warmup benchmarks
    #common.jdk8_weekly_bench_linux              + common.scala_dacapo_warmup_benchmark('jvm-ce')      + {name: 'espresso-bench-jvm-ce-scala_dacapo_warmup-jdk8-linux-amd64'},
    #common.jdk8_weekly_bench_linux              + common.scala_dacapo_warmup_benchmark('native-ce')   + {name: 'espresso-bench-native-ce-scala_dacapo_warmup-jdk8-linux-amd64'},

    // Scala DaCapo warmup benchmarks --engine.MultiTier (post-merge)
    #common.jdk8_weekly_bench_linux              + common.scala_dacapo_warmup_benchmark('jvm-ce', 'multi-tier')      + {name: 'espresso-bench-jvm-ce-scala_dacapo_warmup_benchmark_multi_tier-jdk8-linux-amd64'},
    #common.jdk8_weekly_bench_linux              + common.scala_dacapo_warmup_benchmark('native-ce', 'multi-tier')   + {name: 'espresso-bench-native-ce-scala_dacapo_warmup_benchmark_multi_tier-jdk8-linux-amd64'},

    // On-demand benchmarks
    // Scala DaCapo warmup benchmarks
    common.jdk8_on_demand_bench_linux           + common.graal_benchmark('jvm-ce', common.scala_dacapo_jvm_fast(warmup=true))  + {name: 'bench-graal-ce-scala_dacapo_warmup-jdk8-linux-amd64'},

    // Memory footprint
    common.jdk8_on_demand_linux          + common.espresso_minheap_benchmark('jvm-ce', 'awfy:*', 'infinite-overhead')       + {name: 'espresso-jvm-ce-awfy-minheap-infinite-ovh-jdk8-linux-amd64'},
    common.jdk8_on_demand_bench_linux    + common.espresso_minheap_benchmark('jvm-ce', 'awfy:*', '1.5-overhead')            + {name: 'espresso-bench-jvm-ce-awfy-minheap-1.5-ovh-jdk8-linux-amd64'},
  ]
}
