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

/**
 * Token encapsulates some sensitive data that can be compared in a secure way. That is, the
 * {@link #equals(Object)} method does not leak any information through timing. While one could be
 * careful with {@link String} or byte[] and perform all the comparisons in a secure way, just one
 * careless call of {@link Object#equals(Object)} could expose the secret data to an attacker. For
 * this reason, we encapsulate it into a class to prevent such accidental exposure.
 *
 * The Token class does not allow the data to be extracted in the original form. It has limited set
 * of operation that allow you to learn something about the data:
 * <ul>
 * <li>Comparison - designed not to leak any data through its execution time.</li>
 * <li>{@link #hashCode()} - this might expose part of the <strong>hash</strong>. Note that some
 * collection implementations like {@link java.util.HashMap} might use it and leak this value
 * through timing attack.</li>
 * <li>{@link #toString()} might contain whole <strong>hash</strong> of the sensitive data.</li>
 * </ul>
 *
 * At worst, just a hash of the sensitive data can leak by careless operation. If this happens:
 * <ul>
 * <li>All secrets with entropy outside of attacker's capability for offline attacks are safe.</li>
 * <li>Secrets with low entropy (e.g., short secrets or secrets made in a predictable way) might be
 * cracked by a offline attack.</li>
 * </ul>
 *
 * Please note that this class does <strong>not</strong> use a slow hashing function (like bcrypt),
 * that are recommended for secrets of potentially entropy (e.g., passwords). <strong>You should not
 * store low-entropy secrets there unless you are extremely careful about calling methods such as
 * {@link #hashCode()} and {@link #toString()}.</strong>
 *
 * Those operations are explicitly not planned to be ever supported:
 * <ul>
 * <li>serialization - Allowing serialization would not allow us to change the hash function or
 * String encoding in future.</li>
 * <li>comparing values like {@link Comparable#compareTo(Object)} - This could be hardly implemented
 * in a meaningful way without compromising security.</li>
 * </ul>
 */
public final class Token {

    // SHA-256 is a standard algorithm name that all compliant Java implementation must implement
    private static final String HASH_FUNCTION = "SHA-256";

    private final byte[] token;

    private Token(byte[] token) {
        this.token = token;
    }

    public static Token createHashedTokenFromString(String secret) {
        try {
            return new Token(MessageDigest.getInstance(HASH_FUNCTION).digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Since HASH_FUNCTION is a hash function that must ge implemented, we assume that the
            // NoSuchAlgorithmException is never thrown.
            throw new AssertionError(e);
        }
    }

    /**
     * If the other object is not a Token, it immediatelly returns false. If the other object is a
     * Token, it compares values encapsulated by the tokens in a way that prevents timing attacks.
     * That is, even if an attacker is able to measure the time of this operation, it gives them no
     * valuable information about the secret contents.
     */
    @Override
    public boolean equals(Object o) {
        // We do constant-time comparison for tokens of the same length. For other classes or for
        // different sizes, it might evaluate in a shorter time.
        // We are intentionally not short-circuiting: if (this == o) return true;
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Token token1 = (Token) o;
        return MessageDigest.isEqual(token, token1.token);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(token);
    }

    /**
     * The String representation of Token might contain hash. Currently, it contains the hash.
     * However, it might change in future.
     *
     * This means that you cannot rely on any of those variant. You should be careful when printing
     * the value out, but you cannot rely on it to provide any information.
     */
    @Override
    public String toString() {
        return "Token{" +
                        "token=" + HASH_FUNCTION + ":" + Base64.getEncoder().encodeToString(token) +
                        '}';
    }
}
