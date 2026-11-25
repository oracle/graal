/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.Platform;
import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxy;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.ObjectCopierInputStream;
import jdk.graal.compiler.util.ObjectCopierOutputStream;
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
 * Encodes and decodes {@link RecordedCompilationUnit recorded compilation units} in the compact
 * binary replay format.
 * <p>
 * The binary format uses a versioned tag-based encoding with shared tables.
 *
 * @see ReplayCompilationSupport
 */
public final class BinaryReplayCodec {
    private static final byte[] MAGIC = {'R', 'C', 'P', 'C'};
    private static final int VERSION = 1;

    private static final int NULL_TAG = 0;
    private static final int FALSE_TAG = 1;
    private static final int TRUE_TAG = 2;
    private static final int BYTE_TAG = 3;
    private static final int SHORT_TAG = 4;
    private static final int INT_TAG = 5;
    private static final int LONG_TAG = 6;
    private static final int FLOAT_TAG = 7;
    private static final int DOUBLE_TAG = 8;
    private static final int STRING_TAG = 9;
    private static final int SPECIAL_SINGLETON_TAG = 10;
    private static final int REGISTERED_SINGLETON_TAG = 11;
    private static final int REGISTERED_INSTANCE_TAG = 12;
    private static final int CLASS_TAG = 13;
    private static final int ENUM_TAG = 14;
    private static final int OBJECT_ARRAY_TAG = 15;
    private static final int LIST_TAG = 16;
    private static final int BYTE_ARRAY_TAG = 17;
    private static final int DOUBLE_ARRAY_TAG = 18;
    private static final int RESULT_MARKER_TAG = 19;
    private static final int STACK_TRACE_ELEMENT_TAG = 20;
    private static final int REGISTER_TAG = 21;
    private static final int FIELD_TAG = 22;
    private static final int ASSUMPTION_RESULT_TAG = 23;
    private static final int ASSUMPTION_TAG = 24;
    private static final int UNRESOLVED_JAVA_TYPE_TAG = 25;
    private static final int UNRESOLVED_JAVA_METHOD_TAG = 26;
    private static final int UNRESOLVED_JAVA_FIELD_TAG = 27;
    private static final int PRIMITIVE_CONSTANT_TAG = 28;
    private static final int FOREIGN_CALL_DESCRIPTOR_TAG = 29;
    private static final int ENCODED_SPECULATION_REASON_TAG = 30;
    private static final int HOTSPOT_SPECULATION_TAG = 31;
    private static final int JAVA_TYPE_PROFILE_TAG = 32;
    private static final int PROFILED_TYPE_TAG = 33;
    private static final int BITSET_TAG = 34;
    private static final int VM_FIELD_TAG = 35;
    private static final int REGISTER_VALUE_TAG = 36;
    private static final int ENUM_SET_TAG = 37;
    private static final int ARCHITECTURE_TAG = 38;
    private static final int TARGET_DESCRIPTION_TAG = 39;
    private static final int REGISTER_CONFIG_TAG = 40;
    private static final int EXCEPTION_HANDLER_TAG = 41;
    private static final int THROWABLE_TAG = 42;
    private static final int COMPILATION_TASK_EXCEPTION_TAG = 43;
    private static final int RECORDED_TASK_ARTIFACTS_TAG = 44;

    private static final int SINGLETON_NULL_POINTER = 0;
    private static final int SINGLETON_COMPRESSED_NULL = 1;
    private static final int SINGLETON_NO_SPECULATION = 2;
    private static final int SINGLETON_ILLEGAL_VALUE_KIND = 3;

    private static final int RESULT_NO_RESULT = 0;
    private static final int RESULT_NULL = 1;
    private static final int RESULT_EXCEPTION = 2;

    private static final int ASSUMPTION_NO_FINALIZABLE_SUBCLASS = 0;
    private static final int ASSUMPTION_CONCRETE_SUBTYPE = 1;
    private static final int ASSUMPTION_LEAF_TYPE = 2;
    private static final int ASSUMPTION_CONCRETE_METHOD = 3;
    private static final int ASSUMPTION_CALL_SITE_TARGET_VALUE = 4;

    private static final int AMD64_ARCHITECTURE = 0;
    private static final int RISCV64_ARCHITECTURE = 1;
    private static final int AARCH64_ARCHITECTURE = 2;

    private final CompilerInterfaceDeclarations declarations;
    private final TargetDescription hostTarget;
    private final boolean hostWindowsOS;
    private final CompilerInterfaceDeclarations.Registration[] registrations;
    private final EconomicMap<Class<?>, Integer> registrationIndices;

    /**
     * Constructs a codec for binary replay files.
     *
     * @param declarations the compiler-interface declarations used to encode and decode registered
     *            JVMCI objects
     * @param hostPlatform the current host platform, used for host-dependent values such as
     *            register configurations
     * @param hostTarget the current host target, used for host-dependent values such as register
     *            configurations
     */
    public BinaryReplayCodec(CompilerInterfaceDeclarations declarations, Platform hostPlatform, TargetDescription hostTarget) {
        this.declarations = declarations;
        this.hostTarget = hostTarget;
        this.hostWindowsOS = hostPlatform.osName().equals("windows");
        ArrayList<CompilerInterfaceDeclarations.Registration> registrationList = new ArrayList<>();
        this.registrationIndices = EconomicMap.create();
        for (CompilerInterfaceDeclarations.Registration registration : declarations.getRegistrations()) {
            registrationIndices.put(registration.clazz(), registrationList.size());
            registrationList.add(registration);
        }
        this.registrations = registrationList.toArray(CompilerInterfaceDeclarations.Registration[]::new);
    }

    private final class WriteState {
        private final EconomicMap<String, Integer> stringIndices = EconomicMap.create();
        private final EconomicMap<CompilationProxy.SymbolicMethod, Integer> methodIndices = EconomicMap.create();
        @SuppressWarnings("unchecked") private final EconomicMap<Object, Integer>[] registrationInstanceIds = (EconomicMap<Object, Integer>[]) new EconomicMap<?, ?>[registrations.length];
        private int nextStringId = 1;
        private int nextMethodId;

        private Integer stringIndex(String value) {
            return stringIndices.get(value);
        }

        private int defineString(String value) {
            int id = nextStringId++;
            stringIndices.put(value, id);
            return id;
        }

        private Integer methodIndex(CompilationProxy.SymbolicMethod method) {
            return methodIndices.get(method);
        }

        private int defineMethod(CompilationProxy.SymbolicMethod method) {
            int id = nextMethodId++;
            methodIndices.put(method, id);
            return id;
        }

        private int instanceId(int registrationIndex, Object value) {
            EconomicMap<Object, Integer> ids = registrationInstanceIds[registrationIndex];
            if (ids == null) {
                ids = EconomicMap.create();
                registrationInstanceIds[registrationIndex] = ids;
            }
            Integer existing = ids.get(value);
            if (existing != null) {
                return existing;
            }
            int id = ids.size();
            ids.put(value, id);
            return id;
        }
    }

    private final class ReadState {
        private final CompilationProxies.ProxyFactory proxyFactory;
        private final ArrayList<String> stringTable = new ArrayList<>();
        private final ArrayList<CompilationProxy.SymbolicMethod> methodTable = new ArrayList<>();
        private final Object[] singletonProxies = new Object[registrations.length];

        @SuppressWarnings("unchecked") private final EconomicMap<Integer, Object>[] instanceProxies = (EconomicMap<Integer, Object>[]) new EconomicMap<?, ?>[registrations.length];

        private Architecture architecture = hostTarget.arch;

        private ReadState(CompilationProxies.ProxyFactory proxyFactory) {
            this.proxyFactory = proxyFactory;
            stringTable.add(null);
        }

        private String stringAt(int index) throws IOException {
            if (index < 0 || index >= stringTable.size()) {
                throw new IOException("Invalid replay string table index " + index);
            }
            return stringTable.get(index);
        }

        private void defineString(int index, String value) throws IOException {
            if (index != stringTable.size()) {
                throw new IOException("Invalid replay string definition index " + index);
            }
            stringTable.add(value);
        }

        private CompilationProxy.SymbolicMethod methodAt(int index) throws IOException {
            if (index < 0 || index >= methodTable.size()) {
                throw new IOException("Invalid replay symbolic method index " + index);
            }
            return methodTable.get(index);
        }

        private void defineMethod(int index, CompilationProxy.SymbolicMethod method) throws IOException {
            if (index != methodTable.size()) {
                throw new IOException("Invalid replay symbolic method definition index " + index);
            }
            methodTable.add(method);
        }

        private CompilerInterfaceDeclarations.Registration registrationAt(int index) throws IOException {
            if (index < 0 || index >= registrations.length) {
                throw new IOException("Invalid replay registration index " + index);
            }
            return registrations[index];
        }

        private Object singletonProxy(int registrationIndex) throws IOException {
            CompilerInterfaceDeclarations.Registration registration = registrationAt(registrationIndex);
            if (!registration.singleton()) {
                throw new IOException("Registration " + registration.clazz().getName() + " is not a singleton");
            }
            Object proxy = singletonProxies[registrationIndex];
            if (proxy == null) {
                proxy = proxyFactory.createProxy(registration);
                singletonProxies[registrationIndex] = proxy;
            }
            return proxy;
        }

        private Object instanceProxy(int registrationIndex, int id) throws IOException {
            CompilerInterfaceDeclarations.Registration registration = registrationAt(registrationIndex);
            if (registration.singleton()) {
                throw new IOException("Registration " + registration.clazz().getName() + " is a singleton");
            }
            EconomicMap<Integer, Object> proxies = instanceProxies[registrationIndex];
            if (proxies == null) {
                proxies = EconomicMap.create();
                instanceProxies[registrationIndex] = proxies;
            }
            Object proxy = proxies.get(id);
            if (proxy == null) {
                proxy = proxyFactory.createProxy(registration);
                proxies.put(id, proxy);
            }
            return proxy;
        }

        private Architecture registerArchitecture() {
            return (architecture != null) ? architecture : hostTarget.arch;
        }

        private void setArchitecture(Architecture newArchitecture) {
            architecture = newArchitecture;
        }

        private void setArchitectureFromPlatformName(String archName) {
            architecture = createFallbackArchitecture(archName);
        }
    }

    private static final class ForeignCallDescriptorSurrogate {
    }

    /**
     * Writes a recorded compilation unit in binary replay format.
     *
     * @param compilationUnit the compilation unit to encode
     * @param output the destination stream
     * @throws IOException if encoding fails
     */
    public void write(RecordedCompilationUnit compilationUnit, OutputStream output) throws IOException {
        try (ObjectCopierOutputStream out = new ObjectCopierOutputStream(output, null)) {
            out.write(MAGIC);
            out.writePackedUnsignedInt(VERSION);
            writeCompilationUnit(out, compilationUnit, new WriteState());
        }
    }

    /**
     * Reads a recorded compilation unit from binary replay format.
     *
     * @param input the source stream
     * @param proxyFactory the factory used to recreate registered compiler-interface proxies
     * @return the decoded compilation unit
     * @throws IOException if reading fails or the replay file is malformed
     */
    public RecordedCompilationUnit read(InputStream input, CompilationProxies.ProxyFactory proxyFactory) throws IOException {
        try (ObjectCopierInputStream in = new ObjectCopierInputStream(input)) {
            verifyHeader(in);
            return readCompilationUnit(in, new ReadState(proxyFactory));
        }
    }

    private static void writeStringReference(ObjectCopierOutputStream out, String value, WriteState state) throws IOException {
        if (value == null) {
            // String table index 0 is reserved for null.
            out.writePackedUnsignedInt(0);
            return;
        }
        Integer existing = state.stringIndex(value);
        if (existing != null) {
            out.writePackedUnsignedInt(existing << 1);
            return;
        }
        int index = state.defineString(value);
        out.writePackedUnsignedInt((index << 1) | 1);
        out.writeStringValue(value);
    }

    private static String readStringReference(ObjectCopierInputStream in, ReadState state) throws IOException {
        int encoded = in.readPackedUnsignedInt();
        int index = encoded >>> 1;
        if ((encoded & 1) == 0) {
            return state.stringAt(index);
        }
        String value = in.readStringValue();
        state.defineString(index, value);
        return value;
    }

    private static void writeMethodReference(ObjectCopierOutputStream out, CompilationProxy.SymbolicMethod method, WriteState state) throws IOException {
        Integer existing = state.methodIndex(method);
        if (existing != null) {
            out.writePackedUnsignedInt(existing << 1);
            return;
        }
        int index = state.defineMethod(method);
        out.writePackedUnsignedInt((index << 1) | 1);
        String[] names = method.methodAndParamNames();
        out.writePackedUnsignedInt(names.length);
        for (String name : names) {
            writeStringReference(out, name, state);
        }
    }

    private static CompilationProxy.SymbolicMethod readMethodReference(ObjectCopierInputStream in, ReadState state) throws IOException {
        int encoded = in.readPackedUnsignedInt();
        int index = encoded >>> 1;
        if ((encoded & 1) == 0) {
            return state.methodAt(index);
        }
        int length = in.readPackedUnsignedInt();
        String[] names = new String[length];
        for (int i = 0; i < length; i++) {
            names[i] = requireNonNullString(readStringReference(in, state), "symbolic method name");
        }
        CompilationProxy.SymbolicMethod method = new CompilationProxy.SymbolicMethod(names);
        state.defineMethod(index, method);
        return method;
    }

    private void writeCompilationUnit(ObjectCopierOutputStream out, RecordedCompilationUnit compilationUnit, WriteState state) throws IOException {
        writeValue(out, compilationUnit.request().getMethod(), state);
        writeStringReference(out, compilationUnit.platform().osName(), state);
        writeStringReference(out, compilationUnit.platform().archName(), state);
        writeStringReference(out, compilationUnit.compilerConfiguration(), state);
        writeBooleanFlag(out, compilationUnit.isLibgraal());
        writeProperties(out, compilationUnit.properties(), state);
        out.writePackedSignedInt(compilationUnit.request().getEntryBCI());
        out.writePackedUnsignedInt(compilationUnit.request().getId());
        out.writePackedUnsignedInt(compilationUnit.operations().size());
        for (OperationRecorder.RecordedOperation operation : compilationUnit.operations()) {
            writeValue(out, operation.receiver(), state);
            writeMethodReference(out, operation.method(), state);
            Object[] args = operation.args();
            out.writePackedUnsignedInt(args == null ? 0 : args.length + 1);
            if (args != null) {
                for (Object arg : args) {
                    writeValue(out, arg, state);
                }
            }
            writeValue(out, operation.resultOrMarker(), state);
        }
        var linkages = compilationUnit.linkages().linkages();
        out.writePackedUnsignedInt(linkages.size());
        var linkageCursor = linkages.getEntries();
        while (linkageCursor.advance()) {
            writeStringReference(out, linkageCursor.getKey(), state);
            out.writePackedSignedLong(linkageCursor.getValue().address());
            writeValue(out, linkageCursor.getValue().temporaries(), state);
        }
        CompilationTaskProduct product = compilationUnit.product();
        if (product instanceof CompilationTaskProduct.CompilationTaskArtifacts artifacts) {
            product = artifacts.asRecordedArtifacts();
        }
        writeValue(out, product, state);
    }

    private static RecordedCompilationUnit readCompilationUnit(ObjectCopierInputStream in, ReadState state) throws IOException {
        HotSpotResolvedJavaMethod method = (HotSpotResolvedJavaMethod) readValue(in, state);
        String osName = requireNonNullString(readStringReference(in, state), "osName");
        String archName = requireNonNullString(readStringReference(in, state), "archName");
        state.setArchitectureFromPlatformName(archName);
        String compilerConfiguration = requireNonNullString(readStringReference(in, state), "compilerConfiguration");
        boolean isLibgraal = readBooleanFlag(in);
        Map<String, String> properties = readProperties(in, state);
        int entryBCI = in.readPackedSignedInt();
        int compileId = in.readPackedUnsignedInt();
        int operationCount = in.readPackedUnsignedInt();
        ArrayList<OperationRecorder.RecordedOperation> operations = new ArrayList<>(operationCount);
        for (int i = 0; i < operationCount; i++) {
            Object receiver = readValue(in, state);
            CompilationProxy.SymbolicMethod symbolicMethod = readMethodReference(in, state);
            int encodedArgCount = in.readPackedUnsignedInt();
            Object[] args;
            if (encodedArgCount == 0) {
                args = null;
            } else {
                int argCount = encodedArgCount - 1;
                args = new Object[argCount];
                for (int j = 0; j < argCount; j++) {
                    args[j] = readValue(in, state);
                }
            }
            Object result = readValue(in, state);
            operations.add(new OperationRecorder.RecordedOperation(receiver, symbolicMethod, args, result));
        }
        int linkageCount = in.readPackedUnsignedInt();
        EconomicMap<String, RecordedForeignCallLinkages.RecordedForeignCallLinkage> linkages = EconomicMap.create(linkageCount);
        for (int i = 0; i < linkageCount; i++) {
            String name = requireNonNullString(readStringReference(in, state), "foreign call linkage");
            long address = in.readPackedSignedLong();
            Value[] temporaries = (Value[]) readValue(in, state);
            linkages.put(name, new RecordedForeignCallLinkages.RecordedForeignCallLinkage(address, temporaries));
        }
        CompilationTaskProduct product = (CompilationTaskProduct) readValue(in, state);
        Platform platform = new Platform(osName, archName);
        return new RecordedCompilationUnit(new HotSpotCompilationRequest(method, entryBCI, 0L, compileId), compilerConfiguration, isLibgraal, platform, properties,
                        new RecordedForeignCallLinkages(linkages), product, operations);
    }

    private void writeValue(ObjectCopierOutputStream out, Object value, WriteState state) throws IOException {
        if (value == null) {
            out.write(NULL_TAG);
            return;
        }
        switch (value) {
            case Boolean bool -> {
                out.write(bool ? TRUE_TAG : FALSE_TAG);
            }
            case Byte byteValue -> {
                out.write(BYTE_TAG);
                out.writeByteValue(byteValue);
            }
            case Short shortValue -> {
                out.write(SHORT_TAG);
                out.writeShort(shortValue);
            }
            case Integer intValue -> {
                out.write(INT_TAG);
                out.writePackedSignedInt(intValue);
            }
            case Long longValue -> {
                out.write(LONG_TAG);
                out.writePackedSignedLong(longValue);
            }
            case Float floatValue -> {
                out.write(FLOAT_TAG);
                out.writeFloatPrimitive(floatValue);
            }
            case Double doubleValue -> {
                out.write(DOUBLE_TAG);
                out.writeDoublePrimitive(doubleValue);
            }
            case String string -> {
                out.write(STRING_TAG);
                writeStringReference(out, string, state);
            }
            case Class<?> clazz -> {
                out.write(CLASS_TAG);
                writeStringReference(out, clazz.getName(), state);
            }
            case ReplayTypeSupport.ClassSurrogate surrogate -> {
                out.write(CLASS_TAG);
                writeStringReference(out, surrogate.name(), state);
            }
            case Enum<?> en -> {
                out.write(ENUM_TAG);
                writeStringReference(out, en.getDeclaringClass().getName(), state);
                writeStringReference(out, en.name(), state);
            }
            case Object[] array -> {
                out.write(OBJECT_ARRAY_TAG);
                writeValue(out, arrayComponentType(array), state);
                out.writePackedUnsignedInt(array.length);
                for (Object element : array) {
                    writeValue(out, element, state);
                }
            }
            case List<?> list -> {
                out.write(LIST_TAG);
                out.writePackedUnsignedInt(list.size());
                for (Object element : list) {
                    writeValue(out, element, state);
                }
            }
            case byte[] bytes -> {
                out.write(BYTE_ARRAY_TAG);
                out.writeByteArrayValue(bytes);
            }
            case double[] doubles -> {
                out.write(DOUBLE_ARRAY_TAG);
                out.writePackedUnsignedInt(doubles.length);
                for (double d : doubles) {
                    out.writeDoublePrimitive(d);
                }
            }
            case SpecialResultMarker.ExceptionThrownMarker marker -> {
                out.write(RESULT_MARKER_TAG);
                out.writePackedUnsignedInt(RESULT_EXCEPTION);
                writeValue(out, marker.getThrown(), state);
            }
            case SpecialResultMarker marker -> {
                out.write(RESULT_MARKER_TAG);
                int markerKind;
                if (marker == SpecialResultMarker.NO_RESULT_MARKER) {
                    markerKind = RESULT_NO_RESULT;
                } else if (marker == SpecialResultMarker.NULL_RESULT_MARKER) {
                    markerKind = RESULT_NULL;
                } else {
                    throw new IllegalArgumentException("Unexpected marker " + marker);
                }
                out.writePackedUnsignedInt(markerKind);
            }
            case StackTraceElement ste -> {
                out.write(STACK_TRACE_ELEMENT_TAG);
                writeStringReference(out, ste.getClassLoaderName(), state);
                writeStringReference(out, ste.getModuleName(), state);
                writeStringReference(out, ste.getModuleVersion(), state);
                writeStringReference(out, ste.getClassName(), state);
                writeStringReference(out, ste.getMethodName(), state);
                writeStringReference(out, ste.getFileName(), state);
                out.writePackedSignedInt(ste.getLineNumber());
            }
            case Register register -> {
                out.write(REGISTER_TAG);
                out.writePackedUnsignedInt(register.number);
            }
            case Field field -> {
                out.write(FIELD_TAG);
                writeStringReference(out, field.getDeclaringClass().getName(), state);
                writeStringReference(out, field.getName(), state);
            }
            case Assumptions.AssumptionResult<?> assumptionResult -> {
                out.write(ASSUMPTION_RESULT_TAG);
                writeValue(out, assumptionResult.getResult(), state);
                Assumptions assumptions = new Assumptions();
                assumptionResult.recordTo(assumptions);
                ArrayList<Assumptions.Assumption> collectedAssumptions = new ArrayList<>();
                for (Assumptions.Assumption assumption : assumptions) {
                    collectedAssumptions.add(assumption);
                }
                out.writePackedUnsignedInt(collectedAssumptions.size());
                for (Assumptions.Assumption assumption : collectedAssumptions) {
                    writeValue(out, assumption, state);
                }
            }
            case Assumptions.NoFinalizableSubclass assumption -> {
                out.write(ASSUMPTION_TAG);
                out.writePackedUnsignedInt(ASSUMPTION_NO_FINALIZABLE_SUBCLASS);
                writeValue(out, assumption.receiverType, state);
            }
            case Assumptions.ConcreteSubtype assumption -> {
                out.write(ASSUMPTION_TAG);
                out.writePackedUnsignedInt(ASSUMPTION_CONCRETE_SUBTYPE);
                writeValue(out, assumption.subtype, state);
                writeValue(out, assumption.context, state);
            }
            case Assumptions.LeafType assumption -> {
                out.write(ASSUMPTION_TAG);
                out.writePackedUnsignedInt(ASSUMPTION_LEAF_TYPE);
                writeValue(out, assumption.context, state);
            }
            case Assumptions.ConcreteMethod assumption -> {
                out.write(ASSUMPTION_TAG);
                out.writePackedUnsignedInt(ASSUMPTION_CONCRETE_METHOD);
                writeValue(out, assumption.context, state);
                writeValue(out, assumption.method, state);
                writeValue(out, assumption.impl, state);
            }
            case Assumptions.CallSiteTargetValue assumption -> {
                out.write(ASSUMPTION_TAG);
                out.writePackedUnsignedInt(ASSUMPTION_CALL_SITE_TARGET_VALUE);
                writeValue(out, assumption.callSite, state);
                writeValue(out, assumption.methodHandle, state);
            }
            case UnresolvedJavaType type -> {
                out.write(UNRESOLVED_JAVA_TYPE_TAG);
                writeStringReference(out, type.getName(), state);
            }
            case UnresolvedJavaMethod method -> {
                out.write(UNRESOLVED_JAVA_METHOD_TAG);
                writeStringReference(out, method.getName(), state);
                writeValue(out, method.getSignature(), state);
                writeValue(out, method.getDeclaringClass(), state);
            }
            case UnresolvedJavaField field -> {
                out.write(UNRESOLVED_JAVA_FIELD_TAG);
                writeStringReference(out, field.getName(), state);
                writeValue(out, field.getType(), state);
                writeValue(out, field.getDeclaringClass(), state);
            }
            case PrimitiveConstant constant -> {
                out.write(PRIMITIVE_CONSTANT_TAG);
                out.writePackedSignedLong(constant.getRawValue());
                writeValue(out, constant.getJavaKind(), state);
            }
            case ForeignCallDescriptor ignored -> out.write(FOREIGN_CALL_DESCRIPTOR_TAG);
            case ForeignCallDescriptorSurrogate ignored ->
                out.write(FOREIGN_CALL_DESCRIPTOR_TAG);
            case EncodedSpeculationReason reason -> {
                out.write(ENCODED_SPECULATION_REASON_TAG);
                writeStringReference(out, reason.getGroupName(), state);
                out.writePackedSignedInt(reason.getGroupId());
                writeValue(out, reason.getContext(), state);
            }
            case HotSpotSpeculationLog.HotSpotSpeculation speculation -> {
                out.write(HOTSPOT_SPECULATION_TAG);
                writeValue(out, speculation.getReason(), state);
                writeValue(out, speculation.getReasonEncoding(), state);
                writeValue(out, speculation.getEncoding(), state);
            }
            case JavaTypeProfile typeProfile -> {
                out.write(JAVA_TYPE_PROFILE_TAG);
                writeValue(out, typeProfile.getNullSeen(), state);
                out.writeDoublePrimitive(typeProfile.getNotRecordedProbability());
                out.writePackedUnsignedInt(typeProfile.getTypes().length);
                for (JavaTypeProfile.ProfiledType profiledType : typeProfile.getTypes()) {
                    writeValue(out, profiledType, state);
                }
            }
            case JavaTypeProfile.ProfiledType profiledType -> {
                out.write(PROFILED_TYPE_TAG);
                writeValue(out, profiledType.getType(), state);
                out.writeDoublePrimitive(profiledType.getProbability());
            }
            case BitSet bitSet -> {
                out.write(BITSET_TAG);
                long[] words = bitSet.toLongArray();
                out.writePackedUnsignedInt(words.length);
                for (long word : words) {
                    out.writePackedSignedLong(word);
                }
            }
            case VMField vmField -> {
                out.write(VM_FIELD_TAG);
                writeStringReference(out, vmField.name, state);
                writeStringReference(out, vmField.type, state);
                out.writePackedSignedLong(vmField.offset);
                out.writePackedSignedLong(vmField.address);
                writeValue(out, vmField.value, state);
            }
            case RegisterValue registerValue -> {
                out.write(REGISTER_VALUE_TAG);
                writeValue(out, registerValue.getRegister(), state);
                writeValue(out, registerValue.getValueKind(), state);
            }
            case EnumSet<?> enumSet -> {
                out.write(ENUM_SET_TAG);
                writeStringReference(out, ReplayTypeSupport.enumSetElementType(enumSet).getName(), state);
                out.writePackedUnsignedInt(enumSet.size());
                for (Enum<?> constant : enumSet) {
                    out.writePackedUnsignedInt(constant.ordinal());
                }
            }
            case Architecture architecture -> {
                out.write(ARCHITECTURE_TAG);
                out.writePackedUnsignedInt(architectureKind(architecture));
                writeArchitectureFeatures(out, architecture);
            }
            case TargetDescription targetDescription -> {
                out.write(TARGET_DESCRIPTION_TAG);
                writeValue(out, targetDescription.arch, state);
                writeBooleanFlag(out, targetDescription.isMP);
                out.writePackedSignedInt(targetDescription.stackAlignment);
                out.writePackedSignedInt(targetDescription.implicitNullCheckLimit);
                writeBooleanFlag(out, targetDescription.inlineObjects);
            }
            case RegisterConfig registerConfig -> {
                out.write(REGISTER_CONFIG_TAG);
                out.writePackedUnsignedInt(registerConfigKind(registerConfig));
                writeValue(out, hostTarget, state);
                writeValue(out, registerConfig.getAllocatableRegisters(), state);
                writeBooleanFlag(out, hostWindowsOS);
            }
            case ExceptionHandler handler -> {
                out.write(EXCEPTION_HANDLER_TAG);
                out.writePackedSignedInt(handler.getStartBCI());
                out.writePackedSignedInt(handler.getEndBCI());
                out.writePackedSignedInt(handler.getHandlerBCI());
                out.writePackedSignedInt(handler.catchTypeCPI());
                writeValue(out, handler.getCatchType(), state);
            }
            case Throwable throwable -> {
                out.write(THROWABLE_TAG);
                writeStringReference(out, throwable.getClass().getName(), state);
                writeStringReference(out, throwable.getMessage(), state);
            }
            case CompilationTaskProduct.CompilationTaskException taskException -> {
                out.write(COMPILATION_TASK_EXCEPTION_TAG);
                writeStringReference(out, taskException.className(), state);
                writeStringReference(out, taskException.stackTrace(), state);
            }
            case CompilationTaskProduct.RecordedCompilationTaskArtifacts artifacts -> {
                out.write(RECORDED_TASK_ARTIFACTS_TAG);
                writeStringReference(out, artifacts.finalGraph(), state);
            }
            case CompilationTaskProduct.CompilationTaskArtifacts artifacts ->
                writeValue(out, artifacts.asRecordedArtifacts(), state);
            default -> {
                if (JavaConstant.NULL_POINTER.equals(value)) {
                    out.write(SPECIAL_SINGLETON_TAG);
                    out.writePackedUnsignedInt(SINGLETON_NULL_POINTER);
                    return;
                }
                if (HotSpotCompressedNullConstant.COMPRESSED_NULL.equals(value)) {
                    out.write(SPECIAL_SINGLETON_TAG);
                    out.writePackedUnsignedInt(SINGLETON_COMPRESSED_NULL);
                    return;
                }
                if (value == SpeculationLog.NO_SPECULATION) {
                    out.write(SPECIAL_SINGLETON_TAG);
                    out.writePackedUnsignedInt(SINGLETON_NO_SPECULATION);
                    return;
                }
                if (value == ValueKind.Illegal) {
                    out.write(SPECIAL_SINGLETON_TAG);
                    out.writePackedUnsignedInt(SINGLETON_ILLEGAL_VALUE_KIND);
                    return;
                }
                CompilerInterfaceDeclarations.Registration registration = declarations.isRegisteredClassInstance(value) ? declarations.findRegistrationForInstance(value) : null;
                if (registration != null) {
                    int registrationIndex = registrationIndex(registration);
                    if (registration.singleton()) {
                        out.write(REGISTERED_SINGLETON_TAG);
                        out.writePackedUnsignedInt(registrationIndex);
                    } else {
                        out.write(REGISTERED_INSTANCE_TAG);
                        out.writePackedUnsignedInt(registrationIndex);
                        out.writePackedUnsignedInt(state.instanceId(registrationIndex, value));
                    }
                    return;
                }
                throw new IllegalArgumentException("No serializer for " + value.getClass() + ": " + value);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object readValue(ObjectCopierInputStream in, ReadState state) throws IOException {
        int tag = in.read();
        if (tag < 0) {
            throw new EOFException();
        }
        return switch (tag) {
            case NULL_TAG -> null;
            case FALSE_TAG -> false;
            case TRUE_TAG -> true;
            case BYTE_TAG -> in.readByteValue();
            case SHORT_TAG -> in.readShort();
            case INT_TAG -> in.readPackedSignedInt();
            case LONG_TAG -> in.readPackedSignedLong();
            case FLOAT_TAG -> in.readFloatPrimitive();
            case DOUBLE_TAG -> in.readDoublePrimitive();
            case STRING_TAG -> readStringReference(in, state);
            case SPECIAL_SINGLETON_TAG -> readSpecialSingleton(in.readPackedUnsignedInt());
            case REGISTERED_SINGLETON_TAG -> state.singletonProxy(in.readPackedUnsignedInt());
            case REGISTERED_INSTANCE_TAG -> state.instanceProxy(in.readPackedUnsignedInt(), in.readPackedUnsignedInt());
            case CLASS_TAG -> ReplayTypeSupport.decodeClass(requireNonNullString(readStringReference(in, state), "class name"));
            case ENUM_TAG -> {
                var enumClass = (Class<? extends Enum<?>>) (Class<?>) ReplayTypeSupport.classCast(
                                ReplayTypeSupport.decodeClass(requireNonNullString(readStringReference(in, state), "enum class")));
                String constantName = requireNonNullString(readStringReference(in, state), "enum constant");
                Enum<?>[] constants = enumClass.getEnumConstants();
                Enum<?> match = null;
                for (Enum<?> constant : constants) {
                    if (constant.name().equals(constantName)) {
                        match = constant;
                        break;
                    }
                }
                if (match == null) {
                    throw new IOException("Unknown enum constant " + constantName + " for " + enumClass.getName());
                }
                yield match;
            }
            case OBJECT_ARRAY_TAG -> {
                Class<?> component = ReplayTypeSupport.classCast(readValue(in, state));
                int length = in.readPackedUnsignedInt();
                Object[] array = (Object[]) Array.newInstance(component, length);
                for (int i = 0; i < length; i++) {
                    array[i] = readValue(in, state);
                }
                yield array;
            }
            case LIST_TAG -> {
                int length = in.readPackedUnsignedInt();
                ArrayList<Object> list = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    list.add(readValue(in, state));
                }
                yield list;
            }
            case BYTE_ARRAY_TAG -> in.readByteArrayValue();
            case DOUBLE_ARRAY_TAG -> {
                double[] doubles = new double[in.readPackedUnsignedInt()];
                for (int i = 0; i < doubles.length; i++) {
                    doubles[i] = in.readDoublePrimitive();
                }
                yield doubles;
            }
            case RESULT_MARKER_TAG -> switch (in.readPackedUnsignedInt()) {
                case RESULT_NO_RESULT -> SpecialResultMarker.NO_RESULT_MARKER;
                case RESULT_NULL -> SpecialResultMarker.NULL_RESULT_MARKER;
                case RESULT_EXCEPTION ->
                    new SpecialResultMarker.ExceptionThrownMarker((Throwable) readValue(in, state));
                default -> throw new IOException("Unknown replay marker kind");
            };
            case STACK_TRACE_ELEMENT_TAG ->
                new StackTraceElement(readStringReference(in, state), readStringReference(in, state),
                                readStringReference(in, state), requireNonNullString(readStringReference(in, state), "stack trace holder"),
                                requireNonNullString(readStringReference(in, state), "stack trace method"), readStringReference(in, state), in.readPackedSignedInt());
            case REGISTER_TAG -> findRegister(state.registerArchitecture(), in.readPackedUnsignedInt());
            case FIELD_TAG -> {
                Class<?> holder = ReplayTypeSupport.classCast(ReplayTypeSupport.decodeClass(requireNonNullString(readStringReference(in, state), "field holder")));
                String name = requireNonNullString(readStringReference(in, state), "field name");
                try {
                    if (holder == String.class) {
                        if ("coder".equals(name)) {
                            yield String.class.getDeclaredField("coder");
                        } else if ("value".equals(name)) {
                            yield String.class.getDeclaredField("value");
                        }
                    }
                    yield holder.getDeclaredField(name);
                } catch (NoSuchFieldException e) {
                    throw new IOException("Unknown field " + holder.getName() + "." + name, e);
                }
            }
            case ASSUMPTION_RESULT_TAG -> {
                Object result = readValue(in, state);
                int length = in.readPackedUnsignedInt();
                Assumptions.Assumption[] assumptions = new Assumptions.Assumption[length];
                for (int i = 0; i < length; i++) {
                    assumptions[i] = (Assumptions.Assumption) readValue(in, state);
                }
                yield new Assumptions.AssumptionResult<>(result, assumptions);
            }
            case ASSUMPTION_TAG -> switch (in.readPackedUnsignedInt()) {
                case ASSUMPTION_NO_FINALIZABLE_SUBCLASS ->
                    new Assumptions.NoFinalizableSubclass((ResolvedJavaType) readValue(in, state));
                case ASSUMPTION_CONCRETE_SUBTYPE -> {
                    ResolvedJavaType subtype = (ResolvedJavaType) readValue(in, state);
                    ResolvedJavaType context = (ResolvedJavaType) readValue(in, state);
                    yield new DelayedDeserializationObject.ConcreteSubtypeWithDelayedDeserialization(context, subtype);
                }
                case ASSUMPTION_LEAF_TYPE ->
                    new DelayedDeserializationObject.LeafTypeWithDelayedDeserialization((ResolvedJavaType) readValue(in, state));
                case ASSUMPTION_CONCRETE_METHOD -> {
                    ResolvedJavaType context = (ResolvedJavaType) readValue(in, state);
                    ResolvedJavaMethod method = (ResolvedJavaMethod) readValue(in, state);
                    ResolvedJavaMethod impl = (ResolvedJavaMethod) readValue(in, state);
                    yield new Assumptions.ConcreteMethod(method, context, impl);
                }
                case ASSUMPTION_CALL_SITE_TARGET_VALUE ->
                    new Assumptions.CallSiteTargetValue((JavaConstant) readValue(in, state), (JavaConstant) readValue(in, state));
                default -> throw new IOException("Unknown replay assumption kind");
            };
            case UNRESOLVED_JAVA_TYPE_TAG ->
                UnresolvedJavaType.create(requireNonNullString(readStringReference(in, state), "unresolved type name"));
            case UNRESOLVED_JAVA_METHOD_TAG ->
                new UnresolvedJavaMethod(requireNonNullString(readStringReference(in, state), "unresolved method name"),
                                (Signature) readValue(in, state), (JavaType) readValue(in, state));
            case UNRESOLVED_JAVA_FIELD_TAG -> {
                String name = requireNonNullString(readStringReference(in, state), "unresolved field name");
                JavaType type = (JavaType) readValue(in, state);
                JavaType holder = (JavaType) readValue(in, state);
                yield new UnresolvedJavaField(holder, name, type);
            }
            case PRIMITIVE_CONSTANT_TAG -> {
                long raw = in.readPackedSignedLong();
                JavaKind kind = (JavaKind) readValue(in, state);
                yield JavaConstant.forPrimitive(kind, raw);
            }
            case FOREIGN_CALL_DESCRIPTOR_TAG ->
                new ForeignCallDescriptorSurrogate();
            case ENCODED_SPECULATION_REASON_TAG -> {
                String groupName = requireNonNullString(readStringReference(in, state), "speculation group");
                int groupId = in.readPackedSignedInt();
                Object[] context = (Object[]) readValue(in, state);
                yield new EncodedSpeculationReason(groupId, groupName, context);
            }
            case HOTSPOT_SPECULATION_TAG -> {
                SpeculationLog.SpeculationReason reason = (SpeculationLog.SpeculationReason) readValue(in, state);
                byte[] bytes = (byte[]) readValue(in, state);
                JavaConstant encoding = (JavaConstant) readValue(in, state);
                yield new HotSpotSpeculationLog.HotSpotSpeculation(reason, encoding, bytes);
            }
            case JAVA_TYPE_PROFILE_TAG -> {
                TriState nullSeen = (TriState) readValue(in, state);
                double notRecorded = in.readDoublePrimitive();
                int length = in.readPackedUnsignedInt();
                DelayedDeserializationObject.ProfiledTypeWithDelayedDeserialization[] types = new DelayedDeserializationObject.ProfiledTypeWithDelayedDeserialization[length];
                for (int i = 0; i < length; i++) {
                    types[i] = (DelayedDeserializationObject.ProfiledTypeWithDelayedDeserialization) readValue(in, state);
                }
                yield new DelayedDeserializationObject.JavaTypeProfileWithDelayedDeserialization(nullSeen, notRecorded, types);
            }
            case PROFILED_TYPE_TAG ->
                new DelayedDeserializationObject.ProfiledTypeWithDelayedDeserialization((ResolvedJavaType) readValue(in, state), in.readDoublePrimitive());
            case BITSET_TAG -> {
                long[] words = new long[in.readPackedUnsignedInt()];
                for (int i = 0; i < words.length; i++) {
                    words[i] = in.readPackedSignedLong();
                }
                yield BitSet.valueOf(words);
            }
            case VM_FIELD_TAG ->
                new VMField(requireNonNullString(readStringReference(in, state), "vmField name"), requireNonNullString(readStringReference(in, state), "vmField type"),
                                in.readPackedSignedLong(), in.readPackedSignedLong(), readValue(in, state));
            case REGISTER_VALUE_TAG -> ((Register) readValue(in, state)).asValue((ValueKind<?>) readValue(in, state));
            case ENUM_SET_TAG -> {
                Class<? extends Enum<?>> elementType = (Class<? extends Enum<?>>) (Class<?>) ReplayTypeSupport.classCast(ReplayTypeSupport.decodeClass(
                                requireNonNullString(readStringReference(in, state), "enum set element type")));
                int length = in.readPackedUnsignedInt();
                ArrayList<Object> ordinals = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    ordinals.add(in.readPackedUnsignedInt());
                }
                yield ReplayTypeSupport.asEnumSet((Class) elementType, ordinals);
            }
            case ARCHITECTURE_TAG -> {
                Architecture architecture = readArchitecture(in);
                state.setArchitecture(architecture);
                yield architecture;
            }
            case TARGET_DESCRIPTION_TAG ->
                new TargetDescription((Architecture) readValue(in, state), readBooleanFlag(in), in.readPackedSignedInt(), in.readPackedSignedInt(), readBooleanFlag(in));
            case REGISTER_CONFIG_TAG -> readRegisterConfig(in, state);
            case EXCEPTION_HANDLER_TAG ->
                new ExceptionHandler(in.readPackedSignedInt(), in.readPackedSignedInt(), in.readPackedSignedInt(), in.readPackedSignedInt(), (JavaType) readValue(in, state));
            case THROWABLE_TAG ->
                createThrowable(requireNonNullString(readStringReference(in, state), "throwable class"), readStringReference(in, state));
            case COMPILATION_TASK_EXCEPTION_TAG ->
                new CompilationTaskProduct.CompilationTaskException(requireNonNullString(readStringReference(in, state), "task exception class"),
                                requireNonNullString(readStringReference(in, state), "task exception stack trace"));
            case RECORDED_TASK_ARTIFACTS_TAG ->
                new CompilationTaskProduct.RecordedCompilationTaskArtifacts(requireNonNullString(readStringReference(in, state), "final graph"));
            default -> throw new IOException("Unknown replay tag " + tag);
        };
    }

    private static void writeProperties(ObjectCopierOutputStream out, Map<String, String> properties, WriteState state) throws IOException {
        if (properties == null) {
            out.writePackedUnsignedInt(0);
            return;
        }
        out.writePackedUnsignedInt(properties.size() + 1);
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            writeStringReference(out, entry.getKey(), state);
            writeStringReference(out, entry.getValue(), state);
        }
    }

    private static Map<String, String> readProperties(ObjectCopierInputStream in, ReadState state) throws IOException {
        int encodedSize = in.readPackedUnsignedInt();
        if (encodedSize == 0) {
            return null;
        }
        int size = encodedSize - 1;
        Map<String, String> properties = new EconomicHashMap<>();
        for (int i = 0; i < size; i++) {
            String key = requireNonNullString(readStringReference(in, state), "property key");
            String value = readStringReference(in, state);
            properties.put(key, value);
        }
        return properties;
    }

    private static void verifyHeader(ObjectCopierInputStream in) throws IOException {
        for (byte expected : MAGIC) {
            int actual = in.read();
            if (actual < 0) {
                throw new EOFException();
            }
            if (actual != Byte.toUnsignedInt(expected)) {
                throw new IOException("Invalid replay file header");
            }
        }
        int version = in.readPackedUnsignedInt();
        if (version != VERSION) {
            throw new IOException("Unsupported replay file version " + version);
        }
    }

    private static void writeBooleanFlag(ObjectCopierOutputStream out, boolean value) throws IOException {
        out.write(value ? 1 : 0);
    }

    private static boolean readBooleanFlag(ObjectCopierInputStream in) throws IOException {
        int value = in.read();
        if (value < 0) {
            throw new EOFException();
        }
        return switch (value) {
            case 0 -> false;
            case 1 -> true;
            default -> throw new IOException("Invalid replay boolean flag " + value);
        };
    }

    private int registrationIndex(CompilerInterfaceDeclarations.Registration registration) {
        Integer index = registrationIndices.get(registration.clazz());
        if (index == null) {
            throw new IllegalArgumentException("Unknown registration " + registration.clazz().getName());
        }
        return index;
    }

    private static Object readSpecialSingleton(int kind) throws IOException {
        return switch (kind) {
            case SINGLETON_NULL_POINTER -> JavaConstant.NULL_POINTER;
            case SINGLETON_COMPRESSED_NULL -> HotSpotCompressedNullConstant.COMPRESSED_NULL;
            case SINGLETON_NO_SPECULATION -> SpeculationLog.NO_SPECULATION;
            case SINGLETON_ILLEGAL_VALUE_KIND -> ValueKind.Illegal;
            default -> throw new IOException("Unknown replay singleton kind " + kind);
        };
    }

    private Class<?> arrayComponentType(Object[] array) {
        Class<?> componentType = array.getClass().getComponentType();
        Class<?> registeredSupertype = declarations.findRegisteredSupertype(componentType);
        return (registeredSupertype != null) ? registeredSupertype : componentType;
    }

    private static String requireNonNullString(String value, String description) throws IOException {
        if (value == null) {
            throw new IOException("Required replay field " + description + " is null");
        }
        return value;
    }

    private static Register findRegister(Architecture architecture, int number) throws IOException {
        for (Register register : architecture.getRegisters()) {
            if (register.number == number) {
                return register;
            }
        }
        throw new IOException("Register " + number + " not found in " + architecture.getName());
    }

    private static Throwable createThrowable(String className, String message) {
        if (className.equals(JVMCIError.class.getName())) {
            return new JVMCIError(message);
        } else if (className.equals(GraalError.class.getName())) {
            return new GraalError(message);
        } else if (className.equals(IllegalArgumentException.class.getName())) {
            return new IllegalArgumentException(message);
        } else {
            return new Throwable(message);
        }
    }

    private static int architectureKind(Architecture architecture) {
        return switch (architecture) {
            case AMD64 ignored -> AMD64_ARCHITECTURE;
            case RISCV64 ignored -> RISCV64_ARCHITECTURE;
            case AArch64 ignored -> AARCH64_ARCHITECTURE;
            default -> throw new IllegalArgumentException("Unexpected architecture " + architecture);
        };
    }

    private static void writeArchitectureFeatures(ObjectCopierOutputStream out, Architecture architecture) throws IOException {
        switch (architecture) {
            case AMD64 amd64 -> writeEnumOrdinals(out, amd64.getFeatures());
            case RISCV64 riscv64 -> writeEnumOrdinals(out, riscv64.getFeatures());
            case AArch64 aarch64 -> writeEnumOrdinals(out, aarch64.getFeatures());
            default -> throw new IllegalArgumentException("Unexpected architecture " + architecture);
        }
    }

    private Architecture createFallbackArchitecture(String archName) {
        if (!Platform.KNOWN_ARCHITECTURES.contains(archName)) {
            throw new IllegalArgumentException("Unexpected architecture name " + archName);
        }
        return hostTarget.arch;
    }

    private static Architecture readArchitecture(ObjectCopierInputStream in) throws IOException {
        return switch (in.readPackedUnsignedInt()) {
            case AMD64_ARCHITECTURE -> new AMD64(readEnumOrdinals(in, AMD64.CPUFeature.class));
            case RISCV64_ARCHITECTURE -> new RISCV64(readEnumOrdinals(in, RISCV64.CPUFeature.class));
            case AARCH64_ARCHITECTURE -> new AArch64(readEnumOrdinals(in, AArch64.CPUFeature.class));
            default -> throw new IOException("Unknown replay architecture kind");
        };
    }

    private static void writeEnumOrdinals(ObjectCopierOutputStream out, EnumSet<?> enumSet) throws IOException {
        out.writePackedUnsignedInt(enumSet.size());
        for (Enum<?> constant : enumSet) {
            out.writePackedUnsignedInt(constant.ordinal());
        }
    }

    private static <E extends Enum<E>> EnumSet<E> readEnumOrdinals(ObjectCopierInputStream in, Class<E> enumClass) throws IOException {
        int size = in.readPackedUnsignedInt();
        EnumSet<E> enumSet = EnumSet.noneOf(enumClass);
        E[] constants = enumClass.getEnumConstants();
        for (int i = 0; i < size; i++) {
            int ordinal = in.readPackedUnsignedInt();
            if (ordinal < 0 || ordinal >= constants.length) {
                throw new IOException("Invalid ordinal " + ordinal + " for " + enumClass.getName());
            }
            enumSet.add(constants[ordinal]);
        }
        return enumSet;
    }

    private static int registerConfigKind(RegisterConfig registerConfig) {
        return switch (registerConfig) {
            case AMD64HotSpotRegisterConfig ignored -> AMD64_ARCHITECTURE;
            case RISCV64HotSpotRegisterConfig ignored -> RISCV64_ARCHITECTURE;
            case AArch64HotSpotRegisterConfig ignored -> AARCH64_ARCHITECTURE;
            default -> throw new IllegalArgumentException("Unexpected register config " + registerConfig);
        };
    }

    @SuppressWarnings("unchecked")
    private static RegisterConfig readRegisterConfig(ObjectCopierInputStream in, ReadState state) throws IOException {
        int kind = in.readPackedUnsignedInt();
        TargetDescription targetDescription = (TargetDescription) readValue(in, state);
        List<Register> allocatable = (List<Register>) readValue(in, state);
        return switch (kind) {
            case AMD64_ARCHITECTURE ->
                new AMD64HotSpotRegisterConfig(targetDescription, allocatable, readBooleanFlag(in));
            case RISCV64_ARCHITECTURE -> {
                readBooleanFlag(in);
                yield new RISCV64HotSpotRegisterConfig(targetDescription, allocatable);
            }
            case AARCH64_ARCHITECTURE -> {
                readBooleanFlag(in);
                yield new AArch64HotSpotRegisterConfig(targetDescription, allocatable);
            }
            default -> throw new IOException("Unknown replay register config kind " + kind);
        };
    }

}
