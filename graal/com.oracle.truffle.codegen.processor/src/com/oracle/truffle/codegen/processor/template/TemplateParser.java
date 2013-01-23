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
package com.oracle.truffle.codegen.processor.template;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.util.*;

import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.ext.*;

public abstract class TemplateParser<M extends Template> extends AbstractParser<M> {

    private final ExtensionParser extensionParser;

    public TemplateParser(ProcessorContext c) {
        super(c);
        extensionParser = new ExtensionParser(c);
    }

    public ExtensionParser getExtensionParser() {
        return extensionParser;
    }

    protected boolean verifyExclusiveMethodAnnotation(TypeElement type, Class<?>... annotationTypes) {
        boolean valid = true;
        List<ExecutableElement> methods = ElementFilter.methodsIn(type.getEnclosedElements());
        for (ExecutableElement method : methods) {
            List<AnnotationMirror> foundAnnotations = new ArrayList<>();
            for (int i = 0; i < annotationTypes.length; i++) {
                Class<?> annotationType = annotationTypes[i];
                AnnotationMirror mirror = Utils.findAnnotationMirror(context.getEnvironment(), method, annotationType);
                if (mirror != null) {
                    foundAnnotations.add(mirror);
                }
            }
            if (foundAnnotations.size() > 1) {
                List<String> annotationNames = new ArrayList<>();
                for (AnnotationMirror mirror : foundAnnotations) {
                    annotationNames.add("@" + Utils.getSimpleName(mirror.getAnnotationType()));
                }

                for (AnnotationMirror mirror : foundAnnotations) {
                    context.getLog().error(method, mirror, "Non exclusive usage of annotations %s.", annotationNames);
                }
                valid = false;
            }
        }
        return valid;
    }

    protected boolean verifyTemplateType(TypeElement template, AnnotationMirror annotation) {
        // annotation type on class path!?
        boolean valid = true;
        TypeElement annotationTypeElement = processingEnv.getElementUtils().getTypeElement(getAnnotationType().getCanonicalName());
        if (annotationTypeElement == null) {
            log.error(template, annotation, "Required class " + getAnnotationType().getName() + " is not on the classpath.");
            valid = false;
        }
        if (template.getModifiers().contains(Modifier.PRIVATE)) {
            log.error(template, annotation, "The annotated class must have at least package protected visibility.");
            valid = false;
        }

        if (template.getModifiers().contains(Modifier.FINAL)) {
            log.error(template, annotation, "The annotated class must not be final.");
            valid = false;
        }

        return valid;
    }

}
