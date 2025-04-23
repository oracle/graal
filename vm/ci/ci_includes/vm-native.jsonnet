local utils = import '../../../ci/ci_common/common-utils.libsonnet';
local vm = import 'vm.jsonnet';
local vm_common = import '../ci_common/common.jsonnet';
local graal_common = import '../../../ci/ci_common/common.jsonnet';

{
  local truffle_native_tck = vm_common.svm_common  + {
    run+: [
      ['mx', '--env', 'ce', '--dynamicimports', '/tools', '--native-images=lib:jvmcicompiler', 'gate', '--tags', 'build,truffle-native-tck,truffle-native-tck-sl'],
    ],
    notify_groups: ["truffle"],
    components+: ["truffletck"],
    timelimit: '35:00',
    name: self.targets[0] + '-vm-truffle-native-tck-labs' + self.jdk_name + '-linux-amd64',
  },

  local truffle_native_tck_wasm = vm_common.svm_common  + {
    run+: [
      ['mx', '--env', 'ce', '--dynamicimports', '/wasm', '--native-images=lib:jvmcicompiler', 'gate', '--tags', 'build,truffle-native-tck-wasm'],
    ],
    notify_groups: ["wasm"],
    components+: ["truffletck"],
    timelimit: '35:00',
    name: self.targets[0] + '-vm-truffle-native-tck-wasm-labs' + self.jdk_name + '-linux-amd64',
  },

  local truffle_maven_downloader = vm_common.svm_common + vm_common.sulong + {
    run+: [
      ['export', 'SVM_SUITE=' + vm.svm_suite],
      ['mx', '--env', 'ce-llvm', '--native-images=', 'gate', '--no-warning-as-error', '--tags', 'build,maven-downloader'],
    ],
    notify_groups: ["truffle"],
    components+: ["truffle"],
    timelimit: '30:00',
    packages+: {
      maven: '>=3.3.9',
    },
    name: self.targets[0] + '-vm-ce-truffle-maven-downloader-labs' + self.jdk_name + '-linux-amd64',
  },

  local builds = [
    vm.vm_java_Latest + vm_common.svm_common + vm_common.sulong + vm_common.graalpy + vm.custom_vm + vm_common.vm_base('linux', 'amd64', 'gate') + {
     run+: [
       ['export', 'SVM_SUITE=' + vm.svm_suite],
       ['mx', '--dynamicimports', '$SVM_SUITE,graalpython', '--disable-polyglot', '--disable-libpolyglot', '--force-bash-launchers=lli,native-image', 'gate', '--no-warning-as-error', '--tags', 'build,python'],
     ],
     timelimit: '45:00',
     name: 'gate-vm-native-graalpython-linux-amd64',
    },
    vm.vm_java_Latest + vm_common.vm_base('linux', 'amd64', 'gate')  + truffle_native_tck,
    vm.vm_java_Latest + vm_common.vm_base('linux', 'amd64', 'gate')  + truffle_native_tck_wasm,
    vm.vm_java_Latest + vm_common.vm_base('linux', 'amd64', 'gate')  + truffle_maven_downloader,
  ],

  builds: utils.add_defined_in(builds, std.thisFile),
}
