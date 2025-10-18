/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata;

import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.core.hub.crema.CremaResolvedJavaMethod;
import com.oracle.svm.core.hub.crema.CremaResolvedJavaRecordComponent;
import com.oracle.svm.core.hub.crema.CremaResolvedJavaType;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.ParserKlass;
import com.oracle.svm.espresso.classfile.attributes.Attribute;
import com.oracle.svm.espresso.classfile.attributes.AttributedElement;
import com.oracle.svm.espresso.classfile.attributes.NestHostAttribute;
import com.oracle.svm.espresso.classfile.attributes.NestMembersAttribute;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class CremaResolvedObjectType extends InterpreterResolvedObjectType implements CremaResolvedJavaType, AttributedElement {
    // GR-70288: Only keep a subset of the parsed attributes.
    private final Attribute[] attributes;

    private final byte[] primitiveStatics;
    private final Object[] referenceStatics;

    // GR-70720: Allow AOT types as nest host.
    private CremaResolvedObjectType host;

    public CremaResolvedObjectType(ParserKlass parserKlass, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces,
                    InterpreterConstantPool constantPool, Class<?> javaClass, boolean isWordType,
                    int staticReferenceFields, int staticPrimitiveFieldsSize) {
        super(parserKlass.getType(), modifiers, componentType, superclass, interfaces, constantPool, javaClass, isWordType);
        this.primitiveStatics = new byte[staticPrimitiveFieldsSize];
        this.referenceStatics = new Object[staticReferenceFields];
        this.attributes = parserKlass.getAttributes();
    }

    @Override
    public Object getStaticStorage(boolean primitives, int layerNum) {
        assert layerNum != MultiLayeredImageSingleton.NONSTATIC_FIELD_LAYER_NUMBER;
        return primitives ? primitiveStatics : referenceStatics;
    }

    @Override
    public CremaResolvedJavaFieldImpl[] getDeclaredFields() {
        return (CremaResolvedJavaFieldImpl[]) declaredFields;
    }

    @Override
    public CremaResolvedJavaMethod[] getDeclaredCremaMethods() {
        // filter out constructors
        ArrayList<CremaResolvedJavaMethod> result = new ArrayList<>();
        for (InterpreterResolvedJavaMethod declaredMethod : getDeclaredMethods()) {
            if (!declaredMethod.isConstructor()) {
                result.add((CremaResolvedJavaMethod) declaredMethod);
            }
        }
        return result.toArray(new CremaResolvedJavaMethod[0]);
    }

    @Override
    public CremaResolvedJavaMethod[] getDeclaredConstructors() {
        ArrayList<CremaResolvedJavaMethod> result = new ArrayList<>();
        for (InterpreterResolvedJavaMethod declaredMethod : getDeclaredMethods()) {
            if (declaredMethod.isConstructor()) {
                result.add((CremaResolvedJavaMethod) declaredMethod);
            }
        }
        return result.toArray(new CremaResolvedJavaMethod[0]);
    }

    @Override
    public ResolvedJavaMethod getClassInitializer() {
        for (InterpreterResolvedJavaMethod method : getDeclaredMethods(false)) {
            if (method.isClassInitializer()) {
                return method;
            }
        }
        return null;
    }

    @Override
    public List<? extends CremaResolvedJavaRecordComponent> getRecordComponents() {
        // (GR-69095)
        throw VMError.unimplemented("getRecordComponents");
    }

    @Override
    public byte[] getRawAnnotations() {
        // (GR-69096)
        throw VMError.unimplemented("getRawAnnotations");
    }

    @Override
    public byte[] getRawTypeAnnotations() {
        // (GR-69096)
        throw VMError.unimplemented("getRawTypeAnnotations");
    }

    @Override
    public ResolvedJavaMethod getEnclosingMethod() {
        // (GR-69095)
        throw VMError.unimplemented("getEnclosingMethod");
    }

    @Override
    public JavaType[] getDeclaredClasses() {
        // (GR-69095)
        throw VMError.unimplemented("getDeclaredClasses");
    }

    @Override
    public boolean isHidden() {
        // (GR-69095)
        throw VMError.unimplemented("isHidden");
    }

    @Override
    public JavaType[] getPermittedSubClasses() {
        // (GR-69095)
        throw VMError.unimplemented("getPermittedSubClasses");
    }

    @Override
    public CremaResolvedObjectType getNestHost() {
        if (host == null) {
            host = resolveHost();
        }
        return host;
    }

    @Override
    public InterpreterResolvedObjectType[] getNestMembers() {
        /*
         * This method is not called for VM operations, only for reflection. No need to cache the
         * result as this is a rare operation.
         */
        CremaResolvedObjectType nestHost = getNestHost();
        if (this != nestHost) {
            return resolveNestMembers(nestHost);
        }
        return resolveNestMembers(this);
    }

    private CremaResolvedObjectType resolveHost() {
        NestHostAttribute nestHostAttribute = getAttribute(NestHostAttribute.NAME, NestHostAttribute.class);
        if (nestHostAttribute == null) {
            return this;
        }
        try {
            InterpreterResolvedObjectType declaredHost = getConstantPool().resolvedTypeAt(this, nestHostAttribute.hostClassIndex);
            if (!(declaredHost instanceof CremaResolvedObjectType cremaHost)) {
                throw VMError.unimplemented("Specifying an AOT type as nest host is currently unsupported in runtime-loaded classes.");
            }
            if (cremaHost == this || !sameRuntimePackage(cremaHost) || !nestMemberCheck(cremaHost, this)) {
                /*
                 * Let H be the class named in the NestHostAttribute of the current class M. If any
                 * of the following is true, then M is its own nest host:
                 *
                 * - H is not in the same run-time package as M.
                 *
                 * - H lacks a NestMembers attribute. (checked above)
                 *
                 * - H has a NestMembers attribute, but there is no entry in its classes array that
                 * refers to a class or interface with the name N, where N is the name of M.
                 */
                return this;
            }
            return cremaHost;
        } catch (Throwable e) {
            /*
             * JVMS sect. 5.4.4: Any exception thrown as a result of failure of class or interface
             * resolution is not rethrown.
             */
            return this;
        }
    }

    private boolean sameRuntimePackage(InterpreterResolvedJavaType other) {
        // GR-62339 true package access checks
        return this.getJavaClass().getClassLoader() == other.getJavaClass().getClassLoader() && this.getSymbolicRuntimePackage() == other.getSymbolicRuntimePackage();
    }

    /**
     * Returns whether the given nest {@code host} class declares {@code member} as one of its nest
     * members.
     */
    private static boolean nestMemberCheck(CremaResolvedObjectType host, InterpreterResolvedJavaType member) {
        NestMembersAttribute members = host.getAttribute(NestMembersAttribute.NAME, NestMembersAttribute.class);
        if (members == null) {
            return false;
        }
        for (int clsIndex : members.getClasses()) {
            if (host.getConstantPool().className(clsIndex) == member.getSymbolicName()) {
                return true;
            }
        }
        return false;
    }

    private static InterpreterResolvedObjectType[] resolveNestMembers(CremaResolvedObjectType host) {
        NestMembersAttribute nestMembersAttribute = host.getAttribute(NestMembersAttribute.NAME, NestMembersAttribute.class);
        if (nestMembersAttribute == null || nestMembersAttribute.getClasses().length == 0) {
            return new InterpreterResolvedObjectType[]{host};
        }
        ArrayList<InterpreterResolvedObjectType> members = new ArrayList<>(nestMembersAttribute.getClasses().length + 1);
        members.add(host);
        InterpreterConstantPool pool = host.getConstantPool();
        for (int memberIndex : nestMembersAttribute.getClasses()) {
            InterpreterResolvedObjectType member = null;
            try {
                member = pool.resolvedTypeAt(host, memberIndex);
            } catch (Throwable e) {
                /*
                 * Don't allow badly constructed nest members to break execution here, only report
                 * well-constructed entries.
                 */
                continue;
            }
            if (!(member instanceof CremaResolvedObjectType cremaMember)) {
                // Specifying an AOT type as nest member is currently unsupported in crema.
                continue;
            }
            if (host != cremaMember.getNestHost()) {
                // Skip nest members that do not declare 'this' as their host.
                continue;
            }
            members.add(member);
        }
        return members.toArray(new InterpreterResolvedObjectType[0]);
    }

    @Override
    public Attribute[] getAttributes() {
        return attributes;
    }
}
