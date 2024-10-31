/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.svm.core;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.instrument.UnmodifiableModuleException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jdk.ModuleNative;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ModuleSupport;

/**
 * Java agents can do initialization work before the main method is invoked. This class supports
 * registering such premain methods at native image build-time and invoking them at native image
 * run-time.
 *
 * Two different kinds of premain methods are supported:
 * <ol>
 * <li>{@code public static void premain(String agentArgs, Instrumentation inst)}</li>
 * <li>{@code public static void premain(String agentArgs)}</li>
 * </ol>
 *
 * For the first one, we set the second parameter to an instance of
 * {@link NativeImageNoOpRuntimeInstrumentation} as instrumentation is not supported at run-time.
 *
 * <p/>
 *
 * <b>Please note:</b> unmodified premain methods may not work well with native image. It is the
 * agent developers' responsibility to implement a native image-specific version of their premain
 * method. Below are a few approaches how to determine if the premain method is executed by native
 * image:
 * <ul>
 * <li>Check the system property {@code org.graalvm.nativeimage.imagecode}, e.g., if
 * {@code "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))} returns true,
 * the code is executed by native image at run-time.</li>
 * <li>Call {@link ImageInfo#inImageRuntimeCode}. However, note that this requires a dependency on
 * the native image API.</li>
 * </ul>
 *
 * As a last resort, it is also possible to substitute a premain method with a native image-specific
 * version.
 */
public class PreMainSupport {

    private static final String PREMAIN_OPTION_PREFIX = "-XXpremain:";

    /**
     * A record for premain method.
     *
     * @param className the full qualified class name that declares the premain method
     * @param method the premain method
     * @param args the arguments of premain method
     */
    record PremainMethod(String className, Method method, Object[] args) {
    }

    private final Map<String, String> premainOptions = new HashMap<>();
    // Order matters
    private final List<PremainMethod> premainMethods = new ArrayList<>();

    @Platforms({Platform.HOSTED_ONLY.class})
    public void registerPremainMethod(String className, Method executable, Object... args) {
        premainMethods.add(new PremainMethod(className, executable, args));
    }

    /**
     * Retrieves the premain options and stores them internally. Returns the remaining arguments so
     * that they can be passed to the Java main method. If multiple Java agents are used, a separate
     * {@code -XXpremain:} argument needs to be specified for each agent, e.g.:
     *
     * <pre>
     * -XXpremain:[full.qualified.premain.class1]:[premain options]
     * -XXpremain:[full.qualified.premain.class2]:[premain options]
     * </pre>
     *
     * @param args arguments for premain and main
     * @return arguments for the Java main method
     */
    public String[] retrievePremainArgs(String[] args) {
        if (args == null) {
            return null;
        }
        List<String> mainArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith(PREMAIN_OPTION_PREFIX)) {
                String premainOptionKeyValue = arg.substring(PREMAIN_OPTION_PREFIX.length());
                String[] pair = SubstrateUtil.split(premainOptionKeyValue, ":");
                if (pair.length == 2) {
                    premainOptions.put(pair[0], pair[1]);
                }
            } else {
                mainArgs.add(arg);
            }
        }
        return mainArgs.toArray(new String[0]);
    }

    public void invokePremain() {
        for (PremainMethod premainMethod : premainMethods) {
            Object[] args = premainMethod.args;
            if (premainOptions.containsKey(premainMethod.className)) {
                args[0] = premainOptions.get(premainMethod.className);
            }
            try {
                // premain method must be static
                premainMethod.method.invoke(null, args);
            } catch (Throwable t) {
                Throwable cause = t;
                if (t instanceof InvocationTargetException) {
                    cause = t.getCause();
                }
                throw VMError.shouldNotReachHere("Failed to execute " + premainMethod.className + ".premain", cause);
            }
        }
    }

    /**
     * At native image run-time, instrumentation is not possible. So, most operations either throw
     * an error or are no-ops.
     */
    public static class NativeImageNoOpRuntimeInstrumentation implements Instrumentation {

        @Override
        public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer) {
        }

        @Override
        public boolean removeTransformer(ClassFileTransformer transformer) {
            return false;
        }

        @Override
        public boolean isRetransformClassesSupported() {
            return false;
        }

        @Override
        public void retransformClasses(Class<?>... classes) throws UnmodifiableClassException {
            throw new UnsupportedOperationException("Native image doesn't support retransform class at runtime.");
        }

        @Override
        public boolean isRedefineClassesSupported() {
            return false;
        }

        @Override
        public void redefineClasses(ClassDefinition... definitions) throws ClassNotFoundException, UnmodifiableClassException {
            throw new UnsupportedOperationException("Native image doesn't support redefine class at runtime.");
        }

        @Override
        public boolean isModifiableClass(Class<?> theClass) {
            return false;
        }

        @Override
        public Class<?>[] getAllLoadedClasses() {
            ArrayList<Class<?>> userClasses = new ArrayList<>();
            Heap.getHeap().visitLoadedClasses(clazz -> {
                Module module = clazz.getModule();
                if (module == null || module.getName() == null || !isSystemClass(module)) {
                    userClasses.add(clazz);
                }
            });
            return userClasses.toArray(new Class<?>[0]);
        }

        private static boolean isSystemClass(Module module) {
            return ModuleSupport.SYSTEM_MODULES.contains(module.getName());
        }

        @Override
        public Class<?>[] getInitiatedClasses(ClassLoader loader) {
            throw new UnsupportedOperationException("Native image has flat classloader hierarchy. Can't get classes by classloader. " +
                            "Try to call getAllLoadedClasses() to get all loaded classes.");
        }

        @Override
        public long getObjectSize(Object objectToSize) {
            return -1;
        }

        @Override
        public void appendToBootstrapClassLoaderSearch(JarFile jarfile) {
            throw new UnsupportedOperationException("Native image doesn't support modification of the bootstrap classloader search path at run time." +
                            " Please avoid calling this method in Native Image by checking \"runtime\".equals(System.getProperty(\"org.graalvm.nativeimage.imagecode\"))");
        }

        @Override
        public void appendToSystemClassLoaderSearch(JarFile jarfile) {
            throw new UnsupportedOperationException("Native image doesn't support modification of the system classloader search path at run time." +
                            " Please avoid calling this method in Native Image by checking \"runtime\".equals(System.getProperty(\"org.graalvm.nativeimage.imagecode\"))");
        }

        @Override
        public boolean isNativeMethodPrefixSupported() {
            return false;
        }

        @Override
        public void setNativeMethodPrefix(ClassFileTransformer transformer, String prefix) {
        }

        @Override
        public void redefineModule(Module module, Set<Module> extraReads, Map<String, Set<Module>> extraExports, Map<String, Set<Module>> extraOpens, Set<Class<?>> extraUses,
                        Map<Class<?>, List<Class<?>>> extraProvides) {
            if (!module.isNamed()) {
                return;
            }

            if (!isModifiableModule(module)) {
                throw new UnmodifiableModuleException(module.getName());
            }

            for (Module extraRead : extraReads) {
                ModuleNative.addReads(module, extraRead);
            }

            for (Map.Entry<String, Set<Module>> entry : extraExports.entrySet()) {
                for (Module m : entry.getValue()) {
                    ModuleNative.addExports(module, entry.getKey(), m);
                }
            }
            // Ignore the extraOpens, extraUses and extraProvides as they are not supported by
            // ModuleNative.
        }

        @Override
        public boolean isModifiableModule(Module module) {
            if (module == null) {
                throw new NullPointerException("'module' is null");
            }
            return true;
        }
    }
}
