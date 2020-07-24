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
package com.oracle.truffle.dsl.processor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.dsl.processor.ExpectError;
import com.oracle.truffle.dsl.processor.Log;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.GeneratedElement;

public abstract class MessageContainer implements Iterable<MessageContainer> {

    private final List<Message> messages = new ArrayList<>();
    protected final TruffleTypes types = ProcessorContext.getInstance().getTypes();

    public final void addWarning(String text, Object... params) {
        getMessages().add(new Message(null, null, null, this, String.format(text, params), Kind.WARNING));
    }

    public final void addWarning(AnnotationValue value, String text, Object... params) {
        getMessages().add(new Message(null, value, null, this, String.format(text, params), Kind.WARNING));
    }

    public final void addError(String text, Object... params) {
        addError((AnnotationValue) null, text, params);
    }

    public final void addError(Element enclosedElement, String text, Object... params) {
        getMessages().add(new Message(null, null, enclosedElement, this, String.format(text, params), Kind.ERROR));
    }

    public final void addError(AnnotationValue value, String text, Object... params) {
        getMessages().add(new Message(null, value, null, this, String.format(text, params), Kind.ERROR));
    }

    public final void addError(AnnotationMirror mirror, AnnotationValue value, String text, Object... params) {
        getMessages().add(new Message(mirror, value, null, this, String.format(text, params), Kind.ERROR));
    }

    protected List<MessageContainer> findChildContainers() {
        return Collections.emptyList();
    }

    public abstract Element getMessageElement();

    public MessageContainer getBaseContainer() {
        return null;
    }

    public Iterator<MessageContainer> iterator() {
        return findChildContainers().iterator();
    }

    public final void redirectMessages(MessageContainer to) {
        if (!getMessages().isEmpty()) {
            for (Message message : getMessages()) {
                Element element = message.getEnclosedElement();
                if (element == null) {
                    element = message.getOriginalContainer().getMessageElement();
                }
                String reference = ElementUtils.getReadableReference(to.getMessageElement(), element);
                String prefix = "Message redirected from element " + reference + ":" + System.lineSeparator();
                to.getMessages().add(message.redirect(prefix, to.getMessageElement()));
            }
            getMessages().clear();
        }
        for (MessageContainer container : findChildContainers()) {
            container.redirectMessages(to);
        }
    }

    public final void redirectMessagesNotEnclosedIn(MessageContainer to) {
        if (!getMessages().isEmpty()) {
            Element baseElement = to.getMessageElement();
            ListIterator<Message> messageIterator = getMessages().listIterator();
            while (messageIterator.hasNext()) {
                Message message = messageIterator.next();
                if (!ElementUtils.isEnclosedIn(baseElement, message.getEnclosedElement())) {
                    messageIterator.set(message.redirect("", baseElement));
                }
            }
        }
        for (MessageContainer container : findChildContainers()) {
            container.redirectMessagesNotEnclosedIn(to);
        }
    }

    public final void redirectMessagesOnGeneratedElements(MessageContainer to) {
        if (!getMessages().isEmpty()) {
            Element messageElement = getMessageElement();
            if (messageElement == null || messageElement instanceof GeneratedElement || messageElement.getEnclosingElement() instanceof GeneratedElement) {
                for (Message message : getMessages()) {
                    to.getMessages().add(message.redirect("", to.getMessageElement()));
                }
                getMessages().clear();
            }
        }
        for (MessageContainer container : findChildContainers()) {
            container.redirectMessagesOnGeneratedElements(to);
        }
    }

    public final void emitMessages(ProcessorContext context, Log log) {
        emitMessagesImpl(context, log, new HashSet<MessageContainer>(), null);
    }

    private void emitMessagesImpl(ProcessorContext context, Log log, Set<MessageContainer> visitedSinks, List<Message> verifiedMessages) {
        List<Message> childMessages;
        if (verifiedMessages == null) {
            childMessages = collectMessagesWithElementChildren(new HashSet<MessageContainer>(), getMessageElement());
        } else {
            childMessages = verifiedMessages;
        }

        if (verifiedMessages != null) {
            verifyExpectedMessages(context, log, childMessages);
        }

        for (int i = getMessages().size() - 1; i >= 0; i--) {
            emitDefault(context, log, getMessages().get(i));
        }

        for (MessageContainer sink : findChildContainers()) {
            if (visitedSinks.contains(sink)) {
                continue;
            }

            visitedSinks.add(sink);
            if (sink.getMessageElement() == this.getMessageElement()) {
                sink.emitMessagesImpl(context, log, visitedSinks, childMessages);
            } else {
                sink.emitMessagesImpl(context, log, visitedSinks, null);
            }
        }
    }

    private List<Message> collectMessagesWithElementChildren(Set<MessageContainer> visitedSinks, Element e) {
        if (visitedSinks.contains(this)) {
            return Collections.emptyList();
        }
        visitedSinks.add(this);

        List<Message> foundMessages = new ArrayList<>();
        if (getMessageElement() != null && ElementUtils.typeEquals(getMessageElement().asType(), e.asType())) {
            foundMessages.addAll(getMessages());
        }
        for (MessageContainer sink : findChildContainers()) {
            foundMessages.addAll(sink.collectMessagesWithElementChildren(visitedSinks, e));
        }
        return foundMessages;
    }

    private void verifyExpectedMessages(ProcessorContext context, Log log, List<Message> msgs) {
        Element element = getMessageElement();
        List<String> expectedErrors = ExpectError.getExpectedErrors(context.getEnvironment(), element);
        if (expectedErrors.size() > 0) {
            if (expectedErrors.size() != msgs.size()) {
                log.message(Kind.ERROR, element, null, null, "Error count expected %s but was %s. Expected errors %s but got %s.", expectedErrors.size(), msgs.size(), expectedErrors.toString(),
                                msgs.toString());
            }
        }
    }

    private void emitDefault(ProcessorContext context, Log log, Message message) {
        Kind kind = message.getKind();

        Element messageElement = getMessageElement();
        AnnotationMirror messageAnnotation = getMessageAnnotation();
        AnnotationValue messageValue = getMessageAnnotationValue();
        if (message.getAnnotationValue() != null) {
            messageValue = message.getAnnotationValue();
        }
        if (message.getAnnotationMirror() != null) {
            messageAnnotation = message.getAnnotationMirror();
        }

        Element enclosedElement = message.getEnclosedElement();
        if (messageElement instanceof GeneratedElement) {
            throw new AssertionError("Tried to emit message to generated element: " + messageElement + ". Make sure messages are redirected correctly. Message: " + message.getText());
        }

        String text = message.getText();
        List<String> expectedErrors = ExpectError.getExpectedErrors(context.getEnvironment(), messageElement);
        if (!expectedErrors.isEmpty()) {
            if (ExpectError.isExpectedError(context.getEnvironment(), messageElement, text)) {
                return;
            }
            log.message(kind, messageElement, null, null, "Message expected one of '%s' but was '%s'.", expectedErrors, text);
        } else {
            if (enclosedElement == null) {
                log.message(kind, messageElement, messageAnnotation, messageValue, text);
            } else {
                log.message(kind, enclosedElement, null, null, text);
            }
        }
    }

    public AnnotationMirror getMessageAnnotation() {
        return null;
    }

    public AnnotationValue getMessageAnnotationValue() {
        return null;
    }

    public final boolean hasErrors() {
        return hasErrorsImpl(new HashSet<MessageContainer>(), false);
    }

    public final boolean hasErrorsOrWarnings() {
        return hasErrorsImpl(new HashSet<MessageContainer>(), true);
    }

    public final List<Message> collectMessages() {
        List<Message> collectedMessages = new ArrayList<>();
        collectMessagesImpl(collectedMessages, new HashSet<MessageContainer>());
        return collectedMessages;
    }

    private void collectMessagesImpl(List<Message> collectedMessages, Set<MessageContainer> visitedSinks) {
        collectedMessages.addAll(getMessages());
        for (MessageContainer sink : findChildContainers()) {
            if (visitedSinks.contains(sink)) {
                return;
            }

            visitedSinks.add(sink);
            sink.collectMessagesImpl(collectedMessages, visitedSinks);
        }
    }

    private boolean hasErrorsImpl(Set<MessageContainer> visitedSinks, boolean orWarnings) {
        for (Message msg : getMessages()) {
            if (msg.getKind() == Kind.ERROR || (orWarnings && msg.getKind() == Kind.WARNING)) {
                return true;
            }
        }
        for (MessageContainer sink : findChildContainers()) {
            if (visitedSinks.contains(sink)) {
                continue;
            }

            visitedSinks.add(sink);

            if (sink.hasErrorsImpl(visitedSinks, orWarnings)) {
                return true;
            }
        }
        return false;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public static final class Message {

        private final MessageContainer originalContainer;
        private final Element enclosedElement;

        private final AnnotationMirror annotationMirror;
        private final AnnotationValue annotationValue;
        private final String text;
        private final Kind kind;

        public Message(AnnotationMirror annotationMirror, AnnotationValue annotationValue, Element enclosedElement, MessageContainer originalContainer, String text, Kind kind) {
            this.annotationMirror = annotationMirror;
            this.annotationValue = annotationValue;
            this.enclosedElement = enclosedElement;
            this.originalContainer = originalContainer;
            this.text = text;
            this.kind = kind;
        }

        public Message redirect(String textPrefix, Element element) {
            return new Message(null, null, element, originalContainer, textPrefix + text, kind);
        }

        public Element getEnclosedElement() {
            return enclosedElement;
        }

        public AnnotationMirror getAnnotationMirror() {
            return annotationMirror;
        }

        public AnnotationValue getAnnotationValue() {
            return annotationValue;
        }

        public MessageContainer getOriginalContainer() {
            return originalContainer;
        }

        public String getText() {
            return text;
        }

        public Kind getKind() {
            return kind;
        }

        @Override
        public String toString() {
            return kind + ": " + text;
        }

    }

}
