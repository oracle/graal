/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hosted.runtimecompilation;

import java.util.Objects;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

public final class RuntimeCompilationCandidate {
    AnalysisMethod implementationMethod;
    AnalysisMethod targetMethod;

    RuntimeCompilationCandidate(AnalysisMethod implementationMethod, AnalysisMethod targetMethod) {
        this.implementationMethod = implementationMethod;
        this.targetMethod = targetMethod;
    }

    public AnalysisMethod getImplementationMethod() {
        return implementationMethod;
    }

    public AnalysisMethod getTargetMethod() {
        return targetMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RuntimeCompilationCandidate that = (RuntimeCompilationCandidate) o;
        return implementationMethod.equals(that.implementationMethod) && targetMethod.equals(that.targetMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(implementationMethod, targetMethod);
    }
}
