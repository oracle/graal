/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.cri.xir;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.cri.ri.RiType.Representation;

/**
 * Represents the interface through which the compiler requests the XIR for a given bytecode from the runtime system.
 */
public interface RiXirGenerator {

    XirSnippet genInvokeInterface(XirSite site, XirArgument receiver, RiMethod method);

    XirSnippet genInvokeVirtual(XirSite site, XirArgument receiver, RiMethod method, boolean megamorph);

    XirSnippet genInvokeSpecial(XirSite site, XirArgument receiver, RiMethod method);

    XirSnippet genInvokeStatic(XirSite site, RiMethod method);

    XirSnippet genMonitorEnter(XirSite site, XirArgument receiver, XirArgument lockAddress);

    XirSnippet genMonitorExit(XirSite site, XirArgument receiver, XirArgument lockAddress);

    XirSnippet genNewInstance(XirSite site, RiType type);

    XirSnippet genNewArray(XirSite site, XirArgument length, CiKind elementKind, RiType componentType, RiType arrayType);

    XirSnippet genNewMultiArray(XirSite site, XirArgument[] lengths, RiType type);

    XirSnippet genCheckCast(XirSite site, XirArgument receiver, XirArgument hub, RiType type, RiResolvedType[] hints, boolean hintsExact);

    XirSnippet genInstanceOf(XirSite site, XirArgument receiver, XirArgument hub, RiType type, RiResolvedType[] hints, boolean hintsExact);

    XirSnippet genMaterializeInstanceOf(XirSite site, XirArgument receiver, XirArgument hub, XirArgument trueValue, XirArgument falseValue, RiType type, RiResolvedType[] hints, boolean hintsExact);

    /**
     * Generates code that checks that the {@linkplain Representation#ObjectHub hub} of
     * an object is identical to a given hub constant. In pseudo code:
     * <pre>
     *     if (object.getHub() != hub) {
     *       jump(falseSuccessor)
     *     }
     * </pre>
     * This snippet should only be used when the object is guaranteed not to be null.
     */
    XirSnippet genTypeBranch(XirSite site, XirArgument thisHub, XirArgument otherHub, RiType type);

    /**
     * Initializes the XIR generator for the given XIR assembler.
     *
     * @param asm the XIR assembler
     */
    void initialize(CiXirAssembler asm);

}
