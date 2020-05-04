#
# mx_tools.py - the GraalVM specific commands
#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
#
# ----------------------------------------------------------------------------------------------------

import os
from os.path import exists, join
import re
import tempfile
import shutil
import glob

import mx

from mx_sigtest import sigtest
from mx_unittest import unittest
from mx_jackpot import jackpot
from mx_gate import Task
import mx_gate
import mx_unittest
import mx_benchmark
import mx_sdk_vm

import sys

if sys.version_info[0] < 3:
    from urlparse import urljoin
else:
    from urllib.parse import urljoin # pylint: disable=no-name-in-module


_suite = mx.suite('tools')

class JMHRunnerToolsBenchmarkSuite(mx_benchmark.JMHRunnerBenchmarkSuite):

    def name(self):
        return "tools"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "tools"

    def extraVmArgs(self):
        return ['-XX:-UseJVMCIClassLoader'] + super(JMHRunnerToolsBenchmarkSuite, self).extraVmArgs()

mx_benchmark.add_bm_suite(JMHRunnerToolsBenchmarkSuite())


def javadoc(args):
    """build the Javadoc for all packages"""
    if not args:
        projectNames = []
        for p in mx.projects(True, True):
            projectNames.append(p.name)
        mx.javadoc(['--unified', '--projects', ','.join(projectNames)], includeDeps=False)
    else:
        mx.javadoc(['--unified'] + args)
    javadocDir = os.sep.join([_suite.dir, 'javadoc'])
    index = os.sep.join([javadocDir, 'index.html'])
    if exists(index):
        indexContent = open(index, 'r').read()
        new_file = open(index, "w")
        new_file.write(indexContent)
    checkLinks(javadocDir)

def checkLinks(javadocDir):
    href = re.compile('(?<=href=").*?(?=")')
    filesToCheck = {}
    for root, _, files in os.walk(javadocDir):
        for f in files:
            if f.endswith('.html'):
                html = os.path.join(root, f)
                content = open(html, 'r').read()
                for url in href.findall(content):
                    full = urljoin(html, url)
                    sectionIndex = full.find('#')
                    questionIndex = full.find('?')
                    minIndex = sectionIndex
                    if minIndex < 0:
                        minIndex = len(full)
                    if 0 <= questionIndex < minIndex:
                        minIndex = questionIndex
                    path = full[0:minIndex]

                    sectionNames = filesToCheck.get(path, [])
                    if sectionIndex >= 0:
                        s = full[sectionIndex + 1:]
                        sectionNames = sectionNames + [(html, s)]
                    else:
                        sectionNames = sectionNames + [(html, None)]

                    filesToCheck[path] = sectionNames

    err = False
    for referencedfile, sections in filesToCheck.items():
        if referencedfile.startswith('javascript:') or referencedfile.startswith('http:') or referencedfile.startswith('https:') or referencedfile.startswith('mailto:'):
            continue
        if not exists(referencedfile):
            mx.warn('Referenced file ' + referencedfile + ' does not exist. Referenced from ' + sections[0][0])
            err = True
        else:
            content = open(referencedfile, 'r').read()
            for path, s in sections:
                if not s is None:
                    whereName = content.find('name="' + s + '"')
                    whereId = content.find('id="' + s + '"')
                    if whereName == -1 and whereId == -1:
                        mx.warn('There should be section ' + s + ' in ' + referencedfile + ". Referenced from " + path)
                        err = True

    if err:
        mx.abort('There are wrong references in Javadoc')

def lsp_types_gen(args):
    generators_dir = join(_suite.dir, 'generators')
    out = join(_suite.get_output_root(), 'lsp-types')
    if exists(out):
        mx.rmtree(out)
    mx.run(['npm', 'install'], nonZeroIsFatal=True, cwd=generators_dir)
    mx.run(['npm', 'run', 'compile'], nonZeroIsFatal=True, cwd=generators_dir)
    repository = open(join(generators_dir, 'resources', 'REPOSITORY'), 'r').read()
    branch = open(join(generators_dir, 'resources', 'VERSION'), 'r').read()
    tmpdir = tempfile.mkdtemp()
    catfile = open(join(tmpdir, 'lsp.ts'), 'w')
    mx.run(['git', 'clone', '-b', branch, repository, 'lib'], nonZeroIsFatal=True, cwd=tmpdir)
    spec_files = glob.glob(join(tmpdir, 'lib/types/src/*.ts'))
    spec_files.extend(glob.glob(join(tmpdir, 'lib/jsonrpc/src/*.ts')))
    spec_files.extend(glob.glob(join(tmpdir, 'lib/protocol/src/*.ts')))
    for file_name in spec_files:
        with open(file_name, 'r') as input_file:
            shutil.copyfileobj(input_file, catfile)
        catfile.flush()
    pkg = open(join(generators_dir, 'resources', 'PACKAGE'), 'r').read()
    mx.run(['node', 'out/generator.js', '--source', catfile.name, '--target', out, '--suffixNested', '--pkg', pkg, '--license', 'resources/license.txt'], nonZeroIsFatal=True, cwd=generators_dir)
    mx.rmtree(tmpdir)
    mx.run(['patch', '-p1', '-s', '-i', join(generators_dir, 'resources', 'patch.diff')], nonZeroIsFatal=True, cwd=out)
    mx.log('LSP types generated to: ' + out)

def _unittest_config_participant(config):
    vmArgs, mainClass, mainClassArgs = config
    if mx.get_jdk(tag='default').javaCompliance > '1.8':
        # This is required to access jdk.internal.module.Modules which
        # in turn allows us to dynamically open fields/methods to reflection.
        vmArgs = vmArgs + ['--add-exports=java.base/jdk.internal.module=ALL-UNNAMED']

        # This is required for the call to setAccessible in
        # TruffleTCK.testValueWithSource to work.
        vmArgs = vmArgs + ['--add-opens=org.graalvm.truffle/com.oracle.truffle.api.vm=ALL-UNNAMED', '--add-modules=ALL-MODULE-PATH']
    return (vmArgs, mainClass, mainClassArgs)

mx_unittest.add_config_participant(_unittest_config_participant)

def _tools_gate_runner(args, tasks):
    with Task('Jackpot check', tasks) as t:
        if t: jackpot(['--fail-on-warnings'], suite=None, nonZeroIsFatal=True)
    with Task('Tools Signature Tests', tasks) as t:
        if t: sigtest(['--check', 'binary'])
    with Task('Tools UnitTests', tasks) as t:
        if t: unittest(['--suite', 'tools', '--enable-timing', '--verbose', '--fail-fast'])

mx_gate.add_gate_runner(_suite, _tools_gate_runner)

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmTool(
    suite=_suite,
    name='GraalVM Language Server',
    short_name='lsp',
    dir_name='lsp',
    license_files=[],
    third_party_license_files=[],
    truffle_jars=['tools:LSP_API', 'tools:LSP'],
    support_distributions=['tools:LSP_GRAALVM_SUPPORT'],
    include_by_default=True,
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmTool(
    suite=_suite,
    name='GraalVM Chrome Inspector',
    short_name='ins',
    dir_name='chromeinspector',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle'],
    truffle_jars=['tools:CHROMEINSPECTOR'],
    support_distributions=['tools:CHROMEINSPECTOR_GRAALVM_SUPPORT'],
    include_by_default=True,
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmTool(
    suite=_suite,
    name='AgentScript',
    short_name='ats',
    dir_name='agentscript',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle'],
    truffle_jars=['tools:AGENTSCRIPT'],
    support_distributions=['tools:AGENTSCRIPT_GRAALVM_SUPPORT'],
    priority=10,
    include_by_default=True,
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmTool(
    suite=_suite,
    name='GraalVM Profiler',
    short_name='pro',
    dir_name='profiler',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle'],
    truffle_jars=['tools:TRUFFLE_PROFILER'],
    support_distributions=['tools:TRUFFLE_PROFILER_GRAALVM_SUPPORT'],
    include_by_default=True,
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmTool(
    suite=_suite,
    name='GraalVM Coverage',
    short_name='cov',
    dir_name='coverage',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle'],
    truffle_jars=['tools:TRUFFLE_COVERAGE'],
    support_distributions=['tools:TRUFFLE_COVERAGE_GRAALVM_SUPPORT'],
    include_by_default=True,
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJdkComponent(
    suite=_suite,
    name='VisualVM',
    short_name='vvm',
    dir_name='visualvm',
    license_files=[],
    third_party_license_files=[],
    dependencies=[],
    support_distributions=['tools:VISUALVM_GRAALVM_SUPPORT'],
    provided_executables=[('tools:VISUALVM_PLATFORM_SPECIFIC', './bin/<exe:jvisualvm>')]
))

for mode in ['jvm', 'native']:
    mx_sdk_vm.add_graalvm_hostvm_config(mode + '-cpusampler-exclude-inlined-roots', launcher_args=['--' + mode, '--cpusampler', '--cpusampler.Mode=exclude_inlined_roots'])
    mx_sdk_vm.add_graalvm_hostvm_config(mode + '-cpusampler-roots', launcher_args=['--' + mode, '--cpusampler', '--cpusampler.Mode=roots'])
    mx_sdk_vm.add_graalvm_hostvm_config(mode + '-cpusampler-statements', launcher_args=['--' + mode, '--cpusampler', '--cpusampler.Mode=statements'])
    mx_sdk_vm.add_graalvm_hostvm_config(mode + '-cputracer-roots', launcher_args=['--' + mode, '--cputracer', '--cputracer.TraceRoots=true'])
    mx_sdk_vm.add_graalvm_hostvm_config(mode + '-cputracer-statements', launcher_args=['--' + mode, '--cputracer', '--cputracer.TraceStatements=true'])

mx.update_commands(_suite, {
    'javadoc' : [javadoc, ''],
    'gate' : [mx_gate.gate, ''],
    'lsp-types-gen' : [lsp_types_gen, ''],
})
