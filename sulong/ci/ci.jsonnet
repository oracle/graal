# File is formatted with
# `jsonnetfmt --indent 2 --max-blank-lines 2 --sort-imports --string-style d --comment-style h -i ci.jsonnet`
local sc = (import "ci_common/sulong-common.jsonnet");
{
  local common = import "../../ci/ci_common/common.jsonnet",
  local utils = import '../../ci/ci_common/common-utils.libsonnet',

  local linux_amd64 = common.linux_amd64,

  local basicTags = "build,sulongBasic,nwcc,llvm",
  local basicTagsNoNWCC= "build,sulongBasic,llvm",

  sulong:: {
    suite:: "sulong",
    extra_mx_args+:: [ "--dynamicimport", "/compiler" ],
    setup+: [
      ["cd", "./sulong"],
    ],
  },

  gate(standalone=false, style=false):: sc.gate + {
    setup+: [
      ['apply-predicates', '--delete-excluded', '--process-hidden', '--pattern-root', '..'] # we are in the sulong directory
        + (if std.objectHasAll(self.guard, 'excludes') then ['--exclude=' + e for e in  self.guard.excludes] else [])
        + ['--include=' + e for e in  self.guard.includes]
    ],
    guard+: {
      includes+: [
        # sulong and its dependencies
        "<graal>/.git/**",
        "<graal>/sdk/**",
        "<graal>/truffle/**",
        "<graal>/sulong/**",
        # the compiler and its dependencies
        "<graal>/compiler/**",
        "<graal>/regex/**",
        "<graal>/java-benchmarks/**",
        "<graal>/common.json",
      ] + (if standalone then [
        # substratevm and its dependencies
        "<graal>/substratevm/**",
        # vm and its dependencies
        "<graal>/vm/**",
      ] else []) + (if style then [
        "<graal>/.clang-format",
        "<graal>/pyproject.toml",
      ] else []),
    },
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
    $.sulong + $.gate(style=true) + sc.labsjdkLatest + sc.linux_amd64 + sc.style + { name: "gate-sulong-style-fullbuild-jdk-latest-linux-amd64" },
    $.sulong + $.gate(standalone=true) + sc.labsjdkLatest + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.gateTags("build,sulongMisc,parser") + $.sulong_test_toolchain + { name: "gate-sulong-misc-parser-jdk-latest-linux-amd64" },
    $.sulong + $.gate() + sc.labsjdkLatest + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.gateTags("build,gcc_c") + { name: "gate-sulong-gcc_c-jdk-latest-linux-amd64", timelimit: "45:00" },
    $.sulong + $.gate() + sc.labsjdkLatest + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.gateTags("build,gcc_cpp") + { name: "gate-sulong-gcc_cpp-jdk-latest-linux-amd64", timelimit: "45:00" },
    $.sulong + $.gate() + sc.labsjdkLatest + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.gateTags("build,gcc_fortran") + { name: "gate-sulong-gcc_fortran-jdk-latest-linux-amd64" },

    $.sulong + $.gate() + sc.labsjdkLatest + sc.darwin_amd64 + sc.llvmBundled + sc.gateTags(basicTags) + { name: "gate-sulong-basic-nwcc-llvm-jdk-latest-darwin-amd64", timelimit: "0:45:00", capabilities+: ["ram16gb"] },

    $.sulong + $.gate() + sc.labsjdkLatest + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.gateTags(basicTags) + { name: "gate-sulong-basic-nwcc-llvm-jdk-latest-linux-amd64" },
    $.sulong + $.gate() + sc.labsjdk21 + sc.linux_amd64 + sc.llvmBundled + sc.requireGMP + sc.requireGCC + sc.gateTags(basicTags) + { name: "gate-sulong-basic-nwcc-llvm-jdk21-linux-amd64" },

    $.sulong + $.gate() + sc.labsjdkLatest + sc.linux_aarch64 + sc.llvmBundled + sc.requireGMP + sc.gateTags(basicTagsNoNWCC) + { name: "gate-sulong-basic-llvm-jdk-latest-linux-aarch64", timelimit: "30:00" },

    $.sulong + $.gate() + sc.labsjdkLatest + sc.darwin_aarch64 + sc.llvmBundled + sc.requireGMP + sc.gateTags(basicTagsNoNWCC) + { name: "gate-sulong-basic-llvm-jdk-latest-darwin-aarch64", timelimit: "30:00" },

    $.sulong + $.gate() + sc.labsjdkLatest + sc.windows_amd64 + sc.llvmBundled + sc.gateTags("build,sulongStandalone,interop") + { name: "gate-sulong-standalone-interop-jdk-latest-windows-amd64", timelimit: "1:00:00" },
    $.sulong + $.gate() + sc.labsjdkLatest + sc.windows_amd64 + sc.llvmBundled + sc.gateTags("build,nwcc,llvm") + { name: "gate-sulong-nwcc-llvm-jdk-latest-windows-amd64" },
    $.sulong + $.gate() + sc.labsjdkLatest + sc.windows_amd64 + sc.llvmBundled + sc.requireGMP + sc.gateTags("build,gcc_c") + { name: "gate-sulong-gcc_c-jdk-latest-windows-amd64", timelimit: "45:00" },
    $.sulong + $.gate() + sc.labsjdkLatest + sc.windows_amd64 + sc.llvmBundled + sc.requireGMP + sc.gateTags("build,gcc_cpp") + { name: "gate-sulong-gcc_cpp-jdk-latest-windows-amd64", timelimit: "45:00" },
  ],

  standalone_builds::
    sc.mapPrototypePlatformName(
    [
        $.sulong + $.gate(standalone=true) + sc.gateTags("standalone") {
          job:: "test-ce-standalones-jvm",
          extra_mx_args+:: ["--env", "ce-llvm-standalones", "--use-llvm-standalone=jvm"],
        },
        $.sulong + $.gate(standalone=true) + sc.gateTags("standalone") {
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
      { name: "gate-sulong-test-ce-standalones-jvm-jdk-latest-linux-amd64",    timelimit: "1:00:00" },
      { name: "daily-sulong-test-ce-standalones-jvm-jdk-latest-darwin-amd64",  timelimit: "1:00:00", targets: [] } + sc.daily,
      { name: "daily-sulong-test-ce-standalones-jvm-jdk-latest-windows-amd64",  timelimit: "1:00:00", targets: [] } + sc.daily /* GR-50165 */,
      { name: "gate-sulong-test-ce-standalones-jvm-jdk-latest-linux-aarch64",  timelimit: "1:00:00" },
      { name: "gate-sulong-test-ce-standalones-jvm-jdk-latest-darwin-aarch64", timelimit: "1:00:00" },
      { name: "gate-sulong-test-ce-standalones-native-jdk-latest-linux-amd64",    timelimit: "1:30:00" },
      { name: "daily-sulong-test-ce-standalones-native-jdk-latest-darwin-amd64",  timelimit: "1:00:00", targets: [] } + sc.daily,
      { name: "daily-sulong-test-ce-standalones-native-jdk-latest-windows-amd64",  timelimit: "1:00:00", targets: [] } + sc.daily /* GR-50165 */,
      { name: "gate-sulong-test-ce-standalones-native-jdk-latest-linux-aarch64",  timelimit: "1:00:00" },
      { name: "gate-sulong-test-ce-standalones-native-jdk-latest-darwin-aarch64", timelimit: "1:00:00" },
    ]),

  coverage_builds::
    sc.mapPrototypePlatformName([sc.weekly + $.sulong + sc.coverage($.regular_builds)],
    [
      [sc.linux_amd64,    [sc.labsjdk21]],
      [sc.darwin_amd64,   [sc.labsjdk21]],
      [sc.windows_amd64,  [sc.labsjdk21]],
      [sc.linux_aarch64,  [sc.labsjdk21]],
      [sc.darwin_aarch64, [sc.labsjdk21]],
    ],
    [
      { name: "weekly-sulong-coverage-jdk21-linux-amd64",    timelimit: "2:00:00" },
      { name: "weekly-sulong-coverage-jdk21-darwin-amd64",   timelimit: "1:30:00" },
      { name: "weekly-sulong-coverage-jdk21-windows-amd64",  timelimit: "1:30:00" },
      { name: "weekly-sulong-coverage-jdk21-linux-aarch64",  timelimit: "1:30:00" },
      { name: "weekly-sulong-coverage-jdk21-darwin-aarch64", timelimit: "1:00:00" },
    ]),

  local _builds = [ sc.defBuild(b) for b in self.regular_builds + self.standalone_builds + self.coverage_builds ],

  builds: utils.add_defined_in(_builds, std.thisFile),
}
