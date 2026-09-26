/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.processor;

import static jdk.graal.compiler.processor.AbstractProcessor.getAnnotationValue;
import static jdk.graal.compiler.processor.AbstractProcessor.getSimpleName;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;

import jdk.graal.compiler.processor.AbstractProcessor;

/**
 * Handler for the {@value #FOLD_CLASS_NAME} annotation.
 */
public final class FoldHandler extends AnnotationHandler {

    static final String FOLD_CLASS_NAME = "jdk.graal.compiler.api.replacements.Fold";
    static final String INJECTED_PARAMETER_CLASS_NAME = "jdk.graal.compiler.api.replacements.Fold.InjectedParameter";

    public FoldHandler(AbstractProcessor processor) {
        super(processor, FOLD_CLASS_NAME);
    }

    @Override
    public void process(Element element, AnnotationMirror annotation, PluginGenerator generator) {
        if (element.getKind() != ElementKind.METHOD) {
            assert false : "Element is guaranteed to be a method.";
            return;
        }

        ExecutableElement foldMethod = (ExecutableElement) element;
        TypeMirror resolverMirror = getAnnotationValue(annotation, "resolver", TypeMirror.class);
        TypeElement resolver = processor.asTypeElement(resolverMirror);
        if (foldMethod.getReturnType().getKind() == TypeKind.VOID) {
            processor.env().getMessager().printMessage(Kind.ERROR,
                            String.format("A @%s method must not be void as it won't yield a compile-time constant (the reason for supporting folding!).", getSimpleName(FOLD_CLASS_NAME)), element,
                            annotation);
        } else if (foldMethod.getModifiers().contains(Modifier.PRIVATE)) {
            processor.env().getMessager().printMessage(Kind.ERROR, String.format("A @%s method must not be private.", getSimpleName(FOLD_CLASS_NAME)), element, annotation);
        } else if (!validateResolver(resolver)) {
            processor.env().getMessager().printMessage(Kind.ERROR, String.format("The @%s resolver %s must be a concrete class with a declared zero-argument constructor and must be static if nested.",
                            getSimpleName(FOLD_CLASS_NAME), resolver.getQualifiedName()), foldMethod, annotation);
        } else {
            generator.addPlugin(new GeneratedFoldPlugin(foldMethod));
        }
    }

    private static boolean validateResolver(TypeElement resolver) {
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(resolver.getEnclosedElements());
        boolean hasNoArgumentConstructor = constructors.stream().anyMatch(constructor -> constructor.getParameters().isEmpty());
        return resolver.getKind() == ElementKind.CLASS && !resolver.getModifiers().contains(Modifier.ABSTRACT) &&
                        (!resolver.getNestingKind().isNested() || resolver.getModifiers().contains(Modifier.STATIC)) && hasNoArgumentConstructor;
    }
}
