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

import static com.oracle.truffle.dsl.processor.Utils.*;

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.node.NodeChildData.*;
import com.oracle.truffle.dsl.processor.typesystem.*;

public abstract class TemplateMethodParser<T extends Template, E extends TemplateMethod> {

    private final ProcessorContext context;

    protected final T template;

    private boolean emitErrors = true;
    private boolean parseNullOnError = false;
    private boolean useVarArgs = false;

    public TemplateMethodParser(ProcessorContext context, T template) {
        this.template = template;
        this.context = context;
    }

    protected void setUseVarArgs(boolean useVarArgs) {
        this.useVarArgs = useVarArgs;
    }

    public boolean isUseVarArgs() {
        return useVarArgs;
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

    public TypeSystemData getTypeSystem() {
        return template.getTypeSystem();
    }

    public abstract MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror);

    public abstract E create(TemplateMethod method, boolean invalid);

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
                parsedMethod.addError("Method annotated with @%s must not be private.", getAnnotationType().getSimpleName());
                parsedMethods.add(parsedMethod);
                valid = false;
                continue;
            }

            if (parsedMethod != null) {
                parsedMethods.add(parsedMethod);
            } else {
                valid = false;
            }
        }
        Collections.sort(parsedMethods);

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

        methodSpecification.applyTypeDefinitions("types");

        String id = method.getSimpleName().toString();
        AnnotationMirror idAnnotation = Utils.findAnnotationMirror(context.getEnvironment(), method, NodeId.class);
        if (idAnnotation != null) {
            id = Utils.getAnnotationValue(String.class, idAnnotation, "value");
        }

        ParameterSpec returnTypeSpec = methodSpecification.getReturnType();

        ActualParameter returnTypeMirror = matchParameter(returnTypeSpec, method.getReturnType(), template, 0, -1, false);
        if (returnTypeMirror == null) {
            if (emitErrors) {
                E invalidMethod = create(new TemplateMethod(id, template, methodSpecification, method, annotation, returnTypeMirror, Collections.<ActualParameter> emptyList()), true);
                String expectedReturnType = returnTypeSpec.toSignatureString(true);
                String actualReturnType = Utils.getSimpleName(method.getReturnType());

                String message = String.format("The provided return type \"%s\" does not match expected return type \"%s\".\nExpected signature: \n %s", actualReturnType, expectedReturnType,
                                methodSpecification.toSignatureString(method.getSimpleName().toString()));
                invalidMethod.addError(message);
                return invalidMethod;
            } else {
                return null;
            }
        }

        List<TypeMirror> parameterTypes = new ArrayList<>();
        for (VariableElement var : method.getParameters()) {
            parameterTypes.add(var.asType());
        }

        List<ActualParameter> parameters = parseParameters(methodSpecification, parameterTypes, isUseVarArgs() && method.isVarArgs());
        if (parameters == null) {
            if (isEmitErrors()) {
                E invalidMethod = create(new TemplateMethod(id, template, methodSpecification, method, annotation, returnTypeMirror, Collections.<ActualParameter> emptyList()), true);
                String message = String.format("Method signature %s does not match to the expected signature: \n%s", createActualSignature(methodSpecification, method),
                                methodSpecification.toSignatureString(method.getSimpleName().toString()));
                invalidMethod.addError(message);
                return invalidMethod;
            } else {
                return null;
            }
        }

        return create(new TemplateMethod(id, template, methodSpecification, method, annotation, returnTypeMirror, parameters), false);
    }

    private static String createActualSignature(MethodSpec spec, ExecutableElement method) {
        StringBuilder b = new StringBuilder("(");
        String sep = "";
        for (TypeMirror implicitType : spec.getImplicitRequiredTypes()) {
            b.append(sep);
            b.append("implicit " + Utils.getSimpleName(implicitType));
            sep = ", ";
        }
        for (VariableElement var : method.getParameters()) {
            b.append(sep);
            b.append(Utils.getSimpleName(var.asType()));
            sep = ", ";
        }
        b.append(")");
        return b.toString();
    }

    /*
     * Parameter parsing tries to parse required arguments starting from offset 0 with increasing
     * offset until it finds a signature end that matches the required specification. If there is no
     * end matching the required arguments, parsing fails. Parameters prior to the parsed required
     * ones are cut and used to parse the optional parameters. All those remaining parameters must
     * be consumed otherwise its an error.
     */
    private List<ActualParameter> parseParameters(MethodSpec spec, List<TypeMirror> parameterTypes, boolean varArgs) {
        List<TypeMirror> implicitTypes = spec.getImplicitRequiredTypes();

        int offset = -1;
        List<ActualParameter> parsedRequired = null;
        ConsumableListIterator<TypeMirror> types = null;
        while (parsedRequired == null && offset < parameterTypes.size()) {
            offset++;
            types = new ConsumableListIterator<>(new ArrayList<>(implicitTypes));
            types.data.addAll(parameterTypes.subList(offset, parameterTypes.size()));
            parsedRequired = parseParametersRequired(spec, types, varArgs);
        }

        if (parsedRequired == null && offset >= 0) {
            return null;
        }

        List<TypeMirror> potentialOptionals;
        if (offset == -1) {
            potentialOptionals = parameterTypes;
        } else {
            potentialOptionals = parameterTypes.subList(0, offset);
        }
        types = new ConsumableListIterator<>(potentialOptionals);
        List<ActualParameter> parsedOptionals = parseParametersOptional(spec, types);
        if (parsedOptionals == null) {
            return null;
        }

        List<ActualParameter> finalParameters = new ArrayList<>();
        finalParameters.addAll(parsedOptionals);
        finalParameters.addAll(parsedRequired);
        return finalParameters;
    }

    private List<ActualParameter> parseParametersOptional(MethodSpec spec, ConsumableListIterator<TypeMirror> types) {
        List<ActualParameter> parsedParams = new ArrayList<>();
        // parse optional parameters
        ConsumableListIterator<ParameterSpec> optionals = new ConsumableListIterator<>(spec.getOptional());
        for (TypeMirror type : types) {
            int oldIndex = types.getIndex();
            int optionalCount = 1;
            for (ParameterSpec paramspec : optionals) {
                ActualParameter optionalParam = matchParameter(paramspec, type, template, 0, -1, false);
                if (optionalParam != null) {
                    optionals.consume(optionalCount);
                    types.consume();
                    parsedParams.add(optionalParam);
                    break;
                }
                optionalCount++;
            }
            if (oldIndex == types.getIndex()) {
                // nothing found anymore skip optional
                break;
            }
        }
        if (types.getIndex() <= types.data.size() - 1) {
            return null;
        }
        return parsedParams;
    }

    private List<ActualParameter> parseParametersRequired(MethodSpec spec, ConsumableListIterator<TypeMirror> types, boolean varArgs) {
        List<ActualParameter> parsedParams = new ArrayList<>();

        int varArgsParameterIndex = -1;
        int specificationParameterIndex = 0;
        ConsumableListIterator<ParameterSpec> required = new ConsumableListIterator<>(spec.getRequired());
        while (required.get() != null || types.get() != null) {
            if (required.get() == null || types.get() == null) {
                if (required.get() != null && required.get().getCardinality() == Cardinality.MANY) {
                    required.consume();
                    specificationParameterIndex = 0;
                    continue;
                }
                break;
            }
            TypeMirror actualType = types.get();
            if (varArgs && types.isLast()) {
                if (actualType.getKind() == TypeKind.ARRAY) {
                    actualType = ((ArrayType) actualType).getComponentType();
                }
                varArgsParameterIndex++;
            }
            boolean implicit = types.getIndex() < spec.getImplicitRequiredTypes().size();
            ActualParameter resolvedParameter = matchParameter(required.get(), actualType, template, specificationParameterIndex, varArgsParameterIndex, implicit);
            if (resolvedParameter == null) {
                if (required.get().getCardinality() == Cardinality.MANY) {
                    required.consume();
                    continue;
                }
                // direct mismatch but required -> error
                return null;
            } else {
                parsedParams.add(resolvedParameter);

                if (varArgs && types.isLast()) {
                    /* Both varargs spec and varargs definition. Need to consume to terminate. */
                    if (required.get().getCardinality() == Cardinality.MANY) {
                        types.consume();
                    }
                } else {
                    types.consume();
                }

                if (required.get().getCardinality() == Cardinality.ONE) {
                    required.consume();
                    specificationParameterIndex = 0;
                } else if (required.get().getCardinality() == Cardinality.MANY) {
                    specificationParameterIndex++;
                }
            }
        }

        if (!types.toList().isEmpty() && !(varArgs && types.isLast())) {
            // additional types -> error
            return null;
        }

        if (!required.toList().isEmpty() && !spec.isVariableRequiredArguments()) {
            // additional specifications -> error
            return null;
        }
        return parsedParams;
    }

    private ActualParameter matchParameter(ParameterSpec specification, TypeMirror mirror, Template originalTemplate, int specificationIndex, int varArgsIndex, boolean implicit) {
        TypeMirror resolvedType = mirror;
        if (hasError(resolvedType)) {
            resolvedType = context.resolveNotYetCompiledType(mirror, originalTemplate);
        }

        if (!specification.matches(resolvedType)) {
            return null;
        }

        TypeData resolvedTypeData = getTypeSystem().findTypeData(resolvedType);
        if (resolvedTypeData != null) {
            return new ActualParameter(specification, resolvedTypeData, specificationIndex, varArgsIndex, implicit);
        } else {
            return new ActualParameter(specification, resolvedType, specificationIndex, varArgsIndex, implicit);
        }
    }

    /* Helper class for parsing. */
    private static class ConsumableListIterator<E> implements Iterable<E> {

        private final List<E> data;
        private int index;

        public ConsumableListIterator(List<E> data) {
            this.data = data;
        }

        public E get() {
            if (index >= data.size()) {
                return null;
            }
            return data.get(index);
        }

        public boolean isLast() {
            return index == data.size() - 1;
        }

        public E consume() {
            return consume(1);
        }

        public E consume(int count) {
            if (index + count <= data.size()) {
                index += count;
                return get();
            } else {
                throw new ArrayIndexOutOfBoundsException(count + 1);
            }
        }

        public int getIndex() {
            return index;
        }

        @Override
        public Iterator<E> iterator() {
            return toList().iterator();
        }

        public List<E> toList() {
            if (index < data.size()) {
                return data.subList(index, data.size());
            } else {
                return Collections.<E> emptyList();
            }
        }

    }

}
