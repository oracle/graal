{
  local c = (import '../../../ci/ci_common/common.jsonnet'),
  local utils = (import '../../../ci/ci_common/common-utils.libsonnet'),
  local bc = (import '../../../ci/ci_common/bench-common.libsonnet'),
  local cc = (import 'compiler-common.libsonnet'),
  local bench = (import 'benchmark-suites.libsonnet'),
  local hw = bc.bench_hw,

  # GR-49532 TODO add 'throughput' metric and 'top-tier-throughput' secondary_metrics
  local PR_bench_libgraal = {unicorn_pull_request_benchmarking:: {name: 'libgraal', metrics: ['time', 'throughput'], secondary_metrics: ['max-rss', 'top-tier-throughput']}},

  local main_builds = std.flattenArrays([
    [
    c.daily + c.opt_post_merge + hw.e3 + jdk + cc.libgraal + bench.dacapo + PR_bench_libgraal,
    c.daily + c.opt_post_merge + hw.e3 + jdk + cc.libgraal + bench.scala_dacapo + PR_bench_libgraal,
    c.daily + c.opt_post_merge + hw.e3 + jdk + cc.libgraal + bench.renaissance + PR_bench_libgraal,
    c.daily + c.opt_post_merge + hw.e3 + jdk + cc.libgraal + bench.specjvm2008 + PR_bench_libgraal,
    c.on_demand                + hw.e3 + jdk + cc.libgraal + bench.dacapo_size_variants,
    c.on_demand                + hw.e3 + jdk + cc.libgraal + bench.scala_dacapo_size_variants,
    c.monthly                  + hw.e3 + jdk + cc.libgraal + bench.specjbb2015,
    c.daily + c.opt_post_merge + hw.e3 + jdk + cc.libgraal + bench.awfy + PR_bench_libgraal,
    c.daily                    + hw.e3 + jdk + cc.libgraal + bench.microservice_benchmarks,
    c.weekly                   + hw.e3 + jdk + cc.libgraal + bench.micros_graal_whitebox,
    c.weekly                   + hw.e3 + jdk + cc.libgraal + bench.micros_graal_dist,
    ]
  for jdk in cc.product_jdks
  ]),

  local profiling_builds = std.flattenArrays([
    [
    c.monthly + hw.e3 + jdk + cc.libgraal + suite + cc.enable_profiling   + { job_prefix:: "bench-compiler-profiling" },
    c.monthly + hw.e3 + jdk + cc.libgraal + suite + cc.footprint_tracking + { job_prefix:: "bench-compiler-footprint" }
    ]
  for jdk in cc.product_jdks
  for suite in bench.groups.main_suites
  ]),

  local weekly_amd64_forks_builds = std.flattenArrays([
    bc.generate_fork_builds(c.weekly  + hw.e3  + jdk + cc.libgraal + suite, subdir='compiler') +
    bc.generate_fork_builds(c.monthly + hw.e3  + jdk + cc.jargraal + suite, subdir='compiler')
  for jdk in cc.product_jdks
  for suite in bench.groups.weekly_forks_suites
  ]),

  local weekly_aarch64_forks_builds = std.flattenArrays([
    bc.generate_fork_builds(c.weekly + hw.a12c + jdk + cc.libgraal + suite, subdir='compiler')
  for jdk in cc.product_jdks
  for suite in bench.groups.weekly_forks_suites
  ]),

  local aarch64_builds = [
    c.daily + hw.a12c + jdk + cc.libgraal + suite,
  for jdk in cc.product_jdks
  for suite in bench.groups.main_suites
  ],

  local avx_builds = [
    c.monthly + hw.x82 + jdk + cc.libgraal + avx + suite,
  for avx in [cc.avx2_mode, cc.avx3_mode]
  for jdk in cc.product_jdks
  for suite in bench.groups.main_suites
  ],

  local zgc_builds = [
    c.weekly + hw.e3 + jdk + cc.libgraal + cc.zgc_mode + suite,
  for jdk in cc.product_jdks
  for suite in bench.groups.main_suites + [bench.specjbb2015]
  ],

  local zgc_avx_builds = [
    c.monthly + hw.x82 + jdk + cc.libgraal + cc.zgc_mode + avx + suite,
  for avx in [cc.avx2_mode, cc.avx3_mode]
  for jdk in cc.product_jdks
  for suite in bench.groups.main_suites
  ],

  local all_builds = main_builds + weekly_amd64_forks_builds + weekly_aarch64_forks_builds + profiling_builds + avx_builds + zgc_builds + zgc_avx_builds + aarch64_builds,
  local filtered_builds = [b for b in all_builds if b.is_jdk_supported(b.jdk_version) && b.is_arch_supported(b.arch)],
  // adds a "defined_in" field to all builds mentioning the location of this current file
  builds:: utils.add_defined_in(filtered_builds, std.thisFile),
}
