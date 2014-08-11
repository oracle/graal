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
package com.oracle.truffle.dsl.processor.compiler;

import java.util.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.*;

public class JDTCompiler extends AbstractCompiler {

    public static boolean isValidElement(Element currentElement) {
        try {
            Class<?> elementClass = Class.forName("org.eclipse.jdt.internal.compiler.apt.model.ElementImpl");
            return elementClass.isAssignableFrom(currentElement.getClass());
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public List<? extends Element> getAllMembersInDeclarationOrder(ProcessingEnvironment environment, TypeElement type) {
        return sortBySourceOrder(new ArrayList<>(environment.getElementUtils().getAllMembers(type)));
    }

    public List<? extends Element> getEnclosedElementsInDeclarationOrder(TypeElement type) {
        return sortBySourceOrder(new ArrayList<>(type.getEnclosedElements()));
    }

    private static List<? extends Element> sortBySourceOrder(List<Element> elements) {
        final Map<TypeElement, List<Object>> declarationOrders = new HashMap<>();

        Collections.sort(elements, new Comparator<Element>() {
            public int compare(Element o1, Element o2) {
                try {
                    TypeMirror enclosing1 = o1.getEnclosingElement().asType();
                    TypeMirror enclosing2 = o2.getEnclosingElement().asType();

                    if (Utils.typeEquals(enclosing1, enclosing2)) {
                        List<Object> declarationOrder = lookupDeclarationOrder(declarationOrders, (TypeElement) o1.getEnclosingElement());
                        if (declarationOrder == null) {
                            return 0;
                        }
                        Object o1Binding = field(o1, "_binding");
                        Object o2Binding = field(o2, "_binding");

                        int i1 = declarationOrder.indexOf(o1Binding);
                        int i2 = declarationOrder.indexOf(o2Binding);

                        return i1 - i2;
                    } else {
                        if (Utils.isSubtype(enclosing1, enclosing2)) {
                            return 1;
                        } else if (Utils.isSubtype(enclosing2, enclosing1)) {
                            return -1;
                        } else {
                            return 0;
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        return elements;
    }

    private static List<Object> lookupDeclarationOrder(Map<TypeElement, List<Object>> declarationOrders, TypeElement type) throws Exception, ClassNotFoundException {
        if (declarationOrders.containsKey(type)) {
            return declarationOrders.get(type);
        }

        Object binding = field(type, "_binding");
        Class<?> sourceTypeBinding = Class.forName("org.eclipse.jdt.internal.compiler.lookup.SourceTypeBinding");
        Class<?> binaryTypeBinding = Class.forName("org.eclipse.jdt.internal.compiler.lookup.BinaryTypeBinding");

        List<Object> declarationOrder = null;
        if (sourceTypeBinding.isAssignableFrom(binding.getClass())) {
            declarationOrder = findSourceTypeOrder(binding);
        } else if (binaryTypeBinding.isAssignableFrom(binding.getClass())) {
            declarationOrder = findBinaryTypeOrder(binding);
        }

        declarationOrders.put(type, declarationOrder);

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
        final Object[] sortedTypes = (Object[]) method(binaryType, "getMemberTypes", new Class[0]);
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

        Class<?> binaryMethod = Class.forName("org.eclipse.jdt.internal.compiler.env.IBinaryMethod");
        Class<?> binaryField = Class.forName("org.eclipse.jdt.internal.compiler.env.IBinaryField");
        Class<?> nestedType = Class.forName("org.eclipse.jdt.internal.compiler.env.IBinaryNestedType");

        List<Object> bindings = new ArrayList<>();
        for (Object sortedElement : sortedElements) {
            Class<?> elementClass = sortedElement.getClass();
            if (binaryMethod.isAssignableFrom(elementClass)) {
                char[] selector = (char[]) method(sortedElement, "getSelector");
                Object[] foundBindings = (Object[]) method(binding, "getMethods", new Class[]{char[].class}, selector);
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
                Object foundField = method(binding, "getField", new Class[]{char[].class, boolean.class}, selector, true);
                if (foundField != null) {
                    bindings.add(foundField);
                }
            } else if (nestedType.isAssignableFrom(elementClass)) {
                char[] selector = (char[]) method(sortedElement, "getSourceName");
                Object foundType = method(binding, "getMemberType", new Class[]{char[].class}, selector);
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
        Object nameEnvironmentAnswer = method(nameEnvironment, "findType", new Class[]{char[][].class}, compoundClassName);
        Object binaryType = method(nameEnvironmentAnswer, "getBinaryType", new Class[0]);
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

    @Override
    public String getMethodBody(ProcessingEnvironment env, ExecutableElement method) {
        try {

            char[] source = getSource(method);
            if (source == null) {
                return null;
            }

            /*
             * AbstractMethodDeclaration decl =
             * ((MethodBinding)(((ElementImpl)method)._binding)).sourceMethod(); int bodyStart =
             * decl.bodyStart; int bodyEnd = decl.bodyEnd;
             */
            Object decl = method(field(method, "_binding"), "sourceMethod");
            int bodyStart = (int) field(decl, "bodyStart");
            int bodyEnd = (int) field(decl, "bodyEnd");

            int length = bodyEnd - bodyStart;
            char[] target = new char[length];
            System.arraycopy(source, bodyStart, target, 0, length);

            return new String(target);
        } catch (Exception e) {
            return Utils.printException(e);
        }
    }

    private static char[] getSource(Element element) throws Exception {
        /*
         * Binding binding = ((ElementImpl)element)._binding; char[] source = null; if (binding
         * instanceof MethodBinding) { source = ((MethodBinding)
         * binding).sourceMethod().compilationResult.getCompilationUnit().getContents(); } else if
         * (binding instanceof SourceTypeBinding) { source =
         * ((SourceTypeBinding)binding).scope.referenceContext
         * .compilationResult.compilationUnit.getContents(); } return source;
         */

        Object binding = field(element, "_binding");
        Class<?> methodBindingClass = Class.forName("org.eclipse.jdt.internal.compiler.lookup.MethodBinding");
        Class<?> referenceBindingClass = Class.forName("org.eclipse.jdt.internal.compiler.lookup.SourceTypeBinding");

        char[] source = null;
        if (methodBindingClass.isAssignableFrom(binding.getClass())) {
            Object sourceMethod = method(binding, "sourceMethod");
            if (sourceMethod == null) {
                return null;
            }
            source = (char[]) method(method(field(sourceMethod, "compilationResult"), "getCompilationUnit"), "getContents");
        } else if (referenceBindingClass.isAssignableFrom(binding.getClass())) {
            source = (char[]) method(field(field(field(field(binding, "scope"), "referenceContext"), "compilationResult"), "compilationUnit"), "getContents");
        }
        return source;
    }

    @Override
    public String getHeaderComment(ProcessingEnvironment env, Element type) {
        try {
            char[] source = getSource(type);
            if (source == null) {
                return null;
            }
            return parseHeader(new String(source));
        } catch (Exception e) {
            return Utils.printException(e);
        }
    }

}
