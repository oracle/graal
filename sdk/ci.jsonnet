{
  local common = import '../common.jsonnet',

  local sdk_gate = {
    name: 'gate-sdk-oraclejdk' + self.jdk_version + '-' + self.os + '-' + self.arch,
    setup: [
      ["cd", "./sdk"],
    ],
    run: [
      ["mx", "gate"]
    ],
    targets: ["gate"],
    timelimit: "30:00",
  },

  builds: [
    common["linux-amd64"]  + common.oraclejdk11 + sdk_gate + common.eclipse + common.jdt,
    common["linux-amd64"]  + common.oraclejdk17 + sdk_gate + common.eclipse + common.jdt,
    common["darwin-amd64"] + common.oraclejdk11 + sdk_gate,
    common["darwin-amd64"] + common.oraclejdk17 + sdk_gate,
  ]
}
