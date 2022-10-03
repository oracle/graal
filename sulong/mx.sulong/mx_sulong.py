#
# Copyright (c) 2016, 2022, Oracle and/or its affiliates.
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its contributors may be used to
# endorse or promote products derived from this software without specific prior written
# permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
#
import sys
import os
import pipes
import tempfile
from os.path import join
import shutil
from argparse import ArgumentParser

import mx
import mx_gate
import mx_sdk_vm_impl
import mx_subst
import mx_sdk_vm
import mx_benchmark
import mx_sulong_benchmarks
import mx_sulong_fuzz #pylint: disable=unused-import
import mx_sulong_gen #pylint: disable=unused-import
import mx_sulong_gate
import mx_sulong_llvm_config

# re-export custom mx project classes so they can be used from suite.py
from mx_cmake import CMakeNinjaProject #pylint: disable=unused-import
from mx_sulong_suite_constituents import SulongCMakeTestSuite #pylint: disable=unused-import
from mx_sulong_suite_constituents import ExternalTestSuite #pylint: disable=unused-import
from mx_sulong_suite_constituents import ExternalCMakeTestSuite #pylint: disable=unused-import
from mx_sulong_suite_constituents import BootstrapToolchainLauncherProject #pylint: disable=unused-import
from mx_sulong_suite_constituents import AbstractSulongNativeProject #pylint: disable=unused-import
from mx_sulong_suite_constituents import DocumentationProject #pylint: disable=unused-import
from mx_sulong_suite_constituents import HeaderProject #pylint: disable=unused-import
from mx_sulong_suite_constituents import CopiedNativeProject #pylint: disable=unused-import

if sys.version_info[0] < 3:
    def _decode(x):
        return x
else:
    def _decode(x):
        return x.decode()

_suite = mx.suite('sulong')
_mx = join(_suite.dir, "mx.sulong")
_root = join(_suite.dir, "projects")
_testDir = join(_suite.dir, "tests")


toolchainLLVMVersion = mx_sulong_llvm_config.VERSION

# TODO: [GR-41902] use mx.add_lib_suffix
def _lib_suffix(name):
    if mx.is_windows():
        return name + ".dll"
    else:
        return name + ".so"

def _lib_versioned(arg):
    name, version = arg.split('.')
    if mx.is_darwin():
        return "lib" + name + "." + version + ".dylib"
    elif mx.is_linux() or mx.is_openbsd() or mx.is_sunos():
        return "lib" + name + ".so." + version
    elif mx.is_windows():
        return name + ".dll"
    else:
        mx.abort('unsupported os')

mx_subst.results_substitutions.register_with_arg('libv', _lib_versioned)


def set_sulong_test_config_root(root):
    mx_sulong_gate.set_sulong_test_config_root(root)


def get_test_distribution_path_properties(suite):
    return mx_sulong_gate.get_test_distribution_path_properties(suite)


def testLLVMImage(image, imageArgs=None, testFilter=None, libPath=True, test=None, unittestArgs=None):
    mx_sulong_gate.testLLVMImage(image, imageArgs, testFilter, libPath, test, unittestArgs)

# routine for AOT downstream tests
def runLLVMUnittests(unittest_runner):
    mx_sulong_gate.runLLVMUnittests(unittest_runner)


def findBundledLLVMProgram(llvm_program):
    llvm_dist = 'LLVM_TOOLCHAIN'
    dep = mx.dependency(llvm_dist, fatalIfMissing=True)
    return os.path.join(dep.get_output(), 'bin', mx.exe_suffix(llvm_program))


def truffle_extract_VM_args(args, useDoubleDash=False):
    vmArgs, remainder = [], []
    if args is not None:
        for (i, arg) in enumerate(args):
            if any(arg.startswith(prefix) for prefix in ['-X', '-G:', '-D', '-verbose', '-ea', '-da', '-agentlib']) or arg in ['-esa']:
                vmArgs += [arg]
            elif useDoubleDash and arg == '--':
                remainder += args[i:]
                break
            else:
                remainder += [arg]
    return vmArgs, remainder


def extract_compiler_args(args, useDoubleDash=False):
    compilerArgs, remainder = [], []
    if args is not None:
        for (_, arg) in enumerate(args):
            if any(arg.startswith(prefix) for prefix in ['-']):
                compilerArgs += [arg]
            else:
                remainder += [arg]
    return compilerArgs, remainder


def getCommonOptions(withAssertion, lib_args=None):
    options = []

    if lib_args is not None:
        options.append('-Dpolyglot.llvm.libraries=' + ':'.join(lib_args))

    options += ['-Xss56m', '-Xms4g', '-Xmx4g']
    if withAssertion:
        options += ['-ea', '-esa']

    return options


def get_mx_exe():
    mxpy = join(mx._mx_home, 'mx.py')
    commands = [sys.executable, '-u', mxpy, '--java-home=' + mx.get_jdk().home]
    return ' '.join(commands)


mx_subst.path_substitutions.register_no_arg('mx_exe', get_mx_exe)


def get_jacoco_setting():
    return mx_gate._jacoco


mx_subst.path_substitutions.register_no_arg('jacoco', get_jacoco_setting)


def _subst_get_jvm_args(dep):
    java = mx.get_jdk().java
    main_class = mx.distribution(dep).mainClass
    jvm_args = [pipes.quote(arg) for arg in mx.get_runtime_jvm_args([dep])]
    cmd = [java] + jvm_args + [main_class]
    return " ".join(cmd)


mx_subst.path_substitutions.register_with_arg('get_jvm_cmd_line', _subst_get_jvm_args)

mx.add_argument('--jacoco-exec-file', help='the coverage result file of JaCoCo', default='jacoco.exec')


def mx_post_parse_cmd_line(opts):
    mx_gate.JACOCO_EXEC = opts.jacoco_exec_file


@mx.command(_suite.name, 'llvm-tool', 'Run a tool from the LLVM_TOOLCHAIN distribution')
def llvm_tool(args=None, out=None, **kwargs):
    if len(args) < 1:
        mx.abort("usage: mx llvm-tool <llvm-tool> [args...]")
    llvm_program = findBundledLLVMProgram(args[0])
    mx.run([llvm_program] + args[1:], out=out, **kwargs)


_LLVM_EXTRA_TOOL_DIST = 'LLVM_TOOLCHAIN_FULL'
@mx.command(_suite.name, 'llvm-extra-tool', 'Run a tool from the ' + _LLVM_EXTRA_TOOL_DIST + ' distribution')
def llvm_extra_tool(args=None, out=None, **kwargs):
    if len(args) < 1:
        mx.abort("usage: llvm-extra-tool <llvm-tool> [args...]")
    program = args[0]
    dep = mx.dependency(_LLVM_EXTRA_TOOL_DIST, fatalIfMissing=True)
    llvm_program = os.path.join(dep.get_output(), 'bin', program)
    try:
        mx.run([llvm_program] + args[1:], out=out, nonZeroIsFatal=False, **kwargs)
    except BaseException as e:
        msg = "{}\n".format(e)
        msg += "This might be solved by running: mx build --dependencies={}".format(_LLVM_EXTRA_TOOL_DIST)
        mx.abort(msg)


def getClasspathOptions(extra_dists=None):
    """gets the classpath of the Sulong distributions"""
    return mx.get_runtime_jvm_args(['SULONG_CORE', 'SULONG_NATIVE', 'SULONG_LAUNCHER', 'TRUFFLE_NFI'] + (extra_dists or []))


def _get_sulong_home():
    return mx_subst.path_substitutions.substitute('<path:SULONG_HOME>')

_the_get_sulong_home = _get_sulong_home

def get_sulong_home():
    return _the_get_sulong_home()

def update_sulong_home(new_home):
    global _the_get_sulong_home
    _the_get_sulong_home = new_home

mx_subst.path_substitutions.register_no_arg('sulong_home', get_sulong_home)


@mx.command(_suite.name, "lli-legacy")
def runLLVM(args=None, out=None, err=None, timeout=None, nonZeroIsFatal=True, get_classpath_options=getClasspathOptions):
    """run lli via the legacy mx java launcher (instead of via the current GraalVM)"""
    vmArgs, sulongArgs = truffle_extract_VM_args(args)
    dists = []
    if "tools" in (s.name for s in mx.suites()):
        dists.append('CHROMEINSPECTOR')
    return mx.run_java(getCommonOptions(False) + vmArgs + get_classpath_options(dists) + ["com.oracle.truffle.llvm.launcher.LLVMLauncher"] + sulongArgs, timeout=timeout, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err)

@mx.command(_suite.name, "lli-mul")
def runLLVMMul(args=None, out=None, err=None, timeout=None, nonZeroIsFatal=True, get_classpath_options=getClasspathOptions):
    """run multi-context java launcher"""
    vmArgs, sulongArgs = truffle_extract_VM_args(args)
    dists = []
    if "tools" in (s.name for s in mx.suites()):
        dists.append('CHROMEINSPECTOR')
    return mx.run_java(getCommonOptions(False) + vmArgs + get_classpath_options(dists) + ["com.oracle.truffle.llvm.launcher.LLVMMultiContextLauncher"] + sulongArgs, timeout=timeout, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err)

@mx.command(_suite.name, "lli")
def lli(args=None, out=None, err=None, timeout=None, nonZeroIsFatal=True):
    """run lli via the current GraalVM"""
    debug_args = mx.java_debug_args()
    if debug_args and not mx.is_debug_disabled():
        args = ['--vm.' + arg.lstrip('-') for arg in debug_args] + args
    # on Windows <GRAALVM_HOME>/bin/lli is always a .cmd file because it is a "fake symlink"
    mx.run([os.path.join(mx_sdk_vm_impl.graalvm_home(fatalIfMissing=True), 'bin', mx_subst.path_substitutions.substitute('<cmd:lli>'))] + args, timeout=timeout, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err)


@mx.command(_suite.name, "extract-bitcode")
def extract_bitcode(args=None, out=None):
    """Extract embedded LLVM bitcode from object files"""
    return mx.run_java(mx.get_runtime_jvm_args(["com.oracle.truffle.llvm.tools"]) + ["com.oracle.truffle.llvm.tools.ExtractBitcode"] + args, out=out)


@mx.command(_suite.name, "llvm-dis")
def llvm_dis(args=None, out=None):
    """Disassemble (embedded) LLVM bitcode to LLVM assembly"""
    parser = ArgumentParser(prog='mx llvm-dis', description='Disassemble (embedded) LLVM bitcode to LLVM assembly.')
    parser.add_argument('input', help='The input file.', metavar='<input>')
    parser.add_argument('output', help='The output file. If omitted, <input>.ll is used. If <input> ends with ".bc", the ".bc" part is replaced with ".ll".', metavar='<output>', default=None, nargs='?')
    parser.add_argument('llvm_dis_args', help='Additional arguments forwarded to the llvm-dis command', metavar='<arg>', nargs='*')
    parsed_args = parser.parse_args(args)

    def get_bc_filename(orig_path):
        filename, ext = os.path.splitext(orig_path)
        return orig_path if ext == ".bc" else filename + ".bc"

    def get_ll_filename(orig_path):
        filename, ext = os.path.splitext(orig_path)
        return filename + ".ll" if ext == ".bc" else orig_path + ".ll"

    tmp_dir = None
    try:
        # create temp dir
        tmp_dir = tempfile.mkdtemp()
        in_file = parsed_args.input
        tmp_path = os.path.join(tmp_dir, os.path.basename(get_bc_filename(in_file)))

        extract_bitcode([in_file, tmp_path])

        # disassemble into temporary file
        ll_tmp_path = get_ll_filename(tmp_path)
        llvm_tool(["llvm-dis", tmp_path, "-o", ll_tmp_path] + parsed_args.llvm_dis_args)

        # write output file and patch paths
        ll_path = parsed_args.output or get_ll_filename(in_file)

        def _open_for_writing(path):
            if path == "-":
                return sys.stdout
            return open(path, 'w')

        def _open_for_reading(path):
            if path == "-":
                return sys.stdin
            return open(path, 'r')

        with _open_for_reading(ll_tmp_path) as ll_tmp_f, _open_for_writing(ll_path) as ll_f:
            ll_f.writelines((l.replace(tmp_path, in_file) for l in ll_tmp_f))

    finally:
        if tmp_dir:
            shutil.rmtree(tmp_dir)


_env_flags = []
if 'CPPFLAGS' in os.environ:
    _env_flags = os.environ['CPPFLAGS'].split(' ')


# Legacy bm suite
mx_benchmark.add_bm_suite(mx_sulong_benchmarks.SulongBenchmarkSuite(False))
# Polybench bm suite
mx_benchmark.add_bm_suite(mx_sulong_benchmarks.SulongBenchmarkSuite(True))
# LLVM unit tests suite
mx_benchmark.add_bm_suite(mx_sulong_benchmarks.LLVMUnitTestsSuite())

_toolchains = {}


def _get_toolchain(toolchain_name):
    if toolchain_name not in _toolchains:
        mx.abort("Toolchain '{}' does not exists! Known toolchains {}".format(toolchain_name, ", ".join(_toolchains.keys())))
    return _toolchains[toolchain_name]


def _get_toolchain_tool(name_tool):
    name, tool = name_tool.split(",", 2)
    return _get_toolchain(name).get_toolchain_tool(tool)


mx_subst.path_substitutions.register_with_arg('toolchainGetToolPath', _get_toolchain_tool)
mx_subst.path_substitutions.register_with_arg('toolchainGetIdentifier',
                                              lambda name: _get_toolchain(name).get_toolchain_subdir())


def create_toolchain_root_provider(name, dist):
    def provider():
        bootstrap_graalvm = mx.get_env('SULONG_BOOTSTRAP_GRAALVM')
        if bootstrap_graalvm:
            ret = os.path.join(bootstrap_graalvm, 'jre', 'languages', 'llvm', name)
            if os.path.exists(ret): # jdk8 based graalvm
                return ret
            else: # jdk11+ based graalvm
                return os.path.join(bootstrap_graalvm, 'languages', 'llvm', name)
        return mx.distribution(dist).get_output()
    return provider


def _exe_sub(program):
    return mx_subst.path_substitutions.substitute("<exe:{}>".format(program))

def _cmd_sub(program):
    return mx_subst.path_substitutions.substitute("<cmd:{}>".format(program))

def _lib_sub(program):
    return mx_subst.path_substitutions.substitute("<lib:{}>".format(program))

class ToolchainConfig(object):
    # Please keep this list in sync with Toolchain.java (method documentation) and ToolchainImpl.java (lookup switch block).
    _llvm_tool_map = ["ar", "nm", "objcopy", "objdump", "ranlib", "readelf", "readobj", "strip"]
    _tool_map = {
        "CC": ["graalvm-{name}-clang", "graalvm-clang", "clang", "cc", "gcc"],
        "CXX": ["graalvm-{name}-clang++", "graalvm-clang++", "clang++", "c++", "g++"],
        "CL": ["graalvm-{name}-clang-cl", "graalvm-clang-cl", "clang-cl", "cl"],
        "LD": ["graalvm-{name}-ld", "ld", "ld.lld", "lld", "lld-link", "ld64"],
        "BINUTIL": ["graalvm-{name}-binutil"] + _llvm_tool_map + ["llvm-" + i for i in _llvm_tool_map]
    }

    def __init__(self, name, dist, bootstrap_dist, tools, suite):
        self.name = name
        self.dist = dist if isinstance(dist, list) else [dist]
        self.bootstrap_provider = create_toolchain_root_provider(name, bootstrap_dist)
        self.bootstrap_dist = bootstrap_dist
        self.tools = tools
        self.llvm_binutil_tools = [tool.upper() for tool in ToolchainConfig._llvm_tool_map]
        self.suite = suite
        self.mx_command = self.name + '-toolchain'
        self.tool_map = {tool: [_exe_sub(alias.format(name=name)) for alias in aliases] for tool, aliases in ToolchainConfig._tool_map.items()}
        self.path_map = {_exe_sub(path): tool for tool, aliases in self.tool_map.items() for path in aliases}
        # register mx command
        mx.update_commands(_suite, {
            self.mx_command: [self._toolchain_helper, 'launch {} toolchain commands'.format(self.name)],
        })
        # register bootstrap toolchain substitution
        mx_subst.path_substitutions.register_no_arg(name + 'ToolchainRoot', self.bootstrap_provider)
        if self.name in _toolchains:
            mx.abort("Toolchain '{}' registered twice".format(self.name))
        _toolchains[self.name] = self

    def _toolchain_helper(self, args=None, out=None):
        parser = ArgumentParser(prog='mx ' + self.mx_command, description='launch toolchain commands',
                                epilog='Additional arguments are forwarded to the LLVM image command.', add_help=False)
        parser.add_argument('command', help='toolchain command', metavar='<command>',
                            choices=self._supported_tool_names())
        parsed_args, tool_args = parser.parse_known_args(args)
        main = self._tool_to_main(self.path_map[parsed_args.command])
        if "JACOCO" in os.environ:
            mx_gate._jacoco = os.environ["JACOCO"]
        return mx.run_java(mx.get_runtime_jvm_args([mx.splitqualname(d)[1] for d in self.dist]) + ['-Dorg.graalvm.launcher.executablename=' + parsed_args.command] + [main] + tool_args, out=out)

    def _supported_tool_names(self):
        return [path for tool in self._supported_tools() for path in self._tool_to_aliases(tool)]

    def _supported_tools(self):
        return self.tools.keys()

    def _tool_to_bin(self, tool):
        """
        Return the binary name including any required suffixes (e.g. .cmd / .exe)
        """
        return self._tool_to_aliases(tool)[0]

    def _tool_to_aliases(self, tool):
        self._check_tool(tool)
        return self.tool_map[tool]

    def _tool_to_main(self, tool):
        self._check_tool(tool)
        return self.tools[tool]

    def _check_tool(self, tool):
        if tool not in self._supported_tools():
            mx.abort("The {} toolchain (defined by {}) does not support tool '{}'".format(self.name, self.dist[0], tool))

    def get_toolchain_tool(self, tool):
        if tool in self._supported_tools():
            ret = os.path.join(self.bootstrap_provider(), 'bin', self._tool_to_bin(tool))
            if mx.is_windows() and ret.endswith('.exe') and not os.path.exists(ret):
                # this might be a bootstrap toolchain without native-image, so we have to replace .exe with .cmd
                ret = ret[:-4] + '.cmd'
            return ret
        elif tool in self.llvm_binutil_tools:
            return os.path.join(self.bootstrap_provider(), 'bin', _cmd_sub(tool.lower()))
        else:
            mx.abort("The {} toolchain (defined by {}) does not support tool '{}'".format(self.name, self.dist[0], tool))

    def get_toolchain_subdir(self):
        return self.name

    def get_launcher_configs(self):
        return [
            mx_sdk_vm.LauncherConfig(
                destination=os.path.join(self.name, 'bin', self._tool_to_bin(tool)),
                jar_distributions=self._get_jar_dists(),
                main_class=self._tool_to_main(tool),
                build_args=[
                    '--initialize-at-build-time=com.oracle.truffle.llvm.toolchain.launchers',
                    '-H:-ParseRuntimeOptions',  # we do not want `-D` options parsed by SVM
                    '--gc=epsilon',
                ],
                is_main_launcher=False,
                default_symlinks=False,
                links=[os.path.join(self.name, 'bin', e) for e in self._tool_to_aliases(tool)[1:]],
            ) for tool in self._supported_tools()
        ]

    def _get_jar_dists(self):
        return [d if ":" in d else self.suite.name + ":" + d for d in self.dist]


_suite.toolchain = ToolchainConfig('native', 'SULONG_TOOLCHAIN_LAUNCHERS', 'sulong:SULONG_BOOTSTRAP_TOOLCHAIN',
                                   # unfortunately, we cannot define those in the suite.py because graalvm component
                                   # registration runs before the suite is properly initialized
                                   tools={
                                       "CC": "com.oracle.truffle.llvm.toolchain.launchers.Clang",
                                       "CXX": "com.oracle.truffle.llvm.toolchain.launchers.ClangXX",
                                       "CL": "com.oracle.truffle.llvm.toolchain.launchers.ClangCL",
                                       "LD": "com.oracle.truffle.llvm.toolchain.launchers.Linker",
                                       "BINUTIL": "com.oracle.truffle.llvm.toolchain.launchers.BinUtil",
                                   },
                                   suite=_suite)


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='LLVM Runtime Core',
    short_name='llrc',
    dir_name='llvm',
    license_files=['sulong:SULONG_GRAALVM_DOCS/LICENSE_SULONG.txt'],
    third_party_license_files=['sulong:SULONG_GRAALVM_DOCS/THIRD_PARTY_LICENSE_SULONG.txt'],
    dependencies=['Truffle', 'Truffle NFI'],
    truffle_jars=['sulong:SULONG_CORE', 'sulong:SULONG_API', 'sulong:SULONG_NFI'],
    support_distributions=[
        'sulong:SULONG_CORE_HOME',
        'sulong:SULONG_GRAALVM_DOCS',
    ],
    installable=True,
    stability='experimental' if mx.get_os() == 'windows' else 'supported',
    priority=0,  # this is the main component of the llvm installable
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='LLVM Runtime Native',
    short_name='llrn',
    dir_name='llvm',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle NFI LIBFFI', 'LLVM Runtime Core'],
    truffle_jars=['sulong:SULONG_NATIVE'],
    support_distributions=[
        'sulong:SULONG_BITCODE_HOME',
        'sulong:SULONG_NATIVE_HOME',
    ],
    launcher_configs=_suite.toolchain.get_launcher_configs(),
    installable=True,
    priority=1,  # this component is part of the llvm installable but it's not the main one
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='LLVM Runtime Launcher',
    short_name='llrl',
    dir_name='llvm',
    license_files=[],
    third_party_license_files=[],
    dependencies=[],
    truffle_jars=[],
    support_distributions=[],
    library_configs=[
        mx_sdk_vm.LanguageLibraryConfig(
            launchers=['bin/<exe:lli>'],
            jar_distributions=['sulong:SULONG_LAUNCHER'],
            main_class='com.oracle.truffle.llvm.launcher.LLVMLauncher',
            build_args=[],
            build_args_enterprise=[
                '-H:+AuxiliaryEngineCache',
                '-H:ReservedAuxiliaryImageBytes=2145482548',
            ] if not mx.is_windows() else [],
            language='llvm',
        ),
    ],
    installable=True,
    priority=1,  # this component is part of the llvm installable but it's not the main one
))
