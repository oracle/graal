/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.option;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Function;

import org.graalvm.compiler.options.Option;

/**
 * If an {@link Option} is additionally annotated with {@link APIOption} it will be exposed as
 * native-image option with the given name.
 */
@Repeatable(APIOption.List.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface APIOption {

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    @interface List {
        APIOption[] value();
    }

    /**
     * The name of the option when exposed as native-image option.
     */
    String name();

    /**
     * Provide a custom help message for the option.
     */
    String customHelp() default "";

    APIOptionKind kind() default APIOptionKind.Default;

    /**
     * The value that will be passed to a non-boolean option when no {@code =} is specified.
     * <p>
     * By default {@code --option} form is equivalent to {@code --option=} (it passes empty string).
     */
    String[] defaultValue() default {};

    /**
     * If a {@code fixedValue} is provided the {@link APIOption} will not accept custom option
     * values and instead always use the specified value.
     */
    String[] fixedValue() default {};

    /**
     * Allow transforming option values before assigning them to the underlying {@link Option}.
     **/
    Class<? extends Function<Object, Object>>[] valueTransformer() default DefaultTransformer.class;

    String deprecated() default "";

    /**
     * APIOptionKind can be used to customize how an {@link APIOption} gets rewritten to its
     * {@link Option} counterpart.
     */
    enum APIOptionKind {
        /**
         * A boolean {@link Option} gets passed as
         * <code>-{H,R}:+&lt;OptionDescriptor#name&gt;</code>. For other options if there is a
         * substring after {@code =}, it gets appended to
         * <code>-{H,R}:&lt;OptionDescriptor#name&gt;=</code>.
         */
        Default,
        /**
         * A boolean {@link Option} gets passed as
         * <code>-{H,R}:-&lt;OptionDescriptor#name&gt;</code>. For other options using
         * {@code Negated} is not allowed.
         */
        Negated,
        /**
         * Denotes that the annotated {@code String} option represents a file system path. If the
         * option value is not an absolute path, it will be resolved against the current working
         * directory in which the native image tool is executed.
         */
        Paths
    }

    class Utils {
        public static String name(APIOption annotation) {
            if (annotation.name().startsWith("-")) {
                return annotation.name();
            } else {
                return "--" + annotation.name();
            }
        }
    }

    class DefaultTransformer implements Function<Object, Object> {
        @Override
        public Object apply(Object o) {
            return o;
        }
    }
}
