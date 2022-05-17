/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.graal;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.genscavenge.HeapParameters;
import com.oracle.svm.core.genscavenge.ThreadLocalAllocation;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.GCAllocationSupport;
import com.oracle.svm.core.snippets.SnippetRuntime;

public class GenScavengeAllocationSupport implements GCAllocationSupport {
    private static final SnippetRuntime.SubstrateForeignCallDescriptor SLOW_NEW_INSTANCE = SnippetRuntime.findForeignCall(ThreadLocalAllocation.class, "slowPathNewInstance", true);
    private static final SnippetRuntime.SubstrateForeignCallDescriptor SLOW_NEW_ARRAY = SnippetRuntime.findForeignCall(ThreadLocalAllocation.class, "slowPathNewArray", true);
    private static final SnippetRuntime.SubstrateForeignCallDescriptor SLOW_NEW_POD_INSTANCE = SnippetRuntime.findForeignCall(ThreadLocalAllocation.class, "slowPathNewPodInstance", true);
    private static final SnippetRuntime.SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SnippetRuntime.SubstrateForeignCallDescriptor[]{SLOW_NEW_INSTANCE, SLOW_NEW_ARRAY, SLOW_NEW_POD_INSTANCE};

    public static void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(FOREIGN_CALLS);
    }

    @Override
    public ForeignCallDescriptor getSlowNewInstanceStub() {
        return SLOW_NEW_INSTANCE;
    }

    @Override
    public ForeignCallDescriptor getSlowNewArrayStub() {
        return SLOW_NEW_ARRAY;
    }

    @Override
    public ForeignCallDescriptor getSlowNewPodInstanceStub() {
        return SLOW_NEW_POD_INSTANCE;
    }

    @Override
    public boolean useTLAB() {
        return true;
    }

    @Override
    public boolean shouldAllocateInTLAB(UnsignedWord size, boolean isArray) {
        return !isArray || size.belowThan(HeapParameters.getLargeArrayThreshold());
    }

    @Override
    public Word getTLABInfo() {
        return ThreadLocalAllocation.getTlabAddress();
    }

    @Override
    public int tlabTopOffset() {
        return ThreadLocalAllocation.Descriptor.offsetOfAllocationTop();
    }

    @Override
    public int tlabEndOffset() {
        return ThreadLocalAllocation.Descriptor.offsetOfAllocationEnd();
    }
}
