/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.code;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.VMError;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.NumUtil;

/** Base class common to both hosted and runtime compilations. */
public abstract class SharedCompilationResult extends CompilationResult {
    private int frameSize = -1;
    private int framePointerSaveAreaOffset = -1;
    private int codeAlignment = -1;

    public SharedCompilationResult(CompilationIdentifier compilationId, String name) {
        super(compilationId, name);
    }

    public int getFrameSize() {
        assert frameSize != -1 : "frame size not set";
        return frameSize;
    }

    public void setFrameSize(int value) {
        assert frameSize == -1;
        this.frameSize = value;
    }

    public boolean hasFramePointerSaveAreaOffset() {
        return framePointerSaveAreaOffset != -1;
    }

    public int getFramePointerSaveAreaOffset() {
        assert hasFramePointerSaveAreaOffset();
        return framePointerSaveAreaOffset;
    }

    public void setFramePointerSaveAreaOffset(int value) {
        assert !hasFramePointerSaveAreaOffset();
        this.framePointerSaveAreaOffset = value;
    }

    public static int getCodeAlignment(CompilationResult compilation) {
        int result;
        if (compilation instanceof SharedCompilationResult s) {
            result = s.codeAlignment;
        } else {
            result = SubstrateOptions.codeAlignment();
        }
        VMError.guarantee(result > 0 && NumUtil.isUnsignedPowerOf2(result), "invalid alignment %d", result);
        return result;
    }

    public void setCodeAlignment(int codeAlignment) {
        VMError.guarantee(codeAlignment > 0 && NumUtil.isUnsignedPowerOf2(codeAlignment), "invalid alignment %d", codeAlignment);
        this.codeAlignment = codeAlignment;
    }
}
