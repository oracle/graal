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

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;

import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.GCAllocationSupport;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.word.Word;

public class WasmLMAllocationSupport implements GCAllocationSupport {

    private static final SubstrateForeignCallDescriptor NEW_INSTANCE = SnippetRuntime.findForeignCall(WasmAllocation.class, "newInstance", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor NEW_ARRAY = SnippetRuntime.findForeignCall(WasmAllocation.class, "newArray", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor[] UNCONDITIONAL_FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{NEW_INSTANCE, NEW_ARRAY};

    public static void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(UNCONDITIONAL_FOREIGN_CALLS);
    }

    @Override
    public ForeignCallDescriptor getNewInstanceStub() {
        return NEW_INSTANCE;
    }

    @Override
    public ForeignCallDescriptor getNewArrayStub() {
        return NEW_ARRAY;
    }

    @Override
    public ForeignCallDescriptor getNewStoredContinuationStub() {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public ForeignCallDescriptor getNewPodInstanceStub() {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public boolean useTLAB() {
        return false;
    }

    @Override
    public boolean shouldAllocateInTLAB(UnsignedWord size, boolean isArray) {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public Word getTLABInfo() {
        return Word.nullPointer();
    }

    @Override
    public int tlabTopOffset() {
        return 0;
    }

    @Override
    public int tlabEndOffset() {
        return 0;
    }
}
