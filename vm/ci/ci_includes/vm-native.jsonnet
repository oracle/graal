local utils = import '../../../ci/ci_common/common-utils.libsonnet';
local vm = import 'vm.jsonnet';
local vm_common = import '../ci_common/common.jsonnet';
local graal_common = import '../../../ci/ci_common/common.jsonnet';

{
  local truffle_native_tck = graal_common.deps.svm + {
    run+: [
      ['mx', '--env', 'ce', '--dynamicimports', '/tools', '--native-images=lib:jvmcicompiler', 'gate', '--tags', 'build,truffle-native-tck,truffle-native-tck-sl'],
    ],
    notify_groups: ["truffle"],
    components+: ["truffletck"],
    timelimit: '35:00',
    name: 'gate-vm-truffle-native-tck-labs' + self.jdk_name + '-linux-amd64',
    logs+: [
      "*/call_tree.txt.gz"
    ]
  },

  local truffle_native_tck_wasm = graal_common.deps.svm + {
    run+: [
      ['mx', '--env', 'ce', '--dynamicimports', '/wasm', '--native-images=lib:jvmcicompiler', 'gate', '--tags', 'build,truffle-native-tck-wasm'],
    ],
    notify_groups: ["wasm"],
    components+: ["truffletck"],
    timelimit: '35:00',
    name: 'gate-vm-truffle-native-tck-wasm-labs' + self.jdk_name + '-linux-amd64',
  },

  local truffle_maven_downloader = graal_common.deps.svm + graal_common.deps.sulong + {
    run+: [
      ['mx', '--env', 'ce-llvm', '--native-images=', 'gate', '--no-warning-as-error', '--tags', 'build,maven-downloader'],
    ],
    notify_groups: ["truffle"],
    components+: ["truffle"],
    timelimit: '30:00',
    packages+: {
      maven: '==3.5.3',
    },
    name: 'gate-vm-ce-truffle-maven-downloader-labs' + self.jdk_name + '-linux-amd64',
  },

  local builds = [
    vm.vm_java_Latest + graal_common.deps.svm + graal_common.deps.sulong + graal_common.deps.graalpy + vm.custom_vm + vm_common.vm_base('linux', 'amd64', 'tier3') + {
     run+: [
       ['mx', '--env', vm.edition, '--native-images=true', '--dy', 'graalpython', 'gate', '-B--targets=GRAALPY_NATIVE_STANDALONE', '--no-warning-as-error', '--tags', 'build,python'],
     ],
     notify_groups: ["python"],
     timelimit: '45:00',
     name: 'gate-vm-native-graalpython-linux-amd64',
    },
    vm.vm_java_Latest + vm_common.vm_base('linux', 'amd64', 'tier3')  + truffle_native_tck,
    vm.vm_java_Latest + vm_common.vm_base('linux', 'amd64', 'tier3')  + truffle_native_tck_wasm,
    vm.vm_java_Latest + vm_common.vm_base('linux', 'amd64', 'tier3')  + truffle_maven_downloader,
  ],

  builds: utils.add_defined_in(builds, std.thisFile),
}
