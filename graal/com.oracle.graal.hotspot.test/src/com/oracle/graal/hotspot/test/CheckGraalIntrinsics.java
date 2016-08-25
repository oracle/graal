/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import com.oracle.graal.api.test.Graal;
import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.runtime.RuntimeProvider;
import com.oracle.graal.test.GraalTest;

import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.hotspot.VMIntrinsicMethod;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Checks the set of intrinsics implemented by Graal against the set of intrinsics declared by
 * HotSpot. The purpose of this test is to detect when new intrinsics are added in HotSpot so they
 * can be added to Graal.
 */
public class CheckGraalIntrinsics extends GraalTest {

    public static boolean match(ResolvedJavaMethod method, VMIntrinsicMethod intrinsic) {
        if (intrinsic.name.equals(method.getName())) {
            if (intrinsic.descriptor.equals(method.getSignature().toMethodDescriptor())) {
                String declaringClass = method.getDeclaringClass().toClassName().replace('.', '/');
                if (declaringClass.equals(intrinsic.declaringClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static VMIntrinsicMethod findIntrinsic(List<VMIntrinsicMethod> intrinsics, ResolvedJavaMethod method) {
        for (VMIntrinsicMethod intrinsic : intrinsics) {
            if (match(method, intrinsic)) {
                return intrinsic;
            }
        }
        return null;
    }

    private static ResolvedJavaMethod findMethod(Set<ResolvedJavaMethod> methods, VMIntrinsicMethod intrinsic) {
        for (ResolvedJavaMethod method : methods) {
            if (match(method, intrinsic)) {
                return method;
            }
        }
        return null;
    }

    private static ResolvedJavaMethod resolveIntrinsic(MetaAccessProvider metaAccess, VMIntrinsicMethod intrinsic) throws ClassNotFoundException {
        Class<?> c = Class.forName(intrinsic.declaringClass.replace('/', '.'), false, CheckGraalIntrinsics.class.getClassLoader());
        for (Method javaMethod : c.getDeclaredMethods()) {
            if (javaMethod.getName().equals(intrinsic.name)) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(javaMethod);
                if (intrinsic.descriptor.equals("*")) {
                    // Signature polymorphic method - name match is enough
                    return method;
                } else {
                    if (method.getSignature().toMethodDescriptor().equals(intrinsic.descriptor)) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    /**
     * The set of HotSpot intrinsics that are either not of interest to Graal.
     */
    private static final Set<String> KNOWN_MISSING = new HashSet<>();

    /**
     * The set of HotSpot intrinsics that are yet to be implemented or moved to the
     * {@link #KNOWN_MISSING} set.
     */
    private static final Set<String> TO_BE_INVESTIGATED;
    static {
        if (Java8OrEarlier) {
            TO_BE_INVESTIGATED = new HashSet<>(Arrays.asList(
                            "java/lang/Math.atan2(DD)D",
                            "java/lang/Math.min(II)I",
                            "java/lang/Math.max(II)I",
                            "java/lang/Math.decrementExact(I)I",
                            "java/lang/Math.decrementExact(J)J",
                            "java/lang/Math.incrementExact(I)I",
                            "java/lang/Math.incrementExact(J)J",
                            "java/lang/Math.negateExact(I)I",
                            "java/lang/Math.negateExact(J)J",
                            "java/lang/Float.floatToIntBits(F)I",
                            "java/lang/Double.doubleToLongBits(D)J",
                            "oracle/jrockit/jfr/Timing.counterTime()J",
                            "oracle/jrockit/jfr/VMJFR.threadID()I",
                            "oracle/jrockit/jfr/VMJFR.classID0(Ljava/lang/Class;)J",
                            "java/lang/reflect/Array.newArray(Ljava/lang/Class;I)Ljava/lang/Object;",
                            "java/util/Arrays.copyOf([Ljava/lang/Object;ILjava/lang/Class;)[Ljava/lang/Object;",
                            "java/util/Arrays.copyOfRange([Ljava/lang/Object;IILjava/lang/Class;)[Ljava/lang/Object;",
                            "java/lang/String.compareTo(Ljava/lang/String;)I",
                            "java/lang/String.indexOf(Ljava/lang/String;)I",
                            "java/nio/Buffer.checkIndex(I)I",
                            "sun/nio/cs/ISO_8859_1$Encoder.encodeISOArray([CI[BII)I",
                            "java/math/BigInteger.multiplyToLen([II[II[I)[I",
                            "java/lang/ref/Reference.get()Ljava/lang/Object;",
                            "sun/security/provider/SHA.implCompress([BI)V",
                            "sun/security/provider/SHA2.implCompress([BI)V",
                            "sun/security/provider/SHA5.implCompress([BI)V",
                            "sun/security/provider/DigestBase.implCompressMultiBlock([BII)I",
                            "sun/misc/Unsafe.copyMemory(Ljava/lang/Object;JLjava/lang/Object;JJ)V",
                            "sun/misc/Unsafe.park(ZJ)V",
                            "sun/misc/Unsafe.unpark(Ljava/lang/Object;)V",
                            "sun/misc/Unsafe.prefetchRead(Ljava/lang/Object;J)V",
                            "sun/misc/Unsafe.prefetchWrite(Ljava/lang/Object;J)V",
                            "sun/misc/Unsafe.prefetchReadStatic(Ljava/lang/Object;J)V",
                            "sun/misc/Unsafe.prefetchWriteStatic(Ljava/lang/Object;J)V",
                            "java/lang/Throwable.fillInStackTrace()Ljava/lang/Throwable;",
                            "java/lang/StringBuilder.<init>()V",
                            "java/lang/StringBuilder.<init>(I)V",
                            "java/lang/StringBuilder.<init>(Ljava/lang/String;)V",
                            "java/lang/StringBuilder.append(C)Ljava/lang/StringBuilder;",
                            "java/lang/StringBuilder.append(I)Ljava/lang/StringBuilder;",
                            "java/lang/StringBuilder.append(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                            "java/lang/StringBuilder.toString()Ljava/lang/String;",
                            "java/lang/StringBuffer.<init>()V",
                            "java/lang/StringBuffer.<init>(I)V",
                            "java/lang/StringBuffer.<init>(Ljava/lang/String;)V",
                            "java/lang/StringBuffer.append(C)Ljava/lang/StringBuffer;",
                            "java/lang/StringBuffer.append(I)Ljava/lang/StringBuffer;",
                            "java/lang/StringBuffer.append(Ljava/lang/String;)Ljava/lang/StringBuffer;",
                            "java/lang/StringBuffer.toString()Ljava/lang/String;",
                            "java/lang/Integer.toString(I)Ljava/lang/String;",
                            "java/lang/String.<init>(Ljava/lang/String;)V",
                            "java/lang/reflect/Method.invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                            "java/lang/invoke/MethodHandle.invoke*",
                            "java/lang/invoke/MethodHandle.<compiledLambdaForm>*"));
        } else {
            TO_BE_INVESTIGATED = new HashSet<>(Arrays.asList(
                            "java/lang/Object.notify()V",
                            "java/lang/Object.notifyAll()V",
                            "java/lang/Math.atan2(DD)D",
                            "java/lang/Math.min(II)I",
                            "java/lang/Math.max(II)I",
                            "java/lang/Math.decrementExact(I)I",
                            "java/lang/Math.decrementExact(J)J",
                            "java/lang/Math.incrementExact(I)I",
                            "java/lang/Math.incrementExact(J)J",
                            "java/lang/Math.negateExact(I)I",
                            "java/lang/Math.negateExact(J)J",
                            "java/lang/Float.floatToIntBits(F)I",
                            "java/lang/Double.doubleToLongBits(D)J",
                            "jdk/jfr/internal/JVM.counterTime()J",
                            "java/lang/reflect/Array.newArray(Ljava/lang/Class;I)Ljava/lang/Object;",
                            "java/lang/Thread.onSpinWait()V",
                            "java/util/Arrays.copyOf([Ljava/lang/Object;ILjava/lang/Class;)[Ljava/lang/Object;",
                            "java/util/Arrays.copyOfRange([Ljava/lang/Object;IILjava/lang/Class;)[Ljava/lang/Object;",
                            "java/lang/StringUTF16.compress([CI[BII)I",
                            "java/lang/StringUTF16.compress([BI[BII)I",
                            "java/lang/StringLatin1.inflate([BI[CII)V",
                            "java/lang/StringLatin1.inflate([BI[BII)V",
                            "java/lang/StringUTF16.toBytes([CII)[B",
                            "java/lang/StringUTF16.getChars([BII[CI)V",
                            "java/lang/StringUTF16.getChar([BI)C",
                            "java/lang/StringUTF16.putChar([BII)V",
                            "java/lang/StringLatin1.compareTo([B[B)I",
                            "java/lang/StringUTF16.compareTo([B[B)I",
                            "java/lang/StringLatin1.compareToUTF16([B[B)I",
                            "java/lang/StringUTF16.compareToLatin1([B[B)I",
                            "java/lang/StringLatin1.indexOf([B[B)I",
                            "java/lang/StringUTF16.indexOf([B[B)I",
                            "java/lang/StringUTF16.indexOfLatin1([B[B)I",
                            "java/lang/StringLatin1.indexOf([BI[BII)I",
                            "java/lang/StringUTF16.indexOf([BI[BII)I",
                            "java/lang/StringUTF16.indexOfLatin1([BI[BII)I",
                            "java/lang/StringUTF16.indexOfChar([BIII)I",
                            "java/lang/StringLatin1.equals([B[B)Z",
                            "java/lang/StringUTF16.equals([B[B)Z",
                            "jdk/internal/util/Preconditions.checkIndex(IILjava/util/function/BiFunction;)I",
                            "java/nio/Buffer.checkIndex(I)I",
                            "java/lang/StringCoding.hasNegatives([BII)Z",
                            "sun/nio/cs/ISO_8859_1$Encoder.implEncodeISOArray([CI[BII)I",
                            "java/lang/StringCoding.implEncodeISOArray([BI[BII)I",
                            "java/math/BigInteger.implMultiplyToLen([II[II[I)[I",
                            "java/math/BigInteger.implSquareToLen([II[II)[I",
                            "java/math/BigInteger.implMulAdd([I[IIII)I",
                            "java/math/BigInteger.implMontgomeryMultiply([I[I[IIJ[I)[I",
                            "java/math/BigInteger.implMontgomerySquare([I[IIJ[I)[I",
                            "java/util/ArraysSupport.vectorizedMismatch(Ljava/lang/Object;JLjava/lang/Object;JII)I",
                            "java/lang/ref/Reference.get()Ljava/lang/Object;",
                            "com/sun/crypto/provider/CounterMode.implCrypt([BII[BI)I",
                            "sun/security/provider/SHA.implCompress0([BI)V",
                            "sun/security/provider/SHA2.implCompress0([BI)V",
                            "sun/security/provider/SHA5.implCompress0([BI)V",
                            "sun/security/provider/DigestBase.implCompressMultiBlock0([BII)I",
                            "com/sun/crypto/provider/GHASH.processBlocks([BII[J[J)V",
                            "java/util/zip/CRC32C.updateBytes(I[BII)I",
                            "java/util/zip/CRC32C.updateDirectByteBuffer(IJII)I",
                            "java/util/zip/Adler32.updateBytes(I[BII)I",
                            "java/util/zip/Adler32.updateByteBuffer(IJII)I",
                            "jdk/internal/misc/Unsafe.allocateInstance(Ljava/lang/Class;)Ljava/lang/Object;",
                            "jdk/internal/misc/Unsafe.allocateUninitializedArray0(Ljava/lang/Class;I)Ljava/lang/Object;",
                            "jdk/internal/misc/Unsafe.copyMemory0(Ljava/lang/Object;JLjava/lang/Object;JJ)V",
                            "jdk/internal/misc/Unsafe.loadFence()V",
                            "jdk/internal/misc/Unsafe.storeFence()V",
                            "jdk/internal/misc/Unsafe.fullFence()V",
                            "java/lang/invoke/MethodHandleImpl.isCompileConstant(Ljava/lang/Object;)Z",
                            "jdk/internal/misc/Unsafe.getObject(Ljava/lang/Object;J)Ljava/lang/Object;",
                            "jdk/internal/misc/Unsafe.getBoolean(Ljava/lang/Object;J)Z",
                            "jdk/internal/misc/Unsafe.getByte(Ljava/lang/Object;J)B",
                            "jdk/internal/misc/Unsafe.getShort(Ljava/lang/Object;J)S",
                            "jdk/internal/misc/Unsafe.getChar(Ljava/lang/Object;J)C",
                            "jdk/internal/misc/Unsafe.getInt(Ljava/lang/Object;J)I",
                            "jdk/internal/misc/Unsafe.getLong(Ljava/lang/Object;J)J",
                            "jdk/internal/misc/Unsafe.getFloat(Ljava/lang/Object;J)F",
                            "jdk/internal/misc/Unsafe.getDouble(Ljava/lang/Object;J)D",
                            "jdk/internal/misc/Unsafe.putObject(Ljava/lang/Object;JLjava/lang/Object;)V",
                            "jdk/internal/misc/Unsafe.putBoolean(Ljava/lang/Object;JZ)V",
                            "jdk/internal/misc/Unsafe.putByte(Ljava/lang/Object;JB)V",
                            "jdk/internal/misc/Unsafe.putShort(Ljava/lang/Object;JS)V",
                            "jdk/internal/misc/Unsafe.putChar(Ljava/lang/Object;JC)V",
                            "jdk/internal/misc/Unsafe.putInt(Ljava/lang/Object;JI)V",
                            "jdk/internal/misc/Unsafe.putLong(Ljava/lang/Object;JJ)V",
                            "jdk/internal/misc/Unsafe.putFloat(Ljava/lang/Object;JF)V",
                            "jdk/internal/misc/Unsafe.putDouble(Ljava/lang/Object;JD)V",
                            "jdk/internal/misc/Unsafe.getObjectVolatile(Ljava/lang/Object;J)Ljava/lang/Object;",
                            "jdk/internal/misc/Unsafe.getBooleanVolatile(Ljava/lang/Object;J)Z",
                            "jdk/internal/misc/Unsafe.getByteVolatile(Ljava/lang/Object;J)B",
                            "jdk/internal/misc/Unsafe.getShortVolatile(Ljava/lang/Object;J)S",
                            "jdk/internal/misc/Unsafe.getCharVolatile(Ljava/lang/Object;J)C",
                            "jdk/internal/misc/Unsafe.getIntVolatile(Ljava/lang/Object;J)I",
                            "jdk/internal/misc/Unsafe.getLongVolatile(Ljava/lang/Object;J)J",
                            "jdk/internal/misc/Unsafe.getFloatVolatile(Ljava/lang/Object;J)F",
                            "jdk/internal/misc/Unsafe.getDoubleVolatile(Ljava/lang/Object;J)D",
                            "jdk/internal/misc/Unsafe.putObjectVolatile(Ljava/lang/Object;JLjava/lang/Object;)V",
                            "jdk/internal/misc/Unsafe.putBooleanVolatile(Ljava/lang/Object;JZ)V",
                            "jdk/internal/misc/Unsafe.putByteVolatile(Ljava/lang/Object;JB)V",
                            "jdk/internal/misc/Unsafe.putShortVolatile(Ljava/lang/Object;JS)V",
                            "jdk/internal/misc/Unsafe.putCharVolatile(Ljava/lang/Object;JC)V",
                            "jdk/internal/misc/Unsafe.putIntVolatile(Ljava/lang/Object;JI)V",
                            "jdk/internal/misc/Unsafe.putLongVolatile(Ljava/lang/Object;JJ)V",
                            "jdk/internal/misc/Unsafe.putFloatVolatile(Ljava/lang/Object;JF)V",
                            "jdk/internal/misc/Unsafe.putDoubleVolatile(Ljava/lang/Object;JD)V",
                            "jdk/internal/misc/Unsafe.getObjectOpaque(Ljava/lang/Object;J)Ljava/lang/Object;",
                            "jdk/internal/misc/Unsafe.getBooleanOpaque(Ljava/lang/Object;J)Z",
                            "jdk/internal/misc/Unsafe.getByteOpaque(Ljava/lang/Object;J)B",
                            "jdk/internal/misc/Unsafe.getShortOpaque(Ljava/lang/Object;J)S",
                            "jdk/internal/misc/Unsafe.getCharOpaque(Ljava/lang/Object;J)C",
                            "jdk/internal/misc/Unsafe.getIntOpaque(Ljava/lang/Object;J)I",
                            "jdk/internal/misc/Unsafe.getLongOpaque(Ljava/lang/Object;J)J",
                            "jdk/internal/misc/Unsafe.getFloatOpaque(Ljava/lang/Object;J)F",
                            "jdk/internal/misc/Unsafe.getDoubleOpaque(Ljava/lang/Object;J)D",
                            "jdk/internal/misc/Unsafe.putObjectOpaque(Ljava/lang/Object;JLjava/lang/Object;)V",
                            "jdk/internal/misc/Unsafe.putBooleanOpaque(Ljava/lang/Object;JZ)V",
                            "jdk/internal/misc/Unsafe.putByteOpaque(Ljava/lang/Object;JB)V",
                            "jdk/internal/misc/Unsafe.putShortOpaque(Ljava/lang/Object;JS)V",
                            "jdk/internal/misc/Unsafe.putCharOpaque(Ljava/lang/Object;JC)V",
                            "jdk/internal/misc/Unsafe.putIntOpaque(Ljava/lang/Object;JI)V",
                            "jdk/internal/misc/Unsafe.putLongOpaque(Ljava/lang/Object;JJ)V",
                            "jdk/internal/misc/Unsafe.putFloatOpaque(Ljava/lang/Object;JF)V",
                            "jdk/internal/misc/Unsafe.putDoubleOpaque(Ljava/lang/Object;JD)V",
                            "jdk/internal/misc/Unsafe.getObjectAcquire(Ljava/lang/Object;J)Ljava/lang/Object;",
                            "jdk/internal/misc/Unsafe.getBooleanAcquire(Ljava/lang/Object;J)Z",
                            "jdk/internal/misc/Unsafe.getByteAcquire(Ljava/lang/Object;J)B",
                            "jdk/internal/misc/Unsafe.getShortAcquire(Ljava/lang/Object;J)S",
                            "jdk/internal/misc/Unsafe.getCharAcquire(Ljava/lang/Object;J)C",
                            "jdk/internal/misc/Unsafe.getIntAcquire(Ljava/lang/Object;J)I",
                            "jdk/internal/misc/Unsafe.getLongAcquire(Ljava/lang/Object;J)J",
                            "jdk/internal/misc/Unsafe.getFloatAcquire(Ljava/lang/Object;J)F",
                            "jdk/internal/misc/Unsafe.getDoubleAcquire(Ljava/lang/Object;J)D",
                            "jdk/internal/misc/Unsafe.putObjectRelease(Ljava/lang/Object;JLjava/lang/Object;)V",
                            "jdk/internal/misc/Unsafe.putBooleanRelease(Ljava/lang/Object;JZ)V",
                            "jdk/internal/misc/Unsafe.putByteRelease(Ljava/lang/Object;JB)V",
                            "jdk/internal/misc/Unsafe.putShortRelease(Ljava/lang/Object;JS)V",
                            "jdk/internal/misc/Unsafe.putCharRelease(Ljava/lang/Object;JC)V",
                            "jdk/internal/misc/Unsafe.putIntRelease(Ljava/lang/Object;JI)V",
                            "jdk/internal/misc/Unsafe.putLongRelease(Ljava/lang/Object;JJ)V",
                            "jdk/internal/misc/Unsafe.putFloatRelease(Ljava/lang/Object;JF)V",
                            "jdk/internal/misc/Unsafe.putDoubleRelease(Ljava/lang/Object;JD)V",
                            "jdk/internal/misc/Unsafe.getShortUnaligned(Ljava/lang/Object;J)S",
                            "jdk/internal/misc/Unsafe.getCharUnaligned(Ljava/lang/Object;J)C",
                            "jdk/internal/misc/Unsafe.getIntUnaligned(Ljava/lang/Object;J)I",
                            "jdk/internal/misc/Unsafe.getLongUnaligned(Ljava/lang/Object;J)J",
                            "jdk/internal/misc/Unsafe.putShortUnaligned(Ljava/lang/Object;JS)V",
                            "jdk/internal/misc/Unsafe.putCharUnaligned(Ljava/lang/Object;JC)V",
                            "jdk/internal/misc/Unsafe.putIntUnaligned(Ljava/lang/Object;JI)V",
                            "jdk/internal/misc/Unsafe.putLongUnaligned(Ljava/lang/Object;JJ)V",
                            "jdk/internal/misc/Unsafe.compareAndSwapObject(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
                            "jdk/internal/misc/Unsafe.compareAndExchangeObjectVolatile(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                            "jdk/internal/misc/Unsafe.compareAndExchangeObjectAcquire(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                            "jdk/internal/misc/Unsafe.compareAndExchangeObjectRelease(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                            "jdk/internal/misc/Unsafe.compareAndSwapLong(Ljava/lang/Object;JJJ)Z",
                            "jdk/internal/misc/Unsafe.compareAndExchangeLongVolatile(Ljava/lang/Object;JJJ)J",
                            "jdk/internal/misc/Unsafe.compareAndExchangeLongAcquire(Ljava/lang/Object;JJJ)J",
                            "jdk/internal/misc/Unsafe.compareAndExchangeLongRelease(Ljava/lang/Object;JJJ)J",
                            "jdk/internal/misc/Unsafe.compareAndSwapInt(Ljava/lang/Object;JII)Z",
                            "jdk/internal/misc/Unsafe.compareAndExchangeIntVolatile(Ljava/lang/Object;JII)I",
                            "jdk/internal/misc/Unsafe.compareAndExchangeIntAcquire(Ljava/lang/Object;JII)I",
                            "jdk/internal/misc/Unsafe.compareAndExchangeIntRelease(Ljava/lang/Object;JII)I",
                            "jdk/internal/misc/Unsafe.compareAndSwapByte(Ljava/lang/Object;JBB)Z",
                            "jdk/internal/misc/Unsafe.compareAndExchangeByteVolatile(Ljava/lang/Object;JBB)B",
                            "jdk/internal/misc/Unsafe.compareAndExchangeByteAcquire(Ljava/lang/Object;JBB)B",
                            "jdk/internal/misc/Unsafe.compareAndExchangeByteRelease(Ljava/lang/Object;JBB)B",
                            "jdk/internal/misc/Unsafe.compareAndSwapShort(Ljava/lang/Object;JSS)Z",
                            "jdk/internal/misc/Unsafe.compareAndExchangeShortVolatile(Ljava/lang/Object;JSS)S",
                            "jdk/internal/misc/Unsafe.compareAndExchangeShortAcquire(Ljava/lang/Object;JSS)S",
                            "jdk/internal/misc/Unsafe.compareAndExchangeShortRelease(Ljava/lang/Object;JSS)S",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapObject(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapObjectAcquire(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapObjectRelease(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapObjectVolatile(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapLong(Ljava/lang/Object;JJJ)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapLongAcquire(Ljava/lang/Object;JJJ)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapLongRelease(Ljava/lang/Object;JJJ)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapLongVolatile(Ljava/lang/Object;JJJ)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapInt(Ljava/lang/Object;JII)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapIntAcquire(Ljava/lang/Object;JII)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapIntRelease(Ljava/lang/Object;JII)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapIntVolatile(Ljava/lang/Object;JII)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapByte(Ljava/lang/Object;JBB)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapByteAcquire(Ljava/lang/Object;JBB)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapByteRelease(Ljava/lang/Object;JBB)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapByteVolatile(Ljava/lang/Object;JBB)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapShort(Ljava/lang/Object;JSS)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapShortAcquire(Ljava/lang/Object;JSS)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapShortRelease(Ljava/lang/Object;JSS)Z",
                            "jdk/internal/misc/Unsafe.weakCompareAndSwapShortVolatile(Ljava/lang/Object;JSS)Z",
                            "jdk/internal/misc/Unsafe.getAndAddInt(Ljava/lang/Object;JI)I",
                            "jdk/internal/misc/Unsafe.getAndAddLong(Ljava/lang/Object;JJ)J",
                            "jdk/internal/misc/Unsafe.getAndAddByte(Ljava/lang/Object;JB)B",
                            "jdk/internal/misc/Unsafe.getAndAddShort(Ljava/lang/Object;JS)S",
                            "jdk/internal/misc/Unsafe.getAndSetInt(Ljava/lang/Object;JI)I",
                            "jdk/internal/misc/Unsafe.getAndSetLong(Ljava/lang/Object;JJ)J",
                            "jdk/internal/misc/Unsafe.getAndSetByte(Ljava/lang/Object;JB)B",
                            "jdk/internal/misc/Unsafe.getAndSetShort(Ljava/lang/Object;JS)S",
                            "jdk/internal/misc/Unsafe.getAndSetObject(Ljava/lang/Object;JLjava/lang/Object;)Ljava/lang/Object;",
                            "jdk/internal/misc/Unsafe.park(ZJ)V",
                            "jdk/internal/misc/Unsafe.unpark(Ljava/lang/Object;)V",
                            "java/lang/StringBuilder.<init>()V",
                            "java/lang/StringBuilder.<init>(I)V",
                            "java/lang/StringBuilder.<init>(Ljava/lang/String;)V",
                            "java/lang/StringBuilder.append(C)Ljava/lang/StringBuilder;",
                            "java/lang/StringBuilder.append(I)Ljava/lang/StringBuilder;",
                            "java/lang/StringBuilder.append(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                            "java/lang/StringBuilder.toString()Ljava/lang/String;",
                            "java/lang/StringBuffer.<init>()V",
                            "java/lang/StringBuffer.<init>(I)V",
                            "java/lang/StringBuffer.<init>(Ljava/lang/String;)V",
                            "java/lang/StringBuffer.append(C)Ljava/lang/StringBuffer;",
                            "java/lang/StringBuffer.append(I)Ljava/lang/StringBuffer;",
                            "java/lang/StringBuffer.append(Ljava/lang/String;)Ljava/lang/StringBuffer;",
                            "java/lang/StringBuffer.toString()Ljava/lang/String;",
                            "java/lang/Integer.toString(I)Ljava/lang/String;",
                            "java/lang/String.<init>(Ljava/lang/String;)V",
                            "java/lang/reflect/Method.invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                            "java/lang/invoke/MethodHandle.invoke*",
                            "java/lang/invoke/MethodHandle.<compiledLambdaForm>*",
                            "java/util/stream/Streams$RangeIntSpliterator.forEachRemaining(Ljava/util/function/IntConsumer;)V"));
        }
        if (!getHostArchitectureName().equals("amd64")) {
            TO_BE_INVESTIGATED.addAll(Arrays.asList(
                            "java/util/zip/CRC32.update(II)I",
                            "java/util/zip/CRC32.updateBytes(I[BII)I",
                            "java/util/zip/CRC32.updateByteBuffer(IJII)I",
                            "sun/misc/Unsafe.getAndAddInt(Ljava/lang/Object;JI)I",
                            "sun/misc/Unsafe.getAndAddLong(Ljava/lang/Object;JJ)J",
                            "sun/misc/Unsafe.getAndSetInt(Ljava/lang/Object;JI)I",
                            "sun/misc/Unsafe.getAndSetLong(Ljava/lang/Object;JJ)J",
                            "sun/misc/Unsafe.getAndSetObject(Ljava/lang/Object;JLjava/lang/Object;)Ljava/lang/Object;"));
        }
    }

    private static String getHostArchitectureName() {
        String arch = System.getProperty("os.arch");
        if (arch.equals("x86_64")) {
            arch = "amd64";
        } else if (arch.equals("sparcv9")) {
            arch = "sparc";
        }
        return arch;
    }

    @Test
    @SuppressWarnings("try")
    public void test() throws ClassNotFoundException {
        HotSpotGraalRuntimeProvider rt = (HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class);
        HotSpotProviders providers = rt.getHostBackend().getProviders();
        Map<ResolvedJavaMethod, Object> impl = new HashMap<>();
        Plugins graphBuilderPlugins = providers.getGraphBuilderPlugins();
        InvocationPlugins invocationPlugins = graphBuilderPlugins.getInvocationPlugins();
        for (ResolvedJavaMethod method : invocationPlugins.getMethods()) {
            InvocationPlugin plugin = invocationPlugins.lookupInvocation(method);
            assert plugin != null;
            impl.put(method, plugin);
        }

        Set<ResolvedJavaMethod> methods = invocationPlugins.getMethods();
        HotSpotVMConfigStore store = rt.getVMConfig().getStore();
        List<VMIntrinsicMethod> intrinsics = store.getIntrinsics();

        List<String> missing = new ArrayList<>();
        for (VMIntrinsicMethod intrinsic : intrinsics) {
            ResolvedJavaMethod method = findMethod(methods, intrinsic);
            if (method == null) {
                method = resolveIntrinsic(providers.getMetaAccess(), intrinsic);

                IntrinsicMethod intrinsicMethod = null;
                if (method != null) {
                    intrinsicMethod = providers.getConstantReflection().getMethodHandleAccess().lookupMethodHandleIntrinsic(method);
                    if (intrinsicMethod != null) {
                        continue;
                    }
                }
                String m = String.format("%s.%s%s", intrinsic.declaringClass, intrinsic.name, intrinsic.descriptor);
                if (!TO_BE_INVESTIGATED.contains(m) && !KNOWN_MISSING.contains(m)) {
                    missing.add(m);
                }
            }
        }

        if (!missing.isEmpty()) {
            String missingString = missing.stream().collect(Collectors.joining(String.format("%n    ")));
            fail("missing Graal intrinsics for:%n    %s", missingString);
        }
    }
}
