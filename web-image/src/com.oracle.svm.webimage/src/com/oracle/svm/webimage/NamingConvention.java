/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.webimage;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This interface provides a minimal set of operations necessary to encode a naming convention for
 * types in JS.
 */
public interface NamingConvention {

    /**
     * Reference to the Web Image object that stores the VM state.
     *
     * Should be kept in sync with the <code>runtime.js</code> file.
     */
    String VM_STATE = "vm";

    /**
     * Name of the variable containing the return code.
     *
     * Should be kept in sync with the <code>runtime.js</code> file.
     */
    String PROPERTY_EXITCODE = "exitCode";

    /**
     *
     * @param t the type
     * @return a encoding for the given type
     */
    String identForType(ResolvedJavaType t);

    /**
     *
     * @param m method
     * @return an encoding of the given type
     */
    String identForMethod(ResolvedJavaMethod m);

    /**
     * NOTE: beware of representing a field just by it's name or a numeric representation of the
     * name, with inheritance we often have the problem that private fields in the super class have
     * the same name as fields in sub classes, after compilation to JS however, there is no hiding
     * more possible and writes to sub/super will override values and the field is just represented
     * as one field.
     *
     * @param field
     * @return an encoding for the given field
     */
    default String identForProperty(ResolvedJavaField field) {
        if (field.isStatic()) {
            return identForStaticProperty(field.getName(), field.getDeclaringClass());
        } else {
            return identForArtificialProperty(field.getName(), field.getDeclaringClass());
        }
    }

    /**
     *
     * @param name the name of the artificial field
     * @param type the type the field is associated with
     * @return an encoding for an artificial property of the given type
     */
    String identForArtificialProperty(String name, ResolvedJavaType type);

    default String identForStaticProperty(String name, ResolvedJavaType type) {
        return identForArtificialProperty(name, type);
    }
}
