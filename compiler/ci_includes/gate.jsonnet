{
  local common = (import '../../common.jsonnet'),
  local config = import '../../repo-configuration.libsonnet',
  local cc = (import '../ci_common/compiler-common.libsonnet'),
  local linux_amd64 = common["linux-amd64"],

  local gateMathStubsListener = cc.daily + linux_amd64 + common.oraclejdk8 + {
    name: 'daily-hotspot-mathstubs-listener',
    environment+: {
      "HOTSPOT_PORT_SYNC_CHECK" : "true"
    },
    setup+: [
      ["cd", "./" + config.compiler.compiler_suite],
    ],
    run+: [
      ["mx", "build"]
    ],
    notify_emails: [
      "yudi.zheng@oracle.com"
    ],
  },

  builds: [
    gateMathStubsListener,
  ]
}
