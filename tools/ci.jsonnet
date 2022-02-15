{
  local common = import '../common.jsonnet',
  local devkits = (import "../common.json").devkits,

  local tools_common = {
    setup+: [
      ["cd", "./tools"],
    ],
    timelimit: "30:00",
  },

  local tools_gate = tools_common + common.eclipse + common.jdt + {
    name: 'gate-tools-oraclejdk' + self.jdk_version + '-' + self.os + '-' + self.arch,
    run: [["mx", "--strict-compliance", "gate", "--strict-mode"]],
    targets: ["gate"],
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

  local tools_javadoc = tools_common + {
    name: "gate-tools-javadoc",
    run: [
      ["mx", "build"],
      ["mx", "javadoc"],
    ],
    targets: ["gate"],
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
        "--jacocout", 
        "html",
      ],
      ["mx"] + coverage_whitelisting + ["coverage-upload"],
    ],
    targets: ["weekly"],
  },

  local tools_unittest = {
    environment+: {
        "MX_TEST_RESULTS_PATTERN": "es-XXX.json",
        "MX_TEST_RESULT_TAGS": "tools"
    }
  },

  builds: [
    common.linux_amd64   + common.oraclejdk11 + tools_gate + tools_unittest,
    common.linux_amd64   + common.oraclejdk17 + tools_gate + tools_unittest,

    common.linux_amd64   + common.oraclejdk11 + tools_javadoc,
    common.linux_amd64   + common.oraclejdk17 + tools_coverage_weekly,
    common.linux_aarch64 + common.labsjdk17   + tools_gate_lite,

    common.windows_amd64 + common.oraclejdk11 + devkits["windows-jdk11"] + tools_gate_lite,
    common.windows_amd64 + common.oraclejdk17 + devkits["windows-jdk17"] + tools_gate_lite,

    common.darwin_amd64  + common.oraclejdk11 + tools_gate_lite,
    common.darwin_amd64  + common.oraclejdk17 + tools_gate_lite,
  ],
}