/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.vmaccess.EspressoExternalVMAccess.throwHostException;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoConstantPool;
import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedInstanceType;
import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedJavaField;
import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedJavaMethod;
import com.oracle.truffle.espresso.jvmci.meta.EspressoBootstrapMethodInvocation;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
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
    public int lookupMethodHandleReferenceKind(int cpi) {
        try {
            return cpMirror.invokeMember("lookupMethodHandleReferenceKind", cpi).asInt();
        } catch (PolyglotException e) {
            throw throwHostException(e);
        }
    }

    @Override
    public int lookupMethodHandleReferenceIndex(int cpi) {
        try {
            return cpMirror.invokeMember("lookupMethodHandleReferenceIndex", cpi).asInt();
        } catch (PolyglotException e) {
            throw throwHostException(e);
        }
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
        if (!(method instanceof EspressoExternalResolvedJavaMethod externalMethod)) {
            throw new IllegalArgumentException("Expected an external Espresso method");
        }
        Value methodMirror = externalMethod.getMirror();
        Value resolved;
        try {
            resolved = cpMirror.invokeMember("lookupResolvedField", cpi, opcode, methodMirror);
        } catch (PolyglotException e) {
            throw throwHostException(e);
        }
        if (resolved.isNull()) {
            return null;
        }
        Value fieldHolderMeta = resolved.getMember("holder");
        EspressoExternalResolvedInstanceType fieldHolder = new EspressoExternalResolvedInstanceType(holder.getAccess(), fieldHolderMeta);
        return new EspressoExternalResolvedJavaField(fieldHolder, resolved, null);
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
        try {
            return cpMirror.invokeMember("lookupName", cpi).asString();
        } catch (PolyglotException e) {
            throw throwHostException(e);
        }
    }

    @Override
    protected AbstractEspressoResolvedJavaMethod lookupResolvedMethod(int cpi, int opcode, AbstractEspressoResolvedJavaMethod caller) {
        if (caller != null && !(caller instanceof EspressoExternalResolvedJavaMethod)) {
            throw new IllegalArgumentException("Expected an external Espresso caller");
        }
        Value callerMirror = null;
        if (caller != null) {
            callerMirror = ((EspressoExternalResolvedJavaMethod) caller).getMirror();
        }
        Value resolved;
        try {
            resolved = cpMirror.invokeMember("lookupResolvedMethod", cpi, opcode, callerMirror);
        } catch (PolyglotException e) {
            throw throwHostException(e);
        }
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
        return new EspressoExternalResolvedJavaMethod(methodHolder, resolved, null);
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
        try {
            return cpMirror.invokeMember("lookupUtf8", cpi).asString();
        } catch (PolyglotException e) {
            throw throwHostException(e);
        }
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
                case CONSTANT_String -> new EspressoExternalObjectConstant(holder.getAccess(), cpMirror.invokeMember("lookupConstant", cpi, true));
                case CONSTANT_MethodHandle, CONSTANT_MethodType -> {
                    Value constant = cpMirror.invokeMember("lookupConstant", cpi, resolve);
                    if (constant.isNull()) {
                        yield null;
                    } else {
                        yield new EspressoExternalObjectConstant(holder.getAccess(), constant);
                    }
                }
                case CONSTANT_Dynamic -> {
                    Value constant = cpMirror.invokeMember("lookupConstant", cpi, resolve);
                    char kindChar = (char) cpMirror.invokeMember("lookupResolvedDynamicKind", cpi).asInt();
                    JavaKind kind;
                    if (kindChar == 'A') {
                        kind = JavaKind.Object;
                    } else if (kindChar == '-') {
                        // this marks an unresolved entry
                        yield null;
                    } else {
                        kind = JavaKind.fromPrimitiveOrVoidTypeChar(kindChar);
                    }
                    yield EspressoExternalConstantReflectionProvider.asJavaConstant(constant, kind, holder.getAccess());
                }
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
        if (value.isNull()) {
            return null;
        }
        return new EspressoExternalObjectConstant(holder.getAccess(), value);
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
        return asEspressoBootstrapMethodInvocation(value);
    }

    private EspressoBootstrapMethodInvocation asEspressoBootstrapMethodInvocation(Value value) {
        boolean isIndy = value.getMember("isIndy").asBoolean();
        int cpi = value.getMember("cpi").asInt();
        Value methodMirror = value.getMember("bootstrapMethod");
        EspressoExternalVMAccess access = holder.getAccess();
        EspressoExternalResolvedJavaMethod method = methodMirror.isNull() ? null : new EspressoExternalResolvedJavaMethod(methodMirror, access);
        String name = value.getMember("name").asString();
        Value typeMirror = value.getMember("type");
        EspressoExternalObjectConstant type = typeMirror.isNull() ? null : new EspressoExternalObjectConstant(access, typeMirror);
        Value argumentIndices = value.getMember("staticArgumentIndices");
        int[] staticArgumentIndices = new int[(int) argumentIndices.getArraySize()];
        for (int i = 0; i < staticArgumentIndices.length; i++) {
            staticArgumentIndices[i] = argumentIndices.getArrayElement(i).asInt();
        }
        boolean hasResolvedArguments = value.hasArrayElements();
        int staticArgCount = hasResolvedArguments ? (int) value.getArraySize() / 2 : 0;
        JavaConstant[] staticArguments = hasResolvedArguments ? new JavaConstant[staticArgCount] : null;
        for (int i = 0; i < staticArgCount; i++) {
            Value maybeEntryCPI = value.getArrayElement(2 * i + 1);
            JavaConstant staticArgument;
            if (maybeEntryCPI.isNull()) {
                staticArgument = new EspressoExternalObjectConstant(access, value.getArrayElement(2 * i));
            } else {
                // unresolved dynamic entry
                int entryCpi = maybeEntryCPI.asInt();
                staticArgument = JavaConstant.forInt(entryCpi);
            }
            staticArguments[i] = staticArgument;
        }
        int methodHandleIndex = value.getMember("methodHandleIndex").asInt();
        return new EspressoBootstrapMethodInvocation(isIndy, method, name, type, staticArguments, staticArgumentIndices, cpi, methodHandleIndex, this);
    }

    @Override
    public BootstrapMethodInvocation lookupBootstrapMethodInvocation(int index, int opcode, boolean resolve) {
        Value value;
        try {
            value = cpMirror.invokeMember("lookupBootstrapMethodInvocation", index, opcode, resolve);
        } catch (PolyglotException e) {
            throw throwHostException(e);
        }
        if (value.isNull()) {
            return null;
        }
        return asEspressoBootstrapMethodInvocation(value);
    }

    @Override
    protected EspressoExternalSignature getSignature(String rawSignature) {
        return new EspressoExternalSignature(holder.getAccess(), rawSignature);
    }
}
