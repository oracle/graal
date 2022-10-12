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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import java.awt.Component;
import java.beans.Customizer;
import com.sun.beans.finder.ClassFinder;

@SuppressWarnings({"static-method", "unused"})
public final class JavaBeansSupport {

    /** Remains null as long as the reachability handler has not triggered. */
    private Class<?> COMPONENT_CLASS = null;

    // Checkstyle: stop
    @TargetClass(className = "java.beans.Introspector")
    static final class Target_java_beans_Introspector {

        @Substitute
        private static Class<?> findCustomizerClass(Class<?> type) {
            String name = type.getName() + "Customizer";
            try {
                type = ClassFinder.findClass(name, type.getClassLoader());
                // Each customizer should inherit java.awt.Component and implement java.beans.Customizer
                // according to the section 9.3 of JavaBeans specification
                Class<?> componentClass = lookupComponentClass();
                // The Customizer does not extend java.awt.Component because
                // java.awt.Component class is not reachable.
                if (componentClass == null) {
                    return null;
                }
                if (componentClass.isAssignableFrom(type) && Customizer.class.isAssignableFrom(type)) {
                    return type;
                }
            } catch (Exception exception) {
                // ignore any exceptions
            }
            return null;
        }
    }
    // Checkstyle: resume

    private static Class<?> lookupComponentClass() {
        return ImageSingletons.lookup(JavaBeansSupport.class).COMPONENT_CLASS;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void enableComponentClass() {
        ImageSingletons.lookup(JavaBeansSupport.class).COMPONENT_CLASS = Component.class;
    }
}
