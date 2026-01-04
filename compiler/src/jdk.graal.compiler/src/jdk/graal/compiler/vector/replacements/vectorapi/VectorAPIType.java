/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.replacements.vectorapi;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Information about a Vector API vector type, such as element type, vector length, and the
 * corresponding {@link SimdStamp}.
 */
public final class VectorAPIType {

    /**
     * The name of this type. This is the unqualified name of the corresponding Vector API class.
     */
    public final String name;
    /** Number of vector lanes. */
    public final int vectorLength;
    /**
     * The element kind according to the Java class's {@code ETYPE} field. For masks this is the
     * enclosing type's element type, e.g., for a {@code double} mask this will be {@code double}.
     * Therefore, for masks this is different from the {@link #payloadKind}. For shuffle vectors,
     * the element type of this is the integral type that has the same width as that of the
     * enclosing vector type.
     */
    public final JavaKind elementKind;
    /**
     * The element kind of this vector's {@code payload} array. Shuffles use {@link JavaKind#Byte}
     * in JDK-21, masks use {@link JavaKind#Boolean} irrespective of the containing vector's element
     * type.
     */
    public final JavaKind payloadKind;
    /**
     * A stamp corresponding to this vector type in the graph. Values described by this stamp may or
     * may not be representable on the target architecture.
     */
    public final SimdStamp stamp;
    /**
     * A stamp corresponding to this vector type in this vector's {@code payload} array in memory.
     * This is the same as {@link #stamp}, except for {@link #isMask mask} types, which have a logic
     * stamp in the graph and a {@code boolean} vector stamp for their in-memory representation.
     */
    public final SimdStamp payloadStamp;
    /** Indicates whether this is a mask type. */
    public final boolean isMask;
    /** Indicates whether this is a shuffle type. */
    public final boolean isShuffle;

    /**
     * Builds the {@link VectorAPIType} corresponding to the given {@code javaType}. Returns
     * {@code null} if the given type is not initialized, or is not a Vector API type.
     */
    public static VectorAPIType ofType(ResolvedJavaType javaType, CoreProviders providers) {
        Table lookupTable = Table.instance(VectorAPIUtils.vectorArchitecture(providers));
        String clazzName = javaType.toClassName();
        VectorAPIType result = lookupTable.table.get(clazzName);
        if (result != null) {
            return result.verify(javaType, providers);
        }
        return result;
    }

    /**
     * Like {@link #ofType(ResolvedJavaType, CoreProviders)}, but extracts the needed type from the
     * given {@code value}, if it is a constant that denotes a Java class. Returns {@code null} if
     * the value is not such a constant.
     */
    public static VectorAPIType ofConstant(ValueNode value, CoreProviders providers) {
        if (value.isJavaConstant()) {
            ResolvedJavaType javaType = providers.getConstantReflection().asJavaType(value.asJavaConstant());
            return ofType(javaType, providers);
        }
        return null;
    }

    public static final String VECTOR_PACKAGE_NAME = "jdk.incubator.vector";

    /**
     * Lookup table for Java Vector API types. An unmodifiable instance of this table is built
     * lazily on the first lookup request for each {@link VectorArchitecture} instance. The vector
     * architecture is the owner of its associated table. Users of {@link VectorAPIType} should not
     * interact with this class directly, only through {@link #ofType}.
     */
    public static final class Table {

        /** The element types for which the Vector API provides vector types. */
        private static final JavaKind[] KINDS = new JavaKind[]{JavaKind.Byte, JavaKind.Short, JavaKind.Int, JavaKind.Long, JavaKind.Float, JavaKind.Double};
        /**
         * The fixed vector bit sizes in the Vector API. The "preferred" size is an alias for one of
         * these, it is not a separate type. The "max" size has a separate type which we treat
         * separately.
         */
        private static final int[] SIZES = new int[]{64, 128, 256, 512};

        /** The actual lookup table. */
        private final EconomicMap<String, VectorAPIType> table;
        /** The vector architecture for which this table was built. */
        private final VectorArchitecture vectorArch;

        private static Table instance(VectorArchitecture vectorArch) {
            Table result = vectorArch.getVectorAPITypeTable();
            if (result == null) {
                synchronized (vectorArch) {
                    result = vectorArch.getVectorAPITypeTable();
                    if (result == null) {
                        result = buildLookupTable(vectorArch);
                        vectorArch.setVectorAPITypeTable(result);
                    }
                }
            }
            GraalError.guarantee(result != null && result.vectorArch.equals(vectorArch), "Cannot deal with different vector architecture instances");
            return result;
        }

        private Table(EconomicMap<String, VectorAPIType> table, VectorArchitecture vectorArch) {
            this.table = table;
            this.vectorArch = vectorArch;
        }

        /**
         * Build a lookup table mapping qualified Vector API class names to corresponding
         * {@link VectorAPIType} instances. Entries in this table must be {@linkplain #verify
         * verified} before use.
         */
        private static Table buildLookupTable(VectorArchitecture vectorArch) {
            EconomicMap<String, VectorAPIType> table = EconomicMap.create();

            for (JavaKind elementKind : KINDS) {
                for (int bitSize : SIZES) {
                    int vectorLength = bitSize / elementKind.getBitCount();
                    recordEntries(table, elementKind, Integer.toString(bitSize), vectorLength, vectorArch);
                }
                /*
                 * For the "max" type, guess that the VM will use a vector size according the
                 * architecture's max vector length. We verify this when the type is used.
                 */
                int maxVectorBytes = vectorArch.guessMaxVectorAPIVectorLength(VectorAPIUtils.primitiveStampForKind(elementKind));
                int maxVectorLength = maxVectorBytes / elementKind.getByteCount();
                recordEntries(table, elementKind, "Max", maxVectorLength, vectorArch);
            }

            return new Table(table, vectorArch);
        }

        /**
         * Add entries to the table for the given vector kind and size, as well as the corresponding
         * mask and shuffle vectors.
         */
        private static void recordEntries(EconomicMap<String, VectorAPIType> table, JavaKind elementKind, String size, int vectorLength, VectorArchitecture vectorArch) {
            String name = VECTOR_PACKAGE_NAME + "." + elementKind.name() + size + "Vector";
            table.put(name, buildVectorAPIType(name, vectorLength, elementKind, false, false, vectorArch));
            String maskName = name + "$" + elementKind.name() + size + "Mask";
            table.put(maskName, buildVectorAPIType(maskName, vectorLength, elementKind, true, false, vectorArch));
            String shuffleName = name + "$" + elementKind.name() + size + "Shuffle";
            table.put(shuffleName, buildVectorAPIType(shuffleName, vectorLength, elementKind, false, true, vectorArch));
        }
    }

    /**
     * Verify that this data structure corresponds to the given {@code javaType}. Specifically, the
     * type must be initialized, and its {@code VLENGTH} and {@code ETYPE} fields must correspond to
     * this type's precomputed {@link #vectorLength} and {@link #elementKind}, respectively. This
     * verification is necessary because when we build the lookup table for types we must make a
     * guess at the VM's choice for the "max" vector types (like {@code IntMaxVector}), and we must
     * be sure that we guessed correctly.
     *
     * @return {@code this} if verification succeeds, {@code null} otherwise
     */
    private VectorAPIType verify(ResolvedJavaType javaType, CoreProviders providers) {
        if (!javaType.isInitialized()) {
            return null;
        }
        ConstantReflectionProvider constantReflection = providers.getConstantReflection();
        int vLength = -1;
        ResolvedJavaType eType = null;
        ResolvedJavaField[] staticFields;
        try {
            staticFields = javaType.getStaticFields();
        } catch (Error e) {
            /*
             * We must skip verification of the type if we can't reflect on its fields. This is the
             * case in SVM runtime compilations.
             */
            return this;
        }
        for (var field : staticFields) {
            if (field.getName().equals("VLENGTH")) {
                vLength = constantReflection.readFieldValue(field, null).asInt();
                if (javaType.getUnqualifiedName().contains("Max")) {
                    /*
                     * We can't verify the max vector length while building native images. The
                     * reflectively read value at build time might not match the vector length we're
                     * using for the target.
                     */
                    vLength = this.vectorLength;
                }
            } else if (field.getName().equals("ETYPE")) {
                eType = constantReflection.asJavaType(constantReflection.readFieldValue(field, null));
            }
        }
        if (vLength == -1 || eType == null) {
            throw GraalError.shouldNotReachHere("Unable to resolve required fields in class " + javaType);
        }
        if (vLength == this.vectorLength && eType.getJavaKind() == this.elementKind) {
            return this;
        }
        return null;
    }

    private static VectorAPIType buildVectorAPIType(String name, int vectorLength, JavaKind speciesElementKind, boolean isMask, boolean isShuffle, VectorArchitecture vectorArch) {
        JavaKind elementKind;
        JavaKind payloadKind;
        Stamp elementStamp;
        Stamp payloadElementStamp;
        if (isMask) {
            elementKind = speciesElementKind;
            payloadKind = JavaKind.Boolean;
            payloadElementStamp = IntegerStamp.create(8);
            elementStamp = vectorArch.maskStamp(VectorAPIUtils.primitiveStampForKind(elementKind));
        } else if (isShuffle) {
            // A float shuffle is an int vector
            if (speciesElementKind == JavaKind.Double) {
                elementKind = JavaKind.Long;
            } else if (speciesElementKind == JavaKind.Float) {
                elementKind = JavaKind.Int;
            } else {
                elementKind = speciesElementKind;
            }
            payloadKind = elementKind;
            elementStamp = IntegerStamp.create(payloadKind.getBitCount());
            payloadElementStamp = elementStamp;
        } else {
            elementKind = speciesElementKind;
            payloadKind = elementKind;
            elementStamp = payloadKind.isNumericInteger() ? IntegerStamp.create(payloadKind.getBitCount()) : StampFactory.forKind(payloadKind);
            payloadElementStamp = elementStamp;
        }
        SimdStamp stamp = SimdStamp.broadcast(elementStamp, vectorLength);
        SimdStamp payloadStamp = SimdStamp.broadcast(payloadElementStamp, vectorLength);

        return new VectorAPIType(name, vectorLength, elementKind, payloadKind, stamp, payloadStamp, isMask, isShuffle);
    }

    private VectorAPIType(String name, int vectorLength, JavaKind elementKind, JavaKind payloadKind, SimdStamp stamp, SimdStamp payloadStamp, boolean isMask, boolean isShuffle) {
        this.name = name;
        this.vectorLength = vectorLength;
        this.elementKind = elementKind;
        this.payloadKind = payloadKind;
        this.stamp = stamp;
        this.payloadStamp = payloadStamp;
        this.isMask = isMask;
        this.isShuffle = isShuffle;
    }

    @Override
    public String toString() {
        return "VectorAPIType{%s: %s*%s, stamp %s}".formatted(name, vectorLength, elementKind, stamp);
    }
}
