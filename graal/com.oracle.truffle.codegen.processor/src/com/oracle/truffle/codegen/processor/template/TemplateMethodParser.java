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

import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.template.ParameterSpec.Cardinality;

public abstract class TemplateMethodParser<E extends TemplateMethod> {

    private final ProcessorContext context;

    public TemplateMethodParser(ProcessorContext context) {
        this.context = context;
    }

    public ProcessorContext getContext() {
        return context;
    }

    public abstract MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror);

    public abstract E create(TemplateMethod method);

    public abstract Class<? extends Annotation> getAnnotationType();

    public final E parse(ExecutableElement method, AnnotationMirror annotation, Template template) {
        MethodSpec methodSpecification = createSpecification(method, annotation);
        if (methodSpecification == null) {
            return null;
        }

        ParameterSpec returnTypeSpec = methodSpecification.getReturnType();
        List<ParameterSpec> parameterSpecs = new ArrayList<>();
        parameterSpecs.addAll(methodSpecification.getParameters());

        ActualParameter returnTypeMirror = resolveTypeMirror(returnTypeSpec, method.getReturnType(), template);
        if (returnTypeMirror == null) {
            String expectedReturnType = createTypeSignature(returnTypeSpec, true);
            String actualReturnType = Utils.getSimpleName(method.getReturnType());

            String message = String.format("The provided return type \"%s\" does not match expected return type \"%s\".\nExpected signature: \n %s", actualReturnType, expectedReturnType,
                            createExpectedSignature(method.getSimpleName().toString(), returnTypeSpec, parameterSpecs));

            context.getLog().error(method, annotation, message);
            return null;
        }

        Iterator< ? extends VariableElement> variableIterator = method.getParameters().iterator();
        Iterator< ? extends ParameterSpec> specificationIterator = parameterSpecs.iterator();

        List<ActualParameter> resolvedMirrors = new ArrayList<>();
        VariableElement parameter = null;
        ParameterSpec specification = null;
        while (specificationIterator.hasNext() || specification != null) {
            if (specification == null) {
                specification = specificationIterator.next();
            }

            if (parameter == null && variableIterator.hasNext()) {
                parameter = variableIterator.next();
            }

            if (parameter == null) {
                if (specification.getCardinality() == Cardinality.MULTIPLE) {
                    specification = null;
                    continue;
                } else if (!specification.isOptional()) {
                    // non option type specification found -> argument missing
                    String expectedType = createTypeSignature(specification, false);

                    String message = String.format("Missing argument \"%s\".\nExpected signature: \n %s", expectedType,
                                    createExpectedSignature(method.getSimpleName().toString(), returnTypeSpec, parameterSpecs));

                    context.getLog().error(method, message);
                    return null;
                } else {
                    // specification is optional -> continue
                    specification = null;
                    continue;
                }
            }

            ActualParameter resolvedMirror = resolveTypeMirror(specification, parameter.asType(), template);

            if (resolvedMirror == null) {
                if (specification.isOptional()) {
                    specification = null;
                    continue;
                }

                String expectedReturnType = createTypeSignature(specification, false);
                String actualReturnType = Utils.getSimpleName(parameter.asType()) + " " + parameter.getSimpleName();

                String message = String.format("The provided argument type \"%s\" does not match expected type \"%s\".\nExpected signature: \n %s", actualReturnType, expectedReturnType,
                                createExpectedSignature(method.getSimpleName().toString(), returnTypeSpec, parameterSpecs));

                context.getLog().error(parameter, message);
                return null;
            }

            resolvedMirrors.add(resolvedMirror);
            parameter = null; // consume parameter

            if (specification.getCardinality() != Cardinality.MULTIPLE) {
                specification = null;
            }
        }

        if (variableIterator.hasNext()) {
            parameter = variableIterator.next();
            String actualReturnType = Utils.getSimpleName(parameter.asType()) + " " + parameter.getSimpleName();
            String message = String.format("No argument expected but found \"%s\".\nExpected signature: \n %s", actualReturnType,
                            createExpectedSignature(method.getSimpleName().toString(), returnTypeSpec, parameterSpecs));

            context.getLog().error(parameter, message);
            return null;
        }

        ActualParameter[] paramMirrors = resolvedMirrors.toArray(new ActualParameter[resolvedMirrors.size()]);
        return create(new TemplateMethod(methodSpecification, method, annotation, returnTypeMirror, paramMirrors));
    }

    private ActualParameter resolveTypeMirror(ParameterSpec specification, TypeMirror mirror, Template typeSystem) {
        TypeMirror resolvedType = mirror;
        if (hasError(resolvedType)) {
            resolvedType = context.resolveNotYetCompiledType(mirror, typeSystem);
        }

        if (!specification.matches(resolvedType)) {
            return null;
        }
        return new ActualParameter(specification, resolvedType);
    }

    public static String createExpectedSignature(String methodName, ParameterSpec returnType, List< ? extends ParameterSpec> parameters) {
        StringBuilder b = new StringBuilder();

        b.append("    ");
        b.append(createTypeSignature(returnType, true));

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

            b.append(createTypeSignature(specification, false));

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

        TypeMirror[] types = null;

        //TODO allowed types may differ so different <Any> must be generated.
        if (returnType.getAllowedTypes().length > 1) {
            types = returnType.getAllowedTypes();
        }
        for (ParameterSpec param : parameters) {
            if (param.getAllowedTypes().length > 1) {
                types = param.getAllowedTypes();
            }
        }
        if (types != null) {
            b.append("\n\n    ");
            b.append("<Any> = {");
            String separator = "";
            for (TypeMirror type : types) {
                b.append(separator).append(Utils.getSimpleName(type));
                separator = ", ";
            }
            b.append("}");
        }
        return b.toString();
    }

    private static String createTypeSignature(ParameterSpec spec, boolean typeOnly) {
        StringBuilder builder = new StringBuilder();
        if (spec.getAllowedTypes().length > 1) {
            builder.append("<Any>");
        } else if (spec.getAllowedTypes().length == 1) {
            builder.append(Utils.getSimpleName(spec.getAllowedTypes()[0]));
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
