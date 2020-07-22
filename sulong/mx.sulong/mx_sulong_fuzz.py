#
# Copyright (c) 2020, Oracle and/or its affiliates.
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
import os
import stat
import tempfile
import shutil
import datetime
import time
import filecmp
import shlex
from random import Random
from argparse import ArgumentParser

import mx
import mx_subst
import mx_sulong


def _run_fuzz_tool(tool_name, tool_args, *args, **kwargs):
    dist = 'SULONG_TOOLS'
    tool = os.path.join(mx.dependency(dist, fatalIfMissing=True).get_output(), 'bin', tool_name)
    if not os.path.exists(tool):
        msg = "The executable {} does not exist: {}{}".format(tool_name, tool, os.linesep)
        msg += "This might be solved by running: mx build --dependencies={}".format(dist)
        mx.abort(msg)
    return mx.run([tool] + tool_args, *args, **kwargs)


@mx.command("sulong", "fuzz")
def fuzz(args=None, out=None):
    parser = ArgumentParser(prog='mx fuzz', description='')
    parser.add_argument('--seed', help='Seed used for randomness.', metavar='<seed>', type=int, default=int(time.time()))
    parser.add_argument('--size', help='Approximate size for the generated testcases in lines of code. (default:  %(default)s)', metavar='<size>', type=int, default=30)
    parser.add_argument('--timeout', help='Timeout for running the generated program. (default:  %(default)s)', metavar='<timeout>', type=int, default=10)
    parser.add_argument('--generator', help='Tool used for generating the testcases. (default:  %(default)s)', choices=("llvm-stress", "csmith"), default="llvm-stress")
    parser.add_argument('--nrtestcases', help='Number of testcases to be generated. (default:  %(default)s)', metavar='<nrtestcases>', type=int, default=10)
    parser.add_argument('outdir', help='The output directory.', metavar='<outdir>')
    parsed_args = parser.parse_args(args)

    tmp_dir = None
    try:
        tmp_dir = tempfile.mkdtemp()
        tmp_ll = os.path.join(tmp_dir, 'tmp.ll')
        tmp_main_ll = os.path.join(tmp_dir, 'tmp.main.ll')
        tmp_c = os.path.join(tmp_dir, 'tmp.c')
        tmp_out = os.path.join(tmp_dir, 'tmp.out')
        tmp_sulong_out = os.path.join(tmp_dir, 'tmp_sulong_out.txt')
        tmp_bin_out = os.path.join(tmp_dir, 'tmp_bin_out.txt')
        tmp_sulong_err = os.path.join(tmp_dir, 'tmp_sulong_err.txt')
        tmp_bin_err = os.path.join(tmp_dir, 'tmp_bin_err.txt')
        rand = Random(parsed_args.seed)

        passed = 0
        invalid = 0
        gen = []
        for _ in range(parsed_args.nrtestcases):
            toolchain_clang = mx_sulong._get_toolchain_tool("native,CC")
            if parsed_args.generator == "llvm-stress":
                _run_fuzz_tool("llvm-stress", ["-o", tmp_ll, "--size", str(parsed_args.size), "--seed", str(rand.randint(0, 10000000))])
                fuzz_main = os.path.join(mx.dependency('SULONG_TOOLS', fatalIfMissing=True).get_output(), "src", "fuzzmain.c")
                mx.run([toolchain_clang, "-O0", "-Wno-everything", "-o", tmp_out, tmp_ll, fuzz_main])
                mx_sulong.llvm_tool(["clang", "-O0", "-Wno-everything", "-S", "-emit-llvm", "-o", tmp_main_ll, fuzz_main])
                mx_sulong.llvm_tool(["llvm-link", "-o", tmp_ll, tmp_ll, tmp_main_ll])
                mx_sulong.llvm_tool(["llvm-dis", "-o", tmp_ll, tmp_ll])
            else:
                csmith_exe = mx_sulong.which("csmith")
                if not csmith_exe:
                    mx.abort("`csmith` executable not found")
                csmith_headers = mx.get_env('CSMITH_HEADERS', None)
                if not csmith_headers:
                    mx.abort("Environment variable `CSMITH_HEADERS` not set")
                mx.run([csmith_exe, "-o", tmp_c, "--seed", str(rand.randint(0, 10000000))])
                mx.run([toolchain_clang, "-O0", "-Wno-everything", "-I" + csmith_headers, "-o", tmp_out, tmp_c])
                mx_sulong.llvm_tool(["clang", "-O0", "-Wno-everything", "-S", "-emit-llvm", "-I" + csmith_headers, "-o", tmp_ll, tmp_c])
                gen.append((tmp_c, 'autogen.c'))
            timeout = parsed_args.timeout
            with open(tmp_sulong_out, 'w') as o, open(tmp_sulong_err, 'w') as e:
                mx_sulong.runLLVM(['--llvm.llDebug', '--llvm.traceIR', '--experimental-options', tmp_out], timeout=timeout, nonZeroIsFatal=False, out=o, err=e)
            with open(tmp_bin_out, 'w') as o, open(tmp_bin_err, 'w') as e:
                try:
                    mx.run([tmp_out], timeout=timeout, out=o, err=e)
                except SystemExit:
                    invalid += 1
                    continue

            if all(filecmp.cmp(sulong_f, bin_f, shallow=False) for sulong_f, bin_f in ((tmp_sulong_out, tmp_bin_out), (tmp_sulong_err, tmp_bin_err))):
                passed += 1
            else:
                now = str(datetime.datetime.now())
                now = now.replace(":", "_").replace(" ", "_")
                current_out_dir = os.path.join(parsed_args.outdir, now + "_" + parsed_args.generator)
                os.makedirs(current_out_dir)
                gen += [
                    (tmp_ll, 'autogen.ll'),
                    (tmp_out, 'autogen'),
                    (tmp_sulong_out, 'sulong_out.txt'),
                    (tmp_bin_out, 'bin_out.txt'),
                    (tmp_sulong_err, 'sulong_err.txt'),
                    (tmp_bin_err, 'bin_err.txt'),
                ]
                for tmp_f, gen_f_name in gen:
                    shutil.copy(tmp_f, os.path.join(current_out_dir, gen_f_name))
    finally:
        if tmp_dir:
            shutil.rmtree(tmp_dir)
    mx.log("Test report")
    mx.log("total testcases: {} seed: {}".format(parsed_args.nrtestcases, parsed_args.seed))
    mx.log("interesting testcases: {} invalid testcases: {}".format(parsed_args.nrtestcases-invalid-passed, invalid))


@mx.command("sulong", "ll-reduce")
def ll_reduce(args=None, out=None):
    parser = ArgumentParser(prog='mx ll-reduce', description='')
    parser.add_argument('--interestingness-test', help='Command which exits with code 1 if a given .ll file is interesting and exits with code 0 otherwise. (default:  %(default)s)', metavar='<interestingnesstest>', default='mx check-interesting')
    parser.add_argument('--seed', help='Seed used for randomness.', metavar='<seed>', type=int, default=int(time.time()))
    parser.add_argument('--timeout', help='Time in seconds until no new reduction operations are permitted to start.', metavar='<timeout>', type=int, default=None)
    parser.add_argument('--timeout-stabilized', help='Time in seconds that should pass since no successful minimal reduction operation has been performed until no new reduction operations are permitted to start.', metavar='<timeout-stabilized>', type=int, default=10)
    parser.add_argument('--clang-input', help='Additional input files that should be forwarded to clang. No reductions will be performed on these files. Mx path substitutions are enabled.', metavar='<clanginputs>', nargs='*')
    parser.add_argument('--output', help='The output file. If omitted, <input>.reduced.ll is used.', metavar='<output>', default=None)
    parser.add_argument('input', help='The input file.', metavar='<input>')
    parsed_args = parser.parse_args(args)

    mx.log("Running ll-reduce with the following configuration:")
    for k, v in vars(parsed_args).items():
        mx.log("{:>30}: {}".format(k, v))

    tmp_dir = None
    nrmutations = 4
    starttime = time.time()
    starttime_stabilized = None
    tmp_ll = None

    try:
        tmp_dir = tempfile.mkdtemp()
        tmp_bc = os.path.join(tmp_dir, 'tmp.bc')
        tmp_ll = os.path.join(tmp_dir, 'tmp1.ll')
        tmp_ll_reduced = os.path.join(tmp_dir, 'tmp2.ll')
        tmp_out = os.path.join(tmp_dir, 'tmp.out')
        tmp_sulong_out_original = os.path.join(tmp_dir, 'tmp_sulong_out_original.txt')
        tmp_sulong_err_original = os.path.join(tmp_dir, 'tmp_sulong_err_original.txt')
        rand = Random(parsed_args.seed)
        lli_timeout = 10

        def count_lines(file_name):
            i = 0
            with open(file_name) as f:
                for i, _ in enumerate(f, 1):
                    pass
            return i

        def run_lli(input_f, out_f, err_f):
            additional_clang_input = [mx_subst.path_substitutions.substitute(ci) for ci in parsed_args.clang_input or []]
            toolchain_clang = mx_sulong._get_toolchain_tool("native,CC")
            mx_sulong.llvm_tool([toolchain_clang, "-O0", "-Wno-everything", "-o", tmp_out, input_f] + additional_clang_input)
            with open(out_f, 'w') as o, open(err_f, 'w') as e:
                mx_sulong.runLLVM([tmp_out], timeout=lli_timeout, nonZeroIsFatal=False, out=o, err=e)

        def run_interestingness_test(interestingness_test, input_file):
            return mx.run(shlex.split(interestingness_test) + [input_file], nonZeroIsFatal=False)

        def run_llvm_reduce(nrmutations, input_bc, output_ll):
            reduce_out = mx.OutputCapture()
            try:
                args = [input_bc,
                        "-ignore_remaining_args=1",
                        "-mtriple", "x86_64-unknown-linux-gnu",
                        "-nrmutations", str(nrmutations),
                        "-seed", str(rand.randint(0, 10000000)),
                        "-o", output_ll]
                _run_fuzz_tool("llvm-reduce", args, out=reduce_out, err=reduce_out)
            except SystemExit as se:
                mx.log_error(reduce_out.data)
                mx.abort("Error executing llvm-reduce: {}".format(se))

        shutil.copy(parsed_args.input, tmp_ll)

        # check whether the input is interesting
        orig_interesting = run_interestingness_test(parsed_args.interestingness_test, tmp_ll)
        if not orig_interesting:
            mx.abort("Input program is not interesting!")

        run_lli(tmp_ll, tmp_sulong_out_original, tmp_sulong_err_original)
        while True:
            if parsed_args.timeout and time.time() - starttime > parsed_args.timeout:
                mx.log("Timeout exceeded")
                break
            if starttime_stabilized and time.time() - starttime_stabilized > parsed_args.timeout_stabilized:
                mx.log("Result stabilized (no more progress)")
                break
            mx_sulong.llvm_tool(["llvm-as", "-o", tmp_bc, tmp_ll])
            mx.log("nrmutations: {} filesize: {} bytes (bc), number of lines {} (ll)".format(nrmutations, os.path.getsize(tmp_bc), count_lines(tmp_ll)))
            run_llvm_reduce(nrmutations, tmp_bc, tmp_ll_reduced)
            reduced_interesting = run_interestingness_test(parsed_args.interestingness_test, tmp_ll_reduced)
            if reduced_interesting:
                if not filecmp.cmp(tmp_ll, tmp_ll_reduced, shallow=False):
                    tmp_ll, tmp_ll_reduced = tmp_ll_reduced, tmp_ll
                    nrmutations *= 2
                    starttime_stabilized = None
                    continue
                mx.log("Reduced file is identical to input file!")
            if nrmutations > 1:
                nrmutations //= 2
            else:
                if not starttime_stabilized:
                    starttime_stabilized = time.time()
    finally:
        if tmp_ll and os.path.isfile(tmp_ll):
            result = parsed_args.output or (os.path.splitext(parsed_args.input)[0] + ".reduced.ll")
            mx.log("Writing reduced ll file to {}".format(result))
            shutil.copy(tmp_ll, result)
        if tmp_dir:
            shutil.rmtree(tmp_dir)


@mx.command("sulong", "check-interesting")
def check_interesting(args=None, out=None):
    parser = ArgumentParser(prog='mx check-interesting', description='')
    parser.add_argument('input', help='The input file.', metavar='<input>')
    parser.add_argument('--bin-startswith', help='Prefix the reference output of interesting testprograms has to start with.', metavar='<refstartswith>', default=None)
    parser.add_argument('--sulong-startswith', help='Prefix the output of interesting testprograms has to start with when executed on Sulong.', metavar='<teststartswith>', default=None)
    parsed_args = parser.parse_args(args)

    def _not_interesting(msg=None):
        if msg:
            mx.logv(mx.colorize("mx check-interesting: no ({})".format(msg), color="blue"))
        exit(0)

    def _interesting(msg=None):
        if msg:
            mx.logv(mx.colorize("mx check-interesting: yes ({})".format(msg), color="cyan"))
        exit(1)

    def _files_match_pattern(prefix, out_file, err_file):
        with open(out_file, 'r') as o, open(err_file, 'r') as e:
            if not any(fl.startswith(prefix) for fl in (next(o, ""), next(e, ""))):
                return False
        return True

    tmp_dir = None
    try:
        tmp_dir = tempfile.mkdtemp()
        tmp_out = os.path.join(tmp_dir, 'tmp.out')
        tmp_out_o3 = os.path.join(tmp_dir, 'tmp.o3.out')
        tmp_sulong_out = os.path.join(tmp_dir, 'tmp_sulong_out.txt')
        tmp_bin_out = os.path.join(tmp_dir, 'tmp_bin_out.txt')
        tmp_sulong_err = os.path.join(tmp_dir, 'tmp_sulong_err.txt')
        tmp_bin_err = os.path.join(tmp_dir, 'tmp_bin_err.txt')
        tmp_bin_out_o3 = os.path.join(tmp_dir, 'tmp_bin_out_o3.txt')
        tmp_bin_err_o3 = os.path.join(tmp_dir, 'tmp_bin_err_o3.txt')
        try:
            toolchain_clang = mx_sulong._get_toolchain_tool("native,CC")
            mx.run([toolchain_clang, "-O0", "-Wno-everything", "-o", tmp_out, parsed_args.input])
            mx.run([toolchain_clang, "-O3", "-Wno-everything", "-o", tmp_out_o3, parsed_args.input])
        except SystemExit:
            _not_interesting("Compiling the input file failed!")
        with open(tmp_sulong_out, 'w') as o, open(tmp_sulong_err, 'w') as e:
            mx_sulong.runLLVM([tmp_out], timeout=10, nonZeroIsFatal=False, out=o, err=e)
        with open(tmp_bin_out, 'w') as o, open(tmp_bin_err, 'w') as e:
            try:
                mx.run([tmp_out], timeout=10, out=o, err=e)
            except SystemExit:
                _not_interesting("Running the O0 compiled input files natively failed!")
        with open(tmp_bin_out_o3, 'w') as o, open(tmp_bin_err_o3, 'w') as e:
            try:
                mx.run([tmp_out_o3], timeout=10, out=o, err=e)
            except SystemExit:
                _not_interesting("Running the O3 compiled input files natively failed!")
        if not all(filecmp.cmp(bin_f, bin_f_o3, shallow=False) for bin_f, bin_f_o3 in ((tmp_bin_out, tmp_bin_out_o3), (tmp_bin_err, tmp_bin_err_o3))):
            _not_interesting("The result of O0 and O3 is different!")
        if all(filecmp.cmp(sulong_f, bin_f, shallow=False) for sulong_f, bin_f in ((tmp_sulong_out, tmp_bin_out), (tmp_sulong_err, tmp_bin_err))):
            _not_interesting("The result of native and sulong is the same!")
        if parsed_args.bin_startswith and not _files_match_pattern(parsed_args.bin_startswith, tmp_bin_out, tmp_bin_err):
            _not_interesting("The native result does not match the pattern")
        if parsed_args.sulong_startswith and not _files_match_pattern(parsed_args.sulong_startswith, tmp_sulong_out, tmp_sulong_err):
            _not_interesting("The sulong result does not match the pattern")
        _interesting("Results are different and match the pattern (if provided)")
    finally:
        if tmp_dir:
            shutil.rmtree(tmp_dir)


@mx.command("sulong", "bugpoint")
def bugpoint(args=None, out=None):
    parser = ArgumentParser(prog='mx bugpoint', description="Run 'bugpoint' with useful defaults.", epilog="Remaining arguments are passed to 'bugpoint'.")
    parser.add_argument('--opt-command', help="Path to opt. (default: use 'opt' from the toolchain)", default=mx_sulong.findBundledLLVMProgram('opt'))
    parser.add_argument('--keep-main', help='Force function reduction to keep main. (always true)', action='store_true')
    parser.add_argument('--compile-custom', help='Use -compile-command to define a command to compile the bitcode. Useful to avoid linking. (always true)', action='store_true')
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--compile-command', help='Command to compile the bitcode. The command will be stored in a temporary script file.  (default:  %(default)s)', default='mx check-interesting')
    group.add_argument('--interestingness-test', help='synonym for --compile-command', dest='compile_command')
    parser.add_argument('--mlimit', metavar="<MBytes>", help='Maximum amount of memory to use. 0 disables check. Defaults to 0.', type=int, default=0)
    parser.add_argument('--help-bugpoint', help='Show the bugpoint help.', action='store_true')
    parsed_args, remaining_args = parser.parse_known_args(args)

    if parsed_args.help_bugpoint:
        mx_sulong.llvm_extra_tool(['bugpoint', '--help'])
        return

    class ClosedExecutableTempFile(object):
        def __init__(self, content, *args, **kwargs):
            self.content = content
            self.args = args
            self.kwargs = kwargs
            self.name = None

        def __enter__(self):
            with tempfile.NamedTemporaryFile(*self.args, delete=False, **self.kwargs) as fp:
                fp.write(mx._encode(self.content))
                st = os.stat(fp.name)
                os.chmod(fp.name, st.st_mode | stat.S_IEXEC)
                self.name = fp.name
            # ensure the file is closed!
            return self.name

        def __exit__(self, exc_type, exc_val, exc_tb):
            if self.name:
                os.remove(self.name)

    script_content = "#!/bin/bash\n"
    compile_command = parsed_args.compile_command
    if compile_command.startswith("mx "):
        compile_command = mx_sulong.get_mx_exe() + " -p {} ".format(mx_sulong._suite.dir) + compile_command[3:]
    script_content += compile_command + " $@\n"

    with ClosedExecutableTempFile(script_content, prefix="bugpoint-compile-command-", suffix=".sh") as compile_script:
        mx_sulong.llvm_extra_tool(['bugpoint', '--opt-command=' + parsed_args.opt_command, '--keep-main', '--compile-custom',
                                   '--mlimit=' + str(parsed_args.mlimit), '--compile-command=' + compile_script] + remaining_args)
