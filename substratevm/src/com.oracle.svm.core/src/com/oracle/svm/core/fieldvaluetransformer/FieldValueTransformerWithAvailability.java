/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.fieldvaluetransformer;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.JavaConstant;

@Platforms(Platform.HOSTED_ONLY.class)
public interface FieldValueTransformerWithAvailability extends FieldValueTransformer {

    /**
     * Controls when the transformed value is available at image build time.
     */
    enum ValueAvailability {
        /**
         * The value is available without time constraints, i.e., it is independent of static
         * analysis or compilation.
         */
        BeforeAnalysis,

        /**
         * The value depends on data computed by the static analysis and is therefore not yet
         * available to the static analysis. The value still might be constant folded during
         * compilation.
         */
        AfterAnalysis,

        /**
         * Value depends on data computed during compilation and is therefore available only when
         * writing out the image heap into the native image. Such a value is never available for
         * constant folding.
         */
        AfterCompilation
    }

    /**
     * Returns information about when the value for this custom computation is available.
     */
    ValueAvailability valueAvailability();

    /**
     * Optionally provide a Graal IR node to intrinsify the field access before the static analysis.
     * This allows the compiler to optimize field values that are not available yet, as long as
     * there is a dedicated high-level node available.
     */
    @SuppressWarnings("unused")
    default ValueNode intrinsify(CoreProviders providers, JavaConstant receiver) {
        return null;
    }
}
