local vm = import 'vm.jsonnet';
local vm_common = import '../ci_common/common.jsonnet';

{
  local builds = [
    vm.vm_java_21 + vm_common.svm_common_linux_amd64 + vm.custom_vm_linux + vm_common.gate_vm_linux_amd64 + {
     run+: [
       ['export', 'SVM_SUITE=' + vm.svm_suite],
       ['mx', '--dynamicimports', '$SVM_SUITE', '--disable-polyglot', '--disable-libpolyglot', 'gate', '--no-warning-as-error', '--tags', 'build,substratevm'],
     ],
     timelimit: '40:00',
     name: 'gate-vm-native-substratevm-truffle-linux-amd64',
    },
    vm.vm_java_21 + vm_common.svm_common_linux_amd64 + vm.custom_vm_linux + vm_common.gate_vm_linux_amd64 + {
      run+: [
        ['export', 'SVM_SUITE=' + vm.svm_suite],
        ['mx', '--dynamicimports', '$SVM_SUITE', '--disable-polyglot', '--disable-libpolyglot', 'gate', '--no-warning-as-error', '--tags', 'build,substratevm-quickbuild'],
      ],
      timelimit: '40:00',
      name: 'gate-vm-native-substratevm-truffle-quickbuild-linux-amd64',
    },
    vm.vm_java_21 + vm_common.svm_common_linux_amd64 + vm_common.sulong_linux + vm_common.graalpython_linux_amd64 + vm.custom_vm_linux + vm_common.gate_vm_linux_amd64 + {
     run+: [
       ['export', 'SVM_SUITE=' + vm.svm_suite],
       ['mx', '--dynamicimports', '$SVM_SUITE,graalpython', '--disable-polyglot', '--disable-libpolyglot', '--force-bash-launchers=lli,native-image', 'gate', '--no-warning-as-error', '--tags', 'build,python'],
     ],
     timelimit: '45:00',
     name: 'gate-vm-native-graalpython-linux-amd64',
    },
    vm.vm_java_21 + vm_common.svm_common_linux_amd64 + vm_common.gate_vm_linux_amd64 + {
     run+: [
       ['export', 'SVM_SUITE=' + vm.svm_suite],
       ['mx', '--dynamicimports', '$SVM_SUITE,/tools', '--disable-polyglot', '--skip-libraries=true', '--force-bash-launchers=gu,native-image-configure,polybench', 'gate', '--no-warning-as-error', '--tags', 'build,svm_tck_test,svm_sl_tck'],
     ],
     timelimit: '35:00',
     name: 'gate-vm-svm-truffle-tck-linux-amd64',
    },
    vm.vm_java_21 + vm_common.svm_common_linux_amd64 + vm_common.sulong_linux + vm_common.gate_vm_linux_amd64 + {
     run+: [
       ['export', 'SVM_SUITE=' + vm.svm_suite],
       ['mx', '--env', 'ce-llvm', '--native-images=', 'gate', '--no-warning-as-error', '--tags', 'build,maven-downloader'],
     ],
     timelimit: '30:00',
     name: 'gate-vm-ce-truffle-maven-downloader-linux-amd64',
     packages+: {
       maven: '>=3.3.9',
     },
    },
  ],

  builds: [{'defined_in': std.thisFile} + b  for b in builds],
}
