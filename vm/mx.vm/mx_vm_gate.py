import mx
import mx_vm
from mx_gate import Task

import re
import subprocess
from os.path import join

_suite = mx.suite('vm')


class VmGateTasks:
    graal = 'graal'
    graal_js = 'graal-js'


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
