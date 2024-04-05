local vm = import '../ci_includes/vm.jsonnet';
local vm_common = import 'common.jsonnet';
local run_spec   = import "../../../ci/ci_common/run-spec.libsonnet";
local exclude    = run_spec.exclude;
local graal_common = import '../../../ci/ci_common/common.jsonnet';
local graal_c = import '../../../ci/common.jsonnet';

local task_spec = run_spec.task_spec;
local platform_spec = run_spec.platform_spec;
local evaluate_late(key, object) = task_spec(run_spec.evaluate_late({key:object}));
{
  local target(t) = task_spec({targets+: [t]}),
  local gate = target('gate'),
  local post_merge = target('post-merge'),
  local daily = target('daily'),
  local weekly = target('weekly'),
  local deploy = target('deploy'),

  local timelimit(t) = evaluate_late('999_time_limit', { // the key starts with 999 to be the last one evaluated
    timelimit: t
  }),

  local vm_c = {
    common_vm: graal_common.build_base + vm.vm_setup + {
      python_version: "3",
      logs+: [
        '*/mxbuild/dists/stripped/*.map',
        '**/install.packages.R.log',
      ],
      environment+: if(self.os == 'darwin') then {
        LANG: 'en_US.UTF-8'
      } else if(self.os == 'windows') then {
        PATH: '$MAVEN_HOME\\bin;$JAVA_HOME\\bin;$PATH',
      } else {},
      downloads+: if(self.os == 'darwin') then {
        MAVEN_HOME: {name: 'maven', version: '3.3.9', platformspecific: false},
      } else {},
    },

    common_vm_linux: self.common_vm,

    common_vm_darwin: self.common_vm + {
      environment+: {
        LANG: 'en_US.UTF-8'
      },
    },

    common_vm_windows: self.common_vm + {
      downloads+: {
        MAVEN_HOME: {name: 'maven', version: '3.3.9', platformspecific: false},
      },
      environment+: {
        PATH: '$MAVEN_HOME\\bin;$JAVA_HOME\\bin;$PATH',
      },
    },

    common_vm_windows_jdk21: self.common_vm_windows + graal_common.devkits['windows-jdk21'],
    common_vm_windows_jdkLatest: self.common_vm_windows + graal_common.devkits['windows-jdkLatest'],
    vm_linux_amd64_common: graal_common.deps.svm {
      capabilities+: ['manycores', 'ram16gb', 'fast'],
    },

    vm_linux_amd64: graal_common.linux_amd64 + self.common_vm + self.vm_linux_amd64_common,

    vm_linux_amd64_ol9: graal_common.linux_amd64_ol9 + self.common_vm + self.vm_linux_amd64_common,
    vm_ol9_amd64: self.vm_linux_amd64_ol9,

    vm_linux_amd64_ubuntu: graal_common.linux_amd64_ubuntu + self.common_vm + self.vm_linux_amd64_common,
    vm_ununtu_amd64: self.vm_linux_amd64_ubuntu,

    vm_linux_aarch64: self.common_vm + graal_common.linux_aarch64,

    vm_linux_aarch64_ol9: self.common_vm + graal_common.linux_aarch64_ol9,
    vm_ol9_aarch64: self.vm_linux_aarch64_ol9,

    vm_darwin_amd64: self.common_vm + graal_common.darwin_amd64 + {
      capabilities+: ['darwin_bigsur', 'ram16gb'],
      packages+: {
        gcc: '==4.9.2',
      },
      environment+: {
        # for compatibility with macOS BigSur
        MACOSX_DEPLOYMENT_TARGET: '11.0',
      },
    },

    vm_darwin_amd64_jdkLatest: self.vm_darwin_amd64,

    vm_darwin_aarch64: self.common_vm + graal_common.darwin_aarch64 + {
      capabilities+: ['darwin_bigsur'],
      environment+: {
        # for compatibility with macOS BigSur
        MACOSX_DEPLOYMENT_TARGET: '11.0',
      },
    },

    vm_windows: self.common_vm_windows + graal_common.windows_server_2016_amd64,
    vm_windows_jdk21: self.common_vm_windows_jdk21 + graal_common.windows_server_2016_amd64,
    vm_windows_jdkLatest: self.common_vm_windows_jdkLatest + graal_common.windows_server_2016_amd64,
    vm_windows_amd64: self.vm_windows,
    vm_windows_amd64_jdk21: self.vm_windows_jdk21,
    vm_windows_amd64_jdkLatest: self.vm_windows_jdkLatest,
  },

  local record_file_sizes = ['benchmark', 'file-size:*', '--results-file', 'sizes.json'],
  local upload_file_sizes = ['bench-uploader.py', 'sizes.json'],
  local common_os_deploy = deploy + task_spec({
    deploysArtifacts: true,
    packages+: if (self.os == 'linux') then {maven: '>=3.3.9'} else {},
    environment+: if (self.os == 'darwin') then {PATH: '$MAVEN_HOME/bin:$JAVA_HOME/bin:$PATH:/usr/local/bin'} else {},
    downloads+: if (self.os == 'darwin') then {MAVEN_HOME: {name: 'maven', version: '3.3.9', platformspecific: false}} else {},
  }),
  local no_jobs = {
    "*"+: exclude,
  },

  local sulong = task_spec(graal_common.deps.sulong),
  local truffleruby = task_spec(graal_common.deps.truffleruby),
  local svm_common = task_spec(graal_common.deps.svm) + task_spec({
    packages+: if (self.os == 'windows') then graal_common.devkits[std.join('', ["windows-jdk", if (self.jdk_version == 23) then 'Latest' else std.toString(self.jdk_version)])].packages else {} // we can remove self.jdk_version == 23 and add a hidden field isLatest and use it
  }),
  local ruby_vm_build = svm_common + sulong + truffleruby + run_spec.task_spec(vm.custom_vm),
  local graalpy = task_spec(graal_common.deps.graalpy),
  local ruby_python_vm_build = ruby_vm_build + graalpy,
  local full_vm_build = ruby_python_vm_build + task_spec(graal_common.deps.fastr),

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
    ] + vm.check_graalvm_base_build('$GRAALVM_DIST', self.os, self.arch, std.toString(self.jdk_version))
  }),

  local deploy_graalvm_base = common_os_deploy + name + task_spec(vm.check_structure) + build_base_graalvm_image + task_spec({
      run +: vm.collect_profiles() + [
        self.mx_vm_common + vm.vm_profiles + record_file_sizes,
        upload_file_sizes,
      ],
      notify_groups:: ['deploy'],
    },
  ) + deploy_sdk_base() + check_base_graalvm_image + timelimit("1:00:00"),

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

  local deploy_graalvm_espresso = common_os_deploy + name + task_spec({
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

  local js_windows_common = task_spec({
    environment+: if(self.os == 'windows') then local devkits_version = std.filterMap(function(p) std.startsWith(p, 'devkit:VS'), function(p) std.substr(p, std.length('devkit:VS'), 4), std.objectFields(self.packages))[0];
    {
      DEVKIT_VERSION: devkits_version,
    } else {},
    downloads+: if(self.os == 'windows') then {
      NASM: {name: 'nasm', version: '2.14.02', platformspecific: true},
    } else {},
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
    "linux": {
      "amd64": vm_c.vm_linux_amd64,
      "aarch64": vm_c.vm_linux_aarch64,
    },
    "ubuntu": {
      "amd64": vm_c.vm_linux_amd64_ubuntu,
    },
    "darwin": {
      "amd64": vm_c.vm_darwin_amd64,
      "aarch64": vm_c.vm_darwin_aarch64,
    },
    "windows": {
      "amd64": if (b.jdk == "jdk-latest") then vm_c.vm_windows_amd64_jdkLatest else vm_c.vm_windows_amd64_jdk21,
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

  local deploy_vm_base_task_dict = {
    #
    # Deploy GraalVM Base
    # NOTE: After adding or removing deploy jobs, please make sure you modify ce-release-artifacts.json accordingly.
    #
    "vm-base": mx_env + deploy_graalvm_base + default_os_arch_jdk_mixin + platform_spec(no_jobs) + platform_spec({
      "linux:amd64:jdk21": weekly + full_vm_build,
      "linux:amd64:jdk-latest": post_merge + full_vm_build,
      "linux:aarch64:jdk21": weekly + full_vm_build + capabilities('!xgene3') + timelimit('1:30:00'),
      "linux:aarch64:jdk-latest": daily + full_vm_build + capabilities('!xgene3') + timelimit('1:30:00'),

      "darwin:amd64:jdk21": weekly + full_vm_build,
      "darwin:amd64:jdk-latest": daily + full_vm_build,
      "darwin:aarch64:jdk21": weekly + full_vm_build + timelimit('1:45:00') + notify_emails('bernhard.urban-forster@oracle.com'),
      "darwin:aarch64:jdk-latest": daily + full_vm_build + timelimit('1:45:00') + notify_emails('bernhard.urban-forster@oracle.com'),

      "windows:amd64:jdk21": weekly + js_windows_common + svm_common + timelimit('1:30:00'),
      "windows:amd64:jdk-latest": daily + js_windows_common + svm_common + timelimit('1:30:00'),

      "variants": {
        "ubuntu": {
          "*": exclude,
          "linux:amd64:jdk21": weekly + full_vm_build + task_spec({os_distro:: 'ubuntu'}),
        }
      }
    }),
    "vm-base-ubuntu": mx_env + deploy_graalvm_base + default_os_arch_jdk_mixin + platform_spec(no_jobs) + platform_spec({
      "linux:amd64:jdk21": weekly + full_vm_build + task_spec({os_distro:: 'ubuntu'}),
    }),
  },

  processed_vm_base_builds::run_spec.process(deploy_vm_base_task_dict),
  deploy_vm_base: self.processed_vm_base_builds.list,

  local deploy_vm_espresso_task_dict = {
    #
    # Deploy the GraalVM Espresso artifact (GraalVM Base + espresso - native image)
    #
    "vm-espresso": mx_env + deploy_graalvm_espresso + espresso_os_arch_jdk_mixin + platform_spec(no_jobs) + platform_spec({
      "linux:amd64:jdk21": weekly + full_vm_build,
      "linux:aarch64:jdk21": weekly + full_vm_build,
      "darwin:amd64:jdk21": weekly + full_vm_build,
      "darwin:aarch64:jdk21": weekly + full_vm_build,
      "windows:amd64:jdk21": weekly + sulong + svm_common,
    }),
  },

  processed_vm_espresso_builds::run_spec.process(deploy_vm_espresso_task_dict),
  deploy_vm_espresso: self.processed_vm_espresso_builds.list,
}