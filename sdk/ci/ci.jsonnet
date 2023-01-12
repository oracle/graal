{
  local common = import '../../ci/ci_common/common.jsonnet',
  local utils = import '../../ci/ci_common/common-utils.libsonnet',

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
    guard: {
        includes: ["<graal>/sdk/**", "**.jsonnet"] + utils.top_level_ci,
    }
  },

  builds: [
    common.linux_amd64  + common.oraclejdk20 + sdk_gate + common.eclipse + common.jdt,
    common.linux_amd64  + common.oraclejdk17 + sdk_gate + common.eclipse + common.jdt + common.mach5_target,
    common.darwin_amd64 + common.oraclejdk20 + sdk_gate,
    common.darwin_amd64 + common.oraclejdk17 + sdk_gate + common.mach5_target,
  ]
}
