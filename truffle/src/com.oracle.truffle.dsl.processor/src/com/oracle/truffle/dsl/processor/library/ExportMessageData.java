/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.library;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.java.model.GeneratedElement;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.NodeData;

public class ExportMessageData extends MessageContainer {

    private final ExportsLibrary exports;
    private final LibraryMessage resolvedMessage;

    private final Element element;
    private final AnnotationMirror annotation;
    private NodeData specializedNode;

    private boolean overriden;
    private boolean abstractImpl;

    ExportMessageData(ExportsLibrary exports, LibraryMessage resolvedMessage, Element element, AnnotationMirror annotation) {
        this.exports = exports;
        this.resolvedMessage = resolvedMessage;
        this.element = element;
        this.annotation = annotation;
    }

    public boolean isAbstract() {
        return abstractImpl;
    }

    public void setAbstract(boolean abstractImpl) {
        this.abstractImpl = abstractImpl;
    }

    public void setOverriden(boolean overriden) {
        this.overriden = overriden;
    }

    public boolean isOverriden() {
        return overriden;
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        List<MessageContainer> children = new ArrayList<>();
        if (specializedNode != null) {
            children.add(specializedNode);
        }
        return children;
    }

    public LibraryMessage getResolvedMessage() {
        return resolvedMessage;
    }

    public ExportsLibrary getExportsLibrary() {
        return exports;
    }

    public boolean isClass() {
        return element == null ? false : element.getKind().isClass();
    }

    public boolean isMethod() {
        return !isClass();
    }

    public TypeMirror getReceiverType() {
        if (element == null || exports.isExplicitReceiver()) {
            return exports.getReceiverType();
        }
        if (element instanceof ExecutableElement) {
            ExecutableElement method = ((ExecutableElement) element);
            if (element.getModifiers().contains(Modifier.STATIC)) {
                return method.getParameters().get(0).asType();
            } else {
                return method.getEnclosingElement().asType();
            }
        } else if (element instanceof TypeElement) {
            return element.getEnclosingElement().asType();
        } else {
            throw new AssertionError(element.getClass().getName());
        }
    }

    public void setSpecializedNode(NodeData specializedNode) {
        this.specializedNode = specializedNode;
    }

    public NodeData getSpecializedNode() {
        return specializedNode;
    }

    @Override
    public Element getMessageElement() {
        return element;
    }

    @Override
    public AnnotationMirror getMessageAnnotation() {
        return annotation;
    }

    public boolean isGenerated() {
        return element instanceof GeneratedElement;
    }

    @Override
    public String toString() {
        return "ExportMessageData [element=" + element + ", exports=" + exports + ", message=" + resolvedMessage + "]";
    }

}
