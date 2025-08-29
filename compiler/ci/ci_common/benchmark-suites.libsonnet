{
  local c = (import '../../../ci/ci_common/common.jsonnet'),
  local bc = (import '../../../ci/ci_common/bench-common.libsonnet'),
  local config = (import '../../../ci/repo-configuration.libsonnet'),
  local cc = (import 'compiler-common.libsonnet'),

  local _suite_key(a) = a['suite'],
  local unique_suites(arr) = std.set(arr, keyF=_suite_key),

  // convenient sets of benchmark suites for easy reuse
  groups:: {
    open_suites:: unique_suites([$.awfy, $.dacapo, $.scala_dacapo, $.renaissance, $.barista]),
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
      self.benchmark_cmd + [self.suite + ":*", "--"] + self.extra_vm_args
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
      self.benchmark_cmd + [self.suite + ":*", "--"] + self.extra_vm_args
    ],
    logs+: [
        "%s/*/scratch/%s" % [config.compiler.compiler_suite, file]
        for file in [
            "biojava.out",
            "pmd-report.txt",
        ] + [
            "lusearch.out%d" % n
            # only capture the files used for output validation; defined in lusearch.cnf
            for n in [0, 1, 2, 3, 4, 5, 6, 7, 265, 511, 767, 1023, 1279, 1535, 1791, 2047]
        ]
    ],
    timelimit: "1:30:00",
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
      self.benchmark_cmd + [self.suite + ":*", "--"] + self.extra_vm_args
    ],
    timelimit: "01:30:00",
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
      self.benchmark_cmd + [self.suite + ":*"] + suite_version_args + ["--"] + self.extra_vm_args
    ],
    timelimit: "2:30:00",
    forks_batches:: 4,
    bench_forks_per_batch:: 2,
    forks_timelimit:: "4:00:00",
    min_jdk_version:: 8,
    max_jdk_version:: max_jdk_version
  },

  renaissance: self.renaissance_template(),

  barista_template(suite_version=null, suite_name="barista", max_jdk_version=null, cmd_app_prefix=["hwloc-bind --cpubind node:0.core:0-3.pu:0 --membind node:0"], non_prefix_barista_args=[]):: cc.compiler_benchmark + {
    suite:: suite_name,
    local barista_version = "v0.4.7",
    local suite_version_args = if suite_version != null then ["--bench-suite-version=" + suite_version] else [],
    local prefix_barista_arg = if std.length(cmd_app_prefix) > 0 then [std.format("--cmd-app-prefix=%s", std.join(" ", cmd_app_prefix))] else [],
    local all_barista_args = prefix_barista_arg + non_prefix_barista_args,
    local barista_args_with_separator = if std.length(all_barista_args) > 0 then ["--"] + all_barista_args else [],
    downloads+: {
      "WRK": { "name": "wrk", "version": "a211dd5", platformspecific: true},
      "WRK2": { "name": "wrk2", "version": "2.1", platformspecific: true},
      "BARISTA_BENCHMARKS": { "name": "barista", "version": "0.4.7"}
    },
    packages+: {
      maven: "==3.8.6",
      "pip:toml": "==0.10.2"
    },
    setup: [
      ["set-export", "PATH", "$WRK:$PATH"],
      ["set-export", "PATH", "$WRK2:$PATH"],
      ["git", "clone", "--depth", "1", "--branch", barista_version, ["mx", "urlrewrite", "https://github.com/graalvm/barista-suite.git"], "$BARISTA_HOME"],
      ["cp", "-r", "$BARISTA_BENCHMARKS/*", "$BARISTA_HOME"] // copy the prebuilt jar/nib files
    ] + super.setup,
    run+: [
      self.benchmark_cmd + ["barista:*"] + suite_version_args + ["--"] + self.extra_vm_args + barista_args_with_separator
    ],
    notify_emails+: ["andrija.kolic@oracle.com"],
    timelimit: "2:00:00",
    should_use_hwloc: false, // hwloc-bind is passed to barista with '--cmd-app-prefix'
    environment+: {
      BARISTA_HOME: "$BUILD_DIR/barista-suite",
      XMX: "500m"
    },
    min_jdk_version:: 8,
    max_jdk_version:: max_jdk_version,
    forks_batches:: 3,
    bench_forks_per_batch:: 4,
    forks_timelimit:: "4:30:00"
  },

  barista: self.barista_template(),

  specjbb2015: cc.compiler_benchmark + c.heap.large_with_large_young_gen + bc.bench_no_thread_cap + {
    suite:: "specjbb2015",
    downloads+: {
      "SPECJBB2015": { name: "specjbb2015", version: "1.04" }
    },
    run+: [
      self.benchmark_cmd + ["specjbb2015", "--"] + self.extra_vm_args
    ],
    timelimit: "5:00:00",
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
      self.benchmark_cmd + [self.suite + ":*", "--"] + self.extra_vm_args + ["--", "-ikv", "-it", "30s", "-wt", "30s"]
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
  },

  // Benchmark mixins that run metric-collecting variants of the benchmark suite they're applied to.
  // For example, dacapo-timing is a variant of the dacapo benchmark which collects phase times and other compiler timers in its results.

  timing: {
    suite+: "-timing",
    forks_batches:: null,
    forks_timelimit:: null,
  },

  mem_use: {
    suite+: "-mem-use",
    forks_batches:: null,
    forks_timelimit:: null,
  }
}
