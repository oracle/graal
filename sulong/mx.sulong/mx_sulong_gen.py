#
# Copyright (c) 2016, 2025, Oracle and/or its affiliates.
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
import tempfile
from argparse import ArgumentParser

import mx
import mx_util
import mx_sulong

_suite = mx.suite('sulong')

COPYRIGHT_HEADER_BSD = """\
/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
// Checkstyle: stop
//@formatter:off
{0}
"""


COPYRIGHT_HEADER_BSD_HASH = """\
#
# Copyright (c) 2020, 2025, Oracle and/or its affiliates.
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
"""


@mx.command(_suite.name, 'create-asm-parser')
def create_asm_parser(args=None, out=None):
    """create the inline assembly parser using antlr"""
    mx.suite("truffle").extensions.create_parser("com.oracle.truffle.llvm.asm.amd64", "com.oracle.truffle.llvm.asm.amd64", "InlineAssembly", args=args, out=out, shaded=True)


@mx.command(_suite.name, 'create-debugexpr-parser')
def create_debugexpr_parser(args=None, out=None):
    """create the debug expression parser using antlr"""
    mx.suite("truffle").extensions.create_parser(grammar_project="com.oracle.truffle.llvm.runtime",
                                                 grammar_package="com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.antlr",
                                                 grammar_name="DebugExpression",
                                                 args=args, out=out, shaded=True)


@mx.command(_suite.name, 'create-parsers')
def create_parsers(args=None, out=None):
    """create the debug expression and the inline assembly parser using antlr"""
    create_asm_parser(args, out)
    create_debugexpr_parser(args, out)


def _write_llvm_config_java(project_name, constants, file_comment=None):
    package_name = project_name
    class_name = "LLVMConfig"
    source_gen_dir = mx.dependency(project_name).source_dirs()[0]
    rel_file = package_name.split(".") + [class_name + ".java"]
    src_file = os.path.join(source_gen_dir, *rel_file)
    mx_util.ensure_dir_exists(os.path.dirname(src_file))
    with open(src_file, "w") as fp:
        mx.log("Generating {}".format(src_file))
        fp.write(COPYRIGHT_HEADER_BSD.format("package {package};".format(package=package_name)))
        if file_comment:
            fp.write("\n/**\n * {}\n */\n".format(file_comment))
        fp.write("public abstract class {class_name} {{\n".format(class_name=class_name))
        fp.write("\n    private {class_name}() {{}}\n\n".format(class_name=class_name))
        for const_name, value, description in constants:
            fp.write("    /** {} */\n".format(description))
            if isinstance(value, int):
                fp.write("    public static final int {} = {};\n".format(const_name, value))
            else:
                fp.write("    public static final String {} = \"{}\";\n".format(const_name, value))
        fp.write("}\n")


def _write_llvm_config_mx(constants, file_comment=None):
    file_name = "mx_sulong_llvm_config.py"
    src_file = os.path.join(_suite.mxDir, file_name)
    mx_util.ensure_dir_exists(os.path.dirname(src_file))
    with open(src_file, "w") as fp:
        mx.log("Generating {}".format(src_file))
        fp.write(COPYRIGHT_HEADER_BSD_HASH)
        if file_comment:
            fp.write("\n# {}\n\n".format(file_comment))
        for const_name, value, description in constants:
            fp.write("# {}\n".format(description))
            if isinstance(value, int):
                fp.write("{} = {}\n".format(const_name, value))
            else:
                fp.write("{} = \"{}\"\n".format(const_name, value))


GENERATE_LLVM_CONFIG = "generate-llvm-config"


@mx.command(_suite.name, GENERATE_LLVM_CONFIG)
def generate_llvm_config(args=None, **kwargs):

    constants = []

    # get config full string
    out = mx.OutputCapture()
    mx_sulong.llvm_tool(["llvm-config", "--version"] + list(args), out=out)
    full_version = out.data.strip()
    # NOTE: do not add full version until we need it to avoid regeneration
    # constants.append(("VERSION_FULL", full_version, "Full LLVM version string."))
    # version without suffix
    s = full_version.split("-", 3)
    version = s[0]
    constants.append(("VERSION", version, "LLVM version string."))
    # major, minor, patch
    s = version.split(".", 3)
    major_version, minor_version, patch_version = s[0], s[1], s[2]
    constants.append(("VERSION_MAJOR", int(major_version), "Major version of the LLVM API."))
    constants.append(("VERSION_MINOR", int(minor_version), "Minor version of the LLVM API."))
    constants.append(("VERSION_PATCH", int(patch_version), "Patch version of the LLVM API."))

    file_comment = "GENERATED BY 'mx {}'. DO NOT MODIFY.".format(GENERATE_LLVM_CONFIG)

    _write_llvm_config_java("com.oracle.truffle.llvm.runtime", constants, file_comment)
    _write_llvm_config_java("com.oracle.truffle.llvm.toolchain.launchers", constants, file_comment)
    _write_llvm_config_mx(constants, file_comment)


@mx.command(_suite.name, "create-generated-sources", usage_msg="# recreate generated source files (parsers, config files)")
def create_generated_sources(args=None, out=None):
    parser = ArgumentParser(prog='mx create-generated-sources', description='recreate generated source files (parsers, config files)')
    parser.add_argument('--check', action='store_true', help='check for differences, fail if anything changed')
    parsed_args, args = parser.parse_known_args(args)

    if parsed_args.check:
        witness = tempfile.NamedTemporaryFile()
        mx.run(['git', 'diff', '--', _suite.dir], out=witness)

    try:
        create_parsers(args, out=out)
        generate_llvm_config(args, out=out)

        if parsed_args.check:
            with tempfile.NamedTemporaryFile() as f2:
                mx.run(['git', 'diff', '--', _suite.dir], out=f2)
                with open(witness.name, 'r') as before, open(f2.name, 'r') as after:
                    while True:
                        line_before = before.readline()
                        line_after = after.readline()
                        if line_before != line_after:
                            mx.run(['git', 'diff', '--', _suite.dir])
                            if os.path.getsize(witness.name) > 0:
                                mx.warn("There were changes before so the diff above might be misleading (e.g., create-generated-sources might have reverted some changes)")
                            raise mx.abort('Generating sources changed some files. See diff above')
                        if line_before == '' or line_after == '':
                            break
    finally:
        if parsed_args.check:
            witness.close()
