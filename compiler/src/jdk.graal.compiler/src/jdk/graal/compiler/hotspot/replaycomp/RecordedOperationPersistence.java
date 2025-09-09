/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.hotspot.replaycomp;

import java.io.IOException;
import java.io.Reader;
import java.io.Serial;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.Platform;
import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxy;
import jdk.graal.compiler.hotspot.stubs.IllegalArgumentExceptionArgumentIsNotAnArrayStub;
import jdk.graal.compiler.util.json.JsonBuilder;
import jdk.graal.compiler.util.json.JsonParser;
import jdk.graal.compiler.util.json.JsonWriter;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompressedNullConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.hotspot.VMField;
import jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig;
import jdk.vm.ci.hotspot.amd64.AMD64HotSpotRegisterConfig;
import jdk.vm.ci.hotspot.riscv64.RISCV64HotSpotRegisterConfig;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.EncodedSpeculationReason;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.TriState;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaMethod;
import jdk.vm.ci.meta.UnresolvedJavaType;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import jdk.vm.ci.riscv64.RISCV64;

/**
 * Provides functionality for persisting and loading recorded compilation units.
 * <p>
 * A {@link RecordedCompilationUnit} contains all data required to run a replay compilation. This
 * class can serialize these records to and from JSON files. A recorded compilation unit contains a
 * list of the recorded operations (method calls and their results during a recorded compilation),
 * foreign call linkages, and other metadata.
 * <p>
 * The operations are collected during a recorded compilation via an {@link OperationRecorder}. An
 * operation comprises a symbolic name of a method, the receiver object, argument values, and the
 * result of that operation (a return value or an exception). After a recorded compilation, this
 * class expects the raw JVMCI objects (without any proxies). It deduplicates these JVMCI objects by
 * assigning a single ID to all JVMCI objects that are {@link Object#equals equal}.
 * <p>
 * When {@link #load loading} the recorded compilation unit from JSON, the class creates proxies in
 * place of the JVMCI objects using the provided {@link ProxyFactory}.
 *
 * @see ReplayCompilationSupport
 * @see CompilerInterfaceDeclarations
 */
public class RecordedOperationPersistence {
    /**
     * A recorded compilation unit.
     *
     * @param request the compilation request
     * @param compilerConfiguration the name of the compiler configuration
     * @param isLibgraal {@code true} if the recorded compilation executed on libgraal
     * @param platform the platform of the system
     * @param linkages the recorded foreign call linkages
     * @param finalGraph the final canonical graph or {@code null} if not available
     * @param operations the recorded operations and their results
     */
    public record RecordedCompilationUnit(HotSpotCompilationRequest request, String compilerConfiguration,
                    boolean isLibgraal, Platform platform, RecordedForeignCallLinkages linkages, String finalGraph,
                    List<OperationRecorder.RecordedOperation> operations) {
    }

    /**
     * Factory for creating generic proxy objects for register compiler-interface classes.
     */
    @FunctionalInterface
    public interface ProxyFactory {
        /**
         * Creates a proxy for the given registration.
         *
         * @param registration the registration
         * @return the proxy
         */
        CompilationProxy createProxy(CompilerInterfaceDeclarations.Registration registration);
    }

    private interface RecursiveSerializer {
        void serialize(Object instance, JsonBuilder.ValueBuilder valueBuilder) throws IOException;

        void serialize(Object instance, JsonBuilder.ValueBuilder valueBuilder, String tag) throws IOException;
    }

    private interface RecursiveDeserializer {
        Object deserialize(Object json, ProxyFactory proxyFactory) throws DeserializationException;

        Object deserialize(Object json, ProxyFactory proxyFactory, String tag) throws DeserializationException;

        /**
         * Sets the {@link Architecture} parsed by this deserializer.
         *
         * @param arch the architecture
         */
        void setArchitecture(Architecture arch);

        /**
         * Gets the {@link Architecture} parsed by this deserializer.
         *
         * @return the architecture
         */
        Architecture getArchitecture();
    }

    private sealed interface ObjectSerializer {
        default boolean serializesSubclasses() {
            return false;
        }

        Class<?> clazz();

        String tag();

        void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException;

        Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException;
    }

    public static final class DeserializationException extends Exception {
        @Serial private static final long serialVersionUID = -6470496032190913296L;

        private DeserializationException(ObjectSerializer serializer, EconomicMap<String, Object> json, Throwable cause) {
            super("Serializer " + serializer + " failed to deserialize " + json, cause);
        }

        private DeserializationException(ObjectSerializer serializer, EconomicMap<String, Object> json, String message) {
            super("Serializer " + serializer + " failed to deserialize " + json + ": " + message);
        }
    }

    private static final class ClassSerializer implements ObjectSerializer {
        private static final Class<?>[] knownClasses = new Class<?>[]{String.class, System.class, Object[].class,
                        ExceptionHandler.class, IllegalArgumentExceptionArgumentIsNotAnArrayStub.class};

        @Override
        public Class<?> clazz() {
            return Class.class;
        }

        @Override
        public String tag() {
            return "class";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            objectBuilder.append("name", ((Class<?>) instance).getName());
        }

        @Override
        public Class<?> deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            String name = (String) json.get("name");
            Class<?> primitiveClass = Class.forPrimitiveName(name);
            if (primitiveClass != null) {
                return primitiveClass;
            }
            for (Class<?> knownClass : knownClasses) {
                if (knownClass.getName().equals(name)) {
                    return knownClass;
                }
            }
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                throw new DeserializationException(this, json, e);
            }
        }
    }

    private static final class OperationSerializer implements ObjectSerializer {
        private static final String TAG = "operation";

        @Override
        public Class<?> clazz() {
            return OperationRecorder.RecordedOperation.class;
        }

        @Override
        public String tag() {
            return TAG;
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            OperationRecorder.RecordedOperation op = (OperationRecorder.RecordedOperation) instance;
            try {
                serializer.serialize(op.receiver(), objectBuilder.append("recv"));
                objectBuilder.append("method", Arrays.asList(op.method().methodAndParamNames()));
                if (op.args() != null) {
                    try (JsonBuilder.ArrayBuilder arrayBuilder = objectBuilder.append("args").array()) {
                        for (Object arg : op.args()) {
                            serializer.serialize(arg, arrayBuilder.nextEntry());
                        }
                    }
                }
                serializer.serialize(op.resultOrMarker(), objectBuilder.append("res"));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Failed to serialize " + op + " due to: " + e.getMessage(), e);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public OperationRecorder.RecordedOperation deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory)
                        throws DeserializationException {
            Object recv = deserializer.deserialize(json.get("recv"), proxyFactory);
            List<Object> methodProp = (List<Object>) json.get("method");
            String[] methodArray = new String[methodProp.size()];
            for (int i = 0; i < methodArray.length; i++) {
                methodArray[i] = (String) methodProp.get(i);
            }
            CompilationProxy.SymbolicMethod method = new CompilationProxy.SymbolicMethod(methodArray);
            List<Object> list = (List<Object>) json.get("args");
            Object[] args;
            if (list == null) {
                args = null;
            } else {
                args = new Object[list.size()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = deserializer.deserialize(list.get(i), proxyFactory);
                }
            }
            Object res = deserializer.deserialize(json.get("res"), proxyFactory);
            return new OperationRecorder.RecordedOperation(recv, method, args, res);
        }
    }

    private static final class RegisteredSingletonSerializer implements ObjectSerializer {
        private final CompilerInterfaceDeclarations.Registration registration;

        private Object singletonProxy;

        private RegisteredSingletonSerializer(CompilerInterfaceDeclarations.Registration registration) {
            this.registration = registration;
            if (!registration.singleton()) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public boolean serializesSubclasses() {
            return true;
        }

        @Override
        public Class<?> clazz() {
            return registration.clazz();
        }

        @Override
        public String tag() {
            return clazz().getName();
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) {
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) {
            if (singletonProxy == null) {
                singletonProxy = proxyFactory.createProxy(registration);
            }
            if (singletonProxy == null) {
                throw new IllegalStateException("No singleton found for " + registration);
            }
            return singletonProxy;
        }
    }

    private static final class RegisteredInstanceSerializer implements ObjectSerializer {
        private final CompilerInterfaceDeclarations.Registration registration;

        private final EconomicMap<Object, Integer> instanceId;

        private final EconomicMap<Integer, Object> proxyById;

        private RegisteredInstanceSerializer(CompilerInterfaceDeclarations.Registration registration) {
            this.registration = registration;
            this.instanceId = EconomicMap.create();
            this.proxyById = EconomicMap.create();
            if (registration.singleton()) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public boolean serializesSubclasses() {
            return true;
        }

        @Override
        public Class<?> clazz() {
            return registration.clazz();
        }

        @Override
        public String tag() {
            return clazz().getSimpleName();
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            Integer id = instanceId.get(instance);
            if (id == null) {
                id = instanceId.size();
                instanceId.put(instance, id);
            }
            objectBuilder.append("id", id);
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) {
            int id = ((Number) json.get("id")).intValue();
            Object proxyInstance = proxyById.get(id);
            if (proxyInstance == null) {
                proxyInstance = proxyFactory.createProxy(registration);
                proxyById.put(id, proxyInstance);
            }
            return proxyInstance;
        }
    }

    private static final class StackTraceElementSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return StackTraceElement.class;
        }

        @Override
        public String tag() {
            return "ste";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            StackTraceElement ste = (StackTraceElement) instance;
            objectBuilder.append("loader", ste.getClassLoaderName());
            objectBuilder.append("module", ste.getModuleName());
            objectBuilder.append("moduleVer", ste.getModuleVersion());
            objectBuilder.append("holder", ste.getClassName());
            objectBuilder.append("method", ste.getMethodName());
            objectBuilder.append("file", ste.getFileName());
            objectBuilder.append("line", ste.getLineNumber());
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            String loader = (String) json.get("loader");
            String module = (String) json.get("module");
            String moduleVer = (String) json.get("moduleVer");
            String holder = (String) json.get("holder");
            String method = (String) json.get("method");
            String file = (String) json.get("file");
            int line = (int) json.get("line");
            return new StackTraceElement(loader, module, moduleVer, holder, method, file, line);
        }
    }

    private static final class StringSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return String.class;
        }

        @Override
        public String tag() {
            return "string";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            objectBuilder.append("content", instance);
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            return json.get("content");
        }
    }

    private static final class BooleanSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return Boolean.class;
        }

        @Override
        public String tag() {
            return "boolean";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            objectBuilder.append("value", instance);
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            return json.get("value");
        }
    }

    private static final int HEX_RADIX = 16;

    private static String floatToHex(float value) {
        return Integer.toHexString(Float.floatToRawIntBits(value));
    }

    private static float hexToFloat(String value) {
        return Float.intBitsToFloat(Integer.parseUnsignedInt(value, HEX_RADIX));
    }

    private static String doubleToHex(double value) {
        return Long.toHexString(Double.doubleToRawLongBits(value));
    }

    private static double hexToDouble(String value) {
        return Double.longBitsToDouble(Long.parseUnsignedLong(value, HEX_RADIX));
    }

    private static final class NumberSerializer implements ObjectSerializer {

        @Override
        public boolean serializesSubclasses() {
            return true;
        }

        @Override
        public Class<?> clazz() {
            return Number.class;
        }

        @Override
        public String tag() {
            return "number";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            objectBuilder.append("class", instance.getClass().getSimpleName());
            String value = switch (instance) {
                case Integer n -> Integer.toHexString(n);
                case Byte n -> Integer.toHexString(n);
                case Short n -> Integer.toHexString(n);
                case Long n -> Long.toHexString(n);
                case Float n -> floatToHex(n);
                case Double n -> doubleToHex(n);
                default -> throw new IllegalArgumentException("Unexpected value: " + instance);
            };
            objectBuilder.append("value", value);
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            String className = (String) json.get("class");
            String value = (String) json.get("value");
            switch (className) {
                case "Integer" -> {
                    return Integer.parseUnsignedInt(value, HEX_RADIX);
                }
                case "Byte" -> {
                    return (byte) Integer.parseUnsignedInt(value, HEX_RADIX);
                }
                case "Short" -> {
                    return (short) Integer.parseUnsignedInt(value, HEX_RADIX);
                }
                case "Long" -> {
                    return Long.parseUnsignedLong(value, HEX_RADIX);
                }
                case "Float" -> {
                    return hexToFloat(value);
                }
                case "Double" -> {
                    return hexToDouble(value);
                }
                default -> throw new IllegalArgumentException("Unknown class " + className + " for constant" + value);
            }
        }
    }

    private static final class ResultMarkerSerializer implements ObjectSerializer {

        @Override
        public boolean serializesSubclasses() {
            return true;
        }

        @Override
        public Class<?> clazz() {
            return SpecialResultMarker.class;
        }

        @Override
        public String tag() {
            return "marker";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            String type;
            if (instance == SpecialResultMarker.NO_RESULT_MARKER) {
                type = "noResult";
            } else if (instance == SpecialResultMarker.NULL_RESULT_MARKER) {
                type = "null";
            } else if (instance instanceof SpecialResultMarker.ExceptionThrownMarker thrownMarker) {
                type = "exception";
                serializer.serialize(thrownMarker.getThrown(), objectBuilder.append("throwable"));
            } else {
                throw new IllegalArgumentException("Cannot serialize result marker " + instance);
            }
            objectBuilder.append("type", type);
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            switch ((String) json.get("type")) {
                case "noResult" -> {
                    return SpecialResultMarker.NO_RESULT_MARKER;
                }
                case "null" -> {
                    return SpecialResultMarker.NULL_RESULT_MARKER;
                }
                case "exception" -> {
                    return new SpecialResultMarker.ExceptionThrownMarker((Throwable) deserializer.deserialize(json.get("throwable"), proxyFactory));
                }
                case null, default -> throw new DeserializationException(this, json, "Invalid marker type.");
            }
        }
    }

    private static final class EnumSerializer implements ObjectSerializer {
        @Override
        public boolean serializesSubclasses() {
            return true;
        }

        @Override
        public Class<?> clazz() {
            return Enum.class;
        }

        @Override
        public String tag() {
            return "enum";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            Enum<?> en = (Enum<?>) instance;
            objectBuilder.append("holder", en.getClass().getName());
            objectBuilder.append("constant", en.name());
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            String holderName = (String) json.get("holder");
            String constantName = (String) json.get("constant");
            Class<? extends Enum<?>> enumClass;
            try {
                enumClass = (Class<? extends Enum<?>>) Class.forName(holderName);
            } catch (ClassNotFoundException e) {
                throw new DeserializationException(this, json, e);
            }
            // TODO This could be more efficient.
            for (Enum<?> constant : enumClass.getEnumConstants()) {
                if (constant.name().equals(constantName)) {
                    return constant;
                }
            }
            throw new DeserializationException(this, json, "Invalid constant.");
        }
    }

    private static final class ArraySerializer implements ObjectSerializer {

        private final CompilerInterfaceDeclarations declarations;

        private ArraySerializer(CompilerInterfaceDeclarations declarations) {
            this.declarations = declarations;
        }

        @Override
        public boolean serializesSubclasses() {
            return true;
        }

        @Override
        public Class<?> clazz() {
            return Object[].class;
        }

        @Override
        public String tag() {
            return "array";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            Object[] array = (Object[]) instance;
            Class<?> componentType = array.getClass().getComponentType();
            Class<?> supertype = declarations.findRegisteredSupertype(componentType);
            if (supertype != null) {
                componentType = supertype;
            }
            serializer.serialize(componentType, objectBuilder.append("component"));
            try (JsonBuilder.ArrayBuilder arrayBuilder = objectBuilder.append("elements").array()) {
                for (Object object : array) {
                    serializer.serialize(object, arrayBuilder.nextEntry());
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            Class<?> component = (Class<?>) deserializer.deserialize(json.get("component"), proxyFactory);
            List<Object> elements = (List<Object>) json.get("elements");
            Object[] array = (Object[]) Array.newInstance(component, elements.size());
            for (int i = 0; i < elements.size(); i++) {
                array[i] = deserializer.deserialize(elements.get(i), proxyFactory);
            }
            return array;
        }
    }

    private static final class ListSerializer implements ObjectSerializer {

        @Override
        public boolean serializesSubclasses() {
            return true;
        }

        @Override
        public Class<?> clazz() {
            return List.class;
        }

        @Override
        public String tag() {
            return "list";
        }

        @Override
        @SuppressWarnings("unchecked")
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            List<Object> list = (List<Object>) instance;
            try (var array = objectBuilder.append("elements").array()) {
                for (Object element : list) {
                    serializer.serialize(element, array.nextEntry());
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            List<Object> list = new ArrayList<>();
            for (Object element : (List<Object>) json.get("elements")) {
                list.add(deserializer.deserialize(element, proxyFactory));
            }
            return list;
        }
    }

    private static final class ByteArraySerializer implements ObjectSerializer {
        @Override
        public Class<?> clazz() {
            return byte[].class;
        }

        @Override
        public String tag() {
            return "byteArray";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            byte[] bytes = (byte[]) instance;
            try (JsonBuilder.ArrayBuilder arrayBuilder = objectBuilder.append("bytes").array()) {
                for (byte b : bytes) {
                    arrayBuilder.append(b);
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) {
            List<Object> byteList = (List<Object>) json.get("bytes");
            byte[] bytes = new byte[byteList.size()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = ((Number) byteList.get(i)).byteValue();
            }
            return bytes;
        }
    }

    private static final class DoubleArraySerializer implements ObjectSerializer {
        @Override
        public Class<?> clazz() {
            return double[].class;
        }

        @Override
        public String tag() {
            return "doubleArray";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            double[] doubles = (double[]) instance;
            try (JsonBuilder.ArrayBuilder arrayBuilder = objectBuilder.append("doubles").array()) {
                for (double i : doubles) {
                    arrayBuilder.append(doubleToHex(i));
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) {
            List<Object> doubleList = (List<Object>) json.get("doubles");
            double[] doubles = new double[doubleList.size()];
            for (int i = 0; i < doubles.length; i++) {
                doubles[i] = hexToDouble((String) doubleList.get(i));
            }
            return doubles;
        }
    }

    private static final class SingletonSerializer implements ObjectSerializer {
        private final Object singleton;

        private SingletonSerializer(Object singleton) {
            this.singleton = singleton;
        }

        @Override
        public Class<?> clazz() {
            return singleton.getClass();
        }

        @Override
        public String tag() {
            return singleton.getClass().getName();
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) {
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            return singleton;
        }
    }

    private static final class RegisterSerializer implements ObjectSerializer {
        @Override
        public Class<?> clazz() {
            return Register.class;
        }

        @Override
        public String tag() {
            return "register";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            Register register = (Register) instance;
            objectBuilder.append("number", register.number);
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            int number = (int) json.get("number");
            for (Register register : deserializer.getArchitecture().getRegisters()) {
                if (register.number == number) {
                    return register;
                }
            }
            throw new DeserializationException(this, json, "Register not found");
        }
    }

    private static final class FieldSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return Field.class;
        }

        @Override
        public String tag() {
            return "field";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            Field field = (Field) instance;
            serializer.serialize(field.getDeclaringClass(), objectBuilder.append("holder"));
            objectBuilder.append("name", field.getName());
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            Class<?> holder = (Class<?>) deserializer.deserialize(json.get("holder"), proxyFactory);
            String name = (String) json.get("name");
            try {
                if (holder == String.class) {
                    // Ensure the reflection metadata is included in the libgraal image.
                    if ("coder".equals(name)) {
                        return String.class.getDeclaredField("coder");
                    } else if ("value".equals(name)) {
                        return String.class.getDeclaredField("value");
                    }
                }
                return holder.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                throw new DeserializationException(this, json, e);
            }
        }
    }

    private static final class AssumptionResultSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return Assumptions.AssumptionResult.class;
        }

        @Override
        public String tag() {
            return "assumpRes";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            Assumptions.AssumptionResult<?> result = (Assumptions.AssumptionResult<?>) instance;
            serializer.serialize(result.getResult(), objectBuilder.append("result"));
            try (JsonBuilder.ArrayBuilder arrayBuilder = objectBuilder.append("assumptions").array()) {
                Assumptions assumptions = new Assumptions();
                result.recordTo(assumptions);
                for (Assumptions.Assumption assumption : assumptions) {
                    serializer.serialize(assumption, arrayBuilder.nextEntry());
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            Object result = deserializer.deserialize(json.get("result"), proxyFactory);
            List<Assumptions.Assumption> assumptions = new ArrayList<>();
            for (Object assumption : (List<Object>) json.get("assumptions")) {
                assumptions.add((Assumptions.Assumption) deserializer.deserialize(assumption, proxyFactory));
            }
            return new Assumptions.AssumptionResult<>(result, assumptions.toArray(Assumptions.Assumption[]::new));
        }
    }

    private static final class NoFinalizableSubclassAssumptionSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return Assumptions.NoFinalizableSubclass.class;
        }

        @Override
        public String tag() {
            return "noFinSubclass";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            Assumptions.NoFinalizableSubclass assumption = (Assumptions.NoFinalizableSubclass) instance;
            serializer.serialize(assumption.receiverType, objectBuilder.append("receiverType"));
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            ResolvedJavaType receiverType = (ResolvedJavaType) deserializer.deserialize(json.get("receiverType"), proxyFactory);
            return new Assumptions.NoFinalizableSubclass(receiverType);
        }
    }

    private static final class ConcreteSubtypeAssumptionSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return Assumptions.ConcreteSubtype.class;
        }

        @Override
        public String tag() {
            return "concreteSubtype";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            Assumptions.ConcreteSubtype assumption = (Assumptions.ConcreteSubtype) instance;
            serializer.serialize(assumption.subtype, objectBuilder.append("subtype"));
            serializer.serialize(assumption.context, objectBuilder.append("context"));
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            ResolvedJavaType subtype = (ResolvedJavaType) deserializer.deserialize(json.get("subtype"), proxyFactory);
            ResolvedJavaType context = (ResolvedJavaType) deserializer.deserialize(json.get("context"), proxyFactory);
            return new DelayedDeserializationObject.ConcreteSubtypeWithDelayedDeserialization(context, subtype);
        }
    }

    private static final class LeafTypeAssumptionSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return Assumptions.LeafType.class;
        }

        @Override
        public String tag() {
            return "leafType";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            Assumptions.LeafType assumption = (Assumptions.LeafType) instance;
            serializer.serialize(assumption.context, objectBuilder.append("context"));
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            ResolvedJavaType context = (ResolvedJavaType) deserializer.deserialize(json.get("context"), proxyFactory);
            return new DelayedDeserializationObject.LeafTypeWithDelayedDeserialization(context);
        }
    }

    private static final class ConcreteMethodAssumptionSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return Assumptions.ConcreteMethod.class;
        }

        @Override
        public String tag() {
            return "concreteMethod";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            Assumptions.ConcreteMethod assumption = (Assumptions.ConcreteMethod) instance;
            serializer.serialize(assumption.context, objectBuilder.append("context"));
            serializer.serialize(assumption.method, objectBuilder.append("method"));
            serializer.serialize(assumption.impl, objectBuilder.append("impl"));
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            ResolvedJavaType context = (ResolvedJavaType) deserializer.deserialize(json.get("context"), proxyFactory);
            ResolvedJavaMethod method = (ResolvedJavaMethod) deserializer.deserialize(json.get("method"), proxyFactory);
            ResolvedJavaMethod impl = (ResolvedJavaMethod) deserializer.deserialize(json.get("method"), proxyFactory);
            return new Assumptions.ConcreteMethod(method, context, impl);
        }
    }

    private static final class CallSiteTargetValueAssumptionSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return Assumptions.CallSiteTargetValue.class;
        }

        @Override
        public String tag() {
            return "callSiteTarget";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            Assumptions.CallSiteTargetValue assumption = (Assumptions.CallSiteTargetValue) instance;
            serializer.serialize(assumption.callSite, objectBuilder.append("callSite"));
            serializer.serialize(assumption.methodHandle, objectBuilder.append("methodHandle"));

        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            JavaConstant callSite = (JavaConstant) deserializer.deserialize(json.get("callSite"), proxyFactory);
            JavaConstant methodHandle = (JavaConstant) deserializer.deserialize(json.get("methodHandle"), proxyFactory);
            return new Assumptions.CallSiteTargetValue(callSite, methodHandle);
        }
    }

    private static final class UnresolvedJavaTypeSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return UnresolvedJavaType.class;
        }

        @Override
        public String tag() {
            return "unresType";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            UnresolvedJavaType type = (UnresolvedJavaType) instance;
            objectBuilder.append("name", type.getName());

        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            String name = (String) json.get("name");
            return UnresolvedJavaType.create(name);
        }
    }

    private static final class UnresolvedJavaMethodSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return UnresolvedJavaMethod.class;
        }

        @Override
        public String tag() {
            return "unresMethod";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            UnresolvedJavaMethod method = (UnresolvedJavaMethod) instance;
            objectBuilder.append("name", method.getName());
            serializer.serialize(method.getSignature(), objectBuilder.append("signature"));
            serializer.serialize(method.getDeclaringClass(), objectBuilder.append("holder"));

        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            String name = (String) json.get("name");
            Signature signature = (Signature) deserializer.deserialize(json.get("signature"), proxyFactory);
            JavaType holder = (JavaType) deserializer.deserialize(json.get("holder"), proxyFactory);
            return new UnresolvedJavaMethod(name, signature, holder);
        }
    }

    private static final class UnresolvedJavaFieldSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return UnresolvedJavaField.class;
        }

        @Override
        public String tag() {
            return "unresField";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            UnresolvedJavaField method = (UnresolvedJavaField) instance;
            objectBuilder.append("name", method.getName());
            serializer.serialize(method.getType(), objectBuilder.append("type"));
            serializer.serialize(method.getDeclaringClass(), objectBuilder.append("holder"));

        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            String name = (String) json.get("name");
            JavaType type = (JavaType) deserializer.deserialize(json.get("type"), proxyFactory);
            JavaType holder = (JavaType) deserializer.deserialize(json.get("holder"), proxyFactory);
            return new UnresolvedJavaField(holder, name, type);
        }
    }

    private static final class PrimitiveConstantSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return PrimitiveConstant.class;
        }

        @Override
        public String tag() {
            return "primConst";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            PrimitiveConstant constant = (PrimitiveConstant) instance;
            objectBuilder.append("raw", constant.getRawValue());
            serializer.serialize(constant.getJavaKind(), objectBuilder.append("kind"));
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            Number raw = (Number) json.get("raw");
            JavaKind kind = (JavaKind) deserializer.deserialize(json.get("kind"), proxyFactory);
            return JavaConstant.forPrimitive(kind, raw.longValue());
        }
    }

    private static final class ForeignCallDescriptorSerializer implements ObjectSerializer {
        private static final class ForeignCallDescriptorSurrogate {
        }

        @Override
        public boolean serializesSubclasses() {
            return true;
        }

        @Override
        public Class<?> clazz() {
            return ForeignCallDescriptor.class;
        }

        @Override
        public String tag() {
            return "foreignCallDesc";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            return new ForeignCallDescriptorSurrogate();
        }
    }

    private static final class EncodedSpeculationReasonSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return EncodedSpeculationReason.class;
        }

        @Override
        public String tag() {
            return "encSpecReason";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            EncodedSpeculationReason reason = (EncodedSpeculationReason) instance;
            objectBuilder.append("groupName", reason.getGroupName());
            objectBuilder.append("groupId", reason.getGroupId());
            serializer.serialize(reason.getContext(), objectBuilder.append("context"));
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            String name = (String) json.get("groupName");
            int groupId = (int) json.get("groupId");
            Object[] context = (Object[]) deserializer.deserialize(json.get("context"), proxyFactory);
            return new EncodedSpeculationReason(groupId, name, context);
        }
    }

    private static final class HotSpotSpeculationSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return HotSpotSpeculationLog.HotSpotSpeculation.class;
        }

        @Override
        public String tag() {
            return "hsSpec";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            HotSpotSpeculationLog.HotSpotSpeculation speculation = (HotSpotSpeculationLog.HotSpotSpeculation) instance;
            serializer.serialize(speculation.getReason(), objectBuilder.append("reason"));
            serializer.serialize(speculation.getReasonEncoding(), objectBuilder.append("bytes"));
            serializer.serialize(speculation.getEncoding(), objectBuilder.append("encoding"));
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            JavaConstant encoding = (JavaConstant) deserializer.deserialize(json.get("encoding"), proxyFactory);
            byte[] bytes = (byte[]) deserializer.deserialize(json.get("bytes"), proxyFactory);
            SpeculationLog.SpeculationReason reason = (SpeculationLog.SpeculationReason) deserializer.deserialize(json.get("reason"), proxyFactory);
            return new HotSpotSpeculationLog.HotSpotSpeculation(reason, encoding, bytes);
        }
    }

    private static final class JavaTypeProfileSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return JavaTypeProfile.class;
        }

        @Override
        public String tag() {
            return "typeProf";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            JavaTypeProfile typeProfile = (JavaTypeProfile) instance;
            serializer.serialize(typeProfile.getNullSeen(), objectBuilder.append("nullSeen"));
            objectBuilder.append("notRecorded", doubleToHex(typeProfile.getNotRecordedProbability()));
            try (JsonBuilder.ArrayBuilder arrayBuilder = objectBuilder.append("types").array()) {
                for (JavaTypeProfile.ProfiledType item : typeProfile.getTypes()) {
                    serializer.serialize(item, arrayBuilder.nextEntry());
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            TriState nullSeen = (TriState) deserializer.deserialize(json.get("nullSeen"), proxyFactory);
            double notRecorded = hexToDouble((String) json.get("notRecorded"));
            List<DelayedDeserializationObject.ProfiledTypeWithDelayedDeserialization> types = new ArrayList<>();
            for (var type : (List<Object>) json.get("types")) {
                types.add((DelayedDeserializationObject.ProfiledTypeWithDelayedDeserialization) deserializer.deserialize(type, proxyFactory));
            }
            return new DelayedDeserializationObject.JavaTypeProfileWithDelayedDeserialization(nullSeen, notRecorded,
                            types.toArray(DelayedDeserializationObject.ProfiledTypeWithDelayedDeserialization[]::new));
        }
    }

    private static final class ProfiledTypeSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return JavaTypeProfile.ProfiledType.class;
        }

        @Override
        public String tag() {
            return "ptype";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            JavaTypeProfile.ProfiledType profiledType = (JavaTypeProfile.ProfiledType) instance;
            serializer.serialize(profiledType.getType(), objectBuilder.append("type"));
            objectBuilder.append("prob", doubleToHex(profiledType.getProbability()));
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            double prob = hexToDouble((String) json.get("prob"));
            ResolvedJavaType type = (ResolvedJavaType) deserializer.deserialize(json.get("type"), proxyFactory);
            return new DelayedDeserializationObject.ProfiledTypeWithDelayedDeserialization(type, prob);
        }
    }

    private static final class BitSetSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return BitSet.class;
        }

        @Override
        public String tag() {
            return "bitset";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            BitSet bitSet = (BitSet) instance;
            try (JsonBuilder.ArrayBuilder arrayBuilder = objectBuilder.append("words").array()) {
                for (long word : bitSet.toLongArray()) {
                    arrayBuilder.append(word);
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) {
            List<Object> list = (List<Object>) json.get("words");
            long[] longArray = new long[list.size()];
            for (int i = 0; i < list.size(); i++) {
                longArray[i] = ((Number) list.get(i)).longValue();
            }
            return BitSet.valueOf(longArray);
        }
    }

    private static final class VMFieldSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return VMField.class;
        }

        @Override
        public String tag() {
            return "vmField";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            VMField vmField = (VMField) instance;
            objectBuilder.append("name", vmField.name);
            objectBuilder.append("type", vmField.type);
            objectBuilder.append("offset", vmField.offset);
            objectBuilder.append("address", Long.toHexString(vmField.address));
            serializer.serialize(vmField.value, objectBuilder.append("value"));
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            String name = (String) json.get("name");
            String type = (String) json.get("type");
            long offset = ((Number) json.get("offset")).longValue();
            long address = Long.parseUnsignedLong((String) json.get("address"), HEX_RADIX);
            Object value = deserializer.deserialize(json.get("value"), proxyFactory);
            return new VMField(name, type, offset, address, value);
        }
    }

    private static final class RecordedCompilationUnitSerializer implements ObjectSerializer {
        private static final String TAG = "recordedCompilationUnit";

        @Override
        public Class<?> clazz() {
            return RecordedCompilationUnit.class;
        }

        @Override
        public String tag() {
            return TAG;
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            RecordedCompilationUnit unit = (RecordedCompilationUnit) instance;
            serializer.serialize(unit.request.getMethod(), objectBuilder.append("method"));
            objectBuilder.append("osName", unit.platform.osName());
            objectBuilder.append("archName", unit.platform.archName());
            objectBuilder.append("compilerConfiguration", unit.compilerConfiguration);
            objectBuilder.append("isLibgraal", unit.isLibgraal);
            objectBuilder.append("entryBCI", unit.request.getEntryBCI());
            objectBuilder.append("compileId", unit.request.getId());
            try (JsonBuilder.ArrayBuilder arrayBuilder = objectBuilder.append("operations").array()) {
                for (OperationRecorder.RecordedOperation operation : unit.operations) {
                    serializer.serialize(operation, arrayBuilder.nextEntry(), OperationSerializer.TAG);
                }
            }
            serializer.serialize(unit.linkages, objectBuilder.append("linkages"), RecordedForeignCallLinkagesSerializer.TAG);
            objectBuilder.append("finalGraph", unit.finalGraph);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            HotSpotResolvedJavaMethod method = (HotSpotResolvedJavaMethod) deserializer.deserialize(json.get("method"), proxyFactory);
            String osName = (String) json.get("osName");
            String archName = (String) json.get("archName");
            Platform platform = new Platform(osName, archName);
            String compilerConfiguration = (String) json.get("compilerConfiguration");
            boolean isLibgraal = (boolean) json.get("isLibgraal");
            int entryBCI = (int) json.get("entryBCI");
            int compileId = (int) json.get("compileId");
            List<Object> list = (List<Object>) json.get("operations");
            List<OperationRecorder.RecordedOperation> operations = new ArrayList<>(list.size());
            for (Object object : list) {
                operations.add((OperationRecorder.RecordedOperation) deserializer.deserialize(object, proxyFactory, OperationSerializer.TAG));
            }
            RecordedForeignCallLinkages linkages = (RecordedForeignCallLinkages) deserializer.deserialize(json.get("linkages"), proxyFactory, RecordedForeignCallLinkagesSerializer.TAG);
            String finalGraph = (String) json.get("finalGraph");
            return new RecordedCompilationUnit(new HotSpotCompilationRequest(method, entryBCI, 0, compileId), compilerConfiguration, isLibgraal, platform, linkages, finalGraph, operations);
        }
    }

    private static final class RecordedForeignCallLinkagesSerializer implements ObjectSerializer {
        private static final String TAG = "linkages";

        @Override
        public Class<?> clazz() {
            return RecordedForeignCallLinkages.class;
        }

        @Override
        public String tag() {
            return TAG;
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            RecordedForeignCallLinkages linkages = (RecordedForeignCallLinkages) instance;
            try (JsonBuilder.ObjectBuilder builder = objectBuilder.append("map").object()) {
                var cursor = linkages.linkages().getEntries();
                while (cursor.advance()) {
                    serializer.serialize(cursor.getValue(), builder.append(cursor.getKey()), RecordedForeignCallLinkageSerializer.TAG);
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            EconomicMap<String, RecordedForeignCallLinkages.RecordedForeignCallLinkage> linkages = EconomicMap.create();
            EconomicMap<String, Object> map = (EconomicMap<String, Object>) json.get("map");
            var cursor = map.getEntries();
            while (cursor.advance()) {
                String key = cursor.getKey();
                RecordedForeignCallLinkages.RecordedForeignCallLinkage linkage = (RecordedForeignCallLinkages.RecordedForeignCallLinkage) deserializer.deserialize(cursor.getValue(), proxyFactory,
                                RecordedForeignCallLinkageSerializer.TAG);
                linkages.put(key, linkage);
            }
            return new RecordedForeignCallLinkages(linkages);
        }
    }

    private static final class RecordedForeignCallLinkageSerializer implements ObjectSerializer {
        private static final String TAG = "linkage";

        @Override
        public Class<?> clazz() {
            return RecordedForeignCallLinkages.RecordedForeignCallLinkage.class;
        }

        @Override
        public String tag() {
            return TAG;
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            RecordedForeignCallLinkages.RecordedForeignCallLinkage linkage = (RecordedForeignCallLinkages.RecordedForeignCallLinkage) instance;
            objectBuilder.append("address", linkage.address());
            serializer.serialize(linkage.temporaries(), objectBuilder.append("temporaries"));
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            long address = ((Number) json.get("address")).longValue();
            Value[] temporaries = (Value[]) deserializer.deserialize(json.get("temporaries"), proxyFactory);
            return new RecordedForeignCallLinkages.RecordedForeignCallLinkage(address, temporaries);
        }
    }

    private static final class RegisterValueSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return RegisterValue.class;
        }

        @Override
        public String tag() {
            return "regVal";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            RegisterValue registerValue = (RegisterValue) instance;
            serializer.serialize(registerValue.getRegister(), objectBuilder.append("register"));
            serializer.serialize(registerValue.getValueKind(), objectBuilder.append("valueKind"));
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            Register register = (Register) deserializer.deserialize(json.get("register"), proxyFactory);
            ValueKind<?> valueKind = (ValueKind<?>) deserializer.deserialize(json.get("valueKind"), proxyFactory);
            return register.asValue(valueKind);
        }
    }

    private static final class ExceptionHandlerSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return ExceptionHandler.class;
        }

        @Override
        public String tag() {
            return "exceptHandler";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            ExceptionHandler handler = (ExceptionHandler) instance;
            objectBuilder.append("startBCI", handler.getStartBCI());
            objectBuilder.append("endBCI", handler.getEndBCI());
            objectBuilder.append("catchBCI", handler.getHandlerBCI());
            objectBuilder.append("catchTypeCPI", handler.catchTypeCPI());
            serializer.serialize(handler.getCatchType(), objectBuilder.append("catchType"));
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            int startBCI = ((Number) json.get("startBCI")).intValue();
            int endBCI = ((Number) json.get("endBCI")).intValue();
            int catchBCI = ((Number) json.get("catchBCI")).intValue();
            int catchTypeCPI = ((Number) json.get("catchTypeCPI")).intValue();
            JavaType catchType = (JavaType) deserializer.deserialize(json.get("catchType"), proxyFactory);
            return new ExceptionHandler(startBCI, endBCI, catchBCI, catchTypeCPI, catchType);
        }
    }

    private static final class EnumSetSerializer implements ObjectSerializer {
        /**
         * Represents an unknown {@link Enum} type. Since the type system does not allow creating an
         * {@link EnumSet} without specifying the exact enum type, we can use this type as a generic
         * type parameter (which is erased anyway).
         */
        private enum UnknownEnum {
        }

        @Override
        public boolean serializesSubclasses() {
            return true;
        }

        @Override
        public Class<?> clazz() {
            return EnumSet.class;
        }

        @Override
        public String tag() {
            return "enumSet";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            EnumSet<?> enumSet = (EnumSet<?>) instance;
            Class<?> elementType;
            Optional<?> maybeElementType = enumSet.stream().findAny();
            if (maybeElementType.isPresent()) {
                elementType = maybeElementType.get().getClass();
            } else {
                maybeElementType = EnumSet.complementOf(enumSet).stream().findAny();
                if (maybeElementType.isPresent()) {
                    elementType = maybeElementType.get().getClass();
                } else {
                    elementType = UnknownEnum.class;
                }
            }
            serializer.serialize(elementType, objectBuilder.append("enum"));
            try (var array = objectBuilder.append("ordinals").array()) {
                for (Enum<?> element : enumSet) {
                    array.append(element.ordinal());
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            Class<UnknownEnum> elementType = (Class<UnknownEnum>) deserializer.deserialize(json.get("enum"), proxyFactory);
            return asEnumSet(elementType, (List<Object>) json.get("ordinals"));
        }

        private static <E extends Enum<E>> EnumSet<E> asEnumSet(Class<E> clazz, Collection<Object> ordinals) {
            EnumSet<E> enumSet = EnumSet.noneOf(clazz);
            E[] enumConstants = clazz.getEnumConstants();
            for (Object ordinal : ordinals) {
                enumSet.add(enumConstants[((Number) ordinal).intValue()]);
            }
            return enumSet;
        }
    }

    private static final class TargetDescriptionSerializer implements ObjectSerializer {

        @Override
        public Class<?> clazz() {
            return TargetDescription.class;
        }

        @Override
        public String tag() {
            return "targetDescription";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            TargetDescription targetDescription = (TargetDescription) instance;
            serializer.serialize(targetDescription.arch, objectBuilder.append("architecture"));
            objectBuilder.append("isMP", targetDescription.isMP);
            objectBuilder.append("stackAlignment", targetDescription.stackAlignment);
            objectBuilder.append("implicitNullCheckLimit", targetDescription.implicitNullCheckLimit);
            objectBuilder.append("inlineObjects", targetDescription.inlineObjects);
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            Architecture arch = (Architecture) deserializer.deserialize(json.get("architecture"), proxyFactory);
            boolean isMP = (boolean) json.get("isMP");
            int stackAlignment = ((Number) json.get("stackAlignment")).intValue();
            int implicitNullCheckLimit = ((Number) json.get("implicitNullCheckLimit")).intValue();
            boolean inlineObjects = (boolean) json.get("inlineObjects");
            return new TargetDescription(arch, isMP, stackAlignment, implicitNullCheckLimit, inlineObjects);
        }
    }

    private static final class ArchitectureSerializer implements ObjectSerializer {

        @Override
        public boolean serializesSubclasses() {
            return true;
        }

        @Override
        public Class<?> clazz() {
            return Architecture.class;
        }

        @Override
        public String tag() {
            return "architecture";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            Architecture architecture = (Architecture) instance;
            objectBuilder.append("name", architecture.getName());
            serializer.serialize(architecture.getFeatures(), objectBuilder.append("features"));
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            String name = (String) json.get("name");
            EnumSet<?> features = (EnumSet<?>) deserializer.deserialize(json.get("features"), proxyFactory);
            Architecture architecture = switch (name) {
                case "AMD64" -> new AMD64((EnumSet<AMD64.CPUFeature>) features);
                case "riscv64" -> new RISCV64((EnumSet<RISCV64.CPUFeature>) features);
                case "aarch64" -> new AArch64((EnumSet<AArch64.CPUFeature>) features);
                default -> throw new IllegalStateException("Unexpected value: " + name);
            };
            deserializer.setArchitecture(architecture);
            return architecture;
        }
    }

    private static final class RegisterConfigSerializer implements ObjectSerializer {
        private final boolean isHostWindowsOS;

        private final TargetDescription hostTarget;

        private RegisterConfigSerializer(Platform platform, TargetDescription hostTarget) {
            this.isHostWindowsOS = platform.osName().equals("windows");
            this.hostTarget = hostTarget;
        }

        @Override
        public boolean serializesSubclasses() {
            return true;
        }

        @Override
        public Class<?> clazz() {
            return RegisterConfig.class;
        }

        @Override
        public String tag() {
            return "registerConfig";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            RegisterConfig registerConfig = (RegisterConfig) instance;
            String name = switch (registerConfig) {
                case AMD64HotSpotRegisterConfig ignored -> "AMD64";
                case RISCV64HotSpotRegisterConfig ignored -> "RISCV64";
                case AArch64HotSpotRegisterConfig ignored -> "AArch64";
                default -> throw new IllegalStateException("Unexpected value: " + registerConfig);
            };
            objectBuilder.append("name", name);
            serializer.serialize(hostTarget, objectBuilder.append("target"));
            serializer.serialize(registerConfig.getAllocatableRegisters(), objectBuilder.append("allocatable"));
            objectBuilder.append("windowsOS", isHostWindowsOS);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            String name = (String) json.get("name");
            TargetDescription targetDescription = (TargetDescription) deserializer.deserialize(json.get("target"), proxyFactory);
            List<Register> allocatable = (List<Register>) deserializer.deserialize(json.get("allocatable"), proxyFactory);
            switch (name) {
                case "AMD64" -> {
                    boolean windowsOS = (boolean) json.get("windowsOS");
                    return new AMD64HotSpotRegisterConfig(targetDescription, allocatable, windowsOS);
                }
                case "RISCV64" -> {
                    return new RISCV64HotSpotRegisterConfig(targetDescription, allocatable);
                }
                case "AArch64" -> {
                    return new AArch64HotSpotRegisterConfig(targetDescription, allocatable);
                }
                default -> throw new IllegalStateException("Unexpected kind of register config: " + name);
            }
        }
    }

    private static final class ThrowableSerializer implements ObjectSerializer {

        @Override
        public boolean serializesSubclasses() {
            return true;
        }

        @Override
        public Class<?> clazz() {
            return Throwable.class;
        }

        @Override
        public String tag() {
            return "throwable";
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ObjectBuilder objectBuilder, RecursiveSerializer serializer) throws IOException {
            Throwable throwable = (Throwable) instance;
            serializer.serialize(throwable.getClass(), objectBuilder.append("class"));
            objectBuilder.append("message", throwable.getMessage());
        }

        @Override
        public Object deserialize(EconomicMap<String, Object> json, RecursiveDeserializer deserializer, ProxyFactory proxyFactory) throws DeserializationException {
            Class<?> clazz = (Class<?>) deserializer.deserialize(json.get("class"), proxyFactory);
            String message = (String) json.get("message");
            if (clazz == JVMCIError.class) {
                return new JVMCIError(message);
            } else if (clazz == GraalError.class) {
                return new GraalError(message);
            } else if (clazz == IllegalArgumentException.class) {
                return new IllegalArgumentException(message);
            } else {
                return new Throwable(message);
            }
        }
    }

    private final EconomicMap<String, ObjectSerializer> tagSerializers;

    private final EconomicMap<Class<?>, ObjectSerializer> exactClassSerializers;

    private final EconomicMap<Class<?>, ObjectSerializer> interfaceSerializers;

    /**
     * Constructs a serializer/deserializer for recorded compilation units.
     *
     * @param declarations the definitions of the compiler interface
     * @param hostPlatform the platform of the current host (for serialization)
     * @param hostTarget the target of the current host (for serialization)
     */
    public RecordedOperationPersistence(CompilerInterfaceDeclarations declarations, Platform hostPlatform, TargetDescription hostTarget) {
        this.tagSerializers = EconomicMap.create();
        this.exactClassSerializers = EconomicMap.create();
        this.interfaceSerializers = EconomicMap.create();
        for (CompilerInterfaceDeclarations.Registration registration : declarations.getRegistrations()) {
            if (registration.singleton()) {
                addSerializer(new RegisteredSingletonSerializer(registration));
            } else {
                addSerializer(new RegisteredInstanceSerializer(registration));
            }
        }
        addSerializer(new ClassSerializer());
        addSerializer(new OperationSerializer());
        addSerializer(new StringSerializer());
        addSerializer(new BooleanSerializer());
        addSerializer(new NumberSerializer());
        addSerializer(new StackTraceElementSerializer());
        addSerializer(new ResultMarkerSerializer());
        addSerializer(new EnumSerializer());
        addSerializer(new DoubleArraySerializer());
        addSerializer(new ByteArraySerializer());
        addSerializer(new ArraySerializer(declarations));
        addSerializer(new ListSerializer());
        addSerializer(new SingletonSerializer(JavaConstant.NULL_POINTER));
        addSerializer(new SingletonSerializer(HotSpotCompressedNullConstant.COMPRESSED_NULL));
        addSerializer(new SingletonSerializer(SpeculationLog.NO_SPECULATION));
        addSerializer(new SingletonSerializer(ValueKind.Illegal));
        addSerializer(new RegisterSerializer());
        addSerializer(new FieldSerializer());
        addSerializer(new AssumptionResultSerializer());
        addSerializer(new NoFinalizableSubclassAssumptionSerializer());
        addSerializer(new ConcreteSubtypeAssumptionSerializer());
        addSerializer(new LeafTypeAssumptionSerializer());
        addSerializer(new ConcreteMethodAssumptionSerializer());
        addSerializer(new CallSiteTargetValueAssumptionSerializer());
        addSerializer(new UnresolvedJavaTypeSerializer());
        addSerializer(new UnresolvedJavaMethodSerializer());
        addSerializer(new UnresolvedJavaFieldSerializer());
        addSerializer(new PrimitiveConstantSerializer());
        addSerializer(new ForeignCallDescriptorSerializer());
        addSerializer(new EncodedSpeculationReasonSerializer());
        addSerializer(new HotSpotSpeculationSerializer());
        addSerializer(new JavaTypeProfileSerializer());
        addSerializer(new ProfiledTypeSerializer());
        addSerializer(new BitSetSerializer());
        addSerializer(new VMFieldSerializer());
        addSerializer(new RecordedCompilationUnitSerializer());
        addSerializer(new RecordedForeignCallLinkagesSerializer());
        addSerializer(new RecordedForeignCallLinkageSerializer());
        addSerializer(new RegisterValueSerializer());
        addSerializer(new EnumSetSerializer());
        addSerializer(new ArchitectureSerializer());
        addSerializer(new TargetDescriptionSerializer());
        addSerializer(new RegisterConfigSerializer(hostPlatform, hostTarget));
        addSerializer(new ExceptionHandlerSerializer());
        addSerializer(new ThrowableSerializer());
    }

    private void addSerializer(ObjectSerializer serializer) {
        tagSerializers.put(serializer.tag(), serializer);
        if (serializer.serializesSubclasses()) {
            interfaceSerializers.put(serializer.clazz(), serializer);
        } else {
            exactClassSerializers.put(serializer.clazz(), serializer);
        }
    }

    private final RecursiveSerializer recursiveSerializer = new RecursiveSerializer() {
        @Override
        public void serialize(Object instance, JsonBuilder.ValueBuilder valueBuilder) throws IOException {
            if (instance == null) {
                valueBuilder.value(null);
                return;
            }
            ObjectSerializer serializer = exactClassSerializers.get(instance.getClass());
            if (serializer == null) {
                var cursor = interfaceSerializers.getEntries();
                while (cursor.advance()) {
                    if (cursor.getKey().isInstance(instance)) {
                        serializer = cursor.getValue();
                        exactClassSerializers.put(instance.getClass(), serializer);
                        break;
                    }
                }
                if (serializer == null) {
                    throw new IllegalArgumentException("No serializer for " + instance.getClass() + ": " + instance);
                }
            }
            try (JsonBuilder.ObjectBuilder builder = valueBuilder.object()) {
                builder.append("tag", serializer.tag());
                serializer.serialize(instance, builder, this);
            }
        }

        @Override
        public void serialize(Object instance, JsonBuilder.ValueBuilder valueBuilder, String tag) throws IOException {
            if (instance == null) {
                valueBuilder.value(null);
                return;
            }
            ObjectSerializer serializer = tagSerializers.get(tag);
            if (serializer == null) {
                throw new IllegalArgumentException("No serializer registered for the given tag " + tag);
            }
            try (JsonBuilder.ObjectBuilder builder = valueBuilder.object()) {
                serializer.serialize(instance, builder, this);
            }
        }
    };

    /**
     * Dumps the given compilation unit to the given writer.
     *
     * @param compilationUnit the compilation unit
     * @param writer the writer
     * @throws IOException if dumping fails
     */
    public void dump(RecordedCompilationUnit compilationUnit, JsonWriter writer) throws IOException {
        recursiveSerializer.serialize(compilationUnit, writer.valueBuilder(), RecordedCompilationUnitSerializer.TAG);
    }

    private RecursiveDeserializer createRecursiveDeserializer() {
        return new RecursiveDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public Object deserialize(Object json, ProxyFactory proxyFactory) throws DeserializationException {
                if (json instanceof EconomicMap<?, ?>) {
                    EconomicMap<String, Object> map = (EconomicMap<String, Object>) json;
                    String tag = (String) map.get("tag");
                    if (tag == null) {
                        throw new IllegalArgumentException("The JSON map does not contain a tag: " + map);
                    }
                    ObjectSerializer deserializer = tagSerializers.get(tag);
                    if (deserializer == null) {
                        throw new IllegalArgumentException("No deserializer registered for tag " + tag);
                    }
                    return deserializer.deserialize(map, this, proxyFactory);
                } else {
                    return json;
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            public Object deserialize(Object json, ProxyFactory proxyFactory, String tag) throws DeserializationException {
                if (json instanceof EconomicMap<?, ?>) {
                    EconomicMap<String, Object> map = (EconomicMap<String, Object>) json;
                    ObjectSerializer deserializer = tagSerializers.get(tag);
                    if (deserializer == null) {
                        throw new IllegalArgumentException("No deserializer registered for tag " + tag);
                    }
                    return deserializer.deserialize(map, this, proxyFactory);
                } else {
                    throw new IllegalArgumentException("Expected a map.");
                }
            }

            private Architecture architecture;

            @Override
            public void setArchitecture(Architecture arch) {
                architecture = arch;
            }

            @Override
            public Architecture getArchitecture() {
                return architecture;
            }
        };
    }

    /**
     * Loads a recorded compilation unit from the given reader.
     *
     * @param source the reader
     * @param proxyFactory the proxy factory
     * @return the loaded compilation unit
     * @throws IOException if loading fails
     * @throws DeserializationException if deserialization fails
     */
    public RecordedCompilationUnit load(Reader source, ProxyFactory proxyFactory) throws IOException, DeserializationException {
        JsonParser parser = new JsonParser(source);
        return (RecordedCompilationUnit) createRecursiveDeserializer().deserialize(parser.parse(), proxyFactory, RecordedCompilationUnitSerializer.TAG);
    }
}
