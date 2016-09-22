#!/usr/bin/env python2.7
#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
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
#

import sys
import mx
import os
import shutil
from os.path import exists
from os.path import join
from os.path import isdir

def testgraal(args):
    cloneFrom = mx.get_env("GRAAL_URL")
    if not cloneFrom:
        cloneFrom = "http://github.com/graalvm/graal-core"

    suite = mx.suite('truffle')
    suiteDir = suite.dir
    workDir = join(suiteDir, 'mxbuild', 'sanitycheck')
    mx.ensure_dir_exists(join(workDir, suite.name))
    for f in os.listdir(suiteDir):
        if 'mxbuild' == f:
            continue
        src = join(suiteDir, f)
        tgt = join(workDir, suite.name, f)
        if isdir(src):
            if exists(tgt):
                shutil.rmtree(tgt)
            shutil.copytree(src, tgt)
        else:
            shutil.copy(src, tgt)

    sanityDir = join(workDir, 'sanity')
    git = mx.GitConfig()
    if exists(sanityDir):
        git.pull(sanityDir)
    else:
        git.clone(cloneFrom, sanityDir)

    commands = [sys.executable, join(mx._mx_home, 'mx.py'), '-p', sanityDir, '--java-home=' + mx.get_jdk().home]
    return mx.run(commands + ['build'], cwd=sanityDir) + mx.run(commands + ['unittest', 'truffle'], cwd=sanityDir)
