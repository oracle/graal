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

import static com.oracle.graal.compiler.common.GraalOptions.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.nodes.*;

public final class HotSpotLoadFieldPlugin implements LoadFieldPlugin {
    private final MetaAccessProvider metaAccess;
    private final ConstantReflectionProvider constantReflection;

    public HotSpotLoadFieldPlugin(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        this.metaAccess = metaAccess;
        this.constantReflection = constantReflection;
    }

    static final ThreadLocal<Boolean> FieldReadEnabledInImmutableCode = new ThreadLocal<>();

    public boolean apply(GraphBuilderContext b, ValueNode receiver, ResolvedJavaField field) {
        if (!ImmutableCode.getValue() || b.parsingReplacement()) {
            if (receiver.isConstant()) {
                JavaConstant asJavaConstant = receiver.asJavaConstant();
                return tryReadField(b, field, asJavaConstant);
            }
        }
        return false;
    }

    private boolean tryReadField(GraphBuilderContext b, ResolvedJavaField field, JavaConstant receiver) {
        if (ImmutableCode.getValue()) {
            FieldReadEnabledInImmutableCode.set(Boolean.TRUE);
        }
        try {
            return tryConstantFold(b, metaAccess, constantReflection, field, receiver);
        } finally {
            if (ImmutableCode.getValue()) {
                FieldReadEnabledInImmutableCode.set(null);
            }
        }
    }

    public boolean apply(GraphBuilderContext b, ResolvedJavaField staticField) {
        if (!ImmutableCode.getValue() || b.parsingReplacement()) {
            // Javac does not allow use of "$assertionsDisabled" for a field name but
            // Eclipse does in which case a suffix is added to the generated field.
            if (b.parsingReplacement() && staticField.isSynthetic() && staticField.getName().startsWith("$assertionsDisabled")) {
                // For methods called indirectly from intrinsics, we (silently) disable
                // assertions so that the parser won't see calls to the AssertionError
                // constructor (all Invokes must be removed from intrinsics - see
                // HotSpotInlineInvokePlugin.notifyOfNoninlinedInvoke). Direct use of
                // assertions in intrinsics is forbidden.
                assert b.getMethod().getAnnotation(MethodSubstitution.class) == null : "cannot use assertions in " + b.getMethod().format("%H.%n(%p)");
                b.addPush(ConstantNode.forBoolean(true));
                return true;
            }
            return tryReadField(b, staticField, null);
        }
        return false;
    }
}
