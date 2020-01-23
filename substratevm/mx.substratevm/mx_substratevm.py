#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#
# ----------------------------------------------------------------------------------------------------

from __future__ import print_function

import os
import time
import re
import tempfile
from glob import glob
from contextlib import contextmanager
from distutils.dir_util import mkpath, remove_tree  # pylint: disable=no-name-in-module
from os.path import join, exists, basename, dirname
from shutil import move
import itertools
import pipes
from xml.dom.minidom import parse
from argparse import ArgumentParser
import fnmatch

import mx
import mx_compiler
import mx_gate
import mx_unittest
import mx_sdk_vm
import mx_subst
from mx_compiler import GraalArchiveParticipant
from mx_gate import Task
from mx_substratevm_benchmark import run_js, host_vm_tuple, output_processors, rule_snippets  # pylint: disable=unused-import
from mx_unittest import _run_tests, _VMLauncher

import sys

if sys.version_info[0] < 3:
    from StringIO import StringIO
    def _decode(x):
        return x
else:
    from io import StringIO
    def _decode(x):
        return x.decode()

GRAAL_COMPILER_FLAGS_BASE = [
    '-XX:+UnlockExperimentalVMOptions',
    '-XX:+EnableJVMCI',
    '-Dtruffle.TrustAllTruffleRuntimeProviders=true', # GR-7046
    '-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime', # use truffle interpreter as fallback
    '-Dgraalvm.ForcePolyglotInvalid=true', # use PolyglotInvalid PolyglotImpl fallback (when --tool:truffle is not used)
    '-Dgraalvm.locatorDisabled=true',
]

GRAAL_COMPILER_FLAGS_MAP = dict()
GRAAL_COMPILER_FLAGS_MAP['8'] = ['-d64', '-XX:-UseJVMCIClassLoader']
GRAAL_COMPILER_FLAGS_MAP['11'] = []
# Disable the check for JDK-8 graal version.
GRAAL_COMPILER_FLAGS_MAP['11'] += ['-Dsubstratevm.IgnoreGraalVersionCheck=true']
# GR-11937: Use bytecodes instead of invoke-dynamic for string concatenation.
GRAAL_COMPILER_FLAGS_MAP['11'] += ['-Djava.lang.invoke.stringConcat=BC_SB']


# Turn a list of package names into a list of `--add-exports` command line arguments.
def add_exports_from_packages(packageNameList):
    # Return one command line argument (pair) for one package name.
    def add_exports_to_all_unnamed(packageName):
        return ['--add-exports', packageName + '=ALL-UNNAMED']

    return itertools.chain.from_iterable(add_exports_to_all_unnamed(package) for package in packageNameList)


# Turn a list of package names into a list of `--add-opens` command line arguments.
def add_opens_from_packages(packageNameList):
    # Return one command line argument (pair) for one package name.
    def add_opens_to_all_unnamed(packageName):
        return ['--add-opens', packageName + '=ALL-UNNAMED']

    return itertools.chain.from_iterable(add_opens_to_all_unnamed(package) for package in packageNameList)


# JVMCI access
graal_compiler_export_packages = [
    'jdk.internal.vm.ci/jdk.vm.ci.runtime',
    'jdk.internal.vm.ci/jdk.vm.ci.code',
    'jdk.internal.vm.ci/jdk.vm.ci.aarch64',
    'jdk.internal.vm.ci/jdk.vm.ci.amd64',
    'jdk.internal.vm.ci/jdk.vm.ci.meta',
    'jdk.internal.vm.ci/jdk.vm.ci.hotspot',
    'jdk.internal.vm.ci/jdk.vm.ci.services',
    'jdk.internal.vm.ci/jdk.vm.ci.common',
    'jdk.internal.vm.ci/jdk.vm.ci.code.site',
    'jdk.internal.vm.ci/jdk.vm.ci.code.stack',
]
GRAAL_COMPILER_FLAGS_MAP['11'].extend(add_exports_from_packages(graal_compiler_export_packages))

graal_compiler_opens_packages = [
    'jdk.internal.vm.compiler/org.graalvm.compiler.debug',
    'jdk.internal.vm.compiler/org.graalvm.compiler.nodes',]
GRAAL_COMPILER_FLAGS_MAP['11'].extend(add_opens_from_packages(graal_compiler_opens_packages))

# Packages to open to allow reflective access at runtime.
jdk_opens_packages = [
    # Reflective access
    'jdk.unsupported/sun.reflect',
    # Reflective access to jdk.internal.module.Modules, using which I can export and open other modules.
    'java.base/jdk.internal.module'
]
GRAAL_COMPILER_FLAGS_MAP['11'].extend(add_opens_from_packages(jdk_opens_packages))

# These packages should be opened at runtime calls to Modules.addOpens, if they are still needed.
java_base_opens_packages = [
    # Reflective access to jdk.internal.ref.CleanerImpl$PhantomCleanableRef.
    'java.base/jdk.internal.ref',
    # Reflective access to jdk.internal.reflect.MethodAccessor.
    'java.base/jdk.internal.reflect',
    # Reflective access to java.io.ExpiringCache
    'java.base/java.io',
    # Reflective access to private fields of java.lang.Class.
    'java.base/java.lang',
    # Reflective access to java.lang.reflect.ProxyGenerator.generateProxyClass
    'java.base/java.lang.reflect',
    # Reflective access to java.lang.invoke.VarHandle*.
    'java.base/java.lang.invoke',
    # Reflective access to java.lang.Reference.referent.
    'java.base/java.lang.ref',
    # Reflective access to java.net.URL.getURLStreamHandler.
    'java.base/java.net',
    # Reflective access to java.nio.MappedByteBuffer.fd.
    'java.base/java.nio',
    # Reflective access to java.nio.files.FileTypeDetector
    'java.base/java.nio.file',
    # Reflective access to java.security.Provider.knownEngines
    'java.base/java.security',
    # Reflective access javax.crypto.JceSecurity.getVerificationResult
    'java.base/javax.crypto',
    # Reflective access to java.util.Bits.words.
    'java.base/java.util',
    # Reflective access to java.util.concurrent.atomic.AtomicIntegerFieldUpdater$AtomicIntegerFieldUpdaterImpl.tclass.
    'java.base/java.util.concurrent.atomic',
    # Reflective access to sun.security.x509.OIDMap.nameMap
    'java.base/sun.security.x509',
    'java.base/jdk.internal.logger',]
GRAAL_COMPILER_FLAGS_MAP['11'].extend(add_opens_from_packages(java_base_opens_packages))

# Reflective access to org.graalvm.nativeimage.impl.ImageSingletonsSupport.
graal_sdk_opens_packages = [
    'org.graalvm.sdk/org.graalvm.nativeimage.impl',
    'org.graalvm.sdk/org.graalvm.polyglot',]
GRAAL_COMPILER_FLAGS_MAP['11'].extend(add_opens_from_packages(graal_sdk_opens_packages))

graal_truffle_opens_packages = [
    'org.graalvm.truffle/com.oracle.truffle.polyglot',
    'org.graalvm.truffle/com.oracle.truffle.api.impl',]
GRAAL_COMPILER_FLAGS_MAP['11'].extend(add_opens_from_packages(graal_truffle_opens_packages))

# Currently JDK 13, 14, 15 and JDK 11 have the same flags
GRAAL_COMPILER_FLAGS_MAP['13'] = GRAAL_COMPILER_FLAGS_MAP['11']
GRAAL_COMPILER_FLAGS_MAP['14'] = GRAAL_COMPILER_FLAGS_MAP['11']
GRAAL_COMPILER_FLAGS_MAP['15'] = GRAAL_COMPILER_FLAGS_MAP['11']

def svm_java_compliance():
    return mx.get_jdk(tag='default').javaCompliance

def svm_java8():
    return svm_java_compliance() <= mx.JavaCompliance('1.8')

# The list of supported Java versions is implicitly defined via GRAAL_COMPILER_FLAGS_MAP:
# If there are no compiler flags for a Java version, it is also not supported.
if str(svm_java_compliance().value) not in GRAAL_COMPILER_FLAGS_MAP:
    mx.abort("Substrate VM does not support this Java version: " + str(svm_java_compliance()))
GRAAL_COMPILER_FLAGS = GRAAL_COMPILER_FLAGS_BASE + GRAAL_COMPILER_FLAGS_MAP[str(svm_java_compliance().value)]

IMAGE_ASSERTION_FLAGS = ['-H:+VerifyGraalGraphs', '-H:+VerifyPhases']
suite = mx.suite('substratevm')
svmSuites = [suite]
clibraryDists = ['SVM_HOSTED_NATIVE']


def _host_os_supported():
    return mx.get_os() == 'linux' or mx.get_os() == 'darwin' or mx.get_os() == 'windows'

def svm_unittest_config_participant(config):
    vmArgs, mainClass, mainClassArgs = config
    # Run the VM in a mode where application/test classes can
    # access JVMCI loaded classes.
    vmArgs = GRAAL_COMPILER_FLAGS + vmArgs
    return (vmArgs, mainClass, mainClassArgs)

if mx.primary_suite() == suite:
    mx_unittest.add_config_participant(svm_unittest_config_participant)

def classpath(args):
    if not args:
        return [] # safeguard against mx.classpath(None) behaviour
    return mx.classpath(args, jdk=mx_compiler.jdk)

def clibrary_paths():
    return (mx._get_dependency_path(d) for d in clibraryDists)

def platform_name():
    return mx.get_os() + "-" + mx.get_arch()

def clibrary_libpath():
    return ','.join(join(path, platform_name()) for path in clibrary_paths())

def svm_suite():
    return svmSuites[-1]

def svmbuild_dir(suite=None):
    if not suite:
        suite = svm_suite()
    return join(suite.dir, 'svmbuild')


class GraalVMConfig(object):
    def __init__(self, dynamicimports=None, disable_libpolyglot=False, force_bash_launchers=None, skip_libraries=None, exclude_components=None):
        self.dynamicimports = dynamicimports or []
        self.disable_libpolyglot = disable_libpolyglot
        self.force_bash_launchers = force_bash_launchers or []
        self.skip_libraries = skip_libraries or []
        self.exclude_components = exclude_components or []
        for x, _ in mx.get_dynamic_imports():
            self.dynamicimports.append(x)
        if '/substratevm' not in self.dynamicimports:
            self.dynamicimports.append('/substratevm')

    def mx_args(self):
        args = ['--disable-installables=true']
        if self.dynamicimports:
            args += ['--dynamicimports', ','.join(self.dynamicimports)]
        if self.disable_libpolyglot:
            args += ['--disable-libpolyglot']
        if self.force_bash_launchers:
            if self.force_bash_launchers is True:
                args += ['--force-bash-launchers=true']
            else:
                args += ['--force-bash-launchers=' + ','.join(self.force_bash_launchers)]
        if self.skip_libraries:
            if self.skip_libraries is True:
                args += ['--skip-libraries=true']
            else:
                args += ['--skip-libraries=' + ','.join(self.skip_libraries)]
        if self.exclude_components:
            args += ['--exclude-components=' + ','.join(self.exclude_components)]
        return args

    def _tuple(self):
        _force_bash_launchers = tuple(self.force_bash_launchers) if isinstance(self.force_bash_launchers, list) else self.force_bash_launchers
        _skip_libraries = tuple(self.skip_libraries) if isinstance(self.skip_libraries, list) else self.skip_libraries
        return tuple(self.dynamicimports), self.disable_libpolyglot, _force_bash_launchers, _skip_libraries, tuple(self.exclude_components)

    def __hash__(self):
        return hash(self._tuple())

    def __eq__(self, other):
        if not isinstance(other, GraalVMConfig):
            return False
        return self._tuple() == self._tuple()

    def __repr__(self):
        return "GraalVMConfig[{}]".format(self._tuple())


def _vm_suite_dir():
    return join(dirname(suite.dir), 'vm')


def _mx_vm(args, config, nonZeroIsFatal=True, out=None, err=None, timeout=None, env=None, quiet=False):
    return mx.run_mx(config.mx_args() + args, suite=_vm_suite_dir(), nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, timeout=timeout, env=env, quiet=quiet)


_vm_homes = {}


def _vm_home(config):
    if config not in _vm_homes:
        # get things initialized (e.g., cloning)
        _mx_vm(['graalvm-home'], config, out=mx.OutputCapture())
        capture = mx.OutputCapture()
        _mx_vm(['graalvm-home'], config, out=capture, quiet=True)
        _vm_homes[config] = capture.data.strip()
    return _vm_homes[config]


_graalvm_force_bash_launchers = ['polyglot', 'native-image-configure', 'gu']
_graalvm_skip_libraries = ['native-image-agent']
_graalvm_exclude_components = ['gu'] if mx.is_windows() else []  # gu does not work on Windows atm

def _graalvm_config():
    return GraalVMConfig(disable_libpolyglot=True,
                         force_bash_launchers=_graalvm_force_bash_launchers,
                         skip_libraries=_graalvm_skip_libraries,
                         exclude_components=_graalvm_exclude_components)

def _graalvm_jvm_config():
    return GraalVMConfig(disable_libpolyglot=True,
                         force_bash_launchers=True,
                         skip_libraries=True,
                         exclude_components=_graalvm_exclude_components)

def _graalvm_js_config():
    return GraalVMConfig(dynamicimports=['/graal-js'],
                         disable_libpolyglot=True,
                         force_bash_launchers=_graalvm_force_bash_launchers + ['js'],
                         skip_libraries=_graalvm_skip_libraries,
                         exclude_components=_graalvm_exclude_components)


graalvm_config = _graalvm_config
graalvm_jvm_config = _graalvm_jvm_config


def build_native_image_image(config=None, args=None):
    config = config or graalvm_config()
    mx.log('Building GraalVM with native-image in ' + _vm_home(config))
    env = os.environ.copy()
    if mx.version < mx.VersionSpec("5.219"):
        mx.warn("mx version is older than 5.219, SVM's GraalVM build will not be built with links.\nConsider updating mx to improve IDE compile-on-save workflow.")
    if not mx.is_windows():
        if 'LINKY_LAYOUT' not in env:
            env['LINKY_LAYOUT'] = '*.jar'
        elif '*.jar' not in env['LINKY_LAYOUT']:
            mx.warn("LINKY_LAYOUT already set")
    _mx_vm(['build'] + (args or []), config, env=env)


def locale_US_args():
    return ['-Duser.country=US', '-Duser.language=en']

class Tags(set):
    def __getattr__(self, name):
        if name in self:
            return name
        raise AttributeError

GraalTags = Tags([
    'helloworld',
    'test',
    'maven',
    'js',
    'build',
    'benchmarktest',
    'truffletck',
    'relocations'
])


def vm_native_image_path(config):
    return vm_executable_path('native-image', config)


def vm_executable_path(executable, config):
    if mx.get_os() == 'windows':
        executable += '.cmd'  # links are `.cmd` on windows
    return join(_vm_home(config), 'bin', executable)


@contextmanager
def native_image_context(common_args=None, hosted_assertions=True, native_image_cmd='', config=None, build_if_missing=False):
    config = config or graalvm_config()
    common_args = [] if common_args is None else common_args
    base_args = ['--no-fallback', '-H:+EnforceMaxRuntimeCompileMethods', '-H:+TraceClassInitialization']
    base_args += ['-H:Path=' + svmbuild_dir()]
    has_server = mx.get_os() != 'windows'
    if mx.get_opts().verbose:
        base_args += ['--verbose']
    if mx.get_opts().very_verbose:
        if has_server:
            base_args += ['--verbose-server']
        else:
            base_args += ['--verbose']
    if hosted_assertions:
        base_args += native_image_context.hosted_assertions
    if native_image_cmd:
        if not exists(native_image_cmd):
            mx.abort('Given native_image_cmd does not exist')
    else:
        native_image_cmd = vm_native_image_path(config)

    if build_if_missing and not exists(native_image_cmd):
        build_native_image_image(config)
    if exists(native_image_cmd):
        mx.log('Use ' + native_image_cmd + ' for remaining image builds')
        def _native_image(args, **kwargs):
            mx.run([native_image_cmd, '-H:CLibraryPath=' + clibrary_libpath()] + args, **kwargs)
    else:
        raise mx.abort("GraalVM not built? could not find " + native_image_cmd)

    def query_native_image(all_args, option):
        out = mx.LinesOutputCapture()
        _native_image(['--dry-run'] + all_args, out=out)
        for line in out.lines:
            _, sep, after = line.partition(option)
            if sep:
                return after.split(' ')[0].rstrip()
        return None
    def native_image_func(args, **kwargs):
        all_args = base_args + common_args + args
        path = query_native_image(all_args, '-H:Path=')
        name = query_native_image(all_args, '-H:Name=')
        image = join(path, name)
        if not has_server and '--no-server' in all_args:
            all_args = [arg for arg in all_args if arg != '--no-server']
        _native_image(all_args, **kwargs)
        return image
    try:
        if exists(native_image_cmd) and has_server:
            _native_image(['--server-wipe'])
        yield native_image_func
    finally:
        if exists(native_image_cmd) and has_server:
            def timestr():
                return time.strftime('%d %b %Y %H:%M:%S') + ' - '
            mx.log(timestr() + 'Shutting down image build servers for ' + native_image_cmd)
            _native_image(['--server-shutdown'])
            mx.log(timestr() + 'Shutting down completed')

native_image_context.hosted_assertions = ['-J-ea', '-J-esa']
_native_unittest_features = '--features=com.oracle.svm.test.ImageInfoTest$TestFeature,com.oracle.svm.test.ServiceLoaderTest$TestFeature,com.oracle.svm.test.SecurityServiceTest$TestFeature'


def svm_gate_body(args, tasks):
    build_native_image_image()
    with native_image_context(IMAGE_ASSERTION_FLAGS) as native_image:
        with Task('image demos', tasks, tags=[GraalTags.helloworld]) as t:
            if t:
                if svm_java8():
                    javac_image(['--output-path', svmbuild_dir()])
                    javac_command = ['--javac-command', ' '.join(javac_image_command(svmbuild_dir()))]
                else:
                    # Building javac image currently only supported for Java 8
                    javac_command = []
                helloworld(['--output-path', svmbuild_dir()] + javac_command)
                helloworld(['--output-path', svmbuild_dir(), '--shared'])  # Build and run helloworld as shared library
                cinterfacetutorial([])
                clinittest([])

        with Task('native unittests', tasks, tags=[GraalTags.test]) as t:
            if t:
                with tempfile.NamedTemporaryFile(mode='w') as blacklist:
                    if svm_java8():
                        blacklist_args = []
                    else:
                        # Currently not working on Java > 8
                        blacklist.write('com.oracle.svm.test.ServiceLoaderTest')
                        blacklist.flush()
                        blacklist_args = ['--blacklist', blacklist.name]

                    # We need the -H:+EnableAllSecurityServices for com.oracle.svm.test.SecurityServiceTest
                    native_unittest(['--build-args', _native_unittest_features, '-H:+EnableAllSecurityServices'] + blacklist_args)

        with Task('Run Truffle NFI unittests with SVM image', tasks, tags=["svmjunit"]) as t:
            if t:
                testlib = mx_subst.path_substitutions.substitute('-Dnative.test.lib=<path:truffle:TRUFFLE_TEST_NATIVE>/<lib:nativetest>')
                native_unittest_args = ['com.oracle.truffle.nfi.test', '--build-args', '--language:nfi',
                                        '-H:MaxRuntimeCompileMethods=1500', '--run-args', testlib, '--very-verbose', '--enable-timing']
                native_unittest(native_unittest_args)

        with Task('Truffle TCK', tasks, tags=[GraalTags.truffletck]) as t:
            if t:
                junit_native_dir = join(svmbuild_dir(), platform_name(), 'junit')
                mkpath(junit_native_dir)
                junit_tmp_dir = tempfile.mkdtemp(dir=junit_native_dir)
                try:
                    unittest_deps = []
                    unittest_file = join(junit_tmp_dir, 'truffletck.tests')
                    _run_tests([], lambda deps, vm_launcher, vm_args: unittest_deps.extend(deps), _VMLauncher('dummy_launcher', None, mx_compiler.jdk), ['@Test', '@Parameters'], unittest_file, [], [re.compile('com.oracle.truffle.tck.tests')], None, mx.suite('truffle'))
                    if not exists(unittest_file):
                        mx.abort('TCK tests not found.')
                    unittest_deps.append(mx.dependency('truffle:TRUFFLE_SL_TCK'))
                    vm_image_args = mx.get_runtime_jvm_args(unittest_deps, jdk=mx_compiler.jdk)
                    tests_image = native_image(vm_image_args + ['--macro:truffle',
                                                                '--features=com.oracle.truffle.tck.tests.TruffleTCKFeature',
                                                                '-H:Class=org.junit.runner.JUnitCore', '-H:IncludeResources=com/oracle/truffle/sl/tck/resources/.*',
                                                                '-H:MaxRuntimeCompileMethods=3000'])
                    with open(unittest_file) as f:
                        test_classes = [l.rstrip() for l in f.readlines()]
                    mx.run([tests_image, '-Dtck.inlineVerifierInstrument=false'] + test_classes)
                finally:
                    remove_tree(junit_tmp_dir)

        with Task('Relocations in generated object file on Linux', tasks, tags=[GraalTags.relocations]) as t:
            if t:
                if mx.get_os() == 'linux':
                    reloc_test_path = join(svmbuild_dir(), 'reloctest')
                    mkpath(reloc_test_path)
                    try:
                        def reloctest(arguments):
                            temp_dir = join(reloc_test_path, '__build_tmp')
                            if exists(temp_dir):
                                remove_tree(temp_dir)
                            mkpath(temp_dir)
                            helloworld(['--output-path', reloc_test_path, '-H:TempDirectory=' + temp_dir] + arguments)
                            files = [f for f in os.listdir(temp_dir) if re.match(r'SVM-*', f)]
                            if len(files) != 1:
                                raise Exception('Expected 1 SVM temporary directory. Found: ' + str(len(files)) + '. Perhaps the temporary directory name pattern has changed?')

                            svm_temp_dir = join(temp_dir, files[0])
                            obj_file = join(svm_temp_dir, 'helloworld.o')
                            lib_output = join(svm_temp_dir, 'libhello.so')
                            if not os.path.isfile(obj_file):
                                raise Exception('Expected helloworld.o output file from HelloWorld image. Has the helloworld native image function changed?')
                            mx.run(["cc", "--shared", obj_file, "-o", lib_output])

                            def procOutput(output):
                                if '(TEXTREL)' in output:
                                    mx.log_error('Detected TEXTREL in the output object file after native-image generation. This means that a change has introduced relocations in a read-only section.')
                                    mx.log_error('Arguments to the native-image which caused the error: ' + ','.join(arguments))
                                    raise Exception()
                            mx.run(["readelf", "--dynamic", lib_output], out=procOutput)

                        reloctest(['-H:+SpawnIsolates', '-H:+UseOnlyWritableBootImageHeap'])
                        reloctest(['-H:-SpawnIsolates', '-H:+UseOnlyWritableBootImageHeap'])
                    finally:
                        remove_tree(reloc_test_path)
                else:
                    mx.log('Skipping relocations test. Reason: Only tested on Linux.')

    with Task('JavaScript', tasks, tags=[GraalTags.js]) as t:
        if t:
            build_native_image_image(config=_graalvm_js_config())
            with native_image_context(IMAGE_ASSERTION_FLAGS, config=_graalvm_js_config()) as native_image:
                js = build_js(native_image)
                test_run([js, '-e', 'print("hello:" + Array.from(new Array(10), (x,i) => i*i ).join("|"))'], 'hello:0|1|4|9|16|25|36|49|64|81\n')
                test_js(js, [('octane-richards', 1000, 100, 300)])

    with Task('maven plugin checks', tasks, tags=[GraalTags.maven]) as t:
        if t:
            maven_plugin_install(["--deploy-dependencies"])
            maven_plugin_test([])


def javac_image_command(javac_path):
    return [join(javac_path, 'javac'), "-proc:none", "-bootclasspath",
            join(mx_compiler.jdk.home, "jre", "lib", "rt.jar")]


def _native_junit(native_image, unittest_args, build_args=None, run_args=None, blacklist=None, whitelist=None, preserve_image=False):
    unittest_args = unittest_args
    build_args = build_args or []

    javaProperties = {}
    for dist in suite.dists:
        if isinstance(dist, mx.ClasspathDependency):
            for cpEntry in mx.classpath_entries(dist):
                if hasattr(cpEntry, "getJavaProperties"):
                    for key, value in cpEntry.getJavaProperties().items():
                        javaProperties[key] = value
    for key, value in javaProperties.items():
        build_args.append("-D" + key + "=" + value)

    run_args = run_args or ['--verbose']
    junit_native_dir = join(svmbuild_dir(), platform_name(), 'junit')
    mkpath(junit_native_dir)
    junit_tmp_dir = tempfile.mkdtemp(dir=junit_native_dir)
    try:
        unittest_deps = []
        def dummy_harness(test_deps, vm_launcher, vm_args):
            unittest_deps.extend(test_deps)
        unittest_file = join(junit_tmp_dir, 'svmjunit.tests')
        _run_tests(unittest_args, dummy_harness, _VMLauncher('dummy_launcher', None, mx_compiler.jdk), ['@Test', '@Parameters'], unittest_file, blacklist, whitelist, None, None)
        if not exists(unittest_file):
            mx.abort('No matching unit tests found. Skip image build and execution.')
        with open(unittest_file, 'r') as f:
            mx.log('Building junit image for matching: ' + ' '.join(l.rstrip() for l in f))
        extra_image_args = mx.get_runtime_jvm_args(unittest_deps, jdk=mx_compiler.jdk)
        unittest_image = native_image(build_args + extra_image_args + ['--macro:junit=' + unittest_file, '-H:Path=' + junit_tmp_dir])
        if preserve_image:
            build_dir = join(svmbuild_dir(), 'junit')
            mkpath(build_dir)
            unittest_image_dst = join(build_dir, basename(unittest_image))
            move(unittest_image, unittest_image_dst)
            unittest_image = unittest_image_dst
        mx.log('Running: ' + ' '.join(map(pipes.quote, [unittest_image] + run_args)))
        mx.run([unittest_image] + run_args)
    finally:
        remove_tree(junit_tmp_dir)

_mask_str = '#'


def _mask(arg, arg_list):
    if arg in (arg_list + ['-h', '--help', '--']):
        return arg
    else:
        return arg.replace('-', _mask_str)


def unmask(args):
    return [arg.replace(_mask_str, '-') for arg in args]


def _native_unittest(native_image, cmdline_args):
    parser = ArgumentParser(prog='mx native-unittest', description='Run unittests as native image.')
    all_args = ['--build-args', '--run-args', '--blacklist', '--whitelist', '-p', '--preserve-image']
    cmdline_args = [_mask(arg, all_args) for arg in cmdline_args]
    parser.add_argument(all_args[0], metavar='ARG', nargs='*', default=[])
    parser.add_argument(all_args[1], metavar='ARG', nargs='*', default=[])
    parser.add_argument('--blacklist', help='run all testcases not specified in <file>', metavar='<file>')
    parser.add_argument('--whitelist', help='run testcases specified in <file> only', metavar='<file>')
    parser.add_argument('-p', '--preserve-image', help='do not delete the generated native image', action='store_true')
    parser.add_argument('unittest_args', metavar='TEST_ARG', nargs='*')
    pargs = parser.parse_args(cmdline_args)

    blacklist = unmask([pargs.blacklist])[0] if pargs.blacklist else None
    whitelist = unmask([pargs.whitelist])[0] if pargs.whitelist else None

    if whitelist:
        try:
            with open(whitelist) as fp:
                whitelist = [re.compile(fnmatch.translate(l.rstrip())) for l in fp.readlines() if not l.startswith('#')]
        except IOError:
            mx.log('warning: could not read whitelist: ' + whitelist)
    if blacklist:
        try:
            with open(blacklist) as fp:
                blacklist = [re.compile(fnmatch.translate(l.rstrip())) for l in fp.readlines() if not l.startswith('#')]
        except IOError:
            mx.log('warning: could not read blacklist: ' + blacklist)

    unittest_args = unmask(pargs.unittest_args) if unmask(pargs.unittest_args) else ['com.oracle.svm.test']
    _native_junit(native_image, unittest_args, unmask(pargs.build_args), unmask(pargs.run_args), blacklist, whitelist, pargs.preserve_image)


def js_image_test(binary, bench_location, name, warmup_iterations, iterations, timeout=None, bin_args=None):
    bin_args = bin_args if bin_args is not None else []
    jsruncmd = [binary] + bin_args + [join(bench_location, 'harness.js'), '--', join(bench_location, name + '.js'),
                                      '--', '--warmup-iterations=' + str(warmup_iterations),
                                      '--iterations=' + str(iterations)]
    mx.log(' '.join(jsruncmd))

    passing = []

    stdoutdata = []
    def stdout_collector(x):
        stdoutdata.append(x)
        mx.log(x.rstrip())
    stderrdata = []
    def stderr_collector(x):
        stderrdata.append(x)
        mx.warn(x.rstrip())

    returncode = mx.run(jsruncmd, cwd=bench_location, out=stdout_collector, err=stderr_collector, nonZeroIsFatal=False, timeout=timeout)

    if returncode == mx.ERROR_TIMEOUT:
        print('INFO: TIMEOUT (> %d): %s' % (timeout, name))
    elif returncode >= 0:
        matches = 0
        for line in stdoutdata:
            if re.match(r'^\S+: *\d+(\.\d+)?\s*$', line):
                matches += 1
        if matches > 0:
            passing = stdoutdata

    if not passing:
        mx.abort('JS benchmark ' + name + ' failed')


def build_js(native_image):
    return native_image(['--macro:js-launcher', '--no-server'])

def test_js(js, benchmarks, bin_args=None):
    bench_location = join(suite.dir, '..', '..', 'js-benchmarks')
    for benchmark_name, warmup_iterations, iterations, timeout in benchmarks:
        js_image_test(js, bench_location, benchmark_name, warmup_iterations, iterations, timeout, bin_args=bin_args)

def test_run(cmds, expected_stdout, timeout=10):
    stdoutdata = []
    def stdout_collector(x):
        stdoutdata.append(x)
        mx.log(x.rstrip())
    stderrdata = []
    def stderr_collector(x):
        stderrdata.append(x)
        mx.warn(x.rstrip())
    returncode = mx.run(cmds, out=stdout_collector, err=stderr_collector, nonZeroIsFatal=False, timeout=timeout)
    if ''.join(stdoutdata) != expected_stdout:
        mx.abort('Error: stdout does not match expected_stdout')
    return (returncode, stdoutdata, stderrdata)

mx_gate.add_gate_runner(suite, svm_gate_body)


def _cinterfacetutorial(native_image, args=None):
    """Build and run the tutorial for the C interface"""

    args = [] if args is None else args
    tutorial_proj = mx.dependency('com.oracle.svm.tutorial')
    c_source_dir = join(tutorial_proj.dir, 'native')
    build_dir = join(svmbuild_dir(), tutorial_proj.name, 'build')

    # clean / create output directory
    if exists(build_dir):
        remove_tree(build_dir)
    mkpath(build_dir)

    # Build the shared library from Java code
    native_image(['--shared', '-H:Path=' + build_dir, '-H:Name=libcinterfacetutorial',
                  '-H:CLibraryPath=' + tutorial_proj.dir, '-cp', tutorial_proj.output_dir()] + args)

    # Build the C executable
    if mx.get_os() != 'windows':
        mx.run(['cc', '-g', join(c_source_dir, 'cinterfacetutorial.c'),
                '-I.', '-L.', '-lcinterfacetutorial',
                '-ldl', '-Wl,-rpath,' + build_dir,
                '-o', 'cinterfacetutorial'],
               cwd=build_dir)
    else:
        mx.run(['cl', '-MD', join(c_source_dir, 'cinterfacetutorial.c'),
                '-I.', 'libcinterfacetutorial.lib'],
               cwd=build_dir)

    # Start the C executable
    mx.run([join(build_dir, 'cinterfacetutorial')])

def gen_fallbacks():
    native_project_dir = join(mx.dependency('substratevm:com.oracle.svm.native.jvm.' + ('windows' if mx.is_windows() else 'posix')).dir, 'src')

    def collect_missing_symbols():
        symbols = set()

        def collect_symbols_fn(symbol_prefix):
            def collector(line):
                try:
                    mx.logvv('Processing line: ' + line.rstrip())
                    line_tokens = line.split()
                    if mx.is_windows():
                        # Windows dumpbin /SYMBOLS output
                        # 030 00000000 UNDEF  notype ()    External     | JVM_GetArrayLength
                        found_undef = line_tokens[2] == 'UNDEF'
                    elif mx.is_darwin():
                        # Darwin nm
                        #                  U _JVM_InitStackTraceElement
                        found_undef = line_tokens[0].upper() == 'U'
                    else:
                        # Linux objdump objdump --wide --syms
                        # 0000000000000000         *UND*	0000000000000000 JVM_InitStackTraceElement
                        found_undef = line_tokens[1] = '*UND*'
                    if found_undef:
                        symbol_candiate = line_tokens[-1]
                        mx.logvv('Found undefined symbol: ' + symbol_candiate)
                        platform_prefix = '_' if mx.is_darwin() else ''
                        if symbol_candiate.startswith(platform_prefix + symbol_prefix):
                            mx.logv('Pick symbol: ' + symbol_candiate)
                            symbols.add(symbol_candiate[len(platform_prefix):])
                except:
                    mx.logv('Skipping line: ' + line.rstrip())
            return collector

        if mx.is_windows():
            symbol_dump_command = 'dumpbin /SYMBOLS'
        elif mx.is_darwin():
            symbol_dump_command = 'nm'
        elif mx.is_linux():
            symbol_dump_command = 'objdump --wide --syms'
        else:
            mx.abort('gen_fallbacks not supported on ' + sys.platform)

        staticlib_wildcard = ['lib', mx_subst.path_substitutions.substitute('<staticlib:*>')]
        if svm_java8():
            staticlib_wildcard[0:0] = ['jre']
        staticlib_wildcard_path = join(mx_compiler.jdk.home, *staticlib_wildcard)
        for staticlib_path in glob(staticlib_wildcard_path):
            mx.logv('Collect from : ' + staticlib_path)
            mx.run(symbol_dump_command.split() + [staticlib_path], out=collect_symbols_fn('JVM_'))

        if len(symbols) == 0:
            mx.abort('Could not find any unresolved JVM_* symbols in static JDK libraries')
        return symbols

    def collect_implementations():
        impls = set()

        jvm_funcs_path = join(native_project_dir, 'JvmFuncs.c')

        def collect_impls_fn(symbol_prefix):
            def collector(line):
                mx.logvv('Processing line: ' + line.rstrip())
                # JNIEXPORT void JNICALL JVM_DefineModule(JNIEnv *env, jobject module, jboolean is_open, jstring version
                tokens = line.split()
                try:
                    index = tokens.index('JNICALL')
                    name_part = tokens[index + 1]
                    if name_part.startswith(symbol_prefix):
                        impl_name = name_part.split('(')[0].rstrip()
                        mx.logv('Found matching implementation: ' + impl_name)
                        impls.add(impl_name)
                except:
                    mx.logv('Skipping line: ' + line.rstrip())
            return collector

        with open(jvm_funcs_path) as f:
            collector = collect_impls_fn('JVM_')
            for line in f:
                collector(line)

        if len(impls) == 0:
            mx.abort('Could not find any implementations for JVM_* symbols in JvmFuncs.c')
        return impls

    def write_fallbacks(required_fallbacks):
        try:
            new_fallback = StringIO()
            new_fallback.write('/* Fallback implementations autogenerated by mx_substratevm.py */\n\n')
            new_fallback.write('#include <jni.h>\n')
            jnienv_function_stub = '''
JNIEXPORT jobject JNICALL {0}(JNIEnv *env) {{
    (*env)->FatalError(env, "{0} called:  Unimplemented");
    return NULL;
}}
'''
            plain_function_stub = '''
JNIEXPORT void JNICALL {0}() {{
    fprintf(stderr, "{0} called:  Unimplemented\\n");
    abort();
}}
'''
            noJNIEnvParam = [
                'JVM_GC',
                'JVM_ActiveProcessorCount',
                'JVM_GetInterfaceVersion',
                'JVM_GetManagement',
                'JVM_IsSupportedJNIVersion',
                'JVM_MaxObjectInspectionAge',
                'JVM_NativePath',
                'JVM_ReleaseUTF',
                'JVM_SupportsCX8',
                'JVM_BeforeHalt', 'JVM_Halt',
                'JVM_LoadLibrary', 'JVM_UnloadLibrary', 'JVM_FindLibraryEntry',
                'JVM_FindSignal', 'JVM_RaiseSignal', 'JVM_RegisterSignal',
                'JVM_FreeMemory', 'JVM_MaxMemory', 'JVM_TotalMemory',
                'JVM_RawMonitorCreate', 'JVM_RawMonitorDestroy', 'JVM_RawMonitorEnter', 'JVM_RawMonitorExit'
            ]

            for name in required_fallbacks:
                function_stub = plain_function_stub if name in noJNIEnvParam else jnienv_function_stub
                new_fallback.write(function_stub.format(name))

            native_project_src_gen_dir = join(native_project_dir, 'src_gen')
            jvm_fallbacks_path = join(native_project_src_gen_dir, 'JvmFuncsFallbacks.c')
            if exists(jvm_fallbacks_path):
                with open(jvm_fallbacks_path) as old_fallback:
                    if old_fallback.read() == new_fallback.getvalue():
                        return

            mx.ensure_dir_exists(native_project_src_gen_dir)
            with open(jvm_fallbacks_path, mode='w') as new_fallback_file:
                new_fallback_file.write(new_fallback.getvalue())
                mx.log('Updated ' + jvm_fallbacks_path)
        finally:
            if new_fallback:
                new_fallback.close()

    required_fallbacks = collect_missing_symbols() - collect_implementations()
    write_fallbacks(sorted(required_fallbacks))

def _helloworld(native_image, javac_command, path, build_only, args):
    mkpath(path)
    hello_file = os.path.join(path, 'HelloWorld.java')
    envkey = 'HELLO_WORLD_MESSAGE'
    output = 'Hello from native-image!'
    with open(hello_file, 'w') as fp:
        fp.write('public class HelloWorld { public static void main(String[] args) { System.out.println(System.getenv("' + envkey + '")); } }')
        fp.flush()
    mx.run(javac_command + [hello_file])

    javaProperties = {}
    for dist in suite.dists:
        if isinstance(dist, mx.ClasspathDependency):
            for cpEntry in mx.classpath_entries(dist):
                if hasattr(cpEntry, "getJavaProperties"):
                    for key, value in cpEntry.getJavaProperties().items():
                        javaProperties[key] = value
    for key, value in javaProperties.items():
        args.append("-D" + key + "=" + value)

    native_image(["-H:Path=" + path, '-H:+VerifyNamingConventions', '-cp', path, 'HelloWorld'] + args)

    if not build_only:
        expected_output = [output + os.linesep]
        actual_output = []
        def _collector(x):
            actual_output.append(x)
            mx.log(x)

        if '--shared' in args:
            # If helloword got built into a shared library we use python to load the shared library
            # and call its `run_main`. We are capturing the stdout during the call into an unnamed
            # pipe so that we can use it in the actual vs. expected check below.
            try:
                import ctypes
                so_name = mx.add_lib_suffix('helloworld')
                lib = ctypes.CDLL(join(path, so_name))
                stdout = os.dup(1)  # save original stdout
                pout, pin = os.pipe()
                os.dup2(pin, 1)  # connect stdout to pipe
                os.environ[envkey] = output
                lib.run_main(1, 'dummy')  # call run_main of shared lib
                call_stdout = os.read(pout, 120)  # get pipe contents
                actual_output.append(call_stdout)
                os.dup2(stdout, 1)  # restore original stdout
                mx.log('Stdout from calling run_main in shared object {}:'.format(so_name))
                mx.log(call_stdout)
                actual_output = list(map(_decode, actual_output))
            finally:
                del os.environ[envkey]
                os.close(pin)
                os.close(pout)
        else:
            env = os.environ.copy()
            env[envkey] = output
            mx.run([join(path, 'helloworld')], out=_collector, env=env)

        if actual_output != expected_output:
            raise Exception('Unexpected output: ' + str(actual_output) + "  !=  " + str(expected_output))

def _javac_image(native_image, path, args=None):
    args = [] if args is None else args
    mkpath(path)

    # Build an image for the javac compiler, so that we test and gate-check javac all the time.
    # Dynamic class loading code is reachable (used by the annotation processor), so -H:+ReportUnsupportedElementsAtRuntime is a necessary option
    native_image(["-H:Path=" + path, '-cp', mx_compiler.jdk.toolsjar, "com.sun.tools.javac.Main", "javac",
                  "-H:+ReportUnsupportedElementsAtRuntime", "-H:+AllowIncompleteClasspath",
                  "-H:IncludeResourceBundles=com.sun.tools.javac.resources.compiler,com.sun.tools.javac.resources.javac,com.sun.tools.javac.resources.version"] + args)


orig_command_benchmark = mx.command_function('benchmark')


@mx.command(suite.name, 'benchmark')
def benchmark(args):
    # if '--jsvm=substratevm' in args:
    #     truffle_language_ensure('js')
    return orig_command_benchmark(args)

def mx_post_parse_cmd_line(opts):
    for dist in suite.dists:
        if not dist.isTARDistribution():
            dist.set_archiveparticipant(GraalArchiveParticipant(dist, isTest=dist.name.endswith('_TEST')))

def native_image_context_run(func, func_args=None, config=None, build_if_missing=False):
    func_args = [] if func_args is None else func_args
    with native_image_context(config=config, build_if_missing=build_if_missing) as native_image:
        func(native_image, func_args)

def pom_from_template(proj_dir, svmVersion):
    # Create native-image-maven-plugin pom with correct version info from template
    dom = parse(join(proj_dir, 'pom_template.xml'))
    for svmVersionElement in dom.getElementsByTagName('svmVersion'):
        svmVersionElement.parentNode.replaceChild(dom.createTextNode(svmVersion), svmVersionElement)
    with open(join(proj_dir, 'pom.xml'), 'w') as pom_file:
        dom.writexml(pom_file)

def deploy_native_image_maven_plugin(svmVersion, repo, gpg, keyid):
    proj_dir = join(suite.dir, 'src', 'native-image-maven-plugin')
    pom_from_template(proj_dir, svmVersion)
    # Build and install native-image-maven-plugin into local repository

    maven_args = []
    if keyid:
        maven_args += ['-Dgpg.keyname=' + keyid]
    elif not gpg:
        maven_args += ['-Dgpg.skip=true']
    if repo == mx.maven_local_repository():
        maven_args += ['install']
    else:
        maven_args += [
            '-DaltDeploymentRepository={}::default::{}'.format(repo.name, repo.get_url(svmVersion)),
            'deploy'
        ]
    mx.run_maven(maven_args, cwd=proj_dir)


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='SubstrateVM',
    short_name='svm',
    license_files=[],
    third_party_license_files=[],
    dependencies=['GraalVM compiler', 'Truffle Macro', 'Truffle NFI'],
    jar_distributions=['substratevm:LIBRARY_SUPPORT'],
    builder_jar_distributions=[
        'substratevm:SVM',
        'substratevm:OBJECTFILE',
        'substratevm:POINTSTO',
    ],
    support_distributions=['substratevm:SVM_GRAALVM_SUPPORT'],
))

def _native_image_launcher_main_class():
    """
    Gets the name of the entry point for running com.oracle.svm.driver.NativeImage.
    """
    return "com.oracle.svm.driver.NativeImage" + ("" if svm_java8() else "$JDK9Plus")

def _native_image_launcher_extra_jvm_args():
    """
    Gets the extra JVM args needed for running com.oracle.svm.driver.NativeImage.
    """
    if svm_java8():
        return []
    jdk = mx.get_jdk(tag='default')
    # Support for com.oracle.svm.driver.NativeImage$JDK9Plus
    res = ['--add-exports=java.base/jdk.internal.module=ALL-UNNAMED']
    if not mx_sdk_vm.jdk_enables_jvmci_by_default(jdk):
        res.extend(['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCI'])
    return res

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='Native Image',
    short_name='ni',
    dir_name='svm',
    installable_id='native-image',
    license_files=[],
    third_party_license_files=[],
    dependencies=['SubstrateVM', 'nil'],
    support_distributions=['substratevm:NATIVE_IMAGE_GRAALVM_SUPPORT'],
    launcher_configs=[
        mx_sdk_vm.LauncherConfig(
            destination="bin/<exe:native-image>",
            jar_distributions=["substratevm:SVM_DRIVER"],
            main_class=_native_image_launcher_main_class(),
            build_args=[],
            extra_jvm_args=_native_image_launcher_extra_jvm_args(),
        ),
    ],
    library_configs=[
        mx_sdk_vm.LibraryConfig(
            destination="<lib:native-image-agent>",
            jvm_library=True,
            jar_distributions=[
                'substratevm:SVM_AGENT',
            ],
            build_args=[
            ],
        ),
    ],
    provided_executables=['bin/<cmd:rebuild-images>'],
    installable=True,
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='Native Image licence files',
    short_name='nil',
    dir_name='svm',
    installable_id='native-image',
    license_files=['LICENSE_NATIVEIMAGE.txt'],
    third_party_license_files=[],
    dependencies=[],
    support_distributions=['substratevm:NATIVE_IMAGE_LICENSE_GRAALVM_SUPPORT'],
    installable=True,
    priority=1,
))

if not mx.is_windows():
    mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
        suite=suite,
        name='SubstrateVM LLVM',
        short_name='svml',
        dir_name='svm',
        license_files=[],
        third_party_license_files=[],
        dependencies=['SubstrateVM'],
        builder_jar_distributions=[
            'substratevm:SVM_LLVM',
            'compiler:GRAAL_LLVM',
            'compiler:LLVM_WRAPPER_SHADOWED',
            'compiler:JAVACPP_SHADOWED',
            'compiler:LLVM_PLATFORM_SPECIFIC_SHADOWED',
        ],
    ))


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='Polyglot Native API',
    short_name='polynative',
    dir_name='polyglot',
    license_files=[],
    third_party_license_files=[],
    dependencies=['SubstrateVM'],
    jar_distributions=['substratevm:POLYGLOT_NATIVE_API'],
    support_distributions=[
        "substratevm:POLYGLOT_NATIVE_API_HEADERS",
    ],
    polyglot_lib_build_args=[
        "--macro:truffle",
        "-H:Features=org.graalvm.polyglot.nativeapi.PolyglotNativeAPIFeature",
        "-Dorg.graalvm.polyglot.nativeapi.libraryPath=${.}/../../../polyglot/",
        "-H:CStandard=C11",
        "-H:+SpawnIsolates",
    ],
    polyglot_lib_jar_dependencies=[
        "substratevm:POLYGLOT_NATIVE_API",
    ],
    polyglot_lib_build_dependencies=[
        "substratevm:POLYGLOT_NATIVE_API_HEADERS"
    ],
    has_polyglot_lib_entrypoints=True,
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVMSvmMacro(
    suite=suite,
    name='Native Image JUnit',
    short_name='nju',
    dir_name='junit',
    license_files=[],
    third_party_license_files=[],
    dependencies=['SubstrateVM'],
    builder_jar_distributions=['mx:JUNIT_TOOL', 'mx:JUNIT', 'mx:HAMCREST'],
    support_distributions=['substratevm:NATIVE_IMAGE_JUNIT_SUPPORT'],
))

jar_distributions = [
    'substratevm:GRAAL_HOTSPOT_LIBRARY',
    'compiler:GRAAL_LIBGRAAL_JNI',
    'compiler:GRAAL_TRUFFLE_COMPILER_LIBGRAAL']

if mx_sdk_vm.base_jdk_version() == 8:
    jar_distributions.append('compiler:GRAAL_MANAGEMENT_LIBGRAAL')

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='LibGraal',
    short_name='lg',
    dir_name=False,
    license_files=[],
    third_party_license_files=[],
    dependencies=['SubstrateVM'],
    jar_distributions=[],
    builder_jar_distributions=[],
    support_distributions=[],
    library_configs=[
        mx_sdk_vm.LibraryConfig(
            destination="<lib:jvmcicompiler>",
            jvm_library=True,
            jar_distributions=jar_distributions,
            build_args=[
                '--features=com.oracle.svm.graal.hotspot.libgraal.LibGraalFeature',
                '--initialize-at-build-time',
                '-H:-UseServiceLoaderFeature',
                '-H:+AllowFoldMethods',
                '-H:+ReportExceptionStackTraces',
                '-Djdk.vm.ci.services.aot=true',
                '-Dtruffle.TruffleRuntime='
            ],
        ),
    ],
))

def _native_image_configure_extra_jvm_args():
    if svm_java8():
        return []
    args = list(add_exports_from_packages(['jdk.internal.vm.compiler/org.graalvm.compiler.phases.common', 'jdk.internal.vm.ci/jdk.vm.ci.meta']))
    if not mx_sdk_vm.jdk_enables_jvmci_by_default(mx.get_jdk(tag='default')):
        args.extend(['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCI'])
    return args

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='Native Image Configure Tool',
    short_name='nic',
    dir_name='svm',
    license_files=[],
    third_party_license_files=[],
    dependencies=['ni'],
    support_distributions=[],
    launcher_configs=[
        mx_sdk_vm.LauncherConfig(
            destination="bin/<exe:native-image-configure>",
            jar_distributions=["substratevm:SVM_CONFIGURE"],
            main_class="com.oracle.svm.configure.ConfigurationTool",
            build_args=[
                "-H:-ParseRuntimeOptions",
            ],
            extra_jvm_args=_native_image_configure_extra_jvm_args(),
        )
    ],
))

if mx.is_linux() and mx.get_arch() == "amd64":
    jdk = mx.get_jdk(tag='default')
    if jdk.javaCompliance == '11':
        mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVMSvmStaticLib(
            suite=suite,
            name='JDK11 static libraries compiled with muslc',
            short_name='mjdksl',
            dir_name=False,
            license_files=[],
            third_party_license_files=[],
            dependencies=['svm'],
            support_distributions=['substratevm:JDK11_NATIVE_IMAGE_MUSL_SUPPORT']
        ))


@mx.command(suite_name=suite.name, command_name='helloworld', usage_msg='[options]')
def helloworld(args, config=None):
    """
    builds a Hello, World! native image.
    """
    parser = ArgumentParser(prog='mx helloworld')
    all_args = ['--output-path', '--javac-command', '--build-only']
    masked_args = [_mask(arg, all_args) for arg in args]
    parser.add_argument(all_args[0], metavar='<output-path>', nargs=1, help='Path of the generated image', default=[svmbuild_dir(suite)])
    parser.add_argument(all_args[1], metavar='<javac-command>', help='A javac command to be used', default=mx.get_jdk().javac)
    parser.add_argument(all_args[2], action='store_true', help='Only build the native image', default=False)
    parser.add_argument('image_args', nargs='*', default=[])
    parsed = parser.parse_args(masked_args)
    javac_command = unmask(parsed.javac_command.split())
    output_path = unmask(parsed.output_path)[0]
    build_only = parsed.build_only
    native_image_context_run(
        lambda native_image, a:
            _helloworld(native_image, javac_command, output_path, build_only, a), unmask(parsed.image_args),
        config=config,
        build_if_missing=True
    )


@mx.command(suite.name, 'cinterfacetutorial', 'Runs the ')
def cinterfacetutorial(args):
    """
    runs all tutorials for the C interface.
    """
    native_image_context_run(_cinterfacetutorial, args, build_if_missing=True)


@mx.command(suite.name, 'clinittest', 'Runs the ')
def clinittest(args):
    def build_and_test_clinittest_image(native_image, args=None):
        args = [] if args is None else args
        test_cp = classpath('com.oracle.svm.test')
        build_dir = join(svmbuild_dir(), 'clinittest')

        # clean / create output directory
        if exists(build_dir):
            remove_tree(build_dir)
        mkpath(build_dir)

        # Build and run the example
        native_image(
            ['-H:Path=' + build_dir, '-cp', test_cp, '-H:Class=com.oracle.svm.test.TestClassInitializationMustBeSafe',
             '-H:+PrintClassInitialization', '-H:Name=clinittest', '-H:+ReportExceptionStackTraces'] + args)
        mx.run([join(build_dir, 'clinittest')])

        # Check the reports for initialized classes
        def check_class_initialization(classes_file_name, marker):
            classes_file = os.path.join(build_dir, 'reports', classes_file_name)
            with open(classes_file) as f:
                wrongly_initialized_classes = [line.strip() for line in f if marker not in line.strip()]
                if len(wrongly_initialized_classes) > 0:
                    mx.abort("Only classes with marker " + marker + " must be in file " + classes_file + ". Found:\n" +
                             str(wrongly_initialized_classes))

        reports = os.listdir(os.path.join(build_dir, 'reports'))
        delayed_classes = next(report for report in reports if report.startswith('run_time_classes'))
        safe_classes = next(report for report in reports if report.startswith('safe_classes'))

        check_class_initialization(delayed_classes, 'MustBeDelayed')
        check_class_initialization(safe_classes, 'MustBeSafe')

    native_image_context_run(build_and_test_clinittest_image, args, build_if_missing=True)


orig_command_build = mx.command_function('build')


@mx.command(suite.name, 'build')
def build(args, vm=None):
    if any([opt in args for opt in ['-h', '--help']]):
        orig_command_build(args, vm)

    mx.log('build: Checking SubstrateVM requirements for building ...')

    if not _host_os_supported():
        mx.abort('build: SubstrateVM can be built only on Darwin, Linux and Windows platforms')

    graal_compiler_flags_dir = join(mx.dependency('substratevm:com.oracle.svm.driver').dir, 'resources')

    def update_if_needed(version_tag, graal_compiler_flags):
        flags_filename = 'graal-compiler-flags-' + version_tag + '.config'
        flags_path = join(graal_compiler_flags_dir, flags_filename)
        flags_contents = '\n'.join(graal_compiler_flags)
        needs_update = True
        try:
            with open(flags_path, 'r') as flags_file:
                if flags_file.read() == flags_contents:
                    needs_update = False
        except:
            pass

        if needs_update:
            with open(flags_path, 'w') as f:
                print('Write file ' + flags_path)
                f.write(flags_contents)

    update_if_needed("versions", GRAAL_COMPILER_FLAGS_MAP.keys())
    for version_tag in GRAAL_COMPILER_FLAGS_MAP:
        update_if_needed(version_tag, GRAAL_COMPILER_FLAGS_BASE + GRAAL_COMPILER_FLAGS_MAP[version_tag])

    gen_fallbacks()

    orig_command_build(args, vm)


def _ensure_vm_built(config):
    # build "jvm" config used by native-image and native-image-configure commands
    rebuild_vm = False
    mx.ensure_dir_exists(svmbuild_dir())
    if not mx.is_windows():
        vm_link = join(svmbuild_dir(), 'vm')
        if not os.path.exists(vm_link):
            rebuild_vm = True
            if os.path.lexists(vm_link):
                os.unlink(vm_link)
            vm_linkname = os.path.relpath(_vm_home(config), dirname(vm_link))
            os.symlink(vm_linkname, vm_link)
    rev_file_name = join(svmbuild_dir(), 'vm-rev')
    rev_value = svm_suite().vc.parent(svm_suite().vc_dir)
    if not os.path.exists(rev_file_name):
        rebuild_vm = True
    else:
        with open(rev_file_name, 'r') as f:
            if f.read() != rev_value:
                rebuild_vm = True
    if rebuild_vm:
        with open(rev_file_name, 'w') as f:
            f.write(rev_value)
        build_native_image_image(config)

@mx.command(suite.name, 'native-image')
def native_image_on_jvm(args, **kwargs):
    config = graalvm_jvm_config()
    _ensure_vm_built(config)
    if mx.is_windows():
        executable = vm_native_image_path(config)
    else:
        vm_link = join(svmbuild_dir(), 'vm')
        executable = join(vm_link, 'bin', 'native-image')
    if not exists(executable):
        mx.abort("Can not find " + executable + "\nDid you forget to build? Try `mx build`")

    javaProperties = {}
    for dist in suite.dists:
        if isinstance(dist, mx.ClasspathDependency):
            for cpEntry in mx.classpath_entries(dist):
                if hasattr(cpEntry, "getJavaProperties"):
                    for key, value in cpEntry.getJavaProperties().items():
                        javaProperties[key] = value
    for key, value in javaProperties.items():
        args.append("-D" + key + "=" + value)

    mx.run([executable, '-H:CLibraryPath=' + clibrary_libpath()] + args, **kwargs)

@mx.command(suite.name, 'native-image-configure')
def native_image_configure_on_jvm(args, **kwargs):
    config = graalvm_jvm_config()
    _ensure_vm_built(config)
    if mx.is_windows():
        executable = vm_executable_path('native-image-configure', config)
    else:
        vm_link = join(svmbuild_dir(), 'vm')
        executable = join(vm_link, 'bin', 'native-image-configure')
    if not exists(executable):
        mx.abort("Can not find " + executable + "\nDid you forget to build? Try `mx build`")
    mx.run([executable] + args, **kwargs)


@mx.command(suite.name, 'native-unittest')
def native_unittest(args):
    """builds a native image of JUnit tests and runs them."""
    native_image_context_run(_native_unittest, args, build_if_missing=True)


@mx.command(suite.name, 'maven-plugin-install')
def maven_plugin_install(args):
    parser = ArgumentParser(prog='mx maven-plugin-install')
    parser.add_argument("--deploy-dependencies", action='store_true', help="This will deploy all the artifacts from all suites before building and deploying the plugin")
    parser.add_argument('--licenses', help='Comma-separated list of licenses that are cleared for upload. Only used if no url is given. Otherwise licenses are looked up in suite.py')
    parser.add_argument('--gpg', action='store_true', help='Sign files with gpg before deploying')
    parser.add_argument('--gpg-keyid', help='GPG keyid to use when signing files (implies --gpg)', default=None)
    parser.add_argument('repository_id', metavar='repository-id', nargs='?', action='store', help='Repository ID used for binary deploy. If none is given, mavens local repository is used instead.')
    parser.add_argument('url', metavar='repository-url', nargs='?', action='store', help='Repository URL used for binary deploy. If no url is given, the repository-id is looked up in suite.py')
    parsed = parser.parse_args(args)

    if not suite.isSourceSuite():
        raise mx.abort("maven-plugin-install requires {} to be a source suite, no a binary suite".format(suite.name))

    if parsed.url:
        if parsed.licenses:
            licenses = mx.get_license(parsed.licenses.split(','))
        elif parsed.repository_id:
            licenses = mx.repository(parsed.repository_id).licenses
        else:
            licenses = []
        repo = mx.Repository(suite, parsed.repository_id, parsed.url, parsed.url, licenses)
    elif parsed.repository_id:
        repo = mx.repository(parsed.repository_id)
    else:
        repo = mx.maven_local_repository()

    svm_version = suite.release_version(snapshotSuffix='SNAPSHOT')

    if parsed.deploy_dependencies:
        deploy_args = [
            '--suppress-javadoc',
            '--all-distribution-types',
            '--validate=full',
            '--all-suites',
        ]
        if parsed.licenses:
            deploy_args += ["--licenses", parsed.licenses]
        if parsed.gpg:
            deploy_args += ["--gpg"]
        if parsed.gpg_keyid:
            deploy_args += ["--gpg-keyid", parsed.gpg_keyid]
        if parsed.repository_id:
            deploy_args += [parsed.repository_id]
            if parsed.url:
                deploy_args += [parsed.url]
        suites = set()

        def collect_imports(s):
            if s.name not in suites:
                suites.add(s.name)
                s.visit_imports(visitor)

        def visitor(_, suite_import):
            collect_imports(mx.suite(suite_import.name))

        collect_imports(suite)
        new_env = os.environ.copy()
        if 'DYNAMIC_IMPORTS' in new_env:
            del new_env['DYNAMIC_IMPORTS']
        mx.run_mx(['--suite=' + s for s in suites] + ['maven-deploy'] + deploy_args, suite, env=new_env)

    deploy_native_image_maven_plugin(svm_version, repo, parsed.gpg, parsed.gpg_keyid)

    success_message = [
        '',
        'Use the following plugin snippet to enable native-image building for your maven project:',
        '',
        '<plugin>',
        '    <groupId>org.graalvm.nativeimage</groupId>',
        '    <artifactId>native-image-maven-plugin</artifactId>',
        '    <version>' + svm_version + '</version>',
        '    <executions>',
        '        <execution>',
        '            <goals>',
        '                <goal>native-image</goal>',
        '            </goals>',
        '            <phase>package</phase>',
        '        </execution>',
        '    </executions>',
        '</plugin>',
        '',
        ]
    mx.log('\n'.join(success_message))

@mx.command(suite.name, 'maven-plugin-test')
def maven_plugin_test(args):
    # Create native-image-maven-plugin-test pom with correct version info from template
    proj_dir = join(suite.dir, 'src', 'native-image-maven-plugin-test')
    svm_version = suite.release_version(snapshotSuffix='SNAPSHOT')
    pom_from_template(proj_dir, svm_version)
    # Build native image with native-image-maven-plugin
    env = os.environ.copy()
    maven_opts = env.get('MAVEN_OPTS', '').split()
    if not svm_java8():
        # On Java 9+ without native-image executable the plugin needs access to jdk.internal.module
        maven_opts.append('-XX:+UnlockExperimentalVMOptions')
        maven_opts.append('-XX:+EnableJVMCI')
        maven_opts.append('--add-exports=java.base/jdk.internal.module=ALL-UNNAMED')
    env['MAVEN_OPTS'] = ' '.join(maven_opts)
    mx.run_maven(['-e', 'package'], cwd=proj_dir, env=env)
    mx.run([join(proj_dir, 'target', 'com.oracle.substratevm.nativeimagemojotest')])


@mx.command(suite, 'javac-image', '[image-options]')
def javac_image(args):
    """builds a javac image"""
    parser = ArgumentParser(prog='mx helloworld')
    all_args = ['--output-path']
    masked_args = [_mask(arg, all_args) for arg in args]
    parser.add_argument(all_args[0], metavar='<output-path>', nargs=1, help='Path of the generated image', default=[svmbuild_dir(suite)])
    parser.add_argument('image_args', nargs='*', default=[])
    parsed = parser.parse_args(masked_args)
    output_path = unmask(parsed.output_path)[0]
    native_image_context_run(
        lambda native_image, command_args:
            _javac_image(native_image, output_path, command_args), unmask(parsed.image_args),
        build_if_missing=True
    )
