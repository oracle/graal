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
import java.util.List;
import java.util.Objects;

import com.oracle.svm.configure.json.JsonPrintable;
import com.oracle.svm.configure.json.JsonWriter;

import jdk.vm.ci.meta.MetaUtil;

public class ConfigurationMethod implements JsonPrintable {
    public static final String CONSTRUCTOR_NAME = "<init>";

    public static boolean isConstructorName(String name) {
        return CONSTRUCTOR_NAME.equals(name);
    }

    public static String toInternalParamsSignature(List<ConfigurationType> types) {
        StringBuilder sb = new StringBuilder("(");
        for (ConfigurationType type : types) {
            sb.append(MetaUtil.toInternalName(type.getQualifiedJavaName()));
        }
        sb.append(')');
        // we are missing the return type, so this is only a partial signature
        return sb.toString();
    }

    private final String name;
    private final String internalSignature;
    private int hash;

    public ConfigurationMethod(String name, String internalSignature) {
        this.name = name;
        String paramsOnlySignature = internalSignature;
        if (paramsOnlySignature != null) { // omit return type to avoid duplicates
            int paramsEnd = internalSignature.lastIndexOf(')');
            paramsOnlySignature = paramsOnlySignature.substring(0, paramsEnd + 1);
        }
        this.internalSignature = paramsOnlySignature;
    }

    public String getName() {
        return name;
    }

    public String getInternalSignature() {
        return internalSignature;
    }

    public boolean matches(String methodName, String methodInternalSignature) {
        // NOTE: we use startsWith which matches if methodInternalSignature includes the return type
        return getName().equals(methodName) && (internalSignature == null || methodInternalSignature.startsWith(internalSignature));
    }

    public boolean isConstructor() {
        return isConstructorName(name);
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('{');
        writer.quote("name").append(':').quote(name).append(',');
        writer.quote("parameterTypes").append(":[");
        String prefix = "";
        for (String type : SignatureUtil.toParameterTypes(internalSignature)) {
            writer.append(prefix).quote(type);
            prefix = ",";
        }
        writer.append("] }");
    }

    @Override
    public int hashCode() {
        if (hash == 0) {
            hash = name.hashCode() * 31 + (internalSignature == null ? 0 : internalSignature.hashCode());
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != this && obj instanceof ConfigurationMethod) {
            ConfigurationMethod other = (ConfigurationMethod) obj;
            return name.equals(other.name) && Objects.equals(internalSignature, other.internalSignature);
        }
        return (obj == this);
    }
}
