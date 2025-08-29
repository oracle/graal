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

package com.oracle.svm.core.option;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Use this annotation to mark image builder {@link HostedOptionKey}s and {@link RuntimeOptionKey}s
 * that require layered image build compatibility checking. For example, if a {@code HostedOption}
 * set to value {@code A} in the previous layer is required to also be set to the same value in the
 * current layer, the respective option can use this annotation to enforce this requirement.
 * Annotation elements {@code Severity}, {@code Kind}, {@code Positional} and {@code Message} can be
 * used to configure the nature of checking that is required for the annotated option.
 *
 * @see LayerVerifiedOption.List
 */
@Repeatable(LayerVerifiedOption.List.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface LayerVerifiedOption {

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    @interface List {
        LayerVerifiedOption[] value();
    }

    /**
     * Setting this element is required. It controls how a violation should be reported. Setting it
     * to {@code Error} will cause the image build to abort with an error message in case of a
     * violation. {@code Warn} reports the same message, but only as a warning, still allowing the
     * image build to continue.
     */
    Severity severity();

    enum Severity {
        Error,
        Warn
    }

    /**
     * When a violation is detected, a generic message for reporting the violation is automatically
     * created. This annotation element allows to provide a message more specific to the annotated
     * option.
     */
    String message() default "";

    /**
     * The violation checking has three different variants to choose from. This annotation element
     * is used to specify which kind is requested. Setting it to {@code Removed} reports a violation
     * if an option was given in the previous layer build, but is missing in the current layer
     * build. {@code Added} reports a violation if an option is specified in the current layer build
     * but was not also used when the previous layer got built. {@code Changed} is the strictest
     * form and requires an option to always be exactly the same between dependent layers.
     */
    Kind kind();

    enum Kind {
        Removed,
        Changed,
        Added
    }

    /**
     * The verification usually takes the position of the option within the sequence of options into
     * account. For some options we can be less strict and allow the checking to be independent of
     * the position. Specifying {@code false} selects this less strict mode. For example, if a
     * previous layer was built with {@code --add-exports=foo/bar=ALL-UNNAMED}, the exact position
     * where the current layer build specifies the same option is irrelevant as long as it does
     * specify it somewhere in its sequence of options.
     */
    boolean positional() default true;

    /**
     * If the {@code HostedOption} field (this annotation is used with) also has {@link APIOption}
     * annotations, this annotation element can be used to bind this annotation to a specific
     * {@link APIOption} annotation instead of being valid for all kinds of {@code HostedOption}
     * use. Note that one can also have additional {@code @LayerVerifiedOption} annotations that do
     * not make use of {@code apiOption} on the same {@code HostedOption} field to specify
     * compatibility checking that should apply for raw (non-API) use of the option.
     */
    String apiOption() default "";
}
