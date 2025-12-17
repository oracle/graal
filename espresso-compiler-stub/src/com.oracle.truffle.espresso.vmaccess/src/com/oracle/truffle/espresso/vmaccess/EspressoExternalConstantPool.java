/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.vmaccess;

import static com.oracle.truffle.espresso.vmaccess.EspressoExternalConstantReflectionProvider.asObjectConstant;
import static com.oracle.truffle.espresso.vmaccess.EspressoExternalVMAccess.throwHostException;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoConstantPool;
import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedInstanceType;
import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedJavaField;
import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedJavaMethod;
import com.oracle.truffle.espresso.jvmci.meta.EspressoBootstrapMethodInvocation;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;

final class EspressoExternalConstantPool extends AbstractEspressoConstantPool {
    private final EspressoExternalResolvedInstanceType holder;
    private final Value cpMirror;

    EspressoExternalConstantPool(EspressoExternalResolvedInstanceType holder) {
        this.holder = holder;
        this.cpMirror = holder.getAccess().invokeJVMCIHelper("getConstantPool", holder.getMetaObject());
    }

    @Override
    protected boolean loadReferencedType0(int cpi, int opcode) {
        try {
            return cpMirror.invokeMember("loadReferencedType", cpi, opcode).asBoolean();
        } catch (PolyglotException e) {
            throw throwHostException(e);
        }
    }

    @Override
    protected AbstractEspressoResolvedJavaField lookupResolvedField(int cpi, AbstractEspressoResolvedJavaMethod method, int opcode) {
        Value methodMirror = null;
        if (method != null) {
            methodMirror = ((EspressoExternalResolvedJavaMethod) method).getMirror();
        }
        Value resolved = cpMirror.invokeMember("lookupResolvedField", cpi, opcode, methodMirror);
        if (resolved.isNull()) {
            return null;
        }
        return new EspressoExternalResolvedJavaField(holder, resolved);
    }

    @Override
    protected ResolvedJavaType getMethodHandleType() {
        return (ResolvedJavaType) holder.getAccess().lookupType("Ljava/lang/invoke/MethodHandle;", holder.getAccess().getJavaLangObject(), true);
    }

    @Override
    protected JavaType lookupFieldType(int cpi, AbstractEspressoResolvedInstanceType accessingType) {
        String typeDescriptor = lookupDescriptor(cpi);
        return holder.getAccess().lookupType(typeDescriptor, accessingType, false);
    }

    @Override
    protected String lookupDescriptor(int cpi) {
        try {
            return cpMirror.invokeMember("lookupDescriptor", cpi).asString();
        } catch (PolyglotException e) {
            throw throwHostException(e);
        }
    }

    @Override
    protected String lookupName(int cpi) {
        return cpMirror.invokeMember("lookupName", cpi).asString();
    }

    @Override
    protected AbstractEspressoResolvedJavaMethod lookupResolvedMethod(int cpi, int opcode, AbstractEspressoResolvedJavaMethod caller) {
        Value callerMirror = null;
        if (caller != null) {
            callerMirror = ((EspressoExternalResolvedJavaMethod) caller).getMirror();
        }
        Value resolved = cpMirror.invokeMember("lookupResolvedMethod", cpi, opcode, callerMirror);
        if (resolved.isNull()) {
            return null;
        }
        EspressoExternalResolvedInstanceType methodHolder;
        Value methodHolderMeta = resolved.getMember("holder");
        if (methodHolderMeta.equals(holder.getMetaObject())) {
            methodHolder = holder;
        } else {
            methodHolder = new EspressoExternalResolvedInstanceType(holder.getAccess(), methodHolderMeta);
        }
        return new EspressoExternalResolvedJavaMethod(methodHolder, resolved);
    }

    @Override
    protected byte getTagByteAt(int cpi) {
        try {
            return cpMirror.invokeMember("getTagByteAt", cpi).asByte();
        } catch (PolyglotException e) {
            throw throwHostException(e);
        }
    }

    @Override
    public int length() {
        return cpMirror.getMember("length").asInt();
    }

    @Override
    public JavaType lookupReferencedType(int rawIndex, int opcode) {
        try {
            Value result = cpMirror.invokeMember("lookupReferencedType", rawIndex, opcode);
            return holder.getAccess().toJavaType(result);
        } catch (PolyglotException e) {
            throw throwHostException(e);
        }
    }

    @Override
    public JavaType lookupType(int cpi, int opcode) {
        try {
            Value result = cpMirror.invokeMember("lookupType", cpi, opcode);
            return holder.getAccess().toJavaType(result);
        } catch (PolyglotException e) {
            throw throwHostException(e);
        }
    }

    @Override
    public String lookupUtf8(int cpi) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public Object lookupConstant(int cpi, boolean resolve) {
        try {
            return switch (getTagByteAt(cpi)) {
                case CONSTANT_Integer -> JavaConstant.forInt(cpMirror.invokeMember("lookupConstant", cpi).asInt());
                case CONSTANT_Long -> JavaConstant.forLong(cpMirror.invokeMember("lookupConstant", cpi).asLong());
                case CONSTANT_Float -> JavaConstant.forFloat(cpMirror.invokeMember("lookupConstant", cpi).asFloat());
                case CONSTANT_Double -> JavaConstant.forDouble(cpMirror.invokeMember("lookupConstant", cpi).asDouble());
                case CONSTANT_Class -> lookupType(cpi, 0);
                case CONSTANT_String, CONSTANT_MethodHandle, CONSTANT_MethodType -> new EspressoExternalObjectConstant(holder.getAccess(), cpMirror.invokeMember("lookupConstant", cpi));
                case CONSTANT_Dynamic -> switch (cpMirror.invokeMember("lookupDynamicKind", cpi).asInt()) {
                    case 'Z' -> JavaConstant.forBoolean(cpMirror.invokeMember("lookupConstant", cpi).asBoolean());
                    case 'B' -> JavaConstant.forByte(cpMirror.invokeMember("lookupConstant", cpi).asByte());
                    case 'C' -> JavaConstant.forChar((char) cpMirror.invokeMember("lookupConstant", cpi).asInt());
                    case 'S' -> JavaConstant.forShort(cpMirror.invokeMember("lookupConstant", cpi).asShort());
                    case 'I' -> JavaConstant.forInt(cpMirror.invokeMember("lookupConstant", cpi).asInt());
                    case 'J' -> JavaConstant.forLong(cpMirror.invokeMember("lookupConstant", cpi).asLong());
                    case 'F' -> JavaConstant.forFloat(cpMirror.invokeMember("lookupConstant", cpi).asFloat());
                    case 'D' -> JavaConstant.forDouble(cpMirror.invokeMember("lookupConstant", cpi).asDouble());
                    case 'L' -> new EspressoExternalObjectConstant(holder.getAccess(), cpMirror.invokeMember("lookupConstant", cpi));
                    default -> throw JVMCIError.shouldNotReachHere(cpMirror.invokeMember("lookupDynamicKind", cpi).toString());
                };
                default -> throw new IllegalArgumentException("Unsupported tag: " + getTagByteAt(cpi) + " (" + getTagByteAt(cpi) + ")");
            };
        } catch (PolyglotException e) {
            throw throwHostException(e);
        }
    }

    @Override
    public JavaConstant lookupAppendix(int rawIndex, int opcode) {
        Value value;
        try {
            value = cpMirror.invokeMember("lookupAppendix", rawIndex, opcode);
        } catch (PolyglotException e) {
            throw throwHostException(e);
        }
        return asObjectConstant(value, holder.getAccess());
    }

    @Override
    protected int getNumIndyEntries() {
        return cpMirror.getMember("numIndyEntries").asInt();
    }

    @Override
    protected EspressoBootstrapMethodInvocation lookupIndyBootstrapMethodInvocation(int siteIndex) {
        Value value;
        try {
            value = cpMirror.invokeMember("lookupIndyBootstrapMethodInvocation", siteIndex);
        } catch (PolyglotException e) {
            throw throwHostException(e);
        }
        assert !value.isNull();
        throw JVMCIError.unimplemented();
    }

    @Override
    public BootstrapMethodInvocation lookupBootstrapMethodInvocation(int index, int opcode) {
        Value value;
        try {
            value = cpMirror.invokeMember("lookupBootstrapMethodInvocation", index, opcode);
        } catch (PolyglotException e) {
            throw throwHostException(e);
        }
        if (value.isNull()) {
            return null;
        }
        throw JVMCIError.unimplemented();
    }

    @Override
    protected EspressoExternalSignature getSignature(String rawSignature) {
        return new EspressoExternalSignature(holder.getAccess(), rawSignature);
    }
}
