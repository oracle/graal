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

import os, shutil, zipfile, re
from os.path import join, exists
from argparse import ArgumentParser, REMAINDER

import mx

_suite = mx.suite('graal-core')

def _run_netbeans_app(app_name, env=None, args=None):
    args = [] if args is None else args
    dist = app_name.upper() + '_DIST'
    name = app_name.lower()
    extractPath = join(_suite.get_output_root())
    if mx.get_os() == 'windows':
        executable = join(extractPath, name, 'bin', name + '.exe')
    else:
        executable = join(extractPath, name, 'bin', name)

    # Check whether the current installation is up-to-date
    if exists(executable) and not exists(mx.library(dist).get_path(resolve=False)):
        mx.log('Updating ' + app_name)
        shutil.rmtree(join(extractPath, name))

    archive = mx.library(dist).get_path(resolve=True)

    if not exists(executable):
        zf = zipfile.ZipFile(archive, 'r')
        zf.extractall(extractPath)

    if not exists(executable):
        mx.abort(app_name + ' binary does not exist: ' + executable)

    if mx.get_os() != 'windows':
        # Make sure that execution is allowed. The zip file does not always specfiy that correctly
        os.chmod(executable, 0777)
    mx.run([executable]+args, env=env)

def _igvJdk():
    v8u20 = mx.VersionSpec("1.8.0_20")
    v8u40 = mx.VersionSpec("1.8.0_40")
    v9 = mx.VersionSpec("1.9")
    def _igvJdkVersionCheck(version):
        # Remove v9 check once GR-3187 is resolved
        return version < v9 and (version < v8u20 or version >= v8u40)
    return mx.get_jdk(_igvJdkVersionCheck, versionDescription='>= 1.8 and < 1.8.0u20 or >= 1.8.0u40', purpose="running IGV").home

def igv(args):
    """run the Ideal Graph Visualizer"""
    env = dict(os.environ)
    # make the jar for Batik 1.7 available.
    env['IGV_BATIK_JAR'] = mx.library('BATIK').get_path(True)
    env['jdkhome'] = _igvJdk()
    _run_netbeans_app('IdealGraphVisualizer', env, args)

def c1visualizer(args):
    """run the C1 Compiler Visualizer"""
    env = dict(os.environ)
    env['jdkhome'] = _igvJdk()
    _run_netbeans_app('C1Visualizer', env, args)

def hsdis(args, copyToDir=None):
    """download the hsdis library

    This is needed to support HotSpot's assembly dumping features.
    By default it downloads the Intel syntax version, use the 'att' argument to install AT&T syntax."""
    flavor = None
    if mx.get_arch() == "amd64":
        flavor = mx.get_env('HSDIS_SYNTAX')
        if flavor is None:
            flavor = 'intel'
        if 'att' in args:
            flavor = 'att'

    libpattern = mx.add_lib_suffix('hsdis-' + mx.get_arch() + '-' + mx.get_os() + '-%s')

    sha1s = {
        'att/hsdis-amd64-windows-%s.dll' : 'bcbd535a9568b5075ab41e96205e26a2bac64f72',
        'att/hsdis-amd64-linux-%s.so' : '36a0b8e30fc370727920cc089f104bfb9cd508a0',
        'att/hsdis-amd64-darwin-%s.dylib' : 'c1865e9a58ca773fdc1c5eea0a4dfda213420ffb',
        'intel/hsdis-amd64-windows-%s.dll' : '6a388372cdd5fe905c1a26ced614334e405d1f30',
        'intel/hsdis-amd64-linux-%s.so' : '0d031013db9a80d6c88330c42c983fbfa7053193',
        'intel/hsdis-amd64-darwin-%s.dylib' : '67f6d23cbebd8998450a88b5bef362171f66f11a',
        'hsdis-sparcv9-solaris-%s.so': '970640a9af0bd63641f9063c11275b371a59ee60',
        'hsdis-sparcv9-linux-%s.so': '0c375986d727651dee1819308fbbc0de4927d5d9',
    }

    if flavor:
        flavoredLib = flavor + "/" + libpattern
    else:
        flavoredLib = libpattern
    if flavoredLib not in sha1s:
        mx.warn("hsdis with flavor '{}' not supported on this platform or architecture".format(flavor))
        return

    sha1 = sha1s[flavoredLib]
    lib = flavoredLib % (sha1)
    path = join(_suite.get_output_root(), lib)
    if not exists(path):
        sha1path = path + '.sha1'
        mx.download_file_with_sha1('hsdis', path, ['https://lafo.ssw.uni-linz.ac.at/pub/hsdis/' + lib], sha1, sha1path, True, True, sources=False)

    overwrite = True
    if copyToDir is None:
        # Try install hsdis into JAVA_HOME
        overwrite = False
        base = mx.get_jdk().home
        if exists(join(base, 'jre')):
            copyToDir = join(base, 'jre', 'lib')
        else:
            copyToDir = join(base, 'lib')

    if exists(copyToDir):
        dest = join(copyToDir, mx.add_lib_suffix('hsdis-' + mx.get_arch()))
        if exists(dest) and not overwrite:
            import filecmp
            # Only issue warning if existing lib is different
            if filecmp.cmp(path, dest) == False:
                mx.warn('Not overwriting existing {} with {}'.format(dest, path))
        else:
            try:
                shutil.copy(path, dest)
                mx.log('Copied {} to {}'.format(path, dest))
            except IOError as e:
                mx.warn('Could not copy {} to {}: {}'.format(path, dest, str(e)))

def hcfdis(args):
    """disassemble HexCodeFiles embedded in text files

    Run a tool over the input files to convert all embedded HexCodeFiles
    to a disassembled format."""

    parser = ArgumentParser(prog='mx hcfdis')
    parser.add_argument('-m', '--map', help='address to symbol map applied to disassembler output')
    parser.add_argument('files', nargs=REMAINDER, metavar='files...')

    args = parser.parse_args(args)

    path = mx.library('HCFDIS').get_path(resolve=True)
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
                    address = long(address, 16)
                    symbols[address] = symbol
        for f in args.files:
            with open(f) as fp:
                lines = fp.read().splitlines()
            updated = False
            for i in range(0, len(lines)):
                l = lines[i]
                for m in addressRE.finditer(l):
                    sval = m.group(0)
                    val = long(sval, 16)
                    sym = symbols.get(val)
                    if sym:
                        l = l.replace(sval, sym)
                        updated = True
                        lines[i] = l
            if updated:
                mx.log('updating ' + f)
                with open('new_' + f, "w") as fp:
                    for l in lines:
                        print >> fp, l

def jol(args):
    """Java Object Layout"""
    joljar = mx.library('JOL_INTERNALS').get_path(resolve=True)
    candidates = mx.findclass(args, logToConsole=False, matcher=lambda s, classname: s == classname or classname.endswith('.' + s) or classname.endswith('$' + s))

    if len(candidates) > 0:
        candidates = mx.select_items(sorted(candidates))
    else:
        # mx.findclass can be mistaken, don't give up yet
        candidates = args

    mx.run_java(['-javaagent:' + joljar, '-cp', os.pathsep.join([mx.classpath(jdk=mx.get_jdk()), joljar]), "org.openjdk.jol.MainObjectInternals"] + candidates)

mx.update_commands(_suite, {
    'c1visualizer' : [c1visualizer, ''],
    'hsdis': [hsdis, '[att]'],
    'hcfdis': [hcfdis, ''],
    'igv' : [igv, ''],
    'jol' : [jol, ''],
})
