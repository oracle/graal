/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.java.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import com.oracle.truffle.dsl.processor.java.ElementUtils;

public class JDTCompiler extends AbstractCompiler {

    public static boolean isValidElement(Element currentElement) {
        try {
            Class<?> elementClass = currentElement.getClass().getClassLoader().loadClass("org.eclipse.jdt.internal.compiler.apt.model.ElementImpl");
            return elementClass.isAssignableFrom(currentElement.getClass());
        } catch (ClassNotFoundException e) {
            return false;
        }
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
    public List<? extends Element> getAllMembersInDeclarationOrder(ProcessingEnvironment environment, TypeElement type) {
        return sortBySourceOrder(newElementList(environment.getElementUtils().getAllMembers(type)));
    }

    private static List<? extends Element> sortBySourceOrder(List<Element> elements) {
        Map<TypeElement, List<Element>> groupedByEnclosing = new HashMap<>();
        for (Element element : elements) {
            Element enclosing = element.getEnclosingElement();
            List<Element> grouped = groupedByEnclosing.get(enclosing);
            if (grouped == null) {
                grouped = new ArrayList<>();
                groupedByEnclosing.put((TypeElement) enclosing, grouped);
            }
            grouped.add(element);
        }

        for (TypeElement enclosing : groupedByEnclosing.keySet()) {
            Collections.sort(groupedByEnclosing.get(enclosing), createSourceOrderComparator(enclosing));
        }

        if (groupedByEnclosing.size() == 1) {
            return groupedByEnclosing.get(groupedByEnclosing.keySet().iterator().next());
        } else {
            List<TypeElement> enclosingTypes = new ArrayList<>(groupedByEnclosing.keySet());

            Collections.sort(enclosingTypes, new Comparator<TypeElement>() {
                public int compare(TypeElement o1, TypeElement o2) {
                    if (ElementUtils.isSubtype(o1.asType(), o2.asType())) {
                        return 1;
                    } else {
                        return -1;
                    }
                }
            });

            List<Element> sourceOrderElements = new ArrayList<>();
            for (TypeElement typeElement : enclosingTypes) {
                sourceOrderElements.addAll(groupedByEnclosing.get(typeElement));
            }
            return sourceOrderElements;
        }

    }

    private static Comparator<Element> createSourceOrderComparator(final TypeElement enclosing) {

        Comparator<Element> comparator = new Comparator<Element>() {

            final List<Object> declarationOrder = lookupDeclarationOrder(enclosing);

            public int compare(Element o1, Element o2) {
                try {
                    Element enclosing1Element = o1.getEnclosingElement();
                    Element enclosing2Element = o2.getEnclosingElement();

                    if (!ElementUtils.typeEquals(enclosing1Element.asType(), enclosing2Element.asType())) {
                        throw new AssertionError();
                    }

                    Object o1Binding = field(o1, "_binding");
                    Object o2Binding = field(o2, "_binding");

                    int i1 = declarationOrder.indexOf(o1Binding);
                    int i2 = declarationOrder.indexOf(o2Binding);

                    if (i1 == -1 || i2 == -1) {
                        return 0;
                    }

                    return i1 - i2;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return comparator;
    }

    private static List<Object> lookupDeclarationOrder(TypeElement type) {

        List<Object> declarationOrder;
        try {
            Object binding = field(type, "_binding");
            ClassLoader classLoader = binding.getClass().getClassLoader();
            Class<?> sourceTypeBinding = classLoader.loadClass("org.eclipse.jdt.internal.compiler.lookup.SourceTypeBinding");
            Class<?> binaryTypeBinding = classLoader.loadClass("org.eclipse.jdt.internal.compiler.lookup.BinaryTypeBinding");

            declarationOrder = null;
            if (sourceTypeBinding.isAssignableFrom(binding.getClass())) {
                declarationOrder = findSourceTypeOrder(binding);
            } else if (binaryTypeBinding.isAssignableFrom(binding.getClass())) {
                declarationOrder = findBinaryTypeOrder(binding);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return declarationOrder;
    }

    private static List<Object> findBinaryTypeOrder(Object binding) throws Exception {
        Object binaryType = lookupBinaryType(binding);
        final Object[] sortedMethods = (Object[]) method(binaryType, "getMethods");

        List<Object> sortedElements = new ArrayList<>();
        if (sortedMethods != null) {
            sortedElements.addAll(Arrays.asList(sortedMethods));
        }
        final Object[] sortedFields = (Object[]) method(binaryType, "getFields");
        if (sortedFields != null) {
            sortedElements.addAll(Arrays.asList(sortedFields));
        }
        final Object[] sortedTypes = (Object[]) method(binaryType, "getMemberTypes", new Class<?>[0]);
        if (sortedTypes != null) {
            sortedElements.addAll(Arrays.asList(sortedTypes));
        }

        Collections.sort(sortedElements, new Comparator<Object>() {
            public int compare(Object o1, Object o2) {
                try {
                    int structOffset1 = (int) field(o1, "structOffset");
                    int structOffset2 = (int) field(o2, "structOffset");
                    return structOffset1 - structOffset2;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        ClassLoader classLoader = binding.getClass().getClassLoader();
        Class<?> binaryMethod = classLoader.loadClass("org.eclipse.jdt.internal.compiler.env.IBinaryMethod");
        Class<?> binaryField = classLoader.loadClass("org.eclipse.jdt.internal.compiler.env.IBinaryField");
        Class<?> nestedType = classLoader.loadClass("org.eclipse.jdt.internal.compiler.env.IBinaryNestedType");

        List<Object> bindings = new ArrayList<>();
        for (Object sortedElement : sortedElements) {
            Class<?> elementClass = sortedElement.getClass();
            if (binaryMethod.isAssignableFrom(elementClass)) {
                char[] selector = (char[]) method(sortedElement, "getSelector");
                Object[] foundBindings = (Object[]) method(binding, "getMethods", new Class<?>[]{char[].class}, selector);
                if (foundBindings == null || foundBindings.length == 0) {
                    continue;
                } else if (foundBindings.length == 1) {
                    bindings.add(foundBindings[0]);
                } else {
                    char[] idescriptor = (char[]) method(sortedElement, "getMethodDescriptor");
                    for (Object foundBinding : foundBindings) {
                        char[] descriptor = (char[]) method(foundBinding, "signature");
                        if (descriptor == null && idescriptor == null || Arrays.equals(descriptor, idescriptor)) {
                            bindings.add(foundBinding);
                            break;
                        }
                    }
                }
            } else if (binaryField.isAssignableFrom(elementClass)) {
                char[] selector = (char[]) method(sortedElement, "getName");
                Object foundField = method(binding, "getField", new Class<?>[]{char[].class, boolean.class}, selector, true);
                if (foundField != null) {
                    bindings.add(foundField);
                }
            } else if (nestedType.isAssignableFrom(elementClass)) {
                char[] selector = (char[]) method(sortedElement, "getSourceName");
                Object foundType = method(binding, "getMemberType", new Class<?>[]{char[].class}, selector);
                if (foundType != null) {
                    bindings.add(foundType);
                }
            } else {
                throw new AssertionError("Unexpected encountered type " + elementClass);
            }
        }

        return bindings;
    }

    private static Object lookupBinaryType(Object binding) throws Exception {
        Object lookupEnvironment = field(binding, "environment");
        Object compoundClassName = field(binding, "compoundName");
        Object nameEnvironment = field(lookupEnvironment, "nameEnvironment");
        Object nameEnvironmentAnswer = method(nameEnvironment, "findType", new Class<?>[]{char[][].class}, compoundClassName);
        Object binaryType = method(nameEnvironmentAnswer, "getBinaryType", new Class<?>[0]);
        return binaryType;
    }

    private static List<Object> findSourceTypeOrder(Object binding) throws Exception {
        Object referenceContext = field(field(binding, "scope"), "referenceContext");

        TreeMap<Integer, Object> orderedBindings = new TreeMap<>();

        collectSourceOrder(orderedBindings, referenceContext, "methods");
        collectSourceOrder(orderedBindings, referenceContext, "fields");
        collectSourceOrder(orderedBindings, referenceContext, "memberTypes");

        return new ArrayList<>(orderedBindings.values());
    }

    private static void collectSourceOrder(TreeMap<Integer, Object> orderedBindings, Object referenceContext, String fieldName) throws Exception {
        Object[] declarations = (Object[]) field(referenceContext, fieldName);
        if (declarations != null) {
            for (int i = 0; i < declarations.length; i++) {
                Integer declarationSourceStart = (Integer) field(declarations[i], "declarationSourceStart");
                orderedBindings.put(declarationSourceStart, field(declarations[i], "binding"));
            }
        }
    }
}
