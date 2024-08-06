# Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

import mx
import mx_util
import os
import tempfile
import zipfile
import shutil
from os.path import join, exists
from argparse import ArgumentParser
import filecmp

_suite = mx.suite('visualizer')
igvDir = os.path.join(_suite.dir, 'IdealGraphVisualizer')

class NetBeansProject(mx.ArchivableProject, mx.ClasspathDependency):
    def __init__(self, suite, name, deps, workingSets, theLicense, mxLibs=None, **args):
        mx.ArchivableProject.__init__(self, suite, name, deps, workingSets, theLicense)
        mx.ClasspathDependency.__init__(self)
        self.javaCompliance = mx.JavaCompliance("11+")
        self.dist = args.get('dist') or False
        # Only include visualizer content
        self.spotbugsAnalyzePackages = ['org.graalvm.visualizer.-']
        self.checkstyleProj = name
        self.findbugs = 'always'
        self.mxLibs = mxLibs or []
        if not hasattr(self, 'buildDependencies'):
            self.buildDependencies = []
        self.buildDependencies += self.mxLibs
        self.output_dirs = ['idealgraphvisualizer'] if self.dist else []
        self.build_commands = args.get('buildCommands') or []
        self.subDir = args.get('subDir') or None
        self.baseDir = os.path.join(self.dir, self.subDir or 'IdealGraphVisualizer')

    def is_test_project(self):
        return False

    def source_dirs(self):
        return []

    def classpath_repr(self, resolve=True):
        src = os.path.join(self.dir, 'IdealGraphVisualizer', self.name, 'src')
        if not os.path.exists(src):
            mx.abort("Cannot find {0}".format(src))
        return src

    def output_dir(self, relative=False):
        outdir = os.path.join(_suite.dir, 'IdealGraphVisualizer', 'dist')
        return outdir

    def source_gen_dir(self):
        return None

    def getOutput(self, replaceVar=False):
        return os.path.join(_suite.dir, 'target')

    def getResults(self, replaceVar=False):
        zips = set()
        for output_dir in self.output_dirs:
            for root, _, files in os.walk(join(self.output_dir(), output_dir)):
                for f in files:
                    zips.add(os.path.join(root, f))
        return zips

    def getBuildTask(self, args):
        return NetBeansBuildTask(self, args, None, None)

    def archive_prefix(self):
        return 'igv'

    def annotation_processors(self):
        return []

    def find_classes_with_matching_source_line(self, pkgRoot, function, includeInnerClasses=False):
        return dict()

    def eclipse_settings_sources(self):
        esdict = {}
        return esdict

    def resolveDeps(self):
        super(NetBeansProject, self).resolveDeps()
        self._resolveDepsHelper(self.mxLibs)

class NetBeansBuildTask(mx.BuildTask):
    def __init__(self, subject, args, vmbuild, vm):
        mx.BuildTask.__init__(self, subject, args, 1)
        self.vm = vm
        self.vmbuild = vmbuild

    def __str__(self):
        return 'Building NetBeans for {}'.format(self.subject)

    def needsBuild(self, newestInput):
        return (True, 'Let us re-build everytime')

    def newestOutput(self):
        return None

    def build(self):
        if self.subject.mxLibs:
            libs_dir = os.path.join(self.subject.dir, self.subject.subDir or 'IdealGraphVisualizer', self.subject.name, 'lib')
            mx_util.ensure_dir_exists(libs_dir)
            for lib in self.subject.mxLibs:
                lib_path = lib.classpath_repr(resolve=False)
                link_name = os.path.join(libs_dir, os.path.basename(lib_path))
                from_path = os.path.relpath(lib_path, os.path.dirname(link_name))
                mx.log("Symlink {0}, {1}".format(from_path, link_name))
                os.symlink(from_path, link_name)
        mx.log('Symlinks must be copied!')

        # HACK: since the maven executable plugin does not configure the
        # java executable that is used we unfortunately need to append it to the PATH
        javaHome = os.getenv('JAVA_HOME')
        if javaHome:
            os.environ["PATH"] = os.environ["JAVA_HOME"] + '/bin' + os.pathsep + os.environ["PATH"]

        mx.logv('Setting PATH to {}'.format(os.environ["PATH"]))
        mx.logv('Calling java -version')
        mx.run(['java', '-version'])

        for build_command in self.subject.build_commands:
            mx.log('Invoking ant for {} for {} in {}'.format(build_command, self.subject.name, self.subject.baseDir))
            run_ant([build_command, '-quiet'], nonZeroIsFatal=True, cwd=self.subject.baseDir)

        for output_dir in self.subject.output_dirs:
            os.chmod(os.path.join(self.subject.output_dir(), output_dir, 'bin', 'idealgraphvisualizer'), 0o755)

        mx.log('...finished build of {}'.format(self.subject))

    def clean(self, forBuild=False):
        if self.subject.mxLibs:
            libs_dir = os.path.join(self.subject.dir, self.subject.subDir or 'IdealGraphVisualizer', self.subject.name, 'lib')
            for lib in self.subject.mxLibs:
                lib_path = lib.classpath_repr(resolve=False)
                link_name = os.path.join(libs_dir, os.path.basename(lib_path))
                if os.path.lexists(link_name):
                    os.unlink(link_name)
        if forBuild:
            return
        run_ant(['distclean', '-quiet'], resolve=False, nonZeroIsFatal=True, cwd=igvDir)

def run_ant(args, resolve=True, **kwargs):
    # download the bundle using mx
    platform_lib = mx.library('NETBEANS_14').get_path(resolve=resolve)

    # override the download URL to ensure it's taken from mx
    return mx.run(['ant', '-Dide.dist.url=file:' + platform_lib] + args, **kwargs)

@mx.command(_suite.name, 'igv')
def igv(args):
    """run the Ideal Graph Visualizer"""
    if not mx.get_opts().verbose:
        args.append('-J-Dnetbeans.logger.console=false')
    mx.run([join(igvDir, 'dist', 'idealgraphvisualizer', 'bin', 'idealgraphvisualizer.exe' if mx.is_windows() else 'idealgraphvisualizer'), '--jdkhome', mx.get_jdk().home] + args)

def json_exporter(args, **kwargs):
    _json_exporter(args, **kwargs)

@mx.command(_suite.name, 'bgv2json')
def _json_exporter(args, extra_vm_args=None, env=None, jdk=None, extra_dists=None, cp_prefix=None, cp_suffix=None, **kwargs):
    """Export bgv graphs as json"""

    dists = ['IGV_JSONEXPORTER', 'IGV_DATA_SETTINGS']

    vm_args, prog_args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)

    if extra_dists:
        dists += extra_dists

    vm_args += mx.get_runtime_jvm_args(dists, jdk=jdk, cp_prefix=cp_prefix, cp_suffix=cp_suffix)

    if extra_vm_args:
        vm_args += extra_vm_args

    vm_args.append("org.graalvm.visualizer.JSONExporter")
    return mx.run_java(vm_args + prog_args, jdk=jdk, env=env, **kwargs)


@mx.command(_suite.name, 'build-release')
def build_release(args):
    """Make a release build of Ideal Graph Visualizer"""
    # Clean first to ensure the version number is picked up in the result
    mx.clean([])

    # Ensure the batik library is installed
    mx.build(['--dep', 'libs.batik'])

    # The current released version number is 0.31.  This mainly controls the
    # location of preferences and log files and is not normally changed.
    run_ant(['build-zip', '-quiet', '-Dapp.version=0.31'], nonZeroIsFatal=True, cwd=igvDir)
    os.chmod(os.path.join(igvDir, 'dist', 'idealgraphvisualizer', 'bin', 'idealgraphvisualizer'), 0o755)


@mx.command(_suite.name, 'unittest')
def test(args):
    """Run Ideal Graph Visualizer unit tests
        Single arguments is used as test java class in IGV
    """
    run_ant(['patch-test', '-silent'], nonZeroIsFatal=True, cwd=igvDir)
    add = ['-Dtest.run.args=-ea', '-Djava.awt.headless=true']
    if len(args) == 1:
        add += ['-Dtest.includes=**/*' + args[0] + '.class']
    elif len(args) != 0:
        mx.abort('Only single argument expected')
    run_ant(['test'] + add, nonZeroIsFatal=True, cwd=igvDir)

@mx.command(_suite.name, 'spotbugs')
def igv_spotbugs(args):
    """run spotbugs on IGV modules"""
    spotbugsVersion = _suite.getMxCompatibility().spotbugs_version()
    if spotbugsVersion == '3.0.0':
        jarFileNameBase = 'findbugs'
    else:
        jarFileNameBase = 'spotbugs'

    findBugsHome = mx.get_env('SPOTBUGS_HOME', mx.get_env('FINDBUGS_HOME', None))
    if findBugsHome:
        spotbugsLib = join(findBugsHome, 'lib')
    else:
        spotbugsLib = join(mx._mx_suite.get_output_root(), 'spotbugs-' + spotbugsVersion)
        if not exists(spotbugsLib):
            tmp = tempfile.mkdtemp(prefix='spotbugs-download-tmp', dir=mx._mx_suite.dir)
            try:
                spotbugsDist = mx.library('SPOTBUGS_' + spotbugsVersion).get_path(resolve=True)
                with zipfile.ZipFile(spotbugsDist) as zf:
                    candidates = [e for e in zf.namelist() if e.endswith('/lib/' + jarFileNameBase + ".jar")]
                    assert len(candidates) == 1, candidates
                    libDirInZip = os.path.dirname(candidates[0])
                    zf.extractall(tmp)
                shutil.copytree(join(tmp, libDirInZip), spotbugsLib)
            finally:
                shutil.rmtree(tmp)
    assert exists(spotbugsLib)
    spotbugsResults = join(_suite.dir, 'spotbugs.results')
    exitcode = run_ant(['-Dspotbugs.lib=' + spotbugsLib,
                        '-Dspotbugs.results=' + spotbugsResults,
                        '-buildfile', join(join(_suite.dir, 'IdealGraphVisualizer'), 'build.xml'),
                        'spotbugs'], nonZeroIsFatal=False)
    if exitcode != 0:
        with open(spotbugsResults) as fp:
            mx.log(fp.read())
    os.unlink(spotbugsResults)
    return exitcode

@mx.command(_suite.name, 'verify-graal-graphio')
def verify_graal_graphio(args):
    """Verify org.graalvm.graphio is the unchanged"""
    parser = ArgumentParser(prog='mx verify-graal-graphio')
    parser.add_argument('-s', '--sync', action='store_true', help='synchronize with graal configuration')
    parser.add_argument('-q', '--quiet', action='store_true', help='Only produce output if something is changed')
    args = parser.parse_args(args)

    visualizer_dir = 'IdealGraphVisualizer/Data/src/jdk/graal/compiler/graphio'
    if not exists(visualizer_dir):
        mx.log(f"Error: {visualizer_dir} doesn't exist")
        mx.abort(1)

    graal_dir = '../compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/graphio'
    if not exists(graal_dir):
        mx.log(f"Error: {graal_dir} doesn't exist")
        mx.abort(1)

    def _handle_error(msg, base_file, dest_file):
        if args.sync:
            mx.log(f"Overriding {os.path.normpath(dest_file)} from {os.path.normpath(base_file)}")
            shutil.copy(base_file, dest_file)
        else:
            mx.log(msg + ": " + os.path.normpath(dest_file))
            mx.log("Try synchronizing:")
            mx.log("  " + base_file)
            mx.log("  " + dest_file)
            mx.log("Or execute 'mx verify-graal-graphio' with the  '--sync' option.")
            mx.abort(1)

    def _common_string_end(s1, s2):
        l = 0
        while s1[l-1] == s2[l-1]:
            l -= 1
        return s1[l:]

    def _verify_file(base_file, dest_file):
        if not os.path.isfile(base_file) or not os.path.isfile(dest_file):
            _handle_error('file not found', base_file, dest_file)
        if not filecmp.cmp(base_file, dest_file):
            _handle_error('file mismatch', base_file, dest_file)
        mx.logv(f"File '{_common_string_end(base_file, dest_file)}' matches.")


    verified = 0
    for root, _, files in os.walk(graal_dir):
        rel_root = os.path.relpath(root, graal_dir)
        for f in files:
            graal_file = join(graal_dir, rel_root, f)
            visualizer_file = join(visualizer_dir, rel_root, f)
            _verify_file(graal_file, visualizer_file)
            verified += 1

    if verified == 0:
        mx.log("No files were found to verify")
        mx.abort(1)


    if not args.quiet:
        mx.log("org.graalvm.graphio is unchanged.")
