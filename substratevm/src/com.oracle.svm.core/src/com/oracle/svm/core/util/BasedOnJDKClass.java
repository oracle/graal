/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.monitor.JavaMonitor;

/**
 * Documents that a class is based on JDK source code. For example, {@link JavaMonitor} (which is
 * used to implement "synchronized" in Native Image) is a simplified and customized version of the
 * JDK class {@link java.util.concurrent.locks.ReentrantLock}.
 *
 * Unless specified otherwise, we try to keep our custom implementations in sync with their JDK
 * counterparts. When we update to a new JDK version, we therefore need to check if there were any
 * relevant changes that need to be applied to our code base. Here is a basic rule of thumb:
 * <ul>
 * <li>master should follow JDK latest (unless there are incompatibilities with earlier JDK versions
 * that we need to support on master)</li>
 * <li>important bug/performance fixes need to be backported</li>
 * </ul>
 */
@Repeatable(BasedOnJDKClass.List.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
@Platforms(Platform.HOSTED_ONLY.class)
public @interface BasedOnJDKClass {

    /**
     * @see TargetClass#value()
     */
    Class<?> value() default BasedOnJDKClass.class;

    /**
     * @see TargetClass#className()
     */
    String className() default "";

    /**
     * @see TargetClass#classNameProvider()
     */
    Class<? extends Function<BasedOnJDKClass, String>> classNameProvider() default BasedOnJDKClass.NoClassNameProvider.class;

    /**
     * @see TargetClass#innerClass()
     */
    String[] innerClass() default {};

    interface NoClassNameProvider extends Function<BasedOnJDKClass, String> {
    }

    /**
     * Support for making {@link BasedOnJDKClass} {@linkplain Repeatable repeatable}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(value = {ElementType.TYPE})
    @Platforms(Platform.HOSTED_ONLY.class)
    @interface List {
        BasedOnJDKClass[] value();
    }
}
