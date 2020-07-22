/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Field;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ElementKind;
import javax.tools.Diagnostic.Kind;

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

    @Override
    public List<? extends Element> getEnclosedElementsInDeclarationOrder(TypeElement type) {
        return sortBySourceOrder(newElementList(type.getEnclosedElements()));
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

    @Override
    protected boolean emitDeprecationWarningImpl(ProcessingEnvironment environment, Element element) {
        try {
            Object binding = field(element, "_binding");
            if (binding == null) {
                return false;
            }
            Object astNode = getASTNode(element);
            if (astNode == null) {
                return false;
            }
            Object problemReporter = field(method(environment, "getCompiler"), "problemReporter");
            Object prev = useSource(problemReporter, astNode);
            try {
                return reportProblem(problemReporter, element.getKind(), binding, astNode);
            } finally {
                useSource(problemReporter, prev);
            }
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static Object getASTNode(Element element) throws ReflectiveOperationException {
        Class<?> baseMessagerImplClass = Class.forName("org.eclipse.jdt.internal.compiler.apt.dispatch.BaseMessagerImpl", false, element.getClass().getClassLoader());
        Object problem = staticMethod(baseMessagerImplClass, "createProblem",
                        new Class<?>[]{Kind.class, CharSequence.class, Element.class, AnnotationMirror.class, AnnotationValue.class},
                        Kind.WARNING, "", element, null, null);
        return field(problem, "_referenceContext");
    }

    private static Object useSource(Object problemReporter, Object astNode) throws ReflectiveOperationException {
        Field referenceContextField = problemReporter.getClass().getField("referenceContext");
        Object res = referenceContextField.get(problemReporter);
        referenceContextField.set(problemReporter, astNode);
        return res;
    }

    private static boolean reportProblem(Object problemReporter, ElementKind kind, Object binding, Object astNode) throws ReflectiveOperationException {
        ClassLoader cl = binding.getClass().getClassLoader();
        Class<?> astNodeClass = Class.forName("org.eclipse.jdt.internal.compiler.ast.ASTNode", false, cl);
        if (kind.isClass() || kind.isInterface()) {
            Class<?> typeBindingClass = Class.forName("org.eclipse.jdt.internal.compiler.lookup.TypeBinding", false, cl);
            method(problemReporter, "deprecatedType", new Class<?>[]{typeBindingClass, astNodeClass}, binding, astNode);
            return true;
        } else if (kind.isField()) {
            Class<?> fieldBindingClass = Class.forName("org.eclipse.jdt.internal.compiler.lookup.FieldBinding", false, cl);
            method(problemReporter, "deprecatedField", new Class<?>[]{fieldBindingClass, astNodeClass}, binding, astNode);
            return true;
        } else if (kind == ElementKind.METHOD || kind == ElementKind.CONSTRUCTOR) {
            Class<?> methodBindingClass = Class.forName("org.eclipse.jdt.internal.compiler.lookup.MethodBinding", false, cl);
            method(problemReporter, "deprecatedMethod", new Class<?>[]{methodBindingClass, astNodeClass}, binding, astNode);
            return true;
        } else {
            return false;
        }
    }
}
