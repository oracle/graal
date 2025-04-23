/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common.spi;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Provides additional meta data about JVMCI objects that is not provided by the VM itself, and
 * therefore does not need to be in JVMCI itself.
 */
public interface MetaAccessExtensionProvider {

    /**
     * The {@link JavaKind} used to store the provided type in a field or array element. This can be
     * different than the {@link JavaType#getJavaKind} for types that are intercepted and
     * transformed by the compiler.
     */
    JavaKind getStorageKind(JavaType type);

    /**
     * Checks if a dynamic allocation of the provided type can be canonicalized to a regular
     * allocation node. If the method returns false, then the dynamic allocation would throw an
     * exception at run time and therefore canonicalization would miss that exception.
     */
    boolean canConstantFoldDynamicAllocation(ResolvedJavaType type);

    /**
     * Checks if the invocation of the provided method is guaranteed to include a safepoint.
     *
     * @param isDirect Whether the method is being directly invoked. For indirect calls, a method is
     *            guaranteed to have a safepoint only if all possible destinations are guaranteed to
     *            contain a safepoint.
     */
    boolean isGuaranteedSafepoint(ResolvedJavaMethod method, boolean isDirect);

    /**
     * Returns true if the partial escape analysis is allowed to virtualize object allocations of a
     * given type.
     */
    boolean canVirtualize(ResolvedJavaType instanceType);

    /**
     * Given an unsafe access, try to find the static field that is being accessed.
     *
     * @param base the base object used in the access.
     * @param offset the offset used in the access.
     * @param accessKind the kind of data being accessed.
     * @return the static field being accessed or null if this is not a static field access or if it
     *         can't be determined.
     */
    ResolvedJavaField getStaticFieldForAccess(JavaConstant base, long offset, JavaKind accessKind);
}
