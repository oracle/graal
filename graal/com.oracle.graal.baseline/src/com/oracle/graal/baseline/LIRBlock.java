/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.baseline;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;

public class LIRBlock implements com.oracle.graal.nodes.cfg.AbstractBlock<LIRBlock> {

    public int getId() {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    public AbstractBeginNode getBeginNode() {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    public Loop getLoop() {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    public int getLoopDepth() {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    public boolean isLoopHeader() {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    public boolean isLoopEnd() {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    public boolean isExceptionEntry() {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    public List<LIRBlock> getPredecessors() {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    public int getPredecessorCount() {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    public List<LIRBlock> getSuccessors() {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    public int getSuccessorCount() {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    public int getLinearScanNumber() {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    public void setLinearScanNumber(int linearScanNumber) {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    public boolean isAligned() {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    public void setAlign(boolean align) {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

    public LIRBlock getDominator() {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented("Auto-generated method stub");
    }

}
