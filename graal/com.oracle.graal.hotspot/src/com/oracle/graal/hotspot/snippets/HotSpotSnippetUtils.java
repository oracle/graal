/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.snippets;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.snippets.Snippet.Fold;
import com.oracle.graal.snippets.*;

//JaCoCo Exclude

/**
 * A collection of methods used in HotSpot snippets.
 */
public class HotSpotSnippetUtils {

    @Fold
    static boolean verifyOops() {
        return HotSpotGraalRuntime.getInstance().getConfig().verifyOops;
    }

    @Fold
    static int threadTlabTopOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().threadTlabTopOffset;
    }

    @Fold
    static int threadTlabEndOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().threadTlabEndOffset;
    }

    @Fold
    static Kind wordKind() {
        return HotSpotGraalRuntime.getInstance().getTarget().wordKind;
    }

    @Fold
    static Register threadRegister() {
        return HotSpotGraalRuntime.getInstance().getRuntime().threadRegister();
    }

    @Fold
    static Register stackPointerRegister() {
        return HotSpotGraalRuntime.getInstance().getRuntime().stackPointerRegister();
    }

    @Fold
    static int wordSize() {
        return HotSpotGraalRuntime.getInstance().getTarget().wordSize;
    }

    @Fold
    static int pageSize() {
        return HotSpotGraalRuntime.getInstance().getTarget().pageSize;
    }

    @Fold
    static int prototypeMarkWordOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().prototypeMarkWordOffset;
    }

    @Fold
    static int markOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().markOffset;
    }

    @Fold
    static int unlockedMask() {
        return HotSpotGraalRuntime.getInstance().getConfig().unlockedMask;
    }

    /**
     * Mask for a biasable, locked or unlocked mark word.
     * <pre>
     * +----------------------------------+-+-+
     * |                                 1|1|1|
     * +----------------------------------+-+-+
     * </pre>
     *
     */
    @Fold
    static int biasedLockMaskInPlace() {
        return HotSpotGraalRuntime.getInstance().getConfig().biasedLockMaskInPlace;
    }

    @Fold
    static int epochMaskInPlace() {
        return HotSpotGraalRuntime.getInstance().getConfig().epochMaskInPlace;
    }

    /**
     * Pattern for a biasable, unlocked mark word.
     * <pre>
     * +----------------------------------+-+-+
     * |                                 1|0|1|
     * +----------------------------------+-+-+
     * </pre>
     *
     */
    @Fold
    static int biasedLockPattern() {
        return HotSpotGraalRuntime.getInstance().getConfig().biasedLockPattern;
    }

    @Fold
    static int ageMaskInPlace() {
        return HotSpotGraalRuntime.getInstance().getConfig().ageMaskInPlace;
    }

    @Fold
    static int hubOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().hubOffset;
    }

    @Fold
    static int metaspaceArrayLengthOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().metaspaceArrayLengthOffset;
    }

    @Fold
    static int metaspaceArrayBaseOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().metaspaceArrayBaseOffset;
    }

    @Fold
    static int arrayLengthOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().arrayLengthOffset;
    }

    @Fold
    static int arrayBaseOffset(Kind elementKind) {
        return HotSpotRuntime.getArrayBaseOffset(elementKind);
    }

    @Fold
    static int arrayIndexScale(Kind elementKind) {
        return HotSpotRuntime.getArrayIndexScale(elementKind);
    }

    @Fold
    static int cardTableShift() {
        return HotSpotGraalRuntime.getInstance().getConfig().cardtableShift;
    }

    @Fold
    static long cardTableStart() {
        return HotSpotGraalRuntime.getInstance().getConfig().cardtableStartAddress;
    }

    @Fold
    static int superCheckOffsetOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().superCheckOffsetOffset;
    }

    @Fold
    static int secondarySuperCacheOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().secondarySuperCacheOffset;
    }

    @Fold
    static int secondarySupersOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().secondarySupersOffset;
    }

    @Fold
    static int lockDisplacedMarkOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().basicLockDisplacedHeaderOffset;
    }

    @Fold
    static boolean useBiasedLocking() {
        return HotSpotGraalRuntime.getInstance().getConfig().useBiasedLocking;
    }

    /**
     * Loads the hub from a object, null checking it first.
     */
    static Word loadHub(Object object) {
        return loadHubIntrinsic(object, wordKind());
    }

    static Object verifyOop(Object object) {
        if (verifyOops()) {
            VerifyOopStubCall.call(object);
        }
        return object;
    }

    /**
     * Gets the value of the stack pointer register as a Word.
     */
    static Word stackPointer() {
        return HotSpotSnippetUtils.registerAsWord(stackPointerRegister());
    }

    /**
     * Gets the value of the thread register as a Word.
     */
    static Word thread() {
        return HotSpotSnippetUtils.registerAsWord(threadRegister());
    }

    static int loadIntFromWord(Word address, int offset) {
        Integer value = UnsafeLoadNode.load(address, 0, offset, Kind.Int);
        return value;
    }

    static Word loadWordFromWord(Word address, int offset) {
        return loadWordFromWordIntrinsic(address, 0, offset, wordKind());
    }

    static Word loadWordFromObject(Object object, int offset) {
        return loadWordFromObjectIntrinsic(object, 0, offset, wordKind());
    }

    @NodeIntrinsic(value = RegisterNode.class, setStampFromReturnType = true)
    public static native Word registerAsWord(@ConstantNodeParameter Register register);

    @NodeIntrinsic(value = UnsafeLoadNode.class, setStampFromReturnType = true)
    private static native Word loadWordFromObjectIntrinsic(Object object, @ConstantNodeParameter int displacement, long offset, @ConstantNodeParameter Kind wordKind);

    @NodeIntrinsic(value = UnsafeLoadNode.class, setStampFromReturnType = true)
    private static native Word loadWordFromWordIntrinsic(Word address, @ConstantNodeParameter int displacement, long offset, @ConstantNodeParameter Kind wordKind);

    @NodeIntrinsic(value = LoadHubNode.class, setStampFromReturnType = true)
    static native Word loadHubIntrinsic(Object object, @ConstantNodeParameter Kind word);

    static {
        assert arrayIndexScale(Kind.Byte) == 1;
        assert arrayIndexScale(Kind.Boolean) == 1;
        assert arrayIndexScale(Kind.Char) == 2;
        assert arrayIndexScale(Kind.Short) == 2;
        assert arrayIndexScale(Kind.Int) == 4;
        assert arrayIndexScale(Kind.Long) == 8;
        assert arrayIndexScale(Kind.Float) == 4;
        assert arrayIndexScale(Kind.Double) == 8;
    }
}
