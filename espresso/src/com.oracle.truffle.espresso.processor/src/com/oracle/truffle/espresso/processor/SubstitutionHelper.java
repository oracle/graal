/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.processor;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * Passed around during espresso annotation processing. It is meant to be subclassed to serve as
 * storage for the data required during processing.
 * 
 * @see NativeEnvProcessor.IntrinsincsHelper
 * @see com.oracle.truffle.espresso.processor.SubstitutionProcessor.SubstitutorHelper
 */
public class SubstitutionHelper {
    final boolean hasMetaInjection;
    final boolean hasProfileInjection;
    final boolean hasContextInjection;

    // Target of the substitution, cab be a public static method or a node.
    private final Element target;

    private final TypeElement implAnnotation;

    public TypeElement getNodeTarget() {
        return (TypeElement) target;
    }

    public ExecutableElement getMethodTarget() {
        return (ExecutableElement) target;
    }

    public SubstitutionHelper(EspressoProcessor processor, Element target, TypeElement implAnnotation) {
        this.target = target;
        this.implAnnotation = implAnnotation;
        // If the target is a node, obtain the abstract execute* method.
        ExecutableElement targetMethod = isNodeTarget()
                        ? processor.findNodeExecute(getNodeTarget())
                        : getMethodTarget();
        this.hasMetaInjection = processor.hasMetaInjection(targetMethod);
        this.hasProfileInjection = processor.hasProfileInjection(targetMethod);
        this.hasContextInjection = processor.hasContextInjection(targetMethod);
    }

    public boolean isNodeTarget() {
        return target instanceof TypeElement;
    }

    public TypeElement getImplAnnotation() {
        return implAnnotation;
    }

    public Element getTarget() {
        return target;
    }
}
