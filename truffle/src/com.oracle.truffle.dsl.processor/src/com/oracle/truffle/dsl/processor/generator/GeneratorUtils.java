/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.generator;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.fromTypeMirror;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeNames;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedElement;
import com.oracle.truffle.dsl.processor.model.Template;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;

public class GeneratorUtils {

    static CodeTree createTransferToInterpreterAndInvalidate() {
        ProcessorContext context = ProcessorContext.getInstance();
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startStatement().startStaticCall(context.getType(CompilerDirectives.class), "transferToInterpreterAndInvalidate").end().end();
        return builder.build();
    }

    public static CodeTree createTransferToInterpreter() {
        ProcessorContext context = ProcessorContext.getInstance();
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startStatement().startStaticCall(context.getType(CompilerDirectives.class), "transferToInterpreter").end().end();
        return builder.build();
    }

    public static CodeExecutableElement createConstructorUsingFields(Set<Modifier> modifiers, CodeTypeElement clazz) {
        TypeElement superClass = fromTypeMirror(clazz.getSuperclass());
        ExecutableElement constructor = findConstructor(superClass);
        return createConstructorUsingFields(modifiers, clazz, constructor);
    }

    public static void mergeSupressWarnings(CodeElement<?> element, String... addWarnings) {
        List<String> mergedWarnings = Arrays.asList(addWarnings);
        AnnotationMirror currentWarnings = ElementUtils.findAnnotationMirror(element, SuppressWarnings.class);
        if (currentWarnings != null) {
            List<String> currentValues = ElementUtils.getAnnotationValueList(String.class, currentWarnings, "value");
            if (currentValues != null && !currentValues.isEmpty()) {
                Set<String> warnings = new LinkedHashSet<>(mergedWarnings);
                warnings.addAll(currentValues);
                mergedWarnings = warnings.stream().collect(Collectors.toList());
            }
        }
        DeclaredType suppressWarnings = ProcessorContext.getInstance().getDeclaredType(SuppressWarnings.class);
        CodeAnnotationMirror mirror = new CodeAnnotationMirror(suppressWarnings);
        if (mergedWarnings.size() == 1) {
            mirror.setElementValue(mirror.findExecutableElement("value"), new CodeAnnotationValue(mergedWarnings.iterator().next()));
        } else {
            List<AnnotationValue> values = new ArrayList<>();
            for (String warning : mergedWarnings) {
                values.add(new CodeAnnotationValue(warning));
            }
            mirror.setElementValue(mirror.findExecutableElement("value"), new CodeAnnotationValue(values));
        }

        if (currentWarnings != null) {
            ((CodeElement<?>) element).getAnnotationMirrors().remove(currentWarnings);
        }
        if (!mergedWarnings.isEmpty()) {
            ((CodeElement<?>) element).getAnnotationMirrors().add(mirror);
        }
    }

    public static CodeExecutableElement createConstructorUsingFields(Set<Modifier> modifiers, CodeTypeElement clazz, ExecutableElement constructor) {
        CodeExecutableElement method = new CodeExecutableElement(modifiers, null, clazz.getSimpleName().toString());
        CodeTreeBuilder builder = method.createBuilder();
        if (constructor != null && constructor.getParameters().size() > 0) {
            builder.startStatement();
            builder.startSuperCall();
            for (VariableElement parameter : constructor.getParameters()) {
                method.addParameter(new CodeVariableElement(parameter.asType(), parameter.getSimpleName().toString()));
                builder.string(parameter.getSimpleName().toString());
            }
            builder.end(); // super
            builder.end(); // statement
        }

        for (VariableElement field : clazz.getFields()) {
            if (field.getModifiers().contains(STATIC)) {
                continue;
            }
            String fieldName = field.getSimpleName().toString();
            method.addParameter(new CodeVariableElement(field.asType(), fieldName));
            builder.startStatement();
            builder.string("this.");
            builder.string(fieldName);
            builder.string(" = ");
            builder.string(fieldName);
            builder.end(); // statement
        }

        return method;
    }

    private static ExecutableElement findConstructor(TypeElement clazz) {
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(clazz.getEnclosedElements());
        if (constructors.isEmpty()) {
            return null;
        } else {
            return constructors.get(0);
        }
    }

    public static CodeExecutableElement createSuperConstructor(TypeElement type, ExecutableElement element) {
        if (element.getModifiers().contains(Modifier.PRIVATE)) {
            return null;
        }
        CodeExecutableElement executable = CodeExecutableElement.clone(element);
        executable.setReturnType(null);
        executable.setSimpleName(CodeNames.of(type.getSimpleName().toString()));
        CodeTreeBuilder b = executable.createBuilder();
        b.startStatement();
        b.startSuperCall();
        for (VariableElement v : element.getParameters()) {
            b.string(v.getSimpleName().toString());
        }
        b.end();
        b.end();

        return executable;
    }

    public static CodeTypeElement createClass(Template sourceModel, TemplateMethod sourceMethod, Set<Modifier> modifiers, String simpleName, TypeMirror superType) {
        TypeElement templateType = sourceModel.getTemplateType();

        ProcessorContext context = ProcessorContext.getInstance();

        PackageElement pack = ElementUtils.findPackageElement(templateType);
        CodeTypeElement clazz = new CodeTypeElement(modifiers, ElementKind.CLASS, pack, simpleName);
        TypeMirror resolvedSuperType = superType;
        if (resolvedSuperType == null) {
            resolvedSuperType = context.getType(Object.class);
        }
        clazz.setSuperClass(resolvedSuperType);

        CodeAnnotationMirror generatedByAnnotation = new CodeAnnotationMirror((DeclaredType) context.getType(GeneratedBy.class));
        Element generatedByElement = templateType;
        while (generatedByElement instanceof GeneratedElement) {
            generatedByElement = generatedByElement.getEnclosingElement();
        }
        if (generatedByElement instanceof TypeElement) {
            generatedByAnnotation.setElementValue(generatedByAnnotation.findExecutableElement("value"), new CodeAnnotationValue(generatedByElement.asType()));
            if (sourceMethod != null) {
                generatedByAnnotation.setElementValue(generatedByAnnotation.findExecutableElement("methodName"), new CodeAnnotationValue(ElementUtils.createReferenceName(sourceMethod.getMethod())));
            }
            clazz.addAnnotationMirror(generatedByAnnotation);
        }
        return clazz;
    }

    public static void addGeneratedBy(ProcessorContext context, CodeTypeElement generatedType, TypeElement generatedByType) {
        DeclaredType generatedBy = (DeclaredType) context.getType(GeneratedBy.class);
        // only do this if generatedBy is on the classpath.
        if (generatedBy != null) {
            CodeAnnotationMirror generatedByAnnotation = new CodeAnnotationMirror(generatedBy);
            generatedByAnnotation.setElementValue(generatedByAnnotation.findExecutableElement("value"), new CodeAnnotationValue(generatedByType.asType()));
            generatedType.addAnnotationMirror(generatedByAnnotation);
        }
    }

    static List<ExecutableElement> findUserConstructors(TypeMirror nodeType) {
        List<ExecutableElement> constructors = new ArrayList<>();
        for (ExecutableElement constructor : ElementFilter.constructorsIn(ElementUtils.fromTypeMirror(nodeType).getEnclosedElements())) {
            if (constructor.getModifiers().contains(PRIVATE)) {
                continue;
            }
            if (isCopyConstructor(constructor)) {
                continue;
            }
            constructors.add(constructor);
        }

        if (constructors.isEmpty()) {
            constructors.add(new CodeExecutableElement(null, ElementUtils.getSimpleName(nodeType)));
        }

        return constructors;
    }

    static boolean isCopyConstructor(ExecutableElement element) {
        if (element.getParameters().size() != 1) {
            return false;
        }
        VariableElement var = element.getParameters().get(0);
        TypeElement enclosingType = ElementUtils.findNearestEnclosingType(var).orElseThrow(AssertionError::new);
        if (ElementUtils.typeEquals(var.asType(), enclosingType.asType())) {
            return true;
        }
        List<TypeElement> types = ElementUtils.getDirectSuperTypes(enclosingType);
        for (TypeElement type : types) {
            if (!(type instanceof CodeTypeElement)) {
                // no copy constructors which are not generated types
                return false;
            }

            if (ElementUtils.typeEquals(var.asType(), type.asType())) {
                return true;
            }
        }
        return false;
    }

    public static CodeExecutableElement override(Class<?> type, String methodName) {
        ExecutableElement method = ElementUtils.findMethod(type, methodName);
        if (method == null) {
            return null;
        }
        return CodeExecutableElement.clone(method);
    }

}
