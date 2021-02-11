# File is formatted with
# `jsonnetfmt --indent 2 --max-blank-lines 2 --sort-imports --string-style d --comment-style h -i ci.jsonnet`
local sc = (import "ci_common/sulong-common.jsonnet");
{
  local common = import "../common.jsonnet",

  local linux_amd64 = common["linux-amd64"],

  local basicTags = "build,sulongBasic,nwcc,llvm",
  local basicTagsLLToolchain = "build,sulongBasic,sulongLL,nwcc,llvm,toolchain",
  local basicTagsNoNWCCNoDebugExpr = "build,sulong,sulongLL,interop,linker,debug,irdebug,bitcodeFormat,otherTests,llvm",

  sulong:: {
    environment+: {
      TRUFFLE_STRICT_OPTION_DEPRECATION: "true",
    },
    setup+: [
      ["cd", "./sulong"],
    ],
  },

  sulong_ruby_downstream_test:: {
    packages+: {
      ruby: "==2.6.3",
    },
    run: [
      [
        "mx",
        "testdownstream",
        "--repo",
        "https://github.com/graalvm/truffleruby.git",
        "--mx-command",
        "--dynamicimports /sulong ruby_testdownstream_sulong",
      ],
    ],
    timelimit: "45:00",
  },

  sulong_gate_generated_sources:: {
    run: [
      ["mx", "build", "--dependencies", "LLVM_TOOLCHAIN"],
      ["mx", "create-generated-sources"],
      ["git", "diff", "--exit-code", "."],
    ],
  },

  sulong_coverage_linux:: {
    run: [
      ["mx", "--jacoco-whitelist-package", "com.oracle.truffle.llvm", "--jacoco-exclude-annotation", "@GeneratedBy", "gate", "--tags", "build,sulongCoverage", "--jacocout", "html"],
      # $SONAR_HOST_URL might not be set [GR-28642],
      ["test", "-z", "$SONAR_HOST_URL", "||", "mx", "--jacoco-whitelist-package", "com.oracle.truffle.llvm", "--jacoco-exclude-annotation", "@GeneratedBy", "sonarqube-upload", "-Dsonar.host.url=$SONAR_HOST_URL", "-Dsonar.projectKey=com.oracle.graalvm.sulong", "-Dsonar.projectName=GraalVM - Sulong", "--exclude-generated"],
      ["mx", "--jacoco-whitelist-package", "com.oracle.truffle.llvm", "--jacoco-exclude-annotation", "@GeneratedBy", "coverage-upload"],
    ],
    timelimit: "1:45:00",
  },

  sulong_test_toolchain:: {
    run+: [
      ["mx", "build", "--dependencies", "SULONG_TEST"],
      ["mx", "unittest", "--verbose", "-Dsulongtest.toolchainPathPattern=SULONG_BOOTSTRAP_TOOLCHAIN", "ToolchainAPITest"],
      ["mx", "--env", "toolchain-only", "build"],
      ["set-export", "SULONG_BOOTSTRAP_GRAALVM", ["mx", "--env", "toolchain-only", "graalvm-home"]],
      ["mx", "unittest", "--verbose", "-Dsulongtest.toolchainPathPattern=GRAALVM_TOOLCHAIN_ONLY", "ToolchainAPITest"],
    ],
  },

  sulong_strict_native_image:: {
    run: [
      ["mx", "--dynamicimports", "/substratevm,/tools", "--native-images=lli", "--extra-image-builder-argument=-H:+TruffleCheckBlackListedMethods", "gate", "--tags", "build"],
    ],
  },

  builds: [
    sc.gate + $.sulong + sc.style + sc.jdk8 + sc.linux_amd64 + common.eclipse + sc.gateTags("style") + { name: "gate-sulong-style" },
    sc.gate + $.sulong + sc.style + sc.jdk8 + sc.linux_amd64 + common.eclipse + common.jdt + sc.gateTags("fullbuild") + { name: "gate-sulong-fullbuild" },
    sc.gate + $.sulong + sc.jdk8 + sc.linux_amd64 + $.sulong_gate_generated_sources { name: "gate-sulong-generated-sources" },
    sc.gate + $.sulong + sc.jdk8 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.gateTags("build,sulongMisc") + $.sulong_test_toolchain + { name: "gate-sulong-misc" },
    sc.gate + $.sulong + sc.jdk8 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.gateTags("build,parser") + { name: "gate-sulong-parser" },
    sc.gate + $.sulong + sc.jdk8 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.gateTags("build,gcc_c") + { name: "gate-sulong-gcc_c", timelimit: "45:00" },
    sc.gate + $.sulong + sc.jdk8 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.gateTags("build,gcc_cpp") + { name: "gate-sulong-gcc_cpp", timelimit: "45:00" },
    sc.gate + $.sulong + sc.jdk8 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.gateTags("build,gcc_fortran") + { name: "gate-sulong-gcc_fortran" },
    # No more testing on llvm 3.8 [GR-21735]
    # sc.gate + $.sulong + sc.jdk8 + sc.linux_amd64 + sc.llvm38 + sc.requireGMP + sc.requireGCC + sc.gateTags("build,sulongBasic,nwcc,llvm") + { name: "gate-sulong-basic_v38"},
    sc.gate + $.sulong + sc.jdk8 + sc.linux_amd64 + sc.llvm4 + sc.requireGMP + sc.requireGCC + sc.gateTags(basicTags) + { name: "gate-sulong-basic_v40" },
    sc.gate + $.sulong + sc.jdk8 + sc.linux_amd64 + sc.llvm6 + sc.requireGMP + sc.requireGCC + sc.gateTags(basicTags) + { name: "gate-sulong-basic_v60" },
    sc.gate + $.sulong + sc.jdk8 + sc.linux_amd64 + sc.llvm8 + sc.requireGMP + sc.requireGCC + sc.gateTags(basicTags) + { name: "gate-sulong-basic_v80" },
    sc.gate + $.sulong + sc.jdk8 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.gateTags(basicTagsLLToolchain) + { name: "gate-sulong-basic_bundled-llvm" },
    # FIXME: it is sufficient to only run basicTags
    sc.gate + $.sulong + sc.jdk8 + sc.darwin_amd64 + sc.llvm4 + sc.llvm4_darwin_fix + sc.gateTags(basicTags + ",toolchain") + { name: "gate-sulong-basic_mac" },
    sc.gate + $.sulong + sc.jdk8 + sc.darwin_amd64 + sc.llvmBundled + sc.llvmBundled_darwin_fix + sc.gateTags(basicTagsLLToolchain) + { name: "gate-sulong-basic_bundled-llvm_mac" },

    sc.gate + $.sulong + sc.jdk8 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + $.sulong_ruby_downstream_test + { name: "gate-sulong-ruby-downstream" },

    sc.gate + $.sulong + sc.labsjdk_ce_11 + sc.linux_aarch64 + sc.llvmBundled + sc.requireGMP + sc.gateTags(basicTagsNoNWCCNoDebugExpr) + { name: "gate-sulong_bundled-llvm-linux-aarch64", timelimit: "30:00" },
    sc.gate + $.sulong + sc.labsjdk_ce_11 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.gateTags("build") + $.sulong_test_toolchain + { name: "gate-sulong-build_bundled-llvm-linux-amd64-labsjdk-ce-11" },

    sc.gate + $.sulong + sc.jdk8 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + $.sulong_strict_native_image + { name: "gate-sulong-strict-native-image" },

    sc.weekly + $.sulong + sc.jdk8 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.sulong_weekly_notifications + $.sulong_coverage_linux { name: "weekly-sulong-coverage" },
  ],
}
