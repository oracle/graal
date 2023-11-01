local utils = import '../../../ci/ci_common/common-utils.libsonnet';
local vm = import 'vm.jsonnet';
local vm_common = import '../ci_common/common.jsonnet';

{
  local truffle_unchained = vm_common.svm_common_linux_amd64 + {
    run+: [
      ['export', 'SVM_SUITE=' + vm.svm_suite],
      ['mx', '--env', 'ce', '--native-images=lib:jvmcicompiler', 'gate', '--no-warning-as-error', '--tags', 'build,truffle-unchained'],
    ],
    notify_emails: ["christian.humer@oracle.com", "jakub.chaloupka@oracle.com"],
    timelimit: '30:00',
    name: self.targets[0] + '-vm-ce-truffle-unchained-labs' + self.jdk_name + '-linux-amd64',
  },

  local native_substratevm_truffle = vm_common.svm_common_linux_amd64 + vm.custom_vm_linux + {
    run+: [
      ['export', 'SVM_SUITE=' + vm.svm_suite],
      ['mx', '--dynamicimports', '$SVM_SUITE', '--disable-polyglot', '--disable-libpolyglot', 'gate', '--no-warning-as-error', '--tags', 'build,substratevm' + self.gate_tag_suffix],
    ],
    notify_emails: ["christian.humer@oracle.com", "jakub.chaloupka@oracle.com"],
    timelimit: '40:00',
    name: self.targets[0] + '-vm-native-substratevm-truffle' + self.gate_tag_suffix + '-labs' + self.jdk_name + '-linux-amd64',
  },

  local svm_truffle_tck = vm_common.svm_common_linux_amd64  + {
    run+: [
      ['export', 'SVM_SUITE=' + vm.svm_suite],
      ['mx', '--dynamicimports', '$SVM_SUITE,/tools', '--disable-polyglot', '--skip-libraries=true', '--force-bash-launchers=gu,native-image-configure,polybench', 'gate', '--no-warning-as-error', '--tags', 'build,svm_tck_test,svm_sl_tck'],
    ],
    notify_emails: ["christian.humer@oracle.com", "jakub.chaloupka@oracle.com"],
    timelimit: '35:00',
    name: self.targets[0] + '-vm-svm-truffle-tck-labs' + self.jdk_name + '-linux-amd64',
  },

  local truffle_maven_downloader = vm_common.svm_common_linux_amd64 + vm_common.sulong_linux + {
    run+: [
      ['export', 'SVM_SUITE=' + vm.svm_suite],
      ['mx', '--env', 'ce-llvm', '--native-images=', 'gate', '--no-warning-as-error', '--tags', 'build,maven-downloader'],
    ],
    notify_emails: ["christian.humer@oracle.com", "jakub.chaloupka@oracle.com"],
    timelimit: '30:00',
    packages+: {
      maven: '>=3.3.9',
    },
    name: self.targets[0] + '-vm-ce-truffle-maven-downloader-labs' + self.jdk_name + '-linux-amd64',
  },

  local builds = [
    vm.vm_java_21 + vm_common.svm_common_linux_amd64 + vm_common.sulong_linux + vm_common.graalpython_linux_amd64 + vm.custom_vm_linux + vm_common.gate_vm_linux_amd64 + {
     run+: [
       ['export', 'SVM_SUITE=' + vm.svm_suite],
       ['mx', '--dynamicimports', '$SVM_SUITE,graalpython', '--disable-polyglot', '--disable-libpolyglot', '--force-bash-launchers=lli,native-image', 'gate', '--no-warning-as-error', '--tags', 'build,python'],
     ],
     timelimit: '45:00',
     name: 'gate-vm-native-graalpython-linux-amd64',
    },
    vm.vm_java_21 + vm_common.daily_vm_linux_amd64 + {
      gate_tag_suffix: '',
    } + native_substratevm_truffle,
    vm.vm_java_21 + vm_common.daily_vm_linux_amd64 + {
      gate_tag_suffix: '-quickbuild',
    } + native_substratevm_truffle,
    vm.vm_java_Latest + vm_common.gate_vm_linux_amd64 + {
      gate_tag_suffix: '',
    } + native_substratevm_truffle,
    vm.vm_java_Latest + vm_common.gate_vm_linux_amd64 + {
      gate_tag_suffix: '-quickbuild',
    } + native_substratevm_truffle,
    vm.vm_java_21     + vm_common.daily_vm_linux_amd64 + svm_truffle_tck,
    vm.vm_java_Latest + vm_common.gate_vm_linux_amd64  + svm_truffle_tck,
    vm.vm_java_21     + vm_common.daily_vm_linux_amd64 + truffle_unchained,
    vm.vm_java_Latest + vm_common.gate_vm_linux_amd64  + truffle_unchained,
    vm.vm_java_21     + vm_common.daily_vm_linux_amd64 + truffle_maven_downloader,
    vm.vm_java_Latest + vm_common.gate_vm_linux_amd64  + truffle_maven_downloader,
  ],

  builds: utils.add_defined_in(builds, std.thisFile),
}
