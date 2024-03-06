local vm = import '../ci_includes/vm.jsonnet';
local common = import 'common.jsonnet';
local run_spec   = import "../../../ci/ci_common/run-spec.libsonnet";
local exclude    = run_spec.exclude;
local graal_common = import '../../../ci/ci_common/common.jsonnet';

local task_spec = run_spec.task_spec;
local platform_spec = run_spec.platform_spec;
local evaluate_late = run_spec.evaluate_late;
{

  local no_jobs = {
    "*"+: exclude,
  },
  local deploy_graalvm_base(java_version) = task_spec(vm.check_structure + evaluate_late({execute:{
    run: common.patch_env(self.os, self.arch, java_version) + vm.collect_profiles() + common.build_base_graalvm_image + [
      common.mx_vm_common + vm.vm_profiles + common.record_file_sizes,
      common.upload_file_sizes,
    ] + common.deploy_sdk_base(self.os) + common.check_base_graalvm_image(self.os, self.arch, java_version),
    notify_groups:: ['deploy'],
    timelimit: "1:00:00"}},)
  ),

  local vm_base(os, arch, main_target, deploy=false, bench=false, os_distro=null, jdk_hint=null) = task_spec(
      vm.default_diskspace_required(os, arch, large=deploy)
      + common['vm_' + os + '_' + arch + (if (os_distro != null) then '_' + os_distro else '') + (if (jdk_hint != null) then '_jdk' + jdk_hint else '')]  # examples: `self.vm_linux_amd64_ubuntu`, `self.vm_windows_amd64_jdkLatest`
      + { targets+: [main_target] + (if (deploy) then ['deploy'] else []) + (if (bench) then ['bench'] else []) }
      + (if (bench) then { capabilities+: ['no_frequency_scaling'] } else {})),

  local platform(os, arch, jdk) = platform_spec({
    // std.join(":", [os, arch, "jdk-latest"]) to get a stucture accepted by platform spec that is os:arch:jdk
    [std.join(":", [os, arch, "jdk" + if (jdk == "latest") then "-latest" else jdk])]: vm_base(os, arch, 'post-merge', deploy=true) + task_spec(vm['vm_java_' + if (jdk == "latest") then "Latest" else jdk]),
  }),

  local deploy_vm_base_java21_linux_amd64 = platform_spec(no_jobs) + platform('linux', 'amd64', '21') + common.full_vm_build + common.linux_deploy + deploy_graalvm_base('jdk21')
    + task_spec({name: 'post-merge-deploy-vm-base-java-latest-linux-amd64', notify_groups:: ["deploy"]}),


  local deploy_vm_base_javalatest_linux_amd64 = platform_spec(no_jobs) + platform('linux', 'amd64', 'latest') + common.full_vm_build + common.linux_deploy + deploy_graalvm_base('jdklatest')
    + task_spec({name: 'post-merge-deploy-vm-base-java-latest-linux-amd64', notify_groups:: ["deploy"]}),

  //evaluation of
  local task_dict = {
    "build": deploy_vm_base_javalatest_linux_amd64,
  },

  processed_builds::run_spec.process(task_dict),
  builds: self.processed_builds.list,
  assert std.length(self.builds) > 0
}