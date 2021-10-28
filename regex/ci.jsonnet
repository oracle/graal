{
  local common = import '../common.jsonnet',

  local regex_common = {
    setup+: [
      ["cd", "./regex"],
    ],
    timelimit: "30:00",
  },

  local regex_gate = regex_common + common.eclipse + common.jdt + {
    name: 'gate-regex-oraclejdk' + self.jdk_version,
    run: [["mx", "--strict-compliance", "gate", "--strict-mode"]],
    targets: ["gate"],
  },

  local regex_gate_lite = regex_common + {
    name: 'gate-regex-mac-lite-oraclejdk' + self.jdk_version,
    run: [
      ["mx", "build"],
      ["mx", "unittest", "--verbose", "com.oracle.truffle.regex"],
    ],
    notify_groups:: ["regex"],
    targets: ["weekly"],
  },

  builds: std.flattenArrays([
    [
      common["linux-amd64"]  + jdk + regex_gate,
      common["darwin-amd64"] + jdk + regex_gate_lite,
    ] for jdk in [
      common.oraclejdk11,
      common.oraclejdk17,
    ]
  ]),
}