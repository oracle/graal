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

public final class MHInvokeBasicNode extends EspressoBaseNode {

    private final int form;
    private final int vmentry;
    private final int hidden_vmtarget;
    private @Child IndirectCallNode callNode;

    public MHInvokeBasicNode(Method method) {
        super(method);
        Meta meta = getMeta();
        this.form = meta.form.getFieldIndex();
        this.vmentry = meta.vmentry.getFieldIndex();
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
    }
}