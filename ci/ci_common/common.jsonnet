local composable = (import "common-utils.libsonnet").composable;

local mx_version = (import "../../common.json").mx_version;
local common_json = composable(import "../../common.json");
local repo_config = import '../repo-configuration.libsonnet';
local jdks = common_json.jdks;
local deps = common_json.deps;
local downloads = common_json.downloads;

# Finds the first integer in a string and returns it as an integer.
local find_first_integer(versionString) =
  local charToInt(c) =
    std.codepoint(c) - std.codepoint("0");
  local firstNum(s, i) =
    assert std.length(s) > i : "No number found in string " + s;
    local n = charToInt(s[i]);
    if n >=0 && n < 10 then i else firstNum(s, i + 1);
  local lastNum(s, i) =
    if i >= std.length(s) then
      i
    else
      local n = charToInt(s[i]);
      if n < 0 || n > 9 then i else lastNum(s, i + 1);
  local versionIndexStart = firstNum(versionString, 0);
  local versionIndexEnd = lastNum(versionString, versionIndexStart);
  std.parseInt(versionString[versionIndexStart:versionIndexEnd])
;
# jdk_version is an hidden field that can be used to generate job names
local add_jdk_version(name) =
  local jdk = jdks[name];
  // this assumes that the version is the first number in the jdk.version string
  local version = find_first_integer(jdk.version);
  // santity check that the parsed version is also included in the name
  assert std.length(std.findSubstr(std.toString(version), name)) == 1 : "Cannot find version %d in name %s" % [version, name];
  { jdk_version:: version }
;

{

  mx:: {
    packages+: {
      mx: mx_version
    }
  },

  eclipse:: downloads.eclipse,
  jdt:: downloads.jdt,
  devkits:: common_json.devkits,

  svm_deps:: common_json.svm.deps + repo_config.native_image.extra_deps,

  build_base:: {
    // holds location of CI resources that can easily be overwritten in an overlay
    ci_resources:: (import "ci/ci_common/ci-resources.libsonnet"),
  },

  // Job frequencies
  // ***************
  on_demand:: {
    targets+: ["ondemand"],
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

  # Add a guard to `build` that prevents it from running in the gate
  # for a PR that only touches *.md files, the docs, are config files for GitHub
  add_excludes_guard(build):: build + {
    guard+: {
      excludes+: ["**.md", "<graal>/**.md", "<graal>/docs/**", "<graal>/.devcontainer/**", "<graal>/.github/**"]
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

} + {
  // JDK definitions
  // ***************
  // this adds all jdks from common.json
  [name]: add_jdk_version(name) + { downloads+: { [if std.endsWith(name, "llvm") then "LLVM_JAVA_HOME" else "JAVA_HOME"] : jdks[name] }},
  for name in std.objectFieldsAll(jdks)
} + {
  # Aliases to edition specific labsjdks
  labsjdk11::            self["labsjdk-" + repo_config.graalvm_edition + "-11"],
  labsjdk17::            self["labsjdk-" + repo_config.graalvm_edition + "-17"],
  labsjdk19::            self["labsjdk-" + repo_config.graalvm_edition + "-19"],
  labsjdk17Debug::       self["labsjdk-" + repo_config.graalvm_edition + "-17Debug"],
  labsjdk19Debug::       self["labsjdk-" + repo_config.graalvm_edition + "-19Debug"],
  labsjdk11LLVM::        self["labsjdk-" + repo_config.graalvm_edition + "-11-llvm"],
  labsjdk17LLVM::        self["labsjdk-" + repo_config.graalvm_edition + "-17-llvm"],
  labsjdk19LLVM::        self["labsjdk-" + repo_config.graalvm_edition + "-19-llvm"],

  # Only CE exists for JDK 20 until JDK 20 GA.
  labsjdk20::            self["labsjdk-ce-20"],
  labsjdk20Debug::       self["labsjdk-ce-20Debug"],
  labsjdk20LLVM::        self["labsjdk-ce-20-llvm"],


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
