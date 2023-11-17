/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.methodhandle;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * This method is usually responsible for invoking the type-checking lambda forms. As such, its job
 * it to extract the method handle (given as receiver), and find a way to the payload.
 */
public abstract class MHInvokeBasicNode extends MethodHandleIntrinsicNode {

    private final Field form;
    private final Field vmentry;
    private final Field hiddenVmtarget;

    static final int INLINE_CACHE_SIZE_LIMIT = 5;

    protected abstract Object executeCall(Object[] args, Method target);

    public static boolean canInline(Method target, Method cachedTarget) {
        return target.identity() == cachedTarget.identity();
    }

    private void sanitizeSignature(Method payload) {
        if (payload.getArgumentCount() != getMethod().getArgumentCount()) {
            getMeta().throwException(getMeta().java_lang_UnsupportedOperationException);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = {"inliningEnabled()", "canInline(target, cachedTarget)"})
    Object doCallDirect(Object[] args, Method target,
                    @Cached("target") Method cachedTarget,
                    @Cached("create(target.getCallTarget())") DirectCallNode directCallNode) {
        sanitizeSignature(cachedTarget);
        return directCallNode.call(args);
    }

    @Specialization(replaces = "doCallDirect")
    Object doCallIndirect(Object[] args, Method target,
                    @Cached("create()") IndirectCallNode callNode) {
        sanitizeSignature(target);
        return callNode.call(target.getCallTarget(), args);
    }

    public MHInvokeBasicNode(Method method) {
        super(method);
        Meta meta = method.getMeta();
        this.form = meta.java_lang_invoke_MethodHandle_form;
        this.vmentry = meta.java_lang_invoke_LambdaForm_vmentry;
        this.hiddenVmtarget = meta.HIDDEN_VMTARGET;
    }

    @Override
    public Object call(Object[] args) {
        StaticObject mh = (StaticObject) args[0];
        StaticObject lform = form.getObject(mh);
        StaticObject mname = vmentry.getObject(lform);
        Method target = (Method) hiddenVmtarget.getHiddenObject(mname);
        return executeCall(args, target);
    }
}
