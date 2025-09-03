#
# Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
import os
import re
from collections import OrderedDict
from os.path import join, exists, relpath

import mx
import mx_util


class EspressoSVMShared(mx.JavaProject):
    def __init__(self, suite, name, deps, workingSets, theLicense=None, **attr):
        self.shadedProjects = attr.pop('shadedProjects')
        self.removeAnnotations = attr.pop('removeAnnotations', [])
        self.checkPackagePrefix = True
        packageMap = attr.pop('packageMap')
        self.packageMap = OrderedDict()
        for k in sorted(packageMap, key=str.__len__, reverse=True):
            self.packageMap[k + '.'] = packageMap[k] + '.'
        self.pathMap = OrderedDict((k.replace('.', os.sep), v.replace('.', os.sep)) for k, v in self.packageMap.items())
        javaCompliance = attr.pop('javaCompliance')
        super().__init__(suite, name, "", [], deps, javaCompliance, workingSets, suite.dir, theLicense, **attr)
        self.gen_src = join(self.get_output_root(), 'gen_src')
        self.srcDirs.append(self.gen_src)

    def getBuildTask(self, args):
        jdk = mx.get_jdk(self.javaCompliance, tag=mx.DEFAULT_JDK_TAG, purpose='building ' + self.name)
        return EspressoSVMSharedBuildTask(args, self, jdk)

    def resolveDeps(self):
        super().resolveDeps()
        self._resolveDepsHelper(self.shadedProjects)
        not_java_projects = [d for d in self.shadedProjects if not d.isJavaProject()]
        if not_java_projects:
            raise self.abort(f"shadedProjects must all be java projects, but the following are not: {not_java_projects}")

    def get_checkstyle_config(self, resolve_checkstyle_library=True):
        return None, None, None

    def shaded_deps(self):
        return self.shadedProjects

    def _apply_package_map(self, original, is_path):
        map_dict = self.pathMap if is_path else self.packageMap
        for k, v in map_dict.items():
            if original.startswith(k):
                return v + original[len(k):]
        return original

    def substitute_package_name(self, pkg):
        return self._apply_package_map(pkg, False)

    def substitute_path(self, path):
        return self._apply_package_map(path, True)

    def defined_java_packages(self):
        return set([self.substitute_package_name(pkg) for dep in self.shaded_deps() for pkg in dep.defined_java_packages()])


class EspressoSVMSharedBuildTask(mx.JavaBuildTask):
    def saved_config_path(self):
        return join(self.subject.get_output_root(), 'config')

    def config(self):
        config = str(sorted(self.subject.shaded_deps())) + '\n'
        config += str(self.subject.javaCompliance) + '\n'
        for pkg in sorted(self.subject.packageMap):
            config += f"{pkg}: {self.subject.packageMap[pkg]}\n"
        config += str(sorted(self.subject.removeAnnotations)) + '\n'
        return config

    def _walk_files(self, create_shaded_dirs=False):
        for shaded_project in self.subject.shaded_deps():
            for src_dir in shaded_project.source_dirs():
                for dirpath, _, filenames in os.walk(src_dir):
                    reldirpath = relpath(dirpath, src_dir)
                    mapped_reldirpath = self.subject.substitute_path(reldirpath)
                    if create_shaded_dirs and filenames:
                        mx_util.ensure_dir_exists(join(self.subject.gen_src, mapped_reldirpath))
                    for filename in filenames:
                        shaded_relpath = join(mapped_reldirpath, filename)
                        yield join(dirpath, filename), join(self.subject.gen_src, shaded_relpath), shaded_relpath

    def needsBuild(self, newestInput):
        is_needed, reason = mx.ProjectBuildTask.needsBuild(self, newestInput)
        if is_needed:
            return True, reason
        config_path = self.saved_config_path()
        if not exists(config_path):
            return True, "Saved config file not found"
        config = self.config()
        with open(config_path, 'r', encoding='utf-8') as f:
            if f.read() != config:
                return True, "Configuration changed"
        for original, shaded, _ in self._walk_files():
            ts = mx.TimeStampFile(shaded)
            if ts.isOlderThan(original):
                return True, str(ts)
            if newestInput and ts.isOlderThan(newestInput):
                return True, str(ts)
        return False, None

    def _collect_files(self):
        if self._javafiles is not None:
            # already collected
            return self
        javafiles = {}
        non_javafiles = {}
        output_dir = self.subject.output_dir()
        for _, shaded, shaded_relpath in self._walk_files():
            if shaded.endswith('.java'):
                classfile = output_dir + shaded_relpath[:-len('.java')] + '.class'
                javafiles[shaded] = classfile
            else:
                non_javafiles[shaded] = output_dir + shaded_relpath
        if hasattr(self.subject, 'copyFiles'):
            raise mx.abort('copyFiles is not supported', context=self.subject)
        self._javafiles = javafiles
        self._non_javafiles = non_javafiles
        self._copyfiles = {}
        return self

    def clean(self, forBuild=False):
        super().clean()
        if exists(self.subject.gen_src):
            mx.rmtree(self.subject.gen_src)

    def build(self):
        java_substitutions = [
            sub for orig, shad in self.subject.packageMap.items() for sub in [
                (re.compile(r'\b' + re.escape(orig) + r'(?=\.\w+)?\b'), shad),
            ]
        ]
        re_type_start = re.compile(r"\.[A-Z]")
        for annotation_type in self.subject.removeAnnotations:
            type_name_start = re_type_start.search(annotation_type).start()
            # remove `import com.Foo.Bar;`
            # remove `@Foo.Bar(*)`
            # remove `@Foo.Bar`
            # change `{@link Foo.Bar}` to `{@code Foo.Bar}`
            unqualified_type = annotation_type[type_name_start + 1:]
            java_substitutions += [
                (re.compile(r'^import\s+' + re.escape(annotation_type) + r'\s*;', re.MULTILINE), ''),
                (re.compile(r'@' + re.escape(unqualified_type) + r'(\(.*?\))?'), ''),
                (re.compile(r'\{@link ' + re.escape(unqualified_type) + r'\}'), '{@code ' + unqualified_type + '}'),
            ]
            next_type_name_m = re_type_start.search(annotation_type, type_name_start + 1)
            if next_type_name_m:
                # remove `import com.Foo;`
                # remove `import static com.Foo.Bar;`
                # remove `@Bar(*)`
                # remove `@Bar`
                # change `{@link Bar}` to `{@code Bar}`
                next_type_name_start = next_type_name_m.start()
                next_unqualified_type = annotation_type[next_type_name_start + 1:]
                java_substitutions += [
                    (re.compile(r'^import\s+' + re.escape(annotation_type[:next_type_name_start]) + r'\s*;', re.MULTILINE), ''),
                    (re.compile(r'^import\s+static\s+' + re.escape(annotation_type) + r'\s*;', re.MULTILINE), ''),
                    (re.compile(r'@' + re.escape(next_unqualified_type) + r'(\(.*?\))?'), ''),
                    (re.compile(r'\{@link ' + re.escape(next_unqualified_type) + r'\}'), '{@code ' + next_unqualified_type + '}'),
                ]
                assert not re_type_start.search(annotation_type, next_type_name_start + 1)

        for original, shaded, _ in self._walk_files(True):
            with open(original, 'r', encoding='utf-8') as f_orig, open(shaded, 'w', encoding='utf-8') as f_shaded:
                for line in f_orig:
                    for srch, repl in java_substitutions:
                        line = re.sub(srch, repl, line)
                    f_shaded.write(line)
        super().build()
        with open(self.saved_config_path(), 'w', encoding='utf-8') as f:
            f.write(self.config())
