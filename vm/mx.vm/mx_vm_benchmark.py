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
import pipes

import mx, mx_benchmark
import mx_sdk, mx_vm
import os
from os.path import dirname


_suite = mx.suite('vm')
_native_image_vm_registry = mx_benchmark.VmRegistry('NativeImage', 'ni-vm')
_gu_vm_registry = mx_benchmark.VmRegistry('GraalUpdater', 'gu-vm')

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

    def dimensions(self, cwd, args, code, out):
        return {}

    def run_java(self, args, out=None, err=None, cwd=None, nonZeroIsFatal=False):
        """Run 'java' workloads."""
        return mx.run([os.path.join(mx_vm.graalvm_home(fatalIfMissing=True), 'bin', 'java')] + args, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)

    def run_lang(self, cmd, args, cwd):
        """Deprecated. Call 'run_launcher' instead."""
        return self.run_launcher(cmd, args, cwd)

    def run_launcher(self, cmd, args, cwd):
        """Run the 'cmd' command in the 'bin' directory."""
        out = mx.TeeOutputCapture(mx.OutputCapture())
        args = self.post_process_launcher_command_line_args(args)
        mx.log("Running '{}' on '{}' with args: '{}'".format(cmd, self.name(), args))
        code = mx.run([os.path.join(mx_vm.graalvm_home(fatalIfMissing=True), 'bin', cmd)] + args, out=out, err=out, cwd=cwd, nonZeroIsFatal=False)
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
        def __init__(self):
            self.extra_image_build_arguments = []
            self.extra_run_args = []
            self.extra_agent_run_args = []
            self.extra_profile_run_args = []
            self.needs_config = True
            self.config_dir = None
            self.profile_dir = None

        def parse(self, args):
            def add_to_list(arg, name, arg_list):
                if arg.startswith(name + '='):
                    arg_list += [arg[len(name) + 1:]]
                    return True
                else:
                    return False

            benchmark_run_args = []
            benchmark_config_prefix = '-Dnative-image.benchmark.'
            for arg in args:
                if arg.startswith(benchmark_config_prefix):
                    trimmed_arg = arg[len(benchmark_config_prefix):]
                    found = add_to_list(trimmed_arg, 'extra-image-build-argument', self.extra_image_build_arguments)
                    found |= add_to_list(trimmed_arg, 'extra-run-arg', self.extra_run_args)
                    found |= add_to_list(trimmed_arg, 'extra-agent-run-arg', self.extra_agent_run_args)
                    found |= add_to_list(trimmed_arg, 'extra-profile-run-arg', self.extra_profile_run_args)
                    if trimmed_arg.startswith('needs-config='):
                        self.needs_config = arg[len('needs-config='):] == 'true'
                        found = True
                    if trimmed_arg.startswith('config-dir='):
                        self.config_dir = arg[len('config-dir'):]
                        found = True
                    if not found:
                        mx.abort("Invalid benchmark argument: " + arg)
                else:
                    benchmark_run_args += [arg]

            return benchmark_run_args

    def __init__(self, name, config_name, extra_java_args, extra_launcher_args, pgo_instrumented_iterations, hotspot_pgo, is_gate):
        super(NativeImageVM, self).__init__(name, config_name, extra_java_args, extra_launcher_args)
        self.pgo_instrumented_iterations = pgo_instrumented_iterations
        self.hotspot_pgo = hotspot_pgo
        self.is_gate = is_gate

    @staticmethod
    def supported_vm_args():
        """
            This list is intentionally restrictive. We want to be sure that what we add is correct on the case-by-case
            basis. In the future we can convert this from a failure into a warning.
            :return: a list of args supported by native image.
        """
        return ['-D', '-Xmx', '-Xmn']

    _VM_OPTS_SPACE_SEPARATED_ARG = ['-mp', '-modulepath', '-limitmods', '-addmods', '-upgrademodulepath', '-m',
                                    '--module-path', '--limit-modules', '--add-modules', '--upgrade-module-path',
                                    '--module', '--module-source-path', '--add-exports', '--add-reads',
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
        vm_args, executable, image_run_args = NativeImageVM._split_vm_arguments(args)

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
                if not (vm_arg.startswith('-D') or vm_arg in NativeImageVM.supported_vm_args()):
                    mx.abort('Unsupported argument ' + vm_arg + '.' +
                             ' Currently supported argument prefixes are: ' + str(NativeImageVM.supported_vm_args()))
                image_vm_args.append(vm_arg)
                i += 1

        return executable, classpath_arguments, system_properties, image_vm_args + image_run_args

    def run_java(self, args, out=None, err=None, cwd=None, nonZeroIsFatal=False):
        if '-version' in args:
            return super(NativeImageVM, self).run_java(args, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)
        else:
            image_cwd = os.path.abspath(cwd if cwd else os.getcwd())
            non_zero_is_fatal = self.is_gate or nonZeroIsFatal
            config = NativeImageVM.BenchmarkConfig()
            original_java_run_args = config.parse(args)
            executable, classpath_arguments, system_properties, image_run_args = NativeImageVM.extract_benchmark_arguments(original_java_run_args)
            executable_name = (os.path.splitext(os.path.basename(executable[1]))[0] if executable[0] == '-jar' else executable[0]).lower()
            image_path = os.path.join(image_cwd, executable_name)
            config.profile_dir = mx.mkdtemp(suffix='profile', prefix='native-image')
            profile_path = os.path.join(config.profile_dir, executable_name + '.iprof')

            # Agent configuration and/or HotSpot profiling
            needs_config = (config.config_dir is None) and config.needs_config
            if needs_config or self.hotspot_pgo:
                hotspot_vm_args = ['-ea', '-esa'] if self.is_gate else []
                hotspot_run_args = []

                if needs_config:
                    config.config_dir = mx.mkdtemp(suffix='config', prefix='native-image')
                    hotspot_vm_args += ['-agentlib:native-image-agent=config-output-dir=' + str(config.config_dir)]

                if self.hotspot_pgo:
                    hotspot_vm_args += ['-Dgraal.PGOInstrument=' + profile_path]

                if config.extra_agent_run_args:
                    hotspot_run_args += config.extra_profile_run_args if self.hotspot_pgo else config.extra_agent_run_args
                else:
                    hotspot_run_args += image_run_args

                hotspot_args = hotspot_vm_args + classpath_arguments + executable + system_properties + hotspot_run_args

                mx.log('Running with HotSpot to get the configuration files and profiles. This could take a while:')
                super(NativeImageVM, self).run_java(hotspot_args,
                                                    out=None, err=None, cwd=image_cwd, nonZeroIsFatal=non_zero_is_fatal)

            base_image_build_args = [os.path.join(mx_vm.graalvm_home(fatalIfMissing=True), 'bin', 'native-image')]
            base_image_build_args += ['--no-fallback']
            base_image_build_args += ['-J-ea', '-J-esa', '-H:+VerifyGraalGraphs', '-H:+VerifyPhases', '-H:+TraceClassInitialization'] if self.is_gate else []
            base_image_build_args += system_properties
            base_image_build_args += classpath_arguments
            base_image_build_args += executable
            base_image_build_args += ['-H:Name=' + executable_name, '-H:Path=' + image_cwd]
            if needs_config:
                base_image_build_args += ['-H:ConfigurationFileDirectories=' + config.config_dir]
            base_image_build_args += config.extra_image_build_arguments

            # PGO instrumentation
            i = 0
            while i < self.pgo_instrumented_iterations:
                instrument_args = ['--pgo-instrument'] + ([] if i == 0 and not self.hotspot_pgo else ['--pgo'])
                instrument_image_build_args = base_image_build_args + instrument_args
                mx.log('Building the instrumentation image with: ')
                mx.log(' ' + ' '.join([pipes.quote(str(arg)) for arg in instrument_image_build_args]))
                mx.run(instrument_image_build_args, out=None, err=None, cwd=image_cwd, nonZeroIsFatal=non_zero_is_fatal)

                image_run_cmd = [image_path]
                image_run_cmd += ['-XX:ProfilesDumpFile=' + profile_path]
                if config.extra_profile_run_args:
                    image_run_cmd += config.extra_profile_run_args
                else:
                    image_run_cmd += image_run_args + config.extra_run_args

                mx.log('Running the instrumented image with: ')
                mx.log(' ' + ' '.join([pipes.quote(str(arg)) for arg in image_run_cmd]))
                mx.run(image_run_cmd, out=out, err=err, cwd=image_cwd, nonZeroIsFatal=non_zero_is_fatal)

                i += 1

            # Build the final image
            pgo_args = ['--pgo=' + profile_path] if self.pgo_instrumented_iterations > 0 or self.hotspot_pgo else []
            final_image_args = base_image_build_args + pgo_args
            mx.log('Building the final image with: ')
            mx.log(' ' + ' '.join([pipes.quote(str(arg)) for arg in final_image_args]))
            mx.run(final_image_args, out=None, err=None, cwd=image_cwd, nonZeroIsFatal=non_zero_is_fatal)

            # Execute the benchmark
            image_run_cmd = [image_path] + image_run_args + config.extra_run_args
            mx.log('Running the produced native executable with: ')
            mx.log(' ' + ' '.join([pipes.quote(str(arg)) for arg in image_run_cmd]))
            mx.run(image_run_cmd, out=out, err=err, cwd=image_cwd, nonZeroIsFatal=non_zero_is_fatal)


class NativeImageBuildVm(GraalVm):
    def run(self, cwd, args):
        default_args = ['--no-server'] if mx_vm.has_svm_launcher('svm') else []
        return self.run_launcher('native-image', default_args + args, cwd)


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


mx_benchmark.add_bm_suite(NativeImageBuildBenchmarkSuite(name='native-image', benchmarks={'js': ['--language:js']}, registry=_native_image_vm_registry))
mx_benchmark.add_bm_suite(NativeImageBuildBenchmarkSuite(name='gu', benchmarks={'js': ['js'], 'libpolyglot': ['libpolyglot']}, registry=_gu_vm_registry))


def register_graalvm_vms():
    graalvm_hostvm_name = mx_vm.graalvm_dist_name().lower().replace('_', '-')
    for config_name, java_args, launcher_args, priority in mx_sdk.graalvm_hostvm_configs:
        mx_benchmark.java_vm_registry.add_vm(GraalVm(graalvm_hostvm_name, config_name, java_args, launcher_args), _suite, priority)
    if mx_vm.has_component('svm', fatalIfMissing=False):
        _native_image_vm_registry.add_vm(NativeImageBuildVm(graalvm_hostvm_name, 'default', [], []), _suite, 10)
        _gu_vm_registry.add_vm(GuVm(graalvm_hostvm_name, 'default', [], []), _suite, 10)

    # We support only EE and CE configuration for native-image benchmarks
    for short_name, config_suffix in [('niee', 'ee'), ('ni', 'ce')]:
        if any(component.short_name == short_name for component in mx_vm.registered_graalvm_components(stage1=False)):
            mx_benchmark.add_java_vm(NativeImageVM('native-image', 'default-' + config_suffix, None, None, 0, False, False), _suite, 10)
            mx_benchmark.add_java_vm(NativeImageVM('native-image', 'pgo-' + config_suffix, None, None, 1, False, False), _suite, 10)
            mx_benchmark.add_java_vm(NativeImageVM('native-image', 'pgo-hotspot-' + config_suffix, None, None, 0, True, False), _suite, 10)
            mx_benchmark.add_java_vm(NativeImageVM('native-image', 'gate-' + config_suffix, None, None, 0, True, True), _suite, 10)
            break

# Add VMs for libgraal
    if mx_vm.has_component('LibGraal', fatalIfMissing=False):
        libgraal_location = mx_vm.get_native_image_locations('LibGraal', 'jvmcicompiler')
        if libgraal_location is not None:
            import mx_graal_benchmark
            mx_graal_benchmark.build_jvmci_vm_variants('server', 'graal-core-libgraal',
                                                       ['-server', '-XX:+EnableJVMCI', '-Dgraal.CompilerConfiguration=community', '-Djvmci.Compiler=graal', '-XX:+UseJVMCINativeLibrary', '-XX:JVMCILibPath=' + dirname(libgraal_location)],
                                                       mx_graal_benchmark._graal_variants, suite=_suite, priority=15, hosted=False)
