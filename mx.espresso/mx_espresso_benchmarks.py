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

from mx_benchmark import GuestVm, JavaVm

_suite = mx.suite('espresso')


class EspressoVm(GuestVm, JavaVm):
    def __init__(self, config_name, options, host_vm=None):
        super(EspressoVm, self).__init__(host_vm=host_vm)
        self._config_name = config_name
        self._options = options

    def name(self):
        return 'espresso'

    def config_name(self):
        return self._config_name

    def hosting_registry(self):
        return mx_benchmark.java_vm_registry

    def with_host_vm(self, host_vm):
        return self.__class__(self.config_name(), self._options, host_vm)

    def run(self, cwd, args):
        if hasattr(self.host_vm(), 'run_launcher'):
            return self.host_vm().run_launcher('espresso', self._options + args, cwd)
        else:
            return self.host_vm().run(cwd, mx_espresso._espresso_standalone_command(self._options + args))


class EspressoMinHeapVm(EspressoVm):
    # Runs benchmarks multiple times until it finds the minimum size of max heap (`-Xmx`) required to complete the execution within a given overhead factor.
    # The minimum heap size is stored in an extra dimension.
    def __init__(self, ovh_factor, min_heap, max_heap, config_name, options, host_vm=None):
        super(EspressoMinHeapVm, self).__init__(config_name=config_name, options=options, host_vm=host_vm)
        self.ovh_factor = ovh_factor
        self.min_heap = min_heap
        self.max_heap = max_heap

    def name(self):
        return super(EspressoMinHeapVm, self).name() + '-minheap'

    def with_host_vm(self, host_vm):
        return self.__class__(self.ovh_factor, self.min_heap, self.max_heap, self.config_name(), self._options, host_vm)

    def run(self, cwd, args):
        class PTimeout(object):
            def __init__(self, ptimeout):
                self.ptimeout = ptimeout

            def __enter__(self):
                self.prev_ptimeout = mx.get_opts().ptimeout
                mx.get_opts().ptimeout = self.ptimeout
                return self

            def __exit__(self, exc_type, exc_value, traceback):
                mx.get_opts().ptimeout = self.prev_ptimeout

        run_info = {}
        def run_with_heap(heap, args, timeout, suppressStderr=True, nonZeroIsFatal=False):
            mx.log('Trying with %sMB of heap...' % heap)
            with PTimeout(timeout):
                if hasattr(self.host_vm(), 'run_launcher'):
                    _args = self._options + ['--jvm.Xmx{}M'.format(heap)] + args
                    _exit_code, stdout, dims = self.host_vm().run_launcher('espresso', _args, cwd)
                else:
                    _args = ['-Xmx{}M'.format(heap)] + mx_espresso._espresso_standalone_command(self._options + args)
                    _exit_code, stdout, dims = self.host_vm().run(cwd, _args)
                if _exit_code:
                    mx.log('failed')
                else:
                    mx.log('succeeded')
                    run_info['stdout'] = stdout
                    run_info['dims'] = dims
            return _exit_code

        exit_code = mx.run_java_min_heap(args=args, benchName='# MinHeap', overheadFactor=self.ovh_factor, minHeap=self.min_heap, maxHeap=self.max_heap, cwd=cwd, run_with_heap=run_with_heap)
        return exit_code, run_info['stdout'], run_info['dims']


# Register soon-to-become-default configurations.
mx_benchmark.java_vm_registry.add_vm(EspressoVm('default', []), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoVm('inline-accessors', ['--experimental-options', '--java.InlineFieldAccessors']), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoMinHeapVm(0, 0, 64, 'infinite-overhead', []), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoMinHeapVm(1.5, 0, 2048, '1.5-overhead', []), _suite)

