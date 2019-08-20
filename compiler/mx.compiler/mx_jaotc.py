#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
from __future__ import print_function

import os
import shutil
import tempfile
from argparse import ArgumentParser, ZERO_OR_MORE

import mx
import mx_compiler
from mx_gate import Task

jdk = mx.get_jdk(tag='default')


def run_jaotc(args, classpath=None, cwd=None):
    """run AOT compiler with classes in this repo instead of those in the JDK"""
    if jdk.javaCompliance < '11':
        mx.abort('jaotc command is only available if JAVA_HOME is JDK 11 or later')

    jaotc_entry = mx_compiler.JVMCIClasspathEntry('JAOTC')
    jvmci_classpath_adjusted = False
    if jaotc_entry not in mx_compiler._jvmci_classpath:
        mx_compiler.add_jvmci_classpath_entry(jaotc_entry)
        jvmci_classpath_adjusted = True

    vm_args = [a[2:] for a in args if a.startswith('-J')]
    args = [a for a in args if not a.startswith('-J')]

    verbose = ['--verbose'] if mx._opts.very_verbose else []
    cp = ['-cp', classpath] if classpath else []

    try:
        mx_compiler.run_vm(
            ['--add-exports=jdk.internal.vm.ci/jdk.vm.ci.aarch64=jdk.internal.vm.compiler,jdk.aot',
             '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.amd64=jdk.internal.vm.compiler,jdk.aot',
             '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.code=jdk.internal.vm.compiler,jdk.aot',
             '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.code.site=jdk.internal.vm.compiler,jdk.aot',
             '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.code.stack=jdk.internal.vm.compiler,jdk.aot',
             '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.common=jdk.internal.vm.compiler,jdk.aot',
             '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.hotspot=jdk.internal.vm.compiler,jdk.aot',
             '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.hotspot.aarch64=jdk.internal.vm.compiler,jdk.aot',
             '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.hotspot.amd64=jdk.internal.vm.compiler,jdk.aot',
             '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.hotspot.sparc=jdk.internal.vm.compiler,jdk.aot',
             '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.meta=jdk.internal.vm.compiler,jdk.aot',
             '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.runtime=jdk.internal.vm.compiler,jdk.aot',
             '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.sparc=jdk.internal.vm.compiler,jdk.aot',
             '-XX:+CalculateClassFingerprint'] + vm_args + cp + ['-m', 'jdk.aot/jdk.tools.jaotc.Main'] + verbose + args,
            cwd=cwd)
    finally:
        if jvmci_classpath_adjusted:
            mx_compiler._jvmci_classpath.remove(jaotc_entry)


def jaotc_gate_runner(tasks):
    with Task('jaotc', tasks, tags=['jaotc', 'fulltest']) as t:
        if t: jaotc_test([])


def jaotc_test(args):
    """run (acceptance) tests for the AOT compiler (jaotc)"""
    all_tests = ['HelloWorld', 'java.base', 'javac']
    parser = ArgumentParser(prog='mx jaotc-test')
    parser.add_argument("--list", default=None, action="store_true", help="Print the list of available jaotc tests.")
    parser.add_argument('tests', help='tests to run (omit to run all tests)', nargs=ZERO_OR_MORE)
    args = parser.parse_args(args)

    if args.list:
        print("The following jaotc tests are available:\n")
        for name in all_tests:
            print("  " + name)
        return

    tests = args.tests or all_tests
    for test in tests:
        mx.log('Testing `{}`'.format(test))
        if test == 'HelloWorld':
            test_class(
                classpath=mx.classpath('JAOTC_TEST'),
                main_class='jdk.tools.jaotc.test.HelloWorld'
            )
        elif test == 'javac':
            test_javac('jdk.tools.jaotc')
        elif test == 'java.base':
            test_modules(
                classpath=mx.project('jdk.tools.jaotc.test').output_dir(),
                main_class='jdk.tools.jaotc.test.HelloWorld',
                modules=['java.base']
            )
        else:
            mx.abort('Unknown jaotc test: {}'.format(test))


def mktemp_libfile():
    return tempfile.NamedTemporaryFile(prefix=mx.add_lib_prefix(''), suffix=mx.add_lib_suffix(''))


common_opts_variants = [
    [gc, ops, '-ea', '-esa']
    for gc in ['-XX:+UseParallelGC', '-XX:+UseG1GC']
    for ops in ['-XX:-UseCompressedOops', '-XX:+UseCompressedOops']
]


def test_class(classpath, main_class, program_args=None):
    """(jaotc-)Compiles simple HelloWorld program.
    Compares the output vs. standard JVM.
    """
    # Run on vanilla JVM.
    program_args = program_args or []
    expected_out = mx.OutputCapture()
    mx_compiler.run_vm((['-cp', classpath] if classpath else []) +
                       [main_class] + program_args, out=expected_out)

    for common_opts in common_opts_variants:
        mx.log('Running {} with {}'.format(main_class, ' '.join(common_opts)))

        with mktemp_libfile() as lib_module:
            run_jaotc(['-J' + opt for opt in common_opts] +
                      ['--exit-on-error', '--info', '--output', lib_module.name, main_class],
                      classpath=classpath)
            check_aot(classpath, main_class, common_opts, expected_out.data, lib_module, program_args)


def test_modules(classpath, main_class, modules, program_args=None):
    """(jaotc-)Compiles `modules` and runs `main_class` + AOT library.
    Compares the output vs. standard JVM.
    """
    # Run on vanilla JVM.
    program_args = program_args or []
    expected_out = mx.OutputCapture()

    mx_compiler.run_vm((['-cp', classpath] if classpath else []) +
                       [main_class] + program_args, out=expected_out)

    # jaotc uses ':' as separator.
    module_list = ':'.join(modules)

    for common_opts in common_opts_variants:
        mx.log('(jaotc) Compiling module(s) {} with {}'.format(module_list, ' '.join(common_opts)))
        with mktemp_libfile() as lib_module:
            run_jaotc(['-J' + opt for opt in common_opts] +
                      ['--module', module_list] +
                      ['--exit-on-error', '--info', '--output', lib_module.name])

            check_aot(classpath, main_class, common_opts, expected_out.data, lib_module, program_args)


def collect_java_sources(source_dirs):
    javafilelist = []
    for sourceDir in source_dirs:
        for root, _, files in os.walk(sourceDir, followlinks=True):
            javafiles = [os.path.join(root, name) for name in files if name.endswith('.java')]
            javafilelist += javafiles
    return javafilelist


def test_javac(project_name):
    """(jaotc-)Compiles the `jdk.compiler` module and compiles (mx) project_name using `javac` (+ AOT module)."""
    # jaotc uses ':' as separator.
    modules = ':'.join(['jdk.compiler'])
    for common_opts in common_opts_variants:
        out_dir = tempfile.mkdtemp()
        try:
            mx.log('(jaotc) Compiling module(s) {} with {}'.format(modules, ' '.join(common_opts)))
            with mktemp_libfile() as lib_module:
                run_jaotc(['-J' + opt for opt in common_opts] +
                          ['--exit-on-error', '--info', '--module', modules, '--output', lib_module.name])

                aot_opts = [
                    '-XX:+UnlockDiagnosticVMOptions',
                    '-XX:+UseAOTStrictLoading',
                    '-XX:AOTLibrary=' + lib_module.name
                ]

                project = mx.project(project_name)
                java_files = collect_java_sources(project.source_dirs())
                javac_args = mx.JavacCompiler(jdk).prepare(
                    sourceFiles=java_files,
                    project=project,
                    outputDir=out_dir,
                    classPath=mx.classpath(project, includeSelf=False, jdk=jdk, ignoreStripped=True),
                    sourceGenDir=project.source_gen_dir(),
                    jnigenDir=project.jni_gen_dir(),
                    processorPath=project.annotation_processors_path(jdk),
                    disableApiRestrictions=True,
                    warningsAsErrors=False,
                    showTasks=False,
                    postCompileActions=[],
                    forceDeprecationAsWarning=False)

                mx_compiler.run_vm(common_opts + aot_opts + ['com.sun.tools.javac.Main'] + javac_args)

        finally:
            shutil.rmtree(out_dir)


def check_aot(classpath, main_class, common_opts, expected_output, lib_module, program_args=None):
    aot_opts = [
        '-XX:+UnlockDiagnosticVMOptions',
        '-XX:+UseAOTStrictLoading',
        '-XX:AOTLibrary=' + lib_module.name
    ]

    program_args = program_args or []

    # Check AOT library is loaded.
    out = mx.OutputCapture()
    mx_compiler.run_vm(common_opts + aot_opts + ['-XX:+PrintAOT', '-version'], out=out, err=out, nonZeroIsFatal=False)
    if 'aot library' not in out.data:
        mx.abort("Missing expected 'aot library' in -XX:+PrintAOT -version output. VM Output:\n" + str(out.data))

    # Run main_class+AOT modules and check output.
    aot_out = mx.OutputCapture()
    mx_compiler.run_vm(common_opts + aot_opts + ['-cp', classpath, main_class] + program_args, out=aot_out)

    if expected_output != aot_out.data:
        mx.abort('Outputs differ, expected `{}` != `{}`'.format(expected_output, aot_out.data))
