local composable = (import '../../../ci/ci_common/common-utils.libsonnet').composable;
local vm = import '../ci_includes/vm.jsonnet';
local graal_common = import '../../../ci/ci_common/common.jsonnet';
local repo_config = import '../../../repo-configuration.libsonnet';
local common_json = composable(import '../../../common.json');
local devkits = common_json.devkits;

{
  verify_name(build): {
    expected_prefix:: std.join('-', [target for target in build.targets if target != "mach5"]) + '-vm',
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

  common_vm_linux: self.common_vm + {
    packages+: {
      "00:devtoolset": "==7", # GCC 7.3.1, make 4.2.1, binutils 2.28, valgrind 3.13.0
    },
  },

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

  common_vm_windows_jdk11: self.common_vm_windows + devkits['windows-jdk11'],
  common_vm_windows_jdk17: self.common_vm_windows + devkits['windows-jdk17'],
  common_vm_windows_jdk19: self.common_vm_windows + devkits['windows-jdk19'],

  # JS
  js_windows_common: {
    downloads+: {
      NASM: {name: 'nasm', version: '2.14.02', platformspecific: true},
    },
  },

  js_windows_jdk11: self.js_windows_common + {
    setup+: [
      # Keep in sync with the 'devkits' object defined in the top level common.json file.
      # When this file has been converted to jsonnet, the value can be computed instead
      # using Jsonnet std lib functions.
      ['set-export', 'DEVKIT_VERSION', '2017'],
    ],
  },

  js_windows_jdk17: self.js_windows_common + {
    setup+: [
      # Keep in sync with the 'devkits' object defined in the top level common.json file.
      # When this file has been converted to jsonnet, the value can be computed instead
      # using Jsonnet std lib functions.
      ['set-export', 'DEVKIT_VERSION', '2019'],
    ],
  },

  js_windows_jdk19: self.js_windows_jdk17,

  js_windows: self.js_windows_common + {
    setup+: [
      # Keep in sync with the 'devkits' object defined in the top level common.json file.
      # When this file has been converted to jsonnet, the value can be computed instead
      # using Jsonnet std lib functions.
      ['set-export', 'DEVKIT_VERSION', '2017'],
    ],
  },

  # SULONG
  sulong_linux: common_json.sulong.deps.common + common_json.sulong.deps.linux,
  sulong_darwin_amd64: common_json.sulong.deps.common + common_json.sulong.deps.darwin_amd64,
  sulong_darwin_aarch64: common_json.sulong.deps.common + common_json.sulong.deps.darwin_aarch64,
  sulong_windows: common_json.sulong.deps.common + common_json.sulong.deps.windows,

  # TRUFFLERUBY, needs OpenSSL 1.0.2+, so OracleLinux 7+
  truffleruby_linux_amd64: self.sulong_linux + common_json.truffleruby.deps.common + common_json.truffleruby.deps.linux + graal_common.ol7,
  truffleruby_linux_aarch64: self.sulong_linux + common_json.truffleruby.deps.common + common_json.truffleruby.deps.linux, # OL7 is default on linux-aarch64
  truffleruby_darwin_amd64: self.sulong_darwin_amd64 + common_json.truffleruby.deps.common + common_json.truffleruby.deps.darwin,
  truffleruby_darwin_aarch64: self.sulong_darwin_aarch64 + common_json.truffleruby.deps.common + common_json.truffleruby.deps.darwin,

  # FASTR
  # Note: On both Linux and MacOS, FastR depends on the gnur module and on gfortran
  # of a specific version (4.8.5 on Linux, 10.2.0 on MacOS)
  # However, we do not need to load those modules, we only configure specific environment variables to
  # point to these specific modules. These modules and the configuration is only necessary for installation of
  # some R packages (that have Fortran code) and in order to run GNU-R

  fastr: {
    environment+: {
      FASTR_RELEASE: 'true',
    },
    downloads+: {
      F2C_BINARY: { name: 'f2c-binary', version: '7', platformspecific: true },
      FASTR_RECOMMENDED_BINARY: { name: 'fastr-recommended-pkgs', version: '16', platformspecific: true },
    },
    catch_files+: [
      'GNUR_CONFIG_LOG = (?P<filename>.+\\.log)',
      'GNUR_MAKE_LOG = (?P<filename>.+\\.log)',
    ],
  },

  fastr_linux: self.fastr + {
    packages+: {
      readline: '==6.3',
      pcre2: '==10.37',
      curl: '>=7.50.1',
      gnur: '==4.0.3-gcc4.8.5-pcre2',
    },
    environment+: {
      TZDIR: '/usr/share/zoneinfo',
      PKG_INCLUDE_FLAGS_OVERRIDE : '-I/cm/shared/apps/bzip2/1.0.6/include -I/cm/shared/apps/xz/5.2.2/include -I/cm/shared/apps/pcre2/10.37/include -I/cm/shared/apps/curl/7.50.1/include',
      PKG_LDFLAGS_OVERRIDE : '-L/cm/shared/apps/bzip2/1.0.6/lib -L/cm/shared/apps/xz/5.2.2/lib -L/cm/shared/apps/pcre2/10.37/lib -L/cm/shared/apps/curl/7.50.1/lib -L/cm/shared/apps/gcc/4.8.5/lib64',
      FASTR_FC: '/cm/shared/apps/gcc/4.8.5/bin/gfortran',
      FASTR_CC: '/cm/shared/apps/gcc/4.8.5/bin/gcc',
      GNUR_HOME_BINARY: '/cm/shared/apps/gnur/4.0.3_gcc4.8.5_pcre2-10.37/R-4.0.3',
    },
    downloads+: {
      BLAS_LAPACK_DIR: { name: 'fastr-403-blas-lapack-gcc', version: '4.8.5', platformspecific: true },
    },
  },

  fastr_darwin: self.fastr + {
    packages+: {
      'pcre2': '==10.37',
    },
    environment+: {
      FASTR_FC: '/cm/shared/apps/gcc/8.3.0/bin/gfortran',
      FASTR_CC: '/cm/shared/apps/gcc/8.3.0/bin/gcc',
      TZDIR: '/usr/share/zoneinfo',
      PKG_INCLUDE_FLAGS_OVERRIDE : '-I/cm/shared/apps/pcre2/pcre2-10.37/include -I/cm/shared/apps/bzip2/1.0.6/include -I/cm/shared/apps/xz/5.2.2/include -I/cm/shared/apps/curl/7.50.1/include',
      PKG_LDFLAGS_OVERRIDE : '-L/cm/shared/apps/bzip2/1.0.6/lib -L/cm/shared/apps/xz/5.2.2/lib -L/cm/shared/apps/pcre2/pcre2-10.37/lib -L/cm/shared/apps/curl/7.50.1/lib -L/cm/shared/apps/gcc/10.2.0/lib -L/usr/lib',
    },
    downloads+: {
      BLAS_LAPACK_DIR: { name: "fastr-403-blas-lapack-gcc", version: "8.3.0", platformspecific: true },
    },
  },

  fastr_no_recommended: {
    environment+: {
      FASTR_NO_RECOMMENDED: 'true'
    },
  },

  # GRAALPYTHON
  graalpython_linux: self.sulong_linux + {
    packages+: {
      libffi: '>=3.2.1',
      bzip2: '>=1.0.6',
    },
  },

  graalpython_darwin_amd64: self.sulong_darwin_amd64 + {},

  graalpython_darwin_aarch64: self.sulong_darwin_aarch64 + {},

  vm_linux_amd64: self.common_vm_linux + graal_common.linux_amd64 + graal_common.svm_deps.linux_amd64 {
    capabilities+: ['manycores', 'ram16gb', 'fast'],
  },

  vm_linux_aarch64: self.common_vm_linux + graal_common.linux_aarch64,

  vm_darwin_amd64: self.common_vm_darwin + graal_common.darwin_amd64 + {
    capabilities+: ['darwin_mojave', 'ram16gb'],
    packages+: {
      gcc: '==4.9.2',
    },
    environment+: {
      # for compatibility with macOS Sierra
      MACOSX_DEPLOYMENT_TARGET: '10.13',
    },
  },

  vm_darwin_aarch64: self.common_vm_darwin + graal_common.darwin_aarch64 + {
    environment+: {
      # for compatibility with macOS BigSur
      MACOSX_DEPLOYMENT_TARGET: '11.0',
    },
  },

  vm_windows: self.common_vm_windows + graal_common.windows_server_2016_amd64,
  vm_windows_jdk11: self.common_vm_windows_jdk11 + graal_common.windows_server_2016_amd64,
  vm_windows_jdk17: self.common_vm_windows_jdk17 + graal_common.windows_server_2016_amd64,
  vm_windows_jdk19: self.common_vm_windows_jdk19 + graal_common.windows_server_2016_amd64,

  gate_vm_linux_amd64: self.vm_linux_amd64 + {
    targets+: ['gate']
  },

  gate_vm_linux_aarch64: self.vm_linux_aarch64 + {
    targets+: ['gate'],
  },

  gate_vm_darwin_amd64: self.vm_darwin_amd64 + {
    targets+: ['gate'],
  },

  gate_vm_darwin_aarch64: self.vm_darwin_aarch64 + {
    targets+: ['gate'],
  },

  gate_vm_windows_amd64: self.vm_windows + {
    targets+: ['gate'],
  },

  bench_daily_vm_linux_amd64: self.vm_linux_amd64 + {
    capabilities+: ['no_frequency_scaling'],
    targets+: ['daily', 'bench'],
  },

  bench_daily_vm_darwin_amd64: self.vm_darwin_amd64 + {
    capabilities+: ['no_frequency_scaling'],
    targets+: ['daily', 'bench'],
  },

  bench_ondemand_vm_linux_amd64: self.vm_linux_amd64 + {
    capabilities+: ['no_frequency_scaling'],
    targets+: ['ondemand', 'bench'],
  },

  deploy_vm_linux_amd64: self.vm_linux_amd64 + {
    targets+: ['post-merge', 'deploy'],
  },

  deploy_vm_linux_aarch64: self.vm_linux_aarch64 + {
    targets+: ['post-merge', 'deploy'],
  },

  deploy_daily_vm_linux_amd64: self.vm_linux_amd64 + {
    targets+: ['daily', 'deploy'],
  },

  deploy_daily_vm_linux_aarch64: self.vm_linux_aarch64 + {
    targets+: ['daily', 'deploy'],
  },

  deploy_daily_vm_darwin_amd64: self.vm_darwin_amd64 + {
    targets+: ['daily', 'deploy'],
  },

  deploy_daily_vm_darwin_aarch64: self.vm_darwin_aarch64 + {
    targets+: ['daily', 'deploy'],
  },

  deploy_daily_vm_windows: self.vm_windows + {
    targets+: ['daily', 'deploy'],
  },

  deploy_daily_vm_windows_jdk11: self.vm_windows_jdk11 + {
    targets+: ['daily', 'deploy'],
  },

  deploy_daily_vm_windows_jdk17: self.vm_windows_jdk17 + {
    targets+: ['daily', 'deploy'],
  },

  deploy_daily_vm_windows_jdk19: self.vm_windows_jdk19 + {
    targets+: ['daily', 'deploy'],
  },

  postmerge_vm_linux_amd64: self.vm_linux_amd64 + {
    targets+: ['post-merge'],
  },

  daily_vm_linux_amd64: self.vm_linux_amd64 + {
    targets+: ['daily'],
  },

  daily_vm_linux_aarch64: self.vm_linux_aarch64 + {
    targets+: ['daily'],
  },

  daily_vm_darwin_amd64: self.vm_darwin_amd64 + {
    targets+: ['daily'],
  },

  daily_vm_windows: self.vm_windows + {
    targets+: ['daily'],
  },

  daily_vm_windows_jdk11: self.vm_windows_jdk11 + {
    targets+: ['daily'],
  },

  daily_vm_windows_jdk17: self.vm_windows_jdk17 + {
    targets+: ['daily'],
  },

  weekly_vm_linux_amd64: self.vm_linux_amd64 + {
    targets+: ['weekly'],
  },

  weekly_vm_linux_aarch64: self.vm_linux_aarch64 + {
    targets+: ['weekly'],
  },

  weekly_vm_darwin_amd64: self.vm_darwin_amd64 + {
    targets+: ['weekly'],
  },

  weekly_vm_darwin_aarch64: self.vm_darwin_aarch64+ {
    targets+: ['weekly'],
  },

  weekly_vm_windows: self.vm_windows + {
    targets+: ['weekly'],
  },

  weekly_vm_windows_jdk11: self.vm_windows_jdk11 + {
    targets+: ['weekly'],
  },

  weekly_vm_windows_jdk17: self.vm_windows_jdk17 + {
    targets+: ['weekly'],
  },

  ondemand_vm_linux_amd64: self.vm_linux_amd64 + {
    targets+: ['ondemand'],
  },

  ondemand_vm_darwin_amd64: self.vm_darwin_amd64 + {
    targets+: ['ondemand'],
  },

  ondemand_vm_darwin_aarch64: self.vm_darwin_aarch64+ {
    targets+: ['ondemand'],
  },

  ondemand_deploy_vm_linux_amd64: self.vm_linux_amd64 + {
    targets+: ['ondemand', 'deploy'],
  },

  ondemand_deploy_vm_linux_aarch64: self.vm_linux_aarch64 + {
    targets+: ['ondemand', 'deploy'],
  },

  ondemand_deploy_vm_darwin_amd64: self.vm_darwin_amd64 + {
    targets+: ['ondemand', 'deploy'],
  },

  ondemand_deploy_vm_darwin_aarch64: self.vm_darwin_aarch64 + {
    targets+: ['ondemand', 'deploy'],
  },

  ondemand_deploy_vm_windows_jdk11: self.vm_windows_jdk11 + {
    targets+: ['ondemand', 'deploy'],
  },

  ondemand_deploy_vm_windows_jdk17: self.vm_windows_jdk17 + {
    targets+: ['ondemand', 'deploy'],
  },

  mx_vm_cmd_suffix: ['--sources=sdk:GRAAL_SDK,truffle:TRUFFLE_API,compiler:GRAAL,substratevm:SVM', '--debuginfo-dists', '--base-jdk-info=${BASE_JDK_NAME}:${BASE_JDK_VERSION}'],
  mx_vm_common: vm.mx_cmd_base_no_env + ['--env', '${VM_ENV}'] + self.mx_vm_cmd_suffix,
  mx_vm_installables: vm.mx_cmd_base_no_env + ['--env', '${VM_ENV}-complete'] + self.mx_vm_cmd_suffix,

  svm_common_linux_amd64:        { environment+: graal_common.svm_deps.common.environment, logs+: graal_common.svm_deps.common.logs} + graal_common.svm_deps.linux_amd64,
  svm_common_linux_aarch64:      { environment+: graal_common.svm_deps.common.environment, logs+: graal_common.svm_deps.common.logs} + graal_common.svm_deps.linux_aarch64,
  svm_common_darwin_amd64:       { environment+: graal_common.svm_deps.common.environment, logs+: graal_common.svm_deps.common.logs} + graal_common.svm_deps.darwin_amd64,
  svm_common_darwin_aarch64:     { environment+: graal_common.svm_deps.common.environment, logs+: graal_common.svm_deps.common.logs} + graal_common.svm_deps.darwin_aarch64,
  svm_common_windows_amd64(jdk): { environment+: graal_common.svm_deps.common.environment, logs+: graal_common.svm_deps.common.logs} + graal_common.svm_deps.windows       + common_json.devkits["windows-jdk" + jdk],

  maven_deploy_sdk:                     ['--suite', 'sdk', 'maven-deploy', '--validate', 'none', '--all-distribution-types', '--with-suite-revisions-metadata'],
  deploy_artifacts_sdk(os, base_dist_name=null): (if base_dist_name != null then ['--base-dist-name=' + base_dist_name] else []) + ['--suite', 'sdk', 'deploy-artifacts', '--uploader', if os == 'windows' then 'artifact_uploader.cmd' else 'artifact_uploader'],

  maven_deploy_sdk_base:                    self.maven_deploy_sdk +                     ['--tags', 'graalvm', vm.binaries_repository],
  artifact_deploy_sdk_base(os, base_dist_name): self.deploy_artifacts_sdk(os, base_dist_name) + ['--tags', 'graalvm'],
  deploy_sdk_base(os, base_dist_name=null):     [self.mx_vm_common + vm.vm_profiles + self.maven_deploy_sdk_base, self.mx_vm_common + vm.vm_profiles + self.artifact_deploy_sdk_base(os, base_dist_name)],

  maven_deploy_sdk_base_dry_run:                    self.maven_deploy_sdk +                     ['--tags', 'graalvm', '--dry-run', vm.binaries_repository],
  artifact_deploy_sdk_base_dry_run(os, base_dist_name): self.deploy_artifacts_sdk(os, base_dist_name) + ['--tags', 'graalvm', '--dry-run'],
  deploy_sdk_base_dry_run(os, base_dist_name=null):     [self.mx_vm_common + vm.vm_profiles + self.maven_deploy_sdk_base_dry_run, self.mx_vm_common + vm.vm_profiles + self.artifact_deploy_sdk_base_dry_run(os, base_dist_name)],

  maven_deploy_sdk_components:    self.maven_deploy_sdk +      ['--tags', 'installable,standalone',              vm.binaries_repository],
  artifact_deploy_sdk_components(os): self.deploy_artifacts_sdk(os) +  ['--tags', 'installable,standalone',                ],
  deploy_sdk_components(os):          [$.mx_vm_installables + self.maven_deploy_sdk_components, $.mx_vm_installables + self.artifact_deploy_sdk_components(os)],

  maven_deploy_sdk_components_dry_run:    self.maven_deploy_sdk +     ['--tags', 'installable,standalone', '--dry-run', vm.binaries_repository],
  artifact_deploy_sdk_components_dry_run(os): self.deploy_artifacts_sdk(os) + ['--tags', 'installable,standalone',                '--dry-run'],
  deploy_sdk_components_dry_run(os):          [$.mx_vm_installables + self.maven_deploy_sdk_components_dry_run, $.mx_vm_installables + self.artifact_deploy_sdk_components_dry_run(os)],


  svm_vm_build_ol6_amd64: self.svm_common_linux_amd64 + vm.custom_vm_linux,

  ruby_vm_build_linux: self.svm_common_linux_amd64 + self.sulong_linux + self.truffleruby_linux_amd64 + vm.custom_vm_linux,
  full_vm_build_linux: self.ruby_vm_build_linux + self.fastr_linux + self.graalpython_linux,
  full_vm_build_linux_aarch64: self.svm_common_linux_aarch64 + self.sulong_linux + self.truffleruby_linux_aarch64 + vm.custom_vm_linux,

  ruby_vm_build_darwin_amd64: self.svm_common_darwin_amd64 + self.sulong_darwin_amd64 + self.truffleruby_darwin_amd64 + vm.custom_vm_darwin,
  full_vm_build_darwin_amd64: self.ruby_vm_build_darwin_amd64 + self.fastr_darwin + self.graalpython_darwin_amd64,

  ruby_vm_build_darwin_aarch64: self.svm_common_darwin_aarch64 + self.sulong_darwin_aarch64 + self.truffleruby_darwin_aarch64 + vm.custom_vm_darwin,
  full_vm_build_darwin_aarch64: self.ruby_vm_build_darwin_aarch64 + self.graalpython_darwin_aarch64,

  # for cases where a maven package is not easily accessible
  maven_download_unix: {
    downloads+: {
      MAVEN_HOME: {name: 'maven', version: '3.3.9', platformspecific: false},
    },
    environment+: {
      PATH: '$MAVEN_HOME/bin:$JAVA_HOME/bin:$PATH',
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

  # These dummy artifacts are required by notify_releaser_build in order to trigger the releaser service
  # after the deploy jobs have completed.
  create_releaser_notifier_artifact:: [
    ['python3', '-c', "from os import environ; open('../' + environ['BUILD_NAME'], 'a').close()"],
  ],

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
      if (arch == 'amd64') then
        if (java_version == 'java11') then [
          # default
        ] else if (java_version == 'java17') then [
          # default
        ] else if (java_version == 'java19') then [
          ['set-export', 'VM_ENV', '${VM_ENV}-19'],
        ] else error "java_version not found: " + java_version
      else if (arch == 'aarch64') then
        if (java_version == 'java11') then [
          ['set-export', 'VM_ENV', '${VM_ENV}-aarch64'],
        ] else if (java_version == 'java17') then [
          ['set-export', 'VM_ENV', '${VM_ENV}-aarch64'],
        ] else if (java_version == 'java19') then [
          ['set-export', 'VM_ENV', '${VM_ENV}-aarch64-19'],
        ] else error "java_version not found: " + java_version
      else error "arch not found: " + arch
    # darwin
    else if (os == 'darwin') then
      if (arch == 'amd64') then
        if (java_version == 'java11') then [
          ['set-export', 'VM_ENV', '${VM_ENV}-darwin'],
        ] else if (java_version == 'java17') then [
          ['set-export', 'VM_ENV', '${VM_ENV}-darwin'],
        ] else if (java_version == 'java19') then [
          ['set-export', 'VM_ENV', '${VM_ENV}-darwin-19'],
        ] else error "java_version not found: " + java_version
      else if (arch == 'aarch64') then
        # GR-34811: `ce-darwin-aarch64` can be removed once svml builds
        if (java_version == 'java11') then [
          ['set-export', 'VM_ENV', '${VM_ENV}-darwin-aarch64'],
        ] else if (java_version == 'java17') then [
          ['set-export', 'VM_ENV', '${VM_ENV}-darwin-aarch64'],
        ] else if (java_version == 'java19') then [
          ['set-export', 'VM_ENV', '${VM_ENV}-darwin-aarch64-19'],
        ] else error "java_version not found: " + java_version
      else error "arch not found: " + arch
    # windows
    else if (os == 'windows') then
      if (arch == 'amd64') then
        if (java_version == 'java11') then [
          ['set-export', 'VM_ENV', '${VM_ENV}-win'],
        ] else if (java_version == 'java17') then [
          ['set-export', 'VM_ENV', '${VM_ENV}-win'],
        ] else if (java_version == 'java19') then [
          ['set-export', 'VM_ENV', '${VM_ENV}-win-19'],
        ] else error "java_version not found: " + java_version
      else error "arch not found: " + arch
    else error "os not found: " + os,

  deploy_graalvm_linux_amd64(java_version): vm.check_structure + {
    run: $.patch_env('linux', 'amd64', java_version) + [
      $.mx_vm_installables + ['graalvm-show'],
      $.mx_vm_installables + ['build'],
    ] + $.deploy_sdk_components(self.os) + [
      $.mx_vm_installables + $.record_file_sizes,
      $.upload_file_sizes,
    ] + vm.collect_profiles() + $.build_base_graalvm_image + [
      $.mx_vm_common + vm.vm_profiles + $.record_file_sizes,
      $.upload_file_sizes,
    ] + $.deploy_sdk_base(self.os) + [
      ['set-export', 'GRAALVM_HOME', $.mx_vm_common + ['--quiet', '--no-warning', 'graalvm-home']],
    ] + $.create_releaser_notifier_artifact + $.check_base_graalvm_image("linux", "amd64", java_version) + [
      ['set-export', 'GRAALVM_HOME', $.mx_vm_installables + ['--quiet', '--no-warning', 'graalvm-home']],
    ] + vm.check_graalvm_complete_build($.mx_vm_installables, "linux", "amd64", java_version),
    notify_groups:: ['deploy'],
    timelimit: "1:30:00"
  },

  deploy_graalvm_linux_aarch64(java_version): vm.check_structure + {
    run: $.patch_env('linux', 'aarch64', java_version) + [
      $.mx_vm_installables + ['graalvm-show'],
      $.mx_vm_installables + ['build'],
    ] + $.deploy_sdk_components(self.os) + [
      $.mx_vm_installables + $.record_file_sizes,
      $.upload_file_sizes,
    ] + vm.collect_profiles() + $.build_base_graalvm_image + [
      $.mx_vm_common + vm.vm_profiles + $.record_file_sizes,
      $.upload_file_sizes,
    ] + $.deploy_sdk_base(self.os) + $.create_releaser_notifier_artifact + $.check_base_graalvm_image("linux", "aarch64", java_version) + [
      ['set-export', 'GRAALVM_HOME', $.mx_vm_installables + ['--quiet', '--no-warning', 'graalvm-home']],
    ] + vm.check_graalvm_complete_build($.mx_vm_installables, "linux", "aarch64", java_version),
    notify_groups:: ['deploy'],
    timelimit: '1:30:00',
  },

  deploy_graalvm_base_darwin_amd64(java_version): vm.check_structure + {
    run: $.patch_env('darwin', 'amd64', java_version) + vm.collect_profiles() + $.build_base_graalvm_image + [
      $.mx_vm_common + vm.vm_profiles + $.record_file_sizes,
      $.upload_file_sizes
    ] + $.deploy_sdk_base(self.os) + $.create_releaser_notifier_artifact + $.check_base_graalvm_image("darwin", "amd64", java_version),
    notify_groups:: ['deploy'],
    timelimit: '1:45:00',
  },

  deploy_graalvm_installables_darwin_amd64(java_version): vm.check_structure + {
    run: $.patch_env('darwin', 'amd64', java_version) + [
      $.mx_vm_installables + ['graalvm-show'],
      $.mx_vm_installables + ['build'],
      ['set-export', 'GRAALVM_HOME', $.mx_vm_installables + ['--quiet', '--no-warning', 'graalvm-home']],
    ] + $.deploy_sdk_components(self.os) + [
      $.mx_vm_installables + $.record_file_sizes,
      $.upload_file_sizes,
    ] + $.create_releaser_notifier_artifact + vm.check_graalvm_complete_build($.mx_vm_installables, "darwin", "amd64", java_version),
    notify_groups:: ['deploy'],
    timelimit: '3:00:00',
  },

  deploy_graalvm_base_darwin_aarch64(java_version): vm.check_structure + {
    run: $.patch_env('darwin', 'aarch64', java_version) + vm.collect_profiles() + $.build_base_graalvm_image + [
      $.mx_vm_common + vm.vm_profiles + $.record_file_sizes,
      $.upload_file_sizes,
    ] + $.deploy_sdk_base(self.os) + $.create_releaser_notifier_artifact + $.check_base_graalvm_image("darwin", "aarch64", java_version),
    notify_emails+: ['gilles.m.duboscq@oracle.com', 'bernhard.urban-forster@oracle.com'],
    timelimit: '1:45:00',
  },

  deploy_graalvm_installables_darwin_aarch64(java_version): vm.check_structure + {
    run: $.patch_env('darwin', 'aarch64', java_version) + [
      $.mx_vm_installables + ['graalvm-show'],
      $.mx_vm_installables + ['build'],
      ['set-export', 'GRAALVM_HOME', $.mx_vm_installables + ['--quiet', '--no-warning', 'graalvm-home']],
    ] + $.deploy_sdk_components(self.os) + [
      $.mx_vm_installables + $.record_file_sizes,
      $.upload_file_sizes,
    ] +  $.create_releaser_notifier_artifact + vm.check_graalvm_complete_build($.mx_vm_installables, "darwin", "aarch64", java_version),
    notify_emails: ['gilles.m.duboscq@oracle.com', 'bernhard.urban-forster@oracle.com'],
    timelimit: '3:00:00',
  },

  deploy_graalvm_base_windows_amd64(java_version): vm.check_structure + {
    run: $.patch_env('windows', 'amd64', java_version) + vm.collect_profiles() + $.build_base_graalvm_image + [
      $.mx_vm_common + vm.vm_profiles + $.record_file_sizes,
      $.upload_file_sizes,
    ] + $.deploy_sdk_base(self.os) + $.create_releaser_notifier_artifact + $.check_base_graalvm_image("windows", "amd64", java_version),
    notify_groups:: ['deploy'],
    timelimit: '1:30:00',
  },

  deploy_graalvm_installables_windows_amd64(java_version): vm.check_structure + {
    run: $.patch_env('windows', 'amd64', java_version) + [
      $.mx_vm_installables + ['graalvm-show'],
      $.mx_vm_installables + ['build'],
      ['set-export', 'GRAALVM_HOME', $.mx_vm_installables + ['--quiet', '--no-warning', 'graalvm-home']],
    ] + $.deploy_sdk_components(self.os) + [
      $.mx_vm_installables + $.record_file_sizes,
      $.upload_file_sizes,
    ] + $.create_releaser_notifier_artifact + vm.check_graalvm_complete_build($.mx_vm_installables, "windows", "amd64", java_version),
    notify_groups:: ['deploy'],
    timelimit: '1:30:00',
  },

  deploy_graalvm_ruby(os, arch, java_version): {
    run: vm.collect_profiles() + [
      ['set-export', 'VM_ENV', "${VM_ENV}-ruby"],
    ] + $.build_base_graalvm_image + $.deploy_sdk_base(os, 'ruby') + [
      ['set-export', 'GRAALVM_HOME', $.mx_vm_common + ['--quiet', '--no-warning', 'graalvm-home']],
    ] + $.create_releaser_notifier_artifact,
    notify_groups:: ['deploy', 'ruby'],
    timelimit: '1:45:00',
  },

  #
  # Deploy GraalVM Base and Installables
  # NOTE: After adding or removing deploy jobs, please make sure you modify ce-release-artifacts.json accordingly.
  #

  # Linux/AMD64
  deploy_vm_java17_linux_amd64: vm.vm_java_17_llvm + self.full_vm_build_linux + self.linux_deploy + self.deploy_vm_linux_amd64 + self.deploy_graalvm_linux_amd64("java17") + {name: 'post-merge-deploy-vm-java17-linux-amd64', diskspace_required: vm.diskspace_required.java17_linux_amd64, notify_groups:: ["deploy"]},
  deploy_vm_java19_linux_amd64: vm.vm_java_19_llvm + self.full_vm_build_linux + self.linux_deploy + self.deploy_vm_linux_amd64 + self.deploy_graalvm_linux_amd64("java19") + {name: 'post-merge-deploy-vm-java19-linux-amd64', diskspace_required: vm.diskspace_required.java19_linux_amd64, notify_groups:: ["deploy"]},

  # Linux/AARCH64
  deploy_vm_java17_linux_aarch64: vm.vm_java_17 + self.full_vm_build_linux_aarch64 + self.linux_deploy + self.deploy_daily_vm_linux_aarch64 + self.deploy_graalvm_linux_aarch64("java17") + {name: 'daily-deploy-vm-java17-linux-aarch64', notify_groups:: ["deploy"]},
  deploy_vm_java19_linux_aarch64: vm.vm_java_19 + self.full_vm_build_linux_aarch64 + self.linux_deploy + self.deploy_daily_vm_linux_aarch64 + self.deploy_graalvm_linux_aarch64("java19") + {name: 'daily-deploy-vm-java19-linux-aarch64', notify_groups:: ["deploy"]},

  # Darwin/AMD64
  deploy_vm_base_java17_darwin_amd64: vm.vm_java_17_llvm + self.full_vm_build_darwin_amd64 + self.darwin_deploy + self.deploy_daily_vm_darwin_amd64 + self.deploy_graalvm_base_darwin_amd64("java17") + {name: 'daily-deploy-vm-base-java17-darwin-amd64', notify_groups:: ["deploy"]},
  deploy_vm_installable_java17_darwin_amd64: vm.vm_java_17_llvm + self.full_vm_build_darwin_amd64 + self.darwin_deploy + self.deploy_daily_vm_darwin_amd64 + self.deploy_graalvm_installables_darwin_amd64("java17") + {name: 'daily-deploy-vm-installable-java17-darwin-amd64', diskspace_required: "31GB", notify_groups:: ["deploy"]},
  deploy_vm_base_java19_darwin_amd64: vm.vm_java_19_llvm + self.full_vm_build_darwin_amd64 + self.darwin_deploy + self.deploy_daily_vm_darwin_amd64 + self.deploy_graalvm_base_darwin_amd64("java19") + {name: 'daily-deploy-vm-base-java19-darwin-amd64', notify_groups:: ["deploy"]},
  deploy_vm_installable_java19_darwin_amd64: vm.vm_java_19_llvm + self.full_vm_build_darwin_amd64 + self.darwin_deploy + self.deploy_daily_vm_darwin_amd64 + self.deploy_graalvm_installables_darwin_amd64("java19") + {name: 'daily-deploy-vm-installable-java19-darwin-amd64', diskspace_required: "31GB", notify_groups:: ["deploy"]},

  # Darwin/AARCH64
  deploy_vm_base_java17_darwin_aarch64: vm.vm_java_17 + self.full_vm_build_darwin_aarch64 + self.darwin_deploy + self.deploy_daily_vm_darwin_aarch64 + self.deploy_graalvm_base_darwin_aarch64("java17") + {name: 'daily-deploy-vm-base-java17-darwin-aarch64', notify_groups:: ["deploy"]},
  deploy_vm_installable_java17_darwin_aarch64: vm.vm_java_17 + self.full_vm_build_darwin_aarch64 + self.darwin_deploy + self.deploy_daily_vm_darwin_aarch64 + self.deploy_graalvm_installables_darwin_aarch64("java17") + {name: 'daily-deploy-vm-installable-java17-darwin-aarch64', diskspace_required: "31GB", notify_groups:: ["deploy"]},
  deploy_vm_base_java19_darwin_aarch64: vm.vm_java_19 + self.full_vm_build_darwin_aarch64 + self.darwin_deploy + self.deploy_daily_vm_darwin_aarch64 + self.deploy_graalvm_base_darwin_aarch64("java19") + {name: 'daily-deploy-vm-base-java19-darwin-aarch64', notify_groups:: ["deploy"]},
  deploy_vm_installable_java19_darwin_aarch64: vm.vm_java_19 + self.full_vm_build_darwin_aarch64 + self.darwin_deploy + self.deploy_daily_vm_darwin_aarch64 + self.deploy_graalvm_installables_darwin_aarch64("java19") + {name: 'daily-deploy-vm-installable-java19-darwin-aarch64', diskspace_required: "31GB", notify_groups:: ["deploy"]},

  # Windows/AMD64
  deploy_vm_base_java17_windows_amd64: vm.vm_java_17 + self.svm_common_windows_amd64("17") + self.js_windows_jdk17 + self.deploy_daily_vm_windows_jdk17 + self.deploy_graalvm_base_windows_amd64("java17") + self.deploy_build + {name: 'daily-deploy-vm-base-java17-windows-amd64', notify_groups:: ["deploy"]},
  deploy_vm_installable_java17_windows_amd64: vm.vm_java_17 + self.svm_common_windows_amd64("17") + self.js_windows_jdk17 + self.sulong_windows + self.deploy_daily_vm_windows_jdk17 + self.deploy_graalvm_installables_windows_amd64("java17") + self.deploy_build + {name: 'daily-deploy-vm-installable-java17-windows-amd64', diskspace_required: "31GB", notify_groups:: ["deploy"]},
  deploy_vm_base_java19_windows_amd64: vm.vm_java_19 + self.svm_common_windows_amd64("19") + self.js_windows_jdk19 + self.deploy_daily_vm_windows_jdk19 + self.deploy_graalvm_base_windows_amd64("java19") + self.deploy_build + {name: 'daily-deploy-vm-base-java19-windows-amd64', notify_groups:: ["deploy"]},
  deploy_vm_installable_java19_windows_amd64: vm.vm_java_19 + self.svm_common_windows_amd64("19") + self.js_windows_jdk19 + self.sulong_windows + self.deploy_daily_vm_windows_jdk19 + self.deploy_graalvm_installables_windows_amd64("java19") + self.deploy_build + {name: 'daily-deploy-vm-installable-java19-windows-amd64', diskspace_required: "31GB", notify_groups:: ["deploy"]},

  #
  # Deploy the GraalVM Ruby image (GraalVM Base + ruby - js)
  #

  deploy_vm_ruby_java17_linux_amd64: vm.vm_java_17 + self.ruby_vm_build_linux + self.linux_deploy + self.deploy_daily_vm_linux_amd64 + self.deploy_graalvm_ruby('linux', 'amd64', 'java17') + {name: 'daily-deploy-vm-ruby-java17-linux-amd64', notify_groups:: ["deploy"]},
  deploy_vm_ruby_java17_darwin_amd64: vm.vm_java_17 + self.ruby_vm_build_darwin_amd64 + self.darwin_deploy + self.deploy_daily_vm_darwin_amd64 + self.deploy_graalvm_ruby('darwin', 'amd64', 'java17') + {name: 'daily-deploy-vm-ruby-java17-darwin-amd64', notify_groups:: ["deploy"]},
  deploy_vm_ruby_java17_darwin_aarch64: vm.vm_java_17 + self.ruby_vm_build_darwin_aarch64 + self.darwin_deploy + self.deploy_daily_vm_darwin_aarch64 + self.deploy_graalvm_ruby('darwin', 'aarch64', 'java17') + {name: 'daily-deploy-vm-ruby-java17-darwin-aarch64', notify_groups:: ["deploy"]},

  local builds = [
    #
    # Gates
    #
    vm.vm_java_17 + common_json.downloads.eclipse + common_json.downloads.jdt + self.gate_vm_linux_amd64 + {
     run: [
       ['mx', 'gate', '-B=--force-deprecation-as-warning', '--tags', 'style,fullbuild'],
     ],
     name: 'gate-vm-style-jdk17-linux-amd64',
    },

    vm.vm_java_17 + self.svm_common_linux_amd64 + self.sulong_linux + vm.custom_vm_linux + self.gate_vm_linux_amd64 + {
     run: [
       ['export', 'SVM_SUITE=' + vm.svm_suite],
       ['mx', '--dynamicimports', '$SVM_SUITE,/sulong', '--disable-polyglot', '--disable-libpolyglot', 'gate', '--no-warning-as-error', '--tags', 'build,sulong'],
     ],
     timelimit: '1:00:00',
     name: 'gate-vm-native-sulong-linux-amd64',
    },
  ] + (import 'libgraal.jsonnet').builds,

  builds:: [{'defined_in': std.thisFile} + b for b in builds],
}
