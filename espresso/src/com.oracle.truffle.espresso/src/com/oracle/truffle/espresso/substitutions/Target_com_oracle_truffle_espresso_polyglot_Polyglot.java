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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.PrimitiveKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.bytecodes.InstanceOf;
import com.oracle.truffle.espresso.nodes.interop.MethodArgsUtils;
import com.oracle.truffle.espresso.nodes.interop.ToReference;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@EspressoSubstitutions
public final class Target_com_oracle_truffle_espresso_polyglot_Polyglot {
    @Substitution(isTrivial = true)
    public static boolean isForeignObject(@JavaType(Object.class) StaticObject object) {
        return object.isForeignObject();
    }

    // region Polyglot#cast

    @Substitution
    abstract static class Cast extends SubstitutionNode {

        abstract @JavaType(Object.class) StaticObject execute(
                        @JavaType(Class.class) StaticObject targetClass,
                        @JavaType(Object.class) StaticObject value);

        protected static InstanceOf createInstanceOf(Klass superType) {
            return InstanceOf.create(superType, true);
        }

        @Specialization(guards = "targetClass.getMirrorKlass() == cachedTargetKlass", limit = "1")
        @JavaType(Object.class)
        StaticObject doCached(
                        @JavaType(Class.class) StaticObject targetClass,
                        @JavaType(Object.class) StaticObject value,
                        @Cached("targetClass.getMirrorKlass()") Klass cachedTargetKlass,
                        @Cached BranchProfile nullTargetClassProfile,
                        @Cached BranchProfile reWrappingProfile,
                        @Cached("createInstanceOf(cachedTargetKlass)") InstanceOf instanceOfTarget,
                        @Cached CastImpl castImpl) {
            if (StaticObject.isNull(targetClass)) {
                nullTargetClassProfile.enter();
                Meta meta = getMeta();
                throw meta.throwException(meta.java_lang_NullPointerException);
            }
            if (StaticObject.isNull(value) || (!value.isForeignObject() && instanceOfTarget.execute(value.getKlass()))) {
                return value;
            }
            reWrappingProfile.enter();
            return castImpl.execute(getContext(), cachedTargetKlass, value);
        }

        @ReportPolymorphism.Megamorphic
        @Specialization(replaces = "doCached")
        @JavaType(Object.class)
        StaticObject doGeneric(@JavaType(Class.class) StaticObject targetClass,
                        @JavaType(Object.class) StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOfDynamic,
                        @Cached CastImpl castImpl) {
            Meta meta = getMeta();
            if (StaticObject.isNull(targetClass)) {
                throw meta.throwException(meta.java_lang_NullPointerException);
            }
            if (StaticObject.isNull(value) || (!value.isForeignObject() && instanceOfDynamic.execute(value.getKlass(), targetClass.getMirrorKlass(meta)))) {
                return value;
            }
            return castImpl.execute(getContext(), targetClass.getMirrorKlass(meta), value);
        }
    }

    @GenerateUncached
    abstract static class CastImpl extends SubstitutionNode {

        static final int LIMIT = 3;

        abstract @JavaType(Object.class) StaticObject execute(EspressoContext context, Klass targetKlass, @JavaType(Object.class) StaticObject value);

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
                        @Cached InstanceOf.Dynamic instanceOfDynamic,
                        @Cached BranchProfile exceptionProfile) {
            if (isNull(value) || instanceOfDynamic.execute(value.getKlass(), targetKlass)) {
                return value;
            }
            exceptionProfile.enter();
            Meta meta = context.getMeta();
            throw meta.throwException(meta.java_lang_ClassCastException);
        }

        @Specialization(guards = "value.isForeignObject()")
        @JavaType(Object.class)
        StaticObject doPrimitive(
                        EspressoContext context,
                        PrimitiveKlass targetKlass,
                        @JavaType(Object.class) StaticObject value,
                        @Cached ToReference.DynamicToReference toEspresso,
                        @Cached BranchProfile exceptionProfile) {
            Meta meta = context.getMeta();
            try {
                Klass boxedKlass = MethodArgsUtils.primitiveTypeToBoxedType(targetKlass);
                return toEspresso.execute(value.rawForeignObject(getLanguage()), boxedKlass);
            } catch (UnsupportedTypeException e) {
                exceptionProfile.enter();
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException,
                                "Couldn't read %s value from foreign object", targetKlass.getType());
            }
        }

        @Specialization(guards = "value.isForeignObject()")
        @JavaType(Object.class)
        StaticObject doArray(
                        EspressoContext context,
                        ArrayKlass targetKlass,
                        @JavaType(Object.class) StaticObject value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            Meta meta = context.getMeta();
            Object foreignObject = value.rawForeignObject(getLanguage());

            // Array-like foreign objects can be wrapped as *[].
            // Buffer-like foreign objects can be wrapped (only) as byte[].
            if (interop.hasArrayElements(foreignObject) || (targetKlass == meta._byte_array && interop.hasBufferElements(foreignObject))) {
                return StaticObject.createForeign(meta.getLanguage(), targetKlass, foreignObject, interop);
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
                        @SuppressWarnings("unused") ObjectKlass targetKlass,
                        @JavaType(Object.class) StaticObject value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            Meta meta = context.getMeta();
            try {
                return meta.toGuestString(interop.asString(value.rawForeignObject(getLanguage())));
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
                        @SuppressWarnings("unused") ObjectKlass targetKlass,
                        @JavaType(Object.class) StaticObject value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            Meta meta = context.getMeta();
            // Casting to ForeignException skip the field checks.
            Object foreignObject = value.rawForeignObject(getLanguage());
            if (interop.isException(foreignObject)) {
                return StaticObject.createForeignException(context, foreignObject, interop);
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
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached InstanceOf.Dynamic instanceOfDynamic,
                        @Cached ToReference.DynamicToReference toEspresso,
                        @Cached BranchProfile exceptionProfile) {
            Meta meta = context.getMeta();
            if (targetKlass.isAbstract()) {
                // allow foreign objects of assignable type to be returned
                if (instanceOfDynamic.execute(value.getKlass(), targetKlass)) {
                    return value;
                }
                exceptionProfile.enter();
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Invalid cast to non-array abstract class");
            }
            assert targetKlass instanceof ObjectKlass;
            assert value.isForeignObject();
            ObjectKlass targetObjectKlass = (ObjectKlass) targetKlass;
            try {
                if (meta.isBoxed(targetObjectKlass)) {
                    Object foreignObject = value.rawForeignObject(getLanguage());
                    return StaticObject.createForeign(meta.getLanguage(), targetKlass, foreignObject, interop);
                } else {
                    return toEspresso.execute(value.rawForeignObject(getLanguage()), targetKlass);
                }
            } catch (UnsupportedTypeException e) {
                // allow foreign objects of assignable type to be returned, but only after trying to
                // convert to a guest value with ToEspresso
                if (instanceOfDynamic.execute(value.getKlass(), targetKlass)) {
                    return value;
                }
                exceptionProfile.enter();
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, e.getMessage());
            }
        }
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
            if (e instanceof AbstractTruffleException) {
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
    public static void exportObject(@JavaType(String.class) StaticObject name, @JavaType(Object.class) StaticObject value, @Inject EspressoLanguage language, @Inject Meta meta) {
        if (!meta.getContext().getEnv().isPolyglotBindingsAccessAllowed()) {
            throw meta.throwExceptionWithMessage(meta.java_lang_SecurityException,
                            "Polyglot bindings are not accessible for this language. Use --polyglot or allowPolyglotAccess when building the context.");
        }
        String bindingName = meta.toHostString(name);
        if (value.isForeignObject()) {
            meta.getContext().getEnv().exportSymbol(bindingName, value.rawForeignObject(language));
        } else {
            meta.getContext().getEnv().exportSymbol(bindingName, value);
        }
    }

    protected static StaticObject createForeignObject(Object object, Meta meta, InteropLibrary interopLibrary) {
        return StaticObject.createForeign(meta.getLanguage(), meta.java_lang_Object, object, interopLibrary);
    }
}
