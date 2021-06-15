{
  local common = import "common.jsonnet",
  local utils = import "common-utils.libsonnet",

  # benchmark job base with automatically generated name
  bench_base:: common.build_base + {
    # job name automatically generated: <job_prefix>-<suite>-<platform>-<jdk_version>-<os>-<arch>-<job_suffix>
    # null values are omitted from the list.
    generated_name:: utils.hyphenize([self.job_prefix, self.suite, self.platform, utils.prefixed_jdk(self.jdk_version), self.os, self.arch, self.job_suffix]),
    job_prefix:: null,
    job_suffix:: null,
    name: self.generated_name,
    suite:: error "'suite' must be set to generate job name",
    timelimit: error "build 'timelimit' is not set for "+ self.name +"!",
    local ol8_image = self.ci_resources.infra.ol8_bench_image,
    docker+: {
      "image": ol8_image,
      "mount_modules": true
    },
    should_use_hwloc:: std.objectHasAll(self, "is_numa") && self.is_numa && std.length(std.find("bench", self.targets)) > 0,
  },

  bench_hw:: {
    _bench_machine:: {
      targets+: ["bench"],
      machine_name:: error "machine_name must be set!",
      local _machine_name = self.machine_name,
      capabilities+: [_machine_name],
      environment+: { "MACHINE_NAME": _machine_name },
      numa_nodes:: [],
      is_numa:: std.length(self.numa_nodes) > 0,
    },

    x52:: common.linux + common.amd64 + self._bench_machine + {
      machine_name:: "x52",
      capabilities+: ["no_frequency_scaling", "tmpfs25g"],
      numa_nodes:: [0, 1],
      default_numa_node:: 0
    },
    xgene3:: common.linux + common.aarch64 + self._bench_machine + {
      machine_name:: "xgene3",
      capabilities+: [],
    },
    a12c:: common.linux + common.aarch64 + self._bench_machine + {
      machine_name:: "a12c",
      capabilities+: ["no_frequency_scaling", "tmpfs25g"],
      numa_nodes:: [0, 1],
      default_numa_node:: 0
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
    ]
}
