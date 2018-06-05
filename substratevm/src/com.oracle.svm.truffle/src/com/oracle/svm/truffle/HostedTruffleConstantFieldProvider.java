/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle;

import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.truffle.compiler.TruffleConstantFieldProvider;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import jdk.vm.ci.meta.ResolvedJavaField;

@Platforms(Platform.HOSTED_ONLY.class)
public final class HostedTruffleConstantFieldProvider implements ConstantFieldProvider {
    private final ConstantFieldProvider wrappedConstantFieldProvider;

    public HostedTruffleConstantFieldProvider(ConstantFieldProvider wrappedConstantFieldProvider) {
        this.wrappedConstantFieldProvider = wrappedConstantFieldProvider;
    }

    /**
     * The {@link CompilationFinal} annotation allows to mark arrays as stable so that array
     * elements can be constant folded. However, this constant folding must not happen during native
     * image generation: that would be too early since the array can still be modified before
     * compilation. So we disable the constant folding of such fields when preparing graphs, so that
     * during partial evaluation the {@link TruffleConstantFieldProvider} can do the correct
     * constant folding that takes stable array dimensions into account.
     */
    @Override
    public <T> T readConstantField(ResolvedJavaField field, ConstantFieldTool<T> tool) {
        if (field.getAnnotation(CompilationFinal.class) != null) {
            if (field instanceof AnalysisField) {
                /*
                 * If this is a final field, it might be constant folded in AOT compilation and also
                 * during static analysis. So the runtime graph can be the only place where a read
                 * occurs, therefore we explicitly mark the field as read.
                 */
                ((AnalysisField) field).registerAsRead(null);
            }
            return null;
        }

        return wrappedConstantFieldProvider.readConstantField(field, tool);
    }
}
