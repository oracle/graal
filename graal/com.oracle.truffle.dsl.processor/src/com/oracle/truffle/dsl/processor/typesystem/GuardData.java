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
package com.oracle.truffle.dsl.processor.typesystem;

import com.oracle.truffle.dsl.processor.node.*;
import com.oracle.truffle.dsl.processor.template.*;

public class GuardData extends TemplateMethod {

    private final SpecializationData specialization;
    private final boolean negated;

    public GuardData(TemplateMethod method, SpecializationData specialization, boolean negated) {
        super(method);
        this.negated = negated;
        this.specialization = specialization;
    }

    public boolean isNegated() {
        return negated;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GuardData) {
            GuardData other = (GuardData) obj;
            return getMethod().equals(other.getMethod()) && negated == other.negated;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getMethod().hashCode();
    }

    public SpecializationData getSpecialization() {
        return specialization;
    }

    @Override
    public String toString() {
        return (negated ? "!" : "") + getMethodName() + getParameters().toString();
    }

}
