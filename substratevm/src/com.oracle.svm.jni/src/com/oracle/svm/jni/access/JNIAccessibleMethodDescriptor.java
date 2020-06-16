/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.access;

// Checkstyle: allow reflection

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;

import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Method descriptor that is used for lookups of JNI-accessible methods.
 */
public final class JNIAccessibleMethodDescriptor {

    private static final String CONSTRUCTOR_NAME = "<init>";
    private static final String INITIALIZER_NAME = "<clinit>";

    public static JNIAccessibleMethodDescriptor of(JavaMethod method) {
        return new JNIAccessibleMethodDescriptor(method.getName(), method.getSignature().toMethodDescriptor());
    }

    public static JNIAccessibleMethodDescriptor of(Executable method) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> type : method.getParameterTypes()) {
            sb.append(MetaUtil.toInternalName(type.getName()));
        }
        String name = method.getName();
        Class<?> returnType;
        if (method instanceof Constructor) {
            name = CONSTRUCTOR_NAME;
            returnType = Void.TYPE;
        } else if (method instanceof Method) {
            returnType = ((Method) method).getReturnType();
        } else {
            throw VMError.shouldNotReachHere();
        }
        sb.append(')').append(MetaUtil.toInternalName(returnType.getName()));
        return new JNIAccessibleMethodDescriptor(name, sb.toString());
    }

    private final String name;
    private final String signature;

    JNIAccessibleMethodDescriptor(String name, String signature) {
        assert !signature.contains(".") : "Malformed signature (needs to use '/' as package separator)";
        this.name = name;
        this.signature = signature;
    }

    public boolean isConstructor() {
        return name.equals(CONSTRUCTOR_NAME);
    }

    public boolean isClassInitializer() {
        return name.equals(INITIALIZER_NAME);
    }

    public String getName() {
        return name;
    }

    public String getSignature() {
        return signature;
    }

    public String getNameAndSignature() {
        return name + signature;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof JNIAccessibleMethodDescriptor) {
            JNIAccessibleMethodDescriptor other = (JNIAccessibleMethodDescriptor) obj;
            return (other == this) || (name.equals(other.name) && signature.equals(other.signature));
        }
        return false;
    }

    boolean matchesIgnoreReturnType(ResolvedJavaMethod method) {
        if (!name.equals(method.getName())) {
            return false;
        }
        int position = 1; // skip '('
        for (JavaType parameterType : method.getSignature().toParameterTypes(null)) {
            String paramInternal = parameterType.getName();
            if (!signature.startsWith(paramInternal, position)) {
                return false;
            }
            position += paramInternal.length();
        }
        return signature.startsWith(")", position);
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + signature.hashCode();
    }
}
