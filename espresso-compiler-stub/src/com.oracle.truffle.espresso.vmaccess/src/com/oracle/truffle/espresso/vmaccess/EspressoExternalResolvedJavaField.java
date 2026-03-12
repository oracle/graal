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

import org.graalvm.polyglot.Value;

import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedJavaField;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

final class EspressoExternalResolvedJavaField extends AbstractEspressoResolvedJavaField implements EspressoExternalVMAccess.Element {
    /**
     * The guest {@code com.oracle.truffle.espresso.impl.Field} value associated with this field.
     */
    private final Value vmFieldMirror;

    /**
     * Value of {@code com.oracle.truffle.espresso.classfile.ParserField.flags}.
     */
    private final int flags;

    /**
     * A guest {@link java.lang.reflect.Field} value associated with this field.
     */
    private Value reflectFieldMirror;

    EspressoExternalResolvedJavaField(EspressoExternalResolvedInstanceType holder, Value vmFieldMirror, Value reflectFieldMirror) {
        super(holder);
        this.vmFieldMirror = vmFieldMirror;
        this.reflectFieldMirror = reflectFieldMirror;
        this.flags = vmFieldMirror.getMember("flags").asInt();
    }

    private EspressoExternalVMAccess getAccess() {
        return ((EspressoExternalResolvedInstanceType) getDeclaringClass()).getAccess();
    }

    Value getMirror() {
        return vmFieldMirror;
    }

    @Override
    protected int getFlags() {
        return flags;
    }

    @Override
    public int getOffset() {
        return vmFieldMirror.getMember("offset").asInt();
    }

    @Override
    protected String getName0() {
        return vmFieldMirror.getMember("name").asString();
    }

    @Override
    protected JavaType getType0(UnresolvedJavaType unresolved) {
        String name = vmFieldMirror.getMember("type").asString();
        return getAccess().lookupType(name, getDeclaringClass(), false);
    }

    @Override
    protected int getConstantValueIndex() {
        return vmFieldMirror.getMember("constantValueIndex").asInt();
    }

    @Override
    protected byte[] getRawAnnotationBytes(int category) {
        return getAccess().getRawAnnotationBytes(vmFieldMirror, category);
    }

    @Override
    protected boolean equals0(AbstractEspressoResolvedJavaField that) {
        if (that instanceof EspressoExternalResolvedJavaField espressoField) {
            return vmFieldMirror.equals(espressoField.vmFieldMirror);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return vmFieldMirror.hashCode();
    }

    public Value readValue(Value receiver) {
        return vmFieldMirror.invokeMember("read", receiver);
    }

    void writeValue(JavaConstant receiver, JavaConstant value) {
        if (value == null) {
            throw new NullPointerException("value");
        }

        final Value receiverValue;
        if (isStatic()) {
            if (receiver != null) {
                throw new IllegalArgumentException("Static field write requires null receiver");
            }
            receiverValue = null;
        } else {
            if (receiver == null) {
                throw new NullPointerException("receiver");
            }
            if (receiver.isNull()) {
                throw new IllegalArgumentException("Receiver is null");
            }
            if (!(receiver instanceof EspressoExternalObjectConstant espressoReceiver)) {
                throw new IllegalArgumentException("Expected an espresso object receiver, got " + receiver.getClass().getName());
            }
            receiverValue = espressoReceiver.getValue();
        }

        JavaKind kind = getJavaKind();
        final Object boxed;
        if (kind == JavaKind.Object) {
            if (value.isNull()) {
                boxed = null;
            } else if (value instanceof EspressoExternalObjectConstant objConst) {
                boxed = objConst.getValue();
            } else {
                throw new IllegalArgumentException("Expected an espresso object constant, got " + value.getClass().getName());
            }
        } else {
            if (kind != value.getJavaKind()) {
                throw new IllegalArgumentException("Expected value kind " + kind + " but got " + value.getJavaKind());
            }
            boxed = value.asBoxedPrimitive();
        }

        getAccess().invokeJVMCIHelper("writeField", vmFieldMirror, receiverValue, boxed);
    }

    /**
     * Gets a guest {@link java.lang.reflect.Field} value associated with this field, creating it
     * first if necessary.
     */
    Value getReflectFieldMirror() {
        Value value = reflectFieldMirror;
        if (value == null) {
            value = getAccess().invokeJVMCIHelper("getReflectField", vmFieldMirror);
            reflectFieldMirror = value;
        }
        return value;
    }
}
