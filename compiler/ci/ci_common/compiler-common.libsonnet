{
  local common = import "../../../ci/ci_common/common.jsonnet",
  local bench_common = import "../../../ci/ci_common/bench-common.libsonnet",
  local config = import "../../../ci/repo-configuration.libsonnet",
  local ci_resources = import "../../../ci/ci_common/ci-resources.libsonnet",

  enable_profiling:: {
    should_upload_results:: false,
    environment+: {
      "MX_PROFILER" : "JFR,async"
    },
    logs+: [
      "*.jfr", "**/*.jfr",
      "*.svg", "**/*.svg",
    ]
  },

  footprint_tracking:: {
    should_upload_results:: false,
    python_version: 3,
    packages+: {
      "pip:psrecord": "==1.2",
      "pip:matplotlib": "==3.3.4",
      "pip:psutil": "==5.9.0"
    },
    environment+: {
      "MX_TRACKER" : "psrecord",
      "MX_PROFILER" : ""
    },
    logs+: [
      "ps_*.png", "**/ps_*.png",
      "ps_*.txt", "**/ps_*.txt",
    ]
  },

  latest_jdk:: common["labsjdk-ee-21"],
  bench_jdks:: [
    self.latest_jdk
  ],

  compiler_benchmarks_notifications:: {
    notify_groups:: ["compiler_bench"],
  },

  compiler_benchmark:: bench_common.bench_base + self.compiler_benchmarks_notifications + {
    # The extra steps and mx arguments to be applied to build libgraal with PGO
    local is_libgraal = std.objectHasAll(self, "platform") && std.findSubstr("libgraal", self.platform) != [],
    local with_profiling = !std.objectHasAll(self, "disable_profiling") || !self.disable_profiling,
    local libgraal_profiling_only(value) = if is_libgraal && with_profiling then value else [],
    local collect_libgraal_profile = libgraal_profiling_only(config.compiler.collect_libgraal_profile()),
    local use_libgraal_profile = libgraal_profiling_only(config.compiler.use_libgraal_profile),

    job_prefix:: "bench-compiler",
    tags+: ["bench-compiler"],
    python_version : "3",
    environment+: {
      BENCH_RESULTS_FILE_PATH : "bench-results.json"
    },
    plain_benchmark_cmd::
      ["mx",
      "--kill-with-sigquit",
      "benchmark",
      "--fork-count-file=${FORK_COUNT_FILE}",
      "--extras=${BENCH_SERVER_EXTRAS}",
      "--results-file",
      "${BENCH_RESULTS_FILE_PATH}",
      "--machine-name=${MACHINE_NAME}"] +
      (if std.objectHasAll(self.environment, 'MX_TRACKER') then ["--tracker=" + self.environment['MX_TRACKER']] else ["--tracker=rss"]),
    restrict_threads:: null,  # can be overridden to restrict the benchmark to the given number of threads. If null, will use one full NUMA node
    benchmark_cmd:: if self.should_use_hwloc then bench_common.hwloc_cmd(self.plain_benchmark_cmd, self.restrict_threads, self.default_numa_node, self.hyperthreading, self.threads_per_node) else self.plain_benchmark_cmd,
    min_heap_size:: if std.objectHasAll(self.environment, 'XMS') then ["-Xms${XMS}"] else [],
    max_heap_size:: if std.objectHasAll(self.environment, 'XMX') then ["-Xmx${XMX}"] else [],
    _WarnMissingIntrinsic:: true, # won't be needed after GR-34642
    extra_vm_args::
      ["--profiler=${MX_PROFILER}",
      "--jvm=${JVM}",
      "--jvm-config=${JVM_CONFIG}",
      "-XX:+PrintConcurrentLocks",
      "-Dgraal.CompilationFailureAction=Diagnose",
      "-XX:+CITime"] +
      (if self._WarnMissingIntrinsic then ["-Dgraal.WarnMissingIntrinsic=true"] else []) +
      self.min_heap_size +
      self.max_heap_size,
    should_mx_build:: true,
    setup+: [
      ["cd", "./" + config.compiler.compiler_suite],
    ]
    + if self.should_mx_build then collect_libgraal_profile + [
      ["mx", "hsdis", "||", "true"],
      ["mx"] + use_libgraal_profile + ["build"]
    ] else [],
    should_upload_results:: true,
    _bench_upload(filename="${BENCH_RESULTS_FILE_PATH}"):: ["bench-uploader.py", filename],
    teardown+: if self.should_upload_results then [
      self._bench_upload()
    ] else []
  },

  // JVM configurations
  // ******************
  c1:: {
    platform:: "c1",
    environment+: {
      "JVM": "client",
      "JVM_CONFIG": "default"
    }
  },

  c2:: {
    platform:: "c2",
    environment+: {
      "JVM": "server",
      "JVM_CONFIG": "default"
    }
  },

  jargraal:: {
    platform:: "jargraal",
    environment+: {
      "JVM": "server",
      "JVM_CONFIG": config.compiler.default_jvm_config
    }
  },

  libgraal:: {
    platform:: "libgraal",
    environment+: {
      "JVM": "server",
      "JVM_CONFIG": config.compiler.libgraal_jvm_config(true),
      "MX_PRIMARY_SUITE_PATH": "../" + config.compiler.vm_suite,
      "MX_ENV_PATH": config.compiler.libgraal_env_file
    }
  },

  economy_mode:: {
    platform+:: "-economy",
    environment+: {
      "JVM_CONFIG"+: "-economy",
    }
  },

  no_tiered_comp:: {
    platform+:: "-no-tiered-comp",
    environment+: {
      "JVM_CONFIG"+: "-no-tiered-comp",
    }
  },

  no_profile_info:: {
    platform+:: "-no-profile-info",
    environment+: {
      "JVM_CONFIG"+: "-no-profile-info",
    }
  },

  avx2_mode:: {
    platform+:: "-avx2",
    environment+: {
      "JVM_CONFIG"+: "-avx2",
    }
  },

  avx3_mode:: {
    platform+:: "-avx3",
    environment+: {
      "JVM_CONFIG"+: "-avx3",
    }
  },

  zgc_mode:: {
    platform+:: "-zgc",
    environment+: {
      "JVM_CONFIG"+: "-zgc",
    }
  },

  gen_zgc_mode:: {
    platform+:: "-gen-zgc",
    environment+: {
      "JVM_CONFIG"+: "-gen-zgc",
    }
  },

  serialgc_mode:: {
    platform+:: "-serialgc",
    environment+: {
      "JVM_CONFIG"+: "-serialgc",
    }
  },

  pargc_mode:: {
    platform+:: "-pargc",
    environment+: {
      "JVM_CONFIG"+: "-pargc",
    }
  }
}
