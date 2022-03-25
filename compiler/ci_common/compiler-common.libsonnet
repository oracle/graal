{
  local common = import "../../common.jsonnet",
  local bench_common = import "../../bench-common.libsonnet",
  local config = import "../../repo-configuration.libsonnet",
  local ci_resources = import "../../ci-resources.libsonnet",

  enable_profiling:: {
    environment+: {
      "MX_PROFILER" : "JFR,async"
    },
    logs+: [
      "*.jfr",
      "**/*.jfr",
      "*.svg",
      "**/*.svg",
    ]
  },

  bench_jdks:: [common["labsjdk-ee-11"], common["labsjdk-ee-17"]],

  // Benchmarking building blocks
  // ****************************
  compiler_bench_base:: bench_common.bench_base + {
    # The extra steps and mx arguments to be applied to build libgraal with PGO
    local is_libgraal = std.objectHasAll(self, "platform") && std.findSubstr("libgraal", self.platform) != [],
    local libgraal_only(value) = if is_libgraal then value else [],
    local collect_libgraal_profile = libgraal_only(config.compiler.collect_libgraal_profile()),
    local use_libgraal_profile = libgraal_only(config.compiler.use_libgraal_profile),

    job_prefix:: "bench-compiler",
    environment+: {
      MX_PYTHON_VERSION : "3",
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
      "--machine-name=${MACHINE_NAME}",
      "--tracker=rss"],
    benchmark_cmd:: bench_common.hwlocIfNuma(self.should_use_hwloc, self.plain_benchmark_cmd, node=self.default_numa_node),
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
    teardown+: []
  },

  compiler_benchmarks_notifications:: {
    notify_groups:: ["compiler_bench"],
  },

  compiler_benchmark:: self.compiler_bench_base + self.compiler_benchmarks_notifications + {
    _bench_upload(filename="${BENCH_RESULTS_FILE_PATH}"):: ["bench-uploader.py", filename],
    teardown+: [
      self._bench_upload()
    ]
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
      "JVM_CONFIG": config.compiler.default_jvm_config + "-libgraal",
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
  }
}
