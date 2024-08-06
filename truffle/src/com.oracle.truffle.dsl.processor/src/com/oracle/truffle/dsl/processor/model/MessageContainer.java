/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.dsl.processor.ExpectError;
import com.oracle.truffle.dsl.processor.Log;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.GeneratedElement;

public abstract class MessageContainer implements Iterable<MessageContainer> {

    private List<Message> messages;

    protected final TruffleTypes types = ProcessorContext.getInstance().getTypes();

    public final void addWarning(String text, Object... params) {
        getMessagesForModification().add(new Message(null, null, null, this, String.format(text, params), Kind.WARNING, null));
    }

    public final void addSuppressableWarning(String suppressionKey, String text, Object... params) {
        getMessagesForModification().add(new Message(null, null, null, this, String.format(text, params), Kind.WARNING, suppressionKey));
    }

    public final void addWarning(AnnotationValue value, String text, Object... params) {
        getMessagesForModification().add(new Message(null, value, null, this, String.format(text, params), Kind.WARNING, null));
    }

    public final void addSuppressableWarning(String suppressionKey, AnnotationValue value, String text, Object... params) {
        getMessagesForModification().add(new Message(null, value, null, this, String.format(text, params), Kind.WARNING, suppressionKey));
    }

    public final void addError(String text, Object... params) {
        addError((AnnotationValue) null, text, params);
    }

    public final void addError(Element enclosedElement, String text, Object... params) {
        getMessagesForModification().add(new Message(null, null, enclosedElement, this, String.format(text, params), Kind.ERROR, null));
    }

    public final void addError(AnnotationValue value, String text, Object... params) {
        getMessagesForModification().add(new Message(null, value, null, this, String.format(text, params), Kind.ERROR, null));
    }

    public final void addError(AnnotationMirror mirror, AnnotationValue value, String text, Object... params) {
        getMessagesForModification().add(new Message(mirror, value, null, this, String.format(text, params), Kind.ERROR, null));
    }

    protected List<MessageContainer> findChildContainers() {
        return Collections.emptyList();
    }

    public abstract Element getMessageElement();

    public Iterator<MessageContainer> iterator() {
        return findChildContainers().iterator();
    }

    public final void redirectMessages(MessageContainer to) {
        if (messages != null) {
            List<Message> list = getMessagesForModification();
            for (Message message : list) {
                if (message.getKind() == Kind.WARNING) {
                    continue;
                }
                Element element = message.getEnclosedElement();
                if (element == null) {
                    element = message.getOriginalContainer().getMessageElement();
                }
                String reference = ElementUtils.getReadableReference(to.getMessageElement(), element);
                String prefix = "Message redirected from element " + reference + ":" + System.lineSeparator();
                to.getMessagesForModification().add(message.redirect(prefix, to.getMessageElement()));
            }
            list.clear();
        }
        for (MessageContainer container : findChildContainers()) {
            container.redirectMessages(to);
        }
    }

    public final void redirectMessagesOnGeneratedElements(MessageContainer to) {
        if (messages != null) {
            Element messageElement = getMessageElement();
            if (messageElement == null || messageElement instanceof GeneratedElement || messageElement.getEnclosingElement() instanceof GeneratedElement) {
                List<Message> list = getMessagesForModification();
                for (Message message : list) {
                    to.getMessagesForModification().add(message.redirect("", to.getMessageElement()));
                }
                list.clear();
            }
        }
        for (MessageContainer container : findChildContainers()) {
            container.redirectMessagesOnGeneratedElements(to);
        }
    }

    public final void emitMessages(Log log) {
        Map<Element, List<Message>> emittedMessages = new HashMap<>();
        Set<Element> relevantTypes = new LinkedHashSet<>();
        visit((container) -> {
            List<Message> m = container.getMessages();
            for (int i = m.size() - 1; i >= 0; i--) {
                Message message = m.get(i);
                Element targetElement = container.emitDefault(log, message);
                emittedMessages.computeIfAbsent(targetElement, (e) -> new ArrayList<>()).add(message);
            }
            if (container.getMessageElement() instanceof TypeElement) {
                relevantTypes.add(container.getMessageElement());
            }
            return true; // continue
        });

        if (!ProcessorContext.types().ExpectErrorTypes.isEmpty()) {
            for (Element element : relevantTypes) {
                verifyExpectedErrors(element, emittedMessages);
            }
        }
    }

    private static void verifyExpectedErrors(Element element, Map<Element, List<Message>> emitted) {
        List<String> expectedErrors = ExpectError.getExpectedErrors(element);
        if (!expectedErrors.isEmpty()) {
            List<Message> foundMessages = emitted.get(element);

            ProcessorContext c = ProcessorContext.getInstance();
            List<Message> messages = null;
            if (foundMessages != null) {
                for (Message m : foundMessages) {
                    /*
                     * Check for suppressed using an annotation, but not using the options. This
                     * avoids failing in the truffle.dsl tests if an option is set, e.g. for a
                     * language.
                     */
                    if (!c.getLog().isSuppressed(m.kind, m.suppressionKey, m.originalContainer.getMessageElement(), false)) {
                        if (messages == null) {
                            messages = new ArrayList<>();
                        }
                        messages.add(m);
                    }
                }
            }

            messages = messages == null ? Collections.emptyList() : messages;
            if (expectedErrors.size() != messages.size()) {
                ProcessorContext.getInstance().getLog().message(Kind.ERROR, element, null, null, "Error count expected %s but was %s. Expected errors %s but got %s.",
                                expectedErrors.size(),
                                messages.size(),
                                expectedErrors.toString(),
                                messages.toString());
            }
        }

        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed instanceof TypeElement) {
                // we just validate types.
                continue;
            }
            verifyExpectedErrors(enclosed, emitted);
        }

    }

    private Element emitDefault(Log log, Message message) {
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
        Element targetElement = enclosedElement == null ? messageElement : enclosedElement;
        if (targetElement instanceof GeneratedElement) {
            throw new AssertionError("Tried to emit message to generated element: " + messageElement + ". Make sure messages are redirected correctly. Message: " + message.getText());
        }

        if (log.isSuppressed(kind, message.suppressionKey, messageElement)) {
            return targetElement;
        }

        String text = trimLongMessage(message.getText());
        List<String> expectedErrors = ExpectError.getExpectedErrors(targetElement);
        if (!expectedErrors.isEmpty()) {
            if (ExpectError.isExpectedError(targetElement, text)) {
                return targetElement;
            }
            log.message(Kind.ERROR, targetElement, null, null, "Message expected one of '%s' but was '%s'.", expectedErrors, text);
        } else {
            if (message.suppressionKey != null) {
                text = text + " This warning may be suppressed using @SuppressWarnings(\"" + message.suppressionKey + "\").";
            }
            if (enclosedElement == null) {
                log.message(kind, targetElement, messageAnnotation, messageValue, text);
            } else {
                log.message(kind, targetElement, null, null, text);
            }
        }
        return targetElement;
    }

    private static final int MAX_MARKER_BYTE_LENGTH = 60000;

    /**
     * Eclipse JDT does not support markers bigger than 65535 bytes. In order to avoid making an JDT
     * assertion fail that hides the actual error we truncate the message showing only the beginning
     * and the end. After all messages with that size are not expected anyway and most likely an
     * error.
     */
    private static String trimLongMessage(String valueString) {
        if (valueString.length() < 21000) {
            // optimized test based on maximum 3 bytes per character
            return valueString;
        }
        byte[] bytes;
        try {
            bytes = valueString.getBytes(("UTF-8"));
        } catch (UnsupportedEncodingException uee) {
            return valueString;
        }
        if (bytes.length > MAX_MARKER_BYTE_LENGTH) {
            return String.format("Java compiler message is too long. Showing the first few 8000 and the last 2000 characters only: %n%s%n ... truncated ... %n%s",
                            valueString.substring(0, 8000),
                            valueString.substring(valueString.length() - 2000, valueString.length()));
        }
        return valueString;
    }

    public AnnotationMirror getMessageAnnotation() {
        return null;
    }

    public AnnotationValue getMessageAnnotationValue() {
        return null;
    }

    public final boolean hasErrors() {
        return !visit((container) -> {
            for (Message msg : container.getMessages()) {
                if (msg.getKind() == Kind.ERROR) {
                    return false;
                }
            }
            return true;
        });
    }

    public final boolean hasErrorsOrWarnings() {
        return !visit((container) -> {
            for (Message msg : container.getMessages()) {
                if (msg.getKind() == Kind.ERROR || msg.getKind() == Kind.WARNING) {
                    return false;
                }
            }
            return true;
        });
    }

    private boolean visit(Predicate<MessageContainer> vistor) {
        return visitImpl(new HashSet<>(), vistor);
    }

    private boolean visitImpl(Set<MessageContainer> visited, Predicate<MessageContainer> visitor) {
        if (visited.contains(this)) {
            return true;
        }
        visited.add(this);
        if (!visitor.test(this)) {
            return false;
        }
        for (MessageContainer sink : findChildContainers()) {
            if (!sink.visitImpl(visited, visitor)) {
                return false;
            }
        }
        return true;
    }

    public final List<Message> collectMessages() {
        List<Message> foundMessages = new ArrayList<>();
        visit((s) -> {
            foundMessages.addAll(s.getMessages());
            return true;
        });
        return foundMessages;
    }

    protected final List<Message> getMessagesForModification() {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        return messages;
    }

    public final List<Message> getMessages() {
        if (messages == null) {
            return Collections.emptyList();
        }
        return messages;
    }

    public static final class Message {

        private final MessageContainer originalContainer;
        private final Element enclosedElement;

        private final AnnotationMirror annotationMirror;
        private final AnnotationValue annotationValue;
        private final String text;
        private final Kind kind;
        private final String suppressionKey;

        public Message(AnnotationMirror annotationMirror, AnnotationValue annotationValue, Element enclosedElement, MessageContainer originalContainer, String text, Kind kind, String suppressionKey) {
            this.annotationMirror = annotationMirror;
            this.annotationValue = annotationValue;
            this.enclosedElement = enclosedElement;
            this.originalContainer = originalContainer;
            this.text = text;
            this.kind = kind;
            this.suppressionKey = suppressionKey;
        }

        public Message redirect(String textPrefix, Element element) {
            return new Message(null, null, element, originalContainer, textPrefix + text, kind, null);
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
