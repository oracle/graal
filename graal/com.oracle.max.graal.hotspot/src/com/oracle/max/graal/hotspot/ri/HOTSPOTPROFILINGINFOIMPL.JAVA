/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.hotspot.ri;

import com.oracle.max.cri.ri.*;
import com.oracle.max.graal.hotspot.*;
import com.oracle.max.graal.hotspot.Compiler;


public final class HotSpotProfilingInfoImpl extends CompilerObject implements RiProfilingInfo {

    /**
     *
     */
    private static final long serialVersionUID = -8307682725047864875L;

    private boolean atValidData;
    private int position;
    private HotSpotMethodDataAccessor dataAccessor;
    private HotSpotMethodData methodData;

    public HotSpotProfilingInfoImpl(Compiler compiler, HotSpotMethodData methodData) {
        super(compiler);
        this.methodData = methodData;
        setBCI(0);
    }

    public boolean setBCI(int value) {
        assert value >= 0 : "invalid BCI";

        seek(0);
        while (atValidData && currentBCI() < value) {
            next();
        }

        return atValidData;
    }

    @Override
    public int currentBCI() {
        return dataAccessor.getBCI(methodData, position);
    }

    @Override
    public boolean isAtValidData() {
        return atValidData;
    }

    @Override
    public boolean next() {
        return seek(position + dataAccessor.getSize(methodData, position));
    }

    @Override
    public RiResolvedType[] getTypes() {
        return dataAccessor.getTypes(methodData, position);
    }

    @Override
    public double[] getTypeProbabilities() {
        return dataAccessor.getTypeProbabilities(methodData, position);
    }

    @Override
    public double getBranchTakenProbability() {
        return dataAccessor.getBranchTakenProbability(methodData, position);
    }

    @Override
    public double[] getSwitchProbabilities() {
        return dataAccessor.getSwitchProbabilities(methodData, position);
    }

    @Override
    public boolean hasExceptionOccurred() {
        return dataAccessor.hasExceptionOccurred(methodData, position);
    }

    private boolean seek(int value) {
        position = value;
        dataAccessor = methodData.getDataAccessor(position);
        return dataAccessor != null;
    }
}
