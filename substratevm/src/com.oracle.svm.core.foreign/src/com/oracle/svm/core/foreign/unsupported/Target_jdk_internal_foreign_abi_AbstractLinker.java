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
package com.oracle.svm.core.foreign.unsupported;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.Linker.Option;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.foreign.ForeignAPIPredicates;
import com.oracle.svm.core.foreign.ForeignFunctionsRuntime;

/*
 * Substitutions for when Foreign Function and Memory (FFM) API support is enabled but not fully supported on the current architecture.
 * In this case, the Memory API usually works but the Foreign Function API and the symbol lookups don't.
 */

@TargetClass(className = "jdk.internal.foreign.abi.SoftReferenceCache", onlyWith = ForeignAPIPredicates.FunctionCallsUnsupported.class)
final class Target_jdk_internal_foreign_abi_SoftReferenceCache {
}

@TargetClass(className = "jdk.internal.foreign.abi.AbstractLinker", onlyWith = ForeignAPIPredicates.FunctionCallsUnsupported.class)
final class Target_jdk_internal_foreign_abi_AbstractLinker {
    // Checkstyle: stop
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "jdk.internal.foreign.abi.SoftReferenceCache") //
    private Target_jdk_internal_foreign_abi_SoftReferenceCache DOWNCALL_CACHE;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "jdk.internal.foreign.abi.SoftReferenceCache") //
    private Target_jdk_internal_foreign_abi_SoftReferenceCache UPCALL_CACHE;

    // Checkstyle: resume
    @Substitute
    @SuppressWarnings({"unused", "static-method"})
    MemorySegment upcallStub(MethodHandle target, FunctionDescriptor function, Arena arena, Linker.Option... options) {
        throw ForeignFunctionsRuntime.functionCallsUnsupported();
    }

    @Substitute
    @SuppressWarnings({"unused", "static-method"})
    private MethodHandle downcallHandle0(FunctionDescriptor function, Option... options) {
        throw ForeignFunctionsRuntime.functionCallsUnsupported();
    }
}
