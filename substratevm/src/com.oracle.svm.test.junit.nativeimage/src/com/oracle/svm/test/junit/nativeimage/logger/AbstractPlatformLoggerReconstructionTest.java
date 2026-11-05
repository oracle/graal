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
package com.oracle.svm.test.logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.junit.Assert;

import com.oracle.svm.test.NativeImageBuildArgs;
import com.oracle.svm.shared.util.ModuleSupport;

@NativeImageBuildArgs({
                "--features=com.oracle.svm.test.logger.AbstractPlatformLoggerReconstructionTest$TestFeature",
                "--add-exports=java.base/sun.util.logging=ALL-UNNAMED"
})
abstract class AbstractPlatformLoggerReconstructionTest {
    public static class TestFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            exportPlatformLoggerPackage();
            RuntimeClassInitialization.initializeAtBuildTime(BuildTimePlatformLoggerRoot.class);
            BuildTimePlatformLoggerRoot.logger();
            BuildTimePlatformLoggerRoot.rogueLogger();
        }

        private static void exportPlatformLoggerPackage() {
            try {
                Class<?> platformLoggerClass = Class.forName("sun.util.logging.PlatformLogger");
                ModuleSupport.accessModuleByClass(ModuleSupport.Access.EXPORT, AbstractPlatformLoggerReconstructionTest.class, platformLoggerClass);
                ModuleSupport.accessModuleByClass(ModuleSupport.Access.OPEN, AbstractPlatformLoggerReconstructionTest.class, platformLoggerClass);
            } catch (ClassNotFoundException e) {
                throw new AssertionError("Failed to load sun.util.logging.PlatformLogger", e);
            }
        }
    }

    private static final class BuildTimePlatformLoggerRoot {
        private static final String LOGGER_NAME = "com.oracle.svm.test.logger.PlatformLoggerReconstructionTest";
        private static final String ROGUE_LOGGER_NAME = LOGGER_NAME + ".rogue";
        private static final Object LOGGER = getPlatformLogger(LOGGER_NAME);
        private static final Object ROGUE_LOGGER = createUncachedPlatformLogger(ROGUE_LOGGER_NAME);

        static Object logger() {
            return LOGGER;
        }

        static Object rogueLogger() {
            return ROGUE_LOGGER;
        }
    }

    protected static void assertBuildTimePlatformLoggerPreservedInRuntimeCache(boolean expectPreserved) {
        Object buildTimeLogger = BuildTimePlatformLoggerRoot.logger();
        Object runtimeLookup = getPlatformLogger(BuildTimePlatformLoggerRoot.LOGGER_NAME);
        if (expectPreserved) {
            Assert.assertSame("Expected the runtime cache to reuse the build-time PlatformLogger object.", buildTimeLogger, runtimeLookup);
        } else {
            Assert.assertNotSame("Expected logging-disabled images to recreate PlatformLogger at runtime.", buildTimeLogger, runtimeLookup);
        }
    }

    protected static void assertReachableButUncachedBuildTimePlatformLoggerNotInsertedIntoRuntimeCache() {
        Object rogueBuildTimeLogger = BuildTimePlatformLoggerRoot.rogueLogger();
        Object runtimeLookup = getPlatformLogger(BuildTimePlatformLoggerRoot.ROGUE_LOGGER_NAME);
        Assert.assertNotSame("Expected runtime lookup to ignore reachable PlatformLogger instances that were not present in the original cache.", rogueBuildTimeLogger, runtimeLookup);
    }

    private static Object getPlatformLogger(String name) {
        try {
            Class<?> platformLoggerClass = Class.forName("sun.util.logging.PlatformLogger");
            Method getLoggerMethod = platformLoggerClass.getMethod("getLogger", String.class);
            return getLoggerMethod.invoke(null, name);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to access sun.util.logging.PlatformLogger", e);
        }
    }

    private static Object createUncachedPlatformLogger(String name) {
        try {
            Class<?> platformLoggerClass = Class.forName("sun.util.logging.PlatformLogger");
            Class<?> bridgeClass = Class.forName("sun.util.logging.PlatformLogger$Bridge");
            Method convertMethod = bridgeClass.getMethod("convert", System.Logger.class);
            Object bridge = convertMethod.invoke(null, System.getLogger(name));
            Constructor<?> constructor = platformLoggerClass.getDeclaredConstructor(bridgeClass);
            constructor.setAccessible(true);
            return constructor.newInstance(bridge);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new AssertionError("Failed to construct uncached sun.util.logging.PlatformLogger", e);
        }
    }
}
