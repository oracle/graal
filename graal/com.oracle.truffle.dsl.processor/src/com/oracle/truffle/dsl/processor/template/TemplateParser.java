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
package com.oracle.truffle.dsl.processor.template;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.util.*;

import com.oracle.truffle.dsl.processor.*;

public abstract class TemplateParser<M extends Template> extends AbstractParser<M> {

    public TemplateParser(ProcessorContext c) {
        super(c);
    }

    protected void verifyExclusiveMethodAnnotation(Template template, Class<?>... annotationTypes) {
        List<ExecutableElement> methods = ElementFilter.methodsIn(template.getTemplateType().getEnclosedElements());
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

                template.addError("Non exclusive usage of annotations %s.", annotationNames);
            }
        }
    }

}
