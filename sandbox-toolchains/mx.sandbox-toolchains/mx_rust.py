#
# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#

import fnmatch
import glob
import json
import os
import shutil
import sys

import mx
import mx_util


_RUST_TARGET_SWCFI = 'x86_64-graalos_swcfi-linux-musl'
_RUST_TARGET_HWCFI = 'x86_64-graalos_hwcfi-linux-musl'
_LLVM_TARGET_SWCFI = 'x86_64-unknown-linux-musl_swcfi'
_LLVM_TARGET_HWCFI = 'x86_64-unknown-linux-musl_hwcfi'


def _detect_llvm_cxx11_abi(llvm_toolchain_path):
    lib_llvm = None
    llvm_config = os.path.join(llvm_toolchain_path, 'bin', 'llvm-config')
    if os.access(llvm_config, os.X_OK):
        libfiles = mx.OutputCapture()
        with open(os.devnull, 'w', encoding='utf-8') as devnull:
            ret = mx.run([llvm_config, '--libfiles'], nonZeroIsFatal=False, out=libfiles, err=devnull)
        if ret == 0:
            for line in libfiles.data.splitlines():
                candidate = line.strip()
                if fnmatch.fnmatch(os.path.basename(candidate), 'libLLVM*.so*'):
                    lib_llvm = os.path.realpath(candidate)
                    break

    if lib_llvm is None:
        candidates = glob.glob(os.path.join(llvm_toolchain_path, 'lib', 'libLLVM*.so*'))
        for candidate in sorted(candidates):
            if os.path.exists(candidate):
                lib_llvm = os.path.realpath(candidate)
                break

    if lib_llvm is None:
        mx.warn('Could not detect libLLVM for CXX11 ABI detection.')
        return None

    nm = shutil.which('nm')
    symbol_lines = []
    if nm is not None:
        def inspect_symbol(line):
            if 'llvm::sys::getDefaultTargetTriple' in line:
                symbol_lines.append(line)

        with open(os.devnull, 'w', encoding='utf-8') as devnull:
            mx.run([nm, '-C', lib_llvm], nonZeroIsFatal=False, out=inspect_symbol, err=devnull)

    if not symbol_lines:
        mx.warn('Could not detect the usage of CXX11 ABI.')
        return None
    return 1 if any('abi:cxx11' in line for line in symbol_lines) else 0


class ToolchainRoot:
    def __init__(self, root=None, dist=None):
        self._root = root
        self._dist = dist

    def get_toolchain_root(self):
        return self._root

    def get_toolchain_dist(self):
        return self._dist

    def resolve(self):
        pass

class BootstrapToolchainRoot(ToolchainRoot):
    def __init__(self, path):
        super().__init__(root=path)

class DistributionToolchainRoot(ToolchainRoot):
    def __init__(self, toolchain):
        super().__init__(dist=toolchain)

    def resolve(self):
        resolved = mx.distribution(self._dist)
        self._root = os.path.join(resolved.output, 'toolchains')


def _get_bootstrap_toolchain(dist):
    bootstrap_graalvm = mx.get_env('BOOTSTRAP_GRAALVM')
    if bootstrap_graalvm is not None:
        toolchain_candidate = os.path.join(bootstrap_graalvm, 'lib', 'toolchains')
        llvm_config = os.path.join(toolchain_candidate, 'llvm', 'bin', 'llvm-config')
        if os.path.exists(llvm_config):
            return BootstrapToolchainRoot(toolchain_candidate)
        else:
            mx.warn(f"BOOSTRAP_GRAALVM specified, but no LLVM toolcahin in there! Falling back to build from source.")
    return DistributionToolchainRoot(dist)


class RustBuilderProject(mx.AbstractNativeProject):
    def __init__(self, suite, name, deps, workingSets, output=None, **kwArgs):
        self._source_name = kwArgs.pop('source')
        self._bootstrap_path = kwArgs.pop('bootstrapPath', 'RUST_BOOTSTRAP_PATH')
        self.results = kwArgs.pop('results', ['install'])
        self.output = output
        self.vpath = False
        self._toolchain = _get_bootstrap_toolchain(kwArgs.pop('toolchain'))
        self._host_toolchain = kwArgs.pop('hostToolchain', 'LLVM_TOOLCHAIN')
        buildDependencies = kwArgs.pop('buildDependencies', [])
        for dependency in [self._source_name, self._bootstrap_path, self._toolchain.get_toolchain_dist(), self._host_toolchain]:
            if dependency and dependency not in buildDependencies:
                buildDependencies.append(dependency)
        super(RustBuilderProject, self).__init__(suite, name, subDir='src', srcDirs=['patches'], deps=deps, workingSets=workingSets,
                                                 d=os.path.join(suite.dir, 'src', 'rust'), buildDependencies=buildDependencies,
                                                 defaultBuild=False, **kwArgs)
        self.out_dir = self.get_output_root()

    def resolveDeps(self):
        super().resolveDeps()
        self._toolchain.resolve()
        self._host_toolchain = mx.distribution(self._host_toolchain)
        self._source = mx.library(self._source_name)

    def getBuildTask(self, args):
        return RustBuilderBuildTask(args, self)

    def patch_dir(self):
        return self.source_dirs()[0]

    def patches(self):
        patch_dir = self.patch_dir()
        return [os.path.join(patch_dir, patch) for patch in sorted(os.listdir(patch_dir)) if patch.endswith('.patch') and os.path.isfile(os.path.join(patch_dir, patch))]

    def getArchivableResults(self, use_relpath=True, single=False):
        if single:
            raise ValueError('single not supported')
        out_dir_arch = os.path.join(self.out_dir, mx.get_arch())
        install_dir = os.path.join(out_dir_arch, 'install')
        for subdir in ['bin', 'etc', 'lib', 'share']:
            yield os.path.join(install_dir, subdir), subdir


class RustBuilderBuildTask(mx.AbstractNativeBuildTask):
    def __init__(self, args, project):
        super().__init__(args, project)
        self.target_arch = mx.get_arch()
        self.out_dir = os.path.join(self.subject.out_dir, self.target_arch)
        self._install_dir = os.path.join(self.out_dir, 'install')
        self._work_dir = os.path.join(self.out_dir, 'work')
        self._bootstrap_toml = os.path.join(self._work_dir, 'bootstrap.toml')
        self._cargo_dir = os.path.join(self._work_dir, '.cargo')
        self._cargo_config = os.path.join(self._cargo_dir, 'config.toml')
        self._target_specs_dir = os.path.join(self._work_dir, 'target-specs')

    def __str__(self):
        return f'Building {self.subject.name} for target_arch {self.target_arch}'

    def newestOutput(self):
        return mx.TimeStampFile.newest([self._install_dir])

    def needsBuild(self, newestInput):
        is_needed, reason = super(RustBuilderBuildTask, self).needsBuild(newestInput)
        if is_needed:
            return True, reason

        required_outputs = [
            os.path.join(self._install_dir, 'bin', 'cargo'),
            os.path.join(self._install_dir, 'bin', 'rustc'),
            os.path.join(self._install_dir, 'lib', 'rustlib', _RUST_TARGET_SWCFI, 'lib'),
            os.path.join(self._install_dir, 'lib', 'rustlib', _RUST_TARGET_HWCFI, 'lib'),
        ]
        missing_outputs = [path for path in required_outputs if not os.path.exists(path)]
        if missing_outputs:
            return True, 'missing required Rust outputs: {}'.format(', '.join(missing_outputs))

        output = self.newestOutput()
        if not output.exists():
            return True, f'{self._install_dir} does not exist'

        patch_dir = self.subject.patch_dir()
        newest_patch = mx.TimeStampFile.newest([patch_dir] + self.subject.patches())
        if newest_patch and output.isOlderThan(newest_patch):
            return True, '{} is older than {}'.format(output, newest_patch)

        return False, 'all files are up to date'

    def _toolchain_root(self):
        return self.subject._toolchain.get_toolchain_root()

    def _tool_path(self, relative_path):
        root = self._toolchain_root()
        candidate = os.path.join(root, relative_path)
        if os.path.exists(candidate):
            return candidate
        else:
            mx.abort(f'Missing required Rust build input: {relative_path}. Checked {candidate}.')

    def _host_tool_path(self, *parts):
        candidate = os.path.join(self.subject._host_toolchain.output, *parts)
        if os.path.exists(candidate):
            return candidate
        return None

    def _bootstrap_path_root(self):
        output = mx.distribution(self.subject._bootstrap_path).output
        if output is None:
            mx.abort(f'Missing output path for distribution {self.subject._bootstrap_path}')
        return output

    def _cargo_home(self):
        return os.path.join(self._work_dir, 'cargo-home')

    def _rustup_home(self):
        return os.path.join(self._work_dir, 'rustup-home')

    def _get_extracted_source_dir(self):
        extracted_dirs = [entry for entry in os.listdir(self._work_dir) if os.path.isdir(os.path.join(self._work_dir, entry)) and entry not in {'.cargo', 'cargo-home', 'rustup-home'}]
        if len(extracted_dirs) != 1:
            mx.abort(f"Expected exactly one extracted source directory in {self._work_dir}, but got {extracted_dirs}.")
        return os.path.join(self._work_dir, extracted_dirs[0])

    def _host_tool(self, *names):
        for name in names:
            tool = self._host_tool_path('bin', name)
            if os.path.exists(tool):
                return tool
        return None

    def _preflight_inputs(self):
        required = {
            'musl nocfi clang': self._tool_path('musl-nocfi/bin/graalvm-musl-nocfi-clang'),
            'musl nocfi clang++': self._tool_path('musl-nocfi/bin/graalvm-musl-nocfi-clang++'),
            'musl nocfi llvm-ar': self._tool_path('musl-nocfi/bin/llvm-ar'),
            'musl nocfi llvm-ranlib': self._tool_path('musl-nocfi/bin/llvm-ranlib'),
            'musl swcfi clang': self._tool_path('musl-swcfi/bin/graalvm-musl-swcfi-clang'),
            'musl swcfi clang++': self._tool_path('musl-swcfi/bin/graalvm-musl-swcfi-clang++'),
            'musl swcfi llvm-ar': self._tool_path('musl-swcfi/bin/llvm-ar'),
            'musl swcfi llvm-ranlib': self._tool_path('musl-swcfi/bin/llvm-ranlib'),
            'musl hwcfi clang': self._tool_path('musl-hwcfi/bin/graalvm-musl-hwcfi-clang'),
            'musl hwcfi clang++': self._tool_path('musl-hwcfi/bin/graalvm-musl-hwcfi-clang++'),
            'musl hwcfi llvm-ar': self._tool_path('musl-hwcfi/bin/llvm-ar'),
            'musl hwcfi llvm-ranlib': self._tool_path('musl-hwcfi/bin/llvm-ranlib'),
            'llvm-config': self._tool_path('llvm/bin/llvm-config'),
            'host cc': self._host_tool('clang', 'cc'),
            'host c++': self._host_tool('clang++', 'c++'),
            'host ar': self._host_tool('llvm-ar', 'ar'),
            'host ranlib': self._host_tool('llvm-ranlib', 'ranlib'),
            'python': sys.executable,
            'sh': shutil.which('sh'),
            'env': shutil.which('env'),
        }
        missing = [name for name, path in required.items() if not path or not os.path.exists(path)]
        if missing:
            details = ', '.join(f'{name}={required[name]}' for name in missing)
            mx.abort(f'Missing required Rust build inputs: {details}')

    def _bootstrap_targets(self):
        return [
            'x86_64-unknown-linux-gnu',
            'x86_64-unknown-linux-musl',
            _RUST_TARGET_SWCFI,
            _RUST_TARGET_HWCFI,
        ]

    def _bootstrap_toml_contents(self):
        install_prefix = os.path.realpath(self._install_dir)
        musl_nocfi_root = self._tool_path('sysroot/x86_64-unknown-linux-musl')
        musl_swcfi_root = self._tool_path('sysroot/x86_64-unknown-linux-musl_swcfi')
        musl_hwcfi_root = self._tool_path('sysroot/x86_64-unknown-linux-musl_hwcfi')
        llvm_config = self._tool_path('llvm/bin/llvm-config')
        host_cc = self._host_tool('clang', 'cc')
        host_cxx = self._host_tool('clang++', 'c++')
        host_ar = self._host_tool('llvm-ar', 'ar')
        host_ranlib = self._host_tool('llvm-ranlib', 'ranlib')
        if not all([host_cc, host_cxx, host_ar, host_ranlib]):
            mx.abort('Missing required host compiler/binutils for Rust host bootstrap')
        musl_nocfi_cc = self._tool_path('musl-nocfi/bin/graalvm-musl-nocfi-clang')
        musl_nocfi_cxx = self._tool_path('musl-nocfi/bin/graalvm-musl-nocfi-clang++')
        musl_nocfi_ar = self._tool_path('musl-nocfi/bin/llvm-ar')
        musl_nocfi_ranlib = self._tool_path('musl-nocfi/bin/llvm-ranlib')
        musl_swcfi_cc = self._tool_path('musl-swcfi/bin/graalvm-musl-swcfi-clang')
        musl_swcfi_cxx = self._tool_path('musl-swcfi/bin/graalvm-musl-swcfi-clang++')
        musl_swcfi_ar = self._tool_path('musl-swcfi/bin/llvm-ar')
        musl_swcfi_ranlib = self._tool_path('musl-swcfi/bin/llvm-ranlib')
        musl_hwcfi_cc = self._tool_path('musl-hwcfi/bin/graalvm-musl-hwcfi-clang')
        musl_hwcfi_cxx = self._tool_path('musl-hwcfi/bin/graalvm-musl-hwcfi-clang++')
        musl_hwcfi_ar = self._tool_path('musl-hwcfi/bin/llvm-ar')
        musl_hwcfi_ranlib = self._tool_path('musl-hwcfi/bin/llvm-ranlib')
        targets = ', '.join(f'"{target}"' for target in self._bootstrap_targets())
        return f'''profile = "dist"
change-id = "ignore"

[llvm]
download-ci-llvm = false
link-shared = true

[build]
build = "x86_64-unknown-linux-gnu"
verbose = 0
docs = false
compiler-docs = false
submodules = true
locked-deps = true
vendor = true
extended = true
cargo-native-static = true
patch-binaries-for-nix = false
tools = ["cargo"]
host = ["x86_64-unknown-linux-gnu"]
target = [{targets}]

[install]
prefix = "{install_prefix}"
sysconfdir = "etc"

[rust]
debug = false
codegen-tests = false
download-rustc = false
musl-root = "{musl_nocfi_root}"
rpath = true
lld = false
bootstrap-override-lld = "external"
channel = "nightly"
remap-debuginfo = true

[target.x86_64-unknown-linux-gnu]
llvm-config = "{llvm_config}"
cc = "{host_cc}"
linker = "{host_cxx}"
cxx = "{host_cxx}"
ar = "{host_ar}"
ranlib = "{host_ranlib}"

[target.x86_64-unknown-linux-musl]
crt-static = false
musl-root = "{musl_nocfi_root}"
llvm-config = "{llvm_config}"
cc = "{musl_nocfi_cc}"
linker = "{musl_nocfi_cxx}"
cxx = "{musl_nocfi_cxx}"
ar = "{musl_nocfi_ar}"
ranlib = "{musl_nocfi_ranlib}"

[target.{_RUST_TARGET_SWCFI}]
crt-static = false
musl-root = "{musl_swcfi_root}"
llvm-config = "{llvm_config}"
cc = "{musl_swcfi_cc}"
linker = "{musl_swcfi_cxx}"
cxx = "{musl_swcfi_cxx}"
ar = "{musl_swcfi_ar}"
ranlib = "{musl_swcfi_ranlib}"

[target.{_RUST_TARGET_HWCFI}]
crt-static = false
musl-root = "{musl_hwcfi_root}"
llvm-config = "{llvm_config}"
cc = "{musl_hwcfi_cc}"
linker = "{musl_hwcfi_cxx}"
cxx = "{musl_hwcfi_cxx}"
ar = "{musl_hwcfi_ar}"
ranlib = "{musl_hwcfi_ranlib}"
'''

    def _cargo_config_contents(self, source_dir):
        vendor_dir = os.path.join(source_dir, 'vendor')
        return f'''[source.crates-io]
replace-with = "vendored-sources"

[source.vendored-sources]
directory = "{vendor_dir}"
'''

    @staticmethod
    def _custom_target_spec(llvm_target, description):
        return {
            'arch': 'x86_64',
            'cpu': 'x86-64',
            'crt-objects-fallback': 'musl',
            'crt-static-default': True,
            'crt-static-respected': True,
            'data-layout': 'e-m:e-p270:32:32-p271:32:32-p272:64:64-i64:64-i128:128-f80:128-n8:16:32:64-S128',
            'default-uwtable': True,
            'dynamic-linking': True,
            'env': 'musl',
            'has-rpath': True,
            'has-thread-local': True,
            'linker-flavor': 'gnu-cc',
            'llvm-target': llvm_target,
            'max-atomic-width': 64,
            'metadata': {
                'description': description,
                'host_tools': True,
                'std': True,
                'tier': 2,
            },
            'os': 'linux',
            'plt-by-default': False,
            'position-independent-executables': True,
            'post-link-objects-fallback': {
                'dynamic-dylib': ['crtendS.o', 'crtn.o'],
                'dynamic-nopic-exe': ['crtend.o', 'crtn.o'],
                'dynamic-pic-exe': ['crtendS.o', 'crtn.o'],
                'static-dylib': ['crtendS.o', 'crtn.o'],
                'static-nopic-exe': ['crtend.o', 'crtn.o'],
                'static-pic-exe': ['crtendS.o', 'crtn.o'],
            },
            'pre-link-args': {
                'gnu-cc': ['-m64'],
                'gnu-lld-cc': ['-m64'],
            },
            'pre-link-objects-fallback': {
                'dynamic-dylib': ['crti.o', 'crtbeginS.o'],
                'dynamic-nopic-exe': ['crt1.o', 'crti.o', 'crtbegin.o'],
                'dynamic-pic-exe': ['Scrt1.o', 'crti.o', 'crtbeginS.o'],
                'static-dylib': ['crti.o', 'crtbeginS.o'],
                'static-nopic-exe': ['crt1.o', 'crti.o', 'crtbegin.o'],
                'static-pic-exe': ['rcrt1.o', 'crti.o', 'crtbeginS.o'],
            },
            'relro-level': 'full',
            'stack-probes': {
                'kind': 'inline',
            },
            'static-position-independent-executables': True,
            'supported-sanitizers': ['address', 'leak', 'memory', 'thread', 'cfi'],
            'supported-split-debuginfo': ['packed', 'unpacked', 'off'],
            'supports-xray': True,
            'target-family': ['unix'],
            'target-pointer-width': 64,
        }

    def _write_custom_target_specs(self):
        mx_util.ensure_dir_exists(self._target_specs_dir)
        # These target specs are only for the stage0 bootstrap compiler. The final rustc built
        # from our patched sources has the GraalOS SWCFI and HWCFI targets built in, but the
        # stage0 snapshot does not know them yet and bootstrap sanity rejects unknown targets
        # before that patched compiler exists.
        specs = {
            _RUST_TARGET_SWCFI: self._custom_target_spec(
                _LLVM_TARGET_SWCFI,
                '64-bit Linux with musl 1.2.5 and SWCFI',
            ),
            _RUST_TARGET_HWCFI: self._custom_target_spec(
                _LLVM_TARGET_HWCFI,
                '64-bit Linux with musl 1.2.5 and HWCFI',
            ),
        }
        for target, spec in specs.items():
            with open(os.path.join(self._target_specs_dir, f'{target}.json'), 'w', encoding='utf-8') as target_spec:
                json.dump(spec, target_spec, indent=2, sort_keys=True)
                target_spec.write('\n')

    def _build_env(self):
        shell = shutil.which('sh')
        if shell is None:
            mx.abort('Missing required shell executable: sh')

        pkg_config = shutil.which('pkg-config')
        llvm_toolchain_path = self._tool_path('llvm')
        llvm_lib_dir = self._tool_path('llvm/lib')
        cxx11_abi = _detect_llvm_cxx11_abi(llvm_toolchain_path)

        env = {
            'HOME': self._work_dir,
            'PATH': self._bootstrap_path_root(),
            'CARGO_HOME': self._cargo_home(),
            'RUSTUP_HOME': self._rustup_home(),
            'CARGOFLAGS': '--offline',
            'RUSTUP_DIST_SERVER': 'file:///nonexistent-offline-rust-dist',
            'PYTHON': sys.executable,
            'SHELL': shell,
            'LANG': 'C',
            'LC_ALL': 'C',
            'TZ': 'UTC',
            # Bootstrap runs stage1 rustc before the final toolchain is installed, and that
            # binary needs libLLVM.so from the sandbox LLVM toolchain at runtime.
            'LD_LIBRARY_PATH': llvm_lib_dir,
            'IN_NIX_SHELL': '0',
            # The restricted bootstrap PATH intentionally excludes GNU ld. Tell
            # clang's linker driver to use the colocated ld.lld explicitly.
            'RUSTFLAGS_BOOTSTRAP': '-Clink-arg=-fuse-ld=lld',
            'RUSTBUILD_FORCE_CLANG_BASED_TESTS': '1',
            'RUST_TARGET_PATH': self._target_specs_dir,
        }
        if cxx11_abi is not None:
            # Rust bootstrap merges this target-specific value into the C++ flags
            # used to compile rustc_llvm's wrapper.
            env['CXXFLAGS_x86_64_unknown_linux_gnu'] = f'-D_GLIBCXX_USE_CXX11_ABI={cxx11_abi}'
        # cc-rs derives --target from Cargo's public Rust target. The GraalOS Clang
        # launchers and sysroots use the corresponding internal LLVM target, so
        # append the internal target after cc-rs' automatically generated flag.
        for rust_target, llvm_target in [
            (_RUST_TARGET_SWCFI, _LLVM_TARGET_SWCFI),
            (_RUST_TARGET_HWCFI, _LLVM_TARGET_HWCFI),
        ]:
            target_env = rust_target.replace('-', '_').replace('.', '_')
            env[f'CFLAGS_{target_env}'] = f'--target={llvm_target}'
            env[f'CXXFLAGS_{target_env}'] = f'--target={llvm_target}'
        if pkg_config:
            env['PKG_CONFIG'] = pkg_config
            env['PKG_CONFIG_PATH'] = os.environ.get('PKG_CONFIG_PATH', '')
        return env

    def _host_openssl_available(self):
        pkg_config = shutil.which('pkg-config')
        if pkg_config is None:
            return False
        with open(os.devnull, 'w', encoding='utf-8') as devnull:
            return mx.run([pkg_config, '--exists', 'openssl'], nonZeroIsFatal=False, out=devnull, err=devnull) == 0

    def _run_with_log(self, cmd, cwd, env):
        log = lambda msg: mx.log(msg, important=False)
        ret = mx.run(cmd, nonZeroIsFatal=False, cwd=cwd, env=env, out=log, err=log)
        if ret != 0:
            mx.abort(f'Rust build command failed: {cmd}.')

    def _copy_tree(self, source_dir, destination_dir):
        if not os.path.exists(source_dir):
            return False
        mx_util.ensure_dir_exists(destination_dir)
        for entry in os.listdir(source_dir):
            source = os.path.join(source_dir, entry)
            destination = os.path.join(destination_dir, entry)
            if os.path.isdir(source):
                shutil.copytree(source, destination, dirs_exist_ok=True)
            else:
                shutil.copy2(source, destination)
        return True

    @staticmethod
    def _git_apply_args(source_dir):
        return ['git', 'apply', '--whitespace=nowarn', '--unsafe-paths', '--directory', source_dir]

    def _git_apply_check(self, source_dir, patch, reverse=False):
        with open(os.devnull, 'w', encoding='utf-8') as devnull:
            return mx.run(
                self._git_apply_args(source_dir) + (['--reverse'] if reverse else []) + ['--check', patch],
                cwd=self.subject.suite.vc_dir,
                nonZeroIsFatal=False,
                out=devnull,
                err=devnull,
            ) == 0

    def _apply_patch(self, source_dir, patch):
        patch_name = os.path.basename(patch)
        if self._git_apply_check(source_dir, patch):
            mx.log(f'Applying patch {patch_name}')
            mx.run(self._git_apply_args(source_dir) + [patch], cwd=self.subject.suite.vc_dir)
            return
        if self._git_apply_check(source_dir, patch, reverse=True):
            mx.log(f'Skipping already-applied patch {patch_name}')
            return
        mx.abort(
            f'Cannot classify patch state for {patch_name} in extracted source directory {source_dir}. '
            f'The reused tree has diverged from the expected patched/unpatched state; run mx clean or '
            f'remove the Rust output/work directory and retry.'
        )

    def build(self):
        if not self._host_openssl_available():
            mx.abort('Host OpenSSL is unavailable via pkg-config; This is a mandatory dependency of rust/cargo')

        mx.log('Extracting {}...'.format(self.subject._source))
        os.makedirs(self._work_dir, exist_ok=True)
        os.makedirs(self._install_dir, exist_ok=True)
        os.makedirs(self._cargo_home(), exist_ok=True)
        os.makedirs(self._rustup_home(), exist_ok=True)

        extracted_source_dir = None
        if os.path.isdir(self._work_dir):
            extracted_dirs = [entry for entry in os.listdir(self._work_dir) if os.path.isdir(os.path.join(self._work_dir, entry)) and entry not in {'.cargo', 'cargo-home', 'rustup-home'}]
            if len(extracted_dirs) == 1:
                extracted_source_dir = os.path.join(self._work_dir, extracted_dirs[0])
            elif extracted_dirs:
                mx.abort(f"Expected at most one extracted source directory in {self._work_dir}, but got {extracted_dirs}.")

        self._preflight_inputs()
        if extracted_source_dir is None:
            mx.Extractor.create(self.subject._source.get_path(False)).extract(self._work_dir)
            extracted_source_dir = self._get_extracted_source_dir()

        mx.log('Applying patches...')
        source_dir = os.path.realpath(extracted_source_dir)
        for patch in self.subject.patches():
            self._apply_patch(source_dir, patch)

        mx_util.ensure_dir_exists(self._cargo_dir)
        self._write_custom_target_specs()
        with open(self._bootstrap_toml, 'w', encoding='utf-8') as bootstrap_toml:
            bootstrap_toml.write(self._bootstrap_toml_contents())
        with open(self._cargo_config, 'w', encoding='utf-8') as cargo_config:
            cargo_config.write(self._cargo_config_contents(source_dir))

        env = self._build_env()
        x_py = [sys.executable, 'x.py', '--config', self._bootstrap_toml, '-v']
        mx.log('Building Rust toolchain...')
        self._run_with_log(x_py + ['build', 'rustc', 'library/std', 'cargo'], cwd=source_dir, env=env)
        mx.log('Installing Rust toolchain...')
        self._run_with_log(x_py + ['install', 'rustc', 'library/std', 'cargo'], cwd=source_dir, env=env)

    def clean(self, forBuild=False):
        mx.rmtree(self.out_dir, ignore_errors=True)
