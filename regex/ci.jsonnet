{
  local common = import '../common.jsonnet',

  local regex_common = {
    setup+: [
      ["cd", "./regex"],
    ],
    timelimit: "30:00",
  },

  local regex_gate = regex_common + common["linux-amd64"] + common.eclipse + common.jdt + {
    name: 'gate-regex-oraclejdk' + self.jdk_version,
    run: [["mx", "--strict-compliance", "gate", "--strict-mode"]],
    targets: ["gate"],
  },

  local regex_gate_lite = regex_common + common["darwin-amd64"] + {
    name: 'gate-regex-mac-lite-oraclejdk' + self.jdk_version,
    run: [
      ["mx", "build"],
      ["mx", "unittest", "--verbose", "com.oracle.truffle.regex"],
    ],
    notify_groups:: ["regex"],
    targets: ["weekly"],
  },

  builds: [
    regex_gate      + common.oraclejdk8,
    regex_gate      + common.oraclejdk11,
    regex_gate_lite + common.oraclejdk8,
    regex_gate_lite + common.oraclejdk11,
  ],
}