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
import tempfile
import json
from genericpath import exists
from os.path import basename, dirname, getsize, join
from traceback import print_tb
import inspect
import subprocess

import mx
import mx_benchmark
import mx_sdk_vm
import mx_sdk_vm_impl

_suite = mx.suite('vm')
_polybench_vm_registry = mx_benchmark.VmRegistry('PolyBench', 'polybench-vm')
_polybench_modes = [
    ('standard', ['--mode=standard']),
    ('interpreter', ['--mode=interpreter']),
]

POLYBENCH_METRIC_MAPPING = {
    "compilation-time": "compile-time",
    "partial-evaluation-time": "pe-time",
    "allocated-bytes": "allocated-memory",
    "peak-time": "time"
}  # Maps some polybench metrics to standardized metric names


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
        self.debug_args = mx.java_debug_args() if "jvm" in config_name else []

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
        mx.log(f"Running '{cmd}' on '{self.name()}' with args: '{' '.join(args)}'")
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
            self.benchmark_suite_name = bm_suite.benchSuiteName(args) if len(inspect.getfullargspec(bm_suite.benchSuiteName).args) > 1 else bm_suite.benchSuiteName()
            self.benchmark_name = bm_suite.benchmarkName()
            self.executable, self.classpath_arguments, self.system_properties, self.image_vm_args, image_run_args = NativeImageVM.extract_benchmark_arguments(args)
            self.extra_image_build_arguments = bm_suite.extra_image_build_argument(self.benchmark_name, args)
            # use list() to create fresh copies to safeguard against accidental modification
            self.image_run_args = bm_suite.extra_run_arg(self.benchmark_name, args, list(image_run_args))
            self.extra_agent_run_args = bm_suite.extra_agent_run_arg(self.benchmark_name, args, list(image_run_args))
            self.extra_agentlib_options = bm_suite.extra_agentlib_options(self.benchmark_name, args, list(image_run_args))
            for option in self.extra_agentlib_options:
                if option.startswith('config-output-dir'):
                    mx.abort("config-output-dir must not be set in the extra_agentlib_options.")
            self.extra_profile_run_args = bm_suite.extra_profile_run_arg(self.benchmark_name, args, list(image_run_args))
            self.extra_agent_profile_run_args = bm_suite.extra_agent_profile_run_arg(self.benchmark_name, args, list(image_run_args))
            self.benchmark_output_dir = bm_suite.benchmark_output_dir(self.benchmark_name, args)
            self.pgo_iteration_num = bm_suite.pgo_iteration_num(self.benchmark_name, args)
            self.params = ['extra-image-build-argument', 'extra-run-arg', 'extra-agent-run-arg', 'extra-profile-run-arg',
                           'extra-agent-profile-run-arg', 'benchmark-output-dir', 'stages', 'skip-agent-assertions']

            self.profile_file_extension = '.iprof'
            self.stages = bm_suite.stages(args)
            if vm.jdk_profiles_collect:  # forbid image build/run in the profile collection execution mode
                for stage in ('image', 'run'):
                    if stage in self.stages:
                        self.stages.remove(stage)
            self.last_stage = self.stages[-1]
            self.skip_agent_assertions = bm_suite.skip_agent_assertions(self.benchmark_name, args)
            self.root_dir = self.benchmark_output_dir if self.benchmark_output_dir else mx.suite('vm').get_output_root(platformDependent=False, jdkDependent=False)
            unique_suite_name = f"{self.bmSuite.benchSuiteName()}-{self.bmSuite.version().replace('.', '-')}" if self.bmSuite.version() != 'unknown' else self.bmSuite.benchSuiteName()
            self.executable_name = (unique_suite_name + '-' + self.benchmark_name).lower() if self.benchmark_name else unique_suite_name.lower()
            self.final_image_name = self.executable_name + '-' + vm.config_name()
            self.output_dir = mx.join(os.path.abspath(self.root_dir), 'native-image-benchmarks', self.executable_name + '-' + vm.config_name())
            self.profile_path_no_extension = os.path.join(self.output_dir, self.executable_name)
            self.latest_profile_path = self.profile_path_no_extension + '-latest' + self.profile_file_extension
            self.config_dir = os.path.join(self.output_dir, 'config')
            self.log_dir = self.output_dir
            self.analysis_report_path = os.path.join(self.output_dir, self.executable_name + '-analysis.json')
            self.image_build_stats_file = bm_suite.image_build_stats_file(self, args)
            self.base_image_build_args = [os.path.join(vm.home(), 'bin', 'native-image')]
            self.base_image_build_args += ['--no-fallback', '-g']
            self.base_image_build_args += ['-H:+VerifyGraalGraphs', '-H:+VerifyPhases', '--diagnostics-mode'] if vm.is_gate else []
            self.base_image_build_args += bm_suite.build_assertions(self.benchmark_name, vm.is_gate)

            self.base_image_build_args += self.system_properties
            self.base_image_build_args += self.classpath_arguments
            self.base_image_build_args += self.executable
            self.base_image_build_args += ['-H:Path=' + self.output_dir]
            self.base_image_build_args += ['-H:ConfigurationFileDirectories=' + self.config_dir]
            self.base_image_build_args += ['-H:+PrintAnalysisStatistics', '-H:AnalysisStatisticsFile=' + self.analysis_report_path]
            self.base_image_build_args += ['-H:+PrintCallEdges']
            self.base_image_build_args += ['-H:+CollectImageBuildStatistics', '-H:ImageBuildStatisticsFile=' + self.image_build_stats_file]
            if vm.is_quickbuild:
                self.base_image_build_args += ['-Ob']
            if vm.use_string_inlining:
                self.base_image_build_args += ['-H:+UseStringInlining']
            if vm.is_llvm:
                self.base_image_build_args += ['-H:CompilerBackend=llvm', '-H:Features=org.graalvm.home.HomeFinderFeature', '-H:DeadlockWatchdogInterval=0']
            if vm.gc:
                self.base_image_build_args += ['--gc=' + vm.gc, '-H:+SpawnIsolates']
            if vm.native_architecture:
                self.base_image_build_args += ['-H:+NativeArchitecture']
            if vm.analysis_context_sensitivity:
                self.base_image_build_args += ['-H:AnalysisContextSensitivity=' + vm.analysis_context_sensitivity, '-H:-RemoveSaturatedTypeFlows', '-H:+AliasArrayTypeFlows']
            if vm.no_inlining_before_analysis:
                self.base_image_build_args += ['-H:-InlineBeforeAnalysis']
            if self.image_vm_args is not None:
                self.base_image_build_args += self.image_vm_args
            self.base_image_build_args += self.extra_image_build_arguments

    def __init__(self, name, config_name, extra_java_args=None, extra_launcher_args=None, **kwargs):
        super(NativeImageVM, self).__init__(name, config_name, extra_java_args, extra_launcher_args)
        if len(kwargs) > 0:
            mx.log_deprecation("Ignoring NativeImageVM custom configuration! Use named configuration instead.")
            mx.warn(f"Ignoring: {kwargs}")

        self.vm_args = None
        self.pgo_aot_inline = False
        self.pgo_instrumented_iterations = 0
        self.pgo_context_sensitive = True
        self.is_gate = False
        self.is_quickbuild = False
        self.use_string_inlining = False
        self.is_llvm = False
        self.gc = None
        self.native_architecture = False
        self.use_upx = False
        self.graalvm_edition = None
        self.config = None
        self.stages = None
        self.ml = None
        self.jdk_profiles_collect = False
        self.cached_jdk_pgo = False
        self.analysis_context_sensitivity = None
        self.no_inlining_before_analysis = False
        self._configure_from_name(config_name)

    def _configure_from_name(self, config_name):
        if not config_name:
            mx.abort(f"config_name must be set. Use 'default' for the default {self.__class__.__name__} configuration.")

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
        rule = r'^(?P<native_architecture>native-architecture-)?(?P<string_inlining>string-inlining-)?(?P<gate>gate-)?(?P<upx>upx-)?(?P<quickbuild>quickbuild-)?(?P<gc>g1gc-)?(?P<llvm>llvm-)?(?P<pgo>pgo-|pgo-ctx-insens-)?(?P<inliner>aot-inline-|iterative-|inline-explored-)?' \
               r'(?P<analysis_context_sensitivity>insens-|allocsens-|1obj-|2obj1h-|3obj2h-|4obj3h-)?(?P<no_inlining_before_analysis>no-inline-)?(?P<ml>ml-profile-inference-)?(?P<jdk_profiles>jdk-profiles-collect-|cached-jdk-pgo-)?(?P<edition>ce-|ee-)?$'

        mx.logv(f"== Registering configuration: {config_name}")
        match_name = f"{config_name}-"  # adding trailing dash to simplify the regex
        matching = re.match(rule, match_name)
        if not matching:
            mx.abort(f"{self.__class__.__name__} configuration is invalid: {config_name}")

        if matching.group("native_architecture") is not None:
            mx.logv(f"'native-architecture' is enabled for {config_name}")
            self.native_architecture = True

        if matching.group("string_inlining") is not None:
            mx.logv(f"'string-inlining' is enabled for {config_name}")
            self.use_string_inlining = True

        if matching.group("gate") is not None:
            mx.logv(f"'gate' mode is enabled for {config_name}")
            self.is_gate = True

        if matching.group("upx") is not None:
            mx.logv(f"'upx' is enabled for {config_name}")
            self.use_upx = True

        if matching.group("quickbuild") is not None:
            mx.logv(f"'quickbuild' is enabled for {config_name}")
            self.is_quickbuild = True

        if matching.group("gc") is not None:
            gc = matching.group("gc")[:-1]
            if gc == "g1gc":
                mx.logv(f"'g1gc' is enabled for {config_name}")
                self.gc = "G1"
            else:
                mx.abort(f"Unknown GC: {gc}")

        if matching.group("llvm") is not None:
            mx.logv(f"'llvm' mode is enabled for {config_name}")
            self.is_llvm = True

        if matching.group("pgo") is not None:
            pgo_mode = matching.group("pgo")[:-1]
            if pgo_mode == "pgo":
                mx.logv(f"'pgo' is enabled for {config_name}")
                self.pgo_instrumented_iterations = 1
            elif pgo_mode == "pgo-ctx-insens":
                mx.logv(f"'pgo-ctx-insens' is enabled for {config_name}")
                self.pgo_instrumented_iterations = 1
                self.pgo_context_sensitive = False
            else:
                mx.abort(f"Unknown pgo mode: {pgo_mode}")

        if matching.group("inliner") is not None:
            inliner = matching.group("inliner")[:-1]
            if self.pgo_instrumented_iterations < 1:
                mx.abort(f"The selected inliner require PGO! Invalid configuration: {config_name}")
            if inliner == "aot-inline":
                mx.logv(f"'aot-inline' is enabled for {config_name}")
                self.pgo_aot_inline = True
            elif inliner == "iterative":
                mx.logv(f"'iterative' inliner is enabled for {config_name}")
                self.pgo_instrumented_iterations = 3
            elif inliner == "inline-explored":
                mx.logv(f"'inline-explored' is enabled for {config_name}")
                self.pgo_instrumented_iterations = 3
            else:
                mx.abort(f"Unknown inliner configuration: {inliner}")

        if matching.group("ml") is not None:
            self.ml = matching.group("ml")[:-1]

        if matching.group("jdk_profiles") is not None:
            config = matching.group("jdk_profiles")[:-1]
            if config == 'jdk-profiles-collect':
                self.jdk_profiles_collect = True
                self.pgo_instrumented_iterations = 1

                def generate_profiling_package_prefixes():
                    # run the native-image-configure tool to gather the jdk package prefixes
                    graalvm_home_bin = os.path.join(mx_sdk_vm.graalvm_home(), 'bin')
                    native_image_configure_command = mx.cmd_suffix(join(graalvm_home_bin, 'native-image-configure'))
                    if not exists(native_image_configure_command):
                        mx.abort('Failed to find the native-image-configure command at {}. \nContent {}: \n\t{}'.format(native_image_configure_command, graalvm_home_bin, '\n\t'.join(os.listdir(graalvm_home_bin))))
                    tmp = tempfile.NamedTemporaryFile()
                    ret = mx.run([native_image_configure_command, 'generate-filters',
                                  '--include-packages-from-modules=java.base',
                                  '--exclude-classes=org.graalvm.**', '--exclude-classes=com.oracle.**',  # remove internal packages
                                  f'--output-file={tmp.name}'], nonZeroIsFatal=True)
                    if ret != 0:
                        mx.abort('Native image configure command failed.')

                    # format the profiling package prefixes
                    with open(tmp.name, 'r') as f:
                        prefixes = json.loads(f.read())
                        if 'rules' not in prefixes:
                            mx.abort('Native image configure command failed. Can not generate rules.')
                        rules = prefixes['rules']
                        rules = map(lambda r: r['includeClasses'][:-2], filter(lambda r: 'includeClasses' in r, rules))
                        return ','.join(rules)
                self.generate_profiling_package_prefixes = generate_profiling_package_prefixes
            elif config == 'cached-jdk-pgo':
                self.cached_jdk_pgo = True
            else:
                mx.abort(f'Unknown jdk profiles configuration: {config}')

        if matching.group("edition") is not None:
            edition = matching.group("edition")[:-1]
            mx.logv(f"GraalVM edition is set to: {edition}")
            self.graalvm_edition = edition

        if matching.group("no_inlining_before_analysis") is not None:
            option = matching.group("no_inlining_before_analysis")[:-1]
            if option == "no-inline":
                mx.logv(f"not doing inlining before analysis for {config_name}")
                self.no_inlining_before_analysis = True
            else:
                mx.abort(f"Unknown configuration for no inlining before analysis: {option}")

        if matching.group("analysis_context_sensitivity") is not None:
            context_sensitivity = matching.group("analysis_context_sensitivity")[:-1]
            if context_sensitivity in ["insens", "allocsens"]:
                mx.logv(f"analysis context sensitivity {context_sensitivity} is enabled for {config_name}")
                self.analysis_context_sensitivity = context_sensitivity
            elif context_sensitivity in ["1obj", "2obj1h", "3obj2h", "4obj3h"]:
                mx.logv(f"analysis context sensitivity {context_sensitivity} is enabled for {config_name}")
                self.analysis_context_sensitivity = f"_{context_sensitivity}"
            else:
                mx.abort(f"Unknown analysis context sensitivity: {context_sensitivity}")


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
            self.successfully_finished_stages = []
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
                self.successfully_finished_stages.append(self.current_stage)
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

    def image_build_rules(self, output, benchmarks, bmSuiteArgs):
        return self.image_build_general_rules(output, benchmarks, bmSuiteArgs) + self.image_build_analysis_rules(output, benchmarks, bmSuiteArgs) \
               + self.image_build_statistics_rules(output, benchmarks, bmSuiteArgs) + self.image_build_timers_rules(output, benchmarks, bmSuiteArgs)

    def image_build_general_rules(self, output, benchmarks, bmSuiteArgs):
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
            })
        ]

    def image_build_analysis_rules(self, output, benchmarks, bmSuiteArgs):
        return [
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
        ]

    def image_build_statistics_rules(self, output, benchmarks, bmSuiteArgs):
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
            rules.append(mx_benchmark.JsonFixedFileRule(self.config.image_build_stats_file, {
                "benchmark": benchmarks[0],
                "metric.name": "image-build-stats",
                "metric.type": "numeric",
                "metric.unit": "#",
                "metric.value": ("<" + metric_objects[i] + ">", int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "metric.object": metric_objects[i].replace("_", "-").replace("total-", ""),
            }, [metric_objects[i]]))
        return rules

    def image_build_timers_rules(self, output, benchmarks, bmSuiteArgs):
        class NativeImageTimeToInt(object):
            def __call__(self, *args, **kwargs):
                return int(float(args[0].replace(',', '')))

        measured_phases = ['total', 'setup', 'classlist', 'analysis', 'universe', 'compile', 'dbginfo', 'image',
                           'write']
        rules = []
        for i in range(0, len(measured_phases)):
            phase = measured_phases[i]
            value_name = phase + "_time"
            rules.append(
                mx_benchmark.JsonFixedFileRule(self.config.image_build_stats_file, {
                    "benchmark": benchmarks[0],
                    "metric.name": "compile-time",
                    "metric.type": "numeric",
                    "metric.unit": "ms",
                    "metric.value": ("<" + value_name + ">", NativeImageTimeToInt()),
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": 0,
                    "metric.object": phase,
                }, [value_name]))
            value_name = phase + "_memory"
            rules.append(
                mx_benchmark.JsonFixedFileRule(self.config.image_build_stats_file, {
                    "benchmark": benchmarks[0],
                    "metric.name": "analysis-stats",
                    "metric.type": "numeric",
                    "metric.unit": "B",
                    "metric.value": ("<" + value_name + ">", NativeImageTimeToInt()),
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": 0,
                    "metric.object": phase + "_memory",
                }, [value_name]))
        return rules

    def rules(self, output, benchmarks, bmSuiteArgs):
        rules = super(NativeImageVM, self).rules(output, benchmarks, bmSuiteArgs)

        image_build_finished = 'image' in self.stages.successfully_finished_stages or 'instrument-image' in self.stages.successfully_finished_stages
        if image_build_finished:
            rules += self.image_build_rules(output, benchmarks, bmSuiteArgs)

        return rules

    def run_stage_agent(self, config, stages):
        hotspot_vm_args = ['-ea', '-esa'] if self.is_gate and not config.skip_agent_assertions else []
        agentlib_options = ['native-image-agent=config-output-dir=' + str(config.config_dir)] + config.extra_agentlib_options
        hotspot_vm_args += ['-agentlib:' + ','.join(agentlib_options)]

        # Jargraal is very slow with the agent, and libgraal is usually not built for Native Image benchmarks. Therefore, don't use the GraalVM compiler.
        hotspot_vm_args += ['-XX:-UseJVMCICompiler']

        # Limit parallelism because the JVMTI operations in the agent sometimes scale badly.
        if mx.cpu_count() > 8:
            hotspot_vm_args += ['-XX:ActiveProcessorCount=8']

        if config.image_vm_args is not None:
            hotspot_vm_args += config.image_vm_args

        hotspot_args = hotspot_vm_args + config.classpath_arguments + config.system_properties + config.executable + config.extra_agent_run_args
        with stages.set_command(self.generate_java_command(hotspot_args)) as s:
            s.execute_command()

    def run_stage_instrument_image(self, config, stages, out, i, instrumentation_image_name, image_path, image_path_latest, instrumented_iterations):
        executable_name_args = ['-H:Name=' + instrumentation_image_name]
        pgo_args = ['--pgo=' + config.latest_profile_path]
        pgo_args += ['-H:' + ('+' if self.pgo_context_sensitive else '-') + 'PGOContextSensitivityEnabled']
        pgo_args += ['-H:+AOTInliner'] if self.pgo_aot_inline else ['-H:-AOTInliner']
        # GR-40154/GR-42738 --pgo-sampling does not work with G1/LLVM
        if self.gc == 'G1' or self.is_llvm:
            instrument_args = ['--pgo-instrument'] + ([] if i == 0 else pgo_args)
        else:
            instrument_args = ['--pgo-instrument', '--pgo-sampling'] + ([] if i == 0 else pgo_args)
        if self.jdk_profiles_collect:
            instrument_args += ['-H:+ProfilingEnabled', '-H:+AOTPriorityInline', f'-H:ProfilingPackagePrefixes={self.generate_profiling_package_prefixes()}']

        with stages.set_command(config.base_image_build_args + executable_name_args + instrument_args) as s:
            s.execute_command()
            if s.exit_code == 0:
                mx.copyfile(image_path, image_path_latest)
            if i + 1 == instrumented_iterations and s.exit_code == 0:
                image_size = os.stat(image_path).st_size
                out('Instrumented image size: ' + str(image_size) + ' B')

    def _ensureSamplesAreInProfile(self, profile_path):
        # GR-40154/GR-42738 --pgo-sampling does not work with G1/LLVM
        if self.pgo_aot_inline and self.gc != 'G1' and not self.is_llvm:
            with open(profile_path) as profile_file:
                parsed = json.load(profile_file)
                samples = parsed["samplingProfiles"]
                assert len(samples) != 0, "No sampling profiles in iprof file " + profile_path
                for sample in samples:
                    assert ":" in sample["ctx"], "Sampling profiles seem malformed in file " + profile_path
                    assert len(sample["records"]) == 1, "Sampling profiles seem to be missing records in file " + profile_path
                    assert sample["records"][0] > 0, "Sampling profiles seem to have a 0 in records in file " + profile_path

    def run_stage_instrument_run(self, config, stages, image_path, profile_path):
        image_run_cmd = [image_path, '-XX:ProfilesDumpFile=' + profile_path]
        image_run_cmd += config.extra_profile_run_args
        with stages.set_command(image_run_cmd) as s:
            s.execute_command()
            if s.exit_code == 0:
                mx.copyfile(profile_path, config.latest_profile_path)
            self._ensureSamplesAreInProfile(profile_path)

    def run_stage_image(self, config, stages):
        executable_name_args = ['-H:Name=' + config.final_image_name]
        pgo_args = ['--pgo=' + config.latest_profile_path]
        pgo_args += ['-H:' + ('+' if self.pgo_context_sensitive else '-') + 'PGOContextSensitivityEnabled']
        pgo_args += ['-H:+AOTInliner'] if self.pgo_aot_inline else ['-H:-AOTInliner']
        instrumented_iterations = self.pgo_instrumented_iterations if config.pgo_iteration_num is None else int(config.pgo_iteration_num)
        ml_args = ['-H:+ProfileInference'] if self.ml == 'ml-profile-inference' else []
        if self.cached_jdk_pgo:
            # choose appropriate profiles
            jdk_profiles = f"JDK{mx.get_jdk().javaCompliance}_PROFILES"
            cached_profiles_base_dir = mx.library(jdk_profiles).get_path(True)
            cached_profiles = ','.join(list(map(lambda f: os.path.join(cached_profiles_base_dir, f), os.listdir(cached_profiles_base_dir))))
            jdk_profiles_args = [f'-H:CachedPGOEnabled={cached_profiles}']
        else:
            jdk_profiles_args = []
        final_image_command = config.base_image_build_args + executable_name_args + (pgo_args if instrumented_iterations > 0 else []) + ml_args + jdk_profiles_args
        with stages.set_command(final_image_command) as s:
            s.execute_command()
            if self.use_upx:
                image_path = os.path.join(config.output_dir, config.final_image_name)
                upx_directory = mx.library("UPX", True).get_path(True)
                upx_path = os.path.join(upx_directory, mx.exe_suffix("upx"))
                upx_cmd = [upx_path, image_path]
                mx.log(f"Compressing image: {' '.join(upx_cmd)}")
                mx.run(upx_cmd, s.stdout(True), s.stderr(True))

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
        self.config = config
        stages = NativeImageVM.Stages(config, out, err, self.is_gate, True if self.is_gate else nonZeroIsFatal, os.path.abspath(cwd if cwd else os.getcwd()))
        self.stages = stages
        instrumented_iterations = self.pgo_instrumented_iterations if config.pgo_iteration_num is None else int(config.pgo_iteration_num)

        if not os.path.exists(config.output_dir):
            os.makedirs(config.output_dir)

        if not os.path.exists(config.config_dir):
            os.makedirs(config.config_dir)

        if stages.change_stage('agent'):
            if instrumented_iterations == 0 and config.last_stage.startswith('instrument-'):
                config.last_stage = 'agent'
            self.run_stage_agent(config, stages)

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

        if stages.failed:
            mx.abort('Exiting the benchmark due to the failure.')

    def create_log_files(self, config, executable_name, stage):
        stdout_path = os.path.abspath(
            os.path.join(config.log_dir, executable_name + '-' + stage.current_stage + '-stdout.log'))
        stderr_path = os.path.abspath(
            os.path.join(config.log_dir, executable_name + '-' + stage.current_stage + '-stderr.log'))
        return stderr_path, stdout_path


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
            raise mx.abort(f"Benchmark suite '{self.name()}' cannot run multiple benchmarks in the same VM process")
        if len(benchmarks) != 1:
            raise mx.abort(f"Benchmark suite '{self.name()}' can run only one benchmark at a time")
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
                msg = f"The distribution {dist_name} does not exist: {_root}{os.linesep}"
                msg += f"This might be solved by running: mx build --dependencies={dist_name}"
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
            graal_test = mx.distribution('GRAAL_TEST', fatalIfMissing=False)
            polybench_ee = mx.distribution('POLYBENCH_EE', fatalIfMissing=False)
            if graal_test and polybench_ee and mx.get_env('ENABLE_POLYBENCH_HPC') == 'yes':
                # If the GRAAL_TEST and POLYBENCH_EE (for instructions metric) distributions
                # are present, the CompileTheWorld benchmark is available.
                self._benchmarks = ['CompileTheWorld']
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
        benchmark = benchmarks[0]
        if benchmark == 'CompileTheWorld':
            # Run CompileTheWorld as a polybench benchmark, using instruction counting to get a stable metric.
            # The CompileTheWorld class has been reorganized to have separate "prepare" and
            # "compile" steps such that only the latter is measured by polybench.
            # PAPI instruction counters are thread-local so CTW is run on the same thread as
            # the polybench harness (i.e., CompileTheWorld.MultiThreaded=false).
            import mx_compiler
            res = mx_compiler._ctw_jvmci_export_args(arg_prefix='--vm.-') + [
                   '--ctw',
                   '--vm.cp=' + mx.distribution('GRAAL_ONLY_TEST').path + os.pathsep + mx.distribution('GRAAL_TEST').path,
                   '--vm.DCompileTheWorld.MaxCompiles=10000',
                   '--vm.DCompileTheWorld.Classpath=' + mx.library('DACAPO_MR1_BACH').get_path(resolve=True),
                   '--vm.DCompileTheWorld.Verbose=false',
                   '--vm.DCompileTheWorld.MultiThreaded=false',
                   '--vm.Dlibgraal.ShowConfiguration=info',
                   '--metric=instructions',
                   '-w', '1',
                   '-i', '5'] + vmArgs
        else:
            benchmark_path = os.path.join(self._get_benchmark_root(), benchmark)
            res = ["--path=" + benchmark_path] + vmArgs
        return res

    def get_vm_registry(self):
        return _polybench_vm_registry

    def rules(self, output, benchmarks, bmSuiteArgs):
        metric_name = self._get_metric_name(output)
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
        elif metric_name in ("allocated-memory", "metaspace-memory", "application-memory"):
            rules += [
                ExcludeWarmupRule(r"\[(?P<name>.*)\] iteration (?P<iteration>[0-9]*): (?P<value>.*) (?P<unit>.*)", {
                    "benchmark": ("<name>", str),
                    "metric.better": "lower",
                    "metric.name": metric_name,
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
            mx_benchmark.StdOutRule(r"### load time \((?P<unit>.*)\): (?P<delta>[0-9]+)", {
                "benchmark": benchmarks[0],
                "metric.name": "context-eval-time",
                "metric.value": ("<delta>", float),
                "metric.unit": ("<unit>", str),
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0
            }),
            mx_benchmark.StdOutRule(r"### init time \((?P<unit>.*)\): (?P<delta>[0-9]+)", {
                "benchmark": benchmarks[0],
                "metric.name": "context-init-time",
                "metric.value": ("<delta>", float),
                "metric.unit": ("<unit>", str),
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0
            })
        ]
        return rules

    def _get_metric_name(self, bench_output):
        match = re.search(r"metric class:\s*(?P<metric_class_name>\w+)Metric", bench_output)
        if match is None:
            match = re.search(r"metric class:\s*(?P<metric_class_name>\w+)", bench_output)

        metric_class_name = match.group("metric_class_name")
        metric_class_name = re.sub(r'(?<!^)(?=[A-Z])', '-', metric_class_name).lower()

        if metric_class_name in POLYBENCH_METRIC_MAPPING:
            return POLYBENCH_METRIC_MAPPING[metric_class_name]
        else:
            return metric_class_name


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
        name = 'graalvm-ee' if mx_sdk_vm_impl.has_component('svmee', stage1=True) else 'graalvm-ce'
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
    def __init__(self, name, config_name, extra_java_args, extra_launcher_args):
        super(PolyBenchVm, self).__init__(name, config_name, extra_java_args, extra_launcher_args)
        if self.debug_args:
            # The `arg[1:]` is to strip the first '-' from the args since it's
            # re-added by the subsequent processing of `--vm`
            self.debug_args = [f'--vm.{arg[1:]}' for arg in self.debug_args]

    def run(self, cwd, args):
        return self.run_launcher('polybench', args, cwd)

def polybenchmark_rules(benchmark, metric_name, mode):
    rules = []
    if metric_name == "time":
        # Special case for metric "time": Instead of reporting the aggregate numbers,
        # report individual iterations. Two metrics will be reported:
        # - "warmup" includes all iterations (warmup and run)
        # - "time" includes only the "run" iterations
        rules += [
            mx_benchmark.StdOutRule(r"\[(?P<name>.*)\] iteration ([0-9]*): (?P<value>.*) (?P<unit>.*)", {
                "benchmark": benchmark, #("<name>", str),
                "metric.better": "lower",
                "metric.name": "warmup",
                "metric.unit": ("<unit>", str),
                "metric.value": ("<value>", float),
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.iteration": ("$iteration", int),
                "engine.config": mode,
            }),
            ExcludeWarmupRule(r"\[(?P<name>.*)\] iteration (?P<iteration>[0-9]*): (?P<value>.*) (?P<unit>.*)", {
                "benchmark": benchmark, #("<name>", str),
                "metric.better": "lower",
                "metric.name": "time",
                "metric.unit": ("<unit>", str),
                "metric.value": ("<value>", float),
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.iteration": ("<iteration>", int),
                "engine.config": mode,
            }, startPattern=r"::: Running :::"),
            mx_benchmark.StdOutRule(r"### load time \((?P<unit>.*)\): (?P<delta>[0-9]+)", {
                "benchmark": benchmark,
                "metric.name": "context-eval-time",
                "metric.value": ("<delta>", float),
                "metric.unit": ("<unit>", str),
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "engine.config": mode
            }),
            mx_benchmark.StdOutRule(r"### init time \((?P<unit>.*)\): (?P<delta>[0-9]+)", {
                "benchmark": benchmark,
                "metric.name": "context-init-time",
                "metric.value": ("<delta>", float),
                "metric.unit": ("<unit>", str),
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "engine.config": mode,
            }),
        ]
    elif metric_name in ("allocated-memory", "metaspace-memory", "application-memory", "instructions"):
        rules += [
            ExcludeWarmupRule(r"\[(?P<name>.*)\] iteration (?P<iteration>[0-9]*): (?P<value>.*) (?P<unit>.*)", {
                "benchmark": benchmark, #("<name>", str),
                "metric.better": "lower",
                "metric.name": metric_name,
                "metric.unit": ("<unit>", str),
                "metric.value": ("<value>", float),
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.iteration": ("<iteration>", int),
                "engine.config": mode,
            }, startPattern=r"::: Running :::")
        ]
    elif metric_name in ("compile-time", "pe-time"):
        rules += [
            mx_benchmark.StdOutRule(r"\[(?P<name>.*)\] after run: (?P<value>.*) (?P<unit>.*)", {
                "benchmark": benchmark, #("<name>", str),
                "metric.better": "lower",
                "metric.name": metric_name,
                "metric.unit": ("<unit>", str),
                "metric.value": ("<value>", float),
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.iteration": 0,
                "engine.config": mode,
            }),
        ]
    return rules

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
        if _suite.get_import("polybenchmarks") is not None:
            import mx_polybenchmarks_benchmark
            mx_polybenchmarks_benchmark.polybenchmark_vm_registry.add_vm(PolyBenchVm(host_vm_name, "jvm", [], ["--jvm"]))
            mx_polybenchmarks_benchmark.polybenchmark_vm_registry.add_vm(PolyBenchVm(host_vm_name, "native", [], ["--native"]))
            mx_polybenchmarks_benchmark.rules = polybenchmark_rules

    # Inlining before analysis is done by default
    analysis_context_sensitivity = ['insens', 'allocsens', '1obj', '2obj1h', '3obj2h', '4obj3h']
    analysis_context_sensitivity_no_inline = [f"{analysis_component}-no-inline" for analysis_component in analysis_context_sensitivity]
    pgo_aot_inline_context_sensitivity = [f"pgo-aot-inline-{analysis_component}" for analysis_component in analysis_context_sensitivity]
    pgo_aot_inline_context_sensitivity += [f"pgo-aot-inline-{analysis_component}" for analysis_component in analysis_context_sensitivity_no_inline]

    for short_name, config_suffix in [('niee', 'ee'), ('ni', 'ce')]:
        if any(component.short_name == short_name for component in mx_sdk_vm_impl.registered_graalvm_components(stage1=False)):
            for main_config in ['default', 'gate', 'llvm', 'native-architecture'] + analysis_context_sensitivity + analysis_context_sensitivity_no_inline + pgo_aot_inline_context_sensitivity:
                final_config_name = f'{main_config}-{config_suffix}'
                mx_benchmark.add_java_vm(NativeImageVM('native-image', final_config_name), _suite, 10)
            break

    # Adding JAVA_HOME VMs to be able to run benchmarks on GraalVM binaries without the need of building it first
    for java_home_config in ['default', 'pgo', 'g1gc', 'g1gc-pgo', 'upx', 'upx-g1gc', 'quickbuild', 'quickbuild-g1gc']:
        mx_benchmark.add_java_vm(NativeImageVM('native-image-java-home', java_home_config), _suite, 5)


    # Add VMs for libgraal
    if mx.suite('substratevm', fatalIfMissing=False) is not None:
        import mx_substratevm
        # Use `name` rather than `short_name` since the code that follows
        # should not be executed when "LibGraal Enterprise" is registered
        if mx_sdk_vm_impl.has_component(mx_substratevm.libgraal.name):
            libgraal_location = mx_sdk_vm_impl.get_native_image_locations(mx_substratevm.libgraal.name, 'jvmcicompiler')
            if libgraal_location is not None:
                import mx_graal_benchmark
                mx_graal_benchmark.build_jvmci_vm_variants('server', 'graal-core-libgraal',
                                                           ['-server', '-XX:+EnableJVMCI', '-Dgraal.CompilerConfiguration=community', '-Djvmci.Compiler=graal', '-XX:+UseJVMCINativeLibrary', '-XX:JVMCILibPath=' + dirname(libgraal_location)],
                                                           mx_graal_benchmark._graal_variants, suite=_suite, priority=15, hosted=False)
