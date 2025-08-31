# This file is only shared between the graal and graal-enterprise repositories.

local common = import "../common.jsonnet";
local utils = import "common-utils.libsonnet";
local repo_config = import '../repo-configuration.libsonnet';

common + common.frequencies + {
  build_base:: {
    // holds location of CI resources that can easily be overwritten in an overlay
    ci_resources:: (import "ci-resources.libsonnet"),
  },

  # Add a guard to `build` that prevents it from running in the gate
  # for a PR that only touches *.md files, the docs, are config files for GitHub
  #
  # To avoid skipping the deployment of some artifacts, only `gate` jobs and
  # post-merges that do not have the `deploy` target are considered.
  add_excludes_guard(build):: (
    if (std.length(std.find('gate', build.targets)) > 0 || std.length(std.find('deploy', build.targets)) == 0) then {
      guard: {
        excludes: ["*.md",
          "<graal>/*.md",
          "<graal>/ci/**.md",
          "<graal>/compiler/**.md",
          "<graal>/espresso/**.md",
          "<graal>/regex/**.md",
          "<graal>/sdk/**.md",
          "<graal>/substratevm/docs/**", # Substratevm includes substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/doc-files/PrintImageHeapConnectedComponents.md in the build
          "<graal>/substratevm/CHANGELOG.md",
          "<graal>/substratevm/README.md",
          "<graal>/sulong/docs/**.md",  # Sulong includes its readme in a distribution
          "<graal>/sulong/CHANGELOG.md",
          "<graal>/tools/**.md",
          "<graal>/truffle/**.md",
          "<graal>/visualizer/**.md",
          "<graal>/vm/src/**.md", # vm/GRAALVM-README.md is included in a distribution
          "<graal>/vm/README.md",
          "<graal>/vm/benchmarks/**.md",
          "<graal>/vm/docs/**",
          "<graal>/wasm/**.md",
          "<graal>/docs/**",
          "<graal>/.devcontainer/**",
          "<graal>/.github/**",
          "<graal>/vm/ce-release-artifacts.json"
        ]
      }
    } else {}
  ) + build,

  # Add the specified components to the field `components`.
  with_components(builds, components)::
    [
      if std.objectHas(build, "components") then
        build + { "components" : std.setUnion(components, build.components) }
      else
        build + { "components" : components }
      for build in builds
    ],
  # Add the specified components to the field `components`.
  with_style_component(build)::
    if std.objectHas(build, "name") && utils.contains(build.name, "-style-") then
      $.with_components([build], ["style"])[0]
    else
      build
    ,

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

  labsjdk21::            self["labsjdk-" + repo_config.graalvm_edition + "-21"],
  labsjdk21Debug::       self["labsjdk-" + repo_config.graalvm_edition + "-21Debug"],
  labsjdk21LLVM::        self["labsjdk-" + repo_config.graalvm_edition + "-21-llvm"],
  graalvmee21::          self["graalvm-ee-21"],

  labsjdk25::            self["labsjdk-" + repo_config.graalvm_edition + "-25"],
  labsjdk25Debug::       self["labsjdk-" + repo_config.graalvm_edition + "-25Debug"],
  labsjdk25LLVM::        self["labsjdk-" + repo_config.graalvm_edition + "-25-llvm"],

  labsjdkLatest::            self["labsjdk-" + repo_config.graalvm_edition + "-latest"],
  labsjdkLatestDebug::       self["labsjdk-" + repo_config.graalvm_edition + "-latestDebug"],
  labsjdkLatestLLVM::        self["labsjdk-" + repo_config.graalvm_edition + "-latest-llvm"],

  // Hardware definitions
  // ********************
  local graal_common_extras = common.deps.pylint + common.deps.black + {
    logs+: [
      "*.bgv",
      "*/graal_dumps/*/*",
    ],
  },
  local linux_deps_extras = {
    packages+: {
      "apache/ant": "==1.10.1",
    },
  },

  linux_amd64: common.linux_amd64 + graal_common_extras + linux_deps_extras,
  linux_amd64_ol9: common.linux_amd64_ol9 + graal_common_extras + linux_deps_extras,
  linux_amd64_ubuntu: common.linux_amd64_ubuntu + graal_common_extras,
  linux_aarch64: common.linux_aarch64 + graal_common_extras + linux_deps_extras,
  linux_aarch64_ol9: common.linux_aarch64_ol9 + graal_common_extras + linux_deps_extras,
  darwin_amd64: common.darwin_amd64 + graal_common_extras,
  darwin_aarch64: common.darwin_aarch64 + graal_common_extras,
  windows_amd64: common.windows_amd64 + graal_common_extras,
  windows_server_2016_amd64: common.windows_server_2016_amd64 + graal_common_extras,
}
