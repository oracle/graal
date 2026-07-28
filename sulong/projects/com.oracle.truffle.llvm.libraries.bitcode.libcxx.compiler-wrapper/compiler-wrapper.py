#!/usr/bin/env python3
#
# Copyright (c) 2026, Oracle and/or its affiliates.
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
import sys


_DEBUG_PREFIX_MAP = "-fdebug-prefix-map="


def _rewrite_debug_prefix_map(argument):
    if not argument.startswith(_DEBUG_PREFIX_MAP):
        return argument

    source, separator, destination = argument[len(_DEBUG_PREFIX_MAP):].partition("=")
    if not separator:
        return argument

    # LLVM PR #143004 adds this mapping after our reproducible-build flags and maps generated
    # libc++ headers back to an absolute source directory. That directory is retained in the
    # embedded bitcode and rejected by Native Image's user-directory check. Keep the useful
    # mapping, but make its destination reproducible.
    # https://github.com/llvm/llvm-project/pull/143004#issuecomment-3125013603
    is_libcxx_header_map = source.endswith("/include/c++/v1") and destination.endswith("/src/libcxx/include")
    if is_libcxx_header_map and os.path.isabs(destination):
        return f"{_DEBUG_PREFIX_MAP}{source}=llvm-project/src/libcxx/include"

    return argument


def main():
    compiler_arguments = [_rewrite_debug_prefix_map(argument) for argument in sys.argv[1:]]
    if not compiler_arguments:
        raise SystemExit("missing compiler command")
    os.execvp(compiler_arguments[0], compiler_arguments)


if __name__ == "__main__":
    main()
