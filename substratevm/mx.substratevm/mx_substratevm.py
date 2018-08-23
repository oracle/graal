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
# { GR-8964
from shutil import copy2
import time
# } GR-8964
import collections
import itertools
import glob
from xml.dom.minidom import parse
from argparse import ArgumentParser

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

GRAAL_COMPILER_FLAGS = ['-Dtruffle.TrustAllTruffleRuntimeProviders=true', # GR-7046
                        ]
if mx.get_jdk(tag='default').javaCompliance <= mx.JavaCompliance('1.8'):
    GRAAL_COMPILER_FLAGS += ['-XX:-UseJVMCIClassLoader']
else:
    # JVMCI access
    GRAAL_COMPILER_FLAGS += ['--add-exports', 'jdk.internal.vm.ci/jdk.vm.ci.runtime=ALL-UNNAMED']
    GRAAL_COMPILER_FLAGS += ['--add-exports', 'jdk.internal.vm.ci/jdk.vm.ci.code=ALL-UNNAMED']
    GRAAL_COMPILER_FLAGS += ['--add-exports', 'jdk.internal.vm.ci/jdk.vm.ci.amd64=ALL-UNNAMED']
    # Reflective access
    GRAAL_COMPILER_FLAGS += ['--add-exports', 'jdk.unsupported/sun.reflect=ALL-UNNAMED']
    # Reflective access to private fields of java.lang.Class.
    GRAAL_COMPILER_FLAGS += ['--add-opens', 'java.base/java.lang=ALL-UNNAMED']
    # Reflective access to resource bundle getContents() methods.
    GRAAL_COMPILER_FLAGS += ['--add-opens', 'java.base/sun.text.resources=ALL-UNNAMED']
    GRAAL_COMPILER_FLAGS += ['--add-opens', 'java.base/sun.util.resources=ALL-UNNAMED']
    # Reflective access to java.util.Bits.words.
    GRAAL_COMPILER_FLAGS += ['--add-opens', 'java.base/java.util=ALL-UNNAMED']
    # Reflective access to java.lang.invoke.VarHandle*.
    GRAAL_COMPILER_FLAGS += ['--add-opens', 'java.base/java.lang.invoke=ALL-UNNAMED']
    # Reflective access to java.lang.Reference.referent.
    GRAAL_COMPILER_FLAGS += ['--add-opens', 'java.base/java.lang.ref=ALL-UNNAMED']
    # Reflective access to org.graalvm.nativeimage.impl.ImageSingletonsSupport.
    GRAAL_COMPILER_FLAGS += ['--add-exports', 'org.graalvm.graal_sdk/org.graalvm.nativeimage.impl=ALL-UNNAMED']
    # Reflective access to jdk.internal.ref.CleanerImpl$PhantomCleanableRef.
    GRAAL_COMPILER_FLAGS += ['--add-opens', 'java.base/jdk.internal.ref=ALL-UNNAMED']
    # Disable the check for JDK-8 graal version.
    GRAAL_COMPILER_FLAGS += ['-Dsubstratevm.IgnoreGraalVersionCheck=true']
    # Reflective access to java.net.URL.getURLStreamHandler.
    GRAAL_COMPILER_FLAGS += ['--add-opens', 'java.base/java.net=ALL-UNNAMED']


IMAGE_ASSERTION_FLAGS = ['-H:+VerifyGraalGraphs', '-H:+VerifyGraalGraphEdges', '-H:+VerifyPhases']
suite = mx.suite('substratevm')
svmSuites = [suite]

orig_command_gate = mx.command_function('gate')
orig_command_build = mx.command_function('build')

gate_run = False

def gate(args):
    global gate_run
    gate_run = True
    orig_command_gate(args)

def build(args, vm=None):
    if any([opt in args for opt in ['-h', '--help']]):
        orig_command_build(args, vm)

    mx.log('build: Checking SubstrateVM requirements for building ...')

    if not _host_os_supported():
        mx.abort('build: SubstrateVM can be built only on Darwin, Linux and Windows platforms')

    orig_command_build(args, vm)

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
    root_dir = join(svmbuild_dir(suite), 'native-image-root')
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

def symlink_or_copy(target_path, dest_path, debug_gr_8964=False):
    # Follow symbolic links in case they go outside my suite directories.
    real_target_path = os.path.realpath(target_path)
    if debug_gr_8964:
        mx.log('  [mx_substratevm.symlink_or_copy:')
        mx.log('    suite.dir:' + suite.dir)
        mx.log('    target_path: ' + target_path)
        mx.log('    real_target_path: ' + real_target_path)
        mx.log('    dest_path: ' + dest_path)
    if any(real_target_path.startswith(s.dir) for s in mx.suites(includeBinary=False)):
        # Symbolic link to files in my suites.
        sym_target = os.path.relpath(real_target_path, dirname(dest_path))
        if debug_gr_8964:
            mx.log('      symlink target: ' + sym_target)
        os.symlink(sym_target, dest_path)
    else:
        # Else copy the file to so it can not change out from under me.
        if debug_gr_8964:
            mx.log('      copy2: ')
        copy2(real_target_path, dest_path)
    if debug_gr_8964:
        mx.log('  ]')

def native_image_layout(dists, subdir, native_image_root, debug_gr_8964=False):
    if not dists:
        return
    dest_path = join(native_image_root, subdir)
    # Cleanup leftovers from previous call
    if exists(dest_path):
        if debug_gr_8964:
            mx.log('[mx_substratevm.native_image_layout: remove_tree: ' + dest_path + ']')
        remove_tree(dest_path)
    mkpath(dest_path)
    # Create symlinks to conform with native-image directory layout scheme
    def symlink_jar(jar_path):
        if debug_gr_8964:
            def log_stat(prefix, file_name):
                file_stat = os.stat(file_name)
                mx.log('    ' + prefix + '.st_mode: ' + oct(file_stat.st_mode))
                mx.log('    ' + prefix + '.st_mtime: ' + time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime(file_stat.st_mtime)))

            dest_jar = join(dest_path, basename(jar_path))
            mx.log('[mx_substratevm.native_image_layout.symlink_jar: symlink_or_copy')
            mx.log('  src: ' + jar_path)
            log_stat('src', jar_path)
            mx.log('  dst: ' + dest_jar)
            symlink_or_copy(jar_path, dest_jar, debug_gr_8964)
            log_stat('dst', dest_jar)
            mx.log(']')
        else:
            symlink_or_copy(jar_path, join(dest_path, basename(jar_path)), debug_gr_8964)

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
    ('llvm', ('sulong', ['SULONG', 'SULONG_LAUNCHER'], ['SULONG_LIBS', 'SULONG_DOC'])),
    ('js', ('graal-js', ['GRAALJS', 'GRAALJS_LAUNCHER', 'ICU4J'], ['ICU4J-DIST'], 'js')),
    ('python', ('graalpython', ['GRAALPYTHON', 'GRAALPYTHON-LAUNCHER', 'GRAALPYTHON-ENV'], ['GRAALPYTHON_GRAALVM_SUPPORT', 'GRAALPYTHON-ZIP'])),
    ('R', ('fastr', ['FASTR', 'XZ-1.6', 'GNU_ICONV', 'GNUR', 'ANTLR-3.5'], ['FASTR_RELEASE']))  # JLINE?
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
    'native-image' : ToolDescriptor(image_deps=['substratevm:SVM_DRIVER']),
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
    run_java(['-Dnative-image.root=' + suite_native_image_root(), '-cp', ":".join(driver_cp),
        mx.dependency('substratevm:SVM_DRIVER').mainClass] + save_args, **kwargs)

def build_native_image_image():
    image_path = native_image_path(suite_native_image_root())
    mx.log('Building native-image executable ' + image_path)
    image_dir = dirname(image_path)
    mkpath(image_dir)
    native_image_on_jvm(['--tool:native-image', '-H:Path=' + image_dir])

svmDistribution = ['substratevm:SVM']
graalDistribution = ['compiler:GRAAL']
librarySupportDistribution = ['substratevm:LIBRARY_SUPPORT']

def layout_native_image_root(native_image_root):

    def names_to_dists(dist_names):
        return [mx.dependency(dist_name) for dist_name in dist_names]

    def native_image_layout_dists(subdir, dist_names):
        native_image_layout(names_to_dists(dist_names), subdir, native_image_root)

    def native_image_extract_dists(subdir, dist_names):
        native_image_extract(names_to_dists(dist_names), subdir, native_image_root)

    # Create native-image layout for sdk parts
    native_image_layout_dists(join('lib', 'boot'), ['sdk:GRAAL_SDK'])
    native_image_layout_dists(join('lib', 'graalvm'), ['substratevm:SVM_DRIVER', 'sdk:LAUNCHER_COMMON'])

    # Create native-image layout for compiler & jvmci parts
    native_image_layout_dists(join('lib', 'jvmci'), graalDistribution)
    jdk_config = mx.get_jdk()
    jvmci_path = join(jdk_config.home, 'jre', 'lib', 'jvmci')
    if os.path.isdir(jvmci_path):
        for symlink_name in os.listdir(jvmci_path):
            symlink_or_copy(join(jvmci_path, symlink_name), join(native_image_root, 'lib', 'jvmci', symlink_name))

    # Create native-image layout for truffle parts
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
    native_image_layout_dists(join(svm_subdir, 'builder'), svmDistribution + ['substratevm:POINTSTO', 'substratevm:OBJECTFILE'])
    for clibrary_path in clibrary_paths():
        copy_tree(clibrary_path, join(native_image_root, join(svm_subdir, 'clibraries')))

def truffle_language_ensure(language_flag, version=None, native_image_root=None, early_exit=False, extract=True, debug_gr_8964=False):
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
    native_image_layout(language_deps, language_dir, native_image_root, debug_gr_8964=debug_gr_8964)

    language_suite_nativedistnames = language_entry[2]
    language_nativedists = [dist for dist in language_suite.dists if dist.name in language_suite_nativedistnames]
    native_image_extract(language_nativedists, language_dir, native_image_root)

    option_properties = join(language_suite.mxDir, 'native-image.properties')
    target_path = remove_existing_symlink(join(native_image_root, language_dir, 'native-image.properties'))
    if exists(option_properties):
        if not exists(target_path):
            mx.logv('Add symlink to ' + str(option_properties))
            symlink_or_copy(option_properties, target_path, debug_gr_8964=debug_gr_8964)
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
    'python',
])

@contextmanager
def native_image_context(common_args=None, hosted_assertions=True, debug_gr_8964=False, native_image_cmd=''):
    common_args = [] if common_args is None else common_args
    base_args = ['-H:+EnforceMaxRuntimeCompileMethods']
    base_args += ['-H:Path=' + svmbuild_dir()]
    if debug_gr_8964:
        base_args += ['-Ddebug_gr_8964=true']
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
    def native_image_func(args, debug_gr_8964=False, **kwargs):
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
    # Debug GR-8964 on Darwin gates
    debug_gr_8964 = (mx.get_os() == 'darwin')
    build_native_image_image()
    with native_image_context(IMAGE_ASSERTION_FLAGS, debug_gr_8964=debug_gr_8964) as native_image:
        with Task('image demos', tasks, tags=[GraalTags.helloworld]) as t:
            if t:
                hello_path = svmbuild_dir()
                javac_image(native_image, hello_path)
                helloworld_internal(native_image, hello_path, javac_image_command(hello_path))
                cinterfacetutorial(native_image)

        with Task('native unittests', tasks, tags=[GraalTags.test]) as t:
            if t:
                native_junit(native_image)

        with Task('JavaScript', tasks, tags=[GraalTags.js]) as t:
            if t:
                js = build_js(native_image, debug_gr_8964=debug_gr_8964)
                test_run([js, '-e', 'print("hello:" + Array.from(new Array(10), (x,i) => i*i ).join("|"))'], 'hello:0|1|4|9|16|25|36|49|64|81\n')
                test_js(js, [('octane-richards', 1000, 100, 300)])

        with Task('Python', tasks, tags=[GraalTags.python]) as t:
            if t:
                python = build_python(native_image, debug_gr_8964=debug_gr_8964)
                test_python_smoke([python])

    with Task('maven plugin checks', tasks, tags=[GraalTags.maven]) as t:
        if t:
            maven_plugin_install([])


def javac_image_command(javac_path):
    return [join(javac_path, 'javac'), "-proc:none", "-bootclasspath",
            join(mx_compiler.jdk.home, "jre", "lib", "rt.jar")]


def native_junit(native_image, unittest_args=None, build_args=None, run_args=None):
    unittest_args = unittest_args or ['com.oracle.svm.test']
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
        _run_tests(unittest_args, dummy_harness, _VMLauncher('dummy_launcher', None, mx_compiler.jdk), ['@Test', '@Parameters'], unittest_file, None, None, None, None)
        if not exists(unittest_file):
            mx.abort('No matching unit tests found. Skip image build and execution.')
        with open(unittest_file, 'r') as f:
            mx.log('Building junit image for matching: ' + ' '.join(l.rstrip() for l in f))
        extra_image_args = mx.get_runtime_jvm_args(unittest_deps, jdk=mx_compiler.jdk)
        unittest_image = native_image(build_args + extra_image_args + ['--tool:junit=' + unittest_file, '-H:Path=' + junit_tmp_dir])
        mx.run([unittest_image] + run_args)
    finally:
        remove_tree(junit_tmp_dir)

def native_unittest(native_image, cmdline_args):
    parser = ArgumentParser(prog='mx native-unittest', description='Run unittests as native image')
    mask_str = '#'
    def mask(arg):
        if arg in ['--', '--build-args', '--run-args', '-h', '--help']:
            return arg
        else:
            return arg.replace('-', mask_str)
    cmdline_args = [mask(arg) for arg in cmdline_args]
    parser.add_argument('--build-args', metavar='ARG', nargs='*', default=[])
    parser.add_argument('--run-args', metavar='ARG', nargs='*', default=[])
    parser.add_argument('unittest_args', metavar='TEST_ARG', nargs='*')
    pargs = parser.parse_args(cmdline_args)
    def unmask(args):
        return [arg.replace(mask_str, '-') for arg in args]
    native_junit(native_image, unmask(pargs.unittest_args), unmask(pargs.build_args), unmask(pargs.run_args))

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

def build_js(native_image, debug_gr_8964=False):
    truffle_language_ensure('js', debug_gr_8964=debug_gr_8964)
    return native_image(['--language:js'], debug_gr_8964=debug_gr_8964)

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

def build_python(native_image, debug_gr_8964=False):
    truffle_language_ensure('llvm', debug_gr_8964=debug_gr_8964) # python depends on sulong
    truffle_language_ensure('python', debug_gr_8964=debug_gr_8964)
    return native_image(['--language:python', 'com.oracle.graal.python.shell.GraalPythonMain', 'python'])

def test_python_smoke(args):
    """
    Just a smoke test for now.
    """
    if len(args) != 1:
        mx.abort('mx svm_test_python <python_svm_image_path>')

    out = mx.OutputCapture()
    err = mx.OutputCapture()
    expected_output = "Hello from Python"
    with tempfile.NamedTemporaryFile() as f:
        f.write("print('%s')\n" % expected_output)
        f.flush()
        os.system("ls -l %s" % args[0])
        os.system("ls -l %s" % f.name)
        exitcode = mx.run([args[0], f.name], nonZeroIsFatal=False, out=out, err=err)
        if exitcode != 0:
            mx.abort("Python binary failed to execute: out=" + out.data+ " err=" + err.data)
        if out.data != expected_output + "\n":
            mx.abort("Python smoke test failed")
        mx.log("Python binary says: " + out.data)

mx_gate.add_gate_runner(suite, svm_gate_body)

def cinterfacetutorial(native_image, args=None):
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

def helloworld(native_image, args=None):
    args = [] if args is None else args
    helloworld_internal(native_image, svmbuild_dir(), ['javac'], args)

def helloworld_internal(native_image, path=svmbuild_dir(), javac_command=None, args=None):
    if javac_command is None:
        javac_command = ['javac']
    args = [] if args is None else args
    mkpath(path)
    hello_file = join(path, 'HelloWorld.java')
    output = 'Hello from Substrate VM'
    with open(hello_file, 'w') as fp:
        fp.write('public class HelloWorld { public static void main(String[] args) { System.out.println("' + output + '"); } }')

    # Run javac. We sometimes run with an image so annotation processing must be disabled because it requires dynamic
    #  class loading, and we need to set the bootclasspath manually because our build directory does not contain any
    # .jar files.
    mx.run(javac_command + [hello_file])

    native_image(["-H:Path=" + path, '-cp', path, 'HelloWorld'] + args)

    expectedOutput = [output + '\n']
    actualOutput = []
    def _collector(x):
        actualOutput.append(x)
        mx.log(x)

    mx.run([join(path, 'helloworld')], out=_collector)

    if actualOutput != expectedOutput:
        raise Exception('Wrong output: ' + str(actualOutput) + "  !=  " + str(expectedOutput))

def javac_image(native_image, path, args=None):
    args = [] if args is None else args
    mkpath(path)

    # Build an image for the javac compiler, so that we test and gate-check javac all the time.
    # Dynamic class loading code is reachable (used by the annotation processor), so -H:+ReportUnsupportedElementsAtRuntime is a necessary option
    native_image(["-H:Path=" + path, '-cp', mx_compiler.jdk.toolsjar, "com.sun.tools.javac.Main", "javac",
                  "-H:+ReportUnsupportedElementsAtRuntime",
                  "-H:IncludeResourceBundles=com.sun.tools.javac.resources.compiler,com.sun.tools.javac.resources.javac,com.sun.tools.javac.resources.version"] + args)

orig_command_benchmark = mx.command_function('benchmark')
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

def fetch_languages(args, early_exit=True):
    if args:
        requested = collections.OrderedDict()
        for arg in args:
            language_flag, version_info = extract_target_name(arg, 'language')
            if language_flag:
                version = version_info.partition('version=')[2] if version_info else None
                requested[language_flag] = version
    else:
        requested = collections.OrderedDict((lang, None) for lang in flag_suitename_map)

    for language_flag in requested:
        version = requested[language_flag]
        truffle_language_ensure(language_flag, version, early_exit=early_exit)

def deploy_native_image_maven_plugin(svmVersion, action='install'):
    # Create native-image-maven-plugin pom with correct version info from template
    proj_dir = join(suite.dir, 'src', 'native-image-maven-plugin')
    dom = parse(join(proj_dir, 'pom_template.xml'))
    for svmVersionElement in dom.getElementsByTagName('svmVersion'):
        svmVersionElement.parentNode.replaceChild(dom.createTextNode(svmVersion), svmVersionElement)
    with open(join(proj_dir, 'pom.xml'), 'wb') as pom_file:
        dom.writexml(pom_file)
    # Build and install native-image-maven-plugin into local repository
    mx.run_maven([action], cwd=proj_dir)

def maven_plugin_install(args):
    # First install native-image-maven-plugin dependencies into local maven repository
    deps = []
    def visit(dep, edge):
        if isinstance(dep, mx.Distribution):
            deps.append(dep)
    mx.walk_deps([mx.dependency('substratevm:SVM_DRIVER')], visit=visit, ignoredEdges=[mx.DEP_ANNOTATION_PROCESSOR, mx.DEP_BUILD])
    svmVersion = '{0}-SNAPSHOT'.format(suite.vc.parent(suite.vc_dir))
    mx.maven_deploy([
        '--version-string', svmVersion,
        '--suppress-javadoc',
        '--all-distributions',
        '--validate=none',
        '--all-suites',
        '--skip-existing',
        '--only', ','.join(dep.qualifiedName() for dep in deps)
    ])

    deploy_native_image_maven_plugin(svmVersion)

    success_message = [
        '',
        'Use the following plugin snippet to enable native-image building for your maven project:',
        '',
        '<plugin>',
        '    <groupId>com.oracle.substratevm</groupId>',
        '    <artifactId>native-image-maven-plugin</artifactId>',
        '    <version>' + svmVersion + '</version>',
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
    support_distributions=['substratevm:POLYGLOT_NATIVE_API_SUPPORT'],
    polyglot_lib_build_args=[
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

mx.update_commands(suite, {
    'gate': [gate, '[options]'],
    'build': [build, ''],
    'helloworld' : [lambda args: native_image_context_run(helloworld, args), ''],
    'cinterfacetutorial' : [lambda args: native_image_context_run(cinterfacetutorial, args), ''],
    'fetch-languages': [lambda args: fetch_languages(args, early_exit=False), ''],
    'benchmark': [benchmark, '--vmargs [vmargs] --runargs [runargs] suite:benchname'],
    'native-image': [native_image_on_jvm, ''],
    'maven-plugin-install': [maven_plugin_install, ''],
    'native-unittest' : [lambda args: native_image_context_run(native_unittest, args), ''],
})
