/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.meta;

import java.util.Arrays;
import java.util.Collection;
import java.util.Formatter;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.hotspot.VMField;

/**
 * This class documents unimplemented Graal intrinsics and categorizes them into 4 categories:
 * <ul>
 * <li>ignore: intrinsics that will never be implemented by Graal;</li>
 * <li>complexGuard: intrinsics guarded by complex condition in HotSpot;</li>
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
     * The HotSpot intrinsics whose {@link InvocationPlugin} registration is guarded by a condition
     * too complex to duplicate here.
     * </ul>
     */
    public final Set<String> complexGuard = new TreeSet<>();

    /**
     * The HotSpot intrinsics implemented in GraalVM enterprise edition.
     * </ul>
     */
    public final Set<String> enterprise = new TreeSet<>();

    /**
     * The HotSpot intrinsics yet to be implemented or moved to {@link #ignore}.
     */
    public final Set<String> toBeInvestigated = new TreeSet<>();

    private static Collection<String> add(Collection<String> c, String... elements) {
        String[] sorted = elements.clone();
        Arrays.sort(sorted);
        if (!Arrays.equals(elements, sorted)) {
            int width = 2 + Arrays.asList(elements).stream().map(String::length).reduce(0, Integer::max);
            Formatter fmt = new Formatter();
            fmt.format("%-" + width + "s | sorted%n", "original");
            fmt.format("%s%n", new String(new char[width * 2 + 2]).replace('\0', '='));
            for (int i = 0; i < elements.length; i++) {
                fmt.format("%-" + width + "s | %s%n", elements[i], sorted[i]);
            }
            throw GraalError.shouldNotReachHere(String.format("Elements not sorted alphabetically:%n%s", fmt));
        }
        c.addAll(Arrays.asList(elements));
        return c;
    }

    public UnimplementedGraalIntrinsics(GraalHotSpotVMConfig config, Architecture arch) {
        // These are dead
        add(ignore,
                        "java/lang/Math.atan2(DD)D",
                        "jdk/internal/misc/Unsafe.park(ZJ)V",
                        "jdk/internal/misc/Unsafe.unpark(Ljava/lang/Object;)V",
                        "sun/misc/Unsafe.park(ZJ)V",
                        "sun/misc/Unsafe.prefetchRead(Ljava/lang/Object;J)V",
                        "sun/misc/Unsafe.prefetchReadStatic(Ljava/lang/Object;J)V",
                        "sun/misc/Unsafe.prefetchWrite(Ljava/lang/Object;J)V",
                        "sun/misc/Unsafe.prefetchWriteStatic(Ljava/lang/Object;J)V",
                        "sun/misc/Unsafe.unpark(Ljava/lang/Object;)V");

        // These only exist to assist escape analysis in C2
        add(ignore,
                        "java/lang/Throwable.fillInStackTrace()Ljava/lang/Throwable;");

        // These are only used for the security handling during stack walking
        add(ignore,
                        "java/lang/reflect/Method.invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");

        // These are marker intrinsic ids only
        add(ignore,
                        "java/lang/invoke/MethodHandle.<compiledLambdaForm>*",
                        "java/lang/invoke/MethodHandle.invoke*");

        // These are implemented through lowering
        add(ignore,
                        "java/lang/ref/Reference.get()Ljava/lang/Object;");

        // These are only used by C1
        add(ignore,
                        "java/nio/Buffer.checkIndex(I)I");

        // These do general compiler optimizations and convert min/max to cmov instructions. We are
        // ignoring them as cmovs are not necessarily beneficial.
        add(ignore,
                        "java/lang/Math.max(II)I",
                        "java/lang/Math.min(II)I");

        // These are known to be implemented down stream
        add(enterprise,
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
                        "java/util/Arrays.copyOfRange([Ljava/lang/Object;IILjava/lang/Class;)[Ljava/lang/Object;");

        add(complexGuard,
                        "java/lang/Integer.bitCount(I)I",
                        "java/lang/Integer.numberOfLeadingZeros(I)I",
                        "java/lang/Integer.numberOfTrailingZeros(I)I",
                        "java/lang/Long.bitCount(J)I",
                        "java/lang/Long.numberOfLeadingZeros(J)I",
                        "java/lang/Long.numberOfTrailingZeros(J)I");

        // Relevant for Java flight recorder
        // [GR-10106] These JFR intrinsics are used for firing socket/file events via Java
        // instrumentation and are of low priority.
        add(ignore,
                        "oracle/jrockit/jfr/Timing.counterTime()J",
                        "oracle/jrockit/jfr/VMJFR.classID0(Ljava/lang/Class;)J",
                        "oracle/jrockit/jfr/VMJFR.threadID()I");

        add(ignore,
                        "jdk/jfr/internal/JVM.counterTime()J",
                        "jdk/jfr/internal/JVM.getClassId(Ljava/lang/Class;)J",
                        "jdk/jfr/internal/JVM.getEventWriter()Ljava/lang/Object;");

        add(toBeInvestigated,
                        // Similar to addExact
                        "java/lang/Math.negateExact(I)I",
                        // Similar to addExact
                        "java/lang/Math.negateExact(J)J",
                        // HotSpot MacroAssembler-based intrinsic
                        "java/lang/String.indexOf(Ljava/lang/String;)I",
                        // Can share most implementation parts with with
                        // Unsafe.allocateUninitializedArray0
                        "java/lang/reflect/Array.newArray(Ljava/lang/Class;I)Ljava/lang/Object;",
                        // HotSpot MacroAssembler-based intrinsic
                        "sun/nio/cs/ISO_8859_1$Encoder.encodeISOArray([CI[BII)I",
                        // We have implemented implCompressMultiBlock0 on JDK9+.
                        "sun/security/provider/DigestBase.implCompressMultiBlock([BII)I");

        // See JDK-8207146.
        String oopName = isJDK12OrHigher() ? "Reference" : "Object";

        if (isJDK9OrHigher()) {
            // Relevant for Java flight recorder
            add(toBeInvestigated,
                            "jdk/jfr/internal/JVM.counterTime()J",
                            "jdk/jfr/internal/JVM.getBufferWriter()Ljava/lang/Object;",
                            "jdk/jfr/internal/JVM.getClassId(Ljava/lang/Class;)J");

            add(toBeInvestigated,
                            // Only used as a marker for vectorization?
                            "java/util/stream/Streams$RangeIntSpliterator.forEachRemaining(Ljava/util/function/IntConsumer;)V",
                            // Only implemented on non-AMD64 platforms (some logic and runtime call)
                            "java/util/zip/Adler32.updateByteBuffer(IJII)I",
                            // Only implemented on non-AMD64 platforms (some logic and runtime call)
                            "java/util/zip/Adler32.updateBytes(I[BII)I",
                            // Emits a slow and a fast path and some dispatching logic
                            "jdk/internal/misc/Unsafe.allocateUninitializedArray0(Ljava/lang/Class;I)Ljava/lang/Object;",

                            // HotSpot MacroAssembler-based intrinsic
                            "sun/nio/cs/ISO_8859_1$Encoder.implEncodeISOArray([CI[BII)I");

            // Compact string support - HotSpot MacroAssembler-based intrinsic or complex C2 logic.
            add(toBeInvestigated,
                            "java/lang/StringCoding.hasNegatives([BII)Z",
                            "java/lang/StringCoding.implEncodeISOArray([BI[BII)I");
            add(ignore,
                            // handled through an intrinsic for String.equals itself
                            "java/lang/StringLatin1.equals([B[B)Z",

                            // handled by an intrinsic for StringLatin1.indexOf([BI[BII)I
                            "java/lang/StringLatin1.indexOf([B[B)I",

                            // handled through an intrinsic for String.equals itself
                            "java/lang/StringUTF16.equals([B[B)Z",

                            // handled by an intrinsic for StringUTF16.indexOfUnsafe
                            "java/lang/StringUTF16.indexOf([BI[BII)I",
                            "java/lang/StringUTF16.indexOf([B[B)I",

                            // handled by an intrinsic for StringUTF16.indexOfCharUnsafe
                            "java/lang/StringUTF16.indexOfChar([BIII)I",

                            // handled by an intrinsic for StringUTF16.indexOfLatin1Unsafe
                            "java/lang/StringUTF16.indexOfLatin1([BI[BII)I",
                            "java/lang/StringUTF16.indexOfLatin1([B[B)I");

            if (!config.useAESCTRIntrinsics) {
                add(ignore,
                                "com/sun/crypto/provider/CounterMode.implCrypt([BII[BI)I");
            }
            if (!config.useGHASHIntrinsics()) {
                add(ignore,
                                "com/sun/crypto/provider/GHASH.processBlocks([BII[J[J)V");
            }
            if (!config.useFMAIntrinsics) {
                add(ignore,
                                "java/lang/Math.fma(DDD)D",
                                "java/lang/Math.fma(FFF)F");
            }
        }

        if (isJDK10OrHigher()) {
            if (!(arch instanceof AArch64)) {
                add(toBeInvestigated,
                                "java/lang/Math.multiplyHigh(JJ)J");
            }
        }

        if (hasAESElectronicCodebookStubRoutineFields(config)) {
            add(toBeInvestigated,
                            "com/sun/crypto/provider/ElectronicCodeBook.implECBDecrypt([BII[BI)I",
                            "com/sun/crypto/provider/ElectronicCodeBook.implECBEncrypt([BII[BI)I");
        }

        if (isJDK11OrHigher()) {
            if (arch instanceof AMD64) {
                add(toBeInvestigated,
                                "java/lang/Math.abs(I)I",
                                "java/lang/Math.abs(J)J");

                if (!((AMD64) arch).getFeatures().contains(AMD64.CPUFeature.AVX)) {
                    add(ignore,
                                    "java/lang/Math.max(DD)D",
                                    "java/lang/Math.max(FF)F",
                                    "java/lang/Math.min(DD)D",
                                    "java/lang/Math.min(FF)F");
                }

                if (!((AMD64) arch).getFeatures().contains(AMD64.CPUFeature.AVX512VL)) {
                    add(ignore,
                                    "java/lang/Math.copySign(DD)D",
                                    "java/lang/Math.copySign(FF)F");
                }
            }
            add(toBeInvestigated,
                            "java/lang/CharacterDataLatin1.isDigit(I)Z",
                            "java/lang/CharacterDataLatin1.isLowerCase(I)Z",
                            "java/lang/CharacterDataLatin1.isUpperCase(I)Z",
                            "java/lang/CharacterDataLatin1.isWhitespace(I)Z",
                            "jdk/jfr/internal/JVM.getEventWriter()Ljava/lang/Object;");
            if (!config.useBase64Intrinsics()) {
                add(ignore,
                                "java/util/Base64$Encoder.encodeBlock([BII[BIZ)V");
            }
        }

        if (isJDK13OrHigher()) {
            if (!(arch instanceof AArch64)) {
                add(toBeInvestigated,
                                "java/lang/Math.abs(I)I",
                                "java/lang/Math.abs(J)J");

                if (arch instanceof AMD64) {
                    if (!((AMD64) arch).getFeatures().contains(AMD64.CPUFeature.AVX)) {
                        add(ignore,
                                        "java/lang/Math.max(DD)D",
                                        "java/lang/Math.max(FF)F",
                                        "java/lang/Math.min(DD)D",
                                        "java/lang/Math.min(FF)F");
                    }
                } else {
                    add(toBeInvestigated,
                                    "java/lang/Math.max(DD)D",
                                    "java/lang/Math.max(FF)F",
                                    "java/lang/Math.min(DD)D",
                                    "java/lang/Math.min(FF)F");
                }
            }
        }

        if (isJDK14OrHigher()) {
            add(toBeInvestigated,
                            "java/math/BigInteger.shiftLeftImplWorker([I[IIII)V",
                            "java/math/BigInteger.shiftRightImplWorker([I[IIII)V");
        }

        if (isJDK16OrHigher()) {
            // JDK-8258558
            add(ignore, "java/lang/Object.<blackhole>*");

            add(toBeInvestigated,
                            // Added by JDK-8173585: Intrinsify StringLatin1.indexOf(char)
                            // TODO: Enhance StringLatin1IndexOfNode to support this
                            "java/lang/StringLatin1.indexOfChar([BIII)I",

                            // JDK-8254231: Implementation of Foreign Linker API (Incubator)
                            "java/lang/invoke/MethodHandle.linkToNative*",

                            // JDK-8248188: Add IntrinsicCandidate and API for Base64 decoding
                            "java/util/Base64$Decoder.decodeBlock([BII[BIZ)I",

                            // JDK-8255150: Add utility methods to check long indexes and ranges
                            "jdk/internal/util/Preconditions.checkIndex(JJLjava/util/function/BiFunction;)J",

                            // JDK-8223347: Integration of Vector API (Incubator)
                            // @formatter:off
                            "jdk/internal/vm/vector/VectorSupport.binaryOp(ILjava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;",
                            "jdk/internal/vm/vector/VectorSupport.blend(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljdk/internal/vm/vector/VectorSupport$VectorBlendOp;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                            "jdk/internal/vm/vector/VectorSupport.broadcastCoerced(Ljava/lang/Class;Ljava/lang/Class;IJLjdk/internal/vm/vector/VectorSupport$VectorSpecies;Ljdk/internal/vm/vector/VectorSupport$BroadcastOperation;)Ljava/lang/Object;",
                            "jdk/internal/vm/vector/VectorSupport.broadcastInt(ILjava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;ILjdk/internal/vm/vector/VectorSupport$VectorBroadcastIntOp;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                            "jdk/internal/vm/vector/VectorSupport.compare(ILjava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorCompareOp;)Ljdk/internal/vm/vector/VectorSupport$VectorMask;",
                            "jdk/internal/vm/vector/VectorSupport.convert(ILjava/lang/Class;Ljava/lang/Class;ILjava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$VectorPayload;Ljdk/internal/vm/vector/VectorSupport$VectorSpecies;Ljdk/internal/vm/vector/VectorSupport$VectorConvertOp;)Ljdk/internal/vm/vector/VectorSupport$VectorPayload;",
                            "jdk/internal/vm/vector/VectorSupport.extract(Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;ILjdk/internal/vm/vector/VectorSupport$VecExtractOp;)J",
                            "jdk/internal/vm/vector/VectorSupport.insert(Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;IJLjdk/internal/vm/vector/VectorSupport$VecInsertOp;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                            "jdk/internal/vm/vector/VectorSupport.load(Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;JLjava/lang/Object;ILjdk/internal/vm/vector/VectorSupport$VectorSpecies;Ljdk/internal/vm/vector/VectorSupport$LoadOperation;)Ljava/lang/Object;",
                            "jdk/internal/vm/vector/VectorSupport.loadWithMap(Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Class;Ljava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$Vector;Ljava/lang/Object;I[IILjdk/internal/vm/vector/VectorSupport$VectorSpecies;Ljdk/internal/vm/vector/VectorSupport$LoadVectorOperationWithMap;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                            "jdk/internal/vm/vector/VectorSupport.maskReductionCoerced(ILjava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;Ljdk/internal/vm/vector/VectorSupport$VectorMaskOp;)I",
                            "jdk/internal/vm/vector/VectorSupport.maybeRebox(Ljava/lang/Object;)Ljava/lang/Object;",
                            "jdk/internal/vm/vector/VectorSupport.rearrangeOp(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorShuffle;Ljdk/internal/vm/vector/VectorSupport$VectorRearrangeOp;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                            "jdk/internal/vm/vector/VectorSupport.reductionCoerced(ILjava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;Ljava/util/function/Function;)J",
                            "jdk/internal/vm/vector/VectorSupport.shuffleIota(Ljava/lang/Class;Ljava/lang/Class;Ljdk/internal/vm/vector/VectorSupport$VectorSpecies;IIIILjdk/internal/vm/vector/VectorSupport$ShuffleIotaOperation;)Ljdk/internal/vm/vector/VectorSupport$VectorShuffle;",
                            "jdk/internal/vm/vector/VectorSupport.shuffleToVector(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;Ljdk/internal/vm/vector/VectorSupport$VectorShuffle;ILjdk/internal/vm/vector/VectorSupport$ShuffleToVectorOperation;)Ljava/lang/Object;",
                            "jdk/internal/vm/vector/VectorSupport.store(Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$Vector;Ljava/lang/Object;ILjdk/internal/vm/vector/VectorSupport$StoreVectorOperation;)V",
                            "jdk/internal/vm/vector/VectorSupport.storeWithMap(Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Class;Ljava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$Vector;Ljava/lang/Object;I[IILjdk/internal/vm/vector/VectorSupport$StoreVectorOperationWithMap;)V",
                            "jdk/internal/vm/vector/VectorSupport.ternaryOp(ILjava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljdk/internal/vm/vector/VectorSupport$TernaryOperation;)Ljava/lang/Object;",
                            "jdk/internal/vm/vector/VectorSupport.test(ILjava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;)Z",
                            "jdk/internal/vm/vector/VectorSupport.unaryOp(ILjava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;",
                            // @formatter:on

                            // JDK-8253525: Implement getInstanceSize/sizeOf intrinsics
                            "sun/instrument/InstrumentationImpl.getObjectSize0(JLjava/lang/Object;)J",

                            // 8252204: AArch64: Implement SHA3 accelerator/intrinsic
                            "sun/security/provider/MD5.implCompress0([BI)V",

                            "sun/security/provider/SHA3.implCompress0([BI)V");
        }

        if (!config.inlineNotify()) {
            add(ignore, "java/lang/Object.notify()V");
        }
        if (!config.inlineNotifyAll()) {
            add(ignore, "java/lang/Object.notifyAll()V");
        }

        if (!(arch instanceof AMD64)) {
            // Can we implement these on non-AMD64 platforms? C2 seems to.
            add(toBeInvestigated,
                            "com/sun/crypto/provider/CounterMode.implCrypt([BII[BI)I",
                            "java/lang/String.compareTo(Ljava/lang/String;)I",
                            "java/lang/StringLatin1.indexOf([B[B)I",
                            "java/lang/StringLatin1.inflate([BI[BII)V",
                            "java/lang/StringLatin1.inflate([BI[CII)V",
                            "java/lang/StringUTF16.compress([BI[BII)I",
                            "java/lang/StringUTF16.compress([CI[BII)I",
                            "java/lang/StringUTF16.indexOf([BI[BII)I",
                            "java/lang/StringUTF16.indexOf([B[B)I",
                            "java/lang/StringUTF16.indexOfChar([BIII)I",
                            "java/lang/StringUTF16.indexOfLatin1([BI[BII)I",
                            "java/lang/StringUTF16.indexOfLatin1([B[B)I",
                            "sun/misc/Unsafe.getAndSet" + oopName + "(Ljava/lang/Object;JLjava/lang/Object;)Ljava/lang/Object;");

            if (isJDK9OrHigher()) {
                add(toBeInvestigated,
                                "java/lang/Thread.onSpinWait()V",
                                "java/util/ArraysSupport.vectorizedMismatch(Ljava/lang/Object;JLjava/lang/Object;JII)I");
            }
            if (isJDK10OrHigher()) {
                add(toBeInvestigated,
                                "jdk/internal/util/ArraysSupport.vectorizedMismatch(Ljava/lang/Object;JLjava/lang/Object;JII)I");
            }
        }

        /*
         * The intrinsics down here are known to be implemented but they are not always enabled on
         * the HotSpot side (e.g., because they require certain CPU features). So, we are ignoring
         * them if the HotSpot config tells us that they can't be used.
         */

        // CRC32 intrinsics
        if (!config.useCRC32Intrinsics) {
            add(ignore, "java/util/zip/CRC32.update(II)I");
            if (isJDK9OrHigher()) {
                add(ignore,
                                "java/util/zip/CRC32.updateByteBuffer0(IJII)I",
                                "java/util/zip/CRC32.updateBytes0(I[BII)I");
            } else {
                add(ignore,
                                "java/util/zip/CRC32.updateByteBuffer(IJII)I",
                                "java/util/zip/CRC32.updateBytes(I[BII)I");
            }
        }

        // CRC32C intrinsics
        if (!config.useCRC32CIntrinsics) {
            add(ignore,
                            "java/util/zip/CRC32C.updateBytes(I[BII)I",
                            "java/util/zip/CRC32C.updateDirectByteBuffer(IJII)I");
        }

        String cbcEncryptName = HotSpotGraphBuilderPlugins.lookupIntrinsicName(config, "com/sun/crypto/provider/CipherBlockChaining", "implEncrypt", "encrypt");
        String cbcDecryptName = HotSpotGraphBuilderPlugins.lookupIntrinsicName(config, "com/sun/crypto/provider/CipherBlockChaining", "implDecrypt", "decrypt");
        String aesEncryptName = HotSpotGraphBuilderPlugins.lookupIntrinsicName(config, "com/sun/crypto/provider/AESCrypt", "implEncryptBlock", "encryptBlock");
        String aesDecryptName = HotSpotGraphBuilderPlugins.lookupIntrinsicName(config, "com/sun/crypto/provider/AESCrypt", "implDecryptBlock", "decryptBlock");

        // AES intrinsics
        if (!config.useAESIntrinsics) {
            add(ignore,
                            "com/sun/crypto/provider/AESCrypt." + aesDecryptName + "([BI[BI)V",
                            "com/sun/crypto/provider/AESCrypt." + aesEncryptName + "([BI[BI)V",
                            "com/sun/crypto/provider/CipherBlockChaining." + cbcDecryptName + "([BII[BI)I",
                            "com/sun/crypto/provider/CipherBlockChaining." + cbcEncryptName + "([BII[BI)I");
        }

        // BigInteger intrinsics
        if (!config.useMultiplyToLenIntrinsic()) {
            if (isJDK9OrHigher()) {
                add(ignore, "java/math/BigInteger.implMultiplyToLen([II[II[I)[I");
            } else {
                add(ignore, "java/math/BigInteger.multiplyToLen([II[II[I)[I");
            }
        }
        if (!config.useMulAddIntrinsic()) {
            add(ignore, "java/math/BigInteger.implMulAdd([I[IIII)I");
        }
        if (!config.useMontgomeryMultiplyIntrinsic()) {
            add(ignore, "java/math/BigInteger.implMontgomeryMultiply([I[I[IIJ[I)[I");
        }
        if (!config.useMontgomerySquareIntrinsic()) {
            add(ignore, "java/math/BigInteger.implMontgomerySquare([I[IIJ[I)[I");
        }
        if (!config.useSquareToLenIntrinsic()) {
            add(ignore, "java/math/BigInteger.implSquareToLen([II[II)[I");
        }
        // DigestBase intrinsics
        if (HotSpotGraphBuilderPlugins.isIntrinsicName(config, "sun/security/provider/DigestBase", "implCompressMultiBlock0") &&
                        !(config.useSHA1Intrinsics() || config.useSHA256Intrinsics() || config.useSHA512Intrinsics())) {
            add(ignore, "sun/security/provider/DigestBase.implCompressMultiBlock0([BII)I");
        }
        // SHA intrinsics
        String shaCompressName = HotSpotGraphBuilderPlugins.lookupIntrinsicName(config, "sun/security/provider/SHA", "implCompress0", "implCompress");
        if (!config.useSHA1Intrinsics()) {
            add(ignore, "sun/security/provider/SHA." + shaCompressName + "([BI)V");
        }
        if (!config.useSHA256Intrinsics()) {
            add(ignore, "sun/security/provider/SHA2." + shaCompressName + "([BI)V");
        }
        if (!config.useSHA512Intrinsics()) {
            add(ignore, "sun/security/provider/SHA5." + shaCompressName + "([BI)V");
        }
    }

    /**
     * Determines if the StubRoutines corresponding to JDK-8225625 are potentially available. That
     * is, are the fields pointing to these routines defined. The routines themselves will only be
     * generated if the following code in the VM condition is true:
     * {@code VM_Version::supports_vaes() && VM_Version::supports_avx512vl() && VM_Version::supports_avx512dq()}
     */
    private static boolean hasAESElectronicCodebookStubRoutineFields(GraalHotSpotVMConfig config) {
        HotSpotVMConfigStore store = config.getStore();
        Map<String, VMField> fields = store.getFields();
        return fields.containsKey("StubRoutines::_electronicCodeBook_encryptAESCrypt") &&
                        fields.containsKey("StubRoutines::_electronicCodeBook_decryptAESCrypt");
    }

    private static boolean isJDK9OrHigher() {
        return JavaVersionUtil.JAVA_SPEC >= 9;
    }

    private static boolean isJDK10OrHigher() {
        return JavaVersionUtil.JAVA_SPEC >= 10;
    }

    private static boolean isJDK11OrHigher() {
        return JavaVersionUtil.JAVA_SPEC >= 11;
    }

    private static boolean isJDK12OrHigher() {
        return JavaVersionUtil.JAVA_SPEC >= 12;
    }

    private static boolean isJDK13OrHigher() {
        return JavaVersionUtil.JAVA_SPEC >= 13;
    }

    private static boolean isJDK14OrHigher() {
        return JavaVersionUtil.JAVA_SPEC >= 14;
    }

    private static boolean isJDK16OrHigher() {
        return JavaVersionUtil.JAVA_SPEC >= 16;
    }

    /**
     * Test if the given intrinsic candidate is in the {@link #ignore} category -- it will not be
     * implemented in Graal.
     */
    public boolean isIgnored(String method) {
        return ignore.contains(method);
    }

    /**
     * Test if the given intrinsic candidate is in the {@link #complexGuard} category -- its HotSpot
     * intrinsic is guarded by complex condition and difficult to duplicate in Graal.
     */
    public boolean isComplexGuard(String method) {
        return complexGuard.contains(method);
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
        return isIgnored(method) || isImplementedInEnterprise(method) || isMissing(method) || isIgnored(method);
    }
}
