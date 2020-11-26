/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.common.hotspot;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public interface HotSpotTruffleCompilerRuntime extends TruffleCompilerRuntime {
    /**
     * Gets all methods denoted as a Truffle call boundary (such as being annotated by
     * {@code TruffleCallBoundary}).
     */
    Iterable<ResolvedJavaMethod> getTruffleCallBoundaryMethods();

    /**
     * Notifies this runtime once {@code installedCode} has been installed in the code cache.
     *
     * @param compilable the {@link CompilableTruffleAST compilable} to install code into
     * @param installedCode code that has just been installed in the code cache
     */
    void onCodeInstallation(CompilableTruffleAST compilable, InstalledCode installedCode);

    /**
     * In compilations that are separated between HotSpot and AOT-compiled Graal, creates a scope
     * object that is used for calls across the HotSpot-Graal call-boundary.
     *
     * The return depth must be passed to the {@code exitLibGraalScope} call.
     */
    default int enterLibGraalScope() {
        throw new UnsupportedOperationException();
    }

    /**
     * In compilations that are separated between HotSpot and AOT-compiled Graal, closes the
     * previously-created scope object.
     *
     * The expected depth must match the depth returned by the previous {@code enterLibGraalScope}
     * call.
     */
    @SuppressWarnings("unused")
    default void exitLibGraalScope(int expectedDepth) {
        throw new UnsupportedOperationException();
    }
}
