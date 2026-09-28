suite = {
    "mxversion": "7.68.9",
    "name" : "sandbox-toolchains",
    "versionConflictResolution" : "latest",
    "capture_suite_commit_info": False,

    "imports" : {
        "suites" : [
            {
                "name" : "substratevm",
                "subdir" : True,
            },
            {
                "name" : "build-sandbox-llvm",
                "subdir" : True,
                "version" : "68998be338fa144c99a0a61db3c936bdc21ab1a3",
                "urls" : [
                    {"url" : "https://github.com/graalvm/llvm-project.git", "kind" : "git"},
                ],
            },
            {
                "name" : "musl",
                "version" : "c7bb8193aebe53edade0c92e3a3f7297bbf9067c",
                "urls" : [
                    {"url" : "https://github.com/graalvm/musl.git", "kind" : "git"},
                ],
                "foreign": True,
            },
            {
                "name" : "sulong",
                "subdir" : True,
            },
        ]
    },

    "libraries" : {
        "ZLIB": {
            "packedResource": True,
            "urls": ["https://zlib.net/fossils/zlib-1.3.2.tar.gz"],
            "digest": "sha512:70963771ea5d763614278a69b474f09b7d237ef8f53b675a10fe31d9923aeef601504b35d7ebd1b1e7f347e9ebb048e6b3b47fffdf137e7bdc7e8d5eb4ec4692",
        },
        "KERNEL_HEADERS": {
            # This is re-packaged as tar.bz2 from the OL9 RPM package.
            "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/sandbox-toolchain-deps/kernel-headers-5.14.0-687.36.1.el9_8.x86_64.tar.bz2"],
            "digest": "sha512:fad5684aab2d30f0c053907f57490e1effaa0a9e4d9981b5a7b028eecf500b8f020f865699913961e7ad0e65a08c2ffd07c5d3db812bf98abe05339ab6d2e748",
        },
        "MIMALLOC": {
            "packedResource": True,
            "urls": ["https://github.com/microsoft/mimalloc/archive/refs/tags/v3.4.5.tar.gz"],
            "digest": "sha512:6cb7d1adc653a14abd209f98c141179393a2aaf78916d46c85f1dd71dc9c898b6a4f3b8b7bda54e008202dce824b7fc8852310b68821700641f8fac9482b4e3e",
        },
        "RUST_SOURCE": {
            # don't unpack into the mx cache, we want to patch this
            "resource": True,
            "urls": ["https://lafo.ssw.uni-linz.ac.at/pub/sandbox-toolchain-deps/rust-1.94.0-2.tar.gz"],
            "digest": "sha512:3aa092b03bd45fa42af50dd791bc0bf065f422fb5a889f999cc576c3e2298bd4b8403359e2d4e7cb536bc0d24b77887998041a1b26c9bca50b6cc8c37678697c",
        },

        # build tools
        "GPERF": {
            "packedResource": True,
            "urls": ["https://ftp.gnu.org/gnu/gperf/gperf-3.3.tar.gz"],
            "digest": "sha512:246b75b8ce7d77d6a8725cd15f1cf2e68da404812573af1d5bf32dbe6ad4228f48757baefc77bcb1f5597c2397043c04d31d8a04ab507bfa7a80f85e1ab6045f",
        },

        # run-time dependencies of the JDK libraries (we have to build them for musl-swcfi)
        "EXPAT": {
            "packedResource": True,
            "urls": ["https://github.com/libexpat/libexpat/releases/download/R_2_8_3/expat-2.8.3.tar.bz2"],
            "digest": "sha512:0cf46319fa490b8ed0e9f114471641b037b5c224c4c7d864fd4f840fa239fd2346fd48c228a927d01054f373cb0b06f87d246ba6121f14d0cb4cf124994baae1",
        },
        "FONTCONFIG": {
            "packedResource": True,
            "urls": ["https://gitlab.freedesktop.org/api/v4/projects/890/packages/generic/fontconfig/2.18.3/fontconfig-2.18.3.tar.xz"],
            "digest": "sha512:c3e769f74dc21093ada48084bddaca1ab525b1c4f04c5c11f067b69346fab153cad95de8b8238b20b02e8b9679c314bf2e0e9c4218511d8fe0f1826e4c4c6a24",
        },

        # compile-time dependencies of the JDK libraries (headers and dummy shared object files are sufficient)
        "ALSA_LIB": {
            "urls": ["https://www.alsa-project.org/files/pub/lib/alsa-lib-1.2.15.tar.bz2"],
            "digest": "sha512:6cea9059265ef353a07f1b442004506f0f13883692ea35f03090282ca80db88055f470d2dca5bb54394fef0012711f0e9502d2d0f7fb66b27aa334bffb811559",
        },
        "CUPS": {
            "urls": ["https://github.com/OpenPrinting/cups/releases/download/v2.4.12/cups-2.4.12-source.tar.gz"],
            "digest": "sha512:a32c113ab799a4814538213bc3bf8bc20d54ff67a56505adb6a9b6f5dae853744bcb3c638d5df5a849097e3ce57f2b15225050cd89b69664231b1e4d883cd1f0",
        },
        "LIBX11": {
            "urls": ["https://www.x.org/releases/individual/lib/libX11-1.8.13.tar.gz"],
            "digest": "sha512:3dbcb261bbf56e8613b61e84af5d6924bf804a5fb90fe84f3bde46e4bec3b0c8c496d9e2a9b6717511fd37544fe84350092a744aa98640612f653640693d791e",
        },
        "LIBXEXT": {
            "urls": ["https://www.x.org/releases/individual/lib/libXext-1.3.7.tar.gz"],
            "digest": "sha512:016a4bb21926088c0c14b657b57a259523e134be2f8d35dfc7d3ad72be1fb44b01cb84cf76ebadf4fa30db54053386def5cf13308372e3b27e5ddd56d2b31d01",
        },
        "LIBXI": {
            "urls": ["https://www.x.org/releases/individual/lib/libXi-1.8.2.tar.gz"],
            "digest": "sha512:bbfdcd17338afce956cd50768a4b81e7aa102209d64d14f44c2d8c81f79fa770c14b8d5a31d75c4992a448fcc3c46c2533cc8afcd893ae654600fdf4a6ea81a2",
        },
        "LIBXRANDR": {
            "urls": ["https://www.x.org/releases/individual/lib/libXrandr-1.5.5.tar.gz"],
            "digest": "sha512:e880d8c69cfd7cfc5e70c7bc436e06d919977f36d3658b4d88d098e4262f619ab09eca5ce4dc444614b6e38cd38b236013eff6975e67c6e7282b34738cdeae78",
        },
        "LIBXRENDER": {
            "urls": ["https://www.x.org/releases/individual/lib/libXrender-0.9.12.tar.gz"],
            "digest": "sha512:b7cbe8ead3a4eeb7c42acede8569361cf11818d98d05ede75a5f0c48c3fb6b1c0b3b62bb2ba6aea19b4804938512e63ebed127928b1a553b518e3ab974bd089d",
        },
        "LIBXT": {
            "urls": ["https://www.x.org/releases/individual/lib/libXt-1.3.1.tar.gz"],
            "digest": "sha512:9af5e3ba9674f2a186698d4735e862fb959112ee0bd4bc4732c0f6bd44dc704c7477b5bf89bf95bc887c0e0cae86330b7d0b568902725cd3f0c152851725a0b4",
        },
        "LIBXTST": {
            "urls": ["https://www.x.org/releases/individual/lib/libXtst-1.2.5.tar.gz"],
            "digest": "sha512:fea81f027ef7012d94dbf859f31db844b61c117b88f3bb847e0b4a3aa0280415d5bf3d7fd5966c5640fd54b3ab4afecc5b73fdfd98b8d47da5cb86371dfb6c04",
        },
        "XORGPROTO": {
            "urls": ["https://www.x.org/releases/individual/proto/xorgproto-2024.1.tar.gz"],
            "digest": "sha512:c2d67a98c5ba9b2f4d0b844c96dab342c497710753a8878b75dbf12ecd64b105c9ee3c5fd11eb91e45960420cf8dd7d02547072a32d5c53e58e009394fe33666",
        },
    },

    "projects" : {

        #
        # The toolchain build process builds the following projects, in that order:
        #
        # * sandbox-llvm / SANDBOX_LLVM (declared in the LLVM-project repo)
        #     Builds our patched LLVM compiler with sandbox support.
        #     This is building with a downloaded vanilla LLVM, and linking against the regular system libraries.
        #
        # * bootstrap-compiler-rt-[swcfi|hwcfi|nocfi]
        #     Builds compiler-rt with sandbox-llvm, and sandbox flags enabled, but linked against the regular system libraries.
        #     This is to resolve a cyclic dependency between compiler-rt, musl and libc++/libc++abi/libunwind.
        #
        # * BOOTSTRAP_RESOURCE_DIR
        #     Clang resource dir containing headers from SANDBOX_LLVM and bootstrap-compiler-rt.
        #
        # * sandboxed-musl-[swcfi|hwcfi|nocfi]
        #     Using clang from SANDBOX_LLVM and bootstrap-compiler-rt, build a musl libc with sandboxing enabled.
        #
        # * BOOTSTRAP_MUSL_SYSROOT
        #     Build a minimal sysroot containing just MUSL (swcfi+hwcfi), and kernel headers.
        #
        # * llvm-cxx-runtimes-musl-[swcfi|hwcfi|nocfi]
        #     Build the LLVM C++ runtimes and OpenMP host runtime with sandboxing enabled.
        #     Uses the compiler from SANDBOX_LLVM and BOOTSTRAP_RESOURCE_DIR, and libraries and headers from BOOTSTRAP_MUSL_SYSROOT.
        #
        # * BOOTSTRAP_MUSL_SYSROOT_CXX
        #     Build a sysroot with MUSL, mock kernel headers and the C++ runtime libraries (swcfi+hwcfi).
        #
        # * llvm-runtimes-musl-[swcfi|hwcfi|nocfi]
        #     Build compiler-rt and flang-rt with sandboxing enabled and linked against MUSL.
        #     Uses the compiler from SANDBOX_LLVM, and libraries and headers from BOOTSTRAP_MUSL_SYSROOT_CXX.
        #
        # * SANDBOXED_MUSL_TOOLCHAIN_STAGE1
        #     Full toolchain with sandboxed versions of musl and the LLVM runtime libraries
        #     (C++, compiler-rt and OpenMP).
        #     Includes per-target sysroots in toolchains/sysroot, and a copy of SANDBOX_LLVM in toolchains/llvm.
        #
        # * libraries (sandboxed-zlib)
        #     Build additional libraries using the toolchain from SANDBOXED_MUSL_TOOLCHAIN_STAGE1.
        #
        # * SANDBOXED_MUSL_TOOLCHAIN
        #     Full toolchain like SANDBOXED_MUSL_TOOLCHAIN_STAGE1, plus libraries.
        #     This is the final artifact ready for use by GraalOS or native-image.
        #

        "bootstrap-compiler-rt-swcfi" : {
            "class" : "CMakeNinjaProject",
            "vpath" : True,
            "subDir" : "src",
            "sourceDir" : "<path:build-sandbox-llvm>/..",
            "cmakeSubdir" : "runtimes",
            "ninja_install_targets" : ["install"],
            "symlinkSource" : True,
            "results" : ["bootstrap-swcfi"],
            "max_jobs" : "128",
            "cmakeConfig" : {
                "CMAKE_BUILD_TYPE": "Release",
                "CMAKE_INSTALL_PREFIX" : "bootstrap-swcfi",

                "CMAKE_C_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang",
                "CMAKE_C_COMPILER_TARGET": "x86_64-unknown-linux-musl_swcfi",
                "CMAKE_CXX_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang++",
                "CMAKE_CXX_COMPILER_TARGET": "x86_64-unknown-linux-musl_swcfi",
                "CMAKE_ASM_COMPILER_TARGET": "x86_64-unknown-linux-musl_swcfi",

                # SANDBOX_LLVM is configured to default to link against compilert-rt and libc++.
                # Since we haven't built them yet, this leads to an error in cmake when doing a test-compile.
                "CMAKE_C_COMPILER_WORKS" : "YES",
                "CMAKE_CXX_COMPILER_WORKS" : "YES",

                "RUNTIMES_CMAKE_ARGS": "-DCMAKE_C_COMPILER_TARGET=x86_64-unknown-linux-musl_swcfi;-DCMAKE_CXX_COMPILER_TARGET=x86_64-unknown-linux-musl_swcfi;-DCMAKE_ASM_COMPILER_TARGET=x86_64-unknown-linux-musl_swcfi",
                "BUILTINS_CMAKE_ARGS": "-DCMAKE_C_COMPILER_TARGET=x86_64-unknown-linux-musl_swcfi;-DCMAKE_CXX_COMPILER_TARGET=x86_64-unknown-linux-musl_swcfi;-DCMAKE_ASM_COMPILER_TARGET=x86_64-unknown-linux-musl_swcfi",

                "LLVM_DIR": "<path:CACHED_SANDBOX_LLVM>/usr/lib/cmake/llvm",

                "LLVM_ENABLE_RUNTIMES": "compiler-rt",

                "COMPILER_RT_DEFAULT_TARGET_ONLY": "ON",

                "COMPILER_RT_USE_LLVM_UNWINDER": "YES",
                "COMPILER_RT_USE_BUILTINS_LIBRARY": "NO",
                "COMPILER_RT_CXX_LIBRARY": "libcxx",

                "COMPILER_RT_BUILD_BUILTINS": "YES",
                "COMPILER_RT_BUILD_CRT": "YES",
                "COMPILER_RT_BUILD_STANDALONE_LIBATOMIC": "NO",

                "COMPILER_RT_BUILD_XRAY": "NO",
                "COMPILER_RT_BUILD_LIBFUZZER": "NO",
                "COMPILER_RT_BUILD_PROFILE": "NO",
                "COMPILER_RT_BUILD_MEMPROF": "NO",
                "COMPILER_RT_BUILD_ORC": "NO",
                "COMPILER_RT_BUILD_SANITIZERS": "NO",
                "COMPILER_RT_BUILD_CTX_PROFILE": "NO",
            },
            "clangFormat" : False,
            "buildDependencies" : [ "build-sandbox-llvm:CACHED_SANDBOX_LLVM" ],
        },

        "bootstrap-compiler-rt-hwcfi" : {
            "class" : "CMakeNinjaProject",
            "vpath" : True,
            "subDir" : "src",
            "sourceDir" : "<path:build-sandbox-llvm>/..",
            "cmakeSubdir" : "runtimes",
            "ninja_install_targets" : ["install"],
            "symlinkSource" : True,
            "results" : ["bootstrap-hwcfi"],
            "max_jobs" : "128",
            "cmakeConfig" : {
                "CMAKE_BUILD_TYPE": "Release",
                "CMAKE_INSTALL_PREFIX" : "bootstrap-hwcfi",

                "CMAKE_C_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang",
                "CMAKE_C_COMPILER_TARGET": "x86_64-unknown-linux-musl_hwcfi",
                "CMAKE_CXX_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang++",
                "CMAKE_CXX_COMPILER_TARGET": "x86_64-unknown-linux-musl_hwcfi",
                "CMAKE_ASM_COMPILER_TARGET": "x86_64-unknown-linux-musl_hwcfi",

                # SANDBOX_LLVM is configured to default to link against compilert-rt and libc++.
                # Since we haven't built them yet, this leads to an error in cmake when doing a test-compile.
                "CMAKE_C_COMPILER_WORKS" : "YES",
                "CMAKE_CXX_COMPILER_WORKS" : "YES",

                "RUNTIMES_CMAKE_ARGS": "-DCMAKE_C_COMPILER_TARGET=x86_64-unknown-linux-musl_hwcfi;-DCMAKE_CXX_COMPILER_TARGET=x86_64-unknown-linux-musl_hwcfi;-DCMAKE_ASM_COMPILER_TARGET=x86_64-unknown-linux-musl_hwcfi",
                "BUILTINS_CMAKE_ARGS": "-DCMAKE_C_COMPILER_TARGET=x86_64-unknown-linux-musl_hwcfi;-DCMAKE_CXX_COMPILER_TARGET=x86_64-unknown-linux-musl_hwcfi;-DCMAKE_ASM_COMPILER_TARGET=x86_64-unknown-linux-musl_hwcfi",

                "LLVM_DIR": "<path:CACHED_SANDBOX_LLVM>/usr/lib/cmake/llvm",

                "LLVM_ENABLE_RUNTIMES": "compiler-rt",

                "COMPILER_RT_DEFAULT_TARGET_ONLY": "ON",

                "COMPILER_RT_USE_LLVM_UNWINDER": "YES",
                "COMPILER_RT_USE_BUILTINS_LIBRARY": "NO",
                "COMPILER_RT_CXX_LIBRARY": "libcxx",

                "COMPILER_RT_BUILD_BUILTINS": "YES",
                "COMPILER_RT_BUILD_CRT": "YES",
                "COMPILER_RT_BUILD_STANDALONE_LIBATOMIC": "NO",

                "COMPILER_RT_BUILD_XRAY": "NO",
                "COMPILER_RT_BUILD_LIBFUZZER": "NO",
                "COMPILER_RT_BUILD_PROFILE": "NO",
                "COMPILER_RT_BUILD_MEMPROF": "NO",
                "COMPILER_RT_BUILD_ORC": "NO",
                "COMPILER_RT_BUILD_SANITIZERS": "NO",
                "COMPILER_RT_BUILD_CTX_PROFILE": "NO",
            },
            "clangFormat" : False,
            "buildDependencies" : [ "build-sandbox-llvm:CACHED_SANDBOX_LLVM" ],
        },

        "bootstrap-compiler-rt-nocfi" : {
            "class" : "CMakeNinjaProject",
            "vpath" : True,
            "subDir" : "src",
            "sourceDir" : "<path:build-sandbox-llvm>/..",
            "cmakeSubdir" : "runtimes",
            "ninja_install_targets" : ["install"],
            "symlinkSource" : True,
            "results" : ["bootstrap-nocfi"],
            "max_jobs" : "128",
            "cmakeConfig" : {
                "CMAKE_BUILD_TYPE": "Release",
                "CMAKE_INSTALL_PREFIX" : "bootstrap-nocfi",

                "CMAKE_C_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang",
                "CMAKE_C_COMPILER_TARGET": "x86_64-unknown-linux-musl",
                "CMAKE_CXX_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang++",
                "CMAKE_CXX_COMPILER_TARGET": "x86_64-unknown-linux-musl",
                "CMAKE_ASM_COMPILER_TARGET": "x86_64-unknown-linux-musl",

                # SANDBOX_LLVM is configured to default to link against compilert-rt and libc++.
                # Since we haven't built them yet, this leads to an error in cmake when doing a test-compile.
                "CMAKE_C_COMPILER_WORKS" : "YES",
                "CMAKE_CXX_COMPILER_WORKS" : "YES",

                "RUNTIMES_CMAKE_ARGS": "-DCMAKE_C_COMPILER_TARGET=x86_64-unknown-linux-musl;-DCMAKE_CXX_COMPILER_TARGET=x86_64-unknown-linux-musl;-DCMAKE_ASM_COMPILER_TARGET=x86_64-unknown-linux-musl",
                "BUILTINS_CMAKE_ARGS": "-DCMAKE_C_COMPILER_TARGET=x86_64-unknown-linux-musl;-DCMAKE_CXX_COMPILER_TARGET=x86_64-unknown-linux-musl;-DCMAKE_ASM_COMPILER_TARGET=x86_64-unknown-linux-musl",

                "LLVM_DIR": "<path:CACHED_SANDBOX_LLVM>/usr/lib/cmake/llvm",

                "LLVM_ENABLE_RUNTIMES": "compiler-rt",

                "COMPILER_RT_DEFAULT_TARGET_ONLY": "ON",

                "COMPILER_RT_USE_LLVM_UNWINDER": "YES",
                "COMPILER_RT_USE_BUILTINS_LIBRARY": "NO",
                "COMPILER_RT_CXX_LIBRARY": "libcxx",

                "COMPILER_RT_BUILD_BUILTINS": "YES",
                "COMPILER_RT_BUILD_CRT": "YES",
                "COMPILER_RT_BUILD_STANDALONE_LIBATOMIC": "NO",

                "COMPILER_RT_BUILD_XRAY": "NO",
                "COMPILER_RT_BUILD_LIBFUZZER": "NO",
                "COMPILER_RT_BUILD_PROFILE": "NO",
                "COMPILER_RT_BUILD_MEMPROF": "NO",
                "COMPILER_RT_BUILD_ORC": "NO",
                "COMPILER_RT_BUILD_SANITIZERS": "NO",
                "COMPILER_RT_BUILD_CTX_PROFILE": "NO",
            },
            "clangFormat" : False,
            "buildDependencies" : [ "build-sandbox-llvm:CACHED_SANDBOX_LLVM" ],
        },


        "llvm-cxx-runtimes-musl-swcfi" : {
            "class" : "CMakeNinjaProject",
            "vpath" : True,
            "subDir" : "src",
            "sourceDir" : "<path:build-sandbox-llvm>/..",
            "cmakeSubdir" : "runtimes",
            "ninja_install_targets" : ["install"],
            "symlinkSource" : True,
            "results" : ["sysroot/x86_64-unknown-linux-musl_swcfi"],
            # The LLVM build is very parallelizable, and everything else needs to wait for this build.
            # Turn this up to very high parallelization, to utilize our huge CI machines.
            # mx caps this to the number of CPUs anyway, so it doesn't hurt for local development.
            "max_jobs" : "128",
            "cmakeConfig" : {
                "CMAKE_BUILD_TYPE": "Release",
                "CMAKE_INSTALL_PREFIX" : "sysroot/x86_64-unknown-linux-musl_swcfi",

                "CMAKE_C_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang",
                "CMAKE_CXX_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang++",
                "CMAKE_CXX_COMPILER_TARGET" : "x86_64-unknown-linux-musl_swcfi",
                "CMAKE_ASM_COMPILER_TARGET" : "x86_64-unknown-linux-musl_swcfi",

                # SANDBOX_LLVM is configured to default to link against libc++. Since we haven't built it yet,
                # this leads to an error in cmake when doing a c++ test-compile.
                # The C compiler already works because of the bootstrap compiler-rt (pulled in via -resource-dir).
                "CMAKE_CXX_COMPILER_WORKS" : "YES",

                "CMAKE_SYSROOT" : "<path:BOOTSTRAP_MUSL_SYSROOT>/sysroot/x86_64-unknown-linux-musl_swcfi",
                "CMAKE_C_COMPILER_TARGET" : "x86_64-unknown-linux-musl_swcfi",
                "CMAKE_C_FLAGS": "-fPIC -resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-swcfi",
                "CMAKE_CXX_FLAGS": "-fPIC -resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-swcfi",
                "CMAKE_EXE_LINKER_FLAGS": "-resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-swcfi",
                "CMAKE_SHARED_LINKER_FLAGS": "-resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-swcfi",

                "LLVM_ENABLE_RUNTIMES": "libcxx;libcxxabi;libunwind;openmp",
                "LLVM_INCLUDE_TESTS": "NO",
                "LLVM_DEFAULT_TARGET_TRIPLE": "x86_64-unknown-linux-musl_swcfi",

                # OpenMP is installed with libc++ into the target sysroot.
                "OPENMP_ENABLE_LIBOMPTARGET": "NO",
                "OPENMP_ENABLE_OMPT_TOOLS": "NO",
                "LIBOMP_OMPD_SUPPORT": "NO",
                "LIBOMP_USE_HWLOC": "NO",
                "LIBOMP_ENABLE_SHARED": "YES",
                "LIBOMP_USE_ADAPTIVE_LOCKS": "NO",
                "LIBOMP_USE_ITT_NOTIFY": "NO",
                "LIBOMP_CXXFLAGS": "-DKMP_HAVE_MWAIT=0 -DKMP_HAVE_UMWAIT=0 -DKMP_USE_TSX=0",

                "LIBCXX_USE_COMPILER_RT": "YES",
                "LIBCXX_USE_COMPILER_RT_ATOMICS": "YES",
                "LIBCXX_HAS_MUSL_LIBC": "YES",

                "LIBCXXABI_USE_COMPILER_RT": "YES",
                "LIBCXXABI_USE_LLVM_UNWINDER": "YES",

                "LIBUNWIND_USE_COMPILER_RT": "YES",
                "LIBUNWIND_ENABLE_SW_CET": "YES",
            },
            "clangFormat" : False,
            "buildDependencies" : [ "build-sandbox-llvm:CACHED_SANDBOX_LLVM", "BOOTSTRAP_MUSL_SYSROOT", "BOOTSTRAP_RESOURCE_DIR" ],
        },

        "llvm-cxx-runtimes-musl-hwcfi" : {
            "class" : "CMakeNinjaProject",
            "vpath" : True,
            "subDir" : "src",
            "sourceDir" : "<path:build-sandbox-llvm>/..",
            "cmakeSubdir" : "runtimes",
            "ninja_install_targets" : ["install"],
            "symlinkSource" : True,
            "results" : ["sysroot/x86_64-unknown-linux-musl_hwcfi"],
            # The LLVM build is very parallelizable, and everything else needs to wait for this build.
            # Turn this up to very high parallelization, to utilize our huge CI machines.
            # mx caps this to the number of CPUs anyway, so it doesn't hurt for local development.
            "max_jobs" : "128",
            "cmakeConfig" : {
                "CMAKE_BUILD_TYPE": "Release",
                "CMAKE_INSTALL_PREFIX" : "sysroot/x86_64-unknown-linux-musl_hwcfi",

                "CMAKE_C_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang",
                "CMAKE_CXX_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang++",
                "CMAKE_CXX_COMPILER_TARGET" : "x86_64-unknown-linux-musl_hwcfi",
                "CMAKE_ASM_COMPILER_TARGET" : "x86_64-unknown-linux-musl_hwcfi",

                # SANDBOX_LLVM is configured to default to link against libc++. Since we haven't built it yet,
                # this leads to an error in cmake when doing a c++ test-compile.
                # The C compiler already works because of the bootstrap compiler-rt (pulled in via -resource-dir).
                "CMAKE_CXX_COMPILER_WORKS" : "YES",

                "CMAKE_SYSROOT" : "<path:BOOTSTRAP_MUSL_SYSROOT>/sysroot/x86_64-unknown-linux-musl_hwcfi",
                "CMAKE_C_COMPILER_TARGET" : "x86_64-unknown-linux-musl_hwcfi",
                "CMAKE_C_FLAGS": "-fPIC -resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-hwcfi",
                "CMAKE_CXX_FLAGS": "-fPIC -resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-hwcfi",
                "CMAKE_EXE_LINKER_FLAGS": "-resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-hwcfi",
                "CMAKE_SHARED_LINKER_FLAGS": "-resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-hwcfi",

                "LLVM_ENABLE_RUNTIMES": "libcxx;libcxxabi;libunwind;openmp",
                "LLVM_INCLUDE_TESTS": "NO",
                "LLVM_DEFAULT_TARGET_TRIPLE": "x86_64-unknown-linux-musl_hwcfi",

                # OpenMP is installed with libc++ into the target sysroot.
                "OPENMP_ENABLE_LIBOMPTARGET": "NO",
                "OPENMP_ENABLE_OMPT_TOOLS": "NO",
                "LIBOMP_OMPD_SUPPORT": "NO",
                "LIBOMP_USE_HWLOC": "NO",
                "LIBOMP_ENABLE_SHARED": "YES",
                "LIBOMP_USE_ADAPTIVE_LOCKS": "NO",
                "LIBOMP_USE_ITT_NOTIFY": "NO",
                "LIBOMP_CXXFLAGS": "-DKMP_HAVE_MWAIT=0 -DKMP_HAVE_UMWAIT=0 -DKMP_USE_TSX=0",

                "LIBCXX_USE_COMPILER_RT": "YES",
                "LIBCXX_USE_COMPILER_RT_ATOMICS": "YES",
                "LIBCXX_HAS_MUSL_LIBC": "YES",

                "LIBCXXABI_USE_COMPILER_RT": "YES",
                "LIBCXXABI_USE_LLVM_UNWINDER": "YES",

                "LIBUNWIND_USE_COMPILER_RT": "YES",
            },
            "clangFormat" : False,
            "buildDependencies" : [ "build-sandbox-llvm:CACHED_SANDBOX_LLVM", "BOOTSTRAP_MUSL_SYSROOT", "BOOTSTRAP_RESOURCE_DIR" ],
        },

        "llvm-cxx-runtimes-musl-nocfi" : {
            "class" : "CMakeNinjaProject",
            "vpath" : True,
            "subDir" : "src",
            "sourceDir" : "<path:build-sandbox-llvm>/..",
            "cmakeSubdir" : "runtimes",
            "ninja_install_targets" : ["install"],
            "symlinkSource" : True,
            "results" : ["sysroot/x86_64-unknown-linux-musl"],
            # The LLVM build is very parallelizable, and everything else needs to wait for this build.
            # Turn this up to very high parallelization, to utilize our huge CI machines.
            # mx caps this to the number of CPUs anyway, so it doesn't hurt for local development.
            "max_jobs" : "128",
            "cmakeConfig" : {
                "CMAKE_BUILD_TYPE": "Release",
                "CMAKE_INSTALL_PREFIX" : "sysroot/x86_64-unknown-linux-musl",

                "CMAKE_C_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang",
                "CMAKE_CXX_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang++",
                "CMAKE_CXX_COMPILER_TARGET" : "x86_64-unknown-linux-musl",
                "CMAKE_ASM_COMPILER_TARGET" : "x86_64-unknown-linux-musl",

                # SANDBOX_LLVM is configured to default to link against libc++. Since we haven't built it yet,
                # this leads to an error in cmake when doing a c++ test-compile.
                # The C compiler already works because of the bootstrap compiler-rt (pulled in via -resource-dir).
                "CMAKE_CXX_COMPILER_WORKS" : "YES",

                "CMAKE_SYSROOT" : "<path:BOOTSTRAP_MUSL_SYSROOT>/sysroot/x86_64-unknown-linux-musl",
                "CMAKE_C_COMPILER_TARGET" : "x86_64-unknown-linux-musl",
                "CMAKE_C_FLAGS": "-fPIC -resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-nocfi",
                "CMAKE_CXX_FLAGS": "-fPIC -resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-nocfi",
                "CMAKE_EXE_LINKER_FLAGS": "-resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-nocfi",
                "CMAKE_SHARED_LINKER_FLAGS": "-resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-nocfi",

                "LLVM_ENABLE_RUNTIMES": "libcxx;libcxxabi;libunwind;openmp",
                "LLVM_INCLUDE_TESTS": "NO",
                "LLVM_DEFAULT_TARGET_TRIPLE": "x86_64-unknown-linux-musl",

                # OpenMP is installed with libc++ into the target sysroot.
                "OPENMP_ENABLE_LIBOMPTARGET": "NO",
                "OPENMP_ENABLE_OMPT_TOOLS": "NO",
                "LIBOMP_OMPD_SUPPORT": "NO",
                "LIBOMP_USE_HWLOC": "NO",
                "LIBOMP_ENABLE_SHARED": "YES",
                "LIBOMP_USE_ADAPTIVE_LOCKS": "NO",
                "LIBOMP_USE_ITT_NOTIFY": "NO",
                "LIBOMP_CXXFLAGS": "-DKMP_HAVE_MWAIT=0 -DKMP_HAVE_UMWAIT=0 -DKMP_USE_TSX=0",

                # The module interface is ABI-common across the CFI variants. Build one
                # canonical copy and expose it through Flang's compiler-side search path.
                "LIBOMP_FORTRAN_MODULES": "NO",
                "LIBOMP_FORTRAN_MODULES_COMPILER": "<path:CACHED_SANDBOX_LLVM>/usr/bin/flang;--target=x86_64-unknown-linux-musl;-resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-nocfi",
                "LIBOMP_MODULES_INSTALL_PATH": "include/flang/OpenMP",

                "LIBCXX_USE_COMPILER_RT": "YES",
                "LIBCXX_USE_COMPILER_RT_ATOMICS": "YES",
                "LIBCXX_HAS_MUSL_LIBC": "YES",

                "LIBCXXABI_USE_COMPILER_RT": "YES",
                "LIBCXXABI_USE_LLVM_UNWINDER": "YES",

                "LIBUNWIND_USE_COMPILER_RT": "YES",
            },
            "clangFormat" : False,
            "buildDependencies" : [ "build-sandbox-llvm:CACHED_SANDBOX_LLVM", "BOOTSTRAP_MUSL_SYSROOT", "BOOTSTRAP_RESOURCE_DIR" ],
        },

        "llvm-runtimes-musl-swcfi" : {
            "class" : "CMakeNinjaProject",
            "vpath" : True,
            "subDir" : "src",
            "sourceDir" : "<path:build-sandbox-llvm>/..",
            "cmakeSubdir" : "runtimes",
            "ninja_install_targets" : ["install"],
            "symlinkSource" : True,
            "results" : ["llvm"],
            # The LLVM build is very parallelizable, and everything else needs to wait for this build.
            # Turn this up to very high parallelization, to utilize our huge CI machines.
            # mx caps this to the number of CPUs anyway, so it doesn't hurt for local development.
            "max_jobs" : "128",
            "cmakeConfig" : {
                "CMAKE_BUILD_TYPE": "Release",
                "CMAKE_INSTALL_PREFIX" : "llvm/lib/clang/22",

                "CMAKE_C_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang",
                "CMAKE_C_COMPILER_TARGET" : "x86_64-unknown-linux-musl_swcfi",
                "CMAKE_CXX_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang++",
                "CMAKE_CXX_COMPILER_TARGET" : "x86_64-unknown-linux-musl_swcfi",
                "CMAKE_ASM_COMPILER_TARGET" : "x86_64-unknown-linux-musl_swcfi",
                "CMAKE_Fortran_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/flang",
                "CMAKE_Fortran_COMPILER_TARGET" : "x86_64-unknown-linux-musl_swcfi",
                # The Fortran compiler cannot link a test program until flang-rt has been built.
                "CMAKE_Fortran_COMPILER_WORKS" : "YES",

                "CMAKE_SYSROOT" : "<path:BOOTSTRAP_MUSL_SYSROOT_CXX>/sysroot/x86_64-unknown-linux-musl_swcfi",
                "CMAKE_C_FLAGS": "-fPIC -resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-swcfi",
                "CMAKE_CXX_FLAGS": "-fPIC -resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-swcfi",
                "CMAKE_Fortran_FLAGS": "-fPIC -resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-swcfi",
                "CMAKE_EXE_LINKER_FLAGS": "-resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-swcfi",
                "CMAKE_SHARED_LINKER_FLAGS": "-resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-swcfi",

                "LLVM_ENABLE_RUNTIMES": "compiler-rt;flang-rt",

                "LLVM_DEFAULT_TARGET_TRIPLE": "x86_64-unknown-linux-musl_swcfi",
                "LLVM_ENABLE_PER_TARGET_RUNTIME_DIR": "YES",

                # This install prefix is already the compiler resource directory.
                "FLANG_RT_INSTALL_RESOURCE_PATH:STRING": ".",
                "FLANG_RT_INCLUDE_TESTS": "NO",
                "FLANG_RT_LIBCXX_PROVIDER": "system",

                "COMPILER_RT_USE_LLVM_UNWINDER": "YES",
                "COMPILER_RT_USE_BUILTINS_LIBRARY": "YES",
                "COMPILER_RT_CXX_LIBRARY": "libcxx",

                "COMPILER_RT_BUILD_BUILTINS": "YES",
                "COMPILER_RT_BUILD_CRT": "YES",
                "COMPILER_RT_BUILD_STANDALONE_LIBATOMIC": "YES",

                "COMPILER_RT_BUILD_XRAY": "NO",
                "COMPILER_RT_BUILD_LIBFUZZER": "NO",
                "COMPILER_RT_BUILD_PROFILE": "NO",
                "COMPILER_RT_BUILD_MEMPROF": "NO",
                "COMPILER_RT_BUILD_ORC": "NO",
                "COMPILER_RT_BUILD_SANITIZERS": "NO",
                "COMPILER_RT_BUILD_CTX_PROFILE": "NO",
            },
            "clangFormat" : False,
            "buildDependencies" : [ "build-sandbox-llvm:CACHED_SANDBOX_LLVM", "BOOTSTRAP_MUSL_SYSROOT_CXX", "BOOTSTRAP_RESOURCE_DIR" ],
        },

        "llvm-runtimes-musl-hwcfi" : {
            "class" : "CMakeNinjaProject",
            "vpath" : True,
            "subDir" : "src",
            "sourceDir" : "<path:build-sandbox-llvm>/..",
            "cmakeSubdir" : "runtimes",
            "ninja_install_targets" : ["install"],
            "symlinkSource" : True,
            "results" : ["llvm"],
            # The LLVM build is very parallelizable, and everything else needs to wait for this build.
            # Turn this up to very high parallelization, to utilize our huge CI machines.
            # mx caps this to the number of CPUs anyway, so it doesn't hurt for local development.
            "max_jobs" : "128",
            "cmakeConfig" : {
                "CMAKE_BUILD_TYPE": "Release",
                "CMAKE_INSTALL_PREFIX" : "llvm/lib/clang/22",

                "CMAKE_C_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang",
                "CMAKE_C_COMPILER_TARGET" : "x86_64-unknown-linux-musl_hwcfi",
                "CMAKE_CXX_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang++",
                "CMAKE_CXX_COMPILER_TARGET" : "x86_64-unknown-linux-musl_hwcfi",
                "CMAKE_ASM_COMPILER_TARGET" : "x86_64-unknown-linux-musl_hwcfi",
                "CMAKE_Fortran_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/flang",
                "CMAKE_Fortran_COMPILER_TARGET" : "x86_64-unknown-linux-musl_hwcfi",
                # The Fortran compiler cannot link a test program until flang-rt has been built.
                "CMAKE_Fortran_COMPILER_WORKS" : "YES",

                "CMAKE_SYSROOT" : "<path:BOOTSTRAP_MUSL_SYSROOT_CXX>/sysroot/x86_64-unknown-linux-musl_hwcfi",
                "CMAKE_C_FLAGS": "-fPIC -resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-hwcfi",
                "CMAKE_CXX_FLAGS": "-fPIC -resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-hwcfi",
                "CMAKE_Fortran_FLAGS": "-fPIC -resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-hwcfi",
                "CMAKE_EXE_LINKER_FLAGS": "-resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-hwcfi",
                "CMAKE_SHARED_LINKER_FLAGS": "-resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-hwcfi",

                "LLVM_ENABLE_RUNTIMES": "compiler-rt;flang-rt",

                "LLVM_DEFAULT_TARGET_TRIPLE": "x86_64-unknown-linux-musl_hwcfi",
                "LLVM_ENABLE_PER_TARGET_RUNTIME_DIR": "YES",

                # This install prefix is already the compiler resource directory.
                "FLANG_RT_INSTALL_RESOURCE_PATH:STRING": ".",
                "FLANG_RT_INCLUDE_TESTS": "NO",
                "FLANG_RT_LIBCXX_PROVIDER": "system",

                "COMPILER_RT_USE_LLVM_UNWINDER": "YES",
                "COMPILER_RT_USE_BUILTINS_LIBRARY": "YES",
                "COMPILER_RT_CXX_LIBRARY": "libcxx",

                "COMPILER_RT_BUILD_BUILTINS": "YES",
                "COMPILER_RT_BUILD_CRT": "YES",
                "COMPILER_RT_BUILD_STANDALONE_LIBATOMIC": "YES",

                "COMPILER_RT_BUILD_XRAY": "NO",
                "COMPILER_RT_BUILD_LIBFUZZER": "NO",
                "COMPILER_RT_BUILD_PROFILE": "NO",
                "COMPILER_RT_BUILD_MEMPROF": "NO",
                "COMPILER_RT_BUILD_ORC": "NO",
                "COMPILER_RT_BUILD_SANITIZERS": "NO",
                "COMPILER_RT_BUILD_CTX_PROFILE": "NO",
            },
            "clangFormat" : False,
            "buildDependencies" : [ "build-sandbox-llvm:CACHED_SANDBOX_LLVM", "BOOTSTRAP_MUSL_SYSROOT_CXX", "BOOTSTRAP_RESOURCE_DIR" ],
        },

        "llvm-runtimes-musl-nocfi" : {
            "class" : "CMakeNinjaProject",
            "vpath" : True,
            "subDir" : "src",
            "sourceDir" : "<path:build-sandbox-llvm>/..",
            "cmakeSubdir" : "runtimes",
            "ninja_install_targets" : ["install"],
            "symlinkSource" : True,
            "results" : ["llvm"],
            # The LLVM build is very parallelizable, and everything else needs to wait for this build.
            # Turn this up to very high parallelization, to utilize our huge CI machines.
            # mx caps this to the number of CPUs anyway, so it doesn't hurt for local development.
            "max_jobs" : "128",
            "cmakeConfig" : {
                "CMAKE_BUILD_TYPE": "Release",
                "CMAKE_INSTALL_PREFIX" : "llvm/lib/clang/22",

                "CMAKE_C_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang",
                "CMAKE_C_COMPILER_TARGET" : "x86_64-unknown-linux-musl",
                "CMAKE_CXX_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang++",
                "CMAKE_CXX_COMPILER_TARGET" : "x86_64-unknown-linux-musl",
                "CMAKE_ASM_COMPILER_TARGET" : "x86_64-unknown-linux-musl",
                "CMAKE_Fortran_COMPILER" : "<path:CACHED_SANDBOX_LLVM>/usr/bin/flang",
                "CMAKE_Fortran_COMPILER_TARGET" : "x86_64-unknown-linux-musl",
                # The Fortran compiler cannot link a test program until flang-rt has been built.
                "CMAKE_Fortran_COMPILER_WORKS" : "YES",

                "CMAKE_SYSROOT" : "<path:BOOTSTRAP_MUSL_SYSROOT_CXX>/sysroot/x86_64-unknown-linux-musl",
                "CMAKE_C_FLAGS": "-fPIC -resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-nocfi",
                "CMAKE_CXX_FLAGS": "-fPIC -resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-nocfi",
                "CMAKE_Fortran_FLAGS": "-fPIC -resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-nocfi",
                "CMAKE_EXE_LINKER_FLAGS": "-resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-nocfi",
                "CMAKE_SHARED_LINKER_FLAGS": "-resource-dir=<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-nocfi",

                "LLVM_ENABLE_RUNTIMES": "compiler-rt;flang-rt",

                "LLVM_DEFAULT_TARGET_TRIPLE": "x86_64-unknown-linux-musl",
                "LLVM_ENABLE_PER_TARGET_RUNTIME_DIR": "YES",

                # This install prefix is already the compiler resource directory.
                "FLANG_RT_INSTALL_RESOURCE_PATH:STRING": ".",
                "FLANG_RT_INCLUDE_TESTS": "NO",
                "FLANG_RT_LIBCXX_PROVIDER": "system",

                "COMPILER_RT_USE_LLVM_UNWINDER": "YES",
                "COMPILER_RT_USE_BUILTINS_LIBRARY": "YES",
                "COMPILER_RT_CXX_LIBRARY": "libcxx",

                "COMPILER_RT_BUILD_BUILTINS": "YES",
                "COMPILER_RT_BUILD_CRT": "YES",
                "COMPILER_RT_BUILD_STANDALONE_LIBATOMIC": "YES",

                "COMPILER_RT_BUILD_XRAY": "NO",
                "COMPILER_RT_BUILD_LIBFUZZER": "NO",
                "COMPILER_RT_BUILD_PROFILE": "NO",
                "COMPILER_RT_BUILD_MEMPROF": "NO",
                "COMPILER_RT_BUILD_ORC": "NO",
                "COMPILER_RT_BUILD_SANITIZERS": "NO",
                "COMPILER_RT_BUILD_CTX_PROFILE": "NO",
            },
            "clangFormat" : False,
            "buildDependencies" : [ "build-sandbox-llvm:CACHED_SANDBOX_LLVM", "BOOTSTRAP_MUSL_SYSROOT_CXX", "BOOTSTRAP_RESOURCE_DIR" ],
        },

        "sandboxed-musl-swcfi": {
            "class" : "MuslNativeProject",
            "prefix" : "sysroot/x86_64-unknown-linux-musl_swcfi",
            "buildDependencies" : [
                "build-sandbox-llvm:CACHED_SANDBOX_LLVM",
                "BOOTSTRAP_RESOURCE_DIR",
                "MUSL_BOOTSTRAP_PATH",
            ],
            "buildEnv" : {
                "CC": "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang",
                "LD": "<path:CACHED_SANDBOX_LLVM>/usr/bin/ld.lld",
                "AR": "<path:CACHED_SANDBOX_LLVM>/usr/bin/llvm-ar",
                "RANLIB": "<path:CACHED_SANDBOX_LLVM>/usr/bin/llvm-ranlib",
                "LIBCC": "<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-swcfi/lib/linux/libclang_rt.builtins-x86_64.a",
                "PATH": "<path:MUSL_BOOTSTRAP_PATH>",
            },
            "configureArgs": [
                "CFLAGS=--target=x86_64-unknown-linux-musl_swcfi",
            ],
        },

        "sandboxed-musl-hwcfi": {
            "class" : "MuslNativeProject",
            "prefix" : "sysroot/x86_64-unknown-linux-musl_hwcfi",
            "buildDependencies" : [
                "build-sandbox-llvm:CACHED_SANDBOX_LLVM",
                "BOOTSTRAP_RESOURCE_DIR",
                "MUSL_BOOTSTRAP_PATH",
            ],
            "buildEnv" : {
                "CC": "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang",
                "LD": "<path:CACHED_SANDBOX_LLVM>/usr/bin/ld.lld",
                "AR": "<path:CACHED_SANDBOX_LLVM>/usr/bin/llvm-ar",
                "RANLIB": "<path:CACHED_SANDBOX_LLVM>/usr/bin/llvm-ranlib",
                "LIBCC": "<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-hwcfi/lib/linux/libclang_rt.builtins-x86_64.a",
                "PATH": "<path:MUSL_BOOTSTRAP_PATH>",
            },
            "configureArgs": [
                "CFLAGS=--target=x86_64-unknown-linux-musl_hwcfi",
            ],
        },

        "sandboxed-musl-nocfi": {
            "class" : "MuslNativeProject",
            "prefix" : "sysroot/x86_64-unknown-linux-musl",
            "buildDependencies" : [
                "build-sandbox-llvm:CACHED_SANDBOX_LLVM",
                "BOOTSTRAP_RESOURCE_DIR",
                "MUSL_BOOTSTRAP_PATH",
            ],
            "buildEnv" : {
                "CC": "<path:CACHED_SANDBOX_LLVM>/usr/bin/clang",
                "LD": "<path:CACHED_SANDBOX_LLVM>/usr/bin/ld.lld",
                "AR": "<path:CACHED_SANDBOX_LLVM>/usr/bin/llvm-ar",
                "RANLIB": "<path:CACHED_SANDBOX_LLVM>/usr/bin/llvm-ranlib",
                "LIBCC": "<path:BOOTSTRAP_RESOURCE_DIR>/bootstrap-nocfi/lib/linux/libclang_rt.builtins-x86_64.a",
                "PATH": "<path:MUSL_BOOTSTRAP_PATH>",
            },
            "configureArgs": [
                "CFLAGS=--target=x86_64-unknown-linux-musl -DOUT_OF_SANDBOX",
            ],
        },

        # libraries
        "sandboxed-zlib-swcfi": {
            "class" : "CMakeNinjaProject",
            "vpath" : True,
            "subDir" : "src",
            "sourceDir" : "<path:sandbox-toolchains:ZLIB>/zlib-1.3.2",
            "symlinkSource" : True,
            "results" : [
                "zconf.h",
                "libz.a",
            ],
            "ninja_targets": ["zlibstatic"],
            "cmakeConfig" : {
                "CMAKE_BUILD_TYPE": "Release",
                "CMAKE_C_COMPILER" : "<path:SANDBOXED_MUSL_TOOLCHAIN_STAGE1>/toolchains/musl-swcfi/bin/clang",
                "CMAKE_C_FLAGS" : "-fPIC -Wno-deprecated-non-prototype -Wl,--undefined-version",
                "CMAKE_SYSROOT" : "<path:SANDBOXED_MUSL_TOOLCHAIN_STAGE1>/toolchains/sysroot/x86_64-unknown-linux-musl_swcfi",
                "CMAKE_C_LINK_FLAGS" : "-Wl,--undefined-version",
                "CMAKE_POLICY_VERSION_MINIMUM": "3.5",
            },
            "clangFormat" : False,
            "buildDependencies" : [ "SANDBOXED_MUSL_TOOLCHAIN_STAGE1", "ZLIB" ],
        },

        "sandboxed-zlib-hwcfi": {
            "class" : "CMakeNinjaProject",
            "vpath" : True,
            "subDir" : "src",
            "sourceDir" : "<path:sandbox-toolchains:ZLIB>/zlib-1.3.2",
            "symlinkSource" : True,
            "results" : [
                "zconf.h",
                "libz.a",
            ],
            "ninja_targets": ["zlibstatic"],
            "cmakeConfig" : {
                "CMAKE_BUILD_TYPE": "Release",
                "CMAKE_C_COMPILER" : "<path:SANDBOXED_MUSL_TOOLCHAIN_STAGE1>/toolchains/musl-hwcfi/bin/clang",
                "CMAKE_C_FLAGS" : "-fPIC -Wno-deprecated-non-prototype -Wl,--undefined-version",
                "CMAKE_SYSROOT" : "<path:SANDBOXED_MUSL_TOOLCHAIN_STAGE1>/toolchains/sysroot/x86_64-unknown-linux-musl_hwcfi",
                "CMAKE_C_LINK_FLAGS" : "-Wl,--undefined-version",
                "CMAKE_POLICY_VERSION_MINIMUM": "3.5",
            },
            "clangFormat" : False,
            "buildDependencies" : [ "SANDBOXED_MUSL_TOOLCHAIN_STAGE1", "ZLIB" ],
        },

        "sandboxed-zlib-nocfi": {
            "class" : "CMakeNinjaProject",
            "vpath" : True,
            "subDir" : "src",
            "sourceDir" : "<path:sandbox-toolchains:ZLIB>/zlib-1.3.2",
            "symlinkSource" : True,
            "results" : [
                "zconf.h",
                "libz.a",
            ],
            "ninja_targets": ["zlibstatic"],
            "cmakeConfig" : {
                "CMAKE_BUILD_TYPE": "Release",
                "CMAKE_C_COMPILER" : "<path:SANDBOXED_MUSL_TOOLCHAIN_STAGE1>/toolchains/musl-nocfi/bin/clang",
                "CMAKE_C_FLAGS" : "-fPIC -Wno-deprecated-non-prototype -Wl,--undefined-version",
                "CMAKE_SYSROOT" : "<path:SANDBOXED_MUSL_TOOLCHAIN_STAGE1>/toolchains/sysroot/x86_64-unknown-linux-musl",
                "CMAKE_C_LINK_FLAGS" : "-Wl,--undefined-version",
                "CMAKE_POLICY_VERSION_MINIMUM": "3.5",
            },
            "clangFormat" : False,
            "buildDependencies" : [ "SANDBOXED_MUSL_TOOLCHAIN_STAGE1", "ZLIB" ],
        },

        "sandboxed-mimalloc": {
            "class" : "CMakeNinjaProject",
            "vpath" : True,
            "subDir" : "src",
            "sourceDir" : "<path:sandbox-toolchains:MIMALLOC>/mimalloc-3.4.5",
            "symlinkSource" : True,
            "results" : [
                "libmimalloc.so",
                "libmimalloc.so.3",
                "libmimalloc.so.3.4",
            ],
            "cmakeConfig" : {
                "CMAKE_BUILD_TYPE": "Release",
                "CMAKE_C_COMPILER" : "<path:SANDBOXED_MUSL_TOOLCHAIN_STAGE1>/toolchains/musl-swcfi/bin/clang",
                "MI_LIBC_MUSL": "On",
            },
            "clangFormat" : False,
            "buildDependencies" : [ "SANDBOXED_MUSL_TOOLCHAIN_STAGE1", "MIMALLOC" ],
        },

        # build tools needed for jdk dependencies
        "gperf": {
            "class" : "AutoconfPkgConfigProject",
            "source" : "GPERF",
            "buildEnv" : {
                "CXXFLAGS": "-std=c++11",
                "PATH": "<path:AUTOTOOLS_BOOTSTRAP_PATH>",
            },
            "buildDependencies" : [ "AUTOTOOLS_BOOTSTRAP_PATH" ],
        },

        # jdk dependencies
        "expat": {
            "class" : "AutoconfPkgConfigProject",
            "source" : "EXPAT",
            # a bug in the makefiles makes this always want to rebuild
            # disabling this check should be ok, since we're just downloading the sources anyway
            "checkNeedsBuild": False,
            "buildEnv" : {
                "PATH": "<path:AUTOTOOLS_BOOTSTRAP_PATH>",
            },
            "buildDependencies" : [ "AUTOTOOLS_BOOTSTRAP_PATH" ],
        },
        "fontconfig": {
            "class" : "AutoconfPkgConfigProject",
            "source" : "FONTCONFIG",
            # a bug in the makefiles makes this always want to rebuild
            # disabling this check should be ok, since we're just downloading the sources anyway
            "checkNeedsBuild": False,
            "dependencies" : ["expat"],
            "configureArgs" : ["--disable-cache-build"],
            # build the library only, not the fc-* commands
            "makeArgs" : ["SUBDIRS=fontconfig fc-case fc-lang fc-const fc-genericfamily src", "DIST_SUBDIRS=fontconfig fc-case fc-lang fc-const fc-genericfamily src"],
            "installArgs" : ["SUBDIRS=fontconfig fc-case fc-lang fc-const fc-genericfamily src", "DIST_SUBDIRS=fontconfig fc-case fc-lang fc-const fc-genericfamily src"],
            "buildEnv" : {
                "PATH": "<path:BUILD_TOOLS>/musl-swcfi/bin:<path:AUTOTOOLS_BOOTSTRAP_PATH>",
                "GPERF": "<path:BUILD_TOOLS>/musl-swcfi/bin/gperf",
                # we use freetype bundled in the JDK, not a separate install
                "FREETYPE_CFLAGS": "-I<sandboxed-jdk-source>/src/java.desktop/share/native/libfreetype/include",
                # freetype is only going to be built as part of JDK, which depends on fontconfig - don't attempt to link it now
                "FREETYPE_LIBS": "-L<path:SANDBOXED_LIBFONTCONFIG_BUILD_DEPENDENCIES_BUNDLE>/musl-swcfi/lib -lfreetype -Wl,--allow-shlib-undefined",
            },
            "buildDependencies": [
                "BUILD_TOOLS",
                "SANDBOXED_LIBFONTCONFIG_BUILD_DEPENDENCIES_BUNDLE",
                "AUTOTOOLS_BOOTSTRAP_PATH",
            ],
        },

        # a dummy shared object to use in place of libasound and libX* builds for JDK to link against
        "empty": {
            "subDir": "src",
            "native": "shared_lib",
            "multitarget": {
                "libc": ["musl"],
                "variant": ["swcfi"],
            },
        },

        # static and shared libraries from the JDK
        "sandboxed-jdk": {
            "class" : "JdkAutoconfProject",
            "max_jobs" : "128",
            "configureArgs" : [
                "BUILD_CC=<path:SANDBOXED_MUSL_TOOLCHAIN_STAGE1>/toolchains/musl-swcfi/bin/clang",
                "BUILD_CXX=<path:SANDBOXED_MUSL_TOOLCHAIN_STAGE1>/toolchains/musl-swcfi/bin/clang++",
                "--build=x86_64-pc-linux-gnu",
                "--host=x86_64-pc-linux-musl",
                "--with-toolchain-type=clang",
                "--disable-warnings-as-errors",
                "--with-zlib=bundled",
                "--with-freetype=bundled",
                "--with-alsa=<path:SANDBOXED_JDK_BUILD_DEPENDENCIES_BUNDLE>/musl-swcfi",
                "--with-cups=<path:SANDBOXED_JDK_BUILD_DEPENDENCIES_BUNDLE>/musl-swcfi",
                "--with-fontconfig=<path:SANDBOXED_JDK_BUILD_DEPENDENCIES_BUNDLE>/musl-swcfi",
                "--x-includes=<path:SANDBOXED_JDK_BUILD_DEPENDENCIES_BUNDLE>/musl-swcfi/include",
                "--x-libraries=<path:SANDBOXED_JDK_BUILD_DEPENDENCIES_BUNDLE>/musl-swcfi/lib",
                # musl removed stat64 and friends by default
                # for some reason openjdk defines this only for glibc, not for musl
                "--with-extra-cflags=-D_LARGEFILE64_SOURCE",
                # this warning defaults to fatal in lld
                "--with-extra-ldflags=-Wl,--undefined-version",
            ],
            "buildEnv" : {
                "PATH": "<path:SANDBOXED_MUSL_TOOLCHAIN_STAGE1>/toolchains/musl-swcfi/bin:<path:JDK_BOOTSTRAP_PATH>",
            },
            "buildDependencies": [
                # for manually specified paths
                "SANDBOXED_JDK_BUILD_DEPENDENCIES_BUNDLE",
                "JDK_BOOTSTRAP_PATH",
            ],
        },

        "sandboxed-jdk-static-library-symbols": {
            "class" : "SandboxedJDKStaticLibrarySymbols",
            "buildDependencies": ["SANDBOXED_JDK"],
            "defaultBuild": False,
        },

        # support libraries for native-image
        "com.oracle.graal.sandbox.libc" : {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "substratevm:SVM",
            ],
            "workingSets": "sandbox",
            "javaCompliance": "21+",
        },

        "com.oracle.truffle.llvm.toolchain.launchers.sandboxed": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "requires": [
                "java.logging",
            ],
            "dependencies": [
                "sulong:SULONG_TOOLCHAIN_LAUNCHERS",
            ],
            "checkstyle" : "org.graalvm.word",
            "javaCompliance": "17+",
        },

        "graalvm-musl-swcfi-tool": {
            "class": "NativeImageExecutableProject",
            "dependencies": [
                "SANDBOXED_TOOLCHAIN_LAUNCHERS",
            ],
            "build_args": [
                "--initialize-at-build-time=com.oracle.truffle.llvm.toolchain.launchers",
                "--gc=epsilon",
                "-H:+UnlockExperimentalVMOptions",
                "-H:-ParseRuntimeOptions",
                "-H:-UnlockExperimentalVMOptions",
                # configure LLVM path for toolchain wrapper
                # the toolcahin root is lib/toolchains/musl-swcfi, LLVM is in lib/toolchains/llvm
                "-Dorg.graalvm.llvm.relative.path=../llvm",
                "-Dorg.graalos.targetTriple=x86_64-unknown-linux-musl_swcfi",
                "-Dorg.graalos.rustTargetTriple=x86_64-graalos_swcfi-linux-musl",
                # the main class
                "com.oracle.truffle.llvm.toolchain.launchers.sandboxed.SandboxedTool",
            ],
        },

        "graalvm-musl-swcfi-tool-bash": {
            "subDir": "projects",
            "class": "ToolchainBashLauncherProject",
            "dependencies": [
                "SANDBOXED_TOOLCHAIN_LAUNCHERS",
            ],
            "jvmArgs": [
                # configure LLVM path for toolchain wrapper
                # the toolcahin root is lib/toolchains/musl-swcfi, LLVM is in lib/toolchains/llvm
                "-Dorg.graalvm.llvm.relative.path=../llvm",
                "-Dorg.graalos.targetTriple=x86_64-unknown-linux-musl_swcfi",
                "-Dorg.graalos.rustTargetTriple=x86_64-graalos_swcfi-linux-musl",
                # the main class
                "com.oracle.truffle.llvm.toolchain.launchers.sandboxed.SandboxedTool",
            ],
        },

        "graalvm-musl-hwcfi-tool": {
            "class": "NativeImageExecutableProject",
            "dependencies": [
                "SANDBOXED_TOOLCHAIN_LAUNCHERS",
            ],
            "build_args": [
                "--initialize-at-build-time=com.oracle.truffle.llvm.toolchain.launchers",
                "--gc=epsilon",
                "-H:+UnlockExperimentalVMOptions",
                "-H:-ParseRuntimeOptions",
                "-H:-UnlockExperimentalVMOptions",
                # configure LLVM path for toolchain wrapper
                # the toolcahin root is lib/toolchains/musl-hwcfi, LLVM is in lib/toolchains/llvm
                "-Dorg.graalvm.llvm.relative.path=../llvm",
                "-Dorg.graalos.targetTriple=x86_64-unknown-linux-musl_hwcfi",
                "-Dorg.graalos.rustTargetTriple=x86_64-graalos_hwcfi-linux-musl",
                # the main class
                "com.oracle.truffle.llvm.toolchain.launchers.sandboxed.SandboxedTool",
            ],
        },

        "graalvm-musl-hwcfi-tool-bash": {
            "subDir": "projects",
            "class": "ToolchainBashLauncherProject",
            "dependencies": [
                "SANDBOXED_TOOLCHAIN_LAUNCHERS",
            ],
            "jvmArgs": [
                # configure LLVM path for toolchain wrapper
                # the toolcahin root is lib/toolchains/musl-hwcfi, LLVM is in lib/toolchains/llvm
                "-Dorg.graalvm.llvm.relative.path=../llvm",
                "-Dorg.graalos.targetTriple=x86_64-unknown-linux-musl_hwcfi",
                "-Dorg.graalos.rustTargetTriple=x86_64-graalos_hwcfi-linux-musl",
                # the main class
                "com.oracle.truffle.llvm.toolchain.launchers.sandboxed.SandboxedTool",
            ],
        },

        "graalvm-musl-nocfi-tool": {
            "class": "NativeImageExecutableProject",
            "dependencies": [
                "SANDBOXED_TOOLCHAIN_LAUNCHERS",
            ],
            "build_args": [
                "--initialize-at-build-time=com.oracle.truffle.llvm.toolchain.launchers",
                "--gc=epsilon",
                "-H:+UnlockExperimentalVMOptions",
                "-H:-ParseRuntimeOptions",
                "-H:-UnlockExperimentalVMOptions",
                # configure LLVM path for toolchain wrapper
                # the toolcahin root is lib/toolchains/musl-nocfi, LLVM is in lib/toolchains/llvm
                "-Dorg.graalvm.llvm.relative.path=../llvm",
                "-Dorg.graalos.targetTriple=x86_64-unknown-linux-musl",
                "-Dorg.graalos.rustTargetTriple=x86_64-unknown-linux-musl",
                # the main class
                "com.oracle.truffle.llvm.toolchain.launchers.sandboxed.SandboxedTool",
            ],
        },

        "graalvm-musl-nocfi-tool-bash": {
            "subDir": "projects",
            "class": "ToolchainBashLauncherProject",
            "dependencies": [
                "SANDBOXED_TOOLCHAIN_LAUNCHERS",
            ],
            "jvmArgs": [
                # configure LLVM path for toolchain wrapper
                # the toolcahin root is lib/toolchains/musl-nocfi, LLVM is in lib/toolchains/llvm
                "-Dorg.graalvm.llvm.relative.path=../llvm",
                "-Dorg.graalos.targetTriple=x86_64-unknown-linux-musl",
                "-Dorg.graalos.rustTargetTriple=x86_64-unknown-linux-musl",
                # the main class
                "com.oracle.truffle.llvm.toolchain.launchers.sandboxed.SandboxedTool",
            ],
        },

        "rust": {
            "class": "RustBuilderProject",
            "source": "RUST_SOURCE",
            "toolchain": "SANDBOXED_MUSL_TOOLCHAIN",
            "hostToolchain": "sdk:LLVM_TOOLCHAIN",
            "bootstrapPath": "RUST_BOOTSTRAP_PATH",
            "results": ["install"],
        },
    },

    "distributions" : {
        # restricted PATH containing only selected generic tools
        # to prevent accidentally pulling in compilers from the host
        "MUSL_BOOTSTRAP_PATH": {
            "class": "BootstrapPathDistribution",
            "tools": ["sed", "tr", "cat", "ln", "cp", "mv", "rm", "mkdir", "sh", "chmod"],
        },
        "AUTOTOOLS_BOOTSTRAP_PATH": {
            "class": "BootstrapPathDistribution",
            "tools": ["sed", "expr", "chmod", "rm", "ls", "grep", "sort", "cat", "mkdir", "tr", "awk",
                      "cp", "mv", "bash", "make", "date", "dirname", "basename", "rmdir", "pkg-config",
                      "sh", "touch", "uname", "rpm", "pkgconf", "find", "head", "id", "uniq", "xargs"],
        },
        "JDK_BOOTSTRAP_PATH": {
            "class": "BootstrapPathDistribution",
            "tools": ["sed", "expr", "chmod", "rm", "ls", "grep", "sort", "cat", "mkdir", "tr", "awk",
                      "cp", "mv", "bash", "make", "date", "dirname", "basename", "rmdir", "pkg-config",
                      "sh", "touch", "autoconf", "automake", "head", "mktemp", "tee", "file", "uname",
                      "wc", "cut", "diff", "find", "gunzip", "gzip", "ln", "tail", "tar", "xargs",
                      "unzip", "zip", "whoami", "true", "git"],
        },
        "RUST_BOOTSTRAP_PATH": {
            "class": "BootstrapPathDistribution",
            "tools": ["basename", "cat", "chmod", "cp", "cut", "dirname", "env", "git", "grep", "make",
                      "mkdir", "mv", "perl", "pwd", "rm", "sed", "sh", "strip", "touch", "tr", "uname"],
        },

        "BOOTSTRAP_MUSL_SYSROOT": {
            "native": True,
            "platformDependent": True,
            "layout": {
                "./": [
                    "dependency:sandboxed-musl-swcfi/*",
                    "dependency:sandboxed-musl-hwcfi/*",
                    "dependency:sandboxed-musl-nocfi/*",
                ],
                "./sysroot/x86_64-unknown-linux-musl_swcfi/": "extracted-dependency:KERNEL_HEADERS/usr/*",
                "./sysroot/x86_64-unknown-linux-musl_hwcfi/": "extracted-dependency:KERNEL_HEADERS/usr/*",
                "./sysroot/x86_64-unknown-linux-musl/": "extracted-dependency:KERNEL_HEADERS/usr/*",
            },
        },
        "BOOTSTRAP_MUSL_SYSROOT_CXX": {
            "native": True,
            "platformDependent": True,
            "layout": {
                "./": [
                    "extracted-dependency:BOOTSTRAP_MUSL_SYSROOT/*",
                    "dependency:llvm-cxx-runtimes-musl-swcfi/*",
                    "dependency:llvm-cxx-runtimes-musl-hwcfi/*",
                    "dependency:llvm-cxx-runtimes-musl-nocfi/*",
                ],
                "./sysroot/x86_64-unknown-linux-musl_swcfi/usr/include": "link:../include",
                "./sysroot/x86_64-unknown-linux-musl_hwcfi/usr/include": "link:../include",
                "./sysroot/x86_64-unknown-linux-musl/usr/include": "link:../include",
            },
        },
        "SANDBOXED_MUSL_TOOLCHAIN_STAGE1": {
            "type": "dir",
            "native": True,
            "platformDependent": True,
            "layout": {
                "./toolchains/": [
                    "dependency:llvm-cxx-runtimes-musl-swcfi/*",
                    "dependency:llvm-runtimes-musl-swcfi/*",
                    {
                      "source_type": "dependency",
                      "dependency": "sandboxed-musl-swcfi",
                      "dereference": "never",
                      "path": "*",
                    },
                    "dependency:llvm-cxx-runtimes-musl-hwcfi/*",
                    "dependency:llvm-runtimes-musl-hwcfi/*",
                    {
                      "source_type": "dependency",
                      "dependency": "sandboxed-musl-hwcfi",
                      "dereference": "never",
                      "path": "*",
                    },
                    "dependency:llvm-cxx-runtimes-musl-nocfi/*",
                    "dependency:llvm-runtimes-musl-nocfi/*",
                    {
                      "source_type": "dependency",
                      "dependency": "sandboxed-musl-nocfi",
                      "dereference": "never",
                      "path": "*",
                    },
                ],
                "./toolchains/musl-swcfi/bin/": {
                    "source_type": "dependency",
                    "dependency": "SANDBOXED_MUSL_TOOLS_SWCFI_BASH",
                    "path": "*",
                    "dereference": "never",
                },
                "./toolchains/musl-hwcfi/bin/": {
                    "source_type": "dependency",
                    "dependency": "SANDBOXED_MUSL_TOOLS_HWCFI_BASH",
                    "path": "*",
                    "dereference": "never",
                },
                "./toolchains/musl-nocfi/bin/": {
                    "source_type": "dependency",
                    "dependency": "SANDBOXED_MUSL_TOOLS_NOCFI_BASH",
                    "path": "*",
                    "dereference": "never",
                },
                "./toolchains/musl-swcfi/include": "link:../sysroot/x86_64-unknown-linux-musl_swcfi/include",
                "./toolchains/musl-swcfi/lib": "link:../sysroot/x86_64-unknown-linux-musl_swcfi/lib",
                "./toolchains/musl-swcfi/share": "link:../sysroot/x86_64-unknown-linux-musl_swcfi/share",
                "./toolchains/musl-hwcfi/include": "link:../sysroot/x86_64-unknown-linux-musl_hwcfi/include",
                "./toolchains/musl-hwcfi/lib": "link:../sysroot/x86_64-unknown-linux-musl_hwcfi/lib",
                "./toolchains/musl-hwcfi/share": "link:../sysroot/x86_64-unknown-linux-musl_hwcfi/share",
                "./toolchains/musl-nocfi/include": "link:../sysroot/x86_64-unknown-linux-musl/include",
                "./toolchains/musl-nocfi/lib": "link:../sysroot/x86_64-unknown-linux-musl/lib",
                "./toolchains/musl-nocfi/share": "link:../sysroot/x86_64-unknown-linux-musl/share",
                "./toolchains/sysroot/x86_64-unknown-linux-musl_swcfi/": "extracted-dependency:KERNEL_HEADERS/usr/*",
                "./toolchains/sysroot/x86_64-unknown-linux-musl_swcfi/lib/ld-musl-x86_64.so.1": "link:libc.so",
                "./toolchains/sysroot/x86_64-unknown-linux-musl_hwcfi/": "extracted-dependency:KERNEL_HEADERS/usr/*",
                "./toolchains/sysroot/x86_64-unknown-linux-musl_hwcfi/lib/ld-musl-x86_64.so.1": "link:libc.so",
                "./toolchains/sysroot/x86_64-unknown-linux-musl/": "extracted-dependency:KERNEL_HEADERS/usr/*",
                "./toolchains/sysroot/x86_64-unknown-linux-musl/lib/ld-musl-x86_64.so.1": "link:libc.so",
                # Flang searches for OpenMP modules relative to its installation, not
                # through --sysroot. The no-CFI modules are ABI-common to all variants.
                "./toolchains/llvm/include/flang/OpenMP": "link:../../../sysroot/x86_64-unknown-linux-musl/include/flang/OpenMP",
                "./toolchains/llvm/": {
                    "source_type": "extracted-dependency",
                    "dependency": "build-sandbox-llvm:CACHED_SANDBOX_LLVM",
                    "dereference": "never",
                    "path": "usr/*",
                    "exclude": [
                        "usr/lib/*.a",
                    ],
                },
                # Install directly into the sysroot. toolchains/musl-*/lib is a symlink there.
                "./toolchains/sysroot/x86_64-unknown-linux-musl_swcfi/lib/cmake/": [
                    "file:src/scripts/cmake/toolchain.cmake",
                    "file:src/scripts/cmake/variants/musl-swcfi/GraalOSToolchainVariant.cmake",
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl_hwcfi/lib/cmake/": [
                    "file:src/scripts/cmake/toolchain.cmake",
                    "file:src/scripts/cmake/variants/musl-hwcfi/GraalOSToolchainVariant.cmake",
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl/lib/cmake/": [
                    "file:src/scripts/cmake/toolchain.cmake",
                    "file:src/scripts/cmake/variants/musl-nocfi/GraalOSToolchainVariant.cmake",
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl_swcfi/lib/cmake/Modules/": [
                    "file:src/scripts/cmake/Modules/GraalOSToolchainPaths.cmake",
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl_hwcfi/lib/cmake/Modules/": [
                    "file:src/scripts/cmake/Modules/GraalOSToolchainPaths.cmake",
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl/lib/cmake/Modules/": [
                    "file:src/scripts/cmake/Modules/GraalOSToolchainPaths.cmake",
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl_swcfi/lib/cmake/Modules/Platform/": [
                    "file:src/scripts/cmake/Modules/Platform/Generic-Clang-C-x86_64-GraalOS.cmake",
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl_hwcfi/lib/cmake/Modules/Platform/": [
                    "file:src/scripts/cmake/Modules/Platform/Generic-Clang-C-x86_64-GraalOS.cmake",
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl/lib/cmake/Modules/Platform/": [
                    "file:src/scripts/cmake/Modules/Platform/Generic-Clang-C-x86_64-GraalOS.cmake",
                ],

                # some tools insist on <sysroot>/usr/... instead of just <sysroot>/...
                # we don't symlink ./usr -> ., to prevent searches from discovering infinite paths
                # (e.g. ./toolchains/sysroot/x86_64-unknown-linux-musl_swcfi/usr/usr/usr/usr/.../usr/include)
                "./toolchains/sysroot/x86_64-unknown-linux-musl_swcfi/usr/include": "link:../include",
                "./toolchains/sysroot/x86_64-unknown-linux-musl_swcfi/usr/lib": "link:../lib",
                "./toolchains/sysroot/x86_64-unknown-linux-musl_swcfi/usr/share": "link:../share",
                "./toolchains/sysroot/x86_64-unknown-linux-musl_hwcfi/usr/include": "link:../include",
                "./toolchains/sysroot/x86_64-unknown-linux-musl_hwcfi/usr/lib": "link:../lib",
                "./toolchains/sysroot/x86_64-unknown-linux-musl_hwcfi/usr/share": "link:../share",
                "./toolchains/sysroot/x86_64-unknown-linux-musl/usr/include": "link:../include",
                "./toolchains/sysroot/x86_64-unknown-linux-musl/usr/lib": "link:../lib",
                "./toolchains/sysroot/x86_64-unknown-linux-musl/usr/share": "link:../share",
            },
        },
        "BUILD_TOOLS": {
            "native": True,
            "platformDependent": True,
            "layout": {
                "./": [
                    "dependency:gperf/*",
                ],
            },
        },
        "SANDBOXED_JDK_DEPENDENCIES_BUNDLE": {
            "native": True,
            "platformDependent": True,
            "layout": {
                "./": [
                    "dependency:expat/*",
                    {
                        "source_type": "dependency",
                        "dependency": "fontconfig",
                        "path": "*",
                        # /etc contains absolute links, but we don't need that anyway
                        "exclude": "musl-swcfi/etc/*",
                    },
                ],
            },
            "defaultBuild": False,
        },
        "SANDBOXED_JDK_BUILD_DEPENDENCIES_BUNDLE": {
            "native": True,
            "platformDependent": True,
            "layout": {
                "./": "extracted-dependency:SANDBOXED_JDK_DEPENDENCIES_BUNDLE/*",
                "./musl-swcfi/include/alsa/": [
                    "extracted-dependency:ALSA_LIB/*/include/*.h",
                ],
                "./musl-swcfi/include/cups/": [
                    "extracted-dependency:CUPS/*/cups/*.h",
                ],
                "./musl-swcfi/include/X11/": [
                    "extracted-dependency:XORGPROTO/*/include/X11/*.h",
                    "extracted-dependency:LIBX11/*/include/X11/*.h",
                    "extracted-dependency:LIBXT/*/include/X11/*.h",
                ],
                "./musl-swcfi/include/X11/extensions/": [
                    "extracted-dependency:XORGPROTO/*/include/X11/extensions/*.h",
                    "extracted-dependency:LIBXEXT/*/include/X11/extensions/*.h",
                    "extracted-dependency:LIBXRENDER/*/include/X11/extensions/*.h",
                    "extracted-dependency:LIBXI/*/include/X11/extensions/*.h",
                    "extracted-dependency:LIBXTST/*/include/X11/extensions/*.h",
                    "extracted-dependency:LIBXRANDR/*/include/X11/extensions/*.h",
                ],
                "./musl-swcfi/lib/libasound.so": "dependency:empty/linux-amd64/musl-swcfi/libempty.so",
                "./musl-swcfi/lib/libXext.so": "dependency:empty/linux-amd64/musl-swcfi/libempty.so",
                "./musl-swcfi/lib/libX11.so": "dependency:empty/linux-amd64/musl-swcfi/libempty.so",
                "./musl-swcfi/lib/libXrender.so": "dependency:empty/linux-amd64/musl-swcfi/libempty.so",
                "./musl-swcfi/lib/libXtst.so": "dependency:empty/linux-amd64/musl-swcfi/libempty.so",
                "./musl-swcfi/lib/libXi.so": "dependency:empty/linux-amd64/musl-swcfi/libempty.so",
            },
            "defaultBuild": False,
        },
        "SANDBOXED_LIBFONTCONFIG_BUILD_DEPENDENCIES_BUNDLE": {
            "native": True,
            "platformDependent": True,
            "layout": {
                "./musl-swcfi/lib/libfreetype.so": "dependency:empty/linux-amd64/musl-swcfi/libempty.so",
            },
            "defaultBuild": False,
        },
        "SANDBOXED_JDK": {
            "native": True,
            "platformDependent": True,
            "layout": {
                "./": "dependency:sandboxed-jdk/*",
            },
            "defaultBuild": False,
        },
        "SANDBOXED_MUSL_TOOLS_SWCFI": {
            "class": "ToolchainToolDistribution",
            "tool_project": "graalvm-musl-swcfi-tool",
            "tool_links": {
                "graalvm-musl-swcfi-clang": ["graalvm-clang", "clang", "gcc", "cc", "x86_64-linux-musl-gcc"],
                "graalvm-musl-swcfi-clang++": ["graalvm-clang++", "clang++", "g++", "c++", "x86_64-linux-musl-g++"],
                "graalvm-musl-swcfi-flang": ["graalvm-flang", "flang", "flang-new"],
                "graalvm-musl-swcfi-ld": ["lld", "lld-link", "ld.lld", "ld", "ld64"],
                "llvm-ar": ["ar"],
                "llvm-cxxfilt": ["c++filt"],
                "llvm-nm": ["nm"],
                "llvm-objcopy": ["objcopy"],
                "llvm-objdump": ["objdump"],
                "llvm-ranlib": ["ranlib"],
                "llvm-readelf": ["readelf"],
                "llvm-readobj": ["readobj"],
                "llvm-strip": ["strip"],
            },
            "defaultBuild": False,
        },
        "SANDBOXED_MUSL_TOOLS_SWCFI_BASH": {
            "class": "ToolchainToolDistribution",
            "tool_project": "graalvm-musl-swcfi-tool-bash",
            "tool_links": {
                "graalvm-musl-swcfi-clang": ["graalvm-clang", "clang", "gcc", "cc", "x86_64-linux-musl-gcc"],
                "graalvm-musl-swcfi-clang++": ["graalvm-clang++", "clang++", "g++", "c++", "x86_64-linux-musl-g++"],
                "graalvm-musl-swcfi-flang": ["graalvm-flang", "flang", "flang-new"],
                "graalvm-musl-swcfi-ld": ["lld", "lld-link", "ld.lld", "ld", "ld64"],
                "llvm-ar": ["ar"],
                "llvm-cxxfilt": ["c++filt"],
                "llvm-nm": ["nm"],
                "llvm-objcopy": ["objcopy"],
                "llvm-objdump": ["objdump"],
                "llvm-ranlib": ["ranlib"],
                "llvm-readelf": ["readelf"],
                "llvm-readobj": ["readobj"],
                "llvm-strip": ["strip"],
            },
            "defaultBuild": False,
        },
        "SANDBOXED_MUSL_TOOLS_HWCFI": {
            "class": "ToolchainToolDistribution",
            "tool_project": "graalvm-musl-hwcfi-tool",
            "tool_links": {
                "graalvm-musl-hwcfi-clang": ["graalvm-clang", "clang", "gcc", "cc", "x86_64-linux-musl-gcc"],
                "graalvm-musl-hwcfi-clang++": ["graalvm-clang++", "clang++", "g++", "c++", "x86_64-linux-musl-g++"],
                "graalvm-musl-hwcfi-flang": ["graalvm-flang", "flang", "flang-new"],
                "graalvm-musl-hwcfi-ld": ["lld", "lld-link", "ld.lld", "ld", "ld64"],
                "llvm-ar": ["ar"],
                "llvm-cxxfilt": ["c++filt"],
                "llvm-nm": ["nm"],
                "llvm-objcopy": ["objcopy"],
                "llvm-objdump": ["objdump"],
                "llvm-ranlib": ["ranlib"],
                "llvm-readelf": ["readelf"],
                "llvm-readobj": ["readobj"],
                "llvm-strip": ["strip"],
            },
            "defaultBuild": False,
        },
        "SANDBOXED_MUSL_TOOLS_HWCFI_BASH": {
            "class": "ToolchainToolDistribution",
            "tool_project": "graalvm-musl-hwcfi-tool-bash",
            "tool_links": {
                "graalvm-musl-hwcfi-clang": ["graalvm-clang", "clang", "gcc", "cc", "x86_64-linux-musl-gcc"],
                "graalvm-musl-hwcfi-clang++": ["graalvm-clang++", "clang++", "g++", "c++", "x86_64-linux-musl-g++"],
                "graalvm-musl-hwcfi-flang": ["graalvm-flang", "flang", "flang-new"],
                "graalvm-musl-hwcfi-ld": ["lld", "lld-link", "ld.lld", "ld", "ld64"],
                "llvm-ar": ["ar"],
                "llvm-cxxfilt": ["c++filt"],
                "llvm-nm": ["nm"],
                "llvm-objcopy": ["objcopy"],
                "llvm-objdump": ["objdump"],
                "llvm-ranlib": ["ranlib"],
                "llvm-readelf": ["readelf"],
                "llvm-readobj": ["readobj"],
                "llvm-strip": ["strip"],
            },
            "defaultBuild": False,
        },
        "SANDBOXED_MUSL_TOOLS_NOCFI": {
            "class": "ToolchainToolDistribution",
            "tool_project": "graalvm-musl-nocfi-tool",
            "tool_links": {
                "graalvm-musl-nocfi-clang": ["graalvm-clang", "clang", "gcc", "cc", "x86_64-linux-musl-gcc"],
                "graalvm-musl-nocfi-clang++": ["graalvm-clang++", "clang++", "g++", "c++", "x86_64-linux-musl-g++"],
                "graalvm-musl-nocfi-flang": ["graalvm-flang", "flang", "flang-new"],
                "graalvm-musl-nocfi-ld": ["lld", "lld-link", "ld.lld", "ld", "ld64"],
                "llvm-ar": ["ar"],
                "llvm-cxxfilt": ["c++filt"],
                "llvm-nm": ["nm"],
                "llvm-objcopy": ["objcopy"],
                "llvm-objdump": ["objdump"],
                "llvm-ranlib": ["ranlib"],
                "llvm-readelf": ["readelf"],
                "llvm-readobj": ["readobj"],
                "llvm-strip": ["strip"],
            },
            "defaultBuild": False,
        },
        "SANDBOXED_MUSL_TOOLS_NOCFI_BASH": {
            "class": "ToolchainToolDistribution",
            "tool_project": "graalvm-musl-nocfi-tool-bash",
            "tool_links": {
                "graalvm-musl-nocfi-clang": ["graalvm-clang", "clang", "gcc", "cc", "x86_64-linux-musl-gcc"],
                "graalvm-musl-nocfi-clang++": ["graalvm-clang++", "clang++", "g++", "c++", "x86_64-linux-musl-g++"],
                "graalvm-musl-nocfi-flang": ["graalvm-flang", "flang", "flang-new"],
                "graalvm-musl-nocfi-ld": ["lld", "lld-link", "ld.lld", "ld", "ld64"],
                "llvm-ar": ["ar"],
                "llvm-cxxfilt": ["c++filt"],
                "llvm-nm": ["nm"],
                "llvm-objcopy": ["objcopy"],
                "llvm-objdump": ["objdump"],
                "llvm-ranlib": ["ranlib"],
                "llvm-readelf": ["readelf"],
                "llvm-readobj": ["readobj"],
                "llvm-strip": ["strip"],
            },
            "defaultBuild": False,
        },
        "SANDBOXED_RUST_TOOLS_SWCFI": {
            "class": "ToolchainToolDistribution",
            "tool_project": "graalvm-musl-swcfi-tool",
            "tool_links": {
                "graalvm-musl-swcfi-rustc": ["rustc"],
                "graalvm-musl-swcfi-cargo": ["cargo"],
            },
            "defaultBuild": False,
        },
        "SANDBOXED_RUST_TOOLS_HWCFI": {
            "class": "ToolchainToolDistribution",
            "tool_project": "graalvm-musl-hwcfi-tool",
            "tool_links": {
                "graalvm-musl-hwcfi-rustc": ["rustc"],
                "graalvm-musl-hwcfi-cargo": ["cargo"],
            },
            "defaultBuild": False,
        },
        "SANDBOXED_MUSL_TOOLCHAIN_BASH_LAUNCHER": {
            "native": True,
            "platformDependent": True,
            "layout": {
                "./": [
                    {
                      "source_type": "dependency",
                      "dependency": "SANDBOXED_MUSL_TOOLCHAIN_STAGE1",
                      "dereference": "never",
                      "path": "*",
                    }
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl_swcfi/include/": [
                    "file:<path:sandbox-toolchains:ZLIB>/zlib-1.3.2/zlib.h",
                    "dependency:sandboxed-zlib-swcfi/zconf.h",
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl_hwcfi/include/": [
                    "file:<path:sandbox-toolchains:ZLIB>/zlib-1.3.2/zlib.h",
                    "dependency:sandboxed-zlib-hwcfi/zconf.h",
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl/include/": [
                    "file:<path:sandbox-toolchains:ZLIB>/zlib-1.3.2/zlib.h",
                    "dependency:sandboxed-zlib-nocfi/zconf.h",
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl_swcfi/lib/": [
                    "dependency:sandboxed-zlib-swcfi/libz.*",
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl_hwcfi/lib/": [
                    "dependency:sandboxed-zlib-hwcfi/libz.*",
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl/lib/": [
                    "dependency:sandboxed-zlib-nocfi/libz.*",
                ],
                "./toolchains/musl-swcfi/bin/ldd": "link:../../sysroot/x86_64-unknown-linux-musl_swcfi/lib/ld-musl-x86_64.so.1",
            },
        },
        "BOOTSTRAP_RESOURCE_DIR": {
            "native": True,
            "platformDependent": True,
            "type": "dir",
            "layout": {
                "./": [
                    "dependency:bootstrap-compiler-rt-swcfi/*",
                    "dependency:bootstrap-compiler-rt-hwcfi/*",
                    "dependency:bootstrap-compiler-rt-nocfi/*",
                    "extracted-dependency:build-sandbox-llvm:CACHED_SANDBOX_LLVM/usr/lib/clang/22/include",
                ],

                "./bootstrap-swcfi/include": "link:../include",
                "./bootstrap-hwcfi/include": "link:../include",
                "./bootstrap-nocfi/include": "link:../include",
            },
        },
        "SANDBOXED_MUSL_TOOLCHAIN": {
            "native": True,
            "platformDependent": True,
            "layout": {
                "./": [
                    {
                      "source_type": "dependency",
                      "dependency": "SANDBOXED_MUSL_TOOLCHAIN_STAGE1",
                      "dereference": "never",
                      "path": "*",
                      # exclude the stage1 bash wrappers from the final toolchain
                      "exclude": ["toolchains/musl-swcfi/bin", "toolchains/musl-hwcfi/bin", "toolchains/musl-nocfi/bin"],
                    }
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl_swcfi/include/": [
                    "file:<path:sandbox-toolchains:ZLIB>/zlib-1.3.2/zlib.h",
                    "dependency:sandboxed-zlib-swcfi/zconf.h",
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl_hwcfi/include/": [
                    "file:<path:sandbox-toolchains:ZLIB>/zlib-1.3.2/zlib.h",
                    "dependency:sandboxed-zlib-hwcfi/zconf.h",
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl/include/": [
                    "file:<path:sandbox-toolchains:ZLIB>/zlib-1.3.2/zlib.h",
                    "dependency:sandboxed-zlib-nocfi/zconf.h",
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl_swcfi/lib/": [
                    "dependency:sandboxed-zlib-swcfi/libz.*",
                    {
                      "source_type": "dependency",
                      "dependency": "sandboxed-mimalloc",
                      "dereference": "never",
                      "path": "*",
                    },
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl_hwcfi/lib/": [
                    "dependency:sandboxed-zlib-hwcfi/libz.*",
                ],
                "./toolchains/sysroot/x86_64-unknown-linux-musl/lib/": [
                    "dependency:sandboxed-zlib-nocfi/libz.*",
                ],
                "./toolchains/musl-swcfi/bin/": {
                    "source_type": "dependency",
                    "dependency": "SANDBOXED_MUSL_TOOLS_SWCFI",
                    "path": "*",
                    "dereference": "never",
                },
                "./toolchains/musl-hwcfi/bin/": {
                    "source_type": "dependency",
                    "dependency": "SANDBOXED_MUSL_TOOLS_HWCFI",
                    "path": "*",
                    "dereference": "never",
                },
                "./toolchains/musl-nocfi/bin/": {
                    "source_type": "dependency",
                    "dependency": "SANDBOXED_MUSL_TOOLS_NOCFI",
                    "path": "*",
                    "dereference": "never",
                },
                "./toolchains/musl-swcfi/bin/ldd": "link:../../sysroot/x86_64-unknown-linux-musl_swcfi/lib/ld-musl-x86_64.so.1",
                "./toolchains/musl-hwcfi/bin/ldd": "link:../../sysroot/x86_64-unknown-linux-musl_hwcfi/lib/ld-musl-x86_64.so.1",
                "./toolchains/musl-nocfi/bin/ldd": "link:../../sysroot/x86_64-unknown-linux-musl/lib/ld-musl-x86_64.so.1",
            },
        },
        "SANDBOXED_RUST_TOOLCHAIN": {
            "native": True,
            "platformDependent": True,
            "layout": {
                "./llvm/": [
                    "dependency:rust/*",
                ],
                "./musl-swcfi/bin/": {
                    "source_type": "dependency",
                    "dependency": "SANDBOXED_RUST_TOOLS_SWCFI",
                    "path": "*",
                    "exclude": [
                        # duplicated from SANDBOXED_MUSL_TOOLS_SWCFI
                        "graalvm-musl-swcfi-tool",
                    ],
                    "dereference": "never",
                },
                "./musl-hwcfi/bin/": {
                    "source_type": "dependency",
                    "dependency": "SANDBOXED_RUST_TOOLS_HWCFI",
                    "path": "*",
                    "exclude": [
                        # duplicated from SANDBOXED_MUSL_TOOLS_HWCFI
                        "graalvm-musl-hwcfi-tool",
                    ],
                    "dereference": "never",
                },
            },
        },
        "SANDBOXED_RUST_TOOLCHAIN_INSTALLED": {
            "type": "dir",
            "native": True,
            "platformDependent": True,
            "layout": {
                "./": [
                    "extracted-dependency:SANDBOXED_RUST_TOOLCHAIN/*",
                    "extracted-dependency:SANDBOXED_MUSL_TOOLCHAIN/toolchains/*",
                ],
            },
        },
        "GRAALOS_SUPPORT": {
            "native": True,
            "platformDependent": True,
            "layout": {
                "./": [
                    "file:src/scripts/GRAALOS-README.md",
                ],
                "./lib/graalos/": [
                    "file:src/scripts/build-env.sh",
                ],
            },
        },
        "SANDBOXED_TOOLCHAIN_LAUNCHERS": {
            "subDir" : "projects",
            "dependencies" : ["com.oracle.truffle.llvm.toolchain.launchers.sandboxed"],
            "distDependencies" : ["sdk:LAUNCHER_COMMON"],
            "license" : "BSD-new",
            "maven" : False,
        },
        "SANDBOXED_LIBRARIES_SUPPORT": {
            "native": True,
            "platformDependent": True,
            "layout": {
                "./": "dependency:sandboxed-jdk-static-library-symbols/*",
                "./static/<os>-<arch>/musl-swcfi/": "extracted-dependency:SANDBOXED_JDK/images/static-libs/lib/*.a",
                "./musl-swcfi/": "extracted-dependency:SANDBOXED_JDK/jdk/lib/*.so",
                "./musl-swcfi/libfreetype.so.6": "link:libfreetype.so",
            },
            "defaultBuild": False,
        },
        "SANDBOXED_JDK_DEPS_SUPPORT": {
            "native": True,
            "platformDependent": True,
            "defaultDereference": "never",
            "layout": {
                "./musl-swcfi/": [
                    {
                        "source_type": "extracted-dependency",
                        "dependency": "SANDBOXED_JDK_DEPENDENCIES_BUNDLE",
                        "path": "musl-swcfi/lib/*",
                        "exclude": [
                            "musl-swcfi/lib/*.a",
                            "musl-swcfi/lib/*.la",
                        ],
                    },
                ],
            },
            "defaultBuild": False,
        },

        "SANDBOXED_NINJA_TOOLCHAIN": {
            "native" : True,
            "platformDependent" : True,
            "native_toolchain": {
                "kind": "ninja",
                "compiler": "sandbox-llvm",
                "target": {
                    "libc": "musl",
                    "variant": "swcfi",
                },
            },
            "layout" : {
                "toolchain.ninja" : {
                    "source_type": "string",
                    "value": '''
include <ninja-toolchain:GCC_NINJA_TOOLCHAIN>
CC=<path:SANDBOXED_MUSL_TOOLCHAIN_STAGE1>/toolchains/musl-swcfi/bin/clang
CXX=<path:SANDBOXED_MUSL_TOOLCHAIN_STAGE1>/toolchains/musl-swcfi/bin/clang++
AR=<path:SANDBOXED_MUSL_TOOLCHAIN_STAGE1>/toolchains/llvm/bin/llvm-ar
'''
                },
            },
            "dependencies": [
                "SANDBOXED_MUSL_TOOLCHAIN_STAGE1",
                "mx:GCC_NINJA_TOOLCHAIN",
            ],
            "maven" : False,
        },

        # support libraries for native-image
        "SVM_SANDBOXED": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.graal.sandbox.libc",
            ],
            "distDependencies": [
                "substratevm:SVM",
            ],
            "moduleInfo": {
                "name": "com.oracle.graal.sandbox",
            },
            "maven": False,
        },
    }
}
