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

import java.util.List;

import org.graalvm.polyglot.Value;

import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedArrayType;
import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedInstanceType;
import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedJavaField;
import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedJavaMethod;
import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedJavaRecordComponent;
import com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedObjectType;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

final class EspressoExternalResolvedInstanceType extends AbstractEspressoResolvedInstanceType {
    private final EspressoExternalVMAccess access;
    /**
     * A handle to an espresso Klass.
     */
    private final Value metaObject;
    private final int flags;
    private EspressoExternalConstantPool constantPool;

    EspressoExternalResolvedInstanceType(EspressoExternalVMAccess access, Value metaObject) {
        assert metaObject.isMetaObject();
        this.metaObject = metaObject;
        this.flags = access.invokeJVMCIHelper("getFlags", metaObject).asInt();
        this.access = access;
    }

    EspressoExternalVMAccess getAccess() {
        return access;
    }

    Value getMetaObject() {
        return metaObject;
    }

    @Override
    protected int getFlags() {
        return flags;
    }

    @Override
    protected EspressoExternalResolvedInstanceType[] getArrayInterfaces() {
        return access.getArrayInterfaces();
    }

    @Override
    protected boolean isAssignableFrom(AbstractEspressoResolvedInstanceType other) {
        return access.invokeJVMCIHelper("isAssignableFrom", this.getMetaObject(), ((EspressoExternalResolvedInstanceType) other).getMetaObject()).asBoolean();
    }

    @Override
    protected EspressoExternalResolvedInstanceType getJavaLangObject() {
        return access.getJavaLangObject();
    }

    @Override
    protected EspressoExternalResolvedInstanceType getSuperclass0() {
        Value value = metaObject.getMember("super");
        assert value != null : this;
        return new EspressoExternalResolvedInstanceType(access, value);
    }

    @Override
    protected EspressoExternalResolvedInstanceType[] getInterfaces0() {
        Value value = access.invokeJVMCIHelper("getInterfaces", getMetaObject());
        return translateInstanceTypeArray(value);
    }

    @Override
    protected AbstractEspressoResolvedJavaRecordComponent[] getRecordComponents0() {
        throw JVMCIError.unimplemented();
    }

    private EspressoExternalResolvedInstanceType[] translateInstanceTypeArray(Value value) {
        if (value.isNull()) {
            return null;
        }
        int size = Math.toIntExact(value.getArraySize());
        EspressoExternalResolvedInstanceType[] result = new EspressoExternalResolvedInstanceType[size];
        for (int i = 0; i < size; i++) {
            result[i] = new EspressoExternalResolvedInstanceType(access, value.getArrayElement(i));
        }
        return result;
    }

    @Override
    protected EspressoExternalResolvedInstanceType espressoSingleImplementor() {
        Value result = access.invokeJVMCIHelper("espressoSingleImplementor", getMetaObject());
        if (result.isNull()) {
            return null;
        }
        return new EspressoExternalResolvedInstanceType(access, result);
    }

    @Override
    protected boolean isLeafClass() {
        return access.invokeJVMCIHelper("isLeafClass", getMetaObject()).asBoolean();
    }

    @Override
    protected String getName0() {
        return access.invokeJVMCIHelper("getName", getMetaObject()).asString();
    }

    @Override
    protected boolean hasSameClassLoader(AbstractEspressoResolvedInstanceType otherMirror) {
        return access.invokeJVMCIHelper("hasSameClassLoader", getMetaObject(), ((EspressoExternalResolvedInstanceType) otherMirror).getMetaObject()).asBoolean();
    }

    @Override
    protected EspressoExternalResolvedJavaMethod resolveMethod0(AbstractEspressoResolvedJavaMethod method, AbstractEspressoResolvedInstanceType callerType) {
        throw JVMCIError.unimplemented();
    }

    @Override
    protected AbstractEspressoResolvedJavaField[] getStaticFields0() {
        Value value = access.invokeJVMCIHelper("getStaticFields", metaObject);
        return translateFieldArray(value);
    }

    @Override
    protected AbstractEspressoResolvedJavaField[] getInstanceFields0() {
        Value value = access.invokeJVMCIHelper("getInstanceFields", metaObject);
        return translateFieldArray(value);
    }

    private EspressoExternalResolvedJavaField[] translateFieldArray(Value value) {
        assert value.hasArrayElements();
        int size = Math.toIntExact(value.getArraySize());
        EspressoExternalResolvedJavaField[] result = new EspressoExternalResolvedJavaField[size];
        for (int i = 0; i < size; i++) {
            Value fieldMirror = value.getArrayElement(i);
            result[i] = new EspressoExternalResolvedJavaField(this, fieldMirror);
        }
        return result;
    }

    @Override
    protected EspressoExternalResolvedJavaMethod[] getDeclaredConstructors0() {
        Value value = access.invokeJVMCIHelper("getDeclaredConstructors", getMetaObject());
        return translateDeclaredMethodArray(value);
    }

    @Override
    protected EspressoExternalResolvedJavaMethod[] getDeclaredMethods0() {
        Value value = access.invokeJVMCIHelper("getDeclaredMethods", getMetaObject());
        return translateDeclaredMethodArray(value);
    }

    @Override
    protected EspressoExternalResolvedJavaMethod[] getAllMethods0() {
        Value value = access.invokeJVMCIHelper("getAllMethods", getMetaObject());
        return translateMethodArray(value);
    }

    private EspressoExternalResolvedJavaMethod[] translateDeclaredMethodArray(Value value) {
        if (value.isNull()) {
            return EspressoExternalResolvedJavaMethod.EMPTY_ARRAY;
        }
        assert value.hasArrayElements();
        int size = Math.toIntExact(value.getArraySize());
        EspressoExternalResolvedJavaMethod[] result = new EspressoExternalResolvedJavaMethod[size];
        for (int i = 0; i < size; i++) {
            assert value.getArrayElement(i).getMember("holder").equals(getMetaObject());
            result[i] = new EspressoExternalResolvedJavaMethod(this, value.getArrayElement(i));
        }
        return result;
    }

    private EspressoExternalResolvedJavaMethod[] translateMethodArray(Value value) {
        if (value.isNull()) {
            return EspressoExternalResolvedJavaMethod.EMPTY_ARRAY;
        }
        assert value.hasArrayElements();
        int size = Math.toIntExact(value.getArraySize());
        EspressoExternalResolvedJavaMethod[] result = new EspressoExternalResolvedJavaMethod[size];
        for (int i = 0; i < size; i++) {
            Value methodMeta = value.getArrayElement(i);
            EspressoExternalResolvedInstanceType methodHolder;
            Value methodHolderMeta = methodMeta.invokeMember("holder");
            if (metaObject.equals(methodHolderMeta)) {
                methodHolder = this;
            } else {
                methodHolder = new EspressoExternalResolvedInstanceType(getAccess(), methodHolderMeta);
            }
            result[i] = new EspressoExternalResolvedJavaMethod(methodHolder, methodMeta);
        }
        return result;
    }

    @Override
    protected JavaType lookupType(String typeName, AbstractEspressoResolvedInstanceType accessingType, boolean resolve) {
        return access.lookupType(typeName, accessingType, resolve);
    }

    @Override
    public EspressoExternalConstantPool getConstantPool() {
        if (constantPool == null) {
            constantPool = new EspressoExternalConstantPool(this);
        }
        return constantPool;
    }

    @Override
    protected boolean equals0(AbstractEspressoResolvedInstanceType that) {
        if (that instanceof EspressoExternalResolvedInstanceType thatInstanceType) {
            return metaObject.equals(thatInstanceType.metaObject);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return metaObject.hashCode();
    }

    @Override
    protected Class<?> getMirror0() {
        throw JVMCIError.shouldNotReachHere("Mirrors cannot be accessed for external JVMCI");
    }

    @Override
    protected AbstractEspressoResolvedArrayType getArrayClass0() {
        return new EspressoExternalResolvedArrayType(this, 1, this, access);
    }

    @Override
    public boolean isInitialized() {
        return access.invokeJVMCIHelper("isInitialized", getMetaObject()).asBoolean();
    }

    @Override
    public void initialize() {
        access.invokeJVMCIHelper("initialize", metaObject);
    }

    @Override
    public boolean isLinked() {
        return access.invokeJVMCIHelper("isLinked", getMetaObject()).asBoolean();
    }

    @Override
    public void link() {
        access.invokeJVMCIHelper("link", metaObject);
    }

    @Override
    public boolean declaresDefaultMethods() {
        return access.invokeJVMCIHelper("declaresDefaultMethods", getMetaObject()).asBoolean();
    }

    @Override
    public boolean isHidden() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public List<? extends JavaType> getPermittedSubclasses() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public boolean isRecord() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public boolean hasDefaultMethods() {
        return access.invokeJVMCIHelper("hasDefaultMethods", getMetaObject()).asBoolean();
    }

    @Override
    public String getSourceFileName() {
        return access.invokeJVMCIHelper("getSourceFileName", metaObject).asString();
    }

    @Override
    public boolean isLocal() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public boolean isMember() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public ResolvedJavaType[] getDeclaredTypes() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public ResolvedJavaType getEnclosingType() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public ResolvedJavaMethod getEnclosingMethod() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public ResolvedJavaMethod getClassInitializer() {
        Value value = access.invokeJVMCIHelper("getClassInitializer", getMetaObject());
        if (value.isNull()) {
            return null;
        }
        return new EspressoExternalResolvedJavaMethod(this, value);
    }

    @Override
    protected byte[] getRawAnnotationBytes(int category) {
        throw JVMCIError.unimplemented();
    }

    @Override
    protected int getVtableLength() {
        throw JVMCIError.unimplemented();
    }

    @Override
    protected EspressoResolvedObjectType getObjectType(JavaConstant obj) {
        return ((EspressoExternalObjectConstant) obj).getType();
    }
}
