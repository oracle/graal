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
package com.oracle.truffle.dsl.processor.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.model.ImplicitCastData;
import com.oracle.truffle.dsl.processor.model.Template;
import com.oracle.truffle.dsl.processor.model.TypeCastData;
import com.oracle.truffle.dsl.processor.model.TypeCheckData;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;

public class TypeSystemParser extends AbstractParser<TypeSystemData> {

    @Override
    public DeclaredType getAnnotationType() {
        return types.TypeSystem;
    }

    /**
     * @see "https://bugs.openjdk.java.net/browse/JDK-8039214"
     */
    @SuppressWarnings("unused")
    private static List<Element> newElementList(List<? extends Element> src) {
        List<Element> workaround = new ArrayList<Element>(src);
        return workaround;
    }

    @Override
    protected TypeSystemData parse(Element element, List<AnnotationMirror> mirror) {
        TypeElement templateType = (TypeElement) element;
        AnnotationMirror templateTypeAnnotation = mirror.iterator().next();

        TypeSystemData typeSystem = new TypeSystemData(context, templateType, templateTypeAnnotation, false);

        // annotation type on class path!?
        if (templateType.getModifiers().contains(Modifier.PRIVATE)) {
            typeSystem.addError("A @%s must have at least package protected visibility.", getAnnotationType().asElement().getSimpleName().toString());
        }

        if (templateType.getModifiers().contains(Modifier.FINAL)) {
            typeSystem.addError("The @%s must not be final.", getAnnotationType().asElement().getSimpleName().toString());
        }
        if (typeSystem.hasErrors()) {
            return typeSystem;
        }

        if (typeSystem.hasErrors()) {
            return typeSystem;
        }

        verifyExclusiveMethodAnnotation(typeSystem, types.TypeCast, types.TypeCheck);

        List<Element> elements = newElementList(context.getEnvironment().getElementUtils().getAllMembers(templateType));
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

    private static void verifyExclusiveMethodAnnotation(Template template, DeclaredType... annotationTypes) {
        List<ExecutableElement> methods = ElementFilter.methodsIn(template.getTemplateType().getEnclosedElements());
        for (ExecutableElement method : methods) {
            List<AnnotationMirror> foundAnnotations = new ArrayList<>();
            for (int i = 0; i < annotationTypes.length; i++) {
                DeclaredType annotationType = annotationTypes[i];
                AnnotationMirror mirror = ElementUtils.findAnnotationMirror(method, annotationType);
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

    private static final TypeKind[] TYPE_KIND_VALUES = TypeKind.values();

    private boolean isPrimitiveWrapper(TypeMirror type) {
        Types typeUtils = context.getEnvironment().getTypeUtils();
        for (TypeKind kind : TYPE_KIND_VALUES) {
            if (!kind.isPrimitive()) {
                continue;
            }
            if (ElementUtils.typeEquals(type, typeUtils.boxedClass(typeUtils.getPrimitiveType(kind)).asType())) {
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
