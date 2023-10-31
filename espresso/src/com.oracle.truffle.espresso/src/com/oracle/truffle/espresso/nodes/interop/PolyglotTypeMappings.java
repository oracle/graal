/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.espresso.impl.ArrayKlass;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.options.OptionMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public class PolyglotTypeMappings {

    private static final String GUEST_TYPE_CONVERSION_INTERFACE = "com.oracle.truffle.espresso.polyglot.GuestTypeConversion";
    private final boolean hasInterfaceMappings;
    private final List<String> interfaceMappings;
    private UnmodifiableEconomicMap<String, ObjectKlass> mappedInterfaces;
    private final OptionMap<String> typeConverters;
    private UnmodifiableEconomicMap<String, TypeConverter> typeConverterFunctions;
    private UnmodifiableEconomicMap<String, ObjectKlass> espressoForeignCollections;
    private UnmodifiableEconomicMap<String, InternalTypeConverter> internalTypeConverterFunctions;
    private final boolean builtinCollections;

    public PolyglotTypeMappings(List<String> interfaceMappings, OptionMap<String> typeConverters, boolean builtinCollections) {
        this.interfaceMappings = interfaceMappings;
        this.hasInterfaceMappings = !interfaceMappings.isEmpty();
        this.typeConverters = typeConverters;
        this.builtinCollections = builtinCollections;
    }

    @TruffleBoundary
    public void resolve(EspressoContext context) {
        assert interfaceMappings != null;

        // resolve interface mappings
        if (hasInterfaceMappings) {
            EconomicMap<String, ObjectKlass> temp = EconomicMap.create(interfaceMappings.size());
            StaticObject bindingsLoader = context.getBindingsLoader();

            for (String mapping : interfaceMappings) {
                Klass parent = context.getMeta().loadKlassOrNull(context.getTypes().fromClassGetName(mapping), bindingsLoader, StaticObject.NULL);
                if (parent != null && parent.isInterface()) {
                    temp.put(mapping, (ObjectKlass) parent);
                    parent.typeConversionState = Klass.INTERFACE_MAPPED;
                } else {
                    throw new IllegalStateException("invalid interface type mapping specified: " + mapping);
                }
            }
            mappedInterfaces = EconomicMap.create(temp);
        }
        // resolve type converters
        Set<Map.Entry<String, String>> converters = typeConverters.entrySet();
        if (!converters.isEmpty()) {
            EconomicMap<String, TypeConverter> temp = EconomicMap.create(converters.size());
            StaticObject bindingsLoader = context.getBindingsLoader();

            Symbol<Symbol.Name> name = Symbol.Name.toGuest;
            Symbol<Symbol.Signature> desc = Symbol.Signature.Object_Object;

            // load the GuestTypeConversion interface for type checking
            Klass conversionInterface = context.getMeta().loadKlassOrNull(context.getTypes().fromClassGetName(GUEST_TYPE_CONVERSION_INTERFACE), bindingsLoader, StaticObject.NULL);
            if (conversionInterface == null) {
                throw new IllegalStateException("Missing expected guest type conversion interface in polyglot.jar");
            }

            for (Map.Entry<String, String> entry : converters) {
                String type = entry.getKey();

                String conversionHandler = entry.getValue();
                ObjectKlass conversionKlass = (ObjectKlass) context.getMeta().loadKlassOrNull(context.getTypes().fromClassGetName(conversionHandler), bindingsLoader, StaticObject.NULL);
                if (conversionKlass == null) {
                    throw new IllegalStateException("Class not found for polyglot type conversion handler: " + conversionHandler);
                }
                // make sure the conversion class implements GuestTypeConversion interface
                if (!conversionInterface.isAssignableFrom(conversionKlass)) {
                    throw new IllegalStateException("ConversionHandler does not implement the polyglot type conversion interface: " + GUEST_TYPE_CONVERSION_INTERFACE);
                }
                Method conversionMethod = conversionKlass.requireDeclaredMethod(name, desc);
                StaticObject conversionReceiver = context.getAllocator().createNew(conversionKlass);
                temp.put(type, new TypeConverterImpl(conversionReceiver, DirectCallNode.create(conversionMethod.getCallTarget())));
            }
            typeConverterFunctions = EconomicMap.create(temp);
        }
        addInternalConverters(context.getMeta());
        if (builtinCollections) {
            EconomicMap<String, ObjectKlass> temp = EconomicMap.create(6);
            addInternalEspressoCollections(temp, context.getMeta());
            espressoForeignCollections = EconomicMap.create(temp);
        }
    }

    private void addInternalConverters(Meta meta) {
        EconomicMap<String, InternalTypeConverter> converters = EconomicMap.create(2);

        String current = "java.util.Optional";
        if (!isCustomMapped(current)) {
            converters.put(current, new OptionalTypeConverter());
        } else {
            warn(current, meta.getContext());
        }
        current = "java.math.BigDecimal";
        if (!isCustomMapped(current)) {
            converters.put(current, new BigDecimalTypeConverter());
        } else {
            warn(current, meta.getContext());
        }
        converters.put("byte[]", new PrimitiveArrayConverter(meta._byte_array));
        converters.put("boolean[]", new PrimitiveArrayConverter(meta._boolean_array));
        converters.put("char[]", new PrimitiveArrayConverter(meta._char_array));
        converters.put("short[]", new PrimitiveArrayConverter(meta._short_array));
        converters.put("int[]", new PrimitiveArrayConverter(meta._int_array));
        converters.put("long[]", new PrimitiveArrayConverter(meta._long_array));
        converters.put("float[]", new PrimitiveArrayConverter(meta._float_array));
        converters.put("double[]", new PrimitiveArrayConverter(meta._double_array));
        internalTypeConverterFunctions = EconomicMap.create(converters);
    }

    private void addInternalEspressoCollections(EconomicMap<String, ObjectKlass> map, Meta meta) {
        String current = "java.lang.Iterable";
        if (!isCustomMapped(current)) {
            map.put(current, meta.polyglot.EspressoForeignIterable);
        } else {
            warn(current, meta.getContext());
        }
        current = "java.util.List";
        if (!isCustomMapped(current)) {
            map.put(current, meta.polyglot.EspressoForeignList);
        } else {
            warn(current, meta.getContext());
        }
        current = "java.util.Collection";
        if (!isCustomMapped(current)) {
            map.put(current, meta.polyglot.EspressoForeignCollection);
        } else {
            warn(current, meta.getContext());
        }
        current = "java.util.Iterator";
        if (!isCustomMapped(current)) {
            map.put(current, meta.polyglot.EspressoForeignIterator);
        } else {
            warn(current, meta.getContext());
        }
        current = "java.util.Map";
        if (!isCustomMapped(current)) {
            map.put(current, meta.polyglot.EspressoForeignMap);
        } else {
            warn(current, meta.getContext());
        }
        current = "java.util.Set";
        if (!isCustomMapped(current)) {
            map.put(current, meta.polyglot.EspressoForeignSet);
        } else {
            warn(current, meta.getContext());
        }
    }

    private static void warn(String mapping, EspressoContext context) {
        context.getVM().getLogger().warning("Custom type mapping is used where there's a builtin type conversion available. Remove the [" + mapping + "] to enable the builtin converter.");
    }

    private boolean isCustomMapped(String mapping) {
        if (interfaceMappings != null && interfaceMappings.contains(mapping)) {
            return true;
        }
        if (typeConverterFunctions != null && typeConverterFunctions.containsKey(mapping)) {
            return true;
        }
        return false;
    }

    public ObjectKlass mapEspressoForeignCollection(String metaName) {
        return espressoForeignCollections.get(metaName);
    }

    public ObjectKlass mapEspressoForeignCollection(Klass klass) {
        CompilerAsserts.neverPartOfCompilation();
        if (espressoForeignCollections == null) {
            return null;
        }
        String name = klass.getNameAsString().replace('/', '.');
        return mapEspressoForeignCollection(name);
    }

    @TruffleBoundary
    public ObjectKlass mapInterfaceName(String name) {
        assert mappedInterfaces != null;
        return mappedInterfaces.get(name);
    }

    public boolean hasMappings() {
        return hasInterfaceMappings || typeConverterFunctions != null || internalTypeConverterFunctions != null;
    }

    public boolean hasInterfaceMappings() {
        return hasInterfaceMappings;
    }

    @TruffleBoundary
    public TypeConverter mapTypeConversion(String metaName) {
        if (typeConverterFunctions == null) {
            return null;
        }
        return typeConverterFunctions.get(metaName);
    }

    public TypeConverter mapTypeConversion(Klass klass) {
        CompilerAsserts.neverPartOfCompilation();
        if (typeConverterFunctions == null) {
            return null;
        }
        String name = klass.getNameAsString().replace('/', '.');
        return mapTypeConversion(name);
    }

    @TruffleBoundary
    public InternalTypeConverter mapInternalTypeConversion(String metaName) {
        if (internalTypeConverterFunctions == null) {
            return null;
        }
        return internalTypeConverterFunctions.get(metaName);
    }

    public InternalTypeConverter mapInternalTypeConversion(Klass klass) {
        CompilerAsserts.neverPartOfCompilation();
        if (internalTypeConverterFunctions == null) {
            return null;
        }
        String name = klass.getNameAsString().replace('/', '.');
        return mapInternalTypeConversion(name);
    }

    public interface TypeConverter {
        Object convert(StaticObject foreign);
    }

    public interface InternalTypeConverter {
        StaticObject convertInternal(InteropLibrary interop, Object value, Meta meta, ToReference.DynamicToReference toEspresso);
    }

    public class TypeConverterImpl implements TypeConverter {
        private final Object receiver;
        private final DirectCallNode callNode;

        TypeConverterImpl(Object receiver, DirectCallNode callNode) {
            this.receiver = receiver;
            this.callNode = callNode;
        }

        public Object convert(StaticObject foreign) {
            return callNode.call(receiver, foreign);
        }
    }

    public final class OptionalTypeConverter implements InternalTypeConverter {

        @Override
        public StaticObject convertInternal(InteropLibrary interop, Object value, Meta meta, ToReference.DynamicToReference toEspresso) {
            try {
                Object result = interop.invokeMember(value, "orElse", StaticObject.NULL);
                if (interop.isNull(result)) {
                    return (StaticObject) meta.java_util_Optional_EMPTY.get(meta.java_util_Optional.getStatics());
                } else {
                    StaticObject guestOptional = toEspresso.getAllocator().createNew(meta.java_util_Optional);
                    meta.java_util_Optional_value.setObject(guestOptional, toEspresso.execute(result, meta.java_lang_Object));
                    return guestOptional;
                }
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
            }
        }
    }

    public final class BigDecimalTypeConverter implements InternalTypeConverter {

        @Override
        public StaticObject convertInternal(InteropLibrary interop, Object value, Meta meta, ToReference.DynamicToReference toEspresso) {
            try {
                // state required to reconstruct in guest
                int scale = interop.asInt(interop.invokeMember(value, "scale"));
                int precision = interop.asInt(interop.invokeMember(value, "precision"));
                BigInteger bigInteger = interop.asBigInteger(interop.invokeMember(value, "unscaledValue"));

                // reconstruct on guest side
                StaticObject guestMathContext = toEspresso.getAllocator().createNew(meta.java_math_MathContext);
                meta.java_math_MathContext_init.invokeDirect(guestMathContext, precision);

                StaticObject guestBigInteger = toEspresso.getAllocator().createNew(meta.java_math_BigInteger);
                meta.java_math_BigInteger_init.invokeDirect(guestBigInteger, toByteArray(bigInteger, meta));

                StaticObject guestBigDecimal = toEspresso.getAllocator().createNew(meta.java_math_BigDecimal);
                meta.java_math_BigDecimal_init.invokeDirect(guestBigDecimal, guestBigInteger, scale, guestMathContext);
                return guestBigDecimal;
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
            }
        }

        @TruffleBoundary
        private StaticObject toByteArray(BigInteger bigInteger, Meta meta) {
            return StaticObject.wrap(bigInteger.toByteArray(), meta);
        }
    }

    public final class PrimitiveArrayConverter implements InternalTypeConverter {

        private final ArrayKlass klass;

        public PrimitiveArrayConverter(ArrayKlass klass) {
            this.klass = klass;
        }

        @Override
        public StaticObject convertInternal(InteropLibrary interop, Object value, Meta meta, ToReference.DynamicToReference toEspresso) {
            if (!interop.hasArrayElements(value)) {
                throw new ClassCastException();
            }
            return StaticObject.createForeign(toEspresso.getLanguage(), klass, value, interop);
        }
    }
}
