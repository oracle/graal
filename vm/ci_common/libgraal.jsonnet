local composable = (import '../../common-utils.libsonnet').composable;
local vm = import '../ci_includes/vm.jsonnet';
local graal_common = import '../../common.jsonnet';
local repo_config = import '../../repo-configuration.libsonnet';
local common_json = composable(import '../../common.json');
local devkits = common_json.devkits;
local c = import 'common.jsonnet';
local g = import '../../compiler/ci_common/gate.jsonnet';

{
  libgraal_build(build_args):: {
    local build_command = if repo_config.graalvm_edition == 'ce' then 'build' else 'build-libgraal-pgo',
    run+: [
      ['mx', '--env', vm.libgraal_env] + ['--extra-image-builder-argument=%s' % arg for arg in build_args] + [build_command]
    ]
  },

  # enable asserts in the JVM building the image and enable asserts in the resulting native image
  libgraal_compiler_base(quickbuild_args=[]):: c.svm_common_linux_amd64 + vm.custom_vm_linux + self.libgraal_build(['-J-esa', '-J-ea', '-esa', '-ea'] + quickbuild_args) + {
    run+: [
      ['mx', '--env', vm.libgraal_env, 'gate', '--task', 'LibGraal Compiler'],
    ],
    timelimit: '1:00:00',
  },

  # enable asserts in the JVM building the image and enable asserts in the resulting native image
  libgraal_compiler:: self.libgraal_compiler_base(),
  # enable economy mode building with the -Ob flag
  libgraal_compiler_quickbuild:: self.libgraal_compiler_base(['-Ob']),


  libgraal_truffle_base(quickbuild_args=[]): c.svm_common_linux_amd64 + vm.custom_vm_linux + self.libgraal_build(['-J-ea', '-ea'] + quickbuild_args) + {
    environment+: {
      # The Truffle TCK tests run as a part of Truffle TCK gate
      TEST_LIBGRAAL_EXCLUDE: 'com.oracle.truffle.tck.tests.*'
    },
    run+: [
      ['mx', '--env', vm.libgraal_env, 'gate', '--task', 'LibGraal Truffle'],
    ],
    logs+: ['*/graal-compiler.log'],
    timelimit: '1:00:00',
  },

  # -ea assertions are enough to keep execution time reasonable
  libgraal_truffle: self.libgraal_truffle_base(),
  # enable economy mode building with the -Ob flag
  libgraal_truffle_quickbuild: self.libgraal_truffle_base(['-Ob']),

  builds:: [
    c.gate_vm_linux_amd64 + self.libgraal_compiler + vm.vm_java_11 + { name: 'gate-vm-libgraal-compiler-11-linux-amd64' },
    c.gate_vm_linux_amd64 + self.libgraal_compiler + vm.vm_java_17 + { name: 'gate-vm-libgraal-compiler-17-linux-amd64' },

    c.gate_vm_linux_amd64 + self.libgraal_truffle + vm.vm_java_11 + { name: 'gate-vm-libgraal-truffle-11-linux-amd64' },
    c.gate_vm_linux_amd64 + self.libgraal_truffle + vm.vm_java_17 + { name: 'gate-vm-libgraal-truffle-17-linux-amd64' },

    c.gate_vm_linux_amd64 + self.libgraal_compiler_quickbuild + vm.vm_java_17 + { name: 'gate-vm-libgraal-compiler-quickbuild-17-linux-amd64' },
    c.gate_vm_linux_amd64 + self.libgraal_truffle_quickbuild + vm.vm_java_17 + { name: 'gate-vm-libgraal-truffle-quickbuild-17-linux-amd64' },

    c.gate_vm_linux_amd64 + self.libgraal_compiler_quickbuild + vm.vm_java_19 + { name: 'gate-vm-libgraal-compiler-quickbuild-19-linux-amd64' },
  ]
}
