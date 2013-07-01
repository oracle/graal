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
package com.oracle.truffle.dsl.processor.node;

import javax.lang.model.element.*;

import com.oracle.truffle.dsl.processor.template.*;
import com.oracle.truffle.dsl.processor.typesystem.*;

public class SpecializationGuardData extends MessageContainer {

    private final SpecializationData specialization;
    private final AnnotationValue value;
    private final String guardMethod;
    private final boolean onSpecialization;
    private final boolean onExecution;

    private GuardData guardDeclaration;

    public SpecializationGuardData(SpecializationData specialization, AnnotationValue value, String guardMethod, boolean onSpecialization, boolean onExecution) {
        this.specialization = specialization;
        this.guardMethod = guardMethod;
        this.onSpecialization = onSpecialization;
        this.onExecution = onExecution;
        this.value = value;
    }

    @Override
    public Element getMessageElement() {
        return specialization.getMessageElement();
    }

    @Override
    public AnnotationMirror getMessageAnnotation() {
        return specialization.getMessageAnnotation();
    }

    @Override
    public AnnotationValue getMessageAnnotationValue() {
        return value;
    }

    public String getGuardMethod() {
        return guardMethod;
    }

    public boolean isOnExecution() {
        return onExecution;
    }

    public boolean isOnSpecialization() {
        return onSpecialization;
    }

    public void setGuardDeclaration(GuardData compatibleGuard) {
        this.guardDeclaration = compatibleGuard;
    }

    public GuardData getGuardDeclaration() {
        return guardDeclaration;
    }

}
