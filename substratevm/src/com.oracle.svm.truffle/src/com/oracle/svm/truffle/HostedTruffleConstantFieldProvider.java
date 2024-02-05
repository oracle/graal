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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;

import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@Platforms(Platform.HOSTED_ONLY.class)
public final class HostedTruffleConstantFieldProvider implements ConstantFieldProvider {
    private final ConstantFieldProvider wrappedConstantFieldProvider;

    public HostedTruffleConstantFieldProvider(ConstantFieldProvider wrappedConstantFieldProvider) {
        this.wrappedConstantFieldProvider = wrappedConstantFieldProvider;
    }

    /**
     * The {@link CompilationFinal} annotation allows one to mark arrays as stable so that array
     * elements can be constant folded.
     * <p>
     * However, the "stableness" of the array can be a dynamic property guarded by assumptions in
     * the runtime execution. In other words, it is possible for the array to be considered stable
     * in a Truffle guest compilation, and for the compilation to be invalidated when the array is
     * changed. Therefore, this constant folding must not happen during native image generation, as
     * invalidation of such code is impossible.
     * <p>
     * We disable the constant folding of such fields when preparing runtime graphs, so that during
     * partial evaluation the Truffle constant field provider delegate can do the correct constant
     * folding that takes stable array dimensions into account.
     * <p>
     * Similar restrictions are needed for the Node's {@link Child} and {@link Children}
     * annotations.
     */
    @Override
    public <T> T readConstantField(ResolvedJavaField field, ConstantFieldTool<T> tool) {
        boolean hasTruffleFoldedAnnotation = field.isAnnotationPresent(CompilationFinal.class) || field.isAnnotationPresent(Child.class) || field.isAnnotationPresent(Children.class);
        if (hasTruffleFoldedAnnotation) {
            return null;
        }

        return wrappedConstantFieldProvider.readConstantField(field, tool);
    }

    @Override
    public boolean maybeFinal(ResolvedJavaField field) {
        return wrappedConstantFieldProvider.maybeFinal(field);
    }
}
