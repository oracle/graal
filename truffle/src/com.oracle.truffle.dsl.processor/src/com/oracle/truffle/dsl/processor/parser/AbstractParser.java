/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.dsl.processor.CompileErrorException;
import com.oracle.truffle.dsl.processor.Log;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.library.LibraryData;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.NodeData;

/**
 * THIS IS NOT PUBLIC API.
 */
public abstract class AbstractParser<M extends MessageContainer> {

    protected final ProcessorContext context;
    protected final ProcessingEnvironment processingEnv;
    protected final TruffleTypes types = ProcessorContext.getInstance().getTypes();

    protected final Log log;

    public AbstractParser() {
        this.context = ProcessorContext.getInstance();
        this.processingEnv = context.getEnvironment();
        this.log = context.getLog();
    }

    public final M parse(Element element, boolean emitErrors) {
        M model = null;
        try {
            List<AnnotationMirror> mirrors = null;
            if (getAnnotationType() != null) {
                mirrors = ElementUtils.getRepeatedAnnotation(element.getAnnotationMirrors(), getAnnotationType());
            }

            model = parse(element, mirrors);
            if (model == null) {
                return null;
            }

            if (emitErrors) {
                model.emitMessages(context, log);
            }
            if (model instanceof NodeData || model instanceof LibraryData) {
                return model;
            } else {
                return emitErrors ? filterErrorElements(model) : model;
            }
        } catch (CompileErrorException e) {
            log.message(Kind.WARNING, element, null, null, "The truffle processor could not parse class due to error: %s", e.getMessage());
            return null;
        }
    }

    public final M parse(Element element) {
        return parse(element, true);
    }

    protected M filterErrorElements(M model) {
        return model.hasErrors() ? null : model;
    }

    protected abstract M parse(Element element, List<AnnotationMirror> mirror);

    public abstract DeclaredType getAnnotationType();

    public DeclaredType getRepeatAnnotationType() {
        return null;
    }

    public boolean isDelegateToRootDeclaredType() {
        return false;
    }

    public List<DeclaredType> getAllAnnotationTypes() {
        List<DeclaredType> list = new ArrayList<>();
        if (getAnnotationType() != null) {
            list.add(getAnnotationType());
        }
        list.addAll(getTypeDelegatedAnnotationTypes());
        return list;
    }

    public List<DeclaredType> getTypeDelegatedAnnotationTypes() {
        return Collections.emptyList();
    }

}
