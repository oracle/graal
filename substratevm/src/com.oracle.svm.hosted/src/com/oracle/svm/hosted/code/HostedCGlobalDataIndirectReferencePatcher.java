/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.nio.ByteBuffer;

import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.graal.code.CGlobalDataIndirectReference;
import com.oracle.svm.hosted.image.RelocatableBuffer;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.code.CompilationResult;
import jdk.vm.ci.code.site.Reference;

public class HostedCGlobalDataIndirectReferencePatcher extends CompilationResult.CodeAnnotation implements HostedPatcher {
    private final CGlobalDataIndirectReference reference;

    public HostedCGlobalDataIndirectReferencePatcher(int operandPosition, CGlobalDataIndirectReference reference) {
        super(operandPosition);
        this.reference = reference;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public void relocate(Reference ref, RelocatableBuffer codeBuffer, int compStart) {
        throw VMError.shouldNotReachHere("Indirect CGlobalData access through image heap should be patched during image generation.");
    }

    @Override
    public void patch(int compStart, int relative, byte[] code) {
        assert compStart == -1 && relative == -1 : "We do not need compStart and relative arguments when patching CGlobalDataReference object";
        ByteBuffer targetCode = ByteBuffer.wrap(code).order(SubstrateTarget.getArchitecture().getByteOrder());
        targetCode.putInt(getPosition(), reference.getDataInfo().getOffset());
    }
}
