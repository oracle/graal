/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.processor;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

/**
 * {@link javax.annotation.processing.AbstractProcessor} subclass that provides extra functionality.
 */
@SuppressFBWarnings(value = "NM_SAME_SIMPLE_NAME_AS_SUPERCLASS", //
                justification = "We want this type to be found when someone is writing a new Graal annotation processor")
public abstract class AbstractProcessor extends javax.annotation.processing.AbstractProcessor {

    /**
     * Gets the processing environment available to this processor.
     */
    public ProcessingEnvironment env() {
        return processingEnv;
    }

    @Override
    public final SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return doProcess(annotations, roundEnv);
    }

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

    public DeclaredType getDeclaredType(String className) {
        return (DeclaredType) getType(className);
    }

    public DeclaredType getDeclaredTypeOrNull(String className) {
        return (DeclaredType) getTypeOrNull(className);
    }

    public TypeMirror getType(Class<?> element) {
        if (element.isArray()) {
            return processingEnv.getTypeUtils().getArrayType(getType(element.getComponentType()));
        }
        if (element.isPrimitive()) {
            if (element == void.class) {
                return processingEnv.getTypeUtils().getNoType(TypeKind.VOID);
            }
            TypeKind typeKind;
            if (element == boolean.class) {
                typeKind = TypeKind.BOOLEAN;
            } else if (element == byte.class) {
                typeKind = TypeKind.BYTE;
            } else if (element == short.class) {
                typeKind = TypeKind.SHORT;
            } else if (element == char.class) {
                typeKind = TypeKind.CHAR;
            } else if (element == int.class) {
                typeKind = TypeKind.INT;
            } else if (element == long.class) {
                typeKind = TypeKind.LONG;
            } else if (element == float.class) {
                typeKind = TypeKind.FLOAT;
            } else if (element == double.class) {
                typeKind = TypeKind.DOUBLE;
            } else {
                assert false;
                return null;
            }
            return processingEnv.getTypeUtils().getPrimitiveType(typeKind);
        } else {
            TypeElement typeElement = getTypeElement(element.getCanonicalName());
            if (typeElement == null) {
                return null;
            }
            return processingEnv.getTypeUtils().erasure(typeElement.asType());
        }
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
     * @return {@code null} if the class cannot be resolved
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

    public static PackageElement getPackage(Element element) {
        Element e = element.getEnclosingElement();
        while (e != null && e.getKind() != ElementKind.PACKAGE) {
            e = e.getEnclosingElement();
        }
        return (PackageElement) e;
    }

    public static PrintWriter createSourceFile(String pkg, String relativeName, Filer filer, Element... originatingElements) {
        try {
            /* Ensure Unix line endings to comply with code style guide checked by Checkstyle. */
            String className = pkg + "." + relativeName;
            JavaFileObject sourceFile = filer.createSourceFile(className, originatingElements);
            return new PrintWriter(sourceFile.openWriter()) {
                @Override
                public void println() {
                    print("\n");
                }
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a {@code META-INF/providers/<providerClassName>} file whose contents are a single
     * line containing {@code serviceClassName}.
     */
    public void createProviderFile(String providerClassName, String serviceClassName, Element... originatingElements) {
        assert originatingElements.length > 0;
        String filename = "META-INF/providers/" + providerClassName;
        try {
            FileObject file = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filename, originatingElements);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(file.openOutputStream(), "UTF-8"));
            writer.println(serviceClassName);
            writer.close();
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(isBug367599(e) ? Kind.NOTE : Kind.ERROR, e.getMessage(), originatingElements[0]);
        }
    }

    /**
     * Determines if a given exception is (most likely) caused by
     * <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=367599">Bug 367599</a>.
     */
    private static boolean isBug367599(Throwable t) {
        if (t instanceof FilerException) {
            for (StackTraceElement ste : t.getStackTrace()) {
                if (ste.toString().contains("org.eclipse.jdt.internal.apt.pluggable.core.filer.IdeFilerImpl.create")) {
                    // See: https://bugs.eclipse.org/bugs/show_bug.cgi?id=367599
                    return true;
                }
            }
        }
        return t.getCause() != null && isBug367599(t.getCause());
    }

    public Types typeUtils() {
        return env().getTypeUtils();
    }
}
