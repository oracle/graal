/*
 * Copyright (c) 2022, BELLSOFT. All rights reserved.
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.sun.beans.finder.ClassFinder;

@SuppressWarnings({"static-method", "unused"})
public final class JavaBeansSubstitutions {
    // Checkstyle: stop

    // Do not make string final to avoid class name interception
    // in Class.forName(...) call
    private static String COMPONENT_CLASS = "java.awt.Component";
    private static String CUSTOMIZER_CLASS = "java.beans.Customizer";

    @TargetClass(className = "java.beans.Introspector")
    static final class Target_java_beans_Introspector {

        /**
         * Do not load java.awt.Component and java.beans.Customizer classes
         * when they are not used
         */
        @Substitute
        private static Class<?> findCustomizerClass(Class<?> type) {
            String name = type.getName() + "Customizer";
            try {
                type = ClassFinder.findClass(name, type.getClassLoader());
                if (Class.forName(COMPONENT_CLASS).isAssignableFrom(type)
                        && Class.forName(CUSTOMIZER_CLASS).isAssignableFrom(type)) {
                    return type;
                }
            } catch (Exception exception) {
                // ignore any exceptions
            }
            return null;
        }
    }
    // Checkstyle: resume
}
