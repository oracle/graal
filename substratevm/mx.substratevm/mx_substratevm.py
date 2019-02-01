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
import re
import tarfile
import zipfile
import tempfile
from contextlib import contextmanager
from distutils.dir_util import mkpath, copy_tree, remove_tree # pylint: disable=no-name-in-module
from os.path import join, exists, basename, dirname, islink
from shutil import copy2
import collections
import itertools
import glob
from xml.dom.minidom import parse
from argparse import ArgumentParser
import fnmatch

import mx
import mx_compiler
import mx_gate
import mx_unittest
import mx_urlrewrites
import mx_sdk
from mx_compiler import GraalArchiveParticipant
from mx_compiler import run_java
from mx_gate import Task
from mx_substratevm_benchmark import run_js, host_vm_tuple, output_processors, rule_snippets # pylint: disable=unused-import
from mx_unittest import _run_tests, _VMLauncher

GRAAL_COMPILER_FLAGS_BASE = [
    '-XX:+UnlockExperimentalVMOptions',
    '-XX:+EnableJVMCI',
    '-XX:-UseJVMCICompiler', # GR-8656: Do not run with Graal as JIT compiler until libgraal is available.
    '-Dtruffle.TrustAllTruffleRuntimeProviders=true', # GR-7046
]

GRAAL_COMPILER_FLAGS_MAP = dict()
GRAAL_COMPILER_FLAGS_MAP['1.8'] = ['-d64', '-XX:-UseJVMCIClassLoader']
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
    'jdk.internal.vm.ci/jdk.vm.ci.amd64',
    'jdk.internal.vm.ci/jdk.vm.ci.meta',
    'jdk.internal.vm.ci/jdk.vm.ci.hotspot',
    'jdk.internal.vm.ci/jdk.vm.ci.services',
    'jdk.internal.vm.ci/jdk.vm.ci.common',
    'jdk.internal.vm.ci/jdk.vm.ci.code.site']
GRAAL_COMPILER_FLAGS_MAP['11'].extend(add_exports_from_packages(graal_compiler_export_packages))

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
    # Reflective access to private fields of java.lang.Class.
    'java.base/java.lang',
    # Reflective access to java.lang.invoke.VarHandle*.
    'java.base/java.lang.invoke',
    # Reflective access to java.lang.Reference.referent.
    'java.base/java.lang.ref',
    # Reflective access to java.net.URL.getURLStreamHandler.
    'java.base/java.net',
    # Reflective access to java.nio.MappedByteBuffer.fd.
    'java.base/java.nio',
    # Reflective access to java.util.Bits.words.
    'java.base/java.util']
GRAAL_COMPILER_FLAGS_MAP['11'].extend(add_opens_from_packages(java_base_opens_packages))

# Reflective access to org.graalvm.nativeimage.impl.ImageSingletonsSupport.
graal_sdk_opens_packages = [
    'org.graalvm.sdk/org.graalvm.nativeimage.impl']
GRAAL_COMPILER_FLAGS_MAP['11'].extend(add_opens_from_packages(graal_sdk_opens_packages))

def svm_java_compliance():
    return mx.get_jdk(tag='default').javaCompliance

def svm_java80():
    return svm_java_compliance() <= mx.JavaCompliance('1.8')

if svm_java80():
    GRAAL_COMPILER_FLAGS = GRAAL_COMPILER_FLAGS_BASE + GRAAL_COMPILER_FLAGS_MAP['1.8']
else:
    GRAAL_COMPILER_FLAGS = GRAAL_COMPILER_FLAGS_BASE + GRAAL_COMPILER_FLAGS_MAP['11']

IMAGE_ASSERTION_FLAGS = ['-H:+VerifyGraalGraphs', '-H:+VerifyGraalGraphEdges', '-H:+VerifyPhases']
suite = mx.suite('substratevm')
svmSuites = [suite]


def _host_os_supported():
    return mx.get_os() == 'linux' or mx.get_os() == 'darwin' or mx.get_os() == 'windows'

def _unittest_config_participant(config):
    vmArgs, mainClass, mainClassArgs = config
    # Run the VM in a mode where application/test classes can
    # access JVMCI loaded classes.
    vmArgs = GRAAL_COMPILER_FLAGS + vmArgs
    return (vmArgs, mainClass, mainClassArgs)

mx_unittest.add_config_participant(_unittest_config_participant)

def classpath(args):
    if not args:
        return [] # safeguard against mx.classpath(None) behaviour
    return mx.classpath(args, jdk=mx_compiler.jdk)

def clibrary_paths():
    return (join(suite.dir, 'clibraries') for suite in svmSuites)

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

def suite_native_image_root(suite=None):
    if not suite:
        suite = svm_suite()
    root_basename = 'native-image-root-' + str(svm_java_compliance())
    if llvmDistributions and all([mx.distribution(dist).exists() for dist in llvmDistributions]):
        root_basename = 'llvm-' + root_basename
    root_dir = join(svmbuild_dir(suite), root_basename)
    rev_file_name = join(root_dir, 'rev')
    rev_value = suite.vc.parent(suite.vc_dir)
    def write_rev_file():
        mkpath(root_dir)
        with open(rev_file_name, 'w') as rev_file:
            rev_file.write(rev_value)
    if exists(root_dir):
        try:
            with open(rev_file_name, 'r') as rev_file:
                prev_rev_value = rev_file.readline()
        except:
            prev_rev_value = 'nothing'
        if prev_rev_value != rev_value:
            mx.warn('Rebuilding native-image-root as working directory revision changed from ' + prev_rev_value + ' to ' + rev_value)
            remove_tree(root_dir)
            layout_native_image_root(root_dir)
            write_rev_file()
    else:
        layout_native_image_root(root_dir)
        write_rev_file()
    return root_dir

def native_image_distributions():
    deps = [mx.distribution('GRAAL_MANAGEMENT')]
    for d in svm_suite().dists:
        if isinstance(d, str):
            name = d
        else:
            name = d.name
        if name.startswith("NATIVE_IMAGE"):
            deps.append(d)
    return deps

def remove_existing_symlink(target_path):
    if islink(target_path):
        os.remove(target_path)
    return target_path

def symlink_or_copy(target_path, dest_path):
    # Follow symbolic links in case they go outside my suite directories.
    real_target_path = os.path.realpath(target_path)
    if any(real_target_path.startswith(s.dir) for s in mx.suites(includeBinary=False)):
        # Symbolic link to files in my suites.
        sym_target = os.path.relpath(real_target_path, dirname(dest_path))
        try:
            os.symlink(sym_target, dest_path)
        except AttributeError:
            # no `symlink` on Windows
            copy2(real_target_path, dest_path)
    else:
        # Else copy the file to so it can not change out from under me.
        copy2(real_target_path, dest_path)

def native_image_layout(dists, subdir, native_image_root):
    if not dists:
        return
    dest_path = join(native_image_root, subdir)
    # Cleanup leftovers from previous call
    if exists(dest_path):
        remove_tree(dest_path)
    mkpath(dest_path)
    # Create symlinks to conform with native-image directory layout scheme
    def symlink_jar(jar_path):
        symlink_or_copy(jar_path, join(dest_path, basename(jar_path)))

    for dist in dists:
        mx.logv('Add ' + type(dist).__name__ + ' ' + str(dist) + ' to ' + dest_path)
        symlink_jar(dist.path)
        if not dist.isBaseLibrary() and dist.sourcesPath:
            symlink_jar(dist.sourcesPath)

def native_image_extract(dists, subdir, native_image_root):
    target_dir = join(native_image_root, subdir)
    for distribution in dists:
        mx.logv('Add dist {} to {}'.format(distribution, target_dir))
        if distribution.path.endswith('tar'):
            compressedFile = tarfile.open(distribution.path, 'r:')
        elif distribution.path.endswith('jar') or distribution.path.endswith('zip'):
            compressedFile = zipfile.ZipFile(distribution.path)
        else:
            raise mx.abort('Unsupported compressed file ' + distribution.path)
        compressedFile.extractall(target_dir)

def native_image_option_properties(option_kind, option_flag, native_image_root):
    target_dir = join(native_image_root, option_kind, option_flag)
    target_path = remove_existing_symlink(join(target_dir, 'native-image.properties'))

    option_properties = None
    for svm_suite in svmSuites:
        candidate = join(svm_suite.mxDir, option_kind + '-' + option_flag + '.properties')
        if exists(candidate):
            option_properties = candidate

    if option_properties:
        mx.logv('Add symlink to ' + str(option_properties))
        mkpath(target_dir)
        symlink_or_copy(option_properties, target_path)

flag_suitename_map = collections.OrderedDict([
    ('js', ('graal-js', ['GRAALJS', 'GRAALJS_LAUNCHER', 'ICU4J'], ['ICU4J-DIST'], 'js')),
])

class ToolDescriptor:
    def __init__(self, image_deps=None, builder_deps=None, native_deps=None):
        """
        By adding a new ToolDescriptor entry in the tools_map a new --tool:<keyname>
        option is made available to native-image and also makes the tool available as
        tool:<keyname> in a native-image properties file Requires statement.  The tool is
        represented in the <native_image_root>/tools/<keyname> directory. If a
        corresponding tools-<keyname>.properties file exists in mx.substratevm it will get
        symlinked as <native_image_root>/tools/<keyname>/native-image.properties so that
        native-image will use these options whenever the tool is requested. Image_deps and
        builder_deps (see below) are also represented as symlinks to JAR files in
        <native_image_root>/tools/<keyname> (<native_image_root>/tools/<keyname>/builder).

        :param image_deps: list dependencies that get added to the image-cp (the classpath
        of the application you want to compile into an image) when using the tool.
        :param builder_deps: list dependencies that get added to the image builder-cp when
        using the tool. Builder-cp adds to the classpath that contains the image-generator
        itself. This might be necessary, e.g. when custom substitutions are needed to be
        able to compile classes on the image-cp. Another possible reason is when the image
        builder needs to prepare things prior to image building and doing so needs
        additional classes (see junit tool).
        :param native_deps: list native dependencies that should be extracted to
        <native_image_root>/tools/<keyname>.
        """
        self.image_deps = image_deps if image_deps else []
        self.builder_deps = builder_deps if builder_deps else []
        self.native_deps = native_deps if native_deps else []

tools_map = {
    'truffle' : ToolDescriptor(),
    'native-image' : ToolDescriptor(),
    'junit' : ToolDescriptor(builder_deps=['mx:JUNIT_TOOL', 'JUNIT', 'HAMCREST']),
    'regex' : ToolDescriptor(image_deps=['regex:TREGEX']),
}

def native_image_path(native_image_root):
    native_image_name = 'native-image'
    native_image_dir = join(native_image_root, platform_name(), 'bin')
    return join(native_image_dir, native_image_name)

def remove_option_prefix(text, prefix):
    if text.startswith(prefix):
        return True, text[len(prefix):]
    return False, text

def extract_target_name(arg, kind):
    target_name, target_value = None, None
    is_kind, option_tail = remove_option_prefix(arg, '--' + kind + ':')
    if is_kind:
        target_name, _, target_value = option_tail.partition('=')
    return target_name, target_value

def build_native_image_image():
    image_path = native_image_path(suite_native_image_root())
    mx.log('Building native-image executable ' + image_path)
    image_dir = dirname(image_path)
    mkpath(image_dir)
    native_image_on_jvm(['--tool:native-image', '-H:Path=' + image_dir])

svmDistribution = ['substratevm:SVM']
llvmDistributions = []
graalDistribution = ['compiler:GRAAL']
librarySupportDistribution = ['substratevm:LIBRARY_SUPPORT']

def layout_native_image_root(native_image_root):

    def names_to_dists(dist_names):
        deps = [mx.dependency(dist_name) for dist_name in dist_names]
        return [dep for dep in deps if not dep.isDistribution() or dep.exists()]

    def native_image_layout_dists(subdir, dist_names):
        native_image_layout(names_to_dists(dist_names), subdir, native_image_root)

    def native_image_extract_dists(subdir, dist_names):
        native_image_extract(names_to_dists(dist_names), subdir, native_image_root)

    native_image_layout_dists(join('lib', 'graalvm'), ['substratevm:SVM_DRIVER', 'sdk:LAUNCHER_COMMON'])

    # Create native-image layout for sdk parts
    graal_sdk_dists = ['sdk:GRAAL_SDK']
    if svm_java80():
        native_image_layout_dists(join('lib', 'boot'), graal_sdk_dists)
        jvmci_dists = graalDistribution
    else:
        jvmci_dists = graalDistribution + graal_sdk_dists

    # Create native-image layout for compiler & jvmci parts
    native_image_layout_dists(join('lib', 'jvmci'), jvmci_dists)
    jdk_config = mx.get_jdk()
    if svm_java80():
        jvmci_path = join(jdk_config.home, 'jre', 'lib', 'jvmci')
        if os.path.isdir(jvmci_path):
            for symlink_name in os.listdir(jvmci_path):
                symlink_or_copy(join(jvmci_path, symlink_name), join(native_image_root, 'lib', 'jvmci', symlink_name))

    # Create native-image layout for truffle parts
    if mx.get_os() != 'windows':  # necessary until Truffle is fully supported (GR-7941)
        native_image_layout_dists(join('lib', 'truffle'), ['truffle:TRUFFLE_API', 'truffle:TRUFFLE_NFI'])

    # Create native-image layout for tools parts
    for tool_name in tools_map:
        tool_descriptor = tools_map[tool_name]
        native_image_layout_dists(join('tools', tool_name, 'builder'), tool_descriptor.builder_deps)
        native_image_layout_dists(join('tools', tool_name), tool_descriptor.image_deps)
        native_image_extract_dists(join('tools', tool_name), tool_descriptor.native_deps)
        native_image_option_properties('tools', tool_name, native_image_root)

    # Create native-image layout for svm parts
    svm_subdir = join('lib', 'svm')
    native_image_layout_dists(svm_subdir, librarySupportDistribution)
    native_image_layout_dists(join(svm_subdir, 'builder'), svmDistribution + llvmDistributions + ['substratevm:POINTSTO', 'substratevm:OBJECTFILE'])
    clibraries_dest = join(native_image_root, join(svm_subdir, 'clibraries'))
    for clibrary_path in clibrary_paths():
        copy_tree(clibrary_path, clibraries_dest)

def truffle_language_ensure(language_flag, version=None, native_image_root=None, early_exit=False, extract=True):
    """
    Ensures that we have a valid suite for the given language_flag, by downloading a binary if necessary
    and providing the suite distribution artifacts in the native-image directory hierachy (via symlinks).
    :param language_flag: native-image language_flag whose truffle-language we want to use
    :param version: if not specified and no TRUFFLE_<LANG>_VERSION set latest binary deployed master revision gets downloaded
    :param native_image_root: the native_image_root directory where the the artifacts get installed to
    :return: language suite for the given language_flag
    """
    if not native_image_root:
        native_image_root = suite_native_image_root()

    version_env_var = 'TRUFFLE_' + language_flag.upper() + '_VERSION'
    if not version and os.environ.has_key(version_env_var):
        version = os.environ[version_env_var]

    if language_flag not in flag_suitename_map:
        mx.abort('No truffle-language uses language_flag \'' + language_flag + '\'')

    language_dir = join('languages', language_flag)
    if early_exit and exists(join(native_image_root, language_dir)):
        mx.logv('Early exit mode: Language subdir \'' + language_flag + '\' exists. Skip suite.import_suite.')
        return None

    language_entry = flag_suitename_map[language_flag]

    language_suite_name = language_entry[0]
    language_repo_name = language_entry[3] if len(language_entry) > 3 else None

    urlinfos = [
        mx.SuiteImportURLInfo(mx_urlrewrites.rewriteurl('https://curio.ssw.jku.at/nexus/content/repositories/snapshots'),
                              'binary',
                              mx.vc_system('binary'))
    ]

    failure_warning = None
    if not version and not mx.suite(language_suite_name, fatalIfMissing=False):
        # If no specific version requested use binary import of last recently deployed master version
        repo_suite_name = language_repo_name if language_repo_name else language_suite_name
        repo_url = mx_urlrewrites.rewriteurl('https://github.com/graalvm/{0}.git'.format(repo_suite_name))
        version = mx.SuiteImport.resolve_git_branchref(repo_url, 'binary', abortOnError=False)
        if not version:
            failure_warning = 'Resolving \'binary\' against ' + repo_url + ' failed'

    language_suite = suite.import_suite(
        language_suite_name,
        version=version,
        urlinfos=urlinfos,
        kind=None,
        in_subdir=bool(language_repo_name)
    )

    if not language_suite:
        if failure_warning:
            mx.warn(failure_warning)
        mx.abort('Binary suite not found and no local copy of ' + language_suite_name + ' available.')

    if not extract:
        if not exists(join(native_image_root, language_dir)):
            mx.abort('Language subdir \'' + language_flag + '\' should already exist with extract=False')
        return language_suite

    language_suite_depnames = language_entry[1]
    language_deps = language_suite.dists + language_suite.libs
    language_deps = [dep for dep in language_deps if dep.name in language_suite_depnames]
    native_image_layout(language_deps, language_dir, native_image_root)

    language_suite_nativedistnames = language_entry[2]
    language_nativedists = [dist for dist in language_suite.dists if dist.name in language_suite_nativedistnames]
    native_image_extract(language_nativedists, language_dir, native_image_root)

    option_properties = join(language_suite.mxDir, 'native-image.properties')
    target_path = remove_existing_symlink(join(native_image_root, language_dir, 'native-image.properties'))
    if exists(option_properties):
        if not exists(target_path):
            mx.logv('Add symlink to ' + str(option_properties))
            symlink_or_copy(option_properties, target_path)
    else:
        native_image_option_properties('languages', language_flag, native_image_root)
    return language_suite

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
    'test',
    'benchmarktest'
])

@contextmanager
def native_image_context(common_args=None, hosted_assertions=True, native_image_cmd=''):
    common_args = [] if common_args is None else common_args
    base_args = ['-H:+EnforceMaxRuntimeCompileMethods']
    base_args += ['-H:Path=' + svmbuild_dir()]
    if mx.get_opts().verbose:
        base_args += ['--verbose']
    if mx.get_opts().very_verbose:
        base_args += ['--verbose-server']
    if hosted_assertions:
        base_args += native_image_context.hosted_assertions
    if native_image_cmd:
        if not exists(native_image_cmd):
            mx.abort('Given native_image_cmd does not exist')
    else:
        native_image_cmd = native_image_path(suite_native_image_root())

    if exists(native_image_cmd):
        mx.log('Use ' + native_image_cmd + ' for remaining image builds')
        def _native_image(args, **kwargs):
            mx.run([native_image_cmd] + args, **kwargs)
    else:
        _native_image = native_image_on_jvm

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
        _native_image(all_args, **kwargs)
        return image
    try:
        if exists(native_image_cmd):
            _native_image(['--server-wipe'])
        yield native_image_func
    finally:
        if exists(native_image_cmd):
            _native_image(['--server-shutdown'])

native_image_context.hosted_assertions = ['-J-ea', '-J-esa']

def svm_gate_body(args, tasks):
    with Task('Build native-image image', tasks, tags=[GraalTags.build, GraalTags.helloworld]) as t:
        if t: build_native_image_image()
    with native_image_context(IMAGE_ASSERTION_FLAGS) as native_image:
        with Task('image demos', tasks, tags=[GraalTags.helloworld]) as t:
            if t:
                javac_image(['--output-path', svmbuild_dir()])
                javac_command = ' '.join(javac_image_command(svmbuild_dir()))
                helloworld(['--output-path', svmbuild_dir(), '--javac-command', javac_command])
                if mx.get_os() != 'windows':  # building shared libs on Windows currently not working (GR-13594)
                    helloworld(['--output-path', svmbuild_dir(), '--shared']) # Building and running helloworld as shared library
                    cinterfacetutorial([])

        with Task('native unittests', tasks, tags=[GraalTags.test]) as t:
            if t:
                native_unittest([])

        with Task('JavaScript', tasks, tags=[GraalTags.js]) as t:
            if t:
                js = build_js(native_image)
                test_run([js, '-e', 'print("hello:" + Array.from(new Array(10), (x,i) => i*i ).join("|"))'], 'hello:0|1|4|9|16|25|36|49|64|81\n')
                test_js(js, [('octane-richards', 1000, 100, 300)])

    with Task('maven plugin checks', tasks, tags=[GraalTags.maven]) as t:
        if t:
            maven_plugin_install(["--deploy-dependencies"])
            maven_plugin_test([])


@mx.command(suite.name, 'buildlibgraal')
def build_libgraal_cli(args):
    build_libgraal(args)


def build_libgraal(image_args):
    if mx.get_os() == 'windows':
        return 'libgraal is unsupported on Windows'

    graal_hotspot_library = mx.dependency('substratevm:GRAAL_HOTSPOT_LIBRARY', fatalIfMissing=False)
    if not graal_hotspot_library:
        return 'libgraal dependency substratevm:GRAAL_HOTSPOT_LIBRARY is missing'

    libgraal_args = ['-H:Name=libjvmcicompiler', '--shared', '-cp', graal_hotspot_library.classpath_repr(),
        '--features=com.oracle.svm.graal.hotspot.libgraal.HotSpotGraalLibraryFeature',
        '--tool:truffle',
        '-H:-UseServiceLoaderFeature',
        '-H:+AllowFoldMethods',
        '-Djdk.vm.ci.services.aot=true']

    native_image_on_jvm(libgraal_args + image_args)

    return None


def libgraal_gate_body(args, tasks):
    with Task('Build libgraal', tasks, tags=[GraalTags.build, GraalTags.benchmarktest, GraalTags.test]) as t:
        if t:
            # Build libgraal with assertions in the image builder and assertions in the image
            msg = build_libgraal(['-J-esa', '-ea'])
            if msg:
                mx.logv('Skipping libgraal because: {}'.format(msg))
                return

            extra_vm_argument = ['-XX:+UseJVMCICompiler', '-XX:+UseJVMCINativeLibrary', '-XX:JVMCILibPath=' + os.getcwd()]
            if args.extra_vm_argument:
                extra_vm_argument += args.extra_vm_argument

            mx_compiler.compiler_gate_benchmark_runner(tasks, extra_vm_argument, libgraal=True)

mx_gate.add_gate_runner(suite, libgraal_gate_body)

def javac_image_command(javac_path):
    return [join(javac_path, 'javac'), "-proc:none", "-bootclasspath",
            join(mx_compiler.jdk.home, "jre", "lib", "rt.jar")]


def _native_junit(native_image, unittest_args, build_args=None, run_args=None, blacklist=None, whitelist=None):
    unittest_args = unittest_args
    build_args = build_args or []
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
        unittest_image = native_image(build_args + extra_image_args + ['--tool:junit=' + unittest_file, '-H:Path=' + junit_tmp_dir])
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
    all_args = ['--build-args', '--run-args', '--blacklist', '--whitelist']
    cmdline_args = [_mask(arg, all_args) for arg in cmdline_args]
    parser.add_argument(all_args[0], metavar='ARG', nargs='*', default=[])
    parser.add_argument(all_args[1], metavar='ARG', nargs='*', default=[])
    parser.add_argument('--blacklist', help='run all testcases not specified in <file>', metavar='<file>')
    parser.add_argument('--whitelist', help='run testcases specified in <file> only', metavar='<file>')
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
    _native_junit(native_image, unittest_args, unmask(pargs.build_args), unmask(pargs.run_args), blacklist, whitelist)


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
    truffle_language_ensure('js')
    return native_image(['--language:js'])

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
    cSourceDir = join(tutorial_proj.dir, 'native')
    buildDir = join(svmbuild_dir(), tutorial_proj.name, 'build')

    # clean / create output directory
    if exists(buildDir):
        remove_tree(buildDir)
    mkpath(buildDir)

    # Build the shared library from Java code
    native_image(['--shared', '-H:Path=' + buildDir, '-H:Name=libcinterfacetutorial',
                  '-H:CLibraryPath=' + tutorial_proj.dir, '-cp', tutorial_proj.output_dir()] + args)

    # Build the C executable
    mx.run(['cc', '-g', join(cSourceDir, 'cinterfacetutorial.c'),
            '-I' + buildDir,
            '-L' + buildDir, '-lcinterfacetutorial',
            '-ldl', '-Wl,-rpath,' + buildDir,
            '-o', join(buildDir, 'cinterfacetutorial')])

    # Start the C executable
    mx.run([buildDir + '/cinterfacetutorial'])


def _helloworld(native_image, javac_command, path, args):
    mkpath(path)
    hello_file = os.path.join(path, 'HelloWorld.java')
    output = 'Hello from native-image!'
    with open(hello_file, 'w') as fp:
        fp.write('public class HelloWorld { public static void main(String[] args) { System.out.println("' + output + '"); } }')
        fp.flush()
    mx.run(javac_command + [hello_file])

    native_image(["-H:Path=" + path, '-cp', path, 'HelloWorld'] + args)

    expected_output = [output + os.linesep]
    actual_output = []
    def _collector(x):
        actual_output.append(x)
        mx.log(x)

    if '--shared' in args:
        # If helloword got built into a shared library we use python to load the shared library and call its `run_main`.  We are
        # capturing the stdout during the call into an unnamed pipe so that we can use it in the actual vs. expected check below.
        try:
            import ctypes
            so_name = mx.add_lib_suffix('helloworld')
            lib = ctypes.CDLL(join(path, so_name))
            stdout = os.dup(1) # save original stdout
            pout, pin = os.pipe()
            os.dup2(pin, 1) # connect stdout to pipe
            lib.run_main(1, 'dummy') # call run_main of shared lib
            call_stdout = os.read(pout, 120) # get pipe contents
            actual_output.append(call_stdout)
            os.dup2(stdout, 1) # restore original stdout
            mx.log("Stdout from calling run_main in shared object " + so_name)
            mx.log(call_stdout)
        finally:
            os.close(pin)
            os.close(pout)
    else:
        mx.run([join(path, 'helloworld')], out=_collector)

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
    if '--jsvm=substratevm' in args:
        truffle_language_ensure('js')
    return orig_command_benchmark(args)

def mx_post_parse_cmd_line(opts):
    for dist in suite.dists:
        if not dist.isTARDistribution():
            dist.set_archiveparticipant(GraalArchiveParticipant(dist, isTest=dist.name.endswith('_TEST')))

def native_image_context_run(func, func_args=None):
    func_args = [] if func_args is None else func_args
    with native_image_context() as native_image:
        func(native_image, func_args)

def pom_from_template(proj_dir, svmVersion):
    # Create native-image-maven-plugin pom with correct version info from template
    dom = parse(join(proj_dir, 'pom_template.xml'))
    for svmVersionElement in dom.getElementsByTagName('svmVersion'):
        svmVersionElement.parentNode.replaceChild(dom.createTextNode(svmVersion), svmVersionElement)
    with open(join(proj_dir, 'pom.xml'), 'wb') as pom_file:
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


mx_sdk.register_graalvm_component(mx_sdk.GraalVmJreComponent(
    suite=suite,
    name='SubstrateVM',
    short_name='svm',
    license_files=[],
    third_party_license_files=[],
    jar_distributions=['substratevm:LIBRARY_SUPPORT'],
    builder_jar_distributions=[
        'substratevm:SVM',
        'substratevm:OBJECTFILE',
        'substratevm:POINTSTO',
    ],
    support_distributions=['substratevm:SVM_GRAALVM_SUPPORT'],
    launcher_configs=[
        mx_sdk.LauncherConfig(
            destination="bin/native-image",
            jar_distributions=["substratevm:SVM_DRIVER"],
            main_class="com.oracle.svm.driver.NativeImage",
            build_args=[
                "-H:-ParseRuntimeOptions",
            ]
        )
    ],
))


mx_sdk.register_graalvm_component(mx_sdk.GraalVmJreComponent(
    suite=suite,
    name='Polyglot.Native',
    short_name='polynative',
    dir_name='polyglot',
    license_files=[],
    third_party_license_files=[],
    jar_distributions=['substratevm:POLYGLOT_NATIVE_API'],
    support_distributions=[
        "substratevm:POLYGLOT_NATIVE_API_SUPPORT",
        "substratevm:POLYGLOT_NATIVE_API_HEADERS",
    ],
    polyglot_lib_build_args=[
        "--tool:truffle",
        "-H:Features=org.graalvm.polyglot.nativeapi.PolyglotNativeAPIFeature",
        "-Dorg.graalvm.polyglot.nativeapi.libraryPath=<path:POLYGLOT_NATIVE_API_HEADERS>",
        "-Dorg.graalvm.polyglot.nativeapi.nativeLibraryPath=<path:POLYGLOT_NATIVE_API_SUPPORT>",
        "-H:CStandard=C11",
        "-H:+SpawnIsolates",
    ],
    polyglot_lib_jar_dependencies=[
        "substratevm:POLYGLOT_NATIVE_API",
    ],
    polyglot_lib_build_dependencies=[
        "substratevm:POLYGLOT_NATIVE_API_SUPPORT",
        "substratevm:POLYGLOT_NATIVE_API_HEADERS"
    ],
    has_polyglot_lib_entrypoints=True,
))

if os.environ.has_key('NATIVE_IMAGE_TESTING'):
    mx_sdk.register_graalvm_component(mx_sdk.GraalVmTool(
        suite=suite,
        name='Native Image JUnit',
        short_name='nju',
        dir_name='junit',
        license_files=[],
        third_party_license_files=[],
        truffle_jars=[],
        builder_jar_distributions=['mx:JUNIT_TOOL', 'mx:JUNIT', 'mx:HAMCREST'],
        support_distributions=['substratevm:NATIVE_IMAGE_JUNIT_SUPPORT'],
        include_in_polyglot=False,
    ))


@mx.command(suite_name=suite.name, command_name='helloworld', usage_msg='[options]')
def helloworld(args):
    """
    builds a Hello, World! native image.
    """
    parser = ArgumentParser(prog='mx helloworld')
    all_args = ['--output-path', '--javac-command']
    masked_args = [_mask(arg, all_args) for arg in args]
    parser.add_argument(all_args[0], metavar='<output-path>', nargs=1, help='Path of the generated image', default=[svmbuild_dir(suite)])
    parser.add_argument(all_args[1], metavar='<javac-command>', help='A javac command to be used', default=mx.get_jdk().javac)
    parser.add_argument('image_args', nargs='*', default=[])
    parsed = parser.parse_args(masked_args)
    javac_command = unmask(parsed.javac_command.split())
    output_path = unmask(parsed.output_path)[0]
    native_image_context_run(
        lambda native_image, a:
            _helloworld(native_image, javac_command, output_path, a), unmask(parsed.image_args)
    )


@mx.command(suite.name, 'cinterfacetutorial', 'Runs the ')
def cinterfacetutorial(args):
    """
    runs all tutorials for the C interface.
    """
    native_image_context_run(_cinterfacetutorial, args)


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

    for version_tag in GRAAL_COMPILER_FLAGS_MAP:
        update_if_needed(version_tag, GRAAL_COMPILER_FLAGS_BASE + GRAAL_COMPILER_FLAGS_MAP[version_tag])

    orig_command_build(args, vm)


@mx.command(suite.name, 'native-image')
def native_image_on_jvm(args, **kwargs):
    save_args = []
    for arg in args:
        if arg == '--no-server' or arg.startswith('--server'):
            mx.warn('Ignoring server-mode native-image argument ' + arg)
        else:
            save_args.append(arg)

    driver_cp = [join(suite_native_image_root(), 'lib', subdir, '*.jar') for subdir in ['boot', 'jvmci', 'graalvm']]
    driver_cp += [join(suite_native_image_root(), 'lib', 'svm', tail) for tail in ['*.jar', join('builder', '*.jar')]]
    driver_cp = list(itertools.chain.from_iterable(glob.glob(cp) for cp in driver_cp))

    svm_version = suite.release_version(snapshotSuffix='SNAPSHOT')
    run_java([
        '-Dorg.graalvm.version=' + svm_version,
        '-Dnative-image.root=' + suite_native_image_root(),
        '-cp', os.pathsep.join(driver_cp),
        mx.dependency('substratevm:SVM_DRIVER').mainClass] + save_args, **kwargs)


@mx.command(suite.name, 'native-unittest')
def native_unittest(args):
    """builds a native image of JUnit tests and runs them."""
    native_image_context_run(_native_unittest, args)


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
        mx.maven_deploy(deploy_args)

    deploy_native_image_maven_plugin(svm_version, repo, parsed.gpg, parsed.gpg_keyid)

    success_message = [
        '',
        'Use the following plugin snippet to enable native-image building for your maven project:',
        '',
        '<plugin>',
        '    <groupId>com.oracle.substratevm</groupId>',
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
    mx.run_maven(['package'], cwd=proj_dir)
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
            _javac_image(native_image, output_path, command_args), unmask(parsed.image_args)
    )
