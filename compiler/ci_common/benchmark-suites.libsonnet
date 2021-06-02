{
  local c = (import '../../common.jsonnet'),
  local cc = (import 'compiler-common.libsonnet'),

  local default_jvm_opts = ["-Xmx${XMX}", "-Xms${XMS}", "-XX:+PrintConcurrentLocks", "-Dgraal.CompilationFailureAction=Diagnose"],
  local mx_benchmark_opts = ["--machine-name=${MACHINE_NAME}", "--tracker=rss"],
  bench_arguments:: mx_benchmark_opts + ["--", "--profiler=${MX_PROFILER}", "--jvm=${JVM}", "--jvm-config=${JVM_CONFIG}"] + default_jvm_opts,

  mx_benchmark:: ["mx", "--kill-with-sigquit", "benchmark", "--fork-count-file=${FORK_COUNT_FILE}", "--extras=${BENCH_SERVER_EXTRAS}", "--results-file", "${BENCH_RESULTS_FILE_PATH}"],

  local uniq_key(o) = o['suite'],
  // convenient sets of benchmark suites for easy reuse
  groups:: {
    open_suites:: std.set([$.awfy, $.dacapo, $.scala_dacapo, $.renaissance], keyF=uniq_key),
    spec_suites:: std.set([$.specjvm2008, $.specjbb2005, $.specjbb2015], keyF=uniq_key),
    legacy_suites:: std.set([$.renaissance_legacy], keyF=uniq_key),
    jmh_micros_suites:: std.set([$.micros_graal_dist, $.micros_misc_graal_dist , $.micros_shootout_graal_dist], keyF=uniq_key),
    graal_internals_suites:: std.set([$.micros_graal_whitebox], keyF=uniq_key),
    special_suites:: std.set([$.renaissance_0_10, $.specjbb2015_full_machine], keyF=uniq_key),
    microservice_suites:: std.set([$.microservice_benchmarks], keyF=uniq_key),

    main_suites:: std.set(self.open_suites + self.spec_suites + self.legacy_suites, keyF=uniq_key),
    all_suites:: std.set(self.main_suites + self.jmh_micros_suites + self.special_suites, keyF=uniq_key),

    weekly_forks_suites:: self.main_suites,
    profiled_suites::     std.setDiff(self.main_suites, [$.specjbb2015], keyF=uniq_key),
  },

  // suite definitions
  // *****************
  awfy: cc.compiler_benchmark + c.heap.small + {
    suite:: "awfy",
    run+: [
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["awfy:*"] + $.bench_arguments, node=self.numa_nodes[0])
    ],
    timelimit: "30:00",
    forks_batches:: null, // disables it for now (GR-30956)
    forks_timelimit:: "3:00:00"
  },

  dacapo: cc.compiler_benchmark + c.heap.default + {
    suite:: "dacapo",
    run+: [
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["dacapo:*"] + $.bench_arguments, node=self.numa_nodes[0])
    ],
    timelimit: "45:00",
    forks_batches:: 1,
    forks_timelimit:: "02:45:00"
  },

  dacapo_timing: cc.compiler_benchmark + c.heap.default + {
    suite:: "dacapo-timing",
    run+: [
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["dacapo-timing:*"] + $.bench_arguments, node=self.numa_nodes[0])
    ],
    timelimit: "45:00"
  },

  scala_dacapo: cc.compiler_benchmark + c.heap.default + {
    suite:: "scala-dacapo",
    run+: [
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["scala-dacapo:*"] + $.bench_arguments, node=self.numa_nodes[0])
    ],
    timelimit: "45:00",
    forks_batches:: 1,
    forks_timelimit:: "03:30:00"
  },

  scala_dacapo_timing: cc.compiler_benchmark + c.heap.default + {
    suite:: "scala-dacapo-timing",
    run+: [
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["scala-dacapo-timing:*"] + $.bench_arguments, node=self.numa_nodes[0])
    ],
    timelimit: "45:00"
  },

  renaissance: cc.compiler_benchmark + c.heap.default + {
    suite:: "renaissance",
    environment+: {
      "SPARK_LOCAL_IP": "127.0.0.1"
    },
    run+: [
      c.hwlocIfNuma(self.is_numa,
      if self.arch == "aarch64" then
        $.mx_benchmark + ["renaissance:~db-shootout", "--bench-suite-version=$RENAISSANCE_VERSION"] + $.bench_arguments
      else
        $.mx_benchmark + ["renaissance:*", "--bench-suite-version=$RENAISSANCE_VERSION"] + $.bench_arguments,
      node=self.numa_nodes[0])
    ],
    timelimit: "3:00:00",
    forks_batches:: 4,
    forks_timelimit:: "06:30:00"
  },

  renaissance_0_10: self.renaissance + {
    suite:: "renaissance-0-10",
    environment+: {
      "RENAISSANCE_VERSION": "0.10.0"
    }
  },

  renaissance_legacy: cc.compiler_benchmark + c.heap.default + {
    suite:: "renaissance-legacy",
    downloads+: {
      "RENAISSANCE_LEGACY": { name: "renaissance", version: "0.1" }
    },
    environment+: {
      "SPARK_LOCAL_IP": "127.0.0.1"
    },
    run+: [
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["renaissance-legacy:*"] + $.bench_arguments, node=self.numa_nodes[0])
    ],
    timelimit: "2:45:00",
    forks_batches:: 4,
    forks_timelimit:: "06:30:00"
  },

  specjbb2005: cc.compiler_benchmark + c.heap.large_with_large_young_gen + {
    suite:: "specjbb2005",
    downloads+: {
      "SPECJBB2005": { name: "specjbb2005", version: "1.07" }
    },
    run+: [
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["specjbb2005"] + $.bench_arguments + ["input.ending_number_warehouses=77"], node=self.numa_nodes[0])
    ],
    timelimit: "4:00:00",
    forks_batches:: 1,
    forks_timelimit:: "20:00:00"
  },

  specjbb2015: cc.compiler_benchmark + c.heap.large_with_large_young_gen + {
    suite:: "specjbb2015",
    downloads+: {
      "SPECJBB2015": { name: "specjbb2015", version: "1.03" }
    },
    run+: [
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["specjbb2015"] + $.bench_arguments, node=self.numa_nodes[0])
    ],
    timelimit: "3:00:00",
    forks_batches:: 1,
    forks_timelimit:: "20:00:00"
  },

  specjbb2015_full_machine: cc.compiler_benchmark + c.heap.large_with_large_young_gen + {
    suite:: "specjbb2015-full-machine",
    downloads+: {
      "SPECJBB2015": { name: "specjbb2015", version: "1.03" }
    },
    run+: [
      $.mx_benchmark + ["specjbb2015"] + $.bench_arguments
    ],
    timelimit: "3:00:00"
  },

  specjvm2008: cc.compiler_benchmark + c.heap.large + {
    suite:: "specjvm2008",
    downloads+: {
      "SPECJVM2008": { name: "specjvm2008", version: "1.01" }
    },
    run+: [
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["specjvm2008:*"] + $.bench_arguments + ["--", "-ikv", "-it", "240s", "-wt", "120s"], node=self.numa_nodes[0])
    ],
    teardown+: [
      ["rm", "-r", "${SPECJVM2008}/results"]
    ],
    timelimit: "3:00:00",
    forks_batches:: 5,
    forks_timelimit:: "06:00:00"
  },

  // Microservice benchmarks
  microservice_benchmarks: cc.compiler_benchmark + c.heap.default + {
    suite:: "microservice-benchmarks",
    packages+: {
      "python3": "==3.6.5",
      "pip:psutil": "==5.8.0"
    },
    run+: [
      // JMeter
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["shopcart-jmeter:large"] + $.bench_arguments, node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"],

      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["petclinic-jmeter:tiny"] + $.bench_arguments, node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"],

      // wrk
      // shopcart
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["shopcart-wrk:mixed-tiny"] +    $.bench_arguments + ["-Xms32m",   "-Xmx112m",  "-XX:ActiveProcessorCount=1",  "-XX:MaxDirectMemorySize=256m"], node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"],

      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["shopcart-wrk:mixed-small"] +   $.bench_arguments + ["-Xms64m",   "-Xmx224m",  "-XX:ActiveProcessorCount=2",  "-XX:MaxDirectMemorySize=512m"], node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"],

      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["shopcart-wrk:mixed-medium"] +  $.bench_arguments + ["-Xms128m",  "-Xmx512m",  "-XX:ActiveProcessorCount=4",  "-XX:MaxDirectMemorySize=1024m"], node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"],

      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["shopcart-wrk:mixed-large"] +   $.bench_arguments + ["-Xms512m",  "-Xmx3072m", "-XX:ActiveProcessorCount=16", "-XX:MaxDirectMemorySize=4096m"], node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"],

      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["shopcart-wrk:mixed-huge"] +    $.bench_arguments + ["-Xms1024m", "-Xmx8192m", "-XX:ActiveProcessorCount=32", "-XX:MaxDirectMemorySize=8192m"], node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"],

      // tika-odt
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["tika-wrk:odt-tiny"] +          $.bench_arguments + ["-Xms32m",   "-Xmx150m",  "-XX:ActiveProcessorCount=1"], node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"],

      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["tika-wrk:odt-small"] +         $.bench_arguments + ["-Xms64m",   "-Xmx250m",  "-XX:ActiveProcessorCount=2"], node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"],

      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["tika-wrk:odt-medium"] +        $.bench_arguments + ["-Xms128m",  "-Xmx600m",  "-XX:ActiveProcessorCount=4"], node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"],

      // tika-pdf
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["tika-wrk:pdf-tiny"] +          $.bench_arguments + ["-Xms20m",   "-Xmx80m",   "-XX:ActiveProcessorCount=1"], node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"],

      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["tika-wrk:pdf-small"] +         $.bench_arguments + ["-Xms40m",   "-Xmx200m",  "-XX:ActiveProcessorCount=2"], node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"],

      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["tika-wrk:pdf-medium"] +        $.bench_arguments + ["-Xms80m",   "-Xmx500m",  "-XX:ActiveProcessorCount=4"], node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"],

      // petclinic
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["petclinic-wrk:mixed-tiny"] +   $.bench_arguments + ["-Xms32m",  "-Xmx100m",  "-XX:ActiveProcessorCount=1"], node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"],

      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["petclinic-wrk:mixed-small"] +  $.bench_arguments + ["-Xms40m",  "-Xmx128m",  "-XX:ActiveProcessorCount=2"], node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"],

      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["petclinic-wrk:mixed-medium"] + $.bench_arguments + ["-Xms80m",  "-Xmx256m",  "-XX:ActiveProcessorCount=4"], node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"],

      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["petclinic-wrk:mixed-large"] +  $.bench_arguments + ["-Xms320m", "-Xmx1280m", "-XX:ActiveProcessorCount=16"], node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"],

      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["petclinic-wrk:mixed-huge"] +   $.bench_arguments + ["-Xms640m", "-Xmx3072m", "-XX:ActiveProcessorCount=32"], node=self.numa_nodes[0]),
      ["bench-uploader.py", "bench-results.json"]
    ],
    timelimit: "7:00:00"
  },

  // JMH microbenchmarks
  micros_graal_whitebox: cc.compiler_benchmark + c.heap.default + {
    suite:: "micros-graal-whitebox",
    run+: [
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["jmh-whitebox:*"] + $.bench_arguments, node=self.numa_nodes[0])
    ],
    timelimit: "3:00:00"
  },

  micros_graal_dist: cc.compiler_benchmark + c.heap.default + {
    suite:: "micros-graal-dist",
    run+: [
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["jmh-dist:GRAAL_COMPILER_MICRO_BENCHMARKS"] + $.bench_arguments, node=self.numa_nodes[0])
    ],
    timelimit: "3:00:00"
  },

  micros_misc_graal_dist: cc.compiler_benchmark + c.heap.default + {
    suite:: "micros-misc-graal-dist",
    run+: [
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["jmh-dist:GRAAL_BENCH_MISC"] + $.bench_arguments, node=self.numa_nodes[0])
    ],
    timelimit: "3:00:00"
  },

  micros_shootout_graal_dist: cc.compiler_benchmark + c.heap.default {
    suite:: "micros-shootout-graal-dist",
    run+: [
      c.hwlocIfNuma(self.is_numa, $.mx_benchmark + ["jmh-dist:GRAAL_BENCH_SHOOTOUT"] + $.bench_arguments, node=self.numa_nodes[0])
    ],
    timelimit: "3:00:00"
  }
}
