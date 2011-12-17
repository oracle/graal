/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.hotspot;

import com.sun.cri.ci.*;

/**
 * Used to communicate configuration details, runtime offsets, etc. to graal upon compileMethod.
 */
public final class HotSpotVMConfig extends CompilerObject {

    private HotSpotVMConfig() {
        super(null);
    }

    // os information, register layout, code generation, ...
    public boolean windowsOs;
    public int codeEntryAlignment;
    public boolean verifyPointers;
    public boolean useFastLocking;
    public boolean useFastNewObjectArray;
    public boolean useFastNewTypeArray;

    // offsets, ...
    public int vmPageSize;
    public int stackShadowPages;
    public int hubOffset;
    public int arrayLengthOffset;
    public int klassStateOffset;
    public int klassStateFullyInitialized;
    public int[] arrayOffsets;
    public int arrayClassElementOffset;
    public int threadTlabTopOffset;
    public int threadTlabEndOffset;
    public int threadObjectOffset;
    public int instanceHeaderPrototypeOffset;
    public int threadExceptionOopOffset;
    public int threadExceptionPcOffset;
    public int threadMultiNewArrayStorage;
    public long cardtableStartAddress;
    public int cardtableShift;
    public long safepointPollingAddress;
    public boolean isPollingPageFar;
    public int classMirrorOffset;
    public int runtimeCallStackSize;
    public int klassModifierFlagsOffset;
    public int klassOopOffset;
    public int nmethodEntryOffset;

    // runtime stubs
    public long debugStub;
    public long instanceofStub;
    public long newInstanceStub;
    public long unresolvedNewInstanceStub;
    public long newTypeArrayStub;
    public long newObjectArrayStub;
    public long newMultiArrayStub;
    public long loadKlassStub;
    public long accessFieldStub;
    public long resolveStaticCallStub;
    public long inlineCacheMissStub;
    public long unwindExceptionStub;
    public long handleExceptionStub;
    public long handleDeoptStub;
    public long monitorEnterStub;
    public long monitorExitStub;
    public long fastMonitorEnterStub;
    public long fastMonitorExitStub;
    public long verifyPointerStub;

    public void check() {
        assert vmPageSize >= 16;
        assert codeEntryAlignment > 0;
        assert stackShadowPages > 0;
    }

    public int getArrayOffset(CiKind kind) {
        return arrayOffsets[getKindNumber(kind)];
    }

    private int getKindNumber(CiKind kind) {
        if (kind == CiKind.Boolean) {
            return 0;
        } else if (kind == CiKind.Byte) {
            return 1;
        } else if (kind == CiKind.Short) {
            return 2;
        } else if (kind == CiKind.Char) {
            return 3;
        } else if (kind == CiKind.Int) {
            return 4;
        } else if (kind == CiKind.Float) {
            return 5;
        } else if (kind == CiKind.Long) {
            return 6;
        } else if (kind == CiKind.Double) {
            return 7;
        } else if (kind == CiKind.Object) {
            return 8;
        } else {
            throw new RuntimeException(kind + " is not a Java kind");
        }
    }
}
