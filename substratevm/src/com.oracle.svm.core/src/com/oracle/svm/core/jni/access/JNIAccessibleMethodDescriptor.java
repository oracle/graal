/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jni.access;

import static com.oracle.svm.core.jni.access.JNIReflectionDictionary.WRAPPED_CSTRING_EQUIVALENCE;

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
        String sig = method.getSignature().toMethodDescriptor();
        assert !sig.contains(".") : "Malformed signature (needs to use '/' as package separator)";
        return new JNIAccessibleMethodDescriptor(method.getName(), sig);
    }

    public static JNIAccessibleMethodDescriptor of(Executable method) {
        String name = method.getName();
        Class<?> returnType;
        if (method instanceof Constructor) {
            name = CONSTRUCTOR_NAME;
            returnType = Void.TYPE;
        } else if (method instanceof Method) {
            returnType = ((Method) method).getReturnType();
        } else {
            throw VMError.shouldNotReachHereUnexpectedInput(method); // ExcludeFromJacocoGeneratedReport
        }
        return of(name, method.getParameterTypes(), returnType);
    }

    public static JNIAccessibleMethodDescriptor of(String methodName, Class<?>[] parameterTypes) {
        return of(methodName, parameterTypes, null);
    }

    private static JNIAccessibleMethodDescriptor of(String methodName, Class<?>[] parameterTypes, Class<?> returnType) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> type : parameterTypes) {
            sb.append(MetaUtil.toInternalName(type.getName()));
        }
        sb.append(')');
        if (returnType != null) {
            sb.append(MetaUtil.toInternalName(returnType.getName()));
        }
        assert sb.indexOf(".") == -1 : "Malformed signature (needs to use '/' as package separator)";
        return new JNIAccessibleMethodDescriptor(methodName, sb.toString());
    }

    private final CharSequence name;
    private final CharSequence signature;

    JNIAccessibleMethodDescriptor(CharSequence name, CharSequence signature) {
        this.name = name;
        this.signature = signature;
    }

    public boolean isConstructor() {
        return WRAPPED_CSTRING_EQUIVALENCE.equals(name, CONSTRUCTOR_NAME);
    }

    public boolean isClassInitializer() {
        return WRAPPED_CSTRING_EQUIVALENCE.equals(name, INITIALIZER_NAME);
    }

    public String getName() {
        return (String) name;
    }

    public String getSignature() {
        return (String) signature;
    }

    /**
     * Performs a potentially costly conversion to string, only for slow paths.
     */
    public String getNameConvertToString() {
        return name.toString();
    }

    public String getSignatureWithoutReturnType() {
        String signatureString = signature.toString();
        int parametersEnd = signatureString.lastIndexOf(')');
        if (!signatureString.isEmpty() && signatureString.charAt(0) == '(' && parametersEnd != -1) {
            return signatureString.substring(0, parametersEnd + 1);
        } else {
            return null;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof JNIAccessibleMethodDescriptor) {
            JNIAccessibleMethodDescriptor other = (JNIAccessibleMethodDescriptor) obj;
            return (other == this) || (WRAPPED_CSTRING_EQUIVALENCE.equals(name, other.name) && WRAPPED_CSTRING_EQUIVALENCE.equals(signature, other.signature));
        }
        return false;
    }

    public boolean matchesIgnoreReturnType(ResolvedJavaMethod method) {
        if (!getName().equals(method.getName())) {
            return false;
        }
        int position = 1; // skip '('
        for (JavaType parameterType : method.getSignature().toParameterTypes(null)) {
            String paramInternal = parameterType.getName();
            if (!getSignature().startsWith(paramInternal, position)) {
                return false;
            }
            position += paramInternal.length();
        }
        return getSignature().startsWith(")", position);
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + signature.hashCode();
    }
}
