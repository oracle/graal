/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.spi;

import jdk.vm.ci.meta.JavaConstant;

/**
 * GR-52365: This interface can be removed when {@link jdk.vm.ci.meta.ConstantReflectionProvider}
 * offers an {@code identityHashCode} method too.
 */
public interface IdentityHashCodeProvider {

    /**
     * Reads the identity hash code of the given object. Returns {@code null} if the constant is not
     * an object, or if the value is not available at this point.
     * <p>
     * This method usually computes and stores the identity hash code if it was not computed for the
     * object beforehand, but it is not required to do so - it can also return {@code null} if the
     * computation is not possible or desired at the current time.
     * <p>
     * For the {@link JavaConstant#isNull() null constant}, this method returns zero as specified by
     * {@link System#identityHashCode(Object)}.
     */
    Integer identityHashCode(JavaConstant constant);
}
