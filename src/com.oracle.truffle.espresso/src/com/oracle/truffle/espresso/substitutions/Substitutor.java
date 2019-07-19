/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.substitutions;

import java.util.List;

public abstract class Substitutor {

    public static final String INSTANCE_NAME = "theInstance";
    public static final String GETTER = "getInstance";

    private final String methodName;
    private final String substitutionClassName;
    private final String returnType;
    private final String[] parameterTypes;
    private final boolean hasReceiver;

    Substitutor(String methodName, String substitutionClassName, String returnType, String[] parameterTypes, boolean hasReceiver) {
        this.methodName = methodName;
        this.substitutionClassName = substitutionClassName;
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
        this.hasReceiver = hasReceiver;
    }

    private static String getClassName(String className, String methodName, List<String> parameterTypes) {
        StringBuilder str = new StringBuilder();
        str.append(className).append("_").append(methodName).append(signatureSuffixBuilder(parameterTypes));
        return str.toString();
    }

    /**
     * This method MUST return the same as th one in SubstitutionProcessor.
     */
    public static String getQualifiedClassName(String className, String methodName, List<String> parameterTypes) {
        return Substitutor.class.getPackage().getName() + "." + getClassName(className, methodName, parameterTypes);
    }

    private static StringBuilder signatureSuffixBuilder(List<String> parameterTypes) {
        StringBuilder str = new StringBuilder();
        str.append("_").append(parameterTypes.size());
        return str;
    }

    public String getMethodName() {
        return methodName;
    }

    public String substitutionClassName() {
        return substitutionClassName;
    }

    public String returnType() {
        return returnType;
    }

    public String[] parameterTypes() {
        return parameterTypes;
    }

    public boolean hasReceiver() {
        return hasReceiver;
    }

    public abstract Object invoke(Object[] args);
}
