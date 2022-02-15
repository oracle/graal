# File is formatted with
# `jsonnetfmt --indent 2 --max-blank-lines 2 --sort-imports --string-style d --comment-style h -i ci.jsonnet`
local sc = (import "ci_common/sulong-common.jsonnet");
{
  local common = import "../common.jsonnet",

  local linux_amd64 = common.linux_amd64,

  local basicTags = "build,sulongBasic,nwcc,llvm",
  local basicTagsToolchain = "build,sulongBasic,nwcc,llvm,toolchain",
  local basicTagsNoNWCC= "build,sulongBasic,llvm",

  sulong:: {
    suite:: "sulong",
    environment+: {
      TRUFFLE_STRICT_OPTION_DEPRECATION: "true",
    },
    setup+: [
      ["cd", "./sulong"],
    ],
  },

  sulong_ruby_downstream_test:: {
    job:: "ruby-downstream",
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
    job: "generated-sources",
    run: [
      ["mx", "build", "--dependencies", "LLVM_TOOLCHAIN"],
      ["mx", "create-generated-sources"],
      ["git", "diff", "--exit-code", "."],
    ],
  },

  sulong_coverage:: sc.gateTags("build,sulongCoverage") + {
    job::"coverage",
    extra_mx_args +: ["--jacoco-whitelist-package", "com.oracle.truffle.llvm", "--jacoco-exclude-annotation", "@GeneratedBy"],
    extra_gate_args+: ["--jacocout", "html"],
    run+: [
      # $SONAR_HOST_URL might not be set [GR-28642],
      ["test", "-z", "$SONAR_HOST_URL", "||"] + self.mx + ["sonarqube-upload", "-Dsonar.host.url=$SONAR_HOST_URL", "-Dsonar.projectKey=com.oracle.graalvm.sulong", "-Dsonar.projectName=GraalVM - Sulong", "--exclude-generated"],
      self.mx + ["coverage-upload"],
    ],
    timelimit: "1:45:00",
  },

  sulong_test_toolchain:: {
    run+: [
      ["mx", "build", "--dependencies", "SULONG_TEST"],
      ["mx", "unittest", "--verbose", "-Dsulongtest.toolchainPathPattern=SULONG_BOOTSTRAP_TOOLCHAIN", "ToolchainAPITest"],
      ["mx", "--env", "toolchain-only", "build"],
      ["set-export", "SULONG_BOOTSTRAP_GRAALVM", ["mx", "--quiet", "--no-warning", "--env", "toolchain-only", "graalvm-home"]],
      ["mx", "unittest", "--verbose", "-Dsulongtest.toolchainPathPattern=GRAALVM_TOOLCHAIN_ONLY", "ToolchainAPITest"],
    ],
  },

  builds: [ sc.defBuild(b) for b in [
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + common.eclipse + sc.gateTags("style") + { name: "gate-sulong-style-jdk17-linux-amd64" },
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + common.eclipse + common.jdt + sc.gateTags("fullbuild") + { name: "gate-sulong-fullbuild-jdk17-linux-amd64" },
    sc.Description("Recreate generated sources (parsers, etc.) and check for modification") +
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + $.sulong_gate_generated_sources { name: "gate-sulong-generated-sources-jdk17-linux-amd64" },
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.gateTags("build,sulongMisc") + $.sulong_test_toolchain + { name: "gate-sulong-misc-jdk17-linux-amd64" },
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.gateTags("build,parser") + { name: "gate-sulong-parser-jdk17-linux-amd64" },
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.gateTags("build,gcc_c") + { name: "gate-sulong-gcc_c-jdk17-linux-amd64", timelimit: "45:00" },
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.gateTags("build,gcc_cpp") + { name: "gate-sulong-gcc_cpp-jdk17-linux-amd64", timelimit: "45:00" },
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.gateTags("build,gcc_fortran") + { name: "gate-sulong-gcc_fortran-jdk17-linux-amd64" },
    # No more testing on llvm 3.8 [GR-21735]
    # sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvm38 + sc.requireGMP + sc.requireGCC + sc.gateTags("build,sulongBasic,nwcc,llvm") + { name: "gate-sulong-basic_v38"},
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvm4 + sc.requireGMP + sc.requireGCC + sc.gateTags(basicTags) + { name: "gate-sulong-basic-nwcc-llvm-v40-jdk17-linux-amd64" },
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvm6 + sc.requireGMP + sc.requireGCC + sc.gateTags(basicTags) + { name: "gate-sulong-basic-nwcc-llvm-v60-jdk17-linux-amd64" },
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvm8 + sc.requireGMP + sc.requireGCC + sc.gateTags(basicTags) + { name: "gate-sulong-basic-nwcc-llvm-v80-jdk17-linux-amd64" },

    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.darwin_amd64 + sc.llvm4 + sc.gateTags(basicTags) + { name: "gate-sulong-basic-nwcc-llvm-v40-jdk17-darwin-amd64", timelimit: "0:45:00" },
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.darwin_amd64 + sc.llvmBundled + sc.gateTags(basicTagsToolchain) + { name: "gate-sulong-basic-nwcc-llvm-toolchain-jdk17-darwin-amd64", timelimit: "0:45:00" },

    sc.gate + $.sulong + sc.labsjdk_ce_11 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.gateTags(basicTagsToolchain) + { name: "gate-sulong-basic-nwcc-llvm-toolchain-jdk11-linux-amd64" },
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.gateTags(basicTagsToolchain) + { name: "gate-sulong-basic-nwcc-llvm-toolchain-jdk17-linux-amd64" },

    sc.Description("Run ruby downstream tests") +
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + $.sulong_ruby_downstream_test + { name: "gate-sulong-ruby-downstream-jdk17-linux-amd64" },

    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_aarch64 + sc.llvmBundled + sc.requireGMP + sc.gateTags(basicTagsNoNWCC) + { name: "gate-sulong-basic-llvm-jdk17-linux-aarch64", timelimit: "30:00" },

    sc.weekly + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + $.sulong_coverage { name: "weekly-sulong-coverage-jdk17-linux-amd64" },
  ]],
}
