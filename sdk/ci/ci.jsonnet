{
  local common = import '../../ci/ci_common/common.jsonnet',
  local utils = import '../../ci/ci_common/common-utils.libsonnet',

  local sdk_gate = {
    downloads+: {
      EXTRA_JAVA_HOMES: common.jdks_data['oraclejdk21'],
    },
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
    common.linux_amd64  + common.oraclejdkLatest + sdk_gate + common.deps.eclipse + common.deps.jdt,
    common.linux_amd64  + common.oraclejdk21 + sdk_gate + common.deps.eclipse + common.deps.jdt + common.mach5_target,
    common.darwin_amd64 + common.oraclejdkLatest + sdk_gate,
    common.darwin_amd64 + common.oraclejdk21 + sdk_gate + common.mach5_target,
  ]
}
