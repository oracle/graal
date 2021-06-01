{
  local common = import "../../common.jsonnet",
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

  local prefixed_jdk(jdk_version) =
    if jdk_version == null || std.length(std.toString(jdk_version)) == 0 then
      null
    else
      "jdk" + std.toString(jdk_version),

  // Benchmarking building blocks
  // ****************************
  bench_base:: common.build_base + {
    local ol8_image = self.ci_resources.infra.ol8_bench_image,
    job_prefix:: "bench-compiler",
    job_suffix:: null,
    generated_name:: std.join("-", std.filterMap(function(el) el != null, function(el) std.toString(el), [self.job_prefix, self.suite, self.platform, prefixed_jdk(self.jdk_version), self.os, self.arch, self.job_suffix])),
    name: self.generated_name,
    suite:: error "'suite' must be set to generate job name",
    timelimit: error "build 'timelimit' is not set for "+ self.name +"!",
    docker+: {
      "image": ol8_image,
      "mount_modules": true
    },
    environment+: {
      MX_PYTHON_VERSION : "3",
      BENCH_RESULTS_FILE_PATH : "bench-results.json"
    },
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

  compiler_benchmark:: self.bench_base + self.compiler_benchmarks_notifications + {
    teardown+: [
      ["bench-uploader.py", "${BENCH_RESULTS_FILE_PATH}"]
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

  // Job frequencies
  // ***************
  on_demand:: {
    targets+: [],
  },
  post_merge:: {
    targets+: ["post-merge"],
  },
  daily:: {
    targets+: ["daily"],
  },
  weekly:: {
    targets+: ["weekly"],
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
  }
}
