/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * Hints that a parameter will be injected and is not part of the Java method signature.
 *
 * <p>
 * Explicitly supported types: {@link EspressoLanguage}, {@link SubstitutionProfiler}, and
 * {@link EspressoContext}.
 * <p>
 * Additionally, types that have a getter with the exact class name in
 * {@link com.oracle.truffle.espresso.impl.ContextAccess} are also supported. Examples include:
 * {@link com.oracle.truffle.espresso.meta.Meta} with {@link ContextAccess#getMeta()} and
 * {@link com.oracle.truffle.espresso.vm.VM} with {@link ContextAccess#getVM()}.
 *
 * <pre>
 * {@code @Inject EspressoLanguage language}
 * {@code @Inject Meta meta}
 * {@code @Inject SubstitutionProfiler profiler}
 * {@code @Inject EspressoContext context}
 * {@code @Inject VM vm}
 * </pre>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE_USE)
public @interface Inject {
}
