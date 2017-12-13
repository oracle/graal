/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.config;

import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.deopt.DeoptimizedFrame;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Defines the layout of objects.
 *
 * The layout of instance objects is:
 * <ul>
 * <li>hub (reference)
 * <li>instance fields (references, primitives)
 * <li>optional: hashcode (int)
 * </ul>
 * The hashcode is appended after instance fields and is only present if the identity hashcode is
 * used for that type.
 *
 * The layout of array objects is:
 * <ul>
 * <li>hub (reference)
 * <li>array length (int)
 * <li>hashcode (int)
 * <li>array elements (length * reference or primitive)
 * </ul>
 * The hashcode is always present in arrays. Note that on 64-bit targets it does not impose any size
 * overhead for arrays with 64-bit aligned elements (e.g. arrays of objects).
 *
 * This class must be final to evaluate to a constant in snippets (without the need of analysis)
 */
public final class ObjectLayout {

    private final TargetDescription target;

    private final JavaKind hubKind;
    private final int hubSize;
    private final int referenceSize;
    private final int alignmentMask;
    private final int deoptScratchSpace;

    public ObjectLayout(TargetDescription target, int deoptScratchSpace) {
        this.target = target;
        this.hubKind = JavaKind.Object;
        this.hubSize = sizeInBytes(hubKind);
        this.referenceSize = sizeInBytes(JavaKind.Object);
        this.alignmentMask = getAlignment() - 1;
        this.deoptScratchSpace = deoptScratchSpace;
    }

    public static int roundUp(int number, int mod) {
        return ((number + mod - 1) / mod) * mod;
    }

    public static long roundUp(long number, long mod) {
        return ((number + mod - 1) / mod) * mod;
    }

    public int getAlignment() {
        return sizeInBytes(JavaKind.Long);
    }

    public boolean isAligned(final long value) {
        return ((value % getAlignment()) == 0L);
    }

    public int getReferenceSize() {
        return referenceSize;
    }

    public boolean isReferenceAligned(final long value) {
        return ((value % getReferenceSize()) == 0L);
    }

    public int getCompressedReferenceSize() {
        return referenceSize;
    }

    /**
     * Returns the amount of scratch space which must be reserved for return value registers in
     * {@link DeoptimizedFrame}.
     */
    public int getDeoptScratchSpace() {
        return deoptScratchSpace;
    }

    /**
     * The size (in bytes) of values with the given kind, assuming an uncompressed reference for
     * {@link JavaKind#Object}.
     */
    public int sizeInBytes(JavaKind kind) {
        return target.arch.getPlatformKind(kind).getSizeInBytes();
    }

    /**
     * The size (in bytes) of values with the given kind, with {@code isCompressed} specifying
     * whether a reference is compressed for a {@code kind} of {@link JavaKind#Object}.
     */
    public int sizeInBytes(JavaKind kind, boolean isCompressed) {
        if (kind == JavaKind.Object && isCompressed) {
            return getCompressedReferenceSize();
        }
        return target.arch.getPlatformKind(kind).getSizeInBytes();
    }

    public int getArrayIndexShift(JavaKind kind) {
        return CodeUtil.log2(sizeInBytes(kind));
    }

    public int getArrayIndexScale(JavaKind kind) {
        return 1 << getArrayIndexShift(kind);
    }

    /**
     * The align to the minimum alignment of objects (instances and arrays), in bytes.
     */
    public int alignUp(int obj) {
        return (obj + alignmentMask) & ~alignmentMask;
    }

    /**
     * The align to the minimum alignment of objects (instances and arrays), in bytes.
     */
    public long alignUp(long obj) {
        return (obj + alignmentMask) & ~alignmentMask;
    }

    @SuppressWarnings("static-method")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getHubOffset() {
        return 0;
    }

    public JavaKind getHubKind() {
        return hubKind;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getFirstFieldOffset() {
        return hubSize;
    }

    /*
     * A sequence of fooOffset() and fooNextOffset() methods that give the layout of array fields:
     * length, [hashcode], element ....
     */

    private static final JavaKind arrayLengthKind = JavaKind.Int;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getArrayLengthOffset() {
        return getFirstFieldOffset();
    }

    private int getArrayLengthNextOffset() {
        return getArrayLengthOffset() + sizeInBytes(arrayLengthKind);
    }

    private static final JavaKind arrayHashCodeKind = JavaKind.Int;

    public int getArrayHashCodeOffset() {
        return roundUp(getArrayLengthNextOffset(), sizeInBytes(arrayHashCodeKind));
    }

    private int getArrayHashCodeNextOffset() {
        return getArrayHashCodeOffset() + sizeInBytes(JavaKind.Int);
    }

    public int getArrayBaseOffset(JavaKind kind) {
        return roundUp(getArrayHashCodeNextOffset(), sizeInBytes(kind));
    }

    public long getArrayElementOffset(JavaKind kind, int index) {
        return getArrayBaseOffset(kind) + index * sizeInBytes(kind);
    }

    public long getArraySize(JavaKind kind, int length) {
        return alignUp(getArrayBaseOffset(kind) + ((long) length << getArrayIndexShift(kind)));
    }

    public static JavaKind getCallSignatureKind(boolean isEntryPoint, ResolvedJavaType type, MetaAccessProvider metaAccess, TargetDescription target) {
        if (metaAccess.lookupJavaType(WordBase.class).isAssignableFrom(type)) {
            return target.wordJavaKind;
        }
        if (isEntryPoint && type.getAnnotation(CEnum.class) != null) {
            return JavaKind.Int;
        }
        return type.getJavaKind();
    }
}
