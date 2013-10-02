/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements;

import java.lang.annotation.*;
import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

/**
 * A snippet is a Graal graph expressed as a Java source method. Snippets are used for lowering
 * nodes that have runtime dependent semantics (e.g. the {@code CHECKCAST} bytecode).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Snippet {

    /**
     * Specifies the class defining the inlining policy for this snippet. A
     * {@linkplain DefaultSnippetInliningPolicy default} policy is used if none is supplied.
     */
    Class<? extends SnippetInliningPolicy> inlining() default SnippetInliningPolicy.class;

    /**
     * Specifies whether all FrameStates within this snippet should always be removed. If this is
     * false, FrameStates are only removed if there are no side-effecting instructions in the
     * snippet.
     */
    boolean removeAllFrameStates() default false;

    /**
     * Guides inlining decisions used when installing a snippet.
     */
    public interface SnippetInliningPolicy {

        /**
         * Determines if {@code method} should be inlined into {@code caller}.
         */
        boolean shouldInline(ResolvedJavaMethod method, ResolvedJavaMethod caller);

        /**
         * Determines if {@code method} should be inlined using its replacement graph.
         * 
         * @return true if the replacement graph should be used, false for normal inlining.
         */
        boolean shouldUseReplacement(ResolvedJavaMethod callee, ResolvedJavaMethod methodToParse);
    }

    /**
     * The default inlining policy which inlines everything except for methods in any of the
     * following categories.
     * <ul>
     * <li>{@linkplain Fold foldable} methods</li>
     * <li>{@linkplain NodeIntrinsic node intrinsics}</li>
     * <li>native methods</li>
     * <li>constructors of {@link Throwable} classes</li>
     * </ul>
     */
    public static class DefaultSnippetInliningPolicy implements SnippetInliningPolicy {

        private final MetaAccessProvider metaAccess;

        public DefaultSnippetInliningPolicy(MetaAccessProvider metaAccess) {
            this.metaAccess = metaAccess;
        }

        @Override
        public boolean shouldInline(ResolvedJavaMethod method, ResolvedJavaMethod caller) {
            if (Modifier.isNative(method.getModifiers())) {
                return false;
            }
            if (method.getAnnotation(Fold.class) != null) {
                return false;
            }
            if (method.getAnnotation(NodeIntrinsic.class) != null) {
                return false;
            }
            if (metaAccess.lookupJavaType(Throwable.class).isAssignableFrom(method.getDeclaringClass())) {
                if (method.isConstructor()) {
                    return false;
                }
            }
            if (method.getAnnotation(Word.Operation.class) != null) {
                return false;
            }
            return true;
        }

        @Override
        public boolean shouldUseReplacement(ResolvedJavaMethod callee, ResolvedJavaMethod methodToParse) {
            return true;
        }
    }

    /**
     * Annotates a method replaced by a compile-time constant. A (resolved) call to the annotated
     * method is replaced with a constant obtained by calling the annotated method via reflection.
     * 
     * All arguments to such a method (including the receiver if applicable) must be compile-time
     * constants.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Fold {
    }

    /**
     * Denotes a snippet parameter representing 0 or more arguments that will be bound during
     * snippet template {@linkplain SnippetTemplate#instantiate instantiation}. During snippet
     * template creation, its value must be an array whose length specifies the number of arguments
     * (the contents of the array are ignored) bound to the parameter during
     * {@linkplain SnippetTemplate#instantiate instantiation}.
     * 
     * Such a parameter must be used in a counted loop in the snippet preceded by a call to
     * {@link ExplodeLoopNode#explodeLoop()}. The counted looped must be a standard iteration over
     * all the loop's elements (i.e. {@code for (T e : arr) ... }).
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface VarargsParameter {
    }

    /**
     * Denotes a snippet parameter that will bound to a constant value during snippet template
     * {@linkplain SnippetTemplate#instantiate instantiation}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface ConstantParameter {
    }
}
