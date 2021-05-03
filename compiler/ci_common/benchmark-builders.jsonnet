{
  local c = (import '../../common.jsonnet'),
  local cc = (import 'compiler-common.libsonnet'),
  local bench = (import 'benchmark-suites.libsonnet'),
  local hw = c.bench_hw,

  local jdk8  = c.oraclejdk8,
  local jdk11 = c.labsjdk11,

  local main_builds = [
    cc.post_merge + hw.x52 + jdk8  + cc.libgraal + bench.dacapo,
    cc.daily      + hw.x52 + jdk8  + cc.jargraal + bench.dacapo,
    cc.weekly     + hw.x52 + jdk8  + cc.libgraal + bench.dacapo_timing,
    cc.weekly     + hw.x52 + jdk8  + cc.jargraal + bench.dacapo_timing,
    cc.post_merge + hw.x52 + jdk8  + cc.libgraal + bench.scala_dacapo,
    cc.daily      + hw.x52 + jdk8  + cc.jargraal + bench.scala_dacapo,
    cc.weekly     + hw.x52 + jdk8  + cc.libgraal + bench.scala_dacapo_timing,
    cc.weekly     + hw.x52 + jdk8  + cc.jargraal + bench.scala_dacapo_timing,
    cc.post_merge + hw.x52 + jdk8  + cc.libgraal + bench.renaissance,
    cc.daily      + hw.x52 + jdk8  + cc.jargraal + bench.renaissance,
    cc.daily      + hw.x52 + jdk8  + cc.libgraal + bench.specjvm2008,
    cc.daily      + hw.x52 + jdk8  + cc.jargraal + bench.specjvm2008,
    cc.daily      + hw.x52 + jdk8  + cc.libgraal + bench.specjbb2005,
    cc.daily      + hw.x52 + jdk8  + cc.jargraal + bench.specjbb2005,
    cc.daily      + hw.x52 + jdk8  + cc.libgraal + bench.specjbb2015,
    cc.daily      + hw.x52 + jdk8  + cc.jargraal + bench.specjbb2015,
    cc.weekly     + hw.x52 + jdk8  + cc.libgraal + bench.specjbb2015_full_machine,
    cc.on_demand  + hw.x52 + jdk8  + cc.jargraal + bench.specjbb2015_full_machine,
    cc.weekly     + hw.x52 + jdk8  + cc.libgraal + bench.renaissance_0_10,
    cc.on_demand  + hw.x52 + jdk8  + cc.jargraal + bench.renaissance_0_10,
    cc.daily      + hw.x52 + jdk8  + cc.libgraal + bench.awfy,
    cc.daily      + hw.x52 + jdk8  + cc.jargraal + bench.awfy,
    cc.post_merge + hw.x52 + jdk8  + cc.libgraal + bench.renaissance_legacy,
    cc.daily      + hw.x52 + jdk8  + cc.jargraal + bench.renaissance_legacy,
    cc.daily      + hw.x52 + jdk8  + cc.libgraal + bench.micros_graal_whitebox,
    cc.weekly     + hw.x52 + jdk8  + cc.jargraal + bench.micros_graal_whitebox,
    cc.daily      + hw.x52 + jdk8  + cc.libgraal + bench.micros_graal_dist,
    cc.weekly     + hw.x52 + jdk8  + cc.jargraal + bench.micros_graal_dist,
    cc.daily      + hw.x52 + jdk8  + cc.libgraal + bench.micros_misc_graal_dist,
    cc.weekly     + hw.x52 + jdk8  + cc.jargraal + bench.micros_misc_graal_dist,
    cc.daily      + hw.x52 + jdk8  + cc.libgraal + bench.micros_shootout_graal_dist,
    cc.weekly     + hw.x52 + jdk8  + cc.jargraal + bench.micros_shootout_graal_dist,
    cc.daily      + hw.x52 + jdk11 + cc.libgraal + bench.dacapo,
    cc.daily      + hw.x52 + jdk11 + cc.jargraal + bench.dacapo,
    cc.weekly     + hw.x52 + jdk11 + cc.libgraal + bench.dacapo_timing,
    cc.weekly     + hw.x52 + jdk11 + cc.jargraal + bench.dacapo_timing,
    cc.daily      + hw.x52 + jdk11 + cc.libgraal + bench.scala_dacapo,
    cc.daily      + hw.x52 + jdk11 + cc.jargraal + bench.scala_dacapo,
    cc.weekly     + hw.x52 + jdk11 + cc.libgraal + bench.scala_dacapo_timing,
    cc.weekly     + hw.x52 + jdk11 + cc.jargraal + bench.scala_dacapo_timing,
    cc.post_merge + hw.x52 + jdk11 + cc.libgraal + bench.renaissance,
    cc.daily      + hw.x52 + jdk11 + cc.jargraal + bench.renaissance,
    cc.post_merge + hw.x52 + jdk11 + cc.libgraal + bench.specjvm2008,
    cc.daily      + hw.x52 + jdk11 + cc.jargraal + bench.specjvm2008,
    cc.daily      + hw.x52 + jdk11 + cc.libgraal + bench.specjbb2005,
    cc.daily      + hw.x52 + jdk11 + cc.jargraal + bench.specjbb2005,
    cc.daily      + hw.x52 + jdk11 + cc.libgraal + bench.specjbb2015,
    cc.daily      + hw.x52 + jdk11 + cc.jargraal + bench.specjbb2015,
    cc.weekly     + hw.x52 + jdk11 + cc.libgraal + bench.specjbb2015_full_machine,
    cc.on_demand  + hw.x52 + jdk11 + cc.jargraal + bench.specjbb2015_full_machine,
    cc.weekly     + hw.x52 + jdk11 + cc.libgraal + bench.renaissance_0_10,
    cc.on_demand  + hw.x52 + jdk11 + cc.jargraal + bench.renaissance_0_10,
    cc.daily      + hw.x52 + jdk11 + cc.libgraal + bench.awfy,
    cc.daily      + hw.x52 + jdk11 + cc.jargraal + bench.awfy,
    cc.post_merge + hw.x52 + jdk11 + cc.libgraal + bench.renaissance_legacy,
    cc.daily      + hw.x52 + jdk11 + cc.jargraal + bench.renaissance_legacy,
    cc.daily      + hw.x52 + jdk11 + cc.libgraal + bench.micros_graal_whitebox,
    cc.weekly     + hw.x52 + jdk11 + cc.jargraal + bench.micros_graal_whitebox,
    cc.daily      + hw.x52 + jdk11 + cc.libgraal + bench.micros_graal_dist,
    cc.weekly     + hw.x52 + jdk11 + cc.jargraal + bench.micros_graal_dist,
    cc.daily      + hw.x52 + jdk11 + cc.libgraal + bench.micros_misc_graal_dist,
    cc.weekly     + hw.x52 + jdk11 + cc.jargraal + bench.micros_misc_graal_dist,
    cc.daily      + hw.x52 + jdk11 + cc.libgraal + bench.micros_shootout_graal_dist,
    cc.weekly     + hw.x52 + jdk11 + cc.jargraal + bench.micros_shootout_graal_dist,
  ],

  // JFR and async-profiler jobs
  local profiling_builds = std.flattenArrays([
    [
    cc.weekly + hw.x52 + jdk + cc.libgraal + cc.enable_profiling + suite + { job_prefix:: "bench-profiling" },
    cc.weekly + hw.x52 + jdk + cc.jargraal + cc.enable_profiling + suite + { job_prefix:: "bench-profiling" }
    ]
  for jdk in [jdk8, jdk11]
  for suite in bench.groups.profiled_suites
  ]),

  // Microservices
  local microservice_builds = std.flattenArrays([
    [
    cc.daily + hw.x52 + jdk + cc.libgraal + suite,
    cc.daily + hw.x52 + jdk + cc.jargraal + suite
    ]
  for jdk in [jdk8, jdk11]
  for suite in bench.groups.microservice_suites
  ]),

  // intensive weekly benchmarking
  local weekly_forks_builds = std.flattenArrays([
    std.flattenArrays([
    cc.generate_fork_builds(cc.weekly + hw.x52 + jdk + cc.libgraal + suite),
    cc.generate_fork_builds(cc.weekly + hw.x52 + jdk + cc.jargraal + suite)
    ])
  for jdk in [jdk8, jdk11]
  for suite in bench.groups.weekly_forks_suites
  ]),

  local aarch64_builds = std.flattenArrays([
    [
    cc.weekly + hw.xgene3 + jdk11 + cc.libgraal + suite,
    cc.weekly + hw.xgene3 + jdk11 + cc.jargraal + suite
    ]
  for suite in bench.groups.main_suites
  ]),

  local all_builds = main_builds + weekly_forks_builds + profiling_builds + microservice_builds + aarch64_builds,
  // adds a "defined_in" field to all builds mentioning the location of this current file
  builds:: [{ defined_in: std.thisFile } + b for b in all_builds]
}
