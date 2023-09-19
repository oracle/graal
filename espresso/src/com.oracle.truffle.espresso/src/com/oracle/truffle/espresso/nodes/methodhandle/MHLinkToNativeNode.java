/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.runtime.panama.DowncallStubNode;
import com.oracle.truffle.espresso.runtime.panama.DowncallStubs;

public abstract class MHLinkToNativeNode extends MethodHandleIntrinsicNode {
    protected static final int LIMIT = 3;
    private final int argCount;
    private final Field downcallStubAddress;

    protected MHLinkToNativeNode(Method method, Field downcallStubAddress) {
        super(method);
        this.downcallStubAddress = downcallStubAddress;
        this.argCount = Signatures.parameterCount(method.getParsedSignature());
        assert argCount >= 1;
    }

    public static MHLinkToNativeNode create(Method method, Meta meta) {
        return MHLinkToNativeNodeGen.create(method, meta.jdk_internal_foreign_abi_NativeEntryPoint_downcallStubAddress);
    }

    @Override
    public Object call(Object[] args) {
        return execute(args);
    }

    protected abstract Object execute(Object[] args);

    @Specialization(guards = "downcallStubId == getDowncallStubId(args)", limit = "LIMIT")
    protected Object doCached(Object[] args,
                    @Cached("getDowncallStubId(args)") @SuppressWarnings("unused") long downcallStubId,
                    @Cached("createDowncallStubNode(downcallStubId)") DowncallStubNode node) {
        assert args.length == argCount;
        return node.call(args);
    }

    @Specialization
    protected Object doUncached(Object[] args) {
        assert args.length == argCount;
        long downcallStubId = getDowncallStubId(args);
        EspressoContext context = getContext();
        DowncallStubs.DowncallStub stub = context.getDowncallStubs().getStub(downcallStubId);
        return stub.uncachedCall(args, context);
    }

    protected DowncallStubNode createDowncallStubNode(long downcallStubId) {
        EspressoContext context = getContext();
        DowncallStubs.DowncallStub stub = context.getDowncallStubs().getStub(downcallStubId);
        return DowncallStubNode.create(stub, context.getNativeAccess());
    }

    protected long getDowncallStubId(Object[] args) {
        StaticObject nativeEntryPoint = (StaticObject) args[argCount - 1];
        return this.downcallStubAddress.getLong(nativeEntryPoint);
    }
}
