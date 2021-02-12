# File is formatted with
# `jsonnetfmt --indent 2 --max-blank-lines 2 --sort-imports --string-style d --comment-style h -i ci.jsonnet`
{
  local common = import "../../common.jsonnet",
  local composable = (import "../../common-utils.libsonnet").composable,
  local sulong_deps = composable((import "../../common.json").sulong.deps),

  local linux_amd64 = common["linux-amd64"],
  local linux_aarch64 = common["linux-aarch64"],
  local darwin_amd64 = common["darwin-amd64"],
  local windows_amd64 = common["windows-amd64"],

  nameOrEmpty(b):: if std.objectHas(b, "name") then
    ' (build "%s")' % b.name
  else "",

  jdk8:: common.oraclejdk8,

  labsjdk_ce_11: common["labsjdk-ce-11"] {
    downloads+: {
      # FIXME: do we really need to set EXTRA_JAVA_HOMES to an empty list?
      EXTRA_JAVA_HOMES: { pathlist: [] },
    },
  },

  linux_amd64:: linux_amd64 + sulong_deps.linux,
  linux_aarch64:: linux_aarch64 + sulong_deps.linux,
  darwin_amd64:: darwin_amd64 + sulong_deps.darwin,
  windows_amd64:: windows_amd64 + sulong_deps.windows,

  gate:: {
    targets+: ["gate"],
  },

  weekly:: {
    targets+: ["weekly"],
  },

  gateTags(tags):: {
    run+:
      # enforcing `tags` to be a string makes it easier to copy and paste from the ci config file
      assert std.isString(tags) : "gateTags(tags): the `tags` parameter must be a string" + $.nameOrEmpty(self);
      [["mx", "gate", "--tags", tags]],
  },

  sulong_weekly_notifications:: {
    notify_groups:: ["sulong"],
  },

  style:: {
    packages+: {
      ruby: "==2.1.0",  # for mdl
    },
  },

  sulong_gateTest_default_tools:: {
    environment+: {
      CLANG_LLVM_AS: "llvm-as",
      CLANG_LLVM_LINK: "llvm-link",
      CLANG_LLVM_DIS: "llvm-dis",
      CLANG_LLVM_OPT: "opt",
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
      CLANG_NO_OPTNONE: "1",
      CFLAGS: "-Wno-error",
    },
  },

  llvm4:: $.sulong_gateTest_default_tools {
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

  llvm4_darwin_fix:: {
    # FIXME: We prune `null` entries to produce the original result.
    # Eventually, we should canonicalize this.
    environment: std.prune(super.environment + {
      CPPFLAGS: "-g",
      CFLAGS: null,
      CLANG_LLVM_OBJCOPY: null,
    }),
    timelimit: "0:45:00",
  },

  llvmBundled_darwin_fix: {
    # nothing to do
    environment+: {
      LD_LIBRARY_PATH: "$BUILD_DIR/main/sulong/mxbuild/darwin-amd64/SULONG_LLVM_ORG/lib:$LD_LIBRARY_PATH",
    },
    timelimit: "0:45:00",
  },

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
