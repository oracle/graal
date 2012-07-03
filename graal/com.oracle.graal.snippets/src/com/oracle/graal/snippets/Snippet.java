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
package com.oracle.graal.snippets;

import java.lang.annotation.*;
import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.snippets.Word.Operation;
import com.oracle.graal.snippets.nodes.*;

/**
 * A snippet is a Graal graph expressed as a Java source method. Graal snippets can be used for:
 * <ul>
 * <li>intrinsifying native JDK methods (see {@link ClassSubstitution})</li>
 * <li>lowering operations that have runtime dependent semantics (e.g. the {@code CHECKCAST} bytecode) </li>
 * <li>replacing a method call with a single graph node (see {@link NodeIntrinsic})</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Snippet {

    /**
     * Specifies the class defining the inlining policy for this snippet.
     * A {@linkplain InliningPolicy#Default default} policy is used if none is supplied.
     */
    Class<? extends InliningPolicy> inlining() default InliningPolicy.class;

    /**
     * Guides inlining decisions used when installing a snippet.
     */
    public interface InliningPolicy {
        /**
         * Determines if {@code method} should be inlined into {@code caller}.
         */
        boolean shouldInline(ResolvedJavaMethod method, ResolvedJavaMethod caller);

        /**
         * The default inlining policy which inlines everything except for methods
         * in any of the following categories.
         * <ul>
         * <li>{@linkplain Fold foldable} methods</li>
         * <li>{@linkplain NodeIntrinsic node intrinsics}</li>
         * <li>native methods</li>
         * <li>constructors of {@link Throwable} classes</li>
         * </ul>
         */
        InliningPolicy Default = new InliningPolicy() {
            public boolean shouldInline(ResolvedJavaMethod method, ResolvedJavaMethod caller) {
                if (Modifier.isNative(method.accessFlags())) {
                    return false;
                }
                if (method.getAnnotation(Fold.class) != null) {
                    return false;
                }
                if (method.getAnnotation(NodeIntrinsic.class) != null) {
                    return false;
                }
                if (Throwable.class.isAssignableFrom(method.holder().toJava())) {
                    if (method.name().equals("<init>")) {
                        return false;
                    }
                }
                if (method.getAnnotation(Operation.class) != null) {
                    return false;
                }
                return true;
            }
        };
    }

    /**
     * Annotates a method replaced by a compile-time constant.
     * A (resolved) call to the annotated method is replaced
     * with a constant obtained by calling the annotated method via reflection.
     *
     * All arguments to such a method (including the receiver if applicable)
     * must be compile-time constants.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Fold {
    }

    /**
     * Denotes a snippet parameter that will be bound during snippet
     * template {@linkplain SnippetTemplate#instantiate instantiation}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface Parameter {
        /**
         * The name of this parameter.
         */
        String value();

        /**
         * Determines if this parameter represents 0 or more arguments. During snippet template creation,
         * its value must be an array whose length specifies the number of arguments (the contents
         * of the array are ignored) bound to the parameter during {@linkplain SnippetTemplate#instantiate instantiation}.
         *
         * Such a parameter must be used in a counted loop in the snippet preceded by a call
         * to {@link ExplodeLoopNode#explodeLoop()}. The counted looped must be a
         * standard iteration over all the loop's elements (i.e. {@code for (T e : arr) ... }).
         */
        boolean multiple() default false;
    }

    /**
     * Denotes a snippet parameter that will bound to a constant value during
     * snippet template {@linkplain SnippetTemplate#instantiate instantiation}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface ConstantParameter {
        /**
         * The name of this constant.
         */
        String value();
    }

    /**
     * Wrapper for the prototype value of a {@linkplain Parameter#multiple() multiple} parameter.
     */
    public static class Multiple {
        public final Object array;
        private final Class componentType;
        private final int length;

        public static Multiple multiple(Class componentType, int length) {
            return new Multiple(Array.newInstance(componentType, length));
        }

        public Multiple(Object array) {
            assert array != null;
            this.componentType = array.getClass().getComponentType();
            assert this.componentType != null;
            this.length = java.lang.reflect.Array.getLength(array);
            this.array = array;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Multiple) {
                Multiple other = (Multiple) obj;
                return other.componentType == componentType &&
                        other.length == length;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return componentType.hashCode() ^ length;
        }

        @Override
        public String toString() {
            return componentType.getName() + "[" + length + "]";
        }
    }
}
