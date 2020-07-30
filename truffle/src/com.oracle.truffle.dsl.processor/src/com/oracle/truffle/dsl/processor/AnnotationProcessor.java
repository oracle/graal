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
package com.oracle.truffle.dsl.processor;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import com.oracle.truffle.dsl.processor.generator.CodeTypeElementFactory;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedElement;
import com.oracle.truffle.dsl.processor.java.transform.FixWarningsVisitor;
import com.oracle.truffle.dsl.processor.java.transform.GenerateOverrideVisitor;
import com.oracle.truffle.dsl.processor.model.Template;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;

/**
 * THIS IS NOT PUBLIC API.
 */
public final class AnnotationProcessor<M extends Template> {

    private final AbstractParser<M> parser;
    private final CodeTypeElementFactory<M> factory;

    private final Set<String> processedElements = new HashSet<>();
    private final Map<String, Map<String, Element>> serviceRegistrations = new LinkedHashMap<>();

    AnnotationProcessor(AbstractParser<M> parser, CodeTypeElementFactory<M> factory) {
        this.parser = parser;
        this.factory = factory;
    }

    public AbstractParser<M> getParser() {
        return parser;
    }

    public Map<String, Map<String, Element>> getServiceRegistrations() {
        return serviceRegistrations;
    }

    public void registerService(String serviceBinaryName, String implBinaryName, Element sourceElement) {
        if (sourceElement instanceof GeneratedElement) {
            throw new IllegalArgumentException("Service source element must not be generated.");
        }
        Map<String, Element> services = serviceRegistrations.get(serviceBinaryName);
        if (services == null) {
            services = new LinkedHashMap<>();
            serviceRegistrations.put(serviceBinaryName, services);
        }
        services.put(implBinaryName, sourceElement);
    }

    public void process(Element element, boolean callback) {
        // since it is not guaranteed to be called only once by the compiler
        // we check for already processed elements to avoid errors when writing files.
        if (!callback) {
            String qualifiedName = ElementUtils.getQualifiedName((TypeElement) element);
            if (processedElements.contains(qualifiedName)) {
                return;
            }
            processedElements.add(qualifiedName);
        }

        processImpl(element, callback);
    }

    @SuppressWarnings({"unchecked"})
    private void processImpl(Element element, boolean callback) {
        ProcessorContext context = ProcessorContext.getInstance();
        TypeElement type = (TypeElement) element;

        M model = (M) context.getTemplate(type.asType(), false);
        boolean firstRun = !context.containsTemplate(type);

        if (firstRun || !callback) {
            context.registerTemplate(type, null);
            model = parser.parse(element);
            context.registerTemplate(type, model);

            if (model != null) {
                List<CodeTypeElement> units;
                try {
                    units = factory.create(ProcessorContext.getInstance(), this, model);
                } catch (Throwable e) {
                    RuntimeException ex = new RuntimeException(String.format("Failed to write code for %s.", ElementUtils.getQualifiedName(type)));
                    e.addSuppressed(ex);
                    throw e;
                }
                if (units == null || units.isEmpty()) {
                    return;
                }
                for (CodeTypeElement unit : units) {
                    unit.setGeneratorAnnotationMirror(model.getTemplateTypeAnnotation());
                    unit.setGeneratorElement(model.getTemplateType());

                    DeclaredType overrideType = (DeclaredType) context.getType(Override.class);
                    unit.accept(new GenerateOverrideVisitor(overrideType), null);
                    unit.accept(new FixWarningsVisitor(model.getTemplateType(), overrideType), null);

                    if (!callback) {
                        unit.accept(new CodeWriter(context.getEnvironment(), element), null);
                    }
                }
            }
        }
    }
}
