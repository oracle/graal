import argparse
import os
import os.path
import re
import subprocess
import sys
import zipfile

class LogLevel:
    """
    Log level constants to enable verbose output.
    """
    OFF     = 1<<31
    INFO    = 800
    FINE    = 500

_log_level = LogLevel.INFO

class Abort(RuntimeError):
    def __init__(self, message, retCode=-1):
        self.message = message
        self.retCode = retCode

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
            raise Abort('Cannot copy artifact '.format(self))

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

def _log(level, message, args=[]):
    if level != LogLevel.OFF and level >= _log_level:
        print(message.format(args))

def _is_windows():
    return sys.platform.startswith('win32')

def _rmdir_recursive(file):
    if os.path.isdir(file):
        for child in os.listdir(file):
            _rmdir_recursive(os.path.join(file, child))
        os.rmdir(file)
    else:
        os.unlink(file)

def _run(args, log_level=False):
    _log(LogLevel.FINE, "exec({0})", ', '.join(['"' + a + '"' for a in args]))
    return subprocess.Popen(args)

def _run_java(javaHome, mainClass, cp=None, truffleCp=None , bootCp=None, vmArgs=[], args=[], dbgPort=None):
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
    jvm_space_separated_args = ['-cp','-classpath','-mp', '-modulepath', '-limitmods', '-addmods', '-upgrademodulepath', '-m',
                        '--module-path', '--limit-modules', '--add-modules', '--upgrade-module-path',
                        '--module', '--module-source-path', '--add-exports', '--add-reads',
                        '--patch-module', '--boot-class-path', '--source-path']
    for i, e in enumerate(args):
        if not e.startswith('-') and (i == 0 or not (args[i - 1] in jvm_space_separated_args)):
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
            with zipfile.ZipFile(path) as zip:
                for name in zip.namelist():
                    if name.endswith('Test.class'):
                        name = name[:len(name) - 6].replace('/','.')
                        if includes(name):
                            tests.append(name)
    return tests

def _execute_tck_impl(graalvm_home, cp, truffle_cp, boot_cp, vm_args, tests_filter, language_filter, debug_port):
    tests = _find_unit_tests(cp, pkgs=['com.oracle.truffle.tck.tests'])
    if language_filter:
        vmArgs.append('-Dtck.language={0}'.format(parsed_args.language))
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


def execute_tck(graalvm_home, cp=[], truffle_cp=[], boot_cp=[], vm_args=[], tests_filter=None, language_filter=None, debug_port=None):
    """
    Executes Truffle TCK with given TCK providers and languages using GraalVM installed in graalvm_home

    :param graalvm_home: a path to GraalVM
    :param cp: a list of paths to add on the Java classpath, the classpath must contain the TCK providers and dependencies
    :param truffle_cp: a list of paths to add to truffle.class.path, the additional languages and instruments must be a part of the truffle_cp
    :param boot_cp: list of paths to add to Java boot path
    :param vm_args: list containing additional Java VM args
    :param tests_filter: a substring of TCK test name or a list of substrings of TCK test names
    :param language_filter: the language id, limits TCK tests to certain language
    :param debug_port: a port the Java VM should listen on for debugger connection
    """
    if tests_filter and type(tests_filter) is str:
        tests_filter = [tests_filter]

    return _execute_tck_impl(graalvm_home,
        [_ClassPathEntry(os.path.abspath(e)) for e in cp],
        [_ClassPathEntry(os.path.abspath(e)) for e in truffle_cp],
        [_ClassPathEntry(os.path.abspath(e)) for e in boot_cp],
        vm_args,
        tests_filter,
        language_filter,
        debug_port)

def set_log_level(log_level):
    """
    Sets the default log level

    :param LogLevel log_level: the log level to use
    """
    global _log_level
    _log_level = log_level

_JUNIT = [
    {'groupId':'junit','artifactId':'junit','version':'4.12'},
    {'groupId':'org/hamcrest','artifactId':'hamcrest-all','version':'1.3'}
]

_TCK_COMMON = [
    {'groupId':'org.graalvm.sdk','artifactId':'polyglot-tck'},
    {'groupId':'org.graalvm.truffle','artifactId':'truffle-tck-common'}
]

_INSTRUMENTS = [
    {'groupId':'org.graalvm.truffle','artifactId':'truffle-tck-instrumentation'},
]

def _main(argv):

    unittestHelpSuffix = """
    To avoid conflicts with VM options '--' can be used as delimiter.

    If test filters are supplied, only tests whose fully qualified name
    includes a filter as a substring are run.

    For example:

       python tck.py -Dgraal.TruffleCompileImmediately=true -Dgraal.TruffleBackgroundCompilation=false ExpressionTest

    will run TCK ExpressionTest and will pass -Dgraal.TruffleCompileImmediately=true
    and -Dgraal.TruffleBackgroundCompilation=false options to the Java VM.
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
    parser.add_argument('-cp','--class-path', type=str, dest='class_path', help='classpath containing additional TCK provider(s)', metavar='<classpath>')
    parser.add_argument('-lp','--language-path', type=str, dest='truffle_path', help='classpath containing additinal language jar(s)', metavar='<classpath>')
    parser.add_argument('--language', type=str, dest='language', help='restricts TCK tests to given language', metavar='<language id>')

    usage = parser.format_usage().strip()
    if usage.startswith('usage: '):
        usage = usage[len('usage: '):]
    parser.usage = usage + ' [VM options...] [test filters...]'
    parsed_args, args = parser.parse_known_args()

    global _log_level
    _log_level = parsed_args.log_level
    cache_folder = 'cache'
    try:
        vm_args, tests_filter = _split_VM_args_and_filters(args)
        for pattern in tests_filter:
            if pattern.startswith('-'):
                raise Abort('VM option {0}  must precede {1}'.format(pattern, tests_filter[0]))
        os.mkdir(cache_folder)
        boot = [_MvnClassPathEntry(e['groupId'], e['artifactId'], e['version'] if 'version' in e else parsed_args.tck_version) for e in _TCK_COMMON]
        cp = [_MvnClassPathEntry(e['groupId'], e['artifactId'], e['version'] if 'version' in e else parsed_args.tck_version) for e in _JUNIT]
        truffle_cp = [_MvnClassPathEntry(e['groupId'], e['artifactId'], e['version'] if 'version' in e else parsed_args.tck_version) for e in _INSTRUMENTS]
        if parsed_args.class_path:
            for e in parsed_args.class_path.split(os.pathsep):
                cp.append(_ClassPathEntry(os.path.abspath(e)))
        if parsed_args.truffle_path:
            for e in parsed_args.truffle_path.split(os.pathsep):
                truffle_cp.append(_ClassPathEntry(os.path.abspath(e)))
        for entry in boot + cp + truffle_cp:
            entry.install(cache_folder)
        ret_code = _execute_tck_impl(parsed_args.graalvm_home, cp, truffle_cp, boot, vm_args, tests_filter, parsed_args.language, parsed_args.dbg_port)
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
