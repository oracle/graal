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

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.Uninterruptible;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Immutable class that holds all sizes and offsets that contribute to the object layout.
 *
 * Identity hashcode fields can either be:
 * <ol type="a">
 * <li>In the object header, at a fixed offset for all objects (see
 * {@link #isIdentityHashFieldInObjectHeader()}).</li>
 * <li>At a type specific offset, potentially outside the object header (see
 * {@link #isIdentityHashFieldAtTypeSpecificOffset()}).</li>
 * <li>Outside the object header, at a type- or object-specific offset (see
 * {@link #isIdentityHashFieldOptional()}). Note that the field is not part of every object. When an
 * object needs the field, the object is resized during garbage collection to accommodate the
 * field.</li>
 * </ol>
 * 
 * See this classes instantiation sites (such as {@code HostedConfiguration#createObjectLayout}) for
 * more details on the exact object layout for a given configuration.
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
    private final int objectHeaderIdentityHashOffset;
    private final int identityHashMode;

    public ObjectLayout(SubstrateTargetDescription target, int referenceSize, int objectAlignment, int hubOffset, int firstFieldOffset, int arrayLengthOffset, int arrayBaseOffset,
                    int headerIdentityHashOffset, IdentityHashMode identityHashMode) {
        assert CodeUtil.isPowerOf2(referenceSize) : referenceSize;
        assert CodeUtil.isPowerOf2(objectAlignment) : objectAlignment;
        assert arrayLengthOffset % Integer.BYTES == 0;
        assert hubOffset < firstFieldOffset && hubOffset < arrayLengthOffset : hubOffset;
        assert (identityHashMode != IdentityHashMode.OPTIONAL && headerIdentityHashOffset > 0 && headerIdentityHashOffset < arrayLengthOffset && headerIdentityHashOffset % Integer.BYTES == 0) ||
                        (identityHashMode == IdentityHashMode.OPTIONAL && headerIdentityHashOffset == -1);

        this.target = target;
        this.referenceSize = referenceSize;
        this.objectAlignment = objectAlignment;
        this.alignmentMask = objectAlignment - 1;
        this.hubOffset = hubOffset;
        this.firstFieldOffset = firstFieldOffset;
        this.arrayLengthOffset = arrayLengthOffset;
        this.arrayBaseOffset = arrayBaseOffset;
        this.objectHeaderIdentityHashOffset = headerIdentityHashOffset;
        this.identityHashMode = identityHashMode.value;
    }

    /** The minimum alignment of objects (instances and arrays). */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getAlignment() {
        return objectAlignment;
    }

    /** Tests if the given offset or address is aligned according to {@link #getAlignment()}. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isAligned(final long value) {
        return (value % getAlignment() == 0L);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getReferenceSize() {
        return referenceSize;
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
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int alignUp(int obj) {
        return (obj + alignmentMask) & ~alignmentMask;
    }

    /**
     * Align the specified offset or address up to {@link #getAlignment()}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
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
     * Indicates whether all objects, including arrays, always contain an identity hash code field
     * at a specific offset in the object header.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isIdentityHashFieldInObjectHeader() {
        return identityHashMode == IdentityHashMode.OBJECT_HEADER.value;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isIdentityHashFieldAtTypeSpecificOffset() {
        return identityHashMode == IdentityHashMode.TYPE_SPECIFIC.value;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isIdentityHashFieldOptional() {
        return identityHashMode == IdentityHashMode.OPTIONAL.value;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getObjectHeaderIdentityHashOffset() {
        if (GraalDirectives.inIntrinsic()) {
            ReplacementsUtil.dynamicAssert(objectHeaderIdentityHashOffset > 0, "must check before calling");
        } else {
            assert objectHeaderIdentityHashOffset > 0 : "must check before calling";
        }
        return objectHeaderIdentityHashOffset;
    }

    public int getArrayBaseOffset(JavaKind kind) {
        return NumUtil.roundUp(arrayBaseOffset, sizeInBytes(kind));
    }

    public long getArrayElementOffset(JavaKind kind, int index) {
        return getArrayBaseOffset(kind) + ((long) index) * sizeInBytes(kind);
    }

    public long getArraySize(JavaKind kind, int length, boolean withOptionalIdHashField) {
        return computeArrayTotalSize(getArrayUnalignedSize(kind, length), withOptionalIdHashField);
    }

    private long getArrayUnalignedSize(JavaKind kind, int length) {
        assert length >= 0 : length;
        return getArrayBaseOffset(kind) + ((long) length << getArrayIndexShift(kind));
    }

    public long getArrayIdentityHashOffset(JavaKind kind, int length) {
        return getArrayIdentityHashOffset(getArrayUnalignedSize(kind, length));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getArrayIdentityHashOffset(long unalignedSize) {
        if (isIdentityHashFieldInObjectHeader() || isIdentityHashFieldAtTypeSpecificOffset()) {
            return getObjectHeaderIdentityHashOffset();
        }
        int align = Integer.BYTES;
        return ((unalignedSize + align - 1) / align) * align;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long computeArrayTotalSize(long unalignedSize, boolean withOptionalIdHashField) {
        long size = unalignedSize;
        if (withOptionalIdHashField && isIdentityHashFieldOptional()) {
            size = getArrayIdentityHashOffset(size) + Integer.BYTES;
        }
        return alignUp(size);
    }

    public int getMinImageHeapInstanceSize() {
        int unalignedSize = firstFieldOffset; // assumes no always-present "synthetic fields"
        if (isIdentityHashFieldAtTypeSpecificOffset() || isIdentityHashFieldOptional()) {
            int idHashOffset = NumUtil.roundUp(unalignedSize, Integer.BYTES);
            unalignedSize = idHashOffset + Integer.BYTES;
        }
        return alignUp(unalignedSize);
    }

    public int getMinImageHeapArraySize() {
        return NumUtil.safeToInt(getArraySize(JavaKind.Byte, 0, true));
    }

    public int getMinImageHeapObjectSize() {
        return Math.min(getMinImageHeapArraySize(), getMinImageHeapInstanceSize());
    }

    public static JavaKind getCallSignatureKind(boolean isEntryPoint, ResolvedJavaType type, MetaAccessProvider metaAccess, TargetDescription target) {
        if (metaAccess.lookupJavaType(WordBase.class).isAssignableFrom(type)) {
            return target.wordJavaKind;
        }
        if (isEntryPoint && AnnotationAccess.isAnnotationPresent(type, CEnum.class)) {
            return JavaKind.Int;
        }
        return type.getJavaKind();
    }

    public enum IdentityHashMode {
        /* At a fixed offset, for all objects (part of the object header). */
        OBJECT_HEADER(0),
        /* At a type-specific offset (potentially outside the object header). */
        TYPE_SPECIFIC(1),
        /* At a type- or object-specific offset (outside the object header). */
        OPTIONAL(2);

        final int value;

        IdentityHashMode(int value) {
            this.value = value;
        }
    }
}
