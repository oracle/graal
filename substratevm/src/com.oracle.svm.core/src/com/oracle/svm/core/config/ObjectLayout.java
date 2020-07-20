/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.hub.DynamicHub;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.util.GuardedAnnotationAccess;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.deopt.DeoptimizedFrame;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Immutable class that holds all sizes and offsets that contribute to the object layout.
 */
public final class ObjectLayout {

    private final SubstrateTargetDescription target;
    private final int referenceSize;
    private final int objectAlignment;
    private final int alignmentMask;
    private final int hubOffset;
    private final int firstFieldOffset;
    private final int arrayLengthOffset;
    private final int arrayBaseOffset;
    private final boolean useExplicitIdentityHashCodeField;
    private final int instanceIdentityHashCodeOffset;
    private final int arrayIdentityHashcodeOffset;

    public ObjectLayout(SubstrateTargetDescription target, int referenceSize, int objectAlignment, int hubOffset, int firstFieldOffset, int arrayLengthOffset, int arrayBaseOffset,
                    boolean useExplicitIdentityHashCodeField, int instanceIdentityHashCodeOffset, int arrayIdentityHashcodeOffset) {
        assert CodeUtil.isPowerOf2(referenceSize);
        assert CodeUtil.isPowerOf2(objectAlignment);
        assert hubOffset < firstFieldOffset && hubOffset < arrayLengthOffset;
        assert arrayIdentityHashcodeOffset > 0;

        this.target = target;
        this.referenceSize = referenceSize;
        this.objectAlignment = objectAlignment;
        this.alignmentMask = objectAlignment - 1;
        this.hubOffset = hubOffset;
        this.firstFieldOffset = firstFieldOffset;
        this.arrayLengthOffset = arrayLengthOffset;
        this.arrayBaseOffset = arrayBaseOffset;
        this.useExplicitIdentityHashCodeField = useExplicitIdentityHashCodeField;
        this.instanceIdentityHashCodeOffset = instanceIdentityHashCodeOffset;
        this.arrayIdentityHashcodeOffset = arrayIdentityHashcodeOffset;
    }

    /** The minimum alignment of objects (instances and arrays). */
    public int getAlignment() {
        return objectAlignment;
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
        return target.getDeoptScratchSpace();
    }

    /**
     * The size (in bytes) of values with the given kind.
     */
    public int sizeInBytes(JavaKind kind) {
        return (kind == JavaKind.Object) ? referenceSize : target.arch.getPlatformKind(kind).getSizeInBytes();
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
        return hubOffset;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getFirstFieldOffset() {
        return firstFieldOffset;
    }

    /*
     * A sequence of fooOffset() and fooNextOffset() methods that give the layout of array fields:
     * length, [hashcode], element ....
     */

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getArrayLengthOffset() {
        return arrayLengthOffset;
    }

    /**
     * Whether instance objects should have an additional (optional) field for the identity hashcode
     * appended after instance fields.
     *
     * @return {@code true} if an identity hashcode field should be placed after instance fields if
     *         necessary, or {@code false} if the identity hashcode is mandatory and already has a
     *         set location.
     */
    public boolean useExplicitIdentityHashCodeField() {
        return useExplicitIdentityHashCodeField;
    }

    /**
     * The offset of the identity hashcode field for instance objects.
     *
     * @return The (>= 0) offset of the identity hashcode field if it is known, or < 0 if the offset
     *         should be queried from the hub (see {@link DynamicHub#getHashCodeOffset()}).
     */
    public int getInstanceIdentityHashCodeOffset() {
        return instanceIdentityHashCodeOffset;
    }

    /** The offset of the identity hashcode field for array objects. */
    @Fold
    public int getArrayIdentityHashcodeOffset() {
        return arrayIdentityHashcodeOffset;
    }

    public int getArrayBaseOffset(JavaKind kind) {
        return NumUtil.roundUp(arrayBaseOffset, sizeInBytes(kind));
    }

    public long getArrayElementOffset(JavaKind kind, int index) {
        return getArrayBaseOffset(kind) + index * sizeInBytes(kind);
    }

    public long getArraySize(JavaKind kind, int length) {
        assert length >= 0;
        return alignUp(getArrayBaseOffset(kind) + ((long) length << getArrayIndexShift(kind)));
    }

    public int getMinimumInstanceObjectSize() {
        return alignUp(firstFieldOffset); // assumes there are no always-present "synthetic fields"
    }

    public int getMinimumArraySize() {
        return NumUtil.safeToInt(getArraySize(JavaKind.Byte, 0));
    }

    public int getMinimumObjectSize() {
        return Math.min(getMinimumArraySize(), getMinimumInstanceObjectSize());
    }

    public static JavaKind getCallSignatureKind(boolean isEntryPoint, ResolvedJavaType type, MetaAccessProvider metaAccess, TargetDescription target) {
        if (metaAccess.lookupJavaType(WordBase.class).isAssignableFrom(type)) {
            return target.wordJavaKind;
        }
        if (isEntryPoint && GuardedAnnotationAccess.isAnnotationPresent(type, CEnum.class)) {
            return JavaKind.Int;
        }
        return type.getJavaKind();
    }
}
