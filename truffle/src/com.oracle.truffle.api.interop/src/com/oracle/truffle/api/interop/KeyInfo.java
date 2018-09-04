/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

/**
 * This class represents {@link Message#KEY_INFO key info} bit flags. Use this class as a utility to
 * get or set the bit flags when dealing with {@link Message#KEY_INFO} message.
 * <p>
 * The bit flags have following meaning:
 * <ul>
 * <li>{@link #READABLE}: if {@link Message#READ reading} an existing key is supported.
 * <li>{@link #MODIFIABLE}: if {@link Message#WRITE writing} an existing key is supported.
 * <li>{@link #INSERTABLE} if {@link Message#WRITE writing} a new key is supported.
 * <li>{@link #INVOCABLE}: if {@link Message#INVOKE invoking} an existing key is supported.
 * <li>{@link #REMOVABLE} if {@link Message#REMOVE removing} an existing key is supported.
 * <li>{@link #INTERNAL} if an existing key is internal.
 * <p>
 * When a {@link #isReadable(int) readable} or {@link #isWritable(int) writable} flag is
 * <code>true</code>, it does not necessarily guarantee that subsequent {@link Message#READ} or
 * {@link Message#WRITE} message will succeed. Read or write can fail due to some momentary bad
 * state. An object field is expected not to be readable resp. writable when it's known that the
 * field can not be read (e.g. a bean property without a getter) resp. can not be written to (e.g. a
 * bean property without a setter). The same applies to invocable flag and {@link Message#INVOKE}
 * message.
 * <p>
 *
 * @since 0.26
 */
public final class KeyInfo {

    /**
     * Value of the key info if the key has no capability.
     *
     * @since 0.33
     */
    public static final int NONE = 0;

    /**
     * Single bit that is set if {@link Message#READ reading} an existing key is supported.
     *
     * @since 0.33
     */
    public static final int READABLE = 1 << 1;

    /**
     * Single bit that is set if {@link Message#WRITE writing} an existing key is supported.
     *
     * @since 0.33
     */
    public static final int MODIFIABLE = 1 << 2;

    /**
     * Single bit that is set if {@link Message#INVOKE invoking} an existing key is supported.
     *
     * @since 0.33
     */
    public static final int INVOCABLE = 1 << 3;

    /**
     * Single bit that is set if an existing key is internal.
     *
     * @since 0.33
     */
    public static final int INTERNAL = 1 << 4;

    /**
     * Single bit that is set if {@link Message#REMOVE removing} an existing key is supported.
     *
     * @since 0.33
     */
    public static final int REMOVABLE = 1 << 5;

    /**
     * Single bit that is set if {@link Message#WRITE writing} a new key is supported.
     *
     * @since 0.33
     */
    public static final int INSERTABLE = 1 << 6;

    private static final int WRITABLE = INSERTABLE | MODIFIABLE;

    private static final int EXISTING = READABLE | MODIFIABLE | INVOCABLE | INTERNAL | REMOVABLE;

    private KeyInfo() {
    }

    /**
     * Test if the bits represent an existing key.
     *
     * @since 0.26
     */
    public static boolean isExisting(int infoBits) {
        return (infoBits & EXISTING) != 0;
    }

    /**
     * Test if {@link Message#READ reading} an existing key is supported.
     *
     * @since 0.26
     */
    public static boolean isReadable(int infoBits) {
        return (infoBits & READABLE) != 0;
    }

    /**
     * Test if {@link Message#READ writing} an existing or new key is supported.
     *
     * @since 0.26
     */
    public static boolean isWritable(int infoBits) {
        return (infoBits & WRITABLE) != 0;
    }

    /**
     * Test if {@link Message#INVOKE invoking} an existing key is supported.
     *
     * @since 0.26
     */
    public static boolean isInvocable(int infoBits) {
        return (infoBits & INVOCABLE) != 0;
    }

    /**
     * Test if an existing key is internal.
     *
     * @since 0.26
     */
    public static boolean isInternal(int infoBits) {
        return (infoBits & INTERNAL) != 0;
    }

    /**
     * Test if {@link Message#WRITE writing} a new key is supported.
     *
     * @since 0.33
     */
    public static boolean isRemovable(int infoBits) {
        return (infoBits & REMOVABLE) != 0;
    }

    /**
     * Test if {@link Message#WRITE writing} an existing key is supported.
     *
     * @since 0.33
     */
    public static boolean isModifiable(int infoBits) {
        return (infoBits & MODIFIABLE) != 0;
    }

    /**
     * Test if {@link Message#WRITE writing} a new key is supported.
     *
     * @since 0.33
     */
    public static boolean isInsertable(int infoBits) {
        return (infoBits & INSERTABLE) != 0;
    }

    /**
     * @since 0.26
     * @deprecated in 0.33 use integer constants in {@link KeyInfo} instead. For example
     *             <code> KeyInfo.newBuilder().setWritable(true).setReadable(true).build()</code>
     *             becomes <code>
     *             {@link #READABLE READABLE} | {@link #MODIFIABLE MODIFIABLE} | {@link #INSERTABLE
     *             INSERTABLE}</code>
     */
    @Deprecated
    public static Builder newBuilder() {
        return new KeyInfo().new Builder();
    }

    /**
     * A builder of bit flags. An instance of this class can be reused for multiple key info bits
     * {@link #build() creation}.
     *
     * @since 0.26
     * @deprecated in 0.33 use integer constants in {@link KeyInfo} instead. For example
     *             <code> KeyInfo.newBuilder().setWritable(true).setReadable(true).build()</code>
     *             becomes <code>
     *             {@link #READABLE READABLE} | {@link #MODIFIABLE MODIFIABLE} | {@link #INSERTABLE
     *             INSERTABLE}</code>
     */
    @Deprecated
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
         * Set removability flag.
         *
         * @since 0.32
         */
        public Builder setRemovable(boolean removable) {
            setBit(5, removable);
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
