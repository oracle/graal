/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ObjectStreamClass;
import java.io.Serializable;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.hub.DynamicHub;

@TargetClass(ObjectStreamClass.class)
final class Target_java_io_ObjectStreamClass {
    @Substitute
    static ObjectStreamClass lookup(Class<?> cl, boolean all) {
        if (!all && !Serializable.class.isAssignableFrom(cl)) {
            return null;
        }
        return ImageSingletons.lookup(SerializationSupport.class).getStreamClass(cl);
    }

    @Substitute
    static boolean hasStaticInitializer(Class<?> cl) {
        return DynamicHub.fromClass(cl).getClassInitializationInfo().hasClassInitializer();
    }
}

@TargetClass(java.io.ObjectInputStream.class)
@SuppressWarnings({"static-method"})
final class Target_java_io_ObjectInputStream {
    /**
     * Private method latestUserDefinedLoader is called by
     * java.io.ObjectInputStream.resolveProxyClass and java.io.ObjectInputStream.resolveClass. The
     * returned classloader is eventually used in Class.forName and Proxy.getProxyClass0 which are
     * substituted by Substrate VM and the classloader is ignored. Therefore, this substitution is
     * safe.
     *
     * @return The only classloader in native image
     */
    @Substitute
    private static ClassLoader latestUserDefinedLoader() {
        return Target_java_io_ObjectInputStream.class.getClassLoader();
    }
}
