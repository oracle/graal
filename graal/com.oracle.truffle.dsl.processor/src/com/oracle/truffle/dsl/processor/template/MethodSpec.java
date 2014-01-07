/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.template;

import java.util.*;

import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.*;

public class MethodSpec {

    private final ParameterSpec returnType;
    private final List<ParameterSpec> optional = new ArrayList<>();
    private final List<ParameterSpec> required = new ArrayList<>();

    private boolean ignoreAdditionalParameters;
    private boolean ignoreAdditionalSpecifications;
    private boolean variableRequiredParameters;

    private List<TypeDef> typeDefinitions;

    public MethodSpec(ParameterSpec returnType) {
        this.returnType = returnType;
    }

    public void setVariableRequiredParameters(boolean variableRequiredParameters) {
        this.variableRequiredParameters = variableRequiredParameters;
    }

    public boolean isVariableRequiredParameters() {
        return variableRequiredParameters;
    }

    public void setIgnoreAdditionalParameters(boolean ignoreAdditionalParameter) {
        this.ignoreAdditionalParameters = ignoreAdditionalParameter;
    }

    public boolean isIgnoreAdditionalParameters() {
        return ignoreAdditionalParameters;
    }

    public void addOptional(ParameterSpec spec) {
        optional.add(spec);
    }

    public ParameterSpec addRequired(ParameterSpec spec) {
        required.add(spec);
        return spec;
    }

    public ParameterSpec getReturnType() {
        return returnType;
    }

    public List<ParameterSpec> getRequired() {
        return required;
    }

    public List<ParameterSpec> getOptional() {
        return optional;
    }

    public List<ParameterSpec> getAll() {
        List<ParameterSpec> specs = new ArrayList<>();
        specs.add(getReturnType());
        specs.addAll(getOptional());
        specs.addAll(getRequired());
        return specs;
    }

    public ParameterSpec findParameterSpec(String name) {
        for (ParameterSpec spec : getAll()) {
            if (spec.getName().equals(name)) {
                return spec;
            }
        }
        return null;
    }

    public void applyTypeDefinitions(String prefix) {
        this.typeDefinitions = createTypeDefinitions(prefix);
    }

    private List<TypeDef> createTypeDefinitions(String prefix) {
        List<TypeDef> typeDefs = new ArrayList<>();

        int defIndex = 0;
        for (ParameterSpec spec : getAll()) {
            List<TypeMirror> allowedTypes = spec.getAllowedTypes();
            List<TypeMirror> types = spec.getAllowedTypes();
            if (types != null && allowedTypes.size() > 1) {
                TypeDef foundDef = null;
                for (TypeDef def : typeDefs) {
                    if (allowedTypes.equals(def.getTypes())) {
                        foundDef = def;
                        break;
                    }
                }
                if (foundDef == null) {
                    foundDef = new TypeDef(types, prefix + defIndex);
                    typeDefs.add(foundDef);
                    defIndex++;
                }

                spec.setTypeDefinition(foundDef);
            }
        }

        return typeDefs;
    }

    public String toSignatureString(String methodName) {
        StringBuilder b = new StringBuilder();
        b.append("    ");
        b.append(createTypeSignature(returnType, true));

        b.append(" ");
        b.append(methodName);
        b.append("(");

        String sep = "";

        for (ParameterSpec optionalSpec : getOptional()) {
            b.append(sep);
            b.append("[");
            b.append(createTypeSignature(optionalSpec, false));
            b.append("]");
            sep = ", ";
        }

        for (int i = 0; i < getRequired().size(); i++) {
            ParameterSpec requiredSpec = getRequired().get(i);
            b.append(sep);

            if (isVariableRequiredParameters() && i == getRequired().size() - 1) {
                b.append(("{"));
            }
            b.append(createTypeSignature(requiredSpec, false));
            if (isVariableRequiredParameters() && i == getRequired().size() - 1) {
                b.append(("}"));
            }

            sep = ", ";
        }

        b.append(")");

        if (typeDefinitions != null && !typeDefinitions.isEmpty()) {
            b.append("\n\n");

            String lineSep = "";
            for (TypeDef def : typeDefinitions) {
                b.append(lineSep);
                b.append("    <").append(def.getName()).append(">");
                b.append(" = {");
                String separator = "";
                for (TypeMirror type : def.getTypes()) {
                    b.append(separator).append(Utils.getSimpleName(type));
                    separator = ", ";
                }
                b.append("}");
                lineSep = "\n";

            }
        }
        return b.toString();
    }

    private static String createTypeSignature(ParameterSpec spec, boolean typeOnly) {
        StringBuilder builder = new StringBuilder();
        TypeDef foundTypeDef = spec.getTypeDefinition();
        if (foundTypeDef != null) {
            builder.append("<" + foundTypeDef.getName() + ">");
        } else if (spec.getAllowedTypes().size() >= 1) {
            builder.append(Utils.getSimpleName(spec.getAllowedTypes().get(0)));
        } else {
            builder.append("void");
        }
        if (!typeOnly) {
            builder.append(" ");
            builder.append(spec.getName());
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return toSignatureString("methodName");
    }

    static class TypeDef {

        private final List<TypeMirror> types;
        private final String name;

        public TypeDef(List<TypeMirror> types, String name) {
            this.types = types;
            this.name = name;
        }

        public List<TypeMirror> getTypes() {
            return types;
        }

        public String getName() {
            return name;
        }
    }

    public void setIgnoreAdditionalSpecifications(boolean ignoreAdditoinalSpecifications) {
        this.ignoreAdditionalSpecifications = ignoreAdditoinalSpecifications;
    }

    public boolean isIgnoreAdditionalSpecifications() {
        return ignoreAdditionalSpecifications;
    }

}
