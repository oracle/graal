/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.bytecodes.InstanceOf;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@NodeInfo(shortName = "Convert to Espresso StaticObject")
@GenerateUncached
public abstract class ToReference extends ToEspressoNode {

    @Override
    public abstract StaticObject execute(Object value) throws UnsupportedTypeException;

    @TruffleBoundary
    public static ToReference createToReference(Klass targetType, Meta meta) {
        if (targetType == meta.java_lang_Void) {
            return ToReferenceFactory.ToVoidNodeGen.create();
        }
        if (targetType == meta.java_lang_Boolean) {
            return ToReferenceFactory.ToBooleanNodeGen.create();
        }
        if (targetType == meta.java_lang_Character) {
            return ToReferenceFactory.ToCharNodeGen.create();
        }
        if (targetType == meta.java_lang_Integer) {
            return ToReferenceFactory.ToIntegerNodeGen.create();
        }
        if (targetType == meta.java_lang_Byte) {
            return ToReferenceFactory.ToByteNodeGen.create();
        }
        if (targetType == meta.java_lang_Short) {
            return ToReferenceFactory.ToShortNodeGen.create();
        }
        if (targetType == meta.java_lang_Long) {
            return ToReferenceFactory.ToLongNodeGen.create();
        }
        if (targetType == meta.java_lang_Float) {
            return ToReferenceFactory.ToFloatNodeGen.create();
        }
        if (targetType == meta.java_lang_Double) {
            return ToReferenceFactory.ToDoubleNodeGen.create();
        }
        if (targetType == meta.java_lang_Number) {
            return ToReferenceFactory.ToNumberNodeGen.create();
        }
        if (targetType == meta._byte_array) {
            return ToReferenceFactory.ToByteArrayNodeGen.create();
        }
        if (targetType.isArray()) {
            return ToReferenceFactory.ToArrayNodeGen.create((ArrayKlass) targetType);
        }
        if (targetType.isJavaLangObject()) {
            return ToReferenceFactory.ToJavaLangObjectNodeGen.create();
        }
        if (targetType == meta.java_lang_String) {
            return ToReferenceFactory.ToStringNodeGen.create();
        }
        if (targetType.isInterface()) {
            if (targetType.getContext().getEspressoEnv().BuiltInPolyglotCollections) {
                if (targetType == meta.java_util_List && targetType.isInternalCollectionTypeMapped()) {
                    return ToReferenceFactory.ToListNodeGen.create();
                }
                if (targetType == meta.java_util_Set && targetType.isInternalCollectionTypeMapped()) {
                    return ToReferenceFactory.ToSetNodeGen.create();
                }
                if (targetType == meta.java_util_Collection && targetType.isInternalCollectionTypeMapped()) {
                    return ToReferenceFactory.ToCollectionNodeGen.create();
                }
                if (targetType == meta.java_lang_Iterable && targetType.isInternalCollectionTypeMapped()) {
                    return ToReferenceFactory.ToIterableNodeGen.create();
                }
                if (targetType == meta.java_util_Iterator && targetType.isInternalCollectionTypeMapped()) {
                    return ToReferenceFactory.ToIteratorNodeGen.create();
                }
                if (targetType == meta.java_util_Map && targetType.isInternalCollectionTypeMapped()) {
                    return ToReferenceFactory.ToMapNodeGen.create();
                }
            }
            if (targetType == meta.java_lang_CharSequence) {
                return ToReferenceFactory.ToCharSequenceNodeGen.create();
            }
            if (isTypeMappingEnabled(targetType)) {
                return ToReferenceFactory.ToMappedInterfaceNodeGen.create((ObjectKlass) targetType);
            } else {
                return ToReferenceFactory.ToUnknownNodeGen.create((ObjectKlass) targetType);
            }
        }
        if (isForeignException(targetType, meta)) {
            return ToReferenceFactory.ToForeignExceptionNodeGen.create();
        }
        if (targetType == meta.java_lang_Throwable) {
            return ToReferenceFactory.ToThrowableNodeGen.create();
        }
        if (targetType == meta.java_lang_Exception) {
            return ToReferenceFactory.ToExceptionNodeGen.create();
        }
        if (targetType == meta.java_lang_RuntimeException) {
            return ToReferenceFactory.ToRuntimeExceptionNodeGen.create();
        }
        if (targetType == meta.java_time_LocalDate) {
            return ToReferenceFactory.ToLocalDateNodeGen.create();
        }
        if (targetType == meta.java_time_LocalTime) {
            return ToReferenceFactory.ToLocalTimeNodeGen.create();
        }
        if (targetType == meta.java_time_LocalDateTime) {
            return ToReferenceFactory.ToLocalDateTimeNodeGen.create();
        }
        if (targetType == meta.java_time_ZonedDateTime) {
            return ToReferenceFactory.ToZonedDateTimeNodeGen.create();
        }
        if (targetType == meta.java_time_Instant) {
            return ToReferenceFactory.ToInstantNodeGen.create();
        }
        if (targetType == meta.java_time_Duration) {
            return ToReferenceFactory.ToDurationNodeGen.create();
        }
        if (targetType == meta.java_time_ZoneId) {
            return ToReferenceFactory.ToZoneIdNodeGen.create();
        }
        if (targetType == meta.java_util_Date) {
            return ToReferenceFactory.ToDateNodeGen.create();
        }
        if (isTypeConverterEnabled(targetType)) {
            return ToReferenceFactory.ToMappedTypeNodeGen.create((ObjectKlass) targetType);
        }
        if (isInternalTypeConverterEnabled(targetType)) {
            return ToReferenceFactory.ToMappedInternalTypeNodeGen.create((ObjectKlass) targetType);
        } else {
            return ToReferenceFactory.ToUnknownNodeGen.create((ObjectKlass) targetType);
        }
    }

    @TruffleBoundary
    static ToReference getUncachedToReference(Klass targetType, Meta meta) {
        if (targetType == meta.java_lang_Void) {
            return ToReferenceFactory.ToVoidNodeGen.getUncached();
        }
        if (targetType == meta.java_lang_Boolean) {
            return ToReferenceFactory.ToBooleanNodeGen.getUncached();
        }
        if (targetType == meta.java_lang_Character) {
            return ToReferenceFactory.ToCharNodeGen.getUncached();
        }
        if (targetType == meta.java_lang_Integer) {
            return ToReferenceFactory.ToIntegerNodeGen.getUncached();
        }
        if (targetType == meta.java_lang_Byte) {
            return ToReferenceFactory.ToByteNodeGen.getUncached();
        }
        if (targetType == meta.java_lang_Short) {
            return ToReferenceFactory.ToShortNodeGen.getUncached();
        }
        if (targetType == meta.java_lang_Long) {
            return ToReferenceFactory.ToLongNodeGen.getUncached();
        }
        if (targetType == meta.java_lang_Float) {
            return ToReferenceFactory.ToFloatNodeGen.getUncached();
        }
        if (targetType == meta.java_lang_Double) {
            return ToReferenceFactory.ToDoubleNodeGen.getUncached();
        }
        if (targetType == meta.java_lang_Number) {
            return ToReferenceFactory.ToNumberNodeGen.getUncached();
        }
        if (targetType == meta._byte_array) {
            return ToReferenceFactory.ToByteArrayNodeGen.getUncached();
        }
        if (targetType.isArray()) {
            throw new IllegalStateException("Generic arrays type mappings must be handled separately!");
        }
        if (targetType.isJavaLangObject()) {
            return ToReferenceFactory.ToJavaLangObjectNodeGen.getUncached();
        }
        if (targetType == meta.java_lang_String) {
            return ToReferenceFactory.ToStringNodeGen.getUncached();
        }
        if (targetType.isInterface()) {
            if (targetType == meta.java_lang_CharSequence) {
                return ToReferenceFactory.ToCharSequenceNodeGen.getUncached();
            }
            if (targetType.getContext().getEspressoEnv().BuiltInPolyglotCollections) {
                if (targetType == meta.java_util_List && targetType.isInternalCollectionTypeMapped()) {
                    return ToReferenceFactory.ToListNodeGen.getUncached();
                }
                if (targetType == meta.java_util_Set && targetType.isInternalCollectionTypeMapped()) {
                    return ToReferenceFactory.ToSetNodeGen.getUncached();
                }
                if (targetType == meta.java_util_Collection && targetType.isInternalCollectionTypeMapped()) {
                    return ToReferenceFactory.ToCollectionNodeGen.getUncached();
                }
                if (targetType == meta.java_lang_Iterable && targetType.isInternalCollectionTypeMapped()) {
                    return ToReferenceFactory.ToIterableNodeGen.getUncached();
                }
                if (targetType == meta.java_util_Iterator && targetType.isInternalCollectionTypeMapped()) {
                    return ToReferenceFactory.ToIteratorNodeGen.getUncached();
                }
                if (targetType == meta.java_util_Map && targetType.isInternalCollectionTypeMapped()) {
                    return ToReferenceFactory.ToMapNodeGen.getUncached();
                }
            }
            if (isTypeMappingEnabled(targetType)) {
                throw new IllegalStateException("Interface type mappings must be handled separately!");
            } else {
                throw new IllegalStateException("unknown types must be handled separately!");
            }
        }
        if (isForeignException(targetType, meta)) {
            return ToReferenceFactory.ToForeignExceptionNodeGen.getUncached();
        }
        if (targetType == meta.java_lang_Throwable) {
            return ToReferenceFactory.ToThrowableNodeGen.getUncached();
        }
        if (targetType == meta.java_lang_Exception) {
            return ToReferenceFactory.ToExceptionNodeGen.getUncached();
        }
        if (targetType == meta.java_lang_RuntimeException) {
            return ToReferenceFactory.ToRuntimeExceptionNodeGen.getUncached();
        }
        if (targetType == meta.java_time_LocalDate) {
            return ToReferenceFactory.ToLocalDateNodeGen.getUncached();
        }
        if (targetType == meta.java_time_LocalTime) {
            return ToReferenceFactory.ToLocalTimeNodeGen.getUncached();
        }
        if (targetType == meta.java_time_LocalDateTime) {
            return ToReferenceFactory.ToLocalDateTimeNodeGen.getUncached();
        }
        if (targetType == meta.java_time_ZonedDateTime) {
            return ToReferenceFactory.ToZonedDateTimeNodeGen.getUncached();
        }
        if (targetType == meta.java_time_Instant) {
            return ToReferenceFactory.ToInstantNodeGen.getUncached();
        }
        if (targetType == meta.java_time_Duration) {
            return ToReferenceFactory.ToDurationNodeGen.getUncached();
        }
        if (targetType == meta.java_time_ZoneId) {
            return ToReferenceFactory.ToZoneIdNodeGen.getUncached();
        }
        if (targetType == meta.java_util_Date) {
            return ToReferenceFactory.ToDateNodeGen.getUncached();
        }
        throw new IllegalStateException("unknown types must be handled separately!");
    }

    @NodeInfo(shortName = "Dynamic toEspresso node")
    @GenerateUncached
    @ReportPolymorphism
    public abstract static class DynamicToReference extends EspressoNode {
        protected static final int LIMIT = 4;

        public abstract StaticObject execute(Object value, Klass targetType) throws UnsupportedTypeException;

        protected static ToReference createToEspressoNode(Klass targetType) {
            return createToReference(targetType, targetType.getMeta());
        }

        @Specialization(guards = "targetType == cachedTargetType", limit = "LIMIT")
        public StaticObject doCached(Object value, @SuppressWarnings("unused") Klass targetType,
                        @SuppressWarnings("unused") @Cached("targetType") Klass cachedTargetType,
                        @Cached("createToEspressoNode(cachedTargetType)") ToReference toEspressoNode) throws UnsupportedTypeException {
            return toEspressoNode.execute(value);
        }

        @ReportPolymorphism.Megamorphic
        @Specialization(replaces = "doCached")
        public StaticObject doGeneric(Object value, Klass targetType,
                        @Cached ToReference.GenericToReference genericToReference) throws UnsupportedTypeException {
            return genericToReference.execute(value, targetType);
        }
    }

    @NodeInfo(shortName = "Generic toEspresso node")
    @GenerateUncached
    @ImportStatic(ToEspressoNode.class)
    public abstract static class GenericToReference extends EspressoNode {
        protected static final int LIMIT = 2;

        public abstract StaticObject execute(Object value, Klass targetType) throws UnsupportedTypeException;

        public static boolean isStaticObject(Object value) {
            return value instanceof StaticObject;
        }

        @Specialization
        public StaticObject doStaticObject(StaticObject value, @SuppressWarnings("unused") Klass targetType,
                        @Cached InstanceOf.Dynamic instanceOf) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), targetType)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to ", targetType.getTypeAsString()));
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public StaticObject doForeignNull(Object value, @SuppressWarnings("unused") Klass targetType,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "isTypeMappingEnabled(targetType)",
                        "!isStaticObject(value)"
        })
        public StaticObject doMappedInterface(Object value, @SuppressWarnings("unused") Klass targetType,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                Object metaObject = getMetaObjectOrThrow(value, interop);
                WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, getMetaName(metaObject, interop), targetType);
                if (proxyKlass != null) {
                    targetType.safeInitialize();
                    return proxyKlass.createProxyInstance(value, getLanguage(), interop);
                }
                throw new ClassCastException();
            } catch (ClassCastException e) {
                throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", targetType.getTypeAsString()));
            }
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        public StaticObject doArray(Object value, @SuppressWarnings("unused") ArrayKlass targetType,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
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
        public StaticObject doTypeConverter(Object value, @SuppressWarnings("unused") Klass targetType,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                Object metaObject = getMetaObjectOrThrow(value, interop);
                String metaName = getMetaName(metaObject, interop);

                // check if there's a specific type mapping available
                PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
                if (converter != null) {
                    return (StaticObject) converter.convert(StaticObject.createForeign(getLanguage(), targetType, value, interop));
                }
                throw new ClassCastException();
            } catch (ClassCastException e) {
                throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", targetType.getNameAsString(), e.getMessage()));
            }
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "isInternalTypeConverterEnabled(targetType)",
                        "!isStaticObject(value)"
        })
        public StaticObject doInternalTypeConverter(Object value, @SuppressWarnings("unused") Klass targetType,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached ToReference.DynamicToReference converterToEspresso,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                Object metaObject = getMetaObjectOrThrow(value, interop);
                String metaName = getMetaName(metaObject, interop);

                // check if there's a specific type mapping available
                PolyglotTypeMappings.InternalTypeConverter converter = lookupInternalTypeConverterNode.execute(metaName);
                if (converter != null) {
                    return converter.convertInternal(interop, value, getMeta(), converterToEspresso);
                }
                throw new ClassCastException();
            } catch (ClassCastException e) {
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
        public StaticObject doGeneric(Object value, Klass targetType,
                        @Bind("getMeta()") Meta meta,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                return getUncachedToReference(targetType, meta).execute(value);
            } catch (IllegalStateException ex) {
                // hit the unknown type case, so inline generic handling for that here
                if (targetType instanceof ObjectKlass) {
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
    }

    @NodeInfo(shortName = "List type mapping")
    @GenerateUncached
    public abstract static class ToList extends ToReference {
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
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_util_List)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to ", meta.java_util_List.getTypeAsString()));
        }

        @Specialization(guards = {
                        "interop.hasArrayElements(value)",
                        "interop.hasMetaObject(value)",
                        "isHostObject(context, value)"
        })
        public StaticObject doMappedList(Object value,
                        @Bind("getContext()") EspressoContext context,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            Object metaObject = getMetaObjectOrThrow(value, interop);
            WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, getMetaName(metaObject, interop), context.getMeta().java_util_List);
            if (proxyKlass == null) {
                throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_List.getTypeAsString());
            }
            return proxyKlass.createProxyInstance(value, language, interop);
        }

        @Fallback
        public StaticObject unsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_List.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "Collection type mapping")
    @GenerateUncached
    public abstract static class ToCollection extends ToReference {
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
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_util_Collection)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to ", meta.java_util_Collection.getTypeAsString()));
        }

        @Specialization(guards = {
                        "interop.hasIterator(value)",
                        "interop.hasMetaObject(value)",
                        "isHostObject(getContext(), value)"
        })
        public StaticObject doMappedCollection(Object value,
                        @Bind("getMeta()") Meta meta,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            Object metaObject = getMetaObjectOrThrow(value, interop);
            WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, getMetaName(metaObject, interop), meta.java_util_Collection);
            if (proxyKlass == null) {
                throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Collection.getTypeAsString());
            }
            return proxyKlass.createProxyInstance(value, language, interop);
        }

        @Fallback
        public StaticObject unsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Collection.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "Iterable type mapping")
    @GenerateUncached
    public abstract static class ToIterable extends ToReference {
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
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_lang_Iterable)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to ", meta.java_lang_Iterable.getTypeAsString()));
        }

        @Specialization(guards = {
                        "interop.hasIterator(value)",
                        "interop.hasMetaObject(value)",
                        "isHostObject(getContext(), value)"
        })
        public StaticObject doMappedIterable(Object value,
                        @Bind("getMeta()") Meta meta,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            Object metaObject = getMetaObjectOrThrow(value, interop);
            WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, getMetaName(metaObject, interop), meta.java_lang_Iterable);
            if (proxyKlass == null) {
                throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_Iterable.getTypeAsString());
            }
            return proxyKlass.createProxyInstance(value, language, interop);
        }

        @Fallback
        public StaticObject unsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_Iterable.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "Iterator type mapping")
    @GenerateUncached
    public abstract static class ToIterator extends ToReference {
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
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_util_Iterator)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to ", meta.java_util_Iterator.getTypeAsString()));
        }

        @Specialization(guards = {
                        "interop.isIterator(value)",
                        "interop.hasMetaObject(value)",
                        "isHostObject(getContext(), value)"
        })
        public StaticObject doMappedIterator(Object value,
                        @Bind("getMeta()") Meta meta,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            Object metaObject = getMetaObjectOrThrow(value, interop);
            WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, getMetaName(metaObject, interop), meta.java_util_Iterator);
            if (proxyKlass == null) {
                throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Iterator.getTypeAsString());
            }
            return proxyKlass.createProxyInstance(value, language, interop);
        }

        @Fallback
        public StaticObject unsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Iterator.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "Map type mapping")
    @GenerateUncached
    public abstract static class ToMap extends ToReference {
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
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_util_Map)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to ", meta.java_util_Map.getTypeAsString()));
        }

        @Specialization(guards = {
                        "interop.hasHashEntries(value)",
                        "interop.hasMetaObject(value)",
                        "isHostObject(getContext(), value)"
        })
        public StaticObject doMappedMap(Object value,
                        @Bind("getMeta()") Meta meta,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            Object metaObject = getMetaObjectOrThrow(value, interop);
            WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, getMetaName(metaObject, interop), meta.java_util_Map);
            if (proxyKlass == null) {
                throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Map.getTypeAsString());
            }
            return proxyKlass.createProxyInstance(value, language, interop);
        }

        @Fallback
        public StaticObject unsupported(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Map.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "Set type mapping")
    @GenerateUncached
    public abstract static class ToSet extends ToReference {
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
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_util_Set)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to ", meta.java_util_Set.getTypeAsString()));
        }

        @Specialization(guards = {
                        "interop.hasMetaObject(value)",
                        "isHostObject(getContext(), value)"
        })
        public StaticObject doMappedSet(Object value,
                        @Bind("getMeta()") Meta meta,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            Object metaObject = getMetaObjectOrThrow(value, interop);
            WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, getMetaName(metaObject, interop), meta.java_util_Set);
            if (proxyKlass == null) {
                throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Set.getTypeAsString());
            }
            return proxyKlass.createProxyInstance(value, language, interop);
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doInteger(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doBoolean(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doByte(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ToPrimitive.ToByte toByte,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxByte((byte) toByte.execute(value));
        }
    }

    @NodeInfo(shortName = "short target type")
    @GenerateUncached
    abstract static class ToShort extends ToReference {
        protected static final int LIMIT = 2;

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doShort(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doChar(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doLong(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doFloat(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = "!interop.isNull(value)")
        public StaticObject doDouble(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached ToPrimitive.ToDouble toDouble,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return meta.boxDouble((double) toDouble.execute(value));
        }
    }

    @NodeInfo(shortName = "j.l.Object target type")
    @GenerateUncached
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization(guards = {
                        "interop.isBoolean(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignBoolean(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            try {
                return getMeta().boxBoolean(interop.asBoolean(value));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isBoolean returns true, asBoolean must succeed.");
            }
        }

        @Specialization(guards = {
                        "interop.isNumber(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNumber(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context) throws UnsupportedTypeException {
            if (interop.fitsInDouble(value)) {
                return StaticObject.createForeign(getLanguage(), getMeta().java_lang_Number, value, interop);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnsupportedTypeException.create(new Object[]{value}, "unsupported number");
        }

        @Specialization(guards = {
                        "interop.isString(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignString(Object value,
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
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
                        "!isTypeMappingEnabled(context)",
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)",
                        "!isEspressoException(value)",
                        "!isBoxedPrimitive(value)"
        })
        StaticObject doForeignExceptionNoTypeMapping(Object value,
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context) {
            return StaticObject.createForeignException(context, value, interop);
        }

        @Specialization(guards = {
                        "interop.isException(value)",
                        "isTypeMappingEnabled(context)",
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)",
                        "!isEspressoException(value)",
                        "!isBoxedPrimitive(value)"
        })
        StaticObject doForeignExceptionTypeMapping(Object value,
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context,
                        @Cached BranchProfile errorProfile,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached ToReference.DynamicToReference converterToEspresso,
                        @Bind("getMeta()") Meta meta) {
            try {
                return tryTypeConversion(value, interop, lookupProxyKlassNode, lookupTypeConverterNode, lookupInternalTypeConverterNode, converterToEspresso, errorProfile, meta);
            } catch (@SuppressWarnings("unused") UnsupportedTypeException ex) {
                // no meta object available, but we know it's a foreign exception so simply wrap
                return StaticObject.createForeignException(context, value, interop);
            }
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
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
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
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
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
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context,
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile errorProfile,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached ToReference.DynamicToReference converterToEspresso,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return tryTypeConversion(value, interop, lookupProxyKlassNode, lookupTypeConverterNode, lookupInternalTypeConverterNode, converterToEspresso, errorProfile, meta);
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "interop.hasBufferElements(value)",
                        "!interop.isNull(value)",
                        "!isHostString(value)"
        })
        StaticObject doForeignBuffer(Object value,
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
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
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
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
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        @Cached ToReference.DynamicToReference converterToEspresso,
                        @Cached BranchProfile errorProfile,
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context,
                        @SuppressWarnings("unused") @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            return tryTypeConversion(value, interop, lookupProxyKlassNode, lookupTypeConverterNode, lookupInternalTypeConverterNode, converterToEspresso, errorProfile, meta);
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
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeign(EspressoLanguage.get(this), getMeta().java_lang_Object, value, interop);
        }

        @Fallback
        StaticObject doUnsupportedType(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, "java.lang.Object");
        }

        private StaticObject tryTypeConversion(Object value, InteropLibrary interop, LookupProxyKlassNode lookupProxyKlassNode, LookupTypeConverterNode lookupTypeConverterNode,
                        LookupInternalTypeConverterNode lookupInternalTypeConverterNode,
                        ToReference.DynamicToReference converterToEspresso, BranchProfile errorProfile, Meta meta) throws UnsupportedTypeException {
            try {
                Object metaObject = getMetaObjectOrThrow(value, interop);
                String metaName = getMetaName(metaObject, interop);

                // check if there's a specific type mapping available
                PolyglotTypeMappings.InternalTypeConverter internalConverter = lookupInternalTypeConverterNode.execute(metaName);
                if (internalConverter != null) {
                    return internalConverter.convertInternal(interop, value, meta, converterToEspresso);
                } else {
                    PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
                    if (converter != null) {
                        if (interop.isException(value)) {
                            return (StaticObject) converter.convert(StaticObject.createForeignException(getContext(), value, interop));
                        } else {
                            return (StaticObject) converter.convert(StaticObject.createForeign(getLanguage(), meta.java_lang_Object, value, interop));
                        }
                    }

                    // check if foreign exception
                    if (interop.isException(value)) {
                        return StaticObject.createForeignException(getContext(), value, interop);
                    }
                    // see if a generated proxy can be used for interface mapped types
                    WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, metaName, meta.java_lang_Object);
                    if (proxyKlass != null) {
                        return proxyKlass.createProxyInstance(value, getLanguage(), interop);
                    }
                    return StaticObject.createForeign(getLanguage(), meta.java_lang_Object, value, interop);
                }
            } catch (ClassCastException e) {
                errorProfile.enter();
                throw UnsupportedTypeException.create(new Object[]{value},
                                EspressoError.format("Could not cast foreign object to %s: due to: %s", meta.java_lang_Object.getNameAsString(), e.getMessage()));
            }
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
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
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
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
                        @Cached.Shared("value") @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
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
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
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

        private final ObjectKlass targetType;

        ToMappedInterface(ObjectKlass targetType) {
            this.targetType = targetType;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), targetType)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "isHostObject(getContext(), value)"
        })
        StaticObject doForeignInterface(Object value,
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached LookupProxyKlassNode lookupProxyKlassNode,
                        @Cached BranchProfile errorProfile) throws UnsupportedTypeException {
            try {
                Object metaObject = getMetaObjectOrThrow(value, interop);
                WrappedProxyKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, getMetaName(metaObject, interop), targetType);
                if (proxyKlass != null) {
                    return proxyKlass.createProxyInstance(value, getLanguage(), interop);
                }
                throw new ClassCastException();
            } catch (ClassCastException e) {
                errorProfile.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", targetType.getNameAsString(), e.getMessage()));
            }
        }
    }

    @NodeInfo(shortName = "custom type converter")
    public abstract static class ToMappedType extends ToReference {
        protected static final int LIMIT = 4;

        final ObjectKlass targetType;

        ToMappedType(ObjectKlass targetType) {
            this.targetType = targetType;
        }

        static InstanceOf createInstanceOf(Klass targetType) {
            return InstanceOf.create(targetType, false);
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached("createInstanceOf(targetType)") InstanceOf instanceOf) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass())) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "isHostObject(getContext(), value)"
        })
        StaticObject doForeignConverter(Object value,
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached LookupTypeConverterNode lookupTypeConverterNode,
                        @Cached BranchProfile errorProfile) throws UnsupportedTypeException {
            try {
                Object metaObject = getMetaObjectOrThrow(value, interop);
                String metaName = getMetaName(metaObject, interop);

                // check if there's a specific type mapping available
                PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
                if (converter != null) {
                    return (StaticObject) converter.convert(StaticObject.createForeign(getLanguage(), targetType, value, interop));
                }
                throw new ClassCastException();
            } catch (ClassCastException e) {
                errorProfile.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", targetType.getNameAsString(), e.getMessage()));
            }
        }
    }

    @NodeInfo(shortName = "custom type converter")
    public abstract static class ToMappedInternalType extends ToReference {
        protected static final int LIMIT = 4;

        final ObjectKlass targetType;

        ToMappedInternalType(ObjectKlass targetType) {
            this.targetType = targetType;
        }

        static InstanceOf createInstanceOf(Klass targetType) {
            return InstanceOf.create(targetType, false);
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached("createInstanceOf(targetType)") InstanceOf instanceOf) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass())) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!interop.isNull(value)",
                        "isHostObject(getContext(), value)"
        })
        StaticObject doForeignInternalConverter(Object value,
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached LookupInternalTypeConverterNode lookupTypeConverterNode,
                        @Cached ToReference.DynamicToReference converterToEspresso,
                        @Cached BranchProfile errorProfile,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            try {
                Object metaObject = getMetaObjectOrThrow(value, interop);
                String metaName = getMetaName(metaObject, interop);

                // check if there's a specific type mapping available
                PolyglotTypeMappings.InternalTypeConverter converter = lookupTypeConverterNode.execute(metaName);
                if (converter != null) {
                    return converter.convertInternal(interop, value, meta, converterToEspresso);
                }
                throw new ClassCastException();
            } catch (ClassCastException e) {
                errorProfile.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", targetType.getNameAsString(), e.getMessage()));
            }
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
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNumber(Object value,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            try {
                if (interop.fitsInByte(value)) {
                    return meta.boxByte(interop.asByte(value));
                }
                if (interop.fitsInShort(value)) {
                    return meta.boxShort(interop.asShort(value));
                }
                if (interop.fitsInInt(value)) {
                    return meta.boxInteger(interop.asInt(value));
                }
                if (interop.fitsInLong(value)) {
                    return meta.boxLong(interop.asLong(value));
                }
                if (interop.fitsInFloat(value)) {
                    return meta.boxFloat(interop.asFloat(value));
                }
                if (interop.fitsInDouble(value)) {
                    return meta.boxDouble(interop.asDouble(value));
                }
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_lang_Number.getTypeAsString());
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
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
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
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
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf) throws UnsupportedTypeException {
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
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
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
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getContext()") EspressoContext context) {
            return StaticObject.createForeignException(context, value, interop);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf) throws UnsupportedTypeException {
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
        StaticObject doforeign(Object value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getContext()") EspressoContext context) {
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
        StaticObject doforeign(Object value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getContext()") EspressoContext context) {
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
        StaticObject doforeign(Object value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getContext()") EspressoContext context) {
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_LocalDate)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_LocalDate.getTypeAsString());
        }

        @Specialization(guards = "interop.isDate(value)")
        StaticObject doForeignLocalDate(Object value,
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                LocalDate localDate = interop.asDate(value);
                return (StaticObject) meta.java_time_LocalDate_of.invokeDirect(null, localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isDate returns true, asDate must succeed.");
            }
        }

        @Specialization(guards = "!interop.isDate(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
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
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                LocalTime localTime = interop.asTime(value);
                return (StaticObject) meta.java_time_LocalTime_of.invokeDirect(null, localTime.getHour(), localTime.getMinute(), localTime.getSecond(), localTime.getNano());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isTime returns true, asTime must succeed.");
            }
        }

        @Specialization(guards = "!interop.isTime(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
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
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                LocalDate localDate = interop.asDate(value);
                LocalTime localTime = interop.asTime(value);
                StaticObject guestLocalDate = (StaticObject) meta.java_time_LocalDate_of.invokeDirect(null, localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
                StaticObject guestLocalTime = (StaticObject) meta.java_time_LocalTime_of.invokeDirect(null, localTime.getHour(), localTime.getMinute(), localTime.getSecond(), localTime.getNano());

                return (StaticObject) meta.java_time_LocalDateTime_of.invokeDirect(null, guestLocalDate, guestLocalTime);
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
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
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                Instant instant = interop.asInstant(value);
                ZoneId zoneId = interop.asTimeZone(value);
                StaticObject guestInstant = (StaticObject) meta.java_time_Instant_ofEpochSecond.invokeDirect(null, instant.getEpochSecond(), (long) instant.getNano());
                StaticObject guestZoneID = (StaticObject) meta.java_time_ZoneId_of.invokeDirect(null, meta.toGuestString(zoneId.getId()));
                return (StaticObject) meta.java_time_ZonedDateTime_ofInstant.invokeDirect(null, guestInstant, guestZoneID);
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_Instant)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_Instant.getTypeAsString());
        }

        @Specialization(guards = "interop.isInstant(value)")
        StaticObject doForeignInstant(Object value,
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                Instant instant = interop.asInstant(value);
                return (StaticObject) meta.java_time_Instant_ofEpochSecond.invokeDirect(null, instant.getEpochSecond(), (long) instant.getNano());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isInstant returns true, asInstant must succeed.");
            }
        }

        @Specialization(guards = "!interop.isInstant(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_Duration)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_Duration.getTypeAsString());
        }

        @Specialization(guards = "interop.isDuration(value)")
        StaticObject doForeignDuration(Object value,
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_time_ZoneId)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_time_ZoneId.getTypeAsString());
        }

        @Specialization(guards = "interop.isTimeZone(value)")
        StaticObject doForeignZoneId(Object value, @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                ZoneId zoneId = interop.asTimeZone(value);
                return (StaticObject) meta.java_time_ZoneId_of.invokeDirect(null, meta.toGuestString(zoneId.getId()));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isZoneId returns true, asTimeZone must succeed.");
            }
        }

        @Specialization(guards = "!interop.isTimeZone(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
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
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value,
                        @Cached InstanceOf.Dynamic instanceOf,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), meta.java_util_Date)) {
                return value; // pass through, NULL coercion not needed.
            }
            throw UnsupportedTypeException.create(new Object[]{value}, meta.java_util_Date.getTypeAsString());
        }

        @Specialization(guards = "interop.isInstant(value)")
        StaticObject doForeignDate(Object value,
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getMeta()") Meta meta) {
            try {
                Instant instant = interop.asInstant(value);
                StaticObject guestInstant = (StaticObject) meta.java_time_Instant_ofEpochSecond.invokeDirect(null, instant.getEpochSecond(), (long) instant.getNano());
                return (StaticObject) meta.java_util_Date_from.invokeDirect(null, guestInstant);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isInstant returns true, asInstant must succeed.");
            }
        }

        @Specialization(guards = "!interop.isInstant(value)")
        StaticObject doUnsupported(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, getMeta().java_util_Date.getTypeAsString());
        }
    }

    @NodeInfo(shortName = "unknown type mapping")
    public abstract static class ToUnknown extends ToReference {
        protected static final int LIMIT = 4;

        private final ObjectKlass targetType;

        ToUnknown(ObjectKlass targetType) {
            this.targetType = targetType;
        }

        @Specialization(guards = {
                        "interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignNull(Object value,
                        @SuppressWarnings("unused") @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
        }

        @Specialization
        public StaticObject doEspresso(StaticObject value) throws UnsupportedTypeException {
            if (StaticObject.isNull(value) || targetType.isAssignableFrom(value.getKlass())) {
                return value;
            }
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
        }

        @Specialization(guards = {
                        "!interop.isNull(value)",
                        "!isStaticObject(value)"
        })
        StaticObject doForeignWrapper(Object value,
                        @Cached.Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context,
                        @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
            try {
                if (targetType.isInterface()) {
                    throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
                }
                checkHasAllFieldsOrThrow(value, targetType, interop, meta);
                return StaticObject.createForeign(getLanguage(), targetType, value, interop);
            } catch (ClassCastException ex) {
                throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
            }
        }

        @Specialization
        public StaticObject doUnknown(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
        }
    }

    static boolean isStaticObject(Object obj) {
        return obj instanceof StaticObject;
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
