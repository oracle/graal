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
package com.oracle.svm.agent.restrict;

import java.util.List;
import java.util.Objects;

class ConfigurationMethod {
    public static final String CONSTRUCTOR_NAME = "<init>";

    private static String toParamsSignature(List<ConfigurationType> types) {
        StringBuilder sb = new StringBuilder("(");
        for (ConfigurationType type : types) {
            sb.append(type.getName()); // internal name
        }
        sb.append(')');
        // we are missing the return type, so this is only a partial signature
        return sb.toString();
    }

    private final String name;
    private final String paramsSignature;
    private int hashCode = 0;

    /** Matches methods with that name and any signature. */
    ConfigurationMethod(String name) {
        this.name = name;
        this.paramsSignature = null;
    }

    ConfigurationMethod(String name, List<ConfigurationType> paramTypes) {
        this.name = name;
        this.paramsSignature = toParamsSignature(paramTypes);
    }

    public String getName() {
        return name;
    }

    public boolean matches(String methodName, String signature) {
        return getName().equals(methodName) && (paramsSignature == null || signature.startsWith(paramsSignature));
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = name.hashCode() * 31 + Objects.hashCode(paramsSignature);
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this != obj && obj instanceof ConfigurationMethod) {
            ConfigurationMethod other = (ConfigurationMethod) obj;
            return name.equals(other.name) && Objects.equals(paramsSignature, other.paramsSignature);
        }
        return (this == obj);
    }
}
