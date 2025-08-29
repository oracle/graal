local ci_common = import '../../../ci/ci_common/common.jsonnet';
local r = import '../../../ci/ci_common/run-spec.libsonnet';
local common = import 'common.jsonnet';

local check_no_timelimit = (import '../../../ci/ci_common/run-spec-tools.libsonnet').check_no_timelimit;

// Supported JDKs for jobs
local jdk_name_to_dict = {
  'jdk-latest'+: ci_common.labsjdkLatest,
};

local os_arch_jdk_mixin(mapping) = r.task_spec(r.evaluate_late({
  // this starts with _ on purpose so that it will be evaluated first
  _os_arch_jdk: function(b)
    check_no_timelimit(jdk_name_to_dict[b.jdk] + mapping(b)[b.os][b.arch]),
}));

{
  // targets
  // These should always be used to set/add job targets, otherwise the 'target'
  // key is not set.
  target(t): r.task_spec({ target: t, targets+: [t] }),
  tier1: self.target('tier1'),
  tier2: self.target('tier2'),
  tier3: self.target('tier3'),
  tier4: self.target('tier4'),
  daily: self.target('daily'),
  weekly: self.target('weekly'),
  post_merge: self.target('post-merge'),
  // opt-post-merge jobs should have an associated tag to group the jobs
  opt_post_merge(tag): self.target('opt-post-merge') + r.task_spec({ tags+: { opt_post_merge+: [tag] } }),

  capabilities(capabilities): r.task_spec({ capabilities+: capabilities }),

  docker_ol8: {
    docker: {
      image: 'buildslave_ol8',
      mount_modules: true,
    },
  },

  // Supported operating systems and architectures for jobs
  default_os_arch(b): {
    linux+: {
      amd64+: ci_common.linux_amd64 + $.docker_ol8,
    },
    'windows'+: {
      'amd64'+: ci_common.windows_amd64 {
        packages+: ci_common.devkits['windows-' + b.jdk].packages,
      },
    },
    'darwin'+: {
      'amd64'+: ci_common.darwin_amd64,
      'aarch64'+: ci_common.darwin_aarch64,
    },
  },


  no_jobs: {
    '*'+: r.exclude,
  },

  // Platform spec for only the given platforms
  platforms(spec): r.platform_spec(self.no_jobs + spec),

  // Set an environment variable in the job
  env(key, value): r.task_spec({
    environment+: {
      [key]: value,
    },
  }),

  t(limit): r.task_spec({ timelimit: limit }),
  notify_group(group): r.task_spec({ notify_groups+:: if std.isArray(group) then group else [group] }),

  process: r.process,
  task_spec: r.task_spec,
  generate_variants: r.generate_variants,
  evaluate_late: r.evaluate_late,
  exclude: r.exclude,

  // Tiered jobs should be treated as gate jobs. Moving a job to the tier system
  // or moving it between tiers should not affect anything else about the job
  // definition
  is_gate_target(target): target == 'gate' || std.startsWith(target, 'tier'),

  target_to_prefix(target):
    if $.is_gate_target(target) then
      'gate'
    else
      target,

  // Base Task specification
  job(suite, suite_short=suite, os_arch_mapping=$.default_os_arch): os_arch_jdk_mixin(os_arch_mapping) + r.task_spec(common.catch_test_failures + common.svm {
    // These 4 are provided by the run-spec library
    task_name:: null,
    os:: null,
    arch:: null,
    jdk:: null,

    // These can be overwritten
    suite_short:: suite_short,
    target:: null,
    variations:: [],
    // Pass these flags to the Web Image builder. How exactly depends on the
    // way Web Image is invoked and is specified by callers of this function.
    webimage_flags:: [],

    setup+: [
      ['cd', './' + suite],
    ],
    name: std.join('-', [$.target_to_prefix(self.target), self.suite_short, self.task_name] + self.variations + [self.os, self.arch, self.jdk]),
  }),

  // Get all web image flags in the given job
  extract_flags(obj): obj.webimage_flags,

  // Base Task specification for running mx gate
  // The key in the task dictionary becomes part of the job name
  mxgate(tags, suite, suite_short=suite): self.job(suite, suite_short) + r.task_spec({
    mxgate_tags:: std.split(tags, ','),
    mxgate_unittest_suite:: null,
    mxgate_backend:: null,
    run+: [
      ['mx', 'gate', '--strict-mode'] +
      ['--tags', std.join(',', self.mxgate_tags)] +
      ['--spectest-argument=' + flag for flag in $.extract_flags(self)] +
      (
        if self.mxgate_unittest_suite != null then
          ['--spectest=' + self.mxgate_unittest_suite]
        else
          []
      ) + (
        if self.mxgate_backend != null then
          ['--backend=' + self.mxgate_backend]
        else
          []
      ),
    ],
  }),

  // Add additional gate tags
  gate_tag(tag): r.task_spec({ mxgate_tags+: [tag] }),
  gate_backend(backend): r.task_spec({ mxgate_backend: backend }),

  // Adds web image flags to the job
  web_image_flags(flags=[]): r.task_spec({ webimage_flags+: flags }),

  eclipse: r.task_spec(common.eclipse),
  jdt: r.task_spec(common.jdt),
  spotbugs: r.task_spec(common.spotbugs),

  node22: r.task_spec(common.node22),

  wabt: r.task_spec(common.wabt),
  binaryen: r.task_spec(common.binaryen),

  prettier: r.task_spec(common.gate_prettier),
  maven: r.task_spec(common.maven),

  notify: {
    base: $.notify_group('web_image'),
    bench: $.notify_group('web_image_bench'),
    wasm: $.notify_group('web_image_wasm'),
    demos: $.notify_group('web_image_demos'),
  },
}
