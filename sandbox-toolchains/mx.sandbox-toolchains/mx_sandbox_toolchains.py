#
# Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import mx
import mx_subst
import mx_sdk_vm
import mx_util
import os.path
import sys

import hashlib
import json
import shlex
import shutil

# re-export custom mx project classes, so they can be used from suite.py

from mx_cmake import CMakeNinjaProject #pylint: disable=unused-import
from mx_rust import RustBuilderProject #pylint: disable=unused-import
from mx_sdk_vm_ng import NativeImageExecutableProject, ToolchainToolDistribution  # pylint: disable=unused-import
from mx_substratevm import StaticLibrarySymbolsBuilder  # pylint: disable=unused-import
_suite = mx.suite('sandbox-toolchains')

def suite_version(arg):
    suite = mx.suite(arg)
    return suite.version()

mx_subst.path_substitutions.register_with_arg('suite-version', suite_version)


class BootstrapPathDistribution(mx.LayoutDirDistribution):
    def __init__(self, suite, name=None, deps=None, excludedLibs=None, platformDependent=True, theLicense=None, defaultBuild=True, **kw_args):
        self.tools = kw_args.pop('tools')

        sh = shutil.which('bash')
        layout = {
            f"./{tool}": {
                "source_type": "string",
                "value": f"#!{sh}\nexec -a \"$0\" \"{shutil.which(tool)}\" \"$@\""
            }
            for tool in self.tools if shutil.which(tool) is not None
        }
        super().__init__(suite, name=name, deps=[], layout=layout, path=None, theLicense=theLicense, platformDependent=True, defaultBuild=defaultBuild)

    def make_archive(self):
        super().make_archive()
        output = self.get_output()
        for tool in self.tools:
            p = os.path.join(output, tool)
            if os.path.exists(p):
                os.chmod(p, 0o755)

class MuslNativeProject(mx.NativeProject):
    def __init__(self, suite, name, deps, workingSets, output=None, **kwArgs):
        self._configure_args = kwArgs.pop('configureArgs', [])
        self._make_args = kwArgs.pop('makeArgs', [])
        self._install_args = kwArgs.pop('installArgs', [])
        self._prefix = kwArgs.pop('prefix', 'usr/local')
        results = kwArgs.pop('results', [self._prefix])
        musl = mx.suite('musl')
        super().__init__(suite, name, subDir="musl", srcDirs=[musl.dir], deps=deps, workingSets=workingSets,
                                                results=results, output=output, d=musl.dir, vpath=True, **kwArgs)

    def getBuildTask(self, args):
        return MuslNativeBuildTask(args, self)

    def getOutput(self, **kwArgs):
        return os.path.join(self.get_output_root(), '_install')

    def getPrefix(self):
        return f"/{self._prefix}"

class AutoconfPkgConfigProject(mx.NativeProject):
    def __init__(self, suite, name, deps, workingSets, output=None, **kwArgs):
        self._configure_args = kwArgs.pop('configureArgs', [])
        self._make_args = kwArgs.pop('makeArgs', [])
        self._install_args = kwArgs.pop('installArgs', [])
        self._toolchain = kwArgs.pop('toolchain', 'SANDBOXED_MUSL_TOOLCHAIN_STAGE1')
        self._source = kwArgs.pop('source')
        self._in_tree_build = kwArgs.pop('inTreeBuild', False)
        self._check_needsbuild = kwArgs.pop('checkNeedsBuild', True)
        buildDependencies = kwArgs.pop('buildDependencies', [])
        buildDependencies += [self._toolchain]
        if self._source is not None:
            buildDependencies += [self._source]
        results = kwArgs.pop('results', ["musl-swcfi"])
        if 'd' not in kwArgs:
            kwArgs['d'] = _suite.dir
        super().__init__(suite, name, subDir="libs", srcDirs=[], deps=deps, workingSets=workingSets,
                                                       results=results, output=output, vpath=False,
                                                       buildDependencies=buildDependencies, defaultBuild=False, **kwArgs)

    def getSourcePath(self):
        base = mx.library(self._source).get_path(resolve=True)
        entries = os.listdir(base)
        if len(entries) != 1:
            mx.abort(f"Expected exactly one directory in {self._source}, but got {entries}.")
        return os.path.join(base, entries[0])

    def getBuildTask(self, args):
        return AutoconfPkgConfigBuildTask(args, self)

    def getOutput(self, **kwArgs):
        return os.path.join(self.get_output_root(), '_install')

    def getPrefix(self):
        return os.path.join(self.getOutput(), 'musl-swcfi')

    def getBuildEnv(self):
        return super().getBuildEnv(replaceVar=pkgconfig_subst)

    def getPkgConfigPath(self):
        deps = set()
        def _collect_deps(p):
            for d in p.deps:
                if d not in deps and isinstance(d, AutoconfPkgConfigProject):
                    deps.add(d)
                    _collect_deps(d)
        _collect_deps(self)

        pkgConfigPath = []
        for d in deps:
            for dir_name in ['lib', 'share']:
                path = os.path.join(d.getPrefix(), dir_name, 'pkgconfig')
                if os.path.isdir(path):
                    pkgConfigPath.append(path)
        return os.path.pathsep.join(pkgConfigPath)

    def getPkgConfigLibDir(self):
        # use the current project's output lib directory instead of the system-wide lib directory
        return os.path.join(self.getPrefix(), 'lib')

# parse the `release` file at the root of the JDK to get the correct source revisions
def _parse_jdk_release_file(jdk):
    release_info = {}
    with open(os.path.join(jdk.home, 'release'), encoding='utf-8') as file:
        for line in file:
            line = line.strip()
            if '=' in line:
                key, value = line.split('=', 1)
                release_info[key] = value.strip('"')

    if 'SOURCE' in release_info:
        source_info = {}
        for source in release_info['SOURCE'].split():
            parts = source.split(':')
            # repo:git:revision
            if len(parts) == 3:
                source_info[parts[0]] = parts[2]
        return source_info
    else:
        return None

def _get_jdk_tag(jdk):
    tagCapture = mx.OutputCapture()
    mx.command_function("jvmci-version-check")(['--as-tag'], jdk=jdk, out=tagCapture)
    return tagCapture.data.strip()


class JdkSourceRepository:
    """A source repository to be checked out by a JDK source provider."""

    def __init__(self, name, revision, path, url):
        self.name = name
        self.revision = revision
        self.path = path
        self.url = url


class JdkSourceLayout:
    """The normalized layout of a JDK source checkout."""

    def __init__(self, build_root, java_source_root):
        self.build_root = build_root
        self.java_source_root = java_source_root


_jdk_source_providers = []


def register_jdk_source_provider(provider):
    """Register a provider called as ``provider(jdk, source_info, do_checkout)``.

    Providers return a :class:`JdkSourceLayout` when they recognize the JDK
    source layout, and ``None`` when another provider should handle it.
    """
    _jdk_source_providers.append(provider)


def checkout_jdk_sources(jdk, repositories, do_checkout=False):
    """Apply the common clone, revision-check, and checkout mechanics."""
    git = mx.GitConfig()
    tag = _get_jdk_tag(jdk)

    def _check_rev(repository):
        path = repository.path
        name = repository.name
        rev = repository.revision
        current_rev = git.git_command(path, ['rev-list', '-n1', rev])
        head_rev = git.git_command(path, ['rev-list', '-n1', 'HEAD'])
        if current_rev != head_rev:
            tag_msg = f" (tag: {tag})" if tag else ""
            mx.warn(f"JDK source repository '{name}' not checked out at expected revision {rev}{tag_msg}!")
            return False
        else:
            return True

    def _checkout(repository):
        path = repository.path
        rev = repository.revision
        checked_rev = git.git_command(path, ['rev-list', '-n1', rev])
        if checked_rev:
            print(f"Checking out {rev} in {path}...")
            git.git_command(path, ['checkout', rev], quiet=False)
        else:
            print(f"Revision {rev} not found in {path}. Fetching...")
            git.git_command(path, ['fetch', '--tags'], abortOnError=True, quiet=False)
            git.git_command(path, ['checkout', rev], abortOnError=True, quiet=False)

    def _checkout_or_clone(repository):
        if not os.path.exists(os.path.join(repository.path, '.git')):
            git.clone(repository.url, repository.path, rev=repository.revision)
            return True
        elif do_checkout:
            _checkout(repository)
            return True
        else:
            return _check_rev(repository)

    all_revisions_ok = True
    for repository in repositories:
        if not _checkout_or_clone(repository):
            all_revisions_ok = False
    if not all_revisions_ok:
        mx.warn("Run `mx jdk-checkout` to check out the correct revisions.")


def _get_jdk_source_layout(jdk, do_checkout=False):
    source_info = _parse_jdk_release_file(jdk)
    if not source_info:
        mx.abort("Could not get source revision info for current JDK.")

    if '.' not in source_info:
        mx.abort("Could not get source revision for the JDK repository.")

    for repo in source_info:
        source_info[repo] = source_info[repo].strip('+')

    for provider in _jdk_source_providers:
        layout = provider(jdk, source_info, do_checkout)
        if layout is not None:
            return layout

    if set(source_info) != {'.'}:
        mx.abort(f"The JDK source provider does not support SOURCE entries: {sorted(source_info)}.\nMake sure you are using a labs-openjdk as your JAVA_HOME.")

    source_root = os.path.join(mx.SiblingSuiteModel.siblings_dir(_suite.dir), 'labs-openjdk')
    checkout_jdk_sources(jdk, [JdkSourceRepository(
        'labs-openjdk', source_info['.'], source_root, 'https://github.com/graalvm/labs-openjdk'
    )], do_checkout=do_checkout)
    return JdkSourceLayout(source_root, source_root)


@mx.command(_suite.name, 'jdk-checkout')
def jdk_checkout(args):
    jdk = mx.get_jdk()
    _get_jdk_source_layout(jdk, do_checkout=True)


class JdkAutoconfProject(AutoconfPkgConfigProject):
    def __init__(self, suite, name, deps, workingSets, **kwArgs):
        jdk = mx.get_jdk()
        source_layout = _get_jdk_source_layout(jdk)
        self._source_path = source_layout.build_root
        self._java_source_path = source_layout.java_source_root
        super().__init__(suite, name, deps, workingSets, source=None, results=['images/jdk', 'images/static-libs/lib', 'jdk/lib'], d=self._source_path, **kwArgs)
        self._configure_args.insert(0, f"--with-boot-jdk={jdk.home}")
        self._configure_args.insert(0, f"--with-build-jdk={jdk.home}")
        if mx.get_env('CI') is not None:
            # if we're on the CI, don't be nice
            # everything else running on the same machine is also a build task
            # and this task is most likely the bottleneck
            self._configure_args.insert(0, f"NICE={shutil.which('nice')} -n0")

    # Mark this as JDK-dependent to avoid having to `mx clean` when switching the major JDK version.
    def isJDKDependent(self):
        return True

    def getSourcePath(self):
        return self._source_path

    def getJavaSourcePath(self):
        return self._java_source_path

    def getBuildTask(self, args):
        return JdkBuildTask(args, self)

    def getOutput(self, **kwArgs):
        return os.path.join(self.get_output_root(), '_build')

def sandboxed_jdk_source_path():
    return mx.project('sandboxed-jdk').getJavaSourcePath()

mx_subst.path_substitutions.register_no_arg('sandboxed-jdk-source', sandboxed_jdk_source_path)

class AbstractAutoconfBuildTask(mx.NativeBuildTask):
    def __init__(self, args, project):
        super().__init__(args, project)
        root = self.subject.get_output_root()
        self._buildDir = os.path.join(root, '_build')
        self._configureLog = os.path.join(root, 'configure.log')
        self._failureLogs = os.path.join(root, "_build/make-support/failure-logs")
        self._musl = os.path.join(root, "../SANDBOXED_MUSL_TOOLCHAIN_STAGE1/toolchains/musl-swcfi/bin")
        self._makeLog = os.path.join(root, 'make.log')
        self._installLog = os.path.join(root, 'install.log')

    def getConfigureEnv(self):
        return self.subject.getBuildEnv()

    def getMakeEnv(self):
        return self.subject.getBuildEnv()

    def getInstallEnv(self):
        return self.subject.getBuildEnv()

    def getConfigurePath(self):
        return os.path.join(self.subject.dir, 'configure')

    def getExtraInstallArgs(self):
        return []

    def getMakeCmd(self):
        gmake = mx.gmake_cmd(context=self.subject)
        # use absolute path to gnu make to be able to run with clear PATH
        if '/' in gmake:
            gmake = os.path.realpath(gmake)
        else:
            gmake = shutil.which(gmake)
        makeCmd = [gmake]
        if self.parallelism > 1:
            makeCmd += ['-j', str(self.parallelism)]
        return makeCmd

    def getInstallCmd(self):
        return self.getMakeCmd() + ['install']

    def printFailureLogs(self):
        if os.path.exists(self._failureLogs):
            mx.log("Failure logs (" + self._failureLogs + "):")
            for log in os.listdir(self._failureLogs):
                logPath = os.path.join(self._failureLogs, log)
                if os.path.isfile(logPath):
                    mx.log("failure log file: " + logPath)
            mx.log("musl wrapper: " + os.path.join(self._musl, "musl-clang"))
            mx.log("musl wrapper: " + os.path.join(self._musl, "musl-clang++"))
            mx.log("musl wrapper: " + os.path.join(self._musl, "ld.musl-clang"))
            mx.log("musl wrapper: " + os.path.join(self._musl, "ld.musl-clang++"))
        else:
            mx.log("Failure logs missing (" + self._failureLogs + "):")

    def needsBuild(self, newestInput):
        makeCmd = self.getMakeCmd()
        makeCmd += [mx_subst.path_substitutions.substitute(arg) for arg in self.subject._make_args]
        makeCmd += ['-q']
        with open(os.devnull, 'w', encoding='utf-8') as fnull:
            # suppress out/err (redirect to null device)
            try:
                ret = mx.run(makeCmd, nonZeroIsFatal=False, cwd=self._buildDir, env=self.getMakeEnv(), out=fnull, err=fnull)
            except:
                return (True, "GNU Make failed")

        if ret != 0:
            return (True, "rebuild needed by GNU Make")
        return (False, "up to date according to GNU Make")

    def build(self):
        mx_util.ensure_dir_exists(self._buildDir)

        # configure
        configureScript = self.getConfigurePath()
        configureCmd = [shutil.which('sh'), configureScript, f'--prefix={self.subject.getPrefix()}']
        configureCmd += [mx_subst.path_substitutions.substitute(arg) for arg in self.subject._configure_args]

        with open(self._configureLog, "w", encoding='utf-8') as log:
            ret = mx.run(configureCmd, nonZeroIsFatal=False, cwd=self._buildDir, env=self.getConfigureEnv(), out=log, err=log)
            if ret != 0:
                msg = f"Error running configure for {self.subject}.\nFailing build log: {self._configureLog}"
                configLog = os.path.join(self._buildDir, "config.log")
                if os.path.exists(configLog):
                    msg += f"\nFailing build log: {configLog}"
                mx.abort(msg)

        # build
        makeCmd = self.getMakeCmd()
        makeCmd += [mx_subst.path_substitutions.substitute(arg) for arg in self.subject._make_args]
        with open(self._makeLog, "w", encoding='utf-8') as log:
            ret = mx.run(makeCmd, nonZeroIsFatal=False, cwd=self._buildDir, env=self.getMakeEnv(), out=log, err=log)
            if ret != 0:
                mx.abort(f"Error running make for {self.subject}.\nFailing build log: {self._configureLog}\nFailing build log: {self._makeLog}")

        # install
        installCmd = self.getInstallCmd()
        if installCmd is not None:
            installCmd += [mx_subst.path_substitutions.substitute(arg) for arg in self.subject._install_args]
            with open(self._installLog, "w", encoding='utf-8') as log:
                ret = mx.run(installCmd, nonZeroIsFatal=False, cwd=self._buildDir, env=self.getInstallEnv(), out=log, err=log)
                if ret != 0:
                    mx.abort(f"Error running make install for {self.subject}.\nFailing build log: {self._configureLog}\nFailing build log: {self._makeLog}\nFailing build log: {self._installLog}")

class MuslNativeBuildTask(AbstractAutoconfBuildTask):
    def getInstallCmd(self):
        return self.getMakeCmd() + [f'DESTDIR={self.subject.getOutput()}', 'install']


class AutoconfPkgConfigBuildTask(AbstractAutoconfBuildTask):
    def __init__(self, *args, **kwArgs):
        super().__init__(*args, **kwArgs)
        root = self.subject.get_output_root()
        self._sentinel = mx.TimeStampFile(os.path.join(root, 'build.sentinel'))

    def getConfigureEnv(self):
        env = super().getConfigureEnv()
        toolchainRoot = os.path.join(mx.distribution(self.subject._toolchain).output, 'toolchains')
        toolchainPath = os.path.join(toolchainRoot, 'musl-swcfi', 'bin')
        env['CC'] = os.path.join(toolchainPath, 'clang')
        env['CXX'] = os.path.join(toolchainPath, 'clang++')
        llvmPath = os.path.join(toolchainRoot, 'llvm', 'bin')
        env['AR'] = os.path.join(llvmPath, 'llvm-ar')
        env['PYTHON'] = sys.executable
        env['PKG_CONFIG_LIBDIR'] = self.subject.getPkgConfigLibDir()
        env['PKG_CONFIG_PATH'] = self.subject.getPkgConfigPath()
        return env

    def getMakeEnv(self):
        fullenv = self.subject.getBuildEnv()
        env = {
            'PATH': fullenv['PATH']
        }
        return env

    def getInstallEnv(self):
        fullenv = self.subject.getBuildEnv()
        env = {
            'PATH': fullenv['PATH']
        }
        return env

    def getConfigurePath(self):
        sourcePath = self.subject.getSourcePath()
        if self.subject._in_tree_build:
            create_symlink_tree(src=sourcePath, dst=self._buildDir)
            return os.path.join('.', 'configure')
        else:
            return os.path.join(sourcePath, 'configure')

    def needsBuild(self, newestInput):
        if self.subject._check_needsbuild:
            return super().needsBuild(newestInput)
        # workaround for projects where "make -q" doesn't give an accurate answer
        elif not self._sentinel.exists():
            return (True, "build sentinel does not exist")
        elif self._sentinel.isOlderThan(newestInput):
            return (True, "build sentinel older than newest input")
        else:
            return (False, "build sentinel up to date")

    def build(self):
        super().build()
        self._sentinel.touch()


def _fingerprint_file_tree(root):
    if not os.path.exists(root):
        return None

    entries = []
    for current_root, dirs, files in os.walk(root):
        dirs.sort()
        files.sort()
        rel_root = os.path.relpath(current_root, root)
        entries.append(('dir', rel_root))
        for file_name in files:
            file_path = os.path.join(current_root, file_name)
            stat = os.stat(file_path, follow_symlinks=False)
            rel_path = os.path.relpath(file_path, root)
            entries.append(('file', rel_path, stat.st_size, stat.st_mtime_ns))
    return entries


def _read_json_file(path):
    if not os.path.exists(path):
        return None
    with open(path, encoding='utf-8') as fp:
        return json.load(fp)

class JdkBuildTask(AutoconfPkgConfigBuildTask):
    def __init__(self, *args, **kwArgs):
        super().__init__(*args, **kwArgs)
        self._toolchainFingerprintPath = os.path.join(self.subject.get_output_root(), 'toolchain-fingerprint.json')
        self._pendingToolchainFingerprint = None
        self._pendingToolchainFingerprintHash = None
        self._toolchainChangedReason = None

    def getMakeEnv(self):
        # jdk makefiles use some tools without proper path detection, so we have to give it a PATH
        # clear the rest of the env to ensure a clean build without accidentally pulling in extra deps
        fullenv = self.subject.getBuildEnv()
        return {
            'PATH': fullenv['PATH']
        }

    def getMakeCmd(self):
        makeCmd = [mx.gmake_cmd(context=self.subject)]
        if self.parallelism > 1:
            makeCmd += [f'JOBS={self.parallelism}']
        makeCmd += ['static-libs-image', 'libs', 'images']
        return makeCmd

    def getInstallCmd(self):
        return None

    def _getToolchainFingerprint(self):
        configureEnv = self.getConfigureEnv()
        makeEnv = self.getMakeEnv()
        toolchainOutput = mx.distribution(self.subject._toolchain).output
        muslToolchainRoot = os.path.join(toolchainOutput, 'toolchains', 'musl-swcfi')
        muslSysroot = os.path.join(toolchainOutput, 'toolchains', 'sysroot', 'x86_64-unknown-linux-musl_swcfi')
        llvmToolchainBin = os.path.join(toolchainOutput, 'toolchains', 'llvm', 'bin')
        fingerprint = {
            'toolchainOutput': toolchainOutput,
            'configureArgs': list(self.subject._configure_args),
            'env': {
                'AR': configureEnv['AR'],
                'CC': configureEnv['CC'],
                'CXX': configureEnv['CXX'],
                'PATH': makeEnv['PATH'],
                'PKG_CONFIG_PATH': configureEnv['PKG_CONFIG_PATH'],
                'PYTHON': configureEnv['PYTHON'],
            },
            'trees': {
                'musl-bin': _fingerprint_file_tree(os.path.join(muslToolchainRoot, 'bin')),
                'musl-include': _fingerprint_file_tree(os.path.join(muslSysroot, 'include')),
                'musl-cxx-include': _fingerprint_file_tree(os.path.join(muslSysroot, 'usr', 'include', 'c++', 'v1')),
                'llvm-bin': _fingerprint_file_tree(llvmToolchainBin),
            },
        }
        fingerprint_json = json.dumps(fingerprint, sort_keys=True, separators=(',', ':'))
        return fingerprint, hashlib.sha256(fingerprint_json.encode('utf-8')).hexdigest()

    def _computeToolchainChange(self):
        fingerprint, fingerprint_hash = self._getToolchainFingerprint()
        stored = _read_json_file(self._toolchainFingerprintPath)
        if stored is None:
            return fingerprint, fingerprint_hash, 'toolchain fingerprint missing'
        if stored.get('hash') != fingerprint_hash:
            return fingerprint, fingerprint_hash, 'sandboxed-jdk toolchain/sysroot changed'
        return fingerprint, fingerprint_hash, None

    def needsBuild(self, newestInput):
        fingerprint, fingerprint_hash, reason = self._computeToolchainChange()
        self._pendingToolchainFingerprint = fingerprint
        self._pendingToolchainFingerprintHash = fingerprint_hash
        self._toolchainChangedReason = reason
        if reason is not None:
            return True, reason
        return super().needsBuild(newestInput)

    def build(self):
        if self._toolchainChangedReason is None:
            fingerprint, fingerprint_hash, reason = self._computeToolchainChange()
            self._pendingToolchainFingerprint = fingerprint
            self._pendingToolchainFingerprintHash = fingerprint_hash
            self._toolchainChangedReason = reason
        if self._toolchainChangedReason is not None and os.path.exists(self._buildDir):
            # The JDK build scripts don't have proper dependency edges on header files from the
            # toolchain. Force a clean rebuild if something in the toolchain changed, otherwise
            # this will cause problems with precompiled headers.
            mx.rmtree(self._buildDir)
        super().build()
        with open(self._toolchainFingerprintPath, 'w', encoding='utf-8') as fp:
            json.dump({
                'hash': self._pendingToolchainFingerprintHash,
                'fingerprint': self._pendingToolchainFingerprint,
            }, fp, sort_keys=True, indent=2)
            fp.write('\n')
        self._toolchainChangedReason = None

def _pkgconfig(args, dependency=None, **kwArgs):
    if dependency is None or not isinstance(dependency, AutoconfPkgConfigProject):
        mx.abort("unexpected use of <pkgconfig:...> substitution outside of AutoconfPkgConfigProject")
    env = {
        "PKG_CONFIG_LIBDIR": dependency.getPkgConfigLibDir(),
        "PKG_CONFIG_PATH": dependency.getPkgConfigPath()
    }
    out = mx.OutputCapture()
    mx.run(["pkg-config"] + args.split(','), env=env, out=out)
    return str(out)

pkgconfig_subst = mx_subst.SubstitutionEngine(mx_subst.path_substitutions)
pkgconfig_subst.register_with_arg('pkgconfig', _pkgconfig, keywordArgs=True)

def create_symlink_tree(src, dst):
    for (root, dirs, files) in os.walk(src):
        rel = os.path.relpath(root, start=src)
        dstDir = os.path.join(dst, rel)
        for d in dirs:
            os.makedirs(os.path.join(dstDir, d), exist_ok=True)
        for f in files:
            dstLink = os.path.join(dstDir, f)
            if os.path.islink(dstLink):
                os.unlink(dstLink)
            elif os.path.exists(dstLink):
                mx.abort(f"unexpected file {dstLink} in build directory")
            os.symlink(src=os.path.join(root, f), dst=dstLink)

class ToolchainBashLauncherProject(mx.Project):  # pylint: disable=too-many-ancestors
    def __init__(self, suite, name, deps, workingSets, jvmArgs, **kwArgs):
        super().__init__(suite, name, srcDirs=[], deps=deps, workingSets=workingSets, d=suite.dir, **kwArgs)
        self.jvmArgs = jvmArgs

        assert self.name.endswith("-bash")
        self.exeName = self.name[:-len("-bash")]

    def getArchivableResults(self, use_relpath=True, single=False):
        result = os.path.join(self.get_output_root(), self.exeName)
        yield result, self.exeName

    def getBuildTask(self, args):
        return ToolchainBashLauncherBuildTask(self, args, 1)

    def isPlatformDependent(self):
        return True


class ToolchainBashLauncherBuildTask(mx.BuildTask):
    def __str__(self):
        return "Generating " + self.subject.name

    def newestOutput(self):
        result = os.path.join(self.subject.get_output_root(), self.subject.exeName)
        return mx.TimeStampFile.newest([result])

    def needsBuild(self, newestInput):
        sup = super().needsBuild(newestInput)
        if sup[0]:
            return sup

        result = os.path.join(self.subject.get_output_root(), self.subject.exeName)

        if not os.path.exists(result):
            return True, result + ' does not exist'
        with open(result, encoding='utf-8') as f:
            on_disk = f.read()
        if on_disk != self.contents():
            return True, 'command line changed for ' + os.path.basename(result)

        return False, 'up to date'

    def build(self):
        mx_util.ensure_dir_exists(self.subject.get_output_root())
        result = os.path.join(self.subject.get_output_root(), self.subject.exeName)
        with open(result, "w", encoding='utf-8') as f:
            f.write(self.contents())
        os.chmod(result, 0o755)

    def clean(self, forBuild=False):
        if os.path.exists(self.subject.get_output_root()):
            mx.rmtree(self.subject.get_output_root())

    def contents(self):
        _quote = shlex.quote
        classpath_deps = [dep for dep in self.subject.deps if isinstance(dep, mx.ClasspathDependency)]
        # add jvm args from dependencies
        jvm_args = [_quote(arg) for arg in mx.get_runtime_jvm_args(classpath_deps, include_system_properties=False)]
        jvm_args.append('-Dorg.graalvm.launcher.executablePath="$0"')
        # add properties from the project
        for jvmArg in self.subject.jvmArgs:
            jvm_args.append(_quote(jvmArg))
        java_launcher = mx.get_jdk().java
        return f"""#!/usr/bin/env bash

if [[ "${{VERBOSE_GRAALVM_LAUNCHERS}}" == "true" ]]; then
    set -x
fi

exec {java_launcher} {" ".join(jvm_args)} "$@"
"""


class SandboxedJDKStaticLibrarySymbols(StaticLibrarySymbolsBuilder):
    def _static_lib_root(self):
        return os.path.join(mx.dependency('SANDBOXED_JDK').output, 'images', 'static-libs', 'lib')

    def _manifest_path(self, static_lib):
        return os.path.join(self.get_output_root(), 'static', mx.get_os() + '-' + mx.get_arch(), 'musl-swcfi', os.path.basename(static_lib) + '.symbols')


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='JDK Libraries built with Sandboxed Toolchain',
    short_name='sbjdk',
    dir_name=False,
    license_files=[],
    dependencies=['sbsvm'],
    third_party_license_files=[],
    support_distributions=['sandbox-toolchains:SANDBOXED_LIBRARIES_SUPPORT'],
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='Dependencies of JDK Dynamic Libraries built with Sandboxed Toolchain',
    short_name='sbjdkdeps',
    dir_name=False,
    license_files=[],
    dependencies=['sbjdk'],
    third_party_license_files=[],
    support_distributions=['sandbox-toolchains:SANDBOXED_JDK_DEPS_SUPPORT'],
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='SVM Support Libraries built with Sandboxed Toolchain',
    short_name='sbsvm',
    dir_name='svm',
    license_files=[],
    third_party_license_files=[],
    builder_jar_distributions=['sandbox-toolchains:SVM_SANDBOXED'],
    extra_native_targets=['linux-default-musl-swcfi'] if mx.is_linux() else [],
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='LLVM Toolchain for Sandboxing with bash launchers',
    short_name='bsbllvm',
    dir_name=False,
    license_files=[],
    third_party_license_files=[],
    support_distributions=['sandbox-toolchains:SANDBOXED_MUSL_TOOLCHAIN_BASH_LAUNCHER'],
    stage1_only=True,
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='LLVM Toolchain for Sandboxing',
    short_name='sbllvm',
    dir_name=False,
    license_files=[],
    third_party_license_files=[],
    support_distributions=['sandbox-toolchains:SANDBOXED_MUSL_TOOLCHAIN'],
    final_stage_only=True,
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='Rust Toolchain for Sandboxing',
    short_name='sbrust',
    dir_name='toolchains',
    license_files=[],
    third_party_license_files=[],
    support_distributions=['sandbox-toolchains:SANDBOXED_RUST_TOOLCHAIN'],
    final_stage_only=True,
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmComponent(
    suite=_suite,
    name='GraalOS support files',
    short_name='gos',
    dir_name='.',
    license_files=[],
    third_party_license_files=[],
    support_distributions=['sandbox-toolchains:GRAALOS_SUPPORT'],
))
