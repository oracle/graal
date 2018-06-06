/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.meta;

import org.graalvm.compiler.core.common.spi.JavaConstantFieldProvider;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Implements the default constant folding semantics for Java fields in the HotSpot VM.
 */
public class SubstrateConstantFieldProvider extends JavaConstantFieldProvider {

    public SubstrateConstantFieldProvider(MetaAccessProvider metaAccess) {
        super(metaAccess);
    }

    @Override
    public <T> T readConstantField(ResolvedJavaField javaField, ConstantFieldTool<T> tool) {
        SubstrateField field = (SubstrateField) javaField;
        if (field.constantValue != null) {
            return tool.foldConstant(tool.readValue());
        }

        return super.readConstantField(field, tool);
    }

    @Override
    protected boolean isSyntheticEnumSwitchMap(ResolvedJavaField field) {
        /*
         * Enum-switch fields are constant folded during native image generation, so no need to even
         * check for such fields at run time.
         */
        assert !field.getName().equals("$VALUES") && !field.getName().equals("ENUM$VALUES") && !field.getName().startsWith("$SwitchMap$") && !field.getName().startsWith("$SWITCH_TABLE$");
        return false;
    }
}
