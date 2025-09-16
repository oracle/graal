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

import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Constant;

//JaCoCo Exclude

public final class HotSpotMetaspaceConstantProxy extends CompilationProxyBase implements HotSpotMetaspaceConstant {
    HotSpotMetaspaceConstantProxy(InvocationHandler handler) {
        super(handler);
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(HotSpotMetaspaceConstant.class, name, params);
    }

    private static final SymbolicMethod isDefaultForKindMethod = method("isDefaultForKind");
    private static final InvokableMethod isDefaultForKindInvokable = (receiver, args) -> ((HotSpotMetaspaceConstant) receiver).isDefaultForKind();

    @Override
    public boolean isDefaultForKind() {
        return (boolean) handle(isDefaultForKindMethod, isDefaultForKindInvokable);
    }

    public static final SymbolicMethod toValueStringMethod = method("toValueString");
    public static final InvokableMethod toValueStringInvokable = (receiver, args) -> ((HotSpotMetaspaceConstant) receiver).toValueString();

    @Override
    public String toValueString() {
        return (String) handle(toValueStringMethod, toValueStringInvokable);
    }

    public static final SymbolicMethod isCompressedMethod = method("isCompressed");
    public static final InvokableMethod isCompressedInvokable = (receiver, args) -> ((HotSpotMetaspaceConstant) receiver).isCompressed();

    @Override
    public boolean isCompressed() {
        return (boolean) handle(isCompressedMethod, isCompressedInvokable);
    }

    private static final SymbolicMethod isCompressibleMethod = method("isCompressible");
    private static final InvokableMethod isCompressibleInvokable = (receiver, args) -> ((HotSpotMetaspaceConstant) receiver).isCompressible();

    @Override
    public boolean isCompressible() {
        return (boolean) handle(isCompressibleMethod, isCompressibleInvokable);
    }

    public static final SymbolicMethod compressMethod = method("compress");
    private static final InvokableMethod compressInvokable = (receiver, args) -> ((HotSpotMetaspaceConstant) receiver).compress();

    @Override
    public Constant compress() {
        return (Constant) handle(compressMethod, compressInvokable);
    }

    public static final SymbolicMethod uncompressMethod = method("uncompress");
    private static final InvokableMethod uncompressInvokable = (receiver, args) -> ((HotSpotMetaspaceConstant) receiver).uncompress();

    @Override
    public Constant uncompress() {
        return (Constant) handle(uncompressMethod, uncompressInvokable);
    }

    public static final SymbolicMethod asResolvedJavaTypeMethod = method("asResolvedJavaType");
    public static final InvokableMethod asResolvedJavaTypeInvokable = (receiver, args) -> ((HotSpotMetaspaceConstant) receiver).asResolvedJavaType();

    @Override
    public HotSpotResolvedObjectType asResolvedJavaType() {
        return (HotSpotResolvedObjectType) handle(asResolvedJavaTypeMethod, asResolvedJavaTypeInvokable);
    }

    public static final SymbolicMethod asResolvedJavaMethodMethod = method("asResolvedJavaMethod");
    public static final InvokableMethod asResolvedJavaMethodInvokable = (receiver, args) -> ((HotSpotMetaspaceConstant) receiver).asResolvedJavaMethod();

    @Override
    public HotSpotResolvedJavaMethod asResolvedJavaMethod() {
        return (HotSpotResolvedJavaMethod) handle(asResolvedJavaMethodMethod, asResolvedJavaMethodInvokable);
    }
}
