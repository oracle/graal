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

import java.util.HashSet;
import java.util.Set;

public class ConfigurationType {

    private final String qualifiedName;

    private boolean allDeclaredClasses;
    private boolean allPublicClasses;
    private boolean allDeclaredFields;
    private boolean allPublicFields;
    private boolean allDeclaredMethods;
    private boolean allPublicMethods;
    private boolean allDeclaredConstructors;
    private boolean allPublicConstructors;

    private Set<String> fields;
    private Set<ConfigurationMethod> methods;

    public ConfigurationType(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public String getName() {
        return qualifiedName;
    }

    public boolean haveAllDeclaredClasses() {
        return allDeclaredClasses;
    }

    public void setAllDeclaredClasses() {
        this.allDeclaredClasses = true;
    }

    public boolean haveAllPublicClasses() {
        return allPublicClasses;
    }

    public void setAllPublicClasses() {
        this.allPublicClasses = true;
    }

    public boolean haveAllDeclaredFields() {
        return allDeclaredFields;
    }

    public void setAllDeclaredFields() {
        this.allDeclaredFields = true;
    }

    public boolean haveAllPublicFields() {
        return allPublicFields;
    }

    public void setAllPublicFields() {
        this.allPublicFields = true;
    }

    public boolean haveAllDeclaredMethods() {
        return allDeclaredMethods;
    }

    public void setAllDeclaredMethods() {
        this.allDeclaredMethods = true;
    }

    public boolean haveAllPublicMethods() {
        return allPublicMethods;
    }

    public void setAllPublicMethods() {
        this.allPublicMethods = true;
    }

    public boolean haveAllDeclaredConstructors() {
        return allDeclaredConstructors;
    }

    public void setAllDeclaredConstructors() {
        this.allDeclaredConstructors = true;
    }

    public boolean haveAllPublicConstructors() {
        return allPublicConstructors;
    }

    public void setAllPublicConstructors() {
        this.allPublicConstructors = true;
    }

    public void addField(String fieldName) {
        if (fields == null) {
            fields = new HashSet<>();
        }
        fields.add(fieldName);
    }

    public void addMethod(ConfigurationMethod method) {
        if (methods == null) {
            methods = new HashSet<>();
        }
        methods.add(method);
    }

    public boolean hasIndividualMethod(String name, String fullSignature) {
        if (methods != null) {
            for (ConfigurationMethod method : methods) {
                if (method.matches(name, fullSignature)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasIndividualField(String name) {
        if (fields != null) {
            return fields.contains(name);
        }
        return false;
    }
}
