/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.codegen.processor.typesystem;

import static com.oracle.truffle.codegen.processor.Utils.*;

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.template.*;

public class TypeSystemParser extends TemplateParser<TypeSystemData> {

    public static final List<Class<TypeSystem>> ANNOTATIONS = Arrays.asList(TypeSystem.class);

    public TypeSystemParser(ProcessorContext c) {
        super(c);
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return TypeSystem.class;
    }

    @Override
    protected TypeSystemData parse(Element element, AnnotationMirror mirror) {
        TypeElement templateType = (TypeElement) element;
        AnnotationMirror templateTypeAnnotation = mirror;

        if (!verifyTemplateType(templateType, templateTypeAnnotation)) {
            return null;
        }

        TypeData[] types = parseTypes(templateType, templateTypeAnnotation);
        if (types == null) {
            return null;
        }

        TypeMirror genericType = context.getType(Object.class);
        TypeData voidType = new TypeData(templateType, templateTypeAnnotation, context.getType(void.class), context.getType(Void.class));

        TypeSystemData typeSystem = new TypeSystemData(templateType, templateTypeAnnotation, types, genericType, voidType);

        if (!verifyExclusiveMethodAnnotation(templateType, TypeCast.class, TypeCheck.class)) {
            return null;
        }

        List<Element> elements = new ArrayList<>(context.getEnvironment().getElementUtils().getAllMembers(templateType));
        typeSystem.setExtensionElements(getExtensionParser().parseAll(templateType, elements));
        if (typeSystem.getExtensionElements() != null) {
            elements.addAll(typeSystem.getExtensionElements());
        }

        List<TypeCastData> casts = new TypeCastParser(context, typeSystem).parse(elements);
        List<TypeCheckData> checks = new TypeCheckParser(context, typeSystem).parse(elements);

        if (casts == null || checks == null) {
            return null;
        }

        for (TypeCheckData check : checks) {
            check.getCheckedType().addTypeCheck(check);
        }

        for (TypeCastData cast : casts) {
            cast.getTargetType().addTypeCast(cast);
        }

        if (!verifyGenericTypeChecksAndCasts(types)) {
            return null;
        }

        if (!verifyMethodSignatures(element, types)) {
            return null;
        }

        if (!verifyNamesUnique(templateType, templateTypeAnnotation, types)) {
            return null;
        }

        return typeSystem;
    }

    private boolean verifyGenericTypeChecksAndCasts(TypeData[] types) {
        boolean valid = true;
        for (TypeData type : types) {
            if (!type.getTypeChecks().isEmpty()) {
                boolean hasGeneric = false;
                for (TypeCheckData typeCheck : type.getTypeChecks()) {
                    if (typeCheck.isGeneric()) {
                        hasGeneric = true;
                        break;
                    }
                }
                if (!hasGeneric) {
                    log.error(type.getTypeSystem().getTemplateType(), "No generic but specific @%s method %s for type %s specified. "
                                    + "Specify a generic @%s method with parameter type %s to resolve this.", TypeCheck.class.getSimpleName(), TypeSystemCodeGenerator.isTypeMethodName(type),
                                    Utils.getSimpleName(type.getBoxedType()), TypeCheck.class.getSimpleName(), Object.class.getSimpleName());
                    valid = false;
                }
            }
            if (!type.getTypeCasts().isEmpty()) {
                boolean hasGeneric = false;
                for (TypeCastData typeCast : type.getTypeCasts()) {
                    if (typeCast.isGeneric()) {
                        hasGeneric = true;
                        break;
                    }
                }
                if (!hasGeneric) {
                    log.error(type.getTypeSystem().getTemplateType(), "No generic but specific @%s method %s for type %s specified. "
                                    + "Specify a generic @%s method with parameter type %s to resolve this.", TypeCast.class.getSimpleName(), TypeSystemCodeGenerator.asTypeMethodName(type),
                                    Utils.getSimpleName(type.getBoxedType()), TypeCast.class.getSimpleName(), Object.class.getSimpleName());
                    valid = false;
                }
            }
        }
        return valid;
    }

    private TypeData[] parseTypes(TypeElement templateType, AnnotationMirror templateTypeAnnotation) {
        List<TypeMirror> typeMirrors = Utils.getAnnotationValueList(templateTypeAnnotation, "value");
        if (typeMirrors.size() == 0) {
            log.error(templateType, templateTypeAnnotation, "At least one type must be defined.");
            return null;
        }

        final AnnotationValue annotationValue = Utils.getAnnotationValue(templateTypeAnnotation, "value");
        final TypeMirror objectType = context.getType(Object.class);

        List<TypeData> types = new ArrayList<>();
        for (TypeMirror primitiveType : typeMirrors) {

            if (isPrimitiveWrapper(primitiveType)) {
                log.error(templateType, templateTypeAnnotation, annotationValue, "Types must not contain primitive wrapper types.");
                continue;
            }

            TypeMirror boxedType = Utils.boxType(context, primitiveType);

            if (Utils.typeEquals(boxedType, objectType)) {
                log.error(templateType, templateTypeAnnotation, annotationValue, "Types must not contain the generic type java.lang.Object.");
                continue;
            }

            types.add(new TypeData(templateType, templateTypeAnnotation, primitiveType, boxedType));
        }

        verifyTypeOrder(templateType, templateTypeAnnotation, annotationValue, types);

        types.add(new TypeData(templateType, templateTypeAnnotation, objectType, objectType));

        return types.toArray(new TypeData[types.size()]);
    }

    private void verifyTypeOrder(TypeElement templateType, AnnotationMirror templateTypeAnnotation, AnnotationValue annotationValue, List<TypeData> types) {
        Map<String, List<String>> invalidTypes = new HashMap<>();

        for (int i = types.size() - 1; i >= 0; i--) {
            TypeData typeData = types.get(i);
            TypeMirror type = typeData.getBoxedType();
            if (invalidTypes.containsKey(Utils.getQualifiedName(type))) {
                log.error(templateType, templateTypeAnnotation, annotationValue, "Invalid type order. The type(s) %s are inherited from a earlier defined type %s.",
                                invalidTypes.get(Utils.getQualifiedName(type)), Utils.getQualifiedName(type));
            }
            List<String> nextInvalidTypes = Utils.getQualifiedSuperTypeNames(Utils.fromTypeMirror(type));
            nextInvalidTypes.add(getQualifiedName(type));

            for (String qualifiedName : nextInvalidTypes) {
                List<String> inheritedTypes = invalidTypes.get(qualifiedName);
                if (inheritedTypes == null) {
                    inheritedTypes = new ArrayList<>();
                    invalidTypes.put(qualifiedName, inheritedTypes);
                }
                inheritedTypes.add(Utils.getQualifiedName(typeData.getBoxedType()));
            }
        }
    }

    private boolean isPrimitiveWrapper(TypeMirror type) {
        Types types = context.getEnvironment().getTypeUtils();
        for (TypeKind kind : TypeKind.values()) {
            if (!kind.isPrimitive()) {
                continue;
            }
            if (Utils.typeEquals(type, types.boxedClass(types.getPrimitiveType(kind)).asType())) {
                return true;
            }
        }
        return false;
    }

    private boolean verifyMethodSignatures(Element element, TypeData[] types) {
        Set<String> generatedIsMethodNames = new HashSet<>();
        Set<String> generatedAsMethodNames = new HashSet<>();
        Set<String> generatedExpectMethodNames = new HashSet<>();

        for (TypeData typeData : types) {
            generatedIsMethodNames.add(TypeSystemCodeGenerator.isTypeMethodName(typeData));
            generatedAsMethodNames.add(TypeSystemCodeGenerator.asTypeMethodName(typeData));
            generatedExpectMethodNames.add(TypeSystemCodeGenerator.expectTypeMethodName(typeData));
        }

        boolean valid = true;
        List<ExecutableElement> methods = ElementFilter.methodsIn(element.getEnclosedElements());
        for (ExecutableElement method : methods) {
            if (method.getModifiers().contains(Modifier.PRIVATE)) {
                // will not conflict overridden methods
                continue;
            } else if (method.getParameters().size() != 1) {
                continue;
            }
            String methodName = method.getSimpleName().toString();
            if (generatedIsMethodNames.contains(methodName)) {
                valid &= verifyIsMethod(method);
            } else if (generatedAsMethodNames.contains(methodName)) {
                valid &= verifyAsMethod(method);
            } else if (generatedExpectMethodNames.contains(methodName)) {
                valid &= verifyExpectMethod(method);
            }
        }
        return valid;
    }

    private boolean verifyIsMethod(ExecutableElement method) {
        AnnotationMirror mirror = Utils.findAnnotationMirror(processingEnv, method, TypeCheck.class);
        if (mirror == null) {
            log.error(method, "Method starting with the pattern is${typeName} must be annotated with @%s.", TypeCheck.class.getSimpleName());
            return false;
        }
        return true;
    }

    private boolean verifyAsMethod(ExecutableElement method) {
        AnnotationMirror mirror = Utils.findAnnotationMirror(processingEnv, method, TypeCast.class);
        if (mirror == null) {
            log.error(method, "Method starting with the pattern as${typeName} must be annotated with @%s.", TypeCast.class.getSimpleName());
            return false;
        }
        return true;
    }

    private boolean verifyExpectMethod(ExecutableElement method) {
        log.error(method, "Method starting with the pattern expect${typeName} must not be declared manually.");
        return false;
    }

    private boolean verifyNamesUnique(TypeElement templateType, AnnotationMirror templateTypeAnnotation, TypeData[] types) {
        boolean valid = true;
        for (int i = 0; i < types.length; i++) {
            for (int j = i + 1; j < types.length; j++) {
                String name1 = Utils.getSimpleName(types[i].getBoxedType());
                String name2 = Utils.getSimpleName(types[j].getBoxedType());
                if (name1.equalsIgnoreCase(name2)) {
                    log.error(templateType, templateTypeAnnotation, "Two types result in the same name: %s, %s.", name1, name2);
                    valid = false;
                }
            }
        }
        return valid;
    }
}
