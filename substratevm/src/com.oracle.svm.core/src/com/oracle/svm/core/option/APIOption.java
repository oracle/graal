/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.option;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If an {@link org.graalvm.compiler.options.Option} is additionally annotated with
 * {@link APIOption} it will be exposed as native-image option with the given name.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface APIOption {
    /**
     * The name of the option when exposed as native-image option.
     */
    String name();

    APIOptionKind kind() default APIOptionKind.Default;

    /**
     * APIOptionKind can be used to customize how an {@link APIOption} gets rewritten to its
     * {@link org.graalvm.compiler.options.Option} counterpart.
     */
    enum APIOptionKind {
        /**
         * A boolean {@link org.graalvm.compiler.options.Option} gets passed as
         * <code>-{H,R}:+&lt;OptionDescriptor#name&gt;</code>. For other options if there is a
         * substring after {@code =}, it gets appended to
         * <code>-{H,R}:&lt;OptionDescriptor#name&gt;=</code>.
         */
        Default,
        /**
         * A boolean {@link org.graalvm.compiler.options.Option} gets passed as
         * <code>-{H,R}:-&lt;OptionDescriptor#name&gt;</code>. For other options using
         * {@code Negated} is not allowed.
         */
        Negated,
        /**
         * If a {@code String} {@link org.graalvm.compiler.options.Option} uses {@code Paths} any
         * paths occurring in the {@code String} will be replaced by their fully qualified variants
         * (relative to current working directory). (Needed for {@code NativeImageBuildServer}
         * compatibility)
         */
        Paths
    }
}
