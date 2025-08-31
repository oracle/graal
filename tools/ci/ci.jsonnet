{
  local common = import '../../ci/ci_common/common.jsonnet',
  local utils = import '../../ci/ci_common/common-utils.libsonnet',
  local top_level_ci = utils.top_level_ci,

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

  local tools_gate = gate_guard + tools_common + common.deps.eclipse + common.deps.jdt + common.deps.spotbugs + {
    name: 'gate-tools-oracle' + self.jdk_name + '-' + self.os + '-' + self.arch,
    run: [["mx", "--strict-compliance", "gate", "--strict-mode"]],
    targets: [if (self.jdk_name == "jdk-latest") then "tier2" else "tier3"],
    guard+: {
        includes+: ["**.jsonnet"],
    },
    notify_groups:: ["tools"],
  },

  local tools_weekly = tools_common + {
    name: 'weekly-tools-oracle' + self.jdk_name + '-' + self.os + '-' + self.arch,
    run: [
      ["mx", "build"],
      ["mx", "unittest", "--verbose"],
      ["mx", "sigtest"],
    ],
    notify_groups:: ["tools"],
    targets: ["weekly"],
  },

  local tools_javadoc = tools_common + common_guard + {
    name: "gate-tools-javadoc-" + self.jdk_name,
    run: [
      ["mx", "build"],
      ["mx", "javadoc"],
    ],
    targets: ["tier1"],
    notify_groups:: ["tools"],
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

  local _builds = [
    common.linux_amd64   + common.oraclejdkLatest + tools_gate,
    common.linux_amd64   + common.oraclejdk21 + tools_gate,

    common.linux_amd64   + common.oraclejdkLatest + tools_javadoc,
    common.linux_amd64   + common.oraclejdk21 + tools_coverage_weekly,
    common.linux_aarch64 + common.labsjdkLatest   + tools_weekly,
    common.linux_aarch64 + common.labsjdk21   + tools_weekly,

    common.windows_amd64 + common.oraclejdkLatest + tools_weekly + common.deps.windows_devkit,
    common.windows_amd64 + common.oraclejdk21 + tools_weekly + common.deps.windows_devkit,

    common.darwin_amd64  + common.oraclejdkLatest + tools_weekly,
    common.darwin_amd64  + common.oraclejdk21 + tools_weekly,
  ],

  builds: utils.add_defined_in(_builds, std.thisFile),
}
