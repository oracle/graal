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
package com.oracle.truffle.codegen.processor.template;

import static com.oracle.truffle.codegen.processor.Utils.*;

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.template.ParameterSpec.Cardinality;

public abstract class TemplateMethodParser<T extends Template, E extends TemplateMethod> {

    private final ProcessorContext context;

    protected final T template;

    private boolean emitErrors = true;
    private boolean parseNullOnError = true;

    public TemplateMethodParser(ProcessorContext context, T template) {
        this.template = template;
        this.context = context;
    }

    public boolean isEmitErrors() {
        return emitErrors;
    }

    public void setParseNullOnError(boolean nullOnError) {
        this.parseNullOnError = nullOnError;
    }

    public boolean isParseNullOnError() {
        return parseNullOnError;
    }

    public void setEmitErrors(boolean emitErrors) {
        this.emitErrors = emitErrors;
    }

    public ProcessorContext getContext() {
        return context;
    }

    public abstract MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror);

    public abstract E create(TemplateMethod method);

    public abstract boolean isParsable(ExecutableElement method);

    public Class<? extends Annotation> getAnnotationType() {
        return null;
    }

    public final List<E> parse(List<? extends Element> elements) {
        List<ExecutableElement> methods = new ArrayList<>();
        methods.addAll(ElementFilter.methodsIn(elements));

        List<E> parsedMethods = new ArrayList<>();
        boolean valid = true;
        for (ExecutableElement method : methods) {
            if (!isParsable(method)) {
                continue;
            }

            Class<? extends Annotation> annotationType = getAnnotationType();
            AnnotationMirror mirror = null;
            if (annotationType != null) {
                mirror = Utils.findAnnotationMirror(getContext().getEnvironment(), method, annotationType);
            }

            E parsedMethod = parse(method, mirror);

            if (method.getModifiers().contains(Modifier.PRIVATE) && emitErrors) {
                parsedMethod.addError("Method must not be private.");
                valid = false;
                continue;
            }

            if (parsedMethod != null) {
                parsedMethods.add(parsedMethod);
            } else {
                valid = false;
            }
        }
        if (!valid && parseNullOnError) {
            return null;
        }
        return parsedMethods;
    }

    private E parse(ExecutableElement method, AnnotationMirror annotation) {
        MethodSpec methodSpecification = createSpecification(method, annotation);
        if (methodSpecification == null) {
            return null;
        }

        String id = method.getSimpleName().toString();
        AnnotationMirror idAnnotation = Utils.findAnnotationMirror(context.getEnvironment(), method, NodeId.class);
        if (idAnnotation != null) {
            id = Utils.getAnnotationValue(String.class, idAnnotation, "value");
        }

        List<TypeDef> typeDefs = createTypeDefinitions(methodSpecification.getReturnType(), methodSpecification.getParameters());

        ParameterSpec returnTypeSpec = methodSpecification.getReturnType();
        List<ParameterSpec> parameterSpecs = new ArrayList<>();
        parameterSpecs.addAll(methodSpecification.getParameters());

        ActualParameter returnTypeMirror = matchParameter(returnTypeSpec, method.getReturnType(), template, 0, false);
        if (returnTypeMirror == null) {
            if (emitErrors) {
                E invalidMethod = create(new TemplateMethod(id, template, methodSpecification, method, annotation, returnTypeMirror, Collections.<ActualParameter> emptyList()));
                String expectedReturnType = createTypeSignature(returnTypeSpec, typeDefs, true);
                String actualReturnType = Utils.getSimpleName(method.getReturnType());

                String message = String.format("The provided return type \"%s\" does not match expected return type \"%s\".\nExpected signature: \n %s", actualReturnType, expectedReturnType,
                                createExpectedSignature(method.getSimpleName().toString(), returnTypeSpec, parameterSpecs, typeDefs));
                invalidMethod.addError(message);
                return invalidMethod;
            } else {
                return null;
            }
        }

        List<TypeMirror> parameterTypes = new ArrayList<>();
        parameterTypes.addAll(methodSpecification.getImplicitTypes());
        for (VariableElement var : method.getParameters()) {
            parameterTypes.add(var.asType());
        }

        List<ActualParameter> parameters = parseParameters(parameterTypes, parameterSpecs, methodSpecification.getImplicitTypes().size());
        if (parameters == null) {
            if (isEmitErrors()) {
                E invalidMethod = create(new TemplateMethod(id, template, methodSpecification, method, annotation, returnTypeMirror, Collections.<ActualParameter> emptyList()));
                String message = String.format("Method signature %s does not match to the expected signature: \n%s", createActualSignature(methodSpecification, method),
                                createExpectedSignature(method.getSimpleName().toString(), returnTypeSpec, parameterSpecs, typeDefs));
                invalidMethod.addError(message);
                return invalidMethod;
            } else {
                return null;
            }
        }

        return create(new TemplateMethod(id, template, methodSpecification, method, annotation, returnTypeMirror, parameters));
    }

    private static String createActualSignature(MethodSpec spec, ExecutableElement method) {
        List<String> types = new ArrayList<>();
        for (TypeMirror implicitType : spec.getImplicitTypes()) {
            types.add("implicit " + Utils.getSimpleName(implicitType));
        }
        for (VariableElement var : method.getParameters()) {
            types.add(Utils.getSimpleName(var.asType()));
        }

        StringBuilder b = new StringBuilder("(");
        for (Iterator<String> iterator = types.iterator(); iterator.hasNext();) {
            b.append(iterator.next());
            if (iterator.hasNext()) {
                b.append(", ");
            }
        }
        b.append(")");
        return b.toString();
    }

    private List<ActualParameter> parseParameters(List<TypeMirror> types, List<ParameterSpec> parameterSpecs, int hiddenCount) {
        Iterator<? extends TypeMirror> parameterIterator = types.iterator();
        Iterator<? extends ParameterSpec> specificationIterator = parameterSpecs.iterator();

        TypeMirror parameter = parameterIterator.hasNext() ? parameterIterator.next() : null;
        ParameterSpec specification = specificationIterator.hasNext() ? specificationIterator.next() : null;

        int globalParameterIndex = 0;
        int specificationParameterIndex = 0;
        List<ActualParameter> resolvedParameters = new ArrayList<>();
        while (parameter != null || specification != null) {
            if (parameter == null || specification == null) {
                if (specification != null && (specification.isOptional() || specification.getCardinality() == Cardinality.MULTIPLE)) {
                    specification = specificationIterator.hasNext() ? specificationIterator.next() : null;
                    specificationParameterIndex = 0;
                    continue;
                }
                return null;
            }

            boolean hidden = globalParameterIndex < hiddenCount;
            ActualParameter resolvedParameter = matchParameter(specification, parameter, template, specificationParameterIndex, hidden);
            if (resolvedParameter == null) {
                // mismatch
                if (specification.isOptional()) {
                    specification = specificationIterator.hasNext() ? specificationIterator.next() : null;
                    specificationParameterIndex = 0;
                } else {
                    return null;
                }
            } else {
                resolvedParameters.add(resolvedParameter);

                // match
                if (specification.getCardinality() == Cardinality.ONE) {
                    parameter = parameterIterator.hasNext() ? parameterIterator.next() : null;
                    specification = specificationIterator.hasNext() ? specificationIterator.next() : null;
                    globalParameterIndex++;
                    specificationParameterIndex = 0;
                } else if (specification.getCardinality() == Cardinality.MULTIPLE) {
                    parameter = parameterIterator.hasNext() ? parameterIterator.next() : null;
                    globalParameterIndex++;
                    specificationParameterIndex++;
                }
            }
        }
        return resolvedParameters;
    }

    private ActualParameter matchParameter(ParameterSpec specification, TypeMirror mirror, Template typeSystem, int index, boolean hidden) {
        TypeMirror resolvedType = mirror;
        if (hasError(resolvedType)) {
            resolvedType = context.resolveNotYetCompiledType(mirror, typeSystem);
        }

        if (!specification.matches(resolvedType)) {
            return null;
        }
        return new ActualParameter(specification, resolvedType, index, hidden);
    }

    protected List<TypeDef> createTypeDefinitions(ParameterSpec returnType, List<? extends ParameterSpec> parameters) {
        List<TypeDef> typeDefs = new ArrayList<>();

        List<ParameterSpec> allParams = new ArrayList<>();
        allParams.add(returnType);
        allParams.addAll(parameters);

        int defIndex = 0;
        for (ParameterSpec spec : allParams) {
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
                    foundDef = new TypeDef(types, "Types" + defIndex);
                    typeDefs.add(foundDef);
                    defIndex++;
                }

                foundDef.getParameters().add(spec);
            }
        }

        return typeDefs;
    }

    protected static class TypeDef {

        private final List<TypeMirror> types;
        private final String name;
        private final List<ParameterSpec> parameters = new ArrayList<>();

        public TypeDef(List<TypeMirror> types, String name) {
            this.types = types;
            this.name = name;
        }

        public List<ParameterSpec> getParameters() {
            return parameters;
        }

        public List<TypeMirror> getTypes() {
            return types;
        }

        public String getName() {
            return name;
        }
    }

    public static String createExpectedSignature(String methodName, ParameterSpec returnType, List<? extends ParameterSpec> parameters, List<TypeDef> typeDefs) {
        StringBuilder b = new StringBuilder();

        b.append("    ");
        b.append(createTypeSignature(returnType, typeDefs, true));

        b.append(" ");
        b.append(methodName);
        b.append("(");

        for (int i = 0; i < parameters.size(); i++) {
            ParameterSpec specification = parameters.get(i);
            if (specification.isOptional()) {
                b.append("[");
            }
            if (specification.getCardinality() == Cardinality.MULTIPLE) {
                b.append("{");
            }

            b.append(createTypeSignature(specification, typeDefs, false));

            if (specification.isOptional()) {
                b.append("]");
            }

            if (specification.getCardinality() == Cardinality.MULTIPLE) {
                b.append("}");
            }

            if (i < parameters.size() - 1) {
                b.append(", ");
            }

        }

        b.append(")");

        if (!typeDefs.isEmpty()) {
            b.append("\n\n");

            String lineSep = "";
            for (TypeDef def : typeDefs) {
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

    private static String createTypeSignature(ParameterSpec spec, List<TypeDef> typeDefs, boolean typeOnly) {
        StringBuilder builder = new StringBuilder();
        if (spec.getAllowedTypes().size() > 1) {
            TypeDef foundTypeDef = null;
            for (TypeDef typeDef : typeDefs) {
                if (typeDef.getParameters().contains(spec)) {
                    foundTypeDef = typeDef;
                    break;
                }
            }
            if (foundTypeDef != null) {
                builder.append("<" + foundTypeDef.getName() + ">");
            }
        } else if (spec.getAllowedTypes().size() == 1) {
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

}
