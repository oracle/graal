{
  local common = import '../common.jsonnet',

  local examples_gate = {
    name: 'gate-examples-oraclejdk' + self.jdk_version + '-' + self.os + '-' + self.arch,
    setup: [
      ["cd", "./examples"],
    ],
    run: [
      ["mx", "gate"]
    ],
    targets: ["gate"],
    timelimit: "30:00",
  },

  builds: [
    common["linux-amd64"]  + common.oraclejdk11 + examples_gate + common.eclipse + common.jdt,
    common["linux-amd64"]  + common.oraclejdk17 + examples_gate + common.eclipse + common.jdt,
    common["darwin-amd64"] + common.oraclejdk11 + examples_gate,
    common["darwin-amd64"] + common.oraclejdk17 + examples_gate,
  ]
}
