{
  local utils = (import '../../ci/ci_common/common-utils.libsonnet'),
  local graal_common = (import 'ci_common/gate.jsonnet'),
  local graal_benchmarks = (import 'ci_common/benchmark-builders.jsonnet'),
  local baseline_benchmarks = (import 'ci_includes/baseline-benchmarks.jsonnet'),

  builds: [utils.add_gate_predicate(b, ["sdk", "truffle", "compiler"]) for b in
    graal_common.builds +
    graal_benchmarks.builds +
    baseline_benchmarks.builds
  ]
}
