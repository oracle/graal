/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.options;

import java.util.ServiceLoader;

public class ModuleSupport {

    public static final boolean USE_NI_JPMS = Boolean.parseBoolean(System.getenv().get("USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM"));

    static Iterable<OptionDescriptors> getOptionsLoader() {
        /*
         * The Graal module (i.e., jdk.internal.vm.compiler) is loaded by the platform class loader
         * as of JDK 9. Modules that depend on and extend Graal are loaded by the app class loader.
         * As such, we need to start the provider search at the app class loader instead of the
         * platform class loader.
         */
        if (USE_NI_JPMS) {
            return ServiceLoader.load(ModuleSupport.class.getModule().getLayer(), OptionDescriptors.class);
        }
        return ServiceLoader.load(OptionDescriptors.class, ClassLoader.getSystemClassLoader());
    }
}
