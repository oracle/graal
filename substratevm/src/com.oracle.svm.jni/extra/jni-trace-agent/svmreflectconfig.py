#!/usr/bin/python
#
# Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

java_type_char_to_name = {
  'Z': 'boolean',
  'B': 'byte',
  'C': 'char',
  'S': 'short',
  'I': 'int',
  'J': 'long',
  'F': 'float',
  'D': 'double',
}

def format_java_type(s):
  i = 0
  while i < len(s) and s[i] == '[':
    i += 1
  dims = i
  if dims > 0:
    fmt = "[]" * dims
    if s[i] == 'L':
      return s[i+1:len(s)-1] + "[]" * dims
    else:
      return java_type_char_to_name[s[i]] + "[]" * dims
  return s

def rebuild_methods(fields):
  result = []
  for method_sig, attrs in fields.items():
    new_attrs = attrs.copy()
    new_attrs["name"] = method_sig[0]
    new_attrs["parameterTypes"] = [format_java_type(t) for t in method_sig[1:]]
    result.append(new_attrs)
  return result

def rebuild_fields(fields):
  result = []
  for field_name, attrs in fields.items():
    new_attrs = attrs.copy()
    new_attrs["name"] = field_name
    result.append(new_attrs)
  return result

def rebuild_for_output(classes):
  result = {}
  for class_name, attrs in classes.items():
    new_attrs = attrs.copy()
    if "fields" in attrs:
      new_attrs["fields"] = rebuild_fields(attrs["fields"])
    if "methods" in attrs:
      new_attrs["methods"] = rebuild_methods(attrs["methods"])
    result[format_java_type(class_name)] = new_attrs
  return result

with open(sys.argv[1]) as f:
    classes = {}
    for line in f:
        if not line.startswith("reflect:"):
            continue
        line = line[len("reflect:"):].rstrip('\n')
        tokens = [s.strip('"') for s in re.findall(r'\A:|\w+|"(?:[^"]|\\")*"', line)]
        clazz, fun, args = tokens[0], tokens[1], tokens[2:]
        if fun == "forName":
            className = args[0]
            classes.setdefault(className, {})
        elif fun == "getField" or fun == "getDeclaredField":
            name = args[0]
            classes.setdefault(clazz, {}).setdefault("fields", {}).setdefault(name, {})
        elif fun == "getFields":
            classes.setdefault(clazz, {}).setdefault("allPublicFields", True)
        elif fun == "getDeclaredFields":
            classes.setdefault(clazz, {}).setdefault("allDeclaredFields", True)
        elif fun == "getMethod" or fun == "getDeclaredMethod":
            signature = tuple(args)
            classes.setdefault(clazz, {}).setdefault("methods", {}).setdefault(signature, {})
        elif fun == "getMethods":
            classes.setdefault(clazz, {}).setdefault("allPublicMethods", True)
        elif fun == "getDeclaredMethods":
            classes.setdefault(clazz, {}).setdefault("allDeclaredMethods", True)
    print json.dumps(rebuild_for_output(classes), indent=2, sort_keys=True)
