# This file is only shared between the graal and graal-enterprise repositories.

local common = import "../common.jsonnet";
local repo_config = import '../repo-configuration.libsonnet';

common + common.frequencies + {
  build_base:: {
    // holds location of CI resources that can easily be overwritten in an overlay
    ci_resources:: (import "ci/ci_common/ci-resources.libsonnet"),
  },

  # Add a guard to `build` that prevents it from running in the gate
  # for a PR that only touches *.md files, the docs, are config files for GitHub
  add_excludes_guard(build):: build + {
    guard+: {
      excludes+: ["**.md", "<graal>/**.md", "<graal>/docs/**", "<graal>/.devcontainer/**", "<graal>/.github/**", "<graal>/vm/ce-release-artifacts.json"]
    }
  },

  // Heap settings
  // *************
  local small_heap = "1G",
  local default_heap = "8G",
  local large_heap = "31G", // strictly smaller than 32G to keep compressed oops enabled
  local large_young_gen_heap = "27G", // tuned to reduce latency of large apps like SpecJBB2015
  heap:: {
    small:: {
      environment+: {
        XMX: small_heap
      }
    },
    default:: {
      environment+: {
        XMX: default_heap
      }
    },
    large:: {
      environment+: {
        XMX: large_heap
      }
    },
    large_with_large_young_gen:: {
      environment+: {
        XMS: large_heap,
        XMX: large_heap,
        XMN: large_young_gen_heap
      }
    }
  },

} + common.jdks + {
  # Aliases to edition specific labsjdks
  labsjdk17::            self["labsjdk-" + repo_config.graalvm_edition + "-17"],
  labsjdk19::            self["labsjdk-" + repo_config.graalvm_edition + "-19"],
  labsjdk17Debug::       self["labsjdk-" + repo_config.graalvm_edition + "-17Debug"],
  labsjdk19Debug::       self["labsjdk-" + repo_config.graalvm_edition + "-19Debug"],
  labsjdk17LLVM::        self["labsjdk-" + repo_config.graalvm_edition + "-17-llvm"],
  labsjdk19LLVM::        self["labsjdk-" + repo_config.graalvm_edition + "-19-llvm"],

  labsjdk20::            self["labsjdk-" + repo_config.graalvm_edition + "-20"],
  labsjdk20Debug::       self["labsjdk-" + repo_config.graalvm_edition + "-20Debug"],
  labsjdk20LLVM::        self["labsjdk-" + repo_config.graalvm_edition + "-20-llvm"],

  // Hardware definitions
  // ********************
  local graal_common_extras = common.deps.pylint + {
    logs+: [
      "*.bgv",
      "./" + repo_config.compiler.compiler_suite + "/graal_dumps/*/*",
    ],
    timelimit: "30:00",
  },
  local linux_deps_extras = {
    packages+: {
      "apache/ant": ">=1.9.4",
    },
  },

  linux_amd64: linux_deps_extras + common.linux_amd64 + graal_common_extras,
  linux_aarch64: linux_deps_extras + common.linux_aarch64 + graal_common_extras,
  darwin_amd64: common.darwin_amd64 + graal_common_extras,
  darwin_aarch64: common.darwin_aarch64 + graal_common_extras,
  windows_amd64: common.windows_amd64 + graal_common_extras,
  windows_server_2016_amd64: common.windows_server_2016_amd64 + graal_common_extras,

  // Other
  mach5_target:: {targets+: ["mach5"]},
}
