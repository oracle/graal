{
  local common = import "../../common.jsonnet",
  local bench_common = import "../../bench-common.libsonnet",
  local config = import '../../repo-configuration.libsonnet',
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

  // Benchmarking building blocks
  // ****************************
  compiler_bench_base:: bench_common.bench_base + {
    job_prefix:: "bench-compiler",
    environment+: {
      MX_PYTHON_VERSION : "3",
      BENCH_RESULTS_FILE_PATH : "bench-results.json"
    },
    plain_benchmark_cmd:: ["mx", "--kill-with-sigquit", "benchmark", "--fork-count-file=${FORK_COUNT_FILE}", "--extras=${BENCH_SERVER_EXTRAS}", "--results-file", "${BENCH_RESULTS_FILE_PATH}", "--machine-name=${MACHINE_NAME}", "--tracker=rss"],
    benchmark_cmd:: bench_common.hwlocIfNuma(self.should_use_hwloc, self.plain_benchmark_cmd, node=self.default_numa_node),
    min_heap_size:: if std.objectHasAll(self.environment, 'XMS') then ["-Xms${XMS}"] else [],
    max_heap_size:: if std.objectHasAll(self.environment, 'XMX') then ["-Xmx${XMX}"] else [],
    extra_vm_args:: ["--profiler=${MX_PROFILER}", "--jvm=${JVM}", "--jvm-config=${JVM_CONFIG}", "-XX:+PrintConcurrentLocks", "-Dgraal.CompilationFailureAction=Diagnose"] + self.min_heap_size + self.max_heap_size,
    should_mx_build:: true,
    setup+: [
      ["cd", "./" + config.compiler.compiler_suite],
    ]
    + if self.should_mx_build then [
      ["mx", "hsdis", "||", "true"],
      ["mx", "build"]
    ] else [],
    teardown+: [
    ]
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


  many_forks_benchmarking:: common.build_base + {
    // building block used to generate fork builds
    local benchmarking_config_repo = self.ci_resources.infra.benchmarking_config_repo,
    environment+: {
      BENCHMARKING_CONFIG_REPO: "$BUILD_DIR/benchmarking-config",
      FORK_COUNTS_DIRECTORY: "$BENCHMARKING_CONFIG_REPO/fork-counts",
      FORK_COUNT_FILE: error "FORK_COUNT_FILE env var must be set to use the many forks execution!"
    },
    setup+: [
      ["set-export", "CURRENT_BRANCH", ["git", "rev-parse", "--abbrev-ref", "HEAD"]],
      ["echo", "[BENCH-FORKS-CONFIG] Using configuration files from branch ${CURRENT_BRANCH} if it exists remotely."],
      ["git", "clone", benchmarking_config_repo, "${BENCHMARKING_CONFIG_REPO}"],
      ["test", "${CURRENT_BRANCH}", "=", "master", "||", "git", "-C", "${BENCHMARKING_CONFIG_REPO}", "checkout", "--track", "origin/${CURRENT_BRANCH}", "||", "echo", "Using default fork counts since there is no branch named '${CURRENT_BRANCH}' in the benchmarking-config repo."]
    ]
  },

  generate_fork_builds(suite_obj, subdir='compiler')::
    /* based on a benchmark suite definition, generates the many forks version based on the hidden fields
     * 'forks_batches' that specifies the number of batches this job should be split into and the corresponding
     * 'forks_timelimit' that applies to those long-running jobs.
     */

    if std.objectHasAll(suite_obj, "forks_batches") && std.objectHasAll(suite_obj, "forks_timelimit") && suite_obj.forks_batches != null then
      [ $.many_forks_benchmarking + suite_obj + {
        local batch_str = (if suite_obj.forks_batches > 1 then "batch"+i else null),
        "job_prefix":: "bench-forks-compiler",
        "job_suffix":: batch_str,
        "timelimit": suite_obj.forks_timelimit,
        "environment" +: {
          FORK_COUNT_FILE: "${FORK_COUNTS_DIRECTORY}/" + subdir + "/" + suite_obj.suite + "_forks" + (if batch_str != null then "_"+batch_str else "") + ".json"
        }
      }
      for i in std.range(0, suite_obj.forks_batches - 1)]
    else
      [],

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
  }
}
