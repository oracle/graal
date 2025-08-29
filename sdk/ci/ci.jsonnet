{
  local common = import '../../ci/ci_common/common.jsonnet',
  local utils = import '../../ci/ci_common/common-utils.libsonnet',

  local normalize_target_with_dash(t) = if std.startsWith(t, "post-merge") then "" # no prefix for 'post-merge'
    else if std.startsWith(t, "tier") then "gate-" # keep 'gate' prefix for now, and add dash
    else t + '-', # default for everything else, including dash

  local sdk_gate(target) = common.deps.spotbugs {
    name: normalize_target_with_dash(target) + 'sdk-oracle' + self.jdk_name + '-' + self.os + '-' + self.arch,
    setup: [
      ["cd", "./sdk"],
    ],
    run: [
      ["mx", "gate"]
    ],
    targets: [target],
    timelimit: "30:00",
    guard+: {
        includes+: ["<graal>/sdk/**", "**.jsonnet"] + utils.top_level_ci,
    },
    notify_groups: ["truffle"],
  },

  local _builds = [
    common.linux_amd64  + common.oraclejdkLatest + sdk_gate("tier3") + common.deps.eclipse + common.deps.jdt,
    common.linux_amd64  + common.oraclejdk21 + sdk_gate("post-merge") + common.deps.eclipse + common.deps.jdt,
    common.darwin_aarch64 + common.oraclejdkLatest + sdk_gate("tier3"),
    common.darwin_aarch64 + common.oraclejdk21 + sdk_gate("post-merge"),
    common.darwin_amd64 + common.oraclejdkLatest + sdk_gate("daily"),
    common.darwin_amd64 + common.oraclejdk21 + sdk_gate("daily"),
  ],

  builds: utils.add_defined_in(_builds, std.thisFile),
}
