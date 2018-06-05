#!/usr/bin/python
#
# Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
# pylint: skip-file

import sys
import re
import json

class SetEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, set):
            return list(obj)
        return json.JSONEncoder.default(self, obj)

with open(sys.argv[1]) as f:
    classes = {}
    for line in f:
        tokens = re.findall(r'(?:[^\s,"()]|"(?:\\.|[^"])*")+', line)
        tokens = [s.strip('"') for s in tokens]
        fun = tokens[0]
        if fun == "DefineClass":
            sys.stderr.write('Warning: DefineClass used for ' + tokens[1] + '\n')
        elif fun == "FindClass":
            clazz = tokens[1]
            classes.setdefault(clazz, {})
        elif fun == "GetMethodID" or fun == "GetStaticMethodID":
            clazz = tokens[1]
            name = tokens[2]
            classes.setdefault(clazz, {}).setdefault("methods", set()).add(name)
        elif fun == "GetFieldID" or fun == "GetStaticFieldID":
            clazz = tokens[1]
            name = tokens[2]
            classes.setdefault(clazz, {}).setdefault("fields", set()).add(name)
    print json.dumps({"classes": classes}, indent=2, cls=SetEncoder, sort_keys=True)
