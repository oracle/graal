{
  local common = import "../common.jsonnet",

  local svm_common = {
    setup: [
      ["cd", "./substratevm"],
      ["mx", "hsdis", "||", "true"],
    ],
    targets: ["gate"],
    timelimit: "45:00",
  },

  local svm_cmd_gate = ["mx", "--kill-with-sigquit", "--strict-compliance", "gate", "--strict-mode", "--tags"],

  local svm_clone_js_benchmarks = ["git", "clone", "--depth", "1", ["mx", "urlrewrite", "https://github.com/graalvm/js-benchmarks.git"], "../../js-benchmarks"],

  local gate_svm_js = svm_common + {
    run: [
      svm_clone_js_benchmarks,
      svm_cmd_gate + ["build,js"],
    ],
  },

  local gate_svm_js_quickbuild = svm_common + {
    run: [
      svm_clone_js_benchmarks,
      svm_cmd_gate + ["build,js_quickbuild"],
    ],
  },

  local svm_unittest = {
    environment+: {
        "MX_TEST_RESULTS_PATTERN": "es-XXX.json",
        "MX_TEST_RESULT_TAGS": "native-image",
    },
  },

  local maven = {
    packages+: {
      maven: "==3.6.3",
    },
  },

  builds: [
    common.linux_amd64 + common.oraclejdk17 + gate_svm_js + {
      name: "gate-svm-js",
      timelimit: "35:00",
    },
    common.darwin_amd64 + common.oraclejdk17 + gate_svm_js {
      name: "gate-svm-darwin-js",
    },
    common.darwin_amd64 + common.oraclejdk17 + gate_svm_js_quickbuild {
      name: "gate-svm-darwin-js-quickbuild",
    },
    common.linux_amd64 + common.oraclejdk11 + svm_common + maven + svm_unittest + {
      name: "gate-svm-build-ce-11",
      timelimit: "30:00",
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
      run: [
        svm_cmd_gate + ["build,helloworld,test,nativeimagehelp,muslcbuild"],
      ],
    },
    common.linux_amd64 + common.oraclejdk11 + svm_common + maven + svm_unittest + {
      name: "gate-svm-modules-basic",
      timelimit: "30:00",
      run: [
        svm_cmd_gate + ["build,hellomodule,test"],
      ],
    },
    common.linux_amd64 + common.oraclejdk17 + svm_common + common.eclipse + common.jdt + maven + svm_unittest + {
      name: "gate-svm-style-fullbuild",
      timelimit: "45:00",
      environment+: {
        MX_BUILD_EXPLODED: "true", # test native-image MX_BUILD_EXPLODED compatibility
      },
      run: [
        svm_cmd_gate + ["style,fullbuild,helloworld,test,svmjunit"],
      ],
    },
    common.windows_amd64 + common.oraclejdk17 + common.devkits["windows-jdk17"] + svm_common + svm_unittest + {
      name: "gate-svm-windows-basics",
      timelimit: "1:30:00",
      run: [
        svm_cmd_gate + ["build,helloworld,test,svmjunit"],
      ],
    },
    common.windows_amd64 + common.oraclejdk17 + common.devkits["windows-jdk17"] + svm_common + svm_unittest + {
      name: "gate-svm-windows-basics-quickbuild",
      timelimit: "1:30:00",
      run: [
        svm_cmd_gate + ["build,helloworld_quickbuild,test_quickbuild,svmjunit_quickbuild"],
      ],
    },
  ],
}
