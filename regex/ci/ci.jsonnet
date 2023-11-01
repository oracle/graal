{
  local common = import '../../ci/ci_common/common.jsonnet',

  local regex_common = {
    setup+: [
      ["cd", "./regex"],
    ],
    timelimit: "30:00",
  },

  local regex_gate = regex_common + common.deps.eclipse + common.deps.jdt + {
    name: 'gate-regex-jdk' + self.jdk_version,
    run: [["mx", "--strict-compliance", "gate", "--strict-mode"]],
    targets: ["gate"],
  },

  local regex_gate_lite = regex_common + {
    name: 'gate-regex-mac-lite-jdk' + self.jdk_version,
    run: [
      ["mx", "build"],
      ["mx", "unittest", "--verbose", "com.oracle.truffle.regex"],
    ],
    notify_groups:: ["regex"],
    targets: ["weekly"],
  },

  local regex_downstream_js = regex_common + {
    name: 'gate-regex-downstream-js-jdk' + self.jdk_version,
    run: [
      ["mx", "testdownstream", "--downstream-branch", "cpu/graal-vm/23.1", "-R", ['mx', 'urlrewrite', 'https://github.com/graalvm/js-tests.git'], "--mx-command", " gate --no-warning-as-error --all-suites --tags build,Test262-default,TestV8-default,regex"]
    ],
    targets: ["gate"],
  },

  builds: std.flattenArrays([
    [
      common.linux_amd64  + jdk + regex_gate,
      common.linux_amd64  + jdk + regex_downstream_js,
      common.darwin_amd64 + jdk + regex_gate_lite,
    ] for jdk in [
      common.labsjdk21,
    ]
  ]),
}
