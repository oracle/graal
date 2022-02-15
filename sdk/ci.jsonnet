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

  local sdk_unittest = {
    environment+: {
        "MX_TEST_RESULTS_PATTERN": "es-XXX.json",
        "MX_TEST_RESULT_TAGS": "sdk"
    }
  },

  builds: [
    common.linux_amd64  + common.oraclejdk11 + sdk_gate + common.eclipse + common.jdt + sdk_unittest,
    common.linux_amd64  + common.oraclejdk17 + sdk_gate + common.eclipse + common.jdt + sdk_unittest,
    common.darwin_amd64 + common.oraclejdk11 + sdk_gate + sdk_unittest,
    common.darwin_amd64 + common.oraclejdk17 + sdk_gate + sdk_unittest,
  ]
}
