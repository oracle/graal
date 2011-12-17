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
package com.sun.cri.xir;

import java.util.*;

import com.sun.cri.ci.*;
import com.sun.cri.ri.*;
import com.sun.cri.ri.RiType.*;

/**
 * Represents the interface through which the compiler requests the XIR for a given bytecode from the runtime system.
 */
public interface RiXirGenerator {

    /**
     * Note: may return {@code null}.
     */
    XirSnippet genPrologue(XirSite site, RiResolvedMethod method);

    /**
     * Note: may return {@code null} in which case the compiler will not emit a return instruction.
     */
    XirSnippet genEpilogue(XirSite site, RiResolvedMethod method);

    XirSnippet genSafepointPoll(XirSite site);

    XirSnippet genExceptionObject(XirSite site);

    XirSnippet genResolveClass(XirSite site, RiType type, Representation representation);

    XirSnippet genIntrinsic(XirSite site, XirArgument[] arguments, RiMethod method);

    XirSnippet genInvokeInterface(XirSite site, XirArgument receiver, RiMethod method);

    XirSnippet genInvokeVirtual(XirSite site, XirArgument receiver, RiMethod method);

    XirSnippet genInvokeSpecial(XirSite site, XirArgument receiver, RiMethod method);

    XirSnippet genInvokeStatic(XirSite site, RiMethod method);

    XirSnippet genMonitorEnter(XirSite site, XirArgument receiver, XirArgument lockAddress);

    XirSnippet genMonitorExit(XirSite site, XirArgument receiver, XirArgument lockAddress);

    XirSnippet genGetField(XirSite site, XirArgument receiver, RiField field);

    XirSnippet genPutField(XirSite site, XirArgument receiver, RiField field, XirArgument value);

    XirSnippet genGetStatic(XirSite site, XirArgument staticTuple, RiField field);

    XirSnippet genPutStatic(XirSite site, XirArgument staticTuple, RiField field, XirArgument value);

    XirSnippet genNewInstance(XirSite site, RiType type);

    XirSnippet genNewArray(XirSite site, XirArgument length, CiKind elementKind, RiType componentType, RiType arrayType);

    XirSnippet genNewObjectArrayClone(XirSite site, XirArgument newLength, XirArgument referenceArray);

    XirSnippet genNewMultiArray(XirSite site, XirArgument[] lengths, RiType type);

    XirSnippet genCheckCast(XirSite site, XirArgument receiver, XirArgument hub, RiType type);

    XirSnippet genInstanceOf(XirSite site, XirArgument receiver, XirArgument hub, RiType type);

    XirSnippet genMaterializeInstanceOf(XirSite site, XirArgument receiver, XirArgument hub, XirArgument trueValue, XirArgument falseValue, RiType type);

    XirSnippet genArrayLoad(XirSite site, XirArgument array, XirArgument index, CiKind elementKind, RiType elementType);

    XirSnippet genArrayStore(XirSite site, XirArgument array, XirArgument index, XirArgument value, CiKind elementKind, RiType elementType);

    XirSnippet genArrayLength(XirSite site, XirArgument array);

    XirSnippet genWriteBarrier(XirArgument object);

    XirSnippet genArrayCopy(XirSite site, XirArgument src, XirArgument srcPos, XirArgument dest, XirArgument destPos, XirArgument length, RiType elementType, boolean inputsSame, boolean inputsDifferent);

    XirSnippet genCurrentThread(XirSite site);

    XirSnippet genGetClass(XirSite site, XirArgument xirArgument);

    /**
     * Generates code that checks that the {@linkplain Representation#ObjectHub hub} of
     * an object is identical to a given hub constant. In pseudo code:
     * <pre>
     *     if (object.getHub() != hub) {
     *         uncommonTrap();
     *     }
     * </pre>
     * This snippet should only be used when the object is guaranteed not to be null.
     */
    XirSnippet genTypeCheck(XirSite site, XirArgument object, XirArgument hub, RiType type);

    /**
     * Gets the list of XIR templates, using the given XIR assembler to create them if
     * they haven't yet been created.
     *
     * @param asm the XIR assembler
     * @return the list of templates
     */
    List<XirTemplate> makeTemplates(CiXirAssembler asm);

}
