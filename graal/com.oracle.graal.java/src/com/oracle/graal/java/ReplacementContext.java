/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderContext.Replacement;

/**
 * Information about a substitute method being parsed in lieu of an original method. This can happen
 * when a call to a {@link MethodSubstitution} is encountered or the root of compilation is a
 * {@link MethodSubstitution} or a snippet.
 */
public class ReplacementContext implements Replacement {
    /**
     * The method being replaced.
     */
    final ResolvedJavaMethod method;

    /**
     * The replacement method.
     */
    final ResolvedJavaMethod replacement;

    public ReplacementContext(ResolvedJavaMethod method, ResolvedJavaMethod substitute) {
        this.method = method;
        this.replacement = substitute;
    }

    public ResolvedJavaMethod getOriginalMethod() {
        return method;
    }

    public ResolvedJavaMethod getReplacementMethod() {
        return replacement;
    }

    public boolean isIntrinsic() {
        return false;
    }

    /**
     * Determines if a call within the compilation scope of a replacement represents a call to the
     * original method.
     */
    public boolean isCallToOriginal(ResolvedJavaMethod targetMethod) {
        return method.equals(targetMethod) || replacement.equals(targetMethod);
    }

    IntrinsicContext asIntrinsic() {
        return null;
    }

    @Override
    public String toString() {
        return "Replacement{original: " + method.format("%H.%n(%p)") + ", replacement: " + replacement.format("%H.%n(%p)") + "}";
    }
}
