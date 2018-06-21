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
from argparse import ArgumentParser

import mx
import mx_compiler
from mx_gate import Task

jdk = mx.get_jdk(tag='default')


def run_jaotc(args, cwd=None):
    if jdk.javaCompliance < '11':
        mx.abort('jaotc command is only available if JAVA_HOME is JDK 11 or later')
    jaotc_entry = mx_compiler.JVMCIClasspathEntry('JAOTC')
    jvmci_classpath_adjusted = False
    if jaotc_entry not in mx_compiler._jvmci_classpath:
        mx_compiler.add_jvmci_classpath_entry(jaotc_entry)
        jvmci_classpath_adjusted = True

    vm_args = [a[2:] for a in args if a.startswith('-J')]
    args = [a for a in args if not a.startswith('-J')]

    try:
        mx_compiler.run_vm(['--add-exports=jdk.internal.vm.ci/jdk.vm.ci.aarch64=jdk.internal.vm.compiler,jdk.aot',
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
                            '-XX:+CalculateClassFingerprint'] + vm_args + ['-m', 'jdk.aot/jdk.tools.jaotc.Main'] + args,
                           cwd=cwd)
    finally:
        if jvmci_classpath_adjusted:
            mx_compiler._jvmci_classpath.remove(jaotc_entry)


def jaotc_gate_runner(tasks):
    with Task('jaotc', tasks, tags=['jaotc', 'fulltest']) as t:
        if t: jaotc_tests([])


def jaotc_tests(args):
    parser = ArgumentParser(prog='mx jaotc-tests')
    parser.add_argument('test', help='test to run', nargs='*')
    args = parser.parse_args(args)

    tests = args.test or ['HelloWorld']
    for test in tests:
        mx.log('Running `{}`'.format(test))
        if test == 'HelloWorld':
            test_helloworld()
        else:
            mx.abort('Unknown jaotc test: {}'.format(test))


def test_helloworld():
    verbose = mx._opts.verbose

    common_opts = ['-Xmx4g', '-XX:-UseCompressedOops']
    classfile_dir = mx.project('jdk.tools.jaotc.test').output_dir()

    with tempfile.NamedTemporaryFile(prefix=mx.add_lib_prefix(''), suffix=mx.add_lib_suffix('')) as libHelloWorld:
        run_jaotc(['-J' + opt for opt in common_opts] + ['--info'] + (['--verbose'] if verbose else []) +
                  [
                      '--output', libHelloWorld.name,
                      'jdk/tools/jaotc/test/HelloWorld.class'
                  ],
                  cwd=classfile_dir)

        java_opts = common_opts + [
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+UseAOTStrictLoading",
            "-XX:AOTLibrary=" + libHelloWorld.name
        ]

        out = mx.OutputCapture()
        mx_compiler.run_vm(java_opts + ['-XX:+PrintAOT', '-version'], out=out, err=subprocess.STDOUT)

        if 'aot library' not in out.data:
            if verbose:
                mx.log(out.data)
            mx.abort("Missing expected 'aot library' in -XX:+PrintAOT -version output")

        out = mx.OutputCapture()
        mx_compiler.run_vm(
            java_opts + ['-cp', mx.project('jdk.tools.jaotc.test').output_dir(), 'jdk.tools.jaotc.test.HelloWorld'],
            out=out)

        expected_output = 'Hello, world!\n'
        if expected_output != out.data:
            mx.abort('Output does not match. Expected: `{}`\nReceived: `{}`'.format(expected_output, out.data))
