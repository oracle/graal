import mx, mx_benchmark
import mx_vm

import os

_suite = mx.suite('vm')

class GraalVm(mx_benchmark.OutputCapturingJavaVm):
    def __init__(self, name, config_name, extra_java_args, extra_lang_args):
        """
        :type name: str
        :type config_name: str
        :type extra_java_args: list[str]
        :type extra_lang_args: list[str]
        """
        self._name = name
        self._config_name = config_name
        self.extra_java_args = extra_java_args
        self.extra_lang_args = extra_lang_args

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
    _graalvm_hostvm_name = mx_vm.graalvm_dist_name().lower().replace('_', '-')
    mx_benchmark.java_vm_registry.add_vm(GraalVm(_graalvm_hostvm_name, 'native', [], ['--native']), _suite, 100)
    mx_benchmark.java_vm_registry.add_vm(GraalVm(_graalvm_hostvm_name, 'jvm', [], ['--jvm']), _suite, 50)
