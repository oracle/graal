/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigInteger;
import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
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
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.PrimitiveKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.interop.LookupInternalTypeConverterNode;
import com.oracle.truffle.espresso.nodes.interop.LookupTypeConverterNode;
import com.oracle.truffle.espresso.nodes.interop.MethodArgsUtils;
import com.oracle.truffle.espresso.nodes.interop.PolyglotTypeMappings;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.nodes.interop.ToReference;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions
public final class Target_com_oracle_truffle_espresso_polyglot_Interop {

    static final class InteropUtils {

        static @JavaType(Object.class) StaticObject maybeWrapAsObject(Object value, InteropLibrary valueInterop, EspressoContext context) {
            if (value instanceof StaticObject) {
                // Already wrapped.
                return (StaticObject) value;
            }
            return StaticObject.createForeign(context.getLanguage(), context.getMeta().java_lang_Object, value, valueInterop);
        }

        static @JavaType(Throwable.class) StaticObject maybeWrapAsForeignException(Object value, InteropLibrary valueInterop, EspressoContext context) {
            assert InteropLibrary.getUncached().isException(value);
            if (value instanceof StaticObject) {
                // Already wrapped.
                return (StaticObject) value;
            }
            // Wrap foreign object as ForeignException.
            return StaticObject.createForeignException(context, value, valueInterop);
        }

        static String toHostString(Object value, InteropLibrary valueInterop) {
            assert InteropLibrary.getUncached().isString(value);
            try {
                return valueInterop.asString(value);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        /*
         * This method is used to unwrap an espresso object prior to leaving espresso. Conversion to
         * boxed primitive host values will occur.
         */
        static Object unwrap(EspressoLanguage language, StaticObject object, Meta meta) {
            return com.oracle.truffle.espresso.runtime.InteropUtils.unwrap(language, object, meta);
        }

        /*
         * This method is used to unwrap an espresso object to temporarily use the original interop
         * value. Foreign exception and converted foreign to guest objects are not unwrapped by this
         * method.
         */
        static Object unwrapForeign(EspressoLanguage language, StaticObject object) {
            if (object.isForeignObject()) {
                return object.rawForeignObject(language);
            }
            return object;
        }

        @TruffleBoundary
        static @JavaType(ByteOrder.class) StaticObject getLittleEndian(EspressoContext context) {
            Meta meta = context.getMeta();
            StaticObject staticStorage = meta.java_nio_ByteOrder.tryInitializeAndGetStatics();
            return meta.java_nio_ByteOrder_LITTLE_ENDIAN.getObject(staticStorage);
        }
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>null</code> like value, else
     * <code>false</code>. Most object oriented languages have one or many values representing null
     * values. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#isNull(Object)
     */
    @Substitution
    @ImportStatic(InteropUtils.class)
    abstract static class IsNull extends SubstitutionNode {
        static final int LIMIT = 4;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.isNull(InteropUtils.unwrapForeign(getLanguage(), receiver));
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
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.isBoolean(InteropUtils.unwrapForeign(getLanguage(), receiver));
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
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.asBoolean(InteropUtils.unwrapForeign(getLanguage(), receiver));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
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
    abstract static class IsString extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.isString(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
    }

    /**
     * Returns the Java string value if the receiver represents a
     * {@link InteropLibrary#isString(Object) string} like value.
     *
     * @see InteropLibrary#asString(Object)
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    abstract static class AsString extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(String.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(String.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return getMeta().toGuestString(interop.asString(InteropUtils.unwrapForeign(getLanguage(), receiver)));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class IsNumber extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.isNumber(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java byte primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInByte(Object)
     */
    @Substitution
    abstract static class FitsInByte extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.fitsInByte(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java short primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInShort(Object)
     */
    @Substitution
    abstract static class FitsInShort extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.fitsInShort(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java int primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInInt(Object)
     */
    @Substitution
    abstract static class FitsInInt extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.fitsInInt(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java long primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInLong(Object)
     */
    @Substitution
    abstract static class FitsInLong extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.fitsInLong(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java float primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInFloat(Object)
     */
    @Substitution
    abstract static class FitsInFloat extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.fitsInFloat(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java double primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInDouble(Object)
     */
    @Substitution
    abstract static class FitsInDouble extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.fitsInDouble(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java BigInteger without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInBigInteger(Object)
     */
    @Substitution
    abstract static class FitsInBigInteger extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.fitsInBigInteger(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
    }

    /**
     * Returns the receiver value as Java byte primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asByte(Object)
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    abstract static class AsByte extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract byte execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        byte doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.asByte(InteropUtils.unwrapForeign(getLanguage(), receiver));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class AsShort extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract short execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        short doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.asShort(InteropUtils.unwrapForeign(getLanguage(), receiver));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class AsInt extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract int execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        int doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.asInt(InteropUtils.unwrapForeign(getLanguage(), receiver));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class AsLong extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract long execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        long doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.asLong(InteropUtils.unwrapForeign(getLanguage(), receiver));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class AsFloat extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract float execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        float doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.asFloat(InteropUtils.unwrapForeign(getLanguage(), receiver));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class AsDouble extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract double execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        double doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.asDouble(InteropUtils.unwrapForeign(getLanguage(), receiver));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
        }
    }

    /**
     * Returns the receiver value as Java BigInteger if the number fits without loss of precision.
     * Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asBigInteger(Object)
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    abstract static class AsBigInteger extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(BigInteger.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(BigInteger.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                BigInteger bigInteger = interop.asBigInteger(InteropUtils.unwrapForeign(getLanguage(), receiver));
                return toGuestBigInteger(getMeta(), bigInteger);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
        }

        @TruffleBoundary
        private StaticObject toGuestBigInteger(Meta meta, BigInteger bigInteger) {
            byte[] bytes = bigInteger.toByteArray();
            StaticObject guestBigInteger = getAllocator().createNew(meta.java_math_BigInteger);
            meta.java_math_BigInteger_init.invokeDirect(guestBigInteger, StaticObject.wrap(bytes, meta));
            return guestBigInteger;
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
    abstract static class IsException extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.isException(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
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
    abstract static class ThrowException extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(RuntimeException.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(RuntimeException.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                throw interop.throwException(InteropUtils.unwrapForeign(getLanguage(), receiver));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class GetExceptionType extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/ExceptionType;") StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/ExceptionType;")
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                ExceptionType exceptionType = interop.getExceptionType(InteropUtils.unwrapForeign(getLanguage(), receiver));
                Meta meta = getMeta();
                StaticObject staticStorage = meta.polyglot.ExceptionType.tryInitializeAndGetStatics();
                // @formatter:off
                switch (exceptionType) {
                    case EXIT          : return meta.polyglot.ExceptionType_EXIT.getObject(staticStorage);
                    case INTERRUPT     : return meta.polyglot.ExceptionType_INTERRUPT.getObject(staticStorage);
                    case RUNTIME_ERROR : return meta.polyglot.ExceptionType_RUNTIME_ERROR.getObject(staticStorage);
                    case PARSE_ERROR   : return meta.polyglot.ExceptionType_PARSE_ERROR.getObject(staticStorage);
                    default:
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw EspressoError.shouldNotReachHere("Unexpected ExceptionType: " + exceptionType);
                }
                // @formatter:on
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class IsExceptionIncompleteSource extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile error) {
            try {
                return interop.isExceptionIncompleteSource(InteropUtils.unwrapForeign(getLanguage(), receiver));
            } catch (InteropException e) {
                error.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
        }
    }

    /**
     * Returns exception exit status of the receiver. Throws {@code UnsupportedMessageException}
     * when the receiver is not an {@link InteropLibrary#isException(Object) exception} of the
     * {@link ExceptionType#EXIT exit type}. See
     * <a href= "https://github.com/oracle/graal/blob/master/truffle/docs/Exit.md">Context Exit</a>
     * for further information. A return value zero indicates that the execution of the application
     * was successful, a non-zero value that it failed. The individual interpretation of non-zero
     * values depends on the application.
     *
     * @see InteropLibrary#getExceptionExitStatus(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    abstract static class GetExceptionExitStatus extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract int execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        int doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile error) {
            try {
                return interop.getExceptionExitStatus(InteropUtils.unwrapForeign(getLanguage(), receiver));
            } catch (InteropException e) {
                error.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class HasExceptionCause extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.hasExceptionCause(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
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
    abstract static class GetExceptionCause extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary causeInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile error) {
            try {
                Object cause = interop.getExceptionCause(InteropUtils.unwrapForeign(getLanguage(), receiver));
                assert InteropLibrary.getUncached().isException(cause);
                assert !InteropLibrary.getUncached().isNull(cause);
                // The cause must be an exception; if foreign, wrap it as ForeignException.
                return InteropUtils.maybeWrapAsForeignException(cause, causeInterop, getContext());
            } catch (InteropException e) {
                error.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class HasExceptionMessage extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.hasExceptionMessage(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
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
    abstract static class GetExceptionMessage extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary messageInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                Object message = interop.getExceptionMessage(InteropUtils.unwrapForeign(getLanguage(), receiver));
                assert InteropLibrary.getUncached().isString(message);
                // TODO(peterssen): Cannot wrap as String even if the foreign object is String-like.
                // Executing String methods, that rely on it having a .value field is not supported
                // yet
                // in Espresso.
                return InteropUtils.maybeWrapAsObject(message, messageInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class HasExceptionStackTrace extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.hasExceptionStackTrace(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
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
    abstract static class GetExceptionStackTrace extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary stackTraceInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                Object stackTrace = interop.getExceptionStackTrace(InteropUtils.unwrapForeign(getLanguage(), receiver));
                assert InteropLibrary.getUncached().hasArrayElements(stackTrace);
                return InteropUtils.maybeWrapAsObject(stackTrace, stackTraceInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class HasArrayElements extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.hasArrayElements(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
    }

    /**
     * Reads the value of an array element by index. This method must have not observable
     * side-effect.
     *
     * @see InteropLibrary#readArrayElement(Object, long)
     * @since 19.0
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/InvalidArrayIndexException;"),
    })
    abstract static class ReadArrayElement extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver, long index);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        long index,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary valueInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                Object value = interop.readArrayElement(InteropUtils.unwrapForeign(getLanguage(), receiver), index);
                return InteropUtils.maybeWrapAsObject(value, valueInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class GetArraySize extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract long execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        long doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.getArraySize(InteropUtils.unwrapForeign(getLanguage(), receiver));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class IsArrayElementReadable extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, long index);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        long index,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.isArrayElementReadable(InteropUtils.unwrapForeign(getLanguage(), receiver), index);
        }
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
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedTypeException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/InvalidArrayIndexException;"),
    })
    abstract static class WriteArrayElement extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract void execute(@JavaType(Object.class) StaticObject receiver, long index, @JavaType(Object.class) StaticObject value);

        @Specialization
        void doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        long index,
                        @JavaType(Object.class) StaticObject value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary exceptionInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached BranchProfile exceptionProfile,
                        @Cached ConditionProfile isForeignProfile) {
            try {
                if (isForeignProfile.profile(receiver.isForeignObject())) {
                    // Write to foreign array, full unwrap.
                    interop.writeArrayElement(InteropUtils.unwrapForeign(getLanguage(), receiver), index, InteropUtils.unwrap(getLanguage(), value, getMeta()));
                } else {
                    // Do not throw away the types if the receiver is an Espresso object.
                    interop.writeArrayElement(receiver, index, value);
                }
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            } catch (AbstractTruffleException ex) {
                throw handleAbstractTruffleException(exceptionInterop, getContext(), lookupTypeConverterNode, lookupInternalTypeConverterNode, exceptionProfile, ex);
            }
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
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/InvalidArrayIndexException;"),
    })
    abstract static class RemoveArrayElement extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract void execute(@JavaType(Object.class) StaticObject receiver, long index);

        @Specialization
        void doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        long index,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                interop.removeArrayElement(InteropUtils.unwrapForeign(getLanguage(), receiver), index);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class IsArrayElementModifiable extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, long index);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        long index,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.isArrayElementModifiable(InteropUtils.unwrapForeign(getLanguage(), receiver), index);
        }
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
    abstract static class IsArrayElementInsertable extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, long index);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        long index,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.isArrayElementInsertable(InteropUtils.unwrapForeign(getLanguage(), receiver), index);
        }
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
    abstract static class IsArrayElementRemovable extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, long index);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        long index,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.isArrayElementRemovable(InteropUtils.unwrapForeign(getLanguage(), receiver), index);
        }
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
    abstract static class HasMetaObject extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.hasMetaObject(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
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
    abstract static class GetMetaObject extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary metaObjectInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                Object metaObject = interop.getMetaObject(InteropUtils.unwrapForeign(getLanguage(), receiver));
                assert InteropLibrary.getUncached().isMetaObject(metaObject);
                return InteropUtils.maybeWrapAsObject(metaObject, metaObjectInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
     * @see InteropLibrary#toDisplayString(Object, boolean)
     * @since 20.1
     */
    @Substitution
    abstract static class ToDisplayString extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver, boolean allowSideEffects);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        boolean allowSideEffects,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary displayStringInterop) {
            Object displayString = interop.toDisplayString(InteropUtils.unwrapForeign(getLanguage(), receiver), allowSideEffects);
            assert InteropLibrary.getUncached().isString(displayString);
            return InteropUtils.maybeWrapAsObject(displayString, displayStringInterop, getContext());
        }
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
    abstract static class IsMetaObject extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.isMetaObject(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
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
    abstract static class GetMetaQualifiedName extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary qualifiedNameInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                Object qualifiedName = interop.getMetaQualifiedName(InteropUtils.unwrapForeign(getLanguage(), receiver));
                assert InteropLibrary.getUncached().isString(qualifiedName);
                return InteropUtils.maybeWrapAsObject(qualifiedName, qualifiedNameInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class GetMetaSimpleName extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary simpleNameInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                Object simpleName = interop.getMetaSimpleName(InteropUtils.unwrapForeign(getLanguage(), receiver));
                assert InteropLibrary.getUncached().isString(simpleName);
                return InteropUtils.maybeWrapAsObject(simpleName, simpleNameInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
     * @see InteropLibrary#isMetaInstance(Object, Object)
     * @since 20.1
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    abstract static class IsMetaInstance extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, @JavaType(Object.class) StaticObject instance);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object.class) StaticObject instance,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.isMetaInstance(InteropUtils.unwrapForeign(getLanguage(), receiver), InteropUtils.unwrapForeign(getLanguage(), instance));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class IsIdentical extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, @JavaType(Object.class) StaticObject other);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object.class) StaticObject other,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary otherInterop) {
            return interop.isIdentical(InteropUtils.unwrapForeign(getLanguage(), receiver), InteropUtils.unwrapForeign(getLanguage(), other), otherInterop);
        }
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
    abstract static class IdentityHashCode extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract int execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        int doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.identityHashCode(InteropUtils.unwrapForeign(getLanguage(), receiver));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class HasMembers extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.hasMembers(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
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
    abstract static class GetMembers extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary membersInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                Object members = interop.getMembers(InteropUtils.unwrapForeign(getLanguage(), receiver));
                assert InteropLibrary.getUncached().hasArrayElements(members);
                return InteropUtils.maybeWrapAsObject(members, membersInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class IsMemberReadable extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(String.class) StaticObject member,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary memberInterop) {
            assert InteropLibrary.getUncached().isString(member);
            String hostMember = InteropUtils.toHostString(member, memberInterop);
            return interop.isMemberReadable(InteropUtils.unwrapForeign(getLanguage(), receiver), hostMember);
        }
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
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnknownIdentifierException;")
    })
    abstract static class ReadMember extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(String.class) StaticObject member,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary memberInterop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary memberValueInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            assert InteropLibrary.getUncached().isString(member);
            String hostMember = InteropUtils.toHostString(member, memberInterop);
            try {
                Object memberValue = interop.readMember(InteropUtils.unwrapForeign(getLanguage(), receiver), hostMember);
                return InteropUtils.maybeWrapAsObject(memberValue, memberValueInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class IsMemberModifiable extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(String.class) StaticObject member,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary memberInterop) {
            assert InteropLibrary.getUncached().isString(member);
            String hostMember = InteropUtils.toHostString(member, memberInterop);
            return interop.isMemberModifiable(InteropUtils.unwrapForeign(getLanguage(), receiver), hostMember);
        }
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
    abstract static class IsMemberInsertable extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(String.class) StaticObject member,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary memberInterop) {
            assert InteropLibrary.getUncached().isString(member);
            String hostMember = InteropUtils.toHostString(member, memberInterop);
            return interop.isMemberInsertable(InteropUtils.unwrapForeign(getLanguage(), receiver), hostMember);
        }
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
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnknownIdentifierException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedTypeException;")
    })
    abstract static class WriteMember extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract void execute(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member, @JavaType(Object.class) StaticObject value);

        @Specialization
        void doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(String.class) StaticObject member,
                        @JavaType(Object.class) StaticObject value,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary memberInterop,
                        @Cached BranchProfile exceptionProfile,
                        @Cached ConditionProfile isForeignProfile) {
            assert InteropLibrary.getUncached().isString(member);
            String hostMember = InteropUtils.toHostString(member, memberInterop);
            try {
                if (isForeignProfile.profile(receiver.isForeignObject())) {
                    interop.writeMember(InteropUtils.unwrapForeign(getLanguage(), receiver), hostMember, InteropUtils.unwrap(getLanguage(), value, getMeta()));
                } else {
                    // Preserve the value type.
                    interop.writeMember(receiver, hostMember, value);
                }
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class IsMemberRemovable extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(String.class) StaticObject member,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary memberInterop) {
            assert InteropLibrary.getUncached().isString(member);
            String hostMember = InteropUtils.toHostString(member, memberInterop);
            return interop.isMemberRemovable(InteropUtils.unwrapForeign(getLanguage(), receiver), hostMember);
        }
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
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnknownIdentifierException;")
    })
    abstract static class RemoveMember extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract void execute(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member);

        @Specialization
        void doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(String.class) StaticObject member,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary memberInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            assert InteropLibrary.getUncached().isString(member);
            String hostMember = InteropUtils.toHostString(member, memberInterop);
            try {
                interop.removeMember(InteropUtils.unwrapForeign(getLanguage(), receiver), hostMember);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class IsMemberInvocable extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(String.class) StaticObject member,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary memberInterop) {
            assert InteropLibrary.getUncached().isString(member);
            String hostMember = InteropUtils.toHostString(member, memberInterop);
            return interop.isMemberInvocable(InteropUtils.unwrapForeign(getLanguage(), receiver), hostMember);
        }
    }

    /**
     * Invokes a member for a given receiver and arguments.
     *
     * @see InteropLibrary#invokeMember(Object, String, Object...)
     * @since 19.0
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnknownIdentifierException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/ArityException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedTypeException;")
    })
    abstract static class InvokeMember extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(String.class) StaticObject member,
                        @JavaType(Object[].class) StaticObject arguments);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(String.class) StaticObject member,
                        @JavaType(Object[].class) StaticObject arguments,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary valueInterop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary exceptionInterop,
                        @Bind("getMeta()") Meta meta,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached ToHostArguments toHostArguments,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached BranchProfile exceptionProfile) {
            assert InteropLibrary.getUncached().isString(member);

            String hostMember = meta.toHostString(member);
            try {
                Object[] hostArguments = toHostArguments.execute(receiver.isForeignObject(), arguments);
                Object result = interop.invokeMember(InteropUtils.unwrapForeign(getLanguage(), receiver), hostMember, hostArguments);
                return InteropUtils.maybeWrapAsObject(result, valueInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            } catch (EspressoException e) {
                // make sure we don't try to convert espresso exceptions
                throw e;
            } catch (AbstractTruffleException ex) {
                throw handleAbstractTruffleException(exceptionInterop, getContext(), lookupTypeConverterNode, lookupInternalTypeConverterNode, exceptionProfile, ex);
            }
        }
    }

    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnknownIdentifierException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/ArityException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedTypeException;")
    })
    abstract static class InvokeMemberWithCast extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(
                        @JavaType(Class.class) StaticObject targetClass,
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(String.class) StaticObject member,
                        @JavaType(Object[].class) StaticObject arguments);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @SuppressWarnings("unused") @JavaType(Class.class) StaticObject targetClass,
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(String.class) StaticObject member,
                        @JavaType(Object[].class) StaticObject arguments,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary exceptionInterop,
                        @Bind("getMeta()") Meta meta,
                        @Cached ToReference.DynamicToReference toEspressoNode,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached ToHostArguments toHostArguments,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached BranchProfile exceptionProfile) {
            assert InteropLibrary.getUncached().isString(member);
            String hostMember = getMeta().toHostString(member);
            try {
                Object[] hostArguments = toHostArguments.execute(receiver.isForeignObject(), arguments);
                Object result = interop.invokeMember(InteropUtils.unwrapForeign(getLanguage(), receiver), hostMember, hostArguments);
                Klass targetKlass = targetClass.getMirrorKlass(meta);
                if (targetKlass.isPrimitive()) {
                    targetKlass = MethodArgsUtils.primitiveTypeToBoxedType((PrimitiveKlass) targetKlass);
                }
                return toEspressoNode.execute(result, targetKlass);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            } catch (EspressoException e) {
                // make sure we don't try to convert espresso exceptions
                throw e;
            } catch (AbstractTruffleException ex) {
                throw handleAbstractTruffleException(exceptionInterop, getContext(), lookupTypeConverterNode, lookupInternalTypeConverterNode, exceptionProfile, ex);
            }
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
    abstract static class HasMemberReadSideEffects extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(String.class) StaticObject member,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary memberInterop) {
            assert InteropLibrary.getUncached().isString(member);
            String hostMember = InteropUtils.toHostString(member, memberInterop);
            return interop.hasMemberReadSideEffects(InteropUtils.unwrapForeign(getLanguage(), receiver), hostMember);
        }
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
    abstract static class HasMemberWriteSideEffects extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, @JavaType(String.class) StaticObject member);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(String.class) StaticObject member,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary memberInterop) {
            assert InteropLibrary.getUncached().isString(member);
            String hostMember = InteropUtils.toHostString(member, memberInterop);
            return interop.hasMemberWriteSideEffects(InteropUtils.unwrapForeign(getLanguage(), receiver), hostMember);
        }
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
    abstract static class IsPointer extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.isPointer(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
    }

    /**
     * Returns the pointer value as long value if the receiver represents a pointer like value.
     *
     * @see InteropLibrary#asPointer(Object)
     * @since 19.0
     */
    @Substitution
    abstract static class AsPointer extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract long execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        long doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.asPointer(InteropUtils.unwrapForeign(getLanguage(), receiver));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class ToNative extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract void execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        void doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            interop.toNative(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
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
    abstract static class IsExecutable extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.isExecutable(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
    }

    /**
     * Executes an executable value with the given arguments.
     *
     * @see InteropLibrary#execute(Object, Object...)
     * @since 19.0
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/ArityException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedTypeException;")
    })
    abstract static class Execute extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object[].class) StaticObject arguments);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object[].class) StaticObject arguments,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary resultInterop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary exceptionInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached ToHostArguments toHostArguments,
                        @Cached BranchProfile exceptionProfile) {
            try {
                Object[] hostArguments = toHostArguments.execute(receiver.isForeignObject(), arguments);
                Object result = interop.execute(InteropUtils.unwrapForeign(getLanguage(), receiver), hostArguments);
                return InteropUtils.maybeWrapAsObject(result, resultInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            } catch (AbstractTruffleException ex) {
                throw handleAbstractTruffleException(exceptionInterop, getContext(), lookupTypeConverterNode, lookupInternalTypeConverterNode, exceptionProfile, ex);
            }
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
    abstract static class IsInstantiable extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.isInstantiable(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
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
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/ArityException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedTypeException;")
    })
    abstract static class Instantiate extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object[].class) StaticObject arguments);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object[].class) StaticObject arguments,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary resultInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached ToHostArguments toHostArguments,
                        @Cached BranchProfile exceptionProfile) {
            try {
                Object[] hostArguments = toHostArguments.execute(receiver.isForeignObject(), arguments);
                Object result = interop.instantiate(InteropUtils.unwrapForeign(getLanguage(), receiver), hostArguments);
                return InteropUtils.maybeWrapAsObject(result, resultInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class HasExecutableName extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.hasExecutableName(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
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
    abstract static class GetExecutableName extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary executableNameInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                Object executableName = interop.getExecutableName(InteropUtils.unwrapForeign(getLanguage(), receiver));
                assert InteropLibrary.getUncached().isString(executableName);
                return InteropUtils.maybeWrapAsObject(executableName, executableNameInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    abstract static class HasDeclaringMetaObject extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.hasDeclaringMetaObject(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
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
    abstract static class GetDeclaringMetaObject extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary declaringMetaObjectInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                Object declaringMetaObject = interop.getDeclaringMetaObject(InteropUtils.unwrapForeign(getLanguage(), receiver));
                assert InteropLibrary.getUncached().isMetaObject(declaringMetaObject);
                return InteropUtils.maybeWrapAsObject(declaringMetaObject, declaringMetaObjectInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
            return interop.hasBufferElements(InteropUtils.unwrapForeign(getLanguage(), receiver));
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
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.getBufferSize(InteropUtils.unwrapForeign(getLanguage(), receiver));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
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
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.isBufferWritable(InteropUtils.unwrapForeign(getLanguage(), receiver));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
        }
    }

    /**
     * Reads bytes from the receiver object into the specified byte array.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     * <p>
     * Throws InvalidBufferOffsetException if and only if
     * <code>byteOffset < 0 || length < 0 || byteOffset + length > </code>{@link InteropLibrary#getBufferSize(Object)}
     * <p>
     * Throws UnsupportedMessageException if and only if
     * {@link InteropLibrary#hasBufferElements(Object)} returns {@code false}
     * 
     * @since 24.0
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/InvalidBufferOffsetException;")
    })
    abstract static class ReadBuffer extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract void execute(@JavaType(Object.class) StaticObject receiver, long byteOffset, @JavaType(byte[].class) StaticObject destination, int destinationOffset, int length);

        @Specialization
        void doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        long byteOffset,
                        @JavaType(byte[].class) StaticObject destination,
                        int destinationOffset,
                        int length,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached InlinedBranchProfile unexpectedExceptionProfile,
                        @Cached InlinedBranchProfile exceptionProfile) {
            // check destination array and throw guest exception in case it is too small
            byte[] dest = destination.unwrap(getLanguage());
            if (destinationOffset < 0 || destinationOffset + length > dest.length) {
                unexpectedExceptionProfile.enter(this);
                throw getMeta().throwExceptionWithMessage(getMeta().java_lang_ArrayIndexOutOfBoundsException, "destination array is not able to hold the bytes");
            }
            try {
                interop.readBuffer(InteropUtils.unwrapForeign(getLanguage(), receiver), byteOffset, destination.unwrap(getLanguage()), destinationOffset, length);
            } catch (InteropException e) {
                exceptionProfile.enter(this);
                throw throwInteropExceptionAsGuest.execute(e);
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
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.readBufferByte(InteropUtils.unwrapForeign(getLanguage(), receiver), byteOffset);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
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
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                interop.writeBufferByte(InteropUtils.unwrapForeign(getLanguage(), receiver), byteOffset, value);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
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
    @ImportStatic(InteropUtils.class)
    abstract static class ReadBufferShort extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract short execute(@JavaType(Object.class) StaticObject receiver, @JavaType(ByteOrder.class) StaticObject order, long byteOffset);

        @Specialization
        short doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(ByteOrder.class) StaticObject order,
                        long byteOffset,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                return interop.readBufferShort(InteropUtils.unwrapForeign(getLanguage(), receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
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
    @ImportStatic(InteropUtils.class)
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
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                interop.writeBufferShort(InteropUtils.unwrapForeign(getLanguage(), receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset,
                                value);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
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
    @ImportStatic(InteropUtils.class)
    abstract static class ReadBufferInt extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract int execute(@JavaType(Object.class) StaticObject receiver, @JavaType(ByteOrder.class) StaticObject order, long byteOffset);

        @Specialization
        int doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(ByteOrder.class) StaticObject order,
                        long byteOffset,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                return interop.readBufferInt(InteropUtils.unwrapForeign(getLanguage(), receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
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
    @ImportStatic(InteropUtils.class)
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
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                interop.writeBufferInt(InteropUtils.unwrapForeign(getLanguage(), receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset,
                                value);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
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
    @ImportStatic(InteropUtils.class)
    abstract static class ReadBufferLong extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract long execute(@JavaType(Object.class) StaticObject receiver, @JavaType(ByteOrder.class) StaticObject order, long byteOffset);

        @Specialization
        long doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(ByteOrder.class) StaticObject order,
                        long byteOffset,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                return interop.readBufferLong(InteropUtils.unwrapForeign(getLanguage(), receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
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
    @ImportStatic(InteropUtils.class)
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
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                interop.writeBufferLong(InteropUtils.unwrapForeign(getLanguage(), receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset,
                                value);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
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
    @ImportStatic(InteropUtils.class)
    abstract static class ReadBufferFloat extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract float execute(@JavaType(Object.class) StaticObject receiver, @JavaType(ByteOrder.class) StaticObject order, long byteOffset);

        @Specialization
        float doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(ByteOrder.class) StaticObject order,
                        long byteOffset,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                return interop.readBufferFloat(InteropUtils.unwrapForeign(getLanguage(), receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
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
    @ImportStatic(InteropUtils.class)
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
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                interop.writeBufferFloat(InteropUtils.unwrapForeign(getLanguage(), receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset,
                                value);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
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
    @ImportStatic(InteropUtils.class)
    abstract static class ReadBufferDouble extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract double execute(@JavaType(Object.class) StaticObject receiver, @JavaType(ByteOrder.class) StaticObject order, long byteOffset);

        @Specialization
        double doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(ByteOrder.class) StaticObject order,
                        long byteOffset,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                return interop.readBufferDouble(InteropUtils.unwrapForeign(getLanguage(), receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
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
    @ImportStatic(InteropUtils.class)
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
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile,
                        @Cached("getLittleEndian(getContext())") @JavaType(ByteOrder.class) StaticObject littleEndian) {
            try {
                interop.writeBufferDouble(InteropUtils.unwrapForeign(getLanguage(), receiver),
                                order == littleEndian
                                                ? ByteOrder.LITTLE_ENDIAN
                                                : ByteOrder.BIG_ENDIAN,
                                byteOffset,
                                value);
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
        }
    }

    // endregion Buffer Messages

    // region Iterator Messages

    /**
     * Returns {@code true} if the receiver provides an iterator. For example, an array or a list
     * provide an iterator over their content. Invoking this message does not cause any observable
     * side-effects. By default returns {@code true} for receivers that have
     * {@link InteropLibrary#hasArrayElements(Object) array elements}.
     *
     * @see InteropLibrary#hasIterator(Object)
     * @see GetIterator
     * @since 21.1
     */
    @Substitution
    abstract static class HasIterator extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.hasIterator(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
    }

    /**
     * Returns the iterator for the receiver. The return value is always an {@link IsIterator
     * iterator}. Invoking this message does not cause any observable side-effects.
     *
     * Throws UnsupportedMessageException if and only if {@link HasIterator} returns {@code false}
     * for the same receiver.
     *
     * @see InteropLibrary#getIterator(Object)
     * @since 21.1
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    abstract static class GetIterator extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary iteratorInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                Object iterator = interop.getIterator(InteropUtils.unwrapForeign(getLanguage(), receiver));
                assert InteropLibrary.getUncached().isIterator(iterator);
                return InteropUtils.maybeWrapAsObject(iterator, iteratorInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
        }
    }

    /**
     * Returns {@code true} if the receiver represents an iterator. Invoking this message does not
     * cause any observable side-effects. Returns {@code false} by default.
     *
     * @see InteropLibrary#isIterator(Object)
     * @see HasIterator
     * @see GetIterator
     * @since 21.1
     */
    @Substitution
    abstract static class IsIterator extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.isIterator(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
    }

    /**
     * Returns {@code true} if the receiver is an iterator which has more elements, else
     * {@code false}. Multiple calls to the {@link HasIteratorNextElement} might lead to different
     * results if the underlying data structure is modified.
     * <p>
     * The following example shows how the {@link HasIteratorNextElement hasIteratorNextElement}
     * message can be emulated in languages where iterators only have a next method and throw an
     * exception if there are no further elements.
     *
     * <p>
     * Throws UnsupportedMessageException if and only if {@link IsIterator} returns {@code false}
     * for the same receiver.
     *
     * @see InteropLibrary#hasIteratorNextElement(Object)
     * @see IsIterator
     * @see GetIteratorNextElement
     * @since 21.1
     */
    @Substitution
    abstract static class HasIteratorNextElement extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.hasIteratorNextElement(InteropUtils.unwrapForeign(getLanguage(), receiver));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
        }
    }

    /**
     * Returns the next element in the iteration. When the underlying data structure is modified the
     * {@link GetIteratorNextElement} may throw the {@link StopIterationException} despite the
     * {@link HasIteratorNextElement} returned {@code true}.
     *
     * <p>
     * Throws UnsupportedMessageException if {@link IsIterator} returns {@code false} for the same
     * receiver or when the underlying iterator element exists but is not readable.
     *
     * <p>
     * Throws StopIterationException if the iteration has no more elements. Even if the
     * {@link StopIterationException} was thrown it might not be thrown again by a next
     * {@link GetIteratorNextElement} invocation on the same receiver due to a modification of an
     * underlying iterable.
     *
     * @see InteropLibrary#getIteratorNextElement(Object)
     * @see IsIterator
     * @see HasIteratorNextElement
     * @since 21.1
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/StopIterationException;")
    })
    abstract static class GetIteratorNextElement extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary valueInterop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary exceptionInterop,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                Object element = interop.getIteratorNextElement(InteropUtils.unwrapForeign(getLanguage(), receiver));
                return InteropUtils.maybeWrapAsObject(element, valueInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            } catch (AbstractTruffleException ex) {
                throw handleAbstractTruffleException(exceptionInterop, getContext(), lookupTypeConverterNode, lookupInternalTypeConverterNode, exceptionProfile, ex);
            }
        }
    }

    // endregion Iterator Messages

    // region Hash(Dictionary) Messages

    /**
     * Returns {@code true} if the receiver may have hash entries. Therefore, at least one of
     * {@link ReadHashValue}, {@link WriteHashEntry}, {@link RemoveHashEntry} must not throw
     * {@link UnsupportedMessageException}. For example, the contents of a map data structure could
     * be interpreted as hash elements. Invoking this message does not cause any observable
     * side-effects. Returns {@code false} by default.
     *
     * @see GetHashEntriesIterator
     * @see GetHashSize
     * @see IsHashEntryReadable
     * @see IsHashEntryWritable
     * @see IsHashEntryInsertable
     * @see IsHashEntryRemovable
     * @see ReadHashValue
     * @see ReadHashValueOrDefault
     * @see WriteHashEntry
     * @see RemoveHashEntry
     * @since 21.1
     */
    @Substitution
    abstract static class HasHashEntries extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return interop.hasHashEntries(InteropUtils.unwrapForeign(getLanguage(), receiver));
        }
    }

    /**
     * Returns the number of receiver entries.
     *
     * <p>
     * Throws UnsupportedMessageException if and only if {@link HasHashEntries} returns
     * {@code false}.
     *
     * @since 21.1
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    abstract static class GetHashSize extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract long execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        long doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                return interop.getHashSize(InteropUtils.unwrapForeign(getLanguage(), receiver));
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
        }
    }

    /**
     * Returns {@code true} if mapping for the specified key exists and is {@link ReadHashValue
     * readable}. This method may only return {@code true} if {@link HasHashEntries} returns
     * {@code true} as well and {@link IsHashEntryInsertable} returns {@code false}. Invoking this
     * message does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see ReadHashValue
     * @since 21.1
     */
    @Substitution
    abstract static class IsHashEntryReadable extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, @JavaType(Object.class) StaticObject key);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object.class) StaticObject key,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ConditionProfile isForeignProfile) {
            if (isForeignProfile.profile(receiver.isForeignObject())) {
                EspressoLanguage language = getLanguage();
                return interop.isHashEntryReadable(InteropUtils.unwrapForeign(language, receiver), InteropUtils.unwrap(language, key, getMeta()));
            } else {
                // Preserve Java types.
                return interop.isHashEntryReadable(receiver, key);
            }
        }
    }

    /**
     * Reads the value for the specified key.
     *
     * <p>
     * Throws UnsupportedMessageException if the receiver does not support reading at all. An empty
     * receiver with no readable hash entries supports the read operation (even though there is
     * nothing to read), therefore it throws {@link UnknownKeyException} for all arguments instead.
     * <p>
     * Throws UnknownKeyException if mapping for the specified key is not {@link IsHashEntryReadable
     * readable}, e.g. when the hash does not contain specified key.
     *
     * @see IsHashEntryReadable
     * @see ReadHashValueOrDefault
     * @since 21.1
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnknownKeyException;")
    })
    abstract static class ReadHashValue extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver, @JavaType(Object.class) StaticObject key);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object.class) StaticObject key,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary valueInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile,
                        @Cached ConditionProfile isForeignProfile) {
            try {
                Object result;
                if (isForeignProfile.profile(receiver.isForeignObject())) {
                    EspressoLanguage language = getLanguage();
                    result = interop.readHashValue(InteropUtils.unwrapForeign(language, receiver), InteropUtils.unwrap(language, key, getMeta()));
                } else {
                    result = interop.readHashValue(receiver, key);
                }
                return InteropUtils.maybeWrapAsObject(result, valueInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
        }
    }

    /**
     * Reads the value for the specified key or returns the {@code defaultValue} when the mapping
     * for the specified key does not exist or is not readable.
     *
     * <p>
     * Throws UnsupportedMessageException if the receiver does not support reading at all. An empty
     * receiver with no readable hash entries supports the read operation (even though there is
     * nothing to read), therefore it returns the {@code defaultValue} for all arguments instead.
     *
     * @see IsHashEntryReadable
     * @see ReadHashValue
     * @since 21.1
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    abstract static class ReadHashValueOrDefault extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver, @JavaType(Object.class) StaticObject key,
                        @JavaType(Object.class) StaticObject defaultValue);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object.class) StaticObject key,
                        @JavaType(Object.class) StaticObject defaultValue,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary valueInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile,
                        @Cached ConditionProfile isForeignProfile) {
            try {
                if (isForeignProfile.profile(receiver.isForeignObject())) {
                    EspressoLanguage language = getLanguage();
                    Object unwrappedDefaultValue = InteropUtils.unwrap(language, defaultValue, getMeta());
                    Object result = interop.readHashValueOrDefault(InteropUtils.unwrapForeign(language, receiver), InteropUtils.unwrap(language, key, getMeta()), unwrappedDefaultValue);
                    // If the unwrapped default value was returned, preserve the original type.
                    if (result == unwrappedDefaultValue) {
                        return defaultValue;
                    }
                    return InteropUtils.maybeWrapAsObject(result, valueInterop, getContext());
                } else {
                    Object result = interop.readHashValueOrDefault(receiver, key, defaultValue);
                    return InteropUtils.maybeWrapAsObject(result, valueInterop, getContext());
                }
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
        }
    }

    /**
     * Returns {@code true} if mapping for the specified key exists and is {@link WriteHashEntry
     * writable}. This method may only return {@code true} if {@link HasHashEntries} returns
     * {@code true} as well and {@link IsHashEntryInsertable} returns {@code false}. Invoking this
     * message does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see WriteHashEntry
     * @since 21.1
     */
    @Substitution
    abstract static class IsHashEntryModifiable extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, @JavaType(Object.class) StaticObject key);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object.class) StaticObject key,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ConditionProfile isForeignProfile) {
            if (isForeignProfile.profile(receiver.isForeignObject())) {
                EspressoLanguage language = getLanguage();
                return interop.isHashEntryModifiable(InteropUtils.unwrapForeign(language, receiver), InteropUtils.unwrap(language, key, getMeta()));
            } else {
                // Preserve Java types.
                return interop.isHashEntryModifiable(receiver, key);
            }
        }
    }

    /**
     * Returns {@code true} if mapping for the specified key does not exist and is
     * {@link WriteHashEntry writable}. This method may only return {@code true} if
     * {@link HasHashEntries} returns {@code true} as well and {@link IsHashEntryExisting} returns
     * {@code false}. Invoking this message does not cause any observable side-effects. Returns
     * {@code false} by default.
     *
     * @see WriteHashEntry
     * @since 21.1
     */
    @Substitution
    abstract static class IsHashEntryInsertable extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, @JavaType(Object.class) StaticObject key);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object.class) StaticObject key,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ConditionProfile isForeignProfile) {
            if (isForeignProfile.profile(receiver.isForeignObject())) {
                EspressoLanguage language = getLanguage();
                return interop.isHashEntryInsertable(InteropUtils.unwrapForeign(language, receiver), InteropUtils.unwrap(language, key, getMeta()));
            } else {
                // Preserve Java types.
                return interop.isHashEntryInsertable(receiver, key);
            }
        }
    }

    /**
     * Returns {@code true} if mapping for the specified key is {@link IsHashEntryModifiable
     * modifiable} or {@link IsHashEntryInsertable insertable}.
     *
     * @since 21.1
     */
    @Substitution
    abstract static class IsHashEntryWritable extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, @JavaType(Object.class) StaticObject key);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object.class) StaticObject key,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ConditionProfile isForeignProfile) {
            if (isForeignProfile.profile(receiver.isForeignObject())) {
                EspressoLanguage language = getLanguage();
                return interop.isHashEntryWritable(InteropUtils.unwrapForeign(language, receiver), InteropUtils.unwrap(language, key, getMeta()));
            } else {
                // Preserve Java types.
                return interop.isHashEntryWritable(receiver, key);
            }
        }
    }

    /**
     * Associates the specified value with the specified key in the receiver. Writing the entry is
     * allowed if is existing and {@link IsHashEntryModifiable modifiable}, or not existing and
     * {@link IsHashEntryInsertable insertable}.
     *
     * <p>
     * Throws UnsupportedMessageException when the receiver does not support writing at all, e.g.
     * when it is immutable.
     * <p>
     * Throws UnknownKeyException if mapping for the specified key is not
     * {@link IsHashEntryModifiable modifiable} nor {@link IsHashEntryInsertable insertable}.
     * <p>
     * Throws UnsupportedTypeException if the provided key type or value type is not allowed to be
     * written.
     *
     * @since 21.1
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnknownKeyException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedTypeException;")
    })
    abstract static class WriteHashEntry extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract void execute(@JavaType(Object.class) StaticObject receiver, @JavaType(Object.class) StaticObject key, @JavaType(Object.class) StaticObject value);

        @Specialization
        void doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object.class) StaticObject key,
                        @JavaType(Object.class) StaticObject value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary exceptionInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached BranchProfile exceptionProfile,
                        @Cached ConditionProfile isForeignProfile) {
            try {
                if (isForeignProfile.profile(receiver.isForeignObject())) {
                    EspressoLanguage language = getLanguage();
                    interop.writeHashEntry(InteropUtils.unwrapForeign(language, receiver), InteropUtils.unwrap(language, key, getMeta()), InteropUtils.unwrap(language, value, getMeta()));
                } else {
                    // Preserve Java types.
                    interop.writeHashEntry(receiver, key, value);
                }
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            } catch (AbstractTruffleException ex) {
                throw handleAbstractTruffleException(exceptionInterop, getContext(), lookupTypeConverterNode, lookupInternalTypeConverterNode, exceptionProfile, ex);
            }
        }
    }

    /**
     * Returns {@code true} if mapping for the specified key exists and is removable. This method
     * may only return {@code true} if {@link HasHashEntries} returns {@code true} as well and
     * {@link IsHashEntryInsertable} returns {@code false}. Invoking this message does not cause any
     * observable side-effects. Returns {@code false} by default.
     *
     * @see RemoveHashEntry
     * @since 21.1
     */
    @Substitution
    abstract static class IsHashEntryRemovable extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, @JavaType(Object.class) StaticObject key);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object.class) StaticObject key,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ConditionProfile isForeignProfile) {
            if (isForeignProfile.profile(receiver.isForeignObject())) {
                EspressoLanguage language = getLanguage();
                return interop.isHashEntryRemovable(InteropUtils.unwrapForeign(language, receiver), InteropUtils.unwrap(language, key, getMeta()));
            } else {
                // Preserve Java types.
                return interop.isHashEntryRemovable(receiver, key);
            }
        }
    }

    /**
     * Removes the mapping for a given key from the receiver. Mapping removing is allowed if it is
     * {@link IsHashEntryRemovable removable}.
     *
     * <p>
     * Throws UnsupportedMessageException when the receiver does not support removing at all, e.g.
     * when it is immutable.
     * <p>
     * Throws UnknownKeyException if the given mapping is not {@link IsHashEntryRemovable
     * removable}, e.g. the receiver does not have a mapping for given key.
     *
     * @see IsHashEntryRemovable
     * @since 21.1
     */
    @Substitution
    @Throws(others = {
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"),
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnknownKeyException;")
    })
    abstract static class RemoveHashEntry extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract void execute(@JavaType(Object.class) StaticObject receiver, @JavaType(Object.class) StaticObject key);

        @Specialization
        void doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object.class) StaticObject key,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary exceptionInterop,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile,
                        @Cached ConditionProfile isForeignProfile) {
            try {
                if (isForeignProfile.profile(receiver.isForeignObject())) {
                    EspressoLanguage language = getLanguage();
                    interop.removeHashEntry(InteropUtils.unwrapForeign(language, receiver), InteropUtils.unwrap(language, key, getMeta()));
                } else {
                    // Preserve Java types.
                    interop.removeHashEntry(receiver, key);
                }
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            } catch (AbstractTruffleException ex) {
                throw handleAbstractTruffleException(exceptionInterop, getContext(), lookupTypeConverterNode, lookupInternalTypeConverterNode, exceptionProfile, ex);
            }
        }
    }

    /**
     * Returns {@code true} if mapping for a given key is existing. The mapping is existing if it is
     * {@link IsHashEntryModifiable modifiable}, {@link IsHashEntryReadable readable} or
     * {@link IsHashEntryRemovable removable}.
     *
     * @since 21.1
     */
    @Substitution
    abstract static class IsHashEntryExisting extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract boolean execute(@JavaType(Object.class) StaticObject receiver, @JavaType(Object.class) StaticObject key);

        @Specialization
        boolean doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object.class) StaticObject key,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ConditionProfile isForeignProfile) {
            if (isForeignProfile.profile(receiver.isForeignObject())) {
                EspressoLanguage language = getLanguage();
                return interop.isHashEntryExisting(InteropUtils.unwrapForeign(language, receiver), InteropUtils.unwrap(language, key, getMeta()));
            } else {
                // Preserve Java types.
                return interop.isHashEntryExisting(receiver, key);
            }
        }
    }

    /**
     * Returns the hash entries iterator for the receiver. The return value is always an
     * {@link IsIterator iterator} of {@link HasArrayElements array} elements. The first array
     * element is a key, the second array element is an associated value. Array returned by the
     * iterator may be modifiable but detached from the hash, updating the array elements may not
     * update the hash. So even if array elements are {@link IsArrayElementModifiable modifiable}
     * always use {@link WriteHashEntry} to update the hash mapping.
     *
     * <p>
     * Throws UnsupportedMessageException if and only if {@link HasHashEntries} returns
     * {@code false} for the same receiver.
     *
     * @since 21.1
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    abstract static class GetHashEntriesIterator extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary iteratorInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                Object iterator = interop.getHashEntriesIterator(InteropUtils.unwrapForeign(getLanguage(), receiver));
                assert InteropLibrary.getUncached().isIterator(iterator);
                return InteropUtils.maybeWrapAsObject(iterator, iteratorInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
        }
    }

    /**
     * Returns the hash keys iterator for the receiver. The return value is always an
     * {@link IsIterator iterator}.
     *
     * <p>
     * Throws UnsupportedMessageException if and only if {@link HasHashEntries} returns
     * {@code false} for the same receiver.
     *
     * @since 21.1
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    abstract static class GetHashKeysIterator extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary iteratorInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                Object iterator = interop.getHashKeysIterator(InteropUtils.unwrapForeign(getLanguage(), receiver));
                assert InteropLibrary.getUncached().isIterator(iterator);
                return InteropUtils.maybeWrapAsObject(iterator, iteratorInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
        }
    }

    /**
     * Returns the hash values iterator for the receiver. The return value is always an
     * {@link IsIterator iterator}.
     *
     * <p>
     * Throws UnsupportedMessageException if and only if {@link HasHashEntries} returns
     * {@code false} for the same receiver.
     *
     * @since 21.1
     */
    @Substitution
    @Throws(others = @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/UnsupportedMessageException;"))
    abstract static class GetHashValuesIterator extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract @JavaType(Object.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver);

        @Specialization
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary iteratorInterop,
                        @Cached ThrowInteropExceptionAsGuest throwInteropExceptionAsGuest,
                        @Cached BranchProfile exceptionProfile) {
            try {
                Object iterator = interop.getHashValuesIterator(InteropUtils.unwrapForeign(getLanguage(), receiver));
                assert InteropLibrary.getUncached().isIterator(iterator);
                return InteropUtils.maybeWrapAsObject(iterator, iteratorInterop, getContext());
            } catch (InteropException e) {
                exceptionProfile.enter();
                throw throwInteropExceptionAsGuest.execute(e);
            }
        }
    }

    // endregion Hash(Dictionary) Messages

    /**
     * Converts a guest arguments array to a host Object[].
     *
     * <p>
     * In some cases, preserving the guest Java types of the arguments is preferred e.g. interop
     * with an Espresso object. As an optimization, this method may return the underlying array of
     * the guest arguments Object[].
     *
     * Returns a host Object[] with the arguments unwrapped or not depending on the unwrapArguments
     * argument.
     */
    abstract static class ToHostArguments extends SubstitutionNode {
        private static final Object[] EMPTY_ARRAY = new Object[0];

        abstract Object[] execute(boolean unwrapArguments, @JavaType(Object[].class) StaticObject arguments);

        @Specialization(guards = {
                        "!unwrapArguments",
                        "arguments.isEspressoObject()"
        })
        Object[] doEspressoNoUnwrap(
                        @SuppressWarnings("unused") boolean unwrapArguments,
                        @JavaType(Object[].class) StaticObject arguments) {
            return arguments.<StaticObject[]> unwrap(getLanguage());
        }

        @Specialization(guards = {
                        "unwrapArguments",
                        "arguments.isEspressoObject()",
        })
        Object[] doEspressoUnwrap(
                        @SuppressWarnings("unused") boolean unwrapArguments,
                        @JavaType(Object[].class) StaticObject arguments) {
            EspressoLanguage language = getLanguage();
            Object[] rawArgs = arguments.unwrap(language);
            if (rawArgs.length == 0) {
                return EMPTY_ARRAY;
            }
            Object[] hostArgs = new Object[rawArgs.length];
            for (int i = 0; i < rawArgs.length; ++i) {
                hostArgs[i] = InteropUtils.unwrap(language, (StaticObject) rawArgs[i], getMeta());
            }
            return hostArgs;
        }

        @Specialization(guards = {
                        "arguments.isForeignObject()",
        })
        Object[] doForeign(
                        boolean unwrapArguments,
                        @JavaType(Object[].class) StaticObject arguments,
                        @Cached GetArraySize getArraySize,
                        @Cached ReadArrayElement readArrayElement) {
            EspressoLanguage language = getLanguage();
            int argsLength = Math.toIntExact(getArraySize.execute(arguments));
            if (argsLength == 0) {
                return EMPTY_ARRAY;
            }
            Object[] hostArgs = new Object[argsLength];
            for (int i = 0; i < argsLength; ++i) {
                StaticObject elem = readArrayElement.execute(arguments, i);
                hostArgs[i] = unwrapArguments ? InteropUtils.unwrap(language, elem, getMeta()) : elem;
            }
            return hostArgs;
        }
    }

    /**
     * Throws host interop exceptions as guest exceptions.
     */
    abstract static class ThrowInteropExceptionAsGuest extends SubstitutionNode {
        static final int LIMIT = 2;

        abstract RuntimeException execute(InteropException hostException);

        @Specialization
        RuntimeException doUnsupportedMessageException(
                        UnsupportedMessageException e,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary causeInterop,
                        @Cached ConditionProfile noCauseProfile,
                        @Cached("create(getMeta().polyglot.UnsupportedMessageException_create.getCallTargetForceInit())") DirectCallNode create,
                        @Cached("create(getMeta().polyglot.UnsupportedMessageException_create_Throwable.getCallTargetForceInit())") DirectCallNode createWithCause) {
            Throwable cause = e.getCause();
            assert cause == null || cause instanceof AbstractTruffleException;
            StaticObject exception = noCauseProfile.profile(cause == null)
                            /* UnsupportedMessageException.create() */
                            ? (StaticObject) create.call()
                            /* UnsupportedMessageException.create(Throwable cause) */
                            : (StaticObject) createWithCause.call(InteropUtils.maybeWrapAsForeignException(cause, causeInterop, getContext()));
            throw EspressoException.wrap(exception, getMeta());
        }

        @Specialization
        RuntimeException doUnknownIdentifierException(
                        UnknownIdentifierException e,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary causeInterop,
                        @Cached ConditionProfile noCauseProfile,
                        @Cached("create(getMeta().polyglot.UnknownIdentifierException_create_String.getCallTargetForceInit())") DirectCallNode createWithIdentifier,
                        @Cached("create(getMeta().polyglot.UnknownIdentifierException_create_String_Throwable.getCallTargetForceInit())") DirectCallNode createWithIdentifierAndCause) {
            StaticObject unknownIdentifier = getMeta().toGuestString(e.getUnknownIdentifier());
            Throwable cause = e.getCause();
            StaticObject exception = noCauseProfile.profile(cause == null || !(cause instanceof AbstractTruffleException))
                            /* UnknownIdentifierException.create(String unknownIdentifier) */
                            ? (StaticObject) createWithIdentifier.call(unknownIdentifier)
                            /*
                             * UnknownIdentifierException.create(String unknownIdentifier, Throwable
                             * cause)
                             */
                            : (StaticObject) createWithIdentifierAndCause.call(unknownIdentifier, InteropUtils.maybeWrapAsForeignException(cause, causeInterop, getContext()));
            throw EspressoException.wrap(exception, getMeta());
        }

        @Specialization
        RuntimeException doArityException(
                        ArityException e,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary causeInterop,
                        @Cached ConditionProfile noCauseProfile,
                        @Cached("create(getMeta().polyglot.ArityException_create_int_int_int.getCallTargetForceInit())") DirectCallNode createWithArityRange,
                        @Cached("create(getMeta().polyglot.ArityException_create_int_int_int_Throwable.getCallTargetForceInit())") DirectCallNode createWithArityRangeAndCause) {
            int expectedMinArity = e.getExpectedMinArity();
            int expectedMaxArity = e.getExpectedMaxArity();
            int actualArity = e.getActualArity();
            Throwable cause = e.getCause();
            StaticObject exception = noCauseProfile.profile(cause == null || !(cause instanceof AbstractTruffleException))
                            /*
                             * ArityException.create(int expectedMinArity, int expectedMaxArity, int
                             * actualArity)
                             */
                            ? (StaticObject) createWithArityRange.call(expectedMinArity, expectedMaxArity, actualArity)
                            /*
                             * ArityException.create(int expectedMinArity, int expectedMaxArity, int
                             * actualArity, Throwable cause)
                             */
                            : (StaticObject) createWithArityRangeAndCause.call(expectedMinArity, expectedMinArity, actualArity,
                                            InteropUtils.maybeWrapAsForeignException(cause, causeInterop, getContext()));
            throw EspressoException.wrap(exception, getMeta());
        }

        @Specialization
        RuntimeException doUnsupportedTypeException(
                        UnsupportedTypeException e,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary causeInterop,
                        @Cached ConditionProfile noCauseProfile,
                        @Cached("create(getMeta().polyglot.UnsupportedTypeException_create_Object_array_String.getCallTargetForceInit())") DirectCallNode createWithValuesHint,
                        @Cached("create(getMeta().polyglot.UnsupportedTypeException_create_Object_array_String_Throwable.getCallTargetForceInit())") DirectCallNode createWithValuesHintAndCause) {
            Object[] hostValues = e.getSuppliedValues();
            // Transform suppliedValues[] into a guest Object[].
            StaticObject[] backingArray = new StaticObject[hostValues.length];
            for (int i = 0; i < backingArray.length; i++) {
                backingArray[i] = InteropUtils.maybeWrapAsObject(hostValues[i], InteropLibrary.getUncached(), getContext());
            }
            StaticObject suppliedValues = StaticObject.wrap(backingArray, getMeta());
            StaticObject hint = getMeta().toGuestString(e.getMessage());
            Throwable cause = e.getCause();
            StaticObject exception = noCauseProfile.profile(cause == null || !(cause instanceof AbstractTruffleException))
                            /*
                             * UnsupportedTypeException.create(Object[] suppliedValues, String hint)
                             */
                            ? (StaticObject) createWithValuesHint.call(suppliedValues, hint)
                            /*
                             * UnsupportedTypeException.create(Object[] suppliedValues, String hint,
                             * Throwable cause)
                             */
                            : (StaticObject) createWithValuesHintAndCause.call(suppliedValues, hint, InteropUtils.maybeWrapAsForeignException(cause, causeInterop, getContext()));
            throw EspressoException.wrap(exception, getMeta());
        }

        @Specialization
        RuntimeException doInvalidArrayIndexException(
                        InvalidArrayIndexException e,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary causeInterop,
                        @Cached ConditionProfile noCauseProfile,
                        @Cached("create(getMeta().polyglot.InvalidArrayIndexException_create_long.getCallTargetForceInit())") DirectCallNode createWithIndex,
                        @Cached("create(getMeta().polyglot.InvalidArrayIndexException_create_long_Throwable.getCallTargetForceInit())") DirectCallNode createWithIndexAndCause) {
            long invalidIndex = e.getInvalidIndex();
            Throwable cause = e.getCause();
            StaticObject exception = noCauseProfile.profile(cause == null || !(cause instanceof AbstractTruffleException))
                            /* InvalidArrayIndexException.create(long invalidIndex) */
                            ? (StaticObject) createWithIndex.call(invalidIndex)
                            /*
                             * InvalidArrayIndexException.create(long invalidIndex, Throwable cause)
                             */
                            : (StaticObject) createWithIndexAndCause.call(invalidIndex, InteropUtils.maybeWrapAsObject(cause, causeInterop, getContext()));
            throw EspressoException.wrap(exception, getMeta());
        }

        @Specialization
        RuntimeException doInvalidBufferOffsetException(
                        InvalidBufferOffsetException e,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary causeInterop,
                        @Cached ConditionProfile noCauseProfile,
                        @Cached("create(getMeta().polyglot.InvalidBufferOffsetException_create_long_long.getCallTargetForceInit())") DirectCallNode createWithOffsetLength,
                        @Cached("create(getMeta().polyglot.InvalidBufferOffsetException_create_long_long_Throwable.getCallTargetForceInit())") DirectCallNode createWithOffsetLengthAndCause) {
            long byteOffset = e.getByteOffset();
            long length = e.getLength();
            Throwable cause = e.getCause();
            StaticObject exception = noCauseProfile.profile(cause == null || !(cause instanceof AbstractTruffleException))
                            /* InvalidBufferOffsetException.create(long byteOffset, long length) */
                            ? (StaticObject) createWithOffsetLength.call(byteOffset, length)
                            /*
                             * InvalidBufferOffsetException.create(long byteOffset, long length,
                             * Throwable cause)
                             */
                            : (StaticObject) createWithOffsetLengthAndCause.call(byteOffset, length, InteropUtils.maybeWrapAsForeignException(cause, causeInterop, getContext()));
            throw EspressoException.wrap(exception, getMeta());
        }

        @Specialization
        RuntimeException doStopIterationException(
                        StopIterationException e,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary causeInterop,
                        @Cached ConditionProfile noCauseProfile,
                        @Cached("create(getMeta().polyglot.StopIterationException_create.getCallTargetForceInit())") DirectCallNode create,
                        @Cached("create(getMeta().polyglot.StopIterationException_create_Throwable.getCallTargetForceInit())") DirectCallNode createWithCause) {
            Throwable cause = e.getCause();
            StaticObject exception = noCauseProfile.profile(cause == null || !(cause instanceof AbstractTruffleException))
                            /* StopIterationException.create() */
                            ? (StaticObject) create.call()
                            /* StopIterationException.create(Throwable cause) */
                            : (StaticObject) createWithCause.call(InteropUtils.maybeWrapAsForeignException(cause, causeInterop, getContext()));
            throw EspressoException.wrap(exception, getMeta());
        }

        @Specialization
        RuntimeException doUnknownKeyException(
                        UnknownKeyException e,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary keyInterop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary causeInterop,
                        @Cached ConditionProfile noCauseProfile,
                        @Cached("create(getMeta().polyglot.UnknownKeyException_create_Object.getCallTargetForceInit())") DirectCallNode createWithKey,
                        @Cached("create(getMeta().polyglot.UnknownKeyException_create_Object_Throwable.getCallTargetForceInit())") DirectCallNode createWithKeyAndCause) {
            StaticObject unknownKey = InteropUtils.maybeWrapAsObject(e.getUnknownKey(), keyInterop, getContext());
            Throwable cause = e.getCause();
            StaticObject exception = noCauseProfile.profile(cause == null || !(cause instanceof AbstractTruffleException))
                            /* UnknownKeyException.create(Object unknownKey) */
                            ? (StaticObject) createWithKey.call(unknownKey)
                            /* UnknownKeyException.create(Object unknownKey, Throwable cause) */
                            : (StaticObject) createWithKeyAndCause.call(unknownKey, InteropUtils.maybeWrapAsObject(cause, causeInterop, getContext()));
            throw EspressoException.wrap(exception, getMeta());
        }

        @Fallback
        @TruffleBoundary
        RuntimeException shouldNotReachHere(InteropException e) {
            throw EspressoError.shouldNotReachHere("Unexpected host interop exception", e);
        }
    }

    private static EspressoException handleAbstractTruffleException(InteropLibrary exceptionInterop, EspressoContext context, LookupTypeConverterNode lookupTypeConverterNode,
                    LookupInternalTypeConverterNode internalTypeConverterNode, BranchProfile exceptionProfile, AbstractTruffleException ex) {
        exceptionProfile.enter();
        Meta meta = context.getMeta();
        StaticObject foreignException = context.getAllocator().createForeignException(context, ex, exceptionInterop);
        try {
            Object metaObject = exceptionInterop.getMetaObject(ex);
            String metaName = ToEspressoNode.getMetaName(metaObject, exceptionInterop);
            PolyglotTypeMappings.InternalTypeConverter internalTypeConverter = internalTypeConverterNode.execute(metaName);
            if (internalTypeConverter != null) {
                StaticObject convertedGuestException = internalTypeConverter.convertInternal(exceptionInterop, foreignException, meta, null);
                return EspressoException.wrap(convertedGuestException, meta);
            }
            PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
            if (converter != null) {
                StaticObject converted = (StaticObject) converter.convert(foreignException);
                /*
                 * The back trace of the foreign exception wrapper must be set to the foreign
                 * exception object, then the back trace is retained in the guest code and the
                 * stackTrace field set to null to trigger backtrace lookups
                 */
                meta.java_lang_Throwable_backtrace.setObject(converted, meta.java_lang_Throwable_backtrace.getObject(foreignException));
                if (meta.getJavaVersion().java9OrLater()) {
                    meta.java_lang_Throwable_depth.setInt(converted, meta.java_lang_Throwable_depth.getInt(foreignException));
                }
                meta.java_lang_Throwable_stackTrace.setObject(converted, StaticObject.NULL);
                meta.HIDDEN_FRAMES.setHiddenObject(converted, VM.StackTrace.FOREIGN_MARKER_STACK_TRACE);

                throw EspressoException.wrap(converted, meta);
            }
        } catch (UnsupportedMessageException | UnsupportedTypeException e) {
            // throw the original exception then
            throw ex;
        }
        throw EspressoException.wrap(foreignException, meta);
    }
}
