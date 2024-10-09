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
        # by default skip the unit tests in a build
        self.build_commands = ["package", "-DskipTests"]
        self.subDir = args.get('subDir') or None
        self.baseDir = os.path.join(self.dir, self.subDir or 'IdealGraphVisualizer')

    def is_test_project(self):
        return False

    def source_dirs(self):
        return []

    def classpath_repr(self, resolve=True):
        src = os.path.join(self.dir, 'IdealGraphVisualizer', self.name, 'src')
        if not os.path.exists(src):
            mx.abort(f"Cannot find {src}")
        return src

    def output_dir(self, relative=False):
        outdir = os.path.join(_suite.dir, 'IdealGraphVisualizer', 'application', 'target')
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
        return f'Building NetBeans for {self.subject}'

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
                mx.log(f"Symlink {from_path}, {link_name}")
                os.symlink(from_path, link_name)
        mx.log('Symlinks must be copied!')

        env = os.environ.copy()
        env["MAVEN_OPTS"] = "-Djava.awt.headless=true -Dpolyglot.engine.WarnInterpreterOnly=false"

        mx.logv(f"Setting PATH to {os.environ['PATH']}")

        mx.log(f"Invoking maven for {' '.join(self.subject.build_commands)} for {self.subject.name} in {self.subject.baseDir}")
        run_maven(self.subject.build_commands, nonZeroIsFatal=True, cwd=self.subject.baseDir, env=env)

        for output_dir in self.subject.output_dirs:
            os.chmod(os.path.join(self.subject.output_dir(), output_dir, 'bin', 'idealgraphvisualizer'), 0o755)

        mx.log(f'...finished build of {self.subject}')

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
        env = os.environ.copy()
        env["MAVEN_OPTS"] = "-Djava.awt.headless=true"
        run_maven(['clean', '--quiet'], resolve=False, nonZeroIsFatal=True, cwd=igvDir, env=env)

def run_maven(args, resolve=True, **kwargs):
    return mx.run(['mvn'] + args, **kwargs)

@mx.command(_suite.name, 'igv')
def igv(args):
    """run the newly built Ideal Graph Visualizer"""
    # force a build if it hasn't been built yet
    if not os.path.exists(os.path.join(_suite.dir, 'IdealGraphVisualizer', 'application', 'target', 'idealgraphvisualizer')):
        mx.build(['--dependencies', 'IGV'])

    if mx.get_opts().verbose:
        args.append('-J-Dnetbeans.logger.console=true')
    mx.run([join(igvDir, 'application', 'target', 'idealgraphvisualizer', 'bin', 'idealgraphvisualizer.exe' if mx.is_windows() else 'idealgraphvisualizer'), '--jdkhome', mx.get_jdk().home] + args)

@mx.command(_suite.name, 'unittest')
def test(args):
    """Run Ideal Graph Visualizer unit tests"""
    run_maven(['package'], nonZeroIsFatal=True, cwd=igvDir)

@mx.command(_suite.name, 'release')
def release(args):
    """Build a released version using mvn release:prepare"""
    run_maven(['-B', 'release:clean', 'release:prepare'], nonZeroIsFatal=True, cwd=igvDir)

@mx.command(_suite.name, 'verify-graal-graphio')
def verify_graal_graphio(args):
    """Verify org.graalvm.graphio is unchanged between the compiler and visualizer folders"""
    parser = ArgumentParser(prog='mx verify-graal-graphio')
    parser.add_argument('-s', '--sync-from', choices=['compiler', 'visualizer'], help='original source folder for synchronization')
    parser.add_argument('-q', '--quiet', action='store_true', help='Only produce output if something is changed')
    args = parser.parse_args(args)

    visualizer_dir = 'IdealGraphVisualizer/Data/src/main/java/jdk/graal/compiler/graphio'
    if not exists(visualizer_dir):
        mx.log(f"Error: {visualizer_dir} doesn't exist")
        mx.abort(1)

    graal_dir = '../compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/graphio'
    if not exists(graal_dir):
        mx.log(f"Error: {graal_dir} doesn't exist")
        mx.abort(1)

    def _handle_error(msg, base_file, dest_file):
        if args.sync_from is not None:
            mx.log(f"Overriding {os.path.normpath(dest_file)} from {os.path.normpath(base_file)}")
            os.makedirs(os.path.dirname(dest_file), exist_ok=True)
            shutil.copy(base_file, dest_file)
        else:
            mx.log(msg + ": " + os.path.normpath(dest_file))
            mx.log("Try synchronizing:")
            mx.log("  " + os.path.normpath(base_file))
            mx.log("  " + os.path.normpath(dest_file))
            mx.log("Or execute 'mx verify-graal-graphio' with the  '--sync-from' option.")
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

    source_dir = graal_dir
    dest_dir = visualizer_dir
    if args.sync_from == "visualizer":
        # Reverse synchronization direction
        dest_dir, source_dir = source_dir, dest_dir

    verified = 0
    for root, _, files in os.walk(source_dir):
        rel_root = os.path.relpath(root, source_dir)
        for f in files:
            source_file = join(source_dir, rel_root, f)
            dest_file = join(dest_dir, rel_root, f)
            _verify_file(source_file, dest_file)
            verified += 1

    if verified == 0:
        mx.log("No files were found to verify")
        mx.abort(1)


    if not args.quiet:
        mx.log("jdk.graal.compiler.graphio is unchanged.")
