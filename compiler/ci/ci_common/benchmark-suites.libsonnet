{
  local c = (import '../../../ci/ci_common/common.jsonnet'),
  local bc = (import '../../../ci/ci_common/bench-common.libsonnet'),
  local cc = (import 'compiler-common.libsonnet'),

  local _suite_key(a) = a['suite'],
  local unique_suites(arr) = std.set(arr, keyF=_suite_key),

  // convenient sets of benchmark suites for easy reuse
  groups:: {
    open_suites:: unique_suites([$.awfy, $.dacapo, $.scala_dacapo, $.renaissance]),
    spec_suites:: unique_suites([$.specjvm2008, $.specjbb2015]),
    jmh_micros_suites:: unique_suites([$.micros_graal_dist]),
    graal_internals_suites:: unique_suites([$.micros_graal_whitebox]),
    special_suites:: unique_suites([$.dacapo_size_variants, $.scala_dacapo_size_variants]),
    microservice_suites:: unique_suites([$.microservice_benchmarks]),

    main_suites:: unique_suites([$.specjvm2008] + self.open_suites),
    all_suites:: unique_suites(self.main_suites + self.spec_suites + self.jmh_micros_suites + self.special_suites + self.microservice_suites),

    weekly_forks_suites:: self.main_suites + self.microservice_suites,
    all_but_main_suites:: std.setDiff(self.all_suites, self.main_suites, keyF=_suite_key),
  },

  // suite definitions
  // *****************
  awfy: cc.compiler_benchmark + c.heap.small + bc.bench_max_threads + {
    suite:: "awfy",
    run+: [
      self.benchmark_cmd + ["awfy:*", "--"] + self.extra_vm_args
    ],
    timelimit: "30:00",
    forks_batches:: null,
    forks_timelimit:: null,
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  dacapo: cc.compiler_benchmark + c.heap.default + bc.bench_max_threads + {
    suite:: "dacapo",
    run+: [
      self.benchmark_cmd + ["dacapo:*", "--"] + self.extra_vm_args
    ],
    timelimit: "50:00",
    forks_batches:: 2,
    bench_forks_per_batch:: 3,
    forks_timelimit:: "3:00:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  dacapo_size_variants: cc.compiler_benchmark + c.heap.default + bc.bench_max_threads + {
    suite:: "dacapo-size-variants",
    run+: [
      self.benchmark_cmd + ["dacapo-small:*", "--"] + self.extra_vm_args,
      self._bench_upload(),
      self.benchmark_cmd + ["dacapo-large:*", "--"] + self.extra_vm_args,
      self._bench_upload(),
      self.benchmark_cmd + ["dacapo-huge:*", "--"] + self.extra_vm_args
    ],
    timelimit: "04:30:00",
    forks_batches:: null,
    forks_timelimit:: null,
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  scala_dacapo: cc.compiler_benchmark + c.heap.default + bc.bench_max_threads + {
    suite:: "scala-dacapo",
    run+: [
      self.benchmark_cmd + ["scala-dacapo:*", "--"] + self.extra_vm_args
    ],
    timelimit: "01:00:00",
    forks_batches:: 2,
    bench_forks_per_batch:: 3,
    forks_timelimit:: "02:30:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  scala_dacapo_size_variants: cc.compiler_benchmark + c.heap.default + bc.bench_max_threads + {
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
    timelimit: "03:00:00",
    forks_batches:: null, # weekly forks disabled
    forks_timelimit:: null,
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  renaissance_template(suite_version=null, suite_name="renaissance", max_jdk_version=null):: cc.compiler_benchmark + c.heap.default + bc.bench_max_threads + {
    suite:: suite_name,
    local suite_version_args = if suite_version != null then ["--bench-suite-version=" + suite_version] else [],
    run+: [
      self.benchmark_cmd + ["renaissance:*"] + suite_version_args + ["--"] + self.extra_vm_args
    ],
    timelimit: "2:00:00",
    forks_batches:: 4,
    bench_forks_per_batch:: 2,
    forks_timelimit:: "4:00:00",
    min_jdk_version:: 8,
    max_jdk_version:: max_jdk_version
  },

  renaissance: self.renaissance_template(),

  specjbb2015: cc.compiler_benchmark + c.heap.large_with_large_young_gen + bc.bench_no_thread_cap + {
    suite:: "specjbb2015",
    downloads+: {
      "SPECJBB2015": { name: "specjbb2015", version: "1.04" }
    },
    run+: [
      self.benchmark_cmd + ["specjbb2015", "--"] + self.extra_vm_args
    ],
    timelimit: "3:00:00",
    forks_batches:: null,
    forks_timelimit:: null,
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  specjvm2008: cc.compiler_benchmark + c.heap.default + bc.bench_max_threads + {
    suite:: "specjvm2008",
    downloads+: {
      "SPECJVM2008": { name: "specjvm2008", version: "1.01" }
    },
    run+: [
      self.benchmark_cmd + ["specjvm2008:*", "--"] + self.extra_vm_args + ["--", "-ikv", "-it", "30s", "-wt", "30s"]
    ],
    timelimit: "1:15:00",
    forks_batches:: 3,
    bench_forks_per_batch:: 1,
    forks_timelimit:: "1:15:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  // Microservice benchmarks
  microservice_benchmarks: cc.compiler_benchmark + bc.bench_no_thread_cap + {  # no thread cap here since hwloc is handled at the mx level for microservices
    suite:: "microservices",
    packages+: {
      "pip:psutil": "==5.8.0"
    },
    local bench_upload = ["bench-uploader.py", "bench-results.json"],
    local hwlocBind_1C_1T = ["--hwloc-bind=--cpubind node:0.core:0.pu:0 --membind node:0"],
    local hwlocBind_2C_2T = ["--hwloc-bind=--cpubind node:0.core:0-1.pu:0 --membind node:0"],
    local hwlocBind_4C_4T = ["--hwloc-bind=--cpubind node:0.core:0-3.pu:0 --membind node:0"],
    local hwlocBind_16C_16T = ["--hwloc-bind=--cpubind node:0.core:0-15.pu:0 --membind node:0"],
    local hwlocBind_16C_32T = ["--hwloc-bind=--cpubind node:0.core:0-15.pu:0-1 --membind node:0"],
    run+: [
      # shopcart-wrk
      self.benchmark_cmd + ["shopcart-wrk:mixed-large"]                  + hwlocBind_16C_16T + ["--"] + self.extra_vm_args + ["-Xms512m",  "-Xmx3072m", "-XX:ActiveProcessorCount=16", "-XX:MaxDirectMemorySize=4096m"],
      bench_upload,

      # tika-wrk odt
      self.benchmark_cmd + ["tika-wrk:odt-medium"]                       + hwlocBind_4C_4T   + ["--"] + self.extra_vm_args + ["-Xms128m",  "-Xmx600m",  "-XX:ActiveProcessorCount=4"],
      bench_upload,

      # tika-wrk pdf
      self.benchmark_cmd + ["tika-wrk:pdf-medium"]                       + hwlocBind_4C_4T   + ["--"] + self.extra_vm_args + ["-Xms80m",   "-Xmx500m",  "-XX:ActiveProcessorCount=4"],
      bench_upload,

      # petclinic-wrk
      self.benchmark_cmd + ["petclinic-wrk:mixed-large"]                 + hwlocBind_16C_16T + ["--"] + self.extra_vm_args + ["-Xms128m",  "-Xmx512m", "-XX:ActiveProcessorCount=16"],
      bench_upload,

      # helloworld-wrk
      self.benchmark_cmd + ["micronaut-helloworld-wrk:helloworld"]       + hwlocBind_1C_1T   + ["--"] + self.extra_vm_args + ["-Xms8m",    "-Xmx64m",   "-XX:ActiveProcessorCount=1", "-XX:MaxDirectMemorySize=256m"],
      bench_upload,
      self.benchmark_cmd + ["quarkus-helloworld-wrk:helloworld"]         + hwlocBind_1C_1T   + ["--"] + self.extra_vm_args + ["-Xms8m",    "-Xmx64m",   "-XX:ActiveProcessorCount=1", "-XX:MaxDirectMemorySize=256m"],
      bench_upload,
      self.benchmark_cmd + ["spring-helloworld-wrk:helloworld"]          + hwlocBind_1C_1T   + ["--"] + self.extra_vm_args + ["-Xms8m",    "-Xmx64m",   "-XX:ActiveProcessorCount=1", "-XX:MaxDirectMemorySize=256m"],
      bench_upload
    ],
    timelimit: "2:00:00",
    forks_batches:: 3,
    bench_forks_per_batch:: 1,
    forks_timelimit:: "2:00:00",
    min_jdk_version:: 11,
    max_jdk_version:: null
  },

  // JMH microbenchmarks
  micros_graal_whitebox: cc.compiler_benchmark + c.heap.default + bc.bench_max_threads + {
    suite:: "micros-graal-whitebox",
    run+: [
      self.benchmark_cmd + ["jmh-whitebox:*", "--"] + self.extra_vm_args + ["--", "jdk.graal.compiler"]
    ],
    timelimit: "6:00:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  },

  micros_graal_dist: cc.compiler_benchmark + c.heap.default + bc.bench_max_threads + {
    suite:: "micros-graal-dist",
    run+: [
      self.benchmark_cmd + ["jmh-dist:GRAAL_COMPILER_MICRO_BENCHMARKS", "--"] + self.extra_vm_args
    ],
    timelimit: "5:00:00",
    min_jdk_version:: 8,
    max_jdk_version:: null
  }
}
