import argparse
import os
import os.path
import re
import subprocess
import sys
import zipfile

class Abort(RuntimeError):
    def __init__(self, message, retCode = -1):
        self.message = message
        self.retCode = retCode

class ClassPathEntry:
    def __init__(self, path):
        self.path = path

    def install(self, folder):
        pass

    def __str__(self):
        return self.path

class MvnClassPathEntry(ClassPathEntry):

    def __init__(self, groupId, artifactId, version, repository = None):
        self.repository = repository
        self.groupId = groupId
        self.artifactId = artifactId
        self.version = version
        ClassPathEntry.__init__(self, None)

    def install(self, folder):
        print('Installing {0}'.format(self))
        self.pull()
        install_folder = os.path.join(folder, self.artifactId)
        os.mkdir(install_folder)
        self.copy(install_folder)
        self.path = os.pathsep.join([os.path.join(install_folder, f) for f in os.listdir(install_folder) if f.endswith('.jar')])

    def pull(self):
        process = MvnClassPathEntry._run_maven(['dependency:get', '-DgroupId=' + self.groupId, '-DartifactId=' + self.artifactId, '-Dversion=' + self.version], self.repository)
        ret_code = process.wait()
        if ret_code != 0:
            raise Abort('Cannot download artifact {0} '.format(self))

    def copy(self, folder):
        process = MvnClassPathEntry._run_maven(['dependency:copy', '-Dartifact=' + self.groupId + ':' + self.artifactId + ':' + self.version, '-DoutputDirectory=' + folder], self.repository)
        ret_code = process.wait()
        if ret_code != 0:
            raise Abort('Cannot copy artifact '.format(self))

    def __str__(self):
        return '{0}:{1}:{2}'.format(self.groupId, self.artifactId, self.version)

    @staticmethod
    def _run_maven(args, repository = None):
        extra_args = ['-Dmaven.repo.local=' + repository] if repository else []
        extra_args.append('-q')
        host, port = MvnClassPathEntry._parse_http_proxy(['HTTP_PROXY', 'http_proxy'])
        if host:
            extra_args.append('-DproxyHost=' + host)
        if port:
            extra_args.append('-DproxyPort=' + port)
        host, port = MvnClassPathEntry._parse_http_proxy(['HTTPS_PROXY', 'https_proxy'])
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

def _is_windows():
    return sys.platform.startswith('win32')

def _rmdir_recursive(file):
    if os.path.isdir(file):
        for child in os.listdir(file):
            _rmdir_recursive(os.path.join(file, child))
        os.rmdir(file)
    else:
        os.unlink(file)

def _run(args):
    return subprocess.Popen(args)

def _run_java(javaHome, mainClass, cp = None, truffleCp = None , bootCp = None, vmArgs = [], args = [], dbgPort = None):
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

def _find_unit_tests(cp, pkgs = None):
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

JUNIT = [
    {'groupId':'junit','artifactId':'junit','version':'4.12'},
    {'groupId':'org/hamcrest','artifactId':'hamcrest-all','version':'1.3'}
]

TCK_COMMON = [
    {'groupId':'org.graalvm.sdk','artifactId':'polyglot-tck'},
    {'groupId':'org.graalvm.truffle','artifactId':'truffle-tck-common'}
]

INSTRUMENTS = [
    {'groupId':'org.graalvm.truffle','artifactId':'truffle-tck-instrumentation'},
]


def main(argv):
    parser = argparse.ArgumentParser(description = 'Truffle TCK Runner')
    parser.add_argument('-g', '--graalvm-home', type = str, dest = 'graalvm_home', help = 'GraalVM to execute TCK on.', required = True, metavar = '<graalvm home>')
    parser.add_argument('--dbg', type = int, dest = 'dbg_port', help = 'make TCK tests wait on <port> for a debugger', metavar = '<port>')
    parser.add_argument('-d', action='store_const', const=8000, dest='dbg_port', help='alias for "-dbg 8000"')
    parser.add_argument('--tck-version', type = str, dest = 'tck_version', help = 'maven TCK version, default is LATEST', default = 'LATEST', metavar = '<version>')
    parser.add_argument('-cp','--class-path', type = str, dest = 'class_path', help = 'classpath containing additional TCK provider(s)', metavar = '<classpath>')
    parser.add_argument('-lp','--language-path', type = str, dest = 'truffle_path', help = 'classpath containing additinal language jar(s)', metavar = '<classpath>')
    parser.add_argument('--language', type = str, dest = 'language', help = 'restricts TCK tests to given language', metavar = '<language id>')
    parser.add_argument(dest="test", type=str, nargs="*", help="test filter, the substring of a test name to include", metavar='<test pattern>')
    paresed_args = parser.parse_args()

    cache_folder = 'cache'
    try:
        os.mkdir(cache_folder)
        boot = [MvnClassPathEntry(e['groupId'], e['artifactId'], e['version'] if 'version' in e else paresed_args.tck_version) for e in TCK_COMMON]
        cp = [MvnClassPathEntry(e['groupId'], e['artifactId'], e['version'] if 'version' in e else paresed_args.tck_version) for e in JUNIT]
        truffle_cp = [MvnClassPathEntry(e['groupId'], e['artifactId'], e['version'] if 'version' in e else paresed_args.tck_version) for e in INSTRUMENTS]
        if paresed_args.class_path:
            for e in paresed_args.class_path.split(os.pathsep):
                cp.append(ClassPathEntry(os.path.abspath(e)))
        if paresed_args.truffle_path:
            for e in paresed_args.truffle_path.split(os.pathsep):
                truffle_cp.append(ClassPathEntry(os.path.abspath(e)))
        for entry in boot:
            entry.install(cache_folder)
        for entry in cp:
            entry.install(cache_folder)
        for entry in truffle_cp:
            entry.install(cache_folder)

        vmArgs = []
        if paresed_args.language:
            vmArgs.append('-Dtck.language={0}'.format(paresed_args.language))

        tests = _find_unit_tests(cp, pkgs = ['com.oracle.truffle.tck.tests'])
        if paresed_args.test:
            def includes(test):
                for pattern in paresed_args.test:
                    if test.find(pattern) >= 0:
                        return True
                return False
            tests = [test for test in tests if includes(test)]

        p = _run_java(paresed_args.graalvm_home, 'org.junit.runner.JUnitCore', cp = cp, truffleCp = truffle_cp, bootCp = boot, vmArgs= vmArgs, args = tests, dbgPort = paresed_args.dbg_port)
        ret_code = p.wait()
        sys.exit(ret_code)
    except Abort as abort:
        sys.stderr.write(abort.message)
        sys.stderr.write('\n')
        sys.exit(abort.retCode)
    finally:
        _rmdir_recursive(cache_folder)


if __name__ == '__main__':
    main(sys.argv)
