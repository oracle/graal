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
package com.oracle.svm.webimage.heap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.graal.snippets.GCAllocationSupport;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.word.Word;

/**
 * No-op implementation of {@link GCAllocationSupport}.
 * <p>
 * Native Image requires an {@linkplain ImageSingletons singleton} for {@link GCAllocationSupport}
 * during bytecode parsing (because {@code SubstrateAllocationSnippets#gcAllocationSupport} is
 * reachable from a snippet).
 * <p>
 * None of the methods in this class should ever be called or make it into the image.
 */
public class WebImageNopAllocationSupport implements GCAllocationSupport {
    @Override
    public ForeignCallDescriptor getNewInstanceStub() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public ForeignCallDescriptor getNewArrayStub() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public ForeignCallDescriptor getNewStoredContinuationStub() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public ForeignCallDescriptor getNewPodInstanceStub() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public boolean useTLAB() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public boolean shouldAllocateInTLAB(UnsignedWord size, boolean isArray) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public Word getTLABInfo() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public int tlabTopOffset() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public int tlabEndOffset() {
        throw GraalError.unimplementedOverride();
    }
}
