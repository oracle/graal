local vm = import '../ci_includes/vm.jsonnet';
local run_spec   = import "../../../ci/ci_common/run-spec.libsonnet";
local exclude    = run_spec.exclude;
local common = import "common.jsonnet";
local graal_common = import '../../../ci/ci_common/common.jsonnet';
local graal_c = import '../../../ci/common.jsonnet';

local task_spec = run_spec.task_spec;
local platform_spec = run_spec.platform_spec;
local evaluate_late(key, object) = task_spec(run_spec.evaluate_late({key:object}));
{
  local devkits = {
    "windows-jdk17": { packages+: { "devkit:VS2022-17.1.0+1": "==0" }},
    "windows-jdk19": { packages+: { "devkit:VS2022-17.1.0+1": "==0" }},
    "windows-jdk20": { packages+: { "devkit:VS2022-17.1.0+1": "==0" }},
    "windows-jdk21": { packages+: { "devkit:VS2022-17.1.0+1": "==1" }},
    "windows-jdk-latest": { packages+: { "devkit:VS2022-17.6.5+1": "==0" }},
    "windows-jdkLatest": self["windows-jdk-latest"],
    "linux-jdk17": { packages+: { "devkit:gcc11.2.0-OL6.4+1": "==0" }},
    "linux-jdk19": { packages+: { "devkit:gcc11.2.0-OL6.4+1": "==0" }},
    "linux-jdk20": { packages+: { "devkit:gcc11.2.0-OL6.4+1": "==0" }},
    "linux-jdk21": { packages+: { "devkit:gcc11.2.0-OL6.4+1": "==0" }},
    "linux-jdk-latest": { packages+: { "devkit:gcc13.2.0-OL6.4+1": "==0" }},
    "linux-jdkLatest": self["linux-jdk-latest"],
  },
  local record_file_sizes = ['benchmark', 'file-size:*', '--results-file', 'sizes.json'],
  local upload_file_sizes = ['bench-uploader.py', 'sizes.json'],
  local common_os_deploy = task_spec({
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
    packages+: if (self.os == 'windows') then devkits[std.join('', ["windows-jdk", if (self.jdk_version == 23) then 'Latest' else std.toString(self.jdk_version)])].packages else {} // we can remove self.jdk_version == 23 and add a hidden field isLatest and use it
  }),
  local ruby_vm_build = svm_common + sulong + truffleruby + run_spec.task_spec(vm.custom_vm),
  local graalpy = task_spec(graal_common.deps.graalpy),
  local ruby_python_vm_build = ruby_vm_build + graalpy,
  local full_vm_build = ruby_python_vm_build + task_spec(graal_common.deps.fastr),

  local mx_env = task_spec({
    mx_vm_cmd_suffix:: ['--sources=sdk:GRAAL_SDK,truffle:TRUFFLE_API,compiler:GRAAL,substratevm:SVM', '--debuginfo-dists', '--base-jdk-info=' + self.jdk_name + ':' + self.jdk_version],
    mx_env:: vm.edition,
    mx_vm_common:: vm.mx_cmd_base_no_env + ['--env', self.mx_env] + self.mx_vm_cmd_suffix,
  }),
  local maven_deploy_sdk = ['--suite', 'sdk', 'maven-deploy', '--validate', 'none', '--all-distribution-types', '--with-suite-revisions-metadata'],
  local maven_deploy_sdk_base = maven_deploy_sdk + ['--tags', 'graalvm', vm.binaries_repository],
  local build_base_graalvm_image = task_spec({ run +: [
    self.mx_vm_common + vm.vm_profiles + ['graalvm-show'],
    self.mx_vm_common + vm.vm_profiles + ['build'],
    ['set-export', 'GRAALVM_HOME', self.mx_vm_common + vm.vm_profiles + ['--quiet', '--no-warning', 'graalvm-home']],
  ]}),

  local deploy_artifacts_sdk(base_dist_name=null) = task_spec({ run +:
    (if base_dist_name != null then ['--base-dist-name=' + base_dist_name] else []) + ['--suite', 'sdk', 'deploy-artifacts', '--uploader', if self.os == 'windows' then 'artifact_uploader.cmd' else 'artifact_uploader']
  }),

  local artifact_deploy_sdk_base(base_dist_name=null) = deploy_artifacts_sdk(base_dist_name) + task_spec({ run +:['--tags', 'graalvm']}),

  local deploy_sdk_base(base_dist_name=null) = task_spec({run +:
    [self.mx_vm_common + vm.vm_profiles + maven_deploy_sdk_base, self.mx_vm_common + vm.vm_profiles] //if(self.os_distro == null) then null else self.os_distro
  }) + artifact_deploy_sdk_base(base_dist_name),

  local patch_env = task_spec({ run +:
      # linux
      if (self.os == 'linux') then
        if (self.arch == 'amd64') then [
          # default
        ]
        else if (self.arch == 'aarch64') then [
          ['set-export', 'VM_ENV', self.mx_env + '-aarch64'],
        ]
        else error "arch not found: " + self.arch
      # darwin
      else if (self.os == 'darwin') then
        if (self.arch == 'amd64') then [
          ['set-export', 'VM_ENV', self.mx_env + '-darwin'],
        ]
        else if (self.arch == 'aarch64') then [
          # GR-34811: `ce-darwin-aarch64` can be removed once svml builds
          ['set-export', 'VM_ENV', self.mx_env + '-darwin-aarch64'],
        ]
        else error "arch not found: " + self.arch
      # windows
      else if (self.os == 'windows') then
        if (self.arch == 'amd64') then [
          ['set-export', 'VM_ENV', self.mx_env + '-win'],
        ]
        else error "arch not found: " + self.arch
      else error "os not found: " + self.os
  }),

  local check_base_graalvm_image = task_spec({ run +: [
      ['set-export', 'GRAALVM_DIST', self.mx_vm_common + vm.vm_profiles + ['--quiet', '--no-warning', 'paths', self.mx_vm_common + vm.vm_profiles + ['graalvm-dist-name']]]
    ] + vm.check_graalvm_base_build('$GRAALVM_DIST', self.os, self.arch, self.jdk_version)
  }),

  local deploy_graalvm_base = task_spec(vm.check_structure) + patch_env + check_base_graalvm_image + deploy_sdk_base() + build_base_graalvm_image + task_spec({
      run+: vm.collect_profiles() + [
        self.mx_vm_common + vm.vm_profiles + record_file_sizes,
        upload_file_sizes,
      ],
      notify_groups:: ['deploy'],
      timelimit: "1:00:00"
    },
  ),

  local vm_base(os, arch, main_target, deploy=false, bench=false, os_distro=null, jdk_hint=null) = task_spec(
      vm.default_diskspace_required(os, arch, large=deploy)
      + common['vm_' + os + '_' + arch + (if (os_distro != null) then '_' + os_distro else '') + (if (jdk_hint != null) then '_jdk' + jdk_hint else '')]  # examples: `self.vm_linux_amd64_ubuntu`, `self.vm_windows_amd64_jdkLatest`
      + { targets+: [main_target] + (if (deploy) then ['deploy'] else []) + (if (bench) then ['bench'] else []) }
      + (if (bench) then { capabilities+: ['no_frequency_scaling'] } else {})),

//  option 1
//  local name = task_spec({ // to replace task_spec({name: 'post-merge-deploy-vm-base-java-latest-linux-amd64'}) // option 1
//    java_version:: 'java-' + if self.jdk_name == "jdk-latest" then "latest"
//                   else std.substr(self.jdk_name, 3, std.length(self.jdk_name) - 3),
//    name: std.join('-', self.targets + [self.task_name, std.toString(self.java_version), self.os, self.arch])
//  }),

  local name = task_spec({
    name: self.task_name
  }),

  local timelimit(t) = evaluate_late('laaaate',{
    timelimit:t
  }),

  local notify_groups(group) = task_spec({
    notify_groups:: group
  }),

  local capabilities(caps) = task_spec({
    capabilities+: if(std.type(caps) == 'string') then [caps] else caps
  }),

  local notify_emails(emails) = task_spec({
    notify_emails+: if(std.type(emails) == 'string') then [emails] else emails
  }),

  local deploy_graalvm_espresso = task_spec({
    run: vm.collect_profiles() + (
      if ((self.os == 'linux' || self.os == 'darwin') && self.arch == 'amd64') then [
        ['set-export', 'VM_ENV', "${VM_ENV}-llvm-espresso"],
      ] else [
        ['set-export', 'VM_ENV', "${VM_ENV}-espresso"],
      ]
    ) + build_base_graalvm_image + deploy_sdk_base('espresso') + [
      ['set-export', 'GRAALVM_HOME', self.mx_vm_common + ['--quiet', '--no-warning', 'graalvm-home']],
      ['set-export', 'DACAPO_JAR', self.mx_vm_common + ['--quiet', '--no-warning', 'paths', '--download', 'DACAPO_MR1_2baec49']],
      ['${GRAALVM_HOME}/bin/java', '-jar', '${DACAPO_JAR}', 'luindex'],
    ],
    notify_groups:: ['deploy'],
    timelimit: '1:45:00',
  }),

  local js_windows_common = task_spec({
    environment+: if(self.os == 'windows') then local devkits_version = std.filterMap(function(p) std.startsWith(p, 'devkit:VS'), function(p) std.substr(p, std.length('devkit:VS'), 4), std.objectFields(self.packages))[0];
    {
      DEVKIT_VERSION: devkits_version,
    } else {},
    downloads+: if(self.os == 'windows') then {
      NASM: {name: 'nasm', version: '2.14.02', platformspecific: true},
    } else {},
  }),

  local platform(os, arch, jdk, main_target, deploy=false, bench=false, os_distro=null, jdk_hint=null) = mx_env + platform_spec(no_jobs) + platform_spec({
    // std.join(":", [os, arch, "jdk-latest"]) to get a stucture accepted by platform spec that is os:arch:jdk
    [std.join(":", [os, arch, "jdk" + if (jdk == "latest") then "-latest" else jdk])]: vm_base(os, arch, main_target, deploy, bench, os_distro, jdk_hint) + task_spec(vm['vm_java_' + if (jdk == "latest") then "Latest" else jdk]),
  }),

  local deploy_common_vm_base(os, arch, jdk, main_target, deploy=false, bench=false, os_distro=null, jdk_hint=null) = platform(os, arch, jdk, main_target, deploy, bench, os_distro, jdk_hint) + name + common_os_deploy + deploy_graalvm_base + (if os != 'windows' then full_vm_build else (js_windows_common + svm_common)),

  // linux base
  local deploy_vm_base_java21_linux_amd64 = deploy_common_vm_base('linux', 'amd64', '21', 'weekly', deploy = true) + notify_groups('deploy'),
  local deploy_vm_base_javaLatest_linux_amd64 = deploy_common_vm_base('linux', 'amd64', 'latest', 'post-merge', deploy = true) + notify_groups('deploy'),
  local deploy_vm_base_java21_linux_aarch64 = deploy_common_vm_base('linux', 'aarch64', '21', 'weekly', deploy = true) + capabilities('!xgene3') + timelimit('1:30:00') + notify_groups('deploy'),
  local deploy_vm_base_javaLatest_linux_aarch64 = deploy_common_vm_base('linux', 'aarch64', 'latest', 'daily', deploy = true) + capabilities('!xgene3') + timelimit('1:30:00') + notify_groups('deploy'),

  // windows base
  local deploy_vm_base_java21_windows_amd64 = deploy_common_vm_base('windows', 'amd64', '21', 'weekly', deploy=true, jdk_hint='21') + timelimit('1:30:00') + notify_groups('deploy'),
  local deploy_vm_base_javaLatest_windows_amd64 = deploy_common_vm_base('windows', 'amd64', 'latest', 'daily', deploy=true, jdk_hint='Latest') + timelimit('1:30:00') + notify_groups('deploy'),

  // darwin base
  local deploy_vm_base_java21_darwin_amd64 = deploy_common_vm_base('darwin', 'amd64', '21', 'weekly', deploy=true) + timelimit('1:45:00') + notify_groups('deploy'),
  local deploy_vm_base_javaLatest_darwin_amd64 = deploy_common_vm_base('darwin', 'amd64', 'latest', 'daily', deploy=true) + timelimit('1:45:00') + notify_groups('deploy'),
  local deploy_vm_base_java21_darwin_aarch64 = deploy_common_vm_base('darwin', 'aarch64', '21', 'weekly', deploy=true) + timelimit('1:45:00') + notify_groups('deploy') + notify_emails('bernhard.urban-forster@oracle.com'),
  local deploy_vm_base_javaLatest_darwin_aarch64 = deploy_common_vm_base('darwin', 'aarch64', 'latest', 'daily', deploy=true) + timelimit('1:45:00') + notify_groups('deploy') + notify_emails(['bernhard.urban-forster@oracle.com']),



//  local deploy_vm_espresso_java21_darwin_amd64 = platform('windows', 'amd64', '21', 'weekly', deploy=true, jdk_hint='21') + sulong + svm_common + common_os_deploy + deploy_graalvm_base + name,

  //evaluation of
  local task_dict = {
    // "vm-base": deploy_vm_base_java('linux', 'amd64', 'latest'), can be used with option 1
    "daily-deploy-vm-base-java-latest-windows-amd64": deploy_vm_base_javaLatest_windows_amd64,
  },


  processed_builds::run_spec.process(task_dict),
  builds: self.processed_builds.list,
  assert std.length(self.builds) > 0,
//  assert std.length(self.builds[0].run) > 0
}