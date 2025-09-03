# File is formatted with
# `jsonnetfmt --indent 2 --max-blank-lines 2 --sort-imports --string-style d --comment-style h -i ci.jsonnet`
local sc = (import "ci_common/sulong-common.jsonnet");
{
  local common = import "../../ci/ci_common/common.jsonnet",
  local utils = import '../../ci/ci_common/common-utils.libsonnet',

  local linux_amd64 = common.linux_amd64,

  local basicTags = "build,sulongBasic,nwcc,llvm",
  local basicTagsNoNWCC= "build,sulongBasic,llvm",

  local tier1 = common.frequencies.tier1,
  local tier2 = common.frequencies.tier2,
  local tier3 = common.frequencies.tier3,

  sulong:: {
    suite:: "sulong",
    extra_mx_args+:: if self._jdkIsGraalVM then [] else [ "--dynamicimport", "/compiler" ],
    setup+: [
      ["cd", "./sulong"],
    ],
  },

  common(standalone=false, style=false):: {
    setup+: [
      ['apply-predicates', '--delete-excluded', '--process-hidden', '--pattern-root', '..'] # we are in the sulong directory
        + (if std.objectHasAll(self.guard, 'excludes') then ['--exclude=' + e for e in  self.guard.excludes] else [])
        + ['--include=' + e for e in  self.guard.includes]
    ],
    guard+: {
      includes+: [
        # sulong and its dependencies
        "<graal>/.git/**",  # This ensure the .git directory is preserved in apply-predicates
        "<graal>/ci.jsonnet",
        "<graal>/ci/**",
        "<graal>/common.json",
        "<graal>/sdk/**",
        "<graal>/truffle/**",
        "<graal>/sulong/**",
        # the compiler and its dependencies
        "<graal>/compiler/**",
        "<graal>/regex/**",
        "<graal>/java-benchmarks/**",
      ] + (if standalone then [
        # tools suite (included in standalone)
        "<graal>/tools/**",
        # substratevm and its dependencies
        "<graal>/substratevm/**",
        "<graal>/espresso-shared/**",
        # vm and its dependencies
        "<graal>/vm/**",
      ] else []) + (if style then [
        "<graal>/.clang-format",
        "<graal>/pyproject.toml",
        # `mx checkcopyrights` doesn't work if `.gitignore` doesn't exist
        "<graal>/.gitignore",
      ] else []),
    },
  },

  sulong_test_toolchain:: {
    run+: [
      ["mx", "build", "--dependencies", "SULONG_TEST"],
      ["mx", "unittest", "--verbose", "-Dsulongtest.toolchainPathPattern=SULONG_BOOTSTRAP_TOOLCHAIN", "ToolchainAPITest"],
      ["mx", "--env", "ce-llvm-standalones", "build", "--dependencies", "SULONG_JVM_STANDALONE"],
      ["set-export", "SULONG_BOOTSTRAP_STANDALONE", ["mx", "--quiet", "--no-warning", "--env", "ce-llvm-standalones", "path", "--output", "SULONG_JVM_STANDALONE"]],
      ["mx", "unittest", "--verbose", "-Dsulongtest.toolchainPathPattern=SULONG_JVM_STANDALONE", "ToolchainAPITest"],
    ],
  },

  regular_builds:: [
    $.sulong + tier1 + $.common(style=true) + sc.labsjdkLatest + sc.linux_amd64 + sc.style + { name: "gate-sulong-style-jdk-latest-linux-amd64", timelimit: "30:00" },
    $.sulong + tier1 + $.common(style=true) + sc.labsjdkLatest + sc.linux_amd64 + sc.fullbuild + { name: "gate-sulong-fullbuild-jdk-latest-linux-amd64", timelimit: "30:00" },
    $.sulong + tier2 + $.common(standalone=true) + sc.labsjdkLatest + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.gateTags("build,sulongMisc,parser") + $.sulong_test_toolchain + { name: "gate-sulong-misc-parser-jdk-latest-linux-amd64", timelimit: "30:00" },
    $.sulong + tier2 + $.common() + sc.labsjdkLatest + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.gateTags("build,gcc_c") + { name: "gate-sulong-gcc_c-jdk-latest-linux-amd64", timelimit: "45:00" },
    $.sulong + tier2 + $.common() + sc.labsjdkLatest + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.gateTags("build,gcc_cpp") + { name: "gate-sulong-gcc_cpp-jdk-latest-linux-amd64", timelimit: "45:00" },

    $.sulong + sc.daily + $.common() + sc.labsjdkLatest + sc.darwin_amd64 + sc.llvmBundled + sc.gateTags(basicTags) + { name: "daily-sulong-basic-nwcc-llvm-jdk-latest-darwin-amd64", timelimit: "0:45:00", capabilities+: ["ram16gb"] },

    $.sulong + tier2 + $.common() + sc.labsjdkLatest + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.gateTags(basicTags) + { name: "gate-sulong-basic-nwcc-llvm-jdk-latest-linux-amd64", timelimit: "30:00" },

    $.sulong + tier3 + $.common() + sc.labsjdkLatest + sc.linux_aarch64 + sc.llvmBundled + sc.requireGMP + sc.gateTags(basicTagsNoNWCC) + { name: "gate-sulong-basic-llvm-jdk-latest-linux-aarch64", timelimit: "30:00" },

    $.sulong + tier3 + $.common() + sc.labsjdkLatest + sc.darwin_aarch64 + sc.llvmBundled + sc.requireGMP + sc.gateTags(basicTagsNoNWCC) + { name: "gate-sulong-basic-llvm-jdk-latest-darwin-aarch64", timelimit: "30:00" },

    $.sulong + sc.post_merge + $.common() + sc.labsjdkLatest + sc.windows_amd64 + sc.llvmBundled + sc.gateTags("build,sulongStandalone,interop") + { name: "gate-sulong-standalone-interop-jdk-latest-windows-amd64", timelimit: "1:00:00" },
    $.sulong + tier3 + $.common() + sc.labsjdkLatest + sc.windows_amd64 + sc.llvmBundled + sc.gateTags("build,nwcc,llvm") + { name: "gate-sulong-nwcc-llvm-jdk-latest-windows-amd64", timelimit: "30:00" },
    $.sulong + sc.post_merge + $.common() + sc.labsjdkLatest + sc.windows_amd64 + sc.llvmBundled + sc.requireGMP + sc.gateTags("build,gcc_c") + { name: "gate-sulong-gcc_c-jdk-latest-windows-amd64", timelimit: "45:00" },
    $.sulong + sc.post_merge + $.common() + sc.labsjdkLatest + sc.windows_amd64 + sc.llvmBundled + sc.requireGMP + sc.gateTags("build,gcc_cpp") + { name: "gate-sulong-gcc_cpp-jdk-latest-windows-amd64", timelimit: "45:00" },
  ],

  standalone_builds::
    sc.mapPrototypePlatformName(
    [
        $.sulong + $.common(standalone=true) + sc.gateTags("standalone") {
          job:: "test-ce-standalones-jvm",
          extra_mx_args+:: ["--env", "ce-llvm-standalones", "--use-llvm-standalone=jvm"],
        },
        $.sulong + $.common(standalone=true) + sc.gateTags("standalone") {
          job:: "test-ce-standalones-native",
          extra_mx_args+:: ["--env", "ce-llvm-standalones", "--use-llvm-standalone=native"],
        },
    ],
    [
      [sc.linux_amd64,    [sc.labsjdkLatest]],
      [sc.darwin_amd64,   [sc.labsjdkLatest]],
      [sc.windows_amd64 + { capabilities+: ["windows_server_2016"] /* work around native-image bug GR-48515 */ },  [sc.labsjdkLatest]],
      [sc.linux_aarch64,  [sc.labsjdkLatest]],
      [sc.darwin_aarch64, [sc.labsjdkLatest]],
    ],
    [
      tier2 + { name: "gate-sulong-test-ce-standalones-jvm-jdk-latest-linux-amd64",    timelimit: "1:00:00" },
      sc.daily + { name: "daily-sulong-test-ce-standalones-jvm-jdk-latest-darwin-amd64",  timelimit: "1:00:00", targets: [] },
      sc.daily + { name: "daily-sulong-test-ce-standalones-jvm-jdk-latest-windows-amd64",  timelimit: "1:00:00", targets: [] } /* GR-50165 */,
      tier3 + { name: "gate-sulong-test-ce-standalones-jvm-jdk-latest-linux-aarch64",  timelimit: "1:00:00" },
      tier3 + { name: "gate-sulong-test-ce-standalones-jvm-jdk-latest-darwin-aarch64", timelimit: "1:00:00" },
      tier2 + { name: "gate-sulong-test-ce-standalones-native-jdk-latest-linux-amd64",    timelimit: "1:30:00" },
      sc.daily + { name: "daily-sulong-test-ce-standalones-native-jdk-latest-darwin-amd64",  timelimit: "1:00:00", targets: [] },
      sc.daily + { name: "daily-sulong-test-ce-standalones-native-jdk-latest-windows-amd64",  timelimit: "1:00:00", targets: [] } /* GR-50165 */,
      tier3 + { name: "gate-sulong-test-ce-standalones-native-jdk-latest-linux-aarch64",  timelimit: "1:00:00" },
      tier3 + { name: "gate-sulong-test-ce-standalones-native-jdk-latest-darwin-aarch64", timelimit: "1:00:00" },
    ]),

  coverage_builds::
    sc.mapPrototypePlatformName([sc.weekly + $.sulong + sc.coverage($.regular_builds)],
    [
      [sc.linux_amd64,    [sc.graalvmee21]],
      [sc.darwin_amd64,   [sc.graalvmee21]],
      [sc.windows_amd64,  [sc.graalvmee21]],
      [sc.linux_aarch64,  [sc.graalvmee21]],
      [sc.darwin_aarch64, [sc.graalvmee21]],
    ],
    [
      { name: "weekly-sulong-coverage-jdk21-linux-amd64",    timelimit: "2:00:00" },
      { name: "weekly-sulong-coverage-jdk21-darwin-amd64",   timelimit: "1:30:00" },
      { name: "weekly-sulong-coverage-jdk21-windows-amd64",  timelimit: "2:30:00" },
      { name: "weekly-sulong-coverage-jdk21-linux-aarch64",  timelimit: "1:30:00" },
      { name: "weekly-sulong-coverage-jdk21-darwin-aarch64", timelimit: "1:00:00" },
    ]),

  local _builds = [ sc.defBuild(b) for b in self.regular_builds + self.standalone_builds + self.coverage_builds ],

  builds: utils.add_defined_in(_builds, std.thisFile),
}
