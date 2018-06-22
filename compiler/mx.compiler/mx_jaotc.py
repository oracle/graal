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

import subprocess
import tempfile
from argparse import ArgumentParser, ZERO_OR_MORE

import mx
import mx_compiler
from mx_gate import Task

jdk = mx.get_jdk(tag='default')


def run_jaotc(args, cwd=None):
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

    verbose = ['--verbose'] if mx._opts.verbose else []

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
             '-XX:+CalculateClassFingerprint'] + vm_args + ['-m', 'jdk.aot/jdk.tools.jaotc.Main'] + verbose + args,
            cwd=cwd)
    finally:
        if jvmci_classpath_adjusted:
            mx_compiler._jvmci_classpath.remove(jaotc_entry)


def jaotc_gate_runner(tasks):
    with Task('jaotc', tasks, tags=['jaotc', 'fulltest']) as t:
        if t: jaotc_test([])


def jaotc_test(args):
    """run (acceptance) tests for the AOT compiler (jaotc)"""
    all_tests = ['HelloWorld', 'java.base']
    parser = ArgumentParser(prog='mx jaotc-test')
    parser.add_argument("--list", default=None, action="store_true", help="Print the list of available jaotc tests.")
    parser.add_argument('tests', help='tests to run (omit to run all tests)', nargs=ZERO_OR_MORE)
    args = parser.parse_args(args)

    if args.list:
        print "The following jaotc tests are available:\n"
        for name in all_tests:
            print "  " + name
        return

    tests = args.tests or all_tests
    for test in tests:
        mx.log('Testing `{}`'.format(test))
        if test == 'HelloWorld':
            test_class(
                classpath=mx.project('jdk.tools.jaotc.test').output_dir(),
                main_class='jdk.tools.jaotc.test.HelloWorld'
            )
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
    [gc, ops]
    for gc in ['-XX:+UseParallelGC', '-XX:+UseG1GC']
    for ops in ['-XX:-UseCompressedOops', '-XX:+UseCompressedOops']
]


def test_class(classpath, main_class):
    """(jaotc-)Compiles `main_class` and runs `main_class` + AOT library.
    Compares the output vs. standard JVM.
    """
    # Run on vanilla JVM.
    expected_out = mx.OutputCapture()
    mx_compiler.run_vm(['-cp', classpath, main_class], out=expected_out)

    for common_opts in common_opts_variants:
        mx.log('Running {} with {}'.format(main_class, ' '.join(common_opts)))

        with mktemp_libfile() as lib_module:
            run_jaotc(['-J' + opt for opt in common_opts] +
                      ['--info', '--output', lib_module.name, main_class],
                      cwd=classpath)
            check_aot(classpath, main_class, common_opts, expected_out.data, lib_module)


def test_modules(classpath, main_class, modules):
    """(jaotc-)Compiles `modules` and runs `main_class` + AOT library.
    Compares the output vs. standard JVM.
    """
    # Run on vanilla JVM.
    expected_out = mx.OutputCapture()
    mx_compiler.run_vm(['-cp', classpath, main_class], out=expected_out)

    for common_opts in common_opts_variants:
        mx.log('(jaotc) Compiling module(s) {} with {}'.format(':'.join(modules), ' '.join(common_opts)))
        with mktemp_libfile() as lib_module:
            run_jaotc(['-J' + opt for opt in common_opts] +
                      ['--module', ':'.join(modules)] +
                      ['--info', '--output', lib_module.name])

            check_aot(classpath, main_class, common_opts, expected_out.data, lib_module)


def check_aot(classpath, main_class, common_opts, expected_output, lib_module):
    aot_opts = [
        '-XX:+UnlockDiagnosticVMOptions',
        '-XX:+UseAOTStrictLoading',
        '-XX:AOTLibrary=' + lib_module.name
    ]

    # Check AOT library is loaded.
    out = mx.OutputCapture()
    mx_compiler.run_vm(common_opts + aot_opts + ['-XX:+PrintAOT', '-version'], out=out, err=subprocess.STDOUT)
    if 'aot library' not in out.data:
        if mx._opts.verbose:
            mx.log(out.data)
        mx.abort("Missing expected 'aot library' in -XX:+PrintAOT -version output")

    # Run main_class+AOT modules and check output.
    aot_out = mx.OutputCapture()
    mx_compiler.run_vm(common_opts + aot_opts + ['-cp', classpath, main_class], out=aot_out)

    if expected_output != aot_out.data:
        mx.abort('Outputs differ, expected `{}` != `{}`'.format(expected_output, aot_out.data))
