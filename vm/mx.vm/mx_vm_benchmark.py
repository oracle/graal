#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
import os
import re
from os.path import basename, dirname, getsize, join
from traceback import print_tb
import inspect
import subprocess

import mx
import mx_benchmark
import mx_sdk_vm
import mx_sdk_vm_impl

_suite = mx.suite('vm')
_native_image_vm_registry = mx_benchmark.VmRegistry('NativeImage', 'ni-vm')
_gu_vm_registry = mx_benchmark.VmRegistry('GraalUpdater', 'gu-vm')
_polybench_vm_registry = mx_benchmark.VmRegistry('PolyBench', 'polybench-vm')
_polybench_modes = [
    ('standard', ['--mode=standard']),
    ('interpreter', ['--mode=interpreter']),
]


class GraalVm(mx_benchmark.OutputCapturingJavaVm):
    def __init__(self, name, config_name, extra_java_args, extra_launcher_args):
        """
        :type name: str
        :type config_name: str
        :type extra_java_args: list[str] | None
        :type extra_launcher_args: list[str] | None
        """
        super(GraalVm, self).__init__()
        self._name = name
        self._config_name = config_name
        self.extra_java_args = extra_java_args or []
        self.extra_launcher_args = extra_launcher_args or []
        self.debug_args = mx.java_debug_args() if config_name == "jvm" else []

    def name(self):
        return self._name

    def config_name(self):
        return self._config_name

    def post_process_command_line_args(self, args):
        return self.extra_java_args + self.debug_args + args

    def post_process_launcher_command_line_args(self, args):
        return self.extra_launcher_args + \
               ['--vm.' + x[1:] if x.startswith('-X') else x for x in self.debug_args] + \
               args

    def home(self):
        if self.name() == 'native-image-java-home':
            return mx.get_jdk().home
        return mx_sdk_vm_impl.graalvm_home(fatalIfMissing=True)

    def generate_java_command(self, args):
        return [os.path.join(self.home(), 'bin', 'java')] + args

    def run_java(self, args, out=None, err=None, cwd=None, nonZeroIsFatal=False):
        """Run 'java' workloads."""
        self.extract_vm_info(args)
        cmd = self.generate_java_command(args)
        cmd = mx.apply_command_mapper_hooks(cmd, self.command_mapper_hooks)
        return mx.run(cmd, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)

    def run_lang(self, cmd, args, cwd):
        """Deprecated. Call 'run_launcher' instead."""
        mx.log_deprecation("'run_lang' is deprecated. Use 'run_launcher' instead.")
        return self.run_launcher(cmd, args, cwd)

    def run_launcher(self, cmd, args, cwd):
        """Run the 'cmd' command in the 'bin' directory."""
        args = self.post_process_launcher_command_line_args(args)
        self.extract_vm_info(args)
        mx.log("Running '{}' on '{}' with args: '{}'".format(cmd, self.name(), " ".join(args)))
        out = mx.TeeOutputCapture(mx.OutputCapture())
        command = [os.path.join(self.home(), 'bin', cmd)] + args
        command = mx.apply_command_mapper_hooks(command, self.command_mapper_hooks)
        code = mx.run(command, out=out, err=out, cwd=cwd, nonZeroIsFatal=False)
        out = out.underlying.data
        dims = self.dimensions(cwd, args, code, out)
        return code, out, dims


class NativeImageVM(GraalVm):
    """
    This is a VM that should be used for running all Native Image benchmarks. This VM should support all the benchmarks
    that a regular Java VM supports as it:
       1) Runs a benchmark with the Native Image Agent.
       2) Builds an image based on the configuration collected by the agent.
       3) Runs the image of the benchmark with supported VM arguments and with run-time arguments.
    """

    class BenchmarkConfig:
        def __init__(self, vm, bm_suite, args):
            self.bmSuite = bm_suite
            self.benchmark_suite_name = bm_suite.benchSuiteName(args) if len(inspect.getargspec(bm_suite.benchSuiteName).args) > 1 else bm_suite.benchSuiteName() # pylint: disable=deprecated-method
            self.benchmark_name = bm_suite.benchmarkName()
            self.executable, self.classpath_arguments, self.system_properties, self.vm_args, cmd_line_image_run_args = NativeImageVM.extract_benchmark_arguments(args)
            self.extra_image_build_arguments = bm_suite.extra_image_build_argument(self.benchmark_name, args)
            # use list() to create fresh copies to safeguard against accidental modification
            self.image_run_args = bm_suite.extra_run_arg(self.benchmark_name, args, list(cmd_line_image_run_args))
            self.extra_agent_run_args = bm_suite.extra_agent_run_arg(self.benchmark_name, args, list(cmd_line_image_run_args))
            self.extra_profile_run_args = bm_suite.extra_profile_run_arg(self.benchmark_name, args, list(cmd_line_image_run_args))
            self.extra_agent_profile_run_args = bm_suite.extra_agent_profile_run_arg(self.benchmark_name, args, list(cmd_line_image_run_args))
            self.benchmark_output_dir = bm_suite.benchmark_output_dir(self.benchmark_name, args)
            self.pgo_iteration_num = None
            self.params = ['extra-image-build-argument', 'extra-run-arg', 'extra-agent-run-arg', 'extra-profile-run-arg',
                           'extra-agent-profile-run-arg', 'benchmark-output-dir', 'stages', 'skip-agent-assertions']
            self.profile_file_extension = '.iprof'
            self.stages = bm_suite.stages(args)
            self.last_stage = self.stages[-1]
            self.skip_agent_assertions = bm_suite.skip_agent_assertions(self.benchmark_name, args)
            self.root_dir = self.benchmark_output_dir if self.benchmark_output_dir else mx.suite('vm').get_output_root(platformDependent=False, jdkDependent=False)
            unique_suite_name = '{}-{}'.format(self.bmSuite.benchSuiteName(), self.bmSuite.version().replace('.', '-')) if self.bmSuite.version() != 'unknown' else self.bmSuite.benchSuiteName()
            self.executable_name = (unique_suite_name + '-' + self.benchmark_name).lower() if self.benchmark_name else unique_suite_name.lower()
            self.final_image_name = self.executable_name + '-' + vm.config_name()
            self.output_dir = mx.join(os.path.abspath(self.root_dir), 'native-image-benchmarks', self.executable_name + '-' + vm.config_name())
            self.profile_path_no_extension = os.path.join(self.output_dir, self.executable_name)
            self.latest_profile_path = self.profile_path_no_extension + '-latest' + self.profile_file_extension
            self.config_dir = os.path.join(self.output_dir, 'config')
            self.log_dir = self.output_dir
            self.analysis_report_path = os.path.join(self.output_dir, self.executable_name + '-analysis.json')
            self.image_build_report_path = os.path.join(self.output_dir, self.executable_name + '-image-build-stats.json')
            self.base_image_build_args = [os.path.join(vm.home(), 'bin', 'native-image')]
            self.base_image_build_args += ['--no-fallback', '-g', '--allow-incomplete-classpath']
            self.base_image_build_args += ['-H:+VerifyGraalGraphs', '-H:+VerifyPhases', '--diagnostics-mode'] if vm.is_gate else []
            self.base_image_build_args += ['-J-ea', '-J-esa'] if vm.is_gate and not bm_suite.skip_build_assertions(self.benchmark_name) else []

            self.base_image_build_args += self.system_properties
            self.base_image_build_args += self.classpath_arguments
            self.base_image_build_args += self.executable
            self.base_image_build_args += ['-H:Path=' + self.output_dir]
            self.base_image_build_args += ['-H:ConfigurationFileDirectories=' + self.config_dir]
            self.base_image_build_args += ['-H:+PrintAnalysisStatistics', '-H:AnalysisStatisticsFile=' + self.analysis_report_path]
            self.base_image_build_args += ['-H:+PrintCallEdges']
            self.base_image_build_args += ['-H:+CollectImageBuildStatistics', '-H:ImageBuildStatisticsFile=' + self.image_build_report_path]
            if vm.is_quickbuild:
                self.base_image_build_args += ['-Ob']
            if vm.is_llvm:
                self.base_image_build_args += ['-H:CompilerBackend=llvm', '-H:Features=org.graalvm.home.HomeFinderFeature', '-H:DeadlockWatchdogInterval=0']
            if vm.gc:
                self.base_image_build_args += ['--gc=' + vm.gc, '-H:+SpawnIsolates']
            if vm.native_architecture:
                self.base_image_build_args += ['-H:+NativeArchitecture']
            self.base_image_build_args += self.extra_image_build_arguments

    def __init__(self, name, config_name, extra_java_args=None, extra_launcher_args=None, **kwargs):
        super(NativeImageVM, self).__init__(name, config_name, extra_java_args, extra_launcher_args)
        if len(kwargs) > 0:
            mx.log_deprecation("Ignoring NativeImageVM custom configuration! Use named configuration instead.")
            mx.warn("Ignoring: {}".format(kwargs))

        self.vm_args = None
        self.pgo_aot_inline = False
        self.pgo_instrumented_iterations = 0
        self.pgo_context_sensitive = True
        self.pgo_inline_explored = False
        self.hotspot_pgo = False
        self.is_gate = False
        self.is_quickbuild = False
        self.is_llvm = False
        self.gc = None
        self.native_architecture = False
        self.graalvm_edition = None
        self._configure_from_name(config_name)

    def _configure_from_name(self, config_name):
        if not config_name:
            mx.abort("config_name must be set. Use 'default' for the default {} configuration.".format(self.__class__.__name__))

        # special case for the 'default' configuration, other configurations are handled by the regex to ensure consistent ordering
        if config_name == "default":
            return
        if config_name == "default-ce":
            self.graalvm_edition = "ce"
            return
        if config_name == "default-ee":
            self.graalvm_edition = "ee"
            return

        # This defines the allowed config names for NativeImageVM. The ones registered will be available via --jvm-config
        rule = r'^(?P<native_architecture>native-architecture-)?(?P<gate>gate-)?(?P<quickbuild>quickbuild-)?(?P<gc>g1gc-)?(?P<llvm>llvm-)?(?P<pgo>pgo-|pgo-hotspot-|pgo-ctx-insens-)?(?P<inliner>aot-inline-|iterative-|inline-explored-)?(?P<edition>ce-|ee-)?$'

        mx.logv("== Registering configuration: {}".format(config_name))
        match_name = "{}-".format(config_name)  # adding trailing dash to simplify the regex
        matching = re.match(rule, match_name)
        if not matching:
            mx.abort("{} configuration is invalid: {}".format(self.__class__.__name__, config_name))

        if matching.group("native_architecture") is not None:
            mx.logv("'native-architecture' is enabled for {}".format(config_name))
            self.native_architecture = True

        if matching.group("gate") is not None:
            mx.logv("'gate' mode is enabled for {}".format(config_name))
            self.is_gate = True

        if matching.group("quickbuild") is not None:
            mx.logv("'quickbuild' is enabled for {}".format(config_name))
            self.is_quickbuild = True

        if matching.group("gc") is not None:
            gc = matching.group("gc")[:-1]
            if gc == "g1gc":
                mx.logv("'g1gc' is enabled for {}".format(config_name))
                self.gc = "G1"
            else:
                mx.abort("Unknown GC: {}".format(gc))

        if matching.group("llvm") is not None:
            mx.logv("'llvm' mode is enabled for {}".format(config_name))
            self.is_llvm = True

        if matching.group("pgo") is not None:
            pgo_mode = matching.group("pgo")[:-1]
            if pgo_mode == "pgo":
                mx.logv("'pgo' is enabled for {}".format(config_name))
                self.pgo_instrumented_iterations = 1
            elif pgo_mode == "pgo-hotspot":
                mx.logv("'pgo-hotspot' is enabled for {}".format(config_name))
                self.hotspot_pgo = True
            elif pgo_mode == "pgo-ctx-insens":
                mx.logv("'pgo-ctx-insens' is enabled for {}".format(config_name))
                self.pgo_instrumented_iterations = 1
                self.pgo_context_sensitive = False
            else:
                mx.abort("Unknown pgo mode: {}".format(pgo_mode))

        if matching.group("inliner") is not None:
            inliner = matching.group("inliner")[:-1]
            if self.pgo_instrumented_iterations < 1:
                mx.abort("The selected inliner require PGO! Invalid configuration: {}".format(config_name))
            if inliner == "aot-inline":
                mx.logv("'aot-inline' is enabled for {}".format(config_name))
                self.pgo_aot_inline = True
            elif inliner == "iterative":
                mx.logv("'iterative' inliner is enabled for {}".format(config_name))
                self.pgo_instrumented_iterations = 3
            elif inliner == "inline-explored":
                mx.logv("'inline-explored' is enabled for {}".format(config_name))
                self.pgo_instrumented_iterations = 3
                self.pgo_inline_explored = True
            else:
                mx.abort("Unknown inliner configuration: {}".format(inliner))

        if matching.group("edition") is not None:
            edition = matching.group("edition")[:-1]
            mx.logv("GraalVM edition is set to: {}".format(edition))
            self.graalvm_edition = edition

    @staticmethod
    def supported_vm_arg_prefixes():
        """
            This list is intentionally restrictive. We want to be sure that what we add is correct on the case-by-case
            basis. In the future we can convert this from a failure into a warning.
            :return: a list of args supported by native image.
        """
        return ['-D', '-Xmx', '-Xmn', '-XX:-PrintGC', '-XX:+PrintGC', '--add-opens', '--add-modules', '--add-exports',
                '--add-reads']

    _VM_OPTS_SPACE_SEPARATED_ARG = ['-mp', '-modulepath', '-limitmods', '-addmods', '-upgrademodulepath', '-m',
                                    '--module-path', '--limit-modules', '--add-modules', '--upgrade-module-path',
                                    '--module', '--module-source-path', '--add-exports', '--add-opens', '--add-reads',
                                    '--patch-module', '--boot-class-path', '--source-path', '-cp', '-classpath']

    @staticmethod
    def _split_vm_arguments(args):
        i = 0
        while i < len(args):
            arg = args[i]
            if arg == '-jar':
                return args[:i], args[i:i + 2], args[i + 2:]
            elif not arg.startswith('-'):
                return args[:i], [args[i]], args[i + 1:]
            elif arg in NativeImageVM._VM_OPTS_SPACE_SEPARATED_ARG:
                i += 2
            else:
                i += 1

        mx.abort('No executable found in args: ' + str(args))

    @staticmethod
    def extract_benchmark_arguments(args):
        i = 0
        clean_args = args[:]
        while i < len(args):
            if args[i].startswith('--jvmArgsPrepend'):
                clean_args[i + 1] = ' '.join([x for x in args[i + 1].split(' ') if "-Dnative-image" not in x])
                i += 2
            else:
                i += 1
        clean_args = [x for x in clean_args if "-Dnative-image" not in x]
        vm_args, executable, image_run_args = NativeImageVM._split_vm_arguments(clean_args)

        classpath_arguments = []
        system_properties = [a for a in vm_args if a.startswith('-D')]
        image_vm_args = []
        i = 0
        while i < len(vm_args):
            vm_arg = vm_args[i]
            if vm_arg.startswith('--class-path'):
                classpath_arguments.append(vm_arg)
                i += 1
            elif vm_arg.startswith('-cp') or vm_arg.startswith('-classpath'):
                classpath_arguments += [vm_arg, vm_args[i + 1]]
                i += 2
            else:
                if not any(vm_arg.startswith(elem) for elem in NativeImageVM.supported_vm_arg_prefixes()):
                    mx.abort('Unsupported argument ' + vm_arg + '.' +
                             ' Currently supported argument prefixes are: ' + str(NativeImageVM.supported_vm_arg_prefixes()))
                if vm_arg in NativeImageVM._VM_OPTS_SPACE_SEPARATED_ARG:
                    image_vm_args.append(vm_args[i])
                    image_vm_args.append(vm_args[i + 1])
                    i += 2
                else:
                    image_vm_args.append(vm_arg)
                    i += 1

        return executable, classpath_arguments, system_properties, image_vm_args, image_run_args

    class Stages:
        def __init__(self, config, bench_out, bench_err, is_gate, non_zero_is_fatal, cwd):
            self.stages_till_now = []
            self.config = config
            self.bench_out = bench_out
            self.bench_err = bench_err
            self.final_image_name = config.final_image_name
            self.is_gate = is_gate
            self.non_zero_is_fatal = non_zero_is_fatal
            self.cwd = cwd
            self.failed = False

            self.current_stage = ''
            self.exit_code = None
            self.command = None
            self.stderr_path = None
            self.stdout_path = None

        def reset_stage(self):
            self.current_stage = ''
            self.exit_code = None
            self.command = None
            self.stderr_path = None
            self.stdout_path = None

        def __enter__(self):
            self.stdout_path = os.path.abspath(os.path.join(self.config.log_dir, self.final_image_name + '-' + self.current_stage + '-stdout.log'))
            self.stderr_path = os.path.abspath(os.path.join(self.config.log_dir, self.final_image_name + '-' + self.current_stage + '-stderr.log'))
            self.stdout_file = open(self.stdout_path, 'w')
            self.stderr_file = open(self.stderr_path, 'w')

            self.separator_line()
            mx.log('Entering stage: ' + self.current_stage + ' for ' + self.final_image_name)
            self.separator_line()

            mx.log('Running: ')
            mx.log(' '.join(self.command))

            if self.stdout_path:
                mx.log('The standard output is saved to ' + str(self.stdout_path))
            if self.stderr_path:
                mx.log('The standard error is saved to ' + str(self.stderr_path))

            return self

        def __exit__(self, tp, value, tb):
            self.stdout_file.flush()
            self.stderr_file.flush()

            if self.exit_code == 0 and (tb is None):
                if self.current_stage.startswith(self.config.last_stage):
                    self.bench_out('Successfully finished the last specified stage:' + ' ' + self.current_stage + ' for ' + self.final_image_name)
                else:
                    mx.log('Successfully finished stage:' + ' ' + self.current_stage)

                self.separator_line()
            else:
                self.failed = True
                if self.exit_code is not None and self.exit_code != 0:
                    mx.log(mx.colorize('Failed in stage ' + self.current_stage + ' for ' + self.final_image_name + ' with exit code ' + str(self.exit_code), 'red'))
                    if self.stdout_path:
                        mx.log(mx.colorize('--------- Standard output:', 'blue'))
                        with open(self.stdout_path, 'r') as stdout:
                            mx.log(stdout.read())

                    if self.stderr_path:
                        mx.log(mx.colorize('--------- Standard error:', 'red'))
                        with open(self.stderr_path, 'r') as stderr:
                            mx.log(stderr.read())

                if tb:
                    mx.log(mx.colorize('Failed in stage ' + self.current_stage + ' with ', 'red'))
                    print_tb(tb)

                self.separator_line()

                if len(self.stages_till_now) > 0:
                    mx.log(mx.colorize('--------- To run the failed benchmark execute the following: ', 'green'))
                    mx.log(mx.current_mx_command())

                    if len(self.stages_till_now[:-1]) > 0:
                        mx.log(mx.colorize('--------- To only prepare the benchmark add the following to the end of the previous command: ', 'green'))
                        mx.log('-Dnative-image.benchmark.stages=' + ','.join(self.stages_till_now[:-1]))

                    mx.log(mx.colorize('--------- To only run the failed stage add the following to the end of the previous command: ', 'green'))
                    mx.log('-Dnative-image.benchmark.stages=' + self.current_stage)

                    mx.log(mx.colorize('--------- Additional arguments that can be used for debugging the benchmark go after the final --: ', 'green'))
                    for param in self.config.params:
                        mx.log('-Dnative-image.benchmark.' + param + '=')

                self.separator_line()
                if self.non_zero_is_fatal:
                    mx.abort('Exiting the benchmark due to the failure.')

            self.stdout_file.close()
            self.stderr_file.close()
            self.reset_stage()

        def stdout(self, include_bench_out=False):
            def writeFun(s):
                v = self.stdout_file.write(s)
                if include_bench_out:
                    self.bench_out(s)
                else:
                    mx.logv(s, end='')
                return v
            return writeFun

        def stderr(self, include_bench_err=False):
            def writeFun(s):
                v = self.stdout_file.write(s)
                if include_bench_err:
                    self.bench_err(s)
                else:
                    mx.logv(s, end='')
                return v
            return writeFun

        def change_stage(self, *argv):
            if self.failed:
                return False

            stage_name = '-'.join(argv)
            self.stages_till_now.append(stage_name)
            self.current_stage = stage_name
            stage_applies = argv[0] in self.config.stages or stage_name in self.config.stages
            return stage_applies

        @staticmethod
        def separator_line():
            mx.log(mx.colorize('-' * 120, 'green'))

        def set_command(self, command):
            self.command = command
            return self

        def execute_command(self, vm=None):
            write_output = self.current_stage == 'run' or self.current_stage == 'image' or self.is_gate
            cmd = self.command
            self.exit_code = self.config.bmSuite.run_stage(vm, self.current_stage, cmd, self.stdout(write_output), self.stderr(write_output), self.cwd, False)
            if "image" not in self.current_stage and self.config.bmSuite.validateReturnCode(self.exit_code):
                self.exit_code = 0

    def image_build_statistics_rules(self, benchmark):
        objects_list = ["total_array_store",
                          "total_assertion_error_nullary",
                          "total_assertion_error_object",
                          "total_class_cast",
                          "total_division_by_zero",
                          "total_illegal_argument_exception_argument_is_not_an_array",
                          "total_illegal_argument_exception_negative_length",
                          "total_integer_exact_overflow",
                          "total_long_exact_overflow",
                          "total_null_pointer",
                          "total_out_of_bounds"]
        metric_objects = ["total_devirtualized_invokes"]
        for obj in objects_list:
            metric_objects.append(obj + "_after_parse_canonicalization")
            metric_objects.append(obj + "_before_high_tier")
            metric_objects.append(obj + "_after_high_tier")
        rules = []
        for i in range(0, len(metric_objects)):
            rules.append(mx_benchmark.JsonStdOutFileRule(r'^# Printing image build statistics to: (?P<path>\S+?)$', 'path', {
                "benchmark": benchmark,
                "metric.name": "image-build-stats",
                "metric.type": "numeric",
                "metric.unit": "#",
                "metric.value": ("<"+metric_objects[i]+">", int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "metric.object": metric_objects[i].replace("_", "-").replace("total-", ""),
            }, [metric_objects[i]]))
        return rules

    def rules(self, output, benchmarks, bmSuiteArgs):
        class NativeImageTimeToInt(object):
            def __call__(self, *args, **kwargs):
                return int(float(args[0].replace(',', '')))

        class NativeImageHexToInt(object):
            def __call__(self, *args, **kwargs):
                return int(args[0], 16)

        return [
            mx_benchmark.StdOutRule(
                r"The executed image size for benchmark (?P<bench_suite>[a-zA-Z0-9_\-]+):(?P<benchmark>[a-zA-Z0-9_\-]+) is (?P<value>[0-9]+) B",
                {
                    "bench-suite": ("<bench_suite>", str),
                    "benchmark": ("<benchmark>", str),
                    "vm": "svm",
                    "metric.name": "binary-size",
                    "metric.value": ("<value>", int),
                    "metric.unit": "B",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": 0,
                }),
            mx_benchmark.StdOutRule(
                r"The (?P<type>[a-zA-Z0-9_\-]+) configuration size for benchmark (?P<bench_suite>[a-zA-Z0-9_\-]+):(?P<benchmark>[a-zA-Z0-9_\-]+) is (?P<value>[0-9]+) B",
                {
                    "bench-suite": ("<bench_suite>", str),
                    "benchmark": ("<benchmark>", str),
                    "vm": "svm",
                    "metric.name": "config-size",
                    "metric.value": ("<value>", int),
                    "metric.unit": "B",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": 0,
                    "metric.object": ("<type>", str)
                }),
            mx_benchmark.StdOutRule(r'^\[\S+:[0-9]+\][ ]+\[total\]:[ ]+(?P<time>[0-9,.]+?) ms', {
                "benchmark": benchmarks[0],
                "metric.name": "compile-time",
                "metric.type": "numeric",
                "metric.unit": "ms",
                "metric.value": ("<time>", NativeImageTimeToInt()),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "metric.object": "total",
            }),
            mx_benchmark.StdOutRule(r'^\[\S+:[0-9]+\][ ]+(?P<phase>\w+?):[ ]+(?P<time>[0-9,.]+?) ms', {
                "benchmark": benchmarks[0],
                "metric.name": "compile-time",
                "metric.type": "numeric",
                "metric.unit": "ms",
                "metric.value": ("<time>", NativeImageTimeToInt()),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "metric.object": ("<phase>", str),
            }),
            mx_benchmark.StdOutRule(r'^[ ]*[0-9]+[ ]+.(?P<section>[a-zA-Z0-9._-]+?)[ ]+(?P<size>[0-9a-f]+?)[ ]+', {
                "benchmark": benchmarks[0],
                "metric.name": "binary-section-size",
                "metric.type": "numeric",
                "metric.unit": "B",
                "metric.value": ("<size>", NativeImageHexToInt()),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "metric.object": ("<section>", str),
            }),
            mx_benchmark.JsonStdOutFileRule(r'^# Printing analysis results stats to: (?P<path>\S+?)$', 'path', {
                "benchmark": benchmarks[0],
                "metric.name": "analysis-stats",
                "metric.type": "numeric",
                "metric.unit": "#",
                "metric.value": ("<total_call_edges>", int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "metric.object": "call-edges",
            }, ['total_call_edges']),
            mx_benchmark.JsonStdOutFileRule(r'^# Printing analysis results stats to: (?P<path>\S+?)$', 'path', {
                "benchmark": benchmarks[0],
                "metric.name": "analysis-stats",
                "metric.type": "numeric",
                "metric.unit": "#",
                "metric.value": ("<total_reachable_types>", int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "metric.object": "reachable-types",
            }, ['total_reachable_types']),
            mx_benchmark.JsonStdOutFileRule(r'^# Printing analysis results stats to: (?P<path>\S+?)$', 'path', {
                "benchmark": benchmarks[0],
                "metric.name": "analysis-stats",
                "metric.type": "numeric",
                "metric.unit": "#",
                "metric.value": ("<total_reachable_methods>", int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "metric.object": "reachable-methods",
            }, ['total_reachable_methods']),
            mx_benchmark.JsonStdOutFileRule(r'^# Printing analysis results stats to: (?P<path>\S+?)$', 'path', {
                "benchmark": benchmarks[0],
                "metric.name": "analysis-stats",
                "metric.type": "numeric",
                "metric.unit": "#",
                "metric.value": ("<total_reachable_fields>", int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "metric.object": "reachable-fields",
            }, ['total_reachable_fields']),
            mx_benchmark.JsonStdOutFileRule(r'^# Printing analysis results stats to: (?P<path>\S+?)$', 'path', {
                "benchmark": benchmarks[0],
                "metric.name": "analysis-stats",
                "metric.type": "numeric",
                "metric.unit": "B",
                "metric.value": ("<total_memory_bytes>", int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "metric.object": "memory"
            }, ['total_memory_bytes'])
        ] + self.image_build_statistics_rules(benchmarks[0])

    def run_stage_agent(self, config, stages):
        profile_path = config.profile_path_no_extension + '-agent' + config.profile_file_extension
        hotspot_vm_args = ['-ea', '-esa'] if self.is_gate and not config.skip_agent_assertions else []
        hotspot_run_args = []
        hotspot_vm_args += ['-agentlib:native-image-agent=config-output-dir=' + str(config.config_dir), '-XX:-UseJVMCINativeLibrary']

        if config.vm_args is not None:
            hotspot_vm_args += config.vm_args

        if self.hotspot_pgo:
            hotspot_vm_args += ['-Dgraal.PGOInstrument=' + profile_path]

        if self.hotspot_pgo and not self.is_gate and config.extra_agent_profile_run_args:
            hotspot_run_args += config.extra_agent_profile_run_args
        else:
            hotspot_run_args += config.extra_agent_run_args

        hotspot_args = hotspot_vm_args + config.classpath_arguments + config.executable + config.system_properties + hotspot_run_args
        with stages.set_command(self.generate_java_command(hotspot_args)) as s:
            s.execute_command()
            if self.hotspot_pgo and s.exit_code == 0:
                # Hotspot instrumentation does not produce profiling information for the helloworld benchmark
                if os.path.exists(profile_path):
                    mx.copyfile(profile_path, config.latest_profile_path)
                else:
                    mx.warn("No profile information emitted during agent run.")

    def run_stage_instrument_image(self, config, stages, out, i, instrumentation_image_name, image_path, image_path_latest, instrumented_iterations):
        executable_name_args = ['-H:Name=' + instrumentation_image_name]
        pgo_verification_output_path = os.path.join(config.output_dir, instrumentation_image_name + '-probabilities.log')
        pgo_args = ['--pgo=' + config.latest_profile_path, '-H:+VerifyPGOProfiles', '-H:VerificationDumpFile=' + pgo_verification_output_path]
        pgo_args += ['-H:' + ('+' if self.pgo_context_sensitive else '-') + 'EnablePGOContextSensitivity']
        pgo_args += ['-H:+AOTInliner'] if self.pgo_aot_inline else ['-H:-AOTInliner']
        instrument_args = ['--pgo-instrument'] + ([] if i == 0 else pgo_args)
        instrument_args += ['-H:+InlineAllExplored'] if self.pgo_inline_explored else []

        with stages.set_command(config.base_image_build_args + executable_name_args + instrument_args) as s:
            s.execute_command()
            if s.exit_code == 0:
                mx.copyfile(image_path, image_path_latest)
            if i + 1 == instrumented_iterations and s.exit_code == 0:
                image_size = os.stat(image_path).st_size
                out('Instrumented image size: ' + str(image_size) + ' B')

    def run_stage_instrument_run(self, config, stages, image_path, profile_path):
        image_run_cmd = [image_path, '-XX:ProfilesDumpFile=' + profile_path]
        image_run_cmd += config.extra_profile_run_args
        with stages.set_command(image_run_cmd) as s:
            s.execute_command()
            if s.exit_code == 0:
                mx.copyfile(profile_path, config.latest_profile_path)

    def run_stage_image(self, config, stages):
        executable_name_args = ['-H:Name=' + config.final_image_name]
        pgo_verification_output_path = os.path.join(config.output_dir, config.final_image_name + '-probabilities.log')
        pgo_args = ['--pgo=' + config.latest_profile_path, '-H:+VerifyPGOProfiles', '-H:VerificationDumpFile=' + pgo_verification_output_path]
        pgo_args += ['-H:' + ('+' if self.pgo_context_sensitive else '-') + 'EnablePGOContextSensitivity']
        pgo_args += ['-H:+AOTInliner'] if self.pgo_aot_inline else ['-H:-AOTInliner']
        final_image_command = config.base_image_build_args + executable_name_args + (pgo_args if self.pgo_instrumented_iterations > 0 or (self.hotspot_pgo and os.path.exists(config.latest_profile_path)) else [])
        with stages.set_command(final_image_command) as s:
            s.execute_command()

    def run_stage_run(self, config, stages, out):
        image_path = os.path.join(config.output_dir, config.final_image_name)
        with stages.set_command([image_path] + config.image_run_args) as s:
            s.execute_command(vm=self)
            if s.exit_code == 0:
                # The image size for benchmarks is tracked by printing on stdout and matching the rule.
                image_size = os.stat(image_path).st_size
                out('The executed image size for benchmark ' + config.benchmark_suite_name + ':' + config.benchmark_name + ' is ' + str(image_size) + ' B')
                image_sections_command = "objdump -h " + image_path
                out(subprocess.check_output(image_sections_command, shell=True, universal_newlines=True))
                for config_type in ['jni', 'proxy', 'predefined-classes', 'reflect', 'resource', 'serialization']:
                    config_path = os.path.join(config.config_dir, config_type + '-config.json')
                    if os.path.exists(config_path):
                        config_size = os.stat(config_path).st_size
                        out('The ' + config_type + ' configuration size for benchmark ' + config.benchmark_suite_name + ':' + config.benchmark_name + ' is ' + str(config_size) + ' B')

    def run_java(self, args, out=None, err=None, cwd=None, nonZeroIsFatal=False):

        if '-version' in args:
            return super(NativeImageVM, self).run_java(args, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)

        if self.bmSuite is None:
            mx.abort("Benchmark suite was not registed.")

        if not callable(getattr(self.bmSuite, "run_stage", None)):
            mx.abort("Benchmark suite is not a NativeImageMixin.")

        # never fatal, we handle it ourselves
        config = NativeImageVM.BenchmarkConfig(self, self.bmSuite, args)
        stages = NativeImageVM.Stages(config, out, err, self.is_gate, True if self.is_gate else nonZeroIsFatal, os.path.abspath(cwd if cwd else os.getcwd()))
        instrumented_iterations = self.pgo_instrumented_iterations if config.pgo_iteration_num is None else int(config.pgo_iteration_num)

        if not os.path.exists(config.output_dir):
            os.makedirs(config.output_dir)

        if not os.path.exists(config.config_dir):
            os.makedirs(config.config_dir)

        if stages.change_stage('agent'):
            if instrumented_iterations == 0 and config.last_stage.startswith('instrument-'):
                config.last_stage = 'agent'
            self.run_stage_agent(config, stages)

        if not self.hotspot_pgo:
            # Native Image profile collection
            for i in range(instrumented_iterations):
                profile_path = config.profile_path_no_extension + '-' + str(i) + config.profile_file_extension
                instrumentation_image_name = config.executable_name + '-instrument-' + str(i)
                instrumentation_image_latest = config.executable_name + '-instrument-latest'

                image_path = os.path.join(config.output_dir, instrumentation_image_name)
                image_path_latest = os.path.join(config.output_dir, instrumentation_image_latest)
                if stages.change_stage('instrument-image', str(i)):
                    self.run_stage_instrument_image(config, stages, out, i, instrumentation_image_name, image_path, image_path_latest, instrumented_iterations)

                if stages.change_stage('instrument-run', str(i)):
                    self.run_stage_instrument_run(config, stages, image_path, profile_path)

        # Build the final image
        if stages.change_stage('image'):
            self.run_stage_image(config, stages)

        # Execute the benchmark
        if stages.change_stage('run'):
            self.run_stage_run(config, stages, out)

    def create_log_files(self, config, executable_name, stage):
        stdout_path = os.path.abspath(
            os.path.join(config.log_dir, executable_name + '-' + stage.current_stage + '-stdout.log'))
        stderr_path = os.path.abspath(
            os.path.join(config.log_dir, executable_name + '-' + stage.current_stage + '-stderr.log'))
        return stderr_path, stdout_path


class NativeImageBuildVm(GraalVm):
    def run(self, cwd, args):
        return self.run_launcher('native-image', args, cwd)


class GuVm(GraalVm):
    def run(self, cwd, args):
        return self.run_launcher('gu', ['rebuild-images'] + args, cwd)


class NativeImageBuildBenchmarkSuite(mx_benchmark.VmBenchmarkSuite):
    def __init__(self, name, benchmarks, registry):
        super(NativeImageBuildBenchmarkSuite, self).__init__()
        self._name = name
        self._benchmarks = benchmarks
        self._registry = registry

    def group(self):
        return 'Graal'

    def subgroup(self):
        return 'substratevm'

    def name(self):
        return self._name

    def benchmarkList(self, bmSuiteArgs):
        return list(self._benchmarks.keys())

    def createVmCommandLineArgs(self, benchmarks, runArgs):
        if not benchmarks:
            benchmarks = self.benchmarkList(runArgs)

        cmd_line_args = []
        for bench in benchmarks:
            cmd_line_args += self._benchmarks[bench]
        return cmd_line_args + runArgs

    def get_vm_registry(self):
        return self._registry

    def rules(self, output, benchmarks, bmSuiteArgs):
        class NativeImageTimeToInt(object):
            def __call__(self, *args, **kwargs):
                return int(float(args[0].replace(',', '')))

        return [
            mx_benchmark.StdOutRule(r'^\[(?P<benchmark>\S+?):[0-9]+\][ ]+\[total\]:[ ]+(?P<time>[0-9,.]+?) ms', {
                "bench-suite": self.name(),
                "benchmark": ("<benchmark>", str),
                "metric.name": "time",
                "metric.type": "numeric",
                "metric.unit": "ms",
                "metric.value": ("<time>", NativeImageTimeToInt()),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
            })
        ]


class AgentScriptJsBenchmarkSuite(mx_benchmark.VmBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin):
    def __init__(self):
        super(AgentScriptJsBenchmarkSuite, self).__init__()
        self._benchmarks = {
            'plain' : [],
            'triple' : ['--insight=sieve-filter1.js'],
            'single' : ['--insight=sieve-filter2.js'],
            'iterate' : ['--insight=sieve-filter3.js'],
        }

    def group(self):
        return 'Graal'

    def subgroup(self):
        return 'graal-js'

    def name(self):
        return 'agentscript'

    def version(self):
        return '0.1.0'

    def benchmarkList(self, bmSuiteArgs):
        return self._benchmarks.keys()

    def failurePatterns(self):
        return [
            re.compile(r'error:'),
            re.compile(r'internal error:'),
            re.compile(r'Error in'),
            re.compile(r'\tat '),
            re.compile(r'Defaulting the .*\. Consider using '),
            re.compile(r'java.lang.OutOfMemoryError'),
        ]

    def successPatterns(self):
        return [
            re.compile(r'Hundred thousand prime numbers in [0-9]+ ms', re.MULTILINE),
        ]

    def rules(self, out, benchmarks, bmSuiteArgs):
        assert len(benchmarks) == 1
        return [
            mx_benchmark.StdOutRule(r'^Hundred thousand prime numbers in (?P<time>[0-9]+) ms$', {
                "bench-suite": self.name(),
                "benchmark": (benchmarks[0], str),
                "metric.name": "warmup",
                "metric.type": "numeric",
                "metric.unit": "ms",
                "metric.value": ("<time>", int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": ("$iteration", int),
            })
        ]

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        return self.vmArgs(bmSuiteArgs) + super(AgentScriptJsBenchmarkSuite, self).createCommandLineArgs(benchmarks, bmSuiteArgs)

    def workingDirectory(self, benchmarks, bmSuiteArgs):
        return join(_suite.dir, 'benchmarks', 'agentscript')

    def createVmCommandLineArgs(self, benchmarks, runArgs):
        if not benchmarks:
            raise mx.abort("Benchmark suite '{}' cannot run multiple benchmarks in the same VM process".format(self.name()))
        if len(benchmarks) != 1:
            raise mx.abort("Benchmark suite '{}' can run only one benchmark at a time".format(self.name()))
        return self._benchmarks[benchmarks[0]] + ['-e', 'count=50'] + runArgs + ['sieve.js']

    def get_vm_registry(self):
        return mx_benchmark.js_vm_registry

    def run(self, benchmarks, bmSuiteArgs):
        results = super(AgentScriptJsBenchmarkSuite, self).run(benchmarks, bmSuiteArgs)
        self.addAverageAcrossLatestResults(results)
        return results


class ExcludeWarmupRule(mx_benchmark.StdOutRule):
    """Rule that behaves as the StdOutRule, but skips input until a certain pattern."""

    def __init__(self, *args, **kwargs):
        self.startPattern = re.compile(kwargs.pop('startPattern'))
        super(ExcludeWarmupRule, self).__init__(*args, **kwargs)

    def parse(self, text):
        m = self.startPattern.search(text)
        if m:
            return super(ExcludeWarmupRule, self).parse(text[m.end()+1:])
        else:
            return []


class PolyBenchBenchmarkSuite(mx_benchmark.VmBenchmarkSuite):
    def __init__(self):
        super(PolyBenchBenchmarkSuite, self).__init__()
        self._extensions = [".js", ".rb", ".wasm", ".bc", ".py", ".jar", ".pmh"]

    def _get_benchmark_root(self):
        if not hasattr(self, '_benchmark_root'):
            dist_name = "POLYBENCH_BENCHMARKS"
            distribution = mx.distribution(dist_name)
            _root = distribution.get_output()
            if not os.path.exists(_root):
                msg = "The distribution {} does not exist: {}{}".format(dist_name, _root, os.linesep)
                msg += "This might be solved by running: mx build --dependencies={}".format(dist_name)
                mx.abort(msg)
            self._benchmark_root = _root
        return self._benchmark_root

    def group(self):
        return "Graal"

    def subgroup(self):
        return "truffle"

    def name(self):
        return "polybench"

    def version(self):
        return "0.1.0"

    def benchmarkList(self, bmSuiteArgs):
        if not hasattr(self, "_benchmarks"):
            self._benchmarks = []
            for group in ["interpreter", "compiler", "warmup", "nfi"]:
                dir_path = os.path.join(self._get_benchmark_root(), group)
                for f in os.listdir(dir_path):
                    f_path = os.path.join(dir_path, f)
                    if os.path.isfile(f_path) and os.path.splitext(f_path)[1] in self._extensions:
                        self._benchmarks.append(os.path.join(group, f))
        return self._benchmarks

    def workingDirectory(self, benchmarks, bmSuiteArgs):
        return self._get_benchmark_root()

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None or len(benchmarks) != 1:
            mx.abort("Must specify one benchmark at a time.")
        vmArgs = self.vmArgs(bmSuiteArgs)
        benchmark_path = os.path.join(self._get_benchmark_root(), benchmarks[0])
        return ["--path=" + benchmark_path] + vmArgs

    def get_vm_registry(self):
        return _polybench_vm_registry

    def rules(self, output, benchmarks, bmSuiteArgs):
        metric_name = self._get_metric_name(bmSuiteArgs)
        rules = []
        if metric_name == "time":
            # Special case for metric "time": Instead of reporting the aggregate numbers,
            # report individual iterations. Two metrics will be reported:
            # - "warmup" includes all iterations (warmup and run)
            # - "time" includes only the "run" iterations
            rules += [
                mx_benchmark.StdOutRule(r"\[(?P<name>.*)\] iteration ([0-9]*): (?P<value>.*) (?P<unit>.*)", {
                    "benchmark": ("<name>", str),
                    "metric.better": "lower",
                    "metric.name": "warmup",
                    "metric.unit": ("<unit>", str),
                    "metric.value": ("<value>", float),
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.iteration": ("$iteration", int),
                }),
                ExcludeWarmupRule(r"\[(?P<name>.*)\] iteration (?P<iteration>[0-9]*): (?P<value>.*) (?P<unit>.*)", {
                    "benchmark": ("<name>", str),
                    "metric.better": "lower",
                    "metric.name": "time",
                    "metric.unit": ("<unit>", str),
                    "metric.value": ("<value>", float),
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.iteration": ("<iteration>", int),
                }, startPattern=r"::: Running :::")
            ]
        elif metric_name == "allocated-memory":
            rules += [
                ExcludeWarmupRule(r"\[(?P<name>.*)\] iteration (?P<iteration>[0-9]*): (?P<value>.*) (?P<unit>.*)", {
                    "benchmark": ("<name>", str),
                    "metric.better": "lower",
                    "metric.name": "allocated-memory",
                    "metric.unit": ("<unit>", str),
                    "metric.value": ("<value>", float),
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.iteration": ("<iteration>", int),
                }, startPattern=r"::: Running :::")
            ]
        else:
            rules += [
                mx_benchmark.StdOutRule(r"\[(?P<name>.*)\] after run: (?P<value>.*) (?P<unit>.*)", {
                    "benchmark": ("<name>", str),
                    "metric.better": "lower",
                    "metric.name": metric_name,
                    "metric.unit": ("<unit>", str),
                    "metric.value": ("<value>", float),
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.iteration": 0,
                })
            ]
        rules += [
            mx_benchmark.StdOutRule(r"### Truffle Context eval time \(ms\): (?P<delta>[0-9]+)", {
                "benchmark": benchmarks[0],
                "metric.name": "context-eval-time",
                "metric.value": ("<delta>", float),
                "metric.unit": "ms",
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0
            })
        ]
        return rules

    def _get_metric_name(self, bmSuiteArgs):
        metric = None
        for arg in bmSuiteArgs:
            if arg.startswith("--metric="):
                metric = arg[len("--metric="):]
                break
        if metric == "compilation-time":
            return "compile-time"
        elif metric == "partial-evaluation-time":
            return "pe-time"
        elif metric == "one-shot":
            return "one-shot"
        elif metric == "allocated-bytes":
            return "allocated-memory"
        else:
            return "time"


class FileSizeBenchmarkSuite(mx_benchmark.VmBenchmarkSuite):
    SZ_MSG_PATTERN = "== binary size == {} is {} bytes, path = {}\n"
    SZ_RGX_PATTERN = r"== binary size == (?P<image_name>[a-zA-Z0-9_\-\.:]+) is (?P<value>[0-9]+) bytes, path = (?P<path>.*)"


    def group(self):
        return "Graal"

    def subgroup(self):
        return "truffle"

    def name(self):
        return "file-size"

    def version(self):
        return "0.0.1"

    def benchmarkList(self, bmSuiteArgs):
        return ["default"]

    def get_vm_registry(self):
        return _polybench_vm_registry

    def runAndReturnStdOut(self, benchmarks, bmSuiteArgs):
        vm = self.get_vm_registry().get_vm_from_suite_args(bmSuiteArgs)
        vm.extract_vm_info(self.vmArgs(bmSuiteArgs))
        host_vm = None
        if isinstance(vm, mx_benchmark.GuestVm):
            host_vm = vm.host_vm()
            assert host_vm
        name = 'graalvm-ee' if mx_sdk_vm_impl.has_component('svmee') else 'graalvm-ce'
        dims = {
            # the vm and host-vm fields are hardcoded to one of the accepted names of the field
            "vm": name,
            "host-vm": name,
            "host-vm-config": self.host_vm_config_name(host_vm, vm),
            "guest-vm": name if host_vm else "none",
            "guest-vm-config": self.guest_vm_config_name(host_vm, vm),
        }

        out = ""
        output_root = mx_sdk_vm_impl.get_final_graalvm_distribution().get_output_root()

        def get_size_message(image_name, image_location):
            return FileSizeBenchmarkSuite.SZ_MSG_PATTERN.format(image_name, getsize(join(output_root, image_location)), image_location, output_root)

        for location in mx_sdk_vm_impl.get_all_native_image_locations(include_libraries=True, include_launchers=False, abs_path=False):
            lib_name = 'lib:' + mx_sdk_vm_impl.remove_lib_prefix_suffix(basename(location))
            out += get_size_message(lib_name, location)
        for location in mx_sdk_vm_impl.get_all_native_image_locations(include_libraries=False, include_launchers=True, abs_path=False):
            launcher_name = mx_sdk_vm_impl.remove_exe_suffix(basename(location))
            out += get_size_message(launcher_name, location)
        if out:
            mx.log(out, end='')
        return 0, out, dims

    def rules(self, output, benchmarks, bmSuiteArgs):
        return [
            mx_benchmark.StdOutRule(
                FileSizeBenchmarkSuite.SZ_RGX_PATTERN,
                {
                    "bench-suite": self.name(),
                    "benchmark": ("<image_name>", str),
                    "benchmark-configuration": ("<path>", str),
                    "vm": "svm",
                    "metric.name": "binary-size",
                    "metric.value": ("<value>", int),
                    "metric.unit": "B",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": 0,
                })
        ]


class PolyBenchVm(GraalVm):
    def run(self, cwd, args):
        return self.run_launcher('polybench', args, cwd)


mx_benchmark.add_bm_suite(NativeImageBuildBenchmarkSuite(name='native-image', benchmarks={'js': ['--language:js']}, registry=_native_image_vm_registry))
mx_benchmark.add_bm_suite(NativeImageBuildBenchmarkSuite(name='gu', benchmarks={'js': ['js'], 'libpolyglot': ['libpolyglot']}, registry=_gu_vm_registry))
mx_benchmark.add_bm_suite(AgentScriptJsBenchmarkSuite())
mx_benchmark.add_bm_suite(PolyBenchBenchmarkSuite())
mx_benchmark.add_bm_suite(FileSizeBenchmarkSuite())


def register_graalvm_vms():
    default_host_vm_name = mx_sdk_vm_impl.graalvm_dist_name().lower().replace('_', '-')
    host_vm_names = ([default_host_vm_name.replace('-java8', '')] if '-java8' in default_host_vm_name else []) + [default_host_vm_name]
    for host_vm_name in host_vm_names:
        for config_name, java_args, launcher_args, priority in mx_sdk_vm.get_graalvm_hostvm_configs():
            mx_benchmark.java_vm_registry.add_vm(GraalVm(host_vm_name, config_name, java_args, launcher_args), _suite, priority)
            for mode, mode_options in _polybench_modes:
                _polybench_vm_registry.add_vm(PolyBenchVm(host_vm_name, config_name + "-" + mode, [], mode_options + launcher_args))
        if mx_sdk_vm_impl.has_component('svm'):
            _native_image_vm_registry.add_vm(NativeImageBuildVm(host_vm_name, 'default', [], []), _suite, 10)
            _gu_vm_registry.add_vm(GuVm(host_vm_name, 'default', [], []), _suite, 10)

    for short_name, config_suffix in [('niee', 'ee'), ('ni', 'ce')]:
        if any(component.short_name == short_name for component in mx_sdk_vm_impl.registered_graalvm_components(stage1=False)):
            for main_config in ['default', 'gate', 'llvm', 'native-architecture']:
                final_config_name = '{}-{}'.format(main_config, config_suffix)
                mx_benchmark.add_java_vm(NativeImageVM('native-image', final_config_name), _suite, 10)
            break

    # Adding JAVA_HOME VMs to be able to run benchmarks on GraalVM binaries without the need of building it first
    for java_home_config in ['default', 'pgo', 'g1gc', 'g1gc-pgo', 'quickbuild', 'quickbuild-g1gc']:
        mx_benchmark.add_java_vm(NativeImageVM('native-image-java-home', java_home_config), _suite, 5)


    # Add VMs for libgraal
    if mx_sdk_vm_impl.has_component('LibGraal'):
        libgraal_location = mx_sdk_vm_impl.get_native_image_locations('LibGraal', 'jvmcicompiler')
        if libgraal_location is not None:
            import mx_graal_benchmark
            mx_graal_benchmark.build_jvmci_vm_variants('server', 'graal-core-libgraal',
                                                       ['-server', '-XX:+EnableJVMCI', '-Dgraal.CompilerConfiguration=community', '-Djvmci.Compiler=graal', '-XX:+UseJVMCINativeLibrary', '-XX:JVMCILibPath=' + dirname(libgraal_location)],
                                                       mx_graal_benchmark._graal_variants, suite=_suite, priority=15, hosted=False)
