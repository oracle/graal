/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata;

import static com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod.EST_NO_ENTRY;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.CRC32C;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.interpreter.metadata.serialization.SerializationContext;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Classes, fields, methods and constants for the interpreter, the data structures in this universe
 * are alive at runtime.
 * <p>
 * This class can be used in SVM and HotSpot, in the resident and the server, and can load the
 * interpreter universe using opaque constants even the image heap is not accessible.
 */
public final class InterpreterUniverseImpl implements InterpreterUniverse {

    static final int MAGIC = 0xE597E550; // ESPRESSO
    private static final boolean LOGGING = false;

    private final List<InterpreterResolvedJavaType> types;
    private final List<InterpreterResolvedJavaField> fields;
    private final List<InterpreterResolvedJavaMethod> methods;
    private final Lazy<Map<Class<?>, InterpreterResolvedJavaType>> classToType = //
                    Lazy.of(() -> getTypes().stream()
                                    // Class<?> -> type
                                    .collect(Collectors.toMap(InterpreterResolvedJavaType::getJavaClass, Function.identity())));

    private final Lazy<Map<InterpreterResolvedJavaType, List<InterpreterResolvedJavaField>>> allDeclaredFields = //
                    Lazy.of(() -> getFields().stream()
                                    // type -> [field...]
                                    .collect(Collectors.groupingBy(InterpreterResolvedJavaField::getDeclaringClass)));
    private final Lazy<Map<InterpreterResolvedJavaType, List<InterpreterResolvedJavaMethod>>> allDeclaredMethods = //
                    Lazy.of(() -> getMethods().stream()
                                    // type -> [method...]
                                    .collect(Collectors.groupingBy(InterpreterResolvedJavaMethod::getDeclaringClass)));

    private final Lazy<InterpreterResolvedJavaMethod[]> methodESTOffsetTable = Lazy.of(() -> createMethodTable(getMethods()));

    private final Lazy<Map<InterpreterResolvedJavaMethod, Integer>> methodInverseTable = Lazy.of(() -> createInverseTable(getMethods()));
    private final Lazy<Map<InterpreterResolvedJavaType, Integer>> typeInverseTable = Lazy.of(() -> createInverseTable(getTypes()));

    private final Lazy<Map<InterpreterResolvedJavaField, Integer>> fieldInverseTable = Lazy.of(() -> createInverseTable(getFields()));

    private final Lazy<InterpreterResolvedJavaMethod[]> methodIdMapping = Lazy.of(() -> createMethodIdMapping(getMethods()));

    public InterpreterUniverseImpl(
                    Collection<InterpreterResolvedJavaType> types,
                    Collection<InterpreterResolvedJavaField> fields,
                    Collection<InterpreterResolvedJavaMethod> methods) {
        this.types = List.copyOf(types);
        this.fields = List.copyOf(fields);
        this.methods = List.copyOf(methods);
    }

    private static void consumeMagic(DataInput in) throws IOException {
        int header = in.readInt();
        if (header != MAGIC) {
            throw new IllegalStateException("Invalid header");
        }
    }

    /**
     * Serializes {@code this} {@link InterpreterUniverse} instance into the specified filePath.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public void saveTo(SerializationContext.Builder builder, Path filePath) throws IOException {

        int totalBytesWritten = 0;

        long startNanos = System.nanoTime();
        try (DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(
                                        Files.newOutputStream(filePath,
                                                        StandardOpenOption.WRITE,
                                                        StandardOpenOption.CREATE,
                                                        StandardOpenOption.TRUNCATE_EXISTING)))) {

            out.writeInt(MAGIC);

            SerializationContext.Writer writer = builder.buildWriter();

            for (InterpreterResolvedJavaType type : getTypes()) {
                writer.writeValue(out, type);
            }
            for (InterpreterResolvedJavaField field : getFields()) {
                writer.writeValue(out, field);
            }
            for (InterpreterResolvedJavaMethod method : getMethods()) {
                writer.writeValue(out, method);
            }
            for (InterpreterResolvedJavaMethod method : getMethods()) {
                writer.writeValue(out, method.inlinedBy);
                InterpreterResolvedJavaMethod oneImplementation = method.getOneImplementation();
                writer.writeReference(out, oneImplementation);
            }

            for (InterpreterResolvedJavaType type : getTypes()) {
                if (type instanceof InterpreterResolvedObjectType objectType && !type.isArray()) {
                    writer.writeValue(out, objectType.getConstantPool());
                    if (objectType.getVtableHolder() != null) {
                        writer.writeValue(out, objectType.getVtableHolder());
                    }
                }
            }
            totalBytesWritten = out.size();
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        if (LOGGING) {
            long shallowInterpreterMethods = getMethods().stream().filter(Predicate.not(InterpreterResolvedJavaMethod::isInterpreterExecutable)).count();
            System.err.println("Save interpreter metadata:" +
                            " totalBytesWritten=" + totalBytesWritten +
                            " types=" + getTypes().size() +
                            " fields=" + getFields().size() +
                            " methods=" + getMethods().size() +
                            " (shallow=" + shallowInterpreterMethods + ")" +
                            " elapsedMillis=" + (elapsedNanos / 1_000_000));
        }
    }

    public static String toHexString(int value) {
        String result = Integer.toHexString(value);
        return "0".repeat(8 - result.length()) + result;
    }

    public static int computeCRC32(Path filePath) throws IOException {
        CRC32C checksum = new CRC32C();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(filePath, StandardOpenOption.READ))) {
            byte[] buf = new byte[16 * (1 << 10)]; // 16KB
            int bytesRead = 0;
            while ((bytesRead = in.read(buf)) > 0) {
                checksum.update(buf, 0, bytesRead);
            }
        }
        return (int) checksum.getValue();
    }

    public static InterpreterUniverseImpl loadFrom(SerializationContext.Builder builder, boolean opaqueConstants, String hashString, Path filePath) throws IOException {
        long startNanos = System.nanoTime();
        if (LOGGING) {
            System.err.println("Interpreter metadata hash string: " + hashString);
        }

        if (hashString != null) {
            String[] parts = hashString.split(":");
            String algorithm = parts[0];
            String expectedHexChecksum = parts[1];

            /*
             * TODO(peterssen): GR-46008 Use secure hash to verify interpreter metadata. This check
             * is meant ONLY to improve the user experience, not for security e.g. a secure hash
             * should be used, also note that this check don't guard against the file being modified
             * between the checksum/hash computation and actual de-serialization.
             */
            if ("crc32".equalsIgnoreCase(algorithm)) {
                int crc32 = computeCRC32(filePath);
                String actualHexChecksum = toHexString(crc32);
                if (!expectedHexChecksum.equals(actualHexChecksum)) {
                    throw new IllegalArgumentException(
                                    "Invalid " + algorithm + " verification for metadata file: " + filePath +
                                                    " expected: " + expectedHexChecksum + " actual: " + actualHexChecksum);
                }
            } else {
                throw VMError.unimplemented("Metadata verification with: " + algorithm);
            }
        }

        try (DataInputStream dataInput = new DataInputStream(
                        new BufferedInputStream(
                                        Files.newInputStream(filePath, StandardOpenOption.READ)))) {
            List<InterpreterResolvedJavaType> types = new ArrayList<>();
            List<InterpreterResolvedJavaField> fields = new ArrayList<>();
            List<InterpreterResolvedJavaMethod> methods = new ArrayList<>();

            consumeMagic(dataInput);
            SerializationContext.Reader reader;

            if (opaqueConstants) {
                // Configure ReferenceConstant de-serialization to return opaque constants (heap
                // offsets) to the main application heap.
                reader = builder
                                .registerReader(true, ReferenceConstant.class, (context, in) -> {
                                    boolean inNativeHeap = in.readBoolean();
                                    if (inNativeHeap) {
                                        long nativeHeapOffset = in.readLong();
                                        return ReferenceConstant.createFromHeapOffset(nativeHeapOffset);
                                    } else {
                                        Object ref = context.readReference(in);
                                        return ReferenceConstant.createFromReference(ref);
                                    }
                                })
                                .buildReader();
            } else {
                // nothing to change, de-serialization is already configured for the interpreter
                // (constants are on the same heap).
                reader = builder.buildReader();
            }

            while (true) {
                try {
                    Object value = reader.readValue(dataInput);
                    if (value instanceof InterpreterResolvedJavaType type) {
                        types.add(type);
                    } else if (value instanceof InterpreterResolvedJavaField field) {
                        fields.add(field);
                    } else if (value instanceof InterpreterResolvedJavaMethod method) {
                        methods.add(method);
                    } else if (value instanceof InterpreterConstantPool constantPool) {
                        InterpreterResolvedObjectType holder = constantPool.getHolder();
                        VMError.guarantee(!holder.isArray());
                        holder.setConstantPool(constantPool);
                    } else if (value instanceof InterpreterResolvedObjectType.VTableHolder vTableHolder) {
                        InterpreterResolvedObjectType holder = vTableHolder.holder;
                        holder.setVtable(vTableHolder.vtable);
                    } else if (value instanceof InterpreterResolvedJavaMethod.InlinedBy inlinedBy) {
                        InterpreterResolvedJavaMethod holder = inlinedBy.holder;
                        for (InterpreterResolvedJavaMethod m : inlinedBy.inliners) {
                            holder.addInliner(m);
                        }
                        Object oneImpl = reader.readReference(dataInput);
                        if (oneImpl instanceof InterpreterResolvedJavaMethod interpreterResolvedJavaMethod) {
                            holder.setOneImplementation(interpreterResolvedJavaMethod);
                        } else {
                            VMError.guarantee(oneImpl == null);
                        }
                    } else {
                        // skip value
                    }
                } catch (EOFException e) {
                    break;
                }
            }

            InterpreterUniverseImpl universe = new InterpreterUniverseImpl(types, fields, methods);
            long elapsedNanos = System.nanoTime() - startNanos;

            if (LOGGING) {
                System.err.println("Load interpreter metadata:" +
                                " types=" + universe.getTypes().size() +
                                " fields=" + universe.getFields().size() +
                                " methods=" + universe.getMethods().size() +
                                " elapsedMillis=" + (elapsedNanos / 1_000_000));
            }

            return universe;
        }
    }

    @Override
    public List<InterpreterResolvedJavaType> getTypes() {
        return types;
    }

    @Override
    public List<InterpreterResolvedJavaField> getFields() {
        return fields;
    }

    @Override
    public List<InterpreterResolvedJavaMethod> getMethods() {
        return methods;
    }

    @SuppressWarnings("static-method")
    @Override
    public Class<?> lookupClass(ResolvedJavaType type) {
        return ((InterpreterResolvedJavaType) type).getJavaClass();
    }

    @Override
    public InterpreterResolvedJavaType lookupType(Class<?> clazz) {
        Map<Class<?>, InterpreterResolvedJavaType> map = classToType.get();
        InterpreterResolvedJavaType type = map.get(clazz);
        assert type != null;
        return type;
    }

    @Override
    public Collection<InterpreterResolvedJavaField> getAllDeclaredFields(ResolvedJavaType type) {
        InterpreterResolvedJavaType interpreterType = (InterpreterResolvedJavaType) type;
        return allDeclaredFields.get().getOrDefault(interpreterType, List.of());
    }

    @Override
    public Collection<InterpreterResolvedJavaMethod> getAllDeclaredMethods(ResolvedJavaType type) {
        InterpreterResolvedJavaType interpreterType = (InterpreterResolvedJavaType) type;
        return allDeclaredMethods.get().getOrDefault(interpreterType, List.of());
    }

    private static InterpreterResolvedJavaMethod[] createMethodTable(Collection<InterpreterResolvedJavaMethod> methods) {
        int maxOffset = -1;
        for (InterpreterResolvedJavaMethod m : methods) {
            int methodOffset = m.getEnterStubOffset();
            if (methodOffset != EST_NO_ENTRY) {
                VMError.guarantee(methodOffset >= 0);
                maxOffset = Math.max(maxOffset, methodOffset);
            }
        }
        InterpreterResolvedJavaMethod[] result = new InterpreterResolvedJavaMethod[maxOffset + 1];
        for (InterpreterResolvedJavaMethod m : methods) {
            int methodOffset = m.getEnterStubOffset();
            if (methodOffset != EST_NO_ENTRY) {
                VMError.guarantee(result[methodOffset] == null, "duplicated interpreter method entry");
                result[methodOffset] = m;
            }
        }
        return result;
    }

    @Override
    public InterpreterResolvedJavaMethod getMethodForESTOffset(int methodIndex) {
        InterpreterResolvedJavaMethod method = this.methodESTOffsetTable.get()[methodIndex];
        VMError.guarantee(method != null);
        return method;
    }

    private static <T> Map<T, Integer> createInverseTable(Collection<T> list) {
        HashMap<T, Integer> map = new HashMap<>();
        int counter = 0;
        for (T e : list) {
            map.put(e, counter++);
        }
        return map;
    }

    private static InterpreterResolvedJavaMethod[] createMethodIdMapping(List<InterpreterResolvedJavaMethod> methods) {
        int maxMethodId = 0;
        for (InterpreterResolvedJavaMethod method : methods) {
            int methodId = method.getMethodId();
            assert methodId >= 0;
            maxMethodId = Math.max(maxMethodId, methodId);
        }
        InterpreterResolvedJavaMethod[] mapping = new InterpreterResolvedJavaMethod[maxMethodId + 1];
        for (InterpreterResolvedJavaMethod method : methods) {
            int methodId = method.getMethodId();
            if (methodId != 0) {
                mapping[methodId] = method;
            }
        }
        return mapping;
    }

    @Override
    public ResolvedJavaMethod getMethodAtIndex(int index) {
        return methods.get(index);
    }

    @Override
    public ResolvedJavaType getTypeAtIndex(int index) {
        return types.get(index);
    }

    @Override
    public ResolvedJavaField getFieldAtIndex(int index) {
        return fields.get(index);
    }

    @Override
    public OptionalInt getMethodIndexFor(ResolvedJavaMethod method) {
        Integer index = methodInverseTable.get().get(method);
        if (index == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(index);
    }

    @Override
    public OptionalInt getTypeIndexFor(ResolvedJavaType type) {
        Integer index = typeInverseTable.get().get(type);
        if (index == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(index);
    }

    @Override
    public OptionalInt getFieldIndexFor(ResolvedJavaField field) {
        Integer index = fieldInverseTable.get().get(field);
        if (index == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(index);
    }

    @Override
    public InterpreterResolvedJavaMethod getMethodFromMethodId(int methodId) {
        if (methodId == 0) {
            return null;
        }
        InterpreterResolvedJavaMethod[] mapping = methodIdMapping.get();
        if (0 <= methodId && methodId < mapping.length) {
            return mapping[methodId];
        } else {
            // No interpreter method known for this methodId.
            return null;
        }
    }
}
