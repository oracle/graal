/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class MHInvokeBasicNode extends EspressoBaseNode {

    final int form;
    final int vmentry;
    final int hidden_vmtarget;
    @Child IndirectCallNode callNode;

    public MHInvokeBasicNode(Method method) {
        super(method);
        Meta meta = getMeta();
        this.form = meta.form.getFieldIndex();
        this.vmentry = meta.vmentry.getFieldIndex();
//        this.isCompiled = meta.isCompiled;
        this.hidden_vmtarget = meta.HIDDEN_VMTARGET.getFieldIndex();
        this.callNode = IndirectCallNode.create();
    }

    @Override
    public Object invokeNaked(VirtualFrame frame) {
        StaticObject mh = (StaticObject) frame.getArguments()[0];
        StaticObject lform = (StaticObject) mh.getUnsafeField(form);
        StaticObject mname = (StaticObject) lform.getUnsafeField(vmentry);
        Method target = (Method) mname.getUnsafeField(hidden_vmtarget);
        return callNode.call(target.getCallTarget(), frame.getArguments());
//        return node.executeBasic(lform, frame.getArguments());
    }
}

//abstract class BasicNode extends Node {
//
//    final int vmentry;
//    final Field isCompiled;
//    final int hidden_vmtarget;
//
//    static final int INLINE_CACHE_SIZE_LIMIT = 3;
//
//    BasicNode(Meta meta) {
//        this.vmentry = meta.vmentry.getFieldIndex();
//        this.isCompiled = meta.isCompiled;
//        this.hidden_vmtarget = meta.HIDDEN_VMTARGET.getFieldIndex();
//    }
//
//    abstract Object executeBasic(StaticObject lform, Object[] args);
//
//    /**
//     * Cache on the lambdaForm, since we need to extract it anyway. With some luck, perhaps two
//     * different MH will have the same LF.
//     */
//    @SuppressWarnings("unused")
//    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = {"lform == cachedlform", "getBooleanField(lform, isCompiled)"})
//    Object directBasic(StaticObject lform, Object[] args,
//                    @Cached("lform") StaticObject cachedlform,
//                    @Cached("getMethodHiddenField(getSOIField(lform, vmentry), hidden_vmtarget)") Method target,
//                    @Cached("create(target.getCallTarget())") DirectCallNode callNode) {
//        return callNode.call(args);
//    }
//
//    @Specialization(replaces = "directBasic")
//    Object normalBasic(StaticObject lform, Object[] args,
//                    @Cached("create()") IndirectCallNode callNode) {
//        StaticObject mname = (StaticObject) lform.getUnsafeField(vmentry);
//        Method target = (Method) mname.getUnsafeField(hidden_vmtarget);
//        return callNode.call(target.getCallTarget(), args);
//    }
//
//    static StaticObject getSOIField(StaticObject object, int field) {
//        return (StaticObject) object.getUnsafeField(field);
//    }
//
//    static Method getMethodHiddenField(StaticObject object, int HIDDEN_VMTARGET) {
//        return (Method) object.getUnsafeField(HIDDEN_VMTARGET);
//    }
//
//    static boolean getBooleanField(StaticObject object, Field field) {
//        return object.getBooleanField(field);
//    }
//}