/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.java.ElementUtils;

public class MethodSpec {

    private final ParameterSpec returnType;
    private final List<ParameterSpec> optional = new ArrayList<>();
    private final List<ParameterSpec> required = new ArrayList<>();
    private final List<ParameterSpec> annotations = new ArrayList<>();

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

    public List<ParameterSpec> getAnnotations() {
        return annotations;
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

    public void applyTypeDefinitions(String prefix) {
        this.typeDefinitions = createTypeDefinitions(prefix);
    }

    private List<TypeDef> createTypeDefinitions(String prefix) {
        List<TypeDef> typeDefs = new ArrayList<>();

        int defIndex = 0;
        for (ParameterSpec spec : getAll()) {
            Collection<TypeMirror> allowedTypes = spec.getAllowedTypes();
            Collection<TypeMirror> types = spec.getAllowedTypes();
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
            b.append(System.lineSeparator());
            b.append(System.lineSeparator());

            String lineSep = "";
            for (TypeDef def : typeDefinitions) {
                b.append(lineSep);
                b.append("    <").append(def.getName()).append(">");
                b.append(" = {");
                String separator = "";
                for (TypeMirror type : def.getTypes()) {
                    b.append(separator).append(ElementUtils.getSimpleName(type));
                    separator = ", ";
                }
                b.append("}");
                lineSep = System.lineSeparator();

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
            builder.append(ElementUtils.getSimpleName(spec.getAllowedTypes().iterator().next()));
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

    static final class TypeDef {

        private final Collection<TypeMirror> types;
        private final String name;

        private TypeDef(Collection<TypeMirror> types, String name) {
            this.types = types;
            this.name = name;
        }

        public Collection<TypeMirror> getTypes() {
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
