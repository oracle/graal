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
    common.linux_amd64  + common.oraclejdk11 + sdk_gate + common.eclipse + common.jdt,
    common.linux_amd64  + common.oraclejdk17 + sdk_gate + common.eclipse + common.jdt + common.mach5_target,
    common.darwin_amd64 + common.oraclejdk11 + sdk_gate,
    common.darwin_amd64 + common.oraclejdk17 + sdk_gate + common.mach5_target,
  ]
}
