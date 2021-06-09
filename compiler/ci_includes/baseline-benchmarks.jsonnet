{
  local c = (import '../../common.jsonnet'),
  local bc = (import '../../bench-common.libsonnet'),
  local cc = (import '../ci_common/compiler-common.libsonnet'),
  local bench = (import '../ci_common/benchmark-suites.libsonnet'),
  local hw = bc.bench_hw,

  local jdk8 = c.oraclejdk8,
  local jdk11 = c.labsjdk11,

  local hotspot_main_builds = [
    c.on_demand + hw.x52 + jdk + cc.c2 + suite
  for jdk in [jdk8, jdk11]
  for suite in bench.groups.all_suites
  ],


  local hotspot_profiling_builds = std.flattenArrays([
    [
    c.weekly + hw.x52 + jdk + cc.c2 + cc.enable_profiling + suite + { job_prefix:: "bench-profiling" }
    ]
  for jdk in [jdk8, jdk11]
  for suite in bench.groups.profiled_suites
  ]),

  local weekly_forks_builds = std.flattenArrays([
    cc.generate_fork_builds(c.weekly + hw.x52 + jdk + cc.c2 + suite)
  for jdk in [jdk8, jdk11]
  for suite in bench.groups.weekly_forks_suites
  ]),

  local aarch64_builds = [
    c.weekly + hw.xgene3 + jdk11 + cc.c2 + suite
  for suite in bench.groups.main_suites
  ],

  local all_builds = hotspot_main_builds + hotspot_profiling_builds + weekly_forks_builds + aarch64_builds,
  // adds a "defined_in" field to all builds mentioning the location of this current file
  builds:: [{ defined_in: std.thisFile } + b for b in all_builds]
}