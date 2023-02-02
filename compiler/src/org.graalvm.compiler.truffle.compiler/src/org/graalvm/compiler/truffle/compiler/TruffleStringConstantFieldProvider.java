/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.truffle.compiler.substitutions.KnownTruffleTypes;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class TruffleStringConstantFieldProvider implements ConstantFieldProvider {
    protected final ConstantFieldProvider graalConstantFieldProvider;
    protected final MetaAccessProvider metaAccess;
    private final KnownTruffleTypes types;
    private final ResolvedJavaType byteArrayType;

    public TruffleStringConstantFieldProvider(ConstantFieldProvider graalConstantFieldProvider, MetaAccessProvider metaAccess, KnownTruffleTypes types) {
        this.graalConstantFieldProvider = graalConstantFieldProvider;
        this.metaAccess = metaAccess;
        this.types = types;
        this.byteArrayType = metaAccess.lookupJavaType(byte[].class);
    }

    @Override
    public <T> T readConstantField(ResolvedJavaField field, ConstantFieldTool<T> tool) {
        boolean isStaticField = field.isStatic();
        if (!isStaticField && tool.getReceiver().isNull()) {
            // can't be optimized
            return null;
        }
        T wellKnownField = readWellKnownConstantTruffleField(field, tool);
        if (wellKnownField != null) {
            return wellKnownField;
        }
        return graalConstantFieldProvider.readConstantField(field, tool);

    }

    protected <T> T readWellKnownConstantTruffleField(ResolvedJavaField field, ConstantFieldTool<T> tool) {
        // well-known internal fields of AbstractTruffleString
        if (types.truffleStringDataField.equals(field) || types.truffleStringHashCodeField.equals(field)) {
            // only applies to the immutable subclass TruffleString, not MutableTruffleString
            if (types.truffleStringType.isAssignableFrom(metaAccess.lookupJavaType(tool.getReceiver()))) {
                JavaConstant value = tool.readValue();
                if (value != null) {
                    if (types.truffleStringDataField.equals(field)) {
                        // the "data" field is implicitly stable if it contains a byte array
                        if (byteArrayType.isAssignableFrom(metaAccess.lookupJavaType(value))) {
                            return tool.foldStableArray(value, 1, true);
                        }
                    } else {
                        assert types.truffleStringHashCodeField.equals(field);
                        // the "hashCode" field is stable if its value is not zero
                        if (!value.isDefaultForKind()) {
                            return tool.foldConstant(value);
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public boolean maybeFinal(ResolvedJavaField field) {
        return types.truffleStringDataField.equals(field) || types.truffleStringHashCodeField.equals(field) || graalConstantFieldProvider.maybeFinal(field);
    }
}
