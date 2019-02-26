/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.config;

import java.io.IOException;
import java.util.Arrays;

import com.oracle.svm.configure.json.JsonPrintable;
import com.oracle.svm.configure.json.JsonWriter;

public class ReflectionMethod implements JsonPrintable {
    public static final String CONSTRUCTOR_NAME = "<init>";

    private final String name;
    private final String[] parameterTypes;
    private int hashCode = 0;

    public ReflectionMethod(String name, String[] parameterTypes) {
        this.name = name;
        this.parameterTypes = parameterTypes;
    }

    public ReflectionMethod(String name, String signature) {
        this.name = name;
        this.parameterTypes = SignatureParser.getParameterTypes(signature);
    }

    public String getName() {
        return name;
    }

    public String[] getParameterTypes() {
        return parameterTypes;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = name.hashCode() * 31 + Arrays.hashCode(parameterTypes);
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this != obj && obj instanceof ReflectionMethod) {
            ReflectionMethod other = (ReflectionMethod) obj;
            return name.equals(other.name) && Arrays.equals(parameterTypes, other.parameterTypes);
        }
        return (this == obj);
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append("{ ").quote("name").append(':').quote(name);
        writer.append(", ").quote("parameterTypes").append(":[");
        String prefix = "";
        for (String type : parameterTypes) {
            writer.append(prefix).quote(type);
            prefix = ", ";
        }
        writer.append("] }");
    }
}
