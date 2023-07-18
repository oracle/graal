{
  local common = import '../../../ci/ci_common/common.jsonnet',
  local utils = import '../../../ci/ci_common/common-utils.libsonnet',
  local config = import '../../../ci/repo-configuration.libsonnet',

  local gate_stub_ports_listener = {
    name: 'daily-hotspot-stubports-listener-' + utils.prefixed_jdk(self.jdk_version),
    environment+: {
      "HOTSPOT_PORT_SYNC_CHECK" : "true"
    },
    setup+: [
      ["cd", "./" + config.compiler.compiler_suite],
    ],
    run+: [
      ["mx", "build"]
    ],
    timelimit : "10:00",
    notify_groups:: ["compiler_stubs"],
  },

  builds: [
    common.daily + common.linux_amd64 + common.labsjdk21 + gate_stub_ports_listener,
  ]
}
