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

import java.util.regex.Pattern;

import org.graalvm.compiler.java.LambdaUtils;
import org.graalvm.compiler.phases.common.LazyValue;

import com.oracle.svm.configure.filters.ConfigurationFilter;
import com.oracle.svm.configure.filters.HierarchyFilterNode;

/**
 * Decides if a recorded access should be included in a configuration. Also advises the agent's
 * {@code AccessVerifier} classes which accesses to ignore when the agent is in restriction mode.
 */
public final class AccessAdvisor {
    /**
     * {@link java.lang.reflect.Proxy} generated classes can be put in arbitrary packages depending
     * on the visibility and module of the interfaces they implement, so we can only match against
     * their class name using this pattern (which we hope isn't used by anything else).
     */
    public static final Pattern PROXY_CLASS_NAME_PATTERN = Pattern.compile("^(.+[/.])?\\$Proxy[0-9]+$");

    /** Filter to ignore accesses that <em>originate in</em> methods of these internal classes. */
    private static final HierarchyFilterNode internalCallerFilter;

    /** Filter to unconditionally ignore <em>accesses of</em> these classes and their members. */
    private static final HierarchyFilterNode internalAccessFilter;

    /**
     * Filter to ignore <em>accesses of</em> these classes and their members when the caller
     * (accessing class) is unknown. Used in addition to {@link #accessFilter}, not instead.
     */
    private static final HierarchyFilterNode accessWithoutCallerFilter;

    static {
        internalCallerFilter = HierarchyFilterNode.createInclusiveRoot();

        internalCallerFilter.addOrGetChildren("com.sun.crypto.provider.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("com.sun.java.util.jar.pack.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("com.sun.net.ssl.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("com.sun.nio.file.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("com.sun.nio.sctp.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("com.sun.nio.zipfs.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.io.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.lang.**", ConfigurationFilter.Inclusion.Exclude);
        // The agent should not filter calls from native libraries (JDK11).
        internalCallerFilter.addOrGetChildren("java.lang.ClassLoader$NativeLibrary", ConfigurationFilter.Inclusion.Include);
        internalCallerFilter.addOrGetChildren("java.math.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.net.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.nio.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.text.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.time.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.util.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.util.concurrent.atomic.*", ConfigurationFilter.Inclusion.Include); // Atomic*FieldUpdater
        internalCallerFilter.addOrGetChildren("java.util.Collections", ConfigurationFilter.Inclusion.Include); // java.util.Collections.zeroLengthArray
        internalCallerFilter.addOrGetChildren("javax.crypto.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("javax.lang.model.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("javax.net.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("javax.tools.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("jdk.internal.**", ConfigurationFilter.Inclusion.Exclude);
        // The agent should not filter calls from native libraries (JDK17).
        internalCallerFilter.addOrGetChildren("jdk.internal.loader.NativeLibraries$NativeLibraryImpl", ConfigurationFilter.Inclusion.Include);
        internalCallerFilter.addOrGetChildren("jdk.jfr.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("jdk.net.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("jdk.nio.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("jdk.vm.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.invoke.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.launcher.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.misc.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.net.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.nio.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.reflect.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.text.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.util.**", ConfigurationFilter.Inclusion.Exclude);

        excludeInaccessiblePackages(internalCallerFilter);

        internalCallerFilter.removeRedundantNodes();

        internalAccessFilter = HierarchyFilterNode.createInclusiveRoot();
        excludeInaccessiblePackages(internalAccessFilter);
        internalAccessFilter.removeRedundantNodes();

        accessWithoutCallerFilter = HierarchyFilterNode.createInclusiveRoot(); // in addition to
                                                                               // accessFilter

        accessWithoutCallerFilter.addOrGetChildren("jdk.vm.ci.**", ConfigurationFilter.Inclusion.Exclude);
        accessWithoutCallerFilter.addOrGetChildren("[Ljava.lang.String;", ConfigurationFilter.Inclusion.Exclude);
        // ^ String[]: for command-line argument arrays created before Java main method is called
        accessWithoutCallerFilter.removeRedundantNodes();
    }

    /*
     * Exclude selection of packages distributed with GraalVM which are not unconditionally exported
     * by their module and should not be accessible from application code. Generate all with:
     * native-image-configure generate-filters --exclude-unexported-packages-from-modules [--reduce]
     */
    private static void excludeInaccessiblePackages(HierarchyFilterNode rootNode) {
        rootNode.addOrGetChildren("com.oracle.graal.**", ConfigurationFilter.Inclusion.Exclude);
        rootNode.addOrGetChildren("com.oracle.truffle.**", ConfigurationFilter.Inclusion.Exclude);
        rootNode.addOrGetChildren("org.graalvm.compiler.**", ConfigurationFilter.Inclusion.Exclude);
        rootNode.addOrGetChildren("org.graalvm.libgraal.**", ConfigurationFilter.Inclusion.Exclude);
    }

    public static HierarchyFilterNode copyBuiltinCallerFilterTree() {
        return internalCallerFilter.copy();
    }

    public static HierarchyFilterNode copyBuiltinAccessFilterTree() {
        return internalAccessFilter.copy();
    }

    private ConfigurationFilter callerFilter = internalCallerFilter;
    private ConfigurationFilter accessFilter = internalAccessFilter;
    private boolean heuristicsEnabled = true;
    private boolean isInLivePhase = false;
    private int launchPhase = 0;

    public void setHeuristicsEnabled(boolean enable) {
        heuristicsEnabled = enable;
    }

    public void setCallerFilterTree(ConfigurationFilter rootNode) {
        callerFilter = rootNode;
    }

    public void setAccessFilterTree(ConfigurationFilter rootNode) {
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
        if (qualifiedCaller != null && !callerFilter.includes(qualifiedCaller)) {
            return true;
        }
        // NOTE: queriedClass can be null for non-class accesses like resources
        if (callerClass.get() == null && queriedClass.get() != null && !accessWithoutCallerFilter.includes(queriedClass.get())) {
            return true;
        }
        if (accessFilter != null && queriedClass.get() != null && !accessFilter.includes(queriedClass.get())) {
            return true;
        }
        if (heuristicsEnabled && queriedClass.get() != null) {
            if (queriedClass.get().contains(LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING) ||
                            PROXY_CLASS_NAME_PATTERN.matcher(queriedClass.get()).matches()) {
                return true;
            }
        }
        return false;
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
