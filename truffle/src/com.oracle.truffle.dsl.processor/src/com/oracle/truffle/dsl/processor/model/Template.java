/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.model;

import java.util.Collections;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

public abstract class Template extends MessageContainer {

    private final ProcessorContext context;
    private final TypeElement templateType;
    private final AnnotationMirror annotation;

    public Template(ProcessorContext context, TypeElement templateType, AnnotationMirror annotation) {
        this.context = context;
        this.templateType = templateType;
        this.annotation = annotation;
    }

    public ProcessorContext getContext() {
        return context;
    }

    @Override
    public MessageContainer getBaseContainer() {
        return this;
    }

    public abstract TypeSystemData getTypeSystem();

    @Override
    public Element getMessageElement() {
        return templateType;
    }

    public String dump() {
        return toString();
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        return Collections.emptyList();
    }

    public TypeElement getTemplateType() {
        return templateType;
    }

    public AnnotationMirror getTemplateTypeAnnotation() {
        return annotation;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + ElementUtils.getSimpleName(getTemplateType()) + "]";
    }

}
