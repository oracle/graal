#!/usr/bin/env bash
#
# Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

set -e

if [[ $(pwd) != *graal/regex/src/com.oracle.truffle.regex/tools ]]
then
    echo "This script should be run in graal/regex/src/com.oracle.truffle.regex/tools!"
    exit 1
fi

UNICODE_VERSION=15.1.0
EMOJI_VERSION=15.1

mkdir -p ./dat

wget https://www.unicode.org/Public/${UNICODE_VERSION}/ucd/PropertyAliases.txt -O dat/PropertyAliases.txt
wget https://www.unicode.org/Public/${UNICODE_VERSION}/ucd/PropertyValueAliases.txt -O dat/PropertyValueAliases.txt
wget https://www.unicode.org/Public/${UNICODE_VERSION}/ucd/NameAliases.txt -O dat/NameAliases.txt
wget https://www.unicode.org/Public/${UNICODE_VERSION}/ucd/emoji/emoji-data.txt -O dat/emoji-data.txt
wget https://www.unicode.org/Public/${UNICODE_VERSION}/ucdxml/ucd.nounihan.flat.zip -O dat/ucd.nounihan.flat.zip
wget https://www.unicode.org/Public/emoji/${EMOJI_VERSION}/emoji-sequences.txt -O dat/emoji-sequences.txt
wget https://www.unicode.org/Public/emoji/${EMOJI_VERSION}/emoji-zwj-sequences.txt -O dat/emoji-zwj-sequences.txt

unzip -d dat dat/ucd.nounihan.flat.zip

./generate_unicode_properties.py > ../src/com/oracle/truffle/regex/charset/UnicodePropertyData.java

./generate_name_alias_table.py > ../src/com/oracle/truffle/regex/chardata/UnicodeCharacterAliases.java

rm -r ./dat

mx build
mx java -cp `mx paths regex:TREGEX`:`mx paths truffle:TRUFFLE_API`:`mx paths sdk:COLLECTIONS` com.oracle.truffle.regex.charset.UnicodeGeneralCategoriesGenerator > ../src/com/oracle/truffle/regex/charset/UnicodeGeneralCategories.java

mx eclipseformat --primary || true
