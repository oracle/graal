/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.lang.module.ModuleDescriptor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.oracle.truffle.api.Truffle;

final class ModuleUtils {

    static void exportTo(Module clientModule) {
        if (!isExportedTo(clientModule)) {
            exportFromTo(clientModule);
        }
    }

    static void exportToUnnamedModuleOf(ClassLoader loader) {
        exportTo(loader.getUnnamedModule());
    }

    static void exportTransitivelyTo(Module clientModule) {
        if (isExportedTo(clientModule)) {
            return;
        }

        ModuleLayer layer = clientModule.getLayer();
        Module truffleModule = Truffle.class.getModule();
        Set<Module> targetModules = new HashSet<>();
        Deque<Module> todo = new ArrayDeque<>();
        /*
         * The module graph with reads and provides edges is not a DAG. We need to keeping track of
         * visited modules to detect cycles.
         */
        Set<Module> visited = new HashSet<>();
        todo.add(clientModule);
        Map<String, Set<Module>> serviceDictionary = null;
        while (!todo.isEmpty()) {
            Module module = todo.removeFirst();
            if (visited.add(module) && Objects.equals(module.getLayer(), layer) && readsTruffleModule(truffleModule, module)) {
                targetModules.add(module);
                ModuleDescriptor descriptor = module.getDescriptor();
                if (descriptor == null) {
                    /*
                     * Unnamed module: Deprecated. The unnamed module does not have a module
                     * descriptor, but reads the entire module graph. For unnamed modules we do not
                     * do transitive export because we would have to open the Truffle module to all
                     * modules in the module layer.
                     */
                } else if (descriptor.isAutomatic()) {
                    /*
                     * Automatic module: An unnamed module has an artificial module descriptor, with
                     * only the mandated `requires java.base` directive. But an automatic module
                     * reads the entire module graph. For automatic modules we do not do transitive
                     * export because we would have to open the Truffle module to all modules in the
                     * module layer.
                     */
                } else {
                    /*
                     * Named module with a module descriptor: Export transitively to all modules
                     * required by the named module.
                     */
                    for (ModuleDescriptor.Requires requires : descriptor.requires()) {
                        Module requiredModule = findModule(layer, requires);
                        if (requiredModule != null) {
                            todo.add(requiredModule);
                        }
                    }
                    // Open also to modules providing a service consumed by the module.
                    Set<String> usedServices = descriptor.uses();
                    if (!usedServices.isEmpty()) {
                        if (serviceDictionary == null) {
                            serviceDictionary = new HashMap<>();
                            for (Module m : layer.modules()) {
                                if (readsTruffleModule(truffleModule, m)) {
                                    for (ModuleDescriptor.Provides provides : m.getDescriptor().provides()) {
                                        serviceDictionary.computeIfAbsent(provides.service(), (k) -> new HashSet<>()).add(m);
                                    }
                                }
                            }
                        }
                        for (String service : usedServices) {
                            todo.addAll(serviceDictionary.getOrDefault(service, Set.of()));
                        }
                    }
                }
            }
        }
        targetModules.forEach(ModuleUtils::exportFromTo);
    }

    private static boolean readsTruffleModule(Module truffleModule, Module otherModule) {
        return otherModule != truffleModule && otherModule.canRead(truffleModule);
    }

    private static Module findModule(ModuleLayer layer, ModuleDescriptor.Requires requires) {
        Optional<Module> moduleOrNull = layer.findModule(requires.name());
        if (moduleOrNull.isPresent()) {
            return moduleOrNull.get();
        } else if (requires.modifiers().contains(ModuleDescriptor.Requires.Modifier.STATIC)) {
            // Optional runtime dependency may not be available.
            return null;
        } else {
            throw new AssertionError(String.format("A non-optional module %s not found in the module layer %s.", requires.name(), layer));
        }
    }

    private static boolean isExportedTo(Module clientModule) {
        Module truffleModule = Truffle.class.getModule();
        for (String pack : truffleModule.getPackages()) {
            if (!truffleModule.isExported(pack, clientModule)) {
                return false;
            }
        }
        return true;
    }

    private static void exportFromTo(Module clientModule) {
        Module truffleModule = Truffle.class.getModule();
        if (truffleModule != clientModule) {
            Set<String> packages = truffleModule.getPackages();
            for (String pkg : packages) {
                boolean exported = truffleModule.isExported(pkg, clientModule);
                if (!exported) {
                    truffleModule.addExports(pkg, clientModule);
                }
            }
        }
    }

}
