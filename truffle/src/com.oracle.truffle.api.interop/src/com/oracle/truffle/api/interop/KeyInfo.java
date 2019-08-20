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
 * @since 0.26
 * @deprecated see the individual constants and methods for replacements.
 */
@Deprecated
public final class KeyInfo {

    /**
     *
     * @since 0.33
     * @deprecated without replacement.
     */
    @Deprecated public static final int NONE = 0;

    /**
     * Single bit that is set if {@link Message#READ reading} an existing key is supported.
     *
     * @since 0.33
     * @see #READ_SIDE_EFFECTS
     * @deprecated see {@link InteropLibrary#isMemberReadable(Object, String)} or
     *             {@link InteropLibrary#isArrayElementReadable(Object, long)} instead.
     */
    @Deprecated public static final int READABLE = 1 << 1;

    /**
     * Single bit that is set if {@link Message#WRITE writing} an existing key is supported.
     *
     * @since 0.33
     * @see #WRITE_SIDE_EFFECTS
     * @deprecated see {@link InteropLibrary#isMemberModifiable(Object, String)} or
     *             {@link InteropLibrary#isArrayElementModifiable(Object, long)} instead.
     */
    @Deprecated public static final int MODIFIABLE = 1 << 2;

    /**
     * Single bit that is set if {@link Message#INVOKE invoking} an existing key is supported.
     *
     * @since 0.33
     * @deprecated see {@link InteropLibrary#isMemberInvocable(Object, String) instead.
     */
    @Deprecated public static final int INVOCABLE = 1 << 3;

    /**
     * Single bit that is set if an existing key is internal.
     *
     * @since 0.33
     * @deprecated see {@link InteropLibrary#isMemberInternal(Object, String) instead.
     */
    @Deprecated public static final int INTERNAL = 1 << 4;

    /**
     * Single bit that is set if {@link Message#REMOVE removing} an existing key is supported.
     *
     * @since 0.33
     * @deprecated see {@link InteropLibrary#isMemberRemovable(Object, String)} or
     *             {@link InteropLibrary#isArrayElementRemovable(Object, long)} instead.
     */
    @Deprecated public static final int REMOVABLE = 1 << 5;

    /**
     * Single bit that is set if {@link Message#WRITE writing} a new key is supported.
     *
     * @since 0.33
     * @deprecated see {@link InteropLibrary#isMemberInsertable(Object, String)} or
     *             {@link InteropLibrary#isArrayElementInsertable(Object, long)} instead.
     */
    @Deprecated public static final int INSERTABLE = 1 << 6;

    /**
     * Single bit that is set if {@link Message#READ} may have side-effects. A read side-effect
     * means any change in runtime state that is observable by the guest language program. For
     * instance in JavaScript a property {@link Message#READ} may have side-effects if the property
     * has a getter function.
     *
     * @since 19.0
     * @deprecated see {@link InteropLibrary#hasMemberReadSideEffects(Object, String) instead.
     */
    @Deprecated public static final int READ_SIDE_EFFECTS = 1 << 7;

    /**
     * Single bit that is set if {@link Message#WRITE} may have side-effects. A write side-effect
     * means any change in runtime state, besides the write operation of the member, that is
     * observable by the guest language program. For instance in JavaScript a property
     * {@link Message#WRITE} may have side-effects if the property has a setter function.
     *
     * @since 19.0
     * @deprecated see {@link InteropLibrary#hasMemberWriteSideEffects(Object, String) instead.
     */
    @Deprecated public static final int WRITE_SIDE_EFFECTS = 1 << 8;

    private static final int WRITABLE = INSERTABLE | MODIFIABLE;

    private static final int EXISTING = READABLE | MODIFIABLE | INVOCABLE | INTERNAL | REMOVABLE;

    private KeyInfo() {
    }

    /**
     * Test if the bits represent an existing key.
     *
     * @since 0.26
     * @deprecated use {@link InteropLibrary#isMemberExisting(Object, String)} or
     *             {@link InteropLibrary#isArrayElementExisting(Object, long)} instead.
     */
    @Deprecated
    public static boolean isExisting(int infoBits) {
        return (infoBits & EXISTING) != 0;
    }

    /**
     * Test if {@link Message#READ reading} an existing key is supported.
     *
     * @since 0.26
     * @deprecated use {@link InteropLibrary#isMemberReadable(Object, String)} or
     *             {@link InteropLibrary#isArrayElementReadable(Object, long)} instead.
     */
    @Deprecated
    public static boolean isReadable(int infoBits) {
        return (infoBits & READABLE) != 0;
    }

    /**
     * Test if {@link Message#READ writing} an existing or new key is supported.
     *
     * @since 0.26
     * @deprecated use {@link InteropLibrary#isMemberWritable(Object, String)} or
     *             {@link InteropLibrary#isArrayElementWritable(Object, long)} instead.
     */
    @Deprecated
    public static boolean isWritable(int infoBits) {
        return (infoBits & WRITABLE) != 0;
    }

    /**
     * Test if {@link Message#READ} may have side-effects.
     *
     * @since 19.0
     * @deprecated use {@link InteropLibrary#hasMemberReadSideEffects(Object, String)} instead.
     */
    @Deprecated
    public static boolean hasReadSideEffects(int infoBits) {
        return (infoBits & READ_SIDE_EFFECTS) != 0;
    }

    /**
     * Test if {@link Message#WRITE} may have side-effects.
     *
     * @since 19.0
     * @deprecated use {@link InteropLibrary#hasMemberWriteSideEffects(Object, String)} instead.
     */
    @Deprecated
    public static boolean hasWriteSideEffects(int infoBits) {
        return (infoBits & WRITE_SIDE_EFFECTS) != 0;
    }

    /**
     * Test if {@link Message#INVOKE invoking} an existing key is supported.
     *
     * @since 0.26
     * @deprecated use {@link InteropLibrary#isMemberInvocable(Object, String)} instead.
     */
    @Deprecated
    public static boolean isInvocable(int infoBits) {
        return (infoBits & INVOCABLE) != 0;
    }

    /**
     * Test if an existing key is internal.
     *
     * @since 0.26
     * @deprecated use {@link InteropLibrary#isMemberInternal(Object, String)} instead.
     */
    @Deprecated
    public static boolean isInternal(int infoBits) {
        return (infoBits & INTERNAL) != 0;
    }

    /**
     * Test if {@link Message#WRITE writing} a new key is supported.
     *
     * @since 0.33
     * @deprecated use {@link InteropLibrary#isMemberRemovable(Object, String)} or
     *             {@link InteropLibrary#isArrayElementRemovable(Object, long)} instead.
     */
    @Deprecated
    public static boolean isRemovable(int infoBits) {
        return (infoBits & REMOVABLE) != 0;
    }

    /**
     * Test if {@link Message#WRITE writing} an existing key is supported.
     *
     * @since 0.33
     * @deprecated use {@link InteropLibrary#isMemberModifiable(Object, String)} or
     *             {@link InteropLibrary#isArrayElementModifiable(Object, long)} instead.
     */
    @Deprecated
    public static boolean isModifiable(int infoBits) {
        return (infoBits & MODIFIABLE) != 0;
    }

    /**
     * Test if {@link Message#WRITE writing} a new key is supported.
     *
     * @since 0.33
     * @deprecated use {@link InteropLibrary#isMemberInsertable(Object, String)} or
     *             {@link InteropLibrary#isArrayElementInsertable(Object, long)} instead.
     */
    @Deprecated
    public static boolean isInsertable(int infoBits) {
        return (infoBits & INSERTABLE) != 0;
    }

}
