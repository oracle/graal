/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.api.replacements;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A snippet is a Graal graph expressed as a Java source method. Snippets are used for lowering
 * nodes that have runtime dependent semantics (e.g. the {@code CHECKCAST} bytecode).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Snippet {

    /**
     * Determines when this snippet is lowered. This has influence on how a snippet graph is
     * prepared.
     */
    enum SnippetType {
        /**
         * A snippet lowered during the application of a lowering phase in the Graal compiler.
         */
        INLINED_SNIPPET,
        /**
         * A snippet that is inlined very late in the compilation pipeline. Typically reserved for
         * operations that exhibit zero optimization capabilities in the frontend, or operations
         * that must not be optimized in the scope of the caller. That is code that should not
         * participate in the optimizer. Normally such semantics could be expressed in LIR but
         * snippets are easier to write and maintain representation. Use with caution - no
         * optimizations will be performed on the inlinee in the caller graph, not even global value
         * numbering. Yet, all orderings of the inlinee are preserved, its guaranteed no other code
         * from the caller is scheduled interleaved with the callee code. This allows to express
         * semantics that must be "hidden" from the memory graph optimizations of the caller.
         */
        TRANSPLANTED_SNIPPET
    }

    /**
     * A partial intrinsic exits by (effectively) calling the intrinsified method. Normally, this
     * call must use exactly the same arguments as the call that is being intrinsified. For well
     * known snippets that are used after frame state assignment, we want to relax this restriction.
     */
    boolean allowPartialIntrinsicArgumentMismatch() default false;

    /**
     * Marks a method as known to be missing injected branch probabilities. Normally snippets are
     * required to have at least probabilities in their top level method but sometimes this is not
     * feasible either because the code is outside of our control or there aren't clear
     * probabilities that could be chosen.
     */
    boolean allowMissingProbabilities() default false;

    /**
     * Denotes a snippet parameter representing 0 or more arguments that will be bound during
     * snippet template instantiation. During snippet template creation, its value must be an array
     * whose length specifies the number of arguments (the contents of the array are ignored) bound
     * to the parameter during instantiation.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface VarargsParameter {
    }

    /**
     * Denotes a snippet parameter that will be bound to a constant value during snippet template
     * instantiation.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface ConstantParameter {
    }

    /**
     * Denotes a snippet parameter that will be bound to a non-null value during snippet template
     * instantiation.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface NonNullParameter {
    }
}
