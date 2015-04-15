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
package com.oracle.truffle.dsl.processor.parser;

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.internal.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.model.*;

@DSLOptions
public class TypeSystemParser extends AbstractParser<TypeSystemData> {

    public static final List<Class<? extends Annotation>> ANNOTATIONS = Arrays.asList(TypeSystem.class, ExpectError.class);

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return TypeSystem.class;
    }

    @Override
    protected TypeSystemData parse(Element element, AnnotationMirror mirror) {
        TypeElement templateType = (TypeElement) element;
        AnnotationMirror templateTypeAnnotation = mirror;
        DSLOptions options = element.getAnnotation(DSLOptions.class);
        if (options == null) {
            options = TypeSystemParser.class.getAnnotation(DSLOptions.class);
        }
        assert options != null;

        TypeSystemData typeSystem = new TypeSystemData(context, templateType, templateTypeAnnotation, options, false);

        // annotation type on class path!?
        TypeElement annotationTypeElement = processingEnv.getElementUtils().getTypeElement(getAnnotationType().getCanonicalName());
        if (annotationTypeElement == null) {
            typeSystem.addError("Required class %s is not on the classpath.", getAnnotationType().getName());
        }
        if (templateType.getModifiers().contains(Modifier.PRIVATE)) {
            typeSystem.addError("A @%s must have at least package protected visibility.", getAnnotationType().getName());
        }

        if (templateType.getModifiers().contains(Modifier.FINAL)) {
            typeSystem.addError("The @%s must not be final.", getAnnotationType().getName());
        }
        if (typeSystem.hasErrors()) {
            return typeSystem;
        }

        if (typeSystem.hasErrors()) {
            return typeSystem;
        }

        verifyExclusiveMethodAnnotation(typeSystem, TypeCast.class, TypeCheck.class);

        List<Element> elements = new ArrayList<>(context.getEnvironment().getElementUtils().getAllMembers(templateType));
        List<ImplicitCastData> implicitCasts = new ImplicitCastParser(context, typeSystem).parse(elements);
        List<TypeCastData> casts = new TypeCastParser(context, typeSystem).parse(elements);
        List<TypeCheckData> checks = new TypeCheckParser(context, typeSystem).parse(elements);

        if (casts == null || checks == null || implicitCasts == null) {
            return typeSystem;
        }

        List<TypeMirror> legacyTypes = ElementUtils.getAnnotationValueList(TypeMirror.class, typeSystem.getTemplateTypeAnnotation(), "value");
        for (int i = 0; i < legacyTypes.size(); i++) {
            legacyTypes.set(i, ElementUtils.fillInGenericWildcards(legacyTypes.get(i)));
        }

        typeSystem.getLegacyTypes().addAll(legacyTypes);
        verifyTypes(typeSystem);
        typeSystem.getLegacyTypes().add(context.getType(Object.class));
        typeSystem.getLegacyTypes().add(context.getType(void.class));
        verifyNamesUnique(typeSystem);

        typeSystem.getImplicitCasts().addAll(implicitCasts);
        typeSystem.getCasts().addAll(casts);
        typeSystem.getChecks().addAll(checks);

        if (typeSystem.hasErrors()) {
            return typeSystem;
        }
        return typeSystem;
    }

    private void verifyExclusiveMethodAnnotation(Template template, Class<?>... annotationTypes) {
        List<ExecutableElement> methods = ElementFilter.methodsIn(template.getTemplateType().getEnclosedElements());
        for (ExecutableElement method : methods) {
            List<AnnotationMirror> foundAnnotations = new ArrayList<>();
            for (int i = 0; i < annotationTypes.length; i++) {
                Class<?> annotationType = annotationTypes[i];
                AnnotationMirror mirror = ElementUtils.findAnnotationMirror(context.getEnvironment(), method, annotationType);
                if (mirror != null) {
                    foundAnnotations.add(mirror);
                }
            }
            if (foundAnnotations.size() > 1) {
                List<String> annotationNames = new ArrayList<>();
                for (AnnotationMirror mirror : foundAnnotations) {
                    annotationNames.add("@" + ElementUtils.getSimpleName(mirror.getAnnotationType()));
                }

                template.addError("Non exclusive usage of annotations %s.", annotationNames);
            }
        }
    }

    private void verifyTypes(TypeSystemData typeSystem) {
        for (TypeMirror type : typeSystem.getLegacyTypes()) {
            if (isPrimitiveWrapper(type)) {
                typeSystem.addError("Types must not contain primitive wrapper types.");
            }

            if (ElementUtils.typeEquals(type, context.getType(Object.class))) {
                typeSystem.addError("Types must not contain the generic type java.lang.Object.");
            }
        }

        verifyTypeOrder(typeSystem);
    }

    private static void verifyTypeOrder(TypeSystemData typeSystem) {
        Map<String, List<String>> invalidTypes = new HashMap<>();

        for (int i = typeSystem.getLegacyTypes().size() - 1; i >= 0; i--) {
            TypeMirror typeData = typeSystem.getLegacyTypes().get(i);
            TypeMirror type = typeSystem.boxType(typeData);
            if (invalidTypes.containsKey(ElementUtils.getQualifiedName(type))) {
                typeSystem.addError("Invalid type order. The type(s) %s are inherited from a earlier defined type %s.", invalidTypes.get(ElementUtils.getQualifiedName(type)),
                                ElementUtils.getQualifiedName(type));
            }
            TypeElement element = ElementUtils.fromTypeMirror(type);
            List<String> nextInvalidTypes = new ArrayList<>();
            if (element != null) {
                nextInvalidTypes.addAll(ElementUtils.getQualifiedSuperTypeNames(element));
            }
            nextInvalidTypes.add(ElementUtils.getQualifiedName(type));

            for (String qualifiedName : nextInvalidTypes) {
                List<String> inheritedTypes = invalidTypes.get(qualifiedName);
                if (inheritedTypes == null) {
                    inheritedTypes = new ArrayList<>();
                    invalidTypes.put(qualifiedName, inheritedTypes);
                }
                inheritedTypes.add(ElementUtils.getQualifiedName(typeSystem.boxType(typeData)));
            }
        }
    }

    private boolean isPrimitiveWrapper(TypeMirror type) {
        Types types = context.getEnvironment().getTypeUtils();
        for (TypeKind kind : TypeKind.values()) {
            if (!kind.isPrimitive()) {
                continue;
            }
            if (ElementUtils.typeEquals(type, types.boxedClass(types.getPrimitiveType(kind)).asType())) {
                return true;
            }
        }
        return false;
    }

    private static void verifyNamesUnique(TypeSystemData typeSystem) {
        Set<String> usedNames = new HashSet<>();
        for (TypeMirror type : typeSystem.getLegacyTypes()) {
            String boxedName = ElementUtils.getSimpleName(typeSystem.boxType(type));
            String primitiveName = ElementUtils.getSimpleName(type);
            if (usedNames.contains(boxedName)) {
                typeSystem.addError("Two types result in the same boxed name: %s.", boxedName);
            } else if (usedNames.contains(primitiveName)) {
                typeSystem.addError("Two types result in the same primitive name: %s.", primitiveName);
            }
            usedNames.add(boxedName);
            usedNames.add(primitiveName);
        }
    }

}
