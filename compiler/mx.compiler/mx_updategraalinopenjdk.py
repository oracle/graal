#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
# ----------------------------------------------------------------------------------------------------

import os
import shutil
import re
from collections import namedtuple
from argparse import ArgumentParser
from os.path import join, exists, dirname

import mx
import mx_compiler

def _read_sibling_file(basename):
    path = join(dirname(__file__), basename)
    with open(path, 'r') as fp:
        return fp.read()

def _find_version_base_project(versioned_project):
    base_project_name = getattr(versioned_project, 'overlayTarget', None)
    if base_project_name:
        return mx.dependency(base_project_name, context=versioned_project)
    extended_packages = versioned_project.extended_java_packages()
    if not extended_packages:
        mx.abort('Project with a multiReleaseJarVersion attribute must have sources in a package defined by project without multiReleaseJarVersion attribute', context=versioned_project)
    base_project = None
    base_package = None
    for extended_package in extended_packages:
        for p in mx.projects():
            if versioned_project != p and p.isJavaProject() and not hasattr(p, 'multiReleaseJarVersion'):
                if extended_package in p.defined_java_packages():
                    if base_project is None:
                        base_project = p
                        base_package = extended_package
                    else:
                        if base_project != p:
                            mx.abort('Multi-release jar versioned project {} must extend packages from exactly one project but extends {} from {} and {} from {}'.format(versioned_project, extended_package, p, base_project, base_package))
    if not base_project:
        mx.abort('Multi-release jar versioned project {} must extend package(s) from another project'.format(versioned_project))
    return base_project

def _is_git_repo(jdkrepo):
    git_dir = join(jdkrepo, '.git')
    return exists(git_dir)

def rename_packages(filepath, verbose=False):
    with open(filepath) as fp:
        contents = fp.read()
    new_contents = contents
    for old_name, new_name in package_renamings.items():
        new_contents = new_contents.replace(old_name, new_name)
        if new_contents != contents:
            with open(filepath, 'w') as fp:
                fp.write(new_contents)
                if verbose:
                    mx.log('  updated ' + filepath)

# Packages in Graal that have different names in OpenJDK so that the original packages can be deployed
# as is on the class path and not clash with packages in the jdk.internal.vm.compiler module.
# Also used by other script.
package_renamings = {
    'org.graalvm.collections' : 'jdk.internal.vm.compiler.collections',
    'org.graalvm.word'        : 'jdk.internal.vm.compiler.word',
    'org.graalvm.libgraal'    : 'jdk.internal.vm.compiler.libgraal',
    'org.graalvm.nativeimage' : 'jdk.internal.vm.compiler.nativeimage',
    'org.graalvm.options'     : 'jdk.internal.vm.compiler.options'
}

SuiteJDKInfo = namedtuple('SuiteJDKInfo', 'name includes excludes')
GraalJDKModule = namedtuple('GraalJDKModule', 'name suites')

def updategraalinopenjdk(args):
    """updates the Graal sources in OpenJDK"""
    parser = ArgumentParser(prog='mx updategraalinopenjdk')
    parser.add_argument('--pretty', help='value for --pretty when logging the changes since the last JDK* tag')
    parser.add_argument('jdkrepo', help='path to the local OpenJDK repo')
    parser.add_argument('version', type=int, help='Java version of the OpenJDK repo')
    parser.add_argument('--metro', action='store_true', help='update for Metropolis project (to build libgraal)')

    args = parser.parse_args(args)

    if mx_compiler.jdk.javaCompliance.value < args.version:
        mx.abort('JAVA_HOME/--java-home must be Java version {} or greater: {}'.format(args.version, mx_compiler.jdk))

    if not args.metro:
        mx.log('Processing update for OpenJDK {}'.format(args.version))
        graal_modules = [
            # JDK module jdk.internal.vm.compiler is composed of sources from:
            GraalJDKModule('jdk.internal.vm.compiler',
                # 1. Classes in the compiler suite under the org.graalvm namespace except for packages
                #    or projects whose names contain terms on the specified exclude list
                [SuiteJDKInfo('compiler', ['org.graalvm'], ['truffle', 'management', 'core.llvm', 'replacements.llvm', 'libgraal.jni']),
                # 2. Classes in the sdk suite under the org.graalvm.collections and org.graalvm.word namespaces
                 SuiteJDKInfo('sdk', ['org.graalvm.collections', 'org.graalvm.word'], [])]),
            # JDK module jdk.internal.vm.compiler.management is composed of sources from:
            GraalJDKModule('jdk.internal.vm.compiler.management',
                # 1. Classes in the compiler suite under the org.graalvm.compiler.hotspot.management namespace
                [SuiteJDKInfo('compiler', ['org.graalvm.compiler.hotspot.management'], ['libgraal'])]),
            # JDK module jdk.aot is composed of sources from:
            GraalJDKModule('jdk.aot',
                # 1. Classes in the compiler suite under the jdk.tools.jaotc namespace
                [SuiteJDKInfo('compiler', ['jdk.tools.jaotc'], [])]),
        ]
    else:
        if args.version < 15:
            mx.abort('Update for Metropolis works only for JDK 15 or later')
        mx.log('Processing update for Metropolis JDK {}'.format(args.version))
        # Metropolis needs more code from Graal
        graal_modules = [
            # JDK module jdk.internal.vm.compiler is composed of sources from:
            GraalJDKModule('jdk.internal.vm.compiler',
                # 1. Classes in the compiler suite under the org.graalvm namespace except for packages
                #    or projects whose names contain terms on the specified exclude list
                [SuiteJDKInfo('compiler', ['org.graalvm'], ['truffle', 'management', 'core.llvm', 'replacements.llvm']),
                # 2. Classes in the sdk suite under the org.graalvm.collections and org.graalvm.word namespaces
                 SuiteJDKInfo('sdk', ['org.graalvm.collections', 'org.graalvm.word', 'org.graalvm.nativeimage', 'org.graalvm.options'], [])]),
            # JDK module jdk.internal.vm.compiler.management is composed of sources from:
            GraalJDKModule('jdk.internal.vm.compiler.management',
                # 1. Classes in the compiler suite under the org.graalvm.compiler.hotspot.management namespace
                [SuiteJDKInfo('compiler', ['org.graalvm.compiler.hotspot.management'], ['libgraal'])]),
            # JDK module jdk.aot is composed of sources from:
            GraalJDKModule('jdk.aot',
                # 1. Classes in the compiler suite under the jdk.tools.jaotc namespace
                [SuiteJDKInfo('compiler', ['jdk.tools.jaotc'], [])]),
        ]


    # Strings to be replaced in files copied to OpenJDK.
    replacements = {
        'published by the Free Software Foundation.  Oracle designates this\n * particular file as subject to the "Classpath" exception as provided\n * by Oracle in the LICENSE file that accompanied this code.' : 'published by the Free Software Foundation.',
        _read_sibling_file('upl_substring.txt') : _read_sibling_file('gplv2_substring.txt')
    }

    # Strings that must not exist in OpenJDK source files. This is applied after replacements are made.
    blacklist = ['"Classpath" exception']

    jdkrepo = args.jdkrepo
    git_repo = _is_git_repo(jdkrepo)

    for m in graal_modules:
        m_src_dir = join(jdkrepo, 'src', m.name)
        if not exists(m_src_dir):
            mx.abort(jdkrepo + ' does not look like a JDK repo - ' + m_src_dir + ' does not exist')

    def run_output(args, cwd=None):
        out = mx.OutputCapture()
        mx.run(args, cwd=cwd, out=out, err=out)
        return out.data

    for m in graal_modules:
        m_src_dir = join('src', m.name)
        mx.log('Checking ' + m_src_dir)
        if git_repo:
            out = run_output(['git', 'status', '-s', m_src_dir], cwd=jdkrepo)
        else:
            out = run_output(['hg', 'status', m_src_dir], cwd=jdkrepo)
        if out:
            mx.abort(jdkrepo + ' is not "clean":' + '\n' + out[:min(200, len(out))] + '...')

    mx.checkcopyrights(['--primary', '--', '--fix'])

    for dirpath, _, filenames in os.walk(join(jdkrepo, 'make')):
        for filename in filenames:
            if filename.endswith('.gmk'):
                rename_packages(join(dirpath, filename), True)

    java_package_re = re.compile(r"^\s*package\s+(?P<package>[a-zA-Z_][\w\.]*)\s*;$", re.MULTILINE)

    copied_source_dirs = []
    jdk_internal_vm_compiler_EXCLUDES = set() # pylint: disable=invalid-name
    jdk_internal_vm_compiler_test_SRC = set() # pylint: disable=invalid-name
    # Add org.graalvm.compiler.processor since it is only a dependency
    # for (most) Graal annotation processors and is not needed to
    # run Graal.
    jdk_internal_vm_compiler_EXCLUDES.add('org.graalvm.compiler.processor')
    for m in graal_modules:
        classes_dir = join(jdkrepo, 'src', m.name, 'share', 'classes')
        for info in m.suites:
            mx.log('Processing ' + m.name + ':' + info.name)
            for e in os.listdir(classes_dir):
                if any(inc in e for inc in info.includes) and not any(ex in e for ex in info.excludes):
                    project_dir = join(classes_dir, e)
                    shutil.rmtree(project_dir)
                    mx.log('  removed ' + project_dir)
            suite = mx.suite(info.name)

            worklist = []

            for p in [e for e in suite.projects if e.isJavaProject()]:
                if str(mx_compiler.jdk.javaCompliance) not in p.javaCompliance:
                    mx.log('  skipping {} since its compliance ({}) is not compatible with {}'.format(p, repr(p.javaCompliance), mx_compiler.jdk.javaCompliance))
                    continue
                if args.version >= 15 and "sparc" in p.name:
                    # Filter SPARC port for JDK 15
                    continue
                if any(inc in p.name for inc in info.includes) and not any(ex in p.name for ex in info.excludes):
                    assert len(p.source_dirs()) == 1, p
                    version = 0
                    new_project_name = p.name
                    if hasattr(p, 'multiReleaseJarVersion'):
                        version = int(getattr(p, 'multiReleaseJarVersion'))
                        if version <= args.version:
                            base_project = _find_version_base_project(p)
                            new_project_name = base_project.name
                        else:
                            continue

                    for old_name, new_name in package_renamings.items():
                        if new_project_name.startswith(old_name):
                            new_project_name = new_project_name.replace(old_name, new_name)

                    workitem = (version, p, new_project_name)
                    worklist.append(workitem)

            # Ensure versioned resources are copied in the right order
            # such that higher versions override lower versions.
            worklist = sorted(worklist)

            for version, p, new_project_name in worklist:
                first_file = True

                source_dir = p.source_dirs()[0]
                target_dir = join(classes_dir, new_project_name, 'src')
                copied_source_dirs.append(source_dir)

                trailing = re.compile(r"[ \t]+\n")

                src_files = run_output(['git', 'ls-files'], cwd=source_dir).split('\n')
                for rel_src_file in src_files:
                    if not len(rel_src_file):
                        continue
                    if args.version >= 15 and "sparc" in rel_src_file:
                        # Skip SPARC files for JDK 15
                        continue
                    filename = os.path.basename(rel_src_file)
                    src_file = join(source_dir, rel_src_file)
                    dst_file = join(target_dir, os.path.relpath(src_file, source_dir))
                    binary_flag = '' if filename.endswith('.java') else 'b'
                    with open(src_file, 'r' + binary_flag) as fp:
                        try:
                            contents = fp.read()
                        except Exception as ex: # pylint: disable=broad-except
                            mx.log('Error reading {}: {}'.format(src_file, ex))
                    if filename.endswith('.java'):
                        old_line_count = len(contents.split('\n'))
                        for old_name, new_name in package_renamings.items():
                            old_name_as_dir = old_name.replace('.', os.sep)
                            if old_name_as_dir in src_file:
                                new_name_as_dir = new_name.replace('.', os.sep)
                                dst = src_file.replace(old_name_as_dir, new_name_as_dir)
                                dst_file = join(target_dir, os.path.relpath(dst, source_dir))
                            contents = contents.replace(old_name, new_name)

                        for old_line, new_line in replacements.items():
                            contents = contents.replace(old_line, new_line)

                        contents = re.sub(trailing, '\n', contents)

                        match = java_package_re.search(contents)
                        if not match:
                            mx.abort('Could not find package declaration in {}'.format(src_file))
                        java_package = match.group('package')
                        if any(ex in java_package for ex in info.excludes):
                            mx.log('  excluding ' + filename)
                            continue

                        new_line_count = len(contents.split('\n'))
                        if new_line_count > old_line_count:
                            mx.abort('Pattern replacement caused line count to grow from {} to {} in {}'.format(old_line_count, new_line_count, src_file))
                        else:
                            if new_line_count < old_line_count:
                                contents = contents.replace('\npackage ', '\n' * (old_line_count - new_line_count) + '\npackage ')
                        new_line_count = len(contents.split('\n'))
                        if new_line_count != old_line_count:
                            mx.abort('Unable to correct line count for {}'.format(src_file))
                        for forbidden in blacklist:
                            if forbidden in contents:
                                mx.abort('Found blacklisted pattern \'{}\' in {}'.format(forbidden, src_file))
                    dst_dir = os.path.dirname(dst_file)
                    if not exists(dst_dir):
                        os.makedirs(dst_dir)
                    if first_file:
                        mx.log('  copying: ' + source_dir)
                        mx.log('       to: ' + target_dir)
                        if p.testProject or p.definedAnnotationProcessors:
                            to_exclude = new_project_name
                            jdk_internal_vm_compiler_EXCLUDES.add(to_exclude)
                            if p.testProject:
                                jdk_internal_vm_compiler_test_SRC.add(to_exclude)
                        first_file = False
                    with open(dst_file, 'w' + binary_flag) as fp:
                        fp.write(contents)

    def replace_line(filename, old_line, new_line):
        mx.log('Updating ' + filename + '...')
        old_lines = []
        new_lines = []
        with open(filename) as fp:
            old_lines = fp.readlines()
        for line in old_lines:
            if line == old_line:
                new_line = line.replace(old_line, new_line)
                if old_line != new_line:
                    mx.log('Replaced \n ' + old_line.strip() + '\nwith\n ' + new_line.strip())
            new_lines.append(line.replace(old_line, new_line))
        with open(filename, 'w') as fp:
            fp.writelines(new_lines)

    def replace_lines(filename, begin_lines, end_line, replace_lines, old_line_check, preserve_indent=False, append_mode=False):
        mx.log('Updating ' + filename + '...')
        old_lines = []
        new_lines = []
        with open(filename) as fp:
            for begin_line in begin_lines:
                line = fp.readline()
                while line:
                    stripped_line = line.strip()
                    if stripped_line == begin_line:
                        new_lines.append(line)
                        break
                    new_lines.append(line)
                    line = fp.readline()
                assert line, begin_line + ' not found'

            lines = fp.readlines()
            line_in_def = True

            indent = 0
            if preserve_indent:
                line = lines[0]
                lstripped_line = line.lstrip()
                indent = len(line) - len(lstripped_line)

            if not append_mode:
                for replace in replace_lines:
                    new_lines.append(' ' * indent + replace)

            for line in lines:
                stripped_line = line.strip()
                if line_in_def:
                    if stripped_line == end_line:
                        line_in_def = False
                        new_lines.append(line)
                    else:
                        old_line_check(line)
                        if append_mode:
                            new_lines.append(line)
                    if append_mode and not line_in_def:
                        # reach end line and append new lines
                        for replace in replace_lines:
                            new_lines.append(replace)
                else:
                    new_lines.append(line)
        with open(filename, 'w') as fp:
            for line in new_lines:
                fp.write(line)
        return old_lines

    def single_column_with_continuation(line):
        parts = line.split()
        assert len(parts) == 2 and parts[1] == '\\', line

    def do_nothing(line):
        pass

    # Update jdk.internal.vm.compiler.EXCLUDES in make/CompileJavaModules.gmk
    # to exclude all test, benchmark and annotation processor packages.
    CompileJavaModules_gmk = join(jdkrepo, 'make', 'CompileJavaModules.gmk') # pylint: disable=invalid-name
    new_lines = []
    for pkg in sorted(jdk_internal_vm_compiler_EXCLUDES):
        new_lines.append(pkg + ' \\\n')
    begin_lines = ['jdk.internal.vm.compiler_EXCLUDES += \\']
    end_line = '#'
    old_line_check = single_column_with_continuation
    replace_lines(CompileJavaModules_gmk, begin_lines, end_line, new_lines, old_line_check, preserve_indent=True)

    # replace renamed service
    compiler_module_info = join(jdkrepo, 'src', 'jdk.internal.vm.compiler', 'share', 'classes', 'module-info.java')
    old_line = '    uses org.graalvm.compiler.nodes.graphbuilderconf.NodeIntrinsicPluginFactory;\n'
    new_line = '    uses org.graalvm.compiler.nodes.graphbuilderconf.GeneratedPluginFactory;\n'
    replace_line(compiler_module_info, old_line, new_line)

    if args.version == 11:
        # add aot exclude
        out = run_output(['grep', 'jdk.aot_EXCLUDES', CompileJavaModules_gmk], cwd=jdkrepo)
        if out:
            # replace existing exclude setting
            begin_lines = ['jdk.aot_EXCLUDES += \\']
            end_line = '#'
            new_lines = ['jdk.tools.jaotc.test \\\n']
            replace_lines(CompileJavaModules_gmk, begin_lines, end_line, new_lines, old_line_check, preserve_indent=True)
        else:
            # append exclude setting after jdk.internal.vm.compiler_EXCLUDES
            new_lines = ['\n', 'jdk.aot_EXCLUDES += \\\n', '    jdk.tools.jaotc.test \\\n', '    #\n', '\n']  # indent is inlined
            replace_lines(CompileJavaModules_gmk, begin_lines, end_line, new_lines, old_line_check, preserve_indent=True, append_mode=True)

    # Update 'SRC' in the 'Compile graalunit tests' section of make/test/JtregGraalUnit.gmk
    # to include all test packages.
    JtregGraalUnit_gmk = join(jdkrepo, 'make', 'test', 'JtregGraalUnit.gmk') # pylint: disable=invalid-name
    new_lines = []
    jdk_internal_vm_compiler_test_SRC.discard('jdk.tools.jaotc.test')
    jdk_internal_vm_compiler_test_SRC.discard('org.graalvm.compiler.microbenchmarks')
    jdk_internal_vm_compiler_test_SRC.discard('org.graalvm.compiler.virtual.bench')
    jdk_internal_vm_compiler_test_SRC.discard('org.graalvm.micro.benchmarks')
    for pkg in sorted(jdk_internal_vm_compiler_test_SRC):
        new_lines.append('$(SRC_DIR)/' + pkg + '/src \\\n')
    if args.version == 11:
        begin_lines = ['### Compile and build graalunit tests', 'SRC := \\']
    else:
        begin_lines = ['### Compile graalunit tests', 'SRC := \\']
    end_line = ', \\'
    old_line_check = single_column_with_continuation
    replace_lines(JtregGraalUnit_gmk, begin_lines, end_line, new_lines, old_line_check, preserve_indent=True)

    # Updates for Metropolis
    if args.metro:
        # Update 'PROCESSOR_JARS' and 'PROC_SRC_SUBDIRS' in make/modules/jdk.internal.vm.compiler/Gensrc.gmk
        Gensrc_gmk = join(jdkrepo, 'make', 'modules', 'jdk.internal.vm.compiler', 'Gensrc.gmk') # pylint: disable=invalid-name
        begin_lines = ['PROC_SRC_SUBDIRS := \\']
        end_line = 'org.graalvm.compiler.virtual \\'
        new_lines = ['    jdk.internal.vm.compiler.libgraal.jni \\\n']
        replace_lines(Gensrc_gmk, begin_lines, end_line, new_lines, old_line_check, preserve_indent=True, append_mode=True)

        begin_lines = ['PROCESSOR_JARS := \\']
        end_line = '$(BUILDTOOLS_OUTPUTDIR)/jdk.vm.compiler.serviceprovider.processor.jar \\'
        new_lines = ['    $(BUILDTOOLS_OUTPUTDIR)/jdk.vm.compiler.libgraal.jni.processor.jar \\\n']
        replace_lines(Gensrc_gmk, begin_lines, end_line, new_lines, old_line_check, preserve_indent=True, append_mode=True)

        # Add 'libgraal.jni.processor' build to make/CompileToolsHotspot.gmk
        CompileToolsHotspot_gmk = join(jdkrepo, 'make', 'CompileToolsHotspot.gmk') # pylint: disable=invalid-name
        begin_lines = ['TARGETS += $(BUILD_VM_COMPILER_SERVICEPROVIDER_PROCESSOR)']
        end_line = '##############################################################################'
        new_lines = ['\n  $(eval $(call SetupJavaCompilation, BUILD_VM_COMPILER_LIBGRAAL_JNI_PROCESSOR, \\\n',
                     '      TARGET_RELEASE := $(TARGET_RELEASE_BOOTJDK), \\\n',
                     '      SRC := \\\n',
                     '          $(SRC_DIR)/org.graalvm.compiler.processor/src \\\n',
                     '          $(SRC_DIR)/jdk.internal.vm.compiler.libgraal.jni.annotation/src \\\n',
                     '          $(SRC_DIR)/jdk.internal.vm.compiler.libgraal.jni.processor/src \\\n',
                     '          , \\\n',
                     '      EXCLUDE_FILES := $(EXCLUDE_FILES), \\\n',
                     '      BIN := $(BUILDTOOLS_OUTPUTDIR)/jdk.vm.compiler.libgraal.jni.processor, \\\n',
                     '      JAR := $(BUILDTOOLS_OUTPUTDIR)/jdk.vm.compiler.libgraal.jni.processor.jar, \\\n',
                     '      DISABLED_WARNINGS := options, \\\n',
                     '  ))\n\n',
                     '  TARGETS += $(BUILD_VM_COMPILER_LIBGRAAL_JNI_PROCESSOR)\n\n',
                     '  ##############################################################################\n']  # indent is inlined
        old_line_check = do_nothing
        replace_lines(CompileToolsHotspot_gmk, begin_lines, end_line, new_lines, old_line_check, preserve_indent=True, append_mode=True)

        # Rename packages in libgraal.jni.processor service
        rename_packages(join(jdkrepo, 'src', 'jdk.internal.vm.compiler', 'share', 'classes', 'jdk.internal.vm.compiler.libgraal.jni.processor', 'src', 'META-INF', 'services', 'javax.annotation.processing.Processor'))

    overwritten = ''
    if not git_repo:
        mx.log('Adding new files to HG...')
        m_src_dirs = []
        for m in graal_modules:
            m_src_dirs.append(join('src', m.name))
        out = run_output(['hg', 'log', '-r', 'last(keyword("Update Graal"))', '--template', '{rev}'] + m_src_dirs, cwd=jdkrepo)
        last_graal_update = out.strip()

        for m in graal_modules:
            m_src_dir = join('src', m.name)
            if last_graal_update:
                overwritten += run_output(['hg', 'diff', '-r', last_graal_update, '-r', 'tip', m_src_dir], cwd=jdkrepo)
            mx.run(['hg', 'add', m_src_dir], cwd=jdkrepo)
        mx.log('Removing old files from HG...')
        for m in graal_modules:
            m_src_dir = join('src', m.name)
            out = run_output(['hg', 'status', '-dn', m_src_dir], cwd=jdkrepo)
            if out:
                mx.run(['hg', 'rm'] + out.split(), cwd=jdkrepo)

    out = run_output(['git', 'tag', '-l', 'JDK-*'], cwd=mx_compiler._suite.vc_dir)
    last_jdk_tag = sorted(out.split(), reverse=True)[0]

    pretty = args.pretty or 'format:%h %ad %>(20) %an %s'
    out = run_output(['git', '--no-pager', 'log', '--merges', '--abbrev-commit', '--pretty=' + pretty, '--first-parent', '-r', last_jdk_tag + '..HEAD'] +
            copied_source_dirs, cwd=mx_compiler._suite.vc_dir)
    changes_file = 'changes-since-{}.txt'.format(last_jdk_tag)
    with open(changes_file, 'w') as fp:
        fp.write(out)
    mx.log('Saved changes since {} to {}'.format(last_jdk_tag, os.path.abspath(changes_file)))
    if overwritten:
        overwritten_file = 'overwritten-diffs.txt'
        with open(overwritten_file, 'w') as fp:
            fp.write(overwritten)
        mx.warn('Overwritten changes detected in OpenJDK Graal! See diffs in ' + os.path.abspath(overwritten_file))
