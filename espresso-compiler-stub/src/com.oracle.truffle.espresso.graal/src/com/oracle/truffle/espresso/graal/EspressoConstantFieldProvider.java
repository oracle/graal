/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.graal;

import jdk.graal.compiler.core.common.spi.JavaConstantFieldProvider;
import jdk.graal.compiler.options.OptionValues;
import com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedJavaField;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class EspressoConstantFieldProvider extends JavaConstantFieldProvider {
    public EspressoConstantFieldProvider(MetaAccessProvider metaAccess) {
        super(metaAccess);
    }

    @Override
    protected boolean isStableField(ResolvedJavaField field, ConstantFieldTool<?> tool) {
        if (field.isStatic() && !isStaticFieldConstant(field, tool.getOptions())) {
            return false;
        }

        if (((EspressoResolvedJavaField) field).isStable()) {
            return true;
        }
        return super.isStableField(field, tool);
    }

    @Override
    protected boolean isFinalField(ResolvedJavaField field, ConstantFieldTool<?> tool) {
        if (field.isStatic() && !isStaticFieldConstant(field, tool.getOptions())) {
            return false;
        }

        return super.isFinalField(field, tool);
    }

    private static final String SystemClassName = "Ljava/lang/System;";

    static boolean isStaticFieldConstant(ResolvedJavaField field, @SuppressWarnings("unused") OptionValues options) {
        ResolvedJavaType declaringClass = field.getDeclaringClass();
        if (!declaringClass.isInitialized()) {
            return false;
        }
        if (declaringClass.getName().equals(SystemClassName)) {
            switch (field.getName()) {
                case "in":
                case "out":
                case "err":
                    return false;
            }
        }
        return true;
    }
}
