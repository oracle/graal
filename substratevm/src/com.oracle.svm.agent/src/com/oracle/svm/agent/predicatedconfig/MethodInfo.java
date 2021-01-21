/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.agent.predicatedconfig;

import java.util.Objects;

import com.oracle.svm.configure.config.SignatureUtil;

public class MethodInfo {

    private final String name;
    private final String signature;
    private final ClassInfo declaringClass;

    MethodInfo(String name, String signature, ClassInfo declaringClass) {
        this.name = name;
        this.signature = signature;
        this.declaringClass = declaringClass;
    }

    public String getJavaMethodNameAndSignature() {
        String[] parameterTypes = SignatureUtil.toParameterTypes(signature);
        StringBuilder sb = new StringBuilder(name);
        sb.append(" (");
        boolean first = false;
        for (String parameterType : parameterTypes) {
            if (!first) {
                first = true;
            } else {
                sb.append(",");
            }
            sb.append(parameterType);
        }
        sb.append(")");
        return sb.toString();
    }

    public String getJavaDeclaringClassName() {
        return declaringClass.className;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MethodInfo that = (MethodInfo) o;
        return name.equals(that.name) && signature.equals(that.signature) && declaringClass.equals(that.declaringClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, signature, declaringClass);
    }
}
