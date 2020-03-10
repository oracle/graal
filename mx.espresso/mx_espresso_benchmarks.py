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
import mx_sdk_vm


_suite = mx.suite('espresso')


class EspressoVM(mx_benchmark.JavaVm):
    """
    Espresso (launcher) JVM within GraalVM.
    """
    def __init__(self, config_name):
        super(EspressoVM, self).__init__()
        self._config_name = config_name

    def hosting_registry(self):
        return mx_benchmark.java_vm_registry

    def name(self):
        return "espresso"

    def config_name(self):
        return self._config_name

    def run(self, cwd, args):
        return mx.run(mx_espresso._espresso_launcher_command(args), cwd=cwd)


# Register Espresso (launcher) as a JVM for running `mx benchmark`.
mx_benchmark.java_vm_registry.add_vm(EspressoVM('launcher'), _suite)
