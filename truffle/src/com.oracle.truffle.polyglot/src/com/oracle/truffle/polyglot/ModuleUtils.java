/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.Truffle;

final class ModuleUtils {

    static void exportTo(Module module) {
        exportFromTo(module);
    }

    static void exportToUnnamedModuleOf(ClassLoader loader) {
        exportFromTo(loader.getUnnamedModule());
    }

    static void exportTransitivelyTo(Module module) {
        ModuleLayer layer = module.getLayer();
        ClassLoader platformClassLoader = ClassLoader.getPlatformClassLoader();
        Set<Module> targetModules = new HashSet<>();
        Deque<Module> todo = new ArrayDeque<>();
        todo.add(module);
        while (!todo.isEmpty()) {
            Module m = todo.removeFirst();
            if (Objects.equals(m.getLayer(), layer)) {
                ClassLoader classLoader = m.getClassLoader();
                if (classLoader != null && !classLoader.equals(platformClassLoader)) {
                    targetModules.add(m);
                    ModuleDescriptor descriptor = m.getDescriptor();
                    if (descriptor != null && !descriptor.isAutomatic()) {
                        descriptor.requires().stream().//
                                        map((d) -> layer.findModule(d.name()).get()).//
                                        forEach(todo::add);
                    }
                }
            }
        }
        targetModules.forEach(ModuleUtils::exportTo);
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
