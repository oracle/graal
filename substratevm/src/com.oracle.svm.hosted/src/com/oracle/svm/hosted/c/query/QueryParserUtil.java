/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.query;

import com.oracle.svm.hosted.c.info.PropertyInfo;

public class QueryParserUtil {

    // The size of hex string the represents a long integer
    static final int SIZE_OF_LONG_HEX = 16;

    static void parseSigned(PropertyInfo<Object> info, String hex) {
        if (isNegative(hex)) {
            if (hex.length() < SIZE_OF_LONG_HEX) {
                String extended = signedExtend(hex);
                parseHexToLong(info, extended);
            } else {
                parseHexToLong(info, hex);
            }
        } else {
            parseHexToLong(info, hex);
        }
    }

    /**
     * Only checks the most significant digit in the hexadecimal representation of the integer
     * number. This method converts the most significant digit from String to int and check if its
     * value if greater or equals to 8, if it is the most significant bit is set. Therefore, the
     * integer number is negative.
     * <p>
     * A unsigned integer should not use this method.
     *
     * @param hex hexadecimal string representation of the integer being checked
     * @return true if the hex represents a negative signed integer; no if it does not
     */
    static boolean isNegative(String hex) {
        int mostSignificantDigit = Integer.parseInt(hex.substring(0, 1), 16);

        if (mostSignificantDigit >= 8) {
            return true;
        }

        return false;
    }

    static String unsignedExtend(String hex) {
        int numOfMissingHexDigit = SIZE_OF_LONG_HEX - hex.length();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < numOfMissingHexDigit; i++) {
            sb.append('0');
        }

        return sb.append(hex).toString();
    }

    static String unsignedExtendToSize(int sizeInByte, String hex) {
        int numOfMissingHexDigit = sizeInByte * 2 - hex.length();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < numOfMissingHexDigit; i++) {
            sb.append('0');
        }

        return sb.append(hex).toString();
    }

    static String signedExtend(String hex) {
        int numOfMissingHexDigit = SIZE_OF_LONG_HEX - hex.length();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < numOfMissingHexDigit; i++) {
            sb.append('F');
        }

        return sb.append(hex).toString();
    }

    /**
     * Extends short or int hex to long hex by assigning most significant bits to 0, and then parses
     * the hex string in chunks to avoid the problem that { Long.parseLong } does not deal with
     * two's complement.
     * <p>
     * Signed negative hex should already be signed extended before calling { parseHexToLong }.
     *
     * @param info ValueInfo to be updated
     * @param hex Hex string to be parsed
     */
    static void parseHexToLong(PropertyInfo<Object> info, String hex) {
        String extended = unsignedExtend(hex);

        try {
            String msb = extended.substring(0, 8);
            String lsb = extended.substring(8);
            long msbValue = Long.parseLong(msb, 16);
            long lsbValue = Long.parseLong(lsb, 16);
            long value = msbValue << 32 | lsbValue;
            info.setProperty(value);
        } catch (NumberFormatException e) {
            parseHexToLongUnsafe(info, hex);
        }
    }

    static void parseHexToLongUnsafe(PropertyInfo<Object> info, String hex) {
        long value = 0;

        for (int i = 0; i < hex.length(); i++) {
            char digit = hex.charAt(i);
            value = 16 * value + Character.getNumericValue(digit);
        }

        info.setProperty(value);
    }

}
