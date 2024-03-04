/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.util;

import java.util.List;

/**
 * This class contains utility methods for {@link jdk.vm.ci.meta.Signature}s.
 */
public final class SignatureUtil {

    private SignatureUtil() {
    }

    /**
     * Parses a method descriptor into its constituent parameter and return type descriptors.
     *
     * @param signature a method descriptor as specified in 4.3.3 of the JVMS
     * @param parameters the list to which the parsed parameter type descriptors are added
     * @return the parsed return type descriptor
     */
    public static String parseSignature(String signature, List<String> parameters) {
        if (signature.length() == 0) {
            throw new IllegalArgumentException("Signature cannot be empty");
        }
        if (signature.charAt(0) == '(') {
            int cur = 1;
            while (cur < signature.length() && signature.charAt(cur) != ')') {
                int nextCur = parseSignature(signature, cur);
                parameters.add(signature.substring(cur, nextCur));
                cur = nextCur;
            }

            cur++;
            int nextCur = parseSignature(signature, cur);
            String returnType = signature.substring(cur, nextCur);
            if (nextCur != signature.length()) {
                throw new IllegalArgumentException("Extra characters at end of signature: " + signature);
            }
            return returnType;
        } else {
            throw new IllegalArgumentException("Signature must start with a '(': " + signature);
        }
    }

    private static int parseSignature(String signature, int start) {
        try {
            int cur = start;
            char first;
            do {
                first = signature.charAt(cur);
                cur++;
            } while (first == '[');

            switch (first) {
                case 'L':
                    while (signature.charAt(cur) != ';') {
                        if (signature.charAt(cur) == '.') {
                            throw new IllegalArgumentException("Class name in signature contains '.' at index " + cur + ": " + signature);
                        }
                        cur++;
                    }
                    cur++;
                    break;
                case 'V':
                case 'I':
                case 'B':
                case 'C':
                case 'D':
                case 'F':
                case 'J':
                case 'S':
                case 'Z':
                    break;
                default:
                    throw new IllegalArgumentException("Invalid character '" + signature.charAt(cur - 1) + "' at index " + (cur - 1) + " in signature: " + signature);
            }
            return cur;
        } catch (StringIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Truncated signature: " + signature);
        }
    }
}
