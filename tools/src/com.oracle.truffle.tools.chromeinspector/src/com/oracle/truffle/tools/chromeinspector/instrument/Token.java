/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.instrument;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

// Not designed for serialization. Allowing serialization would not allow us to change the hash function or charset in future.
public final class Token {

    // SHA-256 is a standard algorithm name that all compliant Java implementation must implement
    private static final String HASH_FUNCTION = "SHA-256";

    private final byte[] token;

    private Token(byte[] token) {
        this.token = token;
    }

    public static Token createHashedTokenFromString(String s) {
        try {
            return new Token(MessageDigest.getInstance(HASH_FUNCTION).digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Since HASH_FUNCTION is a hash function that must ge implemented, we assume that the
            // NoSuchAlgorithmException is never thrown.
            throw new AssertionError(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        // We do constant-time comparison for tokens of the same length. For other classes or for
        // different sizes, it might evaluate in a shorter time.
        // intentionally not short-circuiting: if (this == o) return true;
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Token token1 = (Token) o;
        return MessageDigest.isEqual(token, token1.token);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(token);
    }

    @Override
    public String toString() {
        return "Token{" +
                        "token=" + HASH_FUNCTION + ":" + Base64.getEncoder().encodeToString(token) +
                        '}';
    }
}
