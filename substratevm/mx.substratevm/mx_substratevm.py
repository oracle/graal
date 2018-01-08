#
# ----------------------------------------------------------------------------------------------------

# Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
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
import sys
import tempfile
from os.path import join, exists, basename

import re
from contextlib import contextmanager
import time
import threading
import urllib2

import mx
from mx import DisableJavaDebugging
from mx_unittest import unittest, _run_tests, _VMLauncher
import mx_unittest
import mx_gate
from mx_gate import Task
import mx_compiler
from mx_compiler import run_java
from mx_compiler import GraalArchiveParticipant
import mx_subst
import mx_urlrewrites

import shutil
import subprocess
import tarfile

from mx_substratevm_benchmark import run_js, host_vm_tuple, output_processors, rule_snippets # pylint: disable=unused-import

JVM_COMPILER_THREADS = 2 if mx.cpu_count() <= 4 else 4

GRAAL_COMPILER_FLAGS = ['-XX:-UseJVMCIClassLoader', '-XX:+UseJVMCICompiler', '-Dgraal.CompileGraalWithC1Only=false', '-XX:CICompilerCount=' + str(JVM_COMPILER_THREADS),
                        '-Dtruffle.TrustAllTruffleRuntimeProviders=true', # GR-7046
                        '-Dgraal.VerifyGraalGraphs=false', '-Dgraal.VerifyGraalGraphEdges=false', '-Dgraal.VerifyGraalPhasesSize=false', '-Dgraal.VerifyPhases=false']
IMAGE_ASSERTION_FLAGS = ['-H:+VerifyGraalGraphs', '-H:+VerifyGraalGraphEdges', '-H:+VerifyPhases']
GB_MEM_BASE = 4

suite = mx.suite('substratevm')
clibrary_roots = [suite.dir]
svmDistribution = 'substratevm:SVM'
svmNativeDistributions = ['substratevm:SVM_HOSTED_NATIVE']
librarySupportDistribution = 'substratevm:LIBRARY_SUPPORT'

def _host_os_supported():
    return mx.get_os() == 'linux' or mx.get_os() == 'darwin'

def _unittest_config_participant(config):
    vmArgs, mainClass, mainClassArgs = config
    # Run the VM in a mode where application/test classes can
    # access JVMCI loaded classes.
    vmArgs = GRAAL_COMPILER_FLAGS + vmArgs
    return (vmArgs, mainClass, mainClassArgs)

mx_unittest.add_config_participant(_unittest_config_participant)

def _classpath(args):
    return mx.classpath(args, jdk=mx_compiler.jdk)

class ProcessRunner(object):
    def __init__(self, cmd, cwd=None, stdout=subprocess.PIPE, stderr=subprocess.PIPE):
        self.cmd = cmd
        self.cwd = cwd
        self.process = None
        self.stdoutdata = None
        self.stderrdata = None
        self.timedout = False
        self.stdout = stdout
        self.stderr = stderr

    def run(self, timeout):
        def target():
            self.process = subprocess.Popen(self.cmd, stdout=self.stdout, stderr=self.stderr, cwd=self.cwd)
            self.stdoutdata, self.stderrdata = self.process.communicate()
        thread = threading.Thread(target=target)
        thread.start()
        thread.join(timeout)
        if thread.is_alive():
            self.timedout = True
            self.process.terminate()
            thread.join()
        return self.process.returncode, self.stdoutdata, self.stderrdata

def ensure_trufflelanguage(language, version):
    '''
    Ensures that we have a valid suite for "language", by downloading a binary if necessary.
    Takes account "version" if not "None".
    Sets "session_language[language]" to the mx suite and returns it.
    '''
    session_language = {}
    for _language in ['truffleruby', 'graal-js', 'sulong', 'graalpython', 'fastr']:
        session_language[_language] = mx.suite(_language, fatalIfMissing=False)
    mx.logv('Session languages: {}'.format(session_language))

    if session_language[language]:
        mx.log('Reusing ' + language + '.version=' + str(session_language[language].version()))
        return session_language[language]

    if os.path.exists(os.path.join("..", "..", language)) or os.path.exists(os.path.join("..", "..", "main", "mx."+ language)):
        language_suite = suite.import_suite(language)
    else:
        urlinfos = [
            mx.SuiteImportURLInfo(mx_urlrewrites.rewriteurl('https://curio.ssw.jku.at/nexus/content/repositories/snapshots'),
                                  'binary',
                                  mx.vc_system('binary'))
        ]

        if not version:
            # If no specific version requested use binary import of last recently deployed master version
            version = 'git-bref:binary'
            urlinfos.append(
                mx.SuiteImportURLInfo(
                    mx_urlrewrites.rewriteurl('https://github.com/graalvm/{0}.git'.format(language)),
                    'source',
                    mx.vc_system('git')
                )
            )

        try:
            language_suite = suite.import_suite(
                language,
                version=version,
                urlinfos=urlinfos,
                kind=None
            )
        except (urllib2.URLError, SystemExit):
            language_suite = suite.import_suite(language)
            if language_suite:
                if version and session_language[language] and version != session_language[language].version():
                    mx.abort('Cannot switch to ' + language +'.version=' + str(version) + ' without maven access.')
                else:
                    mx.log('No maven access. Using already downloaded ' + language + ' binary suite.')
            else:
                mx.abort('No maven access and no local copy of ' + language + ' binary suite available.')

    if not language_suite:
        mx.abort('Binary suite not found and no local copy of ' + language + ' available.')

    session_language[language] = language_suite
    return session_language[language]

def locale_US_args():
    return ['-Duser.country=US', '-Duser.language=en']

def server_args(args, port):  # with assertions disabled we compile without the server
    return args if mx._opts.strip_jars or "-H:-HostedAssertions" in args or port == -1 else args + ['-server', '-port=' + str(port)]

class Tags(set):
    def __getattr__(self, name):
        if name in self:
            return name
        raise AttributeError

GraalTags = Tags([
    'helloworld',
    'js',
    'ruby',
    'sulong',
    'python',
])

def image_server(port, args, dumpArgs, **kwargs):
    image(server_args(args, port), dumpArgs, **kwargs)

def svm_gate_body(args, tasks, port=-1):
    with Task('image helloworld', tasks, tags=[GraalTags.helloworld]) as t:
        if t: helloworld()

    with Task('JavaScript', tasks, tags=[GraalTags.js]) as t:
        if t:
            image_server(port, IMAGE_ASSERTION_FLAGS + ['-js'], True)
            test_run([join('svmbuild', 'js'), '-e', 'print("hello:" + Array.from(new Array(10), (x,i) => i*i ).join("|"))'], 'hello:0|1|4|9|16|25|36|49|64|81\n')
            test_js(suite.dir, [('octane-richards', 1000, 100, 300)])
    with Task('Ruby', tasks, tags=[GraalTags.ruby]) as t:
        if t:
            image(['-Djava.library.path=/lib64', '--', '-ruby'] + IMAGE_ASSERTION_FLAGS, True)
            test_ruby([join(os.getcwd(), 'svmbuild', 'ruby'), 'release'])
    with Task('Python', tasks, tags=[GraalTags.python]) as t:
        if t:
            gate_build_python_image(port, [])
            test_python_smoke([join(os.getcwd(), 'svmbuild', 'python')])
    gate_sulong(port, tasks, [])

def gate_build_python_image(port, args):
    if port:
        image_server(port, IMAGE_ASSERTION_FLAGS + ['-python'] + args, True)
    else:
        image(IMAGE_ASSERTION_FLAGS + ['-python'] + args, True)

def gate_build_sulong_image(port, args):
    if port:
        image_server(port, IMAGE_ASSERTION_FLAGS + ['-sulong', '-H:MaxRuntimeCompileMethods=10000', '-H:-HostedAssertions'] + args, True)
    else:
        image(IMAGE_ASSERTION_FLAGS + ['-sulong', '-H:MaxRuntimeCompileMethods=10000', '-H:-HostedAssertions'] + args, True)

def gate_sulong(port, tasks, args):
    with Task('Run SulongSuite tests with SVM image', tasks, tags=[GraalTags.sulong]) as t:
        if t:
            gate_build_sulong_image(port, args)
            libpath = mx_subst.path_substitutions.substitute('-Dpolyglot.llvm.libraryPath=<path:sulong:SULONG_LIBS>')
            unittest(['-Dsulongtest.testAOTImage=./svmbuild/sulong', '-Dsulongtest.testAOTArgs=' + libpath, 'SulongSuite', '--enable-timing'])
    with Task('Run Sulong interop tests with SVM image', tasks, tags=[GraalTags.sulong]) as t:
        if t:
            ensure_trufflelanguage('sulong', os.environ.get('TRUFFLE_SULONG_VERSION'))
            image_server(port, ['-nfi', '-junit', 'com.oracle.truffle.llvm.test.interop', '-H:-HostedAssertions'] + args, True)
            libpath = mx_subst.path_substitutions.substitute('-Dpolyglot.llvm.libraryPath=<path:sulong:SULONG_LIBS>:<path:sulong:SULONG_TEST_NATIVE>')
            mx.run(['./svmbuild/svmjunit', libpath, '-Dpolyglot.llvm.libraries=libsulongtest.so', '--enable-timing'])

def js_image_test(binary, bench_location, name, warmup_iterations, iterations, timeout=None):
    jsruncmd = [binary, join(bench_location, 'harness.js'), '--',
                join(bench_location, name + '.js'), '--', '--warmup-iterations=' + str(warmup_iterations),
                '--iterations=' + str(iterations)]
    mx.log(' '.join(jsruncmd))

    passing = []
    runner = ProcessRunner(jsruncmd, cwd=bench_location)
    returncode, stdoutdata, stderrdata = runner.run(timeout)
    print('INFO: ' + name + ' stdout:')
    sys.stdout.write(stdoutdata)
    print('INFO: ' + name + ' stderr:')
    sys.stderr.write(stderrdata)

    if runner.timedout:
        print('INFO: TIMEOUT (> %d): %s' % (timeout, name))
    elif returncode >= 0:
        lines = stdoutdata.splitlines()
        matches = 0
        for line in lines:
            if re.match(r'^\S+: *\d+(\.\d+)?\s*$', line):
                matches += 1
        if matches > 0:
            passing = lines

    if not passing:
        mx.abort('JS benchmark ' + name + ' failed')

def test_js(suite_dir, benchmarks):
    bench_location = join(suite.dir, '..', '..', 'js-benchmarks')
    for benchmark_name, warmup_iterations, iterations, timeout in benchmarks:
        js_image_test(join(suite_dir, 'svmbuild', 'js'), bench_location, benchmark_name, warmup_iterations, iterations, timeout)

def test_run(cmds, expected_stdout, timeout=10, msg_prefix='INFO: '):
    runner = ProcessRunner(cmds)
    returncode, stdoutdata, stderrdata = runner.run(timeout=timeout)
    print(msg_prefix + 'stdout:')
    sys.stdout.write(stdoutdata)
    if stdoutdata != expected_stdout:
        print(msg_prefix + 'expected_stdout:')
        sys.stdout.write(expected_stdout)
        mx.abort('Error: stdout does not match expected_stdout')
    return (returncode, stdoutdata, stderrdata)

def test_python_smoke(args):
    """
    Just a smoke test for now.
    """
    if len(args) != 1:
        mx.abort('mx svm_test_python <python_svm_image_path>')

    out = mx.OutputCapture()
    expected_output = "Hello from Python"
    with tempfile.NamedTemporaryFile() as f:
        f.write("print('%s')\n" % expected_output)
        f.flush()
        exitcode = mx.run([args[0], f.name], nonZeroIsFatal=False, out=out)
        if exitcode != 0:
            mx.abort("Python binary failed to execute: " + out.data)
        if out.data != expected_output + "\n":
            mx.abort("Python smoke test failed")
        mx.log("Python binary says: " + out.data)

def test_ruby(args):
    if len(args) < 1 or len(args) > 2:
        mx.abort('mx svm_test_ruby <ruby_svm_image_path> [<debug_build>=release]')

    aot_bin = args[0]
    debug_build = args[1] if len(args) >= 2 else 'release'

    ruby_version = None
    if not ruby_version and os.environ.has_key('TRUFFLE_RUBY_VERSION'):
        ruby_version = os.environ['TRUFFLE_RUBY_VERSION']
    truffleruby_suite = ensure_trufflelanguage('truffleruby', ruby_version)

    suite_dir = truffleruby_suite.dir
    distsToExtract = ['TRUFFLERUBY-ZIP', 'TRUFFLERUBY-SPECS']
    lib = join(suite_dir, 'lib')
    if not exists(lib):
        # Binary suite, extract the distributions
        for dist_name in distsToExtract:
            mx.log('Extract distribution {} to {}'.format(dist_name, suite_dir))
            dist = mx.distribution(dist_name)
            with tarfile.open(dist.path, 'r:') as archive:
                archive.extractall(suite_dir)

    mx.command_function('ruby_testdownstream_aot')([aot_bin, 'spec', debug_build])

def _svm_gate_body_server(args, tasks):
    with compile_server(["-H:+HostedAssertions"]) as port:
        svm_gate_body(args, tasks, port)

mx_gate.add_gate_runner(suite, _svm_gate_body_server)

def _substratevm_clibrary_path():
    clibrary_subdir = join('clibraries', mx.get_os() + "-" + mx.get_arch())
    return ','.join((join(root, clibrary_subdir) for root in clibrary_roots))

def _parse_standard_arguments(defaultAssertionsEnabled, args):
    hostedAssertions = defaultAssertionsEnabled
    i = 0
    while i < len(args):
        if args[i] == '-H:+HostedAssertions':
            hostedAssertions = True
            args.pop(i)
        elif args[i] == '-H:-HostedAssertions':
            hostedAssertions = False
            args.pop(i)
        else:
            i = i + 1

    vmArgs = []
    normalArgs = []

    if hostedAssertions:
        vmArgs += ['-ea', '-esa']
        normalArgs = ['-H:+VerifyNamingConventions'] + normalArgs
    else:
        vmArgs += ['-da', '-dsa']

    vmArgs += ['-Dsubstratevm.version=' + suite.version()]
    normalArgs += ['-H:CLibraryPath=' + _substratevm_clibrary_path()]

    # SVM supports only the en_US locale
    vmArgs += locale_US_args()
    return (vmArgs, normalArgs)


def _extract_projects_from_arguments(args):
    projects = []
    i = 0
    while i < len(args):
        if args[i].startswith('-H:Projects='):
            projects += args.pop(i)[12:].split(',')
        i += 1
    return projects

def processImageArguments(args):
    """
    Computes the projects, classpath, and extra arguments from command line arguments.
    :param args: Command line arguments.
    :return: (project list, classpath, extra arguments, extra VM arguments)
    """
    projects = _extract_projects_from_arguments(args)
    classpathArgument = ''
    extraNormalArgs = []
    extraVMArgs = []

    buildjs = False
    js_version = None

    buildruby = False
    ruby_version = None

    buildsulong = False
    sulong_version = None

    buildpython = False
    python_version = None

    features = set()
    cleanup = []

    required_memory_gb = 0
    number_of_languages = 0
    i = 0
    while i < len(args):
        if args[i].startswith('-D'):
            extraVMArgs += [args.pop(i)]

        if args[i] == '-js':
            args.pop(i)
            projects += ['graal-js:GRAALJS', 'graal-js:GRAALJS_LAUNCHER']
            extraNormalArgs += [
                '-R:YoungGenerationSize=1g', '-R:OldGenerationSize=3g',
                '-H:MaxRuntimeCompileMethods=8000',
                '-H:Class=com.oracle.truffle.js.shell.JSLauncher', '-H:Name=js']
            buildjs = True
            number_of_languages += 1
            required_memory_gb = 3

        elif args[i].startswith('-js.version='):
            js_version = args.pop(i)[12:]

        elif args[i] == '-sl':
            args.pop(i)
            projects += ['truffle:TRUFFLE_SL', 'truffle:TRUFFLE_SL_LAUNCHER']
            extraNormalArgs += ['-H:Class=com.oracle.truffle.sl.launcher.SLMain', '-H:Name=sl']
            number_of_languages += 1

        elif args[i] == '-ruby':
            args.pop(i)
            if not ruby_version and os.environ.has_key('TRUFFLE_RUBY_VERSION'):
                ruby_version = os.environ['TRUFFLE_RUBY_VERSION']
            # Keep in sync with OptionHandlerRuby
            projects += ['truffleruby:TRUFFLERUBY', 'truffleruby:TRUFFLERUBY-LAUNCHER']
            features.add('com.oracle.svm.truffle.nfi.TruffleNFIFeature')
            extraNormalArgs += ['-R:YoungGenerationSize=1g', '-R:OldGenerationSize=2g',
                                '-H:MaxRuntimeCompileMethods=11000',
                                '-H:+AddAllCharsets',
                                '-H:SubstitutionResources=org/truffleruby/aot/substitutions.json',
                                '-H:Class=org.truffleruby.Main',
                                '-H:Name=ruby']
            buildruby = True
            number_of_languages += 1
            required_memory_gb = 6

        elif args[i] == '-sulong':
            args.pop(i)
            if not sulong_version and os.environ.has_key('TRUFFLE_SULONG_VERSION'):
                sulong_version = os.environ['TRUFFLE_SULONG_VERSION']
            sulong_suite = ensure_trufflelanguage('sulong', sulong_version)
            projects += ['sulong:SULONG', 'SVM']
            features.add('com.oracle.svm.truffle.nfi.TruffleNFIFeature')
            extraNormalArgs += ['-H:Class=com.oracle.truffle.llvm.Sulong', '-H:Name=sulong']
            buildsulong = True
            number_of_languages += 1
            required_memory_gb = 3

        elif args[i] == '-python':
            args.pop(i)
            if not python_version and os.environ.has_key('TRUFFLE_PYTHON_VERSION'):
                python_version = os.environ['TRUFFLE_PYTHON_VERSION']
            if not sulong_version and os.environ.has_key('TRUFFLE_SULONG_VERSION'):
                sulong_version = os.environ['TRUFFLE_SULONG_VERSION']
            sulong_suite = ensure_trufflelanguage('sulong', sulong_version)
            python_suite = ensure_trufflelanguage('graalpython', python_version)
            projects += ['graalpython:GRAALPYTHON']
            features.add('com.oracle.svm.truffle.nfi.TruffleNFIFeature')
            extraNormalArgs += [
                '-H:MaxRuntimeCompileMethods=12000',
                '-H:Class=com.oracle.graal.python.shell.GraalPythonMain',
                '-H:SubstitutionResources=com/oracle/graal/python/aot/substitutions.json',
                '-H:Name=python']
            buildpython = True
            number_of_languages += 1
            required_memory_gb = 4

        elif args[i] == '-nfi':
            args.pop(i)
            features.add('com.oracle.svm.truffle.nfi.TruffleNFIFeature')

        elif args[i] == '-junit':
            args.pop(i)
            features.add('com.oracle.svm.junit.JUnitFeature')

            testArgs = []
            while i < len(args) and not args[i].startswith('-'):
                testArgs += [args.pop(i)]

            (_, testfile) = tempfile.mkstemp(".testclasses", "mxsvm")
            os.close(_)
            cleanup += [lambda: os.remove(testfile)]

            def dummyHarness(unittestDeps, vmLauncher, vmArgs):
                projects.extend(unittestDeps)
                extraNormalArgs.extend(vmArgs)

            _run_tests(testArgs, dummyHarness, _VMLauncher('dummy launcher', None, mx_compiler.jdk), ['@Test', '@Parameters'], testfile, None, None, None, None)
            extraNormalArgs += ['-H:Class=com.oracle.svm.junit.SVMJUnitRunner', '-H:Name=svmjunit', '-H:TestFile=' + testfile]

        elif args[i] == '-cp':
            args.pop(i)
            classpathArgument = ':' + args.pop(i)

        else:
            i += 1

    if number_of_languages > 1:
        extraVMArgs += ["-Xmx" + str(GB_MEM_BASE + number_of_languages) + "g"]
    elif required_memory_gb != 0 and number_of_languages == 1:
        extraVMArgs += ["-Xmx" + str(required_memory_gb) + "g"]

    extraNormalArgs += ['-H:Features=' + ','.join(features)]

    if buildjs:
        if not js_version and os.environ.has_key('TRUFFLE_JS_VERSION'):
            js_version = os.environ['TRUFFLE_JS_VERSION']
        js_suite = ensure_trufflelanguage('graal-js', js_version)
        mx.log('Building image with js.version=' + str(js_suite.version()))

    if buildruby:
        truffleruby_suite = ensure_trufflelanguage('truffleruby', ruby_version)
        mx.log('Building image with truffleruby.version=' + str(truffleruby_suite.version()))

    if buildsulong:
        mx.log('Building image with sulong.version=' + str(sulong_suite.version()))

    if buildpython:
        mx.log('Building image with python.version=' + str(python_suite.version()))

    if buildjs or buildruby or buildsulong or buildpython:
        extraVMArgs += ['-Dgraalvm.version=dev', "-Dorg.graalvm.launcher.home="]

    return (projects, classpathArgument, extraNormalArgs, extraVMArgs, cleanup)


def doImage(args, projects, imageVmArgs, imageNormalArgs, extraClassPath="", timeout=None):
    projects += [svmDistribution]

    if imageVmArgs is None:
        imageVmArgs = []

    if imageNormalArgs is None:
        imageNormalArgs = []

    extraVmArgs, extraNormalArgs = _parse_standard_arguments(False, args)

    server, port = processServerArguments(args)

    compilerClasspath = _classpath([svmDistribution])
    cpVmArgs = mx.get_runtime_jvm_args([librarySupportDistribution] + projects, cp_suffix=extraClassPath, jdk=mx_compiler.jdk)
    (idx, classpath) = mx.find_classpath_arg(cpVmArgs)
    imageVmArgs += cpVmArgs[:idx-1] + cpVmArgs[idx+1:]

    classpathlist = list(set(classpath.split(os.pathsep)))
    classpath = os.pathsep.join(classpathlist)

    vmArgs, normalArgs = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
    imageGenArgs = extraNormalArgs + imageNormalArgs + normalArgs
    if mx._opts.strip_jars:
        imageGenArgs += ['-H:-VerifyNamingConventions']

    run_executable(server, port, imageVmArgs + extraVmArgs + vmArgs, 'com.oracle.svm.hosted.NativeImageGeneratorRunner', compilerClasspath, classpath, imageGenArgs, timeout=timeout)

def run_executable(server, port, vmArgs, task, compilerClasspath, imageClasspath, imageGenArgs, timeout=None, **kwargs):
    if server:
        return build_in_server(task, port, vmArgs, imageClasspath, imageGenArgs)
    else:
        allArgs = ['-Xss10m', '-Xms2G'] + GRAAL_COMPILER_FLAGS + vmArgs + ['-cp', compilerClasspath, task, '-imagecp', imageClasspath] + imageGenArgs
        return run_java(allArgs, timeout=timeout, **kwargs)

def build_in_server(task, port, vmArgs, classpath, imageGenArgs):
    props = [vmArg for vmArg in vmArgs if vmArg.startswith('-D')]
    return _image_server_client(port, 'build', ['-imagecp', classpath, '-task=' + task] + imageGenArgs + props)

def image(args, dumpArgs=False, timeout=None, **kwargs):
    """Generate a SubstrateVM image
       Options:
           -H:Projects=<project dirs>  comma separated list of projects on the class path
           -H:[+|-]HostedAssertions    generate image with asserts [enabled|disabled]
       For other -H: options, please run `mx image -H:+PrintFlags'
       For other -R: options, please run `mx image -R:+PrintFlags'"""

    if dumpArgs:
        print(' '.join(['image'] + args))

    (projects, classpathArgument, additionalExtraNormalArgs, additionalVMArgs, cleanup) = processImageArguments(args)

    try:
        doImage(args, projects, imageVmArgs=additionalVMArgs, imageNormalArgs=additionalExtraNormalArgs, extraClassPath=classpathArgument, timeout=timeout, **kwargs)
    finally:
        for c in cleanup:
            c()

SERVER_PORT_NUMBER = 26681
SERVER_LOG = os.path.join(suite.dir, 'native_image_server.log')
PORT_PREFIX = '-port='
def image_server_start(args, dumpArgs=False, timeout=None):
    """Run an SVM image build server on a defined port. If the port is not defined, the server is started on the
     default port 26681. In case a server is already running command will not start a new server. This is
     a convenience for downstream projects so they can always run `image_server_start` in their builds to
     obviate the need for developers to keep track of their servers.

       Options:
           -port=<port_number> port of the build server
    """

    if dumpArgs:
        print(' '.join(['image_server_start'] + args))

    port = extractPortNumber(args)
    running = image_server_running(port)
    if running:
        print("Native image build server is already running.")
    else:
        classpath = _classpath([svmDistribution])
        extraVmArgs, extraNormalArgs = _parse_standard_arguments(False, args)
        vmArgs, normalArgs = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
        noTruffleRuntimeVmArgs = [arg for arg in vmArgs + extraVmArgs if not arg.startswith('-Dtruffle.TruffleRuntime=')]

        run_java(['-Xss10m', '-Xms2G'] + GRAAL_COMPILER_FLAGS + ['-cp', classpath] + noTruffleRuntimeVmArgs + [
                'com.oracle.svm.hosted.server.NativeImageBuildServer', PORT_PREFIX + str(port)] + normalArgs + extraNormalArgs, timeout=timeout)


def _image_server_client(port, command, arguments, out=sys.stdout, err=sys.stderr):
    client_classpath = _classpath([svmDistribution])
    client_command = ['-cp', client_classpath, 'com.oracle.svm.hosted.server.NativeImageBuildClient', PORT_PREFIX + str(port)]
    run_java(client_command + ['-command=' + command] + arguments, out=out, err=err)

def image_server_version(port):
    """
    Returns the version string of the SVM server running on port.
    """
    out = mx.LinesOutputCapture()
    with DisableJavaDebugging():
        _image_server_client(port, 'version', [], out=out)
    return None if len(out.lines) == 0 else out.lines[0]


def image_server_running(port):
    """Returns True if the SVM image build server is running on the given port."""
    return not image_server_version(port) is None


def _image_server_stop(args, dumpArgs=False):
    """Stops the image build server. If port is not defined, the default port 26681 is used.

       Options:
           -port=<port_number> port of the image build server
    """
    if dumpArgs:
        print(' '.join(['image_server_stop'] + args))

    port = extractPortNumber(args)
    return _image_server_client(port, 'stop', [])

@contextmanager
def compile_server(server_args):
    def start_gate_svm_server(args, err=sys.stderr):
        if os.path.exists(SERVER_LOG):
            os.remove(SERVER_LOG)
        args = [join(os.environ["MX_HOME"], "mx"), '-p', suite.dir, 'image_server_start', '-port=' + str(0), '-logFile=' + SERVER_LOG] + args
        mx.log("Starting image build server with: \n" + " ".join(args))
        p = subprocess.Popen(args, stdout=subprocess.PIPE, stderr=err, preexec_fn=os.setsid)
        server_process_entry = mx._addSubprocess(p, args)

        # wait for the server to start
        retries = 20
        port = None
        wait_time_s = 1
        retrying_msg = "Retrying in " + str(wait_time_s) + " second..."
        while not port or not image_server_running(port):
            time.sleep(wait_time_s)
            if not port and exists(SERVER_LOG):
                try:
                    with open(SERVER_LOG, 'r') as log_file:
                        _, port_string, last = log_file.readline(), log_file.readline(), log_file.readline()
                        if last.strip() == 'Accepting requests...':
                            port = int(port_string.strip()) # port is printed on the second line
                        else:
                            mx.log("The image build server log exists but the server did not start yet.")
                            mx.log(retrying_msg)
                except ValueError:
                    mx.abort("The image build server did not start properly. Was the project built correctly?")
            else:
                mx.log("The image build server did not create a log file at: " + SERVER_LOG)
                mx.log(retrying_msg)

            retries -= 1
            if retries is 0:
                mx.abort('The image build server does not respond to requests')
        print('Started image build server on port ' + str(port))
        return server_process_entry, port

    serverEntry, port = start_gate_svm_server(server_args)
    try:
        yield port
    except SystemExit as e:
        print("Execution in server has encountered an error: " + str(e))
        raise e
    finally:
        if image_server_running(port):
            _image_server_stop(['-port=' + str(port)])
            serverEntry[0].wait()
            print('Stopped the image build server that was running on port ' + str(port))
            mx._removeSubprocess(serverEntry)


def extractPortNumber(args):
    port = SERVER_PORT_NUMBER
    i = 0
    while i < len(args):
        if args[i].startswith(PORT_PREFIX):
            port = int(args.pop(i)[len(PORT_PREFIX):])
        else:
            i += 1
    return port


class SVMImageProject(mx.Project):
    class Type:
        EXECUTABLE = 0
        SHARED_LIBRARY = 1

    def __init__(self, suite, name, deps, workingSets, theLicense=None, **kwargs):
        super(SVMImageProject, self).__init__(suite, name, "", [], deps, workingSets, suite.dir, theLicense)
        context = 'SVM image ' + name
        self.main_class = kwargs.pop('main', None)
        self.type = SVMImageProject.Type.EXECUTABLE if self.main_class else SVMImageProject.Type.SHARED_LIBRARY
        self.binary_name = kwargs.pop('binaryName', None)
        build_deps = kwargs.pop('buildDependencies', [])
        self.buildDependencies = build_deps + [librarySupportDistribution, svmDistribution] + svmNativeDistributions
        self.features = mx.Suite._pop_list(kwargs, "features", context)
        self.image_builder_jvm_args = mx.Suite._pop_list(kwargs, "image_builder_jvm_args", context)
        self.image_builder_args = mx.Suite._pop_list(kwargs, "image_builder_args", context)
        self.build_args = mx.Suite._pop_list(kwargs, "build_args", context)
        self.jni_config = kwargs.pop('jni_config', None)
        self.jni_config_dist = None
        self.jni_config_file = None
        self.jni_config_path = None
        if self.jni_config: # format: [[suite:]distribution:]jni_config_file
            if ':' in self.jni_config:
                _jni_config = self.jni_config.split(':')
                self.jni_config_dist = ':'.join(_jni_config[:-1])
                self.jni_config_file = ':'.join(_jni_config[-1:])
                self.jni_config_path = join(self.get_output_root(), self.jni_config_file)
            else:
                self.jni_config_path = join(suite.dir, self.jni_config)

    def getBuildTask(self, args):
        return SVMImageBuildTask(args, self)

    def get_image_builder_binary_name(self):
        if self.type == SVMImageProject.Type.SHARED_LIBRARY:
            return mx.add_lib_prefix(self.binary_name)
        elif self.type == SVMImageProject.Type.EXECUTABLE:
            return self.binary_name
        else:
            mx.abort("Unsupported type: " + str(self.type))

    def get_output_binary_name(self):
        if self.type == SVMImageProject.Type.SHARED_LIBRARY:
            return mx.add_lib_suffix(self.get_image_builder_binary_name())
        elif self.type == SVMImageProject.Type.EXECUTABLE:
            return mx.exe_suffix(self.get_image_builder_binary_name())
        else:
            mx.abort("Unsupported type: " + str(self.type))

    def image_file(self):
        return join(self.get_output_root(), self.get_output_binary_name())

    def output_files(self):
        files = [self.image_file()]
        if self.type == SVMImageProject.Type.SHARED_LIBRARY:
            files.append(join(self.get_output_root(), self.get_image_builder_binary_name() + ".h"))

        return files

    def get_image_builder_jvm_args(self):
        return self.image_builder_jvm_args

    def get_image_builder_args(self):
        return self.image_builder_args


class SVMImageBuildTask(mx.ProjectBuildTask):
    def __init__(self, args, project):
        # We choose 8 as a rough approximation of the best parallelism level required. 8 is convenient because:
        #  (1) laptops don't have more than 8 cores so developers will only have 1 process running
        #  (2) small-to-mid-sized images exhibit roughly that parallelism level (JIT not taken into account)
        #  (3) on 32 core machines it shows best results in terms of image build time in GraalVM
        super(SVMImageBuildTask, self).__init__(args, min(8, mx.cpu_count()), project)
        self._newestOutput = None

    def __str__(self):
        return "Building SVM image {} ({})".format(self.subject.name, basename(self.subject.image_file()))

    def _get_image_command(self):
        command = list(self.subject.get_image_builder_jvm_args())
        command += ['--']
        command += ['-H:Path=' + self.subject.get_output_root()]
        command += ['-H:Name=' + self.subject.get_image_builder_binary_name()]

        def _dep_name(dep):
            if dep.isProject():
                return dep.name
            else:
                return dep.suite.name + ':' + dep.name

        command += ['-H:Projects=' + ','.join((_dep_name(d) for d in self.subject.deps))]
        if self.subject.build_args:
            command += self.subject.build_args
        if self.subject.jni_config_dist:
            tarfilepath = mx.distribution(self.subject.jni_config_dist).path
            with tarfile.open(tarfilepath, 'r:') as tar:
                mx.logv("Extracting jni config file '{}' from '{}' to '{}'".format(self.subject.jni_config_file, tarfilepath, self.subject.get_output_root()))
                tar.extract(self.subject.jni_config_file, self.subject.get_output_root())
        if self.subject.jni_config_path:
            command += ['-H:JNIConfigurationFiles=' + self.subject.jni_config_path]
        if self.subject.features:
            command += ['-H:Features=' + ','.join(self.subject.features)]
        if self.subject.type == SVMImageProject.Type.SHARED_LIBRARY:
            command += ['-H:Kind=SHARED_LIBRARY']
        elif self.subject.type == SVMImageProject.Type.EXECUTABLE:
            command += ['-H:Class=' + self.subject.main_class]
            command += ['-H:Kind=EXECUTABLE']
        else:
            mx.abort("Unsupported type: " + str(self.subject.type))
        command += list(self.subject.get_image_builder_args())
        return command

    def build(self):
        command = self._get_image_command()
        mx.logv("image " + ' '.join(command))
        original_command = list(command)  # image might mutate the command
        image(command)
        with open(self._get_command_file(), 'w') as f:
            f.writelines((l + os.linesep for l in original_command))

    def clean(self, forBuild=False):
        output_file = self.subject.image_file()
        if exists(output_file):
            os.unlink(output_file)

    def needsBuild(self, newestInput):
        witness = self.newestOutput()
        if not self._newestOutput or witness.isNewerThan(self._newestOutput):
            self._newestOutput = witness
        if not witness.exists():
            return True, witness.path + ' does not exist'
        if newestInput and witness.isOlderThan(newestInput):
            return True, '{} is older than {}'.format(witness, newestInput)
        if self.subject.jni_config_dist and witness.isOlderThan(mx.distribution(self.subject.jni_config_dist).path):
            return True, '{} is older than {}'.format(witness, mx.distribution(self.subject.jni_config_dist).path)
        if self.subject.jni_config_path and witness.isOlderThan(self.subject.jni_config_path):
            return True, '{} is older than {}'.format(witness, self.subject.jni_config_path)

        previous_command = []
        command_file = self._get_command_file()
        if exists(command_file):
            with open(command_file) as f:
                previous_command = [l.rstrip('\r\n') for l in f.readlines()]
        if previous_command != self._get_image_command():
            return True, 'image command changed'
        return False, 'output is up to date'

    def _get_command_file(self):
        return self.subject.image_file() + ".cmd"

    def newestOutput(self):
        return mx.TimeStampFile(self.subject.image_file())


def build(orig_command_build, args, vm):
    if any([opt in args for opt in ['-h', '--help']]):
        orig_command_build(args, vm)

    mx.log('build: Checking SubstrateVM requirements for building ...')

    if not _host_os_supported():
        mx.abort('build: SubstrateVM can be built only on Darwin and Linux platforms')

    orig_command_build(args, vm)

def cinterfacetutorial(args):
    """Build and run the tutorial for the C interface"""

    tutorialSuite = mx.suite('substratevm')
    cSourceDir = join(tutorialSuite.dir, 'src', 'com.oracle.svm.tutorial', 'native')
    buildDir = join(tutorialSuite.dir, 'svmbuild', 'com.oracle.svm.tutorial', 'build')

    # clean / create output directory
    if exists(buildDir):
        shutil.rmtree(buildDir)
    os.makedirs(buildDir)

    # Build the shared library from Java code
    image(IMAGE_ASSERTION_FLAGS + [
           '-H:Kind=SHARED_LIBRARY', '-H:Path=' + buildDir, '-H:Name=libcinterfacetutorial',
           '-H:Projects=com.oracle.svm.tutorial'] + args)

    # Build the C executable
    mx.run(['cc', '-g', join(cSourceDir, 'cinterfacetutorial.c'),
            '-I' + buildDir,
            '-L' + buildDir, '-lcinterfacetutorial',
            '-ldl', '-Wl,-rpath,' + buildDir,
            '-o', join(buildDir, 'cinterfacetutorial')])

    # Start the C executable
    mx.run([buildDir + '/cinterfacetutorial'])

def ensurePathExists(path):
    if not exists(path):
        os.makedirs(path)
    return path

def processServerArguments(args):
    server = False
    i = 0
    while i < len(args):
        if args[i].startswith('-server'):
            server = True
            args.pop(i)
        else:
            i += 1
    return server, extractPortNumber(args)

def build_native_image(args):
    native_image_name = 'native-image'
    truffle_jline_path = _classpath(['truffle:JLINE'])
    shutil.copy(truffle_jline_path, 'lib')
    image(['-Dgraalvm.version=dev', '-H:Projects=com.oracle.svm.driver', '-H:Class=com.oracle.svm.driver.NativeImage', '-H:Name=' + native_image_name, '-H:-TruffleFeature', '-H:-ParseRuntimeOptions'])
    mx.log('\nBuilt native-image tool to ' + join('svmbuild', native_image_name))

def helloworld(args=None):
    if args is None:
        args = []
    helloPath = join(suite.dir, 'svmbuild')
    ensurePathExists(helloPath)

    # Build an image for the javac compiler, so that we test and gate-check javac all the time.
    # Dynamic class loading code is reachable (used by the annotation processor), so -H:+ReportUnsupportedElementsAtRuntime is a necessary option
    image(['-cp', mx_compiler.jdk.toolsjar, "-H:Path=" + helloPath, "-H:Class=com.sun.tools.javac.Main", "-H:Name=javac",
           "-H:+ReportUnsupportedElementsAtRuntime", "-H:IncludeResourceBundles=com.sun.tools.javac.resources.compiler,com.sun.tools.javac.resources.javac,com.sun.tools.javac.resources.version"] + args)

    helloFile = join(helloPath, 'HelloWorld.java')
    output = 'Hello from Substrate VM'
    with open(helloFile, 'w') as fp:
        fp.write('public class HelloWorld { public static void main(String[] args) { System.out.println("' + output + '"); } }')

    # Run the image for javac. Annotation processing must be disabled because it requires dynamic class loading,
    # and we need to set the bootclasspath manually because our build directory does not contain any .jar files.
    mx.run([join(helloPath, 'javac'), "-proc:none", "-bootclasspath", join(mx_compiler.jdk.home, "jre", "lib", "rt.jar"), helloFile])

    image(['-cp', helloPath, "-H:Path=" + helloPath, '-H:Class=HelloWorld', '-H:Name=helloworld'] + args)

    expectedOutput = [output + '\n']
    actualOutput = []
    def _collector(x):
        actualOutput.append(x)

    mx.run([join(helloPath, 'helloworld')], out=_collector)

    if actualOutput != expectedOutput:
        raise Exception('Wrong output: ' + str(actualOutput) + "  !=  " + str(expectedOutput))

orig_command_benchmark = mx.command_function('benchmark')
def benchmark(args):
    if '--jsvm=substratevm' in args:
        ensure_trufflelanguage('graal-js', None)
    orig_command_benchmark(args)

def mx_post_parse_cmd_line(opts):
    for dist in suite.dists:
        if not dist.isTARDistribution():
            dist.set_archiveparticipant(GraalArchiveParticipant(dist, isTest=dist.name.endswith('_TEST')))

orig_command_build = mx.command_function('build')
mx.update_commands(suite, {
    'image_server_start' : [image_server_start, ''],
    'image_server_stop' : [_image_server_stop, ''],
    'image' : [image, ''],
    'cinterfacetutorial' : [cinterfacetutorial, ''],
    'build': [lambda args, vm=None: build(orig_command_build, args, vm), ''],
    'benchmark': [benchmark, '--vmargs [vmargs] --runargs [runargs] suite:benchname'],
    'helloworld' : [helloworld, ''],
    'build_native_image' : [build_native_image, ''],
})
