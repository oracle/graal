local composable = (import '../../common-utils.libsonnet').composable;
local vm = import '../ci_includes/vm.jsonnet';
local graal_common = import '../../common.jsonnet';
local repo_config = import '../../repo-configuration.libsonnet';
local common_json = composable(import '../../common.json');
local devkits = common_json.devkits;
local c = import 'common.jsonnet';
local g = vm.compiler_gate;
local utils = import '../../common-utils.libsonnet';

{
  local underscore(s) = std.strReplace(s, "-", "_"),
  local os(os_arch) = std.split(os_arch, "-")[0],
  local arch(os_arch) = std.split(os_arch, "-")[1],

  libgraal_build(build_args):: {
    local build_command = if repo_config.graalvm_edition == 'ce' then 'build' else 'build-libgraal-pgo',
    run+: [
      ['mx', '--env', vm.libgraal_env] + ['--extra-image-builder-argument=%s' % arg for arg in build_args] + [build_command]
    ]
  },

  # enable asserts in the JVM building the image and enable asserts in the resulting native image
  libgraal_compiler_base(quickbuild_args=[]):: self.libgraal_build(['-J-esa', '-J-ea', '-esa', '-ea'] + quickbuild_args) + {
    run+: [
      ['mx', '--env', vm.libgraal_env, 'gate', '--task', 'LibGraal Compiler'],
    ],
    timelimit: '1:00:00',
  },

  # enable asserts in the JVM building the image and enable asserts in the resulting native image
  libgraal_compiler:: self.libgraal_compiler_base(),
  # enable economy mode building with the -Ob flag
  libgraal_compiler_quickbuild:: self.libgraal_compiler_base(['-Ob']),

  libgraal_truffle_base(quickbuild_args=[]): self.libgraal_build(['-J-ea', '-ea'] + quickbuild_args) + {
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

  # See definition of `gates` local variable in ../../compiler/ci_common/gate.jsonnet
  local gates = {
    "gate-vm-libgraal_compiler-labsjdk-11-linux-amd64": {},
    "gate-vm-libgraal_compiler-labsjdk-17-linux-amd64": {},
    "gate-vm-libgraal_truffle-labsjdk-11-linux-amd64": {},
    "gate-vm-libgraal_truffle-labsjdk-17-linux-amd64": {},
    "gate-vm-libgraal_compiler_quickbuild-labsjdk-17-linux-amd64": {},
    "gate-vm-libgraal_compiler_quickbuild-labsjdk-19-linux-amd64": {},
    "gate-vm-libgraal_truffle_quickbuild-labsjdk-17-linux-amd64": {},
  },

  # See definition of `weeklies` local variable in ../../compiler/ci_common/gate.jsonnet
  local weeklies = {},

  # See definition of `monthlies` local variable in ../../compiler/ci_common/gate.jsonnet
  local monthlies = {},

  local svm_common(os_arch, jdk) =
    local obj = c["svm_common_" + underscore(os_arch)];
    if std.type(obj) == "function" then obj(jdk) else obj,

  # Builds run on all platforms (platform = JDK + OS + ARCH)
  local all_platforms_builds = [
    c["gate_vm_" + underscore(os_arch)] +
    svm_common(os_arch, jdk) +
    vm["custom_vm_" + os(os_arch)] +
    g.make_build(jdk, os_arch, task, extra_tasks=self, suite="vm",
                 include_common_os_arch=false,
                 gates_manifest=gates,
                 weeklies_manifest=weeklies,
                 monthlies_manifest=monthlies).build +
    vm["vm_java_" + jdk]
    for jdk in [
      "11",
      "17",
      "19"
    ]
    for os_arch in [
      "linux-amd64",
      "linux-aarch64",
      "darwin-amd64",
      "darwin-aarch64",
      "windows-amd64"
    ]
    for task in [
      "libgraal_compiler",
      "libgraal_truffle",
      "libgraal_compiler_quickbuild",
      "libgraal_truffle_quickbuild"
    ]
  ],

  # Complete set of builds defined in this file
  local all_builds = all_platforms_builds,

  builds: if
      g.check_manifest(gates, all_builds, std.thisFile, "gates").result
    then
      local conf = repo_config.vm.libgraal_predicate_conf;
      [utils.add_gate_predicate(b, suites=conf.suites, extra_excludes=conf.extra_excludes) for b in all_builds]
}
