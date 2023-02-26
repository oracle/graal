{
  local common = import "ci/ci_common/common.jsonnet",
  local utils = import "common-utils.libsonnet",

  # benchmark job base with automatically generated name
  bench_base:: common.build_base + {
    # job name automatically generated: <job_prefix>-<suite>-<platform>-<jdk_version>-<os>-<arch>-<job_suffix>
    # null values are omitted from the list.
    generated_name:: utils.hyphenize([self.job_prefix, self.suite, self.platform, utils.prefixed_jdk(self.jdk_version), self.os, self.arch, self.job_suffix]),
    job_prefix:: null,
    job_suffix:: null,
    name:
      if self.is_jdk_supported(self.jdk_version) then self.generated_name
      else error "JDK" + self.jdk_version + " is not supported for " + self.generated_name + "! Suite is explicitly marked as working for JDK versions "+ self.min_jdk_version + " until " + self.max_jdk_version,
    suite:: error "'suite' must be set to generate job name",
    timelimit: error "build 'timelimit' is not set for "+ self.name +"!",
    local ol8_image = self.ci_resources.infra.ol8_bench_image,
    docker+: {
      "image": ol8_image,
      "mount_modules": true
    },
    should_use_hwloc:: std.objectHasAll(self, "is_numa") && self.is_numa && std.length(std.find("bench", self.targets)) > 0,
    min_jdk_version:: null,
    max_jdk_version:: null,
    is_jdk_supported(jdk_version)::
      if jdk_version == null then error "jdk_version cannot be null!" else
      if std.objectHasAll(self, "min_jdk_version") && self.min_jdk_version != null && jdk_version < self.min_jdk_version then false
      else if std.objectHasAll(self, "max_jdk_version") &&  self.max_jdk_version != null && jdk_version > self.max_jdk_version then false
      else true
  },

  bench_hw:: {
    _bench_machine:: {
      targets+: ["bench"],
      machine_name:: error "machine_name must be set!",
      local _machine_name = self.machine_name,
      capabilities+: [_machine_name],
      local GR26994_ActiveProcessorCount = "-Dnative-image.benchmark.extra-run-arg=-XX:ActiveProcessorCount="+std.toString(self.threads_per_node), # remove once GR-26994 is fixed
      environment+: { "MACHINE_NAME": _machine_name, "GR26994": GR26994_ActiveProcessorCount },
      numa_nodes:: [],
      is_numa:: std.length(self.numa_nodes) > 0,
      num_threads:: error "num_threads must bet set!",
      threads_per_node:: if self.is_numa then self.num_threads / std.length(self.numa_nodes) else self.num_threads,
    },

    x52:: common.linux + common.amd64 + self._bench_machine + {
      machine_name:: "x52",
      capabilities+: ["no_frequency_scaling", "tmpfs25g"],
      numa_nodes:: [0, 1],
      default_numa_node:: 0,
      num_threads:: 72
    },
    x82:: common.linux + common.amd64 + self._bench_machine + {
      machine_name:: "x82",
      capabilities+: ["no_frequency_scaling", "tmpfs25g"],
      numa_nodes:: [0, 1],
      default_numa_node:: 0,
      num_threads:: 96
    },
    xgene3:: common.linux + common.aarch64 + self._bench_machine + {
      machine_name:: "xgene3",
      capabilities+: [],
      num_threads:: 32
    },
    a12c:: common.linux + common.aarch64 + self._bench_machine + {
      machine_name:: "a12c",
      capabilities+: ["no_frequency_scaling", "tmpfs25g"],
      numa_nodes:: [0, 1],
      default_numa_node:: 0,
      num_threads:: 160
    }
  },

  hwlocIfNuma(numa, cmd, node=0)::
    if numa then
      ["hwloc-bind", "--cpubind", "node:"+node, "--membind", "node:"+node, "--"] + cmd
    else
      cmd,

  parallelHwloc(cmd_node0, cmd_node1)::
    // Returns a list of commands that will run cmd_nod0 on NUMA node 0
    // concurrently with cmd_node1 on NUMA node 1 and then wait for both to complete.
    [
      $.hwlocIfNuma(true, cmd_node0, node=0) + ["&"],
      $.hwlocIfNuma(true, cmd_node1, node=1) + ["&"],
      ["wait"]
    ],

  // building block used to generate fork builds
  many_forks_benchmarking:: common.build_base + {
    // assumes that the CI provides the following env vars: CURRENT_BRANCH, BUILD_DIR (as absolute path)
    local config_repo = "$BUILD_DIR/benchmarking-config",
    environment+: {
      FORK_COUNTS_DIRECTORY: config_repo + "/fork-counts",
      FORK_COUNT_FILE: error "FORK_COUNT_FILE env var must be set to use the many forks execution!"
    },
    // there is no guarantee that those setup steps run first or from the repo root folder, so all paths must be absolute
    setup+: [
      ["set-export", "CURRENT_BRANCH", ["git", "rev-parse", "--abbrev-ref", "HEAD"]],
      ["echo", "[BENCH-FORKS-CONFIG] Using configuration files from branch ${CURRENT_BRANCH} if it exists remotely."],
      ["git", "clone", self.ci_resources.infra.benchmarking_config_repo, config_repo],
      ["test", "${CURRENT_BRANCH}", "=", "master", "||", "git", "-C", config_repo, "checkout", "--track", "origin/${CURRENT_BRANCH}", "||", "echo", "Using default fork counts since there is no branch named '${CURRENT_BRANCH}' in the benchmarking-config repo."]
    ]
  },

  generate_fork_builds(suite_obj, subdir='compiler', forks_file_base_name=null)::
    /* based on a benchmark suite definition, generates the many forks version based on the hidden fields
     * 'forks_batches' that specifies the number of batches this job should be split into and the corresponding
     * 'forks_timelimit' that applies to those long-running jobs.
     *
     * The generated builder will set the 'FORK_COUNT_FILE' to the corresponding json file. So, make sure that the
     * mx benchmark command sets --fork-count-file=${FORK_COUNT_FILE}
     */

    if std.objectHasAll(suite_obj, "forks_batches") && std.objectHasAll(suite_obj, "forks_timelimit") && suite_obj.forks_batches != null then
      [ $.many_forks_benchmarking + suite_obj + {
        local batch_str = if suite_obj.forks_batches > 1 then "batch"+i else null,
        "job_prefix":: "bench-forks-" + subdir,
        "job_suffix":: batch_str,
        "timelimit": suite_obj.forks_timelimit,
        local base_name = if forks_file_base_name != null then forks_file_base_name else suite_obj.suite,
        "environment" +: {
          FORK_COUNT_FILE: "${FORK_COUNTS_DIRECTORY}/" + subdir + "/" + base_name + "_forks" + (if batch_str != null then "_"+batch_str else "") + ".json"
        }
      }
      for i in std.range(0, suite_obj.forks_batches - 1)]
    else
      [],
}
