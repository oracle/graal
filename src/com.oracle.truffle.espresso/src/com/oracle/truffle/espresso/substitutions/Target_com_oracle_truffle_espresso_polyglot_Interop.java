package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
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
    public static boolean isNull(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.isNull(unwrap(receiver));
    }

    // region Boolean Messages

    /**
     * Returns <code>true</code> if the receiver represents a <code>boolean</code> like value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#isBoolean(Object)
     */
    @Substitution
    public static boolean isBoolean(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.isBoolean(unwrap(receiver));
    }

    /**
     * Returns the Java boolean value if the receiver represents a {@link #isBoolean(StaticObject)
     * boolean} like value.
     *
     * @see InteropLibrary#asBoolean(Object)
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static boolean asBoolean(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asBoolean(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
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
    public static boolean isString(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.isString(unwrap(receiver));
    }

    /**
     * Returns the Java string value if the receiver represents a {@link #isString(StaticObject) string}
     * like value.
     *
     * @see InteropLibrary#asString(Object) 
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(String.class) StaticObject asString(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
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
    public static boolean isNumber(@Host(Object.class) StaticObject receiver) {
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
    public static boolean fitsInByte(@Host(Object.class) StaticObject receiver) {
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
    public static boolean fitsInShort(@Host(Object.class) StaticObject receiver) {
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
    public static boolean fitsInInt(@Host(Object.class) StaticObject receiver) {
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
    public static boolean fitsInLong(@Host(Object.class) StaticObject receiver) {
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
    public static boolean fitsInFloat(@Host(Object.class) StaticObject receiver) {
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
    public static boolean fitsInDouble(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInDouble(unwrap(receiver));
    }

    /**
     * Returns the receiver value as Java byte primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asByte(Object)
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static byte asByte(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
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
    @Throws(UnsupportedMessageException.class)
    public static short asShort(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
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
    @Throws(UnsupportedMessageException.class)
    public static int asInt(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
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
    @Throws(UnsupportedMessageException.class)
    public static long asLong(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
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
    @Throws(UnsupportedMessageException.class)
    public static float asFloat(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
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
    @Throws(UnsupportedMessageException.class)
    public static double asDouble(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asDouble(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion Number Messages

    private static Object unwrap(StaticObject receiver) {
        return receiver.isForeignObject() ? receiver.rawForeignObject() : receiver;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    private static RuntimeException createInteropException(ObjectKlass exceptionKlass, InteropException cause) {
        StaticObject exception = Meta.initExceptionWithMessage(exceptionKlass, cause.getMessage());
        EspressoException espressoException = EspressoException.wrap(exception);
        espressoException.initCause(cause);
        return espressoException;
    }

    private static RuntimeException throwInteropException(InteropException e, Meta meta) {
        if (e instanceof UnsupportedMessageException) {
            throw createInteropException(meta.com_oracle_truffle_espresso_polyglot_UnsupportedMessageException, e);
        }
        if (e instanceof UnknownIdentifierException) {
            throw createInteropException(meta.com_oracle_truffle_espresso_polyglot_UnknownIdentifierException, e);
        }
        if (e instanceof ArityException) {
            throw createInteropException(meta.com_oracle_truffle_espresso_polyglot_ArityException, e);
        }
        if (e instanceof UnsupportedTypeException) {
            throw createInteropException(meta.com_oracle_truffle_espresso_polyglot_UnsupportedTypeException, e);
        }
        if (e instanceof InvalidArrayIndexException) {
            throw createInteropException(meta.com_oracle_truffle_espresso_polyglot_InvalidArrayIndexException, e);
        }
        CompilerDirectives.transferToInterpreter();
        throw EspressoError.unexpected("Unexpected interop exception: ", e);
    }
}
