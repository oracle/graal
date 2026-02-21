/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.lir.Variable;

/**
 * Wrapper around Variable to change how indexing
 * in data structures like Map or Set is done.
 * <p>
 * We index only by the Variable index instead of
 * including the kind as well.
 * </p>
 */
public class RAVariable extends RAValue {
    protected Variable variable;

    protected RAVariable(Variable variable) {
        super(variable);
        this.variable = variable;
    }

    @Override
    public RAVariable asVariable() {
        return this;
    }

    @Override
    public boolean isVariable() {
        return true;
    }

    public Variable getVariable() {
        return variable;
    }

    @Override
    public int hashCode() {
        return variable.index;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof RAVariable raVariable) {
            return variable.index == raVariable.variable.index;
        }

        return false;
    }

    @Override
    public String toString() {
        return "v" + variable.index;
    }
}
