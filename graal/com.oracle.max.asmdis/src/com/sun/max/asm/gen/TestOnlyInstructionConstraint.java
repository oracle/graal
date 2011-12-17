/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.gen;

import java.lang.reflect.*;
import java.util.*;

import com.sun.max.asm.*;

/**
 * These instruction constraints are only used for generating test cases.
 * They do not appear in the generated assembler methods.
 */
public class TestOnlyInstructionConstraint implements InstructionConstraint {

    private final InstructionConstraint delegate;

    public TestOnlyInstructionConstraint(InstructionConstraint delegate) {
        this.delegate = delegate;
    }

    public String asJavaExpression() {
        return delegate.asJavaExpression();
    }

    public boolean check(Template template, List<Argument> arguments) {
        return delegate.check(template, arguments);
    }

    public Method predicateMethod() {
        return delegate.predicateMethod();
    }

    public boolean referencesParameter(Parameter parameter) {
        return delegate.referencesParameter(parameter);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
