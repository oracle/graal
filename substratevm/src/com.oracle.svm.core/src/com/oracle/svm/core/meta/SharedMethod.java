/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.deopt.Deoptimizer;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * The method interface which is both used in the hosted and substrate worlds.
 */
public interface SharedMethod extends ResolvedJavaMethod {

    /**
     * Returns true if this method is a native entry point, i.e., called from C code. The method
     * must not be called from Java code then.
     */
    boolean isEntryPoint();

    boolean hasCalleeSavedRegisters();

    SharedMethod[] getImplementations();

    boolean isDeoptTarget();

    boolean canDeoptimize();

    int getVTableIndex();

    /**
     * Returns the deopt stub type for the stub methods in {@link Deoptimizer}. Only used when
     * compiling the deopt stubs during image generation.
     */
    Deoptimizer.StubType getDeoptStubType();

    int getCodeOffsetInImage();

    int getDeoptOffsetInImage();
}
