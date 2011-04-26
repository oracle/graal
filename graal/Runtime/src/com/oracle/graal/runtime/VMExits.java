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

package com.oracle.graal.runtime;

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
