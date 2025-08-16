/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.gc;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.word.ObjectAccess;
import jdk.graal.compiler.word.Word;

/**
 * The object header is a 32-bit word (currently 64bit, see GR-42105). The two least-significant
 * bits are reserved for the GC. Clearing them results in the address of the {@link DynamicHub}.
 * <p>
 * During garbage collection, the two bits indicate the collection state of the object (see also
 * {@link WasmLMGC}):
 * <ul>
 * <li>{@code 00}: White (Object was never visited by GC)</li>
 * <li>{@code 01}: Gray (Object is currently being visited by GC)</li>
 * <li>{@code 10}: Black (Object was visited by GC)</li>
 * </ul>
 *
 * At the end of the GC marking phase, all white objects can be deleted. Objects in the image heap
 * are not affected, they are always considered black (they can't be collected).
 */
public class WasmObjectHeader extends ObjectHeader {

    private static final int RESERVED_BITS_MASK = 0b111;
    private static final UnsignedWord MASK_HEADER_BITS = Word.unsigned(RESERVED_BITS_MASK);
    private static final UnsignedWord CLEAR_HEADER_BITS = MASK_HEADER_BITS.not();

    static {
        assert MASK_HEADER_BITS.rawValue() == RESERVED_BITS_MASK;
        assert CLEAR_HEADER_BITS.rawValue() == ~RESERVED_BITS_MASK;
    }

    private static final UnsignedWord WHITE_BITS = Word.unsigned(0b000);
    private static final UnsignedWord GRAY_BITS = Word.unsigned(0b001);
    private static final UnsignedWord BLACK_BITS = Word.unsigned(0b010);

    @Fold
    public static WasmObjectHeader getObjectHeaderImpl() {
        WasmObjectHeader oh = WasmHeap.getHeapImpl().getObjectHeaderImpl();
        assert oh != null;
        return oh;
    }

    @Override
    public int getReservedHubBitsMask() {
        return RESERVED_BITS_MASK;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public Word encodeAsUnmanagedObjectHeader(DynamicHub hub) {
        // Headers in unmanaged memory don't need any GC-specific bits set
        return encodeAsObjectHeader(hub, false, false);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @AlwaysInline(INLINE_INITIALIZE_HEADER_INIT_REASON)
    @Override
    protected void initializeObjectHeader(Pointer objectPointer, Word encodedHub, boolean isArrayLike, MemWriter writer) {
        writer.writeWord(objectPointer, getHubOffset(), encodedHub);
        writer.writeInt(objectPointer, getIdentityHashCodeOffset(), 0);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public Word readHeaderFromPointer(Pointer objectPointer) {
        return objectPointer.readWord(getHubOffset());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public Word readHeaderFromObject(Object o) {
        return ObjectAccess.readWord(o, getHubOffset());
    }

    @Override
    public boolean hasOptionalIdentityHashField(Word header) {
        if (GraalDirectives.inIntrinsic()) {
            ReplacementsUtil.staticAssert(false, "all objects have the identity hash code field in the object header");
            return false;
        } else {
            throw VMError.shouldNotReachHereAtRuntime();
        }
    }

    @Override
    public boolean hasIdentityHashFromAddress(Word header) {
        ReplacementsUtil.staticAssert(false, "all objects have the identity hash code field in the object header");
        return false;
    }

    @Override
    public void setIdentityHashFromAddress(Pointer ptr, Word currentHeader) {
        ReplacementsUtil.staticAssert(false, "identity hash codes are never computed from addresses");
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void dynamicAssert(boolean condition, String msg) {
        if (GraalDirectives.inIntrinsic()) {
            ReplacementsUtil.dynamicAssert(condition, msg);
        } else {
            assert condition : msg;
        }
    }

    private static void writeHeaderToObject(Object o, WordBase header) {
        ObjectAccess.writeWord(o, getHubOffset(), header);
    }

    @Override
    public Word encodeAsTLABObjectHeader(DynamicHub hub) {
        return encodeAsObjectHeader(hub, false, false);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Word encodeAsObjectHeader(DynamicHub hub, boolean rememberedSet, boolean unaligned) {
        dynamicAssert(!rememberedSet, "Remembered set not supported");
        dynamicAssert(!unaligned, "Unaligned flag not supported");

        /*
         * All DynamicHub instances are in the native image heap and therefore do not move, so we
         * can convert the hub to a Pointer without any precautions.
         */
        return Word.objectToUntrackedPointer(hub);
    }

    @Override
    public long encodeAsTLABObjectHeader(long hubOffsetFromHeapBase) {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public int constantHeaderSize() {
        return -1;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public Pointer extractPotentialDynamicHubFromHeader(Word header) {
        return (Pointer) clearBits(header);
    }

    /**
     * Clear the object header bits from a header.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord clearBits(UnsignedWord header) {
        return header.and(CLEAR_HEADER_BITS);
    }

    @Override
    public long encodeHubPointerForImageHeap(ImageHeapObject obj, long hubOffsetFromHeapBase) {
        long header = hubOffsetFromHeapBase;
        assert (header & MASK_HEADER_BITS.rawValue()) == 0 : "Object header bits must be zero initially";
        return header;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void verifyDynamicHubOffset(long offsetFromHeapBase) {
        /* Nothing to do. */
    }

    public static boolean isWhiteHeader(UnsignedWord header) {
        return header.and(MASK_HEADER_BITS).equal(WHITE_BITS);
    }

    public static boolean isWhiteObject(Object obj) {
        ObjectHeader oh = Heap.getHeap().getObjectHeader();
        UnsignedWord header = oh.readHeaderFromObject(obj);
        return isWhiteHeader(header);
    }

    public static void markWhite(Object o) {
        ObjectHeader oh = Heap.getHeap().getObjectHeader();
        UnsignedWord oldHeader = oh.readHeaderFromObject(o);
        UnsignedWord newHeader = clearBits(oldHeader).or(WHITE_BITS);
        writeHeaderToObject(o, newHeader);
    }

    public static boolean isGrayHeader(UnsignedWord header) {
        return header.and(MASK_HEADER_BITS).equal(GRAY_BITS);
    }

    public static boolean isGrayObject(Object obj) {
        ObjectHeader oh = Heap.getHeap().getObjectHeader();
        UnsignedWord header = oh.readHeaderFromObject(obj);
        return isGrayHeader(header);
    }

    public static void markGray(Object o) {
        ObjectHeader oh = Heap.getHeap().getObjectHeader();
        UnsignedWord oldHeader = oh.readHeaderFromObject(o);
        UnsignedWord newHeader = clearBits(oldHeader).or(GRAY_BITS);
        writeHeaderToObject(o, newHeader);
    }

    public static boolean isBlackHeader(UnsignedWord header) {
        return header.and(MASK_HEADER_BITS).equal(BLACK_BITS);
    }

    public static boolean isBlackObject(Object obj) {
        ObjectHeader oh = Heap.getHeap().getObjectHeader();
        UnsignedWord header = oh.readHeaderFromObject(obj);
        return isBlackHeader(header);
    }

    public static void markBlack(Object o) {
        ObjectHeader oh = Heap.getHeap().getObjectHeader();
        UnsignedWord oldHeader = oh.readHeaderFromObject(o);
        UnsignedWord newHeader = clearBits(oldHeader).or(BLACK_BITS);
        writeHeaderToObject(o, newHeader);
    }

    @Fold
    static int getIdentityHashCodeOffset() {
        return ConfigurationValues.getObjectLayout().getObjectHeaderIdentityHashOffset();
    }
}
