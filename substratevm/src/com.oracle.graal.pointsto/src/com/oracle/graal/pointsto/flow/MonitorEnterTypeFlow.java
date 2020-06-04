/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.nodes.java.MonitorEnterNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.meta.AnalysisMethod;

import jdk.vm.ci.code.BytecodePosition;

public class MonitorEnterTypeFlow extends TypeFlow<BytecodePosition> {

    private final BytecodeLocation location;
    private final AnalysisMethod method;

    public MonitorEnterTypeFlow(BigBang bb, MonitorEnterNode source, BytecodeLocation monitorLocation, AnalysisMethod method) {
        super(source.getNodeSourcePosition(), null);
        this.location = monitorLocation;
        this.method = method;
        this.addUse(bb, bb.getAllSynchronizedTypeFlow());
    }

    public BytecodeLocation getLocation() {
        return location;
    }

    public AnalysisMethod getMethod() {
        return method;
    }

    @Override
    public TypeFlow<BytecodePosition> copy(BigBang bb, MethodFlowsGraph methodFlows) {
        return this;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("MonitorEnterFlow<").append(getState()).append(">");
        return str.toString();
    }

}
