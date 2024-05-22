/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.svm.core.annotate;

import jdk.internal.module.ModuleLoaderMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Predicate;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Platforms(Platform.HOSTED_ONLY.class)
public @interface Aspect {
    /**
     * Specify a list of class names that will be enhanced.
     *
     * @return full qualified class names
     */
    String[] matchers() default {};

    /**
     * All subClasses of the specified class will be enhanced.
     *
     * @return
     */
    String subClassOf() default "";

    /**
     * Match all classes that implement the specified interface.
     * 
     * @return the qualified name of interface
     */
    String implementInterface() default "";

    /**
     * Same as {@link TargetElement#onlyWith()}.
     *
     * @return
     */
    Class<?>[] onlyWith() default TargetClass.AlwaysIncluded.class;

    /**
     * Check if the given class is JDK class.
     */
    class JDKClassOnly implements Predicate<Class<?>> {

        @Override
        public boolean test(Class<?> c) {
            String moduleName = c.getModule().getName();
            if (moduleName == null) {
                return false;
            }
            return ModuleLoaderMap.bootModules().contains(moduleName) || ModuleLoaderMap.platformModules().contains(moduleName);
        }
    }
}
