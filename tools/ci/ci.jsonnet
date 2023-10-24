{
  local common = import '../../ci/ci_common/common.jsonnet',
  local top_level_ci = (import '../../ci/ci_common/common-utils.libsonnet').top_level_ci,
  local devkits = common.devkits,

  local tools_common = {
    setup+: [
      ["cd", "./tools"],
    ],
    timelimit: "45:00",
  },

  local common_guard = {
    guard+: {
      includes+: ["<graal>/tools/**"] + top_level_ci,
    }
  },
  local gate_guard = common_guard + {
    guard+: {
      includes+: ["<graal>/sdk/**", "<graal>/truffle/**"]
    }
  },

  local tools_gate = gate_guard + tools_common + common.deps.eclipse + common.deps.jdt + {
    name: 'gate-tools-oraclejdk' + self.jdk_version + '-' + self.os + '-' + self.arch,
    run: [["mx", "--strict-compliance", "gate", "--strict-mode"]],
    targets: ["gate"],
    guard+: {
        includes+: ["**.jsonnet"],
    }
  },

  local tools_weekly = tools_common + {
    name: 'weekly-tools-oraclejdk' + self.jdk_version + '-' + self.os + '-' + self.arch,
    run: [
      ["mx", "build"],
      ["mx", "unittest", "--verbose"],
      ["mx", "sigtest"],
    ],
    notify_groups:: ["tools"],
    targets: ["weekly"],
  },

  local tools_javadoc = tools_common + common_guard + {
    name: "gate-tools-javadoc",
    run: [
      ["mx", "build"],
      ["mx", "javadoc"],
    ],
    targets: ["gate"]
  },

  local coverage_whitelisting = [
    "--jacoco-whitelist-package",
    "org.graalvm.tools",
    "--jacoco-whitelist-package",
    "com.oracle.truffle.tools"
  ],

  local tools_coverage_weekly = tools_common + common.deps.eclipse + common.deps.jdt + {
    name: "weekly-tools-coverage",
    run: [
      ["mx"] + coverage_whitelisting + [
        "--strict-compliance",
        "gate",
        "--strict-mode",
        "--jacoco-omit-excluded",
        "--jacoco-relativize-paths",
        "--jacoco-omit-src-gen",
        "--jacocout",
        "coverage",
        "--jacoco-format",
        "lcov",
      ],
    ],
    teardown+: [
      ["mx", "sversions", "--print-repositories", "--json", "|", "coverage-uploader.py", "--associated-repos", "-"],
    ],
    targets: ["weekly"],
    notify_groups:: ["tools"],
  },

  builds: [
    common.linux_amd64   + common.oraclejdk21 + tools_gate,
    common.linux_amd64   + common.oraclejdk17 + tools_gate,

    common.linux_amd64   + common.oraclejdk21 + tools_javadoc,
    common.linux_amd64   + common.oraclejdk17 + tools_coverage_weekly,
    common.linux_aarch64 + common.labsjdk21   + tools_weekly,
    common.linux_aarch64 + common.labsjdk17   + tools_weekly,

    common.windows_amd64 + common.oraclejdk21 + tools_weekly + devkits["windows-jdk21"],
    common.windows_amd64 + common.oraclejdk17 + tools_weekly + devkits["windows-jdk17"],

    common.darwin_amd64  + common.oraclejdk21 + tools_weekly,
    common.darwin_amd64  + common.oraclejdk17 + tools_weekly,
  ],
}
