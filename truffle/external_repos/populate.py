#
# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
#

from shutil import copytree

# Populate simplelanguage
copytree("../src/com.oracle.truffle.sl/src/", "simplelanguage/language/src/main/java/")
copytree("../src/com.oracle.truffle.sl.test/src/com/", "simplelanguage/language/src/test/java/com")
copytree("../src/com.oracle.truffle.sl.test/src/tests", "simplelanguage/language/tests")
copytree("../src/com.oracle.truffle.sl.launcher/src/com/", "simplelanguage/launcher/src/main/java/com")
copytree("../src/com.oracle.truffle.sl.tck/src", "simplelanguage/tck/src")

# Create simplelanguage module-info
# GR-46339 Mx supports module-info in the project sources.
module_info = """
module org.graalvm.sl {
  requires java.base;
  requires java.logging;
  requires jdk.unsupported;
  requires org.antlr.antlr4.runtime;
  requires org.graalvm.polyglot;
  requires org.graalvm.truffle;
  provides  com.oracle.truffle.api.provider.TruffleLanguageProvider with
    com.oracle.truffle.sl.SLLanguageProvider;
}
"""
with open('simplelanguage/language/src/main/java/module-info.java', 'w') as f:
    f.write(module_info)

# Populate simpletool
copytree("../src/com.oracle.truffle.st/src/com/", "simpletool/src/main/java/com")
copytree("../src/com.oracle.truffle.st.test/src/com/", "simpletool/src/test/java/com")
