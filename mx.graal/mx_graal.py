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

import os, stat, errno, sys, shutil, zipfile, tarfile, tempfile, re, time, datetime, platform, subprocess, StringIO, socket
from os.path import join, exists, dirname, basename
from argparse import ArgumentParser, REMAINDER
from outputparser import OutputParser, ValuesMatcher
import xml.dom.minidom
import sanitycheck
import itertools
import json, textwrap

import mx
import mx_unittest
import mx_findbugs
import mx_graal_makefile

_suite = mx.suite('graal')
_graal_home = _suite.dir

""" The VMs that can be built and run along with an optional description. Only VMs with a
    description are listed in the dialogue for setting the default VM (see _get_vm()). """
_vmChoices = {
    'jvmci' : 'VM triggered compilation is performed with a tiered system (C1 + Graal) and Graal is available for hosted compilation.',
    'server' : 'Normal compilation is performed with a tiered system (C1 + C2) and Graal is available for hosted compilation.',
    'client' : None,  # VM compilation with client compiler, hosted compilation with Graal
    'server-nojvmci' : None,  # all compilation with tiered system (i.e., client + server), JVMCI omitted
    'client-nojvmci' : None,  # all compilation with client compiler, JVMCI omitted
    'original' : None,  # default VM copied from bootstrap JDK
    'graal' : None, # alias for jvmci
    'server-nograal' : None,  # alias for server-nojvmci
    'client-nograal' : None,  # alias for client-nojvmci
}

""" The VM that will be run by the 'vm' command and built by default by the 'build' command.
    This can be set via the global '--vm' option or the DEFAULT_VM environment variable.
    It can also be temporarily set by using of a VM context manager object in a 'with' statement. """
_vm = None

""" The VM builds that will be run by the 'vm' command - default is first in list """
_vmbuildChoices = ['product', 'fastdebug', 'debug', 'optimized']

""" The VM build that will be run by the 'vm' command.
    This can be set via the global '--vmbuild' option.
    It can also be temporarily set by using of a VM context manager object in a 'with' statement. """
_vmbuild = _vmbuildChoices[0]

_jacoco = 'off'

""" The current working directory to switch to before running the VM. """
_vm_cwd = None

""" The base directory in which the JDKs cloned from $JAVA_HOME exist. """
_installed_jdks = None

""" Prefix for running the VM. """
_vm_prefix = None

_make_eclipse_launch = False

_minVersion = mx.VersionSpec('1.8')

# max version (first _unsupported_ version)
_untilVersion = None

class JDKDeployedDist:
    def __init__(self, name, isExtension=False, usesJVMCIClassLoader=False, partOfHotSpot=False):
        self.name = name
        self.isExtension = isExtension
        self.usesJVMCIClassLoader = usesJVMCIClassLoader
        self.partOfHotSpot = partOfHotSpot # true when this distribution is delivered with HotSpot

_jdkDeployedDists = [
    JDKDeployedDist('JVMCI_SERVICE', partOfHotSpot=True),
    JDKDeployedDist('JVMCI_API', usesJVMCIClassLoader=True, partOfHotSpot=True),
    JDKDeployedDist('JVMCI_HOTSPOT', usesJVMCIClassLoader=True, partOfHotSpot=True),
    JDKDeployedDist('GRAAL', usesJVMCIClassLoader=True),
    JDKDeployedDist('GRAAL_TRUFFLE', usesJVMCIClassLoader=True)
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
    envPath = join(_suite.mxDir, 'env')
    if vm and 'graal' in vm:
        if exists(envPath):
            with open(envPath) as fp:
                if 'DEFAULT_VM=' + vm in fp.read():
                    mx.log('Please update the DEFAULT_VM value in ' + envPath + ' to replace "graal" with "jvmci"')
        vm = vm.replace('graal', 'jvmci')
    if vm is None:
        if not mx.is_interactive():
            mx.abort('Need to specify VM with --vm option or DEFAULT_VM environment variable')
        mx.log('Please select the VM to be executed from the following: ')
        items = [k for k in _vmChoices.keys() if _vmChoices[k] is not None]
        descriptions = [_vmChoices[k] for k in _vmChoices.keys() if _vmChoices[k] is not None]
        vm = mx.select_items(items, descriptions, allowMultiple=False)
        mx.ask_persist_env('DEFAULT_VM', vm)
    _vm = vm
    return vm

"""
A context manager that can be used with the 'with' statement to set the VM
used by all VM executions within the scope of the 'with' statement. For example:

    with VM('server'):
        dacapo(['pmd'])
"""
class VM:
    def __init__(self, vm=None, build=None):
        assert vm is None or vm in _vmChoices.keys()
        assert build is None or build in _vmbuildChoices
        self.vm = vm if vm else _vm
        self.build = build if build else _vmbuild
        self.previousVm = _vm
        self.previousBuild = _vmbuild

    def __enter__(self):
        global _vm, _vmbuild
        _vm = self.vm
        _vmbuild = self.build

    def __exit__(self, exc_type, exc_value, traceback):
        global _vm, _vmbuild
        _vm = self.previousVm
        _vmbuild = self.previousBuild

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
        rmIfExists(_jdksDir())

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

    def _genFileArchPlatformName(archivtype, middle):
        return _genFileName(archivtype, infos['platform'] + '_' + infos['architecture'] + '_' + middle)


    # archive different build types of hotspot
    for vmBuild in _vmbuildChoices:
        jdkpath = join(_jdksDir(), vmBuild)
        if not exists(jdkpath):
            mx.logv("skipping " + vmBuild)
            continue

        tarName = _genFileArchPlatformName('basejdk', vmBuild)
        mx.logv("creating basejdk " + tarName)
        vmSet = set()
        with tarfile.open(tarName, 'w:gz') as tar:
            for root, _, files in os.walk(jdkpath):
                if basename(root) in _vmChoices.keys():
                    # TODO: add some assert to check path assumption
                    vmSet.add(root)
                    continue

                for f in files:
                    name = join(root, f)
                    # print name
                    tar.add(name, name)

            n = _writeJson("basejdk-" + vmBuild, {'vmbuild' : vmBuild})
            tar.add(n, n)

        # create a separate archive for each VM
        for vm in vmSet:
            bVm = basename(vm)
            vmTarName = _genFileArchPlatformName('vm', vmBuild + '_' + bVm)
            mx.logv("creating vm " + vmTarName)

            debugFiles = set()
            with tarfile.open(vmTarName, 'w:gz') as tar:
                for root, _, files in os.walk(vm):
                    for f in files:
                        # TODO: mac, windows, solaris?
                        if any(map(f.endswith, [".debuginfo"])):
                            debugFiles.add(f)
                        else:
                            name = join(root, f)
                            # print name
                            tar.add(name, name)

                n = _writeJson("vm-" + vmBuild + "-" + bVm, {'vmbuild' : vmBuild, 'vm' : bVm})
                tar.add(n, n)

            if len(debugFiles) > 0:
                debugTarName = _genFileArchPlatformName('debugfilesvm', vmBuild + '_' + bVm)
                mx.logv("creating debugfilesvm " + debugTarName)
                with tarfile.open(debugTarName, 'w:gz') as tar:
                    for f in debugFiles:
                        name = join(root, f)
                        # print name
                        tar.add(name, name)

                    n = _writeJson("debugfilesvm-" + vmBuild + "-" + bVm, {'vmbuild' : vmBuild, 'vm' : bVm})
                    tar.add(n, n)

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

    vmOpts, benchmarksAndOptions = mx.extract_VM_args(args, useDoubleDash=availableBenchmarks is None)

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

def dacapo(args):
    """run one or more DaCapo benchmarks"""

    def launcher(bm, harnessArgs, extraVmOpts):
        return sanitycheck.getDacapo(bm, harnessArgs).test(_get_vm(), extraVmOpts=extraVmOpts)

    _run_benchmark(args, sanitycheck.dacapoSanityWarmup.keys(), launcher)

def scaladacapo(args):
    """run one or more Scala DaCapo benchmarks"""

    def launcher(bm, harnessArgs, extraVmOpts):
        return sanitycheck.getScalaDacapo(bm, harnessArgs).test(_get_vm(), extraVmOpts=extraVmOpts)

    _run_benchmark(args, sanitycheck.dacapoScalaSanityWarmup.keys(), launcher)

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
    return os.path.abspath(join(_installed_jdks if _installed_jdks else _graal_home, 'jdk' + str(mx.java().version)))

def _handle_missing_VM(bld, vm=None):
    if not vm:
        vm = _get_vm()
    mx.log('The ' + bld + ' ' + vm + ' VM has not been created')
    if mx.is_interactive():
        if mx.ask_yes_no('Build it now', 'y'):
            with VM(vm, bld):
                build([])
            return
    mx.abort('You need to run "mx --vm ' + vm + ' --vmbuild ' + bld + ' build" to build the selected VM')

def _jdk(build=None, vmToCheck=None, create=False, installJars=True):
    """
    Get the JDK into which Graal is installed, creating it first if necessary.
    """
    if not build:
        build = _vmbuild
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
            if _installed_jdks:
                mx.log("The selected JDK directory does not (yet) exist: " + jdk)
            _handle_missing_VM(build, vmToCheck)

    if installJars:
        for jdkDist in _jdkDeployedDists:
            dist = mx.distribution(jdkDist.name)
            if exists(dist.path) and jdkDist.partOfHotSpot:
                _installDistInJdks(jdkDist)

    if vmToCheck is not None:
        jvmCfg = _vmCfgInJdk(jdk)
        found = False
        with open(jvmCfg) as f:
            for line in f:
                if line.strip() == '-' + vmToCheck + ' KNOWN':
                    found = True
                    break
        if not found:
            _handle_missing_VM(build, vmToCheck)

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
    hsSrcGenDir = join(mx.project('jdk.internal.jvmci.hotspot').source_gen_dir(), 'hotspot')
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

def _extractJVMCIFiles(jdkJars, jvmciJars, servicesDir, optionsDir):

    oldServices = os.listdir(servicesDir) if exists(servicesDir) else os.makedirs(servicesDir)
    oldOptions = os.listdir(optionsDir) if exists(optionsDir) else os.makedirs(optionsDir)

    jvmciServices = {}
    optionsFiles = []
    for jar in jvmciJars:
        if os.path.isfile(jar):
            with zipfile.ZipFile(jar) as zf:
                for member in zf.namelist():
                    if member.startswith('META-INF/jvmci.services/') and member != 'META-INF/jvmci.services/':
                        service = basename(member)
                        assert service != "", member
                        with zf.open(member) as serviceFile:
                            providers = jvmciServices.setdefault(service, [])
                            for line in serviceFile.readlines():
                                line = line.strip()
                                if line:
                                    providers.append(line)
                    elif member.startswith('META-INF/jvmci.options/') and member != 'META-INF/jvmci.options/':
                        filename = basename(member)
                        assert filename != "", member
                        targetpath = join(optionsDir, filename)
                        optionsFiles.append(filename)
                        with zf.open(member) as optionsFile, \
                             file(targetpath, "wb") as target:
                            shutil.copyfileobj(optionsFile, target)
                            if oldOptions and filename in oldOptions:
                                oldOptions.remove(filename)
    for service, providers in jvmciServices.iteritems():
        fd, tmp = tempfile.mkstemp(prefix=service)
        f = os.fdopen(fd, 'w+')
        for provider in providers:
            f.write(provider + os.linesep)
        target = join(servicesDir, service)
        f.close()
        shutil.move(tmp, target)
        if oldServices and service in oldServices:
            oldServices.remove(service)
        if mx.get_os() != 'windows':
            os.chmod(target, JDK_UNIX_PERMISSIONS_FILE)

    if mx.is_interactive():
        for d, files in [(servicesDir, oldServices), (optionsDir, oldOptions)]:
            if files and mx.ask_yes_no('These files in ' + d + ' look obsolete:\n  ' + '\n  '.join(files) + '\nDelete them', 'n'):
                for f in files:
                    path = join(d, f)
                    os.remove(path)
                    mx.log('Deleted ' + path)

def _updateJVMCIFiles(jdkDir):
    jreJVMCIDir = join(jdkDir, 'jre', 'lib', 'jvmci')
    jvmciJars = [join(jreJVMCIDir, e) for e in os.listdir(jreJVMCIDir) if e.endswith('.jar')]
    jreJVMCIServicesDir = join(jreJVMCIDir, 'services')
    jreJVMCIOptionsDir = join(jreJVMCIDir, 'options')
    _extractJVMCIFiles(_getJdkDeployedJars(jdkDir), jvmciJars, jreJVMCIServicesDir, jreJVMCIOptionsDir)

def _updateGraalPropertiesFile(jreLibDir):
    """
    Updates (or creates) 'jreLibDir'/jvmci/graal.properties to set/modify the
    graal.version property.
    """
    version = graal_version()
    graalProperties = join(jreLibDir, 'jvmci', 'graal.properties')
    if not exists(graalProperties):
        with open(graalProperties, 'w') as fp:
            print >> fp, 'graal.version=' + version
    else:
        content = []
        with open(graalProperties) as fp:
            for line in fp:
                if line.startswith('graal.version='):
                    content.append('graal.version=' + version)
                else:
                    content.append(line.rstrip(os.linesep))
        with open(graalProperties, 'w') as fp:
            fp.write(os.linesep.join(content))

def _installDistInJdks(deployableDist):
    """
    Installs the jar(s) for a given Distribution into all existing JVMCI JDKs
    """
    dist = mx.distribution(deployableDist.name)
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
                if dist.name == 'GRAAL':
                    _updateGraalPropertiesFile(jreLibDir)

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
    mx.log('Note that these variables can be given persistent values in the file ' + join(_suite.mxDir, 'env') + ' (see \'mx about\').')

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
            cached_graal_version = 'unknown-{0}-{1}'.format(platform.node(), time.strftime('%Y-%m-%d_%H-%M-%S_%Z'))

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

    if not opts2.native:
        return

    builds = [_vmbuild]

    if os.environ.get('BUILDING_FROM_IDE', None) == 'true':
        build = os.environ.get('IDE_BUILD_TARGET', None)
        if build is None or len(build) == 0:
            return
        if build not in _vmbuildChoices:
            mx.abort('VM build "' + build + '" specified by IDE_BUILD_TARGET environment variable is unknown (must be one of ' +
                     str(_vmbuildChoices) + ')')
        builds = [build]

    if vm is None:
        vm = _get_vm()

    if vm == 'original':
        pass
    elif vm.startswith('server'):
        buildSuffix = ''
    elif vm.startswith('client'):
        buildSuffix = '1'
    else:
        assert vm == 'jvmci', vm
        buildSuffix = 'jvmci'

    if _installed_jdks and _installed_jdks != _graal_home:
        if not mx.ask_yes_no("Warning: building while --installed-jdks is set (" + _installed_jdks + ") is not recommanded - are you sure you want to continue", 'n'):
            mx.abort(1)

    isWindows = platform.system() == 'Windows' or "CYGWIN" in platform.system()
    for build in builds:
        installJars = vm != 'original' and (isWindows or not opts2.java)
        jdk = _jdk(build, create=True, installJars=installJars)

        if vm == 'original':
            if build != 'product':
                mx.log('only product build of original VM exists')
            continue

        if not isVMSupported(vm):
            mx.log('The ' + vm + ' VM is not supported on this platform - skipping')
            continue

        vmDir = join(_vmLibDirInJdk(jdk), vm)
        if not exists(vmDir):
            chmodRecursive(jdk, JDK_UNIX_PERMISSIONS_DIR)
            mx.log('Creating VM directory in JDK: ' + vmDir)
            os.makedirs(vmDir)

        def filterXusage(line):
            if not 'Xusage.txt' in line:
                sys.stderr.write(line + os.linesep)

        # Check if a build really needs to be done
        timestampFile = join(vmDir, '.build-timestamp')
        if opts2.force or not exists(timestampFile):
            mustBuild = True
        else:
            mustBuild = False
            timestamp = os.path.getmtime(timestampFile)
            sources = []
            for d in ['src', 'make', join('jvmci', 'jdk.internal.jvmci.hotspot', 'src_gen', 'hotspot')]:
                for root, dirnames, files in os.walk(join(_graal_home, d)):
                    # ignore <graal>/src/share/tools
                    if root == join(_graal_home, 'src', 'share'):
                        dirnames.remove('tools')
                    sources += [join(root, name) for name in files]
            for f in sources:
                if len(f) != 0 and os.path.getmtime(f) > timestamp:
                    mustBuild = True
                    break

        if not mustBuild:
            mx.logv('[all files in src and make directories are older than ' + timestampFile[len(_graal_home) + 1:] + ' - skipping native build]')
            continue

        if isWindows:
            t_compilelogfile = mx._cygpathU2W(os.path.join(_graal_home, "graalCompile.log"))
            mksHome = mx.get_env('MKS_HOME', 'C:\\cygwin\\bin')

            variant = {'client': 'compiler1', 'server': 'compiler2'}.get(vm, vm)
            project_config = variant + '_' + build
            t_graal_home = mx._cygpathU2W(_graal_home)
            _runInDebugShell('msbuild ' + t_graal_home + r'\build\vs-amd64\jvm.vcproj /p:Configuration=' + project_config + ' /target:clean', t_graal_home)
            winCompileCmd = r'set HotSpotMksHome=' + mksHome + r'& set OUT_DIR=' + mx._cygpathU2W(jdk) + r'& set JAVA_HOME=' + mx._cygpathU2W(jdk) + r'& set path=%JAVA_HOME%\bin;%path%;%HotSpotMksHome%& cd /D "' + t_graal_home + r'\make\windows"& call create.bat ' + t_graal_home
            print winCompileCmd
            winCompileSuccess = re.compile(r"^Writing \.vcxproj file:")
            if not _runInDebugShell(winCompileCmd, t_graal_home, t_compilelogfile, winCompileSuccess):
                mx.log('Error executing create command')
                return
            winBuildCmd = 'msbuild ' + t_graal_home + r'\build\vs-amd64\jvm.vcxproj /p:Configuration=' + project_config + ' /p:Platform=x64'
            if not _runInDebugShell(winBuildCmd, t_graal_home, t_compilelogfile):
                mx.log('Error building project')
                return
        else:
            cpus = mx.cpu_count()
            makeDir = join(_graal_home, 'make')
            runCmd = [mx.gmake_cmd(), '-C', makeDir]

            env = os.environ.copy()

            # These must be passed as environment variables
            env.setdefault('LANG', 'C')
            env['JAVA_HOME'] = jdk

            def setMakeVar(name, default, env=None):
                """Sets a make variable on the command line to the value
                   of the variable in 'env' with the same name if defined
                   and 'env' is not None otherwise to 'default'
                """
                runCmd.append(name + '=' + (env.get(name, default) if env else default))

            if opts2.D:
                for nv in opts2.D:
                    name, value = nv.split('=', 1)
                    setMakeVar(name.strip(), value)

            setMakeVar('ARCH_DATA_MODEL', '64', env=env)
            setMakeVar('HOTSPOT_BUILD_JOBS', str(cpus), env=env)
            setMakeVar('ALT_BOOTDIR', mx.java().jdk, env=env)
            setMakeVar("EXPORT_PATH", jdk)

            setMakeVar('MAKE_VERBOSE', 'y' if mx._opts.verbose else '')
            if vm.endswith('nojvmci'):
                setMakeVar('INCLUDE_JVMCI', 'false')
                setMakeVar('ALT_OUTPUTDIR', join(_graal_home, 'build-nojvmci', mx.get_os()), env=env)
            else:
                version = graal_version()
                setMakeVar('USER_RELEASE_SUFFIX', 'jvmci-' + version)
                setMakeVar('INCLUDE_JVMCI', 'true')
            setMakeVar('INSTALL', 'y', env=env)
            if mx.get_os() == 'darwin' and platform.mac_ver()[0] != '':
                # Force use of clang on MacOS
                setMakeVar('USE_CLANG', 'true')
            if mx.get_os() == 'solaris':
                # If using sparcWorks, setup flags to avoid make complaining about CC version
                cCompilerVersion = subprocess.Popen('CC -V', stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True).stderr.readlines()[0]
                if cCompilerVersion.startswith('CC: Sun C++'):
                    compilerRev = cCompilerVersion.split(' ')[3]
                    setMakeVar('ENFORCE_COMPILER_REV', compilerRev, env=env)
                    setMakeVar('ENFORCE_CC_COMPILER_REV', compilerRev, env=env)
                    if build == 'jvmg':
                        # We want ALL the symbols when debugging on Solaris
                        setMakeVar('STRIP_POLICY', 'no_strip')
            # This removes the need to unzip the *.diz files before debugging in gdb
            setMakeVar('ZIP_DEBUGINFO_FILES', '0', env=env)

            if buildSuffix == "1":
                setMakeVar("BUILD_CLIENT_ONLY", "true")

            # Clear this variable as having it set can cause very confusing build problems
            env.pop('CLASSPATH', None)

            # Issue an env prefix that can be used to run the make on the command line
            if not mx._opts.verbose:
                mx.log('--------------- make command line ----------------------')

            envPrefix = ' '.join([key + '=' + env[key] for key in env.iterkeys() if not os.environ.has_key(key) or env[key] != os.environ[key]])
            if len(envPrefix):
                mx.log('env ' + envPrefix + ' \\')

            runCmd.append(build + buildSuffix)
            runCmd.append("docs")
            runCmd.append("export_" + build)

            if not mx._opts.verbose:
                mx.log(' '.join(runCmd))
                mx.log('--------------------------------------------------------')
            mx.run(runCmd, err=filterXusage, env=env)

        jvmCfg = _vmCfgInJdk(jdk)
        if not exists(jvmCfg):
            mx.abort(jvmCfg + ' does not exist')

        prefix = '-' + vm + ' '
        vmKnown = prefix + 'KNOWN\n'
        lines = []
        found = False
        with open(jvmCfg) as f:
            for line in f:
                if line.strip() == vmKnown.strip():
                    found = True
                lines.append(line)

        if not found:
            mx.log('Prepending "' + prefix + 'KNOWN" to ' + jvmCfg)
            if mx.get_os() != 'windows':
                os.chmod(jvmCfg, JDK_UNIX_PERMISSIONS_FILE)
            with open(jvmCfg, 'w') as f:
                written = False
                for line in lines:
                    if line.startswith('#'):
                        f.write(line)
                        continue
                    if not written:
                        f.write(vmKnown)
                        if vm == 'jvmci':
                            # Legacy support
                            f.write('-graal ALIASED_TO -jvmci\n')
                        written = True
                    if line.startswith(prefix):
                        line = vmKnown
                        if written:
                            continue
                    f.write(line)

        for jdkDist in _jdkDeployedDists: # Install non HotSpot distribution
            if not jdkDist.partOfHotSpot:
                _installDistInJdks(jdkDist)
        if exists(timestampFile):
            os.utime(timestampFile, None)
        else:
            file(timestampFile, 'a')

def vmg(args):
    """run the debug build of VM selected by the '--vm' option"""
    return vm(args, vmbuild='debug')

def vmfg(args):
    """run the fastdebug build of VM selected by the '--vm' option"""
    return vm(args, vmbuild='fastdebug')

def _parseVmArgs(args, vm=None, cwd=None, vmbuild=None):
    """run the VM selected by the '--vm' option"""

    if vm is None:
        vm = _get_vm()

    if not isVMSupported(vm):
        mx.abort('The ' + vm + ' is not supported on this platform')

    if cwd is None:
        cwd = _vm_cwd
    elif _vm_cwd is not None and _vm_cwd != cwd:
        mx.abort("conflicting working directories: do not set --vmcwd for this command")

    build = vmbuild if vmbuild else _vmbuild
    jdk = _jdk(build, vmToCheck=vm, installJars=False)
    _updateInstalledJVMCIOptionsFile(jdk)
    mx.expand_project_in_args(args)
    if _make_eclipse_launch:
        mx.make_eclipse_launch(_suite, args, 'graal-' + build, name=None, deps=mx.project('com.oracle.graal.hotspot').all_deps([], True))
    if _jacoco == 'on' or _jacoco == 'append':
        jacocoagent = mx.library("JACOCOAGENT", True)
        # Exclude all compiler tests and snippets

        includes = ['com.oracle.graal.*', 'jdk.internal.jvmci.*']
        baseExcludes = []
        for p in mx.projects():
            projsetting = getattr(p, 'jacoco', '')
            if projsetting == 'exclude':
                baseExcludes.append(p.name)
            if projsetting == 'include':
                includes.append(p.name + '.*')

        def _filter(l):
            # filter out specific classes which are already covered by a baseExclude package
            return [clazz for clazz in l if not any([clazz.startswith(package) for package in baseExcludes])]
        excludes = []
        for p in mx.projects():
            excludes += _filter(_find_classes_with_annotations(p, None, ['@Snippet', '@ClassSubstitution', '@Test'], includeInnerClasses=True).keys())
            excludes += _filter(p.find_classes_with_matching_source_line(None, lambda line: 'JaCoCo Exclude' in line, includeInnerClasses=True).keys())

        excludes += [package + '.*' for package in baseExcludes]
        agentOptions = {
                        'append' : 'true' if _jacoco == 'append' else 'false',
                        'bootclasspath' : 'true',
                        'includes' : ':'.join(includes),
                        'excludes' : ':'.join(excludes),
                        'destfile' : 'jacoco.exec'
        }
        args = ['-javaagent:' + jacocoagent.get_path(True) + '=' + ','.join([k + '=' + v for k, v in agentOptions.items()])] + args
    exe = join(jdk, 'bin', mx.exe_suffix('java'))
    pfx = _vm_prefix.split() if _vm_prefix is not None else []

    if '-version' in args:
        ignoredArgs = args[args.index('-version') + 1:]
        if  len(ignoredArgs) > 0:
            mx.log("Warning: The following options will be ignored by the vm because they come after the '-version' argument: " + ' '.join(ignoredArgs))

    # Unconditionally prepend truffle.jar to the boot class path.
    # This used to be done by the VM itself but was removed to
    # separate the VM from Truffle.
    truffle_jar = mx.library('TRUFFLE').path
    args = ['-Xbootclasspath/p:' + truffle_jar] + args

    args = mx.java().processArgs(args)
    return (pfx, exe, vm, args, cwd)

def vm(args, vm=None, nonZeroIsFatal=True, out=None, err=None, cwd=None, timeout=None, vmbuild=None):
    (pfx_, exe_, vm_, args_, cwd) = _parseVmArgs(args, vm, cwd, vmbuild)
    return mx.run(pfx_ + [exe_, '-' + vm_] + args_, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, cwd=cwd, timeout=timeout)

def _find_classes_with_annotations(p, pkgRoot, annotations, includeInnerClasses=False):
    """
    Scan the sources of project 'p' for Java source files containing a line starting with 'annotation'
    (ignoring preceding whitespace) and return the fully qualified class name for each Java
    source file matched in a list.
    """

    matches = lambda line: len([a for a in annotations if line == a or line.startswith(a + '(')]) != 0
    return p.find_classes_with_matching_source_line(pkgRoot, matches, includeInnerClasses)

def _find_classpath_arg(vmArgs):
    for index in range(len(vmArgs)):
        if vmArgs[index] in ['-cp', '-classpath']:
            return index + 1, vmArgs[index + 1]

def unittest(args):
    def vmLauncher(vmArgs, mainClass, mainClassArgs):
        if isJVMCIEnabled(_get_vm()):
            # Remove entries from class path that are in JVMCI loaded jars
            cpIndex, cp = _find_classpath_arg(vmArgs)
            if cp:
                excluded = set()
                for jdkDist in _jdkDeployedDists:
                    dist = mx.distribution(jdkDist.name)
                    excluded.update([d.output_dir() for d in dist.sorted_deps()])
                cp = os.pathsep.join([e for e in cp.split(os.pathsep) if e not in excluded])
                vmArgs[cpIndex] = cp

            # Run the VM in a mode where application/test classes can
            # access JVMCI loaded classes.
            vmArgs = ['-XX:-UseJVMCIClassLoader'] + vmArgs

        vm(vmArgs + [mainClass] + mainClassArgs)
    mx_unittest.unittest(args, vmLauncher=vmLauncher)

def shortunittest(args):
    """alias for 'unittest --whitelist test/whitelist_shortunittest.txt'"""

    args = ['--whitelist', 'test/whitelist_shortunittest.txt'] + args
    unittest(args)

def microbench(args):
    """run JMH microbenchmark projects"""
    vmArgs, jmhArgs = mx.extract_VM_args(args, useDoubleDash=True)

    # look for -f in JMH arguments
    containsF = False
    forking = True
    for i in range(len(jmhArgs)):
        arg = jmhArgs[i]
        if arg.startswith('-f'):
            containsF = True
            if arg == '-f' and (i+1) < len(jmhArgs):
                arg += jmhArgs[i+1]
            try:
                if int(arg[2:]) == 0:
                    forking = False
            except ValueError:
                pass

    # default to -f1 if not specified otherwise
    if not containsF:
        jmhArgs += ['-f1']

    # find all projects with a direct JMH dependency
    jmhProjects = []
    for p in mx.projects():
        if 'JMH' in p.deps:
            jmhProjects.append(p.name)
    cp = mx.classpath(jmhProjects)

    # execute JMH runner
    args = ['-cp', cp]
    if not forking:
        args += vmArgs
    args += ['org.openjdk.jmh.Main']
    if forking:
        (_, _, jvm, _, _) = _parseVmArgs(vmArgs)
        args += ['--jvmArgsPrepend', ' '.join(['-' + jvm] + vmArgs)]
    vm(args + jmhArgs)

def buildvms(args):
    """build one or more VMs in various configurations"""

    vmsDefault = ','.join(_vmChoices.keys())
    vmbuildsDefault = ','.join(_vmbuildChoices)

    parser = ArgumentParser(prog='mx buildvms')
    parser.add_argument('--vms', help='a comma separated list of VMs to build (default: ' + vmsDefault + ')', metavar='<args>', default=vmsDefault)
    parser.add_argument('--builds', help='a comma separated list of build types (default: ' + vmbuildsDefault + ')', metavar='<args>', default=vmbuildsDefault)
    parser.add_argument('--check-distributions', action='store_true', dest='check_distributions', help='check built distributions for overlap')
    parser.add_argument('-n', '--no-check', action='store_true', help='omit running "java -version" after each build')
    parser.add_argument('-c', '--console', action='store_true', help='send build output to console instead of log file')

    args = parser.parse_args(args)
    vms = args.vms.split(',')
    builds = args.builds.split(',')

    allStart = time.time()
    check_dists_args = ['--check-distributions'] if args.check_distributions else []
    for v in vms:
        if not isVMSupported(v):
            mx.log('The ' + v + ' VM is not supported on this platform - skipping')
            continue

        for vmbuild in builds:
            if v == 'original' and vmbuild != 'product':
                continue
            if not args.console:
                logFile = join(v + '-' + vmbuild + '.log')
                log = open(join(_graal_home, logFile), 'wb')
                start = time.time()
                mx.log('BEGIN: ' + v + '-' + vmbuild + '\t(see: ' + logFile + ')')
                verbose = ['-v'] if mx._opts.verbose else []
                # Run as subprocess so that output can be directed to a file
                cmd = [sys.executable, '-u', join('mxtool', 'mx.py')] + verbose + ['--vm', v, '--vmbuild', vmbuild, 'build'] + check_dists_args
                mx.logv("executing command: " + str(cmd))
                subprocess.check_call(cmd, cwd=_graal_home, stdout=log, stderr=subprocess.STDOUT)
                duration = datetime.timedelta(seconds=time.time() - start)
                mx.log('END:   ' + v + '-' + vmbuild + '\t[' + str(duration) + ']')
            else:
                with VM(v, vmbuild):
                    build(check_dists_args)
            if not args.no_check:
                vmargs = ['-version']
                if v == 'jvmci':
                    vmargs.insert(0, '-XX:-BootstrapJVMCI')
                vm(vmargs, vm=v, vmbuild=vmbuild)
    allDuration = datetime.timedelta(seconds=time.time() - allStart)
    mx.log('TOTAL TIME:   ' + '[' + str(allDuration) + ']')

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

def ctw(args):
    """run CompileTheWorld"""

    defaultCtwopts = '-Inline'

    parser = ArgumentParser(prog='mx ctw')
    parser.add_argument('--ctwopts', action='store', help='space separated JVMCI options used for CTW compilations (default: --ctwopts="' + defaultCtwopts + '")', default=defaultCtwopts, metavar='<options>')
    parser.add_argument('--jar', action='store', help='jar of classes to compiled instead of rt.jar', metavar='<path>')

    args, vmargs = parser.parse_known_args(args)

    if args.ctwopts:
        vmargs.append('-G:CompileTheWorldConfig=' + args.ctwopts)

    if args.jar:
        jar = os.path.abspath(args.jar)
    else:
        jar = join(_jdk(installJars=False), 'jre', 'lib', 'rt.jar')
        vmargs.append('-G:CompileTheWorldExcludeMethodFilter=sun.awt.X11.*.*')

    vmargs += ['-XX:+CompileTheWorld']
    vm_ = _get_vm()
    if isJVMCIEnabled(vm_):
        if vm_ == 'jvmci':
            vmargs += ['-XX:+BootstrapJVMCI']
        vmargs += ['-G:CompileTheWorldClasspath=' + jar]
    else:
        vmargs += ['-Xbootclasspath/p:' + jar]

    # suppress menubar and dock when running on Mac; exclude x11 classes as they may cause vm crashes (on Solaris)
    vmargs = ['-Djava.awt.headless=true'] + vmargs

    vm(vmargs)

def _basic_gate_body(args, tasks):
    # Build server-hosted-jvmci now so we can run the unit tests
    with Task('BuildHotSpotJVMCIHosted: product', tasks) as t:
        if t: buildvms(['--vms', 'server', '--builds', 'product', '--check-distributions'])

    # Run unit tests on server-hosted-jvmci
    with VM('server', 'product'):
        with Task('UnitTests:hosted-product', tasks) as t:
            if t: unittest(['--enable-timing', '--verbose', '--fail-fast'])

    # Run unit tests on server-hosted-jvmci with -G:-SSA_LIR
    with VM('server', 'product'):
        with Task('UnitTestsNonSSA:hosted-product', tasks) as t:
            if t: unittest(['--enable-timing', '--verbose', '--fail-fast', '-G:-SSA_LIR'])
    # Run ctw against rt.jar on server-hosted-jvmci
    with VM('server', 'product'):
        with Task('CTW:hosted-product', tasks) as t:
            if t: ctw(['--ctwopts', '-Inline +ExitVMOnException', '-esa', '-G:+CompileTheWorldMultiThreaded', '-G:-InlineDuringParsing', '-G:-CompileTheWorldVerbose'])

    # Build the other VM flavors
    with Task('BuildHotSpotGraalOthers: fastdebug,product', tasks) as t:
        if t: buildvms(['--vms', 'jvmci,server', '--builds', 'fastdebug,product', '--check-distributions'])

    with VM('jvmci', 'fastdebug'):
        with Task('BootstrapWithSystemAssertions:fastdebug', tasks) as t:
            if t: vm(['-esa', '-XX:-TieredCompilation', '-version'])

    with VM('jvmci', 'fastdebug'):
        with Task('BootstrapEconomyWithSystemAssertions:fastdebug', tasks) as t:
            if t: vm(['-esa', '-XX:-TieredCompilation', '-G:CompilerConfiguration=economy', '-version'])

    with VM('jvmci', 'fastdebug'):
        with Task('BootstrapWithSystemAssertionsNoCoop:fastdebug', tasks) as t:
            if t: vm(['-esa', '-XX:-TieredCompilation', '-XX:-UseCompressedOops', '-version'])

    with VM('jvmci', 'fastdebug'):
        with Task('BootstrapWithExceptionEdges:fastdebug', tasks) as t:
            if t: vm(['-esa', '-XX:-TieredCompilation', '-G:+StressInvokeWithExceptionNode', '-version'])

    with VM('jvmci', 'product'):
        with Task('BootstrapWithGCVerification:product', tasks) as t:
            if t:
                out = mx.DuplicateSuppressingStream(['VerifyAfterGC:', 'VerifyBeforeGC:']).write
                vm(['-XX:-TieredCompilation', '-XX:+UnlockDiagnosticVMOptions', '-XX:+VerifyBeforeGC', '-XX:+VerifyAfterGC', '-version'], out=out)

    with VM('jvmci', 'product'):
        with Task('BootstrapWithG1GCVerification:product', tasks) as t:
            if t:
                out = mx.DuplicateSuppressingStream(['VerifyAfterGC:', 'VerifyBeforeGC:']).write
                vm(['-XX:-TieredCompilation', '-XX:+UnlockDiagnosticVMOptions', '-XX:-UseSerialGC', '-XX:+UseG1GC', '-XX:+VerifyBeforeGC', '-XX:+VerifyAfterGC', '-version'], out=out)

    with VM('jvmci', 'product'):
        with Task('BootstrapWithRegisterPressure:product', tasks) as t:
            if t:
                registers = 'o0,o1,o2,o3,f8,f9,d32,d34' if platform.processor() == 'sparc' else 'rbx,r11,r10,r14,xmm3,xmm11,xmm14'
                vm(['-XX:-TieredCompilation', '-G:RegisterPressure=' + registers, '-esa', '-version'])

    with VM('jvmci', 'product'):
        with Task('BootstrapNonSSAWithRegisterPressure:product', tasks) as t:
            if t:
                registers = 'o0,o1,o2,o3,f8,f9,d32,d34' if platform.processor() == 'sparc' else 'rbx,r11,r10,r14,xmm3,xmm11,xmm14'
                vm(['-XX:-TieredCompilation', '-G:-SSA_LIR', '-G:RegisterPressure=' + registers, '-esa', '-version'])

    with VM('jvmci', 'product'):
        with Task('BootstrapWithImmutableCode:product', tasks) as t:
            if t: vm(['-XX:-TieredCompilation', '-G:+ImmutableCode', '-G:+VerifyPhases', '-esa', '-version'])

    for vmbuild in ['fastdebug', 'product']:
        for test in sanitycheck.getDacapos(level=sanitycheck.SanityCheckLevel.Gate, gateBuildLevel=vmbuild) + sanitycheck.getScalaDacapos(level=sanitycheck.SanityCheckLevel.Gate, gateBuildLevel=vmbuild):
            with Task(str(test) + ':' + vmbuild, tasks) as t:
                if t and not test.test('jvmci'):
                    t.abort(test.name + ' Failed')

    # ensure -Xbatch still works
    with VM('jvmci', 'product'):
        with Task('DaCapo_pmd:BatchMode:product', tasks) as t:
            if t: dacapo(['-Xbatch', 'pmd'])

    # ensure -Xcomp still works
    with VM('jvmci', 'product'):
        with Task('XCompMode:product', tasks) as t:
            if t: vm(['-Xcomp', '-version'])

    if args.jacocout is not None:
        jacocoreport([args.jacocout])

    global _jacoco
    _jacoco = 'off'

    with Task('CleanAndBuildIdealGraphVisualizer', tasks) as t:
        if t and platform.processor() != 'sparc':
            buildxml = mx._cygpathU2W(join(_graal_home, 'src', 'share', 'tools', 'IdealGraphVisualizer', 'build.xml'))
            mx.run(['ant', '-f', buildxml, '-q', 'clean', 'build'], env=_igvBuildEnv())

    # Prevent JVMCI modifications from breaking the standard builds
    if args.buildNonJVMCI:
        with Task('BuildHotSpotVarieties', tasks) as t:
            if t:
                buildvms(['--vms', 'client,server', '--builds', 'fastdebug,product'])
                if mx.get_os() not in ['windows', 'cygwin']:
                    buildvms(['--vms', 'server-nojvmci', '--builds', 'product,optimized'])

        for vmbuild in ['product', 'fastdebug']:
            for theVm in ['client', 'server']:
                if not isVMSupported(theVm):
                    mx.log('The ' + theVm + ' VM is not supported on this platform')
                    continue
                with VM(theVm, vmbuild):
                    with Task('DaCapo_pmd:' + theVm + ':' + vmbuild, tasks) as t:
                        if t: dacapo(['pmd'])

                    with Task('UnitTests:' + theVm + ':' + vmbuild, tasks) as t:
                        if t: unittest(['-XX:CompileCommand=exclude,*::run*', 'graal.api', 'java.test'])


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
    parser.add_argument('--jacocout', help='specify the output directory for jacoco report')

    args = parser.parse_args(args)

    global _jacoco
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
        with Task('Check jvmci.make in sync with suite.py', tasks) as t:
            if t:
                jvmciMake = join('make', 'jvmci.make')
                if mx_graal_makefile.build_makefile(['-o', jvmciMake]) != 0:
                    t.abort('Rerun "mx makefile -o ' + jvmciMake + ' and check-in the modified ' + jvmciMake)

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
                    t.abort('Rerun "mx canonicalizeprojects" and check-in the modified mx/suite*.py files.')

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
            if t and mx_findbugs.findbugs([]) != 0:
                t.abort('FindBugs warnings were found')

        if exists('jacoco.exec'):
            os.unlink('jacoco.exec')

        if args.jacocout is not None:
            _jacoco = 'append'
        else:
            _jacoco = 'off'

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

def deoptalot(args):
    """bootstrap a VM with DeoptimizeALot and VerifyOops on

    If the first argument is a number, the process will be repeated
    this number of times. All other arguments are passed to the VM."""
    count = 1
    if len(args) > 0 and args[0].isdigit():
        count = int(args[0])
        del args[0]

    for _ in range(count):
        if not vm(['-XX:-TieredCompilation', '-XX:+DeoptimizeALot', '-XX:+VerifyOops'] + args + ['-version']) == 0:
            mx.abort("Failed")

def longtests(args):

    deoptalot(['15', '-Xmx48m'])

    dacapo(['100', 'eclipse', '-esa'])

def _igvJdk():
    v8u20 = mx.VersionSpec("1.8.0_20")
    v8u40 = mx.VersionSpec("1.8.0_40")
    v8 = mx.VersionSpec("1.8")
    def _igvJdkVersionCheck(version):
        return version >= v8 and (version < v8u20 or version >= v8u40)
    return mx.java(_igvJdkVersionCheck, versionDescription='>= 1.8 and < 1.8.0u20 or >= 1.8.0u40', purpose="building & running IGV").jdk

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

def bench(args):
    """run benchmarks and parse their output for results

    Results are JSON formated : {group : {benchmark : score}}."""
    resultFile = None
    if '-resultfile' in args:
        index = args.index('-resultfile')
        if index + 1 < len(args):
            resultFile = args[index + 1]
            del args[index]
            del args[index]
        else:
            mx.abort('-resultfile must be followed by a file name')
    vm = _get_vm()
    if len(args) is 0:
        args = ['all']

    vmArgs = [arg for arg in args if arg.startswith('-')]

    def benchmarks_in_group(group):
        prefix = group + ':'
        return [a[len(prefix):] for a in args if a.startswith(prefix)]

    results = {}
    benchmarks = []
    # DaCapo
    if 'dacapo' in args or 'all' in args:
        benchmarks += sanitycheck.getDacapos(level=sanitycheck.SanityCheckLevel.Benchmark)
    else:
        dacapos = benchmarks_in_group('dacapo')
        for dacapo in dacapos:
            if dacapo not in sanitycheck.dacapoSanityWarmup.keys():
                mx.abort('Unknown DaCapo : ' + dacapo)
            iterations = sanitycheck.dacapoSanityWarmup[dacapo][sanitycheck.SanityCheckLevel.Benchmark]
            if iterations > 0:
                benchmarks += [sanitycheck.getDacapo(dacapo, ['-n', str(iterations)])]

    if 'scaladacapo' in args or 'all' in args:
        benchmarks += sanitycheck.getScalaDacapos(level=sanitycheck.SanityCheckLevel.Benchmark)
    else:
        scaladacapos = benchmarks_in_group('scaladacapo')
        for scaladacapo in scaladacapos:
            if scaladacapo not in sanitycheck.dacapoScalaSanityWarmup.keys():
                mx.abort('Unknown Scala DaCapo : ' + scaladacapo)
            iterations = sanitycheck.dacapoScalaSanityWarmup[scaladacapo][sanitycheck.SanityCheckLevel.Benchmark]
            if iterations > 0:
                benchmarks += [sanitycheck.getScalaDacapo(scaladacapo, ['-n', str(iterations)])]

    # Bootstrap
    if 'bootstrap' in args or 'all' in args:
        benchmarks += sanitycheck.getBootstraps()
    # SPECjvm2008
    if 'specjvm2008' in args or 'all' in args:
        benchmarks += [sanitycheck.getSPECjvm2008(['-ikv', '-wt', '120', '-it', '120'])]
    else:
        specjvms = benchmarks_in_group('specjvm2008')
        for specjvm in specjvms:
            benchmarks += [sanitycheck.getSPECjvm2008(['-ikv', '-wt', '120', '-it', '120', specjvm])]

    if 'specjbb2005' in args or 'all' in args:
        benchmarks += [sanitycheck.getSPECjbb2005()]

    if 'specjbb2013' in args:  # or 'all' in args //currently not in default set
        benchmarks += [sanitycheck.getSPECjbb2013()]

    if 'ctw-full' in args:
        benchmarks.append(sanitycheck.getCTW(vm, sanitycheck.CTWMode.Full))
    if 'ctw-noinline' in args:
        benchmarks.append(sanitycheck.getCTW(vm, sanitycheck.CTWMode.NoInline))

    for test in benchmarks:
        for (groupName, res) in test.bench(vm, extraVmOpts=vmArgs).items():
            group = results.setdefault(groupName, {})
            group.update(res)
    mx.log(json.dumps(results))
    if resultFile:
        with open(resultFile, 'w') as f:
            f.write(json.dumps(results))

def _get_jmh_path():
    path = mx.get_env('JMH_BENCHMARKS', None)
    if not path:
        probe = join(dirname(_graal_home), 'java-benchmarks')
        if exists(probe):
            path = probe

    if not path:
        mx.abort("Please set the JMH_BENCHMARKS environment variable to point to the java-benchmarks workspace")
    if not exists(path):
        mx.abort("The directory denoted by the JMH_BENCHMARKS environment variable does not exist: " + path)
    return path

def makejmhdeps(args):
    """creates and installs Maven dependencies required by the JMH benchmarks

    The dependencies are specified by files named pom.mxdeps in the
    JMH directory tree. Each such file contains a list of dependencies
    defined in JSON format. For example:

    '[{"artifactId" : "compiler.test", "groupId" : "com.oracle.graal", "deps" : ["com.oracle.graal.compiler.test"]}]'

    will result in a dependency being installed in the local Maven repository
    that can be referenced in a pom.xml file as follows:

          <dependency>
            <groupId>com.oracle.graal</groupId>
            <artifactId>compiler.test</artifactId>
            <version>1.0-SNAPSHOT</version>
          </dependency>"""

    parser = ArgumentParser(prog='mx makejmhdeps')
    parser.add_argument('-s', '--settings', help='alternative path for Maven user settings file', metavar='<path>')
    parser.add_argument('-p', '--permissive', action='store_true', help='issue note instead of error if a Maven dependency cannot be built due to missing projects/libraries')
    args = parser.parse_args(args)

    def makejmhdep(artifactId, groupId, deps):
        graalSuite = mx.suite("graal")
        path = artifactId + '.jar'
        if args.permissive:
            allDeps = []
            for name in deps:
                dist = mx.distribution(name, fatalIfMissing=False)
                if dist:
                    allDeps = allDeps + [d.name for d in dist.sorted_deps(transitive=True)]
                else:
                    if not mx.project(name, fatalIfMissing=False):
                        if not mx.library(name, fatalIfMissing=False):
                            mx.log('Skipping dependency ' + groupId + '.' + artifactId + ' as ' + name + ' cannot be resolved')
                            return
                    allDeps.append(name)
        d = mx.Distribution(graalSuite, name=artifactId, path=path, sourcesPath=path, deps=allDeps, mainClass=None, excludedDependencies=[], distDependencies=[], javaCompliance=None)
        d.make_archive()
        cmd = ['mvn', 'install:install-file', '-DgroupId=' + groupId, '-DartifactId=' + artifactId,
               '-Dversion=1.0-SNAPSHOT', '-Dpackaging=jar', '-Dfile=' + d.path]
        if not mx._opts.verbose:
            cmd.append('-q')
        if args.settings:
            cmd = cmd + ['-s', args.settings]
        mx.run(cmd)
        os.unlink(d.path)

    jmhPath = _get_jmh_path()
    for root, _, filenames in os.walk(jmhPath):
        for f in [join(root, n) for n in filenames if n == 'pom.mxdeps']:
            mx.logv('[processing ' + f + ']')
            try:
                with open(f) as fp:
                    for d in json.load(fp):
                        artifactId = d['artifactId']
                        groupId = d['groupId']
                        deps = d['deps']
                        makejmhdep(artifactId, groupId, deps)
            except ValueError as e:
                mx.abort('Error parsing {0}:\n{1}'.format(f, e))

def buildjmh(args):
    """build the JMH benchmarks"""

    parser = ArgumentParser(prog='mx buildjmh')
    parser.add_argument('-s', '--settings', help='alternative path for Maven user settings file', metavar='<path>')
    parser.add_argument('-c', action='store_true', dest='clean', help='clean before building')
    args = parser.parse_args(args)

    jmhPath = _get_jmh_path()
    mx.log('JMH benchmarks: ' + jmhPath)

    # Ensure the mx injected dependencies are up to date
    makejmhdeps(['-p'] + (['-s', args.settings] if args.settings else []))

    timestamp = mx.TimeStampFile(join(_suite.mxDir, 'jmh', jmhPath.replace(os.sep, '_') + '.timestamp'))
    mustBuild = args.clean
    if not mustBuild:
        try:
            hgfiles = [join(jmhPath, f) for f in subprocess.check_output(['hg', '-R', jmhPath, 'locate']).split('\n')]
            mustBuild = timestamp.isOlderThan(hgfiles)
        except:
            # not a Mercurial repository or hg commands are not available.
            mustBuild = True

    if mustBuild:
        buildOutput = []
        def _redirect(x):
            if mx._opts.verbose:
                mx.log(x[:-1])
            else:
                buildOutput.append(x)
        env = os.environ.copy()
        env['JAVA_HOME'] = _jdk(vmToCheck='server')
        env['MAVEN_OPTS'] = '-server -XX:-UseJVMCIClassLoader'
        mx.log("Building benchmarks...")
        cmd = ['mvn']
        if args.settings:
            cmd = cmd + ['-s', args.settings]
        if args.clean:
            cmd.append('clean')
        cmd.append('package')
        retcode = mx.run(cmd, cwd=jmhPath, out=_redirect, env=env, nonZeroIsFatal=False)
        if retcode != 0:
            mx.log(''.join(buildOutput))
            mx.abort(retcode)
        timestamp.touch()
    else:
        mx.logv('[all Mercurial controlled files in ' + jmhPath + ' are older than ' + timestamp.path + ' - skipping build]')

def jmh(args):
    """run the JMH benchmarks

    This command respects the standard --vm and --vmbuild options
    for choosing which VM to run the benchmarks with."""
    if '-h' in args:
        mx.help_(['jmh'])
        mx.abort(1)

    vmArgs, benchmarksAndJsons = mx.extract_VM_args(args)
    if isJVMCIEnabled(_get_vm()) and  '-XX:-UseJVMCIClassLoader' not in vmArgs:
        vmArgs = ['-XX:-UseJVMCIClassLoader'] + vmArgs

    benchmarks = [b for b in benchmarksAndJsons if not b.startswith('{')]
    jmhArgJsons = [b for b in benchmarksAndJsons if b.startswith('{')]
    jmhOutDir = join(_suite.mxDir, 'jmh')
    if not exists(jmhOutDir):
        os.makedirs(jmhOutDir)
    jmhOut = join(jmhOutDir, 'jmh.out')
    jmhArgs = {'-rff' : jmhOut, '-v' : 'EXTRA' if mx._opts.verbose else 'NORMAL'}

    # e.g. '{"-wi" : 20}'
    for j in jmhArgJsons:
        try:
            for n, v in json.loads(j).iteritems():
                if v is None:
                    del jmhArgs[n]
                else:
                    jmhArgs[n] = v
        except ValueError as e:
            mx.abort('error parsing JSON input: {0}\n{1}'.format(j, e))

    jmhPath = _get_jmh_path()
    mx.log('Using benchmarks in ' + jmhPath)

    matchedSuites = set()
    numBench = [0]
    for micros in os.listdir(jmhPath):
        absoluteMicro = os.path.join(jmhPath, micros)
        if not os.path.isdir(absoluteMicro):
            continue
        if not micros.startswith("micros-"):
            mx.logv('JMH: ignored ' + absoluteMicro + " because it doesn't start with 'micros-'")
            continue

        microJar = os.path.join(absoluteMicro, "target", "microbenchmarks.jar")
        if not exists(microJar):
            mx.log('Missing ' + microJar + ' - please run "mx buildjmh"')
            continue
        if benchmarks:
            def _addBenchmark(x):
                if x.startswith("Benchmark:"):
                    return
                match = False
                for b in benchmarks:
                    match = match or (b in x)

                if match:
                    numBench[0] += 1
                    matchedSuites.add(micros)

            mx.run_java(['-jar', microJar, "-l"], cwd=jmhPath, out=_addBenchmark, addDefaultArgs=False)
        else:
            matchedSuites.add(micros)

    mx.logv("matchedSuites: " + str(matchedSuites))
    plural = 's' if not benchmarks or numBench[0] > 1 else ''
    number = str(numBench[0]) if benchmarks else "all"
    mx.log("Running " + number + " benchmark" + plural + '...')

    regex = []
    if benchmarks:
        regex.append(r".*(" + "|".join(benchmarks) + ").*")

    for suite in matchedSuites:
        absoluteMicro = os.path.join(jmhPath, suite)
        (pfx, exe, vm, forkedVmArgs, _) = _parseVmArgs(vmArgs)
        if pfx:
            mx.log("JMH ignores prefix: \"" + ' '.join(pfx) + "\"")
        javaArgs = ['-jar', os.path.join(absoluteMicro, "target", "microbenchmarks.jar"),
                    '--jvm', exe,
                    '--jvmArgs', ' '.join(["-" + vm] + forkedVmArgs)]
        for k, v in jmhArgs.iteritems():
            javaArgs.append(k)
            if len(str(v)):
                javaArgs.append(str(v))
        mx.run_java(javaArgs + regex, addDefaultArgs=False, cwd=jmhPath)

def specjvm2008(args):
    """run one or more SPECjvm2008 benchmarks"""

    def launcher(bm, harnessArgs, extraVmOpts):
        return sanitycheck.getSPECjvm2008(harnessArgs + [bm]).bench(_get_vm(), extraVmOpts=extraVmOpts)

    availableBenchmarks = set(sanitycheck.specjvm2008Names)
    for name in sanitycheck.specjvm2008Names:
        parts = name.rsplit('.', 1)
        if len(parts) > 1:
            assert len(parts) == 2
            group = parts[0]
            availableBenchmarks.add(group)

    _run_benchmark(args, sorted(availableBenchmarks), launcher)

def specjbb2013(args):
    """run the composite SPECjbb2013 benchmark"""

    def launcher(bm, harnessArgs, extraVmOpts):
        assert bm is None
        return sanitycheck.getSPECjbb2013(harnessArgs).bench(_get_vm(), extraVmOpts=extraVmOpts)

    _run_benchmark(args, None, launcher)

def specjbb2005(args):
    """run the composite SPECjbb2005 benchmark"""

    def launcher(bm, harnessArgs, extraVmOpts):
        assert bm is None
        return sanitycheck.getSPECjbb2005(harnessArgs).bench(_get_vm(), extraVmOpts=extraVmOpts)

    _run_benchmark(args, None, launcher)

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

def jacocoreport(args):
    """create a JaCoCo coverage report

    Creates the report from the 'jacoco.exec' file in the current directory.
    Default output directory is 'coverage', but an alternative can be provided as an argument."""
    jacocoreport = mx.library("JACOCOREPORT", True)
    out = 'coverage'
    if len(args) == 1:
        out = args[0]
    elif len(args) > 1:
        mx.abort('jacocoreport takes only one argument : an output directory')

    includes = ['com.oracle.graal', 'jdk.internal.jvmci']
    for p in mx.projects():
        projsetting = getattr(p, 'jacoco', '')
        if projsetting == 'include':
            includes.append(p.name)

    includedirs = set()
    for p in mx.projects():
        projsetting = getattr(p, 'jacoco', '')
        if projsetting == 'exclude':
            continue
        for include in includes:
            if include in p.dir:
                includedirs.add(p.dir)

    for i in includedirs:
        bindir = i + '/bin'
        if not os.path.exists(bindir):
            os.makedirs(bindir)

    mx.run_java(['-jar', jacocoreport.get_path(True), '--in', 'jacoco.exec', '--out', out] + sorted(includedirs))

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

def generateZshCompletion(args):
    """generate zsh completion for mx"""
    try:
        from genzshcomp import CompletionGenerator
    except ImportError:
        mx.abort("install genzshcomp (pip install genzshcomp)")

    # need to fake module for the custom mx arg parser, otherwise a check in genzshcomp fails
    originalModule = mx._argParser.__module__
    mx._argParser.__module__ = "argparse"
    generator = CompletionGenerator("mx", mx._argParser)
    mx._argParser.__module__ = originalModule

    # strip last line and define local variable "ret"
    complt = "\n".join(generator.get().split('\n')[0:-1]).replace('context state line', 'context state line ret=1')

    # add array of possible subcommands (as they are not part of the argument parser)
    complt += '\n  ": :->command" \\\n'
    complt += '  "*::args:->args" && ret=0\n'
    complt += '\n'
    complt += 'case $state in\n'
    complt += '\t(command)\n'
    complt += '\t\tlocal -a main_commands\n'
    complt += '\t\tmain_commands=(\n'
    for cmd in sorted(mx._commands.iterkeys()):
        c, _ = mx._commands[cmd][:2]
        doc = c.__doc__
        complt += '\t\t\t"{0}'.format(cmd)
        if doc:
            complt += ':{0}'.format(_fixQuotes(doc.split('\n', 1)[0]))
        complt += '"\n'
    complt += '\t\t)\n'
    complt += '\t\t_describe -t main_commands command main_commands && ret=0\n'
    complt += '\t\t;;\n'

    complt += '\t(args)\n'
    # TODO: improve matcher: if mx args are given, this doesn't work
    complt += '\t\tcase $line[1] in\n'
    complt += '\t\t\t(vm | vmg | vmfg | unittest | jmh | dacapo | scaladacapo | specjvm2008 | specjbb2013 | specjbb2005)\n'
    complt += '\t\t\t\tnoglob \\\n'
    complt += '\t\t\t\t\t_arguments -s -S \\\n'
    complt += _appendOptions("jvmci", r"G\:")
    # TODO: fix -XX:{-,+}Use* flags
    complt += _appendOptions("hotspot", r"XX\:")
    complt += '\t\t\t\t\t"-version" && ret=0 \n'
    complt += '\t\t\t\t;;\n'
    complt += '\t\tesac\n'
    complt += '\t\t;;\n'
    complt += 'esac\n'
    complt += '\n'
    complt += 'return $ret'
    print complt

def _fixQuotes(arg):
    return arg.replace('\"', '').replace('\'', '').replace('`', '').replace('{', '\\{').replace('}', '\\}').replace('[', '\\[').replace(']', '\\]')

def _appendOptions(optionType, optionPrefix):
    def isBoolean(vmap, field):
        return vmap[field] == "Boolean" or vmap[field] == "bool"

    def hasDescription(vmap):
        return vmap['optDefault'] or vmap['optDoc']

    complt = ""
    for vmap in _parseVMOptions(optionType):
        complt += '\t\t\t\t\t-"'
        complt += optionPrefix
        if isBoolean(vmap, 'optType'):
            complt += '"{-,+}"'
        complt += vmap['optName']
        if not isBoolean(vmap, 'optType'):
            complt += '='
        if hasDescription(vmap):
            complt += "["
        if vmap['optDefault']:
            complt += r"(default\: " + vmap['optDefault'] + ")"
        if vmap['optDoc']:
            complt += _fixQuotes(vmap['optDoc'])
        if hasDescription(vmap):
            complt += "]"
        complt += '" \\\n'
    return complt

def _parseVMOptions(optionType):
    parser = OutputParser()
    # TODO: the optDoc part can wrapped accross multiple lines, currently only the first line will be captured
    # TODO: fix matching for float literals
    jvmOptions = re.compile(
        r"^[ \t]*"
        r"(?P<optType>(Boolean|Integer|Float|Double|String|bool|intx|uintx|ccstr|double)) "
        r"(?P<optName>[a-zA-Z0-9]+)"
        r"[ \t]+=[ \t]*"
        r"(?P<optDefault>([\-0-9]+(\.[0-9]+(\.[0-9]+\.[0-9]+))?|false|true|null|Name|sun\.boot\.class\.path))?"
        r"[ \t]*"
        r"(?P<optDoc>.+)?",
        re.MULTILINE)
    parser.addMatcher(ValuesMatcher(jvmOptions, {
        'optType' : '<optType>',
        'optName' : '<optName>',
        'optDefault' : '<optDefault>',
        'optDoc' : '<optDoc>',
        }))

    # gather JVMCI options
    output = StringIO.StringIO()
    vm(['-XX:-BootstrapJVMCI', '-XX:+UnlockDiagnosticVMOptions', '-G:+PrintFlags' if optionType == "jvmci" else '-XX:+PrintFlagsWithComments'],
       vm="jvmci",
       vmbuild="optimized",
       nonZeroIsFatal=False,
       out=output.write,
       err=subprocess.STDOUT)

    valueMap = parser.parse(output.getvalue())
    return valueMap

def checkheaders(args):
    """check Java source headers against any required pattern"""
    failures = {}
    for p in mx.projects():
        if p.native:
            continue

        csConfig = join(mx.project(p.checkstyleProj).dir, '.checkstyle_checks.xml')
        if not exists(csConfig):
            mx.log('Cannot check headers for ' + p.name + ' - ' + csConfig + ' does not exist')
            continue
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
        'build': [build, ''],
        'buildjmh': [buildjmh, '[-options]'],
        'buildvars': [buildvars, ''],
        'buildvms': [buildvms, '[-options]'],
        'c1visualizer' : [c1visualizer, ''],
        'checkheaders': [checkheaders, ''],
        'clean': [clean, ''],
        'ctw': [ctw, '[-vmoptions|noinline|nocomplex|full]'],
        'export': [export, '[-options] [zipfile]'],
        'generateZshCompletion' : [generateZshCompletion, ''],
        'hsdis': [hsdis, '[att]'],
        'hcfdis': [hcfdis, ''],
        'igv' : [igv, ''],
        'jdkhome': [print_jdkhome, ''],
        'jmh': [jmh, '[VM options] [filters|JMH-args-as-json...]'],
        'dacapo': [dacapo, '[VM options] benchmarks...|"all" [DaCapo options]'],
        'scaladacapo': [scaladacapo, '[VM options] benchmarks...|"all" [Scala DaCapo options]'],
        'specjvm2008': [specjvm2008, '[VM options] benchmarks...|"all" [SPECjvm2008 options]'],
        'specjbb2013': [specjbb2013, '[VM options] [-- [SPECjbb2013 options]]'],
        'specjbb2005': [specjbb2005, '[VM options] [-- [SPECjbb2005 options]]'],
        'gate' : [gate, '[-options]'],
        'bench' : [bench, '[-resultfile file] [all(default)|dacapo|specjvm2008|bootstrap]'],
        'microbench' : [microbench, '[VM options] [-- [JMH options]]'],
        'makejmhdeps' : [makejmhdeps, ''],
        'unittest' : [unittest, '[unittest options] [--] [VM options] [filters...]', mx_unittest.unittestHelpSuffix],
        'shortunittest' : [shortunittest, '[unittest options] [--] [VM options] [filters...]', mx_unittest.unittestHelpSuffix],
        'jacocoreport' : [jacocoreport, '[output directory]'],
        'site' : [site, '[-options]'],
        'vm': [vm, '[-options] class [args...]'],
        'vmg': [vmg, '[-options] class [args...]'],
        'vmfg': [vmfg, '[-options] class [args...]'],
        'deoptalot' : [deoptalot, '[n]'],
        'longtests' : [longtests, ''],
        'jol' : [jol, ''],
        'makefile' : [mx_graal_makefile.build_makefile, 'build makefiles for JDK build'],
    }

    mx.add_argument('--jacoco', help='instruments com.oracle.* classes using JaCoCo', default='off', choices=['off', 'on', 'append'])
    mx.add_argument('--vmcwd', dest='vm_cwd', help='current directory will be changed to <path> before the VM is executed', default=None, metavar='<path>')
    mx.add_argument('--installed-jdks', help='the base directory in which the JDKs cloned from $JAVA_HOME exist. ' +
                    'The VM selected by --vm and --vmbuild options is under this directory (i.e., ' +
                    join('<path>', '<jdk-version>', '<vmbuild>', 'jre', 'lib', '<vm>', mx.add_lib_prefix(mx.add_lib_suffix('jvm'))) + ')', default=None, metavar='<path>')

    mx.add_argument('--vm', action='store', dest='vm', choices=_vmChoices.keys(), help='the VM type to build/run')
    mx.add_argument('--vmbuild', action='store', dest='vmbuild', choices=_vmbuildChoices, help='the VM build to build/run (default: ' + _vmbuildChoices[0] + ')')
    mx.add_argument('--ecl', action='store_true', dest='make_eclipse_launch', help='create launch configuration for running VM execution(s) in Eclipse')
    mx.add_argument('--vmprefix', action='store', dest='vm_prefix', help='prefix for running the VM (e.g. "/usr/bin/gdb --args")', metavar='<prefix>')
    mx.add_argument('--gdb', action='store_const', const='/usr/bin/gdb --args', dest='vm_prefix', help='alias for --vmprefix "/usr/bin/gdb --args"')
    mx.add_argument('--lldb', action='store_const', const='lldb --', dest='vm_prefix', help='alias for --vmprefix "lldb --"')

    mx.update_commands(suite, commands)

class JVMCIArchiveParticipant:
    def __init__(self, dist):
        self.dist = dist
        self.jvmciServices = {}

    def __opened__(self, arc, srcArc, services):
        self.services = services
        self.arc = arc
        self.expectedOptionsProviders = set()

    def __add__(self, arcname, contents):
        if arcname.startswith('META-INF/jvmci.services/'):
            service = arcname[len('META-INF/jvmci.services/'):]
            self.jvmciServices.setdefault(service, []).extend([provider for provider in contents.split('\n')])
            return True
        if arcname.startswith('META-INF/jvmci.providers/'):
            provider = arcname[len('META-INF/jvmci.providers/'):]
            for service in contents.split(os.linesep):
                self.jvmciServices.setdefault(service, []).append(provider)
            return True
        elif arcname.startswith('META-INF/jvmci.options/'):
            # Need to create service files for the providers of the
            # jdk.internal.jvmci.options.Options service created by
            # jdk.internal.jvmci.options.processor.OptionProcessor.
            optionsOwner = arcname[len('META-INF/jvmci.options/'):]
            provider = optionsOwner + '_Options'
            self.expectedOptionsProviders.add(provider.replace('.', '/') + '.class')
            self.services.setdefault('jdk.internal.jvmci.options.Options', []).append(provider)
        return False

    def __addsrc__(self, arcname, contents):
        return False

    def __closing__(self):
        self.expectedOptionsProviders -= set(self.arc.zf.namelist())
        assert len(self.expectedOptionsProviders) == 0, 'missing generated Options providers:\n  ' + '\n  '.join(self.expectedOptionsProviders)
        for service, providers in self.jvmciServices.iteritems():
            arcname = 'META-INF/jvmci.services/' + service
            # Convert providers to a set before printing to remove duplicates
            self.arc.zf.writestr(arcname, '\n'.join(frozenset(providers)))

def mx_post_parse_cmd_line(opts):  #
    # TODO _minVersion check could probably be part of a Suite in mx?
    def _versionCheck(version):
        return version >= _minVersion and (not _untilVersion or version >= _untilVersion)
    versionDesc = ">=" + str(_minVersion)
    if _untilVersion:
        versionDesc += " and <=" + str(_untilVersion)
    mx.java(_versionCheck, versionDescription=versionDesc, defaultJdk=True)

    if hasattr(opts, 'vm') and opts.vm is not None:
        global _vm
        _vm = opts.vm
        _vm = _vm.replace('graal', 'jvmci')
    if hasattr(opts, 'vmbuild') and opts.vmbuild is not None:
        global _vmbuild
        _vmbuild = opts.vmbuild
    global _make_eclipse_launch
    _make_eclipse_launch = getattr(opts, 'make_eclipse_launch', False)
    global _jacoco
    _jacoco = opts.jacoco
    global _vm_cwd
    _vm_cwd = opts.vm_cwd
    global _installed_jdks
    _installed_jdks = opts.installed_jdks
    global _vm_prefix
    _vm_prefix = opts.vm_prefix

    for jdkDist in _jdkDeployedDists:
        def _close(jdkDeployable):
            def _install(dist):
                assert dist.name == jdkDeployable.name, dist.name + "!=" + jdkDeployable.name
                if not jdkDist.partOfHotSpot:
                    _installDistInJdks(jdkDeployable)
            return _install
        dist = mx.distribution(jdkDist.name)
        dist.add_update_listener(_close(jdkDist))
        if jdkDist.usesJVMCIClassLoader:
            dist.set_archiveparticipant(JVMCIArchiveParticipant(dist))

