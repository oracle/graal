package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
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
    @Substitution
    public static boolean isString(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.isString(unwrap(receiver));
    }

    /**
     * Returns the Java string value if the receiver represents a {@link #isString(StaticObject)
     * string} like value.
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

    // region Exception Messages

    /**
     * Returns <code>true</code> if the receiver value represents a throwable exception/error}.
     * Invoking this message does not cause any observable side-effects. Returns <code>false</code>
     * by default.
     * <p>
     * Objects must only return <code>true</code> if they support {@link #throwException} as well.
     * If this method is implemented then also {@link InteropLibrary#throwException(Object)} must be
     * implemented.
     * <p>
     * The following simplified {@code TryCatchNode} shows how the exceptions should be handled by
     * languages.
     *
     * @see InteropLibrary#isException(Object)
     * @since 19.3
     */
    @Substitution
    public static boolean isException(@Host(Object.class) StaticObject receiver) {
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
    @Throws(UnsupportedMessageException.class)
    public static @Host(RuntimeException.class) StaticObject throwException(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
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
    @Throws(UnsupportedMessageException.class)
    public static @Host(typeName = "Lcom/oracle/truffle/espresso/polyglot/ExceptionType;") StaticObject getExceptionType(
                    @Host(Object.class) StaticObject receiver,
                    @InjectMeta Meta meta) {
        try {
            ExceptionType exceptionType = UNCACHED.getExceptionType(unwrap(receiver));
            StaticObject staticStorage = meta.polyglot.ExceptionType.tryInitializeAndGetStatics();
            // @formatter:off
            switch (exceptionType) {
                case EXIT          : return (StaticObject) meta.polyglot.ExceptionType_EXIT.get(staticStorage);
                case INTERRUPT     : return (StaticObject) meta.polyglot.ExceptionType_INTERRUPT.get(staticStorage);
                case RUNTIME_ERROR : return (StaticObject) meta.polyglot.ExceptionType_RUNTIME_ERROR.get(staticStorage);
                case PARSE_ERROR   : return (StaticObject) meta.polyglot.ExceptionType_PARSE_ERROR.get(staticStorage);
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
    @Throws(UnsupportedMessageException.class)
    public static boolean isExceptionIncompleteSource(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
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
    @Throws(UnsupportedMessageException.class)
    public static int getExceptionExitStatus(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
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
    public static boolean hasExceptionCause(@Host(Object.class) StaticObject receiver) {
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
    @Throws(UnsupportedMessageException.class)
    public static @Host(Object.class) StaticObject getExceptionCause(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            Object cause = UNCACHED.getExceptionCause(unwrap(receiver));
            assert UNCACHED.isException(cause);
            assert !UNCACHED.isNull(cause);
            if (cause instanceof StaticObject) {
                return (StaticObject) cause; // Already typed, do not re-type.
            }
            // The cause must be an exception, wrap it as ForeignException.
            return StaticObject.createForeign(meta.polyglot.ForeignException, cause, UNCACHED);
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
    public static boolean hasExceptionMessage(@Host(Object.class) StaticObject receiver) {
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
    @Throws(UnsupportedMessageException.class)
    public static @Host(Object.class) StaticObject getExceptionMessage(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            Object message = UNCACHED.getExceptionMessage(unwrap(receiver));
            assert UNCACHED.isString(message);
            if (message instanceof StaticObject) {
                return (StaticObject) message;
            }
            // TODO(peterssen): Cannot wrap as String even if the foreign object is String-like.
            // Executing String methods, that rely on it having a .value field is not supported yet
            // in Espresso.
            return StaticObject.createForeign(meta.java_lang_Object, message, UNCACHED);
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
    public static boolean hasExceptionStackTrace(@Host(Object.class) StaticObject receiver) {
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
    @Throws(UnsupportedMessageException.class)
    public static @Host(Object.class) StaticObject getExceptionStackTrace(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            Object stackTrace = UNCACHED.getExceptionStackTrace(unwrap(receiver));
            if (stackTrace instanceof StaticObject) {
                return (StaticObject) stackTrace;
            }
            // Return foreign object as an opaque j.l.Object.
            return StaticObject.createForeign(meta.java_lang_Object, stackTrace, UNCACHED);
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
    public static boolean hasArrayElements(@Host(Object.class) StaticObject receiver) {
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
    public static @Host(Object.class) StaticObject readArrayElement(@Host(Object.class) StaticObject receiver, long index, @InjectMeta Meta meta) {
        try {
            Object value = UNCACHED.readArrayElement(unwrap(receiver), index);
            if (value instanceof StaticObject) {
                return (StaticObject) value;
            }
            // The foreign object *could* be wrapped into a more precise Java type, but inferring
            // this Java type
            // from the interop "kind" (string, primitive, exception, array...) is ambiguous and
            // inefficient.
            // The caller is responsible to re-wrap or convert the result as needed.
            return StaticObject.createForeign(meta.java_lang_Object, value, UNCACHED);
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
    @Throws(UnsupportedMessageException.class)
    public static long getArraySize(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
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
    public static boolean isArrayElementReadable(@Host(Object.class) StaticObject receiver, long index) {
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
    public static void writeArrayElement(@Host(Object.class) StaticObject receiver, long index, @Host(Object.class) StaticObject value, @InjectMeta Meta meta) {
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
    public static void removeArrayElement(@Host(Object.class) StaticObject receiver, long index, @InjectMeta Meta meta) {
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
    public static boolean isArrayElementModifiable(@Host(Object.class) StaticObject receiver, long index) {
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
    public static boolean isArrayElementInsertable(@Host(Object.class) StaticObject receiver, long index) {
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
    public static boolean isArrayElementRemovable(@Host(Object.class) StaticObject receiver, long index) {
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
     * Metaobjects for primitive values or values of other languages may be provided using
     * language views. While an object is
     * associated with a metaobject in one language, the metaobject might be a different when viewed
     * from another language.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link InteropLibrary#getMetaObject(Object)} must be implemented.
     *
     * @see InteropLibrary#hasMetaObject(Object)
     * @since 20.1
     */
    @Substitution
    public static boolean hasMetaObject(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.hasMetaObject(unwrap(receiver));
    }

    /**
     * Returns the metaobject that is associated with this value. The metaobject represents a
     * description of the object, reveals its kind and its features. Some information that a
     * metaobject might define includes the base object's type, interface, class, methods,
     * attributes, etc. When no metaobject is known for this type. Throws
     * {@link UnsupportedMessageException} by default.
     * <p>
     * The returned object must return <code>true</code> for {@link InteropLibrary#isMetaObject(Object)} and
     * provide implementations for {@link InteropLibrary#getMetaSimpleName(Object)},
     * {@link InteropLibrary#getMetaQualifiedName(Object)}, and {@link InteropLibrary#isMetaInstance(Object, Object)}. For all
     * values with metaobjects it must at hold that
     * <code>isMetaInstance(getMetaObject(value), value) ==
     * true</code>.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link InteropLibrary#hasMetaObject(Object)} must be implemented.
     *
     * @see InteropLibrary#hasMetaObject(Object)
     * @since 20.1
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(Object.class) StaticObject getMetaObject(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            Object metaObject = UNCACHED.getMetaObject(unwrap(receiver));
            if (metaObject instanceof StaticObject) {
                return (StaticObject) metaObject;
            }
            return StaticObject.createForeign(meta.java_lang_Object, metaObject, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Converts the receiver to a human readable {@link InteropLibrary#isString(Object) string}. Each language may
     * have special formating conventions - even primitive values may not follow the traditional
     * Java rules. The format of the returned string is intended to be interpreted by humans not
     * machines and should therefore not be relied upon by machines. By default the receiver class
     * name and its {@link System#identityHashCode(Object) identity hash code} is used as string
     * representation.
     *
     * @param allowSideEffects whether side-effects are allowed in the production of the string.
     * @since 20.1
     */
    @Substitution
    public static @Host(Object.class) StaticObject toDisplayString(@Host(Object.class) StaticObject receiver, boolean allowSideEffects, @InjectMeta Meta meta) {
        Object displayString = UNCACHED.toDisplayString(unwrap(receiver), allowSideEffects);
        if (displayString instanceof StaticObject) {
            return (StaticObject) displayString;
        }
        return StaticObject.createForeign(meta.java_lang_Object, displayString, UNCACHED);
    }

    /**
     * Converts the receiver to a human readable {@link InteropLibrary#isString(Object) string} of the language.
     * Short-cut for <code>{@link InteropLibrary#toDisplayString(Object) toDisplayString}(true)</code>.
     *
     * @see InteropLibrary#toDisplayString(Object, boolean)
     * @since 20.1
     */
    public static @Host(Object.class) StaticObject toDisplayString(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        return toDisplayString(receiver, true, meta);
    }

    /**
     * Returns <code>true</code> if the receiver value represents a metaobject. Metaobjects may be
     * values that naturally occur in a language or they may be returned by
     * {@link InteropLibrary#getMetaObject(Object)}. A metaobject represents a description of the object, reveals
     * its kind and its features. If a receiver is a metaobject it is often also
     * {@link InteropLibrary#isInstantiable(Object) instantiable}, but this is not a requirement.
     * <p>
     * <b>Sample interpretations:</b> In Java an instance of the type {@link Class} is a metaobject.
     * In JavaScript any function instance is a metaobject. For example, the metaobject of a
     * JavaScript class is the associated constructor function.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link InteropLibrary#getMetaQualifiedName(Object)}, {@link InteropLibrary#getMetaSimpleName(Object)} and
     * {@link InteropLibrary#isMetaInstance(Object, Object)} must be implemented as well.
     *
     * @since 20.1
     */
    @Substitution
    public static boolean isMetaObject(@Host(Object.class) StaticObject receiver) {
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
    @Throws(UnsupportedMessageException.class)
    public static @Host(Object.class) StaticObject getMetaQualifiedName(@Host(Object.class) StaticObject metaObject, @InjectMeta Meta meta) {
        try {
            Object qualifiedName =  UNCACHED.getMetaQualifiedName(unwrap(metaObject));
            if (qualifiedName instanceof StaticObject) {
                return (StaticObject) qualifiedName;
            }
            return StaticObject.createForeign(meta.java_lang_Object, qualifiedName, UNCACHED);
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
    @Throws(UnsupportedMessageException.class)
    public static @Host(Object.class) StaticObject getMetaSimpleName(@Host(Object.class) StaticObject metaObject, @InjectMeta Meta meta) {
        try {
            Object simpleName =  UNCACHED.getMetaSimpleName(unwrap(metaObject));
            if (simpleName instanceof StaticObject) {
                return (StaticObject) simpleName;
            }
            return StaticObject.createForeign(meta.java_lang_Object, simpleName, UNCACHED);
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
    @Throws(UnsupportedMessageException.class)
    public static boolean isMetaInstance(@Host(Object.class) StaticObject receiver, @Host(Object.class) StaticObject instance, @InjectMeta Meta meta) {
        try {
            return UNCACHED.isMetaInstance(unwrap(receiver), unwrap(instance));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion MetaObject Messages

    private static Object unwrap(StaticObject receiver) {
        return receiver.isForeignObject() ? receiver.rawForeignObject() : receiver;
    }

    private static StaticObject wrapForeignException(Throwable throwable, Meta meta) {
        assert UNCACHED.isException(throwable);
        assert throwable instanceof  AbstractTruffleException;
        return StaticObject.createForeign(meta.polyglot.ForeignException, throwable, UNCACHED);
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
            throw EspressoException.wrap(exception);
        }

        if (e instanceof UnknownIdentifierException) {
            StaticObject unknownIdentifier = meta.toGuestString(((UnknownIdentifierException) e).getUnknownIdentifier());
            Throwable cause = e.getCause();
            assert cause == null || cause instanceof AbstractTruffleException;
            StaticObject exception = (cause == null)
                    // UnknownIdentifierException.create(String unknownIdentifier)
                    ? (StaticObject) meta.polyglot.UnknownIdentifierException_create_String.invokeDirect(null, unknownIdentifier)
                    // UnknownIdentifierException.create(String unknownIdentifier, Throwable cause)
                    : (StaticObject) meta.polyglot.UnknownIdentifierException_create_String_Throwable.invokeDirect(null, unknownIdentifier, wrapForeignException(cause, meta));
            throw EspressoException.wrap(exception);
        }

        if (e instanceof ArityException) {
            int expectedArity = ((ArityException) e).getExpectedArity();
            int actualArity = ((ArityException) e).getActualArity();
            Throwable cause = e.getCause();
            assert cause == null || cause instanceof AbstractTruffleException;
            StaticObject exception = (cause == null)
                    // ArityException.create(int expectedArity, int actualArity)
                    ? (StaticObject) meta.polyglot.UnknownIdentifierException_create_String.invokeDirect(null, expectedArity, actualArity)
                    // ArityException.create(int expectedArity, int actualArity, Throwable cause)
                    : (StaticObject) meta.polyglot.UnknownIdentifierException_create_String_Throwable.invokeDirect(null, expectedArity, actualArity, wrapForeignException(cause, meta));
            throw EspressoException.wrap(exception);
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
                    backingArray[i] = StaticObject.createForeign(meta.java_lang_Object, value, UNCACHED);
                }
            }
            StaticObject suppliedValues = StaticObject.wrap(backingArray, meta);
            StaticObject hint = meta.toGuestString(e.getMessage());
            Throwable cause = e.getCause();
            assert cause == null || cause instanceof AbstractTruffleException;
            StaticObject exception = (cause == null)
                    // UnsupportedTypeException.create(Object[] suppliedValues, String hint)
                    ? (StaticObject) meta.polyglot.UnsupportedTypeException_create_Object_array_String.invokeDirect(null, suppliedValues, hint)
                    // UnsupportedTypeException.create(Object[] suppliedValues, String hint, Throwable cause)
                    : (StaticObject) meta.polyglot.UnsupportedTypeException_create_Object_array_String_Throwable.invokeDirect(null, suppliedValues, hint, wrapForeignException(cause, meta));
            throw EspressoException.wrap(exception);
        }

        if (e instanceof InvalidArrayIndexException) {
            long invalidIndex = ((InvalidArrayIndexException) e).getInvalidIndex();
            Throwable cause = e.getCause();
            assert cause == null || cause instanceof AbstractTruffleException;
            StaticObject exception = (cause == null)
                    // InvalidArrayIndexException.create(long invalidIndex)
                    ? (StaticObject) meta.polyglot.InvalidArrayIndexException_create_long.invokeDirect(null, invalidIndex)
                    // InvalidArrayIndexException.create(long invalidIndex, Throwable cause)
                    : (StaticObject) meta.polyglot.InvalidArrayIndexException_create_long_Throwable.invokeDirect(null, invalidIndex, wrapForeignException(cause, meta));
            throw EspressoException.wrap(exception);
        }

        CompilerDirectives.transferToInterpreter();
        throw EspressoError.unexpected("Unexpected interop exception: ", e);
    }
}
