local utils = import '../../../ci/ci_common/common-utils.libsonnet';
local vm_common = import '../ci_common/common.jsonnet';
local vm_common_bench = import '../ci_common/common-bench.jsonnet';
local vm = import 'vm.jsonnet';
local vm_bench = import 'vm-bench.jsonnet';
local vm_native = import 'vm-native.jsonnet';
local graal_common = import '../../../ci/ci_common/common.jsonnet';

{
  vm_java_21:: graal_common.labsjdk21 + vm_common.vm_env_mixin('21'),
  vm_java_Latest::
    local jdk = graal_common.labsjdkLatest;
    jdk + vm_common.vm_env_mixin(std.toString(jdk.jdk_version)),

  vm_java_21_llvm:: self.vm_java_21 + graal_common['labsjdk-ce-21-llvm'],
  vm_java_Latest_llvm:: self.vm_java_Latest + graal_common['labsjdk-ce-latest-llvm'],

  binaries_repository: 'lafo',
  maven_deploy_repository: 'lafo-maven',
  edition:: 'ce',
  vm_dir:: 'vm',
  svm_suite:: '/substratevm',
  libgraal_env: 'libgraal',
  custom_vm_linux: {},
  custom_vm_darwin: {},
  custom_vm_windows: {},
  vm_profiles:: [],
  collect_profiles():: [],

  mx_cmd_base_no_env: ['mx'],

  check_structure: {},

  check_graalvm_base_build(path, os, arch, java_version): [],

  vm_setup:: {
    short_name:: $.edition,
    setup+: [
      ['set-export', 'VM_ENV', self.short_name],
      ['set-export', 'RELEASE_CATALOG', 'https://www.graalvm.org/component-catalog/v2/graal-updater-component-catalog-java${BASE_JDK_SHORT_VERSION}.properties|{ee=GraalVM Enterprise Edition}rest://gds.oracle.com/api/20220101/'],
      ['set-export', 'RELEASE_PRODUCT_ID', 'D53FAE8052773FFAE0530F15000AA6C6'],
      ['set-export', 'SNAPSHOT_CATALOG', ['mx', 'urlrewrite', 'http://www.graalvm.org/catalog/ce/java${BASE_JDK_SHORT_VERSION}']],
      ['cd', $.vm_dir],
    ],
  },

  notify_releaser_build: vm_common.vm_base('linux', 'amd64', 'daily', deploy=true) + {
    name: 'daily-deploy-vm-notify-releaser-build-linux-amd64',
    packages+: {
      curl: '>=7.50.1',
      git: '>=1.8.3',
    },
    run+: [
        ['test', ['git', 'rev-parse', '--abbrev-ref', 'HEAD'], '!=', 'master', '||'] + self.ci_resources.infra.notify_releaser_service,
    ],
    runAfter: [
      'post-merge-deploy-vm-base-java-latest-linux-amd64',
      'post-merge-deploy-vm-base-java21-linux-amd64',
      'daily-deploy-vm-installables-standalones-java-latest-linux-amd64',
      'daily-deploy-vm-base-java-latest-linux-aarch64',
      'daily-deploy-vm-installables-standalones-java-latest-linux-aarch64',
      'daily-deploy-vm-base-java-latest-darwin-amd64',
      'daily-deploy-vm-standalones-java-latest-darwin-amd64',
      'daily-deploy-vm-base-java-latest-darwin-aarch64',
      'daily-deploy-vm-standalones-java-latest-darwin-aarch64',
      'daily-deploy-vm-base-java-latest-windows-amd64',
      'daily-deploy-vm-standalones-java-latest-windows-amd64',
      'daily-deploy-vm-maven-linux-amd64',
      'daily-deploy-vm-espresso-java21-linux-amd64',
      'daily-deploy-vm-espresso-java21-linux-aarch64',
      'daily-deploy-vm-espresso-java21-darwin-amd64',
      'daily-deploy-vm-espresso-java21-darwin-aarch64',
      'daily-deploy-vm-espresso-java21-windows-amd64',
    ],
    notify_groups:: ['deploy'],
  },

  default_diskspace_required(os, arch, large): {
    # `os` and `arch` are not yet used
    'diskspace_required': if (large) then '30GB' else '25GB',
  },

  maven_deploy_base_functions: {
    edition:: vm.edition,

    mx_args(os, arch, reduced=false):: ['--native-images=false'],

    dynamic_imports(os, arch)::
      ['--dynamicimports', vm_common.maven_deploy_base_functions.dynamic_ce_imports(os, arch)],

    ee_suites(os, arch)::
      error 'The vm suite does not define ee suites',

    ee_licenses()::
      error 'The vm suite does not define ee licenses',

    reduced_ce_dists:: error 'The vm suite does not define reduced dists',
    reduced_ee_dists:: error 'The vm suite does not define reduced dists',
  },

  local builds = [
    utils.add_gate_predicate(self.vm_java_21 + vm_common.vm_base('linux', 'amd64', 'gate') + {
     run: [
       ['mx', 'build'],
       ['mx', 'unittest', '--suite', 'vm'],
     ],
     name: 'gate-vm-unittest-linux-amd64',
    }, ['sdk', 'truffle', 'vm']),
    utils.add_gate_predicate(self.vm_java_21 + graal_common.devkits['windows-jdk21'] + vm_common.vm_base('windows', 'amd64', 'gate') + {
     run: [
         ['mx', 'build'],
         ['mx', 'unittest', '--suite', 'vm'],
     ],
     name: 'gate-vm-unittest-windows-amd64',
    }, ["sdk", "truffle", "vm"]),
    self.vm_java_21 + vm_common.vm_base('linux', 'amd64', 'gate') + vm_common.sulong_linux + {
     environment+: {
       DYNAMIC_IMPORTS: '/tools,/substratevm,/sulong',
       NATIVE_IMAGES: 'polyglot',
     },
     run: [
       ['rm', '-rf', '../.git'],
       ['mx', 'gate', '--strict-mode', '--tags', 'build'],
     ],
     name: 'gate-vm-build-without-vcs-linux-amd64',
    },

    # Linux/AMD64
    vm_common.graalvm_complete_build_deps('ce', 'linux', 'amd64', java_version='latest') + vm_common.linux_deploy + vm_common.vm_base('linux', 'amd64', 'gate') + vm_common.maven_deploy_base_functions.base_object('linux', 'amd64', dry_run=true, remote_mvn_repo=$.maven_deploy_repository, remote_non_mvn_repo=$.binaries_repository, local_repo='local') + {
      name: 'gate-vm-maven-dry-run-linux-amd64',
      timelimit: '1:00:00',
    },
    vm_common.graalvm_complete_build_deps('ce', 'linux', 'amd64', java_version='latest') + vm_common.linux_deploy + vm_common.vm_base('linux', 'amd64', 'daily', deploy=true) + vm_common.maven_deploy_base_functions.base_object('linux', 'amd64', dry_run=false, remote_mvn_repo=$.maven_deploy_repository, remote_non_mvn_repo=$.binaries_repository, local_repo='local') + {
      name: 'daily-deploy-vm-maven-linux-amd64',
      timelimit: '1:00:00',
      notify_groups:: ['deploy'],
    },
    # Linux/AARCH64
    vm_common.graalvm_complete_build_deps('ce', 'linux', 'aarch64', java_version='latest') + vm_common.linux_deploy + vm_common.vm_base('linux', 'aarch64', 'gate') + vm_common.maven_deploy_base_functions.base_object('linux', 'aarch64', dry_run=true, remote_mvn_repo=$.maven_deploy_repository, remote_non_mvn_repo=$.binaries_repository, local_repo='local') + {
      name: 'gate-vm-maven-dry-run-linux-aarch64',
      timelimit: '1:00:00',
    },
    vm_common.graalvm_complete_build_deps('ce', 'linux', 'aarch64', java_version='latest') + vm_common.linux_deploy + vm_common.vm_base('linux', 'aarch64', 'daily', deploy=true) + vm_common.maven_deploy_base_functions.base_object('linux', 'aarch64', dry_run=false, remote_mvn_repo=$.maven_deploy_repository, remote_non_mvn_repo=$.binaries_repository, local_repo='local') + {
      name: 'daily-deploy-vm-maven-linux-aarch64',
      timelimit: '1:00:00',
      notify_groups:: ['deploy'],
    },
    # Darwin/AMD64
    vm_common.graalvm_complete_build_deps('ce', 'darwin', 'amd64', java_version='latest') + vm_common.darwin_deploy + vm_common.vm_base('darwin', 'amd64', 'gate', jdk_hint='Latest') + vm_common.maven_deploy_base_functions.base_object('darwin', 'amd64', dry_run=true, remote_mvn_repo=$.maven_deploy_repository, remote_non_mvn_repo=$.binaries_repository, local_repo='local') + {
      name: 'gate-vm-maven-dry-run-darwin-amd64',
      timelimit: '1:00:00',
    },
    vm_common.graalvm_complete_build_deps('ce', 'darwin', 'amd64', java_version='latest') + vm_common.darwin_deploy + vm_common.vm_base('darwin', 'amd64', 'daily', deploy=true, jdk_hint='Latest') + vm_common.maven_deploy_base_functions.base_object('darwin', 'amd64', dry_run=false, remote_mvn_repo=$.maven_deploy_repository, remote_non_mvn_repo=$.binaries_repository, local_repo='local') + {
      name: 'daily-deploy-vm-maven-darwin-amd64',
      timelimit: '1:00:00',
      notify_groups:: ['deploy'],
    },
    # Darwin/AARCH64
    vm_common.graalvm_complete_build_deps('ce', 'darwin', 'aarch64', java_version='latest') + vm_common.darwin_deploy + vm_common.vm_base('darwin', 'aarch64', 'gate') + vm_common.maven_deploy_base_functions.base_object('darwin', 'aarch64', dry_run=true, remote_mvn_repo=$.maven_deploy_repository, remote_non_mvn_repo=$.binaries_repository, local_repo='local') + {
      name: 'gate-vm-maven-dry-run-darwin-aarch64',
      timelimit: '1:00:00',
    },
    vm_common.graalvm_complete_build_deps('ce', 'darwin', 'aarch64', java_version='latest') + vm_common.darwin_deploy + vm_common.vm_base('darwin', 'aarch64', 'daily', deploy=true) + vm_common.maven_deploy_base_functions.base_object('darwin', 'aarch64', dry_run=false, remote_mvn_repo=$.maven_deploy_repository, remote_non_mvn_repo=$.binaries_repository, local_repo='local') + {
      name: 'daily-deploy-vm-maven-darwin-aarch64',
      timelimit: '1:00:00',
      notify_groups:: ['deploy'],
    },
    # Windows/AMD64
    vm_common.graalvm_complete_build_deps('ce', 'windows', 'amd64', java_version='latest') + vm_common.deploy_build + vm_common.vm_base('windows', 'amd64', 'gate') + vm_common.maven_deploy_base_functions.base_object('windows', 'amd64', dry_run=true, remote_mvn_repo=$.maven_deploy_repository, remote_non_mvn_repo=$.binaries_repository, local_repo='local') + {
      name: 'gate-vm-maven-dry-run-windows-amd64',
      timelimit: '1:00:00',
    },
    vm_common.graalvm_complete_build_deps('ce', 'windows', 'amd64', java_version='latest') + vm_common.deploy_build + vm_common.vm_base('windows', 'amd64', 'daily', deploy=true, jdk_hint='21') + vm_common.maven_deploy_base_functions.base_object('windows', 'amd64', dry_run=false, remote_mvn_repo=$.maven_deploy_repository, remote_non_mvn_repo=$.binaries_repository, local_repo='local') + {
      name: 'daily-deploy-vm-maven-windows-amd64',
      timelimit: '1:00:00',
      notify_groups:: ['deploy'],
    },

    #
    # Update the `stable` mx branch with the currently imported revision
    #
    vm_common.vm_base('linux', 'amd64', 'post-merge') + {
      run: [
        ['set-export', 'BRANCH_NAME', ['git', 'rev-parse', '--abbrev-ref', 'HEAD']],
        ['bash', '-c', 'if [[ ${BRANCH_NAME} == master ]] || [[ ${BRANCH_NAME} == release/* ]] || [[ ${BRANCH_NAME} == cpu/* ]]; then git -C ${MX_HOME} push origin +HEAD:refs/heads/graal/${BRANCH_NAME}; fi']
      ],
      name: 'post-merge-vm-update-stable-mx-branch-linux-amd64',
      notify_groups:: ['deploy'],
    },


    #
    # Deploy GraalVM Base and Installables
    # NOTE: After adding or removing deploy jobs, please make sure you modify ce-release-artifacts.json accordingly.
    #

    # Linux/AMD64
    # - JDK-Latest
    vm_common.deploy_vm_base_javaLatest_linux_amd64,
    vm_common.deploy_vm_installables_standalones_javaLatest_linux_amd64,
    # - JDK21
    vm_common.deploy_vm_base_java21_linux_amd64,
    vm_common.deploy_vm_installables_standalones_java21_linux_amd64,

    # Linux/AARCH64
    # - JDK-Latest
    vm_common.deploy_vm_base_javaLatest_linux_aarch64,
    vm_common.deploy_vm_installables_standalones_javaLatest_linux_aarch64,
    # - JDK21
    vm_common.deploy_vm_base_java21_linux_aarch64,
    vm_common.deploy_vm_installables_standalones_java21_linux_aarch64,

    # Darwin/AMD64
    # - JDK-Latest
    vm_common.deploy_vm_base_javaLatest_darwin_amd64,
    vm_common.deploy_vm_standalones_javaLatest_darwin_amd64,
    # - JDK21
    vm_common.deploy_vm_base_java21_darwin_amd64,
    vm_common.deploy_vm_installables_java21_darwin_amd64,
    vm_common.deploy_vm_standalones_java21_darwin_amd64,

    # Darwin/AARCH64
    # - JDK-Latest
    vm_common.deploy_vm_base_javaLatest_darwin_aarch64,
    vm_common.deploy_vm_standalones_javaLatest_darwin_aarch64,
    # - JDK21
    vm_common.deploy_vm_base_java21_darwin_aarch64,
    vm_common.deploy_vm_installables_java21_darwin_aarch64,
    vm_common.deploy_vm_standalones_java21_darwin_aarch64,

    # Windows/AMD64
    # - JDK-Latest
    vm_common.deploy_vm_base_javaLatest_windows_amd64,
    vm_common.deploy_vm_standalones_javaLatest_windows_amd64,
    # - JDK21
    vm_common.deploy_vm_base_java21_windows_amd64,
    vm_common.deploy_vm_installables_java21_windows_amd64,
    vm_common.deploy_vm_standalones_java21_windows_amd64,

    #
    # Deploy the GraalVM Espresso image (GraalVM Base + espresso)
    #
    vm_common.deploy_vm_espresso_java21_linux_amd64,
    vm_common.deploy_vm_espresso_java21_linux_aarch64,
    vm_common.deploy_vm_espresso_java21_darwin_amd64,
    vm_common.deploy_vm_espresso_java21_darwin_aarch64,
    vm_common.deploy_vm_espresso_java21_windows_amd64,

    # Trigger the releaser service
    self.notify_releaser_build,
  ],

  builds: [vm_common.verify_name(b) for b in vm_common.builds + vm_common_bench.builds + vm_bench.builds + vm_native.builds + utils.add_defined_in(builds, std.thisFile)],

  compiler_gate:: (import '../../../compiler/ci/ci_common/gate.jsonnet')
}
