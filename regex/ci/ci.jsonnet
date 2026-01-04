{
  local utils = (import '../../ci/ci_common/common-utils.libsonnet'),
  local common = import '../../ci/ci_common/common.jsonnet',
  local galahad = import "../../ci/ci_common/galahad-common.libsonnet",
  
  local regex_common = {
    setup+: [
      ["cd", "./regex"],
    ],
    timelimit: "30:00",
  },

  local regex_gate = regex_common + common.deps.eclipse + common.deps.jdt + common.deps.spotbugs + {
    name: 'gate-regex-' + self.jdk_name,
    run: [["mx", "--strict-compliance", "gate", "--strict-mode"]],
  },

  local regex_gate_lite = regex_common + galahad.exclude + {
    name: 'weekly-regex-mac-lite-' + self.jdk_name,
    run: [
      ["mx", "build"],
      ["mx", "unittest", "--verbose", "com.oracle.truffle.regex"],
    ],
    notify_groups:: ["regex"],
  },

  local regex_downstream_js = regex_common + {
    name: 'gate-regex-downstream-js-' + self.jdk_name,
    run: [
      # checkout graal-js and js-tests suites at the imported (downstream-branch) revisions.
      ["mx", "-p", "../vm", "--dynamicimports", "/graal-js", "sforceimports"],
      ["git", "clone", ["mx", "urlrewrite", "https://github.com/graalvm/js-tests.git"], "../../js-tests"],
      ["mx", "-p", "../vm", "--dynamicimports", "/graal-js,js-tests", "checkout-downstream", "graal-js", "js-tests"],
      # run downstream gate from js-tests suite.
      ["cd", "../../js-tests"],
      ["mx", "sversions"],
      ["mx", "gate", "--no-warning-as-error", "--all-suites", "--tags", "build,Test262-default,TestV8-default,regex"],
    ],
  },

  local regex_coverage = regex_common + {
    name: 'weekly-regex-coverage-' + self.jdk_name,
    run: [
      ['mx', 'gate', '--tags', 'build,coverage', '--jacoco-omit-excluded', '--jacoco-relativize-paths', '--jacoco-omit-src-gen', '--jacoco-format', 'lcov', '--jacocout', 'coverage']
    ],
    teardown+: [
      ['mx', 'sversions', '--print-repositories', '--json', '|', 'coverage-uploader.py', '--associated-repos', '-'],
    ],
    targets: ["weekly"],
    notify_emails: ["josef.haider@oracle.com"],
  },


  local _builds = [utils.add_gate_predicate(b, ["sdk", "truffle", "regex", "compiler", "vm", "substratevm"]) for b in std.flattenArrays([
      [
        common.linux_amd64    + jdk + common.tier1  + regex_gate,
        common.linux_amd64    + jdk + common.tier3  + regex_downstream_js,
        common.darwin_aarch64 + jdk + common.weekly + regex_gate_lite,
      ] for jdk in [
        common.labsjdkLatest,
      ]
    ]) +
    [common.linux_amd64 + common.labsjdk21 + regex_coverage]
  ],

  builds: utils.add_defined_in(_builds, std.thisFile),
}
