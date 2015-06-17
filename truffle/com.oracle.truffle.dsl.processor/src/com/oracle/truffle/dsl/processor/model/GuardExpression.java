/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.model;

import java.util.*;

import javax.lang.model.element.*;

import com.oracle.truffle.dsl.processor.expression.*;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Negate;
import com.oracle.truffle.dsl.processor.java.*;

public final class GuardExpression extends MessageContainer {

    private final TemplateMethod source;
    private final DSLExpression expression;

    public GuardExpression(TemplateMethod source, DSLExpression expression) {
        this.source = source;
        this.expression = expression;
    }

    @Override
    public Element getMessageElement() {
        return source.getMessageElement();
    }

    @Override
    public AnnotationMirror getMessageAnnotation() {
        return source.getMessageAnnotation();
    }

    @Override
    public AnnotationValue getMessageAnnotationValue() {
        return ElementUtils.getAnnotationValue(getMessageAnnotation(), "guards");
    }

    public DSLExpression getExpression() {
        return expression;
    }

    public boolean equalsNegated(GuardExpression other) {
        boolean negated = false;
        DSLExpression thisExpression = expression;
        if (thisExpression instanceof Negate) {
            negated = true;
            thisExpression = ((Negate) thisExpression).getReceiver();
        }

        boolean otherNegated = false;
        DSLExpression otherExpression = other.expression;
        if (otherExpression instanceof Negate) {
            otherNegated = true;
            otherExpression = ((Negate) otherExpression).getReceiver();
        }
        return Objects.equals(thisExpression, otherExpression) && negated != otherNegated;
    }

    public boolean implies(GuardExpression other) {
        if (Objects.equals(expression, other.expression)) {
            return true;
        }
        return false;
    }

}
