#!/usr/bin/env python3
#
# Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import argparse
import re
import shutil
import subprocess
import sys


def run_lldb(binary_path: str, line: int, filename: str) -> str:
    lldb = shutil.which("lldb")
    if lldb is None:
        print("lldb not found; skipping test.")
        sys.exit(0)

    commands = [
        "target create {}".format(binary_path),
        "settings set target.process.stop-on-sharedlibrary-events false",
        "breakpoint set --file {} --line {}".format(filename, line),
        "breakpoint list",
        "run",
        "frame info",
    ]

    cmd = [lldb, "--batch"]
    for command in commands:
        cmd.extend(["-o", command])

    result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    output = result.stdout
    if result.returncode != 0:
        raise AssertionError("lldb failed with exit code {}:\n{}".format(result.returncode, output))
    return output


def assert_breakpoint_hit(output: str, line: int, filename: str) -> None:
    if "Breakpoint 1:" not in output:
        raise AssertionError("Expected breakpoint to be created. Output:\n{}".format(output))
    if "pending" in output or "0 locations" in output:
        raise AssertionError("Expected resolved breakpoint location. Output:\n{}".format(output))
    if "stop reason = breakpoint" not in output:
        raise AssertionError("Expected process to stop at breakpoint. Output:\n{}".format(output))
    if "{}:{}".format(filename, line) not in output:
        raise AssertionError("Expected breakpoint to resolve to {}:{}.\n{}".format(filename, line, output))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("binary_path")
    parser.add_argument("--line", type=int, default=252)
    parser.add_argument("--file", dest="filename", default="Hello.java")
    args = parser.parse_args()

    output = run_lldb(args.binary_path, args.line, args.filename)
    assert_breakpoint_hit(output, args.line, args.filename)
    return 0


if __name__ == "__main__":
    sys.exit(main())
