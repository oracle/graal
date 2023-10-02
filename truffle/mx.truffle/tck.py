#
# Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import xml.etree.ElementTree as ET

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
                '-Dpolyglot.engine.Mode=latency',
                # '-Dpolyglot.engine.CompilationFailureAction=Throw', GR-29208
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


def _run_maven(args, repository=None, cwd=None):
    extra_args = ['-Dmaven.repo.local=' + repository] if repository else []
    extra_args.append('-q')
    host, port = _parse_http_proxy(['HTTP_PROXY', 'http_proxy'])
    if host:
        extra_args.append('-DproxyHost=' + host)
    if port:
        extra_args.append('-DproxyPort=' + port)
    host, port = _parse_http_proxy(['HTTPS_PROXY', 'https_proxy'])
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
    return _run([mvn_cmd] + extra_args + args, cwd=cwd)


def _create_pom(target_folder, maven_dependencies, graalvm_version, repository_url):

    def _add_dependency(deps, maven_coordinate):
        dependency = ET.SubElement(deps, 'dependency')
        ET.SubElement(dependency, 'groupId').text = maven_coordinate['groupId']
        ET.SubElement(dependency, 'artifactId').text = maven_coordinate['artifactId']
        version = maven_coordinate['version'] if 'version' in maven_coordinate else graalvm_version
        ET.SubElement(dependency, 'version').text = version
        artifact_type = maven_coordinate.get('type')
        if artifact_type:
            ET.SubElement(dependency, 'type').text = artifact_type

    doc = ET.Element("project", attrib={
        'xmlns': "http://maven.apache.org/POM/4.0.0",
        'xmlns:xsi': "http://www.w3.org/2001/XMLSchema-instance",
        'xsi:schemaLocation': "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
    })
    ET.SubElement(doc, 'modelVersion').text = '4.0.0'
    ET.SubElement(doc, 'groupId').text = 'org.graalvm.truffle'
    ET.SubElement(doc, 'artifactId').text = 'tck-fetch'
    ET.SubElement(doc, 'version').text = '1.0'
    ET.SubElement(doc, 'packaging').text = 'pom'
    if maven_dependencies:
        dependencies = ET.SubElement(doc, 'dependencies')
        for maven_coordinate in maven_dependencies:
            _add_dependency(dependencies, maven_coordinate)
    if repository_url:
        repositories = ET.SubElement(doc, 'repositories')
        repository = ET.SubElement(repositories, 'repository')
        ET.SubElement(repository, 'id').text = 'local-snapshots'
        ET.SubElement(repository, 'name').text = 'Local Snapshot Repository'
        ET.SubElement(repository, 'url').text = repository_url
        snapshots = ET.SubElement(repository, 'snapshots')
        ET.SubElement(snapshots, 'enabled').text = 'true'
    build = ET.SubElement(doc, 'build')
    plugins = ET.SubElement(build, 'plugins')
    plugin = ET.SubElement(plugins, 'plugin')
    ET.SubElement(plugin, 'groupId').text = 'org.apache.maven.plugins'
    ET.SubElement(plugin, 'artifactId').text = 'maven-dependency-plugin'
    ET.SubElement(plugin, "version").text = '3.2.0'
    executions = ET.SubElement(plugin, 'executions')
    execution = ET.SubElement(executions, 'execution')
    ET.SubElement(execution, 'id').text = 'copy-dependencies'
    ET.SubElement(execution, 'phase').text = 'package'
    goals = ET.SubElement(execution, 'goals')
    ET.SubElement(goals, 'goal').text = 'copy-dependencies'
    configuration = ET.SubElement(execution, 'configuration')
    ET.SubElement(configuration, 'outputDirectory').text = os.path.abspath(target_folder)
    pom = os.path.join(target_folder, 'pom.xml')
    ET.ElementTree(doc).write(pom, encoding='utf-8', xml_declaration=True, method="xml")


def _install_maven_dependencies(target_folder, dependencies, graalvm_version, repository):
    _create_pom(target_folder, dependencies, graalvm_version, repository)
    process = _run_maven(['package'], cwd=target_folder)
    ret_code = process.wait()
    if ret_code != 0:
        raise Abort('Failed to install maven dependencies ' + str(dependencies) + ' to ' + target_folder)


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


def _run(args, log_level=False, cwd=None):
    _log(LogLevel.FINE, "exec({0})", ' '.join(args))
    return subprocess.Popen(args, cwd=cwd)


def _run_java(javaHome, mainClass, module_path=None, class_path=None, vmArgs=None, args=None, dbgPort=None):
    if not vmArgs:
        vmArgs = []
    if not args:
        args = []
    if module_path:
        vmArgs.append('-p')
        vmArgs.append(os.pathsep.join(module_path))
    if class_path:
        vmArgs.append('-cp')
        vmArgs.append(os.pathsep.join(class_path))
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


def _find_unit_tests(class_path, pkgs=None):
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
    for path in class_path:
        if zipfile.is_zipfile(path):
            with zipfile.ZipFile(path) as zf:
                for name in zf.namelist():
                    if name.endswith('Test.class'):
                        name = name[:len(name) - 6].replace('/', '.')
                        if includes(name):
                            tests.append(name)
    tests.sort(reverse=True)
    return tests


def _execute_tck_impl(graalvm_home, mode, language_filter, values_filter, tests_filter, module_path, class_path, vm_args, debug_port):
    tests = _find_unit_tests(class_path, pkgs=['com.oracle.truffle.tck.tests'])
    if mode.name == 'default' and not _has_explicit_assertion_option(vm_args):
        vm_args.append('-ea')
    if mode.name == 'default' and not _has_explicit_system_assertion_option(vm_args):
        vm_args.append('-esa')
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
    vm_args = vm_args + ['--add-modules', ','.join(_ROOT_MODULES)]
    p = _run_java(graalvm_home, 'org.junit.runner.JUnitCore', module_path=module_path, class_path=class_path, vmArgs=vm_args, args=tests, dbgPort=debug_port)
    ret_code = p.wait()
    return ret_code


def _has_explicit_assertion_option(vm_args):
    """
        Checks if the vm_args contain any option for enabling or disabling assertions.

        :param vm_args: an iterable containing Java VM args
        """
    for vm_arg in vm_args:
        if vm_arg.startswith('-ea') or vm_arg.startswith('-enableassertions') or vm_arg.startswith('-da') or vm_arg.startswith('-disableassertions'):
            return True
    return False


def _has_explicit_system_assertion_option(vm_args):
    """
        Checks if the vm_args contain any option for enabling or disabling system assertions.

        :param vm_args: an iterable containing Java VM args
        """
    return '-esa' in vm_args or '-enablesystemassertions' in vm_args or '-dsa' in vm_args or '-disablesystemassertions' in vm_args


def execute_tck(graalvm_home, mode=Mode.default(), language_filter=None, values_filter=None, tests_filter=None, module_path=None, class_path=None, vm_args=None, debug_port=None):
    """
    Executes Truffle TCK with given TCK providers and languages using GraalVM installed in graalvm_home

    :param graalvm_home: a path to GraalVM
    :param mode: the TCK mode
    :param language_filter: the language id, limits TCK tests to certain language
    :param values_filter: an iterable of value constructors language ids, limits TCK values to certain language(s)
    :param tests_filter: a substring of TCK test name or an iterable of substrings of TCK test names
    :param module_path: an iterable of paths to add on the Java module-path containing additional languages, instruments and TCK provider(s)
    :param class_path: an iterable of paths to add on the Java class-path containing additional TCK provider(s)
    :param vm_args: an iterable containing additional Java VM args
    :param debug_port: a port the Java VM should listen on for debugger connection
    """
    if not module_path:
        module_path = []
    if not class_path:
        class_path = []
    if not vm_args:
        vm_args = []

    if tests_filter and isinstance(tests_filter, str):
        tests_filter = [tests_filter]

    return _execute_tck_impl(graalvm_home, mode, language_filter, values_filter, tests_filter, module_path, class_path,
                             vm_args if isinstance(vm_args, list) else list(vm_args), debug_port)


def set_log_level(log_level):
    """
    Sets the default log level

    :param LogLevel log_level: the log level to use
    """
    global _log_level
    _log_level = log_level


_MVN_DEPENDENCIES = {
    'RUNTIME': [
        {'groupId': 'org.graalvm.truffle', 'artifactId': 'truffle-runtime'},
    ],
    'TCK': [
        {'groupId': 'org.graalvm.sdk', 'artifactId': 'polyglot-tck'},
        {'groupId': 'org.graalvm.truffle', 'artifactId': 'truffle-tck-common'},
        {'groupId': 'org.graalvm.truffle', 'artifactId': 'truffle-tck-instrumentation'},
    ],
    'TESTS': [
        {'groupId': 'junit', 'artifactId': 'junit', 'version': '4.12'},
        {'groupId': 'org.hamcrest', 'artifactId': 'hamcrest-all', 'version': '1.3'},
        {'groupId': 'org.graalvm.truffle', 'artifactId': 'truffle-tck-tests'},
    ],
}

_ROOT_MODULES = [
    'org.graalvm.polyglot_tck',
    'truffle.tck.common',
]


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
    parser.add_argument('--graalvm-version', type=str, dest='graalvm_version', help='graalvm version used to dowload the graalvm maven artifacts, default is LATEST', default='LATEST', metavar='<version>')
    parser.add_argument('--maven-repository', type=str, dest='maven_repository', help='an explicitly defined Maven repository for fetching GraalVM artifacts. By default, it corresponds to the default repository configured in the Maven command', default=None, metavar='<repository>')
    parser.add_argument('--tck-values', type=str, dest='tck_values', help="language ids of value providers to use, separated by ','", metavar='<value providers>')
    parser.add_argument('-p', '--module-path', type=str, dest='module_path', help='module path containing additional language(s) and TCK provider(s)', metavar='<modulepath>')
    parser.add_argument('-cp', '--class-path', type=str, dest='class_path', help='classpath containing additional TCK provider(s)', metavar='<classpath>')
    parser.add_argument('--artifact', type=str, dest='artifacts',  nargs='+', help='additional maven artifacts to add to a module path. The format is groupid:artifactid:version?:type?. ' +
                        'If version is not specified, the graalvm-version is used. '
                        'If type is not specified, the \'jar\' is assumed.', metavar='<artifact>')

    usage = parser.format_usage().strip()
    if usage.startswith('usage: '):
        usage = usage[len('usage: '):]
    parser.usage = usage + ' [--] [VM options...] [language [mode [test filters...]]]'

    args_before_delimiter = []
    args_after_delimiter = argv[1:]     # remove the script name in argv[0]
    while len(args_after_delimiter) > 0:
        arg = args_after_delimiter.pop(0)
        if arg == '--':
            break
        args_before_delimiter.append(arg)
    if len(args_after_delimiter) == 0:
        # parse all known arguments
        parsed_args, args = parser.parse_known_args(args_before_delimiter)
    else:
        # all arguments before '--' must be recognized
        parsed_args = parser.parse_args(args_before_delimiter)
        args = args_after_delimiter

    global _log_level
    _log_level = parsed_args.log_level
    cache_folder = 'cache'
    modules_folder = os.path.join(cache_folder, 'modules')
    tests_folder = os.path.join(cache_folder, 'tests')
    graalvm_version = parsed_args.graalvm_version
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
        os.mkdir(modules_folder)
        os.mkdir(tests_folder)

        artifacts = _MVN_DEPENDENCIES['RUNTIME'] + _MVN_DEPENDENCIES['TCK']
        if parsed_args.artifacts:
            for maven_coordinate in parsed_args.artifacts:
                coordinate_parts = maven_coordinate.split(':')
                groupId = coordinate_parts[0]
                artifactId = coordinate_parts[1]
                version = coordinate_parts[2] if len(coordinate_parts) > 2 and coordinate_parts[2] else graalvm_version
                type = coordinate_parts[3] if len(coordinate_parts) > 3 and coordinate_parts[3] else 'jar'
                artifacts.append({'groupId': groupId, 'artifactId': artifactId, 'version': version, 'type': type})
        _install_maven_dependencies(modules_folder, artifacts, graalvm_version, parsed_args.maven_repository)
        _install_maven_dependencies(tests_folder, _MVN_DEPENDENCIES['TESTS'], graalvm_version,
                                    parsed_args.maven_repository)

        module_path = [os.path.abspath(modules_folder)]
        if parsed_args.module_path:
            for e in parsed_args.module_path.split(os.pathsep):
                module_path.append(os.path.abspath(e))
        class_path = [os.path.join(tests_folder, p) for p in os.listdir(tests_folder) if p.lower().endswith('.jar')]
        if parsed_args.class_path:
            for e in parsed_args.class_path.split(os.pathsep):
                class_path.append(os.path.abspath(e))
        ret_code = _execute_tck_impl(parsed_args.graalvm_home, mode, language, values, tests_filter, module_path, class_path, vm_args, parsed_args.dbg_port)
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
