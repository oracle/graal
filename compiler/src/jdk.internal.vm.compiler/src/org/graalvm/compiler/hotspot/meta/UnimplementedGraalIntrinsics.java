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
package org.graalvm.compiler.hotspot.meta;

import static org.graalvm.compiler.serviceprovider.JavaVersionUtil.JAVA_SPEC;

import java.util.Arrays;
import java.util.Collection;
import java.util.Formatter;
import java.util.Set;
import java.util.TreeSet;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.replacements.nodes.AESNode;
import org.graalvm.compiler.replacements.nodes.CipherBlockChainingAESNode;
import org.graalvm.compiler.replacements.nodes.CounterModeAESNode;
import org.graalvm.compiler.replacements.nodes.GHASHProcessBlocksNode;
import org.graalvm.compiler.replacements.nodes.MessageDigestNode.SHA1Node;
import org.graalvm.compiler.replacements.nodes.MessageDigestNode.SHA256Node;
import org.graalvm.compiler.replacements.nodes.MessageDigestNode.SHA3Node;
import org.graalvm.compiler.replacements.nodes.MessageDigestNode.SHA512Node;

import jdk.vm.ci.aarch64.AArch64;
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

        // Relevant for Java flight recorder
        // [GR-10106] These JFR intrinsics are used for firing socket/file events via Java
        // instrumentation and are of low priority.
        add(ignore,
                        "jdk/jfr/internal/JVM.commit(J)J",
                        "jdk/jfr/internal/JVM.counterTime()J",
                        "jdk/jfr/internal/JVM.getBufferWriter()Ljava/lang/Object;",
                        "jdk/jfr/internal/JVM.getClassId(Ljava/lang/Class;)J",
                        "jdk/jfr/internal/JVM.getEventWriter()Ljava/lang/Object;",
                        "oracle/jrockit/jfr/Timing.counterTime()J",
                        "oracle/jrockit/jfr/VMJFR.classID0(Ljava/lang/Class;)J",
                        "oracle/jrockit/jfr/VMJFR.threadID()I");

        add(ignore,
                        // ppc only
                        "java/lang/CharacterDataLatin1.isLowerCase(I)Z",
                        "java/lang/CharacterDataLatin1.isUpperCase(I)Z",
                        "java/lang/CharacterDataLatin1.isWhitespace(I)Z",
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

        // Only used as a marker for vectorization
        add(ignore, "java/util/stream/Streams$RangeIntSpliterator.forEachRemaining(Ljava/util/function/IntConsumer;)V");

        // JDK-8258558
        add(ignore, "java/lang/Object.<blackhole>*");

        /*
         * The intrinsics down here are known to be implemented but they are not always enabled on
         * the HotSpot side (e.g., because they require certain CPU features). So, we are ignoring
         * them if the HotSpot config tells us that they can't be used.
         */
        if (arch instanceof AMD64) {
            if (!((AMD64) arch).getFeatures().contains(AMD64.CPUFeature.POPCNT)) {
                add(ignore,
                                "java/lang/Integer.bitCount(I)I",
                                "java/lang/Long.bitCount(J)I");
            }
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

        if (!config.inlineNotify()) {
            add(ignore, "java/lang/Object.notify()V");
        }
        if (!config.inlineNotifyAll()) {
            add(ignore, "java/lang/Object.notifyAll()V");
        }

        if (config.base64EncodeBlock == 0L) {
            add(ignore, "java/util/Base64$Encoder.encodeBlock([BII[BIZ)V");
        }
        if (config.base64DecodeBlock == 0L) {
            add(ignore,
                            "java/util/Base64$Decoder.decodeBlock([BII[BIZ)I",
                            "java/util/Base64$Decoder.decodeBlock([BII[BIZZ)I");
        }

        if (!GHASHProcessBlocksNode.isSupported(arch)) {
            add(ignore,
                            "com/sun/crypto/provider/GHASH.processBlocks([BII[J[J)V");
        }
        if (!config.useFMAIntrinsics) {
            add(ignore,
                            "java/lang/Math.fma(DDD)D",
                            "java/lang/Math.fma(FFF)F");
        }

        // CRC32 intrinsics
        if (!config.useCRC32Intrinsics) {
            add(ignore,
                            "java/util/zip/CRC32.update(II)I",
                            "java/util/zip/CRC32.updateByteBuffer0(IJII)I",
                            "java/util/zip/CRC32.updateBytes0(I[BII)I");
        }

        // CRC32C intrinsics
        if (!config.useCRC32CIntrinsics) {
            add(ignore,
                            "java/util/zip/CRC32C.updateBytes(I[BII)I",
                            "java/util/zip/CRC32C.updateDirectByteBuffer(IJII)I");
        }

        // AES intrinsics
        if (!AESNode.isSupported(arch)) {
            add(ignore,
                            "com/sun/crypto/provider/AESCrypt.implDecryptBlock([BI[BI)V",
                            "com/sun/crypto/provider/AESCrypt.implEncryptBlock([BI[BI)V");
        }

        if (!CounterModeAESNode.isSupported(arch)) {
            add(ignore,
                            "com/sun/crypto/provider/CounterMode.implCrypt([BII[BI)I");
        }

        if (!CipherBlockChainingAESNode.isSupported(arch)) {
            add(ignore,
                            "com/sun/crypto/provider/CipherBlockChaining.implDecrypt([BII[BI)I",
                            "com/sun/crypto/provider/CipherBlockChaining.implEncrypt([BII[BI)I");
        }

        // BigInteger intrinsics
        if (!config.useMontgomeryMultiplyIntrinsic()) {
            add(ignore, "java/math/BigInteger.implMontgomeryMultiply([I[I[IIJ[I)[I");
        }
        if (!config.useMontgomerySquareIntrinsic()) {
            add(ignore, "java/math/BigInteger.implMontgomerySquare([I[IIJ[I)[I");
        }
        // DigestBase intrinsics
        if (HotSpotGraphBuilderPlugins.isIntrinsicName(config, "sun/security/provider/DigestBase", "implCompressMultiBlock0") &&
                        !(config.md5ImplCompressMultiBlock != 0L || config.useSHA1Intrinsics() || config.useSHA256Intrinsics() || config.useSHA512Intrinsics() ||
                                        config.sha3ImplCompressMultiBlock != 0L)) {
            add(ignore, "sun/security/provider/DigestBase.implCompressMultiBlock0([BII)I");
        }
        // SHA intrinsics
        if (!SHA1Node.isSupported(arch)) {
            add(ignore, "sun/security/provider/SHA.implCompress0([BI)V");
        }
        if (!SHA256Node.isSupported(arch)) {
            add(ignore, "sun/security/provider/SHA2.implCompress0([BI)V");
        }
        if (!SHA3Node.isSupported(arch)) {
            add(ignore, "sun/security/provider/SHA3.implCompress0([BI)V");
        }
        if (!SHA512Node.isSupported(arch)) {
            add(ignore, "sun/security/provider/SHA5.implCompress0([BI)V");
        }
        if (config.updateBytesAdler32 == 0L) {
            add(ignore,
                            "java/util/zip/Adler32.updateByteBuffer(IJII)I",
                            "java/util/zip/Adler32.updateBytes(I[BII)I");
        }
        if (config.bigIntegerLeftShiftWorker == 0L) {
            add(ignore, "java/math/BigInteger.shiftLeftImplWorker([I[IIII)V");
        }
        if (config.bigIntegerRightShiftWorker == 0L) {
            add(ignore, "java/math/BigInteger.shiftRightImplWorker([I[IIII)V");
        }
        if (config.electronicCodeBookEncrypt == 0L) {
            add(ignore, "com/sun/crypto/provider/ElectronicCodeBook.implECBDecrypt([BII[BI)I");
        }
        if (config.electronicCodeBookDecrypt == 0L) {
            add(ignore, "com/sun/crypto/provider/ElectronicCodeBook.implECBEncrypt([BII[BI)I");
        }
        if (config.poly1305ProcessBlocks == 0L) {
            add(ignore, "com/sun/crypto/provider/Poly1305.processMultipleBlocks([BII[J[J)V");
        }
        if (config.chacha20Block == 0L) {
            add(ignore, "com/sun/crypto/provider/ChaCha20Cipher.implChaCha20Block([I[B)I");
        }

        if (JAVA_SPEC >= 18) {
            add(ignore,
                            "java/lang/StrictMath.max(II)I",
                            "java/lang/StrictMath.min(II)I");
            if (arch instanceof AMD64) {
                if (!((AMD64) arch).getFeatures().contains(AMD64.CPUFeature.AVX)) {
                    add(ignore,
                                    "java/lang/StrictMath.max(DD)D",
                                    "java/lang/StrictMath.max(FF)F",
                                    "java/lang/StrictMath.min(DD)D",
                                    "java/lang/StrictMath.min(FF)F");
                }
            }
            if (config.galoisCounterModeCrypt == 0L) {
                add(ignore, "com/sun/crypto/provider/GaloisCounterMode.implGCMCrypt0([BII[BI[BILcom/sun/crypto/provider/GCTR;Lcom/sun/crypto/provider/GHASH;)I");
            }
        }

        if (JAVA_SPEC >= 19) {
            if (config.threadScopedValueCacheOffset == -1L) {
                add(ignore,
                                "java/lang/Thread.scopedValueCache()[Ljava/lang/Object;",
                                "java/lang/Thread.setScopedValueCache([Ljava/lang/Object;)V");
            }

            if (arch instanceof AMD64) {
                if (!((AMD64) arch).getFeatures().contains(AMD64.CPUFeature.AVX512DQ)) {
                    add(ignore,
                                    "java/lang/Double.isInfinite(D)Z",
                                    "java/lang/Float.isInfinite(F)Z");
                }
            }
        }

        if (JAVA_SPEC >= 20) {
            // without JIT implementation
            add(ignore,
                            "java/lang/Thread.findScopedValueBindings()Ljava/lang/Object;",
                            "jdk/internal/vm/Continuation.doYield()I",
                            "jdk/internal/vm/Continuation.enter(Ljdk/internal/vm/Continuation;Z)V",
                            "jdk/internal/vm/Continuation.enterSpecial(Ljdk/internal/vm/Continuation;ZZ)V");

            // same ignore reason as "jdk/jfr/internal/JVM.getEventWriter()Ljava/lang/Object;"
            // see also https://bugs.openjdk.org/browse/JDK-8286480
            add(ignore,
                            "jdk/jfr/internal/JVM.getEventWriter()Ljdk/jfr/internal/event/EventWriter;");

            if (arch instanceof AMD64) {
                if (!((AMD64) arch).getFeatures().contains(AMD64.CPUFeature.valueOf("GFNI"))) {
                    add(ignore,
                                    "java/lang/Integer.reverse(I)I",
                                    "java/lang/Long.reverse(J)J");
                }
            }
        }

        // JDK-8254231: Implementation of Foreign Linker API (Incubator)
        add(toBeInvestigated,
                        "java/lang/invoke/MethodHandle.linkToNative*");

        if (JAVA_SPEC == 17) {
            // JDK-8223347: Integration of Vector API (Incubator)
            add(toBeInvestigated, // @formatter:off
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
                            "jdk/internal/vm/vector/VectorSupport.unaryOp(ILjava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"
                            // @formatter:on
            );
        }

        if (JAVA_SPEC >= 20) {
            // JDK-8223347: Integration of Vector API (Incubator)
            add(toBeInvestigated, // @formatter:off
                            "jdk/internal/vm/vector/VectorSupport.compressExpandOp(ILjava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljdk/internal/vm/vector/VectorSupport$CompressExpandOperation;)Ljdk/internal/vm/vector/VectorSupport$VectorPayload;",
                            "jdk/internal/vm/vector/VectorSupport.extract(Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;ILjdk/internal/vm/vector/VectorSupport$VecExtractOp;)J",
                            "jdk/internal/vm/vector/VectorSupport.indexPartiallyInUpperRange(Ljava/lang/Class;Ljava/lang/Class;IJJLjdk/internal/vm/vector/VectorSupport$IndexPartiallyInUpperRangeOperation;)Ljdk/internal/vm/vector/VectorSupport$VectorMask;",
                            "jdk/internal/vm/vector/VectorSupport.indexVector(Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;ILjdk/internal/vm/vector/VectorSupport$VectorSpecies;Ljdk/internal/vm/vector/VectorSupport$IndexOperation;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                            "jdk/internal/vm/vector/VectorSupport.insert(Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;IJLjdk/internal/vm/vector/VectorSupport$VecInsertOp;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                            "jdk/internal/vm/vector/VectorSupport.loadMasked(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$VectorMask;ILjava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$VectorSpecies;Ljdk/internal/vm/vector/VectorSupport$LoadVectorMaskedOperation;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                            "jdk/internal/vm/vector/VectorSupport.loadWithMap(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Class;Ljava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljava/lang/Object;I[IILjdk/internal/vm/vector/VectorSupport$VectorSpecies;Ljdk/internal/vm/vector/VectorSupport$LoadVectorOperationWithMap;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                            "jdk/internal/vm/vector/VectorSupport.maskReductionCoerced(ILjava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$VectorMask;Ljdk/internal/vm/vector/VectorSupport$VectorMaskOp;)J",
                            "jdk/internal/vm/vector/VectorSupport.maybeRebox(Ljdk/internal/vm/vector/VectorSupport$VectorPayload;)Ljdk/internal/vm/vector/VectorSupport$VectorPayload;",
                            "jdk/internal/vm/vector/VectorSupport.rearrangeOp(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorShuffle;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljdk/internal/vm/vector/VectorSupport$VectorRearrangeOp;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                            "jdk/internal/vm/vector/VectorSupport.storeMasked(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$StoreVectorMaskedOperation;)V",
                            "jdk/internal/vm/vector/VectorSupport.storeWithMap(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Class;Ljava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljava/lang/Object;I[IILjdk/internal/vm/vector/VectorSupport$StoreVectorOperationWithMap;)V",
                            "jdk/internal/vm/vector/VectorSupport.ternaryOp(ILjava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljdk/internal/vm/vector/VectorSupport$TernaryOperation;)Ljdk/internal/vm/vector/VectorSupport$Vector;"
                            // @formatter:on
            );
            // not implemented yet, watch https://bugs.openjdk.org/browse/JDK-8294198
            add(toBeInvestigated,
                            "java/lang/Double.isFinite(D)Z",
                            "java/lang/Float.isFinite(F)Z");

            if (arch instanceof AArch64) {
                // not implemented yet, watch https://bugs.openjdk.org/browse/JDK-8285868
                add(toBeInvestigated,
                                "java/lang/Double.isInfinite(D)Z",
                                "java/lang/Float.isInfinite(F)Z");

                // not implemented yet, watch https://bugs.openjdk.org/browse/JDK-8283892
                add(toBeInvestigated,
                                "java/lang/Integer.compress(II)I",
                                "java/lang/Integer.expand(II)I",
                                "java/lang/Long.compress(JJ)J",
                                "java/lang/Long.expand(JJ)J");

                if (JAVA_SPEC >= 21) {
                    // not implemented yet, watch JDK-8282664
                    add(toBeInvestigated, "jdk/internal/util/ArraysSupport.vectorizedHashCode(Ljava/lang/Object;IIII)I");
                }
            }
        }

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
                        "jdk/internal/vm/vector/VectorSupport.load(Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;JLjava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$VectorSpecies;Ljdk/internal/vm/vector/VectorSupport$LoadOperation;)Ljdk/internal/vm/vector/VectorSupport$VectorPayload;",
                        "jdk/internal/vm/vector/VectorSupport.reductionCoerced(ILjava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;ILjdk/internal/vm/vector/VectorSupport$Vector;Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljdk/internal/vm/vector/VectorSupport$ReductionOperation;)J",
                        "jdk/internal/vm/vector/VectorSupport.shuffleIota(Ljava/lang/Class;Ljava/lang/Class;Ljdk/internal/vm/vector/VectorSupport$VectorSpecies;IIIILjdk/internal/vm/vector/VectorSupport$ShuffleIotaOperation;)Ljdk/internal/vm/vector/VectorSupport$VectorShuffle;",
                        "jdk/internal/vm/vector/VectorSupport.shuffleToVector(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;Ljdk/internal/vm/vector/VectorSupport$VectorShuffle;ILjdk/internal/vm/vector/VectorSupport$ShuffleToVectorOperation;)Ljdk/internal/vm/vector/VectorSupport$Vector;",
                        "jdk/internal/vm/vector/VectorSupport.store(Ljava/lang/Class;Ljava/lang/Class;ILjava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$VectorPayload;Ljava/lang/Object;JLjdk/internal/vm/vector/VectorSupport$StoreVectorOperation;)V",
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
