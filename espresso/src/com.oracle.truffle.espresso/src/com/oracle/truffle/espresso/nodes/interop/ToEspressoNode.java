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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.PrimitiveKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.bytecodes.InitCheck;
import com.oracle.truffle.espresso.nodes.bytecodes.InstanceOf;
import com.oracle.truffle.espresso.nodes.helper.TypeCheckNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Handles conversions of (potentially) foreign objects to Espresso types.
 */
@GenerateUncached
public abstract class ToEspressoNode extends EspressoNode {

    public abstract Object execute(Object value, Klass targetType) throws UnsupportedTypeException;

    public static final int LIMIT = 2;

    // region Specialization predicates

    static boolean isStaticObject(Object obj) {
        return obj instanceof StaticObject;
    }

    static boolean isHostString(Object object) {
        return object instanceof String;
    }

    static boolean isString(Meta meta, Klass klass) {
        return meta.java_lang_String.equals(klass);
    }

    static boolean isStringCompatible(Meta meta, Klass klass) {
        // Accept String superclasses and superinterfaces.
        return klass.isAssignableFrom(meta.java_lang_String);
    }

    static boolean isZonedDateTime(Meta meta, Klass klass) {
        return meta.java_time_ZonedDateTime.equals(klass);
    }

    static boolean isLocalDate(Meta meta, Klass klass) {
        return meta.java_time_LocalDate.equals(klass);
    }

    static boolean isLocalTime(Meta meta, Klass klass) {
        return meta.java_time_LocalTime.equals(klass);
    }

    static boolean isLocalDateTime(Meta meta, Klass klass) {
        return meta.java_time_LocalDateTime.equals(klass);
    }

    static boolean isInstant(Meta meta, Klass klass) {
        return meta.java_time_Instant.equals(klass);
    }

    static boolean isDate(Meta meta, Klass klass) {
        return meta.java_util_Date.equals(klass);
    }

    static boolean isZoneId(Meta meta, Klass klass) {
        return meta.java_time_ZoneId.equals(klass);
    }

    static boolean isDuration(Meta meta, Klass klass) {
        return meta.java_time_Duration.equals(klass);
    }

    static boolean isByteArray(Meta meta, Klass klass) {
        return meta._byte_array.equals(klass);
    }

    static boolean isPrimitiveKlass(Klass klass) {
        return klass instanceof PrimitiveKlass;
    }

    static boolean isForeignException(Meta meta, Klass klass) {
        return meta.polyglot != null /* polyglot enabled */ && meta.polyglot.ForeignException.equals(klass);
    }

    static boolean isEspressoException(Object object) {
        return object instanceof EspressoException;
    }

    static boolean isBoxedPrimitive(Object obj) {
        return obj instanceof Number || obj instanceof Character || obj instanceof Boolean;
    }

    static boolean isIntegerCompatible(Klass klass) {
        return klass.isAssignableFrom(klass.getMeta().java_lang_Integer);
    }

    static boolean isBooleanCompatible(Klass klass) {
        return klass.isAssignableFrom(klass.getMeta().java_lang_Boolean);
    }

    static boolean isByteCompatible(Klass klass) {
        return klass.isAssignableFrom(klass.getMeta().java_lang_Byte);
    }

    static boolean isShortCompatible(Klass klass) {
        return klass.isAssignableFrom(klass.getMeta().java_lang_Short);
    }

    static boolean isCharacterCompatible(Klass klass) {
        return klass.isAssignableFrom(klass.getMeta().java_lang_Character);
    }

    static boolean isLongCompatible(Klass klass) {
        return klass.isAssignableFrom(klass.getMeta().java_lang_Long);
    }

    static boolean isFloatCompatible(Klass klass) {
        return klass.isAssignableFrom(klass.getMeta().java_lang_Float);
    }

    static boolean isDoubleCompatible(Klass klass) {
        return klass.isAssignableFrom(klass.getMeta().java_lang_Double);
    }

    /*
     * If this method returns true for a specialization, the following guards are known to be false:
     * 1. isEspressoException(value) 2. isStaticObject(value)
     */
    static boolean isTypeMappingEnabled(Klass klass, EspressoContext context, Object value) {
        return klass.getContext().explicitTypeMappingsEnabled() && isHostObject(context, value);
    }

    static boolean isHostObject(EspressoContext context, Object value) {
        return context.getEnv().isHostObject(value);
    }

    // endregion Specialization predicates

    @Specialization(guards = "!isPrimitiveKlass(klass)")
    Object doEspresso(StaticObject value, Klass klass,
                    @Cached BranchProfile exceptionProfile,
                    @Cached InstanceOf.Dynamic instanceOf) throws UnsupportedTypeException {
        if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), klass)) {
            return value; // pass through, NULL coercion not needed.
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to ", klass.getTypeAsString()));
    }

    @Specialization
    Object doPrimitive(Object value,
                    PrimitiveKlass primitiveKlass,
                    @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached BranchProfile exceptionProfile) throws UnsupportedTypeException {
        try {
            // @formatter:off
            switch (primitiveKlass.getJavaKind()) {
                case Boolean : return interop.asBoolean(value);
                case Byte    : return interop.asByte(value);
                case Short   : return interop.asShort(value);
                case Int     : return interop.asInt(value);
                case Float   : return interop.asFloat(value);
                case Long    : return interop.asLong(value);
                case Double  : return interop.asDouble(value);
                case Char: {
                    String str = interop.asString(value);
                    if (str.length() == 1) {
                        return str.charAt(0);
                    }
                    break;
                }
                case Void:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere("Unexpected cast to void");
            }
            // @formatter:on
        } catch (UnsupportedMessageException e) {
            // fall-through
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", interop.toDisplayString(value), " to ", primitiveKlass.getPrimitiveJavaKind().getJavaName()));
    }

    @Specialization
    Object doEspressoException(EspressoException value, ObjectKlass klass) throws UnsupportedTypeException {
        return execute(value.getGuestException(), klass);
    }

    @Specialization(guards = "isStringCompatible(meta, klass)")
    Object doHostString(String value, @SuppressWarnings("unused") ObjectKlass klass,
                    @Bind("getMeta()") Meta meta) {
        return meta.toGuestString(value);
    }

    @Specialization(guards = {
                    "!isStaticObject(value)",
                    "interop.isNull(value)",
                    "!isPrimitiveKlass(klass)"
    })
    Object doForeignNull(Object value, @SuppressWarnings("unused") Klass klass,
                    @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
        return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
    }

    @Specialization(guards = {
                    "isString(meta, klass)",
                    "!isStaticObject(value)",
                    "interop.isString(value)",
                    "!isHostString(value)",
                    // !interop.isNull(value), // redundant
                    // "!isEspressoException(value)", // redundant
    })
    Object doForeignString(Object value, @SuppressWarnings("unused") ObjectKlass klass,
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
                    "isZonedDateTime(meta, klass)",
                    "!isStaticObject(value)",
                    "interop.isInstant(value)",
                    "interop.isTimeZone(value)",
    })
    Object doForeignDateTime(Object value, @SuppressWarnings("unused") ObjectKlass klass,
                    @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Bind("getMeta()") Meta meta) {
        StaticObject guestInstant = (StaticObject) doForeignInstant(value, meta.java_time_Instant, interop, meta);
        StaticObject guestZoneID = (StaticObject) doForeignZoneId(value, meta.java_time_ZoneId, interop, meta);

        return meta.java_time_ZonedDateTime_ofInstant.invokeDirect(null, guestInstant, guestZoneID);
    }

    @Specialization(guards = {
                    "isLocalDate(meta, klass)",
                    "!isStaticObject(value)",
                    "interop.isDate(value)",
    })
    Object doForeignLocalDate(Object value, @SuppressWarnings("unused") ObjectKlass klass,
                    @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Bind("getMeta()") Meta meta) {
        try {
            LocalDate localDate = interop.asDate(value);
            return meta.java_time_LocalDate_of.invokeDirect(null, localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Contract violation: if isLocalDate returns true, asDate must succeed.");
        }
    }

    @Specialization(guards = {
                    "isLocalTime(meta, klass)",
                    "!isStaticObject(value)",
                    "interop.isTime(value)",
    })
    Object doForeignLocalTime(Object value, @SuppressWarnings("unused") ObjectKlass klass,
                    @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Bind("getMeta()") Meta meta) {
        try {
            LocalTime localTime = interop.asTime(value);
            return meta.java_time_LocalTime_of.invokeDirect(null, localTime.getHour(), localTime.getMinute(), localTime.getSecond(), localTime.getNano());
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Contract violation: if isLocalTime returns true, asTime must succeed.");
        }
    }

    @Specialization(guards = {
                    "isLocalDateTime(meta, klass)",
                    "!isStaticObject(value)",
                    "interop.isTime(value)",
                    "interop.isDate(value)",
    })
    Object doForeignLocalDateTime(Object value, @SuppressWarnings("unused") ObjectKlass klass,
                    @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Bind("getMeta()") Meta meta) {
        return meta.java_time_LocalDateTime_of.invokeDirect(null,
                        doForeignLocalDate(value, meta.java_time_LocalDate, interop, meta),
                        doForeignLocalTime(value, meta.java_time_LocalTime, interop, meta));
    }

    @Specialization(guards = {
                    "isInstant(meta, klass)",
                    "!isStaticObject(value)",
                    "interop.isInstant(value)",
    })
    Object doForeignInstant(Object value, @SuppressWarnings("unused") ObjectKlass klass,
                    @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Bind("getMeta()") Meta meta) {
        try {
            Instant instant = interop.asInstant(value);
            return meta.java_time_Instant_ofEpochSecond.invokeDirect(null, instant.getEpochSecond(), (long) instant.getNano());
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Contract violation: if isInstant returns true, asInstant must succeed.");
        }
    }

    @Specialization(guards = {
                    "isDate(meta, klass)",
                    "!isStaticObject(value)",
                    "interop.isInstant(value)",
    })
    Object doForeignDate(Object value, @SuppressWarnings("unused") ObjectKlass klass,
                    @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Bind("getMeta()") Meta meta) {
        return meta.java_util_Date_from.invokeDirect(null, doForeignInstant(value, meta.java_time_Instant, interop, meta));
    }

    @Specialization(guards = {
                    "isZoneId(meta, klass)",
                    "!isStaticObject(value)",
                    "interop.isTimeZone(value)",
    })
    Object doForeignZoneId(Object value, @SuppressWarnings("unused") ObjectKlass klass,
                    @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Bind("getMeta()") Meta meta) {
        try {
            ZoneId zoneId = interop.asTimeZone(value);
            return meta.java_time_ZoneId_of.invokeDirect(null, meta.toGuestString(zoneId.getId()));
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Contract violation: if isZoneId returns true, asTimeZone must succeed.");
        }
    }

    @Specialization(guards = {
                    "isDuration(meta, klass)",
                    "!isStaticObject(value)",
                    "interop.isDuration(value)",
    })
    Object doForeignDuration(Object value, @SuppressWarnings("unused") ObjectKlass klass,
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

    @Specialization(guards = {
                    "isForeignException(context.getMeta(), klass)",
                    "!isStaticObject(value)",
                    "interop.isException(value)",
                    "!isEspressoException(value)",
                    // !interop.isNull(value), // redundant
                    // "!isHostString(value)", // redundant
    })
    Object doForeignException(Object value, ObjectKlass klass,
                    @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached InitCheck initCheck,
                    @Bind("getContext()") EspressoContext context) {
        initCheck.execute(klass);
        return StaticObject.createForeignException(context, value, interop);
    }

    @Specialization(guards = {
                    "interop.hasArrayElements(value)",
                    "!isStaticObject(value)",
                    "!interop.isNull(value)",
                    "!isHostString(value)",
                    "!isEspressoException(value)"
    })
    Object doForeignArray(Object value, ArrayKlass klass,
                    @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
        return StaticObject.createForeign(EspressoLanguage.get(this), klass, value, interop);
    }

    @Specialization(guards = {
                    "isByteArray(meta, klass)",
                    "!isStaticObject(value)",
                    "interop.hasBufferElements(value)",
                    "!interop.isNull(value)",
                    "!isHostString(value)",
                    "!isEspressoException(value)",
    })
    Object doForeignBuffer(Object value, ArrayKlass klass,
                    @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @SuppressWarnings("unused") @Bind("getMeta()") Meta meta) {
        return StaticObject.createForeign(EspressoLanguage.get(this), klass, value, interop);
    }

    @Specialization(guards = {"isIntegerCompatible(klass)"})
    Object doHostInteger(Integer value, @SuppressWarnings("unused") ObjectKlass klass) {
        return getMeta().boxInteger(value);
    }

    @Specialization(guards = {"isBooleanCompatible(klass)"})
    Object doHostBoolean(Boolean value, @SuppressWarnings("unused") ObjectKlass klass) {
        return getMeta().boxBoolean(value);
    }

    @Specialization(guards = {"isByteCompatible(klass)"})
    Object doHostByte(Byte value, @SuppressWarnings("unused") ObjectKlass klass) {
        return getMeta().boxByte(value);
    }

    @Specialization(guards = {"isCharacterCompatible(klass)"})
    Object doHostChar(Character value, @SuppressWarnings("unused") ObjectKlass klass) {
        return getMeta().boxCharacter(value);
    }

    @Specialization(guards = {"isShortCompatible(klass)"})
    Object doHostShort(Short value, @SuppressWarnings("unused") ObjectKlass klass) {
        return getMeta().boxShort(value);
    }

    @Specialization(guards = {"isLongCompatible(klass)"})
    Object doHostLong(Long value, @SuppressWarnings("unused") ObjectKlass klass) {
        return getMeta().boxLong(value);
    }

    @Specialization(guards = {"isFloatCompatible(klass)"})
    Object doHostFloat(Float value, @SuppressWarnings("unused") ObjectKlass klass) {
        return getMeta().boxFloat(value);
    }

    @Specialization(guards = {"isDoubleCompatible(klass)"})
    Object doHostDouble(Double value, @SuppressWarnings("unused") ObjectKlass klass) {
        return getMeta().boxDouble(value);
    }

    @Specialization(guards = {
                    "!isTypeMappingEnabled(klass, getContext(), value)",
                    "!isStaticObject(value)",
                    "!interop.isNull(value)",
                    "!isHostString(value)",
                    "!isEspressoException(value)",
                    "!isForeignException(meta, klass)",
                    "!klass.isAbstract()",
                    "!isString(meta, klass)",
                    "!isBoxedPrimitive(value)"
    })
    Object doForeignConcreteClassWrapper(Object value, ObjectKlass klass,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached BranchProfile errorProfile,
                    @Cached InitCheck initCheck,
                    @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
        try {
            checkHasAllFieldsOrThrow(value, klass, interop, meta);
        } catch (ClassCastException e) {
            errorProfile.enter();
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: due to: %s", klass.getNameAsString(), e.getMessage()));
        }
        initCheck.execute(klass);
        return StaticObject.createForeign(getLanguage(), klass, value, interop);
    }

    @Specialization(guards = {
                    "isTypeMappingEnabled(klass, getContext(), value)",
                    "!interop.isNull(value)",
                    "!isHostString(value)",
                    "!isForeignException(meta, klass)",
                    "!klass.isAbstract()",
                    "!isString(meta, klass)",
                    "!isBoxedPrimitive(value)",
                    // "!isStaticObject(value)", // redundant
                    // "!isEspressoException(value)", // redundant
    })
    Object doForeignClassProxy(Object value, ObjectKlass klass,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached LookupProxyKlassNode lookupProxyKlassNode,
                    @Cached LookupTypeConverterNode lookupTypeConverterNode,
                    @Cached TypeCheckNode typeCheckNode,
                    @Cached BranchProfile errorProfile,
                    @SuppressWarnings("unused") @Bind("getMeta()") Meta meta) throws UnsupportedTypeException {
        try {
            Object metaObject = getMetaObjectOrThrow(value, interop);
            String metaName = getMetaName(metaObject, interop);

            // check if there's a specific type mapping available
            PolyglotTypeMappings.TypeConverter converter = lookupTypeConverterNode.execute(metaName);
            if (converter != null) {
                StaticObject converted = (StaticObject) converter.convert(StaticObject.createForeign(getLanguage(), klass, value, interop));
                if (StaticObject.isNull(converted) || typeCheckNode.executeTypeCheck(klass, converted.getKlass())) {
                    return converted;
                } else {
                    throw new ClassCastException();
                }
            } else {
                if (klass == meta.java_lang_Object) {
                    // check if foreign exception
                    if (interop.isException(value)) {
                        return StaticObject.createForeignException(klass.getContext(), value, interop);
                    }
                    // see if a generated proxy can be used for interface mapped types
                    ObjectKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, metaName, klass);
                    if (proxyKlass != null) {
                        return StaticObject.createForeign(getLanguage(), proxyKlass, value, interop);
                    }
                }
                checkHasAllFieldsOrThrow(value, klass, interop, meta);
                return StaticObject.createForeign(getLanguage(), klass, value, interop);
            }
        } catch (ClassCastException e) {
            errorProfile.enter();
            throw UnsupportedTypeException.create(new java.lang.Object[]{value}, EspressoError.format("Could not cast foreign object to %s: due to: %s", klass.getNameAsString(), e.getMessage()));
        }
    }

    @Specialization(guards = {"!isStaticObject(value)", "!interop.isNull(value)", "klass.isInterface()", "isHostObject(getContext(), value)"})
    Object doForeignInterface(Object value, ObjectKlass klass,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached InitCheck initCheck,
                    @Cached LookupProxyKlassNode lookupProxyKlassNode,
                    @Cached BranchProfile errorProfile) throws UnsupportedTypeException {
        try {
            if (getContext().interfaceMappingsEnabled()) {
                Object metaObject = getMetaObjectOrThrow(value, interop);
                ObjectKlass proxyKlass = lookupProxyKlassNode.execute(metaObject, getMetaName(metaObject, interop), klass);
                if (proxyKlass != null) {
                    initCheck.execute(klass);
                    return StaticObject.createForeign(getLanguage(), proxyKlass, value, interop);
                }
            }
            throw new ClassCastException();
        } catch (ClassCastException e) {
            errorProfile.enter();
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", klass.getNameAsString(), e.getMessage()));
        }
    }

    private static Object getMetaObjectOrThrow(Object value, InteropLibrary interop) throws ClassCastException {
        try {
            return interop.getMetaObject(value);
        } catch (UnsupportedMessageException e) {
            throw new ClassCastException("Could not lookup meta object");
        }
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

    @Fallback
    Object doUnsupportedType(Object value, Klass klass) throws UnsupportedTypeException {
        throw UnsupportedTypeException.create(new Object[]{value}, klass.getTypeAsString());
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
