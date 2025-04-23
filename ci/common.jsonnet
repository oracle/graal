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
  local parse_labsjdk_version(jdk) =
    if jdk.name == "jpg-jdk" then std.parseInt(jdk.version) else
    local version = jdk.version;
    assert std.startsWith(version, "ce-") || std.startsWith(version, "ee-") : "Unsupported labsjdk version: " + version;
    local number_prefix(str) =
      if std.length(str) == 0 || std.length(std.findSubstr(str[0], "0123456789")) == 0 then
        ""
      else
        str[0] + number_prefix(str[1:])
      ;
    std.parseInt(number_prefix(version[3:]))
    ,
  # gets the build_id from a labsjdk version string (e.g., "ce-21+35-jvmci-23.1-b15" -> 21)
  local get_labsjdk_build_id(jdk) =
    local _parts = std.split(jdk.version, "-");
    local _version_build_id = std.split(_parts[1], "+");
    _version_build_id[1]
    ,
  local jdks_data = {
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
    'oraclejdk23': jdk_base + common_json.jdks["oraclejdk23"] + { jdk_version:: 23 },
  } + {
    [name]: jdk_base + common_json.jdks[name] + { jdk_version:: parse_labsjdk_version(self), jdk_name:: "jdk-latest"}
    for name in ["oraclejdk-latest"] + variants("labsjdk-ce-latest") + variants("labsjdk-ee-latest")
  } + {
    'graalvm-ee-21': jdk_base + common_json.jdks["graalvm-ee-21"] + { jdk_version:: 21 },
  },
  # We do not want to expose galahad-jdk
  assert std.assertEqual([x for x in std.objectFields(common_json.jdks) if x != "galahad-jdk"], std.objectFields(jdks_data)),
  # Verify oraclejdk-latest and labsjdk-ee-latest versions match
  assert
    local _labsjdk = common_json.jdks["labsjdk-ee-latest"];
    local _oraclejdk = common_json.jdks["oraclejdk-latest"];
    local _ov = _oraclejdk.build_id;
    local _lv = std.strReplace(_labsjdk.version, "ee-", "jdk-");
    # Skip the check if we are not using a labsjdk. This can happen on JDK integration branches.
    local no_labsjdk = _labsjdk.name != "labsjdk";
    assert no_labsjdk || std.startsWith(_lv, _ov) : "update oraclejdk-latest to match labsjdk-ee-latest: %s+%s vs %s" % [_oraclejdk.version, _oraclejdk.build_id, _labsjdk.version];
    true,

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

    labsjdk21ce: self["labsjdk-ce-21"],
    labsjdk21ee: self["labsjdk-ee-21"],

    labsjdkLatestCE: self["labsjdk-ce-latest"],
    labsjdkLatestEE: self["labsjdk-ee-latest"],

    oraclejdkLatest: self["oraclejdk-latest"],
  },

  # The devkits versions reflect those used to build the JVMCI JDKs (e.g., see devkit_platform_revisions in <jdk>/make/conf/jib-profiles.js)
  devkits: {
    "windows-jdk17": { packages+: { "devkit:VS2022-17.1.0+1": "==0" }},
    "windows-jdk19": { packages+: { "devkit:VS2022-17.1.0+1": "==0" }},
    "windows-jdk20": { packages+: { "devkit:VS2022-17.1.0+1": "==0" }},
    "windows-jdk21": { packages+: { "devkit:VS2022-17.1.0+1": "==1" }},
    "windows-jdk-latest": { packages+: { "devkit:VS2022-17.6.5+1": "==0" }},
    "windows-jdkLatest": self["windows-jdk-latest"],
    "linux-jdk17": { packages+: { "devkit:gcc11.2.0-OL6.4+1": "==0" }},
    "linux-jdk19": { packages+: { "devkit:gcc11.2.0-OL6.4+1": "==0" }},
    "linux-jdk20": { packages+: { "devkit:gcc11.2.0-OL6.4+1": "==0" }},
    "linux-jdk21": { packages+: { "devkit:gcc11.2.0-OL6.4+1": "==0" }},
    "linux-jdk-latest": { packages+: { "devkit:gcc13.2.0-OL6.4+1": "==0" }},
    "linux-jdkLatest": self["linux-jdk-latest"],
  },

  # Dependencies
  # ************
  deps: {
    # These dependencies are included in Build GraalVM platforms, but not in bare platforms

    mx: {
      environment+: {
        MX_PYTHON: "python3.8",
        PYTHONIOENCODING: "utf-8",
      },
      packages+: {
        python3: "==3.8.10",
        "pip:ninja_syntax": common_json.pip.ninja_syntax,
        mx: common_json.mx_version,
      },
      python_version: "3", # To use the correct virtualenv
    },

    common_catch_files: {
      catch_files+: [
        # Keep in sync with jdk.graal.compiler.debug.StandardPathUtilitiesProvider#DIAGNOSTIC_OUTPUT_DIRECTORY_MESSAGE_REGEXP
        "Graal diagnostic output saved in '(?P<filename>[^']+)'",
        # Keep in sync with jdk.graal.compiler.debug.DebugContext#DUMP_FILE_MESSAGE_REGEXP
        "Dumping debug output to '(?P<filename>[^']+)'",
        # Keep in sync with com.oracle.svm.hosted.NativeImageOptions#DEFAULT_ERROR_FILE_NAME
        " (?P<filename>.+/svm_err_b_\\d+T\\d+\\.\\d+_pid\\d+\\.md)",
        # Keep in sync with jdk.graal.compiler.test.SubprocessUtil#makeArgfile
        "@(?P<filename>.*SubprocessUtil-argfiles.*\\.argfile)",
        # Keep in sync with com.oracle.truffle.api.test.SubprocessTestUtils#makeArgfile
        "@(?P<filename>.*SubprocessTestUtils-argfiles.*\\.argfile)",
        # Keep in sync with mx_gate.py:get_jacoco_agent_args
        "JaCoCo agent config: '(?P<filename>[^']+)'",
      ],
    },

    common_env: {
      environment+: {
        # Enforce experimental option checking in CI (GR-47922)
        NATIVE_IMAGE_EXPERIMENTAL_OPTIONS_ARE_FATAL: "true",
      },
    },

    # These dependencies are not included by default in any platform object

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

    black:: {
      packages+: {
        # black is used to format python source code
        "pip:black": common_json.pip.black,
      },
    },

    gradle:: {
      downloads+: {
        GRADLE_JAVA_HOME: jdks_data["oraclejdk21"],
      }
    },

    local code_tools = {
      downloads+: if 'jdk_version' in self && self.jdk_version > 21 then {
        TOOLS_JAVA_HOME: jdks_data['oraclejdk21'],
      } else {},
    },
    # GR-46676: ProGuard does not yet run on JDK 22
    proguard: code_tools,
    # GR-49566: SpotBugs does not yet run on JDK 22
    spotbugs: code_tools,

    sulong:: {
      packages+: {
        cmake: "==3.22.2",
      } + if self.os == "windows" then {
        msvc_source: "==14.0",
      } else {},
    },

    truffleruby:: {
      packages+: (if self.os == "linux" && self.arch == "amd64" then {
        ruby: "==3.2.2", # Newer version, also used for benchmarking
      } else if (self.os == "windows") then
        error('truffleruby is not supported on windows')
      else {
        ruby: "==3.0.2",
      }) + (if self.os == "linux" then {
        libyaml: "==0.2.5",
      } else {}),
    },

    graalnodejs:: {
      local this = self,
      packages+: if self.os == "linux" then {
        cmake: "==3.22.2",
      } else {},
      environment+: if self.os == "windows" then {
        local devkits_version = std.filterMap(
          function(p) std.startsWith(p, 'devkit:VS'),  # filter function
          function(p) std.substr(p, std.length('devkit:VS'), 4),  # map function
          std.objectFields(this.packages)  # array
        )[0],
        DEVKIT_VERSION: devkits_version,  # TODO: dep of Graal.nodejs
      } else {},
      downloads+: if self.os == "windows" then {
        NASM: {name: 'nasm', version: '2.14.02', platformspecific: true},
      } else {},
    },

    graalpy:: self.gradle + {
      packages+: if (self.os == "linux") then {
        libffi: '>=3.2.1',
        bzip2: '>=1.0.6',
        maven: ">=3.3.9",
      } else {},
    },

    wasm:: {
      downloads+: {
        WABT_DIR: {name: 'wabt', version: '1.0.36', platformspecific: true},
      },
      environment+: {
        WABT_DIR: '$WABT_DIR/bin',
      },
    },

    fastr:: {
      # Note: On both Linux and MacOS, FastR depends on the gnur module and on gfortran
      # of a specific version (4.8.5 on Linux, 10.2.0 on MacOS)
      # However, we do not need to load those modules, we only configure specific environment variables to
      # point to these specific modules. These modules and the configuration is only necessary for installation of
      # some R packages (that have Fortran code) and in order to run GNU-R
      packages+:
        if (self.os == "linux" && self.arch == "amd64") then {
          readline: '==6.3',
          pcre2: '==10.37',
          curl: '==7.50.1',
          gnur: '==4.0.3-gcc4.8.5-pcre2',
        }
        else if (self.os == "darwin" && self.arch == "amd64") then {
          'pcre2': '==10.37',
        } else {},
      environment+:
        if (self.os == "linux" && self.arch == "amd64") then {
          TZDIR: '/usr/share/zoneinfo',
          PKG_INCLUDE_FLAGS_OVERRIDE : '-I/cm/shared/apps/bzip2/1.0.6/include -I/cm/shared/apps/xz/5.2.2/include -I/cm/shared/apps/pcre2/10.37/include -I/cm/shared/apps/curl/7.50.1/include',
          PKG_LDFLAGS_OVERRIDE : '-L/cm/shared/apps/bzip2/1.0.6/lib -L/cm/shared/apps/xz/5.2.2/lib -L/cm/shared/apps/pcre2/10.37/lib -L/cm/shared/apps/curl/7.50.1/lib -L/cm/shared/apps/gcc/4.8.5/lib64',
          FASTR_FC: '/cm/shared/apps/gcc/4.8.5/bin/gfortran',
          FASTR_CC: '/cm/shared/apps/gcc/4.8.5/bin/gcc',
          GNUR_HOME_BINARY: '/cm/shared/apps/gnur/4.0.3_gcc4.8.5_pcre2-10.37/R-4.0.3',
          FASTR_RELEASE: 'true',
        }
        else if (self.os == "darwin" && self.arch == "amd64") then {
          FASTR_FC: '/cm/shared/apps/gcc/8.3.0/bin/gfortran',
          FASTR_CC: '/cm/shared/apps/gcc/8.3.0/bin/gcc',
          TZDIR: '/usr/share/zoneinfo',
          PKG_INCLUDE_FLAGS_OVERRIDE : '-I/cm/shared/apps/pcre2/pcre2-10.37/include -I/cm/shared/apps/bzip2/1.0.6/include -I/cm/shared/apps/xz/5.2.2/include -I/cm/shared/apps/curl/7.50.1/include',
          PKG_LDFLAGS_OVERRIDE : '-L/cm/shared/apps/bzip2/1.0.6/lib -L/cm/shared/apps/xz/5.2.2/lib -L/cm/shared/apps/pcre2/pcre2-10.37/lib -L/cm/shared/apps/curl/7.50.1/lib -L/cm/shared/apps/gcc/10.2.0/lib -L/usr/lib',
          FASTR_RELEASE: 'true',
        } else {},
      downloads+:
        if (self.os == "linux" && self.arch == "amd64") then {
          BLAS_LAPACK_DIR: { name: 'fastr-403-blas-lapack-gcc', version: '4.8.5', platformspecific: true },
          F2C_BINARY: { name: 'f2c-binary', version: '7', platformspecific: true },
          FASTR_RECOMMENDED_BINARY: { name: 'fastr-recommended-pkgs', version: '16', platformspecific: true },
        }
        else if (self.os == "darwin" && self.arch == "amd64") then {
          BLAS_LAPACK_DIR: { name: "fastr-403-blas-lapack-gcc", version: "8.3.0", platformspecific: true },
          F2C_BINARY: { name: 'f2c-binary', version: '7', platformspecific: true },
          FASTR_RECOMMENDED_BINARY: { name: 'fastr-recommended-pkgs', version: '16', platformspecific: true },
        } else {},
      catch_files+: if (self.os != "windows" && self.arch == "amd64") then [
        'GNUR_CONFIG_LOG = (?P<filename>.+\\.log)',
        'GNUR_MAKE_LOG = (?P<filename>.+\\.log)',
      ] else [],
    },

    svm:: {
      packages+: {
        cmake: "==3.22.2",
      },
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
      tags+: {opt_post_merge +: []},
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

  # OS specific file handling
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

  # Utils
  disable_proxies: {
    setup+: [
      ["unset", "HTTP_PROXY", "HTTPS_PROXY", "FTP_PROXY", "NO_PROXY", "http_proxy", "https_proxy", "ftp_proxy", "no_proxy"],
    ],
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

  # Bare platforms, just the bare minimum and nothing else. Also see Build GraalVM platforms below.
  bare:: {
    local ol7 = {
      docker+: {
        image: "buildslave_ol7",
        mount_modules: true,
      },
    },
    local ol8 = {
      docker+: {
        image: "buildslave_ol8",
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

    local linux   = { os:: "linux",   capabilities+: [self.os] },
    # Run darwin jobs on Big Sur or later by excluding all older versions
    local darwin  = { os:: "darwin",  capabilities+: [self.os, "!darwin_sierra", "!darwin_mojave", "!darwin_catalina"] },
    local windows = { os:: "windows", capabilities+: [self.os] },

    local amd64   = { arch:: "amd64",   capabilities+: [self.arch] },
    local aarch64 = { arch:: "aarch64", capabilities+: [self.arch] },

    local ol_distro = { os_distro:: "ol" },

    linux_amd64: self.linux_amd64_ol7,
    linux_amd64_ol7: linux + amd64 + ol7 + ol_distro,
    linux_amd64_ol8: linux + amd64 + ol8 + ol_distro,
    linux_amd64_ol9: linux + amd64 + ol9 + ol_distro,

    linux_aarch64: self.linux_aarch64_ol7,
    linux_aarch64_ol7: linux + aarch64 + ol7 + ol_distro,
    linux_aarch64_ol8: linux + aarch64 + ol8 + ol_distro,
    linux_aarch64_ol9: linux + aarch64 + ol9 + ol_distro,

    linux_amd64_ubuntu: linux + amd64 + ubuntu22 + { os_distro:: "ubuntu" },

    darwin_amd64: darwin + amd64,
    darwin_aarch64: darwin + aarch64,

    windows_amd64: windows + amd64,
    windows_server_2016_amd64: windows + amd64 + { capabilities+: ["windows_server_2016"] },
  },

  # Build GraalVM platforms, they include the dependencies listed in `local common =` just below.
  # They also include a devtoolset on Oracle Linux, to use the same system toolchain for all builds.
  local common = self.deps.mx + self.deps.common_catch_files + self.deps.common_env,

  local ol_devtoolset = {
    packages+: (if self.arch == "aarch64" then {
      "00:devtoolset": "==10", # GCC 10.2.1, make 4.2.1, binutils 2.35, valgrind 3.16.1
    } else {
      "00:devtoolset": "==11", # GCC 11.2, make 4.3, binutils 2.36, valgrind 3.17
    }),
  },

  linux_amd64: self.linux_amd64_ol7,
  linux_amd64_ol7: self.bare.linux_amd64_ol7 + common + ol_devtoolset,
  linux_amd64_ol8: self.bare.linux_amd64_ol8 + common + ol_devtoolset,
  linux_amd64_ol9: self.bare.linux_amd64_ol9 + common + ol_devtoolset,

  linux_aarch64: self.linux_aarch64_ol7,
  linux_aarch64_ol7: self.bare.linux_aarch64_ol7 + common + ol_devtoolset,
  linux_aarch64_ol8: self.bare.linux_aarch64_ol8 + common + ol_devtoolset,
  linux_aarch64_ol9: self.bare.linux_aarch64_ol9 + common + ol_devtoolset,

  linux_amd64_ubuntu: self.bare.linux_amd64_ubuntu + common,

  darwin_amd64: self.bare.darwin_amd64 + common,
  darwin_aarch64: self.bare.darwin_aarch64 + common,

  windows_amd64: self.bare.windows_amd64 + common,
  windows_server_2016_amd64: self.bare.windows_server_2016_amd64 + common,
}
