{
  local common = import '../../ci/ci_common/common.jsonnet',
  local utils = import '../../ci/ci_common/common-utils.libsonnet',

  local sdk_gate = common.deps.spotbugs {
    name: 'gate-sdk-oracle' + self.jdk_name + '-' + self.os + '-' + self.arch,
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
    common.linux_amd64  + common.oraclejdkLatest + sdk_gate + common.deps.eclipse + common.deps.jdt,
    common.linux_amd64  + common.oraclejdk21 + sdk_gate + common.deps.eclipse + common.deps.jdt + common.mach5_target,
    # JDK latest only works on MacOS Ventura (GR-49652)
    # common.darwin_amd64 + common.oraclejdkLatest + sdk_gate,
    common.darwin_aarch64 + common.oraclejdkLatest + sdk_gate,
    common.darwin_amd64 + common.oraclejdk21 + sdk_gate + common.mach5_target,
  ]
}
