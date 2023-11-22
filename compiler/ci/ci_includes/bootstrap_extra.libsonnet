# Bootstrap tasks specific to Graal CE
{
  local c = import '../../../ci/ci_common/common.jsonnet',
  local g = import '../ci_common/gate.jsonnet',

  # See definition of `gates` local variable in ../ci_common/gate.jsonnet
  local gates = {
    "gate-compiler-bootstrap-labsjdk-latest-linux-amd64": g.many_cores + c.mach5_target,
    "gate-compiler-bootstrap_economy-labsjdk-latest-linux-amd64": g.many_cores + c.mach5_target,
  },

  # Builds run on only on linux-amd64-jdk21
  local linux_amd64_builds = [g.make_build(jdk, "linux-amd64", task, gates_manifest=gates).build
    for jdk in [g.jdk_latest]
    for task in ["bootstrap", "bootstrap_economy"]
  ],

  # Complete set of builds defined in this file
  local all_builds = linux_amd64_builds,

  builds: if g.check_manifest(gates, all_builds, std.thisFile, "gates").result then all_builds
}
