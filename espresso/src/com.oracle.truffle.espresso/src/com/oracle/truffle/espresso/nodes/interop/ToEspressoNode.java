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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.EspressoType;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.ParameterizedEspressoType;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.bytecodes.InstanceOf;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * Handles conversions of (potentially) foreign objects to Espresso types.
 */
@NodeInfo(shortName = "Convert to Espresso")
public abstract class ToEspressoNode extends EspressoNode {

    public abstract Object execute(Object value) throws UnsupportedTypeException;

    public static boolean isStaticObject(Object value) {
        return value instanceof StaticObject;
    }

    @TruffleBoundary
    public static ToEspressoNode createToEspresso(EspressoType targetType, Meta meta) {
        Klass rawType = targetType.getRawType();
        if (rawType.isPrimitive()) {
            switch (rawType.getJavaKind()) {
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
    public static ToEspressoNode getUncachedToEspresso(EspressoType targetType, Meta meta) {
        Klass rawType = targetType.getRawType();
        if (rawType.isPrimitive()) {
            switch (rawType.getJavaKind()) {
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

        public abstract Object execute(Object value, EspressoType targetType) throws UnsupportedTypeException;

        protected static ToEspressoNode createToEspressoNode(EspressoType targetType) {
            return ToEspressoNode.createToEspresso(targetType, targetType.getRawType().getMeta());
        }

        @Specialization(guards = "targetType == cachedTargetType", limit = "LIMIT")
        public Object doCached(Object value, @SuppressWarnings("unused") EspressoType targetType,
                        @SuppressWarnings("unused") @Cached("targetType") EspressoType cachedTargetType,
                        @Cached("createToEspressoNode(cachedTargetType)") ToEspressoNode toEspressoNode) throws UnsupportedTypeException {
            return toEspressoNode.execute(value);
        }

        @Megamorphic
        @Specialization(replaces = "doCached")
        public Object doGeneric(Object value, EspressoType targetType,
                        @Cached ToEspressoNode.GenericToEspresso genericToEspresso) throws UnsupportedTypeException {
            return genericToEspresso.execute(value, targetType);
        }
    }

    @NodeInfo(shortName = "Generic toEspresso node")
    @GenerateUncached
    @ImportStatic(ToEspressoNode.class)
    public abstract static class GenericToEspresso extends EspressoNode {
        protected static final int LIMIT = 2;

        public abstract Object execute(Object value, EspressoType targetType) throws UnsupportedTypeException;

        public static boolean isStaticObject(Object value) {
            return value instanceof StaticObject;
        }

        @Specialization
        public static Object doStaticObject(StaticObject value, EspressoType targetType,
                        @Bind Node node,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Cached InlinedBranchProfile error) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), targetType.getRawType())) {

                return value; // pass through, NULL coercion not needed.
            }
            error.enter(node);
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to ", targetType.getRawType().getTypeName()));
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public static Object doForeignNull(Object value, @SuppressWarnings("unused") EspressoType targetType,
                        @Bind Node node,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached InlinedBranchProfile error) throws UnsupportedTypeException {
            if (targetType.getRawType().isPrimitive()) {
                error.enter(node);
                throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to ", targetType.getRawType().getTypeName()));
            }
            return StaticObject.createForeignNull(EspressoLanguage.get(node), value);
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "isTypeMappingEnabled(targetType)",
                        "!isStaticObject(value)"
        })
        public static Object doMappedInterface(Object value, EspressoType targetType,
                        @Bind Node node,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached ProxyInstantiateNode proxyInstantiateNode,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached InlinedBranchProfile error) throws UnsupportedTypeException {
            try {
                Object metaObject = interop.getMetaObject(value);
                WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, getMetaName(metaObject, interop), targetType.getRawType());

                if (proxyKlass != null) {
                    return proxyInstantiateNode.execute(proxyKlass, value, targetType);
                }
            } catch (UnsupportedMessageException e) {
                // no meta object, fall through to throw unsupported type
            }
            error.enter(node);
            throw unsupportedType(value, targetType.getRawType());
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public static Object doArray(Object value, ArrayKlass targetType,
                        @Bind Node node,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached InlinedBranchProfile error) throws UnsupportedTypeException {
            Meta meta = EspressoContext.get(node).getMeta();
            if (targetType == meta._byte_array) {
                if (interop.hasBufferElements(value) && !isHostString(value)) {
                    return StaticObject.createForeign(EspressoLanguage.get(node), meta._byte_array, value, interop);
                }
            }
            if (interop.hasArrayElements(value) && !isHostString(value)) {
                return StaticObject.createForeign(EspressoLanguage.get(node), targetType, value, interop);
            }
            error.enter(node);
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "isTypeConverterEnabled(targetType)",
                        "!isStaticObject(value)"
        })
        public static Object doTypeConverter(Object value, EspressoType targetType,
                        @Bind Node node,
                        @Cached LookupTypeConverterNode lookupTypeConverter,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached InlinedBranchProfile error) throws UnsupportedTypeException {
            try {
                Object metaObject = interop.getMetaObject(value);
                String metaName = getMetaName(metaObject, interop);

                // check if there's a specific type mapping available
                PolyglotTypeMappings.TypeConverter converter = lookupTypeConverter.execute(metaName);
                if (converter != null) {
                    StaticObject foreignWrapper = StaticObject.createForeign(EspressoLanguage.get(node), targetType.getRawType(), value, interop);
                    if (targetType instanceof ParameterizedEspressoType parameterizedEspressoType) {
                        EspressoLanguage.get(node).getTypeArgumentProperty().setObject(foreignWrapper, parameterizedEspressoType.getTypeArguments());
                    }
                    StaticObject result = (StaticObject) converter.convert(foreignWrapper);
                    if (instanceOf.execute(result.getKlass(), targetType.getRawType())) {
                        return result;
                    }
                }
            } catch (UnsupportedMessageException e) {
                // no meta object, fall through to throw unsupported type
            }
            error.enter(node);
            throw unsupportedType(value, targetType.getRawType());
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "isInternalTypeConverterEnabled(targetType)",
                        "!isStaticObject(value)"
        })
        public static Object doInternalTypeConverter(Object value, EspressoType targetType,
                        @Bind Node node,
                        @Cached ToReference.DynamicToReference converterToEspresso,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverter,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached InlinedBranchProfile errorProfile) throws UnsupportedTypeException {
            try {
                Object metaObject = interop.getMetaObject(value);
                String metaName = getMetaName(metaObject, interop);

                // check if there's a specific type mapping available
                PolyglotTypeMappings.InternalTypeConverter converter = lookupInternalTypeConverter.execute(metaName);
                if (converter != null) {
                    return converter.convertInternal(interop, value, EspressoContext.get(node).getMeta(), converterToEspresso, targetType);
                }
            } catch (UnsupportedMessageException e) {
                // no meta object, fall through to throw unsupported type
            }
            errorProfile.enter(node);
            throw unsupportedType(value, targetType.getRawType());
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "isBuiltInCollectionMapped(targetType)",
                        "!isStaticObject(value)"
        })
        public static Object doBuiltinCollectionMapped(Object value, EspressoType targetType,
                        @Bind Node node,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached ProxyInstantiateNode proxyInstantiateNode,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached InlinedBranchProfile converterProfile,
                        @Cached InlinedBranchProfile errorProfile) throws UnsupportedTypeException {
            try {
                Object metaObject = interop.getMetaObject(value);
                String metaName = getMetaName(metaObject, interop);
                // first check if there's a user-defined custom type converter defined
                PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
                if (converter != null) {
                    converterProfile.enter(node);
                    EspressoContext context = EspressoContext.get(node);
                    StaticObject foreignWrapper = StaticObject.createForeign(context.getLanguage(), context.getMeta().java_lang_Object, value, interop);
                    StaticObject result = (StaticObject) converter.convert(foreignWrapper);
                    if (instanceOf.execute(result.getKlass(), targetType.getRawType())) {
                        return result;
                    }
                }
                // then check if there's a type-mapped interface
                WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, metaName, targetType.getRawType());
                if (proxyKlass != null) {
                    return proxyInstantiateNode.execute(proxyKlass, value, targetType);
                }
            } catch (UnsupportedMessageException ex) {
                // no meta object, fall through to throw unsupported type
            }
            errorProfile.enter(node);
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getRawType().getTypeName());
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "!isStaticObject(value)",
                        "!isArray(targetType)",
                        "!isTypeConverterEnabled(targetType)",
                        "!isTypeMappingEnabled(targetType)",
                        "!isBuiltInCollectionMapped(targetType)",
                        "!isInternalTypeConverterEnabled(targetType)",
        })
        public static Object doGeneric(Object value, EspressoType targetType,
                        @Bind Node node,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached ToReference.DynamicToReference converterToEspresso,
                        @Cached InlinedBranchProfile unknownProfile) throws UnsupportedTypeException {
            Meta meta = EspressoContext.get(node).getMeta();
            ToEspressoNode uncachedToEspresso = getUncachedToEspresso(targetType, meta);
            if (uncachedToEspresso != null) {
                return uncachedToEspresso.execute(value);
            }
            unknownProfile.enter(node);
            // hit the unknown type case, so inline generic handling for that here
            StaticObject result = ToReference.tryConverterForUnknownTarget(value, targetType, interop, lookupTypeConverterNode, lookupInternalTypeConverterNode, converterToEspresso, meta);
            if (result != null) {
                return result;
            }
            if (targetType.getRawType() instanceof ObjectKlass rawType) {
                checkHasAllFieldsOrThrow(value, rawType, interop, meta);
                return StaticObject.createForeign(EspressoLanguage.get(node), rawType, value, interop);
            }
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getRawType().getTypeAsString());
        }
    }

    public static boolean isHostString(Object obj) {
        return obj instanceof String;
    }

    public static boolean isArray(EspressoType type) {
        return type.getRawType().isArray();
    }

    public static boolean isTypeMappingEnabled(EspressoType type) {
        return type.getRawType().getTypeConversionState() == Klass.INTERFACE_MAPPED;
    }

    public static boolean isTypeConverterEnabled(EspressoType type) {
        return type.getRawType().getTypeConversionState() == Klass.TYPE_MAPPED;
    }

    public static boolean isInternalTypeConverterEnabled(EspressoType type) {
        return type.getRawType().getTypeConversionState() == Klass.INTERNAL_MAPPED;
    }

    public static boolean isBuiltInCollectionMapped(EspressoType type) {
        return type.getRawType().getTypeConversionState() == Klass.INTERNAL_COLLECTION_MAPPED;
    }

    public static boolean isForeignException(EspressoType type, Meta meta) {
        return meta.polyglot != null /* polyglot enabled */ && meta.polyglot.ForeignException.equals(type.getRawType());
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

    @TruffleBoundary
    public static void checkHasAllFieldsOrThrow(Object value, ObjectKlass klass, InteropLibrary interopLibrary, Meta meta) throws UnsupportedTypeException {
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
                throw UnsupportedTypeException.create(new Object[]{value}, klass.getTypeAsString() + " due to missing field: " + f.getNameAsString());
            }
        }
        if (klass.getSuperClass() != null) {
            checkHasAllFieldsOrThrow(value, klass.getSuperKlass(), interopLibrary, meta);
        }
    }

    @TruffleBoundary
    static UnsupportedTypeException unsupportedType(Object value, Klass targetType) {
        return UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", targetType.getNameAsString()));
    }
}
