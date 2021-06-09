{
  local composable = (import "common-utils.libsonnet").composable,

  local mx_version = (import "graal-common.json").mx_version,
  local common_json = composable(import "common.json"),
  local repo_config = import 'repo-configuration.libsonnet',
  local jdks = common_json.jdks,
  local deps = common_json.deps,
  local downloads = common_json.downloads,
  # This must always point to HEAD in the master branch but can be used to point
  # to another branch/commit in a Graal PR when mx changes are required for the PR.
  mx:: {
    packages +: {
      mx: mx_version
    }
  },

  eclipse:: downloads.eclipse,
  jdt:: downloads.jdt,

  build_base:: {
    // holds location of CI resources that can easily be overwritten in an overlay
    ci_resources:: (import "ci-resources.libsonnet"),
  },

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

  // Heap settings
  // *************
  local small_heap = "1G",
  local default_heap = "8G",
  local large_heap = "31G", // strictly smaller than 32G to keep compressed oops enabled
  local large_young_gen_heap = "27G", // tuned to reduce latency of large apps like SpecJBB2015
  heap:: {
    small:: {
      environment+: {
        XMS: small_heap,
        XMX: small_heap
      }
    },
    default:: {
      environment+: {
        XMS: default_heap,
        XMX: default_heap
      }
    },
    large:: {
      environment+: {
        XMS: large_heap,
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

  // JDK definitions
  // ***************
  # jdk_version is an hidden field that can be used to generate job names
  local jdk8 =           { jdk_version:: 8},
  local jdk11 =          { jdk_version:: 11},
  local jdk16 =          { jdk_version:: 16},

  oraclejdk8::           jdk8 + { downloads+: { JAVA_HOME : jdks.oraclejdk8,      EXTRA_JAVA_HOMES : { pathlist :[ jdks["labsjdk-ee-11"] ]} }},
  oraclejdk8Only::       jdk8 + { downloads+: { JAVA_HOME : jdks.oraclejdk8 }},
  oraclejdk8Debug::      jdk8 + { downloads+: { JAVA_HOME : jdks.oraclejdk8Debug, EXTRA_JAVA_HOMES : { pathlist :[ jdks["labsjdk-ee-11"] ]} }},
  oraclejdk8OnlyDebug::  jdk8 + { downloads+: { JAVA_HOME : jdks.oraclejdk8Debug }},

  openjdk8::             jdk8 + { downloads+: { JAVA_HOME : jdks.openjdk8 }},

  oraclejdk11::          jdk11 + { downloads+: { JAVA_HOME : jdks.oraclejdk11 }},
  oraclejdk16::          jdk16 + { downloads+: { JAVA_HOME : jdks.oraclejdk16 }},
  openjdk11::            jdk11 + { downloads+: { JAVA_HOME : jdks.openjdk11 }},

  "labsjdk-ce-11"::      jdk11 + { downloads+: { JAVA_HOME : jdks["labsjdk-ce-11"] }},
  "labsjdk-ee-11"::      jdk11 + { downloads+: { JAVA_HOME : jdks["labsjdk-ee-11"] }},
  labsjdk11::            self["labsjdk-" + repo_config.graalvm_edition + "-11"],
  "labsjdk-ce-16"::      jdk16 + { downloads+: { JAVA_HOME : jdks["labsjdk-ce-16"] }},
  "labsjdk-ee-16"::      jdk16 + { downloads+: { JAVA_HOME : jdks["labsjdk-ee-16"] }},
  labsjdk16::            self["labsjdk-" + repo_config.graalvm_edition + "-16"],
  "labsjdk-ce-16Debug":: jdk16 + { downloads+: { JAVA_HOME : jdks["labsjdk-ce-16Debug"] }},
  "labsjdk-ee-16Debug":: jdk16 + { downloads+: { JAVA_HOME : jdks["labsjdk-ee-16Debug"] }},


  // Hardware definitions
  // ********************
  common:: deps.common + self.mx + {
    # enforce self.os (useful for generating job names)
    os:: error "self.os not set",
    # enforce self.arch (useful for generating job names)
    arch:: error "self.arch not set",
    capabilities +: [],
    catch_files +: [
      "Graal diagnostic output saved in (?P<filename>.+\\.zip)"
    ]
  },

  linux:: deps.linux + self.common + {
    os::"linux",
    capabilities+: [self.os],
  },

  darwin:: deps.darwin + self.common + {
    os::"darwin",
    capabilities+: [self.os],
  },

  windows:: deps.windows + self.common + {
    os::"windows",
    capabilities+: [self.os],
  },

  amd64:: {
    arch::"amd64",
    capabilities+: [self.arch]
  },

  aarch64:: {
    arch::"aarch64",
    capabilities+: [self.arch],
  },

  "linux-amd64"::     self.linux + self.amd64,
  "darwin-amd64"::    self.darwin + self.amd64,
  "windows-amd64"::   self.windows + self.amd64,
  "linux-aarch64"::   self.linux + self.aarch64,
}
