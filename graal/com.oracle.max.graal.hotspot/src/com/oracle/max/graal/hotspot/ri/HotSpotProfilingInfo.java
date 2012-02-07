/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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


public final class HotSpotProfilingInfo extends CompilerObject implements RiProfilingInfo {

    /**
     *
     */
    private static final long serialVersionUID = -8307682725047864875L;

    private int position;
    private int hintPosition;
    private int hintBCI;
    private HotSpotMethodDataAccessor dataAccessor;
    private HotSpotMethodData methodData;

    public HotSpotProfilingInfo(Compiler compiler, HotSpotMethodData methodData) {
        super(compiler);
        this.methodData = methodData;
        hintPosition = 0;
        hintBCI = -1;
    }

    @Override
    public RiTypeProfile getTypeProfile(int bci) {
        findBCI(bci, false);
        return dataAccessor.getTypeProfile(methodData, position);
    }

    @Override
    public double getBranchTakenProbability(int bci) {
        findBCI(bci, false);
        return dataAccessor.getBranchTakenProbability(methodData, position);
    }

    @Override
    public double[] getSwitchProbabilities(int bci) {
        findBCI(bci, false);
        return dataAccessor.getSwitchProbabilities(methodData, position);
    }

    @Override
    public boolean getExceptionSeen(int bci) {
        findBCI(bci, true);
        return dataAccessor.getExceptionSeen(methodData, position);
    }

    @Override
    public int getExecutionCount(int bci) {
        findBCI(bci, false);
        return dataAccessor.getExecutionCount(methodData, position);
    }

    private void findBCI(int targetBCI, boolean searchExtraData) {
        assert targetBCI >= 0 : "invalid BCI";

        if (methodData.hasNormalData()) {
            int currentPosition = targetBCI < hintBCI ? 0 : hintPosition;
            HotSpotMethodDataAccessor currentAccessor;
            while ((currentAccessor = methodData.getNormalData(currentPosition)) != null) {
                int currentBCI = currentAccessor.getBCI(methodData, currentPosition);
                if (currentBCI == targetBCI) {
                    normalDataFound(currentAccessor, currentPosition, currentBCI);
                    return;
                } else if (currentBCI > targetBCI) {
                    break;
                }
                currentPosition = currentPosition + currentAccessor.getSize(methodData, currentPosition);
            }
        }

        if (searchExtraData && methodData.hasExtraData()) {
            int currentPosition = methodData.getExtraDataBeginOffset();
            HotSpotMethodDataAccessor currentAccessor;
            while ((currentAccessor = methodData.getExtraData(currentPosition)) != null) {
                int currentBCI = currentAccessor.getBCI(methodData, currentPosition);
                if (currentBCI == targetBCI) {
                    extraDataFound(currentAccessor, currentPosition);
                    return;
                }
                currentPosition = currentPosition + currentAccessor.getSize(methodData, currentPosition);
            }
        }

        // TODO (ch) getExceptionSeen() should return UNKNOWN if not enough extra data

        noDataFound();
    }

    private void normalDataFound(HotSpotMethodDataAccessor data, int pos, int bci) {
        setCurrentData(data, pos);
        this.hintPosition = position;
        this.hintBCI = bci;
    }

    private void extraDataFound(HotSpotMethodDataAccessor data, int pos) {
        setCurrentData(data, pos);
    }

    private void noDataFound() {
        setCurrentData(HotSpotMethodData.getNoMethodData(), -1);
    }

    private void setCurrentData(HotSpotMethodDataAccessor dataAccessor, int position) {
        this.dataAccessor = dataAccessor;
        this.position = position;
    }
}
