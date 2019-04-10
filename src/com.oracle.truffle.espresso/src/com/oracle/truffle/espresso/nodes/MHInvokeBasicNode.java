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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

public class MHInvokeBasicNode extends EspressoBaseNode {

    final int form;
    @Child BasicNode node;

    public MHInvokeBasicNode(Method method) {
        super(method);
        this.form = getMeta().form.getFieldIndex();
        this.node = BasicNodeGen.create(getMeta());
    }

    @Override
    public Object invokeNaked(VirtualFrame frame) {
        StaticObjectImpl mh = (StaticObjectImpl) frame.getArguments()[0];
        StaticObjectImpl lform = (StaticObjectImpl) mh.getUnsafeField(form);
        return node.executeBasic(lform, frame.getArguments());
    }
}

abstract class BasicNode extends Node {

    final int vmentry;
    final Field isCompiled;
    final int hidden_vmtarget;

    static final int INLINE_CACHE_SIZE_LIMIT = 3;

    BasicNode(Meta meta) {
        this.vmentry = meta.vmentry.getFieldIndex();
        this.isCompiled = meta.isCompiled;
        this.hidden_vmtarget = meta.HIDDEN_VMTARGET.getFieldIndex();
    }

    abstract Object executeBasic(StaticObjectImpl lform, Object[] args);

    /**
     * Cache on the lambdaForm, since we need to extract it anyway. With some luck, perhaps two
     * different MH will have the same LF.
     */
    @SuppressWarnings("unused")
    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = {"lform == cachedlform", "getBooleanField(lform, isCompiled)"})
    Object directBasic(StaticObjectImpl lform, Object[] args,
                    @Cached("lform") StaticObjectImpl cachedlform,
                    @Cached("getMethodHiddenField(getSOIField(lform, vmentry), hidden_vmtarget)") Method target,
                    @Cached("create(target.getCallTarget())") DirectCallNode callNode) {
        return callNode.call(args);
    }

    @Specialization(replaces = "directBasic")
    Object normalBasic(StaticObjectImpl lform, Object[] args,
                    @Cached("create()") IndirectCallNode callNode) {
        StaticObjectImpl mname = (StaticObjectImpl) lform.getUnsafeField(vmentry);
        Method target = (Method) mname.getUnsafeField(hidden_vmtarget);
        return callNode.call(target.getCallTarget(), args);
    }

    static StaticObjectImpl getSOIField(StaticObjectImpl object, int field) {
        return (StaticObjectImpl) object.getUnsafeField(field);
    }

    static Method getMethodHiddenField(StaticObjectImpl object, int HIDDEN_VMTARGET) {
        return (Method) object.getUnsafeField(HIDDEN_VMTARGET);
    }

    static boolean getBooleanField(StaticObjectImpl object, Field field) {
        return object.getBooleanField(field);
    }
}