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
package com.oracle.truffle.codegen.processor.ext;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.util.*;

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.api.*;
import com.oracle.truffle.codegen.processor.api.element.*;

public class ExtensionParser {

    private final Map<String, ExtensionProcessor> extensions = new HashMap<>();
    private final ProcessorContext context;
    private final ExtensionCodeElementFactory factory;
    private final ExtensionContextImpl extensionContext;

    public ExtensionParser(ProcessorContext context) {
        this.context = context;
        this.factory = new ExtensionCodeElementFactory(context);
        this.extensionContext = new ExtensionContextImpl(context.getEnvironment(), null, factory);
    }

    public List<WritableElement> parseAll(TypeElement typeElement, List<? extends Element> elements) {
        List<WritableElement> generatedMethods = new ArrayList<>();
        parseElement(generatedMethods, typeElement);

        List<? extends ExecutableElement> methods = ElementFilter.methodsIn(elements);
        for (ExecutableElement method : methods) {
            for (VariableElement var : method.getParameters()) {
                parseElement(generatedMethods, var);
            }
            parseElement(generatedMethods, method);
        }

        return generatedMethods;
    }

    private void parseElement(List<WritableElement> elements, Element element) {
        List<? extends AnnotationMirror> mirrors = element.getAnnotationMirrors();
        for (AnnotationMirror mirror : mirrors) {
            ExtensionProcessor processor = findProcessor(element, mirror);
            if (processor != null) {
                try {
                    factory.generatorAnnotationMirror = mirror;
                    factory.generatorElement = element;
                    processor.process(extensionContext, mirror, element);
                    elements.addAll(extensionContext.returnElements());
                } catch (Throwable e) {
                    context.getLog().error(element, mirror, "Processor for '%s' failed with exception: \n\n%s.", Utils.getQualifiedName(mirror.getAnnotationType()), Utils.printException(e));
                } finally {
                    factory.generatorAnnotationMirror = null;
                    factory.generatorElement = null;
                }
            }
        }
    }

    private ExtensionProcessor findProcessor(Element element, AnnotationMirror mirror) {
        String processorName = Utils.getQualifiedName(mirror.getAnnotationType());
        ExtensionProcessor processor = null;
        if (extensions.containsKey(processorName)) {
            processor = extensions.get(processorName);
        } else {
            AnnotationMirror foundExtension = Utils.findAnnotationMirror(context.getEnvironment(), mirror.getAnnotationType().asElement(), ExtensionAnnotation.class);
            if (foundExtension != null) {
                String className = Utils.getAnnotationValueString(foundExtension, "processorClassName");
                Class<?> processorClass;
                try {
                    processorClass = Class.forName(className);
                } catch (ClassNotFoundException e) {
                    context.getLog().error(element, mirror, "Could not find processor class '%s' configured in '@%s'.", className, processorName);
                    return null;
                }
                try {
                    processor = (ExtensionProcessor) processorClass.newInstance();
                } catch (InstantiationException e) {
                    context.getLog().error(element, mirror, "Could not instantiate processor class '%s' configured in '@%s'.", className, processorName);
                    return null;
                } catch (IllegalAccessException e) {
                    context.getLog().error(element, mirror, "Could not access processor class '%s' configured in '@%s'.", className, processorName);
                    return null;
                } catch (ClassCastException e) {
                    context.getLog().error(element, mirror, "Processor class '%s' configured in '@%s' does not implement '%s'.", className, processorName, ExtensionProcessor.class.getName());
                    return null;
                }
            }
            extensions.put(processorName, processor);
        }
        return processor;
    }

}
