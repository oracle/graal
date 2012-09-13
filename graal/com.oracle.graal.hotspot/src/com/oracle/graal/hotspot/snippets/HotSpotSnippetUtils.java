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

import static com.oracle.graal.nodes.extended.UnsafeLoadNode.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.Register.RegisterFlag;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.snippets.Snippet.Fold;
import com.oracle.graal.snippets.*;
import com.oracle.max.asm.target.amd64.*;

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
    static Register threadReg() {
        return HotSpotGraalRuntime.getInstance().getConfig().threadRegister;
    }

    @Fold
    static Register stackPointerReg() {
        return AMD64.rsp;
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
    static int hubOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().hubOffset;
    }

    @Fold
    static int arrayLengthOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().arrayLengthOffset;
    }

    @Fold
    static int arrayBaseOffset(Kind elementKind) {
        return elementKind.getArrayBaseOffset();
    }

    @Fold
    static int arrayIndexScale(Kind elementKind) {
        return elementKind.getArrayIndexScale();
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

    /**
     * Loads the hub from a object, null checking it first.
     */
    static Object loadHub(Object object) {
        return UnsafeLoadNode.loadObject(object, 0, hubOffset(), true);
    }


    static Object verifyOop(Object object) {
        if (verifyOops()) {
            VerifyOopStubCall.call(object);
        }
        return object;
    }

    static Word asWord(Object object) {
        return Word.fromObject(object);
    }

    static Word loadWord(Word address, int offset) {
        Object value = loadObject(address, 0, offset, true);
        return asWord(value);
    }

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

    public static Register getStubParameterRegister(int index) {
        RegisterConfig regConfig = HotSpotGraalRuntime.getInstance().getRuntime().getGlobalStubRegisterConfig();
        return regConfig.getCallingConventionRegisters(CallingConvention.Type.RuntimeCall, RegisterFlag.CPU)[index];
    }
}
