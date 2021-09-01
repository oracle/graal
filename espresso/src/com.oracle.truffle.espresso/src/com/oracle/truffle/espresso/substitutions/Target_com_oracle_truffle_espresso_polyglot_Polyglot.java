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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.helper.TypeCheckNode;
import com.oracle.truffle.espresso.nodes.helper.TypeCheckNodeGen;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Target_com_oracle_truffle_espresso_polyglot_PolyglotFactory.CastImplNodeGen;

@EspressoSubstitutions
public final class Target_com_oracle_truffle_espresso_polyglot_Polyglot {
    @Substitution
    public static boolean isForeignObject(@JavaType(Object.class) StaticObject object) {
        return object.isForeignObject();
    }

    // region Polyglot#cast

    @Substitution
    abstract static class Cast extends SubstitutionNode {

        abstract @JavaType(Object.class) StaticObject execute(
                        @JavaType(Class.class) StaticObject targetClass,
                        @JavaType(Object.class) StaticObject value);

        @Specialization(guards = "targetClass.getMirrorKlass() == cachedTargetKlass", limit = "1")
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Class.class) StaticObject targetClass,
                        @JavaType(Object.class) StaticObject value,
                        @Cached("targetClass.getMirrorKlass()") Klass cachedTargetKlass,
                        @Cached BranchProfile nullTargetClassProfile,
                        @Cached BranchProfile reWrappingProfile,
                        @Cached TypeCheckNode typeCheckNode,
                        @Cached CastImpl castImpl) {
            if (StaticObject.isNull(targetClass)) {
                nullTargetClassProfile.enter();
                Meta meta = getMeta();
                throw meta.throwException(meta.java_lang_NullPointerException);
            }
            if (StaticObject.isNull(value) || typeCheckNode.executeTypeCheck(cachedTargetKlass, value.getKlass())) {
                return value;
            }
            reWrappingProfile.enter();
            return castImpl.execute(getContext(), cachedTargetKlass, value);
        }

        @TruffleBoundary
        @ReportPolymorphism.Megamorphic
        @Specialization(replaces = "doCached")
        @JavaType(Object.class)
        StaticObject doGeneric(@JavaType(Class.class) StaticObject targetClass, @JavaType(Object.class) StaticObject value) {
            return doCached(targetClass, value, targetClass.getMirrorKlass(),
                            BranchProfile.getUncached(), BranchProfile.getUncached(),
                            TypeCheckNodeGen.getUncached(), CastImplNodeGen.getUncached());
        }
    }

    @GenerateUncached
    abstract static class CastImpl extends SubstitutionNode {

        static final int LIMIT = 3;

        abstract @JavaType(Object.class) StaticObject execute(EspressoContext context, Klass targetKlass, @JavaType(Object.class) StaticObject value);

        static boolean isPrimitiveKlass(Klass targetKlass) {
            return targetKlass.isPrimitive();
        }

        static boolean isArrayKlass(Klass targetKlass) {
            return targetKlass.isArray();
        }

        static boolean isNull(StaticObject object) {
            return StaticObject.isNull(object);
        }

        static boolean isStringKlass(EspressoContext context, Klass targetKlass) {
            return targetKlass == context.getMeta().java_lang_String;
        }

        static boolean isForeignException(EspressoContext context, Klass targetKlass) {
            Meta.PolyglotSupport polyglot = context.getMeta().polyglot;
            return polyglot != null /* polyglot support is enabled */
                            && targetKlass == polyglot.ForeignException;
        }

        @Specialization(guards = "value.isEspressoObject()")
        @JavaType(Object.class)
        StaticObject doEspresso(
                        EspressoContext context,
                        Klass targetKlass,
                        @JavaType(Object.class) StaticObject value,
                        @Cached TypeCheckNode typeCheck,
                        @Cached BranchProfile exceptionProfile) {
            if (isNull(value) || typeCheck.executeTypeCheck(targetKlass, value.getKlass())) {
                return value;
            }
            exceptionProfile.enter();
            Meta meta = context.getMeta();
            throw meta.throwException(meta.java_lang_ClassCastException);
        }

        @Specialization(guards = {
                        "isPrimitiveKlass(targetKlass)",
                        "value.isForeignObject()",
        })
        @JavaType(Object.class)
        StaticObject doPrimitive(
                        EspressoContext context,
                        Klass targetKlass,
                        @JavaType(Object.class) StaticObject value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            Meta meta = context.getMeta();
            try {
                return castToBoxed(targetKlass, value.rawForeignObject(), interop, meta, exceptionProfile);
            } catch (UnsupportedMessageException e) {
                exceptionProfile.enter();
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException,
                                "Couldn't read " + targetKlass.getTypeAsString() + " value from foreign object");
            }
        }

        @Specialization(guards = {
                        "isArrayKlass(targetKlass)",
                        "value.isForeignObject()"
        })
        @JavaType(Object.class)
        StaticObject doArray(
                        EspressoContext context,
                        Klass targetKlass,
                        @JavaType(Object.class) StaticObject value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            Meta meta = context.getMeta();
            Object foreignObject = value.rawForeignObject();

            // Array-like foreign objects can be wrapped as *[].
            // Buffer-like foreign objects can be wrapped (only) as byte[].
            if (interop.hasArrayElements(foreignObject) || (targetKlass == meta._byte_array && interop.hasBufferElements(foreignObject))) {
                return StaticObject.createForeign(context.getLanguage(), targetKlass, foreignObject, interop);
            }

            exceptionProfile.enter();
            throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Cannot cast non-array value to an array type");
        }

        @Specialization(guards = {
                        "isStringKlass(context, targetKlass)",
                        "value.isForeignObject()"
        })
        @JavaType(Object.class)
        StaticObject doString(
                        EspressoContext context,
                        @SuppressWarnings("unused") Klass targetKlass,
                        @JavaType(Object.class) StaticObject value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            Meta meta = context.getMeta();
            try {
                return meta.toGuestString(interop.asString(value.rawForeignObject()));
            } catch (UnsupportedMessageException e) {
                exceptionProfile.enter();
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Cannot cast a non-string foreign object to String");
            }
        }

        @Specialization(guards = {
                        "isForeignException(context, targetKlass)",
                        "value.isForeignObject()"
        })
        @JavaType(Object.class)
        StaticObject doForeignException(
                        EspressoContext context,
                        @SuppressWarnings("unused") Klass targetKlass,
                        @JavaType(Object.class) StaticObject value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            Meta meta = context.getMeta();
            // Casting to ForeignException skip the field checks.
            Object foreignObject = value.rawForeignObject();
            if (interop.isException(foreignObject)) {
                return StaticObject.createForeignException(meta, foreignObject, interop);
            }
            exceptionProfile.enter();
            throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Cannot cast a non-exception foreign object to ForeignException");
        }

        @Fallback
        @JavaType(Object.class)
        StaticObject doDataClassOrThrow(
                        EspressoContext context,
                        Klass targetKlass,
                        @JavaType(Object.class) StaticObject value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            Meta meta = context.getMeta();
            if (targetKlass.isAbstract()) {
                exceptionProfile.enter();
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Invalid cast to non-array abstract class");
            }
            assert targetKlass instanceof ObjectKlass;
            assert value.isForeignObject();
            ObjectKlass targetObjectKlass = (ObjectKlass) targetKlass;
            try {
                Object foreignObject = value.rawForeignObject();
                ToEspressoNode.checkHasAllFieldsOrThrow(foreignObject, targetObjectKlass, interop, meta);
                return StaticObject.createForeign(meta.getEspressoLanguage(), targetKlass, foreignObject, interop);
            } catch (ClassCastException e) {
                exceptionProfile.enter();
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, e.getMessage());
            }
        }
    }

    private static StaticObject castToBoxed(Klass targetKlass, Object foreignValue, InteropLibrary interopLibrary, Meta meta, BranchProfile exceptionProfile) throws UnsupportedMessageException {
        assert targetKlass.isPrimitive();
        switch (targetKlass.getJavaKind()) {
            case Boolean:
                boolean boolValue = interopLibrary.asBoolean(foreignValue);
                return meta.boxBoolean(boolValue);
            case Char:
                String value = interopLibrary.asString(foreignValue);
                if (value.length() != 1) {
                    exceptionProfile.enter();
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
                exceptionProfile.enter();
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Cannot cast to void");
        }
        CompilerDirectives.transferToInterpreter();
        throw EspressoError.shouldNotReachHere("Unexpected primitive klass: ", targetKlass);
    }

    // endregion Polyglot#cast

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
    public static @JavaType(Object.class) StaticObject eval(@JavaType(String.class) StaticObject language, @JavaType(String.class) StaticObject code, @Inject Meta meta) {
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
    public static @JavaType(Object.class) StaticObject importObject(@JavaType(String.class) StaticObject name, @Inject Meta meta) {
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
    public static void exportObject(@JavaType(String.class) StaticObject name, @JavaType(Object.class) StaticObject value, @Inject Meta meta) {
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
        return StaticObject.createForeign(meta.getEspressoLanguage(), meta.java_lang_Object, object, interopLibrary);
    }
}
