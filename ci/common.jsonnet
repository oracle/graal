# This file is shared between many repositories.
# All objects defined here are mixins, so you can use them like:
# { name: "myjob" } + common.linux_amd64 + common.jdks.labsjdk17ce + common.deps.sulong + ...
# Note that using a os-arch mixin like linux_amd64 mixin is required for using common.deps.

local common_json = import "../common.json";

{
  # JDK definitions
  # ***************
  local jdk_base = {
    name:         error "name not set",         # string; The JDK provider, e.g. "jpg-jdk", "labsjdk"
    version:      error "version not set",      # string; Full version string, e.g., "ce-21+35-jvmci-23.1-b15"
    jdk_version:: error "jdk_version not set",  #    int; The major JDK version, e.g., 21
    jdk_name::    "jdk%d" % self.jdk_version,   # string; The major JDK version with the JDK prefix.
                                                #         For the latest (unreleased) this should be overridden with "jdk-latest"
                                                #         otherwise the jdk_version with the "jdk" prefix, e.g., "jdk21".
                                                #         This should be use for constructing CI job names.
    # Optional:
    # "build_id": "33",
    # "release": true,
    # "platformspecific": true,
    # "extrabundles": ["static-libs"],
  },
  # ***************
  local variants(name) = [name, name + "Debug", name + "-llvm"],
  # gets the JDK major version from a labsjdk version string (e.g., "ce-21+35-jvmci-23.1-b15" -> 21)
  local parse_labsjdk_version(version) =
    assert std.startsWith(version, "ce-") || std.startsWith(version, "ee-") : "Unsupported labsjdk version: " + version;
    local number_prefix(str) =
      if std.length(str) == 0 || std.length(std.findSubstr(str[0], "0123456789")) == 0 then
        ""
      else
        str[0] + number_prefix(str[1:])
      ;
    std.parseInt(number_prefix(version[3:]))
    ,
  local jdks_data = {
    oraclejdk11: jdk_base + common_json.jdks["oraclejdk11"] + { jdk_version:: 11 },
  } + {
    [name]: jdk_base + common_json.jdks[name] + { jdk_version:: 17 }
    for name in ["oraclejdk17"] + variants("labsjdk-ce-17") + variants("labsjdk-ee-17")
  } + {
    [name]: jdk_base + common_json.jdks[name] + { jdk_version:: 19 }
    for name in ["oraclejdk19"] + variants("labsjdk-ce-19") + variants("labsjdk-ee-19")
  } + {
    [name]: jdk_base + common_json.jdks[name] + { jdk_version:: 20 }
    for name in ["oraclejdk20"] + variants("labsjdk-ce-20") + variants("labsjdk-ee-20")
  } + {
    [name]: jdk_base + common_json.jdks[name] + { jdk_version:: 21 }
    for name in ["oraclejdk21"] + variants("labsjdk-ce-21") + variants("labsjdk-ee-21")
  } + {
    [name]: jdk_base + common_json.jdks[name] + { jdk_version:: parse_labsjdk_version(self.version), jdk_name:: "jdk-latest"}
    for name in variants("labsjdk-ce-latest") + variants("labsjdk-ee-latest")
  },
  assert std.assertEqual(std.objectFields(common_json.jdks), std.objectFields(jdks_data)),

  # The raw jdk data, the same as common_json.jdks + { jdk_version:: }
  jdks_data: jdks_data,

  # Mixins to include and download the given JDK
  jdks: {
    [name]: {
      downloads+: {
        [if std.endsWith(name, "llvm") then "LLVM_JAVA_HOME" else "JAVA_HOME"]: jdks_data[name]
      },
      jdk_version:: jdks_data[name].jdk_version,
      jdk_name:: jdks_data[name].jdk_name,
    },
    for name in std.objectFields(jdks_data)
  } + {
    # Some convenient JDK aliases which don't require ["name"] for frequently-used JDKs
    labsjdk17ce: self["labsjdk-ce-17"],
    labsjdk17ee: self["labsjdk-ee-17"],

    labsjdk20ce: self["labsjdk-ce-20"],
    labsjdk20ee: self["labsjdk-ee-20"],

    labsjdkLatestCE: self["labsjdk-ce-21"],
    labsjdkLatestEE: self["labsjdk-ee-21"],
  },

  # The devkits versions reflect those used to build the JVMCI JDKs (e.g., see devkit_platform_revisions in <jdk>/make/conf/jib-profiles.js)
  devkits: {
    "windows-jdk17": { packages+: { "devkit:VS2022-17.1.0+1": "==0" }},
    "windows-jdk19": { packages+: { "devkit:VS2022-17.1.0+1": "==0" }},
    "windows-jdk20": { packages+: { "devkit:VS2022-17.1.0+1": "==0" }},
    "windows-jdk21": { packages+: { "devkit:VS2022-17.1.0+1": "==1" }},
    "windows-jdkLatest": { packages+: { "devkit:VS2022-17.1.0+1": "==1" }},
    "linux-jdk17": { packages+: { "devkit:gcc11.2.0-OL6.4+1": "==0" }},
    "linux-jdk19": { packages+: { "devkit:gcc11.2.0-OL6.4+1": "==0" }},
    "linux-jdk20": { packages+: { "devkit:gcc11.2.0-OL6.4+1": "==0" }},
    "linux-jdk21": { packages+: { "devkit:gcc11.2.0-OL6.4+1": "==0" }},
  },

  # Dependencies
  # ************
  deps: {
    eclipse: {
      downloads+: {
        ECLIPSE: {
          name: "eclipse",
          version: common_json.eclipse.version,
          platformspecific: true,
        }
      },
      environment+: {
        ECLIPSE_EXE: "$ECLIPSE/eclipse",
      },
    },

    jdt: {
      environment+: {
        JDT: "builtin",
      },
    },

    pylint:: {
      packages+: {
        "pip:pylint": common_json.pip.pylint,
        "pip:lazy-object-proxy": common_json.pip["lazy-object-proxy"],
      },

      # Required to keep pylint happy on Darwin
      # https://coderwall.com/p/-k_93g/mac-os-x-valueerror-unknown-locale-utf-8-in-python"
      environment+: if self.os == "darwin" then {
        LC_ALL: "en_US.UTF-8",
      } else {},
    },

    sulong:: {
      packages+: {
        cmake: "==3.22.2",
      } + if self.os == "windows" then {
        msvc_source: "==14.0",
      } else {},
    },

    truffleruby:: {
      packages+: (if self.os == "linux" && self.arch == "amd64" then {
        ruby: "==3.1.2", # Newer version, also used for benchmarking
      } else {
        ruby: "==3.0.2",
      }) + (if self.os == "linux" then {
        libyaml: "==0.2.5",
      } else {}),
    },

    graalnodejs:: {
      packages+: if self.os == "linux" then {
        cmake: "==3.22.2",
      } + (if self.arch == "aarch64" then {
        "00:devtoolset": "==10",
      } else {
        "00:devtoolset": "==11",
      })
      else {},
    },

    svm:: {
      environment+: {
        DEFAULT_VM: "server",
        LANG: "en_US.UTF-8",
      },
      logs+: [
        "../.native-image/*/*/*/*.log",
        "*/*.log",
        "*/svmbuild/*.log",
        "*/svmbuild/images/*.log",
        "*/*/stripped/*.map",
        "*/callgrind.*",
        "*.log",
      ],

      packages+: if self.os == "linux" && std.objectHas(self, "os_distro") && self.os_distro == "ol" then
        (if self.arch == "aarch64" then {
          "00:devtoolset": "==10",
        } else {
          "00:devtoolset": "==11",
        })
      else {},
    },
  },

  # Job frequencies
  # ***************
  frequencies: {
    gate: {
      targets+: ["gate"],
    },
    bench: {
      targets+: ["bench"],
    },
    on_demand: {
      targets+: ["ondemand"],
    },
    post_merge: {
      targets+: ["post-merge"],
    },
    opt_post_merge: {
      targets+: ["opt-post-merge"],
      tags+: []
    },
    daily: {
      targets+: ["daily"],
    },
    weekly: {
      targets+: ["weekly"],
    },
    monthly: {
      targets+: ["monthly"],
    }
  },

  # Hardware definitions and common fields
  # **************************************
  # Note that only platforms (os-arch) are exposed (not os and arch separately),
  # because this is the simplest way to ensure correct usage and dependencies (e.g. ol7 in linux_amd64).
  #
  # To add extra "common" fields for your CI:
  # * If you already have platforms objects, you could extend them like:
  #   linux_amd64: common.linux_amd64 + self.my_common,
  # * Otherwise, just include your common object as well as one of the os-arch objects below in each job:
  #   { name: "myjob" } + common.linux_amd64 + self.my_common + ...
  #
  # This also means self.my_common should no longer include mx, etc as it is already included by the os-arch objects.
  local mx = {
    environment+: {
      MX_PYTHON: "python3.8",
    },
    packages+: {
      python3: "==3.8.10",
      "pip:ninja_syntax": common_json.pip.ninja_syntax,
      mx: common_json.mx_version,
    },
    python_version: "3", # To use the correct virtualenv
  },

  local common = mx + {
    catch_files+: [
      # Keep in sync with org.graalvm.compiler.debug.StandardPathUtilitiesProvider#DIAGNOSTIC_OUTPUT_DIRECTORY_MESSAGE_REGEXP
      "Graal diagnostic output saved in '(?P<filename>[^']+)'",
      # Keep in sync with org.graalvm.compiler.debug.DebugContext#DUMP_FILE_MESSAGE_REGEXP
      "Dumping debug output to '(?P<filename>[^']+)'",
      # Keep in sync with com.oracle.svm.hosted.NativeImageOptions#DEFAULT_ERROR_FILE_NAME
      " (?P<filename>.+/svm_err_b_\\d+T\\d+\\.\\d+_pid\\d+\\.md)",
    ],
  },

  // OS specific file handling
  os_utils:: {
    local lib_format = {
      "windows": "%s.dll",
      "linux":   "lib%s.so",
      "darwin":  "lib%s.dylib"
    },

    # Converts unixpath to an OS specific path
    os_path(unixpath):: if self.os == "windows" then std.strReplace(unixpath, "/", "\\") else unixpath,

    # Converts unixpath to an OS specific path for an executable
    os_exe(unixpath)::  if self.os == "windows" then self.os_path(unixpath) + ".exe" else unixpath,

    # Converts a base library name to an OS specific file name
    os_lib(name)::      lib_format[self.os] % name,
  },

  local ol7 = {
    docker+: {
      image: "buildslave_ol7",
      mount_modules: true,
    },
  },
  local ol9 = {
    docker+: {
      image: "buildslave_ol9",
      mount_modules: true,
    },
  },
  local ubuntu22 = {
    docker+: {
      image: "buildslave_ubuntu22",
      mount_modules: true,
    },
  },
  local deps_linux = {
  },
  local deps_darwin = {
  },
  local deps_windows = {
  },

  local linux   = deps_linux   + common + { os:: "linux",   capabilities+: [self.os] },
  local darwin  = deps_darwin  + common + { os:: "darwin",  capabilities+: [self.os] },
  local windows = deps_windows + common + { os:: "windows", capabilities+: [self.os] },
  local windows_server_2016 = windows + { capabilities+: ["windows_server_2016"] },

  local amd64   = { arch:: "amd64",   capabilities+: [self.arch] },
  local aarch64 = { arch:: "aarch64", capabilities+: [self.arch] },
  local ol_distro = {os_distro:: "ol"},

  linux_amd64: linux + amd64 + ol7 + ol_distro,
  linux_amd64_ubuntu: linux + amd64 + ubuntu22 + {os_distro:: "ubuntu"},
  linux_amd64_ol9: linux + amd64 + ol9 + ol_distro,
  linux_aarch64: linux + aarch64 + ol_distro,
  linux_aarch64_ol9: linux + aarch64 + ol9 + ol_distro,

  darwin_amd64: darwin + amd64,
  darwin_aarch64: darwin + aarch64,

  windows_amd64: windows + amd64,
  windows_server_2016_amd64: windows_server_2016 + amd64,


  # Utils
  disable_proxies: {
    setup+: [
      ["unset", "HTTP_PROXY", "HTTPS_PROXY", "FTP_PROXY", "NO_PROXY", "http_proxy", "https_proxy", "ftp_proxy", "no_proxy"],
    ],
  },
}
