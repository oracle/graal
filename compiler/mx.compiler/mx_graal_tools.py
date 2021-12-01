#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

from __future__ import print_function

import os
import shutil
import re
import sys
from os.path import join, exists
from argparse import ArgumentParser, REMAINDER

import mx

if sys.version_info[0] < 3:
    _long = long # pylint: disable=undefined-variable
else:
    _long = int

_suite = mx.suite('compiler')

def run_netbeans_app(app_name, env=None, args=None):
    args = [] if args is None else args
    dist = app_name.upper() + '_DIST'
    name = app_name.lower()
    res = mx.library(dist)

    assert res.isPackedResourceLibrary(), name + " should be a PackedResourceLibrary"
    extractPath = res.get_path(resolve=True)

    if mx.get_os() == 'windows':
        executable = join(extractPath, name, 'bin', name + '.exe')
    else:
        executable = join(extractPath, name, 'bin', name)

    if not exists(executable):
        mx.abort(app_name + ' binary does not exist: ' + executable)

    if mx.get_os() != 'windows':
        # Make sure that execution is allowed. The zip file does not always specfiy that correctly
        os.chmod(executable, 0o777)
    launch = [executable]
    if not mx.get_opts().verbose:
        launch.append('-J-Dnetbeans.logger.console=false')
    mx.run(launch+args, env=env)

def igv(args):
    """(obsolete) informs about IGV"""
    mx.warn(
        """IGV (idealgraphvisualizer) is available from
    https://www.oracle.com/technetwork/graalvm/downloads/index.html
Please download the distribution and run
    bin/idealgraphvisualizer
from the GraalVM EE installation.
""")

def c1visualizer(args):
    """run the C1 Compiler Visualizer"""
    v8u40 = mx.VersionSpec("1.8.0_40")
    def _c1vJdkVersionCheck(version):
        return version >= v8u40 and str(version).startswith('1.8.0')
    env = dict(os.environ)
    env['jdkhome'] = mx.get_jdk(_c1vJdkVersionCheck, versionDescription='(1.8 JDK that is >= 1.8.0u40 )', purpose="running C1 Visualizer").home
    run_netbeans_app('C1Visualizer', env, args() if callable(args) else args)

def hsdis(args, copyToDir=None):
    """download the hsdis library and copy it to a specific dir or to the current JDK

    This is needed to support HotSpot's assembly dumping features.
    On amd64 platforms, it downloads the Intel syntax version"""

    parser = ArgumentParser(prog='hsdis')
    args = parser.parse_args(args)

    hsdis_syntax = mx.get_env('HSDIS_SYNTAX')
    if hsdis_syntax:
        mx.warn("The 'hsdis' function ignores the value of the 'HSDIS_SYNTAX' environment variable: " + hsdis_syntax)

    hsdis_lib_name = 'HSDIS'
    hsdis_lib = mx.library(hsdis_lib_name)

    if hsdis_lib.optional:
        mx.abort('hsdis is not supported on this platform or architecture')

    hsdis_lib_path = hsdis_lib.get_path(resolve=True)
    hsdis_lib_files = os.listdir(hsdis_lib_path)
    if len(hsdis_lib_files) != 1:
        mx.abort("hsdis library '{}' does not contain a single file: {}".format(hsdis_lib_name, hsdis_lib_files))
    hsdis_lib_file = join(hsdis_lib_path, hsdis_lib_files[0])

    overwrite = True
    if copyToDir is None:
        # Try install hsdis into JAVA_HOME
        overwrite = False
        jdk = mx.get_jdk()
        base = jdk.home
        if exists(join(base, 'jre')):
            base = join(base, 'jre')
        if mx.get_os() == 'darwin':
            copyToDir = join(base, 'lib')
        elif mx.get_os() == 'windows':
            copyToDir = join(base, 'bin')
        else:
            if jdk.javaCompliance >= '11':
                copyToDir = join(base, 'lib')
            else:
                copyToDir = join(base, 'lib', mx.get_arch())

    if exists(copyToDir):
        dest = join(copyToDir, mx.add_lib_suffix('hsdis-' + mx.get_arch()))
        if exists(dest) and not overwrite:
            import filecmp
            # Only issue warning if existing lib is different
            if filecmp.cmp(hsdis_lib_file, dest) is False:
                mx.warn('Not overwriting existing {} with {}'.format(dest, hsdis_lib_file))
        else:
            try:
                shutil.copy(hsdis_lib_file, dest)
                mx.log('Copied {} to {}'.format(hsdis_lib_file, dest))
            except IOError as e:
                mx.warn('Could not copy {} to {}: {}'.format(hsdis_lib_file, dest, str(e)))

def hcfdis(args, cp=None):
    """disassemble HexCodeFiles embedded in text files

    Run a tool over the input files to convert all embedded HexCodeFiles
    to a disassembled format."""

    parser = ArgumentParser(prog='mx hcfdis')
    parser.add_argument('-m', '--map', help='address to symbol map applied to disassembler output')
    parser.add_argument('files', nargs=REMAINDER, metavar='files...')

    args = parser.parse_args(args)

    path = cp or mx.library('HCFDIS').get_path(resolve=True)
    mx.run_java(['-cp', path, 'com.oracle.max.hcfdis.HexCodeFileDis'] + args.files)

    if args.map is not None:
        addressRE = re.compile(r'0[xX]([A-Fa-f0-9]+)')
        with open(args.map) as fp:
            lines = fp.read().splitlines()
        symbols = dict()
        for l in lines:
            addressAndSymbol = l.split(' ', 1)
            if len(addressAndSymbol) == 2:
                address, symbol = addressAndSymbol
                if address.startswith('0x'):
                    address = _long(address, 16)
                    symbols[address] = symbol
        for f in args.files:
            with open(f) as fp:
                lines = fp.read().splitlines()
            updated = False
            for i in range(0, len(lines)):
                l = lines[i]
                for m in addressRE.finditer(l):
                    sval = m.group(0)
                    val = _long(sval, 16)
                    sym = symbols.get(val)
                    if sym:
                        l = l.replace(sval, sym)
                        updated = True
                        lines[i] = l
            if updated:
                mx.log('updating ' + f)
                with open('new_' + f, "w") as fp:
                    for l in lines:
                        print(l, file=fp)

def jol(args):
    """Java Object Layout"""
    joljar = mx.library('JOL_CLI').get_path(resolve=True)

    commands = ['estimates', 'externals', 'footprint', 'heapdump', 'heapdumpstats', 'idealpack', 'internals', 'shapes', 'string-compress', 'help']
    command = 'internals'
    if len(args) == 0:
        command = 'help'
    elif args[0] in commands:
        command, args = args[0], args[1:]

    # classpath operations
    if command in ['estimates', 'externals', 'footprint', 'internals']:
        candidates = mx.findclass(args, logToConsole=False, matcher=lambda s, classname: s == classname or classname.endswith('.' + s) or classname.endswith('$' + s))
        if len(candidates) > 0:
            args = mx.select_items(sorted(candidates))
        if len(args) > 0:
            args = ['-cp', mx.classpath(jdk=mx.get_jdk())] + args

    mx.run_java(['-javaagent:' + joljar, '-cp', joljar, 'org.openjdk.jol.Main', command] + args)

mx.update_commands(_suite, {
    'c1visualizer' : [c1visualizer, ''],
    'hsdis': [hsdis, ''],
    'hcfdis': [hcfdis, ''],
    'igv' : [igv, ''],
    'jol' : [jol, ''],
})
