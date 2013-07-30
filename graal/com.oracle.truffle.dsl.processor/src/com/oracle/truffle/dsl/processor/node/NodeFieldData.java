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
package com.oracle.truffle.dsl.processor.node;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.template.*;

public class NodeFieldData extends MessageContainer {

    private final Element messageElement;
    private final AnnotationMirror messageAnnotation;
    private final String name;
    private final TypeMirror type;
    private final boolean generated;
    private ExecutableElement getter;

    public NodeFieldData(Element messageElement, AnnotationMirror messageAnnotation, TypeMirror type, String name, boolean generated) {
        this.messageElement = messageElement;
        this.messageAnnotation = messageAnnotation;
        this.name = name;
        this.type = type;
        this.generated = generated;
    }

    void setGetter(ExecutableElement getter) {
        this.getter = getter;
    }

    @Override
    public Element getMessageElement() {
        return messageElement;
    }

    @Override
    public AnnotationMirror getMessageAnnotation() {
        return messageAnnotation;
    }

    public String getName() {
        return name;
    }

    public TypeMirror getType() {
        return type;
    }

    public boolean isGenerated() {
        return generated;
    }

    public ExecutableElement getGetter() {
        return getter;
    }

}
