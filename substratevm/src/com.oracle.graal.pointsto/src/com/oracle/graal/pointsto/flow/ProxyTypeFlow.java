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
package com.oracle.graal.pointsto.flow;

import org.graalvm.compiler.graph.Node;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.code.BytecodePosition;

/**
 * A proxy class to be used for {@link AllInstantiatedTypeFlow}s. The cloning mechanism needs a
 * mechanism to clone only those uses of these global flows that belong to the current cloned
 * method. Thus the proxy is just a level of indirection to the global flows where the local uses
 * can be attached.
 * 
 * A proxy type flow doesn't participate in any type flow updates. When a method is cloned it links
 * the clones of the uses/observers of the proxy flow directly to the input flow.
 */
public class ProxyTypeFlow extends TypeFlow<BytecodePosition> {

    protected TypeFlow<?> input;

    public ProxyTypeFlow(Node source, TypeFlow<?> input) {
        this(source.getNodeSourcePosition(), input);
    }

    public ProxyTypeFlow(BytecodePosition source, TypeFlow<?> input) {
        super(source, null);
        assert input instanceof AllInstantiatedTypeFlow || input instanceof UnknownTypeFlow;
        this.input = input;
    }

    @Override
    public TypeFlow<BytecodePosition> copy(BigBang bb, MethodFlowsGraph methodFlows) {
        return this;
    }

    public TypeFlow<?> getInput() {
        return input;
    }

    @Override
    public boolean addState(BigBang bb, TypeState add) {
        throw AnalysisError.shouldNotReachHere();
    }

    @Override
    public void update(BigBang bb) {
        throw AnalysisError.shouldNotReachHere();
    }

    @Override
    public TypeState getState() {
        throw AnalysisError.shouldNotReachHere();
    }

    @Override
    public String toString() {
        return "ProxyTypeFlow<" + input + ">";
    }
}
