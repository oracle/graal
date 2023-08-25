#
# Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import io
import os
import re
import shutil
import sys
import zipfile
from pathlib import PurePath, PurePosixPath

import mx


class ShadedLibraryProject(mx.JavaProject):
    """
    A special JavaProject for shading third-party libraries.
    Configuration:
        shadedDependencies: [
            # one or more library dependencies
        ],
        "shade": {
            "packages" : {
                # a list of package name/path prefixes that should be shaded.
                # only .java/.class files that are in one of these packages are included.
                # package names must contain at least one '.' (i.e., two package parts).
                "old.pkg.name": "new.pkg.name",
            },
            "include" : [
                # a list of resource path patterns that should be copied.
                # by default, only shaded .java/.class files are included.
                "pkg/name/**",
            ],
            "exclude" : [
                # a list of (re)source path patterns that should be excluded from the generated jar
                "**/*.html",
            ],
            "patch" : [
                # a list of (re)source path patterns that should be patched with regex substitutions
                "pkg/name/my.properties" : {
                    "<pattern>" : "<replacement>",
                },
            ],
        }
    The build task then runs a Java program to shade the library and generates a .jar file.
    """
    def __init__(self, suite, name, deps, workingSets, theLicense, **args):
        self.shade = args.pop('shade')
        subDir = args.pop('subDir', 'src')
        srcDirs = args.pop('sourceDirs', ['src']) # + [source_gen_dir()], added below
        d = mx.join(suite.dir, subDir, name)
        shadedLibraries = args.pop('shadedDependencies', [])
        self.shadedDeps = list(set(mx.dependency(d) for d in shadedLibraries))
        assert all(dep.isLibrary() for dep in self.shadedDeps), f"shadedDependencies must all be libraries: {self.shadedDeps}"
        super().__init__(suite, name, subDir=subDir, srcDirs=srcDirs, deps=deps, # javaCompliance
                        workingSets=workingSets, d=d, theLicense=theLicense, **args)

        # add 'src_gen' dir to srcDirs (self.source_gen_dir() should only be called after Project.__init__)
        src_gen_dir = self.source_gen_dir()
        self.srcDirs.append(src_gen_dir)
        mx.ensure_dir_exists(src_gen_dir)

        self.checkstyleProj = args.get('checkstyle', name)
        self.checkPackagePrefix = False

    def getBuildTask(self, args):
        jdk = mx.get_jdk(self.javaCompliance, tag=mx.DEFAULT_JDK_TAG, purpose='building ' + self.name)
        return ShadedLibraryBuildTask(args, self, jdk)

    def shaded_deps(self):
        return self.shadedDeps

    def shaded_package_paths(self):
        result = getattr(self, '_shaded_package_paths', None)
        if not result:
            result = {k.replace('.', '/'): v.replace('.', '/') for (k, v) in self.shaded_package_names().items()}
            self._shaded_package_paths = result
        return result

    def shaded_package_names(self):
        return self.shade.get('packages', {})

    def included_paths(self):
        return self.shade.get('include', [])

    def excluded_paths(self):
        return self.shade.get('exclude', [])

    def defined_java_packages(self):
        """Get defined java packages from the dependencies, rename them, and remove any non-shaded packages."""
        packagesDefinedByDeps = [pkg for dep in self.shaded_deps() for pkg in dep.defined_java_packages()]
        return set([self.substitute_package_name(pkg) for pkg in packagesDefinedByDeps]).difference(set(packagesDefinedByDeps))

    def substitute_path(self, old_filename, mappings=None, default=None, reverse=False):
        """renames package path (using '/' as separator)."""
        assert isinstance(old_filename, str), old_filename
        if mappings is None:
            mappings = self.shaded_package_paths()
        if default is None:
            default = old_filename
        for (orig, shad) in mappings.items():
            if old_filename.startswith(orig):
                return old_filename.replace(orig, shad) if not reverse else old_filename.replace(shad, orig)
        return default

    def substitute_package_name(self, old_package_name):
        """renames java package name (using '.' as separator)."""
        return self.substitute_path(old_package_name, mappings=self.shaded_package_names())

class ShadedLibraryBuildTask(mx.JavaBuildTask):
    def __str__(self):
        return f'Shading {self.subject}'

    def needsBuild(self, newestInput):
        is_needed, reason = mx.ProjectBuildTask.needsBuild(self, newestInput)
        if is_needed:
            return True, reason

        proj = self.subject
        for outDir in [proj.output_dir(), proj.source_gen_dir()]:
            if not os.path.exists(outDir):
                return True, f"{outDir} does not exist"

        suite_py_ts = mx.TimeStampFile.newest([self.subject.suite.suite_py(), __file__])

        for dep in proj.shaded_deps():
            jarFilePath = dep.get_path(False)
            srcFilePath = dep.get_source_path(False)

            input_ts = mx.TimeStampFile.newest([jarFilePath, srcFilePath])
            if input_ts is None or suite_py_ts.isNewerThan(input_ts):
                input_ts = suite_py_ts

            for zipFilePath, outDir in [(srcFilePath, proj.source_gen_dir())]:
                try:
                    with zipfile.ZipFile(zipFilePath, 'r') as zf:
                        for zi in zf.infolist():
                            if zi.is_dir():
                                continue

                            old_filename = zi.filename
                            if old_filename.endswith('.java'):
                                filepath = PurePosixPath(old_filename)
                                if any(glob_match(filepath, i) for i in proj.excluded_paths()):
                                    continue
                                new_filename = proj.substitute_path(old_filename)
                                if old_filename != new_filename:
                                    output_file = os.path.join(outDir, new_filename)
                                    output_ts = mx.TimeStampFile(output_file)
                                    if output_ts.isOlderThan(input_ts):
                                        return True, f'{output_ts} is older than {input_ts}'
                except FileNotFoundError:
                    return True, f"{zipFilePath} does not exist"

        return super().needsBuild(newestInput)

    def prepare(self, daemons):
        # delay prepare until build
        self.daemons = daemons

    def build(self):
        dist = self.subject
        shadedDeps = dist.shaded_deps()
        includedPaths = dist.included_paths()
        patch = dist.shade.get('patch', [])
        excludedPaths = dist.excluded_paths()

        binDir = dist.output_dir()
        srcDir = dist.source_gen_dir()
        mx.ensure_dir_exists(binDir)
        mx.ensure_dir_exists(srcDir)

        javaSubstitutions = [
                                sub for orig, shad in dist.shaded_package_names().items() for sub in [
                                    (re.compile(r'\b' + re.escape(orig) + r'(?=\.[\w]+)?\b'), shad),
                                ]
                            ] + [
                                sub for orig, shad in dist.shaded_package_paths().items() for sub in [
                                    (re.compile(r'(?<=")' + re.escape(orig) + r'(?=/[\w./]+")'), shad),
                                    (re.compile(r'(?<="/)' + re.escape(orig) + r'(?=/[\w./]+")'), shad),
                                ]
                            ]

        for dep in shadedDeps:
            jarFilePath = dep.get_path(True)
            srcFilePath = dep.get_source_path(True)

            for zipFilePath, outDir in [(jarFilePath, binDir), (srcFilePath, srcDir)]:
                with zipfile.ZipFile(zipFilePath, 'r') as zf:
                    for zi in zf.infolist():
                        if zi.is_dir():
                            continue

                        old_filename = zi.filename
                        filepath = PurePosixPath(old_filename)
                        if any(glob_match(filepath, i) for i in excludedPaths):
                            mx.logv(f'ignoring file {old_filename} (matches {", ".join(i for i in excludedPaths if glob_match(filepath, i))})')
                            continue

                        if filepath.suffix not in ['.java', '.class'] and not any(glob_match(filepath, i) for i in includedPaths):
                            mx.warn(f'file {old_filename} is not included (if this is intended, please add the file to the exclude list)')
                            continue

                        new_filename = dist.substitute_path(old_filename)
                        applicableSubs = []

                        if filepath.suffix == '.java':
                            applicableSubs += javaSubstitutions
                        if filepath.suffix == '.class':
                            continue

                        mx.logv(f'extracting file {old_filename} to {new_filename}')
                        extraPatches = [sub for filepattern, subs in patch.items() if glob_match(filepath, filepattern) for sub in subs.items()]
                        extraSubs = list((re.compile(s, flags=re.MULTILINE), r) for (s, r) in extraPatches)
                        applicableSubs += extraSubs
                        if old_filename == new_filename and len(applicableSubs) == 0:
                            # same file name, no substitutions: just extract
                            zf.extract(zi, outDir)
                        else:
                            output_file = os.path.join(outDir, new_filename)
                            mx.ensure_dir_exists(mx.dirname(output_file))
                            if len(applicableSubs) == 0:
                                with zf.open(zi) as src, open(output_file, 'wb') as dst:
                                    shutil.copyfileobj(src, dst)
                            else:
                                assert filepath.suffix != '.class', filepath
                                with io.TextIOWrapper(zf.open(zi), encoding='utf-8') as src, open(output_file, 'w', encoding='utf-8') as dst:
                                    contents = src.read()

                                    # remove trailing whitespace and duplicate blank lines.
                                    contents = re.sub(r'(\n)?\s*(\n|$)', r'\1\2', contents)

                                    # apply substitutions
                                    for (srch, repl) in applicableSubs:
                                        contents = re.sub(srch, repl, contents)

                                    # turn off eclipseformat for generated .java files
                                    if filepath.suffix == '.java':
                                        dst.write('// @formatter:off\n')

                                    dst.write(contents)

        # After generating (re)sources, run the normal Java build task.
        if getattr(self, '_javafiles', None) == {}:
            self._javafiles = None
        super().prepare(self.daemons)
        super().build()

def glob_match(path, pattern):
    """
    Like PurePath.match(pattern), but adds support for the recursive wildcard '**'.
    :param path: a PurePath
    :param pattern: a string or a PurePath representing the glob pattern
    """
    assert isinstance(path, PurePath), path
    if sys.version_info[:2] >= (3, 13):
        # Since Python 3.13, PurePath.match already supports '**'.
        return path.match(pattern)

    pathType = type(path)
    patternParts = pathType(pattern).parts
    if not '**' in patternParts:
        if len(path.parts) != len(patternParts):
            return False
        return path.match(str(pattern))
    else:
        # split pattern at first '**'
        i = next(i for (i, p) in enumerate(patternParts) if p == '**')
        lhs = patternParts[:i]
        if (lhs == () or glob_match(pathType(*path.parts[:len(lhs)]), pathType(*lhs))):
            rhs = patternParts[i+1:]
            if rhs == ():
                return True
            min_start = len(lhs)
            max_start = len(path.parts) - len(rhs)
            if not '**' in rhs:
                return glob_match(pathType(*path.parts[max_start:]), pathType(*rhs))
            else:
                # multiple '**', must recurse
                for start in range(min_start, max_start + 1):
                    if glob_match(pathType(*path.parts[start:]), pathType(*rhs)):
                        return True
        return False
