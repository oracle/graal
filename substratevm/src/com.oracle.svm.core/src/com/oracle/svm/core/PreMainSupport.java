/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Alibaba Group Holding Limited. All rights reserved.
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

import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
 * {@link SVMRuntimeInstrumentImpl} class which does not do any actual work as no instrumentation
 * can do in native code at runtime.
 * <p>
 * <b>Be noticed</b>, the original agent premain method may not work well at native image runtime
 * even if the input {@link Instrumentation} class is replaced with
 * {@link SVMRuntimeInstrumentImpl}.
 * </p>
 * <p>
 * It is the agent developers' responsibility to implement a native version of their agent premain
 * method. It can be implemented in two ways:
 * <ul>
 * <li>Isolate code by checking current runtime. For example: <code>
 *         <pre>
 *         String vm = System.getProperty("java.vm.name");
 *         if ("Substrate VM".equals(vm)) {
 *           // native image runtime
 *         } else{
 *           // JVM runtime
 *         }
 *         </pre>
 *     </code></li>
 * <li>Use {@link com.oracle.svm.core.annotate.TargetClass} API to implement a native image version
 * premain.</li>
 * </ul>
 * </p>
 */
public class PreMainSupport {
    class PremainMethod {
        String name;
        Method method;
        Object[] args;

        PremainMethod(String name, Method method, Object[] args) {
            this.name = name;
            this.method = method;
            this.args = args;
        }
    }

    // Order matters
    private List<PremainMethod> premainMethods = new ArrayList<>();

    @Platforms({Platform.HOSTED_ONLY.class})
    public void registerPremainMethod(String className, Method executable, Object... args) {
        premainMethods.add(new PremainMethod(className, executable, args));
    }

    public void invokePremain() {
        for (PremainMethod premainMethod : premainMethods) {
            // premain method must be static
            try {
                premainMethod.method.invoke(null, premainMethod.args);
            } catch (Throwable t) {
                VMError.shouldNotReachHere("Fail to execute " + premainMethod.name + ".premain", t);
            }
        }
    }

    /**
     * This class is a dummy implementation of {@link Instrumentation} interface. It serves as the
     * registered premain method's second parameter. At native image runtime, no actual
     * instrumentation work can do. So all the methods here are empty.
     */
    public static class SVMRuntimeInstrumentImpl implements Instrumentation {

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
        }

        @Override
        public boolean isRedefineClassesSupported() {
            return false;
        }

        @Override
        public void redefineClasses(ClassDefinition... definitions) throws ClassNotFoundException, UnmodifiableClassException {
        }

        @Override
        public boolean isModifiableClass(Class<?> theClass) {
            return false;
        }

        @Override
        public Class<?>[] getAllLoadedClasses() {
            return new Class<?>[0];
        }

        @Override
        public Class<?>[] getInitiatedClasses(ClassLoader loader) {
            return new Class<?>[0];
        }

        @Override
        public long getObjectSize(Object objectToSize) {
            return 0;
        }

        @Override
        public void appendToBootstrapClassLoaderSearch(JarFile jarfile) {
        }

        @Override
        public void appendToSystemClassLoaderSearch(JarFile jarfile) {
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
        }

        @Override
        public boolean isModifiableModule(Module module) {
            return false;
        }
    }
}
