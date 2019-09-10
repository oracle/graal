/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.meta;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.truffle.compiler.nodes.ObjectLocationIdentity;
import org.graalvm.nativeimage.ImageSingletons;

import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * Tests if two {@linkplain SubstrateObjectConstant object constants} reference the same object.
 *
 * This class is needed because different subclasses of {@link SubstrateObjectConstant} must be
 * comparable, but their {@link Object#equals} methods should not and cannot (due to dependencies)
 * have direct knowledge of other classes, so their methods delegate to this class. This code
 * overlaps with {@link ConstantReflectionProvider#constantEquals}, but existing code relies on
 * directly testing object equality on constants, such as the {@link ObjectLocationIdentity} class,
 * which is crucial for the alias analysis of memory accesses during compilation.
 */
public interface ObjectConstantEquality {
    @Fold
    static ObjectConstantEquality get() {
        return ImageSingletons.lookup(ObjectConstantEquality.class);
    }

    boolean test(SubstrateObjectConstant x, SubstrateObjectConstant y);
}
