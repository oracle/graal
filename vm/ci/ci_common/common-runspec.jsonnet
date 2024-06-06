local vm = import '../ci_includes/vm.jsonnet';
local vm_common = import 'common.jsonnet';
local run_spec   = import "../../../ci/ci_common/run-spec.libsonnet";
local exclude    = run_spec.exclude;
local graal_common = import '../../../ci/ci_common/common.jsonnet';
local graal_c = import '../../../ci/common.jsonnet';
local utils = import '../../../ci/ci_common/common-utils.libsonnet';

local task_spec = run_spec.task_spec;
local platform_spec = run_spec.platform_spec;
local evaluate_late(key, object) = task_spec(run_spec.evaluate_late({key:object}));
{
  local no_jobs = {'*'+: exclude},

  local target(t) = task_spec({targets+: [t]}),
  local gate = target('gate'),
  local post_merge = target('post-merge'),
  local daily = target('daily'),
  local weekly = target('weekly'),
  local deploy = target('deploy'),

  local svm_common = task_spec(graal_common.deps.svm) + task_spec({
    packages+: if (self.os == 'windows') then graal_common.devkits[std.join('', ["windows-jdk", if (self.jdk_name == 'jdk-latest') then 'Latest' else std.toString(self.jdk_version)])].packages else {} // we can remove self.jdk_version == 23 and add a hidden field isLatest and use it
  }),
  local sulong = task_spec(graal_common.deps.sulong),
  local truffleruby = task_spec(graal_common.deps.truffleruby),
  local graalpy = task_spec(graal_common.deps.graalpy),
  local graalnodejs = task_spec(graal_common.deps.graalnodejs),
  local fastr = task_spec(graal_common.deps.fastr),

  local timelimit(t) = evaluate_late('999_time_limit', { // the key starts with 999 to be the last one evaluated
    timelimit: t
  }),

  local name = task_spec({
    name: std.join('-', vm_common.job_name_targets(self)
      + [self.task_name, std.strReplace(self.jdk_name, 'jdk', 'java')]
      + (if (std.objectHasAll(self, 'os_distro') && self.os_distro != 'ol') then [self.os_distro] else [])
      + [self.os, self.arch]
    ),
  }),

  local notify_groups(group) = task_spec({
    notify_groups+: if(std.type(group) == 'string') then [group] else group
  }),

  local diskspace_required(diskspace) = task_spec({
    diskspace_required: diskspace
  }),

  local capabilities(caps) = task_spec({
    capabilities+: if(std.type(caps) == 'string') then [caps] else caps
  }),

  local notify_emails(emails) = task_spec({
    notify_emails+: if(std.type(emails) == 'string') then [emails] else emails
  }),

  local default_jdk(b) = {
    "jdk21"+: vm.vm_java_21,
    "jdk-latest"+: vm.vm_java_Latest,
  },

  local llvm_jdk(b) = if (b.os != 'windows' && b.arch == 'amd64') then {
    "jdk21"+: vm.vm_java_21_llvm,
    "jdk-latest"+: vm.vm_java_Latest_llvm,
  } else default_jdk(b),

  local default_os_arch(b) = {
    local common_vm = graal_common.build_base + vm.vm_setup + vm.custom_vm + {
      python_version: "3",
      logs+: [
        '*/mxbuild/dists/stripped/*.map',
        '**/install.packages.R.log',
      ],
    },

    local common_vm_linux = common_vm + {
      capabilities+: ['manycores'],
    },

    local common_vm_darwin = common_vm + {
      environment+: {
        LANG: 'en_US.UTF-8',
        MACOSX_DEPLOYMENT_TARGET: '11.0',  # for compatibility with macOS BigSur
      },
      capabilities+: ['darwin_bigsur', 'ram16gb'],
    },

    local common_vm_windows = common_vm + graal_common.windows_server_2016_amd64,

    "linux": {
      "amd64": graal_common.linux_amd64 + common_vm_linux,
      "aarch64": graal_common.linux_aarch64 + common_vm_linux,
    },
    "ubuntu": {
      "amd64": graal_common.linux_amd64_ubuntu + common_vm_linux,
    },
    "darwin": {
      "amd64": graal_common.darwin_amd64 + common_vm_darwin,
      "aarch64": graal_common.darwin_aarch64 + common_vm_darwin,
    },
    "windows": {
      "amd64": if (b.jdk == "jdk-latest") then
        graal_common.devkits['windows-jdkLatest'] + common_vm_windows
      else
        graal_common.devkits['windows-jdk21'] + common_vm_windows,
    },
  },

  local default_os_arch_jdk_mixin = task_spec(run_spec.evaluate_late({
    // this starts with _ on purpose so that it will be evaluated first
    "_os_arch_jdk": function(b)
      local os = if (std.objectHasAll(b, 'os_distro')) then b.os_distro else b.os;
      default_jdk(b)[b.jdk] + default_os_arch(b)[os][b.arch]
  })),

  local espresso_os_arch_jdk_mixin = task_spec(run_spec.evaluate_late({
    // this starts with _ on purpose so that it will be evaluated first
    "_os_arch_jdk": function(b)
      llvm_jdk(b)[b.jdk] + default_os_arch(b)[b.os][b.arch]
  })),

  local common_os_deploy = deploy + task_spec({
    deploysArtifacts: true,
    packages+: {
      maven: '==3.3.9'
    },
  }),

  local record_file_sizes = ['benchmark', 'file-size:*', '--results-file', 'sizes.json'],
  local upload_file_sizes = ['bench-uploader.py', 'sizes.json'],

  local mx_env = task_spec({
    mx_vm_cmd_suffix:: ['--sources=sdk:GRAAL_SDK,truffle:TRUFFLE_API,compiler:GRAAL,substratevm:SVM', '--debuginfo-dists', '--base-jdk-info=' + self.downloads.JAVA_HOME.name + ':' + std.toString(self.jdk_version)],
    mx_env::
    if (self.os == 'linux') then
      if (self.arch == 'amd64') then vm.edition
      else if (self.arch == 'aarch64') then vm.edition + '-aarch64'
      else error "arch not found: " + self.arch
    # darwin
    else if (self.os == 'darwin') then
      if (self.arch == 'amd64') then vm.edition + '-darwin'
      else if (self.arch == 'aarch64') then
      # GR-34811: `ce-darwin-aarch64` can be removed once svml builds
        vm.edition + '-darwin-aarch64'
      else error "arch not found: " + self.arch
    # windows
    else if (self.os == 'windows') then
      if (self.arch == 'amd64') then vm.edition + '-win'
      else error "arch not found: " + self.arch
    else error "os not found: " + self.os,
    mx_env_espresso:: vm.edition + '-espresso',
    mx_env_llvm:: vm.edition + '-llvm-espresso',
    mx_vm_common:: vm.mx_cmd_base_no_env + ['--env', self.mx_env] + self.mx_vm_cmd_suffix,
  }),
  local maven_deploy_sdk = ['--suite', 'sdk', 'maven-deploy', '--validate', 'none', '--all-distribution-types', '--with-suite-revisions-metadata'],
  local maven_deploy_sdk_base = maven_deploy_sdk + ['--tags', 'graalvm', vm.binaries_repository],
  local build_base_graalvm_image = task_spec({ run +: [
    self.mx_vm_common + vm.vm_profiles + ['graalvm-show'],
    self.mx_vm_common + vm.vm_profiles + ['build'],
    ['set-export', 'GRAALVM_HOME', self.mx_vm_common + vm.vm_profiles + ['--quiet', '--no-warning', 'graalvm-home']],
  ]}),

  local deploy_artifacts_sdk(base_dist_name=null) = task_spec({
    deploy_artifacts_sdk::(if base_dist_name != null then ['--base-dist-name=' + base_dist_name] else []) + ['--suite', 'sdk', 'deploy-artifacts', '--uploader', if self.os == 'windows' then 'artifact_uploader.cmd' else 'artifact_uploader'],
  }),

  local artifact_deploy_sdk_base(base_dist_name=null) = task_spec({
    artifact_deploy_sdk_base:: self.deploy_artifacts_sdk + ['--tags', 'graalvm'],
  }),

  local deploy_sdk_base(base_dist_name=null) = deploy_artifacts_sdk(base_dist_name) + artifact_deploy_sdk_base(base_dist_name) + task_spec({
    run +: [self.mx_vm_common + vm.vm_profiles + maven_deploy_sdk_base, self.mx_vm_common + vm.vm_profiles + self.artifact_deploy_sdk_base] //if(self.os_distro == null) then null else self.os_distro
  }),

  local check_base_graalvm_image = task_spec({ run +: [
      ['set-export', 'GRAALVM_DIST', self.mx_vm_common + vm.vm_profiles + ['--quiet', '--no-warning', 'paths', self.mx_vm_common + vm.vm_profiles + ['graalvm-dist-name']]]
    ] + vm.check_graalvm_base_build('$GRAALVM_DIST', self.os, self.arch, if (self.jdk_name == 'jdk-latest') then 'latest' else 'java' + std.toString(self.jdk_version))
  }),

  local deploy_graalvm_base = svm_common + common_os_deploy + name + task_spec(vm.check_structure) + task_spec({
    run +: vm.collect_profiles(mx_prefix=self.mx_vm_common),
  }) + build_base_graalvm_image + task_spec({
      run +: [
        self.mx_vm_common + vm.vm_profiles + record_file_sizes,
        upload_file_sizes,
      ],
      notify_groups:: ['deploy'],
    },
  ) + deploy_sdk_base() + check_base_graalvm_image + timelimit("1:00:00"),

  local deploy_graalvm_espresso = svm_common + sulong + common_os_deploy + name + task_spec({
    mx_env:: if ((self.os == 'linux' || self.os == 'darwin') && self.arch == 'amd64') then self.mx_env_llvm else self.mx_env_espresso,
    run: vm.collect_profiles() + [['set-export', 'VM_ENV', self.mx_env]],
    notify_groups:: ['deploy'],
  }) + build_base_graalvm_image + deploy_sdk_base(base_dist_name='espresso') + task_spec({
    run +:[
      ['set-export', 'GRAALVM_HOME', self.mx_vm_common + ['--quiet', '--no-warning', 'graalvm-home']],
      ['set-export', 'DACAPO_JAR', self.mx_vm_common + ['--quiet', '--no-warning', 'paths', '--download', 'DACAPO_MR1_2baec49']],
      ['${GRAALVM_HOME}/bin/java', '-jar', '${DACAPO_JAR}', 'luindex'],
    ],
  }) + timelimit('1:45:00'),

  local deploy_vm_base_task_dict = {
    #
    # Deploy GraalVM Base
    # NOTE: After adding or removing deploy jobs, please make sure you modify ce-release-artifacts.json accordingly.
    #
    "vm-base": mx_env + deploy_graalvm_base + default_os_arch_jdk_mixin + platform_spec(no_jobs) + platform_spec({
      "linux:amd64:jdk21": weekly,
      "linux:amd64:jdk-latest": post_merge,
      "linux:aarch64:jdk21": weekly + capabilities('!xgene3') + timelimit('1:30:00'),
      "linux:aarch64:jdk-latest": daily + capabilities('!xgene3') + timelimit('1:30:00'),

      "darwin:amd64:jdk21": weekly,
      "darwin:amd64:jdk-latest": daily,
      "darwin:aarch64:jdk21": weekly + timelimit('1:45:00') + notify_emails('bernhard.urban-forster@oracle.com'),
      "darwin:aarch64:jdk-latest": daily + timelimit('1:45:00') + notify_emails('bernhard.urban-forster@oracle.com'),

      "windows:amd64:jdk21": weekly + timelimit('1:30:00'),
      "windows:amd64:jdk-latest": daily + timelimit('1:30:00'),
    }),
  },

  local deploy_vm_espresso_task_dict = {
    #
    # Deploy the GraalVM Espresso artifact (GraalVM Base + espresso - native image)
    #
    "vm-espresso": mx_env + deploy_graalvm_espresso + espresso_os_arch_jdk_mixin + platform_spec(no_jobs) + (
    if vm.deploy_espress_standalone then platform_spec({
      "linux:amd64:jdk21": weekly,
      "linux:aarch64:jdk21": weekly,
      "darwin:amd64:jdk21": weekly,
      "darwin:aarch64:jdk21": weekly,
      "windows:amd64:jdk21": weekly,
    }) else {}),
  },

  builds: utils.add_defined_in(std.flattenArrays([run_spec.process(task_dict).list for task_dict in [
    deploy_vm_base_task_dict,
    deploy_vm_espresso_task_dict,
  ]]), std.thisFile),
}