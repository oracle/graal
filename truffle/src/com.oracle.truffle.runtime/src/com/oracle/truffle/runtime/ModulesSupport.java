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

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.Truffle;

public final class ModulesSupport {

    private static final boolean ATTACH_AVAILABLE;
    private static final ModulesAccessor ACCESSOR;

    static {
        if (isClassIsolationDisabled()) {
            ATTACH_AVAILABLE = loadModulesSupportLibrary();
        } else {
            ATTACH_AVAILABLE = ModulesSupport.class.getModule().isNamed() && loadModulesSupportLibrary();
        }

        if (ATTACH_AVAILABLE) {
            // this is the only access we really need to request natively using JNI.
            // after that we can access through the Modules class
            ACCESSOR = ModulesAccessor.create(ModulesSupport.class);
            addExports0(ModuleLayer.boot().findModule("java.base").orElseThrow(), "jdk.internal.module", ACCESSOR.getTargetModule());
        } else {
            ACCESSOR = null;
        }
    }

    private static boolean isClassIsolationDisabled() {
        return Boolean.parseBoolean(System.getProperty("polyglotimpl.DisableClassPathIsolation", "true"));
    }

    private ModulesSupport() {
    }

    /**
     * This is invoked reflectively from {@link Truffle}.
     */
    public static String exportJVMCI(Class<?> toClass) {
        Class<?> currentClass = toClass;
        Module previousModule = null;
        Set<Module> seenModules = new HashSet<>();
        while (currentClass != null && currentClass != Object.class) {
            Module currentModule = currentClass.getModule();
            if (!currentModule.equals(previousModule)) {
                String s = exportJVMCI(currentModule, seenModules);
                if (s != null) {
                    return s;
                }
            }
            previousModule = currentModule;
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }

    public static String exportJVMCI(Module module) {
        return exportJVMCI(module, new HashSet<>());
    }

    private static String exportJVMCI(Module module, Set<Module> seenModules) {
        ModuleLayer layer = module.getLayer();
        if (layer == null) {
            layer = ModuleLayer.boot();
        }
        Module jvmciModule = layer.findModule("jdk.internal.vm.ci").orElse(null);
        if (jvmciModule == null) {
            // jvmci not found -> fallback to default runtime
            return "JVMCI is not enabled for this JVM. Enable JVMCI using -XX:+EnableJVMCI.";
        }

        if (!ATTACH_AVAILABLE) {
            return "The Truffle attach library is not available.";
        }
        addExportsRecursive(layer, jvmciModule, module, seenModules);
        return null;
    }

    private static void addExportsRecursive(ModuleLayer layer, Module sourceModule, Module exportTo, Set<Module> seenModules) {
        if (seenModules.contains(exportTo)) {
            return;
        }
        for (String pn : sourceModule.getPackages()) {
            addExports(sourceModule, pn, exportTo);
        }
        seenModules.add(exportTo);
        ModuleDescriptor descriptor = exportTo.getDescriptor();
        if (descriptor != null) { // unnamed module has a null descriptor
            for (Requires requires : descriptor.requires()) {
                Module requiredModule = layer.findModule(requires.name()).orElse(null);
                if (requiredModule != null) {
                    String name = requiredModule.getName();
                    if (name.startsWith("java.") || name.startsWith("jdk.")) {
                        // no need to export to those
                        continue;
                    }
                    addExportsRecursive(layer, sourceModule, requiredModule, seenModules);
                }
            }
        }
    }

    public static void addExports(Module base, String p, Module target) {
        if (target.isNamed()) {
            ACCESSOR.addExports(base, p, target);
        } else {
            ACCESSOR.addExportsToAllUnnamed(base, p);
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
