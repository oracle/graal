/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.meta;

import java.util.Arrays;
import java.util.Collection;
import java.util.Formatter;
import java.util.Set;
import java.util.TreeSet;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

/**
 * This class documents unimplemented Graal intrinsics and categorizes them into 3 categories:
 * <ul>
 * <li>ignore: intrinsics that will never be implemented by Graal;</li>
 * <li>downStream: intrinsics implemented in downstream;</li>
 * <li>toBeInvestigated: intrinsics that yet to be implemented or moved to ignore.</li>
 * </ul>
 */
public final class UnimplementedGraalIntrinsics {

    /**
     * The HotSpot intrinsics that:
     * <ul>
     * <li>will never be implemented by Graal (comments must explain why)</li>
     * <li>are implemented without {@link InvocationPlugin}s, or</li>
     * <li>whose {@link InvocationPlugin} registration is guarded by a condition that is false in
     * the current VM context.</li>
     * </ul>
     */
    public final Set<String> ignore = new TreeSet<>();

    /**
     * The HotSpot intrinsics implemented in GraalVM enterprise edition.
     * </ul>
     */
    public final Set<String> enterprise = new TreeSet<>();

    /**
     * The HotSpot intrinsics yet to be implemented or moved to {@link #ignore}.
     */
    public final Set<String> toBeInvestigated = new TreeSet<>();

    private static void add(Collection<String> c, String... elements) {
        String[] sorted = elements.clone();
        Arrays.sort(sorted);
        if (!Arrays.equals(elements, sorted)) {
            int width = 2 + Arrays.stream(elements).map(String::length).reduce(0, Integer::max);
            Formatter fmt = new Formatter();
            fmt.format("%-" + width + "s | sorted%n", "original");
            fmt.format("%s%n", new String(new char[width * 2 + 2]).replace('\0', '='));
            for (int i = 0; i < elements.length; i++) {
                fmt.format("%-" + width + "s | %s%n", elements[i], sorted[i]);
            }
            throw GraalError.shouldNotReachHere(String.format("Elements not sorted alphabetically:%n%s", fmt)); // ExcludeFromJacocoGeneratedReport
        }
        c.addAll(Arrays.asList(elements));
    }

    public UnimplementedGraalIntrinsics(Architecture arch) {
        int jdk = Runtime.version().feature();
        add(ignore,
                        // These are dead
                        "java/lang/Math.atan2(DD)D",
                        // These do general compiler optimizations and convert min/max to cmov
                        // instructions. We are ignoring them as cmovs are not necessarily
                        // beneficial.
                        "java/lang/Math.max(II)I",
                        "java/lang/Math.min(II)I",
                        "java/lang/StrictMath.max(II)I",
                        "java/lang/StrictMath.min(II)I",
                        // handled through an intrinsic for String.equals itself
                        "java/lang/StringLatin1.equals([B[B)Z",
                        // handled by an intrinsic for StringLatin1.indexOf([BI[BII)I
                        "java/lang/StringLatin1.indexOf([B[B)I",
                        // handled through an intrinsic for String.equals itself
                        "java/lang/StringUTF16.equals([B[B)Z",
                        // handled by an intrinsic for StringUTF16.indexOfUnsafe
                        "java/lang/StringUTF16.indexOf([BI[BII)I",
                        "java/lang/StringUTF16.indexOf([B[B)I",
                        // handled by an intrinsic for StringUTF16.indexOfLatin1Unsafe
                        "java/lang/StringUTF16.indexOfLatin1([BI[BII)I",
                        "java/lang/StringUTF16.indexOfLatin1([B[B)I",
                        // implemented through lowering
                        "java/lang/ref/Reference.get()Ljava/lang/Object;",
                        // Relevant for Java flight recorder
                        // [GR-10106] These JFR intrinsics are used for firing socket/file events
                        // via Java instrumentation and are of low priority.
                        "jdk/jfr/internal/JVM.commit(J)J",
                        "jdk/jfr/internal/JVM.counterTime()J",
                        "jdk/jfr/internal/JVM.getEventWriter()Ljdk/jfr/internal/event/EventWriter;");

        if (arch instanceof AMD64 amd64) {
            if (amd64.getFeatures().contains(AMD64.CPUFeature.SHA) && !amd64.getFeatures().contains(AMD64.CPUFeature.AVX2)) {
                // This happens when UseAVX=0 or 1 is specified.
                add(ignore,
                                "sun/security/provider/SHA2.implCompress0([BI)V");
            }
        }

        if (jdk == 21) {
            // JDK-8325169
            // handled by an intrinsic for StringUTF16.indexOfCharUnsafe
            add(ignore, "java/lang/StringUTF16.indexOfChar([BIII)I");
        }

        add(toBeInvestigated, // @formatter:off
                        // JDK-8309130: x86_64 AVX512 intrinsics for Arrays.sort methods (GR-48679)
                        "java/util/DualPivotQuicksort.partition(Ljava/lang/Class;Ljava/lang/Object;JIIIILjava/util/DualPivotQuicksort$PartitionOperation;)[I",
                        "java/util/DualPivotQuicksort.sort(Ljava/lang/Class;Ljava/lang/Object;JIILjava/util/DualPivotQuicksort$SortOperation;)V",
                        // JDK-8223347: Integration of Vector API
                        "jdk/internal/vm/vector/VectorSupport.compressExpandOp(ILjava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljdk/internal/vm/vector/VectorSupport$CompressExpandOperation;)Ljdk/internal/vm/vector/VectorSupport$VectorPayload;",
                        jdk == 21 ? "jdk/internal/vm/vector/VectorSupport.extract(Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;ILjdk/internal/vm/vector/VectorSupport$VecExtractOp;)J":
                                    "jdk/internal/vm/vector/VectorSupport.extract(Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$VectorPayload;ILjdk/internal/vm/vector/VectorSupport$VecExtractOp;)J",
                        "jdk/internal/vm/vector/VectorSupport.indexPartiallyInUpperRange(Ljava/lang/Class;Ljava/lang/Class;IJJLjdk/internal/vm/vector/VectorSupport$IndexPartiallyInUpperRangeOperation;)Ljdk/internal/vm/vector/VectorSupport$VectorMask;",
                        "jdk/internal/vm/vector/VectorSupport.indexVector(Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;ILjdk/internal/vm/vector/VectorSupport$VectorSpecies;Ljdk/internal/vm/vector/VectorSupport$IndexOperation;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                        "jdk/internal/vm/vector/VectorSupport.insert(Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;IJLjdk/internal/vm/vector/VectorSupport$VecInsertOp;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                        jdk == 21 ? "jdk/internal/vm/vector/VectorSupport.loadMasked(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$VectorMask;ILjava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$VectorSpecies;Ljdk/internal/vm/vector/VectorSupport$LoadVectorMaskedOperation;)Ljdk/internal/vm/vector/VectorSupport$Vector;":
                                    "jdk/internal/vm/vector/VectorSupport.loadMasked(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;JZLjdk/internal/vm/vector/VectorSupport$VectorMask;ILjava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$VectorSpecies;Ljdk/internal/vm/vector/VectorSupport$LoadVectorMaskedOperation;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                        "jdk/internal/vm/vector/VectorSupport.loadWithMap(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Class;Ljava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljava/lang/Object;I[IILjdk/internal/vm/vector/VectorSupport$VectorSpecies;Ljdk/internal/vm/vector/VectorSupport$LoadVectorOperationWithMap;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                        "jdk/internal/vm/vector/VectorSupport.maskReductionCoerced(ILjava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$VectorMask;Ljdk/internal/vm/vector/VectorSupport$VectorMaskOp;)J",
                        "jdk/internal/vm/vector/VectorSupport.maybeRebox(Ljdk/internal/vm/vector/VectorSupport$VectorPayload;)Ljdk/internal/vm/vector/VectorSupport$VectorPayload;",
                        "jdk/internal/vm/vector/VectorSupport.rearrangeOp(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorShuffle;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljdk/internal/vm/vector/VectorSupport$VectorRearrangeOp;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                        jdk == 21 ? "jdk/internal/vm/vector/VectorSupport.storeMasked(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$StoreVectorMaskedOperation;)V":
                                    "jdk/internal/vm/vector/VectorSupport.storeMasked(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;JZLjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$StoreVectorMaskedOperation;)V",
                        "jdk/internal/vm/vector/VectorSupport.storeWithMap(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Class;Ljava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljava/lang/Object;I[IILjdk/internal/vm/vector/VectorSupport$StoreVectorOperationWithMap;)V",
                        "jdk/internal/vm/vector/VectorSupport.ternaryOp(ILjava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljdk/internal/vm/vector/VectorSupport$TernaryOperation;)Ljdk/internal/vm/vector/VectorSupport$Vector;"
                        // @formatter:on
        );

        // These are known to be implemented down stream
        add(enterprise, // @formatter:off
                        "java/lang/Integer.toString(I)Ljava/lang/String;",
                        "java/lang/String.<init>(Ljava/lang/String;)V",
                        "java/lang/StringBuffer.<init>()V",
                        "java/lang/StringBuffer.<init>(I)V",
                        "java/lang/StringBuffer.<init>(Ljava/lang/String;)V",
                        "java/lang/StringBuffer.append(C)Ljava/lang/StringBuffer;",
                        "java/lang/StringBuffer.append(I)Ljava/lang/StringBuffer;",
                        "java/lang/StringBuffer.append(Ljava/lang/String;)Ljava/lang/StringBuffer;",
                        "java/lang/StringBuffer.toString()Ljava/lang/String;",
                        "java/lang/StringBuilder.<init>()V",
                        "java/lang/StringBuilder.<init>(I)V",
                        "java/lang/StringBuilder.<init>(Ljava/lang/String;)V",
                        "java/lang/StringBuilder.append(C)Ljava/lang/StringBuilder;",
                        "java/lang/StringBuilder.append(I)Ljava/lang/StringBuilder;",
                        "java/lang/StringBuilder.append(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                        "java/lang/StringBuilder.toString()Ljava/lang/String;",
                        "java/util/Arrays.copyOf([Ljava/lang/Object;ILjava/lang/Class;)[Ljava/lang/Object;",
                        "java/util/Arrays.copyOfRange([Ljava/lang/Object;IILjava/lang/Class;)[Ljava/lang/Object;",
                        "jdk/internal/vm/vector/VectorSupport.binaryOp(ILjava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$VectorPayload;Ljdk/internal/vm/vector/VectorSupport$VectorPayload;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljdk/internal/vm/vector/VectorSupport$BinaryOperation;)Ljdk/internal/vm/vector/VectorSupport$VectorPayload;",
                        "jdk/internal/vm/vector/VectorSupport.blend(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljdk/internal/vm/vector/VectorSupport$VectorBlendOp;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                        "jdk/internal/vm/vector/VectorSupport.broadcastInt(ILjava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;ILjdk/internal/vm/vector/VectorSupport$VectorMask;Ljdk/internal/vm/vector/VectorSupport$VectorBroadcastIntOp;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                        "jdk/internal/vm/vector/VectorSupport.compare(ILjava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljdk/internal/vm/vector/VectorSupport$VectorCompareOp;)Ljdk/internal/vm/vector/VectorSupport$VectorMask;",
                        "jdk/internal/vm/vector/VectorSupport.convert(ILjava/lang/Class;Ljava/lang/Class;ILjava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$VectorPayload;Ljdk/internal/vm/vector/VectorSupport$VectorSpecies;Ljdk/internal/vm/vector/VectorSupport$VectorConvertOp;)Ljdk/internal/vm/vector/VectorSupport$VectorPayload;",
                        "jdk/internal/vm/vector/VectorSupport.fromBitsCoerced(Ljava/lang/Class;Ljava/lang/Class;IJILjdk/internal/vm/vector/VectorSupport$VectorSpecies;Ljdk/internal/vm/vector/VectorSupport$FromBitsCoercedOperation;)Ljdk/internal/vm/vector/VectorSupport$VectorPayload;",
                        jdk == 21 ? "jdk/internal/vm/vector/VectorSupport.load(Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;JLjava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$VectorSpecies;Ljdk/internal/vm/vector/VectorSupport$LoadOperation;)Ljdk/internal/vm/vector/VectorSupport$VectorPayload;":
                                    "jdk/internal/vm/vector/VectorSupport.load(Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;JZLjava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$VectorSpecies;Ljdk/internal/vm/vector/VectorSupport$LoadOperation;)Ljdk/internal/vm/vector/VectorSupport$VectorPayload;",
                        "jdk/internal/vm/vector/VectorSupport.reductionCoerced(ILjava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljdk/internal/vm/vector/VectorSupport$ReductionOperation;)J",
                        "jdk/internal/vm/vector/VectorSupport.shuffleIota(Ljava/lang/Class;Ljava/lang/Class;Ljdk/internal/vm/vector/VectorSupport$VectorSpecies;IIIILjdk/internal/vm/vector/VectorSupport$ShuffleIotaOperation;)Ljdk/internal/vm/vector/VectorSupport$VectorShuffle;",
                        "jdk/internal/vm/vector/VectorSupport.shuffleToVector(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;Ljdk/internal/vm/vector/VectorSupport$VectorShuffle;ILjdk/internal/vm/vector/VectorSupport$ShuffleToVectorOperation;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                        jdk == 21 ? "jdk/internal/vm/vector/VectorSupport.store(Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$VectorPayload;Ljava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$StoreVectorOperation;)V":
                                    "jdk/internal/vm/vector/VectorSupport.store(Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;JZLjdk/internal/vm/vector/VectorSupport$VectorPayload;Ljava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$StoreVectorOperation;)V",
                        "jdk/internal/vm/vector/VectorSupport.test(ILjava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$VectorMask;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljava/util/function/BiFunction;)Z",
                        "jdk/internal/vm/vector/VectorSupport.unaryOp(ILjava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljdk/internal/vm/vector/VectorSupport$UnaryOperation;)Ljdk/internal/vm/vector/VectorSupport$Vector;"
                        // @formatter:on
        );
    }

    /**
     * Test if the given intrinsic candidate is in the {@link #ignore} category -- it will not be
     * implemented in Graal.
     */
    public boolean isIgnored(String method) {
        return ignore.contains(method);
    }

    /**
     * Test if the given intrinsic candidate is in the {@link #enterprise} category -- its Graal
     * intrinsic is implemented in GraalVM EE.
     */
    public boolean isImplementedInEnterprise(String method) {
        return enterprise.contains(method);
    }

    /**
     * Test if the given intrinsic candidate is in the {@link #toBeInvestigated} category -- it may
     * be a performance improvement opportunity and yet to be implemented.
     */
    public boolean isMissing(String method) {
        return toBeInvestigated.contains(method);
    }

    /**
     * Test if the given intrinsic candidate is documented in this file.
     */
    public boolean isDocumented(String method) {
        return isIgnored(method) || isImplementedInEnterprise(method) || isMissing(method);
    }
}
