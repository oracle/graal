{
  local c = (import '../../../ci/ci_common/common.jsonnet'),
  local bc = (import '../../../ci/ci_common/bench-common.libsonnet'),
  local cc = (import 'compiler-common.libsonnet'),
  local bench = (import 'benchmark-suites.libsonnet'),
  local hw = bc.bench_hw,

  local main_builds = std.flattenArrays([
    [
    c.daily      + hw.x52 + jdk + cc.libgraal + bench.dacapo,
    c.daily      + hw.x52 + jdk + cc.jargraal + bench.dacapo,
    c.weekly     + hw.x52 + jdk + cc.libgraal + bench.dacapo_size_variants,
    c.weekly     + hw.x52 + jdk + cc.jargraal + bench.dacapo_size_variants,
    c.weekly     + hw.x52 + jdk + cc.libgraal + bench.dacapo_timing,
    c.weekly     + hw.x52 + jdk + cc.jargraal + bench.dacapo_timing,
    c.daily      + hw.x52 + jdk + cc.libgraal + bench.scala_dacapo,
    c.daily      + hw.x52 + jdk + cc.jargraal + bench.scala_dacapo,
    c.weekly     + hw.x52 + jdk + cc.libgraal + bench.scala_dacapo_size_variants,
    c.weekly     + hw.x52 + jdk + cc.jargraal + bench.scala_dacapo_size_variants,
    c.weekly     + hw.x52 + jdk + cc.libgraal + bench.scala_dacapo_timing,
    c.weekly     + hw.x52 + jdk + cc.jargraal + bench.scala_dacapo_timing,
    c.daily      + hw.x52 + jdk + cc.libgraal + bench.renaissance,
    c.daily      + hw.x52 + jdk + cc.jargraal + bench.renaissance,
    c.daily      + hw.x52 + jdk + cc.libgraal + bench.specjvm2008,
    c.daily      + hw.x52 + jdk + cc.jargraal + bench.specjvm2008,
    c.daily      + hw.x52 + jdk + cc.libgraal + bench.specjbb2015,
    c.weekly     + hw.x52 + jdk + cc.jargraal + bench.specjbb2015,
    c.weekly     + hw.x52 + jdk + cc.libgraal + bench.specjbb2015_full_machine,
    c.monthly    + hw.x52 + jdk + cc.jargraal + bench.specjbb2015_full_machine,
    c.weekly     + hw.x52 + jdk + cc.libgraal + bench.renaissance_0_11,
    c.monthly    + hw.x52 + jdk + cc.jargraal + bench.renaissance_0_11,
    c.daily      + hw.x52 + jdk + cc.libgraal + bench.awfy,
    c.daily      + hw.x52 + jdk + cc.jargraal + bench.awfy,
    c.daily      + hw.x52 + jdk + cc.libgraal + bench.microservice_benchmarks,
    c.weekly     + hw.x52 + jdk + cc.jargraal + bench.microservice_benchmarks,
    c.daily      + hw.x52 + jdk + cc.libgraal + bench.renaissance_legacy,
    c.daily      + hw.x52 + jdk + cc.jargraal + bench.renaissance_legacy,
    c.daily      + hw.x52 + jdk + cc.libgraal + bench.micros_graal_whitebox,
    c.weekly     + hw.x52 + jdk + cc.jargraal + bench.micros_graal_whitebox,
    c.daily      + hw.x52 + jdk + cc.libgraal + bench.micros_graal_dist,
    c.weekly     + hw.x52 + jdk + cc.jargraal + bench.micros_graal_dist,
    c.daily      + hw.x52 + jdk + cc.libgraal + bench.micros_misc_graal_dist,
    c.weekly     + hw.x52 + jdk + cc.jargraal + bench.micros_misc_graal_dist,
    c.daily      + hw.x52 + jdk + cc.libgraal + bench.micros_shootout_graal_dist,
    c.weekly     + hw.x52 + jdk + cc.jargraal + bench.micros_shootout_graal_dist,
    ]
  for jdk in cc.bench_jdks
  ]),


  local profiling_builds = std.flattenArrays([
    [
    c.weekly + hw.x52 + jdk + cc.libgraal + cc.enable_profiling + suite + { job_prefix:: "bench-profiling" },
    c.weekly + hw.x52 + jdk + cc.libgraal + cc.footprint_tracking + suite + { job_prefix:: "bench-footprint" }
    ]
  for jdk in cc.bench_jdks
  for suite in bench.groups.profiled_suites
  ]),

  // intensive weekly benchmarking
  local weekly_forks_builds = std.flattenArrays([
    bc.generate_fork_builds(c.weekly + hw.x52 + jdk + cc.libgraal + suite, subdir='compiler')
  for jdk in cc.bench_jdks
  for suite in bench.groups.weekly_forks_suites
  ]),

  local aarch64_builds = std.flattenArrays([
    [
    c.weekly + hw.a12c + jdk + cc.libgraal + suite,
    c.weekly + hw.a12c + jdk + cc.jargraal + suite
    ]
  for jdk in cc.bench_jdks
  for suite in bench.groups.all_suites
  ]),

  local avx_builds = [
    c.monthly + hw.x82 + jdk + cc.libgraal + avx + suite,
  for avx in [cc.avx2_mode, cc.avx3_mode]
  for jdk in cc.bench_jdks
  for suite in bench.groups.main_suites
  ],

  local no_tiered_builds = [
    c.weekly + hw.x52 + jdk + cc.libgraal + cc.no_tiered_comp + suite,
  for jdk in cc.bench_jdks
  for suite in bench.groups.main_suites
  ],

  local no_profile_info_builds = [
    c.weekly + hw.x52 + jdk + cc.libgraal + cc.no_profile_info + suite,
  for jdk in cc.bench_jdks
  for suite in bench.groups.main_suites
  ],


  local all_builds = main_builds + weekly_forks_builds + profiling_builds + avx_builds + aarch64_builds + no_tiered_builds + no_profile_info_builds,
  local filtered_builds = [b for b in all_builds if b.is_jdk_supported(b.jdk_version)],
  // adds a "defined_in" field to all builds mentioning the location of this current file
  builds:: [{ defined_in: std.thisFile } + b for b in filtered_builds]
}
