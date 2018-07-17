/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.config;

import org.graalvm.compiler.core.common.NumUtil;
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
 */
public class ObjectLayout {

    private final TargetDescription target;

    private final int referenceSize;
    private final int alignmentMask;
    private final int deoptScratchSpace;

    public ObjectLayout(TargetDescription target, int deoptScratchSpace) {
        this.target = target;
        this.referenceSize = target.arch.getPlatformKind(JavaKind.Object).getSizeInBytes();
        this.alignmentMask = target.wordSize - 1;
        this.deoptScratchSpace = deoptScratchSpace;
    }

    /** The minimum alignment of objects (instances and arrays). */
    public int getAlignment() {
        return target.wordSize;
    }

    /** Tests if the given offset or address is aligned according to {@link #getAlignment()}. */
    public boolean isAligned(final long value) {
        return (value % getAlignment() == 0L);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getReferenceSize() {
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
     * The size (in bytes) of values with the given kind.
     */
    public int sizeInBytes(JavaKind kind) {
        return target.arch.getPlatformKind(kind).getSizeInBytes();
    }

    public int getArrayIndexShift(JavaKind kind) {
        return CodeUtil.log2(getArrayIndexScale(kind));
    }

    public int getArrayIndexScale(JavaKind kind) {
        return sizeInBytes(kind);
    }

    /**
     * Align the specified offset or address up to {@link #getAlignment()}.
     */
    public int alignUp(int obj) {
        return (obj + alignmentMask) & ~alignmentMask;
    }

    /**
     * Align the specified offset or address up to {@link #getAlignment()}.
     */
    public long alignUp(long obj) {
        return (obj + alignmentMask) & ~alignmentMask;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getHubOffset() {
        return 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getHubNextOffset() {
        return getHubOffset() + getReferenceSize();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getFirstFieldOffset() {
        return getHubNextOffset();
    }

    /*
     * A sequence of fooOffset() and fooNextOffset() methods that give the layout of array fields:
     * length, [hashcode], element ....
     */

    private static final JavaKind arrayLengthKind = JavaKind.Int;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getArrayLengthOffset() {
        return getHubNextOffset();
    }

    private int getArrayLengthNextOffset() {
        return getArrayLengthOffset() + sizeInBytes(arrayLengthKind);
    }

    private static final JavaKind arrayHashCodeKind = JavaKind.Int;

    public int getArrayHashCodeOffset() {
        return NumUtil.roundUp(getArrayLengthNextOffset(), sizeInBytes(arrayHashCodeKind));
    }

    private int getArrayHashCodeNextOffset() {
        return getArrayHashCodeOffset() + sizeInBytes(arrayHashCodeKind);
    }

    public int getArrayBaseOffset(JavaKind kind) {
        return NumUtil.roundUp(getArrayHashCodeNextOffset(), sizeInBytes(kind));
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
