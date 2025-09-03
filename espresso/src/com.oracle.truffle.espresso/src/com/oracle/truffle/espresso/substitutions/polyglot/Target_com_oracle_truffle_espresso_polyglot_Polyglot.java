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
package com.oracle.truffle.espresso.substitutions.polyglot;

import static com.oracle.truffle.espresso.substitutions.SubstitutionFlag.IsTrivial;

import java.util.Set;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.EspressoType;
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
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;

@EspressoSubstitutions
public final class Target_com_oracle_truffle_espresso_polyglot_Polyglot {
    @Substitution(flags = {IsTrivial})
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

        static ToReference createToEspressoNode(EspressoType type, Meta meta) {
            if (type.getRawType() instanceof PrimitiveKlass primitiveKlass) {
                Klass boxedKlass = MethodArgsUtils.primitiveTypeToBoxedType(primitiveKlass);
                assert boxedKlass != null;
                return ToReference.createToReference(boxedKlass, meta);
            }
            return ToReference.createToReference(type, meta);
        }

        @Specialization(guards = "targetClass.getMirrorKlass() == cachedTargetKlass", limit = "2")
        @JavaType(Object.class)
        static StaticObject doCached(
                        @JavaType(Class.class) StaticObject targetClass,
                        @JavaType(Object.class) StaticObject value,
                        @Bind Node node,
                        @SuppressWarnings("unused") @Cached("targetClass.getMirrorKlass()") Klass cachedTargetKlass,
                        @Cached InlinedBranchProfile nullTargetClassProfile,
                        @Cached InlinedBranchProfile reWrappingProfile,
                        @Cached InlinedBranchProfile errorProfile,
                        @Bind("getMeta()") Meta meta,
                        @Cached("createInstanceOf(cachedTargetKlass)") InstanceOf instanceOfTarget,
                        @Cached("createToEspressoNode(cachedTargetKlass, meta)") ToReference toEspresso) {
            if (StaticObject.isNull(targetClass)) {
                nullTargetClassProfile.enter(node);
                throw meta.throwException(meta.java_lang_NullPointerException);
            }
            if (StaticObject.isNull(value) || instanceOfTarget.execute(value.getKlass())) {
                return value;
            }
            reWrappingProfile.enter(node);
            try {
                Object interopObject = value.isForeignObject() ? value.rawForeignObject(EspressoLanguage.get(node)) : value;
                return toEspresso.execute(interopObject);
            } catch (UnsupportedTypeException e) {
                errorProfile.enter(node);
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "%s cannot be cast to %s", value, targetClass.getMirrorKlass(meta).getTypeAsString());
            }
        }

        @ReportPolymorphism.Megamorphic
        @Specialization(replaces = "doCached")
        @JavaType(Object.class)
        static StaticObject doGeneric(@JavaType(Class.class) StaticObject targetClass,
                        @JavaType(Object.class) StaticObject value,
                        @Bind Node node,
                        @Cached InstanceOf.Dynamic instanceOfDynamic,
                        @Cached ToReference.DynamicToReference toEspresso) {
            Meta meta = EspressoContext.get(node).getMeta();
            if (StaticObject.isNull(targetClass)) {
                throw meta.throwException(meta.java_lang_NullPointerException);
            }
            if (StaticObject.isNull(value) || instanceOfDynamic.execute(value.getKlass(), targetClass.getMirrorKlass(meta))) {
                return value;
            }
            Klass mirrorKlass = targetClass.getMirrorKlass(meta);
            try {
                if (mirrorKlass instanceof PrimitiveKlass primitiveKlass) {
                    mirrorKlass = MethodArgsUtils.primitiveTypeToBoxedType(primitiveKlass);
                    assert mirrorKlass != null;
                }
                Object interopObject = value.isForeignObject() ? value.rawForeignObject(EspressoLanguage.get(node)) : value;
                return toEspresso.execute(interopObject, mirrorKlass);
            } catch (UnsupportedTypeException e) {
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "%s cannot be cast to %s", value, targetClass.getMirrorKlass(meta).getTypeAsString());
            }
        }
    }

    @Substitution
    abstract static class CastWithGenerics extends SubstitutionNode {

        @Idempotent
        static boolean isNull(StaticObject object) {
            return StaticObject.isNull(object);
        }

        static EspressoType getEspressoType(StaticObject targetType, Meta meta) {
            return (EspressoType) meta.polyglot.HIDDEN_TypeLiteral_internalType.getHiddenObject(targetType);
        }

        static ToReference createToEspressoNode(EspressoType type, Meta meta) {
            return ToReference.createToReference(type, meta);
        }

        protected static InstanceOf createInstanceOf(EspressoType superType) {
            return InstanceOf.create(superType.getRawType(), true);
        }

        abstract @JavaType(Object.class) StaticObject execute(
                        @JavaType(Object.class) StaticObject value,
                        @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/TypeLiteral;") StaticObject targetType);

        @Specialization(guards = "isNull(targetType)")
        @JavaType(Object.class)
        static StaticObject doNull(
                        @SuppressWarnings("unused") @JavaType(Object.class) StaticObject value,
                        @SuppressWarnings("unused") @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/TypeLiteral;") StaticObject targetType,
                        @SuppressWarnings("unused") @Bind Node node,
                        @Bind("get(node)") EspressoContext context) {
            throw context.getMeta().throwException(context.getMeta().java_lang_NullPointerException);
        }

        @Specialization(guards = {
                        "!isNull(targetType)",
                        "getEspressoType(targetType, context.getMeta()) == cachedTargetType"}, limit = "1")
        @JavaType(Object.class)
        static StaticObject doCached(
                        @JavaType(Object.class) StaticObject value,
                        @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/TypeLiteral;") StaticObject targetType,
                        @Bind Node node,
                        @SuppressWarnings("unused") @Bind("get(node)") EspressoContext context,
                        @Cached InlinedBranchProfile reWrappingProfile,
                        @Cached InlinedBranchProfile errorProfile,
                        @SuppressWarnings("unused") @Cached("getEspressoType(targetType, context.getMeta())") EspressoType cachedTargetType,
                        @Cached("createInstanceOf(cachedTargetType.getRawType())") InstanceOf instanceOfTarget,
                        @Cached("createToEspressoNode(cachedTargetType, context.getMeta())") ToReference toEspressoNode) {
            Meta meta = context.getMeta();
            if (StaticObject.isNull(value) || (!value.isForeignObject() && instanceOfTarget.execute(value.getKlass()))) {
                return value;
            }
            reWrappingProfile.enter(node);
            try {
                if (value.isForeignObject()) {
                    return toEspressoNode.execute(value.rawForeignObject(EspressoLanguage.get(node)));
                } else {
                    // we know it's not instance of the target type, so throw CCE
                    errorProfile.enter(node);
                    throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "%s cannot be cast to %s", value, targetType);
                }
            } catch (UnsupportedTypeException e) {
                if (value.isForeignObject() && cachedTargetType.getRawType().isAbstract() && !cachedTargetType.getRawType().isArray()) {
                    // allow foreign objects of assignable type to be returned, but only after
                    // trying to convert to a guest value with ToEspresso
                    if (instanceOfTarget.execute(value.getKlass())) {
                        return value;
                    }
                }
                errorProfile.enter(node);
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "%s cannot be cast to %s", value, targetType);
            }
        }

        @ReportPolymorphism.Megamorphic
        @Specialization(replaces = "doCached", guards = "!isNull(targetType)")
        @JavaType(Object.class)
        static StaticObject doGeneric(
                        @JavaType(Object.class) StaticObject value,
                        @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/TypeLiteral;") StaticObject targetType,
                        @Bind Node node,
                        @Cached InstanceOf.Dynamic instanceOfDynamic,
                        @Cached ToReference.DynamicToReference toEspressoNode) {
            Meta meta = EspressoContext.get(node).getMeta();
            if (StaticObject.isNull(value)) {
                return value;
            }
            EspressoType type = getEspressoType(targetType, meta);
            if (value.isForeignObject()) {
                try {
                    return toEspressoNode.execute(value.rawForeignObject(EspressoLanguage.get(node)), type);
                } catch (UnsupportedTypeException e) {
                    if (value.isForeignObject() && type.getRawType().isAbstract() && !type.getRawType().isArray()) {
                        // allow foreign objects of assignable type to be returned, but only after
                        // trying to convert to a guest value with ToEspresso
                        if (instanceOfDynamic.execute(value.getKlass(), type.getRawType())) {
                            return value;
                        }
                    }
                    throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "%s cannot be cast to %s", value, type);
                }
            } else if (instanceOfDynamic.execute(value.getKlass(), type.getRawType())) {
                return value;
            } else {
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "%s cannot be cast to %s", value, type);
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

    private static StaticObject createForeignObject(Object object, Meta meta, InteropLibrary interopLibrary) {
        return StaticObject.createForeign(meta.getLanguage(), meta.java_lang_Object, object, interopLibrary);
    }
}
