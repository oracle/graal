#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

def _check_jaotc_support():
    if jdk.javaCompliance < '11':
        mx.abort('jaotc command is only available if JAVA_HOME is JDK 11 or later')
    if not mx_compiler._is_jaotc_supported():
        mx.abort('jaotc executable is not present in ' + str(mx_compiler.jdk))

def run_jaotc(args, classpath=None, cwd=None):
    """run AOT compiler with classes in this repo instead of those in the JDK"""
    _check_jaotc_support()
    vm_args = [a for a in args if a.startswith('-J')]
    args = [a for a in args if not a.startswith('-J')]

    verbose = ['--verbose'] if mx._opts.very_verbose else []
    cp = ['-J--class-path=' + classpath] if classpath else []

    graaljdk_dir, _ = mx_compiler._update_graaljdk(mx_compiler.jdk)
    graaljdk = mx.JDKConfig(graaljdk_dir)
    jaotc_exe = graaljdk.exe_path('jaotc', 'bin')
    mx.run([jaotc_exe] + vm_args + cp + verbose + args, cwd=cwd)

def jaotc_gate_runner(tasks):
    with Task('jaotc', tasks, tags=['jaotc', 'fulltest']) as t:
        group = mx.get_env('JAOTC_TEST_GROUP', 'gate')
        if t: jaotc_test(['--group', group])


def jaotc_run(tests, group):
    for test in tests:
        mx.log('Testing `{}`'.format(test))

        group_config = jaotc_group_config[group]
        test_info = jaotc_test_info[test]

        test_type = jaotc_test_info[test]['type']
        if test_type == 'app':
            test_class(
                opts_set=group_config['class'],
                classpath=mx.classpath('JAOTC_TEST'),
                main_class=test_info['main']
            )
        elif test_type == 'javac':
            test_javac('jdk.tools.jaotc', group_config['javac'])
        elif test_type == 'modules':
            cp = jaotc_test_info[test].get('cp')
            cp = cp() if cp else None
            test_modules(
                opts_set=group_config['modules'],
                classpath=cp,
                main_class=test_info['main'],
                modules=test_info['modules'],
                vm_args=test_info.get('vm_args'),
                program_args=test_info.get('program_args'),
                commands=test_info.get('commands'),
            )
        else:
            mx.abort('Unknown jaotc test: {}'.format(test))


def jaotc_test(args):
    """run (acceptance) tests for the AOT compiler (jaotc)"""
    _check_jaotc_support()
    parser = ArgumentParser(prog='mx jaotc-test')
    parser.add_argument("--list", default=None, action="store_true", help="Print the list of available jaotc tests.")
    parser.add_argument("--group", default='default', action="store", help="Test group {}.".format(jaotc_group_config.keys()))
    parser.add_argument('tests', help='tests to run (omit to run all tests)', nargs=ZERO_OR_MORE)
    args = parser.parse_args(args)

    group = args.group
    if args.list:
        print("The following jaotc tests are available:\n")
        for name in jaotc_group_config[group]['tests']:
            print("  " + name)
        return

    group_tests = jaotc_group_config[group]['tests']
    tests = args.tests or group_tests

    if tests == args.tests:
        for test in tests:
            if not test in group_tests:
                mx.abort('Test {} not on list: {}'.format(test, [name for name in group_tests]))

    jaotc_run(tests, group)

def mktemp_libfile():
    return tempfile.NamedTemporaryFile(prefix=mx.add_lib_prefix(''), suffix=mx.add_lib_suffix(''))

def jaotc_classpath():
    return mx.project('jdk.tools.jaotc.test').output_dir()

jaotc_test_info = {
    'HelloWorld' : {
        'type' : 'app',
        'main' : 'jdk.tools.jaotc.test.HelloWorld'
    },
    'java.base' : {
        'type'    : 'modules',
        'modules' : ['java.base'],
        'cp'      : jaotc_classpath,
        'main'    : 'jdk.tools.jaotc.test.HelloWorld'
    },
    'graal-jlink' : {
        'type'         : 'modules',
        'main'         : 'jdk.tools.jlink.internal.Main',
        'modules'      : ['jdk.internal.vm.ci', 'jdk.internal.vm.compiler', 'jdk.internal.vm.compiler.management'],
        'commands'     : '\n'.join([
            '# exclude troublesome methods',
            r'exclude org\.graalvm\.compiler\.replacements\..*',
            r'exclude org\.graalvm\.compiler\.hotspot\.replacements\..*',
            r'exclude org\.graalvm\.compiler\.nodes\.java\.DynamicNewArrayNode\.new.*',
            r'exclude org\.graalvm\.compiler\.nodes\.PiNode\..*',
            r'exclude org\.graalvm\.compiler\.hotspot\.stubs\.Plugin.*',
            r'exclude org\.graalvm\.compiler\.hotspot\.stubs\.StubUtil\..*',
            r'exclude org\.graalvm\.compiler\.hotspot\.nodes\.GraalHotSpotVMConfigNode\..*',
            r'exclude org\.graalvm\.compiler\..*\.substitutions\..*',
            'exclude org.graalvm.compiler.nodes.java.NewArrayNode.newUninitializedArray(Ljava/lang/Class;I)Ljava/lang/Object;',
            'exclude org.graalvm.compiler.nodes.PiNode.piCastNonNull(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;',
            ''
        ]),
        'vm_args'      : ['-XX:+UnlockExperimentalVMOptions', '-XX:+UseJVMCICompiler', '-Xcomp'],
        'program_args' : ['--list-plugins'],
    },
    'javac'     : {'type' : 'javac'},
}

jaotc_common_opts = ['-ea:org.graalvm...']

jaotc_common_gc_compressed = [
    jaotc_common_opts + [gc, ops]
    for gc in ['-XX:+UseParallelGC', '-XX:+UseG1GC']
    for ops in ['-XX:-UseCompressedOops', '-XX:+UseCompressedOops']
]

jaotc_group_config = {
    'default': {
        'tests': ['HelloWorld', 'javac', 'graal-jlink', 'java.base'],
        'class':   jaotc_common_gc_compressed,
        'javac':   [jaotc_common_opts],
        'modules': [jaotc_common_opts],
    },
    'gate': {
        'tests': ['HelloWorld', 'javac', 'java.base'],
        'class':   jaotc_common_gc_compressed,
        'javac':   [jaotc_common_opts],
        'modules': [jaotc_common_opts],
    },
    'daily': {
        'tests': ['graal-jlink', 'java.base'],
        'modules': jaotc_common_gc_compressed,
    },
    'stress': {
        'tests': ['HelloWorld', 'javac', 'graal-jlink', 'java.base'],
        'class':   jaotc_common_gc_compressed,
        'javac':   jaotc_common_gc_compressed,
        'modules': jaotc_common_gc_compressed,
    },
}

def test_class(opts_set, classpath, main_class, program_args=None):
    """(jaotc-)Compiles simple HelloWorld program.
    Compares the output vs. standard JVM.
    """
    # Run on vanilla JVM.
    program_args = program_args or []
    expected_out = mx.OutputCapture()
    mx_compiler.run_vm((['-cp', classpath] if classpath else []) +
                       [main_class] + program_args, out=expected_out)

    for common_opts in opts_set:
        mx.log('Running {} with {}'.format(main_class, ' '.join(common_opts)))

        with mktemp_libfile() as lib_module:
            lib_module.file.close()
            run_jaotc(['-J' + opt for opt in common_opts] +
                      ['--exit-on-error', '--info', '--output', lib_module.name, main_class],
                      classpath=classpath)
            check_aot(classpath, main_class, common_opts, expected_out.data, lib_module, program_args)

def test_modules(opts_set, classpath, main_class, modules, vm_args, program_args, commands):
    """(jaotc-)Compiles `modules` and runs `main_class` + AOT library.
    Compares the output vs. standard JVM.
    """
    # Run on vanilla JVM.
    program_args = program_args or []
    vm_args = vm_args or []
    commands = commands or ''
    expected_out = mx.OutputCapture()

    mx_compiler.run_vm((['-cp', classpath] if classpath else []) +
                       vm_args +
                       [main_class] + program_args, out=expected_out)

    # jaotc uses ':' as separator.
    module_list = ':'.join(modules)

    for common_opts in opts_set:
        mx.log('(jaotc) Compiling module(s) {} with {}'.format(module_list, ' '.join(common_opts)))
        with mktemp_libfile() as lib_module:
            lib_module.file.close()
            with tempfile.NamedTemporaryFile(mode='w', prefix='cmds_', suffix='.txt') as cmd_file:
                cmd_file.write(commands)
                cmd_file.file.close()
                run_jaotc(['-J' + opt for opt in common_opts] +
                          ['--module', module_list] +
                          ['--compile-commands', cmd_file.name] +
                          ['--exit-on-error', '--info', '--output', lib_module.name])

            check_aot(classpath, main_class, common_opts, expected_out.data, lib_module, program_args)


def collect_java_sources(source_dirs):
    javafilelist = []
    for sourceDir in source_dirs:
        for root, _, files in os.walk(sourceDir, followlinks=True):
            javafiles = [os.path.join(root, name) for name in files if name.endswith('.java')]
            javafilelist += javafiles
    return javafilelist


def test_javac(project_name, opts_set):
    """(jaotc-)Compiles the `jdk.compiler` module and compiles (mx) project_name using `javac` (+ AOT module)."""
    # jaotc uses ':' as separator.
    modules = ':'.join(['jdk.compiler'])
    for common_opts in opts_set:
        out_dir = tempfile.mkdtemp()
        try:
            mx.log('(jaotc) Compiling module(s) {} with {}'.format(modules, ' '.join(common_opts)))
            with mktemp_libfile() as lib_module:
                lib_module.file.close()
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
    mx_compiler.run_vm(common_opts + aot_opts + (['-cp', classpath] if classpath else []) + [main_class] + program_args, out=aot_out)

    if expected_output != aot_out.data:
        mx.abort('Outputs differ, expected `{}` != `{}`'.format(expected_output, aot_out.data))
