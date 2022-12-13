{
  local c = (import '../../../ci/ci_common/common.jsonnet'),
  local bc = (import '../../../ci/ci_common/bench-common.libsonnet'),
  local cc = (import '../ci_common/compiler-common.libsonnet'),
  local bench = (import '../ci_common/benchmark-suites.libsonnet'),
  local hw = bc.bench_hw,

  local hotspot_main_builds = [
    c.weekly + hw.x52 + jdk + cc.c2 + suite
  for jdk in cc.bench_jdks
  for suite in bench.groups.all_suites
  ],

  local hotspot_profiling_builds = std.flattenArrays([
    [
    c.weekly + hw.x52 + jdk + cc.c2 + cc.enable_profiling   + suite + { job_prefix:: "bench-profiling" },
    c.weekly + hw.x52 + jdk + cc.c2 + cc.footprint_tracking + suite + { job_prefix:: "bench-footprint" }
    ]
  for jdk in cc.bench_jdks
  for suite in bench.groups.profiled_suites
  ]),

  local weekly_forks_builds = std.flattenArrays([
    bc.generate_fork_builds(c.weekly + hw.x52 + jdk + cc.c2 + suite)
  for jdk in cc.bench_jdks
  for suite in bench.groups.weekly_forks_suites
  ]),

  local aarch64_builds = std.flattenArrays([
    [
    c.weekly + hw.a12c + jdk + cc.c2 + suite
    ]
  for jdk in cc.bench_jdks
  for suite in bench.groups.all_suites
  ]),

  local daily_economy_builds = [
      c.daily + hw.x52 + jdk + cc.libgraal + cc.economy_mode + suite
    for jdk in cc.bench_jdks
    for suite in bench.groups.main_suites
  ],

  local weekly_economy_builds = [
      c.weekly + hw.x52 + jdk + cc.libgraal + cc.economy_mode + suite
    for jdk in cc.bench_jdks
    for suite in bench.groups.all_but_main_suites
  ],

  local no_tiered_builds = std.flattenArrays([
    [
    c.weekly + hw.x52 + jdk + cc.c1                                             + suite,
    c.weekly + hw.x52 + jdk + cc.c2                         + cc.no_tiered_comp + suite,
    c.weekly + hw.x52 + jdk + cc.libgraal + cc.economy_mode + cc.no_tiered_comp + suite
    ]
  for jdk in cc.bench_jdks
  for suite in bench.groups.main_suites
  ]),

  local all_builds = hotspot_main_builds + hotspot_profiling_builds + weekly_forks_builds + aarch64_builds + daily_economy_builds + weekly_economy_builds + no_tiered_builds,
  local filtered_builds = [b for b in all_builds if b.is_jdk_supported(b.jdk_version)],
  // adds a "defined_in" field to all builds mentioning the location of this current file
  builds:: [{ defined_in: std.thisFile } + b for b in filtered_builds]
}
