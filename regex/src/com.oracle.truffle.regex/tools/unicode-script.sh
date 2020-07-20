#!/usr/bin/env bash
#
# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


# This script takes the CaseFolding.txt and UnicodeData.txt files of the Unicode
# character database and extracts from them the files UnicodeFoldTable.txt and
# NonUnicodeFoldTable.txt. These files contain definitions of the Canonicalize
# abstract function used in the ECMAScript spec to define case folding in
# regular expressions. UnicodeFoldTable.txt contains the definition of case
# folding for when the Unicode ('u') flag is present and NonUnicodeFoldTable.txt
# contains the definition of case folding for when the Unicode flag is missing.
# These two files are then picked up by the generate_case_fold_table.clj script
# which produces Java code that can be put into the CaseFoldTable class in
# TRegex.

# We produce the table for the Canonicalize abstract function when the Unicode
# flag is present. The function is based on the contents of CodeFolding.txt. We
# remove any comments and empty lines from the file. We also remove items
# belonging from the full (F) and Turkic (T) mapping and only keep the simple
# (S) and common (C) ones.
cat dat/CaseFolding.txt \
    | sed -e '/^#/d' \
          -e '/^$/d' \
          -e '/; [FT]; /d' \
          -e 's/; /;/g' \
    | cut -d\; -f1,3 \
    > dat/UnicodeFoldTable.txt

# We produce the table for the Canonicalize abstract function when the Unicode
# flag is not present. We use the UnicodeData.txt file and extract the
# Uppercase_Character field. We remove entries which do not have an
# Uppercase_Character mapping and entries which map from non-ASCII
# code points (>= 128) to ASCII code points (< 128), as per the ECMAScript spec.
cat dat/UnicodeData.txt \
    | cut -d\; -f1,13 \
    | sed -e '/;$/d' \
          -e '/^\(00[8-F][0-9A-F]\|0[^0][0-9A-F]\+\|[^0][0-9A-F]\+\);00[0-7][0-9A-F]$/d' \
    > dat/NonUnicodeFoldTable.txt

# In Python's case insensitive regular expressions, characters are considered
# equivalent if they have the same Lowercase mapping. However, in some cases
# concerning character classes, Python also tries to match character by
# considering their Uppercase mapping. In recent revisions of CPython 3, this is
# supplemented by an explicit list of equivalence classes of lowercase
# characters which are to be considered equal since they have the same Uppercase
# mapping.

# One approach to model this would be to generate the case fold table from the
# equivalences given by the Lowercase mappings and the special list of
# exceptions used in CPython. However, this might not account for some of the
# matches due to the use of Uppercase matching in ranges of characters in
# Unicode character classes. By using both the Uppercase and Lowercase mappings,
# we arrive at a larger equivalence relation, but one that might be more in the
# spirit of what CPython is trying to model.

# We make characters equivalent to their simple Uppercase and Lowercase
# mappings. We filter out the codepoint and the three character mappings, remove
# any empty fields by collapsing neighboring or terminating semicolons and
# finally removing any lines consisting of a single codepoint (the case when a
# character has cased mappings).
cat dat/UnicodeData.txt \
    | cut -d\; -f1,13,14 \
    | sed -e 's/;\+/;/g' \
          -e 's/;$//' \
          -e '/^[^;]*$/d' \
    > dat/PythonSimpleCasing.txt

# The Python case folding algorithm also makes use of the extended case mappings
# defined in SpecialCasing.txt. These occur when, e.g., a single character is
# mapped to multiple characters. In this case, Python makes a character
# equivalent to the first character it maps to.
# When processing the file, we strip away the comments and blank lines. Then we
# reduce each codepoint sequence to just its first codepoint. Finally, we drop
# any missing entries (by collapsing neighboring or terminating semicolons).
cat dat/SpecialCasing.txt \
    | sed -e '/^#/d' \
          -e '/^$/d' \
          -e 's/^\([0-9A-F]\+\); \([0-9A-F]*\)[^;]*; \([0-9A-F]*\)[^;]*; \([0-9A-F]*\).*$/\1;\2;\4/g' \
          -e 's/;\+/;/g' \
          -e 's/;$//g' \
    > dat/PythonExtendedCasing.txt

# We produce the Python case fold table by merging the equivalences due to both
# the simple case mappings and the extended case mappings.
cat dat/PythonSimpleCasing.txt dat/PythonExtendedCasing.txt > dat/PythonFoldTable.txt
