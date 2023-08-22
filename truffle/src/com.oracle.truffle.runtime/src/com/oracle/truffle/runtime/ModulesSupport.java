/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime;

import java.lang.module.ModuleDescriptor.Requires;
import java.lang.reflect.Method;
import java.nio.file.Path;

import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.Truffle;

import jdk.internal.module.Modules;

public final class ModulesSupport {

    private static final boolean ATTACH_AVAILABLE;

    static {
        Module moduleSupportModule = ModulesSupport.class.getModule();
        // Don't export jdk.internal.module to an unnamed module when ModulesSupport is on the
        // classpath
        ATTACH_AVAILABLE = moduleSupportModule.isNamed() && loadModulesSupportLibrary();

        if (ATTACH_AVAILABLE) {
            // this is the only access we really need to request natively using JNI.
            // after that we can access through the Modules class
            addExports0(moduleSupportModule.getLayer().findModule("java.base").orElseThrow(), "jdk.internal.module", ModulesSupport.class.getModule());
        }
    }

    private ModulesSupport() {
    }

    public static String exportJVMCI(Module module) {
        ModuleLayer layer = module.getLayer();
        if (layer == null) {
            /*
             * Truffle is running in an unnamed module, so we cannot export jvmci to it.
             */
            if (ModulesSupport.class.getModule().getLayer() != null) {
                /*
                 * Even if the runtime is in the unnamed mode, make sure truffle runtime classes are
                 * in a module layer and ensure they have access to JVMCI.
                 */
                exportJVMCI(ModulesSupport.class);
            }
            return "Truffle was loaded from the class-path. Use Truffle from the module-path instead. Did you disable polyglot class-path isolation?";
        }

        Module jvmciModule = layer.findModule("jdk.internal.vm.ci").orElse(null);
        if (jvmciModule == null) {
            // jvmci not found -> fallback to default runtime
            return "JVMCI is not enabled for this JVM. Enable JVMCI using -XX:+EnableJVMCI.";
        }

        if (!ATTACH_AVAILABLE) {
            return "The Truffle attach library is not available.";
        }

        addExportsRecursive(jvmciModule, module);
        return null;
    }

    /**
     * This is invoked reflectively from {@link Truffle}.
     */
    public static String exportJVMCI(Class<?> toClass) {
        return exportJVMCI(toClass.getModule());
    }

    private static void addExportsRecursive(Module jvmciModule, Module runtimeModule) {
        for (String pn : jvmciModule.getPackages()) {
            ModulesSupport.addExports(jvmciModule, pn, runtimeModule);
        }
        for (Requires requires : runtimeModule.getDescriptor().requires()) {
            Module requiredModule = ModulesSupport.class.getModule().getLayer().findModule(requires.name()).orElse(null);
            if (requiredModule != null) {
                if (requiredModule.getName().equals("java.base")) {
                    continue;
                }
                addExportsRecursive(jvmciModule, requiredModule);
            }
        }
    }

    public static void addExports(Module m1, String pn, Module m2) {
        // we check available to avoid illegal access errors for the Modules class
        if (ATTACH_AVAILABLE) {
            Modules.addExports(m1, pn, m2);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private static boolean loadModulesSupportLibrary() {
        String attachLibPath = System.getProperty("truffle.attach.library");
        try {
            if (attachLibPath == null) {
                Class<?> resourceCacheClass = Class.forName("com.oracle.truffle.polyglot.InternalResourceCache", false, ModulesSupport.class.getClassLoader());
                Method installRuntimeResource = resourceCacheClass.getDeclaredMethod("installRuntimeResource", InternalResource.class);
                installRuntimeResource.setAccessible(true);
                Path root = (Path) installRuntimeResource.invoke(null, new LibTruffleAttachResource());
                Path libAttach = root.resolve("bin").resolve(System.mapLibraryName("truffleattach"));
                attachLibPath = libAttach.toString();
            }
            System.load(attachLibPath);
            return true;
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    private static native void addExports0(Module m1, String pn, Module m2);

}
