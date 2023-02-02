/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;

/**
 * {@link AbstractProcessor} subclass that provides extra functionality.
 */
public abstract class BaseProcessor extends AbstractProcessor {

    /**
     * Gets the processing environment available to this processor.
     */
    public ProcessingEnvironment env() {
        return processingEnv;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // In JDK 8, each annotation processing round has its own Elements object
        // so this cache must be cleared at the start of each round. As of JDK9,
        // a single Elements is preserved across all annotation processing rounds.
        // However, since both behaviors are compliant with the annotation processing
        // specification, we unconditionally clear the cache to be safe.
        types.clear();

        return doProcess(annotations, roundEnv);
    }

    /**
     * Does the actual work of the processor.
     */
    protected abstract boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv);

    private final Map<String, TypeElement> types = new HashMap<>();

    /**
     * Gets the {@link TypeMirror} for a given class name.
     *
     * @throws NoClassDefFoundError if the class cannot be resolved
     */
    public TypeMirror getType(String className) {
        return getTypeElement(className).asType();
    }

    /**
     * Gets the {@link TypeMirror} for a given class name.
     *
     * @return {@code null} if the class cannot be resolved
     */
    public TypeMirror getTypeOrNull(String className) {
        TypeElement element = getTypeElementOrNull(className);
        if (element == null) {
            return null;
        }
        return element.asType();
    }

    /**
     * Gets the {@link TypeElement} for a given class name.
     *
     * @throws NoClassDefFoundError if the class cannot be resolved
     */
    public TypeElement getTypeElement(String className) {
        TypeElement type = getTypeElementOrNull(className);
        if (type == null) {
            throw new NoClassDefFoundError(className);
        }
        return type;
    }

    /**
     * Gets the {@link TypeElement} for a given class name.
     *
     * @returns {@code null} if the class cannot be resolved
     */
    public TypeElement getTypeElementOrNull(String className) {
        TypeElement type = types.get(className);
        if (type == null) {
            type = processingEnv.getElementUtils().getTypeElement(className);
            if (type == null) {
                return null;
            }
            types.put(className, type);
        }
        return type;
    }

    /**
     * Converts a given {@link TypeMirror} to a {@link TypeElement}.
     *
     * @throws ClassCastException if type cannot be converted to a {@link TypeElement}
     */
    public TypeElement asTypeElement(TypeMirror type) {
        Element element = processingEnv.getTypeUtils().asElement(type);
        if (element == null) {
            throw new ClassCastException(type + " cannot be converted to a " + TypeElement.class.getName());
        }
        return (TypeElement) element;
    }

    /**
     * Regular expression for a qualified class name that assumes package names start with lowercase
     * and non-package components start with uppercase.
     */
    private static final Pattern QUALIFIED_CLASS_NAME_RE = Pattern.compile("(?:[a-z]\\w*\\.)+([A-Z].*)");

    /**
     * Gets the non-package component of a qualified class name.
     *
     * @throws IllegalArgumentException if {@code className} does not match
     *             {@link #QUALIFIED_CLASS_NAME_RE}
     */
    public static String getSimpleName(String className) {
        Matcher m = QUALIFIED_CLASS_NAME_RE.matcher(className);
        if (m.matches()) {
            return m.group(1);
        }
        throw new IllegalArgumentException("Class name \"" + className + "\" does not match pattern " + QUALIFIED_CLASS_NAME_RE);
    }

    /**
     * Gets the package component of a qualified class name.
     *
     * @throws IllegalArgumentException if {@code className} does not match
     *             {@link #QUALIFIED_CLASS_NAME_RE}
     */
    public static String getPackageName(String className) {
        String simpleName = getSimpleName(className);
        return className.substring(0, className.length() - simpleName.length() - 1);
    }

    /**
     * Gets the annotation of type {@code annotationType} directly present on {@code element}.
     *
     * @return {@code null} if an annotation of type {@code annotationType} is not on
     *         {@code element}
     */
    public AnnotationMirror getAnnotation(Element element, TypeMirror annotationType) {
        List<AnnotationMirror> mirrors = getAnnotations(element, annotationType);
        return mirrors.isEmpty() ? null : mirrors.get(0);
    }

    /**
     * Gets all annotations directly present on {@code element}.
     */
    public List<AnnotationMirror> getAnnotations(Element element, TypeMirror typeMirror) {
        List<AnnotationMirror> result = new ArrayList<>();
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (processingEnv.getTypeUtils().isSameType(mirror.getAnnotationType(), typeMirror)) {
                result.add(mirror);
            }
        }
        return result;
    }

    /**
     * Gets the value of the {@code name} element of {@code annotation} and converts it to a value
     * of type {@code type}.
     *
     * @param type the expected type of the element value. This must be a subclass of one of the
     *            types described by {@link AnnotationValue}.
     * @throws NoSuchElementException if {@code annotation} has no element named {@code name}
     * @throws ClassCastException if the value of the specified element cannot be converted to
     *             {@code type}
     */
    public static <T> T getAnnotationValue(AnnotationMirror annotation, String name, Class<T> type) {
        ExecutableElement valueMethod = null;
        for (ExecutableElement method : ElementFilter.methodsIn(annotation.getAnnotationType().asElement().getEnclosedElements())) {
            if (method.getSimpleName().toString().equals(name)) {
                valueMethod = method;
                break;
            }
        }

        if (valueMethod == null) {
            return null;
        }

        AnnotationValue value = annotation.getElementValues().get(valueMethod);
        if (value == null) {
            value = valueMethod.getDefaultValue();
        }

        return type.cast(value.getValue());
    }

    /**
     * Gets the value of the {@code name} array-typed element of {@code annotation} and converts it
     * to list of values of type {@code type}.
     *
     * @param componentType the expected component type of the element value. This must be a
     *            subclass of one of the types described by {@link AnnotationValue}.
     * @throws NoSuchElementException if {@code annotation} has no element named {@code name}
     * @throws ClassCastException if the value of the specified element is not an array whose
     *             components cannot be converted to {@code componentType}
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> getAnnotationValueList(AnnotationMirror annotation, String name, Class<T> componentType) {
        List<? extends AnnotationValue> values = getAnnotationValue(annotation, name, List.class);
        List<T> result = new ArrayList<>();

        if (values != null) {
            for (AnnotationValue value : values) {
                result.add(componentType.cast(value.getValue()));
            }
        }
        return result;
    }

    /**
     * Return the fully qualified name in the JVM format, including $ separator for inner classes.
     */
    String dollarQualifiedName(TypeElement element) {
        String qualifiedName = element.getSimpleName().toString();
        Element enclosing = element.getEnclosingElement();
        while (enclosing != null) {
            final ElementKind kind = enclosing.getKind();
            if (kind == ElementKind.PACKAGE) {
                qualifiedName = ((PackageElement) enclosing).getQualifiedName().toString() + "." + qualifiedName;
                break;
            } else if (kind == ElementKind.CLASS || kind == ElementKind.INTERFACE) {
                qualifiedName = ((TypeElement) enclosing).getSimpleName().toString() + "$" + qualifiedName;
                enclosing = enclosing.getEnclosingElement();
            } else {
                String msg = String.format("Cannot generate provider descriptor for service class %s as it is not nested in a package, class or interface",
                                element.getQualifiedName());
                processingEnv.getMessager().printMessage(Kind.ERROR, msg);
            }
        }
        return qualifiedName;
    }
}
