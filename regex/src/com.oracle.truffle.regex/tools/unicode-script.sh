#!/usr/bin/env bash

# ------------------------------------------------------------------------------
# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
    | cut -d\; -f1,3 \
    > UnicodeFoldTable.txt

# We produce the table for the Canonicalize abstract function when the Unicode
# flag is not present. We use the UnicodeData.txt file and extract the
# Uppercase_Character field. We remove entries which do not have an
# Uppercase_Character mapping, add spaces before the semicolon delimiters and
# finally remove entries which map from non-ASCII code points (>= 128) to ASCII
# code points (< 128) as per the ECMAScript spec.
cat UnicodeData.txt \
    | cut -d\; -f1,13 \
    | sed -e '/;$/d' \
          -e 's/;/; /' \
          -e '/^\(00[8-F][0-9A-F]\|0[^0][0-9A-F]\+\|[^0][0-9A-F]\+\); 00[0-7][0-9A-F]$/d' \
    > NonUnicodeFoldTable.txt
