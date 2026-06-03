/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.imagelayer;

import java.util.function.Supplier;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.vm.ci.meta.JavaConstant;

/** Layer-specific resolution policies for {@link Fold}. */
public final class LayeredFoldResolver {
    /** Operations available to layer-specific resolvers. */
    public interface LayeredResolutionContext {
        JavaConstant resolveInInitialLayer(Supplier<JavaConstant> computation);

        /** Resolves in the application layer, subject to the restrictions on {@link ApplicationLayer}. */
        JavaConstant resolveInApplicationLayer(Supplier<JavaConstant> computation);
    }

    /** Resolves the Fold in the initial layer. */
    public static final class InitialLayer implements Fold.Resolver<LayeredResolutionContext> {
        @Override
        public JavaConstant resolve(LayeredResolutionContext context, Supplier<JavaConstant> computation) {
            return context.resolveInInitialLayer(computation);
        }
    }

    /**
     * Resolves the Fold in the application layer.
     *
     * When a call is deferred from an earlier layer, its declared return type must be a primitive,
     * a final class, or an array type. Interface and non-final class return types, including
     * {@link Object}, are not supported even if the eventual result is an instance of a final
     * class. This restriction allows earlier layers to represent the future result without
     * evaluating the method. Object results may be null.
     *
     * This restriction does not apply to ordinary {@link Fold} methods or standalone image builds.
     */
    public static final class ApplicationLayer implements Fold.Resolver<LayeredResolutionContext> {
        @Override
        public JavaConstant resolve(LayeredResolutionContext context, Supplier<JavaConstant> computation) {
            return context.resolveInApplicationLayer(computation);
        }
    }
}
