{
  local c = (import '../../../ci/ci_common/common.jsonnet'),
  local bc = (import '../../../ci/ci_common/bench-common.libsonnet'),
  local cc = (import '../ci_common/compiler-common.libsonnet'),
  local bench = (import '../ci_common/benchmark-suites.libsonnet'),
  local hw = bc.bench_hw,

  local hotspot_amd64_builds = [
    c.weekly + hw.e3 + jdk + cc.c2 + suite
  for jdk in cc.bench_jdks
  for suite in bench.groups.all_suites
  ],

  local hotspot_aarch64_builds = [
    c.weekly + hw.a12c + cc.latest_jdk + cc.c2 + suite
  for suite in bench.groups.all_suites
  ],

  local hotspot_profiling_builds = std.flattenArrays([
    [
    c.monthly + hw.e3  + cc.latest_jdk + cc.c2 + suite + cc.enable_profiling   + { job_prefix:: "bench-compiler-profiling" },
    c.monthly + hw.a12c + cc.latest_jdk + cc.c2 + suite + cc.enable_profiling   + { job_prefix:: "bench-compiler-profiling" },
    c.monthly + hw.e3  + cc.latest_jdk + cc.c2 + suite + cc.footprint_tracking + { job_prefix:: "bench-compiler-footprint" },
    c.monthly + hw.a12c + cc.latest_jdk + cc.c2 + suite + cc.footprint_tracking + { job_prefix:: "bench-compiler-footprint" }
    ]
  for suite in bench.groups.profiled_suites
  ]),

  local weekly_forks_amd64_builds = std.flattenArrays([
    bc.generate_fork_builds(c.weekly + hw.e3 + jdk + cc.c2 + suite)
  for jdk in cc.bench_jdks
  for suite in bench.groups.weekly_forks_suites
  ]),

  local weekly_forks_aarch64_builds = std.flattenArrays([
    bc.generate_fork_builds(c.weekly + hw.a12c + cc.latest_jdk + cc.c2 + suite)
  for suite in bench.groups.weekly_forks_suites
  ]),

  local economy_builds = [
      c.weekly + hw.e3 + jdk + cc.libgraal + cc.economy_mode + suite
    for jdk in cc.bench_jdks
    for suite in bench.groups.main_suites
  ],
  local no_tiered_builds = std.flattenArrays([
    [
    c.monthly + hw.e3 + jdk + cc.c1                                             + suite,
    c.monthly + hw.e3 + jdk + cc.c2                         + cc.no_tiered_comp + suite,
    c.monthly + hw.e3 + jdk + cc.libgraal + cc.economy_mode + cc.no_tiered_comp + suite
    ]
  for jdk in cc.bench_jdks
  for suite in bench.groups.main_suites
  ]),

  local gc_variants_builds = std.flattenArrays([
    [
    c.monthly + hw.e3 + jdk + cc.c2                         + cc.zgc_mode + suite,
    ]
  for jdk in cc.bench_jdks
  for suite in bench.groups.main_suites
  ]) + std.flattenArrays([
    [
    c.monthly + hw.e3 + jdk + cc.c2                         + cc.serialgc_mode + bench.microservice_benchmarks,
    c.monthly + hw.e3 + jdk + cc.c2                         + cc.pargc_mode    + bench.microservice_benchmarks,
    c.monthly + hw.e3 + jdk + cc.c2                         + cc.zgc_mode      + bench.microservice_benchmarks,
    c.monthly + hw.e3 + jdk + cc.c2                         + cc.gen_zgc_mode  + bench.microservice_benchmarks,
    ]
  for jdk in cc.bench_jdks
  ]),
  local all_builds = hotspot_amd64_builds + hotspot_aarch64_builds + hotspot_profiling_builds +
    weekly_forks_amd64_builds + weekly_forks_aarch64_builds + economy_builds + no_tiered_builds + gc_variants_builds,
  local filtered_builds = [b for b in all_builds if b.is_jdk_supported(b.jdk_version) && b.is_arch_supported(b.arch)],

  // adds a "defined_in" field to all builds mentioning the location of this current file
  builds:: [{ defined_in: std.thisFile } + b for b in filtered_builds]
}
