# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

def _f(): pass
FunctionType = type(_f)
descriptor = type(FunctionType.__code__)


def make_named_tuple_class(name, fields):
    class named_tuple(tuple):
        __name__ = name
        n_sequence_fields = len(fields)
        fields = fields

        def __str__(self):
            return self.__repr__()

        def __repr__(self):
            sb = [name, "("]
            for f in fields:
                sb.append(f)
                sb.append("=")
                sb.append(repr(getattr(self, f)))
                sb.append(", ")
            sb.pop()
            sb.append(")")
            return "".join(sb)

    def _define_named_tuple_methods():
        for i, name in enumerate(fields):
            def make_func(i):
                def func(self):
                    return self[i]
                return func
            setattr(named_tuple, name, descriptor(fget=make_func(i), name=name, owner=named_tuple))


    _define_named_tuple_methods()
    return named_tuple


class SimpleNamespace(object):
    def __init__(self, **kwargs):
        object.__setattr__(self, "__ns__", kwargs)

    def __delattr__(self, name):
        object.__getattribute__(self, "__ns__").__delitem__(name)

    def __getattr__(self, name):
        return object.__getattribute__(self, "__ns__")[name]

    def __setattr__(self, name, value):
        object.__getattribute__(self, "__ns__")[name] = value

    def __repr__(self):
        sb = []
        ns = object.__getattribute__(self, "__ns__")
        for k,v in ns.items():
            sb.append("%s='%s'" % (k,v))
        return "namespace(%s)" % ", ".join(sb)
