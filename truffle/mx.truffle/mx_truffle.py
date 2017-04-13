#
# commands.py - the GraalVM specific commands
#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2007, 2016, Oracle and/or its affiliates. All rights reserved.
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
from os.path import exists
import re

import mx

from mx_graaltest import testgraal
from mx_unittest import unittest
from mx_sigtest import sigtest
from mx_jackpot import jackpot
from mx_gate import Task
from mx_javamodules import as_java_module, get_java_module_info
from urlparse import urljoin
import mx_gate
import mx_unittest

_suite = mx.suite('truffle')

def javadoc(args, vm=None):
    """build the Javadoc for all API packages"""
    mx.javadoc(['--unified'] + args)
    javadocDir = os.sep.join([_suite.dir, 'javadoc'])
    index = os.sep.join([javadocDir, 'index.html'])
    indexContent = open(index, 'r').read()
    indexContent = indexContent.replace('src="allclasses-frame.html"', 'src="com/oracle/truffle/api/vm/package-frame.html"')
    indexContent = indexContent.replace('src="overview-summary.html"', 'src="com/oracle/truffle/tutorial/package-summary.html"')
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
                    if questionIndex >= 0 and questionIndex < minIndex:
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
                if not s == None:
                    where = content.find('name="' + s + '"')
                    if where == -1:
                        mx.warn('There should be section ' + s + ' in ' + referencedfile + ". Referenced from " + path)
                        err = True

    if err:
        mx.abort('There are wrong references in Javadoc')

def build(args, vm=None):
    """build the Java sources"""
    opts2 = mx.build(['--source', '1.7'] + args)
    assert len(opts2.remainder) == 0

def _path_args(depNames=None):
    """
    Gets the VM args for putting the dependencies named in `depNames` on the
    class path and module path (if running on JDK9 or later).

    :param names: a Dependency, str or list containing Dependency/str objects. If None,
           then all registered dependencies are used.
    """
    jdk = mx.get_jdk()
    if jdk.javaCompliance >= '1.9':
        modules = [as_java_module(dist, jdk) for dist in _suite.dists if get_java_module_info(dist)]
        if modules:
            # Partition resources between the class path and module path
            modulepath = []
            classpath = []
            cpEntryToModule = {m.dist.path : m for m in modules}

            for e in mx.classpath(depNames).split(os.pathsep):
                if cpEntryToModule.has_key(e):
                    modulepath.append(cpEntryToModule[e].jarpath)
                else:
                    classpath.append(e)
            # The Truffle modules must be eagerly loaded as they could be referenced from
            # the main class hence the --add-modules argument
            return ['--add-modules=' + ','.join([m.name for m in modules]), '--module-path=' + os.pathsep.join(modulepath), '-cp', os.pathsep.join(classpath)]
    return ['-cp', mx.classpath(depNames)]

def _unittest_config_participant(config):
    vmArgs, mainClass, mainClassArgs = config
    if mx.get_jdk(tag='default').javaCompliance > '1.8':
        # This is required to access jdk.internal.module.Modules which
        # in turn allows us to dynamically open fields/methods to reflection.
        vmArgs = vmArgs + ['--add-exports=java.base/jdk.internal.module=ALL-UNNAMED']

        # This is required for the call to setAccessible in
        # TruffleTCK.testValueWithSource to work.
        vmArgs = vmArgs + ['--add-opens=com.oracle.truffle.truffle_api/com.oracle.truffle.api.vm=ALL-UNNAMED']
    return (vmArgs, mainClass, mainClassArgs)

mx_unittest.add_config_participant(_unittest_config_participant)

def sl(args):
    """run an SL program"""
    vmArgs, slArgs = mx.extract_VM_args(args)
    mx.run_java(vmArgs + _path_args(["TRUFFLE_API", "com.oracle.truffle.sl"]) + ["com.oracle.truffle.sl.SLMain"] + slArgs)

def _truffle_gate_runner(args, tasks):
    jdk = mx.get_jdk(tag=mx.DEFAULT_JDK_TAG)
    with Task('Jackpot check', tasks) as t:
        if t: jackpot(['--fail-on-warnings'], suite=None, nonZeroIsFatal=True)
    if jdk.javaCompliance < '9':
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
    'testgraal' : [testgraal, ''],
})

"""
Merges META-INF/truffle/language and META-INF/truffle/instrument files.
This code is tightly coupled with the file format generated by
LanguageRegistrationProcessor and InstrumentRegistrationProcessor.
"""
class TruffleArchiveParticipant:
    PROPERTY_RE = re.compile(r'(language\d+|instrument\d+)(\..+)')

    def _truffle_metainf_file(self, arcname):
        if arcname == 'META-INF/truffle/language':
            return 'language'
        if arcname == 'META-INF/truffle/instrument':
            return 'instrument'
        return None

    def __opened__(self, arc, srcArc, services):
        self.settings = {}
        self.arc = arc

    def __add__(self, arcname, contents):
        metainfFile = self._truffle_metainf_file(arcname)
        if metainfFile:
            propertyRe = TruffleArchiveParticipant.PROPERTY_RE
            properties = {}
            for line in contents.strip().split('\n'):
                if not line.startswith('#'):
                    m = propertyRe.match(line)
                    assert m, 'line in ' + arcname + ' does not match ' + propertyRe.pattern + ': ' + line
                    enum = m.group(1)
                    prop = m.group(2)
                    properties.setdefault(enum, []).append(prop)

            self.settings.setdefault(metainfFile, []).append(properties)
            return True
        return False

    def __addsrc__(self, arcname, contents):
        return False

    def __closing__(self):
        for metainfFile, propertiesList in self.settings.iteritems():
            arcname = 'META-INF/truffle/' + metainfFile
            lines = []
            counter = 1
            for properties in propertiesList:
                for enum in sorted(properties.viewkeys()):
                    assert enum.startswith(metainfFile)
                    newEnum = metainfFile + str(counter)
                    counter += 1
                    for prop in properties[enum]:
                        lines.append(newEnum + prop)

            content = os.linesep.join(lines)
            self.arc.zf.writestr(arcname, content + os.linesep)

def mx_post_parse_cmd_line(opts):

    def _uses_truffle_dsl_processor(dist):
        for dep in dist.deps:
            if dep.name.startswith('TRUFFLE_DSL_PROCESSOR'):
                return True
        truffle_dsl_processors = set()
        def visit(dep, edge):
            if dep is not dist and dep.isJavaProject():
                for ap in dep.annotation_processors():
                    if ap.name.startswith('TRUFFLE_DSL_PROCESSOR'):
                        truffle_dsl_processors.add(ap)
        dist.walk_deps(visit=visit)
        return len(truffle_dsl_processors) != 0

    for d in mx.dependencies():
        if d.isJARDistribution():
            if _uses_truffle_dsl_processor(d):
                d.set_archiveparticipant(TruffleArchiveParticipant())
