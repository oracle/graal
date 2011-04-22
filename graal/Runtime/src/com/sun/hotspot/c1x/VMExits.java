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

import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Exits from the HotSpot VM into Java code.
 *
 * @author Thomas Wuerthinger, Lukas Stadler
 */
public interface VMExits {

    void compileMethod(long methodVmId, String name, int entryBCI) throws Throwable;

    RiMethod createRiMethodResolved(long vmId, String name);

    RiMethod createRiMethodUnresolved(String name, String signature, RiType holder);

    RiSignature createRiSignature(String signature);

    RiField createRiField(RiType holder, String name, RiType type, int offset, int flags);

    RiType createRiType(long vmId, String name);

    RiType createRiTypePrimitive(int basicType);

    RiType createRiTypeUnresolved(String name);

    RiConstantPool createRiConstantPool(long vmId);

    CiConstant createCiConstant(CiKind kind, long value);

    CiConstant createCiConstantFloat(float value);

    CiConstant createCiConstantDouble(double value);

    CiConstant createCiConstantObject(Object object);

}
