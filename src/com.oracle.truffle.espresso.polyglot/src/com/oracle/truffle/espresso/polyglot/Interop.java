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

    // region Exception Messages

    /**
     * Returns <code>true</code> if the receiver value represents a throwable exception/error}.
     * Invoking this message does not cause any observable side-effects. Returns <code>false</code>
     * by default.
     * <p>
     * Objects must only return <code>true</code> if they support {@link #throwException} as well.
     * If this method is implemented then also {@link #throwException(Object)} must be implemented.
     *
     * The following simplified {@code TryCatchNode} shows how the exceptions should be handled by
     * languages.
     *
     * @see #throwException(Object)
     * @see com.oracle.truffle.api.exception.AbstractTruffleException
     * @since 19.3
     */
    public static native boolean isException(Object receiver);

    /**
     * Throws the receiver object as an exception of the source language, as if it was thrown by the
     * source language itself. Allows rethrowing exceptions caught by another language. If this
     * method is implemented then also {@link #isException(Object)} must be implemented.
     * <p>
     * Any interop value can be an exception value and export {@link #throwException(Object)}. The
     * exception thrown by this message must extend
     * {@link com.oracle.truffle.api.exception.AbstractTruffleException}. In future versions this
     * contract will be enforced using an assertion.
     * <p>
     * For a sample {@code TryCatchNode} implementation see {@link #isException(Object)
     * isException}.
     *
     * @throws UnsupportedMessageException if and only if {@link #isException(Object)} returns
     *             <code>false</code> for the same receiver.
     * @see #isException(Object)
     * @since 19.3
     */
    public static native RuntimeException throwException(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns {@link ExceptionType exception type} of the receiver. Throws
     * {@code UnsupportedMessageException} when the receiver is not an {@link #isException(Object)
     * exception}.
     * <p>
     * For a sample {@code TryCatchNode} implementation see {@link #isException(Object)
     * isException}.
     *
     * @see #isException(Object)
     * @see ExceptionType
     * @since 20.3
     */
    public static native ExceptionType getExceptionType(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns {@code true} if receiver value represents an incomplete source exception. Throws
     * {@code UnsupportedMessageException} when the receiver is not an {@link #isException(Object)
     * exception} or the exception is not a {@link ExceptionType#PARSE_ERROR}.
     *
     * @see #isException(Object)
     * @see #getExceptionType(Object)
     * @since 20.3
     */
    public static native boolean isExceptionIncompleteSource(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns exception exit status of the receiver. Throws {@code UnsupportedMessageException}
     * when the receiver is not an {@link #isException(Object) exception} of the
     * {@link ExceptionType#EXIT exit type}. A return value zero indicates that the execution of the
     * application was successful, a non-zero value that it failed. The individual interpretation of
     * non-zero values depends on the application.
     *
     * @see #isException(Object)
     * @see #getExceptionType(Object)
     * @see ExceptionType
     * @since 20.3
     */
    public static native int getExceptionExitStatus(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns {@code true} if the receiver is an exception with an attached internal cause.
     * Invoking this message does not cause any observable side-effects. Returns {@code false} by
     * default.
     *
     * @see #isException(Object)
     * @see #getExceptionCause(Object)
     * @since 20.3
     */
    public static native boolean hasExceptionCause(Object receiver);

    /**
     * Returns the internal cause of the receiver. Throws {@code UnsupportedMessageException} when
     * the receiver is not an {@link #isException(Object) exception} or has no internal cause. The
     * return value of this message is guaranteed to return <code>true</code> for
     * {@link #isException(Object)}.
     *
     *
     * @see #isException(Object)
     * @see #hasExceptionCause(Object)
     * @since 20.3
     */
    public static native Object getExceptionCause(Object receiver) throws UnsupportedMessageException;
    /**
     * Returns {@code true} if the receiver is an exception that has an exception message. Invoking
     * this message does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see #isException(Object)
     * @see #getExceptionMessage(Object)
     * @since 20.3
     */
    public static native boolean hasExceptionMessage(Object receiver);

    /**
     * Returns exception message of the receiver. Throws {@code UnsupportedMessageException} when
     * the receiver is not an {@link #isException(Object) exception} or has no exception message.
     * The return value of this message is guaranteed to return <code>true</code> for
     * {@link #isString(Object)}.
     *
     * @see #isException(Object)
     * @see #hasExceptionMessage(Object)
     * @since 20.3
     */
    public static native Object getExceptionMessage(Object receiver) throws UnsupportedMessageException;

    /**
     * Returns {@code true} if the receiver is an exception and has a stack trace. Invoking this
     * message does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see #isException(Object)
     * @see #getExceptionStackTrace(Object)
     * @since 20.3
     */
    public static native boolean hasExceptionStackTrace(Object receiver);
    /**
     * Returns the exception stack trace of the receiver that is of type exception. Returns an
     * {@link #hasArrayElements(Object) array} of objects with potentially
     * {@link #hasExecutableName(Object) executable name}, {@link #hasDeclaringMetaObject(Object)
     * declaring meta object} and {@link #hasSourceLocation(Object) source location} of the caller.
     * Throws {@code UnsupportedMessageException} when the receiver is not an
     * {@link #isException(Object) exception} or has no stack trace. Invoking this message or
     * accessing the stack trace elements array must not cause any observable side-effects.
     *
     * @see #isException(Object)
     * @see #hasExceptionStackTrace(Object)
     * @since 20.3
     */
    public static native Object getExceptionStackTrace(Object receiver) throws UnsupportedMessageException;

    // endregion Exception Messages
}
