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
package com.oracle.svm.interpreter.ristretto;

public class RistrettoDirectives {

    /**
     * Method that evaluates to true when Native Image compiles the current method at run-time, and
     * false otherwise.
     * <p>
     * In SVM, most code paths are "compiled" in some sense, so
     * {@link jdk.graal.compiler.api.directives.GraalDirectives#inCompiledCode()} is not sufficient
     * for distinguishing AOT image-building from runtime compilation. For testing and
     * instrumentation, this method (intrinsified by a special node) defers folding until after
     * high-tier lowering and then:
     * <ul>
     * <li>Returns a constant true if the method's {@link jdk.vm.ci.meta.ResolvedJavaMethod} is a
     * {@link com.oracle.svm.graal.meta.SubstrateMethod} (i.e., compiled at SVM runtime via JVMCI).
     * Note that this also returns {@code true} for Truffle compilations at SVM runtime.</li>
     * <li>Returns a constant false otherwise.</li>
     * </ul>
     */
    public static boolean inRuntimeCompiledCode() {
        return false;
    }

}
