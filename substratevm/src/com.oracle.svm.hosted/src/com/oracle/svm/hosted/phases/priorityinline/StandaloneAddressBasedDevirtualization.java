/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases.priorityinline;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.Invoke;

import jdk.graal.compiler.phases.common.priorityinline.nodes.devirtualization.AddressBasedDevirtualization;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class StandaloneAddressBasedDevirtualization extends AddressBasedDevirtualization {

    private final Invoke originalInvoke;
    private final ResolvedJavaMethod dispatchedMethod;
    private final double probability;

    public StandaloneAddressBasedDevirtualization(Invoke originalInvoke, ResolvedJavaMethod dispatchedMethod, double probability) {
        this.originalInvoke = originalInvoke;
        this.dispatchedMethod = dispatchedMethod;
        this.probability = probability;
    }

    @Override
    public NodeSourcePosition callerPosition() {
        return originalInvoke.asNode().getNodeSourcePosition();
    }

    @Override
    public double probability() {
        return probability;
    }

    @Override
    protected void setDuplicatedInvoke(Invoke duplicatedInvoke) {
        // Ignored
    }

    @Override
    protected ResolvedJavaMethod dispatchedMethod() {
        return dispatchedMethod;
    }
}
