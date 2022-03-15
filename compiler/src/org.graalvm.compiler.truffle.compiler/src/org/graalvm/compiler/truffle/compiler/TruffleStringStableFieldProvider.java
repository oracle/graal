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
import org.graalvm.compiler.core.common.spi.StableFieldProvider;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class TruffleStringStableFieldProvider implements StableFieldProvider {

    private final MetaAccessProvider metaAccess;
    private final ResolvedJavaType truffleStringType;
    private final ResolvedJavaField truffleStringDataField;
    private final ResolvedJavaField truffleStringHashCodeField;
    private final ResolvedJavaType byteArrayType;

    public TruffleStringStableFieldProvider(
                    MetaAccessProvider metaAccess,
                    ResolvedJavaType truffleStringType,
                    ResolvedJavaField truffleStringDataField,
                    ResolvedJavaField truffleStringHashCodeField,
                    ResolvedJavaType byteArrayType) {
        this.metaAccess = metaAccess;
        this.truffleStringType = truffleStringType;
        this.truffleStringDataField = truffleStringDataField;
        this.truffleStringHashCodeField = truffleStringHashCodeField;
        this.byteArrayType = byteArrayType;
    }

    @Override
    public boolean maybeStableField(ResolvedJavaField field) {
        return truffleStringDataField.equals(field) || truffleStringHashCodeField.equals(field);
    }

    @Override
    public boolean isStableField(ResolvedJavaField field, ConstantFieldProvider.ConstantFieldTool<?> tool) {
        if (truffleStringDataField.equals(field)) {
            if (truffleStringType.isAssignableFrom(metaAccess.lookupJavaType(tool.getReceiver()))) {
                JavaConstant value = tool.readValue();
                return value != null && byteArrayType.isAssignableFrom(metaAccess.lookupJavaType(value));
            }
        }
        if (truffleStringHashCodeField.equals(field)) {
            return truffleStringType.isAssignableFrom(metaAccess.lookupJavaType(tool.getReceiver()));
        }
        return false;
    }

    @Override
    public int getArrayDimension(ResolvedJavaField field) {
        if (truffleStringDataField.equals(field)) {
            return 1;
        }
        return -1;
    }
}
