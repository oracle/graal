#
# Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
from __future__ import print_function

import argparse
import os
import os.path
import re
import subprocess
import sys
import zipfile

class Abort(RuntimeError):
    def __init__(self, message, retCode=-1):
        RuntimeError.__init__(self)
        self.message = message
        self.retCode = retCode

_mode_factories = dict()

def _mode_factory(name):
    def decorator(sm):
        _mode_factories[name] = sm.__func__
        return sm
    return decorator

class Mode:
    _default = None
    _compile = None

    def __init__(self, name, vm_args=None):
        self.name = name
        self.vm_args = vm_args if vm_args else []

    def __str__(self):
        return self.name

    @_mode_factory('default')
    @staticmethod
    def default():
        if not Mode._default:
            Mode._default = Mode('default')
        return Mode._default

    @_mode_factory('compile')
    @staticmethod
    def compile():
        if not Mode._compile:
            Mode._compile = Mode('compile', [
                '-Dpolyglot.engine.AllowExperimentalOptions=true',
                '-Dpolyglot.engine.CompileImmediately=true',
                '-Dpolyglot.engine.BackgroundCompilation=false',
                '-Dtck.inlineVerifierInstrument=false'])
        return Mode._compile

    @staticmethod
    def for_name(name):
        factory = _mode_factories.get(name)
        if factory:
            return factory()
        else:
            raise Abort('Mode must be default or compile')

class LogLevel:
    """
    Log level constants to enable verbose output.
    """
    OFF = 1<<31
    INFO = 800
    FINE = 500

_log_level = LogLevel.INFO

class _ClassPathEntry:
    def __init__(self, path):
        self.path = path

    def install(self, folder):
        pass

    def __str__(self):
        return self.path

class _MvnClassPathEntry(_ClassPathEntry):

    def __init__(self, groupId, artifactId, version, repository=None):
        self.repository = repository
        self.groupId = groupId
        self.artifactId = artifactId
        self.version = version
        _ClassPathEntry.__init__(self, None)

    def install(self, folder):
        _log(LogLevel.INFO, 'Installing {0}'.format(self))
        self.pull()
        install_folder = os.path.join(folder, self.artifactId)
        os.mkdir(install_folder)
        self.copy(install_folder)
        self.path = os.pathsep.join([os.path.join(install_folder, f) for f in os.listdir(install_folder) if f.endswith('.jar')])

    def pull(self):
        process = _MvnClassPathEntry._run_maven(['dependency:get', '-DgroupId=' + self.groupId, '-DartifactId=' + self.artifactId, '-Dversion=' + self.version], self.repository)
        ret_code = process.wait()
        if ret_code != 0:
            raise Abort('Cannot download artifact {0} '.format(self))

    def copy(self, folder):
        process = _MvnClassPathEntry._run_maven(['dependency:copy', '-Dartifact=' + self.groupId + ':' + self.artifactId + ':' + self.version, '-DoutputDirectory=' + folder], self.repository)
        ret_code = process.wait()
        if ret_code != 0:
            raise Abort('Cannot copy artifact {0}'.format(self))

    def __str__(self):
        return '{0}:{1}:{2}'.format(self.groupId, self.artifactId, self.version)

    @staticmethod
    def _run_maven(args, repository=None):
        extra_args = ['-Dmaven.repo.local=' + repository] if repository else []
        extra_args.append('-q')
        host, port = _MvnClassPathEntry._parse_http_proxy(['HTTP_PROXY', 'http_proxy'])
        if host:
            extra_args.append('-DproxyHost=' + host)
        if port:
            extra_args.append('-DproxyPort=' + port)
        host, port = _MvnClassPathEntry._parse_http_proxy(['HTTPS_PROXY', 'https_proxy'])
        if host:
            extra_args.append('-Dhttps.proxyHost=' + host)
        if port:
            extra_args.append('-Dhttps.proxyPort=' + port)
        mvn_cmd = 'mvn'
        mvn_home = os.environ.get('MAVEN_HOME')
        if mvn_home:
            mvn_cmd = os.path.join(mvn_home, 'bin', mvn_cmd)
        if _is_windows():
            mvn_cmd += '.cmd'
            extra_args += ['--batch-mode']
        return _run([mvn_cmd] + extra_args + args)

    @staticmethod
    def _parse_http_proxy(envVarNames):
        p = re.compile(r'(?:https?://)?([^:]+):?(\d+)?/?$')
        for name in envVarNames:
            value = os.environ.get(name)
            if value:
                m = p.match(value)
                if m:
                    return m.group(1), m.group(2)
                else:
                    raise Abort('Value of ' + name + ' is not valid:  ' + value)
        return (None, None)

def _log(level, message, args=None):
    if level != LogLevel.OFF and level >= _log_level:
        print(message.format(args if args else []))

def _is_windows():
    return sys.platform.startswith('win32')

def _rmdir_recursive(to_delete):
    if os.path.isdir(to_delete):
        for child in os.listdir(to_delete):
            _rmdir_recursive(os.path.join(to_delete, child))
        os.rmdir(to_delete)
    else:
        os.unlink(to_delete)

def _run(args, log_level=False):
    _log(LogLevel.FINE, "exec({0})", ', '.join(['"' + a + '"' for a in args]))
    return subprocess.Popen(args)

def _run_java(javaHome, mainClass, cp=None, truffleCp=None, bootCp=None, vmArgs=None, args=None, dbgPort=None):
    if not vmArgs:
        vmArgs = []
    if not args:
        args = []
    if cp:
        vmArgs.append('-cp')
        vmArgs.append(os.pathsep.join([e.path for e in cp]))
    if truffleCp:
        vmArgs.append('-Dtruffle.class.path.append=' + os.pathsep.join([e.path for e in truffleCp]))
    if bootCp:
        vmArgs.append('-Xbootclasspath/a:' + os.pathsep.join([e.path for e in bootCp]))
    java_cmd = os.path.join(javaHome, 'bin', 'java')
    if _is_windows():
        java_cmd += '.exe'
    if dbgPort:
        vmArgs.append('-Xdebug')
        vmArgs.append('-Xrunjdwp:transport=dt_socket,server=y,address={0},suspend=y'.format(dbgPort))
    return _run([java_cmd] + vmArgs + [mainClass] + args)

def _split_VM_args_and_filters(args):
    jvm_space_separated_args = ['-cp', '-classpath', '-mp', '-modulepath', '-limitmods', '-addmods', '-upgrademodulepath', '-m',
                        '--module-path', '--limit-modules', '--add-modules', '--upgrade-module-path',
                        '--module', '--module-source-path', '--add-exports', '--add-reads',
                        '--patch-module', '--boot-class-path', '--source-path']
    for i, e in enumerate(args):
        if not e.startswith('-') and (i == 0 or not args[i - 1] in jvm_space_separated_args):
            return args[:i], args[i:]
    return args, []

def _find_unit_tests(cp, pkgs=None):
    def includes(n):
        if not pkgs:
            return True
        else:
            index = n.rfind('.')
            if index < 0:
                owner = n
            else:
                owner = n[:index]
            for pkg in pkgs:
                if pkg == owner:
                    return True
            return False
    tests = []
    for e in cp:
        path = e.path
        if zipfile.is_zipfile(path):
            with zipfile.ZipFile(path) as zf:
                for name in zf.namelist():
                    if name.endswith('Test.class'):
                        name = name[:len(name) - 6].replace('/', '.')
                        if includes(name):
                            tests.append(name)
    tests.sort(reverse=True)
    return tests

def _execute_tck_impl(graalvm_home, mode, language_filter, values_filter, tests_filter, cp, truffle_cp, boot_cp, vm_args, debug_port):
    tests = _find_unit_tests(cp, pkgs=['com.oracle.truffle.tck.tests'])
    vm_args.extend(mode.vm_args)
    if language_filter:
        vm_args.append('-Dtck.language={0}'.format(language_filter))
    if values_filter:
        vm_args.append('-Dtck.values={0}'.format(','.join(values_filter)))
    if tests_filter:
        def includes(test):
            for pattern in tests_filter:
                if test.find(pattern) >= 0:
                    return True
            return False
        tests = [test for test in tests if includes(test)]
    p = _run_java(graalvm_home, 'org.junit.runner.JUnitCore', cp=cp, truffleCp=truffle_cp, bootCp=boot_cp, vmArgs=vm_args, args=tests, dbgPort=debug_port)
    ret_code = p.wait()
    return ret_code


def execute_tck(graalvm_home, mode=Mode.default(), language_filter=None, values_filter=None, tests_filter=None, cp=None, truffle_cp=None, boot_cp=None, vm_args=None, debug_port=None):
    """
    Executes Truffle TCK with given TCK providers and languages using GraalVM installed in graalvm_home

    :param graalvm_home: a path to GraalVM
    :param mode: the TCK mode
    :param language_filter: the language id, limits TCK tests to certain language
    :param values_filter: an iterable of value constructors language ids, limits TCK values to certain language(s)
    :param tests_filter: a substring of TCK test name or an iterable of substrings of TCK test names
    :param cp: an iterable of paths to add on the Java classpath, the classpath must contain the TCK providers and dependencies
    :param truffle_cp: an iterable of paths to add to truffle.class.path, the additional languages and instruments must be a part of the truffle_cp
    :param boot_cp: an iterable of paths to add to Java boot path
    :param vm_args: an iterable containing additional Java VM args
    :param debug_port: a port the Java VM should listen on for debugger connection
    """
    if not cp:
        cp = []
    if not truffle_cp:
        truffle_cp = []
    if not boot_cp:
        boot_cp = []
    if not vm_args:
        vm_args = []

    if tests_filter and isinstance(tests_filter, str):
        tests_filter = [tests_filter]

    return _execute_tck_impl(graalvm_home, mode, language_filter, values_filter, tests_filter,
        [_ClassPathEntry(os.path.abspath(e)) for e in cp],
        [_ClassPathEntry(os.path.abspath(e)) for e in truffle_cp],
        [_ClassPathEntry(os.path.abspath(e)) for e in boot_cp],
        vm_args if isinstance(vm_args, list) else list(vm_args),
        debug_port)

def set_log_level(log_level):
    """
    Sets the default log level

    :param LogLevel log_level: the log level to use
    """
    global _log_level
    _log_level = log_level

_MVN_DEPENDENCIES = {
    'JUNIT' : [
        {'groupId':'junit', 'artifactId':'junit', 'version':'4.12'},
        {'groupId':'org/hamcrest', 'artifactId':'hamcrest-all', 'version':'1.3'}
    ],
    'TCK' : [
        {'groupId':'org.graalvm.sdk', 'artifactId':'polyglot-tck'},
        {'groupId':'org.graalvm.truffle', 'artifactId':'truffle-tck-common'},
    ],
    'INSTRUMENTS' : [
        {'groupId':'org.graalvm.truffle', 'artifactId':'truffle-tck-instrumentation'},
    ]
}

def _main(argv):

    unittestHelpSuffix = """
    Supported modes are:
        default     executes the test with default GraalVM configuration
        compile     compiles the tests before execution

    If test filters are supplied, only tests whose fully qualified name
    includes a filter as a substring are run.

    For example:

       python tck.py js default ExpressionTest

    will run TCK ExpressionTest for JavaScript language in a default mode.
    """

    parser = argparse.ArgumentParser(description='Truffle TCK Runner',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=unittestHelpSuffix)
    parser.set_defaults(log_level=LogLevel.INFO)
    parser.add_argument('-v', action='store_const', dest='log_level', const=LogLevel.FINE, help='enable log_level output')
    parser.add_argument('-q', action='store_const', dest='log_level', const=LogLevel.OFF, help='quiet output')
    parser.add_argument('-g', '--graalvm-home', type=str, dest='graalvm_home', help='GraalVM to execute TCK on', required=True, metavar='<graalvm home>')
    parser.add_argument('--dbg', type=int, dest='dbg_port', help='make TCK tests wait on <port> for a debugger', metavar='<port>')
    parser.add_argument('-d', action='store_const', const=8000, dest='dbg_port', help='alias for "-dbg 8000"')
    parser.add_argument('--tck-version', type=str, dest='tck_version', help='maven TCK version, default is LATEST', default='LATEST', metavar='<version>')
    parser.add_argument('--tck-values', type=str, dest='tck_values', help="language ids of value providers to use, separated by ','", metavar='<value providers>')
    parser.add_argument('-cp', '--class-path', type=str, dest='class_path', help='classpath containing additional TCK provider(s)', metavar='<classpath>')
    parser.add_argument('-lp', '--language-path', type=str, dest='truffle_path', help='classpath containing additinal language jar(s)', metavar='<classpath>')

    usage = parser.format_usage().strip()
    if usage.startswith('usage: '):
        usage = usage[len('usage: '):]
    parser.usage = usage + ' [VM options...] [language [mode [test filters...]]]'
    parsed_args, args = parser.parse_known_args()

    global _log_level
    _log_level = parsed_args.log_level
    cache_folder = 'cache'
    try:
        vm_args, other_args = _split_VM_args_and_filters(args)
        for pattern in other_args:
            if pattern.startswith('-'):
                raise Abort('VM option {0}  must precede {1}'.format(pattern, other_args[0]))
        language = None
        values = None
        mode = Mode.default()
        tests_filter = []
        if len(other_args) > 0:
            language = other_args[0]
        if len(other_args) > 1:
            mode = Mode.for_name(other_args[1])
        if len(other_args) > 2:
            tests_filter = other_args[2:]
        if parsed_args.tck_values:
            values = parsed_args.tck_values.split(',')
        os.mkdir(cache_folder)
        boot = [_MvnClassPathEntry(e['groupId'], e['artifactId'], e['version'] if 'version' in e else parsed_args.tck_version) for e in _MVN_DEPENDENCIES['TCK']]
        cp = [_MvnClassPathEntry(e['groupId'], e['artifactId'], e['version'] if 'version' in e else parsed_args.tck_version) for e in _MVN_DEPENDENCIES['JUNIT']]
        truffle_cp = [_MvnClassPathEntry(e['groupId'], e['artifactId'], e['version'] if 'version' in e else parsed_args.tck_version) for e in _MVN_DEPENDENCIES['INSTRUMENTS']]
        if parsed_args.class_path:
            for e in parsed_args.class_path.split(os.pathsep):
                cp.append(_ClassPathEntry(os.path.abspath(e)))
        if parsed_args.truffle_path:
            for e in parsed_args.truffle_path.split(os.pathsep):
                truffle_cp.append(_ClassPathEntry(os.path.abspath(e)))
        for entry in boot + cp + truffle_cp:
            entry.install(cache_folder)
        ret_code = _execute_tck_impl(parsed_args.graalvm_home, mode, language, values, tests_filter, cp, truffle_cp, boot, vm_args, parsed_args.dbg_port)
        sys.exit(ret_code)
    except Abort as abort:
        sys.stderr.write(abort.message)
        sys.stderr.write('\n')
        sys.exit(abort.retCode)
    finally:
        if os.path.isdir(cache_folder):
            _rmdir_recursive(cache_folder)

if __name__ == '__main__':
    _main(sys.argv)
