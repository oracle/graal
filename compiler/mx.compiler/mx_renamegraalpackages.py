#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
from os.path import join, exists

import mx
from mx_updategraalinopenjdk import package_renamings, rename_packages

def renamegraalpackages(args):
    """ rename Graal packages to match names in OpenJDK"""
    parser = ArgumentParser(prog='mx renamegraalpackages')
    parser.add_argument('version', type=int, help='Java version of the OpenJDK')

    args = parser.parse_args(args)

    package_suffixes = {
        'org.graalvm.collections' : ['', '.test'],
        'org.graalvm.nativeimage' : ['', '.test'],
        'org.graalvm.options'     : [''],
        'org.graalvm.word'        : [''],
        'org.graalvm.libgraal'    : ['', '.jdk8', '.jdk11', '.jdk13', '.jni', '.jni.annotation', '.jni.processor']
    }
    vc_dir = mx.primary_suite().vc_dir

    # rename packages
    def rename_one(filepath, old, new):
        with open(filepath) as fp:
            contents = fp.read()
        new_contents = contents
        new_contents = new_contents.replace(old, new)
        if new_contents != contents:
            with open(filepath, 'w') as fp:
                fp.write(new_contents)

    for proj_dir in [join(vc_dir, x) for x in os.listdir(vc_dir) if exists(join(vc_dir, x, 'mx.' + x, 'suite.py'))]:
        for dirpath, _, filenames in os.walk(proj_dir):
            if args.version >= 15 and "sparc" in dirpath:
                # Remove SPARC port for JDK 15
                shutil.rmtree(dirpath)
            else:
                for filename in filenames:
                    if filename.endswith('.java') or filename == 'suite.py' or filename == 'generate_unicode_properties.py' or filename == "mx_substratevm.py" or "META-INF" in dirpath:
                        filepath = join(dirpath, filename)
                        rename_packages(filepath)

        # move directories according to new package name
        for old_name, new_name in package_renamings.items():
            for sfx in package_suffixes[old_name]:
                old_path = join(proj_dir, 'src', old_name + sfx)
                old_dir = join(old_path, 'src', old_name.replace('.', os.sep))
                if exists(old_dir):
                    new_name_sfx = new_name + sfx
                    new_path = join(proj_dir, 'src', new_name_sfx)
                    if exists(new_path):
                        shutil.rmtree(new_path)
                    new_dir = join(new_path, 'src', new_name.replace('.', os.sep))
                    os.makedirs(new_dir)
                    mx.log("Move files to new dir: " + new_dir)
                    for f in os.listdir(old_dir):
                        shutil.move(os.path.join(old_dir, f), new_dir)
                    old_meta = join(old_path, 'src', 'META-INF')
                    if exists(old_meta):
                        new_meta = join(new_path, 'src', 'META-INF')
                        shutil.move(old_meta, new_meta)
                    shutil.rmtree(old_path)

    # rename in additional place
    package = 'com.oracle.svm.graal.hotspot.libgraal'
    filepath = join(vc_dir, 'substratevm', 'src', package, 'src', package.replace('.', os.sep), 'LibGraalEntryPoints.java')
    old = 'Java_org_graalvm_libgraal'
    new = 'Java_jdk_internal_vm_compiler_libgraal'
    rename_one(filepath, old, new)
