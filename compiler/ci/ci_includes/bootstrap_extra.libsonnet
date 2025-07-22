# Bootstrap tasks specific to Graal CE
{
  local c = import '../../../ci/ci_common/common.jsonnet',
  local g = import '../ci_common/gate.jsonnet',

  # See definition of `gate_jobs` local variable in ../ci_common/gate.jsonnet
  local gate_jobs = {
    "compiler-bootstrap-labsjdk-latest-linux-amd64": {},
    "compiler-bootstrap_economy-labsjdk-latest-linux-amd64": {},
  },
  local gates = g.as_gates(gate_jobs),
  local dailies = g.as_dailies(gate_jobs),

  # Builds run on only on linux-amd64-jdk21
  local linux_amd64_builds = [g.make_build(jdk, "linux-amd64", task,
                              gates_manifest=gates,
                              dailies_manifest=dailies).build
    for jdk in [g.jdk_latest]
    for task in ["bootstrap", "bootstrap_economy"]
  ],

  # Complete set of builds defined in this file
  local all_builds = linux_amd64_builds,

  builds: if g.check_manifest(gates, all_builds, std.thisFile, "gates").result &&
             g.check_manifest(dailies, all_builds, std.thisFile, "dailies").result
          then all_builds
}
