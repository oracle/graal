#
# Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
#

import mx
import mx_benchmark
import mx_espresso
import mx_sdk_vm_impl

_suite = mx.suite('espresso')


class EspressoLauncherVM(mx_benchmark.JavaVm):
    """
    Espresso (launcher) within GraalVM.
    """

    def __init__(self, config_name):
        super(EspressoLauncherVM, self).__init__()
        self._config_name = config_name

    def hosting_registry(self):
        return mx_benchmark.java_vm_registry

    def name(self):
        return "espresso"

    def config_name(self):
        return self._config_name

    def run(self, cwd, args):
        capture = mx.TeeOutputCapture(mx.OutputCapture())
        ret_code = mx.run(mx_espresso._espresso_launcher_command(args),
                          nonZeroIsFatal=False, out=capture, err=capture, cwd=cwd)

        output = capture.underlying.data
        graalvm_dist = mx_sdk_vm_impl.get_final_graalvm_distribution()
        return [ret_code, output, {'vm.graalvm.config': graalvm_dist.vm_config_name,
                                   'vm.graalvm.dist': graalvm_dist.name}]
