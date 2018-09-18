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

import mx, mx_benchmark
import mx_sdk, mx_vm

import os

_suite = mx.suite('vm')


class GraalVm(mx_benchmark.OutputCapturingJavaVm):
    def __init__(self, name, config_name, extra_java_args, extra_lang_args):
        """
        :type name: str
        :type config_name: str
        :type extra_java_args: list[str] | None
        :type extra_lang_args: list[str] | None
        """
        self._name = name
        self._config_name = config_name
        self.extra_java_args = extra_java_args or []
        self.extra_lang_args = extra_lang_args or []

    def name(self):
        return self._name

    def config_name(self):
        return self._config_name

    def post_process_command_line_args(self, args):
        return self.extra_java_args + args

    def post_process_lang_command_line_args(self, args):
        return self.extra_lang_args + args

    def dimensions(self, cwd, args, code, out):
        return {}

    def run_java(self, args, out=None, err=None, cwd=None, nonZeroIsFatal=False):
        """Run 'java' workloads."""
        return mx.run([os.path.join(mx_vm.graalvm_home(fatalIfMissing=True), 'bin', 'java')] + args, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)

    def run_lang(self, cmd, args, cwd):
        """Run the 'cmd' command in the 'bin' directory."""
        out = mx.TeeOutputCapture(mx.OutputCapture())
        args = self.post_process_lang_command_line_args(args)
        mx.log("Running {} on {} with args: {}".format(cmd, self.name(), args))
        code = mx.run([os.path.join(mx_vm.graalvm_home(fatalIfMissing=True), 'bin', cmd)] + args, out=out, err=out, cwd=cwd, nonZeroIsFatal=False)
        out = out.underlying.data
        dims = self.dimensions(cwd, args, code, out)
        return code, out, dims


def register_graalvm_vms():
    graalvm_hostvm_name = mx_vm.graalvm_dist_name().lower().replace('_', '-')
    for name, java_args, lang_args, priority in mx_sdk.graalvm_hostvm_configs:
        mx_benchmark.java_vm_registry.add_vm(GraalVm(graalvm_hostvm_name, name, java_args, lang_args), _suite, priority)
