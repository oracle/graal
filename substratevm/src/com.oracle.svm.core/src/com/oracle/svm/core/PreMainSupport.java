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

import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.jdk.ModuleNative;
import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

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

/**
 * Java agent can do instrumentation initialization work in premain phase. This class supports
 * registering such premain methods at native image build time and invoking them at native image
 * runtime. <br>
 * JVM supports two kind of premain methods:
 * <ol>
 * <li>{@code public static void premain(String agentArgs, Instrumentation inst)}</li>
 * <li>{@code public static void premain(String agentArgs)}</li>
 * </ol>
 * For the first one, at registration time we will set the second parameter with an instance of
 * {@link NativeImageNoOpRuntimeInstrumentation} class which does not do any actual work as no
 * instrumentation can do in native code at runtime.
 * <p>
 * <b>Be noticed</b>, the original agent premain method may not work well at native image runtime
 * even if the input {@link Instrumentation} class is replaced with
 * {@link NativeImageNoOpRuntimeInstrumentation}.
 * </p>
 * <p>
 * It is the agent developers' responsibility to implement a native version of their agent premain
 * method. It can be implemented in two ways:
 * <ul>
 * <li>Isolate code by checking current runtime. For example: <code>
 *         <pre>
 *         if ("runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
 *           // native image runtime
 *         } else{
 *           // JVM runtime
 *         }
 *         </pre>
 *     </code> Alternatively: Instead of directly getting property,
 * <code>ImageInfo.inImageRuntimeCode()</code> can also be used to check if current runtime is
 * native image runtime, but it requires extra dependency.</li>
 * <li>Use {@link com.oracle.svm.core.annotate.TargetClass} API to implement a native image version
 * premain.</li>
 * </ul>
 * </p>
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
     * Retrieve premain options from input args. Keep premain options and return the rest args as
     * main args. Multiple agent options should be given in separated {@code -XX-premain:} leading
     * arguments. The premain options format: <br>
     * -XX-premain:[full.qualified.premain.class]:[premain options]
     * -XX-premain:[full.qualified.premain.class2]:[premain options] <br>
     *
     * @param args original arguments for premain and main
     * @return arguments for main class
     */
    public String[] retrievePremainArgs(String[] args) {
        if (args == null) {
            return null;
        }
        List<String> mainArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith(PREMAIN_OPTION_PREFIX)) {
                String premainOptionKeyValue = arg.substring(PREMAIN_OPTION_PREFIX.length());
                String[] pair = premainOptionKeyValue.split(":");
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
                VMError.shouldNotReachHere("Fail to execute " + premainMethod.className + ".premain", cause);
            }
        }
    }

    /**
     * This class is a dummy implementation of {@link Instrumentation} interface. It serves as the
     * registered premain method's second parameter. At native image runtime, no actual
     * instrumentation work can do. So all the methods here are empty.
     */
    public static class NativeImageNoOpRuntimeInstrumentation implements Instrumentation {

        private static final Set<String> systemModules = Set.of("org.graalvm.nativeimage.builder", "org.graalvm.nativeimage", "org.graalvm.nativeimage.base", "com.oracle.svm.svm_enterprise",
                        "org.graalvm.word", "jdk.internal.vm.ci", "jdk.graal.compiler", "com.oracle.graal.graal_enterprise");

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
            CodeInfo imageCodeInfo = CodeInfoTable.getFirstImageCodeInfo();
            while (imageCodeInfo.isNonNull()) {
                Class<?>[] classes = NonmovableArrays.heapCopyOfObjectArray(CodeInfoAccess.getClasses(imageCodeInfo));
                if (classes != null) {
                    for (Class<?> clazz : classes) {
                        if (clazz != null) {
                            Module module = clazz.getModule();
                            if (module == null ||
                                            module.getName() == null ||
                                            !isSystemClass(module)) {
                                userClasses.add(clazz);
                            }
                        }
                    }
                }
                imageCodeInfo = CodeInfoAccess.getNextImageCodeInfo(imageCodeInfo);
            }
            return userClasses.toArray(new Class<?>[0]);
        }

        private static boolean isSystemClass(Module module) {
            return systemModules.contains(module.getName());
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
