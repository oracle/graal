/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.hotspot;

import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;

import org.graalvm.compiler.code.CompilationResult;

import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

/**
 * Constants used to mark special positions in code being installed into the code cache by Graal C++
 * code.
 */
public enum HotSpotMarkId implements CompilationResult.MarkId {
    VERIFIED_ENTRY("VERIFIED_ENTRY", false),
    UNVERIFIED_ENTRY("UNVERIFIED_ENTRY", false),
    OSR_ENTRY("OSR_ENTRY", false),
    EXCEPTION_HANDLER_ENTRY("EXCEPTION_HANDLER_ENTRY", false),
    DEOPT_HANDLER_ENTRY("DEOPT_HANDLER_ENTRY", false),
    FRAME_COMPLETE("FRAME_COMPLETE", true, true),
    INVOKEINTERFACE("INVOKEINTERFACE", true),
    INVOKEVIRTUAL("INVOKEVIRTUAL", true),
    INVOKESTATIC("INVOKESTATIC", true),
    INVOKESPECIAL("INVOKESPECIAL", true),
    INLINE_INVOKE("INLINE_INVOKE", true),
    POLL_NEAR("POLL_NEAR", true),
    POLL_RETURN_NEAR("POLL_RETURN_NEAR", true),
    POLL_FAR("POLL_FAR", true),
    POLL_RETURN_FAR("POLL_RETURN_FAR", true),
    CARD_TABLE_ADDRESS("CARD_TABLE_ADDRESS", true),
    NARROW_KLASS_BASE_ADDRESS("NARROW_KLASS_BASE_ADDRESS", true),
    NARROW_OOP_BASE_ADDRESS("NARROW_OOP_BASE_ADDRESS", true),
    CRC_TABLE_ADDRESS("CRC_TABLE_ADDRESS", true),
    LOG_OF_HEAP_REGION_GRAIN_BYTES("LOG_OF_HEAP_REGION_GRAIN_BYTES", true);

    private final String name;
    private final boolean isMarkAfter;
    @NativeImageReinitialize private Integer value;
    private final boolean optional;

    HotSpotMarkId(String name, boolean isMarkAfter) {
        this.name = name;
        this.isMarkAfter = isMarkAfter;
        this.optional = false;
    }

    HotSpotMarkId(String name, boolean isMarkAfter, boolean optional) {
        this.name = name;
        this.isMarkAfter = isMarkAfter;
        this.optional = optional;
    }

    private Integer getValue() {
        if (value == null) {
            Long result = HotSpotJVMCIRuntime.runtime().getConfigStore().getConstants().get("CodeInstaller::" + name);
            if (result != null) {
                this.value = result.intValue();
            } else if (!optional) {
                throw shouldNotReachHere("Unsupported Mark " + name);
            }
        }
        return value;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getId() {
        assert isAvailable() : this;
        return getValue();
    }

    @Override
    public boolean isMarkAfter() {
        return isMarkAfter;
    }

    public boolean isAvailable() {
        return getValue() != null;
    }

    @Override
    public String toString() {
        return "HotSpotCodeMark{" + name +
                        ", value=" + getValue() +
                        ", optional=" + optional +
                        '}';
    }
}
