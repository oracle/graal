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
package com.oracle.graal.hotspot.meta;

import com.oracle.jvmci.meta.ResolvedJavaField;
import com.oracle.jvmci.meta.MetaAccessProvider;
import com.oracle.jvmci.meta.JavaConstant;
import com.oracle.jvmci.meta.ConstantReflectionProvider;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.hotspot.meta.HotSpotGraalConstantReflectionProvider.*;

import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.nodes.*;

public final class HotSpotLoadFieldPlugin implements LoadFieldPlugin {
    private final MetaAccessProvider metaAccess;
    private final ConstantReflectionProvider constantReflection;

    public HotSpotLoadFieldPlugin(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        this.metaAccess = metaAccess;
        this.constantReflection = constantReflection;
    }

    public boolean apply(GraphBuilderContext b, ValueNode receiver, ResolvedJavaField field) {
        if (!ImmutableCode.getValue() || b.parsingIntrinsic()) {
            if (receiver.isConstant()) {
                JavaConstant asJavaConstant = receiver.asJavaConstant();
                return tryReadField(b, field, asJavaConstant);
            }
        }
        return false;
    }

    private boolean tryReadField(GraphBuilderContext b, ResolvedJavaField field, JavaConstant receiver) {
        // FieldReadEnabledInImmutableCode is non null only if assertions are enabled
        if (FieldReadEnabledInImmutableCode != null && ImmutableCode.getValue()) {
            FieldReadEnabledInImmutableCode.set(Boolean.TRUE);
            try {
                return tryConstantFold(b, metaAccess, constantReflection, field, receiver);
            } finally {
                FieldReadEnabledInImmutableCode.set(null);
            }
        } else {
            return tryConstantFold(b, metaAccess, constantReflection, field, receiver);
        }
    }

    public boolean apply(GraphBuilderContext b, ResolvedJavaField staticField) {
        if (!ImmutableCode.getValue() || b.parsingIntrinsic()) {
            return tryReadField(b, staticField, null);
        }
        return false;
    }
}
