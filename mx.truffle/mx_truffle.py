#
# commands.py - the GraalVM specific commands
#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
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

import os, stat, errno, sys, shutil, zipfile, tarfile, tempfile, re, time, datetime, platform, subprocess, socket
from os.path import join, exists, dirname, basename
from argparse import ArgumentParser, RawDescriptionHelpFormatter, REMAINDER
import mx
import xml.dom.minidom
import itertools
import json, textwrap
import fnmatch

# This works because when mx loads this file, it makes sure __file__ gets an absolute path
_graal_home = dirname(dirname(__file__))

""" The VM that will be run by the 'vm' command and built by default by the 'build' command.
    This can be set via the global '--vm' option or the DEFAULT_VM environment variable.
    It can also be temporarily set by using of a VM context manager object in a 'with' statement. """
_vm = None

_make_eclipse_launch = False

_minVersion = mx.VersionSpec('1.7')

# max version (first _unsupported_ version)
_untilVersion = None

class JDKDeployedDist:
    def __init__(self, name, isExtension=False, usesJVMCIClassLoader=False, partOfHotSpot=False):
        self.name = name
        self.isExtension = isExtension
        self.usesJVMCIClassLoader = usesJVMCIClassLoader
        self.partOfHotSpot = partOfHotSpot # true when this distribution is delivered with HotSpot

_jdkDeployedDists = [
    JDKDeployedDist('TRUFFLE'),
]

JDK_UNIX_PERMISSIONS_DIR = 0755
JDK_UNIX_PERMISSIONS_FILE = 0644
JDK_UNIX_PERMISSIONS_EXEC = 0755

def isVMSupported(vm):
    if 'client' == vm and len(platform.mac_ver()[0]) != 0:
        # Client VM not supported: java launcher on Mac OS X translates '-client' to '-server'
        return False
    return True

def _get_vm():
    """
    Gets the configured VM, presenting a dialogue if there is no currently configured VM.
    """
    global _vm
    if _vm:
        return _vm
    vm = mx.get_env('DEFAULT_VM')
    if vm is None:
        extras = mx.get_env('EXTRA_JAVA_HOMES')
        if not extras is None:
            for e in extras.split(':'):
                vm = e
                break
    envPath = join(_graal_home, 'mx', 'env')
    if vm and 'graal' in vm:
        if exists(envPath):
            with open(envPath) as fp:
                if 'DEFAULT_VM=' + vm in fp.read():
                    mx.log('Please update the DEFAULT_VM value in ' + envPath + ' to replace "graal" with "jvmci"')
        vm = vm.replace('graal', 'jvmci')
    if vm is None:
        mx.abort('Need to specify VM with --vm option or DEFAULT_VM environment variable')
    _vm = vm
    return vm

def chmodRecursive(dirname, chmodFlagsDir):
    if mx.get_os() == 'windows':
        return

    def _chmodDir(chmodFlags, dirname, fnames):
        os.chmod(dirname, chmodFlagsDir)

    os.path.walk(dirname, _chmodDir, chmodFlagsDir)

def clean(args):
    """clean the source tree"""
    opts = mx.clean(args, parser=ArgumentParser(prog='mx clean'))

    if opts.native:
        def handleRemoveReadonly(func, path, exc):
            excvalue = exc[1]
            if mx.get_os() == 'windows' and func in (os.rmdir, os.remove) and excvalue.errno == errno.EACCES:
                os.chmod(path, stat.S_IRWXU | stat.S_IRWXG | stat.S_IRWXO)  # 0777
                func(path)
            else:
                raise

        def rmIfExists(name):
            if os.path.isdir(name):
                shutil.rmtree(name, ignore_errors=False, onerror=handleRemoveReadonly)
            elif os.path.isfile(name):
                os.unlink(name)

        rmIfExists(join(_graal_home, 'build'))
        rmIfExists(join(_graal_home, 'build-nojvmci'))

def export(args):
    """create archives of builds split by vmbuild and vm"""

    parser = ArgumentParser(prog='mx export')
    args = parser.parse_args(args)

    # collect data about export
    infos = dict()
    infos['timestamp'] = time.time()

    hgcfg = mx.HgConfig()
    hgcfg.check()
    infos['revision'] = hgcfg.tip('.') + ('+' if hgcfg.isDirty('.') else '')
    # TODO: infos['repository']

    infos['jdkversion'] = str(mx.java().version)

    infos['architecture'] = mx.get_arch()
    infos['platform'] = mx.get_os()

    if mx.get_os != 'windows':
        pass
        # infos['ccompiler']
        # infos['linker']

    infos['hostname'] = socket.gethostname()

    def _writeJson(suffix, properties):
        d = infos.copy()
        for k, v in properties.iteritems():
            assert not d.has_key(k)
            d[k] = v

        jsonFileName = 'export-' + suffix + '.json'
        with open(jsonFileName, 'w') as f:
            print >> f, json.dumps(d)
        return jsonFileName


    def _genFileName(archivtype, middle):
        idPrefix = infos['revision'] + '_'
        idSuffix = '.tar.gz'
        return join(_graal_home, "graalvm_" + archivtype + "_" + idPrefix + middle + idSuffix)

    # graal directory
    graalDirTarName = _genFileName('classfiles', 'javac')
    mx.logv("creating graal " + graalDirTarName)
    with tarfile.open(graalDirTarName, 'w:gz') as tar:
        for root, _, files in os.walk("graal"):
            for f in [f for f in files if not f.endswith('.java')]:
                name = join(root, f)
                # print name
                tar.add(name, name)

        n = _writeJson("graal", {'javacompiler' : 'javac'})
        tar.add(n, n)


def _run_benchmark(args, availableBenchmarks, runBenchmark):

    vmOpts, benchmarksAndOptions = _extract_VM_args(args, useDoubleDash=availableBenchmarks is None)

    if availableBenchmarks is None:
        harnessArgs = benchmarksAndOptions
        return runBenchmark(None, harnessArgs, vmOpts)

    if len(benchmarksAndOptions) == 0:
        mx.abort('at least one benchmark name or "all" must be specified')
    benchmarks = list(itertools.takewhile(lambda x: not x.startswith('-'), benchmarksAndOptions))
    harnessArgs = benchmarksAndOptions[len(benchmarks):]

    if 'all' in benchmarks:
        benchmarks = availableBenchmarks
    else:
        for bm in benchmarks:
            if bm not in availableBenchmarks:
                mx.abort('unknown benchmark: ' + bm + '\nselect one of: ' + str(availableBenchmarks))

    failed = []
    for bm in benchmarks:
        if not runBenchmark(bm, harnessArgs, vmOpts):
            failed.append(bm)

    if len(failed) != 0:
        mx.abort('Benchmark failures: ' + str(failed))

def _vmLibDirInJdk(jdk):
    """
    Get the directory within a JDK where the server and client
    subdirectories are located.
    """
    mxos = mx.get_os()
    if mxos == 'darwin':
        return join(jdk, 'jre', 'lib')
    if mxos == 'windows' or mxos == 'cygwin':
        return join(jdk, 'jre', 'bin')
    return join(jdk, 'jre', 'lib', mx.get_arch())

def _vmJliLibDirs(jdk):
    """
    Get the directories within a JDK where the jli library designates to.
    """
    mxos = mx.get_os()
    if mxos == 'darwin':
        return [join(jdk, 'jre', 'lib', 'jli')]
    if mxos == 'windows' or mxos == 'cygwin':
        return [join(jdk, 'jre', 'bin'), join(jdk, 'bin')]
    return [join(jdk, 'jre', 'lib', mx.get_arch(), 'jli'), join(jdk, 'lib', mx.get_arch(), 'jli')]

def _vmCfgInJdk(jdk, jvmCfgFile='jvm.cfg'):
    """
    Get the jvm.cfg file.
    """
    mxos = mx.get_os()
    if mxos == "windows" or mxos == "cygwin":
        return join(jdk, 'jre', 'lib', mx.get_arch(), jvmCfgFile)
    return join(_vmLibDirInJdk(jdk), jvmCfgFile)

def _jdksDir():
    return os.path.abspath(join(_graal_home, 'jdk' + str(mx.java().version)))

def _handle_missing_VM(bld, vm=None):
    if not vm:
        vm = _get_vm()
    mx.log('The ' + bld + ' ' + vm + ' VM has not been created')
    mx.abort('You need to run "mx --javahome ' + vm + ' use the selected VM')

def _jdk(build=None, vmToCheck=None, create=False, installJars=True):
    """
    Get the JDK into which Graal is installed, creating it first if necessary.
    """
    jdk = join(_jdksDir(), build)
    if create:
        srcJdk = mx.java().jdk
        if not exists(jdk):
            mx.log('Creating ' + jdk + ' from ' + srcJdk)
            shutil.copytree(srcJdk, jdk)

            # Make a copy of the default VM so that this JDK can be
            # reliably used as the bootstrap for a HotSpot build.
            jvmCfg = _vmCfgInJdk(jdk)
            if not exists(jvmCfg):
                mx.abort(jvmCfg + ' does not exist')

            defaultVM = None
            jvmCfgLines = []
            with open(jvmCfg) as f:
                for line in f:
                    if line.startswith('-') and defaultVM is None:
                        parts = line.split()
                        if len(parts) == 2:
                            assert parts[1] == 'KNOWN', parts[1]
                            defaultVM = parts[0][1:]
                            jvmCfgLines += ['# default VM is a copy of the unmodified ' + defaultVM + ' VM\n']
                            jvmCfgLines += ['-original KNOWN\n']
                        else:
                            # skip lines which we cannot parse (e.g. '-hotspot ALIASED_TO -client')
                            mx.log("WARNING: skipping not parsable line \"" + line + "\"")
                    else:
                        jvmCfgLines += [line]

            assert defaultVM is not None, 'Could not find default VM in ' + jvmCfg
            chmodRecursive(jdk, JDK_UNIX_PERMISSIONS_DIR)
            shutil.move(join(_vmLibDirInJdk(jdk), defaultVM), join(_vmLibDirInJdk(jdk), 'original'))

            if mx.get_os() != 'windows':
                os.chmod(jvmCfg, JDK_UNIX_PERMISSIONS_FILE)
            with open(jvmCfg, 'w') as fp:
                for line in jvmCfgLines:
                    fp.write(line)

            # patch 'release' file (append graalvm revision)
            releaseFile = join(jdk, 'release')
            if exists(releaseFile):
                releaseFileLines = []
                with open(releaseFile) as f:
                    for line in f:
                        releaseFileLines.append(line)

                if mx.get_os() != 'windows':
                    os.chmod(releaseFile, JDK_UNIX_PERMISSIONS_FILE)
                with open(releaseFile, 'w') as fp:
                    for line in releaseFileLines:
                        if line.startswith("SOURCE="):
                            try:
                                sourceLine = line[0:-2]  # remove last char
                                hgcfg = mx.HgConfig()
                                hgcfg.check()
                                revision = hgcfg.tip('.')[:12]  # take first 12 chars
                                fp.write(sourceLine + ' graal:' + revision + '\"\n')
                            except:
                                fp.write(line)
                        else:
                            fp.write(line)

            # Install a copy of the disassembler library
            try:
                hsdis([], copyToDir=_vmLibDirInJdk(jdk))
            except SystemExit:
                pass
    else:
        if not exists(jdk):
            _handle_missing_VM(build, vmToCheck)

    if installJars:
        for jdkDist in _jdkDeployedDists:
            dist = mx.distribution(jdkDist.name)
            if exists(dist.path) and jdkDist.partOfHotSpot:
                _installDistInJdks(jdkDist)

    return jdk

def _updateInstalledJVMCIOptionsFile(jdk):
    jvmciOptions = join(_graal_home, 'jvmci.options')
    jreLibDir = join(jdk, 'jre', 'lib')
    if exists(jvmciOptions):
        shutil.copy(jvmciOptions, join(jreLibDir, 'jvmci.options'))
    else:
        toDelete = join(jreLibDir, 'jvmci.options')
        if exists(toDelete):
            os.unlink(toDelete)

def _makeHotspotGeneratedSourcesDir():
    """
    Gets the directory containing all the HotSpot sources generated from
    JVMCI Java sources. This directory will be created if it doesn't yet exist.
    """
    hsSrcGenDir = join(mx.project('com.oracle.jvmci.hotspot').source_gen_dir(), 'hotspot')
    if not exists(hsSrcGenDir):
        os.makedirs(hsSrcGenDir)
    return hsSrcGenDir

def _copyToJdk(src, dst, permissions=JDK_UNIX_PERMISSIONS_FILE):
    name = os.path.basename(src)
    dstLib = join(dst, name)
    if mx.get_env('SYMLINK_GRAAL_JAR', None) == 'true':
        # Using symlinks is much faster than copying but may
        # cause issues if the lib is being updated while
        # the VM is running.
        if not os.path.islink(dstLib) or not os.path.realpath(dstLib) == src:
            if exists(dstLib):
                os.remove(dstLib)
            os.symlink(src, dstLib)
    else:
        # do a copy and then a move to get atomic updating (on Unix)
        fd, tmp = tempfile.mkstemp(suffix='', prefix=name, dir=dst)
        shutil.copyfile(src, tmp)
        os.close(fd)
        shutil.move(tmp, dstLib)
        os.chmod(dstLib, permissions)

def _filterJVMCIServices(servicesMap, classpath):
    """
    Filters and returns the names in 'serviceImplNames' that denote
    types available in 'classpath' implementing or extending
    com.oracle.jvmci.service.Service.
    """
    _, binDir = mx._compile_mx_class('FilterTypes', os.pathsep.join(classpath), myDir=dirname(__file__))
    serialized = [k + '=' + ','.join(v) for k, v in servicesMap.iteritems()]
    cmd = [mx.java().java, '-cp', mx._cygpathU2W(os.pathsep.join([binDir] + classpath)), 'FilterTypes', 'com.oracle.jvmci.service.Service'] + serialized
    serialized = subprocess.check_output(cmd)
    if len(serialized) == 0:
        return {}
    servicesMap = {}
    for e in serialized.split(' '):
        k, v = e.split('=')
        impls = v.split(',')
        servicesMap[k] = impls
    return servicesMap

def _extractJVMCIFiles(jdkJars, jvmciJars, servicesDir, optionsDir, cleanDestination=True):
    if cleanDestination:
        if exists(servicesDir):
            shutil.rmtree(servicesDir)
        if exists(optionsDir):
            shutil.rmtree(optionsDir)
    if not exists(servicesDir):
        os.makedirs(servicesDir)
    if not exists(optionsDir):
        os.makedirs(optionsDir)
    servicesMap = {}
    optionsFiles = []
    for jar in jvmciJars:
        if os.path.isfile(jar):
            with zipfile.ZipFile(jar) as zf:
                for member in zf.namelist():
                    if member.startswith('META-INF/services') and member:
                        serviceName = basename(member)
                        if serviceName == "":
                            continue # Zip files may contain empty entries for directories (jar -cf ... creates such)
                        # we don't handle directories
                        assert serviceName and member == 'META-INF/services/' + serviceName
                        with zf.open(member) as serviceFile:
                            serviceImpls = servicesMap.setdefault(serviceName, [])
                            for line in serviceFile.readlines():
                                line = line.strip()
                                if line:
                                    serviceImpls.append(line)
                    elif member.startswith('META-INF/options'):
                        filename = basename(member)
                        if filename == "":
                            continue # Zip files may contain empty entries for directories (jar -cf ... creates such)
                        # we don't handle directories
                        assert filename and member == 'META-INF/options/' + filename
                        targetpath = join(optionsDir, filename)
                        optionsFiles.append(filename)
                        with zf.open(member) as optionsFile, \
                             file(targetpath, "wb") as target:
                            shutil.copyfileobj(optionsFile, target)
    servicesMap = _filterJVMCIServices(servicesMap, jdkJars)
    for serviceName, serviceImpls in servicesMap.iteritems():
        fd, tmp = tempfile.mkstemp(prefix=serviceName)
        f = os.fdopen(fd, 'w+')
        for serviceImpl in serviceImpls:
            f.write(serviceImpl + os.linesep)
        target = join(servicesDir, serviceName)
        f.close()
        shutil.move(tmp, target)
        if mx.get_os() != 'windows':
            os.chmod(target, JDK_UNIX_PERMISSIONS_FILE)

def _updateJVMCIFiles(jdkDir):
    jreJVMCIDir = join(jdkDir, 'jre', 'lib', 'jvmci')
    jvmciJars = [join(jreJVMCIDir, e) for e in os.listdir(jreJVMCIDir) if e.endswith('.jar')]
    jreJVMCIServicesDir = join(jreJVMCIDir, 'services')
    jreJVMCIOptionsDir = join(jreJVMCIDir, 'options')
    _extractJVMCIFiles(_getJdkDeployedJars(jdkDir), jvmciJars, jreJVMCIServicesDir, jreJVMCIOptionsDir)

def _patchGraalVersionConstant(dist):
    """
    Patches the constant "@@@@@@@@@@@@@@@@graal.version@@@@@@@@@@@@@@@@" in the constant pool of Graal.class
    with the computed Graal version string.
    """
    zf = zipfile.ZipFile(dist.path, 'r')
    graalClassfilePath = 'com/oracle/graal/api/runtime/Graal.class'
    try:
        graalClassfile = zf.read(graalClassfilePath)
    except KeyError:
        mx.log(graalClassfilePath + ' is not present in ' + dist.path)
        return
    placeholder = '@@@@@@@@@@@@@@@@graal.version@@@@@@@@@@@@@@@@'
    placeholderLen = len(placeholder)
    versionSpec = '{:' + str(placeholderLen) + '}'
    versionStr = versionSpec.format(graal_version())

    if len(versionStr) > placeholderLen:
        # Truncate the version string if necessary
        assert versionStr.startswith('unknown'), versionStr
        versionStr = versionStr[:placeholderLen]
    if placeholder not in graalClassfile:
        assert versionStr in graalClassfile, 'could not find "' + placeholder + '" or "' + versionStr + '" constant in ' + dist.path + '!' + graalClassfilePath
        zf.close()
        return False

    zfOutFd, zfOutPath = tempfile.mkstemp(suffix='', prefix=basename(dist.path) + '.', dir=dirname(dist.path))
    zfOut = zipfile.ZipFile(zfOutPath, 'w')
    for zi in zf.infolist():
        if zi.filename == graalClassfilePath:
            data = graalClassfile.replace(placeholder, versionStr)
        else:
            data = zf.read(zi)
        zfOut.writestr(zi, data)
    zfOut.close()
    os.close(zfOutFd)
    zf.close()
    shutil.move(zfOutPath, dist.path)

def _installDistInJdks(deployableDist):
    """
    Installs the jar(s) for a given Distribution into all existing JVMCI JDKs
    """
    if True:
        return
    dist = mx.distribution(deployableDist.name)
    if dist.name == 'GRAAL':
        _patchGraalVersionConstant(dist)

    jdks = _jdksDir()
    if exists(jdks):
        for e in os.listdir(jdks):
            jdkDir = join(jdks, e)
            jreLibDir = join(jdkDir, 'jre', 'lib')
            if exists(jreLibDir):
                if deployableDist.isExtension:
                    targetDir = join(jreLibDir, 'ext')
                elif deployableDist.usesJVMCIClassLoader:
                    targetDir = join(jreLibDir, 'jvmci')
                else:
                    targetDir = jreLibDir
                if not exists(targetDir):
                    os.makedirs(targetDir)
                _copyToJdk(dist.path, targetDir)
                if dist.sourcesPath:
                    _copyToJdk(dist.sourcesPath, jdkDir)
                if deployableDist.usesJVMCIClassLoader:
                    # deploy service files
                    _updateJVMCIFiles(jdkDir)

def _getJdkDeployedJars(jdkDir):
    """
    Gets jar paths for all deployed distributions in the context of
    a given JDK directory.
    """
    jreLibDir = join(jdkDir, 'jre', 'lib')
    jars = []
    for dist in _jdkDeployedDists:
        jar = basename(mx.distribution(dist.name).path)
        if dist.isExtension:
            jars.append(join(jreLibDir, 'ext', jar))
        elif dist.usesJVMCIClassLoader:
            jars.append(join(jreLibDir, 'jvmci', jar))
        else:
            jars.append(join(jreLibDir, jar))
    return jars


# run a command in the windows SDK Debug Shell
def _runInDebugShell(cmd, workingDir, logFile=None, findInOutput=None, respondTo=None):
    if respondTo is None:
        respondTo = {}
    newLine = os.linesep
    startToken = 'RUNINDEBUGSHELL_STARTSEQUENCE'
    endToken = 'RUNINDEBUGSHELL_ENDSEQUENCE'

    winSDK = mx.get_env('WIN_SDK', 'C:\\Program Files\\Microsoft SDKs\\Windows\\v7.1\\')

    if not exists(mx._cygpathW2U(winSDK)):
        mx.abort("Could not find Windows SDK : '" + winSDK + "' does not exist")

    winSDKSetEnv = mx._cygpathW2U(join(winSDK, 'Bin', 'SetEnv.cmd'))
    if not exists(winSDKSetEnv):
        mx.abort("Invalid Windows SDK path (" + winSDK + ") : could not find Bin/SetEnv.cmd (you can use the WIN_SDK environment variable to specify an other path)")

    wincmd = 'cmd.exe /E:ON /V:ON /K "' + mx._cygpathU2W(winSDKSetEnv) + '"'
    p = subprocess.Popen(wincmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    stdout = p.stdout
    stdin = p.stdin
    if logFile:
        log = open(logFile, 'w')
    ret = False

    def _writeProcess(s):
        stdin.write(s + newLine)

    _writeProcess("echo " + startToken)
    while True:
        # encoding may be None on windows plattforms
        if sys.stdout.encoding is None:
            encoding = 'utf-8'
        else:
            encoding = sys.stdout.encoding

        line = stdout.readline().decode(encoding)
        if logFile:
            log.write(line.encode('utf-8'))
        line = line.strip()
        mx.log(line)
        if line == startToken:
            _writeProcess('cd /D ' + workingDir + ' & ' + cmd + ' & echo ' + endToken)
        for regex in respondTo.keys():
            match = regex.search(line)
            if match:
                _writeProcess(respondTo[regex])
        if findInOutput:
            match = findInOutput.search(line)
            if match:
                ret = True
        if line == endToken:
            if not findInOutput:
                _writeProcess('echo ERRXXX%errorlevel%')
            else:
                break
        if line.startswith('ERRXXX'):
            if line == 'ERRXXX0':
                ret = True
            break
    _writeProcess("exit")
    if logFile:
        log.close()
    return ret

def jdkhome(vm=None):
    """return the JDK directory selected for the 'vm' command"""
    return _jdk(installJars=False)

def print_jdkhome(args, vm=None):
    """print the JDK directory selected for the 'vm' command"""
    print jdkhome(vm)

def buildvars(args):
    """describe the variables that can be set by the -D option to the 'mx build' commmand"""

    buildVars = {
        'ALT_BOOTDIR' : 'The location of the bootstrap JDK installation (default: ' + mx.java().jdk + ')',
        'ALT_OUTPUTDIR' : 'Build directory',
        'HOTSPOT_BUILD_JOBS' : 'Number of CPUs used by make (default: ' + str(mx.cpu_count()) + ')',
        'INSTALL' : 'Install the built VM into the JDK? (default: y)',
        'ZIP_DEBUGINFO_FILES' : 'Install zipped debug symbols file? (default: 0)',
    }

    mx.log('HotSpot build variables that can be set by the -D option to "mx build":')
    mx.log('')
    for n in sorted(buildVars.iterkeys()):
        mx.log(n)
        mx.log(textwrap.fill(buildVars[n], initial_indent='    ', subsequent_indent='    ', width=200))

    mx.log('')
    mx.log('Note that these variables can be given persistent values in the file ' + join(_graal_home, 'mx', 'env') + ' (see \'mx about\').')

cached_graal_version = None

def graal_version(dev_suffix='dev'):
    global cached_graal_version

    if not cached_graal_version:
        # extract latest release tag for graal
        try:
            tags = [x.split() for x in subprocess.check_output(['hg', '-R', _graal_home, 'tags']).split('\n') if x.startswith("graal-")]
            current_id = subprocess.check_output(['hg', '-R', _graal_home, 'log', '--template', '{rev}\n', '--rev', 'tip']).strip()
        except:
            # not a mercurial repository or hg commands are not available.
            tags = None

        if tags and current_id:
            sorted_tags = sorted(tags, key=lambda e: [int(x) for x in e[0][len("graal-"):].split('.')], reverse=True)
            most_recent_tag_name, most_recent_tag_revision = sorted_tags[0]
            most_recent_tag_id = most_recent_tag_revision[:most_recent_tag_revision.index(":")]
            most_recent_tag_version = most_recent_tag_name[len("graal-"):]

            # tagged commit is one-off with commit that tags it
            if int(current_id) - int(most_recent_tag_id) <= 1:
                cached_graal_version = most_recent_tag_version
            else:
                major, minor = map(int, most_recent_tag_version.split('.'))
                cached_graal_version = str(major) + '.' + str(minor + 1) + '-' + dev_suffix
        else:
            cached_graal_version = 'unknown-{0}'.format(platform.node())

    return cached_graal_version

def build(args, vm=None):
    """build the VM binary

    The global '--vm' and '--vmbuild' options select which VM type and build target to build."""

    # Override to fail quickly if extra arguments are given
    # at the end of the command line. This allows for a more
    # helpful error message.
    class AP(ArgumentParser):
        def __init__(self):
            ArgumentParser.__init__(self, prog='mx build')
        def parse_args(self, args):
            result = ArgumentParser.parse_args(self, args)
            if len(result.remainder) != 0:
                firstBuildTarget = result.remainder[0]
                mx.abort('To specify the ' + firstBuildTarget + ' VM build target, you need to use the global "--vmbuild" option. For example:\n' +
                         '    mx --vmbuild ' + firstBuildTarget + ' build')
            return result

    # Call mx.build to compile the Java sources
    parser = AP()
    parser.add_argument('-D', action='append', help='set a HotSpot build variable (run \'mx buildvars\' to list variables)', metavar='name=value')

    opts2 = mx.build(['--source', '1.7'] + args, parser=parser)
    assert len(opts2.remainder) == 0

def vmg(args):
    """run the debug build of VM selected by the '--vm' option"""
    return vm(args, vmbuild='debug')

def vmfg(args):
    """run the fastdebug build of VM selected by the '--vm' option"""
    return vm(args, vmbuild='fastdebug')

def _parseVmArgs(args, vm=None, cwd=None, vmbuild=None):
    """run the VM selected by the '--vm' option"""

    jdk = mx.java().jdk
    mx.expand_project_in_args(args)
    exe = join(jdk, 'bin', mx.exe_suffix('java'))
    pfx = []

    if '-version' in args:
        ignoredArgs = args[args.index('-version') + 1:]
        if len(ignoredArgs) > 0:
            mx.log("Warning: The following options will be ignored by the vm because they come after the '-version' argument: " + ' '.join(ignoredArgs))

    # Unconditionally prepend Truffle to the boot class path.
    # This used to be done by the VM itself but was removed to
    # separate the VM from Truffle.
    truffle_jar = mx.archive(['@TRUFFLE'])[0]
    args = ['-Xbootclasspath/p:' + truffle_jar] + args

    args = mx.java().processArgs(args)
    return (pfx, exe, vm, args, cwd)

def vm(args, vm=None, nonZeroIsFatal=True, out=None, err=None, cwd=None, timeout=None, vmbuild=None):
    (pfx_, exe_, _, args_, cwd) = _parseVmArgs(args, vm, cwd, vmbuild)
    return mx.run(pfx_ + [exe_] + args_, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, cwd=cwd)

def _find_classes_with_annotations(p, pkgRoot, annotations, includeInnerClasses=False):
    """
    Scan the sources of project 'p' for Java source files containing a line starting with 'annotation'
    (ignoring preceding whitespace) and return the fully qualified class name for each Java
    source file matched in a list.
    """

    matches = lambda line: len([a for a in annotations if line == a or line.startswith(a + '(')]) != 0
    return p.find_classes_with_matching_source_line(pkgRoot, matches, includeInnerClasses)

def _extract_VM_args(args, allowClasspath=False, useDoubleDash=False, defaultAllVMArgs=True):
    """
    Partitions a command line into a leading sequence of HotSpot VM options and the rest.
    """
    for i in range(0, len(args)):
        if useDoubleDash:
            if args[i] == '--':
                vmArgs = args[:i]
                remainder = args[i + 1:]
                return vmArgs, remainder
        else:
            if not args[i].startswith('-'):
                if i != 0 and (args[i - 1] == '-cp' or args[i - 1] == '-classpath'):
                    if not allowClasspath:
                        mx.abort('Cannot supply explicit class path option')
                    else:
                        continue
                vmArgs = args[:i]
                remainder = args[i:]
                return vmArgs, remainder

    if defaultAllVMArgs:
        return args, []
    else:
        return [], args

def _run_tests(args, harness, annotations, testfile, blacklist, whitelist, regex):


    vmArgs, tests = _extract_VM_args(args)
    for t in tests:
        if t.startswith('-'):
            mx.abort('VM option ' + t + ' must precede ' + tests[0])

    candidates = {}
    for p in mx.projects_opt_limit_to_suites():
        if mx.java().javaCompliance < p.javaCompliance:
            continue
        for c in _find_classes_with_annotations(p, None, annotations).keys():
            candidates[c] = p

    classes = []
    if len(tests) == 0:
        classes = candidates.keys()
        projectsCp = mx.classpath([pcp.name for pcp in mx.projects_opt_limit_to_suites() if pcp.javaCompliance <= mx.java().javaCompliance])
    else:
        projs = set()
        found = False
        if len(tests) == 1 and '#' in tests[0]:
            words = tests[0].split('#')
            if len(words) != 2:
                mx.abort("Method specification is class#method: " + tests[0])
            t, method = words

            for c, p in candidates.iteritems():
                # prefer exact matches first
                if t == c:
                    found = True
                    classes.append(c)
                    projs.add(p.name)
            if not found:
                for c, p in candidates.iteritems():
                    if t in c:
                        found = True
                        classes.append(c)
                        projs.add(p.name)
            if not found:
                mx.log('warning: no tests matched by substring "' + t)
            elif len(classes) != 1:
                mx.abort('More than one test matches substring {0} {1}'.format(t, classes))

            classes = [c + "#" + method for c in classes]
        else:
            for t in tests:
                if '#' in t:
                    mx.abort('Method specifications can only be used in a single test: ' + t)
                for c, p in candidates.iteritems():
                    if t in c:
                        found = True
                        classes.append(c)
                        projs.add(p.name)
                if not found:
                    mx.log('warning: no tests matched by substring "' + t)
        projectsCp = mx.classpath(projs)

    if blacklist:
        classes = [c for c in classes if not any((glob.match(c) for glob in blacklist))]

    if whitelist:
        classes = [c for c in classes if any((glob.match(c) for glob in whitelist))]

    if regex:
        classes = [c for c in classes if re.search(regex, c)]

    if len(classes) != 0:
        f_testfile = open(testfile, 'w')
        for c in classes:
            f_testfile.write(c + '\n')
        f_testfile.close()
        harness(projectsCp, vmArgs)

def _unittest(args, annotations, prefixCp="", blacklist=None, whitelist=None, verbose=False, fail_fast=False, enable_timing=False, regex=None, color=False, eager_stacktrace=False, gc_after_test=False):
    testfile = os.environ.get('MX_TESTFILE', None)
    if testfile is None:
        (_, testfile) = tempfile.mkstemp(".testclasses", "graal")
        os.close(_)

    coreCp = mx.classpath(['com.oracle.truffle.tck', 'HCFDIS'])

    coreArgs = []
    if verbose:
        coreArgs.append('-JUnitVerbose')
    if fail_fast:
        coreArgs.append('-JUnitFailFast')
    if enable_timing:
        coreArgs.append('-JUnitEnableTiming')
    if color:
        coreArgs.append('-JUnitColor')
    if eager_stacktrace:
        coreArgs.append('-JUnitEagerStackTrace')
    if gc_after_test:
        coreArgs.append('-JUnitGCAfterTest')


    def harness(projectsCp, vmArgs):
        if _get_vm() != 'jvmci':
            prefixArgs = ['-esa', '-ea']
        else:
            prefixArgs = ['-XX:-BootstrapJVMCI', '-esa', '-ea']
        if gc_after_test:
            prefixArgs.append('-XX:-DisableExplicitGC')
        with open(testfile) as fp:
            testclasses = [l.rstrip() for l in fp.readlines()]

        # Remove entries from class path that are in graal.jar and
        # run the VM in a mode where application/test classes can
        # access core Graal classes.
        cp = prefixCp + coreCp + os.pathsep + projectsCp
        if isJVMCIEnabled(_get_vm()):
            excluded = set()
            for jdkDist in _jdkDeployedDists:
                dist = mx.distribution(jdkDist.name)
                excluded.update([d.output_dir() for d in dist.sorted_deps()])
            cp = os.pathsep.join([e for e in cp.split(os.pathsep) if e not in excluded])

        # suppress menubar and dock when running on Mac
        vmArgs = ['-Djava.awt.headless=true'] + vmArgs

        if len(testclasses) == 1:
            # Execute Junit directly when one test is being run. This simplifies
            # replaying the VM execution in a native debugger (e.g., gdb).
            vm(prefixArgs + vmArgs + ['-cp', mx._separatedCygpathU2W(cp), 'com.oracle.truffle.tck.TruffleJUnitCore'] + coreArgs + testclasses)
        else:
            vm(prefixArgs + vmArgs + ['-cp', mx._separatedCygpathU2W(cp), 'com.oracle.truffle.tck.TruffleJUnitCore'] + coreArgs + ['@' + mx._cygpathU2W(testfile)])

    try:
        _run_tests(args, harness, annotations, testfile, blacklist, whitelist, regex)
    finally:
        if os.environ.get('MX_TESTFILE') is None:
            os.remove(testfile)

_unittestHelpSuffix = """
    Unittest options:

      --blacklist <file>     run all testcases not specified in the blacklist
      --whitelist <file>     run only testcases which are included
                             in the given whitelist
      --verbose              enable verbose JUnit output
      --fail-fast            stop after first JUnit test class that has a failure
      --enable-timing        enable JUnit test timing
      --regex <regex>        run only testcases matching a regular expression
      --color                enable colors output
      --eager-stacktrace     print stacktrace eagerly
      --gc-after-test        force a GC after each test

    To avoid conflicts with VM options '--' can be used as delimiter.

    If filters are supplied, only tests whose fully qualified name
    includes a filter as a substring are run.

    For example, this command line:

       mx unittest -G:Dump= -G:MethodFilter=BC_aload.* -G:+PrintCFG BC_aload

    will run all JUnit test classes that contain 'BC_aload' in their
    fully qualified name and will pass these options to the VM:

        -G:Dump= -G:MethodFilter=BC_aload.* -G:+PrintCFG

    To get around command line length limitations on some OSes, the
    JUnit class names to be executed are written to a file that a
    custom JUnit wrapper reads and passes onto JUnit proper. The
    MX_TESTFILE environment variable can be set to specify a
    file which will not be deleted once the unittests are done
    (unlike the temporary file otherwise used).

    As with all other commands, using the global '-v' before 'unittest'
    command will cause mx to show the complete command line
    it uses to run the VM.
"""

def unittest(args):
    """run the JUnit tests (all testcases){0}"""

    parser = ArgumentParser(prog='mx unittest',
          description='run the JUnit tests',
          add_help=False,
          formatter_class=RawDescriptionHelpFormatter,
          epilog=_unittestHelpSuffix,
        )
    parser.add_argument('--blacklist', help='run all testcases not specified in the blacklist', metavar='<path>')
    parser.add_argument('--whitelist', help='run testcases specified in whitelist only', metavar='<path>')
    parser.add_argument('--verbose', help='enable verbose JUnit output', action='store_true')
    parser.add_argument('--fail-fast', help='stop after first JUnit test class that has a failure', action='store_true')
    parser.add_argument('--enable-timing', help='enable JUnit test timing', action='store_true')
    parser.add_argument('--regex', help='run only testcases matching a regular expression', metavar='<regex>')
    parser.add_argument('--color', help='enable color output', action='store_true')
    parser.add_argument('--eager-stacktrace', help='print stacktrace eagerly', action='store_true')
    parser.add_argument('--gc-after-test', help='force a GC after each test', action='store_true')

    ut_args = []
    delimiter = False
    # check for delimiter
    while len(args) > 0:
        arg = args.pop(0)
        if arg == '--':
            delimiter = True
            break
        ut_args.append(arg)

    if delimiter:
        # all arguments before '--' must be recognized
        parsed_args = parser.parse_args(ut_args)
    else:
        # parse all know arguments
        parsed_args, args = parser.parse_known_args(ut_args)

    if parsed_args.whitelist:
        try:
            with open(join(_graal_home, parsed_args.whitelist)) as fp:
                parsed_args.whitelist = [re.compile(fnmatch.translate(l.rstrip())) for l in fp.readlines() if not l.startswith('#')]
        except IOError:
            mx.log('warning: could not read whitelist: ' + parsed_args.whitelist)
    if parsed_args.blacklist:
        try:
            with open(join(_graal_home, parsed_args.blacklist)) as fp:
                parsed_args.blacklist = [re.compile(fnmatch.translate(l.rstrip())) for l in fp.readlines() if not l.startswith('#')]
        except IOError:
            mx.log('warning: could not read blacklist: ' + parsed_args.blacklist)

    _unittest(args, ['@Test', '@Parameters'], **parsed_args.__dict__)

def shortunittest(args):
    """alias for 'unittest --whitelist test/whitelist_shortunittest.txt'{0}"""

    args = ['--whitelist', 'test/whitelist_shortunittest.txt'] + args
    unittest(args)

class Task:
    # None or a list of strings. If not None, only tasks whose title
    # matches at least one of the substrings in this list will return
    # a non-None value from __enter__. The body of a 'with Task(...) as t'
    # statement should check 't' and exit immediately if it is None.
    filters = None
    filtersExclude = False

    def __init__(self, title, tasks=None):
        self.tasks = tasks
        self.title = title
        if tasks is not None and Task.filters is not None:
            if Task.filtersExclude:
                self.skipped = any([f in title for f in Task.filters])
            else:
                self.skipped = not any([f in title for f in Task.filters])
        else:
            self.skipped = False
        if not self.skipped:
            self.start = time.time()
            self.end = None
            self.duration = None
            mx.log(time.strftime('gate: %d %b %Y %H:%M:%S: BEGIN: ') + title)
    def __enter__(self):
        assert self.tasks is not None, "using Task with 'with' statement requires to pass the tasks list in the constructor"
        if self.skipped:
            return None
        return self
    def __exit__(self, exc_type, exc_value, traceback):
        if not self.skipped:
            self.tasks.append(self.stop())
    def stop(self):
        self.end = time.time()
        self.duration = datetime.timedelta(seconds=self.end - self.start)
        mx.log(time.strftime('gate: %d %b %Y %H:%M:%S: END:   ') + self.title + ' [' + str(self.duration) + ']')
        return self
    def abort(self, codeOrMessage):
        self.end = time.time()
        self.duration = datetime.timedelta(seconds=self.end - self.start)
        mx.log(time.strftime('gate: %d %b %Y %H:%M:%S: ABORT: ') + self.title + ' [' + str(self.duration) + ']')
        mx.abort(codeOrMessage)
        return self

def _basic_gate_body(args, tasks):
    # Run unit tests on server-hosted-jvmci
    with Task('UnitTests:hosted-product', tasks) as t:
        if t: unittest(['--enable-timing', '--verbose', '--fail-fast'])


def gate(args, gate_body=_basic_gate_body):
    """run the tests used to validate a push

    If this command exits with a 0 exit code, then the source code is in
    a state that would be accepted for integration into the main repository."""

    parser = ArgumentParser(prog='mx gate')
    parser.add_argument('-j', '--omit-java-clean', action='store_false', dest='cleanJava', help='omit cleaning Java native code')
    parser.add_argument('-n', '--omit-native-clean', action='store_false', dest='cleanNative', help='omit cleaning and building native code')
    parser.add_argument('-i', '--omit-ide-clean', action='store_false', dest='cleanIde', help='omit cleaning the ide project files')
    parser.add_argument('-g', '--only-build-jvmci', action='store_false', dest='buildNonJVMCI', help='only build the JVMCI VM')
    parser.add_argument('-t', '--task-filter', help='comma separated list of substrings to select subset of tasks to be run')
    parser.add_argument('-x', action='store_true', help='makes --task-filter an exclusion instead of inclusion filter')

    args = parser.parse_args(args)

    if args.task_filter:
        Task.filters = args.task_filter.split(',')
        Task.filtersExclude = args.x
    elif args.x:
        mx.abort('-x option cannot be used without --task-filter option')

    # Force
    if not mx._opts.strict_compliance:
        mx.log("[gate] forcing strict compliance")
        mx._opts.strict_compliance = True

    tasks = []
    total = Task('Gate')
    try:
        with Task('Pylint', tasks) as t:
            if t: mx.pylint([])

        def _clean(name='Clean'):
            with Task(name, tasks) as t:
                if t:
                    cleanArgs = []
                    if not args.cleanNative:
                        cleanArgs.append('--no-native')
                    if not args.cleanJava:
                        cleanArgs.append('--no-java')
                    clean(cleanArgs)
        _clean()

        with Task('IDEConfigCheck', tasks) as t:
            if t:
                if args.cleanIde:
                    mx.ideclean([])
                    mx.ideinit([])

        eclipse_exe = mx.get_env('ECLIPSE_EXE')
        if eclipse_exe is not None:
            with Task('CodeFormatCheck', tasks) as t:
                if t and mx.eclipseformat(['-e', eclipse_exe]) != 0:
                    t.abort('Formatter modified files - run "mx eclipseformat", check in changes and repush')

        with Task('Canonicalization Check', tasks) as t:
            if t:
                mx.log(time.strftime('%d %b %Y %H:%M:%S - Ensuring mx/projects files are canonicalized...'))
                if mx.canonicalizeprojects([]) != 0:
                    t.abort('Rerun "mx canonicalizeprojects" and check-in the modified mx/projects files.')

        if mx.get_env('JDT'):
            with Task('BuildJavaWithEcj', tasks):
                if t: build(['-p', '--no-native', '--jdt-warning-as-error'])
            _clean('CleanAfterEcjBuild')

        with Task('BuildJavaWithJavac', tasks):
            if t: build(['-p', '--no-native', '--force-javac'])

        with Task('Checkstyle', tasks) as t:
            if t and mx.checkstyle([]) != 0:
                t.abort('Checkstyle warnings were found')

        with Task('Checkheaders', tasks) as t:
            if t and checkheaders([]) != 0:
                t.abort('Checkheaders warnings were found')

        with Task('FindBugs', tasks) as t:
            if t and findbugs([]) != 0:
                t.abort('FindBugs warnings were found')

        gate_body(args, tasks)

    except KeyboardInterrupt:
        total.abort(1)

    except BaseException as e:
        import traceback
        traceback.print_exc()
        total.abort(str(e))

    total.stop()

    mx.log('Gate task times:')
    for t in tasks:
        mx.log('  ' + str(t.duration) + '\t' + t.title)
    mx.log('  =======')
    mx.log('  ' + str(total.duration))

    if args.task_filter:
        Task.filters = None

def _igvJdk():
    v8u20 = mx.VersionSpec("1.8.0_20")
    v8u40 = mx.VersionSpec("1.8.0_40")
    v8 = mx.VersionSpec("1.8")
    def _igvJdkVersionCheck(version):
        return version >= v8 and (version < v8u20 or version >= v8u40)
    return mx.java_version(_igvJdkVersionCheck, versionDescription='>= 1.8 and < 1.8.0u20 or >= 1.8.0u40').jdk

def _igvBuildEnv():
        # When the http_proxy environment variable is set, convert it to the proxy settings that ant needs
    env = dict(os.environ)
    proxy = os.environ.get('http_proxy')
    if not (proxy is None) and len(proxy) > 0:
        if '://' in proxy:
            # Remove the http:// prefix (or any other protocol prefix)
            proxy = proxy.split('://', 1)[1]
        # Separate proxy server name and port number
        proxyName, proxyPort = proxy.split(':', 1)
        proxyEnv = '-DproxyHost="' + proxyName + '" -DproxyPort=' + proxyPort
        env['ANT_OPTS'] = proxyEnv

    env['JAVA_HOME'] = _igvJdk()
    return env

def igv(args):
    """run the Ideal Graph Visualizer"""
    logFile = '.ideal_graph_visualizer.log'
    with open(join(_graal_home, logFile), 'w') as fp:
        mx.logv('[Ideal Graph Visualizer log is in ' + fp.name + ']')
        nbplatform = join(_graal_home, 'src', 'share', 'tools', 'IdealGraphVisualizer', 'nbplatform')

        # Remove NetBeans platform if it is earlier than the current supported version
        if exists(nbplatform):
            updateTrackingFile = join(nbplatform, 'platform', 'update_tracking', 'org-netbeans-core.xml')
            if not exists(updateTrackingFile):
                mx.log('Could not find \'' + updateTrackingFile + '\', removing NetBeans platform')
                shutil.rmtree(nbplatform)
            else:
                dom = xml.dom.minidom.parse(updateTrackingFile)
                currentVersion = mx.VersionSpec(dom.getElementsByTagName('module_version')[0].getAttribute('specification_version'))
                supportedVersion = mx.VersionSpec('3.43.1')
                if currentVersion < supportedVersion:
                    mx.log('Replacing NetBeans platform version ' + str(currentVersion) + ' with version ' + str(supportedVersion))
                    shutil.rmtree(nbplatform)
                elif supportedVersion < currentVersion:
                    mx.log('Supported NetBeans version in igv command should be updated to ' + str(currentVersion))

        if not exists(nbplatform):
            mx.logv('[This execution may take a while as the NetBeans platform needs to be downloaded]')

        env = _igvBuildEnv()
        # make the jar for Batik 1.7 available.
        env['IGV_BATIK_JAR'] = mx.library('BATIK').get_path(True)
        if mx.run(['ant', '-f', mx._cygpathU2W(join(_graal_home, 'src', 'share', 'tools', 'IdealGraphVisualizer', 'build.xml')), '-l', mx._cygpathU2W(fp.name), 'run'], env=env, nonZeroIsFatal=False):
            mx.abort("IGV ant build & launch failed. Check '" + logFile + "'. You can also try to delete 'src/share/tools/IdealGraphVisualizer/nbplatform'.")

def maven_install_truffle(args):
    """install Truffle into your local Maven repository"""
    for name in mx._dists:
        dist = mx._dists[name]
        if dist.isProcessorDistribution:
            continue
        mx.archive(["@" + name])
        path = dist.path
        slash = path.rfind('/')
        dot = path.rfind('.')
        if dot <= slash:
            mx.abort('Dot should be after / in ' + path)
        artifactId = path[slash + 1: dot]
        mx.run(['mvn', 'install:install-file', '-DgroupId=com.oracle.' + dist.suite.name, '-DartifactId=' + artifactId, '-Dversion=' + graal_version('SNAPSHOT'), '-Dpackaging=jar', '-Dfile=' + path])

def c1visualizer(args):
    """run the Cl Compiler Visualizer"""
    libpath = join(_graal_home, 'lib')
    if mx.get_os() == 'windows':
        executable = join(libpath, 'c1visualizer', 'bin', 'c1visualizer.exe')
    else:
        executable = join(libpath, 'c1visualizer', 'bin', 'c1visualizer')

    # Check whether the current C1Visualizer installation is the up-to-date
    if exists(executable) and not exists(mx.library('C1VISUALIZER_DIST').get_path(resolve=False)):
        mx.log('Updating C1Visualizer')
        shutil.rmtree(join(libpath, 'c1visualizer'))

    archive = mx.library('C1VISUALIZER_DIST').get_path(resolve=True)

    if not exists(executable):
        zf = zipfile.ZipFile(archive, 'r')
        zf.extractall(libpath)

    if not exists(executable):
        mx.abort('C1Visualizer binary does not exist: ' + executable)

    if mx.get_os() != 'windows':
        # Make sure that execution is allowed. The zip file does not always specfiy that correctly
        os.chmod(executable, 0777)

    mx.run([executable])

def hsdis(args, copyToDir=None):
    """download the hsdis library

    This is needed to support HotSpot's assembly dumping features.
    By default it downloads the Intel syntax version, use the 'att' argument to install AT&T syntax."""
    flavor = 'intel'
    if 'att' in args:
        flavor = 'att'
    if mx.get_arch() == "sparcv9":
        flavor = "sparcv9"
    lib = mx.add_lib_suffix('hsdis-' + mx.get_arch())
    path = join(_graal_home, 'lib', lib)

    sha1s = {
        'att/hsdis-amd64.dll' : 'bcbd535a9568b5075ab41e96205e26a2bac64f72',
        'att/hsdis-amd64.so' : '58919ba085d4ef7a513f25bae75e7e54ee73c049',
        'intel/hsdis-amd64.dll' : '6a388372cdd5fe905c1a26ced614334e405d1f30',
        'intel/hsdis-amd64.so' : '844ed9ffed64fe9599638f29a8450c50140e3192',
        'intel/hsdis-amd64.dylib' : 'fdb13ef0d7d23d93dacaae9c98837bea0d4fc5a2',
        'sparcv9/hsdis-sparcv9.so': '970640a9af0bd63641f9063c11275b371a59ee60',
    }

    flavoredLib = flavor + "/" + lib
    if flavoredLib not in sha1s:
        mx.logv("hsdis not supported on this plattform or architecture")
        return

    if not exists(path):
        sha1 = sha1s[flavoredLib]
        sha1path = path + '.sha1'
        mx.download_file_with_sha1('hsdis', path, ['http://lafo.ssw.uni-linz.ac.at/hsdis/' + flavoredLib], sha1, sha1path, True, True, sources=False)
    if copyToDir is not None and exists(copyToDir):
        shutil.copy(path, copyToDir)

def hcfdis(args):
    """disassemble HexCodeFiles embedded in text files

    Run a tool over the input files to convert all embedded HexCodeFiles
    to a disassembled format."""

    parser = ArgumentParser(prog='mx hcfdis')
    parser.add_argument('-m', '--map', help='address to symbol map applied to disassembler output')
    parser.add_argument('files', nargs=REMAINDER, metavar='files...')

    args = parser.parse_args(args)

    path = mx.library('HCFDIS').get_path(resolve=True)
    mx.run_java(['-cp', path, 'com.oracle.max.hcfdis.HexCodeFileDis'] + args.files)

    if args.map is not None:
        addressRE = re.compile(r'0[xX]([A-Fa-f0-9]+)')
        with open(args.map) as fp:
            lines = fp.read().splitlines()
        symbols = dict()
        for l in lines:
            addressAndSymbol = l.split(' ', 1)
            if len(addressAndSymbol) == 2:
                address, symbol = addressAndSymbol
                if address.startswith('0x'):
                    address = long(address, 16)
                    symbols[address] = symbol
        for f in args.files:
            with open(f) as fp:
                lines = fp.read().splitlines()
            updated = False
            for i in range(0, len(lines)):
                l = lines[i]
                for m in addressRE.finditer(l):
                    sval = m.group(0)
                    val = long(sval, 16)
                    sym = symbols.get(val)
                    if sym:
                        l = l.replace(sval, sym)
                        updated = True
                        lines[i] = l
            if updated:
                mx.log('updating ' + f)
                with open('new_' + f, "w") as fp:
                    for l in lines:
                        print >> fp, l

def sl(args):
    """run an SL program"""
    vmArgs, slArgs = _extract_VM_args(args)
    vm(vmArgs + ['-cp', mx.classpath(["TRUFFLE", "com.oracle.truffle.sl"]), "com.oracle.truffle.sl.SLLanguage"] + slArgs)

def sldebug(args):
    """run a simple command line debugger for the Simple Language"""
    vmArgs, slArgs = _extract_VM_args(args, useDoubleDash=True)
    vm(vmArgs + ['-cp', mx.classpath("com.oracle.truffle.sl.tools"), "com.oracle.truffle.sl.tools.debug.SLREPLServer"] + slArgs)

def isJVMCIEnabled(vm):
    return vm != 'original' and not vm.endswith('nojvmci')

def jol(args):
    """Java Object Layout"""
    joljar = mx.library('JOL_INTERNALS').get_path(resolve=True)
    candidates = mx.findclass(args, logToConsole=False, matcher=lambda s, classname: s == classname or classname.endswith('.' + s) or classname.endswith('$' + s))

    if len(candidates) > 0:
        candidates = mx.select_items(sorted(candidates))
    else:
        # mx.findclass can be mistaken, don't give up yet
        candidates = args

    vm(['-javaagent:' + joljar, '-cp', os.pathsep.join([mx.classpath(), joljar]), "org.openjdk.jol.MainObjectInternals"] + candidates)

def site(args):
    """create a website containing javadoc and the project dependency graph"""

    return mx.site(['--name', 'Graal',
                    '--jd', '@-tag', '--jd', '@test:X',
                    '--jd', '@-tag', '--jd', '@run:X',
                    '--jd', '@-tag', '--jd', '@bug:X',
                    '--jd', '@-tag', '--jd', '@summary:X',
                    '--jd', '@-tag', '--jd', '@vmoption:X',
                    '--overview', join(_graal_home, 'graal', 'overview.html'),
                    '--title', 'Graal OpenJDK Project Documentation',
                    '--dot-output-base', 'projects'] + args)

def findbugs(args):
    '''run FindBugs against non-test Java projects'''
    findBugsHome = mx.get_env('FINDBUGS_HOME', None)
    if findBugsHome:
        findbugsJar = join(findBugsHome, 'lib', 'findbugs.jar')
    else:
        findbugsLib = join(_graal_home, 'lib', 'findbugs-3.0.0')
        if not exists(findbugsLib):
            tmp = tempfile.mkdtemp(prefix='findbugs-download-tmp', dir=_graal_home)
            try:
                findbugsDist = mx.library('FINDBUGS_DIST').get_path(resolve=True)
                with zipfile.ZipFile(findbugsDist) as zf:
                    candidates = [e for e in zf.namelist() if e.endswith('/lib/findbugs.jar')]
                    assert len(candidates) == 1, candidates
                    libDirInZip = os.path.dirname(candidates[0])
                    zf.extractall(tmp)
                shutil.copytree(join(tmp, libDirInZip), findbugsLib)
            finally:
                shutil.rmtree(tmp)
        findbugsJar = join(findbugsLib, 'findbugs.jar')
    assert exists(findbugsJar)
    nonTestProjects = [p for p in mx.projects() if not p.name.endswith('.test') and not p.name.endswith('.jtt')]
    outputDirs = map(mx._cygpathU2W, [p.output_dir() for p in nonTestProjects])
    javaCompliance = max([p.javaCompliance for p in nonTestProjects])
    findbugsResults = join(_graal_home, 'findbugs.results')

    cmd = ['-jar', mx._cygpathU2W(findbugsJar), '-textui', '-low', '-maxRank', '15']
    if mx.is_interactive():
        cmd.append('-progress')
    cmd = cmd + ['-auxclasspath', mx._separatedCygpathU2W(mx.classpath([d.name for d in _jdkDeployedDists] + [p.name for p in nonTestProjects])), '-output', mx._cygpathU2W(findbugsResults), '-exitcode'] + args + outputDirs
    exitcode = mx.run_java(cmd, nonZeroIsFatal=False, javaConfig=mx.java(javaCompliance))
    if exitcode != 0:
        with open(findbugsResults) as fp:
            mx.log(fp.read())
    os.unlink(findbugsResults)
    return exitcode

def checkheaders(args):
    """check Java source headers against any required pattern"""
    failures = {}
    for p in mx.projects():
        if p.native:
            continue

        csConfig = join(mx.project(p.checkstyleProj).dir, '.checkstyle_checks.xml')
        dom = xml.dom.minidom.parse(csConfig)
        for module in dom.getElementsByTagName('module'):
            if module.getAttribute('name') == 'RegexpHeader':
                for prop in module.getElementsByTagName('property'):
                    if prop.getAttribute('name') == 'header':
                        value = prop.getAttribute('value')
                        matcher = re.compile(value, re.MULTILINE)
                        for sourceDir in p.source_dirs():
                            for root, _, files in os.walk(sourceDir):
                                for name in files:
                                    if name.endswith('.java') and name != 'package-info.java':
                                        f = join(root, name)
                                        with open(f) as fp:
                                            content = fp.read()
                                        if not matcher.match(content):
                                            failures[f] = csConfig
    for n, v in failures.iteritems():
        mx.log('{0}: header does not match RegexpHeader defined in {1}'.format(n, v))
    return len(failures)

def mx_init(suite):
    commands = {
        'checkheaders': [checkheaders, ''],
        'clean': [clean, ''],
        'findbugs': [findbugs, ''],
        'maven-install-truffle' : [maven_install_truffle, ''],
        'jdkhome': [print_jdkhome, ''],
        'gate' : [gate, '[-options]'],
        'unittest' : [unittest, '[unittest options] [--] [VM options] [filters...]', _unittestHelpSuffix],
        'shortunittest' : [shortunittest, '[unittest options] [--] [VM options] [filters...]', _unittestHelpSuffix],
        'site' : [site, '[-options]'],
        'sl' : [sl, '[SL args|@VM options]'],
        'sldebug' : [sldebug, '[SL args|@VM options]'],
        'jol' : [jol, ''],
    }

    mx.update_commands(suite, commands)

def mx_post_parse_cmd_line(opts):  #
    # TODO _minVersion check could probably be part of a Suite in mx?
    if mx.java().version < _minVersion:
        mx.abort('Requires Java version ' + str(_minVersion) + ' or greater for JAVA_HOME, got version ' + str(mx.java().version))
    if _untilVersion and mx.java().version >= _untilVersion:
        mx.abort('Requires Java version strictly before ' + str(_untilVersion) + ' for JAVA_HOME, got version ' + str(mx.java().version))

    for jdkDist in _jdkDeployedDists:
        def _close(jdkDeployable):
            def _install(dist):
                assert dist.name == jdkDeployable.name, dist.name + "!=" + jdkDeployable.name
                if not jdkDist.partOfHotSpot:
                    _installDistInJdks(jdkDeployable)
            return _install
        mx.distribution(jdkDist.name).add_update_listener(_close(jdkDist))
