# File is formatted with
# `jsonnetfmt --indent 2 --max-blank-lines 2 --sort-imports --string-style d --comment-style h -i ci.jsonnet`
local common = import "../../../ci/ci_common/common.jsonnet";
local sulong_deps = common.deps.sulong;

{
  local linux_amd64 = common.linux_amd64,
  local linux_aarch64 = common.linux_aarch64,
  local darwin_amd64 = common.darwin_amd64,
  local darwin_aarch64 = common.darwin_aarch64,
  local windows_amd64 = common.windows_amd64,

  nameOrEmpty(b):: if std.objectHas(b, "name") then
    ' (build "%s")' % b.name
  else "",

  local isNonEmptyString(s) = std.isString(s) && std.length(s)> 0,

  build_template:: {
    targets: [],
    suite:: error "suite not set" + $.nameOrEmpty(self),
    jdk_name:: error "jdk_name not set" + $.nameOrEmpty(self),
    os:: error "os not set" + $.nameOrEmpty(self),
    arch:: error "arch not set" + $.nameOrEmpty(self),
    job:: error "job not set" + $.nameOrEmpty(self),
    bitcode_config:: [],
    sulong_config:: [],
    gen_name_componentes::
      assert std.isArray(self.targets) : "targets must be an array" + $.nameOrEmpty(self);
      assert isNonEmptyString(self.suite) : "suite must be a non-empty string" + $.nameOrEmpty(self);
      assert isNonEmptyString(self.jdk_name) : "jdk_name must be a non-empty string" + $.nameOrEmpty(self);
      assert isNonEmptyString(self.os) : "os must be a non-empty string" + $.nameOrEmpty(self);
      assert isNonEmptyString(self.arch) : "arch must be a non-empty string" + $.nameOrEmpty(self);
      assert isNonEmptyString(self.job) : "job must be a non-empty string" + $.nameOrEmpty(self);
      assert std.isArray(self.bitcode_config) : "bitcode_config must be an array" + $.nameOrEmpty(self);
      assert std.isArray(self.sulong_config) : "sulong_config must be an array" + $.nameOrEmpty(self);
      self.targets + [self.suite] + [self.job] + self.bitcode_config + self.sulong_config + [self.jdk_name] + [self.os] + [self.arch],
    gen_name:: std.join("-", self.gen_name_componentes),
  },

  defBuild(b):: {
    assert self.gen_name == self.name : "Name error. expected '%s', actual '%s'" % [self.gen_name, self.name],
  } + $.build_template + b + if std.objectHasAll(b, "description_text") then { description: "%s with %s on %s/%s" % [b.description_text, self.jdk_name, self.os, self.arch]} else {},

  # Generates an array of build specs for give build spec prototypes and platform configurations and applies the names array.
  # If any resulting build contains a hidden field "skipPlattform:: true", then that build is dropped from the result array.
  #
  # Parameters
  #
  #   prototypes:       An array of build spec prototypes which will be extended with os, arch, jdk and a name.
  #   platform_specs:   An array of tuples (actually, an array) of the form (<os_arch>, [<jdk1>, <jdk2>, ...]).
  #                     The value will be flattened into an array "platforms" of the form [<os_arch1> + <jdk1>, <os_arch1> + <jdk2>, ...].
  #   names:            An array of name objects (object with a key called "name") that contains a name for each generated
  #                     build spec (prototypes X platforms). The name objects can be used to set additional properties
  #                     that are specific to the concrete job at hand, such as "timelimit".
  #
  mapPrototypePlatformName(prototypes, platform_specs, names)::
    local flattenPlatformSpec(spec) =
      local os_arch = spec[0];
      local jdks = spec[1];
      [os_arch + jdk for jdk in jdks]
    ;
    local mapSpecWithName(build_spec, names) = (
      assert std.length(build_spec) == std.length(names) : "got " + std.length(build_spec) + " build specs but " + std.length(names) + " names.\n" +
        "Expected:\n  " + std.join("\n  ", [(self.build_template + x).gen_name for x in build_spec]) +
        "\nGot:\n  " + std.join("\n  ", [x.name for x in names]);
      std.mapWithIndex(function(idx, spec) spec + names[idx], build_spec)
    );
    local platforms = std.flattenArrays([flattenPlatformSpec(spec) for spec in platform_specs]);
    local result = mapSpecWithName([
        proto + platform
          for proto in prototypes
              for platform in platforms
      ],
      names);
    [b for b in result if !std.objectHasAll(b, "skipPlatform") || !b.skipPlatform]
  ,

  linux_amd64:: linux_amd64 + sulong_deps,
  linux_aarch64:: linux_aarch64 + sulong_deps,
  darwin_amd64:: darwin_amd64 + sulong_deps,
  darwin_aarch64:: darwin_aarch64 + sulong_deps,
  windows_amd64:: windows_amd64 + sulong_deps + common.deps.windows_devkit,

  sulong_notifications:: {
    notify_groups:: ["sulong"],
  },

  post_merge:: $.sulong_notifications + common.frequencies.post_merge,
  daily:: $.sulong_notifications + common.frequencies.daily,
  weekly:: $.sulong_notifications + common.frequencies.weekly,

  mxCommand:: {
    extra_mx_args+:: [],
    mx:: ["mx"] + self.extra_mx_args
  },

  mxStrictMixin:: {
    extra_mx_args+: ["--strict-compliance"],
  },

  local firstLower(s) =
    if std.length(s) > 0 then
      std.asciiLower(s[0]) + s[1:]
    else s,

  Description(description):: { description_text:: description },

  mxGate:: $.mxCommand + {
    # sorted and unique
    local prefixes = std.uniq(std.sort(["sulong"] + if std.objectHasAll(self, "suite") then [self.suite] else [])),
    local processTags(tags) =
      local stripPrefix(prefix, tag) =
        if std.startsWith(tag, prefix) then
          std.asciiLower(std.stripChars(tag[std.length(prefix):], "-"))
        else
          tag
        ;
      local res = [
        # foldr because `prefixes` is sorted and we want to match longer entries first
        std.foldr(stripPrefix, prefixes, tag)
        for tag in std.filter(function(tag) tag != "build", tags)
      ];
      if std.length(res) > 0 then res
      # return tags if we would have returned an empty array
      else tags
    ,
    local tags = std.join(",", self.gateTags),
    gateTags:: [],
    extra_gate_args:: [],
    job:: std.join("-", processTags(self.gateTags)),
    run+: [self.mx + ["gate"] + self.extra_gate_args + ["--tags", tags]],
    description_text:: "Run mx gate --tags " + tags,
    catch_files+: [
      # logs from cmake-based tests
      "Output from these tests are in: (?P<filename>.+\\.log)",
    ],
  },

  gateTags(tags):: $.mxGate + {
    # enforcing `tags` to be a string makes it easier to copy and paste from the ci config file
    assert std.isString(tags) : "gateTags(tags): the `tags` parameter must be a string" + $.nameOrEmpty(self),
    gateTags:: std.split(tags, ","),
  },

  local strict_gate(tags) = $.gateTags(tags) + {
    extra_gate_args+:: ["--strict-mode"],
  },

  style:: common.deps.eclipse + strict_gate("style"),
  fullbuild:: common.deps.jdt + common.deps.spotbugs + strict_gate("fullbuild"),

  coverage(builds):: $.llvmBundled + $.requireGMP + $.mxGate + {
      local sameArchBuilds = std.filter(function(b) b.os == self.os && b.arch == self.arch, builds),
      local allTags = std.set(std.flattenArrays([b.gateTags for b in sameArchBuilds if std.objectHasAll(b, "gateTags")])),
      local coverageTags = std.setDiff(allTags, ["build", "build-all", "fullbuild", "style"]),
      job:: "coverage",
      skipPlatform:: coverageTags == [],
      gateTags:: ["build"] + coverageTags,
      # The Jacoco annotations interfere with partial evaluation. Use the DefaultTruffleRuntime to disable compilation just for the coverage runs.
      extra_mx_args+: ["-J-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime", "-J-Dpolyglot.engine.WarnInterpreterOnly=false"],
      extra_gate_args+: ["--jacoco-relativize-paths", "--jacoco-omit-src-gen", "--jacocout", "coverage", "--jacoco-format", "lcov"],
      teardown+: [
        ["mx", "sversions", "--print-repositories", "--json", "|", "coverage-uploader.py", "--associated-repos", "-"],
      ],
    },

  sulong_gateTest_default_tools:: {
    environment+: {
      CLANG_LLVM_AS: "llvm-as",
      CLANG_LLVM_LINK: "llvm-link",
      CLANG_LLVM_DIS: "llvm-dis",
      CLANG_LLVM_OPT: "opt",
    },
  },

  llvmBundled:: {},

  requireGMP:: {
    packages+: if self.os == "darwin" && self.arch == "aarch64" then {
        libgmp: "==6.2.1",
      } else {
        libgmp: "==6.1.2",
      },
  },
} + {

  [std.strReplace(name, "-", "_")]: common[name] + { _jdkIsGraalVM:: false }
  for name in std.objectFieldsAll(common)
  if std.startsWith(name, "labsjdk")

} + {

  [name]: common[name] + { _jdkIsGraalVM:: true }
  for name in std.objectFieldsAll(common)
  if std.startsWith(name, "graalvm")

}
