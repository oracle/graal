{
  local composable = (import "common-utils.libsonnet").composable,

  local mx_version = (import "graal-common.json").mx_version,
  local common_json = composable(import "common.json"),
  local repo_config = import 'repo-configuration.libsonnet',
  local jdks = common_json.jdks,
  local deps = common_json.deps,
  local downloads = common_json.downloads,

  mx:: {
    packages+: {
      mx: mx_version
    }
  },

  eclipse:: downloads.eclipse,
  jdt:: downloads.jdt,
  devkits:: common_json.devkits,

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
  monthly:: {
    targets+: ["monthly"],
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

  // JDK definitions
  // ***************
  # jdk_version is an hidden field that can be used to generate job names
  local jdk11 =          { jdk_version:: 11},
  local jdk17 =          { jdk_version:: 17},

  oraclejdk11::          jdk11 + { downloads+: { JAVA_HOME : jdks.oraclejdk11 }},
  oraclejdk17::          jdk17 + { downloads+: { JAVA_HOME : jdks.oraclejdk17 }},
  openjdk11::            jdk11 + { downloads+: { JAVA_HOME : jdks.openjdk11 }},

  "labsjdk-ce-11"::      jdk11 + { downloads+: { JAVA_HOME : jdks["labsjdk-ce-11"] }},
  "labsjdk-ee-11"::      jdk11 + { downloads+: { JAVA_HOME : jdks["labsjdk-ee-11"] }},
  "labsjdk-ce-17"::      jdk17 + { downloads+: { JAVA_HOME : jdks["labsjdk-ce-17"] }},
  "labsjdk-ee-17"::      jdk17 + { downloads+: { JAVA_HOME : jdks["labsjdk-ee-17"] }},
  "labsjdk-ce-17Debug":: jdk17 + { downloads+: { JAVA_HOME : jdks["labsjdk-ce-17Debug"] }},
  "labsjdk-ee-17Debug":: jdk17 + { downloads+: { JAVA_HOME : jdks["labsjdk-ee-17Debug"] }},
  "labsjdk-ce-11-llvm":: jdk11 + { downloads+: { LLVM_JAVA_HOME : jdks["labsjdk-ce-11-llvm"] }},
  "labsjdk-ee-11-llvm":: jdk11 + { downloads+: { LLVM_JAVA_HOME : jdks["labsjdk-ee-11-llvm"] }},
  "labsjdk-ce-17-llvm":: jdk17 + { downloads+: { LLVM_JAVA_HOME : jdks["labsjdk-ce-17-llvm"] }},
  "labsjdk-ee-17-llvm":: jdk17 + { downloads+: { LLVM_JAVA_HOME : jdks["labsjdk-ee-17-llvm"] }},

  # Aliases to edition specific labsjdks
  labsjdk11::            self["labsjdk-" + repo_config.graalvm_edition + "-11"],
  labsjdk17::            self["labsjdk-" + repo_config.graalvm_edition + "-17"],
  labsjdk11Debug::       self["labsjdk-" + repo_config.graalvm_edition + "-11Debug"],
  labsjdk17Debug::       self["labsjdk-" + repo_config.graalvm_edition + "-17Debug"],
  labsjdk11LLVM::        self["labsjdk-" + repo_config.graalvm_edition + "-11-llvm"],
  labsjdk17LLVM::        self["labsjdk-" + repo_config.graalvm_edition + "-17-llvm"],


  // Hardware definitions
  // ********************
  common:: deps.common + self.mx + {
    local where = if std.objectHas(self, "name") then " in " + self.name else "",
    # enforce self.os (useful for generating job names)
    os:: error "self.os not set" + where,
    # enforce self.arch (useful for generating job names)
    arch:: error "self.arch not set" + where,
    capabilities +: [],
    catch_files +: common_json.catch_files,
    logs +: [
      "*.bgv",
      "./" + repo_config.compiler.compiler_suite + "/graal_dumps/*/*"
    ]
  },

  ol7:: self.build_base + {
    local ol7_image = self.ci_resources.infra.docker_image_ol7,
    docker+: {
      image: ol7_image,
      mount_modules: true,
    },
  },

  linux::   deps.linux   + self.common + {os::"linux",   capabilities+: [self.os]},
  darwin::  deps.darwin  + self.common + {os::"darwin",  capabilities+: [self.os]},
  windows:: deps.windows + self.common + {os::"windows", capabilities+: [self.os]},
  windows_server_2016:: self.windows + {capabilities+: ["windows_server_2016"]},

  amd64::   { arch::"amd64",   capabilities+: [self.arch]},
  aarch64:: { arch::"aarch64", capabilities+: [self.arch]},

  linux_amd64::               self.linux               + self.amd64,
  darwin_amd64::              self.darwin              + self.amd64,
  darwin_aarch64::            self.darwin              + self.aarch64 + {
      # only needed until GR-22580 is resolved?
      python_version: 3,
  },
  windows_amd64::             self.windows             + self.amd64,
  windows_server_2016_amd64:: self.windows_server_2016 + self.amd64,
  linux_aarch64::             self.linux               + self.aarch64,

  mach5_target:: {targets+: ["mach5"]},

  // Utils
  disable_proxies:: {
    setup+: [["unset", "HTTP_PROXY", "HTTPS_PROXY", "FTP_PROXY", "NO_PROXY", "http_proxy", "https_proxy", "ftp_proxy", "no_proxy"]],
  },
}
