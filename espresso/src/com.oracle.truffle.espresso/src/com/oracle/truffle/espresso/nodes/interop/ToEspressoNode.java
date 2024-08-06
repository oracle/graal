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
package com.oracle.truffle.espresso.nodes.interop;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.bytecodes.InstanceOf;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * Handles conversions of (potentially) foreign objects to Espresso types.
 */
@NodeInfo(shortName = "Convert to Espresso")
public abstract class ToEspressoNode extends EspressoNode {

    public abstract Object execute(Object value) throws UnsupportedTypeException;

    @TruffleBoundary
    public static ToEspressoNode createToEspresso(Klass targetType, Meta meta) {
        if (targetType.isPrimitive()) {
            switch (targetType.getJavaKind()) {
                case Boolean:
                    return ToPrimitiveFactory.ToBooleanNodeGen.create();
                case Byte:
                    return ToPrimitiveFactory.ToByteNodeGen.create();
                case Short:
                    return ToPrimitiveFactory.ToShortNodeGen.create();
                case Int:
                    return ToPrimitiveFactory.ToIntNodeGen.create();
                case Float:
                    return ToPrimitiveFactory.ToFloatNodeGen.create();
                case Long:
                    return ToPrimitiveFactory.ToLongNodeGen.create();
                case Double:
                    return ToPrimitiveFactory.ToDoubleNodeGen.create();
                case Char:
                    return ToPrimitiveFactory.ToCharNodeGen.create();
                case Void:
                    return ToReferenceFactory.ToVoidNodeGen.create();
            }
        }
        return ToReference.createToReference(targetType, meta);
    }

    @TruffleBoundary
    public static ToEspressoNode getUncachedToEspresso(Klass targetType, Meta meta) {
        if (targetType.isPrimitive()) {
            switch (targetType.getJavaKind()) {
                case Boolean:
                    return ToPrimitiveFactory.ToBooleanNodeGen.getUncached();
                case Byte:
                    return ToPrimitiveFactory.ToByteNodeGen.getUncached();
                case Short:
                    return ToPrimitiveFactory.ToShortNodeGen.getUncached();
                case Int:
                    return ToPrimitiveFactory.ToIntNodeGen.getUncached();
                case Float:
                    return ToPrimitiveFactory.ToFloatNodeGen.getUncached();
                case Long:
                    return ToPrimitiveFactory.ToLongNodeGen.getUncached();
                case Double:
                    return ToPrimitiveFactory.ToDoubleNodeGen.getUncached();
                case Char:
                    return ToPrimitiveFactory.ToCharNodeGen.getUncached();
                case Void:
                    return ToReferenceFactory.ToVoidNodeGen.getUncached();
            }
        }
        return ToReference.getUncachedToReference(targetType, meta);
    }

    @NodeInfo(shortName = "Dynamic toEspresso node")
    @GenerateUncached
    public abstract static class DynamicToEspresso extends EspressoNode {
        protected static final int LIMIT = 4;

        public abstract Object execute(Object value, Klass targetType) throws UnsupportedTypeException;

        protected static ToEspressoNode createToEspressoNode(Klass targetType) {
            return ToEspressoNode.createToEspresso(targetType, targetType.getMeta());
        }

        @Specialization(guards = "targetType == cachedTargetType", limit = "LIMIT")
        public Object doCached(Object value, @SuppressWarnings("unused") Klass targetType,
                        @SuppressWarnings("unused") @Cached("targetType") Klass cachedTargetType,
                        @Cached("createToEspressoNode(cachedTargetType)") ToEspressoNode toEspressoNode) throws UnsupportedTypeException {
            return toEspressoNode.execute(value);
        }

        @Megamorphic
        @Specialization(replaces = "doCached")
        public Object doGeneric(Object value, Klass targetType,
                        @Cached ToEspressoNode.GenericToEspresso genericToEspresso) throws UnsupportedTypeException {
            return genericToEspresso.execute(value, targetType);
        }
    }

    @NodeInfo(shortName = "Generic toEspresso node")
    @GenerateUncached
    @ImportStatic(ToEspressoNode.class)
    public abstract static class GenericToEspresso extends EspressoNode {
        protected static final int LIMIT = 2;

        public abstract Object execute(Object value, Klass targetType) throws UnsupportedTypeException;

        public static boolean isStaticObject(Object value) {
            return value instanceof StaticObject;
        }

        @Specialization
        public Object doStaticObject(StaticObject value, Klass targetType,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Cached InlinedBranchProfile error) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), targetType)) {
                return value; // pass through, NULL coercion not needed.
            }
            error.enter(this);
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to ", targetType.getTypeAsString()));
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public Object doForeignNull(Object value, @SuppressWarnings("unused") Klass targetType,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached InlinedBranchProfile error) throws UnsupportedTypeException {
            if (targetType.isPrimitive()) {
                error.enter(this);
                throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to ", targetType.getTypeAsString()));
            }
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "isTypeMappingEnabled(targetType)",
                        "!isStaticObject(value)"
        })
        public Object doMappedInterface(Object value, Klass targetType,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached InlinedBranchProfile error) throws UnsupportedTypeException {
            try {
                Object metaObject = getMetaObjectOrThrow(value, interop);
                WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, getMetaName(metaObject, interop), targetType);
                if (proxyKlass != null) {
                    targetType.safeInitialize();
                    return proxyKlass.createProxyInstance(value, getLanguage(), interop);
                }
                error.enter(this);
                throw new ClassCastException();
            } catch (ClassCastException e) {
                error.enter(this);
                throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", targetType.getTypeAsString()));
            }
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public Object doArray(Object value, ArrayKlass targetType,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached InlinedBranchProfile error) throws UnsupportedTypeException {
            if (targetType == getMeta()._byte_array) {
                if (interop.hasBufferElements(value) && !isHostString(value)) {
                    return StaticObject.createForeign(EspressoLanguage.get(this), getMeta()._byte_array, value, interop);
                }
            }
            if (interop.hasArrayElements(value) && !isHostString(value)) {
                return StaticObject.createForeign(EspressoLanguage.get(this), targetType, value, interop);
            }
            error.enter(this);
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "isTypeConverterEnabled(targetType)",
                        "!isStaticObject(value)"
        })
        public Object doTypeConverter(Object value, Klass targetType,
                        @Cached LookupTypeConverterNode lookupTypeConverter,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached InlinedBranchProfile error) throws UnsupportedTypeException {
            try {
                Object metaObject = getMetaObjectOrThrow(value, interop);
                String metaName = getMetaName(metaObject, interop);

                // check if there's a specific type mapping available
                PolyglotTypeMappings.TypeConverter converter = lookupTypeConverter.execute(metaName);
                if (converter != null) {
                    return converter.convert(StaticObject.createForeign(getLanguage(), targetType, value, interop));
                }
                error.enter(this);
                throw new ClassCastException();
            } catch (ClassCastException e) {
                error.enter(this);
                throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", targetType.getNameAsString(), e.getMessage()));
            }
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "isInternalTypeConverterEnabled(targetType)",
                        "!isStaticObject(value)"
        })
        public Object doInternalTypeConverter(Object value, Klass targetType,
                        @Cached ToReference.DynamicToReference converterToEspresso,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverter,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached InlinedBranchProfile error) throws UnsupportedTypeException {
            try {
                Object metaObject = getMetaObjectOrThrow(value, interop);
                String metaName = getMetaName(metaObject, interop);

                // check if there's a specific type mapping available
                PolyglotTypeMappings.InternalTypeConverter converter = lookupInternalTypeConverter.execute(metaName);
                if (converter != null) {
                    return converter.convertInternal(interop, value, getMeta(), converterToEspresso);
                }
                error.enter(this);
                throw new ClassCastException();
            } catch (ClassCastException e) {
                error.enter(this);
                throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", targetType.getNameAsString(), e.getMessage()));
            }
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "!isStaticObject(value)",
                        "!targetType.isArray()",
                        "!isTypeConverterEnabled(targetType)",
                        "!isTypeMappingEnabled(targetType)"
        })
        public Object doGeneric(Object value, Klass targetType,
                        @Bind("getMeta()") Meta meta,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached ToReference.DynamicToReference converterToEspresso,
                        @Cached InlinedBranchProfile unknownProfile) throws UnsupportedTypeException {
            ToEspressoNode uncachedToEspresso = getUncachedToEspresso(targetType, meta);
            if (uncachedToEspresso != null) {
                return uncachedToEspresso.execute(value);
            }
            unknownProfile.enter(this);
            // hit the unknown type case, so inline generic handling for that here
            if (targetType instanceof ObjectKlass) {
                StaticObject result = ToReference.tryConverterForUnknownTarget(value, interop, lookupTypeConverterNode, lookupInternalTypeConverterNode, converterToEspresso, meta);
                if (result != null) {
                    return result;
                }
                try {
                    checkHasAllFieldsOrThrow(value, (ObjectKlass) targetType, interop, getMeta());
                    return StaticObject.createForeign(getLanguage(), targetType, value, interop);
                } catch (ClassCastException e) {
                    throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
                }
            }
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
        }
    }

    public static boolean isHostString(Object obj) {
        return obj instanceof String;
    }

    public static boolean isTypeMappingEnabled(Klass klass) {
        return klass.typeConversionState == Klass.INTERFACE_MAPPED;
    }

    public static boolean isTypeConverterEnabled(Klass klass) {
        return klass.isTypeMapped();
    }

    public static boolean isInternalTypeConverterEnabled(Klass klass) {
        return klass.isInternalTypeMapped();
    }

    public static boolean isForeignException(Klass klass, Meta meta) {
        return meta.polyglot != null /* polyglot enabled */ && meta.polyglot.ForeignException.equals(klass);
    }

    public static String getMetaName(Object metaObject, InteropLibrary interop) {
        assert interop.isMetaObject(metaObject);
        try {
            return interop.asString(interop.getMetaQualifiedName(metaObject));
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere();
        }
    }

    public static Object getMetaObjectOrThrow(Object value, InteropLibrary interop) throws ClassCastException {
        try {
            return interop.getMetaObject(value);
        } catch (UnsupportedMessageException e) {
            throw new ClassCastException("Could not lookup meta object");
        }
    }

    @TruffleBoundary
    public static void checkHasAllFieldsOrThrow(Object value, ObjectKlass klass, InteropLibrary interopLibrary, Meta meta) {
        CompilerAsserts.partialEvaluationConstant(klass);
        /*
         * For boxed types a .value member is not required if there's a direct conversion via
         * interop as* methods.
         */
        if (meta.isBoxed(klass)) {
            try {
                if ((klass == meta.java_lang_Integer && interopLibrary.fitsInInt(value)) ||
                                (klass == meta.java_lang_Long && interopLibrary.fitsInLong(value)) ||
                                (klass == meta.java_lang_Float && interopLibrary.fitsInFloat(value)) ||
                                (klass == meta.java_lang_Double && interopLibrary.fitsInDouble(value)) ||
                                (klass == meta.java_lang_Boolean && interopLibrary.isBoolean(value)) ||
                                (klass == meta.java_lang_Short && interopLibrary.fitsInShort(value)) ||
                                (klass == meta.java_lang_Byte && interopLibrary.fitsInByte(value)) ||
                                (klass == meta.java_lang_Character && interopLibrary.isString(value) && interopLibrary.asString(value).length() == 1)) {
                    return;
                }
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        for (Field f : klass.getDeclaredFields()) {
            if (!f.isStatic() && !interopLibrary.isMemberExisting(value, f.getNameAsString())) {
                throw new ClassCastException("Missing field: " + f.getNameAsString());
            }
        }
        if (klass.getSuperClass() != null) {
            checkHasAllFieldsOrThrow(value, klass.getSuperKlass(), interopLibrary, meta);
        }
    }
}
