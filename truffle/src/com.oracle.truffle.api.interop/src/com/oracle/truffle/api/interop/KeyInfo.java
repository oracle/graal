/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

/**
 * This class represents {@link Message#KEY_INFO key info} bit flags. Use this class as a utility to
 * get or set the bit flags when dealing with {@link Message#KEY_INFO} message.
 * <p>
 * The bit flags have following meaning:
 * <ul style="list-style-type: none;">
 * <li>0. bit: Existence of the key</li>
 * <li>1. bit: Readability of the key ({@link Message#READ} is supported)</li>
 * <li>2. bit: Writability of the key ({@link Message#WRITE} is supported)</li>
 * <li>3. bit: Invocability of the key ({@link Message#createInvoke(int)} is supported)</li>
 * <li>4. bit: Internal attribute of the key (see an internal argument to {@link Message#KEYS})</li>
 * </ul>
 * <p>
 * When a readable or writable flag is <code>true</code>, it does not necessarily guarantee that
 * subsequent {@link Message#READ} or {@link Message#WRITE} message will succeed. Read or write can
 * fail due to some momentary bad state. An object field is expected not to be readable resp.
 * writable when it's known that the field can not be read (e.g. a bean property without a getter)
 * resp. can not be written to (e.g. a bean property without a setter). The same applies to
 * invocable flag and {@link Message#createInvoke(int) invoke} message.
 * <p>
 * When the key does not exist (0. bit is zero), then all other bits must be zero as well.
 *
 * @since 0.26
 */
public final class KeyInfo {

    private static final int READABLE = 1 << 1;
    private static final int WRITABLE = 1 << 2;
    private static final int INVOCABLE = 1 << 3;
    private static final int INTERNAL = 1 << 4;

    private KeyInfo() {
    }

    /**
     * Create a new bit flags builder. The builder is reusable and builds key info of existing keys
     * only. Use <code>0</code> directly to inform about a non-existing key.
     *
     * @since 0.26
     */
    public static Builder newBuilder() {
        return new KeyInfo().new Builder();
    }

    /**
     * Test if the bits represent an existing key.
     *
     * @since 0.26
     */
    public static boolean isExisting(int infoBits) {
        return (infoBits & 1) != 0;
    }

    /**
     * Test if readable flag is on.
     *
     * @since 0.26
     */
    public static boolean isReadable(int infoBits) {
        return (infoBits & READABLE) != 0;
    }

    /**
     * Test if writable flag is on.
     *
     * @since 0.26
     */
    public static boolean isWritable(int infoBits) {
        return (infoBits & WRITABLE) != 0;
    }

    /**
     * Test if invocable flag is on.
     *
     * @since 0.26
     */
    public static boolean isInvocable(int infoBits) {
        return (infoBits & INVOCABLE) != 0;
    }

    /**
     * Test if internal flag is on.
     *
     * @since 0.26
     */
    public static boolean isInternal(int infoBits) {
        return (infoBits & INTERNAL) != 0;
    }

    /**
     * A builder of bit flags. An instance of this class can be reused for multiple key info bits
     * {@link #build() creation}.
     *
     * @since 0.26
     */
    public final class Builder {

        private int infoBits;

        private Builder() {
            infoBits = 1;
        }

        /**
         * Set readability flag.
         *
         * @since 0.26
         */
        public Builder setReadable(boolean readable) {
            setBit(1, readable);
            return this;
        }

        /**
         * Set writability flag.
         *
         * @since 0.26
         */
        public Builder setWritable(boolean readable) {
            setBit(2, readable);
            return this;
        }

        /**
         * Set invocability flag.
         *
         * @since 0.26
         */
        public Builder setInvocable(boolean readable) {
            setBit(3, readable);
            return this;
        }

        /**
         * Set internal attribute flag.
         *
         * @since 0.26
         */
        public Builder setInternal(boolean readable) {
            setBit(4, readable);
            return this;
        }

        /**
         * Get the current bit flags of this builder in an integer value.
         *
         * @since 0.26
         */
        public int build() {
            return infoBits;
        }

        private void setBit(int b, boolean value) {
            int v = value ? 1 : 0;
            infoBits = infoBits & ~(1 << b) | (v << b);
        }
    }
}
