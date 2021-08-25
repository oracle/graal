{
  local c = (import '../../common.jsonnet'),
  local bc = (import '../../bench-common.libsonnet'),
  local cc = (import 'compiler-common.libsonnet'),
  local bench = (import 'benchmark-suites.libsonnet'),
  local hw = bc.bench_hw,

  local jdk8  = c.oraclejdk8,
  local jdk11 = c.labsjdk11,
  local jdk17 = c.labsjdk17,

  local amd64_jdks = [jdk8, jdk11, jdk17],
  local aarch64_jdks = [jdk11, jdk17],

  local main_builds = [
    c.daily      + hw.x52 + jdk8  + cc.libgraal + bench.dacapo,
    c.daily      + hw.x52 + jdk8  + cc.jargraal + bench.dacapo,
    c.weekly     + hw.x52 + jdk8  + cc.libgraal + bench.dacapo_size_variants,
    c.on_demand  + hw.x52 + jdk8  + cc.jargraal + bench.dacapo_size_variants,
    c.weekly     + hw.x52 + jdk8  + cc.libgraal + bench.dacapo_timing,
    c.weekly     + hw.x52 + jdk8  + cc.jargraal + bench.dacapo_timing,
    c.daily      + hw.x52 + jdk8  + cc.libgraal + bench.scala_dacapo,
    c.daily      + hw.x52 + jdk8  + cc.jargraal + bench.scala_dacapo,
    c.weekly     + hw.x52 + jdk8  + cc.libgraal + bench.scala_dacapo_size_variants,
    c.on_demand  + hw.x52 + jdk8  + cc.jargraal + bench.scala_dacapo_size_variants,
    c.weekly     + hw.x52 + jdk8  + cc.libgraal + bench.scala_dacapo_timing,
    c.weekly     + hw.x52 + jdk8  + cc.jargraal + bench.scala_dacapo_timing,
    c.post_merge + hw.x52 + jdk8  + cc.libgraal + bench.renaissance,
    c.daily      + hw.x52 + jdk8  + cc.jargraal + bench.renaissance,
    c.daily      + hw.x52 + jdk8  + cc.libgraal + bench.specjvm2008,
    c.weekly     + hw.x52 + jdk8  + cc.jargraal + bench.specjvm2008,
    c.daily      + hw.x52 + jdk8  + cc.libgraal + bench.specjbb2005,
    c.weekly     + hw.x52 + jdk8  + cc.jargraal + bench.specjbb2005,
    c.weekly     + hw.x52 + jdk8  + cc.libgraal + bench.specjbb2015,
    c.weekly     + hw.x52 + jdk8  + cc.jargraal + bench.specjbb2015,
    c.weekly     + hw.x52 + jdk8  + cc.libgraal + bench.specjbb2015_full_machine,
    c.on_demand  + hw.x52 + jdk8  + cc.jargraal + bench.specjbb2015_full_machine,
    c.weekly     + hw.x52 + jdk8  + cc.libgraal + bench.renaissance_0_10,
    c.on_demand  + hw.x52 + jdk8  + cc.jargraal + bench.renaissance_0_10,
    c.daily      + hw.x52 + jdk8  + cc.libgraal + bench.awfy,
    c.daily      + hw.x52 + jdk8  + cc.jargraal + bench.awfy,
    c.daily      + hw.x52 + jdk8  + cc.libgraal + bench.renaissance_legacy,
    c.daily      + hw.x52 + jdk8  + cc.jargraal + bench.renaissance_legacy,
    c.daily      + hw.x52 + jdk8  + cc.libgraal + bench.micros_graal_whitebox,
    c.weekly     + hw.x52 + jdk8  + cc.jargraal + bench.micros_graal_whitebox,
    c.daily      + hw.x52 + jdk8  + cc.libgraal + bench.micros_graal_dist,
    c.weekly     + hw.x52 + jdk8  + cc.jargraal + bench.micros_graal_dist,
    c.daily      + hw.x52 + jdk8  + cc.libgraal + bench.micros_misc_graal_dist,
    c.weekly     + hw.x52 + jdk8  + cc.jargraal + bench.micros_misc_graal_dist,
    c.daily      + hw.x52 + jdk8  + cc.libgraal + bench.micros_shootout_graal_dist,
    c.weekly     + hw.x52 + jdk8  + cc.jargraal + bench.micros_shootout_graal_dist,

    c.post_merge + hw.x52 + jdk11 + cc.libgraal + bench.dacapo,
    c.daily      + hw.x52 + jdk11 + cc.jargraal + bench.dacapo,
    c.weekly     + hw.x52 + jdk11 + cc.libgraal + bench.dacapo_size_variants,
    c.weekly     + hw.x52 + jdk11 + cc.jargraal + bench.dacapo_size_variants,
    c.weekly     + hw.x52 + jdk11 + cc.libgraal + bench.dacapo_timing,
    c.weekly     + hw.x52 + jdk11 + cc.jargraal + bench.dacapo_timing,
    c.post_merge + hw.x52 + jdk11 + cc.libgraal + bench.scala_dacapo,
    c.daily      + hw.x52 + jdk11 + cc.jargraal + bench.scala_dacapo,
    c.weekly     + hw.x52 + jdk11 + cc.libgraal + bench.scala_dacapo_size_variants,
    c.weekly     + hw.x52 + jdk11 + cc.jargraal + bench.scala_dacapo_size_variants,
    c.weekly     + hw.x52 + jdk11 + cc.libgraal + bench.scala_dacapo_timing,
    c.weekly     + hw.x52 + jdk11 + cc.jargraal + bench.scala_dacapo_timing,
    c.post_merge + hw.x52 + jdk11 + cc.libgraal + bench.renaissance,
    c.daily      + hw.x52 + jdk11 + cc.jargraal + bench.renaissance,
    c.post_merge + hw.x52 + jdk11 + cc.libgraal + bench.specjvm2008,
    c.daily      + hw.x52 + jdk11 + cc.jargraal + bench.specjvm2008,
    c.daily      + hw.x52 + jdk11 + cc.libgraal + bench.specjbb2005,
    c.daily      + hw.x52 + jdk11 + cc.jargraal + bench.specjbb2005,
    c.daily      + hw.x52 + jdk11 + cc.libgraal + bench.specjbb2015,
    c.weekly     + hw.x52 + jdk11 + cc.jargraal + bench.specjbb2015,
    c.weekly     + hw.x52 + jdk11 + cc.libgraal + bench.specjbb2015_full_machine,
    c.on_demand  + hw.x52 + jdk11 + cc.jargraal + bench.specjbb2015_full_machine,
    c.weekly     + hw.x52 + jdk11 + cc.libgraal + bench.renaissance_0_10,
    c.on_demand  + hw.x52 + jdk11 + cc.jargraal + bench.renaissance_0_10,
    c.daily      + hw.x52 + jdk11 + cc.libgraal + bench.awfy,
    c.daily      + hw.x52 + jdk11 + cc.jargraal + bench.awfy,
    c.post_merge + hw.x52 + jdk11 + cc.libgraal + bench.renaissance_legacy,
    c.daily      + hw.x52 + jdk11 + cc.jargraal + bench.renaissance_legacy,
    c.daily      + hw.x52 + jdk11 + cc.libgraal + bench.micros_graal_whitebox,
    c.weekly     + hw.x52 + jdk11 + cc.jargraal + bench.micros_graal_whitebox,
    c.daily      + hw.x52 + jdk11 + cc.libgraal + bench.micros_graal_dist,
    c.weekly     + hw.x52 + jdk11 + cc.jargraal + bench.micros_graal_dist,
    c.daily      + hw.x52 + jdk11 + cc.libgraal + bench.micros_misc_graal_dist,
    c.weekly     + hw.x52 + jdk11 + cc.jargraal + bench.micros_misc_graal_dist,
    c.daily      + hw.x52 + jdk11 + cc.libgraal + bench.micros_shootout_graal_dist,
    c.weekly     + hw.x52 + jdk11 + cc.jargraal + bench.micros_shootout_graal_dist,

    c.daily      + hw.x52 + jdk17 + cc.libgraal + bench.dacapo,
    c.daily      + hw.x52 + jdk17 + cc.jargraal + bench.dacapo,
    c.daily      + hw.x52 + jdk17 + cc.libgraal + bench.dacapo_size_variants,
    c.weekly     + hw.x52 + jdk17 + cc.jargraal + bench.dacapo_size_variants,
    c.weekly     + hw.x52 + jdk17 + cc.libgraal + bench.dacapo_timing,
    c.weekly     + hw.x52 + jdk17 + cc.jargraal + bench.dacapo_timing,
    c.daily      + hw.x52 + jdk17 + cc.libgraal + bench.scala_dacapo,
    c.daily      + hw.x52 + jdk17 + cc.jargraal + bench.scala_dacapo,
    c.daily      + hw.x52 + jdk17 + cc.libgraal + bench.scala_dacapo_size_variants,
    c.weekly     + hw.x52 + jdk17 + cc.jargraal + bench.scala_dacapo_size_variants,
    c.weekly     + hw.x52 + jdk17 + cc.libgraal + bench.scala_dacapo_timing,
    c.weekly     + hw.x52 + jdk17 + cc.jargraal + bench.scala_dacapo_timing,
    #c.post_merge + hw.x52 + jdk17 + cc.libgraal + bench.renaissance,
    #c.daily      + hw.x52 + jdk17 + cc.jargraal + bench.renaissance,
    c.daily      + hw.x52 + jdk17 + cc.libgraal + bench.specjvm2008,
    c.daily      + hw.x52 + jdk17 + cc.jargraal + bench.specjvm2008,
    c.daily      + hw.x52 + jdk17 + cc.libgraal + bench.specjbb2005,
    c.daily      + hw.x52 + jdk17 + cc.jargraal + bench.specjbb2005,
    c.daily      + hw.x52 + jdk17 + cc.libgraal + bench.specjbb2015,
    c.weekly     + hw.x52 + jdk17 + cc.jargraal + bench.specjbb2015,
    c.weekly     + hw.x52 + jdk17 + cc.libgraal + bench.specjbb2015_full_machine,
    c.on_demand  + hw.x52 + jdk17 + cc.jargraal + bench.specjbb2015_full_machine,
    c.daily      + hw.x52 + jdk17 + cc.libgraal + bench.awfy,
    c.daily      + hw.x52 + jdk17 + cc.jargraal + bench.awfy,
    #c.post_merge + hw.x52 + jdk17 + cc.libgraal + bench.renaissance_legacy,
    #c.daily      + hw.x52 + jdk17 + cc.jargraal + bench.renaissance_legacy,
    c.daily      + hw.x52 + jdk17 + cc.libgraal + bench.micros_graal_whitebox,
    c.weekly     + hw.x52 + jdk17 + cc.jargraal + bench.micros_graal_whitebox,
    c.daily      + hw.x52 + jdk17 + cc.libgraal + bench.micros_graal_dist,
    c.weekly     + hw.x52 + jdk17 + cc.jargraal + bench.micros_graal_dist,
    c.daily      + hw.x52 + jdk17 + cc.libgraal + bench.micros_misc_graal_dist,
    c.weekly     + hw.x52 + jdk17 + cc.jargraal + bench.micros_misc_graal_dist,
    c.daily      + hw.x52 + jdk17 + cc.libgraal + bench.micros_shootout_graal_dist,
    c.weekly     + hw.x52 + jdk17 + cc.jargraal + bench.micros_shootout_graal_dist
  ],

  // JFR and async-profiler jobs
  local profiling_builds = std.flattenArrays([
    [
    c.weekly + hw.x52 + jdk + cc.libgraal + cc.enable_profiling + suite + { job_prefix:: "bench-profiling" },
    c.weekly + hw.x52 + jdk + cc.jargraal + cc.enable_profiling + suite + { job_prefix:: "bench-profiling" }
    ]
  for jdk in amd64_jdks
  for suite in bench.groups.profiled_suites
  if suite.is_jdk_supported(jdk.jdk_version)
  ]),

  // Microservices
  local microservice_builds = std.flattenArrays([
    [
    c.daily + hw.x52 + jdk + cc.libgraal + suite,
    c.daily + hw.x52 + jdk + cc.jargraal + suite
    ]
  for jdk in amd64_jdks
  for suite in bench.groups.microservice_suites
  if suite.is_jdk_supported(jdk.jdk_version)
  ]),

  // intensive weekly benchmarking
  local weekly_forks_builds = std.flattenArrays([
    std.flattenArrays([
    cc.generate_fork_builds(c.weekly + hw.x52 + jdk + cc.libgraal + suite),
    cc.generate_fork_builds(c.weekly + hw.x52 + jdk + cc.jargraal + suite)
    ])
  for jdk in amd64_jdks
  for suite in bench.groups.weekly_forks_suites
  if suite.is_jdk_supported(jdk.jdk_version)
  ]),

  local aarch64_builds = std.flattenArrays([
    [
    c.weekly + hw.xgene3 + jdk + cc.libgraal + suite,
    c.weekly + hw.xgene3 + jdk + cc.jargraal + suite
    ]
  for jdk in aarch64_jdks
  for suite in bench.groups.main_suites
  if suite.is_jdk_supported(jdk.jdk_version)
  ]),

  local all_builds = main_builds + weekly_forks_builds + profiling_builds + microservice_builds + aarch64_builds,
  // adds a "defined_in" field to all builds mentioning the location of this current file
  builds:: [{ defined_in: std.thisFile } + b for b in all_builds]
}
