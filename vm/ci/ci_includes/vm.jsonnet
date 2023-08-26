local utils = import '../../../ci/ci_common/common-utils.libsonnet';
local vm_common = import '../ci_common/common.jsonnet';
local vm_common_bench = import '../ci_common/common-bench.jsonnet';
local vm = import 'vm.jsonnet';
local vm_bench = import 'vm-bench.jsonnet';
local vm_native = import 'vm-native.jsonnet';
local graal_common = import '../../../ci/ci_common/common.jsonnet';

{
  vm_java_21:: graal_common.labsjdk21 + vm_common.vm_env_mixin('21'),
  vm_java_22:: graal_common.oraclejdk22 + vm_common.vm_env_mixin('22'),

  vm_java_21_llvm:: self.vm_java_21 + graal_common['labsjdk-ce-21-llvm'],

  binaries_repository: 'lafo',
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

  check_graalvm_complete_build(mx_command_base, os, arch, java_version): [],

  vm_setup:: {
    short_name:: 'ce',
    setup+: [
      ['set-export', 'VM_ENV', 'ce'],
      ['set-export', 'RELEASE_CATALOG', 'https://www.graalvm.org/component-catalog/v2/graal-updater-component-catalog-java${BASE_JDK_SHORT_VERSION}.properties|{ee=GraalVM Enterprise Edition}rest://gds.oracle.com/api/20220101/'],
      ['set-export', 'RELEASE_PRODUCT_ID', 'D53FAE8052773FFAE0530F15000AA6C6'],
      ['set-export', 'SNAPSHOT_CATALOG', ['mx', 'urlrewrite', 'http://www.graalvm.org/catalog/ce/java${BASE_JDK_SHORT_VERSION}']],
      ['cd', 'vm'],
    ],
  },

  maven_17_21:: {
    downloads+: {
      JAVA_HOME: graal_common.jdks_data['labsjdk-ce-17'],
      EXTRA_JAVA_HOMES: graal_common.jdks_data['labsjdk-ce-21'],
    },
    mx_cmd_base:: ['mx', '--dynamicimports', '/tools,/compiler,/graal-js,/espresso,/substratevm', '--disable-installables=true', '--force-bash-launcher=true', '--skip-libraries=true'],
    build:: self.mx_cmd_base + ['build'],
    deploy(repo, dry_run=false):: self.mx_cmd_base + ['--suite', 'compiler', '--suite', 'truffle', '--suite', 'sdk', '--suite', 'tools', '--suite', 'regex', '--suite', 'graal-js', '--suite', 'espresso', '--suite', 'substratevm', 'maven-deploy', '--tags=default', '--all-distribution-types', '--validate', 'full', '--licenses', 'GPLv2-CPE,GPLv2,UPL,MIT,ICU'] +
        (if dry_run then ['--dry-run'] else []) +
        (if repo == null then [] else [repo]),
  },

  maven_17_21_only_native:: self.maven_17_21 + {
    native_distributions:: 'TRUFFLE_NFI_NATIVE,SVM_HOSTED_NATIVE',
    mx_cmd_base:: ['mx', '--dynamicimports', '/substratevm', '--disable-installables=true', '--force-bash-launcher=true', '--skip-libraries=true'],
    build:: self.mx_cmd_base + ['build', '--dependencies', self.native_distributions],
    deploy(repo, dry_run=false):: self.mx_cmd_base + ['maven-deploy', '--only', self.native_distributions, '--tags=default', '--all-suites', '--all-distribution-types', '--validate', 'full', '--licenses', 'GPLv2-CPE,UPL,MIT'] +
        (if dry_run then ['--dry-run'] else []) +
        (if repo == null then [] else [repo]),
  },

  notify_releaser_build: vm_common.common_vm_linux + graal_common.linux_amd64 + {
    name: 'daily-vm-notify-releaser-build-linux-amd64',
    packages+: {
      curl: '>=7.50.1',
      git: '>=1.8.3',
    },
    run+: [
        ['test', ['git', 'rev-parse', '--abbrev-ref', 'HEAD'], '!=', 'master', '||'] + self.ci_resources.infra.notify_releaser_service,
    ],
    requireArtifacts: [
      {name: 'post-merge-deploy-vm-java21-linux-amd64'},
      {name: 'daily-deploy-vm-java21-linux-aarch64'},
      {name: 'daily-deploy-vm-base-java21-darwin-amd64'},
      {name: 'daily-deploy-vm-installable-java21-darwin-amd64'},
      {name: 'daily-deploy-vm-base-java21-darwin-aarch64'},
      {name: 'daily-deploy-vm-installable-java21-darwin-aarch64'},
      {name: 'daily-deploy-vm-base-java21-windows-amd64'},
      {name: 'daily-deploy-vm-installable-java21-windows-amd64'},
      {name: 'daily-deploy-vm-ruby-java21-linux-amd64'},
      {name: 'daily-deploy-vm-ruby-java21-darwin-amd64'},
      {name: 'daily-deploy-vm-ruby-java21-darwin-aarch64'},
      {name: 'daily-deploy-vm-espresso-java21-linux-amd64'},
      {name: 'daily-deploy-vm-espresso-java21-linux-aarch64'},
      {name: 'daily-deploy-vm-espresso-java21-darwin-amd64'},
      {name: 'daily-deploy-vm-espresso-java21-darwin-aarch64'},
      {name: 'daily-deploy-vm-espresso-java21-windows-amd64'},
    ],
    targets+: ['daily'],
    notify_groups:: ['deploy'],
  },

  deploy_vm_publish_releaser_artifact(build): build + {
    publishArtifacts: [
      {
        name: build.name,
        patterns: [build.name]
      }
    ]
  },

  diskspace_required: {
    java21_linux_amd64: "30GB",
  },

  local builds = [
    utils.add_gate_predicate(self.vm_java_21 + vm_common.gate_vm_linux_amd64 + {
     run: [
       ['mx', 'build'],
       ['mx', 'unittest', '--suite', 'vm'],
     ],
     name: 'gate-vm-unittest-linux-amd64',
    }, ['sdk', 'truffle', 'vm']),
    utils.add_gate_predicate(self.vm_java_21 + graal_common.devkits['windows-jdk21'] + vm_common.gate_vm_windows_amd64 + {
     run: [
         ['mx', 'build'],
         ['mx', 'unittest', '--suite', 'vm'],
     ],
     name: 'gate-vm-unittest-windows-amd64',
    }, ["sdk", "truffle", "vm"]),
    self.vm_java_21 + vm_common.gate_vm_linux_amd64 + vm_common.sulong_linux + {
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
    vm_common.linux_deploy + vm_common.gate_vm_linux_amd64 + self.maven_17_21 + vm_common.sulong_linux + {
     run: [ $.maven_17_21.build, $.maven_17_21.deploy(repo='lafo-maven', dry_run=true)],
     name: 'gate-vm-maven-dry-run-linux-amd64',
    },
    vm_common.linux_deploy + vm_common.deploy_vm_linux_amd64 + self.maven_17_21 + vm_common.sulong_linux + {
     run: [ $.maven_17_21.build, $.maven_17_21.deploy(repo='lafo-maven')],
     name: 'post-merge-deploy-vm-maven-linux-amd64',
     timelimit: '45:00',
     notify_groups:: ['deploy'],
    },
    vm_common.linux_deploy + vm_common.gate_vm_linux_aarch64 + self.maven_17_21_only_native + {
     run: [ $.maven_17_21_only_native.build, $.maven_17_21_only_native.deploy(repo='lafo-maven', dry_run=true)],
     name: 'gate-vm-maven-dry-run-linux-aarch64',
    },
    vm_common.linux_deploy + vm_common.deploy_vm_linux_aarch64 + self.maven_17_21_only_native + {
     run: [ $.maven_17_21_only_native.build, $.maven_17_21_only_native.deploy(repo='lafo-maven')],
     name: 'post-merge-deploy-vm-maven-linux-aarch64',
     notify_groups:: ['deploy'],
    },
    vm_common.darwin_deploy + vm_common.gate_vm_darwin_amd64 + self.maven_17_21_only_native + {
     run: [ $.maven_17_21_only_native.build, $.maven_17_21_only_native.deploy(repo='lafo-maven', dry_run=true)],
     name: 'gate-vm-maven-dry-run-darwin-amd64',
    },
    vm_common.darwin_deploy + vm_common.gate_vm_darwin_aarch64 + self.maven_17_21_only_native + {
     run: [ $.maven_17_21_only_native.build, $.maven_17_21_only_native.deploy(repo='lafo-maven', dry_run=true)],
     name: 'gate-vm-maven-dry-run-darwin-aarch64',
    },
    vm_common.darwin_deploy + vm_common.deploy_daily_vm_darwin_amd64 + self.maven_17_21_only_native + {
     run: [ $.maven_17_21_only_native.build, $.maven_17_21_only_native.deploy(repo='lafo-maven')],
     name: 'daily-deploy-vm-maven-darwin-amd64',
     notify_groups:: ['deploy'],
    },
    vm_common.darwin_deploy + vm_common.deploy_daily_vm_darwin_aarch64 + self.maven_17_21_only_native + {
     run: [ $.maven_17_21_only_native.build, $.maven_17_21_only_native.deploy(repo='lafo-maven')],
     name: 'daily-deploy-vm-maven-darwin-aarch64',
     notify_groups:: ['deploy'],
    },
    vm_common.svm_common_windows_amd64("21") + vm_common.gate_vm_windows_amd64 + self.maven_17_21_only_native + {
     run: [ $.maven_17_21_only_native.build, $.maven_17_21_only_native.deploy(repo='lafo-maven', dry_run=true)],
     name: 'gate-vm-maven-dry-run-windows-amd64',
    },
    vm_common.svm_common_windows_amd64("21") + vm_common.deploy_daily_vm_windows + self.maven_17_21_only_native + {
     run: [ $.maven_17_21_only_native.build, $.maven_17_21_only_native.deploy(repo='lafo-maven')],
     name: 'daily-deploy-vm-maven-windows-amd64',
     notify_groups:: ['deploy'],
    },

    #
    # Update the `stable` mx branch with the currently imported revision
    #
    vm_common.postmerge_vm_linux_amd64 + {
      run: [
        ['set-export', 'BRANCH_NAME', ['git', 'rev-parse', '--abbrev-ref', 'HEAD']],
        ['bash', '-c', 'if [[ ${BRANCH_NAME} == master ]] || [[ ${BRANCH_NAME} == release/* ]] || [[ ${BRANCH_NAME} == cpu/* ]]; then git -C ${MX_HOME} push origin +HEAD:refs/heads/graal/${BRANCH_NAME}; fi']
      ],
        name: 'post-merge-vm-update-stable-mx-branch-linux-amd64',
    },


    #
    # Deploy GraalVM Base and Installables
    # NOTE: After adding or removing deploy jobs, please make sure you modify ce-release-artifacts.json accordingly.
    #

    # Linux/AMD64
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_java21_linux_amd64),

    # Linux/AARCH64
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_java21_linux_aarch64),

    # Darwin/AMD64
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_base_java21_darwin_amd64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_installable_java21_darwin_amd64),

    # Darwin/AARCH64
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_base_java21_darwin_aarch64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_installable_java21_darwin_aarch64),

    # Windows/AMD64
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_base_java21_windows_amd64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_installable_java21_windows_amd64),

    #
    # Deploy the GraalVM Ruby image (GraalVM Base + ruby)
    #
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_ruby_java21_linux_amd64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_ruby_java21_darwin_amd64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_ruby_java21_darwin_aarch64),

    #
    # Deploy the GraalVM Espresso image (GraalVM Base + espresso)
    #
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_espresso_java21_linux_amd64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_espresso_java21_linux_aarch64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_espresso_java21_darwin_amd64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_espresso_java21_darwin_aarch64),
    self.deploy_vm_publish_releaser_artifact(vm_common.deploy_vm_espresso_java21_windows_amd64),

    # Trigger the releaser service
    self.notify_releaser_build,
  ],

  builds: [vm_common.verify_name(b1) for b1 in vm_common.builds + vm_common_bench.builds + vm_bench.builds + vm_native.builds + [{'defined_in': std.thisFile} + b2  for b2 in builds]],

  compiler_gate:: (import '../../../compiler/ci/ci_common/gate.jsonnet')
}
