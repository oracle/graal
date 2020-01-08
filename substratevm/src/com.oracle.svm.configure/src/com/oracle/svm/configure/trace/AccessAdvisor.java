/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.trace;

import org.graalvm.compiler.phases.common.LazyValue;

import com.oracle.svm.configure.filters.RuleNode;

public final class AccessAdvisor {

    private static final RuleNode internalsFilter;
    static {
        internalsFilter = RuleNode.createRoot();
        internalsFilter.addOrGetChildren("**", RuleNode.Inclusion.Include);
        internalsFilter.addOrGetChildren("java.**", RuleNode.Inclusion.Exclude);
        internalsFilter.addOrGetChildren("javax.**", RuleNode.Inclusion.Exclude);
        internalsFilter.addOrGetChildren("sun.**", RuleNode.Inclusion.Exclude);
        internalsFilter.addOrGetChildren("com.sun.**", RuleNode.Inclusion.Exclude);
        internalsFilter.addOrGetChildren("jdk.**", RuleNode.Inclusion.Exclude);
        internalsFilter.addOrGetChildren("org.graalvm.compiler.**", RuleNode.Inclusion.Exclude);
        internalsFilter.removeRedundantNodes();
    }

    public static RuleNode copyBuiltinFilterTree() {
        return internalsFilter.copy();
    }

    private RuleNode callerFilter = internalsFilter;
    private boolean heuristicsEnabled = true;
    private boolean isInLivePhase = false;
    private int launchPhase = 0;

    private boolean filterExcludesCaller(String qualifiedClass) {
        assert qualifiedClass == null || qualifiedClass.indexOf('/') == -1 : "expecting Java-format qualifiers, not internal format";
        return qualifiedClass != null && !callerFilter.treeIncludes(qualifiedClass);
    }

    public void setHeuristicsEnabled(boolean enable) {
        heuristicsEnabled = enable;
    }

    public void setCallerFilterTree(RuleNode rootNode) {
        callerFilter = rootNode;
    }

    public void setInLivePhase(boolean live) {
        isInLivePhase = live;
    }

    public boolean shouldIgnoreCaller(LazyValue<String> qualifiedClass) {
        return (heuristicsEnabled && !isInLivePhase) || filterExcludesCaller(qualifiedClass.get());
    }

    public boolean shouldIgnoreJniMethodLookup(LazyValue<String> queriedClass, LazyValue<String> name, LazyValue<String> signature, LazyValue<String> callerClass) {
        if (shouldIgnoreCaller(callerClass)) {
            return true;
        }
        if (!heuristicsEnabled) {
            return false;
        }
        // Heuristic to ignore this sequence during startup:
        // 1. Lookup of LauncherHelper.getApplicationClass()
        // 2. Lookup of Class.getCanonicalName() -- only on Darwin
        // 3. Lookup of application's main(String[])
        if ("sun.launcher.LauncherHelper".equals(queriedClass.get())) {
            if (launchPhase == 0 && "getApplicationClass".equals(name.get()) && "()Ljava/lang/Class;".equals(signature.get())) {
                launchPhase = 1;
            }
            return true;
        }
        if (launchPhase == 1 && "getCanonicalName".equals(name.get()) && "()Ljava/lang/String;".equals(signature.get())) {
            launchPhase = 2;
            return true;
        }
        if (launchPhase > 0) {
            launchPhase = -1;
            if ("main".equals(name.get()) && "([Ljava/lang/String;)V".equals(signature.get())) {
                return true;
            }
        }
        // Ignore libjvmcicompiler internal JNI calls:
        // * jdk.vm.ci.services.Services.getJVMCIClassLoader
        // * org.graalvm.compiler.hotspot.management.libgraal.runtime.SVMToHotSpotEntryPoints
        if (callerClass.get() == null && "jdk.vm.ci.services.Services".equals(queriedClass.get()) && "getJVMCIClassLoader".equals(name.get()) &&
                        "()Ljava/lang/ClassLoader;".equals(signature.get())) {
            return true;
        }
        if (callerClass.get() == null && "org.graalvm.compiler.hotspot.management.libgraal.runtime.SVMToHotSpotEntryPoints".equals(queriedClass.get())) {
            return true;
        }
        /*
         * NOTE: JVM invocations cannot be reliably filtered with callerClass == null because these
         * could also be calls in a manually launched thread which is attached to JNI, but is not
         * executing Java code (yet).
         */
        return false;
    }

    public boolean shouldIgnoreJniClassLookup(LazyValue<String> name, LazyValue<String> callerClass) {
        if (shouldIgnoreCaller(callerClass)) {
            return true;
        }
        if (!heuristicsEnabled) {
            return false;
        }
        // Ignore libjvmcicompiler internal JNI calls: jdk.vm.ci.services.Services
        if (callerClass.get() == null && "jdk.vm.ci.services.Services".equals(name.get())) {
            return true;
        }
        return false;
    }

    public boolean shouldIgnoreJniNewObjectArray(LazyValue<String> arrayClass, LazyValue<String> callerClass) {
        if (shouldIgnoreCaller(callerClass)) {
            return true;
        }
        if (!heuristicsEnabled) {
            return false;
        }
        if (callerClass.get() == null && "[Ljava.lang.String;".equals(arrayClass.get())) {
            /*
             * For command-line argument arrays created before the Java main method is called. We
             * cannot detect this only via launchPhase on Java 8.
             */
            return true;
        }
        return false;
    }
}
