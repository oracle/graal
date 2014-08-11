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
package com.oracle.truffle.dsl.processor.parser;

import java.lang.annotation.*;
import java.util.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.model.*;
import com.oracle.truffle.dsl.processor.model.MessageContainer.Message;

/**
 * THIS IS NOT PUBLIC API.
 */
public abstract class AbstractParser<M extends Template> {

    protected final ProcessorContext context;
    protected final ProcessingEnvironment processingEnv;

    protected final Log log;

    public AbstractParser() {
        this.context = ProcessorContext.getInstance();
        this.processingEnv = context.getEnvironment();
        this.log = context.getLog();
    }

    public final M parse(Element element) {
        M model = null;
        try {
            AnnotationMirror mirror = null;
            if (getAnnotationType() != null) {
                mirror = ElementUtils.findAnnotationMirror(processingEnv, element.getAnnotationMirrors(), getAnnotationType());
            }

            if (!context.getTruffleTypes().verify(context, element, mirror)) {
                return null;
            }
            model = parse(element, mirror);
            if (model == null) {
                return null;
            }

            redirectMessages(new HashSet<MessageContainer>(), model, model);
            model.emitMessages(context, log);
            return filterErrorElements(model);
        } catch (CompileErrorException e) {
            log.message(Kind.WARNING, element, null, null, "The truffle processor could not parse class due to error: %s", e.getMessage());
            return null;
        }
    }

    private void redirectMessages(Set<MessageContainer> visitedSinks, MessageContainer model, MessageContainer baseContainer) {
        List<Message> messages = model.getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (!ElementUtils.isEnclosedIn(baseContainer.getMessageElement(), message.getOriginalContainer().getMessageElement())) {
                // redirect message
                MessageContainer original = message.getOriginalContainer();
                String text = wrapText(original.getMessageElement(), original.getMessageAnnotation(), message.getText());
                Message redirectedMessage = new Message(null, baseContainer, text, message.getKind());
                model.getMessages().remove(i);
                baseContainer.getMessages().add(redirectedMessage);
            }
        }

        for (MessageContainer childContainer : model) {
            if (visitedSinks.contains(childContainer)) {
                continue;
            }
            visitedSinks.add(childContainer);

            MessageContainer newBase = baseContainer;
            if (childContainer.getBaseContainer() != null) {
                newBase = childContainer.getBaseContainer();
            }
            redirectMessages(visitedSinks, childContainer, newBase);
        }
    }

    private static String wrapText(Element element, AnnotationMirror mirror, String text) {
        StringBuilder b = new StringBuilder();
        if (element != null) {
            if (element.getKind() == ElementKind.METHOD) {
                b.append("Method " + ElementUtils.createReferenceName((ExecutableElement) element));
            } else {
                b.append("Element " + element.getSimpleName());
            }
        }
        if (mirror != null) {
            b.append(" at annotation @" + ElementUtils.getSimpleName(mirror.getAnnotationType()).trim());
        }

        if (b.length() > 0) {
            b.append(" is erroneous: ").append(text);
            return b.toString();
        } else {
            return text;
        }
    }

    protected M filterErrorElements(M model) {
        return model.hasErrors() ? null : model;
    }

    protected abstract M parse(Element element, AnnotationMirror mirror);

    public abstract Class<? extends Annotation> getAnnotationType();

    public boolean isDelegateToRootDeclaredType() {
        return false;
    }

    public List<Class<? extends Annotation>> getAllAnnotationTypes() {
        List<Class<? extends Annotation>> list = new ArrayList<>();
        if (getAnnotationType() != null) {
            list.add(getAnnotationType());
        }
        list.addAll(getTypeDelegatedAnnotationTypes());
        return list;
    }

    public List<Class<? extends Annotation>> getTypeDelegatedAnnotationTypes() {
        return Collections.emptyList();
    }

}
