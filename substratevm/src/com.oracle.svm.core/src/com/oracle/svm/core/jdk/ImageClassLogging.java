/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.registry.AbstractRuntimeClassRegistry;
import com.oracle.svm.core.logging.LogConfiguration;
import com.oracle.svm.core.logging.LogMessage;
import com.oracle.svm.core.logging.LogTagSet;
import com.oracle.svm.guest.staging.jdk.RuntimeSupport;
import com.oracle.svm.shared.Uninterruptible;

/// Reports classes and modules included in the image using the final startup logging options.
public final class ImageClassLogging {
    /// Keeps the hook implementation in a runtime class, not in its hosted registration feature.
    @Platforms(Platform.HOSTED_ONLY.class)
    public static RuntimeSupport.Hook initializationHook() {
        return _ -> {
            LogConfiguration.logInitializationComplete();
            reportImageClasses();
        };
    }

    /// Reports image classes and modules after startup logging options have been applied.
    private static void reportImageClasses() {
        boolean logClassLoad = LogTagSet.class_load_image.isInfo();
        boolean logModuleLoad = LogTagSet.module_load_image.isInfo();
        if (logClassLoad || logModuleLoad) {
            EconomicSet<Module> reportedModules = EconomicSet.create();
            if (logModuleLoad) {
                /*
                 * This initialization hook runs after option parsing, before the startup hook
                 * augments the boot layer with modules from the runtime module path. The boot layer
                 * therefore still contains exactly the modules included in the image here.
                 */
                for (Module module : ModuleLayer.boot().modules()) {
                    reportImageModule(module, reportedModules);
                }
            }
            Heap.getHeap().visitLoadedClasses((cls) -> {
                DynamicHub hub = DynamicHub.fromClass(cls);
                if (!hub.isArray() && !hub.isPrimitive()) {
                    if (logClassLoad) {
                        ClassLoader loader = hub.getClassLoader();
                        AbstractRuntimeClassRegistry.logClassLoadingMessage(LogTagSet.class_load_image, hub.getName(), loader, null, "image");
                    }
                    if (logModuleLoad) {
                        reportImageModule(hub.getModule(), reportedModules);
                    }
                }
            });
        }
    }

    /// Reports `module` once if it is named.
    private static void reportImageModule(Module module, EconomicSet<Module> reportedModules) {
        String moduleName = ModuleNative.getName(module);
        if (moduleName != null && reportedModules.add(module)) {
            logImageModule(moduleName);
        }
    }

    /// Emits the prepared module name within a checked message scope.
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_OWNER)
    private static void logImageModule(String moduleName) {
        LogMessage message = LogTagSet.module_load_image.message();
        try {
            message.info().string(moduleName).string(" location: image");
        } finally {
            message.close();
        }
    }

}
