/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle.test.nodes;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.instrument.ProbeNode.WrapperNode;
import com.oracle.truffle.api.nodes.*;

/**
 * Support for PE testing instrumentation.
 */
public final class WrapperTestNode extends AbstractTestNode implements WrapperNode {

    @Child private AbstractTestNode child;
    @Child private ProbeNode probeNode;

    public WrapperTestNode(AbstractTestNode child) {
        this.child = child;
    }

    public String instrumentationInfo() {
        return "Wrapper for PE test nodes";
    }

    @Override
    public boolean isInstrumentable() {
        return false;
    }

    public void insertProbe(ProbeNode newProbeNode) {
        this.probeNode = newProbeNode;
    }

    public Probe getProbe() {
        return probeNode.getProbe();
    }

    @Override
    public Node getChild() {
        return child;
    }

    @Override
    public int execute(VirtualFrame frame) {
        probeNode.enter(child, frame);
        try {
            final int result = child.execute(frame);
            probeNode.returnValue(child, frame, result);
            return result;
        } catch (KillException e) {
            throw (e);
        } catch (Exception e) {
            probeNode.returnExceptional(child, frame, e);
            throw (e);
        }

    }

}
