# File is formatted with
# `jsonnetfmt --indent 2 --max-blank-lines 2 --sort-imports --string-style d --comment-style h -i ci.jsonnet`
local sc = (import "ci_common/sulong-common.jsonnet");
{
  local common = import "../../ci/ci_common/common.jsonnet",

  local linux_amd64 = common.linux_amd64,

  local basicTags = "build,sulongBasic,nwcc,llvm",
  local basicTagsToolchain = "build,sulongBasic,nwcc,llvm,toolchain",
  local basicTagsNoNWCC= "build,sulongBasic,llvm",

  sulong:: {
    suite:: "sulong",
    setup+: [
      ["cd", "./sulong"],
    ],
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

  regular_builds:: [
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.style + { name: "gate-sulong-style-fullbuild-jdk17-linux-amd64" },
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.gateTags("build,sulongMisc,parser") + $.sulong_test_toolchain + { name: "gate-sulong-misc-parser-jdk17-linux-amd64" },
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.gateTags("build,gcc_c") + { name: "gate-sulong-gcc_c-jdk17-linux-amd64", timelimit: "45:00" },
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.gateTags("build,gcc_cpp") + { name: "gate-sulong-gcc_cpp-jdk17-linux-amd64", timelimit: "45:00" },
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.gateTags("build,gcc_fortran") + { name: "gate-sulong-gcc_fortran-jdk17-linux-amd64" },

    sc.weekly + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvm4 + sc.requireGMP + sc.requireGCC + sc.gateTags(basicTags) + { name: "weekly-sulong-basic-nwcc-llvm-v40-jdk17-linux-amd64" },
    sc.weekly + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvm6 + sc.requireGMP + sc.requireGCC + sc.gateTags(basicTags) + { name: "weekly-sulong-basic-nwcc-llvm-v60-jdk17-linux-amd64" },
    sc.weekly + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvm8 + sc.requireGMP + sc.requireGCC + sc.gateTags(basicTags) + { name: "weekly-sulong-basic-nwcc-llvm-v80-jdk17-linux-amd64" },

    sc.weekly + $.sulong + sc.labsjdk_ce_17 + sc.darwin_amd64 + sc.llvm4 + sc.gateTags(basicTags) + { name: "weekly-sulong-basic-nwcc-llvm-v40-jdk17-darwin-amd64", timelimit: "0:45:00" },
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.darwin_amd64 + sc.llvmBundled + sc.gateTags(basicTagsToolchain) + { name: "gate-sulong-basic-nwcc-llvm-toolchain-jdk17-darwin-amd64", timelimit: "0:45:00" },

    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.gateTags(basicTagsToolchain) + { name: "gate-sulong-basic-nwcc-llvm-toolchain-jdk17-linux-amd64" },
    sc.gate + $.sulong + sc.labsjdk_ce_19 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.gateTags(basicTagsToolchain) + { name: "gate-sulong-basic-nwcc-llvm-toolchain-jdk19-linux-amd64" },

    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.linux_aarch64 + sc.llvmBundled + sc.requireGMP + sc.gateTags(basicTagsNoNWCC) + { name: "gate-sulong-basic-llvm-jdk17-linux-aarch64", timelimit: "30:00" },

    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.darwin_aarch64 + sc.llvmBundled + sc.requireGMP + sc.gateTags(basicTagsNoNWCC) + { name: "gate-sulong-basic-llvm-jdk17-darwin-aarch64", timelimit: "30:00" },

    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.windows_amd64 + sc.llvmBundled + sc.gateTags("build,sulongStandalone,interop") + { name: "gate-sulong-standalone-interop-jdk17-windows-amd64", timelimit: "30:00" },
    sc.gate + $.sulong + sc.labsjdk_ce_17 + sc.windows_amd64 + sc.llvmBundled + sc.gateTags("build,nwcc,llvm,toolchain") + { name: "gate-sulong-nwcc-llvm-toolchain-jdk17-windows-amd64" },
  ],

  coverage_builds::
    sc.mapPrototypePlatformName([sc.weekly + $.sulong + sc.coverage($.regular_builds)],
    [
      [sc.linux_amd64,    [sc.labsjdk_ce_17]],
      [sc.darwin_amd64,   [sc.labsjdk_ce_17]],
      [sc.windows_amd64,  [sc.labsjdk_ce_17]],
      [sc.linux_aarch64,  [sc.labsjdk_ce_17]],
      [sc.darwin_aarch64, [sc.labsjdk_ce_17]],
    ],
    [
      { name: "weekly-sulong-coverage-jdk17-linux-amd64",    timelimit: "1:00:00" },
      { name: "weekly-sulong-coverage-jdk17-darwin-amd64",   timelimit: "1:00:00" },
      { name: "weekly-sulong-coverage-jdk17-windows-amd64",  timelimit: "1:00:00" },
      { name: "weekly-sulong-coverage-jdk17-linux-aarch64",  timelimit: "1:00:00" },
      { name: "weekly-sulong-coverage-jdk17-darwin-aarch64", timelimit: "1:00:00" },
    ]),

  builds: [ sc.defBuild(b) for b in self.regular_builds + self.coverage_builds ],
}
