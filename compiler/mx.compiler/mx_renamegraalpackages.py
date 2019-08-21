#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import shutil
from argparse import ArgumentParser
from os.path import join, exists, dirname

import mx
import mx_compiler

def renamegraalpackages(args):
    """ rename Graal packages to match names in OpenJDK"""
    parser = ArgumentParser(prog='mx updategraalinopenjdk')

    args = parser.parse_args(args)

    # Packages in Graal that have different names in OpenJDK so that the original packages can be deployed
    # as it on the class path and not clash with packages in the jdk.internal.vm.compiler module.
    package_renamings = {
        'org.graalvm.collections' : 'jdk.internal.vm.compiler.collections',
        'org.graalvm.word'        : 'jdk.internal.vm.compiler.word',
        'org.graalvm.libgraal'    : 'jdk.internal.vm.compiler.libgraal'
    }

    package_suffixes = {
        'org.graalvm.collections' : ['', '.test'],
        'org.graalvm.word'        : [''],
        'org.graalvm.libgraal'    : ['', '.jdk8', '.jdk11', '.jdk13']
    }
    vc_dir = mx.primary_suite().vc_dir
    # rename packages
    for proj_dir in [join(vc_dir, x) for x in os.listdir(vc_dir) if exists(join(vc_dir, x, 'mx.' + x, 'suite.py'))]:
        for dirpath, _, filenames in os.walk(proj_dir):
            for filename in filenames:
                if filename.endswith('.java') or filename == 'suite.py' or filename == 'generate_unicode_properties.py':
                    filepath = join(dirpath, filename)
                    with open(filepath) as fp:
                        contents = fp.read()
                    new_contents = contents
                    for old_name, new_name in package_renamings.items():
                        new_contents = new_contents.replace(old_name, new_name)
                    if new_contents != contents:
                        with open(filepath, 'w') as fp:
                            fp.write(new_contents)
        # move directories accoding to new package name
        for old_name, new_name in package_renamings.items():
            for sfx in package_suffixes[old_name]:
                old_dir = join(proj_dir, 'src', old_name + sfx, 'src', old_name.replace('.', os.sep))
                if exists(old_dir):
                    if exists(join(proj_dir, 'src', new_name + sfx)):
                        shutil.rmtree(join(proj_dir, 'src', new_name + sfx))
                    new_dir = join(proj_dir, 'src', new_name + sfx, 'src', new_name.replace('.', os.sep))
                    os.makedirs(new_dir)
                    for file in os.listdir(old_dir):
                        shutil.move(os.path.join(old_dir, file), new_dir)
                    shutil.rmtree(join(proj_dir, 'src', old_name + sfx))

    # rename in additional place
    package = 'com.oracle.svm.graal.hotspot.libgraal'
    filepath = join(vc_dir, 'substratevm', 'src', package, 'src', package.replace('.', os.sep), 'LibGraalEntryPoints.java')
    with open(filepath) as fp:
        contents = fp.read()
    new_contents = contents
    old_name = 'Java_org_graalvm_libgraal'
    new_name = 'Java_jdk_internal_vm_compiler_libgraal'
    new_contents = new_contents.replace(old_name, new_name)
    if new_contents != contents:
        with open(filepath, 'w') as fp:
            fp.write(new_contents)

