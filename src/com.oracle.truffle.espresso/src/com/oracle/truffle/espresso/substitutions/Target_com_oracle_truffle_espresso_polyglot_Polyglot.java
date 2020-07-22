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

import java.util.NoSuchElementException;
import java.util.Set;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@EspressoSubstitutions
public class Target_com_oracle_truffle_espresso_polyglot_Polyglot {
    @Substitution
    public static boolean isForeignObject(@Host(Object.class) StaticObject object) {
        return object.isForeignObject();
    }

    @Substitution
    public static @Host(Object.class) StaticObject cast(@Host(Class.class) StaticObject targetClass, @Host(Object.class) StaticObject value, @InjectMeta Meta meta) {
        Klass targetKlass = targetClass.getMirrorKlass();
        if (StaticObject.isNull(value)) {
            return value;
        }
        if (value.isForeignObject()) {
            if (targetKlass.isPrimitive()) {
                try {
                    return castToBoxed(targetKlass, value.rawForeignObject(), meta);
                } catch (UnsupportedMessageException e) {
                    throw Meta.throwExceptionWithMessage(meta.java_lang_ClassCastException,
                                    "Couldn't read " + targetKlass.getTypeAsString() + " value from foreign object");
                }
            }

            if (targetKlass.isAssignableFrom(value.getKlass())) {
                return value;
            }

            InteropLibrary interopLibrary = InteropLibrary.getUncached();

            try {
                checkHasAllFieldsOrThrow(value.rawForeignObject(), targetKlass, interopLibrary);
            } catch (NoSuchElementException e) {
                throw Meta.throwExceptionWithMessage(meta.java_lang_ClassCastException,
                                "Field " + e.getMessage() + " not found");
            }

            return StaticObject.createForeign(targetKlass, value.rawForeignObject(), interopLibrary);
        } else {
            return InterpreterToVM.checkCast(value, targetKlass);
        }
    }

    private static void checkHasAllFieldsOrThrow(Object foreignObject, Klass klass, InteropLibrary interopLibrary) {
        for (Field f : klass.getDeclaredFields()) {
            if (!f.isStatic() && !interopLibrary.isMemberExisting(foreignObject, f.getNameAsString())) {
                throw new NoSuchElementException(f.getNameAsString());
            }
        }
        if (klass.getSuperClass() != null) {
            checkHasAllFieldsOrThrow(foreignObject, klass.getSuperKlass(), interopLibrary);
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
                    throw Meta.throwExceptionWithMessage(meta.java_lang_ClassCastException,
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
                throw Meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Cannot cast to void");
        }
        throw EspressoError.shouldNotReachHere("Unexpected primitive klass: ", targetKlass);
    }

    @Substitution
    public static @Host(Object.class) StaticObject eval(@Host(String.class) StaticObject languageId, @Host(String.class) StaticObject code, @InjectMeta Meta meta) {
        String languageString = Meta.toHostString(languageId);
        Set<String> publicLanguages = meta.getContext().getEnv().getPublicLanguages().keySet();
        if (!publicLanguages.contains(languageString)) {
            throw Meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException,
                            "No language for id " + languageString + " found. Supported languages are: " + publicLanguages);
        }

        Source source = Source.newBuilder(languageId.toString(), code.toString(), "(eval)").build();
        CallTarget callTarget;
        try {
            callTarget = meta.getContext().getEnv().parsePublic(source);
        } catch (Exception e) {
            throw Meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Error when parsing the source: " + e.getMessage());
        }

        Object evalResult;
        try {
            evalResult = callTarget.call();
        } catch (Exception e) {
            if (e instanceof TruffleException) {
                throw Meta.throwExceptionWithMessage(meta.java_lang_RuntimeException, "Exception during evaluation: " + e.getMessage());
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
    public static @Host(Object.class) StaticObject importObject(@Host(String.class) StaticObject name, @InjectMeta Meta meta) {
        if (!meta.getContext().getEnv().isPolyglotBindingsAccessAllowed()) {
            throw Meta.throwExceptionWithMessage(meta.java_lang_SecurityException,
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
    public static void exportObject(@Host(String.class) StaticObject name, @Host(Object.class) StaticObject value, @InjectMeta Meta meta) {
        if (!meta.getContext().getEnv().isPolyglotBindingsAccessAllowed()) {
            throw Meta.throwExceptionWithMessage(meta.java_lang_SecurityException,
                            "Polyglot bindings are not accessible for this language. Use --polyglot or allowPolyglotAccess when building the context.");
        }
        String bindingName = Meta.toHostString(name);
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
