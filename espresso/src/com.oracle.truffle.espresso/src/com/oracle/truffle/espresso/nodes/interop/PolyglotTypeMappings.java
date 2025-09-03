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

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.options.OptionMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Signatures;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.EspressoType;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.ParameterizedEspressoType;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.threads.ThreadState;
import com.oracle.truffle.espresso.threads.Transition;
import com.oracle.truffle.espresso.vm.VM;

public class PolyglotTypeMappings {
    private static final TruffleLogger LOGGER = TruffleLogger.getLogger(EspressoLanguage.ID, PolyglotTypeMappings.class);

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

            Symbol<Name> name = Names.toGuest;
            Symbol<Signature> desc = Signatures.Object_Object;

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
            warn(current);
        }
        current = "java.math.BigDecimal";
        if (!isCustomMapped(current)) {
            converters.put(current, new BigDecimalTypeConverter());
        } else {
            warn(current);
        }

        // primitive array types
        converters.put("byte[]", new BuiltinArrayTypeConverter(meta._byte_array));
        converters.put("boolean[]", new BuiltinArrayTypeConverter(meta._boolean_array));
        converters.put("char[]", new BuiltinArrayTypeConverter(meta._char_array));
        converters.put("short[]", new BuiltinArrayTypeConverter(meta._short_array));
        converters.put("int[]", new BuiltinArrayTypeConverter(meta._int_array));
        converters.put("long[]", new BuiltinArrayTypeConverter(meta._long_array));
        converters.put("float[]", new BuiltinArrayTypeConverter(meta._float_array));
        converters.put("double[]", new BuiltinArrayTypeConverter(meta._double_array));

        // boxed primitives
        converters.put("java.lang.Byte[]", new BuiltinArrayTypeConverter(meta.java_lang_Byte.array()));
        converters.put("java.lang.Boolean[]", new BuiltinArrayTypeConverter(meta.java_lang_Boolean.array()));
        converters.put("java.lang.Character[]", new BuiltinArrayTypeConverter(meta.java_lang_Character.array()));
        converters.put("java.lang.Short[]", new BuiltinArrayTypeConverter(meta.java_lang_Short.array()));
        converters.put("java.lang.Integer[]", new BuiltinArrayTypeConverter(meta.java_lang_Integer.array()));
        converters.put("java.lang.Long[]", new BuiltinArrayTypeConverter(meta.java_lang_Long.array()));
        converters.put("java.lang.Float[]", new BuiltinArrayTypeConverter(meta.java_lang_Float.array()));
        converters.put("java.lang.Double[]", new BuiltinArrayTypeConverter(meta.java_lang_Double.array()));

        // String array type
        converters.put("java.lang.String[]", new BuiltinArrayTypeConverter(meta.java_lang_String_array));

        // common java.* exception types where only exception message is expected to be transferred
        converters.put("java.lang.ClassCastException", new BuiltinExceptionTypeConverter(meta.java_lang_ClassCastException));
        converters.put("java.lang.IllegalArgumentException", new BuiltinExceptionTypeConverter(meta.java_lang_IllegalArgumentException));
        converters.put("java.lang.ClassNotFoundException", new BuiltinExceptionTypeConverter(meta.java_lang_ClassNotFoundException));
        converters.put("java.lang.IllegalStateException", new BuiltinExceptionTypeConverter(meta.java_lang_IllegalStateException));
        converters.put("java.util.NoSuchElementException", new BuiltinExceptionTypeConverter(meta.java_util_NoSuchElementException));
        converters.put("java.lang.NullPointerException", new BuiltinExceptionTypeConverter(meta.java_lang_NullPointerException));
        converters.put("java.lang.NumberFormatException", new BuiltinExceptionTypeConverter(meta.java_lang_NumberFormatException));
        converters.put("java.lang.UnsupportedOperationException", new BuiltinExceptionTypeConverter(meta.java_lang_UnsupportedOperationException));
        converters.put("java.lang.NoSuchMethodException", new BuiltinExceptionTypeConverter(meta.java_lang_NoSuchMethodException));
        converters.put("java.lang.NoSuchFieldException", new BuiltinExceptionTypeConverter(meta.java_lang_NoSuchFieldException));
        converters.put("java.lang.LinkageError", new BuiltinExceptionTypeConverter(meta.java_lang_LinkageError));
        converters.put("java.lang.ArithmeticException", new BuiltinExceptionTypeConverter(meta.java_lang_ArithmeticException));
        converters.put("java.lang.SecurityException", new BuiltinExceptionTypeConverter(meta.java_lang_SecurityException));
        converters.put("java.lang.CloneNotSupportedException", new BuiltinExceptionTypeConverter(meta.java_lang_CloneNotSupportedException));
        converters.put("java.lang.InstantiationError", new BuiltinExceptionTypeConverter(meta.java_lang_InstantiationError));
        converters.put("java.lang.InstantiationException", new BuiltinExceptionTypeConverter(meta.java_lang_InstantiationException));
        converters.put("java.lang.ExceptionInInitializerError", new BuiltinExceptionTypeConverter(meta.java_lang_ExceptionInInitializerError));
        converters.put("java.lang.StringIndexOutOfBoundsException", new BuiltinExceptionTypeConverter(meta.java_lang_StringIndexOutOfBoundsException));
        converters.put("java.lang.ArrayIndexOutOfBoundsException", new BuiltinExceptionTypeConverter(meta.java_lang_ArrayIndexOutOfBoundsException));
        converters.put("java.lang.IndexOutOfBoundsException", new BuiltinExceptionTypeConverter(meta.java_lang_IndexOutOfBoundsException));
        converters.put("java.lang.ArrayStoreException", new BuiltinExceptionTypeConverter(meta.java_lang_ArrayStoreException));
        converters.put("java.lang.UnsatisfiedLinkError", new BuiltinExceptionTypeConverter(meta.java_lang_UnsatisfiedLinkError));
        converters.put("java.lang.ClassCircularityError", new BuiltinExceptionTypeConverter(meta.java_lang_ClassCircularityError));
        converters.put("java.lang.ClassFormatError", new BuiltinExceptionTypeConverter(meta.java_lang_ClassFormatError));
        converters.put("java.lang.VerifyError", new BuiltinExceptionTypeConverter(meta.java_lang_VerifyError));
        converters.put("java.lang.InternalError", new BuiltinExceptionTypeConverter(meta.java_lang_InternalError));
        converters.put("java.lang.AbstractMethodError", new BuiltinExceptionTypeConverter(meta.java_lang_AbstractMethodError));
        converters.put("java.lang.OutOfMemoryError", new BuiltinExceptionTypeConverter(meta.java_lang_OutOfMemoryError));
        converters.put("java.lang.StackOverflowError", new BuiltinExceptionTypeConverter(meta.java_lang_StackOverflowError));
        converters.put("java.lang.InterruptedException", new BuiltinExceptionTypeConverter(meta.java_lang_InterruptedException));
        converters.put("java.lang.NoClassDefFoundError", new BuiltinExceptionTypeConverter(meta.java_lang_NoClassDefFoundError));
        converters.put("java.lang.IllegalMonitorStateException", new BuiltinExceptionTypeConverter(meta.java_lang_IllegalMonitorStateException));
        converters.put("java.lang.NegativeArraySizeException", new BuiltinExceptionTypeConverter(meta.java_lang_NegativeArraySizeException));
        internalTypeConverterFunctions = EconomicMap.create(converters);
    }

    private void addInternalEspressoCollections(EconomicMap<String, ObjectKlass> map, Meta meta) {
        String current = "java.lang.Iterable";
        if (!isCustomMapped(current)) {
            map.put(current, meta.polyglot.EspressoForeignIterable);
        } else {
            warn(current);
        }
        current = "java.util.List";
        if (!isCustomMapped(current)) {
            map.put(current, meta.polyglot.EspressoForeignList);
        } else {
            warn(current);
        }
        current = "java.util.Collection";
        if (!isCustomMapped(current)) {
            map.put(current, meta.polyglot.EspressoForeignCollection);
        } else {
            warn(current);
        }
        current = "java.util.Iterator";
        if (!isCustomMapped(current)) {
            map.put(current, meta.polyglot.EspressoForeignIterator);
        } else {
            warn(current);
        }
        current = "java.util.Map";
        if (!isCustomMapped(current)) {
            map.put(current, meta.polyglot.EspressoForeignMap);
        } else {
            warn(current);
        }
        current = "java.util.Set";
        if (!isCustomMapped(current)) {
            map.put(current, meta.polyglot.EspressoForeignSet);
        } else {
            warn(current);
        }
    }

    private static void warn(String mapping) {
        LOGGER.warning("Custom type mapping is used where there's a builtin type conversion available. Remove the [" + mapping + "] to enable the builtin converter.");
    }

    private boolean isCustomMapped(String mapping) {
        if (interfaceMappings != null && interfaceMappings.contains(mapping)) {
            return true;
        }
        return typeConverterFunctions != null && typeConverterFunctions.containsKey(mapping);
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
        StaticObject convertInternal(InteropLibrary interop, Object value, Meta meta, ToReference.DynamicToReference toEspresso, EspressoType targetType) throws UnsupportedTypeException;
    }

    public static class TypeConverterImpl implements TypeConverter {
        private final Object receiver;
        private final DirectCallNode callNode;

        TypeConverterImpl(Object receiver, DirectCallNode callNode) {
            this.receiver = receiver;
            this.callNode = callNode;
        }

        public Object convert(StaticObject foreign) {
            Transition transition = Transition.transition(ThreadState.IN_ESPRESSO);
            try {
                return callNode.call(receiver, foreign);
            } finally {
                transition.restore();
            }
        }
    }

    public static final class OptionalTypeConverter implements InternalTypeConverter {

        @Override
        public StaticObject convertInternal(InteropLibrary interop, Object value, Meta meta, ToReference.DynamicToReference toEspresso, EspressoType targetType) throws UnsupportedTypeException {
            try {
                Object result = interop.invokeMember(value, "orElse", StaticObject.NULL);
                if (interop.isNull(result)) {
                    return (StaticObject) meta.java_util_Optional_EMPTY.get(meta.java_util_Optional.getStatics());
                } else {
                    StaticObject guestOptional = toEspresso.getAllocator().createNew(meta.java_util_Optional);
                    EspressoType target = meta.java_lang_Object;
                    if (targetType instanceof ParameterizedEspressoType parameterizedEspressoType) {
                        target = parameterizedEspressoType.getTypeArguments()[0];
                    }
                    meta.java_util_Optional_value.setObject(guestOptional, toEspresso.execute(result, target));
                    return guestOptional;
                }
            } catch (UnsupportedTypeException e) {
                throw new ClassCastException();
            } catch (InteropException e) {
                throw UnsupportedTypeException.create(new Object[]{value}, "Could not cast foreign object to Optional", e);
            }
        }
    }

    public static final class BigDecimalTypeConverter implements InternalTypeConverter {

        @Override
        public StaticObject convertInternal(InteropLibrary interop, Object value, Meta meta, ToReference.DynamicToReference toEspresso, EspressoType targetType) throws UnsupportedTypeException {
            try {
                // state required to reconstruct in guest
                int scale = interop.asInt(interop.invokeMember(value, "scale"));
                int precision = interop.asInt(interop.invokeMember(value, "precision"));
                BigInteger bigInteger = interop.asBigInteger(interop.invokeMember(value, "unscaledValue"));

                // reconstruct on guest side
                StaticObject guestMathContext = toEspresso.getAllocator().createNew(meta.java_math_MathContext);
                meta.java_math_MathContext_init.invokeDirectSpecial(guestMathContext, precision);

                StaticObject guestBigInteger = toEspresso.getAllocator().createNew(meta.java_math_BigInteger);
                meta.java_math_BigInteger_init.invokeDirectSpecial(guestBigInteger, toByteArray(bigInteger, meta));

                StaticObject guestBigDecimal = toEspresso.getAllocator().createNew(meta.java_math_BigDecimal);
                meta.java_math_BigDecimal_init.invokeDirectSpecial(guestBigDecimal, guestBigInteger, scale, guestMathContext);
                return guestBigDecimal;
            } catch (InteropException e) {
                throw UnsupportedTypeException.create(new Object[]{value}, "Could not cast foreign object to BigDecimal", e);
            }
        }

        @TruffleBoundary
        private static StaticObject toByteArray(BigInteger bigInteger, Meta meta) {
            return StaticObject.wrap(bigInteger.toByteArray(), meta);
        }
    }

    public static final class BuiltinArrayTypeConverter implements InternalTypeConverter {

        private final ArrayKlass klass;

        public BuiltinArrayTypeConverter(ArrayKlass klass) {
            this.klass = klass;
        }

        @Override
        public StaticObject convertInternal(InteropLibrary interop, Object value, Meta meta, ToReference.DynamicToReference toEspresso, EspressoType espressoType) throws UnsupportedTypeException {
            if (!interop.hasArrayElements(value)) {
                boundaryThrow(value);
            }
            return StaticObject.createForeign(toEspresso.getLanguage(), klass, value, interop);
        }

        @TruffleBoundary
        private void boundaryThrow(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value},
                            EspressoError.format("Could not cast foreign object to %s: %s", klass.getNameAsString(), "foreign object has no array elements"));
        }
    }

    public static final class BuiltinExceptionTypeConverter implements InternalTypeConverter {

        private final ObjectKlass exceptionKlass;
        private final Method messageConstructor;

        public BuiltinExceptionTypeConverter(ObjectKlass klass) {
            this.exceptionKlass = klass;
            this.messageConstructor = klass.lookupDeclaredMethod(Names._init_, Signatures._void_String, Klass.LookupMode.INSTANCE_ONLY);
        }

        @Override
        public StaticObject convertInternal(InteropLibrary interop, Object value, Meta meta, ToReference.DynamicToReference toEspresso, EspressoType espressoType) throws UnsupportedTypeException {
            if (!interop.isException(value)) {
                throw UnsupportedTypeException.create(new Object[]{value},
                                EspressoError.format("Could not cast foreign object to %s: %s", exceptionKlass.getNameAsString(), "foreign object is not an exception"));
            }
            EspressoContext context = meta.getContext();
            // an espresso foreign exception type value will be passed from Interop invocations
            // whereas the raw foreign exception object is passed from ToEspressoNode
            StaticObject foreignException;
            if (value instanceof StaticObject) {
                foreignException = (StaticObject) value;
            } else {
                foreignException = StaticObject.createForeignException(context, value, interop);
            }

            // create the correctly typed guest exception that
            // will store the foreign exception in backtrace
            StaticObject result = context.getAllocator().createNew(exceptionKlass);
            StaticObject guestMessage;
            try {
                String message = interop.asString(interop.getExceptionMessage(foreignException));
                guestMessage = meta.toGuestString(message);
            } catch (UnsupportedMessageException e) {
                guestMessage = StaticObject.NULL;
            }
            messageConstructor.invokeDirectSpecial(result, guestMessage);
            /*
             * The back trace of the guest exception wrapper must be set to the foreign exception
             * object, then the back trace is retained in the guest code and the stackTrace field
             * set to null to trigger backtrace lookups
             */
            meta.java_lang_Throwable_backtrace.setObject(result, meta.java_lang_Throwable_backtrace.getObject(foreignException));
            if (meta.getJavaVersion().java9OrLater()) {
                meta.java_lang_Throwable_depth.setInt(result, meta.java_lang_Throwable_depth.getInt(foreignException));
            }
            meta.java_lang_Throwable_stackTrace.setObject(result, StaticObject.NULL);
            meta.HIDDEN_FRAMES.setHiddenObject(result, VM.StackTrace.FOREIGN_MARKER_STACK_TRACE);

            return result;
        }
    }
}
