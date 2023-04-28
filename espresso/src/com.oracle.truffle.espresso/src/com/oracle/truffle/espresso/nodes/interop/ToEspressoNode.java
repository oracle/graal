/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.bytecodes.InstanceOf;
import com.oracle.truffle.espresso.nodes.bytecodes.InstanceOfFactory;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

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
                    return new ToReference.ToVoid();
            }
        }
        if (targetType.getMeta().isBoxed(targetType)) {
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
            if (isTypeMappingEnabled(targetType)) {
                return ToReferenceFactory.ToMappedInterfaceNodeGen.create((ObjectKlass) targetType);
            } else if (targetType == meta.java_lang_CharSequence) {
                return ToReferenceFactory.ToCharSequenceNodeGen.create();
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
        } else {
            return ToReferenceFactory.ToUnknownNodeGen.create((ObjectKlass) targetType);
        }
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
                    return new ToReference.ToVoid();
            }
        }
        if (targetType.getMeta().isBoxed(targetType)) {
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
    public abstract static class DynamicToEspresso extends EspressoNode {
        protected static final int LIMIT = 8;

        public abstract Object execute(Object value, Klass targetType) throws UnsupportedTypeException;

        protected static ToEspressoNode createToEspressoNode(Klass targetType) {
            return ToEspressoNode.createToEspresso(targetType, targetType.getMeta());
        }

        protected static ToEspressoNode getUncachedToEspressoNode(Klass targetType) {
            return ToEspressoNode.getUncachedToEspresso(targetType, targetType.getMeta());
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
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
            if (interop.isNull(value)) {
                return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
            }
            if (value instanceof StaticObject) {
                StaticObject staticObject = (StaticObject) value;
                InstanceOf.Dynamic instanceOf = InstanceOfFactory.DynamicNodeGen.getUncached();
                if (StaticObject.isNull(staticObject) || instanceOf.execute(staticObject.getKlass(), targetType)) {
                    return value; // pass through, NULL coercion not needed.
                }
                throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to ", targetType.getTypeAsString()));
            }
            if (targetType.isInterface()) {
                if (isTypeMappingEnabled(targetType)) {
                    try {
                        Object metaObject = getMetaObjectOrThrow(value, interop);
                        ObjectKlass proxyKlass = LookupProxyKlassNodeGen.getUncached().execute(metaObject, getMetaName(metaObject, interop), targetType);
                        if (proxyKlass != null) {
                            targetType.safeInitialize();
                            return StaticObject.createForeign(getLanguage(), proxyKlass, value, interop);
                        }
                        throw new ClassCastException();
                    } catch (ClassCastException e) {
                        throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", targetType.getNameAsString(), e.getMessage()));
                    }
                }
            }
            if (targetType.isArray()) {
                if (targetType == getMeta()._byte_array) {
                    if (interop.hasBufferElements(value) && !isHostString(value)) {
                        return StaticObject.createForeign(EspressoLanguage.get(this), getMeta()._byte_array, value, interop);
                    }
                    throw UnsupportedTypeException.create(new Object[]{value}, getMeta()._byte_array.getTypeAsString());
                } else {
                    if (interop.hasArrayElements(value) && !isHostString(value)) {
                        return StaticObject.createForeign(EspressoLanguage.get(this), targetType, value, interop);
                    }
                    throw UnsupportedTypeException.create(new Object[]{value}, targetType.getTypeAsString());
                }
            }
            if (isTypeConverterEnabled(targetType)) {
                try {
                    Object metaObject = getMetaObjectOrThrow(value, interop);
                    String metaName = getMetaName(metaObject, interop);

                    // check if there's a specific type mapping available
                    PolyglotTypeMappings.TypeConverter converter = LookupTypeConverterNodeGen.getUncached().execute(metaName);
                    if (converter != null) {
                        return converter.convert(StaticObject.createForeign(getLanguage(), targetType, value, interop));
                    }
                    throw new ClassCastException();
                } catch (ClassCastException e) {
                    throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", targetType.getNameAsString(), e.getMessage()));
                }
            }
            try {
                return getUncachedToEspressoNode(targetType).execute(value);
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

    public static boolean isHostString(Object obj) {
        return obj instanceof String;
    }

    public static boolean isTypeMappingEnabled(Klass klass) {
        EspressoContext context = klass.getContext();
        return context.getPolyglotInterfaceMappings().hasMappings() && context.getPolyglotInterfaceMappings().mapInterfaceName(klass) != null;
    }

    public static boolean isTypeConverterEnabled(Klass klass) {
        EspressoContext context = klass.getContext();
        return context.getPolyglotInterfaceMappings().hasMappings() && context.getPolyglotInterfaceMappings().mapTypeConversion(klass) != null;
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

    @CompilerDirectives.TruffleBoundary
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
