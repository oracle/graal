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
package com.oracle.graal.hotspot;

import com.oracle.graal.api.code.*;
import com.oracle.max.asm.target.amd64.*;

/**
 * Used to communicate configuration details, runtime offsets, etc. to graal upon compileMethod.
 */
public final class HotSpotVMConfig extends CompilerObject {

    private static final long serialVersionUID = -4744897993263044184L;

    HotSpotVMConfig() {
    }

    // os information, register layout, code generation, ...
    public boolean windowsOs;
    public int codeEntryAlignment;
    public boolean verifyOops;
    public boolean useFastLocking;
    public boolean useFastNewObjectArray;
    public boolean useFastNewTypeArray;
    public boolean useTLAB;
    public boolean useBiasedLocking;

    // offsets, ...
    public int vmPageSize;
    public int stackShadowPages;

    /**
     * The offset of the mark word in an object's header.
     */
    public int markOffset;

    /**
     * The offset of the hub/klassOop word in an object's header.
     */
    public int hubOffset;

    /**
     * The offset of the _prototype_header field in a Klass.
     */
    public int prototypeMarkWordOffset;

    /**
     * The offset of an the array length in an array's header.
     */
    public int arrayLengthOffset;

    /**
     * The offset of the _super_check_offset field in a Klass.
     */
    public int superCheckOffsetOffset;

    /**
     * The offset of the _secondary_super_cache field in a Klass.
     */
    public int secondarySuperCacheOffset;

    /**
     * The offset of the _secondary_supers field in a Klass.
     */
    public int secondarySupersOffset;

    /**
     * The offset of the _init_state field in an instanceKlass.
     */
    public int klassStateOffset;

    /**
     * The value of instanceKlass::fully_initialized.
     */
    public int klassStateFullyInitialized;

    /**
     * The value of objArrayKlass::element_klass_offset().
     */
    public int arrayClassElementOffset;

    /**
     * The value of JavaThread::tlab_top_offset().
     */
    public int threadTlabTopOffset;

    /**
     * The value of JavaThread::tlab_end_offset().
     */
    public int threadTlabEndOffset;

    public int threadObjectOffset;

    /**
     * The value of markOopDesc::unlocked_value.
     */
    public int unlockedMask;

    /**
     * The value of markOopDesc::biased_lock_mask_in_place.
     */
    public int biasedLockMaskInPlace;

    /**
     * The value of markOopDesc::age_mask_in_place.
     */
    public int ageMaskInPlace;

    /**
     * The value of markOopDesc::epoch_mask_in_place.
     */
    public int epochMaskInPlace;

    /**
     * The value of markOopDesc::biased_lock_pattern.
     */
    public int biasedLockPattern;

    public int threadExceptionOopOffset;
    public int threadExceptionPcOffset;
    public int threadMultiNewArrayStorageOffset;
    public long cardtableStartAddress;
    public int cardtableShift;
    public long safepointPollingAddress;
    public boolean isPollingPageFar;
    public int classMirrorOffset;
    public int runtimeCallStackSize;
    public int klassModifierFlagsOffset;
    public int klassOopOffset;
    public int graalMirrorKlassOffset;
    public int nmethodEntryOffset;
    public int methodCompiledEntryOffset;
    public int basicLockSize;
    public int basicLockDisplacedHeaderOffset;

    // methodData information
    public int methodDataOopDataOffset;
    public int methodDataOopTrapHistoryOffset;
    public int dataLayoutHeaderSize;
    public int dataLayoutTagOffset;
    public int dataLayoutFlagsOffset;
    public int dataLayoutBCIOffset;
    public int dataLayoutCellsOffset;
    public int dataLayoutCellSize;
    public int bciProfileWidth;
    public int typeProfileWidth;

    // runtime stubs
    public long debugStub;
    public long instanceofStub;
    public long newInstanceStub;
    public long newTypeArrayStub;
    public long newObjectArrayStub;
    public long newMultiArrayStub;
    public long inlineCacheMissStub;
    public long handleExceptionStub;
    public long handleDeoptStub;
    public long fastMonitorEnterStub;
    public long fastMonitorExitStub;
    public long verifyOopStub;
    public long vmErrorStub;

    // special registers
    public final Register threadRegister = AMD64.r15;

    public void check() {
        assert vmPageSize >= 16;
        assert codeEntryAlignment > 0;
        assert stackShadowPages > 0;
    }
}
