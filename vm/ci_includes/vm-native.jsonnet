local vm = import 'vm.jsonnet';
local vm_common = import '../ci_common/common.jsonnet';

{
  builds: [
    vm.vm_java_17 + vm_common.svm_common_linux_amd64 + vm.custom_vm_linux + vm_common.gate_vm_linux_amd64 + {
      run+: [
        ['export', 'SVM_SUITE=' + vm.svm_suite],
        ['mx', '--dynamicimports', '$SVM_SUITE', '--disable-polyglot', '--disable-libpolyglot', 'gate', '--no-warning-as-error', '--tags', 'build,substratevm'],
      ],
      timelimit: '15:00',
      name: 'gate-vm-native-substratevm-truffle',
    },
    vm.vm_java_17 + vm_common.svm_common_linux_amd64 + vm_common.sulong_linux + vm_common.graalpython_linux + vm.custom_vm_linux + vm_common.gate_vm_linux_amd64 + {
      run+: [
        ['export', 'SVM_SUITE=' + vm.svm_suite],
        ['mx', '--dynamicimports', '$SVM_SUITE,graalpython', '--disable-polyglot', '--disable-libpolyglot', '--force-bash-launchers=lli,native-image', 'gate', '--no-warning-as-error', '--tags', 'build,python'],
      ],
      timelimit: '45:00',
      name: 'gate-vm-native-graalpython',
    },
    vm.vm_java_17 + vm_common.svm_common_linux_amd64 + vm_common.gate_vm_linux_amd64 + {
      run+: [
        ['export', 'SVM_SUITE=' + vm.svm_suite],
        ['mx', '--dynamicimports', '$SVM_SUITE,/tools', '--disable-polyglot', '--skip-libraries=true', '--force-bash-launchers=gu,native-image-configure', 'gate', '--no-warning-as-error', '--tags', 'build,svm_sl_tck'],
      ],
      timelimit: '35:00',
      name: 'gate-svm-truffle-tck',
    },
  ],
}