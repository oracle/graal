#!/usr/bin/env bash

# ------------------------------------------------------------------------------
# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
# ------------------------------------------------------------------------------

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
cat CaseFolding.txt \
    | sed -e '/^#/d' \
          -e '/^$/d' \
          -e '/; [FT]; /d' \
          -e 's/; /;/g' \
    | cut -d\; -f1,3 \
    > UnicodeFoldTable.txt

# We produce the table for the Canonicalize abstract function when the Unicode
# flag is not present. We use the UnicodeData.txt file and extract the
# Uppercase_Character field. We remove entries which do not have an
# Uppercase_Character mapping and entries which map from non-ASCII
# code points (>= 128) to ASCII code points (< 128), as per the ECMAScript spec.
cat UnicodeData.txt \
    | cut -d\; -f1,13 \
    | sed -e '/;$/d' \
          -e '/^\(00[8-F][0-9A-F]\|0[^0][0-9A-F]\+\|[^0][0-9A-F]\+\);00[0-7][0-9A-F]$/d' \
    > NonUnicodeFoldTable.txt

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
cat UnicodeData.txt \
    | cut -d\; -f1,13,14 \
    | sed -e 's/;\+/;/g' \
          -e 's/;$//' \
          -e '/^[^;]*$/d' \
    > PythonSimpleCasing.txt

# The Python case folding algorithm also makes use of the extended case mappings
# defined in SpecialCasing.txt. These occur when, e.g., a single character is
# mapped to multiple characters. In this case, Python makes a character
# equivalent to the first character it maps to.
# When processing the file, we strip away the comments and blank lines. Then we
# reduce each codepoint sequence to just its first codepoint. Finally, we drop
# any missing entries (by collapsing neighboring or terminating semicolons).
cat SpecialCasing.txt \
    | sed -e '/^#/d' \
          -e '/^$/d' \
          -e 's/^\([0-9A-F]\+\); \([0-9A-F]*\)[^;]*; \([0-9A-F]*\)[^;]*; \([0-9A-F]*\).*$/\1;\2;\4/g' \
          -e 's/;\+/;/g' \
          -e 's/;$//g' \
    > PythonExtendedCasing.txt

# We produce the Python case fold table by merging the equivalences due to both
# the simple case mappings and the extended case mappings.
cat PythonSimpleCasing.txt PythonExtendedCasing.txt > PythonFoldTable.txt
