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
from contextlib import contextmanager
from os.path import exists
import subprocess
import re

import time

import functools
import mx
import mx_substratevm

_default_image_options = ['-R:+PrintGCSummary', '-R:+PrintGC', '-H:+PrintImageHeapPartitionSizes',
                          '-H:+PrintImageElementSizes']
_bench_configs = {
    'default': (1, _default_image_options),  # multi-threaded, with graal-enterprise
    'single-threaded': (1, _default_image_options + ['-H:-MultiThreaded']),  # single-threaded
    'native': (1, _default_image_options + ['-H:+NativeArchitecture']),
}


_NANOS_PER_SEC = 10 ** 9

_conf_arg_prefix = '--config='
_IMAGE_BENCH_REPETITIONS = 3
_IMAGE_WARM_UP_ITERATIONS = 3

@contextmanager
def _timedelta(name, out=print):
    start = time.time()
    yield
    end = time.time()
    elapsed = end - start
    lf = '' if out == print else '\n'
    out('INFO: TIMEDELTA: ' + name + '%0.2f' % elapsed + lf)


def _bench_result(benchmark, metric_name, metric_value, metric_unit, better="lower", m_iteration=0, m_type="numeric",
                  extra=None):
    if extra is None:
        extra = {}
    unit = {"metric.unit": metric_unit} if metric_unit else {}
    return dict({
                    "benchmark": benchmark,
                    "metric.name": metric_name,
                    "metric.type": m_type,
                    "metric.value": metric_value,
                    "metric.score-function": "id",
                    "metric.better": better,
                    "metric.iteration": m_iteration
                }.items() + unit.items() + extra.items())


def _get_bench_conf(args):
    conf_arg = next((arg for arg in args if arg.startswith(_conf_arg_prefix)), '')
    server_prefix = '--bench-compilation-server'
    server_arg = next((True for arg in args if arg == server_prefix), False)
    if conf_arg:
        conf_arg = conf_arg[len(_conf_arg_prefix):]
        if conf_arg == 'list' or not conf_arg in _bench_configs:
            print('Possible configurations: ' + ','.join(_bench_configs.keys()))
            conf_arg = ''
    if not conf_arg:
        conf_arg = 'default'
    return conf_arg, server_arg


# Called by js-benchmarks to allow customization of (host_vm, host_vm_config) tuple
def host_vm_tuple(args):
    config, _ = _get_bench_conf(args)
    return 'svm', config


def find_collections(log):
    regex = re.compile(r'^\[(?P<type>Incremental|Full) GC.+?, (?P<gctime>[0-9]+(?:\.[0-9]+)?) secs\]$', re.MULTILINE)
    results = [(gc_run.group('type'), int(float(gc_run.group('gctime')) * _NANOS_PER_SEC)) for gc_run in regex.finditer(log)]
    return results


def report_gc_results(benchmark, score, execution_time_ns, gc_times_aggregate):
    gc_time_ns = sum([x for _, x in gc_times_aggregate.values()])
    gc_load_percent = (float(gc_time_ns) / execution_time_ns) * 100

    return [
        _bench_result(benchmark, "gc-load-percent", gc_load_percent, "%"),
        _bench_result(benchmark, "incremental-gc-nanos", gc_times_aggregate["Incremental"][1], "ns"),
        _bench_result(benchmark, "complete-gc-nanos", gc_times_aggregate["Full"][1], "ns"),
        _bench_result(benchmark, "incremental-gc-count", gc_times_aggregate["Incremental"][0], "ns"),
        _bench_result(benchmark, "complete-gc-count", gc_times_aggregate["Full"][1], "ns"),
        _bench_result(benchmark, "throughput-no-gc",
                      execution_time_ns * score / (execution_time_ns - gc_time_ns),
                      None, better="higher"),
    ]


# Called by js-benchmarks to extract additional metrics from benchmark-run output.
def output_processors(host_vm_config, common_functions):
    return [functools.partial(common_functions.gc_stats, find_collections, report_gc_results)]


# Called by js-benchmarks to extract additional metrics from benchmark-run output.
def rule_snippets(host_vm_config):
    image_build_start = r'^INFO: EXECUTE IMAGEBUILD: (?P<imageconf>.+?)\n(?:[\s\S]*?)'
    after_bench_run = r'^(?P<benchmark>[a-zA-Z0-9\.\-_]+):[ \t]+(?:[0-9]+(?:\.[0-9]+)?)(?:[\s\S]+?)'

    default_snippets = [
        (
            image_build_start + r'INFO: TIMEDELTA: IMAGEBUILD-SERVER: (?P<buildtime>.+?)\n',
            _bench_result(("<imageconf>", str), "aot-image-buildtime-server", ("<buildtime>", float), "s")
        ),
        (
            image_build_start + r'INFO: TIMEDELTA: IMAGEBUILD: (?P<buildtime>.+?)\n',
            _bench_result(("<imageconf>", str), "aot-image-buildtime", ("<buildtime>", float), "s")
        ),
        (
            image_build_start + r'INFO: IMAGESIZE: (?P<size>.+?) MiB\n',
            _bench_result(("<imageconf>", str), "aot-image-size", ("<size>", float), "MiB")
        ),
        (
            image_build_start + r'PrintImageHeapPartitionSizes:  partition: readOnlyPrimitive  size: (?P<readonly_primitive_size>.+?)\n',
            _bench_result(("<imageconf>", str), "image-heap-readonly-primitive-size",
                          ("<readonly_primitive_size>", int), "B")
        ),
        (
            image_build_start + r'PrintImageHeapPartitionSizes:  partition: readOnlyReference  size: (?P<readonly_reference_size>.+?)\n',
            _bench_result(("<imageconf>", str), "image-heap-readonly-reference-size",
                          ("<readonly_reference_size>", int), "B")
        ),
        (
            image_build_start + r'PrintImageHeapPartitionSizes:  partition: writablePrimitive  size: (?P<writable_primitive_size>.+?)\n',
            _bench_result(("<imageconf>", str), "image-heap-writable-primitive-size",
                          ("<writable_primitive_size>", int), "B")
        ),
        (
            image_build_start + r'PrintImageHeapPartitionSizes:  partition: writableReference  size: (?P<writable_reference_size>.+?)\n',
            _bench_result(("<imageconf>", str), "image-heap-writable-reference-size",
                          ("<writable_reference_size>", int), "B")
        ),
        (
            image_build_start + r'PrintImageElementSizes:  size: +(?P<element_size>.+?)  name: (?P<element_name>.+?)\n',
            _bench_result(("element-" + "<element_name>", str), "size", ("<element_size>", int), "B",
                          extra={"bench-suite": "js-image"})
        ),
        (
            after_bench_run + r'INFO: TIMEDELTA: IMAGERUN: (?P<runtime>.+?)\n',
            _bench_result(("<benchmark>", str), "time", ("<runtime>", float), "s")
        ),
        (
            after_bench_run + r'PrintGCSummary: CompleteCollectionsTime: (?P<gctime>.+?)\n',
            _bench_result(("<benchmark>", str), "gc-nanos-whole", ("<gctime>", int), "#")
        ),
        (
            after_bench_run + r'PrintGCSummary: CompleteCollectionsCount: (?P<gccount>.+?)\n',
            _bench_result(("<benchmark>", str), "gc-count-whole", ("<gccount>", int), "ns")
        ),
        (
            after_bench_run + r'PrintGCSummary: IncrementalGCCount: (?P<incremental_gc_count>.+?)\n',
            _bench_result(("<benchmark>", str), "incremental-gc-count-whole", ("<incremental_gc_count>", int), "#")
        ),
        (
            after_bench_run + r'PrintGCSummary: IncrementalGCNanos: (?P<incremental_gc_nanos>.+?)\n',
            _bench_result(("<benchmark>", str), "incremental-gc-nanos-whole", ("<incremental_gc_nanos>", int), "ns")
        ),
        (
            after_bench_run + r'PrintGCSummary: CompleteGCCount: (?P<complete_gc_count>.+?)\n',
            _bench_result(("<benchmark>", str), "complete-gc-count-whole", ("<complete_gc_count>", int), "#")
        ),
        (
            after_bench_run + r'PrintGCSummary: CompleteGCNanos: (?P<complete_gc_nanos>.+?)\n',
            _bench_result(("<benchmark>", str), "complete-gc-nanos-whole", ("<complete_gc_nanos>", int), "ns")
        ),
        (
            after_bench_run + r'PrintGCSummary: GCLoadPercent: (?P<gc_load_percent>.+?)\n',
            _bench_result(("<benchmark>", str), "gc-load-percent-whole", ("<gc_load_percent>", int), "%")
        ),
        (
            after_bench_run + r'PrintGCSummary: AllocatedTotalObjectBytes: (?P<mem>.+?)\n',
            _bench_result(("<benchmark>", str), "allocated-memory", ("<mem>", int), "B", better="lower")
        ),
    ]
    return default_snippets


def _bench_image_params(bench_conf):
    image_dir = os.path.join(mx.suite('substratevm').dir, 'svmbenchbuild')
    js_image_name = 'js_' + bench_conf
    image_path = os.path.join(image_dir, js_image_name)
    return image_dir, js_image_name, image_path


def bench_jsimage(bench_conf, out, err, extra_options=None):
    if extra_options is None:
        extra_options = []

    image_dir, js_image_name, image_path = _bench_image_params(bench_conf)
    if not exists(image_path):
        with _timedelta('IMAGEBUILD: ', out=out):
            out('INFO: EXECUTE IMAGEBUILD: svmimage-%s\n' % bench_conf)
            _, image_building_options = _bench_configs[bench_conf]
            command = [mx_substratevm.native_image_path(mx_substratevm.suite_native_image_root()), '--language:js', '-H:Path=' + image_dir,
                       '-H:Name=' + js_image_name] + image_building_options + extra_options
            # Print out the command.
            print(' '.join(command))
            # Run the command and copy the output to out so it can be examined for metrics.
            runner = mx_substratevm.ProcessRunner(command, stderr=subprocess.STDOUT)
            returncode, stdoutdata, _ = runner.run(timeout=240)
            out(stdoutdata)
            if runner.timedout:
                mx.abort('Javascript image building for js-benchmarks timed out.')
            if returncode != 0:
                mx.abort('Javascript image building for js-benchmarks failed.')
            # Generate the image size metric.
            image_statinfo = os.stat(image_path)
            image_size = image_statinfo.st_size / 1024.0 / 1024.0
            out('INFO: IMAGESIZE: %0.2f MiB\n' % image_size)
    return image_path


def _bench_compile_server(bench_conf, out):
    def delete_image(image_path):
        if os.path.exists(image_path):
            os.remove(image_path)

    def build_js_image_in_server(stdouterr):
        return bench_jsimage(bench_conf, stdouterr, stdouterr)

    devnull = open(os.devnull, 'w').write
    for i in range(_IMAGE_WARM_UP_ITERATIONS):
        print("Building js image in the image build server: " + str(i))
        image_path = build_js_image_in_server(devnull)
        delete_image(image_path)

    print('Measuring performance of js image compilation in the server.')
    for _ in range(_IMAGE_BENCH_REPETITIONS):
        with _timedelta("IMAGEBUILD-SERVER: ", out=out):
            out('INFO: EXECUTE IMAGEBUILD: svmimage-%s\n' % bench_conf)
            image_path = build_js_image_in_server(devnull)
        delete_image(image_path)


# Called by js-benchmarks to run a javascript benchmark
def run_js(vmArgs, jsArgs, nonZeroIsFatal, out, err, cwd):
    bench_conf, should_bench_compile_server = _get_bench_conf(vmArgs)
    _, _, image_path = _bench_image_params(bench_conf)
    if should_bench_compile_server and not exists(image_path):
        for _ in range(_IMAGE_BENCH_REPETITIONS):
            with mx_substratevm.native_image_context():
                _bench_compile_server(bench_conf, out)

    image_path = bench_jsimage(bench_conf, out=out, err=err)
    if image_path:
        vmArgs = [vmArg for vmArg in vmArgs if not (vmArg.startswith(_conf_arg_prefix) or vmArg == '--bench-compilation-server')]

        all_args = vmArgs + jsArgs
        mx.logv("Running substratevm image '%s' with '%s' i.e.:" % (image_path, all_args))
        mx.logv(image_path + " " + " ".join(all_args))
        runner = mx_substratevm.ProcessRunner([image_path] + all_args, stderr=subprocess.STDOUT)

        timeout_factor, _ = _bench_configs[bench_conf]
        with _timedelta('IMAGERUN: ', out=out):
            returncode, stdoutdata, _ = runner.run(8 * 60 * timeout_factor)
            if runner.timedout:
                if nonZeroIsFatal:
                    mx.abort('Javascript benchmark timeout')
                return -1
            out(stdoutdata)
            return returncode
    if nonZeroIsFatal:
        mx.abort('Javascript image building for js-benchmarks failed')
    return -1
