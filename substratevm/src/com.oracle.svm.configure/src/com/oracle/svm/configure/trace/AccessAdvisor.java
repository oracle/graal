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

/**
 * Decides if a recorded access should be included in a configuration. Also advises the agent's
 * {@code AccessVerifier} classes which accesses to ignore when the agent is in restriction mode.
 */
public final class AccessAdvisor {

    /** Filter to ignore accesses that <em>originate in</em> methods of these internal classes. */
    private static final RuleNode internalCallerFilter;

    /** Filter to ignore <em>accesses of</em> these classes and their members without a caller. */
    private static final RuleNode accessWithoutCallerFilter;

    static {
        internalCallerFilter = RuleNode.createRoot();
        internalCallerFilter.addOrGetChildren("**", RuleNode.Inclusion.Include);

        internalCallerFilter.addOrGetChildren("com.sun.crypto.provider.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("com.sun.java.util.jar.pack.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("com.sun.net.ssl.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("com.sun.nio.file.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("com.sun.nio.sctp.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("com.sun.nio.zipfs.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.io.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.lang.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.math.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.net.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.nio.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.text.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.time.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.util.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("javax.crypto.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("javax.lang.model.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("javax.net.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("javax.tools.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("jdk.internal.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("jdk.jfr.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("jdk.net.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("jdk.nio.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("jdk.vm.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.invoke.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.launcher.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.misc.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.net.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.nio.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.reflect.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.text.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.util.**", RuleNode.Inclusion.Exclude);

        internalCallerFilter.addOrGetChildren("org.graalvm.compiler.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("org.graalvm.libgraal.**", RuleNode.Inclusion.Exclude);
        internalCallerFilter.removeRedundantNodes();

        accessWithoutCallerFilter = RuleNode.createRoot();
        accessWithoutCallerFilter.addOrGetChildren("**", RuleNode.Inclusion.Include);
        accessWithoutCallerFilter.addOrGetChildren("jdk.vm.ci.**", RuleNode.Inclusion.Exclude);
        accessWithoutCallerFilter.addOrGetChildren("org.graalvm.compiler.**", RuleNode.Inclusion.Exclude);
        accessWithoutCallerFilter.addOrGetChildren("org.graalvm.libgraal.**", RuleNode.Inclusion.Exclude);
        accessWithoutCallerFilter.addOrGetChildren("[Ljava.lang.String;", RuleNode.Inclusion.Exclude);
        // ^ String[]: for command-line argument arrays created before Java main method is called
        accessWithoutCallerFilter.removeRedundantNodes();
    }

    public static RuleNode copyBuiltinCallerFilterTree() {
        return internalCallerFilter.copy();
    }

    public static RuleNode copyBuiltinAccessFilterTree() {
        RuleNode root = RuleNode.createRoot();
        root.addOrGetChildren("**", RuleNode.Inclusion.Include);
        return root;
    }

    private RuleNode callerFilter = internalCallerFilter;
    private RuleNode accessFilter = null;
    private boolean heuristicsEnabled = true;
    private boolean isInLivePhase = false;
    private int launchPhase = 0;

    public void setHeuristicsEnabled(boolean enable) {
        heuristicsEnabled = enable;
    }

    public void setCallerFilterTree(RuleNode rootNode) {
        callerFilter = rootNode;
    }

    public void setAccessFilterTree(RuleNode rootNode) {
        accessFilter = rootNode;
    }

    public void setInLivePhase(boolean live) {
        isInLivePhase = live;
    }

    public boolean shouldIgnore(LazyValue<String> queriedClass, LazyValue<String> callerClass) {
        if (heuristicsEnabled && !isInLivePhase) {
            return true;
        }
        String qualifiedCaller = callerClass.get();
        assert qualifiedCaller == null || qualifiedCaller.indexOf('/') == -1 : "expecting Java-format qualifiers, not internal format";
        if (qualifiedCaller != null && !callerFilter.treeIncludes(qualifiedCaller)) {
            return true;
        }
        // NOTE: queriedClass can be null for non-class accesses like resources
        if (callerClass.get() == null && queriedClass.get() != null && !accessWithoutCallerFilter.treeIncludes(queriedClass.get())) {
            return true;
        }
        return accessFilter != null && queriedClass.get() != null && !accessFilter.treeIncludes(queriedClass.get());
    }

    public boolean shouldIgnoreJniMethodLookup(LazyValue<String> queriedClass, LazyValue<String> name, LazyValue<String> signature, LazyValue<String> callerClass) {
        assert !shouldIgnore(queriedClass, callerClass) : "must have been checked before";
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
        /*
         * NOTE: JVM invocations cannot be reliably filtered with callerClass == null because these
         * could also be calls in a manually launched thread which is attached to JNI, but is not
         * executing Java code (yet).
         */
        return false;
    }

    public boolean shouldIgnoreLoadClass(LazyValue<String> queriedClass, LazyValue<String> callerClass) {
        assert !shouldIgnore(queriedClass, callerClass) : "must have been checked before";
        if (!heuristicsEnabled) {
            return false;
        }
        /*
         * Without a caller, we always assume that the class loader was invoked directly by the VM,
         * which indicates a system class (compiler, JVMCI, etc.) that we shouldn't need in our
         * configuration. The class loader could also have been called via JNI in a manually
         * attached native thread without Java frames, but that is unusual.
         */
        return callerClass.get() == null;
    }
}
