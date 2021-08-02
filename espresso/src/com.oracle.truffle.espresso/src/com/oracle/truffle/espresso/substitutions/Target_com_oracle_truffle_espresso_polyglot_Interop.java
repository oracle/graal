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
package com.oracle.truffle.espresso.substitutions;

import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoSubstitutions
public final class Target_com_oracle_truffle_espresso_polyglot_Interop {

    private static final InteropLibrary UNCACHED = InteropLibrary.getUncached();

    /**
     * Returns <code>true</code> if the receiver represents a <code>null</code> like value, else
     * <code>false</code>. Most object oriented languages have one or many values representing null
     * values. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#isNull(Object)
     */
    @Substitution
    abstract static class IsNull extends SubstitutionNode {
        static final int LIMIT = 4;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean cached(@JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.isNull(unwrap(receiver));
        }
    }

    // region Boolean Messages

    /**
     * Returns <code>true</code> if the receiver represents a <code>boolean</code> like value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#isBoolean(Object)
     */
    @Substitution
    abstract static class IsBoolean extends SubstitutionNode {
        static final int LIMIT = 4;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(@JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.isBoolean(unwrap(receiver));
        }
    }

    /**
     * Returns the Java boolean value if the receiver represents a
     * {@link InteropLibrary#isBoolean(Object) boolean} like value.
     *
     * @see InteropLibrary#asBoolean(Object)
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    abstract static class AsBoolean extends SubstitutionNode {
        static final int LIMIT = 4;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(@JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile error) {
            try {
                return interop.asBoolean(unwrap(receiver));
            } catch (InteropException e) {
                error.enter();
                throw throwInteropException(e, getMeta());
            }
        }
    }

    // endregion Boolean Messages

    // region String Messages

    /**
     * Returns <code>true</code> if the receiver represents a <code>string</code> value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#isString(Object)
     */
    @Substitution
    public static boolean isString(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.isString(unwrap(receiver));
    }

    /**
     * Returns the Java string value if the receiver represents a
     * {@link InteropLibrary#isString(Object) string} like value.
     *
     * @see InteropLibrary#asString(Object)
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static @JavaType(String.class) StaticObject asString(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return meta.toGuestString(UNCACHED.asString(unwrap(receiver)));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion String Messages

    // region Number Messages

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#isNumber(Object)
     */
    @Substitution
    public static boolean isNumber(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.isNumber(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java byte primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInByte(Object)
     */
    @Substitution
    public static boolean fitsInByte(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInByte(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java short primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInShort(Object)
     */
    @Substitution
    public static boolean fitsInShort(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInShort(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java int primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInInt(Object)
     */
    @Substitution
    public static boolean fitsInInt(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInInt(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java long primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInLong(Object)
     */
    @Substitution
    public static boolean fitsInLong(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInLong(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java float primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInFloat(Object)
     */
    @Substitution
    public static boolean fitsInFloat(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInFloat(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java double primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInDouble(Object)
     */
    @Substitution
    public static boolean fitsInDouble(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInDouble(unwrap(receiver));
    }

    /**
     * Returns the receiver value as Java byte primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asByte(Object)
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static byte asByte(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asByte(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the receiver value as Java short primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asShort(Object)
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static short asShort(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asShort(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the receiver value as Java int primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asInt(Object)
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static int asInt(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asInt(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the receiver value as Java long primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asLong(Object)
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static long asLong(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asLong(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the receiver value as Java float primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asFloat(Object)
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static float asFloat(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asFloat(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the receiver value as Java double primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asDouble(Object)
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static double asDouble(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asDouble(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion Number Messages

    // region Exception Messages

    /**
     * Returns <code>true</code> if the receiver value represents a throwable exception/error}.
     * Invoking this message does not cause any observable side-effects. Returns <code>false</code>
     * by default.
     * <p>
     * Objects must only return <code>true</code> if they support
     * {@link InteropLibrary#throwException} as well. If this method is implemented then also
     * {@link InteropLibrary#throwException(Object)} must be implemented.
     * <p>
     * The following simplified {@code TryCatchNode} shows how the exceptions should be handled by
     * languages.
     *
     * @see InteropLibrary#isException(Object)
     * @since 19.3
     */
    @Substitution
    public static boolean isException(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.isException(unwrap(receiver));
    }

    /**
     * Throws the receiver object as an exception of the source language, as if it was thrown by the
     * source language itself. Allows rethrowing exceptions caught by another language. If this
     * method is implemented then also {@link InteropLibrary#isException(Object)} must be
     * implemented.
     * <p>
     * Any interop value can be an exception value and export
     * {@link InteropLibrary#throwException(Object)}. The exception thrown by this message must
     * extend {@link com.oracle.truffle.api.exception.AbstractTruffleException}. In future versions
     * this contract will be enforced using an assertion.
     *
     * @see InteropLibrary#throwException(Object)
     * @since 19.3
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static @JavaType(RuntimeException.class) StaticObject throwException(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            throw UNCACHED.throwException(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns {@link ExceptionType exception type} of the receiver. Throws
     * {@code UnsupportedMessageException} when the receiver is not an exception.
     *
     * @see InteropLibrary#getExceptionType(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/ExceptionType;") StaticObject getExceptionType(
                    @JavaType(Object.class) StaticObject receiver,
                    @InjectMeta Meta meta) {
        try {
            ExceptionType exceptionType = UNCACHED.getExceptionType(unwrap(receiver));
            StaticObject staticStorage = meta.polyglot.ExceptionType.tryInitializeAndGetStatics();
            // @formatter:off
            switch (exceptionType) {
                case EXIT          : return meta.polyglot.ExceptionType_EXIT.getObject(staticStorage);
                case INTERRUPT     : return meta.polyglot.ExceptionType_INTERRUPT.getObject(staticStorage);
                case RUNTIME_ERROR : return meta.polyglot.ExceptionType_RUNTIME_ERROR.getObject(staticStorage);
                case PARSE_ERROR   : return meta.polyglot.ExceptionType_PARSE_ERROR.getObject(staticStorage);
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere("Unexpected ExceptionType: ", exceptionType);
            }
            // @formatter:on
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns {@code true} if receiver value represents an incomplete source exception. Throws
     * {@code UnsupportedMessageException} when the receiver is not an
     * {@link InteropLibrary#isException(Object) exception} or the exception is not a
     * {@link ExceptionType#PARSE_ERROR}.
     *
     * @see InteropLibrary#isExceptionIncompleteSource(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static boolean isExceptionIncompleteSource(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.isExceptionIncompleteSource(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns exception exit status of the receiver. Throws {@code UnsupportedMessageException}
     * when the receiver is not an {@link InteropLibrary#isException(Object) exception} of the
     * {@link ExceptionType#EXIT exit type}. A return value zero indicates that the execution of the
     * application was successful, a non-zero value that it failed. The individual interpretation of
     * non-zero values depends on the application.
     *
     * @see InteropLibrary#getExceptionExitStatus(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static int getExceptionExitStatus(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.getExceptionExitStatus(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns {@code true} if the receiver is an exception with an attached internal cause.
     * Invoking this message does not cause any observable side-effects. Returns {@code false} by
     * default.
     *
     * @see InteropLibrary#hasExceptionCause(Object)
     * @since 20.3
     */
    @Substitution
    public static boolean hasExceptionCause(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.hasExceptionCause(unwrap(receiver));
    }

    /**
     * Returns the internal cause of the receiver. Throws {@code UnsupportedMessageException} when
     * the receiver is not an {@link InteropLibrary#isException(Object) exception} or has no
     * internal cause. The return value of this message is guaranteed to return <code>true</code>
     * for {@link InteropLibrary#isException(Object)}.
     *
     * @see InteropLibrary#getExceptionCause(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static @JavaType(Object.class) StaticObject getExceptionCause(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            Object cause = UNCACHED.getExceptionCause(unwrap(receiver));
            assert UNCACHED.isException(cause);
            assert !UNCACHED.isNull(cause);
            if (cause instanceof StaticObject) {
                return (StaticObject) cause; // Already typed, do not re-type.
            }
            // The cause must be an exception, wrap it as ForeignException.
            return StaticObject.createForeignException(meta, cause, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns {@code true} if the receiver is an exception that has an exception message. Invoking
     * this message does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see InteropLibrary#hasExceptionMessage(Object)
     * @since 20.3
     */
    @Substitution
    public static boolean hasExceptionMessage(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.hasExceptionMessage(unwrap(receiver));
    }

    /**
     * Returns exception message of the receiver. Throws {@code UnsupportedMessageException} when
     * the receiver is not an exception or has no exception message. The return value of this
     * message is guaranteed to return <code>true</code> for
     * {@link InteropLibrary#isString(Object)}.
     *
     * @see InteropLibrary#getExceptionMessage(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static @JavaType(Object.class) StaticObject getExceptionMessage(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            Object message = UNCACHED.getExceptionMessage(unwrap(receiver));
            assert UNCACHED.isString(message);
            if (message instanceof StaticObject) {
                return (StaticObject) message;
            }
            // TODO(peterssen): Cannot wrap as String even if the foreign object is String-like.
            // Executing String methods, that rely on it having a .value field is not supported yet
            // in Espresso.
            return StaticObject.createForeign(meta.getEspressoLanguage(), meta.java_lang_Object, message, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns {@code true} if the receiver is an exception and has a stack trace. Invoking this
     * message does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see InteropLibrary#hasExceptionStackTrace(Object)
     * @since 20.3
     */
    @Substitution
    public static boolean hasExceptionStackTrace(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.hasExceptionStackTrace(unwrap(receiver));
    }

    /**
     * Returns the exception stack trace of the receiver that is of type exception. Returns an
     * {@link InteropLibrary#hasArrayElements(Object) array} of objects with potentially
     * {@link InteropLibrary#hasExecutableName(Object) executable name},
     * {@link InteropLibrary#hasDeclaringMetaObject(Object) declaring meta object} and
     * {@link InteropLibrary#hasSourceLocation(Object) source location} of the caller. Throws
     * {@code UnsupportedMessageException} when the receiver is not an
     * {@link InteropLibrary#isException(Object) exception} or has no stack trace. Invoking this
     * message or accessing the stack trace elements array must not cause any observable
     * side-effects.
     *
     * @see InteropLibrary#getExceptionStackTrace(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static @JavaType(Object.class) StaticObject getExceptionStackTrace(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            Object stackTrace = UNCACHED.getExceptionStackTrace(unwrap(receiver));
            if (stackTrace instanceof StaticObject) {
                return (StaticObject) stackTrace;
            }
            // Return foreign object as an opaque j.l.Object.
            return StaticObject.createForeign(meta.getEspressoLanguage(), meta.java_lang_Object, stackTrace, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion Exception Messages

    // region Array Messages

    /**
     * Returns <code>true</code> if the receiver may have array elements. Therefore, At least one of
     * {@link InteropLibrary#readArrayElement(Object, long)},
     * {@link InteropLibrary#writeArrayElement(Object, long, Object)},
     * {@link InteropLibrary#removeArrayElement(Object, long)} must not throw {#link
     * {@link UnsupportedMessageException}. For example, the contents of an array or list
     * datastructure could be interpreted as array elements. Invoking this message does not cause
     * any observable side-effects. Returns <code>false</code> by default.
     *
     * @see InteropLibrary#hasArrayElements(Object)
     * @since 19.0
     */
    @Substitution
    public static boolean hasArrayElements(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.hasArrayElements(unwrap(receiver));
    }

    /**
     * Reads the value of an array element by index. This method must have not observable
     * side-effect.
     *
     * @see InteropLibrary#readArrayElement(Object, long)
     * @since 19.0
     */
    @Substitution
    @Throws({UnsupportedMessageException.class, InvalidArrayIndexException.class})
    public static @JavaType(Object.class) StaticObject readArrayElement(@JavaType(Object.class) StaticObject receiver, long index, @InjectMeta Meta meta) {
        try {
            Object value = UNCACHED.readArrayElement(unwrap(receiver), index);
            if (value instanceof StaticObject) {
                return (StaticObject) value;
            }
            /*
             * The foreign object *could* be wrapped into a more precise Java type, but inferring
             * this Java type from the interop "kind" (string, primitive, exception, array...) is
             * ambiguous and inefficient. The caller is responsible to re-wrap or convert the result
             * as needed.
             */
            return StaticObject.createForeign(meta.getEspressoLanguage(), meta.java_lang_Object, value, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the array size of the receiver.
     *
     * @see InteropLibrary#getArraySize(Object)
     * @since 19.0
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static long getArraySize(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.getArraySize(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns <code>true</code> if a given array element is
     * {@link InteropLibrary#readArrayElement(Object, long) readable}. This method may only return
     * <code>true</code> if {@link InteropLibrary#hasArrayElements(Object)} returns
     * <code>true</code> as well. Invoking this message does not cause any observable side-effects.
     * Returns <code>false</code> by default.
     *
     * @see InteropLibrary#isArrayElementReadable(Object, long)
     * @since 19.0
     */
    @Substitution
    public static boolean isArrayElementReadable(@JavaType(Object.class) StaticObject receiver, long index) {
        return UNCACHED.isArrayElementReadable(unwrap(receiver), index);
    }

    /**
     * Writes the value of an array element by index. Writing an array element is allowed if is
     * existing and {@link InteropLibrary#isArrayElementModifiable(Object, long) modifiable}, or not
     * existing and {@link InteropLibrary#isArrayElementInsertable(Object, long) insertable}.
     * <p>
     * This method must have not observable side-effects other than the changed array element.
     *
     * @see InteropLibrary#writeArrayElement(Object, long, Object)
     * @since 19.0
     */
    @Substitution
    @Throws({UnsupportedMessageException.class, UnsupportedTypeException.class, InvalidArrayIndexException.class})
    public static void writeArrayElement(@JavaType(Object.class) StaticObject receiver, long index, @JavaType(Object.class) StaticObject value, @InjectMeta Meta meta) {
        try {
            if (receiver.isEspressoObject()) {
                // Do not throw away the types if the receiver is an Espresso object.
                UNCACHED.writeArrayElement(receiver, index, value);
            } else {
                // Write to foreign array, full unwrap.
                UNCACHED.writeArrayElement(unwrap(receiver), index, unwrap(value));
            }
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Remove an array element from the receiver object. Removing member is allowed if the array
     * element is {@link InteropLibrary#isArrayElementRemovable(Object, long) removable}. This
     * method may only return <code>true</code> if {@link InteropLibrary#hasArrayElements(Object)}
     * returns <code>true</code> as well and
     * {@link InteropLibrary#isArrayElementInsertable(Object, long)} returns <code>false</code>.
     * <p>
     * This method does not have observable side-effects other than the removed array element and
     * shift of remaining elements. If shifting is not supported then the array might allow only
     * removal of last element.
     *
     * @see InteropLibrary#removeArrayElement(Object, long)
     * @since 19.0
     */
    @Substitution
    @Throws({UnsupportedMessageException.class, InvalidArrayIndexException.class})
    public static void removeArrayElement(@JavaType(Object.class) StaticObject receiver, long index, @InjectMeta Meta meta) {
        try {
            UNCACHED.removeArrayElement(unwrap(receiver), index);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns <code>true</code> if a given array element index is existing and
     * {@link InteropLibrary#writeArrayElement(Object, long, Object) writable}. This method may only
     * return <code>true</code> if {@link InteropLibrary#hasArrayElements(Object)} returns
     * <code>true</code> as well and {@link InteropLibrary#isArrayElementInsertable(Object, long)}
     * returns <code>false</code>. Invoking this message does not cause any observable side-effects.
     * Returns <code>false</code> by default.
     *
     * @see InteropLibrary#isArrayElementModifiable(Object, long)
     * @since 19.0
     */
    @Substitution
    public static boolean isArrayElementModifiable(@JavaType(Object.class) StaticObject receiver, long index) {
        return UNCACHED.isArrayElementModifiable(unwrap(receiver), index);
    }

    /**
     * Returns <code>true</code> if a given array element index is not existing and
     * {@link InteropLibrary#writeArrayElement(Object, long, Object) insertable}. This method may
     * only return <code>true</code> if {@link InteropLibrary#hasArrayElements(Object)} returns
     * <code>true</code> as well and {@link InteropLibrary#isArrayElementExisting(Object, long)}}
     * returns <code>false</code>. Invoking this message does not cause any observable side-effects.
     * Returns <code>false</code> by default.
     *
     * @see InteropLibrary#isArrayElementInsertable(Object, long)
     * @since 19.0
     */
    @Substitution
    public static boolean isArrayElementInsertable(@JavaType(Object.class) StaticObject receiver, long index) {
        return UNCACHED.isArrayElementModifiable(unwrap(receiver), index);
    }

    /**
     * Returns <code>true</code> if a given array element index is existing and
     * {@link InteropLibrary#removeArrayElement(Object, long) removable}. This method may only
     * return <code>true</code> if {@link InteropLibrary#hasArrayElements(Object)} returns
     * <code>true</code> as well and {@link InteropLibrary#isArrayElementInsertable(Object, long)}}
     * returns <code>false</code>. Invoking this message does not cause any observable side-effects.
     * Returns <code>false</code> by default.
     *
     * @see InteropLibrary#isArrayElementRemovable(Object, long)
     * @since 19.0
     */
    @Substitution
    public static boolean isArrayElementRemovable(@JavaType(Object.class) StaticObject receiver, long index) {
        return UNCACHED.isArrayElementRemovable(unwrap(receiver), index);
    }

    // endregion Array Messages

    // region MetaObject Messages

    /**
     * Returns <code>true</code> if the receiver value has a metaobject associated. The metaobject
     * represents a description of the object, reveals its kind and its features. Some information
     * that a metaobject might define includes the base object's type, interface, class, methods,
     * attributes, etc. Should return <code>false</code> when no metaobject is known for this type.
     * Returns <code>false</code> by default.
     * <p>
     * An example, for Java objects the returned metaobject is the {@link Object#getClass() class}
     * instance. In JavaScript this could be the function or class that is associated with the
     * object.
     * <p>
     * Metaobjects for primitive values or values of other languages may be provided using language
     * views. While an object is associated with a metaobject in one language, the metaobject might
     * be a different when viewed from another language.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link InteropLibrary#getMetaObject(Object)} must be implemented.
     *
     * @see InteropLibrary#hasMetaObject(Object)
     * @since 20.1
     */
    @Substitution
    public static boolean hasMetaObject(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.hasMetaObject(unwrap(receiver));
    }

    /**
     * Returns the metaobject that is associated with this value. The metaobject represents a
     * description of the object, reveals its kind and its features. Some information that a
     * metaobject might define includes the base object's type, interface, class, methods,
     * attributes, etc. When no metaobject is known for this type. Throws
     * {@link UnsupportedMessageException} by default.
     * <p>
     * The returned object must return <code>true</code> for
     * {@link InteropLibrary#isMetaObject(Object)} and provide implementations for
     * {@link InteropLibrary#getMetaSimpleName(Object)},
     * {@link InteropLibrary#getMetaQualifiedName(Object)}, and
     * {@link InteropLibrary#isMetaInstance(Object, Object)}. For all values with metaobjects it
     * must at hold that <code>isMetaInstance(getMetaObject(value), value) ==
     * true</code>.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link InteropLibrary#hasMetaObject(Object)} must be implemented.
     *
     * @see InteropLibrary#hasMetaObject(Object)
     * @since 20.1
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static @JavaType(Object.class) StaticObject getMetaObject(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            Object metaObject = UNCACHED.getMetaObject(unwrap(receiver));
            if (metaObject instanceof StaticObject) {
                return (StaticObject) metaObject;
            }
            return StaticObject.createForeign(meta.getEspressoLanguage(), meta.java_lang_Object, metaObject, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Converts the receiver to a human readable {@link InteropLibrary#isString(Object) string}.
     * Each language may have special formating conventions - even primitive values may not follow
     * the traditional Java rules. The format of the returned string is intended to be interpreted
     * by humans not machines and should therefore not be relied upon by machines. By default the
     * receiver class name and its {@link System#identityHashCode(Object) identity hash code} is
     * used as string representation.
     *
     * @param allowSideEffects whether side-effects are allowed in the production of the string.
     * @since 20.1
     */
    @Substitution
    public static @JavaType(Object.class) StaticObject toDisplayString(@JavaType(Object.class) StaticObject receiver, boolean allowSideEffects, @InjectMeta Meta meta) {
        Object displayString = UNCACHED.toDisplayString(unwrap(receiver), allowSideEffects);
        if (displayString instanceof StaticObject) {
            return (StaticObject) displayString;
        }
        return StaticObject.createForeign(meta.getEspressoLanguage(), meta.java_lang_Object, displayString, UNCACHED);
    }

    /**
     * Converts the receiver to a human readable {@link InteropLibrary#isString(Object) string} of
     * the language. Short-cut for
     * <code>{@link InteropLibrary#toDisplayString(Object) toDisplayString}(true)</code>.
     *
     * @see InteropLibrary#toDisplayString(Object, boolean)
     * @since 20.1
     */
    public static @JavaType(Object.class) StaticObject toDisplayString(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        return toDisplayString(receiver, true, meta);
    }

    /**
     * Returns <code>true</code> if the receiver value represents a metaobject. Metaobjects may be
     * values that naturally occur in a language or they may be returned by
     * {@link InteropLibrary#getMetaObject(Object)}. A metaobject represents a description of the
     * object, reveals its kind and its features. If a receiver is a metaobject it is often also
     * {@link InteropLibrary#isInstantiable(Object) instantiable}, but this is not a requirement.
     * <p>
     * <b>Sample interpretations:</b> In Java an instance of the type {@link Class} is a metaobject.
     * In JavaScript any function instance is a metaobject. For example, the metaobject of a
     * JavaScript class is the associated constructor function.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link InteropLibrary#getMetaQualifiedName(Object)},
     * {@link InteropLibrary#getMetaSimpleName(Object)} and
     * {@link InteropLibrary#isMetaInstance(Object, Object)} must be implemented as well.
     *
     * @since 20.1
     */
    @Substitution
    public static boolean isMetaObject(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.isMetaObject(unwrap(receiver));
    }

    /**
     * Returns the qualified name of a metaobject as {@link InteropLibrary#isString(Object) string}.
     * <p>
     * <b>Sample interpretations:</b> The qualified name of a Java class includes the package name
     * and its class name. JavaScript does not have the notion of qualified name and therefore
     * returns the {@link InteropLibrary#getMetaSimpleName(Object) simple name} instead.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link InteropLibrary#isMetaObject(Object)} must be implemented as well.
     *
     * @since 20.1
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static @JavaType(Object.class) StaticObject getMetaQualifiedName(@JavaType(Object.class) StaticObject metaObject, @InjectMeta Meta meta) {
        try {
            Object qualifiedName = UNCACHED.getMetaQualifiedName(unwrap(metaObject));
            if (qualifiedName instanceof StaticObject) {
                return (StaticObject) qualifiedName;
            }
            return StaticObject.createForeign(meta.getEspressoLanguage(), meta.java_lang_Object, qualifiedName, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the simple name of a metaobject as {@link InteropLibrary#isString(Object) string}.
     * <p>
     * <b>Sample interpretations:</b> The simple name of a Java class is the class name.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link InteropLibrary#isMetaObject(Object)} must be implemented as well.
     *
     * @since 20.1
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static @JavaType(Object.class) StaticObject getMetaSimpleName(@JavaType(Object.class) StaticObject metaObject, @InjectMeta Meta meta) {
        try {
            Object simpleName = UNCACHED.getMetaSimpleName(unwrap(metaObject));
            if (simpleName instanceof StaticObject) {
                return (StaticObject) simpleName;
            }
            return StaticObject.createForeign(meta.getEspressoLanguage(), meta.java_lang_Object, simpleName, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns <code>true</code> if the given instance is of the provided receiver metaobject, else
     * <code>false</code>.
     * <p>
     * <b>Sample interpretations:</b> A Java object is an instance of its returned
     * {@link Object#getClass() class}.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link InteropLibrary#isMetaObject(Object)} must be implemented as well.
     *
     * @param instance the instance object to check.
     * @since 20.1
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static boolean isMetaInstance(@JavaType(Object.class) StaticObject receiver, @JavaType(Object.class) StaticObject instance, @InjectMeta Meta meta) {
        try {
            return UNCACHED.isMetaInstance(unwrap(receiver), unwrap(instance));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion MetaObject Messages

    // region Identity Messages

    /**
     * Returns <code>true</code> if two values represent the identical value, else
     * <code>false</code>. Two values are identical if and only if they have specified identity
     * semantics in the target language and refer to the identical instance.
     * <p>
     * By default, an interop value does not support identical comparisons, and will return
     * <code>false</code> for any invocation of this method. Use
     * {@link InteropLibrary#hasIdentity(Object)} to find out whether a receiver supports identity
     * comparisons.
     * <p>
     * This method has the following properties:
     * <ul>
     * <li>It is <b>not</b> <i>reflexive</i>: for any value {@code x},
     * {@code lib.isIdentical(x, x, lib)} may return {@code false} if the object does not support
     * identity, else <code>true</code>. This method is reflexive if {@code x} supports identity. A
     * value supports identity if {@code lib.isIdentical(x, x, lib)} returns <code>true</code>. The
     * method {@link InteropLibrary#hasIdentity(Object)} may be used to document this intent
     * explicitly.
     * <li>It is <i>symmetric</i>: for any values {@code x} and {@code y},
     * {@code lib.isIdentical(x, y, yLib)} returns {@code true} if and only if
     * {@code lib.isIdentical(y, x, xLib)} returns {@code true}.
     * <li>It is <i>transitive</i>: for any values {@code x}, {@code y}, and {@code z}, if
     * {@code lib.isIdentical(x, y, yLib)} returns {@code true} and
     * {@code lib.isIdentical(y, z, zLib)} returns {@code true}, then
     * {@code lib.isIdentical(x, z, zLib)} returns {@code true}.
     * <li>It is <i>consistent</i>: for any values {@code x} and {@code y}, multiple invocations of
     * {@code lib.isIdentical(x, y, yLib)} consistently returns {@code true} or consistently return
     * {@code false}.
     * </ul>
     * <p>
     * Note that the target language identical semantics typically does not map directly to interop
     * identical implementation. Instead target language identity is specified by the language
     * operation, may take multiple other rules into account and may only fallback to interop
     * identical for values without dedicated interop type. For example, in many languages
     * primitives like numbers or strings may be identical, in the target language sense, still
     * identity can only be exposed for objects and non-primitive values. Primitive values like
     * {@link Integer} can never be interop identical to other boxed language integers as this would
     * violate the symmetric property.
     * <p>
     * This method performs double dispatch by forwarding calls to isIdenticalOrUndefined with
     * receiver and other value first and then with reversed parameters if the result was
     * {@link TriState#UNDEFINED undefined}. This allows the receiver and the other value to
     * negotiate identity semantics. This method is supposed to be exported only if the receiver
     * represents a wrapper that forwards messages. In such a case the isIdentical message should be
     * forwarded to the delegate value. Otherwise, the isIdenticalOrUndefined should be exported
     * instead.
     * <p>
     * This method must not cause any observable side-effects.
     *
     * For a full example please refer to the SLEqualNode of the SimpleLanguage example
     * implementation.
     *
     * @since 20.2
     */
    @Substitution
    public static boolean isIdentical(@JavaType(Object.class) StaticObject receiver, @JavaType(Object.class) StaticObject other) {
        return UNCACHED.isIdentical(unwrap(receiver), unwrap(other), UNCACHED);
    }

    /**
     * Returns an identity hash code for the receiver if it has
     * {@link InteropLibrary#hasIdentity(Object) identity}. If the receiver has no identity then an
     * {@link UnsupportedMessageException} is thrown. The identity hash code may be used by
     * languages to store foreign values with identity in an identity hash map.
     * <p>
     * <ul>
     * <li>Whenever it is invoked on the same object more than once during an execution of a guest
     * context, the identityHashCode method must consistently return the same integer. This integer
     * need not remain consistent from one execution context of a guest application to another
     * execution context of the same application.
     * <li>If two objects are the same according to the
     * {@link InteropLibrary#isIdentical(Object, Object, InteropLibrary)} message, then calling the
     * identityHashCode method on each of the two objects must produce the same integer result.
     * <li>As much as is reasonably practical, the identityHashCode message does return distinct
     * integers for objects that are not the same.
     * </ul>
     * This method must not cause any observable side-effects.
     *
     * @see InteropLibrary#identityHashCode(Object)
     * @since 20.2
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static int identityHashCode(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.identityHashCode(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion Identity Messages

    // region Member Messages

    /**
     * Returns <code>true</code> if the receiver may have members. Therefore, at least one of
     * {@link InteropLibrary#readMember(Object, String)},
     * {@link InteropLibrary#writeMember(Object, String, Object)},
     * {@link InteropLibrary#removeMember(Object, String)},
     * {@link InteropLibrary#invokeMember(Object, String, Object...)} must not throw
     * {@link UnsupportedMessageException}. Members are structural elements of a class. For example,
     * a method or field is a member of a class. Invoking this message does not cause any observable
     * side-effects. Returns <code>false</code> by default.
     *
     * @see InteropLibrary#getMembers(Object, boolean)
     * @see InteropLibrary#isMemberReadable(Object, String)
     * @see InteropLibrary#isMemberModifiable(Object, String)
     * @see InteropLibrary#isMemberInvocable(Object, String)
     * @see InteropLibrary#isMemberInsertable(Object, String)
     * @see InteropLibrary#isMemberRemovable(Object, String)
     * @see InteropLibrary#readMember(Object, String)
     * @see InteropLibrary#writeMember(Object, String, Object)
     * @see InteropLibrary#removeMember(Object, String)
     * @see InteropLibrary#invokeMember(Object, String, Object...)
     * @since 19.0
     */
    @Substitution
    public static boolean hasMembers(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.hasMembers(unwrap(receiver));
    }

    /**
     * Returns an array of member name strings. The returned value must return <code>true</code> for
     * {@link InteropLibrary#hasArrayElements(Object)} and every array element must be of type
     * {@link InteropLibrary#isString(Object) string}. The member elements may also provide
     * additional information like {@link InteropLibrary#getSourceLocation(Object) source location}
     * in case of {@link InteropLibrary#isScope(Object) scope} variables, etc.
     *
     * @see InteropLibrary#getMembers(Object)
     * @since 19.0
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static @JavaType(Object.class) StaticObject getMembers(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            Object value = UNCACHED.getMembers(unwrap(receiver));
            if (value instanceof StaticObject) {
                return (StaticObject) value;
            }
            return StaticObject.createForeign(meta.getEspressoLanguage(), meta.java_lang_Object, value, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns <code>true</code> if a given member is
     * {@link InteropLibrary#readMember(Object, String) readable}. This method may only return
     * <code>true</code> if {@link InteropLibrary#hasMembers(Object)} returns <code>true</code> as
     * well and {@link InteropLibrary#isMemberInsertable(Object, String)} returns
     * <code>false</code>. Invoking this message does not cause any observable side-effects. Returns
     * <code>false</code> by default.
     *
     * @see InteropLibrary#isMemberReadable(Object, String)
     * @since 19.0
     */
    @Substitution
    public static boolean isMemberReadable(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member) {
        String hostMember = Meta.toHostStringStatic(member);
        return UNCACHED.isMemberReadable(unwrap(receiver), hostMember);
    }

    /**
     * Reads the value of a given member. If the member is
     * {@link InteropLibrary#isMemberReadable(Object, String) readable} and
     * {@link InteropLibrary#isMemberInvocable(Object, String) invocable} then the result of reading
     * the member is {@link InteropLibrary#isExecutable(Object) executable} and is bound to this
     * receiver. This method must have not observable side-effects unless
     * {@link InteropLibrary#hasMemberReadSideEffects(Object, String)} returns <code>true</code>.
     *
     * @see InteropLibrary#readMember(Object, String)
     * @since 19.0
     */
    @Substitution
    @Throws({UnsupportedMessageException.class, UnknownIdentifierException.class})
    public static @JavaType(Object.class) StaticObject readMember(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member, @InjectMeta Meta meta) {
        try {
            String hostMember = Meta.toHostStringStatic(member);
            Object value = UNCACHED.readMember(unwrap(receiver), hostMember);
            if (value instanceof StaticObject) {
                return (StaticObject) value;
            }
            return StaticObject.createForeign(meta.getEspressoLanguage(), meta.java_lang_Object, value, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns <code>true</code> if a given member is existing and
     * {@link InteropLibrary#writeMember(Object, String, Object) writable}. This method may only
     * return <code>true</code> if {@link InteropLibrary#hasMembers(Object)} returns
     * <code>true</code> as well and {@link InteropLibrary#isMemberInsertable(Object, String)}
     * returns <code>false</code>. Invoking this message does not cause any observable side-effects.
     * Returns <code>false</code> by default.
     *
     * @see InteropLibrary#isMemberModifiable(Object, String)
     * @since 19.0
     */
    @Substitution
    public static boolean isMemberModifiable(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member) {
        String hostMember = Meta.toHostStringStatic(member);
        return UNCACHED.isMemberModifiable(unwrap(receiver), hostMember);
    }

    /**
     * Returns <code>true</code> if a given member is not existing and
     * {@link InteropLibrary#writeMember(Object, String, Object) writable}. This method may only
     * return <code>true</code> if {@link InteropLibrary#hasMembers(Object)} returns
     * <code>true</code> as well and {@link InteropLibrary#isMemberExisting(Object, String)} returns
     * <code>false</code>. Invoking this message does not cause any observable side-effects. Returns
     * <code>false</code> by default.
     *
     * @see InteropLibrary#isMemberInsertable(Object, String)
     * @since 19.0
     */
    @Substitution
    public static boolean isMemberInsertable(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member) {
        String hostMember = Meta.toHostStringStatic(member);
        return UNCACHED.isMemberInsertable(unwrap(receiver), hostMember);
    }

    /**
     * Writes the value of a given member. Writing a member is allowed if is existing and
     * {@link InteropLibrary#isMemberModifiable(Object, String) modifiable}, or not existing and
     * {@link InteropLibrary#isMemberInsertable(Object, String) insertable}.
     *
     * This method must have not observable side-effects other than the changed member unless
     * {@link InteropLibrary#hasMemberWriteSideEffects(Object, String) side-effects} are allowed.
     *
     * @see InteropLibrary#writeMember(Object, String, Object)
     * @since 19.0
     */
    @Substitution
    @Throws({UnsupportedMessageException.class, UnknownIdentifierException.class, UnsupportedTypeException.class})
    public static void writeMember(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member, @JavaType(Object.class) StaticObject value,
                    @InjectMeta Meta meta) {
        String hostMember = Meta.toHostStringStatic(member);
        try {
            if (receiver.isForeignObject()) {
                UNCACHED.writeMember(unwrap(receiver), hostMember, unwrap(value));
            } else {
                // Preserve the value type.
                UNCACHED.writeMember(receiver, hostMember, value);
            }
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns <code>true</code> if a given member is existing and removable. This method may only
     * return <code>true</code> if {@link InteropLibrary#hasMembers(Object)} returns
     * <code>true</code> as well and {@link InteropLibrary#isMemberInsertable(Object, String)}
     * returns <code>false</code>. Invoking this message does not cause any observable side-effects.
     * Returns <code>false</code> by default.
     *
     * @see InteropLibrary#isMemberRemovable(Object, String)
     * @since 19.0
     */
    @Substitution
    public static boolean isMemberRemovable(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member) {
        String hostMember = Meta.toHostStringStatic(member);
        return UNCACHED.isMemberRemovable(unwrap(receiver), hostMember);
    }

    /**
     * Removes a member from the receiver object. Removing member is allowed if is
     * {@link InteropLibrary#isMemberRemovable(Object, String) removable}.
     *
     * This method does not have not observable side-effects other than the removed member.
     *
     * @see InteropLibrary#removeMember(Object, String)
     * @since 19.0
     */
    @Substitution
    @Throws({UnsupportedMessageException.class, UnknownIdentifierException.class})
    public static void removeMember(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member, @InjectMeta Meta meta) {
        String hostMember = Meta.toHostStringStatic(member);
        try {
            UNCACHED.removeMember(unwrap(receiver), hostMember);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns <code>true</code> if a given member is invocable. This method may only return
     * <code>true</code> if {@link InteropLibrary#hasMembers(Object)} returns <code>true</code> as
     * well and {@link InteropLibrary#isMemberInsertable(Object, String)} returns
     * <code>false</code>. Invoking this message does not cause any observable side-effects. Returns
     * <code>false</code> by default.
     *
     * @see InteropLibrary#isMemberInvocable(Object, String)
     * @see InteropLibrary#invokeMember(Object, String, Object...)
     * @since 19.0
     */
    @Substitution
    public static boolean isMemberInvocable(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member) {
        String hostMember = Meta.toHostStringStatic(member);
        return UNCACHED.isMemberInvocable(unwrap(receiver), hostMember);
    }

    /**
     * Invokes a member for a given receiver and arguments.
     *
     * @see InteropLibrary#invokeMember(Object, String, Object...)
     * @since 19.0
     */
    @Substitution
    @Throws({UnsupportedMessageException.class, ArityException.class, UnknownIdentifierException.class, UnsupportedTypeException.class})
    public static @JavaType(Object.class) StaticObject invokeMember(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member,
                    @JavaType(Object[].class) StaticObject arguments,
                    @InjectMeta Meta meta) {
        String hostMember = Meta.toHostStringStatic(member);
        try {
            Object result = UNCACHED.invokeMember(unwrap(receiver), hostMember, getArguments(arguments, receiver.isForeignObject(), meta));
            if (result instanceof StaticObject) {
                return (StaticObject) result;
            }
            return StaticObject.createForeign(meta.getEspressoLanguage(), meta.java_lang_Object, result, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns <code>true</code> if reading a member may cause a side-effect. Invoking this message
     * does not cause any observable side-effects. A member read does not cause any side-effects by
     * default.
     * <p>
     * For instance in JavaScript a property read may have side-effects if the property has a getter
     * function.
     *
     * @see InteropLibrary#hasMemberReadSideEffects(Object, String)
     * @since 19.0
     */
    @Substitution
    public static boolean hasMemberReadSideEffects(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member) {
        String hostMember = Meta.toHostStringStatic(member);
        return UNCACHED.hasMemberReadSideEffects(unwrap(receiver), hostMember);
    }

    /**
     * Returns <code>true</code> if writing a member may cause a side-effect, besides the write
     * operation of the member. Invoking this message does not cause any observable side-effects. A
     * member write does not cause any side-effects by default.
     * <p>
     * For instance in JavaScript a property write may have side-effects if the property has a
     * setter function.
     *
     * @see InteropLibrary#hasMemberWriteSideEffects(Object, String)
     * @since 19.0
     */
    @Substitution
    public static boolean hasMemberWriteSideEffects(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member) {
        String hostMember = Meta.toHostStringStatic(member);
        return UNCACHED.hasMemberWriteSideEffects(unwrap(receiver), hostMember);
    }

    // endregion Member Messages

    // region Pointer Messages

    /**
     * Returns <code>true</code> if the receiver value represents a native pointer. Native pointers
     * are represented as 64 bit pointers. Invoking this message does not cause any observable
     * side-effects. Returns <code>false</code> by default.
     * <p>
     * It is expected that objects should only return <code>true</code> if the native pointer value
     * corresponding to this object already exists, and obtaining it is a cheap operation. If an
     * object can be transformed to a pointer representation, but this hasn't happened yet, the
     * object is expected to return <code>false</code> with
     * {@link InteropLibrary#isPointer(Object)}, and wait for the
     * {@link InteropLibrary#toNative(Object)} message to trigger the transformation.
     *
     * @see InteropLibrary#isPointer(Object)
     * @since 19.0
     */
    @Substitution
    public static boolean isPointer(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.isPointer(unwrap(receiver));
    }

    /**
     * Returns the pointer value as long value if the receiver represents a pointer like value.
     *
     * @see InteropLibrary#asPointer(Object)
     * @since 19.0
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static long asPointer(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asPointer(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Attempts to transform a receiver to a value that represents a raw native pointer. After a
     * successful transformation, the provided receiver returns true for
     * {@link InteropLibrary#isPointer(Object)} and can be unwrapped using the
     * {@link InteropLibrary#asPointer(Object)} message. If transformation cannot be done
     * {@link InteropLibrary#isPointer(Object)} will keep returning false.
     *
     * @see InteropLibrary#toNative(Object)
     * @since 19.0
     */
    @Substitution
    public static void toNative(@JavaType(Object.class) StaticObject receiver) {
        UNCACHED.toNative(unwrap(receiver));
    }

    // endregion Pointer Messages

    // region Executable Messages

    /**
     * Returns <code>true</code> if the receiver represents an <code>executable</code> value, else
     * <code>false</code>. Functions, methods or closures are common examples of executable values.
     * Invoking this message does not cause any observable side-effects. Note that receiver values
     * which are {@link InteropLibrary#isExecutable(Object) executable} might also be
     * {@link InteropLibrary#isInstantiable(Object) instantiable}.
     *
     * @see InteropLibrary#isExecutable(Object)
     * @since 19.0
     */
    @Substitution
    public static boolean isExecutable(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.isExecutable(unwrap(receiver));
    }

    /**
     * Executes an executable value with the given arguments.
     *
     * @see InteropLibrary#execute(Object, Object...)
     * @since 19.0
     */
    @Substitution
    @Throws({UnsupportedTypeException.class, ArityException.class, UnsupportedMessageException.class})
    public static @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver, @JavaType(Object[].class) StaticObject arguments, @InjectMeta Meta meta) {
        try {
            Object result = UNCACHED.execute(unwrap(receiver), getArguments(arguments, receiver.isForeignObject(), meta));
            if (result instanceof StaticObject) {
                return (StaticObject) result;
            }
            return StaticObject.createForeign(meta.getEspressoLanguage(), meta.java_lang_Object, result, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion Executable Messages

    // region Instantiable Messages

    /**
     * Returns <code>true</code> if the receiver represents an <code>instantiable</code> value, else
     * <code>false</code>. Contructors or {@link InteropLibrary#isMetaObject(Object) metaobjects}
     * are typical examples of instantiable values. Invoking this message does not cause any
     * observable side-effects. Note that receiver values which are
     * {@link InteropLibrary#isExecutable(Object) executable} might also be
     * {@link InteropLibrary#isInstantiable(Object) instantiable}.
     *
     * @see InteropLibrary#isInstantiable(Object)
     * @since 19.0
     */
    @Substitution
    public static boolean isInstantiable(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.isInstantiable(unwrap(receiver));
    }

    /**
     * Instantiates the receiver value with the given arguments. The returned object must be
     * initialized correctly according to the language specification (e.g. by calling the
     * constructor or initialization routine).
     *
     * @see InteropLibrary#instantiate(Object, Object...)
     * @since 19.0
     */
    @Substitution
    @Throws({UnsupportedTypeException.class, ArityException.class, UnsupportedMessageException.class})
    public static @JavaType(Object.class) StaticObject instantiate(@JavaType(Object.class) StaticObject receiver, @JavaType(Object[].class) StaticObject arguments, @InjectMeta Meta meta) {
        try {
            Object result = UNCACHED.instantiate(unwrap(receiver), getArguments(arguments, receiver.isForeignObject(), meta));
            if (result instanceof StaticObject) {
                return (StaticObject) result;
            }
            return StaticObject.createForeign(meta.getEspressoLanguage(), meta.java_lang_Object, result, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion Instantiable Messages

    // region StackFrame Messages

    /**
     * Returns {@code true} if the receiver has an executable name. Invoking this message does not
     * cause any observable side-effects. Returns {@code false} by default.
     *
     * @see InteropLibrary#getExecutableName(Object)
     * @since 20.3
     */
    @Substitution
    public static boolean hasExecutableName(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.hasExecutableName(unwrap(receiver));
    }

    /**
     * Returns executable name of the receiver. Throws {@code UnsupportedMessageException} when the
     * receiver is has no {@link InteropLibrary#hasExecutableName(Object) executable name}. The
     * return value is an interop value that is guaranteed to return <code>true</code> for
     * {@link InteropLibrary#isString(Object)}.
     *
     * @see InteropLibrary#hasExecutableName(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static @JavaType(Object.class) StaticObject getExecutableName(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            Object result = UNCACHED.getExecutableName(unwrap(receiver));
            if (result instanceof StaticObject) {
                return (StaticObject) result;
            }
            return StaticObject.createForeign(meta.getEspressoLanguage(), meta.java_lang_Object, result, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns {@code true} if the receiver has a declaring meta object. The declaring meta object
     * is the meta object of the executable or meta object that declares the receiver value.
     * Invoking this message does not cause any observable side-effects. Returns {@code false} by
     * default.
     *
     * @see InteropLibrary#hasDeclaringMetaObject(Object)
     * @since 20.3
     */
    @Substitution
    public static boolean hasDeclaringMetaObject(@JavaType(Object.class) StaticObject receiver) {
        return UNCACHED.hasDeclaringMetaObject(unwrap(receiver));
    }

    /**
     * Returns declaring meta object. The declaring meta object is the meta object of declaring
     * executable or meta object. Throws {@code UnsupportedMessageException} when the receiver is
     * has no {@link InteropLibrary#hasDeclaringMetaObject(Object) declaring meta object}. The
     * return value is an interop value that is guaranteed to return <code>true</code> for
     * {@link InteropLibrary#isMetaObject(Object)}.
     *
     * @see InteropLibrary#getDeclaringMetaObject(Object)
     * @see InteropLibrary#hasDeclaringMetaObject(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    public static @JavaType(Object.class) StaticObject getDeclaringMetaObject(@JavaType(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            Object result = UNCACHED.getDeclaringMetaObject(unwrap(receiver));
            if (result instanceof StaticObject) {
                return (StaticObject) result;
            }
            return StaticObject.createForeign(meta.getEspressoLanguage(), meta.java_lang_Object, result, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion StackFrame Messages

    // region Buffer Messages

    /**
     * Returns {@code true} if the receiver may have buffer elements.
     *
     * <p>
     * If this message returns {@code true}, then {@link InteropLibrary#getBufferSize(Object)},
     * {@link InteropLibrary#readBufferByte(Object, long)},
     * {@link InteropLibrary#readBufferShort(Object, ByteOrder, long)},
     * {@link InteropLibrary#readBufferInt(Object, ByteOrder, long)},
     * {@link InteropLibrary#readBufferLong(Object, ByteOrder, long)},
     * {@link InteropLibrary#readBufferFloat(Object, ByteOrder, long)} and
     * {@link InteropLibrary#readBufferDouble(Object, ByteOrder, long)} must not throw
     * {@link UnsupportedMessageException}.
     *
     * <p>
     * Invoking this message does not cause any observable side-effects.
     *
     * @since 21.1
     */
    @Substitution
    @ReportPolymorphism
    abstract static class HasBufferElements extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.hasBufferElements(unwrap(receiver));
        }
    }

    /**
     * Returns the buffer size of the receiver, in bytes.
     *
     * <p>
     * Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#getBufferSize(Object)
     * @since 21.1
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    abstract static class GetBufferSize extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract long execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        long doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.getBufferSize(unwrap(receiver));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropException(e, getMeta());
            }
        }
    }

    /**
     * Returns {@code true} if the receiver is a modifiable buffer.
     * <p>
     * If this message returns {@code true}, then {@link InteropLibrary#getBufferSize(Object)},
     * {@link InteropLibrary#writeBufferByte(Object, long, byte)},
     * {@link InteropLibrary#writeBufferShort(Object, ByteOrder, long, short)},
     * {@link InteropLibrary#writeBufferInt(Object, ByteOrder, long, int)},
     * {@link InteropLibrary#writeBufferLong(Object, ByteOrder, long, long)},
     * {@link InteropLibrary#writeBufferFloat(Object, ByteOrder, long, float)} and
     * {@link InteropLibrary#writeBufferDouble(Object, ByteOrder, long, double)} must not throw
     * {@link UnsupportedMessageException}.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     * <p>
     * By default, it returns {@code false} if {@link InteropLibrary#hasBufferElements(Object)}
     * return {@code true}, and throws {@link UnsupportedMessageException} otherwise.
     *
     * @see InteropLibrary#isBufferWritable(Object)
     * @since 21.1
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    abstract static class IsBufferWritable extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.isBufferWritable(unwrap(receiver));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropException(e, getMeta());
            }
        }
    }

    /**
     * Reads the byte from the receiver object at the given byte offset from the start of the
     * buffer.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     *
     * <p>
     * Throws InvalidBufferOffsetException if and only if
     * <code>byteOffset < 0 || byteOffset >= </code>{@link InteropLibrary#getBufferSize(Object)}
     * <p>
     * Throws UnsupportedMessageException if and only if either
     * {@link InteropLibrary#hasBufferElements(Object)} returns {@code false} returns {@code false}
     *
     * @since 21.1
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/InvalidBufferOffsetException;")
    })
    abstract static class ReadBufferByte extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract byte execute(@JavaType(Object.class) StaticObject receiver, long byteOffset);

        @Specialization
        byte doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        long byteOffset,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.readBufferByte(unwrap(receiver), byteOffset);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropException(e, getMeta());
            }
        }
    }

    /**
     * Writes the given byte from the receiver object at the given byte offset from the start of the
     * buffer.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     *
     * <p>
     * Throws InvalidBufferOffsetException if and only if
     * <code>byteOffset < 0 || byteOffset >= </code>{@link InteropLibrary#getBufferSize(Object)}
     * <p>
     * Throws UnsupportedMessageException if and only if either
     * {@link InteropLibrary#hasBufferElements(Object)} or {@link InteropLibrary#isBufferWritable}
     * returns {@code false}
     *
     * @since 21.1
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/InvalidBufferOffsetException;")
    })
    abstract static class WriteBufferByte extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract void execute(@JavaType(Object.class) StaticObject receiver, long byteOffset, byte value);

        @Specialization
        void doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        long byteOffset,
                        byte value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            try {
                interop.writeBufferByte(unwrap(receiver), byteOffset, value);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropException(e, getMeta());
            }
        }
    }

    static final class ByteOrderUtils {
        @TruffleBoundary
        static @JavaType(ByteOrder.class) StaticObject getLittleEndian(EspressoContext context) {
            Meta meta = context.getMeta();
            StaticObject staticStorage = meta.java_nio_ByteOrder.tryInitializeAndGetStatics();
            return meta.java_nio_ByteOrder_LITTLE_ENDIAN.getObject(staticStorage);
        }
    }

    /**
     * Reads the short from the receiver object in the given byte order at the given byte offset
     * from the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     *
     * <p>
     * Returns the short at the given byte offset from the start of the buffer.
     *
     * <p>
     * Throws InvalidBufferOffsetException if and only if
     * <code>byteOffset < 0 || byteOffset >= {@link InteropLibrary#getBufferSize(Object)} - 1</code>
     * <p>
     * Throws UnsupportedMessageException if and only if
     * {@link InteropLibrary#hasBufferElements(Object)} returns {@code false}
     *
     * @since 21.1
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/InvalidBufferOffsetException;")
    })
    @ImportStatic(ByteOrderUtils.class)
    abstract static class ReadBufferShort extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract short execute(@JavaType(Object.class) StaticObject receiver, @JavaType(ByteOrder.class) StaticObject order, long byteOffset);

        @Specialization
        short doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(ByteOrder.class) StaticObject order,
                        long byteOffset,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                return interop.readBufferShort(unwrap(receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropException(e, getMeta());
            }
        }
    }

    /**
     * Writes the given short from the receiver object in the given byte order at the given byte
     * offset from the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     *
     * <p>
     * Throws InvalidBufferOffsetException if and only if
     * <code>byteOffset < 0 || byteOffset >= {@link InteropLibrary#getBufferSize(Object)} - 1</code>
     * <p>
     * Throws UnsupportedMessageException if and only if either
     * {@link InteropLibrary#hasBufferElements(Object)} or {@link InteropLibrary#isBufferWritable}
     * returns {@code false}
     *
     * @since 21.1
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/InvalidBufferOffsetException;")
    })
    @ImportStatic(ByteOrderUtils.class)
    abstract static class WriteBufferShort extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract void execute(@JavaType(Object.class) StaticObject receiver, @JavaType(ByteOrder.class) StaticObject order, long byteOffset, short value);

        @Specialization
        void doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(ByteOrder.class) StaticObject order,
                        long byteOffset,
                        short value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                interop.writeBufferShort(unwrap(receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset,
                                value);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropException(e, getMeta());
            }
        }
    }

    /**
     * Reads the int from the receiver object in the given byte order at the given byte offset from
     * the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     *
     * <p>
     * Returns the int at the given byte offset from the start of the buffer
     * <p>
     * Throws InvalidBufferOffsetException if and only if
     * <code>byteOffset < 0 || byteOffset >= {@link InteropLibrary#getBufferSize(Object)} - 3</code>
     * <p>
     * Throws UnsupportedMessageException if and only if
     * {@link InteropLibrary#hasBufferElements(Object)} returns {@code false}
     *
     * @since 21.1
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/InvalidBufferOffsetException;")
    })
    @ImportStatic(ByteOrderUtils.class)
    abstract static class ReadBufferInt extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract int execute(@JavaType(Object.class) StaticObject receiver, @JavaType(ByteOrder.class) StaticObject order, long byteOffset);

        @Specialization
        int doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(ByteOrder.class) StaticObject order,
                        long byteOffset,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                return interop.readBufferInt(unwrap(receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropException(e, getMeta());
            }
        }
    }

    /**
     * Writes the given int from the receiver object in the given byte order at the given byte
     * offset from the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     *
     * <p>
     * Throws InvalidBufferOffsetException if and only if
     * <code>byteOffset < 0 || byteOffset >= {@link InteropLibrary#getBufferSize(Object)} - 3</code>
     * <p>
     * Throws UnsupportedMessageException if and only if either
     * {@link InteropLibrary#hasBufferElements(Object)} or {@link InteropLibrary#isBufferWritable}
     * returns {@code false}
     *
     * @since 21.1
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/InvalidBufferOffsetException;")
    })
    @ImportStatic(ByteOrderUtils.class)
    abstract static class WriteBufferInt extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract void execute(@JavaType(Object.class) StaticObject receiver, @JavaType(ByteOrder.class) StaticObject order, long byteOffset, int value);

        @Specialization
        void doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(ByteOrder.class) StaticObject order,
                        long byteOffset,
                        int value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                interop.writeBufferInt(unwrap(receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset,
                                value);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropException(e, getMeta());
            }
        }
    }

    /**
     * Reads the long from the receiver object in the given byte order at the given byte offset from
     * the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     *
     * Returns the int at the given byte offset from the start of the buffer
     * <p>
     * Throws InvalidBufferOffsetException if and only if
     * <code>byteOffset < 0 || byteOffset >= {@link InteropLibrary#getBufferSize(Object)} - 7</code>
     * <p>
     * Throws UnsupportedMessageException if and only if
     * {@link InteropLibrary#hasBufferElements(Object)} returns {@code false}
     *
     * @since 21.1
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/InvalidBufferOffsetException;")
    })
    @ImportStatic(ByteOrderUtils.class)
    abstract static class ReadBufferLong extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract long execute(@JavaType(Object.class) StaticObject receiver, @JavaType(ByteOrder.class) StaticObject order, long byteOffset);

        @Specialization
        long doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(ByteOrder.class) StaticObject order,
                        long byteOffset,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                return interop.readBufferLong(unwrap(receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropException(e, getMeta());
            }
        }
    }

    /**
     * Writes the given long from the receiver object in the given byte order at the given byte
     * offset from the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     *
     * <p>
     * Throws InvalidBufferOffsetException if and only if
     * <code>byteOffset < 0 || byteOffset >= {@link InteropLibrary#getBufferSize(Object)} - 7</code>
     * <p>
     * Throws UnsupportedMessageException if and only if either
     * {@link InteropLibrary#hasBufferElements(Object)} or {@link InteropLibrary#isBufferWritable}
     * returns {@code false}
     *
     * @since 21.1
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/InvalidBufferOffsetException;")
    })
    @ImportStatic(ByteOrderUtils.class)
    abstract static class WriteBufferLong extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract void execute(@JavaType(Object.class) StaticObject receiver, @JavaType(ByteOrder.class) StaticObject order, long byteOffset, long value);

        @Specialization
        void doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(ByteOrder.class) StaticObject order,
                        long byteOffset,
                        long value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                interop.writeBufferLong(unwrap(receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset,
                                value);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropException(e, getMeta());
            }
        }
    }

    /**
     * Reads the float from the receiver object in the given byte order at the given byte offset
     * from the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     *
     * Returns the float at the given byte offset from the start of the buffer
     * <p>
     * Throws InvalidBufferOffsetException if and only if
     * <code>byteOffset < 0 || byteOffset >= {@link InteropLibrary#getBufferSize(Object)} - 3</code>
     * <p>
     * Throws UnsupportedMessageException if and only if
     * {@link InteropLibrary#hasBufferElements(Object)} returns {@code false}
     *
     * @since 21.1
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/InvalidBufferOffsetException;")
    })
    @ImportStatic(ByteOrderUtils.class)
    abstract static class ReadBufferFloat extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract float execute(@JavaType(Object.class) StaticObject receiver, @JavaType(ByteOrder.class) StaticObject order, long byteOffset);

        @Specialization
        float doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(ByteOrder.class) StaticObject order,
                        long byteOffset,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                return interop.readBufferFloat(unwrap(receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropException(e, getMeta());
            }
        }
    }

    /**
     * Writes the given float from the receiver object in the given byte order at the given byte
     * offset from the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     *
     * <p>
     * Throws InvalidBufferOffsetException if and only if
     * <code>byteOffset < 0 || byteOffset >= {@link InteropLibrary#getBufferSize(Object)} - 3</code>
     * <p>
     * Throws UnsupportedMessageException if and only if either
     * {@link InteropLibrary#hasBufferElements(Object)} or {@link InteropLibrary#isBufferWritable}
     * returns {@code false}
     *
     * @since 21.1
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/InvalidBufferOffsetException;")
    })
    @ImportStatic(ByteOrderUtils.class)
    abstract static class WriteBufferFloat extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract void execute(@JavaType(Object.class) StaticObject receiver, @JavaType(ByteOrder.class) StaticObject order, long byteOffset, float value);

        @Specialization
        void doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(ByteOrder.class) StaticObject order,
                        long byteOffset,
                        float value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                interop.writeBufferFloat(unwrap(receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset,
                                value);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropException(e, getMeta());
            }
        }
    }

    /**
     * Reads the double from the receiver object in the given byte order at the given byte offset
     * from the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     *
     * Returns the double at the given byte offset from the start of the buffer
     * <p>
     * Throws InvalidBufferOffsetException if and only if
     * <code>byteOffset < 0 || byteOffset >= {@link InteropLibrary#getBufferSize(Object)} - 7</code>
     * <p>
     * Throws UnsupportedMessageException if and only if
     * {@link InteropLibrary#hasBufferElements(Object)} returns {@code false}
     *
     * @since 21.1
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/InvalidBufferOffsetException;")
    })
    @ImportStatic(ByteOrderUtils.class)
    abstract static class ReadBufferDouble extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract double execute(@JavaType(Object.class) StaticObject receiver, @JavaType(ByteOrder.class) StaticObject order, long byteOffset);

        @Specialization
        double doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(ByteOrder.class) StaticObject order,
                        long byteOffset,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,

                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                return interop.readBufferDouble(unwrap(receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropException(e, getMeta());
            }
        }
    }

    /**
     * Writes the given double from the receiver object in the given byte order at the given byte
     * offset from the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     *
     * <p>
     * Throws InvalidBufferOffsetException if and only if
     * <code>byteOffset < 0 || byteOffset >= {@link InteropLibrary#getBufferSize(Object)} - 7</code>
     * <p>
     * Throws UnsupportedMessageException if and only if either
     * {@link InteropLibrary#hasBufferElements(Object)} or {@link InteropLibrary#isBufferWritable}
     * returns {@code false}
     *
     * @since 21.1
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/InvalidBufferOffsetException;")
    })
    @ImportStatic(ByteOrderUtils.class)
    abstract static class WriteBufferDouble extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract void execute(@JavaType(Object.class) StaticObject receiver, @JavaType(ByteOrder.class) StaticObject order, long byteOffset, double value);

        @Specialization
        void doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(ByteOrder.class) StaticObject order,
                        long byteOffset,
                        double value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                interop.writeBufferDouble(unwrap(receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset,
                                value);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropException(e, getMeta());
            }
        }
    }

    // endregion Buffer Messages

    private static Object unwrap(StaticObject receiver) {
        return receiver.isForeignObject() ? receiver.rawForeignObject() : receiver;
    }

    private static StaticObject wrapForeignException(Throwable throwable, Meta meta) {
        assert UNCACHED.isException(throwable);
        assert throwable instanceof AbstractTruffleException;
        return StaticObject.createForeignException(meta, throwable, UNCACHED);
    }

    @TruffleBoundary
    private static RuntimeException throwInteropException(InteropException e, Meta meta) {
        if (e instanceof UnsupportedMessageException) {
            Throwable cause = e.getCause();
            assert cause == null || cause instanceof AbstractTruffleException;
            StaticObject exception = (cause == null)
                            // UnsupportedMessageException.create()
                            ? (StaticObject) meta.polyglot.UnsupportedMessageException_create.invokeDirect(null)
                            // UnsupportedMessageException.create(Throwable cause)
                            : (StaticObject) meta.polyglot.UnsupportedMessageException_create_Throwable.invokeDirect(null, wrapForeignException(cause, meta));
            throw EspressoException.wrap(exception, meta);
        }

        if (e instanceof UnknownIdentifierException) {
            StaticObject unknownIdentifier = meta.toGuestString(((UnknownIdentifierException) e).getUnknownIdentifier());
            Throwable cause = e.getCause();
            StaticObject exception = (cause == null || !(cause instanceof AbstractTruffleException))
                            // UnknownIdentifierException.create(String unknownIdentifier)
                            ? (StaticObject) meta.polyglot.UnknownIdentifierException_create_String.invokeDirect(null, unknownIdentifier)
                            // UnknownIdentifierException.create(String unknownIdentifier, Throwable
                            // cause)
                            : (StaticObject) meta.polyglot.UnknownIdentifierException_create_String_Throwable.invokeDirect(null, unknownIdentifier, wrapForeignException(cause, meta));
            throw EspressoException.wrap(exception, meta);
        }

        if (e instanceof ArityException) {
            int expectedArity = ((ArityException) e).getExpectedMinArity();
            int actualArity = ((ArityException) e).getActualArity();
            Throwable cause = e.getCause();
            StaticObject exception = (cause == null || !(cause instanceof AbstractTruffleException))
                            // ArityException.create(int expectedArity, int actualArity)
                            ? (StaticObject) meta.polyglot.ArityException_create_int_int.invokeDirect(null, expectedArity, actualArity)
                            // ArityException.create(int expectedArity, int actualArity, Throwable
                            // cause)
                            : (StaticObject) meta.polyglot.ArityException_create_int_int_Throwable.invokeDirect(null, expectedArity, actualArity, wrapForeignException(cause, meta));
            throw EspressoException.wrap(exception, meta);
        }

        if (e instanceof UnsupportedTypeException) {
            Object[] hostValues = ((UnsupportedTypeException) e).getSuppliedValues();
            // Transform suppliedValues[] into a guest Object[].
            StaticObject[] backingArray = new StaticObject[hostValues.length];
            for (int i = 0; i < backingArray.length; i++) {
                Object value = hostValues[i];
                if (value instanceof StaticObject) {
                    backingArray[i] = (StaticObject) value; // no need to re-type
                } else {
                    // TODO(peterssen): Wrap with precise types.
                    backingArray[i] = StaticObject.createForeign(meta.getEspressoLanguage(), meta.java_lang_Object, value, UNCACHED);
                }
            }
            StaticObject suppliedValues = StaticObject.wrap(backingArray, meta);
            StaticObject hint = meta.toGuestString(e.getMessage());
            Throwable cause = e.getCause();
            StaticObject exception = (cause == null || !(cause instanceof AbstractTruffleException))
                            // UnsupportedTypeException.create(Object[] suppliedValues, String hint)
                            ? (StaticObject) meta.polyglot.UnsupportedTypeException_create_Object_array_String.invokeDirect(null, suppliedValues, hint)
                            // UnsupportedTypeException.create(Object[] suppliedValues, String hint,
                            // Throwable cause)
                            : (StaticObject) meta.polyglot.UnsupportedTypeException_create_Object_array_String_Throwable.invokeDirect(null, suppliedValues, hint, wrapForeignException(cause, meta));
            throw EspressoException.wrap(exception, meta);
        }

        if (e instanceof InvalidArrayIndexException) {
            long invalidIndex = ((InvalidArrayIndexException) e).getInvalidIndex();
            Throwable cause = e.getCause();
            StaticObject exception = (cause == null || !(cause instanceof AbstractTruffleException))
                            // InvalidArrayIndexException.create(long invalidIndex)
                            ? (StaticObject) meta.polyglot.InvalidArrayIndexException_create_long.invokeDirect(null, invalidIndex)
                            // InvalidArrayIndexException.create(long invalidIndex, Throwable cause)
                            : (StaticObject) meta.polyglot.InvalidArrayIndexException_create_long_Throwable.invokeDirect(null, invalidIndex, wrapForeignException(cause, meta));
            throw EspressoException.wrap(exception, meta);
        }

        if (e instanceof InvalidBufferOffsetException) {
            long byteOffset = ((InvalidBufferOffsetException) e).getByteOffset();
            long length = ((InvalidBufferOffsetException) e).getLength();
            Throwable cause = e.getCause();
            StaticObject exception = (cause == null || !(cause instanceof AbstractTruffleException))
                            // InvalidArrayIndexException.create(long byteOffset)
                            ? (StaticObject) meta.polyglot.InvalidBufferOffsetException_create_long_long.invokeDirect(null, byteOffset, length)
                            // InvalidArrayIndexException.create(long byteOffset, Throwable cause)
                            : (StaticObject) meta.polyglot.InvalidBufferOffsetException_create_long_long_Throwable.invokeDirect(null, byteOffset, length, wrapForeignException(cause, meta));
            throw EspressoException.wrap(exception, meta);
        }

        CompilerDirectives.transferToInterpreter();
        throw EspressoError.unexpected("Unexpected interop exception: ", e);
    }

    /**
     * Converts a guest arguments array to a host Object[].
     *
     * <p>
     * In some cases, preserving the guest Java types of the arguments is preferred e.g. interop
     * with an Espresso object. As an optimization, this method may return the underlying array of
     * the guest arguments Object[].
     *
     * @param unwrap if true, all arguments,
     * @return a host Object[] with "converted" arguments
     */
    private static Object[] getArguments(@JavaType(Object[].class) StaticObject arguments, boolean unwrap, Meta meta) {
        Object[] args = null;
        if (unwrap) {
            // Unwrap arguments.
            args = new Object[arguments.length()];
            for (int i = 0; i < args.length; i++) {
                args[i] = unwrap(meta.getInterpreterToVM().getArrayObject(i, arguments));
            }
        } else {
            // Preserve argument types.
            if (arguments.isEspressoObject()) {
                // Avoid copying, use the underlying array.
                args = arguments.unwrap();
            } else {
                args = new Object[arguments.length()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = meta.getInterpreterToVM().getArrayObject(i, arguments);
                }
            }
        }
        return args;
    }
}
