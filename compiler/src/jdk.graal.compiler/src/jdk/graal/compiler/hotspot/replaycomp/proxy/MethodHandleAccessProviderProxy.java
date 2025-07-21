/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.hotspot.replaycomp.proxy;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

//JaCoCo Exclude

final class MethodHandleAccessProviderProxy extends CompilationProxyBase implements MethodHandleAccessProvider {
    MethodHandleAccessProviderProxy(InvocationHandler handler) {
        super(handler);
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(MethodHandleAccessProvider.class, name, params);
    }

    private static final SymbolicMethod lookupMethodHandleIntrinsicMethod = method("lookupMethodHandleIntrinsic", ResolvedJavaMethod.class);
    private static final InvokableMethod lookupMethodHandleIntrinsicInvokable = (receiver, args) -> ((MethodHandleAccessProvider) receiver).lookupMethodHandleIntrinsic((ResolvedJavaMethod) args[0]);

    @Override
    public IntrinsicMethod lookupMethodHandleIntrinsic(ResolvedJavaMethod method) {
        return (IntrinsicMethod) handle(lookupMethodHandleIntrinsicMethod, lookupMethodHandleIntrinsicInvokable, method);
    }

    private static final SymbolicMethod resolveInvokeBasicTargetMethod = method("resolveInvokeBasicTarget", JavaConstant.class, boolean.class);
    private static final InvokableMethod resolveInvokeBasicTargetInvokable = (receiver, args) -> ((MethodHandleAccessProvider) receiver).resolveInvokeBasicTarget((JavaConstant) args[0],
                    (boolean) args[1]);

    @Override
    public ResolvedJavaMethod resolveInvokeBasicTarget(JavaConstant methodHandle, boolean forceBytecodeGeneration) {
        return (ResolvedJavaMethod) handle(resolveInvokeBasicTargetMethod, resolveInvokeBasicTargetInvokable, methodHandle, forceBytecodeGeneration);
    }

    private static final SymbolicMethod resolveLinkToTargetMethod = method("resolveLinkToTarget", JavaConstant.class);
    private static final InvokableMethod resolveLinkToTargetInvokable = (receiver, args) -> ((MethodHandleAccessProvider) receiver).resolveLinkToTarget((JavaConstant) args[0]);

    @Override
    public ResolvedJavaMethod resolveLinkToTarget(JavaConstant memberName) {
        return (ResolvedJavaMethod) handle(resolveLinkToTargetMethod, resolveLinkToTargetInvokable, memberName);
    }
}
