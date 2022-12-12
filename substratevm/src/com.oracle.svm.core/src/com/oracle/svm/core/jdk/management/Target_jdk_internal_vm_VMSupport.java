/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.management;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;

import java.util.Properties;
import com.oracle.svm.core.JavaMainWrapper.JavaMainSupport;
import org.graalvm.nativeimage.ImageSingletons;

@TargetClass(jdk.internal.vm.VMSupport.class)
final class Target_jdk_internal_vm_VMSupport {

    @Substitute
    @TargetElement(onlyWith = JmxServerIncluded.class)
    private static Properties initAgentProperties(Properties properties) {
        if (ImageSingletons.contains(JavaMainSupport.class)) {
            JavaMainSupport support = ImageSingletons.lookup(JavaMainSupport.class);
            properties.setProperty("sun.jvm.args", support.getJavaCommand());
            properties.setProperty("sun.jvm.flags", "");
            properties.setProperty("sun.java.command", support.getJavaCommand());
        }
        return properties;
    }
}
