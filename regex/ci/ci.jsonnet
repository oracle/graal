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

  local regex_gate_jdkLatest = regex_common + common.deps.eclipse + common.deps.jdt + {
    name: 'gate-regex-jdk' + self.jdk_version,
    run: [
      ["mx", "build"],
      ["mx", "unittest", "com.oracle.truffle.regex"],
    ],
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
      # checkout graal-js and js-tests suites at the imported (downstream-branch) revisions.
      ["mx", "-p", "../vm", "--dynamicimports", "/graal-js", "sforceimports"],
      ["git", "clone", ["mx", "urlrewrite", "https://github.com/graalvm/js-tests.git"], "../../js-tests"],
      ["mx", "-p", "../vm", "--dynamicimports", "/graal-js,js-tests", "checkout-downstream", "graal-js", "js-tests"],
      # run downstream gate from js-tests suite.
      ["mx", "-p", "../../js-tests", "sversions"],
      ["mx", "-p", "../../js-tests", "gate", "--no-warning-as-error", "--all-suites", "--tags", "build,Test262-default,TestV8-default,regex"],
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
  ]) + [
      common.linux_amd64  + common.labsjdkLatest + regex_gate_jdkLatest,
  ],
}
