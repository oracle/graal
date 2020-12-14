/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.polyglot;

/**
 *
 */
public final class Interop {

    /**
     * Returns <code>true</code> if the receiver represents a <code>null</code> like value, else
     * <code>false</code>. Most object oriented languages have one or many values representing null
     * values. Invoking this message does not cause any observable side-effects.
     *
     * @since 19.0
     */
    public native static boolean isNull(Object receiver);

    // region Boolean Messages

    /**
     * Returns <code>true</code> if the receiver represents a <code>boolean</code> like value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see #asBoolean(Object)
     * @since 19.0
     */
    public native static boolean isBoolean(Object receiver);

    /**
     * Returns the Java boolean value if the receiver represents a {@link #isBoolean(Object)
     * boolean} like value.
     *
     * @throws UnsupportedMessageException if and only if {@link #isBoolean(Object)} returns
     *                                     <code>false</code> for the same receiver.
     * @see #isBoolean(Object)
     * @since 19.0
     */
    public native static boolean asBoolean(Object receiver) throws UnsupportedMessageException;

    // endregion Boolean Messages

    // region String Messages

    /**
     * Returns <code>true</code> if the receiver represents a <code>string</code> value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see #asString(Object)
     * @since 19.0
     */
    public native static boolean isString(Object receiver);

    /**
     * Returns the Java string value if the receiver represents a {@link #isString(Object) string}
     * like value.
     *
     * @throws UnsupportedMessageException if and only if {@link #isString(Object)} returns
     *                                     <code>false</code> for the same receiver.
     * @see #isString(Object)
     * @since 19.0
     */
    public native static String asString(Object receiver) throws UnsupportedMessageException;

    // endregion String Messages

    // region Number Messages

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see #fitsInByte(Object)
     * @see #fitsInShort(Object)
     * @see #fitsInInt(Object)
     * @see #fitsInLong(Object)
     * @see #fitsInFloat(Object)
     * @see #fitsInDouble(Object)
     * @see #asByte(Object)
     * @see #asShort(Object)
     * @see #asInt(Object)
     * @see #asLong(Object)
     * @see #asFloat(Object)
     * @see #asDouble(Object)
     * @since 19.0
     */
    public native static boolean isNumber(Object receiver);

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java byte primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asByte(Object)
     * @since 19.0
     */
    public native static boolean fitsInByte(Object receiver);

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java short primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asShort(Object)
     * @since 19.0
     */
    public native static boolean fitsInShort(Object receiver);

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java int primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asInt(Object)
     * @since 19.0
     */
    public native static boolean fitsInInt(Object receiver);

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java long primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asLong(Object)
     * @since 19.0
     */
    public native static boolean fitsInLong(Object receiver);

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java float primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asFloat(Object)
     * @since 19.0
     */
    public native static boolean fitsInFloat(Object receiver);

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java double primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asDouble(Object)
     * @since 19.0
     */
    public native static boolean fitsInDouble(Object receiver);

    /**
     * Returns the receiver value as Java byte primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *                                     {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInByte(Object)
     * @since 19.0
     */
    public native static byte asByte(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns the receiver value as Java short primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *                                     {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInShort(Object)
     * @since 19.0
     */
    public native static short asShort(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns the receiver value as Java int primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *                                     {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInInt(Object)
     * @since 19.0
     */
    public native static int asInt(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns the receiver value as Java long primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *                                     {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInLong(Object)
     * @since 19.0
     */
    public native static long asLong(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns the receiver value as Java float primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *                                     {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInFloat(Object)
     * @since 19.0
     */
    public native static float asFloat(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns the receiver value as Java double primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *                                     {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInDouble(Object)
     * @since 19.0
     */
    public native static double asDouble(Object receiver) throws UnsupportedMessageException;

    // endregion Number Messages
}
