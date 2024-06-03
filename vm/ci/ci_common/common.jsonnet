local vm = import '../ci_includes/vm.jsonnet';
local graal_common = import '../../../ci/ci_common/common.jsonnet';
local galahad = import '../../../ci/ci_common/galahad-common.libsonnet';
local utils = import '../../../ci/ci_common/common-utils.libsonnet';
local repo_config = import '../../../repo-configuration.libsonnet';
local devkits = graal_common.devkits;

{
  job_name_targets(build)::
    local has_deploy = std.member(build.targets, 'deploy');
    local filtered_targets = [target for target in build.targets
      if target != 'mach5'  # don't add 'mach5' to the job name
      && target != 'deploy'  # the 'deploy' target is re-added later
    ];
    if (has_deploy) then
      filtered_targets + ['deploy']  # the 'deploy' target appears last in the job name
    else
      filtered_targets,

  verify_name(build): {
    expected_prefix:: std.join('-', $.job_name_targets(build)) + '-vm',
    expected_suffix:: build.os + '-' + build.arch,
    assert std.startsWith(build.name, self.expected_prefix) : "'%s' is defined in '%s' with '%s' targets but does not start with '%s'" % [build.name, build.defined_in, build.targets, self.expected_prefix],
    assert std.endsWith(build.name, self.expected_suffix) : "'%s' is defined in '%s' with os/arch '%s/%s' but does not end with '%s'" % [build.name, build.defined_in, build.os, build.arch, self.expected_suffix],
  } + build,

  vm_env_mixin(shortverison): {
    local jdk = self.downloads.JAVA_HOME,
    environment+: {
      BASE_JDK_NAME: jdk.name,
      BASE_JDK_VERSION: jdk.version,
      BASE_JDK_SHORT_VERSION: shortverison,
    },
  },

  common_vm: graal_common.build_base + vm.vm_setup + {
    python_version: "3",
    logs+: [
      '*/mxbuild/dists/stripped/*.map',
      '**/install.packages.R.log',
    ],
  },

  common_vm_linux: self.common_vm,

  common_vm_darwin: self.common_vm + {
    environment+: {
      LANG: 'en_US.UTF-8'
    },
  },

  common_vm_windows: self.common_vm + {
    downloads+: {
      MAVEN_HOME: {name: 'maven', version: '3.3.9', platformspecific: false},
    },
    environment+: {
      PATH: '$MAVEN_HOME\\bin;$JAVA_HOME\\bin;$PATH',
    },
  },

  common_vm_windows_jdk21: self.common_vm_windows + devkits['windows-jdk21'],
  common_vm_windows_jdkLatest: self.common_vm_windows + devkits['windows-jdkLatest'],

  # JS
  js_windows_common: {
    # derive VS version from devkit package name
    local devkit_version = std.filterMap(function(p) std.startsWith(p, 'devkit:VS'), function(p) std.substr(p, std.length('devkit:VS'), 4), std.objectFields(super.packages))[0],
    environment+: {
      DEVKIT_VERSION: devkit_version,
    },
    downloads+: {
      NASM: {name: 'nasm', version: '2.14.02', platformspecific: true},
    },
  },

  # SULONG
  sulong: graal_common.deps.sulong,

  # TRUFFLERUBY, needs OpenSSL 1.0.2+, so OracleLinux 7+
  truffleruby: graal_common.deps.sulong + graal_common.deps.truffleruby,

  fastr_no_recommended: {
    environment+: {
      FASTR_NO_RECOMMENDED: 'true'
    },
  },

  # GRAALPYTHON
  graalpy: self.sulong + graal_common.deps.graalpy,

  vm_linux_amd64_common: graal_common.deps.svm {
    capabilities+: ['manycores', 'ram16gb', 'fast'],
  },

  vm_linux_amd64: graal_common.linux_amd64 + self.common_vm_linux + self.vm_linux_amd64_common,

  vm_linux_amd64_ol9: graal_common.linux_amd64_ol9 + self.common_vm_linux + self.vm_linux_amd64_common,
  vm_ol9_amd64: self.vm_linux_amd64_ol9,

  vm_linux_amd64_ubuntu: graal_common.linux_amd64_ubuntu + self.common_vm + self.vm_linux_amd64_common,
  vm_ununtu_amd64: self.vm_linux_amd64_ubuntu,

  vm_linux_aarch64: self.common_vm_linux + graal_common.linux_aarch64,

  vm_linux_aarch64_ol9: self.common_vm_linux + graal_common.linux_aarch64_ol9,
  vm_ol9_aarch64: self.vm_linux_aarch64_ol9,

  vm_darwin_amd64: self.common_vm_darwin + graal_common.darwin_amd64 + {
    capabilities+: ['darwin_bigsur', 'ram16gb'],
    packages+: {
      gcc: '==4.9.2',
    },
    environment+: {
      # for compatibility with macOS BigSur
      MACOSX_DEPLOYMENT_TARGET: '11.0',
    },
  },

  vm_darwin_amd64_jdkLatest: self.vm_darwin_amd64,

  vm_darwin_aarch64: self.common_vm_darwin + graal_common.darwin_aarch64 + {
    capabilities+: ['darwin_bigsur'],
    environment+: {
      # for compatibility with macOS BigSur
      MACOSX_DEPLOYMENT_TARGET: '11.0',
    },
  },

  vm_windows: self.common_vm_windows + graal_common.windows_server_2016_amd64,
  vm_windows_jdk21: self.common_vm_windows_jdk21 + graal_common.windows_server_2016_amd64,
  vm_windows_jdkLatest: self.common_vm_windows_jdkLatest + graal_common.windows_server_2016_amd64,
  vm_windows_amd64: self.vm_windows,
  vm_windows_amd64_jdk21: self.vm_windows_jdk21,
  vm_windows_amd64_jdkLatest: self.vm_windows_jdkLatest,

  vm_base(os, arch, main_target, deploy=false, bench=false, os_distro=null, jdk_hint=null):
    self['vm_' + os + '_' + arch + (if (os_distro != null) then '_' + os_distro else '') + (if (jdk_hint != null) then '_jdk' + jdk_hint else '')]  # examples: `self.vm_linux_amd64_ubuntu`, `self.vm_windows_amd64_jdkLatest`
    + { targets+: [main_target] + (if (deploy) then ['deploy'] else []) + (if (bench) then ['bench'] else []) }
    + (if (bench) then { capabilities+: ['no_frequency_scaling'] } else {}),

  mx_vm_cmd_suffix: ['--sources=sdk:GRAAL_SDK,truffle:TRUFFLE_API,compiler:GRAAL,substratevm:SVM', '--debuginfo-dists', '--base-jdk-info=${BASE_JDK_NAME}:${BASE_JDK_VERSION}'],
  mx_vm_common: vm.mx_cmd_base_no_env + ['--env', '${VM_ENV}'] + self.mx_vm_cmd_suffix,
  mx_vm_complete: vm.mx_cmd_base_no_env + ['--env', '${VM_ENV}-complete'] + self.mx_vm_cmd_suffix,

  // svm_common includes the dependencies for all platforms besides windows amd64
  svm_common: graal_common.deps.svm,
  svm_common_windows_amd64(jdk): self.svm_common + graal_common.devkits["windows-jdk" + jdk],

  maven_deploy_sdk:                     ['--suite', 'sdk', 'maven-deploy', '--validate', 'none', '--all-distribution-types', '--with-suite-revisions-metadata'],
  deploy_artifacts_sdk(os, base_dist_name=null): (if base_dist_name != null then ['--base-dist-name=' + base_dist_name] else []) + ['--suite', 'sdk', 'deploy-artifacts', '--uploader', if os == 'windows' then 'artifact_uploader.cmd' else 'artifact_uploader'],

  maven_deploy_sdk_base:                    self.maven_deploy_sdk +                     ['--tags', 'graalvm', vm.binaries_repository],
  artifact_deploy_sdk_base(os, base_dist_name): self.deploy_artifacts_sdk(os, base_dist_name) + ['--tags', 'graalvm'],
  deploy_sdk_base(os, base_dist_name=null):     [self.mx_vm_common + vm.vm_profiles + self.maven_deploy_sdk_base, self.mx_vm_common + vm.vm_profiles + self.artifact_deploy_sdk_base(os, base_dist_name)],

  maven_deploy_sdk_base_dry_run:                    self.maven_deploy_sdk +                     ['--tags', 'graalvm', '--dry-run', vm.binaries_repository],
  artifact_deploy_sdk_base_dry_run(os, base_dist_name): self.deploy_artifacts_sdk(os, base_dist_name) + ['--tags', 'graalvm', '--dry-run'],
  deploy_sdk_base_dry_run(os, base_dist_name=null):     [self.mx_vm_common + vm.vm_profiles + self.maven_deploy_sdk_base_dry_run, self.mx_vm_common + vm.vm_profiles + self.artifact_deploy_sdk_base_dry_run(os, base_dist_name)],

  deploy_sdk_components(os, tags): [
    $.mx_vm_complete + self.maven_deploy_sdk + ['--tags', tags, vm.binaries_repository],
    $.mx_vm_complete + self.deploy_artifacts_sdk(os) + ['--tags', tags]
  ],

  maven_deploy_sdk_components_dry_run: self.maven_deploy_sdk + ['--tags', 'standalone', '--dry-run', vm.binaries_repository],
  artifact_deploy_sdk_components_dry_run(os): self.deploy_artifacts_sdk(os) + ['--tags', 'standalone', '--dry-run'],
  deploy_sdk_components_dry_run(os): [
    $.mx_vm_complete + self.maven_deploy_sdk_components_dry_run,
    $.mx_vm_complete + self.artifact_deploy_sdk_components_dry_run(os)
  ],

  ruby_vm_build: self.svm_common + self.sulong + self.truffleruby + vm.custom_vm,
  ruby_python_vm_build: self.ruby_vm_build + self.graalpy,
  full_vm_build: self.ruby_python_vm_build + graal_common.deps.fastr,

  graalvm_complete_build_deps(edition, os, arch, java_version):
      local java_deps(edition) = {
        downloads+: {
          JAVA_HOME: graal_common.jdks_data['labsjdk-' + edition + '-' + java_version],
          ESPRESSO_JAVA_HOME: graal_common.jdks_data['labsjdk-ee-21'],
        } + (
          if (os == 'linux' || os == 'darwin') && (arch == 'amd64') then {
            ESPRESSO_LLVM_JAVA_HOME: graal_common.jdks_data['labsjdk-ee-21-llvm'],
          } else {
          }
        ) + (
          if (java_version == 'latest') then {
            TOOLS_JAVA_HOME: graal_common.jdks_data['oraclejdk21'],
          } else {
          }
        )
      };

      if (os == 'windows') then
        if (arch == 'amd64') then
          # Windows/AMD64
          java_deps(edition) + (if (java_version == 'latest') then self.svm_common_windows_amd64("Latest") else self.svm_common_windows_amd64(java_version)) + self.js_windows_common + self.sulong
        else
          error 'Unknown windows arch: ' + arch
      else if (os == 'linux' || os == 'darwin') then
        java_deps(edition) + self.full_vm_build
      else
        error 'Unknown os: ' + os,

  # for cases where a maven package is not easily accessible
  maven_download_unix: {
    downloads+: {
      MAVEN_HOME: {name: 'maven', version: '3.3.9', platformspecific: false},
    },
    environment+: {
      PATH: '$MAVEN_HOME/bin:$JAVA_HOME/bin:$PATH',
    },
  },

  maven_deploy_base_functions: {
    dynamic_ce_imports(os, arch)::
      local legacy_imports = '/tools,/compiler,/graal-js,/espresso,/substratevm';
      local ce_windows_imports = legacy_imports + ',/vm,/wasm,/sulong,graalpython';
      local non_windows_imports = ',truffleruby';

      if (os == 'windows') then
        ce_windows_imports
      else
        ce_windows_imports + non_windows_imports,

    ce_suites(os, arch)::
      local legacy_suites = [
        '--suite', 'compiler',
        '--suite', 'truffle',
        '--suite', 'sdk',
        '--suite', 'tools',
        '--suite', 'regex',
        '--suite', 'graal-js',
        '--suite', 'espresso',
        '--suite', 'substratevm',
      ];
      local ce_windows_suites = legacy_suites + [
        '--suite', 'vm',
        '--suite', 'wasm',
        '--suite', 'sulong',
        '--suite', 'graalpython'
      ];
      local non_windows_suites = [
        '--suite', 'truffleruby',
      ];

      if (os == 'windows') then
        ce_windows_suites
      else
        ce_windows_suites + non_windows_suites,

    ce_licenses()::
      local legacy_licenses = 'GPLv2-CPE,GPLv2,UPL,MIT,ICU';
      local ce_licenses = legacy_licenses + ',PSF-License,BSD-simplified,BSD-new,EPL-2.0';
      ce_licenses,

    platform_specific_distribution_name(base_name, os, arch)::
      base_name + '_' + std.asciiUpper(os) + '_' + std.asciiUpper(arch),

    language_polyglot_isolate_distributions(language_id, current_os, current_arch, current_only=false)::
      local id_upcase = std.asciiUpper(language_id);
      local base_names = [id_upcase + '_ISOLATE', id_upcase + '_ISOLATE_RESOURCES'];
      local oss = ['linux', 'darwin', 'windows'];
      local archs = ['amd64', 'aarch64'];
      [base_names[0]] + [self.platform_specific_distribution_name(base_name, os, arch),
        for base_name in base_names
        for os in oss for arch in archs
        if os != 'windows' || arch != 'aarch64'
        if !current_only || os == current_os && arch == current_arch
      ],

    polyglot_isolate_distributions(language_ids, current_os, current_arch, current_only=false)::
      std.flattenArrays([self.language_polyglot_isolate_distributions(id, current_os, current_arch, current_only) for id in language_ids]),

    # To add a polyglot isolate build for a language, ensure that the language is included in the `ee_suites`
    # and add the language id to `polyglot_isolate_languages`.
    local polyglot_isolate_languages = ['js', 'python'],


    legacy_mx_args:: ['--disable-installables=true'],  # `['--force-bash-launcher=true', '--skip-libraries=true']` have been replaced by arguments from `vm.maven_deploy_base_functions.mx_args(os, arch)`
    mx_args(os, arch, reduced):: self.legacy_mx_args + vm.maven_deploy_base_functions.mx_args(os, arch, reduced),
    mx_cmd_base(os, arch, reduced):: ['mx'] + vm.maven_deploy_base_functions.dynamic_imports(os, arch) + self.mx_args(os, arch, reduced),
    mx_cmd_base_only_native(os, arch, reduced):: ['mx', '--dynamicimports', '/substratevm'] + self.mx_args(os, arch, reduced) + ['--native-images=false'],  # `--native-images=false` takes precedence over `self.mx_args(os, arch)`

    only_native_dists:: 'TRUFFLE_NFI_NATIVE,SVM_HOSTED_NATIVE',

    build(os, arch, reduced, mx_args=[], build_args=[]):: [
      self.mx_cmd_base(os, arch, reduced) + mx_args + ['build'] + build_args,
    ],

    pd_layouts_archive_name(platform):: 'pd-layouts-' + platform + '.tgz',

    pd_layouts_artifact_name(platform, dry_run):: 'pd-layouts-' + (if dry_run then 'dry-run-' else '') + platform,

    mvn_args: ['maven-deploy', '--tags=public', '--all-distribution-types', '--validate=full', '--version-suite=vm'],
    mvn_args_only_native: self.mvn_args + ['--all-suites', '--only', self.only_native_dists],

    compose_platform(os, arch):: os + '-' + arch,

    deploy_ce(os, arch, reduced, dry_run, extra_args, extra_mx_args=[]):: [
      self.mx_cmd_base(os, arch, reduced)
      + $.maven_deploy_base_functions.ce_suites(os,arch)
      + extra_mx_args
      + self.mvn_args
      + ['--licenses', $.maven_deploy_base_functions.ce_licenses()]
      + (if dry_run then ['--dry-run'] else [])
      + extra_args,
    ],

    deploy_ee(os, arch, reduced, dry_run, extra_args, extra_mx_args=[]):: [
      self.mx_cmd_base(os, arch, reduced)
      + vm.maven_deploy_base_functions.ee_suites(os, arch)
      + extra_mx_args
      + self.mvn_args
      + ['--licenses', vm.maven_deploy_base_functions.ee_licenses()]
      + (if dry_run then ['--dry-run'] else [])
      + extra_args,
    ],

    deploy_only_native(os, arch, reduced, dry_run, extra_args, extra_mx_args=[]):: [
      self.mx_cmd_base_only_native(os, arch, reduced)
      + extra_mx_args
      + self.mvn_args_only_native
      + ['--licenses', $.maven_deploy_base_functions.ce_licenses()]
      + (if dry_run then ['--dry-run'] else [])
      + extra_args,
    ],

    run_block(os, arch, dry_run, remote_mvn_repo, remote_non_mvn_repo, local_repo, main_platform, other_platforms, mvn_artifacts=true, mvn_bundle=true, mvn_reduced_bundle=true)::
      local multiplatform_build(reduced) = self.build(os, arch, reduced, mx_args=['--multi-platform-layout-directories=' + std.join(',', [main_platform] + other_platforms)], build_args=['--targets={MAVEN_TAG_DISTRIBUTIONS:public}']);  # `self.only_native_dists` are in `{MAVEN_TAG_DISTRIBUTIONS:public}`

      local mvn_artifacts_snippet =
        # remotely deploy only the suites that are defined in the current repository, to avoid duplicated deployments
        if (vm.maven_deploy_base_functions.edition == 'ce') then
          self.deploy_ce(os, arch, false, dry_run, [remote_mvn_repo])
        else
          self.deploy_ee(os, arch, false, dry_run, ['--dummy-javadoc', '--skip', std.join(',', self.polyglot_isolate_distributions(polyglot_isolate_languages, os, arch)) + ',TOOLS_COMMUNITY,LANGUAGES_COMMUNITY', remote_mvn_repo])
          + self.deploy_ee(os, arch, false, dry_run, ['--dummy-javadoc', '--only', std.join(',', self.polyglot_isolate_distributions(polyglot_isolate_languages, os, arch, true)) + ',TOOLS_COMMUNITY,LANGUAGES_COMMUNITY', remote_mvn_repo], extra_mx_args=['--suite', 'graal-js']);

      local mvn_bundle_snippet =
        [
          # Set env vars
          ['set-export', 'VERSION_STRING', self.mx_cmd_base(os, arch, reduced=false) + ['--quiet', 'graalvm-version']],
          ['set-export', 'LOCAL_MAVEN_REPO_REL_PATH', 'maven-bundle-' + vm.maven_deploy_base_functions.edition + '-${VERSION_STRING}'],
          ['set-export', 'LOCAL_MAVEN_REPO_URL', ['mx', '--quiet', 'local-path-to-url', '${LOCAL_MAVEN_REPO_REL_PATH}']],
        ]
        + (
          # Locally deploy all relevant suites
          if (vm.maven_deploy_base_functions.edition == 'ce') then
            self.deploy_ce(os, arch, false, dry_run, [local_repo, '${LOCAL_MAVEN_REPO_URL}'])
          else
            self.deploy_ce(os, arch, false, dry_run, ['--dummy-javadoc', '--skip', std.join(',', self.polyglot_isolate_distributions(polyglot_isolate_languages, os, arch)) + ',TOOLS_COMMUNITY,LANGUAGES_COMMUNITY', local_repo, '${LOCAL_MAVEN_REPO_URL}'])
            + self.deploy_ee(os, arch, false, dry_run, ['--dummy-javadoc', '--only', std.join(',', self.polyglot_isolate_distributions(polyglot_isolate_languages, os, arch)) + ',TOOLS_COMMUNITY,LANGUAGES_COMMUNITY', local_repo, '${LOCAL_MAVEN_REPO_URL}'], extra_mx_args=['--suite', 'graal-js'])
            + self.deploy_ee(os, arch, false, dry_run, ['--dummy-javadoc', local_repo, '${LOCAL_MAVEN_REPO_URL}'])
        )
        + (
          # Archive and deploy
          if (dry_run) then
            [['echo', 'Skipping the archiving and the final deployment of the Maven bundle']]
          else [
            ['set-export', 'MAVEN_BUNDLE_PATH', '${LOCAL_MAVEN_REPO_REL_PATH}'],
            ['set-export', 'MAVEN_BUNDLE_ARTIFACT_ID', 'maven-bundle-' + vm.maven_deploy_base_functions.edition],
            ['mx', 'build', '--targets', 'MAVEN_BUNDLE'],
            ['mx', '--suite', 'vm', 'maven-deploy', '--tags=resource-bundle', '--all-distribution-types', '--validate=none', '--with-suite-revisions-metadata', remote_non_mvn_repo],
          ]
        );

      local mvn_reduced_bundle_snippet =
        if (vm.maven_deploy_base_functions.edition == 'ce') then
          [['echo', 'Skipping the archiving and the final deployment of the reduced Maven bundle']]
        else (
          [
            # Set env vars
            ['set-export', 'VERSION_STRING', self.mx_cmd_base(os, arch, reduced=true) + ['--quiet', 'graalvm-version']],
            ['set-export', 'LOCAL_MAVEN_REDUCED_REPO_REL_PATH', 'maven-reduced-bundle-' + vm.maven_deploy_base_functions.edition + '-${VERSION_STRING}'],
            ['set-export', 'LOCAL_MAVEN_REDUCED_REPO_URL', ['mx', '--quiet', 'local-path-to-url', '${LOCAL_MAVEN_REDUCED_REPO_REL_PATH}']],
          ]
          + (
            multiplatform_build(reduced=true)
          )
          + (
            # Locally deploy all relevant suites
            self.deploy_ce(os, arch, true, dry_run, ['--dummy-javadoc', '--only', vm.maven_deploy_base_functions.reduced_ce_dists, local_repo, '${LOCAL_MAVEN_REDUCED_REPO_URL}'])
            + self.deploy_ee(os, arch, true, dry_run, ['--dummy-javadoc', '--only', vm.maven_deploy_base_functions.reduced_ee_dists, local_repo, '${LOCAL_MAVEN_REDUCED_REPO_URL}'], extra_mx_args=['--suite', 'graal-js'])
          )
          + (
            # Archive and deploy
            if (dry_run) then
              [['echo', 'Skipping the archiving and the final deployment of the reduced Maven bundle']]
            else [
              ['set-export', 'MAVEN_BUNDLE_PATH', '${LOCAL_MAVEN_REDUCED_REPO_REL_PATH}'],
              ['set-export', 'MAVEN_BUNDLE_ARTIFACT_ID', 'maven-reduced-bundle-' + vm.maven_deploy_base_functions.edition + (if (std.length(other_platforms) == 0) then '-' + main_platform else '')],
              ['mx', 'build', '--targets', 'MAVEN_BUNDLE'],
              ['mx', '--suite', 'vm', 'maven-deploy', '--tags=resource-bundle', '--all-distribution-types', '--validate=none', '--with-suite-revisions-metadata', remote_non_mvn_repo],
            ]
          )
        );

      if (self.compose_platform(os, arch) == main_platform) then (
        # The polyglot isolate layout distributions are not merged; each distribution exists solely for the current platform.
        # Therefore, it's necessary to ignore these distributions for other platforms."
        [self.mx_cmd_base(os, arch, reduced=false) + ['restore-pd-layouts', '--ignore-unknown-distributions', self.pd_layouts_archive_name(platform)] for platform in other_platforms]
        + (
          if (mvn_artifacts || mvn_bundle) then
            multiplatform_build(reduced=false)
          else
            [['echo', 'Skipping the full build']]
        )
        + (
          if (mvn_artifacts) then
            mvn_artifacts_snippet
          else
            [['echo', 'Skipping Maven artifacts']]
        )
        + (
          if (mvn_bundle) then
            mvn_bundle_snippet
          else
            [['echo', 'Skipping Maven bundle']]
        )
        + (
          if (mvn_reduced_bundle) then
            mvn_reduced_bundle_snippet
          else
          [['echo', 'Skipping reduced Maven bundle']]
        )
      ) else (
        (
          if (vm.maven_deploy_base_functions.edition == 'ce') then
            self.build(os, arch, reduced=false, build_args=['--targets=' + self.only_native_dists + ',{PLATFORM_DEPENDENT_LAYOUT_DIR_DISTRIBUTIONS}']) +
            self.deploy_only_native(os, arch, reduced=false, dry_run=dry_run, extra_args=[remote_mvn_repo])
          else
            self.build(os, arch, reduced=false, build_args=['--targets=' + self.only_native_dists + ',' + std.join(',', self.polyglot_isolate_distributions(polyglot_isolate_languages, os, arch, true)) + ',{PLATFORM_DEPENDENT_LAYOUT_DIR_DISTRIBUTIONS}']) +
            [['echo', 'Skipping the deployment of ' + self.only_native_dists + ': It is already deployed by the ce job']] +
            self.deploy_ee(os, arch, false, dry_run, ['--dummy-javadoc', '--only', std.join(',', self.polyglot_isolate_distributions(polyglot_isolate_languages, os, arch, true)), remote_mvn_repo], extra_mx_args=['--suite', 'graal-js'])
        )
        + [self.mx_cmd_base(os, arch, reduced=false) + ['archive-pd-layouts', self.pd_layouts_archive_name(os + '-' + arch)]]
      ),

    base_object(os, arch, dry_run, remote_mvn_repo, remote_non_mvn_repo, local_repo, main_platform='linux-amd64', other_platforms=['linux-aarch64', 'darwin-amd64', 'darwin-aarch64', 'windows-amd64'],):: {
      run: $.maven_deploy_base_functions.run_block(os, arch, dry_run, remote_mvn_repo, remote_non_mvn_repo, local_repo, main_platform, other_platforms),
    } + if (self.compose_platform(os, arch) == main_platform) then {
       requireArtifacts+: [
         {
           name: $.maven_deploy_base_functions.pd_layouts_artifact_name(platform, dry_run),
           dir: vm.vm_dir,
           autoExtract: true,
         } for platform in other_platforms
       ],
     }
    else {
      publishArtifacts+: [
        {
           name: $.maven_deploy_base_functions.pd_layouts_artifact_name(os + '-' + arch, dry_run),
           dir: vm.vm_dir,
           patterns: [$.maven_deploy_base_functions.pd_layouts_archive_name(os + '-' + arch)],
        },
      ],
    },
  },

  deploy_build: {
    deploysArtifacts: true
  },

  linux_deploy: self.deploy_build + {
    packages+: {
      maven: '>=3.3.9',
    },
  },

  darwin_deploy: self.deploy_build + self.maven_download_unix + {
    environment+: {
      PATH: '$MAVEN_HOME/bin:$JAVA_HOME/bin:$PATH:/usr/local/bin',
    },
  },

  record_file_sizes:: ['benchmark', 'file-size:*', '--results-file', 'sizes.json'],
  upload_file_sizes:: ['bench-uploader.py', 'sizes.json'],

  build_base_graalvm_image: [
    $.mx_vm_common + vm.vm_profiles + ['graalvm-show'],
    $.mx_vm_common + vm.vm_profiles + ['build'],
    ['set-export', 'GRAALVM_HOME', $.mx_vm_common + vm.vm_profiles + ['--quiet', '--no-warning', 'graalvm-home']],
  ],

  check_base_graalvm_image(os, arch, java_version): [
    ['set-export', 'GRAALVM_DIST', $.mx_vm_common + vm.vm_profiles + ['--quiet', '--no-warning', 'paths', $.mx_vm_common + vm.vm_profiles + ['graalvm-dist-name']]]
    ] + vm.check_graalvm_base_build('$GRAALVM_DIST', os, arch, java_version),

  patch_env(os, arch, java_version):
    # linux
    if (os == 'linux') then
      if (arch == 'amd64') then [
        # default
      ]
      else if (arch == 'aarch64') then [
        ['set-export', 'VM_ENV', '${VM_ENV}-aarch64'],
      ]
      else error "arch not found: " + arch
    # darwin
    else if (os == 'darwin') then
      if (arch == 'amd64') then [
        ['set-export', 'VM_ENV', '${VM_ENV}-darwin'],
      ]
      else if (arch == 'aarch64') then [
        # GR-34811: `ce-darwin-aarch64` can be removed once svml builds
        ['set-export', 'VM_ENV', '${VM_ENV}-darwin-aarch64'],
      ]
      else error "arch not found: " + arch
    # windows
    else if (os == 'windows') then
      if (arch == 'amd64') then [
        ['set-export', 'VM_ENV', '${VM_ENV}-win'],
      ]
      else error "arch not found: " + arch
    else error "os not found: " + os,

  deploy_graalvm_base(java_version): vm.check_structure + {
    run: $.patch_env(self.os, self.arch, java_version) + vm.collect_profiles() + $.build_base_graalvm_image + [
      $.mx_vm_common + vm.vm_profiles + $.record_file_sizes,
      $.upload_file_sizes,
    ] + $.deploy_sdk_base(self.os) + $.check_base_graalvm_image(self.os, self.arch, java_version),
    notify_groups:: ['deploy'],
    timelimit: "1:00:00"
  },

  deploy_graalvm_components(java_version, standalones, record_file_sizes=false): vm.check_structure + {
    build_deps:: std.join(',', []
      + (if (record_file_sizes) then ['GRAALVM'] else [])
      + (if (standalones) then ['GRAALVM_STANDALONES'] else [])
    ),

    tags:: std.join(',', []
      + (if (standalones) then ['standalone'] else [])
    ),

    run: $.patch_env(self.os, self.arch, java_version) + [
      $.mx_vm_complete + ['graalvm-show'],
      $.mx_vm_complete + ['build', '--dependencies', self.build_deps],
    ]
    + $.deploy_sdk_components(self.os, self.tags)
    + (
      if (record_file_sizes) then [
        $.mx_vm_complete + $.record_file_sizes,
        $.upload_file_sizes,
      ] else []
    ),
    notify_groups:: ['deploy'],
    timelimit: "1:30:00"
  },

  deploy_graalvm_espresso(os, arch, java_version): {
    run: vm.collect_profiles() + (
      if ((os == 'linux' || os == 'darwin') && arch == 'amd64') then [
        ['set-export', 'VM_ENV', "${VM_ENV}-llvm-espresso"],
      ] else [
        ['set-export', 'VM_ENV', "${VM_ENV}-espresso"],
      ]
    ) + $.build_base_graalvm_image + $.deploy_sdk_base(os, 'espresso') + [
      ['set-export', 'GRAALVM_HOME', $.mx_vm_common + ['--quiet', '--no-warning', 'graalvm-home']],
      ['set-export', 'DACAPO_JAR', $.mx_vm_common + ['--quiet', '--no-warning', 'paths', '--download', 'DACAPO_MR1_2baec49']],
      ['${GRAALVM_HOME}/bin/java', '-jar', '${DACAPO_JAR}', 'luindex'],
    ],
    notify_groups:: ['deploy'],
    timelimit: '1:45:00',
  },

  #
  # Deploy GraalVM Base and Standalones
  # NOTE: After adding or removing deploy jobs, please make sure you modify ce-release-artifacts.json accordingly.
  #

  # Linux/AMD64
  # - JDK-Latest
  deploy_vm_standalones_javaLatest_linux_amd64: vm.vm_java_Latest + self.full_vm_build + self.linux_deploy + self.vm_base('linux', 'amd64', 'daily', deploy=true) + self.deploy_graalvm_components('latest', standalones=true, record_file_sizes=true) + {name: 'daily-deploy-vm-standalones-java-latest-linux-amd64', notify_groups:: ["deploy"]},
  # - JDK21
  deploy_vm_standalones_java21_linux_amd64: vm.vm_java_21_llvm + self.full_vm_build + self.linux_deploy + self.vm_base('linux', 'amd64', 'weekly', deploy=true) + self.deploy_graalvm_components("java21", standalones=true, record_file_sizes=true) + {name: 'weekly-deploy-vm-standalones-java21-linux-amd64', notify_groups:: ["deploy"]},

  # Linux/AARCH64
  # - JDK-Latest
  deploy_vm_standalones_javaLatest_linux_aarch64: vm.vm_java_Latest + self.full_vm_build + self.linux_deploy + self.vm_base('linux', 'aarch64', 'daily', deploy=true) + self.deploy_graalvm_components('latest', standalones=true) + {name: 'daily-deploy-vm-standalones-java-latest-linux-aarch64', notify_groups:: ["deploy"], capabilities+: ["!xgene3"]},
  # - JDK21
  deploy_vm_standalones_java21_linux_aarch64: vm.vm_java_21 + self.full_vm_build + self.linux_deploy + self.vm_base('linux', 'aarch64', 'weekly', deploy=true) + self.deploy_graalvm_components("java21", standalones=true) + {name: 'weekly-deploy-vm-standalones-java21-linux-aarch64', notify_groups:: ["deploy"], capabilities+: ["!xgene3"]},

  # Darwin/AMD64
  # - JDK-Latest
  deploy_vm_standalones_javaLatest_darwin_amd64: vm.vm_java_Latest + self.full_vm_build + self.darwin_deploy + self.vm_base('darwin', 'amd64', 'daily', deploy=true, jdk_hint='Latest') + self.deploy_graalvm_components('latest', standalones=true) + {name: 'daily-deploy-vm-standalones-java-latest-darwin-amd64', capabilities+: ["!macmini_late_2014"], notify_groups:: ["deploy"], timelimit: '3:00:00'},
  # - JDK21
  deploy_vm_standalones_java21_darwin_amd64: vm.vm_java_21_llvm + self.full_vm_build + self.darwin_deploy + self.vm_base('darwin', 'amd64', 'weekly', deploy=true) + self.deploy_graalvm_components("java21", standalones=true) + {name: 'weekly-deploy-vm-standalones-java21-darwin-amd64', capabilities+: ["!macmini_late_2014"], notify_groups:: ["deploy"], timelimit: '3:00:00'},

  # Darwin/AARCH64
  # - JDK-Latest
  deploy_vm_standalones_javaLatest_darwin_aarch64: vm.vm_java_Latest + self.full_vm_build + self.darwin_deploy + self.vm_base('darwin', 'aarch64', 'daily', deploy=true) + self.deploy_graalvm_components('latest', standalones=true) + {name: 'daily-deploy-vm-standalones-java-latest-darwin-aarch64', notify_groups:: ["deploy"], notify_emails+: ["bernhard.urban-forster@oracle.com"], timelimit: '3:00:00'},
  # - JDK21
  deploy_vm_standalones_java21_darwin_aarch64: vm.vm_java_21 + self.full_vm_build + self.darwin_deploy + self.vm_base('darwin', 'aarch64', 'weekly', deploy=true) + self.deploy_graalvm_components("java21", standalones=true) + {name: 'weekly-deploy-vm-standalones-java21-darwin-aarch64', notify_groups:: ["deploy"], notify_emails+: ["bernhard.urban-forster@oracle.com"], timelimit: '3:00:00'},

  # Windows/AMD64
  # - JDK-Latest
  deploy_vm_standalones_javaLatest_windows_amd64: vm.vm_java_Latest + self.svm_common_windows_amd64('Latest') + self.js_windows_common + self.sulong + self.vm_base('windows', 'amd64', 'daily', deploy=true, jdk_hint='Latest') + self.deploy_graalvm_components('latest', standalones=true) + self.deploy_build + {name: 'daily-deploy-vm-standalones-java-latest-windows-amd64', timelimit: '2:30:00', notify_groups:: ["deploy"]},
  # - JDK21
  deploy_vm_standalones_java21_windows_amd64: vm.vm_java_21 + self.svm_common_windows_amd64("21") + self.js_windows_common + self.sulong + self.vm_base('windows', 'amd64', 'weekly', deploy=true, jdk_hint='21') + self.deploy_graalvm_components("java21", standalones=true) + self.deploy_build + {name: 'weekly-deploy-vm-standalones-java21-windows-amd64', timelimit: '2:30:00', notify_groups:: ["deploy"]},

  #
  # Deploy the GraalVM Espresso artifact (GraalVM Base + espresso - native image)
  #

  local sulong_vm_tests = self.svm_common + self.sulong + vm.custom_vm + self.vm_base('linux', 'amd64', 'gate') + {
     run: [
       ['export', 'SVM_SUITE=' + vm.svm_suite],
       ['mx', '--dynamicimports', '$SVM_SUITE,/sulong', '--disable-polyglot', '--disable-libpolyglot', 'gate', '--no-warning-as-error', '--tags', 'build,sulong'],
     ],
     timelimit: '1:00:00',
     name: 'gate-vm-native-sulong-' + self.jdk_name + '-linux-amd64',
  },

  local builds = [
    #
    # Gates
    #
    vm.vm_java_21 + graal_common.deps.eclipse + graal_common.deps.jdt + self.vm_base('linux', 'amd64', 'gate') + galahad.exclude + {
     run: [
       ['mx', 'gate', '-B=--force-deprecation-as-warning', '--tags', 'style,fullbuild'],
     ],
     name: 'gate-vm-style-jdk21-linux-amd64',
    },

    vm.vm_java_21 + sulong_vm_tests,
    vm.vm_java_Latest + sulong_vm_tests,
  ] + (import 'libgraal.jsonnet').builds,

  builds:: utils.add_defined_in(builds, std.thisFile),
}
