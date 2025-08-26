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

import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ResolvedJavaType;

//JaCoCo Exclude

public final class HotSpotResolvedObjectTypeProxy extends HotSpotResolvedJavaTypeProxy implements HotSpotResolvedObjectType {
    HotSpotResolvedObjectTypeProxy(InvocationHandler handler) {
        super(handler);
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(HotSpotResolvedObjectType.class, name, params);
    }

    private static final SymbolicMethod getSupertypeMethod = method("getSupertype");
    private static final InvokableMethod getSupertypeInvokable = (receiver, args) -> ((HotSpotResolvedObjectType) receiver).getSupertype();

    @Override
    public HotSpotResolvedObjectType getSupertype() {
        return (HotSpotResolvedObjectType) handle(getSupertypeMethod, getSupertypeInvokable);
    }

    private static final SymbolicMethod getConstantPoolMethod = method("getConstantPool");
    private static final InvokableMethod getConstantPoolInvokable = (receiver, args) -> ((HotSpotResolvedObjectType) receiver).getConstantPool();

    @Override
    public ConstantPool getConstantPool() {
        return (ConstantPool) handle(getConstantPoolMethod, getConstantPoolInvokable);
    }

    public static final SymbolicMethod instanceSizeMethod = method("instanceSize");
    public static final InvokableMethod instanceSizeInvokable = (receiver, args) -> ((HotSpotResolvedObjectType) receiver).instanceSize();

    @Override
    public int instanceSize() {
        return (int) handle(instanceSizeMethod, instanceSizeInvokable);
    }

    private static final SymbolicMethod getVtableLengthMethod = method("getVtableLength");
    private static final InvokableMethod getVtableLengthInvokable = (receiver, args) -> ((HotSpotResolvedObjectType) receiver).getVtableLength();

    @Override
    public int getVtableLength() {
        return (int) handle(getVtableLengthMethod, getVtableLengthInvokable);
    }

    private static final SymbolicMethod isDefinitelyResolvedWithRespectToMethod = method("isDefinitelyResolvedWithRespectTo", ResolvedJavaType.class);
    private static final InvokableMethod isDefinitelyResolvedWithRespectToInvokable = (receiver,
                    args) -> ((HotSpotResolvedObjectType) receiver).isDefinitelyResolvedWithRespectTo((ResolvedJavaType) args[0]);

    @Override
    public boolean isDefinitelyResolvedWithRespectTo(ResolvedJavaType accessingClass) {
        return (boolean) handle(isDefinitelyResolvedWithRespectToMethod, isDefinitelyResolvedWithRespectToInvokable, accessingClass);
    }

    public static final SymbolicMethod klassMethod = method("klass");
    public static final InvokableMethod klassInvokable = (receiver, args) -> ((HotSpotResolvedObjectType) receiver).klass();

    @Override
    public Constant klass() {
        return (Constant) handle(klassMethod, klassInvokable);
    }

    public static final SymbolicMethod isPrimaryTypeMethod = method("isPrimaryType");
    public static final InvokableMethod isPrimaryTypeInvokable = (receiver, args) -> ((HotSpotResolvedObjectType) receiver).isPrimaryType();

    @Override
    public boolean isPrimaryType() {
        return (boolean) handle(isPrimaryTypeMethod, isPrimaryTypeInvokable);
    }

    public static final SymbolicMethod superCheckOffsetMethod = method("superCheckOffset");
    private static final InvokableMethod superCheckOffsetInvokable = (receiver, args) -> ((HotSpotResolvedObjectType) receiver).superCheckOffset();

    @Override
    public int superCheckOffset() {
        return (int) handle(superCheckOffsetMethod, superCheckOffsetInvokable);
    }

    private static final SymbolicMethod prototypeMarkWordMethod = method("prototypeMarkWord");
    private static final InvokableMethod prototypeMarkWordInvokable = (receiver, args) -> ((HotSpotResolvedObjectType) receiver).prototypeMarkWord();

    @Override
    public long prototypeMarkWord() {
        return (long) handle(prototypeMarkWordMethod, prototypeMarkWordInvokable);
    }

    private static final SymbolicMethod layoutHelperMethod = method("layoutHelper");
    private static final InvokableMethod layoutHelperInvokable = (receiver, args) -> ((HotSpotResolvedObjectType) receiver).layoutHelper();

    @Override
    public int layoutHelper() {
        return (int) handle(layoutHelperMethod, layoutHelperInvokable);
    }
}
