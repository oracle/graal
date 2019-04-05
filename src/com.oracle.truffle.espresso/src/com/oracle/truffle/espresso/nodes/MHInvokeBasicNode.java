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
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

import static com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives.VMTARGET;

public class MHInvokeBasicNode extends EspressoBaseNode {

    @Child BasicNode node;

    public MHInvokeBasicNode(Method method) {
        super(method);
        this.node = BasicNodeGen.create(getMeta());
    }

    @Override
    public Object invokeNaked(VirtualFrame frame) {
        StaticObjectImpl mh = (StaticObjectImpl) frame.getArguments()[0];
        return node.executeBasic(mh, frame.getArguments());
    }
}

abstract class BasicNode extends Node {
    final static String vmtarget = VMTARGET;
    final int form;
    final int vmentry;
    final int isCompiled;

    static final int INLINE_CACHE_SIZE_LIMIT = 3;

    BasicNode(Meta meta) {
        this.form = meta.form.getFieldIndex();
        this.vmentry = meta.vmentry.getFieldIndex();
        this.isCompiled = meta.isCompiled.getFieldIndex();
    }

    abstract Object executeBasic(StaticObjectImpl methodHandle, Object[] args);

    @SuppressWarnings("unused")
    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = {"methodHandle == cachedHandle", "getBooleanField(lform, isCompiled)"})
    Object directBasic(StaticObjectImpl methodHandle, Object[] args,
                    @Cached("methodHandle") StaticObjectImpl cachedHandle,
                    @Cached("getSOIField(methodHandle, form)") StaticObjectImpl lform,
                    @Cached("getMethodHiddenField(getSOIField(lform, vmentry))") Method target,
                    @Cached("create(target.getCallTarget())") DirectCallNode callNode) {
        return callNode.call(args);
    }

    @Specialization(replaces = "directBasic")
    Object normalBasic(StaticObjectImpl methodHandle, Object[] args,
                    @Cached("create()") IndirectCallNode callNode) {
        StaticObjectImpl lform = (StaticObjectImpl) methodHandle.getUnsafeField(form);
        StaticObjectImpl mname = (StaticObjectImpl) lform.getUnsafeField(vmentry);
        Method target = (Method) mname.getCommonHiddenField();
        assert target == mname.getHiddenField(VMTARGET);
        return callNode.call(target.getCallTarget(), args);
    }

    static StaticObjectImpl getSOIField(StaticObjectImpl object, int field) {
        return (StaticObjectImpl) object.getUnsafeField(field);
    }

    static Method getMethodHiddenField(StaticObjectImpl object) {
        return (Method) object.getCommonHiddenField();
    }

    static boolean getBooleanField(StaticObjectImpl object, int field) {
        return object.getUnsafeWordField(field) != 0;
    }
}