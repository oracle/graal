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

import java.util.Set;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@EspressoSubstitutions
public final class Target_com_oracle_truffle_espresso_polyglot_Polyglot {
    @Substitution
    public static boolean isForeignObject(@JavaType(Object.class) StaticObject object) {
        return object.isForeignObject();
    }

    @Substitution
    public static @JavaType(Object.class) StaticObject cast(@JavaType(Class.class) StaticObject targetClass, @JavaType(Object.class) StaticObject value, @InjectMeta Meta meta) {
        if (StaticObject.isNull(value)) {
            return value;
        }
        Klass targetKlass = targetClass.getMirrorKlass();
        if (value.isForeignObject()) {
            if (targetKlass.isAssignableFrom(value.getKlass())) {
                return value;
            }

            if (targetKlass.isPrimitive()) {
                try {
                    return castToBoxed(targetKlass, value.rawForeignObject(), meta);
                } catch (UnsupportedMessageException e) {
                    throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException,
                                    "Couldn't read " + targetKlass.getTypeAsString() + " value from foreign object");
                }
            }

            if (targetKlass.isArray()) {
                InteropLibrary interopLibrary = InteropLibrary.getUncached();
                if (!interopLibrary.hasArrayElements(value.rawForeignObject())) {
                    throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Cannot cast a non-array value to an array type");
                }
                return StaticObject.createForeign(targetKlass, value.rawForeignObject(), interopLibrary);
            }

            if (targetKlass instanceof ObjectKlass) {
                if (targetKlass.isAbstract()) {
                    throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Cannot cast a foreign object to an abstract class: " + targetKlass.getTypeAsString());
                }

                InteropLibrary interopLibrary = InteropLibrary.getUncached();

                // TODO: remove eager conversion once TruffleString is available
                /*
                 * Eager String conversion is necessary here since there's no way to access the
                 * content/chars of foreign strings without a full conversion.
                 */
                if (targetKlass == meta.java_lang_String) {
                    if (!interopLibrary.isString(value.rawForeignObject())) {
                        throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Cannot cast a non-string foreign object to string");
                    }
                    try {
                        return meta.toGuestString(interopLibrary.asString(value.rawForeignObject()));
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw EspressoError.shouldNotReachHere("Contract violation: if isString returns true, asString must succeed.");
                    }
                }

                /*
                 * Casting to ForeignException skip the field checks.
                 */
                if (meta.polyglot != null /* polyglot enabled */ && meta.polyglot.ForeignException == targetKlass) {
                    if (!interopLibrary.isException(value.rawForeignObject())) {
                        throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Cannot cast a non-exception foreign object to ForeignException");
                    }
                    return StaticObject.createForeignException(meta, value.rawForeignObject(), interopLibrary);
                }

                try {
                    ToEspressoNode.checkHasAllFieldsOrThrow(value.rawForeignObject(), (ObjectKlass) targetKlass, interopLibrary, meta);
                } catch (ClassCastException e) {
                    throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Could not cast foreign object to " + targetKlass.getNameAsString() + ": " + e.getMessage());
                }
                return StaticObject.createForeign(targetKlass, value.rawForeignObject(), interopLibrary);
            }

            throw EspressoError.shouldNotReachHere("Klass is either Primitive, Object or Array");
        } else {
            return InterpreterToVM.checkCast(value, targetKlass);
        }
    }

    private static StaticObject castToBoxed(Klass targetKlass, Object foreignValue, Meta meta) throws UnsupportedMessageException {
        assert targetKlass.isPrimitive();
        InteropLibrary interopLibrary = InteropLibrary.getUncached();
        switch (targetKlass.getJavaKind()) {
            case Boolean:
                boolean boolValue = interopLibrary.asBoolean(foreignValue);
                return meta.boxBoolean(boolValue);
            case Char:
                String value = interopLibrary.asString(foreignValue);
                if (value.length() != 1) {
                    throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException,
                                    "Cannot cast string " + value + " to char");
                }
                return meta.boxCharacter(value.charAt(0));
            case Byte:
                byte byteValue = interopLibrary.asByte(foreignValue);
                return meta.boxByte(byteValue);
            case Short:
                short shortValue = interopLibrary.asShort(foreignValue);
                return meta.boxShort(shortValue);
            case Int:
                int intValue = interopLibrary.asInt(foreignValue);
                return meta.boxInteger(intValue);
            case Long:
                long longValue = interopLibrary.asLong(foreignValue);
                return meta.boxLong(longValue);
            case Float:
                float floatValue = interopLibrary.asFloat(foreignValue);
                return meta.boxFloat(floatValue);
            case Double:
                double doubleValue = interopLibrary.asDouble(foreignValue);
                return meta.boxDouble(doubleValue);
            case Void:
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Cannot cast to void");
        }
        throw EspressoError.shouldNotReachHere("Unexpected primitive klass: ", targetKlass);
    }

    @TruffleBoundary
    private static EspressoException rethrowExceptionAsEspresso(ObjectKlass exceptionKlass, String additionalMessage, Throwable originalException) {
        throw exceptionKlass.getMeta().throwExceptionWithMessage(exceptionKlass, additionalMessage + originalException.getMessage());
    }

    @TruffleBoundary
    private static Source getSource(String languageId, String code) {
        return Source.newBuilder(languageId, code, "(eval)").build();
    }

    @TruffleBoundary
    private static void validateLanguage(String languageId, Meta meta) {
        Set<String> publicLanguages = meta.getContext().getEnv().getPublicLanguages().keySet();
        if (!publicLanguages.contains(languageId)) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException,
                            "No language for id " + languageId + " found. Supported languages are: " + publicLanguages);
        }
    }

    // TODO(peterssen): Fix deprecation, GR-26729
    @SuppressWarnings("deprecation")
    @Substitution
    public static @JavaType(Object.class) StaticObject eval(@JavaType(String.class) StaticObject language, @JavaType(String.class) StaticObject code, @InjectMeta Meta meta) {
        String languageId = meta.toHostString(language);
        validateLanguage(languageId, meta);

        Source source = getSource(languageId, meta.toHostString(code));
        CallTarget callTarget;
        try {
            callTarget = meta.getContext().getEnv().parsePublic(source);
        } catch (Exception e) {
            throw rethrowExceptionAsEspresso(meta.java_lang_IllegalArgumentException, "Error when parsing the source: ", e);
        }

        Object evalResult;
        try {
            evalResult = callTarget.call();
        } catch (Exception e) {
            if (e instanceof com.oracle.truffle.api.TruffleException) {
                throw rethrowExceptionAsEspresso(meta.java_lang_RuntimeException, "Exception during evaluation: ", e);
            } else {
                throw e;
            }
        }
        if (evalResult instanceof StaticObject) {
            return (StaticObject) evalResult;
        }
        return createForeignObject(evalResult, meta, InteropLibrary.getUncached());
    }

    @Substitution
    public static @JavaType(Object.class) StaticObject importObject(@JavaType(String.class) StaticObject name, @InjectMeta Meta meta) {
        if (!meta.getContext().getEnv().isPolyglotBindingsAccessAllowed()) {
            throw meta.throwExceptionWithMessage(meta.java_lang_SecurityException,
                            "Polyglot bindings are not accessible for this language. Use --polyglot or allowPolyglotAccess when building the context.");
        }
        Object binding = meta.getContext().getEnv().importSymbol(name.toString());
        if (binding == null) {
            return StaticObject.NULL;
        }
        if (binding instanceof StaticObject) {
            return (StaticObject) binding;
        }
        return createForeignObject(binding, meta, InteropLibrary.getUncached());
    }

    @Substitution
    public static void exportObject(@JavaType(String.class) StaticObject name, @JavaType(Object.class) StaticObject value, @InjectMeta Meta meta) {
        if (!meta.getContext().getEnv().isPolyglotBindingsAccessAllowed()) {
            throw meta.throwExceptionWithMessage(meta.java_lang_SecurityException,
                            "Polyglot bindings are not accessible for this language. Use --polyglot or allowPolyglotAccess when building the context.");
        }
        String bindingName = meta.toHostString(name);
        if (value.isForeignObject()) {
            meta.getContext().getEnv().exportSymbol(bindingName, value.rawForeignObject());
        } else {
            meta.getContext().getEnv().exportSymbol(bindingName, value);
        }
    }

    protected static StaticObject createForeignObject(Object object, Meta meta, InteropLibrary interopLibrary) {
        return StaticObject.createForeign(meta.java_lang_Object, object, interopLibrary);
    }
}
