# File is formatted with
# `jsonnetfmt --indent 2 --max-blank-lines 2 --sort-imports --string-style d --comment-style h -i ci.jsonnet`
{
  local common = import "../../common.jsonnet",
  local composable = (import "../../common-utils.libsonnet").composable,
  local sulong_deps = composable((import "../../common.json").sulong.deps),

  local linux_amd64 = common.linux_amd64,
  local linux_aarch64 = common.linux_aarch64,
  local darwin_amd64 = common.darwin_amd64,
  local windows_amd64 = common.windows_amd64,

  nameOrEmpty(b):: if std.objectHas(b, "name") then
    ' (build "%s")' % b.name
  else "",

  local isNonEmptyString(s) = std.isString(s) && std.length(s)> 0,

  build_template:: {
    targets: [],
    suite:: error "suite not set" + $.nameOrEmpty(self),
    jdk:: error "jdk not set" + $.nameOrEmpty(self),
    os:: error "os not set" + $.nameOrEmpty(self),
    arch:: error "arch not set" + $.nameOrEmpty(self),
    job:: error "job not set" + $.nameOrEmpty(self),
    bitcode_config:: [],
    sulong_config:: [],
    gen_name_componentes::
      assert std.isArray(self.targets) : "targets must be an array" + $.nameOrEmpty(self);
      assert isNonEmptyString(self.suite) : "suite must be a non-empty string" + $.nameOrEmpty(self);
      assert isNonEmptyString(self.jdk) : "jdk must be a non-empty string" + $.nameOrEmpty(self);
      assert isNonEmptyString(self.os) : "os must be a non-empty string" + $.nameOrEmpty(self);
      assert isNonEmptyString(self.arch) : "arch must be a non-empty string" + $.nameOrEmpty(self);
      assert isNonEmptyString(self.job) : "job must be a non-empty string" + $.nameOrEmpty(self);
      assert std.isArray(self.bitcode_config) : "bitcode_config must be an array" + $.nameOrEmpty(self);
      assert std.isArray(self.sulong_config) : "sulong_config must be an array" + $.nameOrEmpty(self);
      self.targets + [self.suite] + [self.job] + self.bitcode_config + self.sulong_config + [self.jdk] + [self.os] + [self.arch],
    gen_name:: std.join("-", self.gen_name_componentes),
  },

  defBuild(b):: $.build_template + b + {
    assert self.gen_name == self.name : "Name error. expected '%s', actual '%s'" % [self.gen_name, self.name],
  } + if std.objectHasAll(b, "description_text") then { description: "%s with %s on %s/%s" % [b.description_text, self.jdk, self.os, self.arch]} else {},

  labsjdk_ce_11: common["labsjdk-ce-11"] {
    jdk:: "jdk11",
    downloads+: {
      # FIXME: do we really need to set EXTRA_JAVA_HOMES to an empty list?
      EXTRA_JAVA_HOMES: { pathlist: [] },
    },
  },

  labsjdk_ce_17: common["labsjdk-ce-17"] {
    jdk:: "jdk17",
    downloads+: {
      # FIXME: do we really need to set EXTRA_JAVA_HOMES to an empty list?
      EXTRA_JAVA_HOMES: { pathlist: [] },
    },
  },

  labsjdk_ee_11: common["labsjdk-ee-11"] {
    jdk:: "jdk11",
  },

  labsjdk_ee_17: common["labsjdk-ee-17"] {
    jdk:: "jdk17",
  },

  linux_amd64:: linux_amd64 + sulong_deps.linux,
  linux_aarch64:: linux_aarch64 + sulong_deps.linux,
  darwin_amd64:: darwin_amd64 + sulong_deps.darwin,
  windows_amd64:: windows_amd64 + sulong_deps.windows,

  sulong_notifications:: {
    notify_groups:: ["sulong"],
  },

  gate:: {
    targets+: ["gate"],
  },

  daily:: $.sulong_notifications {
    targets+: ["daily"],
  },

  weekly:: $.sulong_notifications {
    targets+: ["weekly"],
  },

  mxCommand:: {
    extra_mx_args+:: [],
    mx:: ["mx"] + self.extra_mx_args
  },

  mxStripJarsMixin:: {
    extra_mx_args+: ["--strip-jars"],
  },

  mxStrictMixin:: {
    extra_mx_args+: ["--strict-compliance"],
  },

  local firstLower(s) =
    if std.length(s) > 0 then
      std.asciiLower(s[0]) + s[1:]
    else s,

  Description(description):: { description_text:: description },

  gateTags(tags):: $.mxCommand + {
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
    extra_gate_args:: [],
    job:: std.join("-", processTags(std.split(tags, ","))),
    run+:
      # enforcing `tags` to be a string makes it easier to copy and paste from the ci config file
      assert std.isString(tags) : "gateTags(tags): the `tags` parameter must be a string" + $.nameOrEmpty(self);
      [self.mx + ["gate"] + self.extra_gate_args + ["--tags", tags]],
  } + self.Description("Run mx gate --tags " + tags),

  sulong_gateTest_default_tools:: {
    environment+: {
      CLANG_LLVM_AS: "llvm-as",
      CLANG_LLVM_LINK: "llvm-link",
      CLANG_LLVM_DIS: "llvm-dis",
      CLANG_LLVM_OPT: "opt",
      MX_TEST_RESULTS_PATTERN: "es-XXX.json",
      MX_TEST_RESULT_TAGS: "sulong",
    },
  },


  llvm38:: $.sulong_gateTest_default_tools {
    packages+: {
      llvm: "==3.8",
    },
    environment+: {
      NO_FEMBED_BITCODE: "true",
      CLANG_CC: "clang-3.8",
      CLANG_CXX: "clang-3.8 --driver-mode=g++",
      CLANG_LLVM_OBJCOPY: "objcopy",
      CLANG_LLVM_CONFIG: "llvm-config",
      CLANG_NO_OPTNONE: "1",
      CFLAGS: "-Wno-error",
    },
  },

  llvm4:: $.sulong_gateTest_default_tools {
    bitcode_config +:: ["v40"],
    packages+: {
      llvm: "==4.0.1",
    },
    environment+: {
      CLANG_CC: "clang-4.0",
      CLANG_CXX: "clang-4.0 --driver-mode=g++",
      CLANG_LLVM_OBJCOPY: "objcopy",
      CLANG_NO_OPTNONE: "1",
      CFLAGS: "-Wno-error",
    },
  },

  llvm6:: $.sulong_gateTest_default_tools {
    bitcode_config +:: ["v60"],
    packages+: {
      llvm: "==6.0.1",
    },
    environment+: {
      CLANG_CC: "clang-6.0",
      CLANG_CXX: "clang-6.0 --driver-mode=g++",
      CFLAGS: "-Wno-error",
    },
  },

  llvm8: $.sulong_gateTest_default_tools {
    bitcode_config +:: ["v80"],
    packages+: {
      llvm: "==8.0.0",
    },
    environment+: {
      CLANG_CC: "clang-8",
      CLANG_CXX: "clang-8 --driver-mode=g++",
      CFLAGS: "-Wno-error",
    },
  },

  llvmBundled:: {},

  requireGCC:: {
    packages+: {
      gcc: "==6.1.0",
    },
    downloads+: {
      DRAGONEGG_GCC: { name: "gcc+dragonegg", version: "4.6.4-1", platformspecific: true },
      DRAGONEGG_LLVM: { name: "clang+llvm", version: "3.2", platformspecific: true },
    },
  },

  requireGMP:: {
    downloads+: {
      LIBGMP: { name: "libgmp", version: "6.1.0", platformspecific: true },
    },
    environment+: {
      CPPFLAGS: "-g -I$LIBGMP/include",
      LD_LIBRARY_PATH: "$LIBGMP/lib:$LD_LIBRARY_PATH",
      LDFLAGS: "-L$LIBGMP/lib",
    },
  },
}
