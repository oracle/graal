/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.hotspot;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.debug.GraalError;

import jdk.vm.ci.common.NativeImageReinitialize;

/**
 * Constants used to mark special positions in code being installed into the code cache by Graal C++
 * code.
 */
public enum HotSpotMarkId implements CompilationResult.MarkId {
    VERIFIED_ENTRY,
    UNVERIFIED_ENTRY,
    OSR_ENTRY,
    EXCEPTION_HANDLER_ENTRY,
    DEOPT_HANDLER_ENTRY,
    DEOPT_MH_HANDLER_ENTRY,
    FRAME_COMPLETE,
    ENTRY_BARRIER_PATCH,
    INVOKEINTERFACE,
    INVOKEVIRTUAL,
    INVOKESTATIC,
    INVOKESPECIAL,
    INLINE_INVOKE,
    POLL_NEAR,
    POLL_RETURN_NEAR,
    POLL_FAR,
    POLL_RETURN_FAR,
    CARD_TABLE_ADDRESS,
    NARROW_KLASS_BASE_ADDRESS,
    NARROW_OOP_BASE_ADDRESS,
    CRC_TABLE_ADDRESS,
    LOG_OF_HEAP_REGION_GRAIN_BYTES,
    VERIFY_OOPS,
    VERIFY_OOP_BITS,
    VERIFY_OOP_MASK,
    VERIFY_OOP_COUNT_ADDRESS();

    @NativeImageReinitialize private Integer value;

    HotSpotMarkId() {
        this.value = null;
    }

    void setValue(Integer value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String getName() {
        return name();
    }

    @Override
    public Object getId() {
        GraalError.guarantee(isAvailable(), "mark is not available %s", this);
        return value;
    }

    public boolean isAvailable() {
        return value != null;
    }

    @Override
    public String toString() {
        return "HotSpotCodeMark{" + name() +
                        ", value=" + value +
                        '}';
    }

}
