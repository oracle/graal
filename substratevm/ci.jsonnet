{
  local common = import "../common.jsonnet",

  local t(limit) = {timelimit: limit},

  local gate(name, tags) = common.disable_proxies + {
    name: "gate-svm-" + name + "-jdk" + self.jdk_version + "-" + self.os + "-" + self.arch,
    setup+: [
      ["cd", "./substratevm"],
      ["mx", "hsdis", "||", "true"],
    ],
    run+: [["mx", "--kill-with-sigquit", "--strict-compliance", "gate", "--strict-mode", "--tags", tags]],
    targets: ["gate"],
    timelimit: "45:00",
  },

  local clone_js_benchmarks = {
    setup+: [["git", "clone", "--depth", "1", ["mx", "urlrewrite", "https://github.com/graalvm/js-benchmarks.git"], "../../js-benchmarks"]],
  },

  local gdb(version) = {
    downloads+: {
      GDB: {name: "gdb", version: version, platformspecific: true},
    },
    environment+: {
      GDB_BIN: "$GDB/bin/gdb",
    },
  },

  local maven = {
    packages+: {
      maven: "==3.6.3",
    },
  },

  local jsonschema = {
    packages+: {
      "pip:jsonschema": "==4.6.1",
    },
  },

  local musl_toolchain = {
    downloads+: {
      "MUSL_TOOLCHAIN": {
        "name": "musl-toolchain",
        "version": "1.0",
        "platformspecific": true,
      },
    },
    environment+: {
      # Note that we must add the toolchain to the end of the PATH so that the system gcc still remains the first choice
      # for building the rest of GraalVM. The musl toolchain also provides a gcc executable that would shadow the system one
      # if it were added at the start of the PATH.
      PATH: "$PATH:$MUSL_TOOLCHAIN/bin",
    },
  },

  local mx_build_exploded = {
    environment+: {
      MX_BUILD_EXPLODED: "true", # test native-image MX_BUILD_EXPLODED compatibility
    },
  },

  local linux_amd64_jdk11 = common.linux_amd64   + common.labsjdk11,
  local linux_amd64_jdk17 = common.linux_amd64   + common.labsjdk17,
  local linux_amd64_jdk19 = common.linux_amd64   + common.labsjdk19,
  local darwin_jdk17      = common.darwin_amd64  + common.labsjdk17,
  local windows_jdk17     = common.windows_amd64 + common.labsjdk17 + common.devkits["windows-jdk17"],

  builds: [
    linux_amd64_jdk17 + gate("js", "build,js") + clone_js_benchmarks + t("35:00"),
    darwin_jdk17      + gate("js", "build,js") + clone_js_benchmarks,
    darwin_jdk17      + gate("js-quickbuild", "build,js_quickbuild") + clone_js_benchmarks,
    linux_amd64_jdk11 + gate("build-ce", "build,checkstubs,helloworld,test,nativeimagehelp,muslcbuild,debuginfotest") + maven + t("35:00") + musl_toolchain + gdb("10.2"),
    linux_amd64_jdk11 + gate("modules-basic", "build,hellomodule,test") + maven + t("30:00"),
    linux_amd64_jdk17 + gate("style-fullbuild", "style,fullbuild,helloworld,test,svmjunit,debuginfotest") + common.eclipse + common.jdt + maven + jsonschema + t("50:00") + mx_build_exploded + gdb("10.2"),
    linux_amd64_jdk19 + gate("basics", "build,helloworld,test,svmjunit") + t("55:00"),
    windows_jdk17     + gate("basics", "build,helloworld,test,svmjunit") + t("1:30:00"),
    windows_jdk17     + gate("basics-quickbuild", "build,helloworld_quickbuild,test_quickbuild,svmjunit_quickbuild") + t("1:30:00"),
  ],
}
