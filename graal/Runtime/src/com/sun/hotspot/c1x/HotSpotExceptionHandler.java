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
package com.sun.hotspot.c1x;

import com.sun.cri.ri.*;


public class HotSpotExceptionHandler extends CompilerObject implements RiExceptionHandler {
    private int startBci;
    private int endBci;
    private int handlerBci;
    private int catchClassIndex;
    private RiType catchClass;

    public HotSpotExceptionHandler() {
        super(null);
    }

    @Override
    public int startBCI() {
        return startBci;
    }

    @Override
    public int endBCI() {
        return endBci;
    }

    @Override
    public int handlerBCI() {
        return handlerBci;
    }

    @Override
    public int catchTypeCPI() {
        return catchClassIndex;
    }

    @Override
    public boolean isCatchAll() {
        return catchClassIndex == 0;
    }

    @Override
    public RiType catchType() {
        return catchClass;
    }

}
