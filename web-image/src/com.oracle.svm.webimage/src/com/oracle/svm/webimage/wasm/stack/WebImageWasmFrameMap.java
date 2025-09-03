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

package com.oracle.svm.webimage.wasm.stack;

import com.oracle.svm.webimage.wasm.types.WasmLMUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.lir.framemap.FrameMap;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.ValueKind;

/**
 * Stack frame map for the shadow stack in the Wasm backend.
 * <p>
 * This is the chosen format for the shadow stack:
 *
 * <pre>
 *   Base       Contents
 *
 *   caller   |                                |                    |
 *   frame    :     ...                        :                   High
 *            |                                |                   Addr
 *   ---------+--------------------------------+---------------     |
 *            | Caller "Instruction Pointer"   |    ^               |
 *            +--------------------------------+    |             Stack
 *    callee  | stack slot 0                   |  total    ^      growth
 *    frame   :     ...                        :  frame  frame      |
 *            | stack slot n                   |  size   size       |
 *            +--------------------------------+    |      |       Low
 *            | alignment padding              |    v      v       Addr
 *    sp -->  +--------------------------------+---------------     v
 * </pre>
 *
 * Here, the program execution is in the callee after its stack frame has been completely set up.
 * The stack grows downwards towards lower addresses.
 * <p>
 * Wasm does not have instruction pointers accessible to the program. However, SVM heavily relies on
 * IPs being present. Because of that we introduce a globally unique 32-bit pseudo instruction
 * pointer which is increased for every instruction that requires an associated IP (e.g. calls and
 * other {@linkplain Infopoint infopoints}). For each call, the first word in the callee's frame is
 * set to the IP of the call site.
 * <p>
 * That IP is also used to perform stack walks in the caller frame.
 */
public class WebImageWasmFrameMap extends FrameMap {
    public WebImageWasmFrameMap(CodeCacheProvider codeCache, RegisterConfig registerConfig, ReferenceMapBuilderFactory referenceMapFactory) {
        super(codeCache, registerConfig, referenceMapFactory);
        this.initialSpillSize = frameSetupSize();
        this.spillSize = initialSpillSize;
    }

    @Override
    public int totalFrameSize() {
        // frameSize + IP
        int result = frameSize() + initialSpillSize;
        assert result % getTarget().stackAlignment == 0 : "Total frame size not aligned: " + result;
        return result;
    }

    public static int frameSetupSize() {
        // Size of instruction pointer that is saved before every call
        return getIPSize();
    }

    @Fold
    public static int getIPSize() {
        return WasmLMUtil.POINTER_TYPE.getByteCount();
    }

    @Override
    public int currentFrameSize() {
        return alignFrameSize(outgoingSize + spillSize - initialSpillSize);
    }

    @Override
    public int spillSlotSize(ValueKind<?> kind) {
        return WasmLMUtil.POINTER_TYPE.getByteCount();
    }

    @Override
    protected int alignFrameSize(int size) {
        return NumUtil.roundUp(size + initialSpillSize, getTarget().stackAlignment) - initialSpillSize;
    }
}
