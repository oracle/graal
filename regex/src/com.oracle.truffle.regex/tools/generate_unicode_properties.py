#!/usr/bin/env python2
#
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
#
# This script generates the contents of the
# graal/com.oracle.truffle.regex/src/com/oracle/truffle/regex/tregex/parser/UnicodeCharacterPropertyData.java
# source file in the Graal.js code base. It collects data from the Unicode
# database necessary for the support of Unicode property escapes in regular
# expressions. In order to run this script, you need to make sure to have the
# following Unicode data files in your working directory:
#   - ucd.nounihan.flat.xml (This is part of the Unicode in XML data files (UAX #42))
#   - PropertyAliases.txt
#   - PropertyValueAliases.txt
#   - emoji-data.txt (This is part of the Emoji data included in Unicode TR51)

import re
import xml.etree.ElementTree as ET

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

# The names of binary Emoji properties defined in Unicode TR51.
bin_props_emoji = [
    'Emoji',
    'Emoji_Component',
    'Emoji_Modifier_Base',
    'Emoji_Modifier',
    'Emoji_Presentation',
    'Extended_Pictographic'
]

tracked_props = bin_props_xml + bin_props_emoji + ['gc', 'sc', 'scx']

prop_contents = {}

def add_code_points_to_property(code_points, prop_name):
    if prop_name in prop_contents:
        prop_contents[prop_name].extend(code_points)
    else:
        prop_contents[prop_name] = code_points

def add_xml_entry_to_property(entry, prop_name):
    if entry.get('first-cp'):
        first_code_point = int(entry.get('first-cp'), 16)
        last_code_point = int(entry.get('last-cp'), 16)
        add_code_points_to_property(range(first_code_point, last_code_point + 1), prop_name)
    else:
        code_point = int(entry.get('cp'), 16)
        add_code_points_to_property([code_point], prop_name)

def add_txt_entry_to_property(code_points_str, prop_name):
    if '..' in code_points_str:
        [first_code_point, last_code_point] = [int(cps, 16) for cps in code_points_str.split('..')]
        add_code_points_to_property(range(first_code_point, last_code_point + 1), prop_name)
    else:
        code_point = int(code_points_str, 16)
        add_code_points_to_property([code_point], prop_name)

def complement_property(prop):
    return sorted(set(xrange(0, 0x110000)) - set(prop))


## Parse XML

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
    for script in char_elem.get('scx').split():
        add_xml_entry_to_property(char_elem, 'scx=' + script)



## Parse Emoji data

def unicode_file_lines_without_comments(file_name):
    lines = []
    with open(file_name) as unicode_file:
        for line in unicode_file:
            line = re.sub('#.*$', '', line).strip()
            if not line == '':
                lines.append(line)
    return lines

for line in unicode_file_lines_without_comments('emoji-data.txt'):
    [code_points_str, prop_name] = [x.strip() for x in line.split(';')]
    add_txt_entry_to_property(code_points_str, prop_name)



## Add special properties from Unicode TR18

# The following properties are not defined in the Unicode character database. Their
# definitions are given in Section 1.2.1. of Techinal Report 18
# (http://www.unicode.org/reports/tr18/tr18-19.html#General_Category_Property).
prop_contents['Any'] = xrange(0, 0x110000)
prop_contents['ASCII'] = xrange(0, 0x80)
prop_contents['Assigned'] = complement_property(prop_contents['gc=Cn'])



## Parse aliases

prop_aliases = {}

for line in unicode_file_lines_without_comments('PropertyAliases.txt'):
    aliases = [x.strip() for x in line.split(';')]
    short_name = aliases[0]
    all_names = aliases[0:]
    if short_name in tracked_props:
        for name in all_names:
            prop_aliases[name] = short_name

# The Emoji character properties from TR51 are not included in the Unicode
# character database's PropertyAliases.txt file and so add their names to the
# alias table manually.
for name in bin_props_emoji:
    prop_aliases[name] = name

# We also add the names of the properties from TR18 into the alias table.
for name in ['Any', 'ASCII', 'Assigned']:
    prop_aliases[name] = name

gc_aliases = {}
sc_aliases = {}

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



## Generate Java source code

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
    absolute = '0x%04x' % abs(i)
    if i < 0:
        return '-' + absolute
    else:
        return absolute

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
        if range_start == range_end:
            encoding.append(-1 * range_start - 1)
        else:
            encoding.append(range_start)
            encoding.append(range_end)
    return 'new int[]{%s}' % ', '.join(map(int_to_java_hex_literal, encoding))

def aliases_to_java_initializer(map_name, aliases):
    return '\n'.join(['%s.put("%s", "%s");' % (map_name, long_name, short_name)
                      for (long_name, short_name) in sorted(aliases.items())])

def mangle_prop_name(name):
    return name.upper().replace('=', '_')

PROPERTY_ALIASES = aliases_to_java_initializer('PROPERTY_ALIASES', prop_aliases)

GENERAL_CATEGORY_ALIASES = aliases_to_java_initializer('GENERAL_CATEGORY_ALIASES', gc_aliases)

SCRIPT_ALIASES = aliases_to_java_initializer('SCRIPT_ALIASES', sc_aliases)

POPULATE_CALLS = '\n'.join(['populate%s();' % mangle_prop_name(name)
                            for (name, _) in sorted(prop_contents.items())])

POPULATE_DEFS = '\n\n'.join(['''private static void populate%s() {
    SET_ENCODINGS.put("%s", %s);
}''' % (mangle_prop_name(name), name, property_to_java_array_init(prop))
                           for (name, prop) in sorted(prop_contents.items())])

print '''/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
/* Copyright (c) 2018 Unicode, Inc.
 * Unicode and the Unicode Logo are registered trademarks of Unicode, Inc. in the U.S. and other countries.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */

package com.oracle.truffle.regex.chardata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class UnicodeCharacterPropertyData {

    public static final Map<String, String> PROPERTY_ALIASES = new HashMap<>(%d);
    public static final Map<String, String> GENERAL_CATEGORY_ALIASES = new HashMap<>(%d);
    public static final Map<String, String> SCRIPT_ALIASES = new HashMap<>(%d);
    private static final Map<String, int[]> SET_ENCODINGS = new HashMap<>(%d);

    public static CodePointSet retrieveProperty(String propertySpec) {
        if (!SET_ENCODINGS.containsKey(propertySpec)) {
            throw new IllegalArgumentException("Unsupported Unicode character property escape");
        }
        int[] encoding = SET_ENCODINGS.get(propertySpec);

        List<CodePointRange> ranges = new ArrayList<>(encoding.length);
        int i = 0;
        while (i < encoding.length) {
            if (encoding[i] >= 0) {
                ranges.add(new CodePointRange(encoding[i], encoding[i + 1]));
                i = i + 2;
            } else {
                ranges.add(new CodePointRange(-1 * encoding[i] - 1));
                i = i + 1;
            }
        }

        return CodePointSet.create(ranges);
    }

    static {
%s

%s

%s

%s
    }

%s
}''' % (len(prop_aliases) * 1.5, len(gc_aliases) * 1.5, len(sc_aliases) * 1.5, len(prop_contents) * 1.5,
        indent(PROPERTY_ALIASES, 8), indent(GENERAL_CATEGORY_ALIASES, 8), indent(SCRIPT_ALIASES, 8), indent(POPULATE_CALLS, 8),
        indent(POPULATE_DEFS, 4))
