/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
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
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.ParameterizedEspressoType;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.bytecodes.InstanceOf;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.dispatch.staticobject.EspressoInterop;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@NodeInfo(shortName = "Convert to Espresso StaticObject")
@GenerateUncached
public abstract class ToReference extends ToEspressoNode {

    @Override
    public abstract StaticObject execute(Object value) throws UnsupportedTypeException;

    @TruffleBoundary
    public static ToReference createToReference(EspressoType targetType, Meta meta) {
        Klass rawType = targetType.getRawType();
        if (rawType == meta.java_lang_Void) {
            return ToReferenceFactory.ToVoidNodeGen.create();
        }
        if (rawType == meta.java_lang_Boolean) {
            return ToReferenceFactory.ToBooleanNodeGen.create();
        }
        if (rawType == meta.java_lang_Character) {
            return ToReferenceFactory.ToCharNodeGen.create();
        }
        if (rawType == meta.java_lang_Integer) {
            return ToReferenceFactory.ToIntegerNodeGen.create();
        }
        if (rawType == meta.java_lang_Byte) {
            return ToReferenceFactory.ToByteNodeGen.create();
        }
        if (rawType == meta.java_lang_Short) {
            return ToReferenceFactory.ToShortNodeGen.create();
        }
        if (rawType == meta.java_lang_Long) {
            return ToReferenceFactory.ToLongNodeGen.create();
        }
        if (rawType == meta.java_lang_Float) {
            return ToReferenceFactory.ToFloatNodeGen.create();
        }
        if (rawType == meta.java_lang_Double) {
            return ToReferenceFactory.ToDoubleNodeGen.create();
        }
        if (rawType == meta.java_lang_Number) {
            return ToReferenceFactory.ToNumberNodeGen.create();
        }
        if (rawType == meta._byte_array) {
            return ToReferenceFactory.ToByteArrayNodeGen.create();
        }
        if (rawType.isArray()) {
            return ToReferenceFactory.ToArrayNodeGen.create((ArrayKlass) targetType);
        }
        if (rawType.isJavaLangObject()) {
            return ToReferenceFactory.ToJavaLangObjectNodeGen.create();
        }
        if (rawType == meta.java_lang_String) {
            return ToReferenceFactory.ToStringNodeGen.create();
        }
        if (rawType.isInterface()) {
            if (isBuiltInCollectionMapped(targetType)) {
                if (rawType == meta.java_util_List) {
                    return ToReferenceFactory.ToListNodeGen.create(targetType);
                }
                if (rawType == meta.java_util_Set) {
                    return ToReferenceFactory.ToSetNodeGen.create(targetType);
                }
                if (rawType == meta.java_util_Collection) {
                    return ToReferenceFactory.ToCollectionNodeGen.create(targetType);
                }
                if (rawType == meta.java_lang_Iterable) {
                    return ToReferenceFactory.ToIterableNodeGen.create(targetType);
                }
                if (rawType == meta.java_util_Iterator) {
                    return ToReferenceFactory.ToIteratorNodeGen.create(targetType);
                }
                if (rawType == meta.java_util_Map) {
                    return ToReferenceFactory.ToMapNodeGen.create(targetType);
                }
            }
            if (rawType == meta.java_lang_CharSequence) {
                return ToReferenceFactory.ToCharSequenceNodeGen.create();
            }
            if (isTypeMappingEnabled(targetType)) {
                return ToReferenceFactory.ToMappedInterfaceNodeGen.create(targetType);
            } else {
                return ToReferenceFactory.ToUnknownNodeGen.create(targetType);
            }
        }
        if (isForeignException(targetType, meta)) {
            return ToReferenceFactory.ToForeignExceptionNodeGen.create();
        }
        if (rawType == meta.java_lang_Throwable) {
            return ToReferenceFactory.ToThrowableNodeGen.create();
        }
        if (rawType == meta.java_lang_Exception) {
            return ToReferenceFactory.ToExceptionNodeGen.create();
        }
        if (rawType == meta.java_lang_RuntimeException) {
            return ToReferenceFactory.ToRuntimeExceptionNodeGen.create();
        }
        if (rawType == meta.java_time_LocalDate) {
            return ToReferenceFactory.ToLocalDateNodeGen.create();
        }
        if (rawType == meta.java_time_LocalTime) {
            return ToReferenceFactory.ToLocalTimeNodeGen.create();
        }
        if (rawType == meta.java_time_LocalDateTime) {
            return ToReferenceFactory.ToLocalDateTimeNodeGen.create();
        }
        if (rawType == meta.java_time_ZonedDateTime) {
            return ToReferenceFactory.ToZonedDateTimeNodeGen.create();
        }
        if (rawType == meta.java_time_Instant) {
            return ToReferenceFactory.ToInstantNodeGen.create();
        }
        if (rawType == meta.java_time_Duration) {
            return ToReferenceFactory.ToDurationNodeGen.create();
        }
        if (rawType == meta.java_time_ZoneId) {
            return ToReferenceFactory.ToZoneIdNodeGen.create();
        }
        if (rawType == meta.java_util_Date) {
            return ToReferenceFactory.ToDateNodeGen.create();
        }
        if (rawType == meta.java_math_BigInteger) {
            return ToReferenceFactory.ToBigIntegerNodeGen.create();
        }
        if (isTypeConverterEnabled(targetType)) {
            return ToReferenceFactory.ToMappedTypeNodeGen.create(targetType);
        }
        if (isInternalTypeConverterEnabled(targetType)) {
            return ToReferenceFactory.ToMappedInternalTypeNodeGen.create(targetType);
        } else {
            return ToReferenceFactory.ToUnknownNodeGen.create(targetType);
        }
    }

    @TruffleBoundary
    public static ToReference getUncachedToReference(EspressoType targetType, Meta meta) {
        Klass rawType = targetType.getRawType();
        if (rawType == meta.java_lang_Void) {
            return ToReferenceFactory.ToVoidNodeGen.getUncached();
        }
        if (rawType == meta.java_lang_Boolean) {
            return ToReferenceFactory.ToBooleanNodeGen.getUncached();
        }
        if (rawType == meta.java_lang_Character) {
            return ToReferenceFactory.ToCharNodeGen.getUncached();
        }
        if (rawType == meta.java_lang_Integer) {
            return ToReferenceFactory.ToIntegerNodeGen.getUncached();
        }
        if (rawType == meta.java_lang_Byte) {
            return ToReferenceFactory.ToByteNodeGen.getUncached();
        }
        if (rawType == meta.java_lang_Short) {
            return ToReferenceFactory.ToShortNodeGen.getUncached();
        }
        if (rawType == meta.java_lang_Long) {
            return ToReferenceFactory.ToLongNodeGen.getUncached();
        }
        if (rawType == meta.java_lang_Float) {
            return ToReferenceFactory.ToFloatNodeGen.getUncached();
        }
        if (rawType == meta.java_lang_Double) {
            return ToReferenceFactory.ToDoubleNodeGen.getUncached();
        }
        if (rawType == meta.java_lang_Number) {
            return ToReferenceFactory.ToNumberNodeGen.getUncached();
        }
        if (rawType == meta._byte_array) {
            return ToReferenceFactory.ToByteArrayNodeGen.getUncached();
        }
        if (rawType.isArray()) {
            return null;
        }
        if (rawType.isJavaLangObject()) {
            return ToReferenceFactory.ToJavaLangObjectNodeGen.getUncached();
        }
        if (rawType == meta.java_lang_String) {
            return ToReferenceFactory.ToStringNodeGen.getUncached();
        }
        if (rawType.isInterface()) {
            if (rawType == meta.java_lang_CharSequence) {
                return ToReferenceFactory.ToCharSequenceNodeGen.getUncached();
            }
            // Interface type mappings & unknown interface types must be handled separately!
            return null;
        }
        if (isForeignException(targetType, meta)) {
            return ToReferenceFactory.ToForeignExceptionNodeGen.getUncached();
        }
        if (rawType == meta.java_lang_Throwable) {
            return ToReferenceFactory.ToThrowableNodeGen.getUncached();
        }
        if (rawType == meta.java_lang_Exception) {
            return ToReferenceFactory.ToExceptionNodeGen.getUncached();
        }
        if (rawType == meta.java_lang_RuntimeException) {
            return ToReferenceFactory.ToRuntimeExceptionNodeGen.getUncached();
        }
        if (rawType == meta.java_time_LocalDate) {
            return ToReferenceFactory.ToLocalDateNodeGen.getUncached();
        }
        if (rawType == meta.java_time_LocalTime) {
            return ToReferenceFactory.ToLocalTimeNodeGen.getUncached();
        }
        if (rawType == meta.java_time_LocalDateTime) {
            return ToReferenceFactory.ToLocalDateTimeNodeGen.getUncached();
        }
        if (rawType == meta.java_time_ZonedDateTime) {
            return ToReferenceFactory.ToZonedDateTimeNodeGen.getUncached();
        }
        if (rawType == meta.java_time_Instant) {
            return ToReferenceFactory.ToInstantNodeGen.getUncached();
        }
        if (rawType == meta.java_time_Duration) {
            return ToReferenceFactory.ToDurationNodeGen.getUncached();
        }
        if (rawType == meta.java_time_ZoneId) {
            return ToReferenceFactory.ToZoneIdNodeGen.getUncached();
        }
        if (rawType == meta.java_util_Date) {
            return ToReferenceFactory.ToDateNodeGen.getUncached();
        }
        if (rawType == meta.java_math_BigInteger) {
            return ToReferenceFactory.ToBigIntegerNodeGen.getUncached();
        }
        return null;
    }

    @NodeInfo(shortName = "Dynamic toEspresso node")
    @GenerateUncached
    public abstract static class DynamicToReference extends EspressoNode {
        protected static final int LIMIT = 4;

        public abstract StaticObject execute(Object value, EspressoType targetType) throws UnsupportedTypeException;

        protected static ToReference createToEspressoNode(EspressoType targetType) {
            return createToReference(targetType, targetType.getRawType().getMeta());
        }

        @Specialization(guards = "targetType == cachedTargetType", limit = "LIMIT")
        public StaticObject doCached(Object value, @SuppressWarnings("unused") EspressoType targetType,
                        @SuppressWarnings("unused") @Cached("targetType") EspressoType cachedTargetType,
                        @Cached("createToEspressoNode(cachedTargetType)") ToReference toEspressoNode) throws UnsupportedTypeException {
            return toEspressoNode.execute(value);
        }

        @ReportPolymorphism.Megamorphic
        @Specialization(replaces = "doCached")
        public StaticObject doGeneric(Object value, EspressoType targetType,
                        @Cached ToReference.GenericToReference genericToReference) throws UnsupportedTypeException {
            return genericToReference.execute(value, targetType);
        }
    }

    @NodeInfo(shortName = "Generic toEspresso node")
    @GenerateUncached
    @ImportStatic(ToEspressoNode.class)
    public abstract static class GenericToReference extends EspressoNode {
        protected static final int LIMIT = 2;

        public abstract StaticObject execute(Object value, EspressoType targetType) throws UnsupportedTypeException;

        @Specialization
        public StaticObject doStaticObject(StaticObject value, EspressoType targetType,
                        @Cached InstanceOf.Dynamic instanceOf) throws UnsupportedTypeException {
            Klass rawType = targetType.getRawType();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), rawType)) {
                return value;
            }
            try {
                Meta meta = getMeta();
                if (rawType == meta.java_lang_Double && EspressoInterop.fitsInDouble(value)) {
                    return meta.boxDouble(EspressoInterop.asDouble(value));
                }
                if (rawType == meta.java_lang_Float && EspressoInterop.fitsInFloat(value)) {
                    return meta.boxFloat(EspressoInterop.asFloat(value));
                }
                if (rawType == meta.java_lang_Long && EspressoInterop.fitsInLong(value)) {
                    return meta.boxLong(EspressoInterop.asLong(value));
                }
                if (rawType == meta.java_lang_Integer && EspressoInterop.fitsInInt(value)) {
                    return meta.boxInteger(EspressoInterop.asInt(value));
                }
                if (rawType == meta.java_lang_Short && EspressoInterop.fitsInShort(value)) {
                    return meta.boxShort(EspressoInterop.asShort(value));
                }
                if (rawType == meta.java_lang_Byte && EspressoInterop.fitsInByte(value)) {
                    return meta.boxByte(EspressoInterop.asByte(value));
                }
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if fitsIn* returns true, as* must succeed.");
            }
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to ", targetType.getRawType().getTypeAsString()));
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public StaticObject doForeignNull(Object value, @SuppressWarnings("unused") EspressoType targetType,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "isTypeMappingEnabled(targetType)",
                        "!isStaticObject(value)"
        })
        public static StaticObject doMappedInterface(Object value, EspressoType targetType,
                        @Bind Node node,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached ProxyInstantiateNode proxyInstantiateNode,
                        @CachedLibrary(limit = "LIMIT") @Exclusive InteropLibrary interop,
                        @Cached @Shared InlinedBranchProfile errorProfile) throws UnsupportedTypeException {
            try {
                Object metaObject = interop.getMetaObject(value);
                WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, getMetaName(metaObject, interop), targetType.getRawType());
                if (proxyKlass != null) {
                    return proxyInstantiateNode.execute(proxyKlass, value, targetType);
                }
            } catch (UnsupportedMessageException e) {
                // no meta object, fall through to throw unsupported type
            }
            errorProfile.enter(node);
            throw ToEspressoNode.unsupportedType(value, targetType.getRawType());
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public StaticObject doArray(Object value, @SuppressWarnings("unused") ArrayKlass targetType,
                        @CachedLibrary(limit = "LIMIT") @Exclusive InteropLibrary interop) throws UnsupportedTypeException {
            if (targetType == getMeta()._byte_array) {
                if (interop.hasBufferElements(value) && !isHostString(value)) {
                    return StaticObject.createForeign(EspressoLanguage.get(this), getMeta()._byte_array, value, interop);
                }
            }
            if (interop.hasArrayElements(value) && !isHostString(value)) {
                return StaticObject.createForeign(EspressoLanguage.get(this), targetType, value, interop);
            }
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "isTypeConverterEnabled(targetType)",
                        "!isStaticObject(value)"
        })
        public static StaticObject doTypeConverter(Object value, EspressoType targetType,
                        @Bind Node node,
                        @Cached @Exclusive LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached @Shared InlinedBranchProfile errorProfile,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @CachedLibrary(limit = "LIMIT") @Exclusive InteropLibrary interop) throws UnsupportedTypeException {
            try {
                Object metaObject = interop.getMetaObject(value);
                String metaName = getMetaName(metaObject, interop);

                // check if there's a specific type mapping available
                PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
                if (converter != null) {
                    StaticObject foreign;
                    if (interop.isException(value)) {
                        foreign = StaticObject.createForeignException(EspressoContext.get(node), value, interop);
                    } else {
                        foreign = StaticObject.createForeign(EspressoLanguage.get(node), targetType.getRawType(), value, interop);
                    }
                    if (targetType instanceof ParameterizedEspressoType parameterizedEspressoType) {
                        EspressoLanguage.get(node).getTypeArgumentProperty().setObject(foreign, parameterizedEspressoType.getTypeArguments());
                    }
                    StaticObject result = (StaticObject) converter.convert(foreign);
                    if (instanceOf.execute(result.getKlass(), targetType.getRawType())) {
                        return result;
                    }
                }
            } catch (UnsupportedMessageException e) {
                // no meta object, fall through to throw unsupported type
            }
            errorProfile.enter(node);
            throw ToEspressoNode.unsupportedType(value, targetType.getRawType());
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "isInternalTypeConverterEnabled(targetType)",
                        "!isStaticObject(value)"
        })
        public static StaticObject doInternalTypeConverter(Object value, EspressoType targetType,
                        @Bind Node node,
                        @Cached @Exclusive LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached @Exclusive ToReference.DynamicToReference converterToEspresso,
                        @Cached @Shared InlinedBranchProfile errorProfile,
                        @CachedLibrary(limit = "LIMIT") @Exclusive InteropLibrary interop) throws UnsupportedTypeException {
            try {
                Object metaObject = interop.getMetaObject(value);
                String metaName = getMetaName(metaObject, interop);

                // check if there's a specific type mapping available
                PolyglotTypeMappings.InternalTypeConverter converter = lookupInternalTypeConverterNode.execute(metaName);
                if (converter != null) {
                    return converter.convertInternal(interop, value, EspressoContext.get(node).getMeta(), converterToEspresso, targetType);
                }
            } catch (UnsupportedMessageException e) {
                // no meta object, fall through to throw unsupported type
            }
            errorProfile.enter(node);
            throw ToEspressoNode.unsupportedType(value, targetType.getRawType());
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "isBuiltInCollectionMapped(targetType)",
                        "!isStaticObject(value)"
        })
        public static StaticObject doBuiltinTypeConverter(Object value, EspressoType targetType,
                        @Bind Node node,
                        @Cached @Exclusive LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached @Exclusive LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached @Exclusive ProxyInstantiateNode proxyInstantiatorNode,
                        @CachedLibrary(limit = "LIMIT") @Exclusive InteropLibrary interop,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Cached InlinedBranchProfile noConverterProfile,
                        @Cached InlinedBranchProfile errorProfile) throws UnsupportedTypeException {
            try {
                Object metaObject = interop.getMetaObject(value);
                String metaName = getMetaName(metaObject, interop);
                Klass rawType = targetType.getRawType();
                // first check if there's a user-defined custom type converter defined
                PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
                if (converter != null) {
                    noConverterProfile.enter(node);
                    EspressoContext context = EspressoContext.get(node);
                    StaticObject foreignWrapper = StaticObject.createForeign(context.getLanguage(), context.getMeta().java_lang_Object, value, interop);
                    StaticObject result = (StaticObject) converter.convert(foreignWrapper);
                    if (instanceOf.execute(result.getKlass(), targetType.getRawType())) {
                        return result;
                    }
                }
                // then check if there's a type-mapped interface
                WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, metaName, rawType);
                if (proxyKlass != null) {
                    return proxyInstantiatorNode.execute(proxyKlass, value, targetType);
                }
            } catch (UnsupportedMessageException e) {
                // no meta object, fall through to throw unsupported type
            }
            errorProfile.enter(node);
            throw ToEspressoNode.unsupportedType(value, targetType.getRawType());
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "!isStaticObject(value)",
                        "!isArray(targetType)",
                        "!isTypeConverterEnabled(targetType)",
                        "!isInternalTypeConverterEnabled(targetType)",
                        "!isBuiltInCollectionMapped(targetType)",
                        "!isTypeMappingEnabled(targetType)"
        })
        public static StaticObject doGeneric(Object value, EspressoType targetType,
                        @Bind Node node,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached ToReference.DynamicToReference converterToEspresso,
                        @Cached InlinedBranchProfile unknownProfile,
                        @Cached InlinedBranchProfile noConverterProfile) throws UnsupportedTypeException {
            Meta meta = EspressoContext.get(node).getMeta();

            ToReference uncachedToReference = getUncachedToReference(targetType, meta);
            if (uncachedToReference != null) {
                return uncachedToReference.execute(value);
            }
            unknownProfile.enter(node);
            // hit the unknown type case, so inline generic handling for that here
            StaticObject result = tryConverterForUnknownTarget(value, targetType, interop, lookupTypeConverterNode, lookupInternalTypeConverterNode, converterToEspresso, meta);
            if (result != null) {
                return result;
            }
            // no generic conversion to abstract target types allowed
            if (targetType.getRawType().isAbstract()) {
                throw UnsupportedTypeException.create(new Object[]{value}, targetType.getRawType().getTypeAsString());
            }
            if (targetType instanceof ObjectKlass rawType) {
                noConverterProfile.enter(node);
                checkHasAllFieldsOrThrow(value, rawType, interop, meta);
                return StaticObject.createForeign(EspressoLanguage.get(node), rawType, value, interop);
            }
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getRawType().getTypeAsString());
        }
    }

    @NodeInfo(shortName = "List type mapping")
    public abstract static class ToList extends ToReference {
        protected static final int LIMIT = 4;

        final EspressoType targetType;

        ToList(EspressoType targetType) {
            this.targetType = targetType;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public StaticObject doForeignNull(Object value,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(language, value);
        }

        @Specialization(guards = {
                        "interop.hasArrayElements(value)",
                        "interop.hasMetaObject(value)",
                        "!isStaticObject(value)"
        })
        @SuppressWarnings("truffle-static-method")
        public StaticObject doMappedList(Object value,
                        @Bind Node node,
                        @Bind("getContext()") EspressoContext context,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached ProxyInstantiateNode proxyInstantiateNode,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @CachedLibrary(limit = "LIMIT") @Exclusive InteropLibrary interop,
                        @Cached InlinedBranchProfile converterProfile,
                        @Cached InlinedBranchProfile errorProfile) throws UnsupportedTypeException {
            try {
                Object metaObject = interop.getMetaObject(value);
                String metaName = getMetaName(metaObject, interop);

                // first check if there's a user-defined custom type converter defined
                PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
                if (converter != null) {
                    converterProfile.enter(node);
                    StaticObject foreignWrapper = StaticObject.createForeign(context.getLanguage(), context.getMeta().java_lang_Object, value, interop);
                    StaticObject result = (StaticObject) converter.convert(foreignWrapper);
                    if (instanceOf.execute(result.getKlass(), context.getMeta().java_util_List)) {
                        return result;
                    }
                }
                // then check if there's a type-mapped interface
                WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, metaName, context.getMeta().java_util_List);
                if (proxyKlass != null) {
                    return proxyInstantiateNode.execute(proxyKlass, value, targetType);
                }
            } catch (UnsupportedMessageException ex) {
                // no meta object, fall through to throw unsupported type
            }
            errorProfile.enter(node);
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_List.getTypeAsString());
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached("createInstanceOf(targetType.getRawType())") InstanceOf instanceOf) throws UnsupportedTypeException {
            if (!instanceOf.execute(value.getKlass())) {
                throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_List.getTypeAsString());
            }
            return value;
        }

        @Fallback
        public StaticObject unsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_List.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "Collection type mapping")
    public abstract static class ToCollection extends ToReference {
        protected static final int LIMIT = 4;

        final EspressoType targetType;

        ToCollection(EspressoType targetType) {
            this.targetType = targetType;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public StaticObject doForeignNull(Object value,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(language, value);
        }

        @Specialization(guards = {
                        "interop.hasIterator(value)",
                        "interop.hasMetaObject(value)",
                        "isHostObject(getContext(), value)",
                        "!isStaticObject(value)"
        })
        @SuppressWarnings("truffle-static-method")
        public StaticObject doMappedCollection(Object value,
                        @Bind Node node,
                        @Bind("getMeta()") Meta meta,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached ProxyInstantiateNode proxyInstantiateNode,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @CachedLibrary(limit = "LIMIT") @Exclusive InteropLibrary interop,
                        @Cached InlinedBranchProfile converterProfile,
                        @Cached InlinedBranchProfile errorProfile) throws UnsupportedTypeException {
            try {
                Object metaObject = interop.getMetaObject(value);
                String metaName = getMetaName(metaObject, interop);

                // first check if there's a user-defined custom type converter defined
                PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
                if (converter != null) {
                    converterProfile.enter(node);
                    StaticObject foreignWrapper = StaticObject.createForeign(meta.getLanguage(), meta.java_lang_Object, value, interop);
                    StaticObject result = (StaticObject) converter.convert(foreignWrapper);
                    if (instanceOf.execute(result.getKlass(), meta.java_util_Collection)) {
                        return result;
                    }
                }
                // then check if there's a type-mapped interface
                WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, metaName, meta.java_util_Collection);
                if (proxyKlass != null) {
                    return proxyInstantiateNode.execute(proxyKlass, value, targetType);
                }
            } catch (UnsupportedMessageException ex) {
                // no meta object, fall through to throw unsupported type
            }
            errorProfile.enter(node);
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Collection.getTypeAsString());
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached("createInstanceOf(targetType.getRawType())") InstanceOf instanceOf) throws UnsupportedTypeException {
            if (!instanceOf.execute(value.getKlass())) {
                throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Collection.getTypeAsString());
            }
            return value;
        }

        @Fallback
        public StaticObject unsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Collection.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "Iterable type mapping")
    public abstract static class ToIterable extends ToReference {
        protected static final int LIMIT = 4;

        final EspressoType targetType;

        ToIterable(EspressoType targetType) {
            this.targetType = targetType;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public StaticObject doForeignNull(Object value,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(language, value);
        }

        @Specialization(guards = {
                        "interop.hasIterator(value)",
                        "interop.hasMetaObject(value)",
                        "isHostObject(getContext(), value)",
                        "!isStaticObject(value)"
        })
        @SuppressWarnings("truffle-static-method")
        public StaticObject doMappedIterable(Object value,
                        @Bind Node node,
                        @Bind("getMeta()") Meta meta,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached ProxyInstantiateNode proxyInstantiateNode,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @CachedLibrary(limit = "LIMIT") @Exclusive InteropLibrary interop,
                        @Cached InlinedBranchProfile converterProfile,
                        @Cached InlinedBranchProfile errorProfile) throws UnsupportedTypeException {
            try {
                Object metaObject = interop.getMetaObject(value);
                String metaName = getMetaName(metaObject, interop);

                // first check if there's a user-defined custom type converter defined
                PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
                if (converter != null) {
                    converterProfile.enter(node);
                    StaticObject foreignWrapper = StaticObject.createForeign(meta.getLanguage(), meta.java_lang_Object, value, interop);
                    StaticObject result = (StaticObject) converter.convert(foreignWrapper);
                    if (instanceOf.execute(result.getKlass(), meta.java_lang_Iterable)) {
                        return result;
                    }
                }
                // then check if there's a type-mapped interface
                WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, metaName, meta.java_lang_Iterable);
                if (proxyKlass != null) {
                    return proxyInstantiateNode.execute(proxyKlass, value, targetType);
                }
            } catch (UnsupportedMessageException ex) {
                // no meta object, fall through to throw unsupported type
            }
            errorProfile.enter(node);
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_Iterable.getTypeAsString());
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached("createInstanceOf(targetType.getRawType())") InstanceOf instanceOf) throws UnsupportedTypeException {
            if (!instanceOf.execute(value.getKlass())) {
                throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_Iterable.getTypeAsString());
            }
            return value;
        }

        @Fallback
        public StaticObject unsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_Iterable.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "Iterator type mapping")
    public abstract static class ToIterator extends ToReference {
        protected static final int LIMIT = 4;

        final EspressoType targetType;

        ToIterator(EspressoType targetType) {
            this.targetType = targetType;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public StaticObject doForeignNull(Object value,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(language, value);
        }

        @Specialization(guards = {
                        "interop.isIterator(value)",
                        "interop.hasMetaObject(value)",
                        "isHostObject(getContext(), value)",
                        "!isStaticObject(value)"
        })
        @SuppressWarnings("truffle-static-method")
        public StaticObject doMappedIterator(Object value,
                        @Bind Node node,
                        @Bind("getMeta()") Meta meta,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached ProxyInstantiateNode proxyInstantiateNode,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @CachedLibrary(limit = "LIMIT") @Exclusive InteropLibrary interop,
                        @Cached InlinedBranchProfile converterProfile,
                        @Cached InlinedBranchProfile errorProfile) throws UnsupportedTypeException {
            try {
                Object metaObject = interop.getMetaObject(value);
                String metaName = getMetaName(metaObject, interop);

                // first check if there's a user-defined custom type converter defined
                PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
                if (converter != null) {
                    converterProfile.enter(node);
                    StaticObject foreignWrapper = StaticObject.createForeign(meta.getLanguage(), meta.java_lang_Object, value, interop);
                    StaticObject result = (StaticObject) converter.convert(foreignWrapper);
                    if (instanceOf.execute(result.getKlass(), meta.java_util_Iterator)) {
                        return result;
                    }
                }
                // then check if there's a type-mapped interface
                WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, metaName, meta.java_util_Iterator);
                if (proxyKlass != null) {
                    return proxyInstantiateNode.execute(proxyKlass, value, targetType);
                }
            } catch (UnsupportedMessageException ex) {
                // no meta object, fall through to throw unsupported type
            }
            errorProfile.enter(node);
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Iterator.getTypeAsString());
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached("createInstanceOf(targetType.getRawType())") InstanceOf instanceOf) throws UnsupportedTypeException {
            if (!instanceOf.execute(value.getKlass())) {
                throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Iterator.getTypeAsString());
            }
            return value;
        }

        @Fallback
        public StaticObject unsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Iterator.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "Map type mapping")
    public abstract static class ToMap extends ToReference {
        protected static final int LIMIT = 4;

        final EspressoType targetType;

        ToMap(EspressoType targetType) {
            this.targetType = targetType;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public StaticObject doForeignNull(Object value,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(language, value);
        }

        @Specialization(guards = {
                        "interop.hasHashEntries(value)",
                        "interop.hasMetaObject(value)",
                        "isHostObject(getContext(), value)",
                        "!isStaticObject(value)"
        })
        @SuppressWarnings("truffle-static-method")
        public StaticObject doMappedMap(Object value,
                        @Bind Node node,
                        @Bind("getMeta()") Meta meta,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached ProxyInstantiateNode proxyInstantiateNode,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @CachedLibrary(limit = "LIMIT") @Exclusive InteropLibrary interop,
                        @Cached InlinedBranchProfile converterProfile,
                        @Cached InlinedBranchProfile errorProfile) throws UnsupportedTypeException {
            try {
                Object metaObject = interop.getMetaObject(value);
                String metaName = getMetaName(metaObject, interop);
                // first check if there's a user-defined custom type converter defined
                PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
                if (converter != null) {
                    converterProfile.enter(node);
                    StaticObject foreignWrapper = StaticObject.createForeign(meta.getLanguage(), meta.java_lang_Object, value, interop);
                    StaticObject result = (StaticObject) converter.convert(foreignWrapper);
                    if (instanceOf.execute(result.getKlass(), meta.java_util_Map)) {
                        return result;
                    }
                }
                // then check if there's a type-mapped interface
                WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, metaName, meta.java_util_Map);
                if (proxyKlass != null) {
                    return proxyInstantiateNode.execute(proxyKlass, value, targetType);
                }
            } catch (UnsupportedMessageException ex) {
                // no meta object, fall through to throw unsupported type
            }
            errorProfile.enter(node);
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Map.getTypeAsString());
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached("createInstanceOf(targetType.getRawType())") InstanceOf instanceOf) throws UnsupportedTypeException {
            if (!instanceOf.execute(value.getKlass())) {
                throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Map.getTypeAsString());
            }
            return value;
        }

        @Fallback
        public StaticObject unsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Map.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "Set type mapping")
    public abstract static class ToSet extends ToReference {
        protected static final int LIMIT = 4;

        final EspressoType targetType;

        ToSet(EspressoType targetType) {
            this.targetType = targetType;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public StaticObject doForeignNull(Object value,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(language, value);
        }

        @Specialization(guards = {
                        "interop.hasMetaObject(value)",
                        "isHostObject(getContext(), value)",
                        "!isStaticObject(value)"
        })
        @SuppressWarnings("truffle-static-method")
        public StaticObject doMappedSet(Object value,
                        @Bind Node node,
                        @Bind("getMeta()") Meta meta,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached ProxyInstantiateNode proxyInstantiateNode,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @CachedLibrary(limit = "LIMIT") @Exclusive InteropLibrary interop,
                        @Cached InlinedBranchProfile converterProfile,
                        @Cached InlinedBranchProfile errorProfile) throws UnsupportedTypeException {
            try {
                Object metaObject = interop.getMetaObject(value);
                String metaName = getMetaName(metaObject, interop);

                // first check if there's a user-defined custom type converter defined
                PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
                if (converter != null) {
                    converterProfile.enter(node);
                    StaticObject foreignWrapper = StaticObject.createForeign(meta.getLanguage(), meta.java_lang_Object, value, interop);
                    StaticObject result = (StaticObject) converter.convert(foreignWrapper);
                    if (instanceOf.execute(result.getKlass(), meta.java_util_Set)) {
                        return result;
                    }
                }
                // then check if there's a type-mapped interface
                WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, metaName, meta.java_util_Set);
                if (proxyKlass != null) {
                    return proxyInstantiateNode.execute(proxyKlass, value, targetType);
                }
            } catch (UnsupportedMessageException ex) {
                // no meta object, fall through to throw unsupported type
            }
            errorProfile.enter(node);
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Set.getTypeAsString());
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached("createInstanceOf(targetType.getRawType())") InstanceOf instanceOf) throws UnsupportedTypeException {
            if (!instanceOf.execute(value.getKlass())) {
                throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Set.getTypeAsString());
            }
            return value;
        }

        @Fallback
        public StaticObject unsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Set.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "void target type")
    @GenerateUncached
    abstract static class ToVoid extends ToReference {

        @Specialization
        public StaticObject doVoid(@SuppressWarnings("unused") Object value) {
            return StaticObject.NULL;
        }
    }

    @NodeInfo(shortName = "integer target type")
    @GenerateUncached
    abstract static class ToInteger extends ToReference {
        protected static final int LIMIT = 2;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doInteger(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ToPrimitive.ToInt toInt,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxInteger((int) toInt.execute(value));
        }
    }

    @NodeInfo(shortName = "boolean target type")
    @GenerateUncached
    abstract static class ToBoolean extends ToReference {
        protected static final int LIMIT = 2;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doBoolean(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ToPrimitive.ToBoolean toBoolean,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxBoolean((boolean) toBoolean.execute(value));
        }
    }

    @NodeInfo(shortName = "byte target type")
    @GenerateUncached
    abstract static class ToByte extends ToReference {
        protected static final int LIMIT = 2;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doByte(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ToPrimitive.ToByte toByte,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxByte((byte) toByte.execute(value));
        }
    }

    @GenerateUncached
    abstract static class ToShort extends ToReference {
        protected static final int LIMIT = 2;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doShort(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ToPrimitive.ToShort toShort,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxShort((short) toShort.execute(value));
        }
    }

    @NodeInfo(shortName = "char target type")
    @GenerateUncached
    abstract static class ToChar extends ToReference {
        protected static final int LIMIT = 2;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doChar(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ToPrimitive.ToChar toChar,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxCharacter((char) toChar.execute(value));
        }
    }

    @NodeInfo(shortName = "long target type")
    @GenerateUncached
    abstract static class ToLong extends ToReference {
        protected static final int LIMIT = 2;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doLong(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ToPrimitive.ToLong toLong,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxLong((long) toLong.execute(value));
        }
    }

    @NodeInfo(shortName = "float target type")
    @GenerateUncached
    abstract static class ToFloat extends ToReference {
        protected static final int LIMIT = 2;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doFloat(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ToPrimitive.ToFloat toFloat,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxFloat((float) toFloat.execute(value));
        }
    }

    @NodeInfo(shortName = "double target type")
    @GenerateUncached
    abstract static class ToDouble extends ToReference {
        protected static final int LIMIT = 2;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doDouble(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ToPrimitive.ToDouble toDouble,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxDouble((double) toDouble.execute(value));
        }
    }

    @NodeInfo(shortName = "j.l.Object target type")
    @GenerateUncached
    @ImportStatic(EspressoContext.class)
    public abstract static class ToJavaLangObject extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization
        public StaticObject doEspresso(StaticObject value) {
            return value;
        }

        @Specialization
        StaticObject doEspressoException(EspressoException value) {
            return value.getGuestException();
        }

        @Specialization
        StaticObject doHostString(String value, @Bind("getMeta()") Meta meta) {
            return meta.toGuestString(value);
        }

        @Specialization
        StaticObject doHostInteger(Integer value) {
            return getMeta().boxInteger(value);
        }

        @Specialization
        StaticObject doHostBoolean(Boolean value) {
            return getMeta().boxBoolean(value);
        }

        @Specialization
        StaticObject doHostByte(Byte value) {
            return getMeta().boxByte(value);
        }

        @Specialization
        StaticObject doHostChar(Character value) {
            return getMeta().boxCharacter(value);
        }

        @Specialization
        StaticObject doHostShort(Short value) {
            return getMeta().boxShort(value);
        }

        @Specialization
        StaticObject doHostLong(Long value) {
            return getMeta().boxLong(value);
        }

        @Specialization
        StaticObject doHostFloat(Float value) {
            return getMeta().boxFloat(value);
        }

        @Specialization
        StaticObject doHostDouble(Double value) {
            return getMeta().boxDouble(value);
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = {
                        "interop.isBoolean(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignBoolean(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            try {
                return getMeta().boxBoolean(interop.asBoolean(value));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isBoolean returns true, asBoolean must succeed.");
            }
        }

        @Specialization(guards = {
                        "interop.isNumber(value)",
                        "!isStaticObject(value)",
                        "!isHostNumber(value)"
        })
        StaticObject doForeignNumber(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getContext()") EspressoContext context) throws UnsupportedTypeException {
            if (interop.fitsInBigInteger(value) || interop.fitsInDouble(value)) {
                return StaticObject.createForeign(getLanguage(), context.getMeta().polyglot.EspressoForeignNumber, value, interop);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnsupportedTypeException.create(new Object[]{value}, "unsupported number");
        }

        @Specialization(guards = {
                        "interop.isString(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignString(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                String hostString = interop.asString(value);
                return meta.toGuestString(hostString);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isString returns true, asString must succeed.");
            }
        }

        @Specialization(guards = {
                        "interop.isException(value)",
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)",
                        "!isEspressoException(value)",
                        "!isBoxedPrimitive(value)"
        })
        static StaticObject doForeignException(Object value,
                        @Bind Node node,
                        @Bind("get(node)") EspressoContext context,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached ProxyInstantiateNode proxyInstantiateNode,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached ToReference.DynamicToReference converterToEspresso,
                        @Cached InlinedBranchProfile errorProfile) {
            // This is a workaround for a DSL issue where the node cannot be used in a guard, due to
            // the generated code for the fallback guard trying to reference it when it's not in
            // scope.
            // TODO - GR-59087. Re-introduce two specializations instead of the below if-else.
            if (isTypeMappingEnabled(context)) {
                try {
                    return tryTypeConversion(value, interop, lookupProxyKlassNode, proxyInstantiateNode, lookupTypeConverterNode, lookupInternalTypeConverterNode, converterToEspresso,
                                    context.getMeta());
                } catch (@SuppressWarnings("unused") UnsupportedTypeException ex) {
                    // we know it's a foreign exception so fall through
                    errorProfile.enter(node);
                }
            }
            return StaticObject.createForeignException(context, value, interop);
        }

        @Specialization(guards = {
                        "interop.hasArrayElements(value)",
                        "!isTypeMappingEnabled(context)",
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)"
        })
        StaticObject doForeignArray(Object value,
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeign(EspressoLanguage.get(this), getMeta().java_lang_Object, value, interop);
        }

        @Specialization(guards = {
                        "interop.hasArrayElements(value)",
                        "isTypeMappingEnabled(context)",
                        "!interop.hasMetaObject(value)",
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)"
        })
        StaticObject doForeignArrayNoMetaObject(Object value,
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            return StaticObject.createForeign(getLanguage(), meta.java_lang_Object, value, interop);
        }

        @Specialization(guards = {
                        "interop.hasArrayElements(value)",
                        "isTypeMappingEnabled(context)",
                        "interop.hasMetaObject(value)",
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)"
        })
        StaticObject doForeignArrayTypeMapping(Object value,
                        @Bind("getContext()") EspressoContext context,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached ProxyInstantiateNode proxyInstantiateNode,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached ToReference.DynamicToReference converterToEspresso) throws UnsupportedTypeException {
            return tryTypeConversion(value, interop, lookupProxyKlassNode, proxyInstantiateNode, lookupTypeConverterNode, lookupInternalTypeConverterNode, converterToEspresso, context.getMeta());
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "interop.hasBufferElements(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)"
        })
        StaticObject doForeignBuffer(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @SuppressWarnings("unused") @Bind("getMeta()") Meta meta) {
            return StaticObject.createForeign(EspressoLanguage.get(this), meta._byte_array, value, interop);
        }

        @Specialization(guards = {
                        "!isTypeMappingEnabled(context)",
                        "interop.hasMetaObject(value)",
                        "!interop.hasArrayElements(value)",
                        "!interop.hasBufferElements(value)",
                        "!interop.isException(value)",
                        "!interop.isNumber(value)",
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)",
                        "!isEspressoException(value)",
                        "!isBoxedPrimitive(value)"
        })
        StaticObject doJavaLangObjectForeignWrapper(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context,
                        @Bind("getMeta()") Meta meta) {
            return StaticObject.createForeign(getLanguage(), meta.java_lang_Object, value, interop);
        }

        @Specialization(guards = {
                        "isTypeMappingEnabled(context)",
                        "interop.hasMetaObject(value)",
                        "!interop.isNumber(value)",
                        "!interop.isException(value)",
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)",
                        "!isEspressoException(value)",
                        "!isBoxedPrimitive(value)",
        })
        StaticObject doForeignTypeMapping(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached ProxyInstantiateNode proxyInstantiateNode,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached ToReference.DynamicToReference converterToEspresso,
                        @Bind("getContext()") EspressoContext context) throws UnsupportedTypeException {
            return tryTypeConversion(value, interop, lookupProxyKlassNode, proxyInstantiateNode, lookupTypeConverterNode, lookupInternalTypeConverterNode, converterToEspresso, context.getMeta());
        }

        @Specialization(guards = {
                        "!interop.hasMetaObject(value)",
                        "!interop.hasArrayElements(value)",
                        "!interop.isBoolean(value)",
                        "!interop.isNumber(value)",
                        "!interop.isString(value)",
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)"
        })
        StaticObject doForeignObject(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeign(EspressoLanguage.get(this), getMeta().java_lang_Object, value, interop);
        }

        @Fallback
        StaticObject doUnsupportedType(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, "java.lang.Object");
        }
    }

    private static Object getMetaObjectOrThrow(Object value, InteropLibrary interop, Klass targetType) throws UnsupportedTypeException {
        try {
            return interop.getMetaObject(value);
        } catch (UnsupportedMessageException e) {
            // translate the exception to unsupported type, to avoid try/catch blocks in
            // ToReference/ToEspresso caller code
            throw unsupportedType(value, targetType);
        }
    }

    private static Object getMetaObjectOrNull(Object value, InteropLibrary interop) {
        try {
            return interop.getMetaObject(value);
        } catch (UnsupportedMessageException e) {
            return null;
        }
    }

    private static StaticObject tryTypeConversion(Object value, InteropLibrary interop, LookupProxyKlassNode lookupProxyKlassNode, ProxyInstantiateNode proxyInstantiateNode,
                    LookupTypeConverterNode lookupTypeConverterNode,
                    LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                    ToReference.DynamicToReference converterToEspresso, Meta meta) throws UnsupportedTypeException {
        Object metaObject = getMetaObjectOrThrow(value, interop, meta.java_lang_Object);
        String metaName = getMetaName(metaObject, interop);

        // check if there's a specific type mapping available
        PolyglotTypeMappings.InternalTypeConverter internalConverter = lookupInternalTypeConverterNode.execute(metaName);
        if (internalConverter != null) {
            return internalConverter.convertInternal(interop, value, meta, converterToEspresso, meta.java_lang_Object);
        } else {
            PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
            if (converter != null) {
                if (interop.isException(value)) {
                    return (StaticObject) converter.convert(StaticObject.createForeignException(converterToEspresso.getContext(), value, interop));
                } else {
                    return (StaticObject) converter.convert(StaticObject.createForeign(converterToEspresso.getLanguage(), meta.java_lang_Object, value, interop));
                }
            }

            // check if foreign exception
            if (interop.isException(value)) {
                return StaticObject.createForeignException(converterToEspresso.getContext(), value, interop);
            }
            // see if a generated proxy can be used for interface mapped types
            WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, metaName, meta.java_lang_Object);
            if (proxyKlass != null) {
                return proxyInstantiateNode.execute(proxyKlass, value, meta.java_lang_Object);
            }
            return StaticObject.createForeign(converterToEspresso.getLanguage(), meta.java_lang_Object, value, interop);
        }
    }

    static StaticObject tryConverterForUnknownTarget(Object value, EspressoType targetType, InteropLibrary interop, LookupTypeConverterNode lookupTypeConverterNode,
                    LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                    ToReference.DynamicToReference converterToEspresso, Meta meta) throws UnsupportedTypeException {
        Object metaObject = getMetaObjectOrNull(value, interop);
        if (metaObject == null) {
            return null;
        }
        String metaName = getMetaName(metaObject, interop);

        // check if there's a specific type mapping available
        PolyglotTypeMappings.InternalTypeConverter internalConverter = lookupInternalTypeConverterNode.execute(metaName);
        if (internalConverter != null) {
            return internalConverter.convertInternal(interop, value, meta, converterToEspresso, meta.java_lang_Object);
        } else {
            PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
            // check if foreign exception
            boolean isException = interop.isException(value);
            StaticObject foreignWrapper = isException ? StaticObject.createForeignException(converterToEspresso.getContext(), value, interop) : null;

            if (converter != null) {
                if (foreignWrapper == null) {
                    // not exception
                    foreignWrapper = StaticObject.createForeign(converterToEspresso.getLanguage(), meta.java_lang_Object, value, interop);
                }
                if (targetType instanceof ParameterizedEspressoType parameterizedEspressoType) {
                    meta.getLanguage().getTypeArgumentProperty().setObject(foreignWrapper, parameterizedEspressoType.getTypeArguments());
                }
                StaticObject result = (StaticObject) converter.convert(foreignWrapper);
                if (!targetType.getRawType().isAssignableFrom(result.getKlass())) {
                    return null;
                }
                return result;
            }
            return foreignWrapper;
        }
    }

    @NodeInfo(shortName = "j.l.String target type")
    @GenerateUncached
    public abstract static class ToString extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        StaticObject doHostString(String value, @Bind("getMeta()") Meta meta) {
            return meta.toGuestString(value);
        }

        @Specialization(guards = "isEspressoString(value, meta)")
        StaticObject doEspressoString(StaticObject value, @Bind("getMeta()") @SuppressWarnings("unused") Meta meta) {
            return value;
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!isHostString(value)",
                        "interop.isString(value)"
        })
        StaticObject doForeignString(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                String hostString = interop.asString(value);
                return meta.toGuestString(hostString);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isString returns true, asString must succeed.");
            }
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "!interop.isString(value)"
        })
        StaticObject doUnsupported(Object value,
                        @Shared("value") @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_String.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "j.l.CharSequence target type")
    @GenerateUncached
    public abstract static class ToCharSequence extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        StaticObject doHostString(String value, @Bind("getMeta()") Meta meta) {
            return meta.toGuestString(value);
        }

        @Specialization(guards = "isEspressoString(value, meta)")
        StaticObject doEspressoString(StaticObject value, @Bind("getMeta()") @SuppressWarnings("unused") Meta meta) {
            return value;
        }

        @Specialization(guards = "!isEspressoString(value, meta)")
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_lang_CharSequence)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_lang_CharSequence.getTypeAsString());
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!isHostString(value)",
                        "interop.isString(value)"
        })
        StaticObject doForeignString(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                String hostString = interop.asString(value);
                return meta.toGuestString(hostString);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isString returns true, asString must succeed.");
            }
        }

        @Fallback
        StaticObject doUnsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_String.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "interface type mapping")
    public abstract static class ToMappedInterface extends ToReference {
        protected static final int LIMIT = 4;

        private final EspressoType targetType;

        ToMappedInterface(EspressoType targetType) {
            this.targetType = targetType;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), targetType.getRawType())) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getRawType().getTypeAsString());
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "isHostObject(getContext(), value)"
        })
        StaticObject doForeignInterface(Object value,
                        @Bind Node node,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached ProxyInstantiateNode proxyInstantiateNode,
                        @Cached InlinedBranchProfile errorProfile) throws UnsupportedTypeException {
            try {
                Object metaObject = interop.getMetaObject(value);
                WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, getMetaName(metaObject, interop), targetType.getRawType());

                if (proxyKlass != null) {
                    return proxyInstantiateNode.execute(proxyKlass, value, targetType);
                }
            } catch (UnsupportedMessageException e) {
                // no meta object, fall through to throw unsupported type
            }
            errorProfile.enter(node);
            throw unsupportedType(value, targetType.getRawType());
        }
    }

    @NeverDefault
    static InstanceOf createInstanceOf(Klass targetType) {
        return InstanceOf.create(targetType, false);
    }

    @NodeInfo(shortName = "custom type converter")
    @ImportStatic(ToReference.class)
    public abstract static class ToMappedType extends ToReference {
        protected static final int LIMIT = 4;

        final EspressoType targetType;

        ToMappedType(EspressoType targetType) {
            this.targetType = targetType;
        }

        static InstanceOf createInstanceOf(EspressoType targetType) {
            return InstanceOf.create(targetType.getRawType(), false);
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached("createInstanceOf(targetType)") InstanceOf instanceOf) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass())) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getRawType().getTypeAsString());
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "isHostObject(getContext(), value)"
        })
        StaticObject doForeignConverter(Object value,
                        @Bind Node node,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getContext()") EspressoContext context,
                        @Cached InlinedBranchProfile errorProfile) throws UnsupportedTypeException {
            try {
                Object metaObject = interop.getMetaObject(value);
                String metaName = getMetaName(metaObject, interop);

                // check if there's a specific type mapping available
                PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
                if (converter != null) {
                    StaticObject foreign;
                    if (interop.isException(value)) {
                        foreign = StaticObject.createForeignException(context, value, interop);
                    } else {
                        foreign = StaticObject.createForeign(getLanguage(), targetType.getRawType(), value, interop);
                    }
                    if (targetType instanceof ParameterizedEspressoType parameterizedEspressoType) {
                        context.getLanguage().getTypeArgumentProperty().setObject(foreign, parameterizedEspressoType.getTypeArguments());
                    }
                    StaticObject result = (StaticObject) converter.convert(foreign);
                    if (instanceOf.execute(result.getKlass(), targetType.getRawType())) {
                        return result;
                    }
                }
            } catch (UnsupportedMessageException e) {
                // no meta object, fall through to throw unsupported type
            }
            errorProfile.enter(node);
            throw ToEspressoNode.unsupportedType(value, targetType.getRawType());
        }
    }

    @NodeInfo(shortName = "custom type converter")
    @ImportStatic(ToReference.class)
    public abstract static class ToMappedInternalType extends ToReference {
        protected static final int LIMIT = 4;

        final EspressoType targetType;

        ToMappedInternalType(EspressoType targetType) {
            this.targetType = targetType;
        }

        static InstanceOf createInstanceOf(EspressoType targetType) {
            return InstanceOf.create(targetType.getRawType(), false);
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached("createInstanceOf(targetType)") InstanceOf instanceOf) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass())) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getRawType().getTypeAsString());
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "isHostObject(getContext(), value)"
        })
        StaticObject doForeignInternalConverter(Object value,
                        @Bind Node node,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached LookupInternalTypeConverterNode lookupTypeConverterNode,
                        @Cached ToReference.DynamicToReference converterToEspresso,
                        @Cached InlinedBranchProfile errorProfile,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            try {
                Object metaObject = interop.getMetaObject(value);
                String metaName = getMetaName(metaObject, interop);

                // check if there's a specific type mapping available
                PolyglotTypeMappings.InternalTypeConverter converter = lookupTypeConverterNode.execute(metaName);
                if (converter != null) {
                    return converter.convertInternal(interop, value, meta, converterToEspresso, targetType);
                }
            } catch (UnsupportedMessageException e) {
                // no meta object, fall through to throw unsupported type
            }
            errorProfile.enter(node);
            throw ToEspressoNode.unsupportedType(value, targetType.getRawType());
        }
    }

    @NodeInfo(shortName = "Number type mapping")
    @GenerateUncached
    public abstract static class ToNumber extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_lang_Number)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_lang_Number.getTypeAsString());
        }

        @Specialization
        StaticObject doHostInteger(Integer value) {
            return getMeta().boxInteger(value);
        }

        @Specialization
        StaticObject doHostByte(Byte value) {
            return getMeta().boxByte(value);
        }

        @Specialization
        StaticObject doHostShort(Short value) {
            return getMeta().boxShort(value);
        }

        @Specialization
        StaticObject doHostLong(Long value) {
            return getMeta().boxLong(value);
        }

        @Specialization
        StaticObject doHostFloat(Float value) {
            return getMeta().boxFloat(value);
        }

        @Specialization
        StaticObject doHostDouble(Double value) {
            return getMeta().boxDouble(value);
        }

        @Specialization(guards = {
                        "interop.isNumber(value)",
                        "!isStaticObject(value)",
                        "!isHostNumber(value)"
        })
        StaticObject doForeignNumber(Object value,
                        @CachedLibrary(limit = "LIMIT") @Exclusive InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (interop.fitsInBigInteger(value) || interop.fitsInDouble(value)) {
                return StaticObject.createForeign(getLanguage(), meta.polyglot.EspressoForeignNumber, value, interop);
            }
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_Number.getTypeAsString());
        }

        @Fallback
        public StaticObject doUnsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_Number.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "byte array type mapping")
    @GenerateUncached
    public abstract static class ToByteArray extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta._byte_array)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta._byte_array.getTypeAsString());
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "interop.hasBufferElements(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)",
                        "!isEspressoException(value)",
        })
        StaticObject doForeignBuffer(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @SuppressWarnings("unused") @Bind("getMeta()") Meta meta) {
            return StaticObject.createForeign(EspressoLanguage.get(this), meta._byte_array, value, interop);
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "interop.hasArrayElements(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)",
                        "!isEspressoException(value)",
        })
        StaticObject doForeignArray(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @SuppressWarnings("unused") @Bind("getMeta()") Meta meta) {
            return StaticObject.createForeign(EspressoLanguage.get(this), meta._byte_array, value, interop);
        }

        @Fallback
        public StaticObject doUnsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta()._byte_array.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "array type mapping")
    public abstract static class ToArray extends ToReference {
        protected static final int LIMIT = 4;

        private final ArrayKlass targetType;

        protected ToArray(ArrayKlass targetType) {
            this.targetType = targetType;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), targetType)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
        }

        @Specialization(guards = {
                        "interop.hasArrayElements(value)",
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)",
                        "!isEspressoException(value)"
        })
        StaticObject doForeignArray(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeign(EspressoLanguage.get(this), targetType, value, interop);
        }

        @Fallback
        public StaticObject doUnsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "foreign exception type mapping")
    @GenerateUncached
    public abstract static class ToForeignException extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public StaticObject doForeignNull(Object value,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(language, value);
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "interop.isException(value)",
                        "!isEspressoException(value)"
        })
        StaticObject doForeignException(Object value,
                        @CachedLibrary(limit = "LIMIT") @Exclusive InteropLibrary interop,
                        @Bind("getContext()") EspressoContext context) {
            return StaticObject.createForeignException(context, value, interop);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), getMeta().polyglot.ForeignException)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().polyglot.ForeignException.getTypeAsString());
        }

        @Fallback
        public StaticObject doUnsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().polyglot.ForeignException.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "throwable type mapping")
    @GenerateUncached
    public abstract static class ToThrowable extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public StaticObject doForeignNull(Object value,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(language, value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || meta.java_lang_Throwable.isAssignableFrom(value.getKlass())) {
                return value;
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_lang_Throwable.getTypeAsString());
        }

        @Specialization
        public StaticObject doEspressoException(EspressoException value) {
            return value.getGuestException();
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "interop.isException(value)",
                        "!isEspressoException(value)"
        })

        StaticObject doForeign(Object value,
                        @CachedLibrary(limit = "LIMIT") @Exclusive InteropLibrary interop,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached ToReference.DynamicToReference converterToEspresso,
                        @Bind("getContext()") EspressoContext context) throws UnsupportedTypeException {
            StaticObject result = ToReference.tryConverterForUnknownTarget(value, getMeta().java_lang_Throwable, interop, lookupTypeConverterNode, lookupInternalTypeConverterNode, converterToEspresso,
                            context.getMeta());
            if (result != null) {
                return result;
            }
            return StaticObject.createForeignException(context, value, interop);
        }

        @Fallback
        public StaticObject doUnsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_Throwable.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "exception type mapping")
    @GenerateUncached
    public abstract static class ToException extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public StaticObject doForeignNull(Object value,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(language, value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || meta.java_lang_Exception.isAssignableFrom(value.getKlass())) {
                return value;
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_lang_Exception.getTypeAsString());
        }

        @Specialization
        public StaticObject doEspressoException(EspressoException value, @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            StaticObject guestException = value.getGuestException();
            if (meta.java_lang_Exception.isAssignableFrom(guestException.getKlass())) {
                return guestException;
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_lang_Exception.getTypeAsString());
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "interop.isException(value)",
                        "!isEspressoException(value)"
        })

        StaticObject doForeign(Object value,
                        @CachedLibrary(limit = "LIMIT") @Exclusive InteropLibrary interop,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached ToReference.DynamicToReference converterToEspresso,
                        @Bind("getContext()") EspressoContext context) throws UnsupportedTypeException {
            StaticObject result = ToReference.tryConverterForUnknownTarget(value, getMeta().java_lang_Exception, interop, lookupTypeConverterNode, lookupInternalTypeConverterNode, converterToEspresso,
                            context.getMeta());
            if (result != null) {
                return result;
            }
            return StaticObject.createForeignException(context, value, interop);
        }

        @Fallback
        public StaticObject doUnsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_Exception.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "runtime exception type mapping")
    @GenerateUncached
    public abstract static class ToRuntimeException extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public StaticObject doForeignNull(Object value,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(language, value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || meta.java_lang_RuntimeException.isAssignableFrom(value.getKlass())) {
                return value;
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_lang_RuntimeException.getTypeAsString());
        }

        @Specialization
        public StaticObject doEspressoException(EspressoException value, @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            StaticObject guestException = value.getGuestException();
            if (meta.java_lang_RuntimeException.isAssignableFrom(guestException.getKlass())) {
                return guestException;
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_lang_RuntimeException.getTypeAsString());
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "interop.isException(value)",
                        "!isEspressoException(value)"
        })
        StaticObject doForeign(Object value,
                        @CachedLibrary(limit = "LIMIT") @Exclusive InteropLibrary interop,
                        @Bind("getContext()") EspressoContext context,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached ToReference.DynamicToReference converterToEspresso) throws UnsupportedTypeException {
            StaticObject result = ToReference.tryConverterForUnknownTarget(value, getMeta().java_lang_RuntimeException, interop, lookupTypeConverterNode, lookupInternalTypeConverterNode,
                            converterToEspresso, context.getMeta());
            if (result != null) {
                return result;
            }
            return StaticObject.createForeignException(context, value, interop);
        }

        @Fallback
        public StaticObject doUnsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_RuntimeException.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "LocalDate type mapping")
    @GenerateUncached
    public abstract static class ToLocalDate extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_LocalDate)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_LocalDate.getTypeAsString());
        }

        @Specialization(guards = "interop.isDate(value)")
        StaticObject doForeignLocalDate(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                LocalDate localDate = interop.asDate(value);
                return (StaticObject) meta.java_time_LocalDate_of.invokeDirectStatic(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isDate returns true, asDate must succeed.");
            }
        }

        @Specialization(guards = "!interop.isDate(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_time_LocalDate.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "LocalTime type mapping")
    @GenerateUncached
    public abstract static class ToLocalTime extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_LocalTime)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_LocalTime.getTypeAsString());
        }

        @Specialization(guards = "interop.isTime(value)")
        StaticObject doForeignLocalTime(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                LocalTime localTime = interop.asTime(value);
                return (StaticObject) meta.java_time_LocalTime_of.invokeDirectStatic(localTime.getHour(), localTime.getMinute(), localTime.getSecond(), localTime.getNano());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isTime returns true, asTime must succeed.");
            }
        }

        @Specialization(guards = "!interop.isTime(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_time_LocalTime.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "LocalDateTime type mapping")
    @GenerateUncached
    public abstract static class ToLocalDateTime extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_LocalDateTime)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_LocalDateTime.getTypeAsString());
        }

        @Specialization(guards = {
                        "interop.isTime(value)",
                        "interop.isDate(value)"
        })
        StaticObject doForeignLocalDateTime(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                LocalDate localDate = interop.asDate(value);
                LocalTime localTime = interop.asTime(value);
                StaticObject guestLocalDate = (StaticObject) meta.java_time_LocalDate_of.invokeDirectStatic(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
                StaticObject guestLocalTime = (StaticObject) meta.java_time_LocalTime_of.invokeDirectStatic(localTime.getHour(), localTime.getMinute(), localTime.getSecond(), localTime.getNano());

                return (StaticObject) meta.java_time_LocalDateTime_of.invokeDirectStatic(guestLocalDate, guestLocalTime);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isDate returns true, asDate must succeed.");
            }
        }

        @Specialization(guards = {
                        "!interop.isTime(value)",
                        "!interop.isDate(value)"
        })
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_time_LocalDateTime.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "ZonedDateTime type mapping")
    @GenerateUncached
    public abstract static class ToZonedDateTime extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_ZonedDateTime)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_ZonedDateTime.getTypeAsString());
        }

        @Specialization(guards = {
                        "interop.isInstant(value)",
                        "interop.isTimeZone(value)"
        })
        StaticObject doForeignDateTime(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                Instant instant = interop.asInstant(value);
                ZoneId zoneId = interop.asTimeZone(value);
                StaticObject guestInstant = (StaticObject) meta.java_time_Instant_ofEpochSecond.invokeDirectStatic(instant.getEpochSecond(), (long) instant.getNano());
                StaticObject guestZoneID = (StaticObject) meta.java_time_ZoneId_of.invokeDirectStatic(meta.toGuestString(zoneId.getId()));
                return (StaticObject) meta.java_time_ZonedDateTime_ofInstant.invokeDirectStatic(guestInstant, guestZoneID);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isTime returns true, asTime must succeed.");
            }
        }

        @Specialization(guards = {
                        "!interop.isInstant(value)",
                        "!interop.isTimeZone(value)"
        })
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_time_ZonedDateTime.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "Instant type mapping")
    @GenerateUncached
    public abstract static class ToInstant extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_Instant)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_Instant.getTypeAsString());
        }

        @Specialization(guards = "interop.isInstant(value)")
        StaticObject doForeignInstant(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                Instant instant = interop.asInstant(value);
                return (StaticObject) meta.java_time_Instant_ofEpochSecond.invokeDirectStatic(instant.getEpochSecond(), (long) instant.getNano());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isInstant returns true, asInstant must succeed.");
            }
        }

        @Specialization(guards = "!interop.isInstant(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_time_Instant.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "Duration type mapping")
    @GenerateUncached
    public abstract static class ToDuration extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_Duration)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_Duration.getTypeAsString());
        }

        @Specialization(guards = "interop.isDuration(value)")
        StaticObject doForeignDuration(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                Duration duration = interop.asDuration(value);
                StaticObject guestDuration = meta.getAllocator().createNew(meta.java_time_Duration);
                meta.java_time_Duration_seconds.setLong(guestDuration, duration.getSeconds());
                meta.java_time_Duration_nanos.setInt(guestDuration, duration.getNano());
                return guestDuration;
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isDuration returns true, asDuration must succeed.");
            }
        }

        @Specialization(guards = "!interop.isDuration(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_time_Duration.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "ZoneId type mapping")
    @GenerateUncached
    public abstract static class ToZoneId extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_ZoneId)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_ZoneId.getTypeAsString());
        }

        @Specialization(guards = "interop.isTimeZone(value)")
        StaticObject doForeignZoneId(Object value, @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                ZoneId zoneId = interop.asTimeZone(value);
                return (StaticObject) meta.java_time_ZoneId_of.invokeDirectStatic(meta.toGuestString(zoneId.getId()));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isZoneId returns true, asTimeZone must succeed.");
            }
        }

        @Specialization(guards = "!interop.isTimeZone(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_time_ZoneId.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "Date type mapping")
    @GenerateUncached
    public abstract static class ToDate extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_util_Date)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_util_Date.getTypeAsString());
        }

        @Specialization(guards = "interop.isInstant(value)")
        StaticObject doForeignDate(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                Instant instant = interop.asInstant(value);
                StaticObject guestInstant = (StaticObject) meta.java_time_Instant_ofEpochSecond.invokeDirectStatic(instant.getEpochSecond(), (long) instant.getNano());
                return (StaticObject) meta.java_util_Date_from.invokeDirectStatic(guestInstant);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isInstant returns true, asInstant must succeed.");
            }
        }

        @Specialization(guards = "!interop.isInstant(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Date.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "BigInteger type mapping")
    @GenerateUncached
    public abstract static class ToBigInteger extends ToReference {
        protected static final int LIMIT = 4;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_math_BigInteger)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_math_BigInteger.getTypeAsString());
        }

        @TruffleBoundary
        private StaticObject toGuestBigInteger(Meta meta, BigInteger bigInteger) {
            byte[] bytes = bigInteger.toByteArray();
            StaticObject guestBigInteger = getAllocator().createNew(meta.java_math_BigInteger);
            meta.java_math_BigInteger_init.invokeDirectSpecial(guestBigInteger, StaticObject.wrap(bytes, meta));
            return guestBigInteger;
        }

        @Specialization(guards = "interop.fitsInBigInteger(value)")
        StaticObject doForeignBigInteger(Object value,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                BigInteger bigInteger = interop.asBigInteger(value);
                return toGuestBigInteger(meta, bigInteger);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if fitsInBigInteger returns true, asBigInteger must succeed.");
            }
        }

        @Specialization(guards = "!interop.fitsInBigInteger(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_math_BigInteger.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "unknown type mapping")
    @ImportStatic(ToReference.class)
    public abstract static class ToUnknown extends ToReference {
        protected static final int LIMIT = 4;

        protected final EspressoType targetType;

        ToUnknown(EspressoType targetType) {
            this.targetType = targetType;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value) throws UnsupportedTypeException {
            assert !value.isForeignObject();
            if (StaticObject.isNull(value) || targetType.getRawType().isAssignableFrom(value.getKlass())) {
                return value;
            }
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getRawType().getTypeAsString());
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        static StaticObject doForeignWrapper(Object value,
                        @Bind Node node,
                        @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("get(node)") EspressoContext context,
                        @Bind("targetType") EspressoType target,
                        @Cached("getUncachedToReference(targetType, context.getMeta())") ToReference uncachedToReference,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached ToReference.DynamicToReference converterToEspresso,
                        @Cached InlinedBranchProfile unknownProfile,
                        @Cached InlinedBranchProfile noConverterProfile) throws UnsupportedTypeException {
            if (uncachedToReference != null) {
                return uncachedToReference.execute(value);
            }
            unknownProfile.enter(node);
            // hit the unknown type case, so inline generic handling for that here
            StaticObject result = tryConverterForUnknownTarget(value, target, interop, lookupTypeConverterNode, lookupInternalTypeConverterNode, converterToEspresso, context.getMeta());
            if (result != null) {
                return result;
            }
            // no generic conversion to abstract target types allowed
            if (target.getRawType().isAbstract()) {
                throw UnsupportedTypeException.create(new Object[]{value}, target.getRawType().getTypeAsString());
            }
            noConverterProfile.enter(node);
            checkHasAllFieldsOrThrow(value, (ObjectKlass) target.getRawType(), interop, context.getMeta());
            return StaticObject.createForeign(EspressoLanguage.get(node), target.getRawType(), value, interop);
        }

        @Specialization
        public StaticObject doUnknown(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getRawType().getTypeAsString());
        }
    }

    static boolean isHostNumber(Object obj) {
        return obj instanceof Number;
    }

    static boolean isEspressoString(StaticObject obj, Meta meta) {
        return obj.getKlass() == meta.java_lang_String;
    }

    static boolean isBoxedPrimitive(Object obj) {
        return obj instanceof Number || obj instanceof Character || obj instanceof Boolean;
    }

    static boolean isEspressoException(Object obj) {
        return obj instanceof EspressoException;
    }

    @Idempotent
    static boolean isTypeMappingEnabled(EspressoContext context) {
        return context.getPolyglotTypeMappings().hasMappings();
    }

    static boolean isHostObject(EspressoContext context, Object value) {
        return context.getEnv().isHostObject(value);
    }
}
