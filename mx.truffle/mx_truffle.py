#
# commands.py - the GraalVM specific commands
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

import os
import subprocess

import mx

from mx_unittest import unittest
from mx_sigtest import sigtest
from mx_gate import Task
import mx_gate

_suite = mx.suite('truffle')

def javadoc(args, vm=None):
    """build the Javadoc for all API packages"""
    mx.javadoc(['--unified'] + args)

def build(args, vm=None):
    """build the Java sources"""
    opts2 = mx.build(['--source', '1.7'] + args)
    assert len(opts2.remainder) == 0

def sl(args):
    """run an SL program"""
    vmArgs, slArgs = mx.extract_VM_args(args)
    mx.run_java(vmArgs + ['-cp', mx.classpath(["TRUFFLE_API", "com.oracle.truffle.sl"]), "com.oracle.truffle.sl.SLLanguage"] + slArgs)

def sldebug(args):
    """run a simple command line debugger for the Simple Language"""
    vmArgs, slArgs = mx.extract_VM_args(args, useDoubleDash=True)
    mx.run_java(vmArgs + ['-cp', mx.classpath("com.oracle.truffle.sl.tools"), "com.oracle.truffle.sl.tools.debug.SLREPL"] + slArgs)

def testdownstream(args):
    """test downstream users of the Truffle API"""
    jruby_dir = 'jruby'
    jruby_repo = 'https://github.com/jruby/jruby.git'
    jruby_branch = 'truffle-head'
    git = mx.GitConfig()
    if os.path.exists('jruby'):
        git.run(['git', 'reset', 'HEAD', '--hard'], nonZeroIsFatal=True, cwd=jruby_dir)
        git.pull('jruby')
    else:
        git.clone(jruby_repo, jruby_dir)
        git.run(['git', 'checkout', jruby_branch], nonZeroIsFatal=True, cwd=jruby_dir)
    dev_version = _suite.release_version(snapshotSuffix='SNAPSHOT')
    subprocess.check_call(['tool/truffle/set_truffle_version.sh', dev_version], cwd=jruby_dir)
    mx.build([])
    mx.maven_install([])
    subprocess.check_call(['./mvnw', 'clean'], cwd=jruby_dir)
    subprocess.check_call(['./mvnw'], cwd=jruby_dir)
    subprocess.check_call(['bin/jruby', 'tool/jt.rb', 'test', 'fast'], cwd=jruby_dir)

def _truffle_gate_runner(args, tasks):
    with Task('Truffle Javadoc', tasks) as t:
        if t: mx.javadoc(['--unified'])
    with Task('Truffle UnitTests', tasks) as t:
        if t: unittest(['--suite', 'truffle', '--enable-timing', '--verbose', '--fail-fast'])
    with Task('Truffle Signature Tests', tasks) as t:
        if t: sigtest(['--check', 'binary'])

mx_gate.add_gate_runner(_suite, _truffle_gate_runner)

mx.update_commands(_suite, {
    'javadoc' : [javadoc, '[SL args|@VM options]'],
    'sl' : [sl, '[SL args|@VM options]'],
    'sldebug' : [sldebug, '[SL args|@VM options]'],
    'testdownstream' : [testdownstream, ''],
})
