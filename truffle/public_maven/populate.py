#!/usr/bin/env python
# -*- coding: utf-8 -*-

from distutils.dir_util import copy_tree

# Populate simplelanguage
copy_tree("../src/com.oracle.truffle.sl/src/", "simplelanguage/language/src/main/java/")
copy_tree("../src/com.oracle.truffle.sl.test/src/com/", "simplelanguage/language/src/test/java/com")
copy_tree("../src/com.oracle.truffle.sl.test/src/tests", "simplelanguage/language/tests")
copy_tree("../src/com.oracle.truffle.sl.launcher/src/com/", "simplelanguage/launcher/src/main/java/com")
copy_tree("../src/com.oracle.truffle.sl.tck/src", "simplelanguage/tck/src")

# Populate simpletool
copy_tree("../src/com.oracle.truffle.st/src/com/", "simpletool/src/main/java/com")
copy_tree("../src/com.oracle.truffle.st.test/src/com/", "simpletool/src/test/java/com")
