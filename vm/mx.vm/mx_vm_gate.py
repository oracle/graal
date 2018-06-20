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

import mx
import mx_vm
import mx_subst
from mx_gate import Task

import re
import subprocess
from os.path import join
import functools

_suite = mx.suite('vm')


class VmGateTasks:
    graal = 'graal'
    graal_js = 'graal-js'
    sulong = 'sulong'


_openjdk_version_regex = re.compile(r'openjdk version \"[0-9_.]+\"\nOpenJDK Runtime Environment \(build [0-9a-z_\-.]+\)\nGraalVM (?P<graalvm_version>[0-9a-z_\-.]+) \(build [0-9a-z\-.]+, mixed mode\)')
_anyjdk_version_regex = re.compile(r'(openjdk|java) version \"[0-9_.]+\"\n(OpenJDK|Java\(TM\) SE) Runtime Environment \(build [0-9a-z_\-.]+\)\nGraalVM (?P<graalvm_version>[0-9a-z_\-.]+) \(build [0-9a-z\-.]+, mixed mode\)')


def gate(args, tasks):
    with Task('Vm: Basic GraalVM Tests', tasks, tags=[VmGateTasks.graal]) as t:
        if t:
            _java = join(mx_vm.graalvm_output(), 'bin', 'java')

            _out = mx.OutputCapture()
            if mx.run([_java, '-XX:+JVMCIPrintProperties'], nonZeroIsFatal=False, out=_out, err=_out):
                mx.log_error(_out.data)
                mx.abort('The GraalVM image is not built with a JVMCI-enabled JDK, it misses `-XX:+JVMCIPrintProperties`.')

            _out = subprocess.check_output([_java, '-version'], stderr=subprocess.STDOUT)
            if args.strict_mode:
                # A full open-source build should be built with an open-source JDK
                _version_regex = _openjdk_version_regex
            else:
                # Allow Oracle JDK in non-strict mode as it is common on developer machines
                _version_regex = _anyjdk_version_regex
            match = _version_regex.match(_out)
            if match is None:
                if args.strict_mode and _anyjdk_version_regex.match(_out):
                    mx.abort("In --strict-mode, GraalVM must be built with OpenJDK")
                else:
                    mx.abort('Unexpected version string:\n{}Does not match:\n{}'.format(_out, _version_regex.pattern))
            elif match.group('graalvm_version') != _suite.release_version():
                mx.abort("Wrong GraalVM version in -version string: got '{}', expected '{}'".format(match.group('graalvm_version'), _suite.release_version()))

    if mx_vm.has_component('js'):
        with Task('Vm: Graal.js tests', tasks, tags=[VmGateTasks.graal_js]) as t:
            if t:
                pass

    gate_sulong(tasks)

def gate_sulong(tasks):
    with Task('Run SulongSuite tests as native-image', tasks, tags=[VmGateTasks.sulong]) as t:
        if t:
            lli = join(mx_vm.graalvm_output(), 'bin', 'lli')
            sulong = mx.suite('sulong')
            sulong.extensions.testLLVMImage(lli, libPath=False, unittestArgs=['--enable-timing'])

    with Task('Run Sulong interop tests as native-image', tasks, tags=[VmGateTasks.sulong]) as t:
        if t:
            sulong = mx.suite('sulong')
            svm = mx.suite('substratevm')
            native_image_cmd = join(mx_vm.graalvm_output(), 'bin', 'native-image')
            with svm.extensions.native_image_context(svm.extensions.IMAGE_ASSERTION_FLAGS, native_image_cmd=native_image_cmd) as native_image:
                # TODO Use mx_vm.get_final_graalvm_distribution().find_single_source_location to rewire SULONG_LIBS
                sulong_libs = join(mx_vm.graalvm_output(), 'jre', 'languages', 'llvm')
                def distribution_paths(dname):
                    path_substitutions = {
                        'SULONG_LIBS': sulong_libs
                    }
                    return path_substitutions.get(dname, mx._get_dependency_path(dname))
                mx_subst.path_substitutions.register_with_arg('path', distribution_paths)
                sulong.extensions.runLLVMUnittests(functools.partial(svm.extensions.native_junit, native_image, build_args=['--language:llvm']))
