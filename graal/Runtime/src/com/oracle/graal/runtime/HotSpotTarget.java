/*
 * Copyright (c) 2010 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.oracle.graal.runtime;

import com.sun.cri.ci.*;

/**
 * HotSpot-specific CiTarget that provides the correct stack frame size alignment.
 *
 * @author Lukas Stadler
 */
public class HotSpotTarget extends CiTarget {

    public HotSpotTarget(CiArchitecture arch, boolean isMP, int spillSlotSize, int stackAlignment, int pageSize, int cacheAlignment, boolean inlineObjects) {
        super(arch, isMP, spillSlotSize, stackAlignment, pageSize, cacheAlignment, inlineObjects, true);
    }

    @Override
    public int alignFrameSize(int frameSize) {
        // account for the stored rbp value
        return super.alignFrameSize(frameSize + wordSize) - wordSize;
    }
}
