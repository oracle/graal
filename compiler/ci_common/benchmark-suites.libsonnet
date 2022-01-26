{
  local c = (import '../../common.jsonnet'),
  local bc = (import '../../bench-common.libsonnet'),
  local cc = (import 'compiler-common.libsonnet'),

  local uniq_key(o) = o['suite'],
  // convenient sets of benchmark suites for easy reuse
  groups:: {
    open_suites:: std.set([$.awfy, $.dacapo, $.scala_dacapo, $.renaissance, $.renaissance_0_13], keyF=uniq_key),
    spec_suites:: std.set([$.specjvm2008, $.specjbb2005, $.specjbb2015], keyF=uniq_key),
    legacy_and_secondary_suites:: std.set([$.renaissance_legacy], keyF=uniq_key),
    jmh_micros_suites:: std.set([$.micros_graal_dist, $.micros_misc_graal_dist , $.micros_shootout_graal_dist], keyF=uniq_key),
    graal_internals_suites:: std.set([$.micros_graal_whitebox], keyF=uniq_key),
    special_suites:: std.set([$.renaissance_0_10, $.dacapo_size_variants, $.scala_dacapo_size_variants, $.specjbb2015_full_machine], keyF=uniq_key),
    microservice_suites:: std.set([$.microservice_benchmarks], keyF=uniq_key),

    main_suites:: std.set([$.specjvm2008] + self.open_suites + self.legacy_and_secondary_suites, keyF=uniq_key),
    all_suites:: std.set(self.main_suites + self.spec_suites + self.jmh_micros_suites + self.special_suites + self.microservice_suites, keyF=uniq_key),

    weekly_forks_suites:: std.set([$.renaissance_0_13] + self.main_suites, keyF=uniq_key),
    profiled_suites::     std.setDiff(self.main_suites, [$.specjbb2015], keyF=uniq_key),
    all_but_main_suites::     std.setDiff(self.all_suites, self.main_suites, keyF=uniq_key),
  },

  // suite definitions
  // *****************
  awfy: cc.compiler_benchmark + c.heap.small + {
    suite:: "awfy",
    run+: [
      self.benchmark_cmd + ["awfy:*", "--"] + self.extra_vm_args
    ],
    timelimit: "30:00",
    forks_batches:: 1,
    forks_timelimit:: "2:00:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  dacapo: cc.compiler_benchmark + c.heap.default + {
    suite:: "dacapo",
    run+: [
      self.benchmark_cmd + ["dacapo:*", "--"] + self.extra_vm_args
    ],
    timelimit: "45:00",
    forks_batches:: 1,
    forks_timelimit:: "04:00:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  dacapo_size_variants: cc.compiler_benchmark + c.heap.default + {
    suite:: "dacapo-size-variants",
    run+: [
      self.benchmark_cmd + ["dacapo-small:*", "--"] + self.extra_vm_args,
      self._bench_upload(),
      self.benchmark_cmd + ["dacapo-large:*", "--"] + self.extra_vm_args,
      self._bench_upload(),
      self.benchmark_cmd + ["dacapo-huge:*", "--"] + self.extra_vm_args
    ],
    timelimit: "07:00:00",
    forks_batches:: null, # weekly forks disabled
    forks_timelimit:: null,
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  dacapo_timing: cc.compiler_benchmark + c.heap.default + {
    suite:: "dacapo-timing",
    run+: [
      self.benchmark_cmd + ["dacapo-timing:*", "--"] + self.extra_vm_args
    ],
    timelimit: "45:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  scala_dacapo: cc.compiler_benchmark + c.heap.default + {
    suite:: "scala-dacapo",
    run+: [
      self.benchmark_cmd + ["scala-dacapo:*", "--"] + self.extra_vm_args
    ],
    timelimit: "01:30:00",
    forks_batches:: 1,
    forks_timelimit:: "03:30:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  scala_dacapo_size_variants: cc.compiler_benchmark + c.heap.default + {
    suite:: "scala-dacapo-size-variants",
    run+: [
      self.benchmark_cmd + ["scala-dacapo-tiny:*", "--"] + self.extra_vm_args,
      self._bench_upload(),
      self.benchmark_cmd + ["scala-dacapo-small:*", "--"] + self.extra_vm_args,
      self._bench_upload(),
      self.benchmark_cmd + ["scala-dacapo-large:*", "--"] + self.extra_vm_args,
      self._bench_upload(),
      self.benchmark_cmd + ["scala-dacapo-huge:*", "--"] + self.extra_vm_args,
      // Disabling the 'gargantuan' sizes since they require a lot of compute time for little added value
      //self._bench_upload(),
      //self.benchmark_cmd + ["scala-dacapo-gargantuan:*", "--"] + self.extra_vm_args
    ],
    timelimit: "08:00:00",
    forks_batches:: null, # weekly forks disabled
    forks_timelimit:: null,
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  scala_dacapo_timing: cc.compiler_benchmark + c.heap.default + {
    suite:: "scala-dacapo-timing",
    run+: [
      self.benchmark_cmd + ["scala-dacapo-timing:*", "--"] + self.extra_vm_args
    ],
    timelimit: "45:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  renaissance: cc.compiler_benchmark + c.heap.default + {
    suite:: "renaissance",
    environment+: {
      "SPARK_LOCAL_IP": "127.0.0.1"
    },
    run+: [
      self.benchmark_cmd + ["renaissance:*", "--bench-suite-version=$RENAISSANCE_VERSION", "--"] + self.extra_vm_args
    ],
    timelimit: "3:00:00",
    forks_batches:: 4,
    forks_timelimit:: "06:30:00",
    min_jdk_version:: 8,
    max_jdk_version:: 11
  },

  renaissance_0_10: self.renaissance + {
    suite:: "renaissance-0-10",
    environment+: {
      "RENAISSANCE_VERSION": "0.10.0"
    },
    min_jdk_version:: 8,
    max_jdk_version:: 11
  },

  renaissance_0_13: self.renaissance + {
    suite:: "renaissance-0-13",
    environment+: {
      "RENAISSANCE_VERSION": "0.13.0"
    },
    min_jdk_version:: 8,
    max_jdk_version:: null
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
      self.benchmark_cmd + ["renaissance-legacy:*", "--"] + self.extra_vm_args
    ],
    timelimit: "2:45:00",
    forks_batches:: 4,
    forks_timelimit:: "06:30:00",
    min_jdk_version:: 8,
    max_jdk_version:: 11
  },

  specjbb2005: cc.compiler_benchmark + c.heap.large_with_large_young_gen + {
    suite:: "specjbb2005",
    downloads+: {
      "SPECJBB2005": { name: "specjbb2005", version: "1.07" }
    },
    run+: [
      self.benchmark_cmd + ["specjbb2005", "--"] + self.extra_vm_args + ["--", "input.ending_number_warehouses=77"]
    ],
    timelimit: "4:00:00",
    forks_batches:: 1,
    forks_timelimit:: "20:00:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  specjbb2015: cc.compiler_benchmark + c.heap.large_with_large_young_gen + {
    suite:: "specjbb2015",
    downloads+: {
      "SPECJBB2015": { name: "specjbb2015", version: "1.03" }
    },
    run+: [
      self.benchmark_cmd + ["specjbb2015", "--"] + self.extra_vm_args
    ],
    timelimit: "3:00:00",
    forks_batches:: 1,
    forks_timelimit:: "20:00:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  specjbb2015_full_machine: cc.compiler_benchmark + c.heap.large_with_large_young_gen + {
    suite:: "specjbb2015-full-machine",
    downloads+: {
      "SPECJBB2015": { name: "specjbb2015", version: "1.03" }
    },
    run+: [
      self.plain_benchmark_cmd + ["specjbb2015", "--"] + self.extra_vm_args
    ],
    timelimit: "3:00:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  specjvm2008: cc.compiler_benchmark + c.heap.default + {
    suite:: "specjvm2008",
    downloads+: {
      "SPECJVM2008": { name: "specjvm2008", version: "1.01" }
    },
    run+: [
      self.benchmark_cmd + ["specjvm2008:*", "--"] + self.extra_vm_args + ["--", "-ikv", "-it", "240s", "-wt", "120s"]
    ],
    teardown+: [
      ["rm", "-r", "${SPECJVM2008}/results"]
    ],
    timelimit: "3:00:00",
    forks_batches:: 5,
    forks_timelimit:: "06:00:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  // Microservice benchmarks
  microservice_benchmarks: cc.compiler_benchmark + {
    suite:: "microservices",
    packages+: {
      "python3": "==3.6.5",
      "pip:psutil": "==5.8.0"
    },
    local bench_upload = ["bench-uploader.py", "bench-results.json"],
    local hwlocBind_1C_1T = ["--hwloc-bind=--cpubind node:0.core:0.pu:0 --membind node:0"],
    local hwlocBind_2C_2T = ["--hwloc-bind=--cpubind node:0.core:0-1.pu:0 --membind node:0"],
    local hwlocBind_4C_4T = ["--hwloc-bind=--cpubind node:0.core:0-3.pu:0 --membind node:0"],
    local hwlocBind_16C_16T = ["--hwloc-bind=--cpubind node:0.core:0-15.pu:0 --membind node:0"],
    local hwlocBind_16C_32T = ["--hwloc-bind=--cpubind node:0.core:0-15.pu:0-1 --membind node:0"],
    run+: [
      # JMeter
      self.benchmark_cmd + ["shopcart-jmeter:large"]                                         + ["--"] + self.extra_vm_args + ["-Xmx8g"],
      bench_upload,
      self.benchmark_cmd + ["petclinic-jmeter:tiny"]                                         + ["--"] + self.extra_vm_args + ["-Xmx8g"],
      bench_upload,
      # shopcart-wrk
      self.benchmark_cmd + ["shopcart-wrk:mixed-tiny"]                   + hwlocBind_1C_1T   + ["--"] + self.extra_vm_args + ["-Xms32m",   "-Xmx112m",  "-XX:ActiveProcessorCount=1",  "-XX:MaxDirectMemorySize=256m"],
      bench_upload,
      self.benchmark_cmd + ["shopcart-wrk:mixed-small"]                  + hwlocBind_2C_2T   + ["--"] + self.extra_vm_args + ["-Xms64m",   "-Xmx224m",  "-XX:ActiveProcessorCount=2",  "-XX:MaxDirectMemorySize=512m"],
      bench_upload,
      self.benchmark_cmd + ["shopcart-wrk:mixed-medium"]                 + hwlocBind_4C_4T   + ["--"] + self.extra_vm_args + ["-Xms128m",  "-Xmx512m",  "-XX:ActiveProcessorCount=4",  "-XX:MaxDirectMemorySize=1024m"],
      bench_upload,
      self.benchmark_cmd + ["shopcart-wrk:mixed-large"]                  + hwlocBind_16C_16T + ["--"] + self.extra_vm_args + ["-Xms512m",  "-Xmx3072m", "-XX:ActiveProcessorCount=16", "-XX:MaxDirectMemorySize=4096m"],
      bench_upload,
      self.benchmark_cmd + ["shopcart-wrk:mixed-huge"]                   + hwlocBind_16C_32T + ["--"] + self.extra_vm_args + ["-Xms1024m", "-Xmx8192m", "-XX:ActiveProcessorCount=32", "-XX:MaxDirectMemorySize=8192m"],
      bench_upload,
      # tika-wrk odt
      self.benchmark_cmd + ["tika-wrk:odt-tiny"]                         + hwlocBind_1C_1T   + ["--"] + self.extra_vm_args + ["-Xms32m",   "-Xmx150m",  "-XX:ActiveProcessorCount=1"],
      bench_upload,
      self.benchmark_cmd + ["tika-wrk:odt-small"]                        + hwlocBind_2C_2T   + ["--"] + self.extra_vm_args + ["-Xms64m",   "-Xmx250m",  "-XX:ActiveProcessorCount=2"],
      bench_upload,
      self.benchmark_cmd + ["tika-wrk:odt-medium"]                       + hwlocBind_4C_4T   + ["--"] + self.extra_vm_args + ["-Xms128m",  "-Xmx600m",  "-XX:ActiveProcessorCount=4"],
      bench_upload,
      # tika-wrk pdf
      self.benchmark_cmd + ["tika-wrk:pdf-tiny"]                         + hwlocBind_1C_1T   + ["--"] + self.extra_vm_args + ["-Xms20m",   "-Xmx80m",   "-XX:ActiveProcessorCount=1"],
      bench_upload,
      self.benchmark_cmd + ["tika-wrk:pdf-small"]                        + hwlocBind_2C_2T   + ["--"] + self.extra_vm_args + ["-Xms40m",   "-Xmx200m",  "-XX:ActiveProcessorCount=2"],
      bench_upload,
      self.benchmark_cmd + ["tika-wrk:pdf-medium"]                       + hwlocBind_4C_4T   + ["--"] + self.extra_vm_args + ["-Xms80m",   "-Xmx500m",  "-XX:ActiveProcessorCount=4"],
      bench_upload,
      # petclinic-wrk
      self.benchmark_cmd + ["petclinic-wrk:mixed-tiny"]                  + hwlocBind_1C_1T   + ["--"] + self.extra_vm_args + ["-Xms32m",   "-Xmx100m",  "-XX:ActiveProcessorCount=1"],
      bench_upload,
      self.benchmark_cmd + ["petclinic-wrk:mixed-small"]                 + hwlocBind_2C_2T   + ["--"] + self.extra_vm_args + ["-Xms40m",   "-Xmx144m",  "-XX:ActiveProcessorCount=2"],
      bench_upload,
      self.benchmark_cmd + ["petclinic-wrk:mixed-medium"]                + hwlocBind_4C_4T   + ["--"] + self.extra_vm_args + ["-Xms80m",   "-Xmx256m",  "-XX:ActiveProcessorCount=4"],
      bench_upload,
      self.benchmark_cmd + ["petclinic-wrk:mixed-large"]                 + hwlocBind_16C_16T + ["--"] + self.extra_vm_args + ["-Xms320m",  "-Xmx1280m", "-XX:ActiveProcessorCount=16"],
      bench_upload,
      self.benchmark_cmd + ["petclinic-wrk:mixed-huge"]                  + hwlocBind_16C_32T + ["--"] + self.extra_vm_args + ["-Xms640m",  "-Xmx3072m", "-XX:ActiveProcessorCount=32"],
      bench_upload,
      # helloworld-wrk
      self.benchmark_cmd + ["micronaut-helloworld-wrk:helloworld"]       + hwlocBind_1C_1T   + ["--"] + self.extra_vm_args + ["-Xms8m",    "-Xmx64m",   "-XX:ActiveProcessorCount=1", "-XX:MaxDirectMemorySize=256m"],
      bench_upload,
      self.benchmark_cmd + ["quarkus-helloworld-wrk:helloworld"]         + hwlocBind_1C_1T   + ["--"] + self.extra_vm_args + ["-Xms8m",    "-Xmx64m",   "-XX:ActiveProcessorCount=1", "-XX:MaxDirectMemorySize=256m"],
      bench_upload,
      self.benchmark_cmd + ["spring-helloworld-wrk:helloworld"]          + hwlocBind_1C_1T   + ["--"] + self.extra_vm_args + ["-Xms8m",    "-Xmx64m",   "-XX:ActiveProcessorCount=1", "-XX:MaxDirectMemorySize=256m"],
      bench_upload
    ],
    timelimit: "7:00:00",
    min_jdk_version:: 11, # GR-32793: disabled JDK8
    max_jdk_version:: null
  },

  // JMH microbenchmarks
  micros_graal_whitebox: cc.compiler_benchmark + c.heap.default + {
    suite:: "micros-graal-whitebox",
    run+: [
      self.benchmark_cmd + ["jmh-whitebox:*", "--"] + self.extra_vm_args
    ],
    timelimit: "3:00:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  micros_graal_dist: cc.compiler_benchmark + c.heap.default + {
    suite:: "micros-graal-dist",
    run+: [
      self.benchmark_cmd + ["jmh-dist:GRAAL_COMPILER_MICRO_BENCHMARKS", "--"] + self.extra_vm_args
    ],
    timelimit: "5:00:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  micros_misc_graal_dist: cc.compiler_benchmark + c.heap.default + {
    suite:: "micros-misc-graal-dist",
    run+: [
      self.benchmark_cmd + ["jmh-dist:GRAAL_BENCH_MISC", "--"] + self.extra_vm_args
    ],
    timelimit: "3:00:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  micros_shootout_graal_dist: cc.compiler_benchmark + c.heap.default {
    suite:: "micros-shootout-graal-dist",
    run+: [
      self.benchmark_cmd + ["jmh-dist:GRAAL_BENCH_SHOOTOUT", "--"] + self.extra_vm_args
    ],
    timelimit: "3:00:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  }
}
