#!/usr/bin/env python3
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


# This script generates the contents of the
# graal/com.oracle.truffle.regex/src/com/oracle/truffle/regex/tregex/parser/UnicodeCharacterPropertyData.java
# source file in the TRegex code base. It collects data from the Unicode database
# necessary for the support of Unicode property escapes in regular expressions.
# In order to run this script, you need to make sure to have the following Unicode
# data files in a folder called "dat" in your working directory:
#   - ucd.nounihan.flat.xml (This is part of the Unicode in XML data files (UAX #42))
#   - PropertyAliases.txt
#   - PropertyValueAliases.txt
#   - emoji-data.txt (This is part of the Emoji data included in Unicode TR51)
#   - emoji-sequences.txt (This is part of the Emoji data included in Unicode TR51)
#   - emoji-zwj-sequences.txt (This is part of the Emoji data included in Unicode TR51)

import re
import xml.etree.ElementTree as ET
import os

os.chdir('dat')

# The abbreviated names of binary character properties required by ECMAScript.
# All of these can be found in the Unicode Character Database
# (ucd.nounihan.flat.xml).
bin_props_xml = [
    'Alpha',   # Alphabetic
    'AHex',    # ASCII_Hex_Digit
    'Bidi_C',  # Bidi_Control
    'Bidi_M',  # Bidi_Mirrored
    'CI',      # Case_Ignorable
    'Cased',   # Cased
    'CWCF',    # Changes_When_Casefolded
    'CWCM',    # Changes_When_Casemapped
    'CWL',     # Changes_When_Lowercased
    'CWKCF',   # Changes_When_NFKC_Casefolded
    'CWT',     # Changes_When_Titlecased
    'CWU',     # Changes_When_Uppercased
    'Dash',    # Dash
    'DI',      # Default_Ignorable_Code_Point
    'Dep',     # Deprecated
    'Dia',     # Diacritic
    'Ext',     # Extender
    'Gr_Base', # Grapheme_Base
    'Gr_Ext',  # Grapheme_Extended
    'Hex',     # Hex_Digit
    'IDC',     # ID_Continue
    'IDS',     # ID_Start
    'Ideo',    # Ideographic
    'IDSB',    # IDS_Binary_Operator
    'IDST',    # IDS_Trinary_Operator
    'Join_C',  # Join_Control
    'LOE',     # Logical_Order_Exception
    'Lower',   # Lowercase
    'Math',    # Math
    'NChar',   # Noncharacter_Code_Point
    'Pat_Syn', # Pattern_Syntax
    'Pat_WS',  # Pattern_White_Space
    'QMark',   # Quotation_Mark
    'Radical', # Radical
    'RI',      # Regional_Indicator
    'STerm',   # Sentence_Terminal
    'SD',      # Soft_Dotted
    'Term',    # Terminal_Punctuation
    'UIdeo',   # Unified_Ideograph
    'Upper',   # Uppercase
    'VS',      # Variation_Selector
    'WSpace',  # White_Space
    'XIDC',    # XID_Continue
    'XIDS'     # XID_Start
]

# The names of binary properties of Emoji codepoints defined in Unicode TR51.
bin_props_emoji_cp = [
    'Emoji',
    'EComp',  # Emoji_Component
    'EBase',  # Emoji_Modifier_Base
    'EMod',   # Emoji_Modifier
    'EPres',  # Emoji_Presentation
    'ExtPict' # Extended_Pictographic
]

# The names of binary properties of Emoji strings defined in Unicode TR51
bin_props_emoji_str = [
    'Basic_Emoji',
    'Emoji_Keycap_Sequence',
    'RGI_Emoji_Flag_Sequence',
    'RGI_Emoji_Tag_Sequence',
    'RGI_Emoji_Modifier_Sequence',
    'RGI_Emoji_ZWJ_Sequence'
]

tracked_props = bin_props_xml + bin_props_emoji_cp + bin_props_emoji_str + ['gc', 'sc', 'scx', 'blk'] + ['RGI_Emoji']

prop_contents = {}
str_prop_contents = {}


def add_elements_to_property(elements, prop_name):
    if prop_name in prop_contents:
        prop_contents[prop_name].extend(elements)
    else:
        prop_contents[prop_name] = elements

def add_elements_to_str_property(elements, prop_name):
    if prop_name in str_prop_contents:
        str_prop_contents[prop_name].extend(elements)
    else:
        str_prop_contents[prop_name] = elements


def add_xml_entry_to_property(entry, prop_name):
    if entry.get('first-cp'):
        first_code_point = int(entry.get('first-cp'), 16)
        last_code_point = int(entry.get('last-cp'), 16)
        add_elements_to_property(list(range(first_code_point, last_code_point + 1)), prop_name)
    else:
        code_point = int(entry.get('cp'), 16)
        add_elements_to_property([code_point], prop_name)


def add_txt_entry_to_property(code_points_str, prop_name):
    if '..' in code_points_str:
        [first_code_point, last_code_point] = [int(cps, 16) for cps in code_points_str.split('..')]
        add_elements_to_property(list(range(first_code_point, last_code_point + 1)), prop_name)
    else:
        code_point = int(code_points_str, 16)
        add_elements_to_property([code_point], prop_name)

def add_txt_entry_to_str_property(code_points_str, prop_name):
    if '..' in code_points_str:
        [first_code_point, last_code_point] = [int(cps, 16) for cps in code_points_str.split('..')]
        add_elements_to_str_property([chr(code_point) for code_point in range(first_code_point, last_code_point + 1)], prop_name)
    else:
        code_points = ''.join([chr(int(cps, 16)) for cps in code_points_str.split(' ')])
        add_elements_to_str_property([code_points], prop_name)

def complement_property(prop):
    return sorted(set(range(0, 0x110000)) - set(prop))

def unicode_file_lines_without_comments(file_name):
    lines = []
    with open(file_name) as unicode_file:
        for line in unicode_file:
            line = re.sub('#.*$', '', line).strip()
            if not line == '':
                lines.append(line)
    return lines


# Parse aliases

prop_aliases = {}

for line in unicode_file_lines_without_comments('PropertyAliases.txt'):
    aliases = [x.strip() for x in line.split(';')]
    short_name = aliases[0]
    all_names = aliases[0:]
    if short_name in tracked_props:
        for name in all_names:
            prop_aliases[name] = short_name

# We also add the names of the properties from TR18 into the alias table.
for name in ['Any', 'ASCII', 'Assigned']:
    prop_aliases[name] = name

# The properties of emoji strings are not listed in PropertyAliases.txt.
for name in bin_props_emoji_str + ['RGI_Emoji']:
    prop_aliases[name] = name

gc_aliases = {}
sc_aliases = {}
blk_aliases = {}

for line in unicode_file_lines_without_comments('PropertyValueAliases.txt'):
    fields = [x.strip() for x in line.split(';')]
    prop_name = fields[0]
    short_value_name = fields[1]
    all_value_names = fields[1:]
    if prop_name == 'gc':
        for value_name in all_value_names:
            gc_aliases[value_name] = short_value_name
    elif prop_name == 'sc':
        for value_name in all_value_names:
            sc_aliases[value_name] = short_value_name
    elif prop_name == 'blk':
        for value_name in all_value_names:
            blk_aliases[value_name] = short_value_name

# Parse XML

tree = ET.parse('ucd.nounihan.flat.xml')
root = tree.getroot()

ns = {
    'unicode': 'http://www.unicode.org/ns/2003/ucd/1.0'
}

for char_elem in root.find('unicode:repertoire', ns):
    for bin_prop_name in bin_props_xml:
        if char_elem.get(bin_prop_name) == 'Y':
            add_xml_entry_to_property(char_elem, bin_prop_name)
    add_xml_entry_to_property(char_elem, 'gc=' + char_elem.get('gc'))
    add_xml_entry_to_property(char_elem, 'sc=' + char_elem.get('sc'))
    add_xml_entry_to_property(char_elem, 'blk=' + char_elem.get('blk'))
    for script in char_elem.get('scx').split():
        add_xml_entry_to_property(char_elem, 'scx=' + script)


# Parse Emoji data

for line in unicode_file_lines_without_comments('emoji-data.txt'):
    [code_points_str, long_prop_name] = [x.strip() for x in line.split(';')]
    short_prop_name = prop_aliases[long_prop_name]
    add_txt_entry_to_property(code_points_str, short_prop_name)


# Parse properties of Emoji strings

for line in unicode_file_lines_without_comments('emoji-sequences.txt') + unicode_file_lines_without_comments('emoji-zwj-sequences.txt'):
    [code_points_str, prop_name, ignored] = [x.strip() for x in line.split(';')]
    add_txt_entry_to_str_property(code_points_str, prop_name)

# Add special properties from Unicode TR18

# The following properties are not defined in the Unicode character database. Their
# definitions are given in Section 1.2.1. of Techinal Report 18
# (http://www.unicode.org/reports/tr18/tr18-19.html#General_Category_Property).
prop_contents['Any'] = range(0, 0x110000)
prop_contents['ASCII'] = range(0, 0x80)
prop_contents['Assigned'] = complement_property(prop_contents['gc=Cn'])


# Generate Java source code
def indent(text, spaces, skipFirst=False):
    def indent_line(line):
        if len(line.strip()) == 0:
            return line
        else:
            return spaces * ' ' + line
    lines = text.splitlines(True)
    if skipFirst:
        indented = ''.join(map(indent_line, lines[1:]))
        return lines[0] + indented
    else:
        indented = ''.join(map(indent_line, lines))
        return indented


def int_to_java_hex_literal(i):
    absolute = '0x%06x' % abs(i)
    if i < 0:
        return '-' + absolute
    else:
        return absolute


def str_to_java_string_literal(s):
    utf16 = s.encode('utf-16-be')
    literal = '"'
    for i in range(0, len(utf16), 2):
        code_unit = utf16[i] * 256 + utf16[i + 1]
        literal = literal + '\\u%04X' % code_unit
    literal = literal + '"'
    return literal


def property_to_java_array_init(prop):
    ranges = []
    range_start = None
    prev_cp = None
    for cp in prop:
        if range_start is None:
            range_start = cp
        elif cp > prev_cp + 1:
            ranges.append((range_start, prev_cp))
            range_start = cp
        prev_cp = cp
    if range_start is not None:
        ranges.append((range_start, prev_cp))

    encoding = []
    for (range_start, range_end) in ranges:
        encoding.append(range_start)
        encoding.append(range_end)

    return 'CodePointSet.createNoDedup(%s)' % ', '.join(map(int_to_java_hex_literal, encoding))


def str_property_single_code_points_to_java_array_init(prop):
    single_code_points_as_ints = [ord(elem) for elem in prop if len(elem) == 1]
    return property_to_java_array_init(single_code_points_as_ints)


def str_property_strings(prop):
    strings = [string for string in prop if len(string) != 1]
    init = 'EconomicSet<String> strings = EconomicSet.create(%d);' % len(strings);
    body = '\n'.join(['    strings.add(%s);' % str_to_java_string_literal(string) for string in strings])
    return init + '\n' + body


def aliases_to_java_initializer(map_name, aliases):
    return '\n'.join(['%s.put("%s", "%s");' % (map_name, long_name, short_name)
                      for (long_name, short_name) in sorted(aliases.items())])


def mangle_prop_name(name):
    return name.upper().replace('=', '_')


PROPERTY_ALIASES = aliases_to_java_initializer('PROPERTY_ALIASES', prop_aliases)

GENERAL_CATEGORY_ALIASES = aliases_to_java_initializer('GENERAL_CATEGORY_ALIASES', gc_aliases)

SCRIPT_ALIASES = aliases_to_java_initializer('SCRIPT_ALIASES', sc_aliases)

BLOCK_ALIASES = aliases_to_java_initializer('BLOCK_ALIASES', blk_aliases)

POPULATE_CALLS = '\n'.join(['populate%s();' % mangle_prop_name(name)
                            for name in sorted(prop_contents.keys()) + sorted(str_prop_contents.keys()) + ['RGI_Emoji']])

POPULATE_DEFS = '\n\n'.join(['''private static void populate%s() {
    SET_ENCODINGS.put("%s", %s);
}''' % (mangle_prop_name(name), name, property_to_java_array_init(prop))
                           for (name, prop) in sorted(prop_contents.items())])

POPULATE_STR_DEFS = '\n\n'.join(['''private static void populate%s() {
    %s
    CodePointSet codePointSet = %s;
    CLASS_SET_ENCODINGS.put("%s", ClassSetContents.createUnicodePropertyOfStrings(codePointSet, strings));
}''' % (mangle_prop_name(name), str_property_strings(prop), str_property_single_code_points_to_java_array_init(prop), name)
                           for (name, prop) in sorted(str_prop_contents.items())])

POPULATE_STR_DEFS = POPULATE_STR_DEFS + '''

private static void populateRGI_EMOJI() {
    ClassSetContentsAccumulator rgiEmoji = new ClassSetContentsAccumulator();
''' + '\n'.join(['''    rgiEmoji.addAll(CLASS_SET_ENCODINGS.get("%s"));''' % prop_name for prop_name in bin_props_emoji_str]) + '''
    CLASS_SET_ENCODINGS.put("RGI_Emoji", ClassSetContents.createClass(rgiEmoji.getCodePointSet(), rgiEmoji.getStrings(), rgiEmoji.mayContainStrings()));
}'''

print('''/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
/* Copyright (c) 2018 Unicode, Inc.
 * Unicode and the Unicode Logo are registered trademarks of Unicode, Inc. in the U.S. and other countries.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */

package com.oracle.truffle.regex.charset;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;

class UnicodePropertyData {

    static final EconomicMap<String, String> PROPERTY_ALIASES = EconomicMap.create(%d);
    static final EconomicMap<String, String> GENERAL_CATEGORY_ALIASES = EconomicMap.create(%d);
    static final EconomicMap<String, String> SCRIPT_ALIASES = EconomicMap.create(%d);
    static final EconomicMap<String, String> BLOCK_ALIASES = EconomicMap.create(%d);
    private static final EconomicMap<String, CodePointSet> SET_ENCODINGS = EconomicMap.create(%d);
    private static final EconomicMap<String, ClassSetContents> CLASS_SET_ENCODINGS = EconomicMap.create(%d);

    public static CodePointSet retrieveProperty(String propertySpec) {
        if (!SET_ENCODINGS.containsKey(propertySpec)) {
            throw new IllegalArgumentException("Unsupported Unicode character property escape");
        }
        return SET_ENCODINGS.get(propertySpec);
    }

    public static ClassSetContents retrievePropertyOfStrings(String propertySpec) {
        if (SET_ENCODINGS.containsKey(propertySpec)) {
            assert !CLASS_SET_ENCODINGS.containsKey(propertySpec);
            return ClassSetContents.createCharacterClass(SET_ENCODINGS.get(propertySpec));
        }
        if (CLASS_SET_ENCODINGS.containsKey(propertySpec)) {
            assert !SET_ENCODINGS.containsKey(propertySpec);
            return CLASS_SET_ENCODINGS.get(propertySpec);
        }
        throw new IllegalArgumentException("Unsupported Unicode character property escape");
    }

    static {
%s

%s

%s

%s

%s
    }

%s

%s
}''' % (len(prop_aliases), len(gc_aliases), len(sc_aliases), len(blk_aliases), len(prop_contents), len(str_prop_contents) + 1,
        indent(PROPERTY_ALIASES, 8), indent(GENERAL_CATEGORY_ALIASES, 8), indent(SCRIPT_ALIASES, 8), indent(BLOCK_ALIASES, 8), indent(POPULATE_CALLS, 8),
        indent(POPULATE_DEFS, 4), indent(POPULATE_STR_DEFS, 4)))
