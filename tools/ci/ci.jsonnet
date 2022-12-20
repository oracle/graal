{
  local common_json = import '../../common.json',
  local common = import '../../ci/ci_common/common.jsonnet',
  local composable = (import '../../ci/ci_common/common-utils.libsonnet').composable,
  local top_level_ci = (import '../../ci/ci_common/common-utils.libsonnet').top_level_ci,
  local devkits = composable(common_json.devkits),

  local tools_common = composable(common_json.deps.common) + common.mx + {
    setup+: [
      ["cd", "./tools"],
    ],
    timelimit: "30:00",
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

  local tools_gate = gate_guard + tools_common + common.eclipse + common.jdt + {
    name: 'gate-tools-oraclejdk' + self.jdk_version + '-' + self.os + '-' + self.arch,
    run: [["mx", "--strict-compliance", "gate", "--strict-mode"]],
    targets: ["gate"],
    guard+: {
        includes+: ["**.jsonnet"],
    }
  },

  local tools_gate_lite = tools_common + {
    name: 'gate-tools-lite-oraclejdk' + self.jdk_version + '-' + self.os + '-' + self.arch,
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

  local tools_coverage_weekly = tools_common + common.eclipse + common.jdt + {
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
    common.linux_amd64   + common.oraclejdk20 + tools_gate,
    common.linux_amd64   + common.oraclejdk17 + tools_gate,

    common.linux_amd64   + common.oraclejdk20 + tools_javadoc,
    common.linux_amd64   + common.oraclejdk17 + tools_coverage_weekly,
    common.linux_aarch64 + common.labsjdk17   + tools_gate_lite,

    common.windows_amd64 + common.oraclejdk20 + tools_gate_lite + devkits["windows-jdk20"],
    common.windows_amd64 + common.oraclejdk17 + tools_gate_lite + devkits["windows-jdk17"],

    common.darwin_amd64  + common.oraclejdk20 + tools_gate_lite,
    common.darwin_amd64  + common.oraclejdk17 + tools_gate_lite,
  ],
}
