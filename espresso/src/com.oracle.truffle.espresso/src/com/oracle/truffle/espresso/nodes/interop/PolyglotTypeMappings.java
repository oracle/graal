/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.impl.Klass;
import org.graalvm.options.OptionMap;

public class PolyglotTypeMappings {

    private final TypeConverter EMPTY_CONVERTER = new TypeConverter(null, null);

    private static final String GUEST_TYPE_CONVERSION_INTERFACE = "com.oracle.truffle.espresso.polyglot.GuestTypeConversion";
    private final boolean hasInterfaceMappings;
    private final List<String> interfaceMappings;
    private UnmodifiableEconomicMap<String, ObjectKlass> resolvedKlasses;
    private final OptionMap<String> typeConverters;
    private UnmodifiableEconomicMap<String, TypeConverter> typeConverterFunctions;
    private Map<Integer, TypeConverter> identityConverterCache;

    public PolyglotTypeMappings(List<String> interfaceMappings, OptionMap<String> typeConverters) {
        this.interfaceMappings = interfaceMappings;
        this.hasInterfaceMappings = !interfaceMappings.isEmpty();
        this.typeConverters = typeConverters;
        this.identityConverterCache = new ConcurrentHashMap<>(typeConverters.entrySet().size());
    }

    @TruffleBoundary
    public void resolve(EspressoContext context) {
        assert interfaceMappings != null;

        // resolve interface mappings
        if (hasInterfaceMappings) {
            EconomicMap<String, ObjectKlass> temp = EconomicMap.create(interfaceMappings.size());
            StaticObject bindingsLoader = context.getBindings().getBindingsLoader();

            for (String mapping : interfaceMappings) {
                Klass parent = context.getRegistries().loadKlass(context.getTypes().fromClassGetName(mapping), bindingsLoader, StaticObject.NULL);
                if (parent.isInterface()) {
                    temp.put(mapping, (ObjectKlass) parent);
                } else {
                    throw new IllegalStateException("invalid interface type mapping specified: " + mapping);
                }
            }
            resolvedKlasses = EconomicMap.create(temp);
        }
        // resolve type converters
        Set<Map.Entry<String, String>> converters = typeConverters.entrySet();
        if (!converters.isEmpty()) {
            EconomicMap<String, TypeConverter> temp = EconomicMap.create(converters.size());
            StaticObject bindingsLoader = context.getBindings().getBindingsLoader();

            Symbol<Symbol.Name> name = Symbol.Name.toGuest;
            Symbol<Symbol.Signature> desc = Symbol.Signature.Object_Object;

            // load the GuestTypeConversion interface for type checking
            Klass conversionInterface = context.getRegistries().loadKlass(context.getTypes().fromClassGetName(GUEST_TYPE_CONVERSION_INTERFACE), bindingsLoader, StaticObject.NULL);
            if (conversionInterface == null) {
                throw new IllegalStateException("Missing expected guest type conversion interface in polyglot.jar");
            }

            for (Map.Entry<String, String> entry : converters) {
                String type = entry.getKey();
                // make sure the target type class can be found
                Klass targetKlass = context.getRegistries().loadKlass(context.getTypes().fromClassGetName(type), bindingsLoader, StaticObject.NULL);
                if (targetKlass == null) {
                    throw new IllegalStateException("Class not found for target type conversion: " + type);
                }
                String conversionHandler = entry.getValue();
                ObjectKlass conversionKlass = (ObjectKlass) context.getRegistries().loadKlass(context.getTypes().fromClassGetName(conversionHandler), bindingsLoader, StaticObject.NULL);
                if (conversionKlass == null) {
                    throw new IllegalStateException("Class not found for polyglot type conversion handler: " + conversionHandler);
                }
                // make sure the conversion class implements GuestTypeConversion interface
                if (!conversionInterface.isAssignableFrom(conversionKlass)) {
                    throw new IllegalStateException("ConversionHandler does not implement the polyglot type conversion interface: " + GUEST_TYPE_CONVERSION_INTERFACE);
                }
                Method conversionMethod = conversionKlass.requireDeclaredMethod(name, desc);
                StaticObject conversionReceiver = context.getAllocator().createNew(conversionKlass);
                temp.put(type, new TypeConverter(conversionReceiver, DirectCallNode.create(conversionMethod.getCallTarget())));
            }
            typeConverterFunctions = EconomicMap.create(temp);
        }
    }

    @TruffleBoundary
    public ObjectKlass mapInterfaceName(String name) {
        assert resolvedKlasses != null;
        return resolvedKlasses.get(name);
    }

    public boolean hasInterfaceMappings() {
        return hasInterfaceMappings;
    }

    @TruffleBoundary
    public TypeConverter mapTypeConversion(Object metaObject, int identity, InteropLibrary interop) {
        assert typeConverterFunctions != null;
        TypeConverter converter = identityConverterCache.get(identity);
        if (converter == null) {
            String metaName = ToEspressoNode.getMetaName(metaObject, interop);
            converter = typeConverterFunctions.get(metaName);
            if (converter == null) {
                identityConverterCache.put(identity, EMPTY_CONVERTER);
                return EMPTY_CONVERTER;
            } else {
                identityConverterCache.put(identity, converter);
            }
        }
        return converter;
    }

    public final class TypeConverter {
        private final Object receiver;
        private final DirectCallNode callNode;

        TypeConverter(Object receiver, DirectCallNode callNode) {
            this.receiver = receiver;
            this.callNode = callNode;
        }

        public Object convert(StaticObject foreign) {
            if (this == EMPTY_CONVERTER) {
                return foreign;
            }
            return callNode.call(receiver, foreign);
        }
    }
}
