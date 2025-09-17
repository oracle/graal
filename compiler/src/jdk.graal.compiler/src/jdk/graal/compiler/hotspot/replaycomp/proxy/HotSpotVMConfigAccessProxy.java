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

import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.equalsInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.equalsMethod;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.hashCodeInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.hashCodeMethod;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.toStringInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.toStringMethod;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.unproxifyInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.unproxifyMethod;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.hotspot.VMField;

//JaCoCo Exclude

final class HotSpotVMConfigAccessProxy extends HotSpotVMConfigAccess implements CompilationProxy {
    private final InvocationHandler handler;

    HotSpotVMConfigAccessProxy(InvocationHandler handler) {
        super(null);
        this.handler = handler;
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(HotSpotVMConfigAccess.class, name, params);
    }

    private Object handle(SymbolicMethod method, InvokableMethod invokable, Object... args) {
        return CompilationProxy.handle(handler, this, method, invokable, args);
    }

    @Override
    public HotSpotVMConfigStore getStore() {
        // Config store serialization is not implemented.
        return HotSpotJVMCIRuntime.runtime().getConfigStore();
    }

    private static final SymbolicMethod getAddressMethod = method("getAddress", String.class, Long.class);
    private static final InvokableMethod getAddressInvokable = (receiver, args) -> ((HotSpotVMConfigAccess) receiver).getAddress((String) args[0], (Long) args[1]);

    @Override
    public long getAddress(String name, Long notPresent) {
        return (long) handle(getAddressMethod, getAddressInvokable, name, notPresent);
    }

    private static final SymbolicMethod getConstantMethod = method("getConstant", String.class, Class.class, Object.class);
    @SuppressWarnings("unchecked") private static final InvokableMethod getConstantInvokable = (receiver, args) -> ((HotSpotVMConfigAccess) receiver).getConstant((String) args[0],
                    (Class<Object>) args[1], args[2]);

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConstant(String name, Class<T> type, T notPresent) {
        return (T) handle(getConstantMethod, getConstantInvokable, name, type, notPresent);
    }

    private static final SymbolicMethod getFieldMethod = method("getField", String.class, String.class, boolean.class);
    private static final InvokableMethod getFieldInvokable = (receiver, args) -> ((HotSpotVMConfigAccess) receiver).getField((String) args[0], (String) args[1], (boolean) args[2]);

    @Override
    public VMField getField(String name, String cppType, boolean required) {
        return (VMField) handle(getFieldMethod, getFieldInvokable, name, cppType, required);
    }

    private static final SymbolicMethod getFlagMethod = method("getFlag", String.class, Class.class, Object.class);
    @SuppressWarnings("unchecked") private static final InvokableMethod getFlagInvokable = (receiver, args) -> ((HotSpotVMConfigAccess) receiver).getFlag((String) args[0], (Class<Object>) args[1],
                    args[2]);

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getFlag(String name, Class<T> type, T notPresent) {
        return (T) handle(getFlagMethod, getFlagInvokable, name, type, notPresent);
    }

    @Override
    public Object unproxify() {
        return handle(unproxifyMethod, unproxifyInvokable);
    }

    @Override
    public int hashCode() {
        return (int) handle(hashCodeMethod, hashCodeInvokable);
    }

    @Override
    public boolean equals(Object obj) {
        return (boolean) handle(equalsMethod, equalsInvokable, obj);
    }

    @Override
    public String toString() {
        return (String) handle(toStringMethod, toStringInvokable);
    }
}
