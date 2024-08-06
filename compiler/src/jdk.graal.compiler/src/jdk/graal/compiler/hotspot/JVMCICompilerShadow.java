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
package jdk.graal.compiler.hotspot;

import jdk.vm.ci.runtime.JVMCICompiler;

/**
 * Shadows methods added to newer versions of {@link JVMCICompiler} such that Graal can be compiled
 * against older versions that do not have the new methods. Without shadowing, javac will issue a
 * warning for the {@code Override} annotation on the implementations of these methods in
 * {@link HotSpotGraalCompiler}.
 */
public interface JVMCICompilerShadow {
    /**
     * Determines if this compiler supports a given HotSpot garbage collector.
     *
     * @param gcIdentifier ordinal of a {@code CollectedHeap::Name} value
     */
    boolean isGCSupported(int gcIdentifier);
}
