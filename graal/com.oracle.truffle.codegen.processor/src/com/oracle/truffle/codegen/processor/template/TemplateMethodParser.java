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

            if (method.getModifiers().contains(Modifier.PRIVATE)) {
                getContext().getLog().error(method, "Method must not be private.");
                valid = false;
                continue;
            }

            E parsedMethod = parse(method, mirror);
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

        ParameterSpec returnTypeSpec = methodSpecification.getReturnType();
        List<ParameterSpec> parameterSpecs = new ArrayList<>();
        parameterSpecs.addAll(methodSpecification.getParameters());

        ActualParameter returnTypeMirror = resolveTypeMirror(returnTypeSpec, method.getReturnType(), template);
        if (returnTypeMirror == null) {
            if (isEmitErrors()) {
                String expectedReturnType = createTypeSignature(returnTypeSpec, true);
                String actualReturnType = Utils.getSimpleName(method.getReturnType());

                String message = String.format("The provided return type \"%s\" does not match expected return type \"%s\".\nExpected signature: \n %s", actualReturnType, expectedReturnType,
                                createExpectedSignature(method.getSimpleName().toString(), returnTypeSpec, parameterSpecs));

                context.getLog().error(method, annotation, message);
            }
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
                    if (isEmitErrors()) {
                        // non option type specification found -> argument missing
                        String expectedType = createTypeSignature(specification, false);

                        String message = String.format("Missing argument \"%s\".\nExpected signature: \n %s", expectedType,
                                        createExpectedSignature(method.getSimpleName().toString(), returnTypeSpec, parameterSpecs));

                        context.getLog().error(method, message);
                    }
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

                if (isEmitErrors()) {
                    String expectedReturnType = createTypeSignature(specification, false);
                    String actualReturnType = Utils.getSimpleName(parameter.asType()) + " " + parameter.getSimpleName();

                    String message = String.format("The provided argument type \"%s\" does not match expected type \"%s\".\nExpected signature: \n %s", actualReturnType, expectedReturnType,
                                    createExpectedSignature(method.getSimpleName().toString(), returnTypeSpec, parameterSpecs));

                    context.getLog().error(parameter, message);
                }
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
            if (isEmitErrors()) {
                String actualReturnType = Utils.getSimpleName(parameter.asType()) + " " + parameter.getSimpleName();
                String message = String.format("No argument expected but found \"%s\".\nExpected signature: \n %s", actualReturnType,
                                createExpectedSignature(method.getSimpleName().toString(), returnTypeSpec, parameterSpecs));

                context.getLog().error(parameter, message);
            }
            return null;
        }

        ActualParameter[] paramMirrors = resolvedMirrors.toArray(new ActualParameter[resolvedMirrors.size()]);
        return create(new TemplateMethod(template, methodSpecification, method, annotation, returnTypeMirror, paramMirrors));
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
