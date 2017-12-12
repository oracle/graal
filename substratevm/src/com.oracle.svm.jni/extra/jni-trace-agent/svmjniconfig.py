#!/usr/bin/python
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
