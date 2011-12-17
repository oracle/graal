/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.annotate;
import java.lang.annotation.*;

/**
 * Every thus annotated method is to be inlined unconditionally by the VM's optimizing compiler
 * and the receiver is never null-checked.
 *
 * This annotation exists primarily for annotating methods that <b>must</b> be inlined
 * for semantic reasons as opposed to those that could be inlined for performance reasons.
 * Using this annotation for the latter should be done very rarely and only when
 * profiling highlights a performance bottleneck or such a bottleneck is known <i>a priori</i>.
 *
 * If the {@linkplain #override() override} element of this annotation is set to true, then
 * an annotated method must be {@code static} or {@code final} (implicitly or explicitly).
 * Additionally, only a INLINE virtual method with this element set to true can be overridden.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface INLINE {

    /**
     * If true, this element specifies that the annotated method provides the prototypical implementation
     * of the functionality expected (in the target VM) of every method that overrides
     * the annotated method. That is, the code produced by the compiler for every overriding method
     * must be functionality equivalent to the code produced for the prototypical method.
     *
     * <b>WARNING: Setting this element to true implies that you guarantee that all overriders
     * satisfy the above stated invariant.</b> There is no (easy) way to test for violations of
     * the invariant.
     *
     * A method annotated with INLINE should only be overridden for one of the following reasons:
     *
     *  1. To coerce the value returned by the overridden method to a subtype.
     *     See {@link MemberActor#holder()} and {@link FieldActor#holder()} for an example.
     *
     *  2. A method is overridden to make bootstrapping work.
     *     See {@link ClassActor#toJava()} and {@link ArrayClassActor#toJava()} for an example.
     *
     *  3. A method is overridden because a subclass can provide a more efficient implementation
     *     and it is known that certain call sites will be reduced to a constant receiver
     *     (not just a known receiver type but a known receiver object) via annotation driven
     *     compile-time {@linkplain FOLD folding}. This is how calls to the {@link GeneralLayout layout}
     *     interface methods are reduced to monomorphic calls at compile-time.
     *
     *     See {@link ClassActor#toJava()} and {@link ArrayClassActor#toJava()} for an example.
     */
    boolean override() default false;
}
