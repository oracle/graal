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
package com.oracle.svm.common.layeredimage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to specify how a method needs to be compiled when building layered images.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface LayeredCompilationBehavior {
    /**
     * This state represents how a method should be compiled in layered images. The state of a
     * method can only be decided in the first layer if it is marked as tracked across layers. The
     * state has to stay the same across all the extension layers. If not specified, the state of a
     * method will be {@link Behavior#DEFAULT}.
     */
    enum Behavior {

        /**
         * Method remains unanalyzed until the application layer and any inlining in a shared layer
         * is prevented. A call to the method in a shared layer will be replaced by an indirect
         * call. The compilation of those methods is then forced in the application layer and the
         * corresponding symbol is declared as global.
         *
         * A delayed method that is not referenced in any shared layer is treated as a
         * {@link Behavior#DEFAULT} method in the application layer and does not have to be
         * compiled. If it is only referenced in the application layer, it might be inlined and not
         * compiled at all.
         */
        FULLY_DELAYED_TO_APPLICATION_LAYER,

        /**
         * Method can be inlined into other methods, both before analysis and during compilation,
         * and will be compiled as a distinct compilation unit as stipulated by the normal native
         * image generation process (i.e., the method is installed as a root and/or a reference to
         * the method exists via a call and/or an explicit MethodReference).
         */
        DEFAULT,

        /**
         * Method is pinned to the initial layer, meaning it has to be analyzed and compiled in this
         * specific layer.
         */
        PINNED_TO_INITIAL_LAYER,
    }

    Behavior value();
}
