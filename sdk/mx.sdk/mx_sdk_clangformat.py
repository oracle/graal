#
# Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#

import mx

import os.path
import subprocess
import sys

from argparse import ArgumentParser

_suite = mx.suite('sdk')

@mx.command(_suite.name, "clangformat")
def clangformat(args=None):
    """ Runs clang-format on C/C++ files in native projects of the primary suite """
    parser = ArgumentParser(prog='mx clangformat')
    parser.add_argument('--with-projects', action='store_true', help='check native projects. Defaults to true unless a path is specified.')
    parser.add_argument('--primary', action='store_true', help='limit checks to primary suite')
    parser.add_argument('paths', metavar='path', nargs='*', help='check given paths')
    args = parser.parse_args(args)
    paths = [(p, "<cmd-line-argument>") for p in args.paths]

    if not paths or args.with_projects:
        paths += [(p.dir, p.name) for p in mx.projects(limit_to_primary=args.primary) if p.isNativeProject() and getattr(p, "clangFormat", True)]

    # ensure LLVM_TOOLCHAIN is built
    mx.command_function('build')(['--dependencies', 'LLVM_TOOLCHAIN'])
    clangFormat = os.path.join(mx.dependency('LLVM_TOOLCHAIN', fatalIfMissing=True).get_output(), 'bin', mx.exe_suffix('clang-format'))

    error = False
    for f, reason in paths:
        if not checkCFiles(clangFormat, f, reason):
            error = True
    if error:
        mx.log_error("found formatting errors!")
        sys.exit(-1)


def checkCFiles(clangFormat, target, reason):
    error = False
    files_to_check = []
    if os.path.isfile(target):
        files_to_check.append(target)
    else:
        for path, _, files in os.walk(target):
            for f in files:
                if f.endswith('.c') or f.endswith('.cpp') or f.endswith('.h') or f.endswith('.hpp'):
                    files_to_check.append(os.path.join(path, f))
    if not files_to_check:
        mx.logv("clang-format: no files found {} ({})".format(target, reason))
        return True
    mx.logv("clang-format: checking {} ({}, {} files)".format(target, reason, len(files_to_check)))
    for f in files_to_check:
        if not checkCFile(clangFormat, f):
            error = True
    return not error


def checkCFile(clangFormat, targetFile):
    mx.logvv("  checking file " + targetFile)
    """ Checks the formatting of a C file and returns True if the formatting is okay """
    formatCommand = [clangFormat, targetFile]
    formattedContent = subprocess.check_output(formatCommand).decode().splitlines()
    with open(targetFile) as f:
        originalContent = f.read().splitlines()
    if not formattedContent == originalContent:
        # modify the file to the right format
        subprocess.check_output(formatCommand + ['-i'])
        mx.log('\n'.join(formattedContent))
        mx.log('\nmodified formatting in {0} to the format above'.format(targetFile))
        mx.logv("command: " + " ".join(formatCommand))
        return False
    return True
