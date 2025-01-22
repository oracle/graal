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

import java.util.Set;
import java.util.regex.Pattern;

import com.oracle.svm.configure.filters.ConfigurationFilter;
import com.oracle.svm.configure.filters.HierarchyFilterNode;

import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.phases.common.LazyValue;

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
        internalCallerFilter.addOrGetChildren("java.io.ObjectInputFilter$Config", ConfigurationFilter.Inclusion.Include);

        internalCallerFilter.addOrGetChildren("java.lang.**", ConfigurationFilter.Inclusion.Exclude);
        // ClassLoader.findSystemClass calls ClassLoader.loadClass
        internalCallerFilter.addOrGetChildren("java.lang.ClassLoader", ConfigurationFilter.Inclusion.Include);
        // The agent should not filter calls from native libraries (JDK11).
        internalCallerFilter.addOrGetChildren("java.lang.ClassLoader$NativeLibrary", ConfigurationFilter.Inclusion.Include);
        // Module has resource query wrappers
        internalCallerFilter.addOrGetChildren("java.lang.Module", ConfigurationFilter.Inclusion.Include);
        internalCallerFilter.addOrGetChildren("java.math.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.net.**", ConfigurationFilter.Inclusion.Exclude);
        // URLConnection.lookupContentHandlerClassFor calls Class.forName
        internalCallerFilter.addOrGetChildren("java.net.URLConnection", ConfigurationFilter.Inclusion.Include);
        internalCallerFilter.addOrGetChildren("java.nio.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.text.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.time.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.util.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.util.concurrent.atomic.*", ConfigurationFilter.Inclusion.Include); // Atomic*FieldUpdater
        internalCallerFilter.addOrGetChildren("java.util.concurrent.atomic.AtomicReference", ConfigurationFilter.Inclusion.Exclude); // AtomicReference.<clinit>
        internalCallerFilter.addOrGetChildren("java.util.Collections", ConfigurationFilter.Inclusion.Include); // java.util.Collections.zeroLengthArray
        // LogRecord.readObject looks up resource bundles
        internalCallerFilter.addOrGetChildren("java.util.logging.LogRecord", ConfigurationFilter.Inclusion.Include);
        internalCallerFilter.addOrGetChildren("java.util.random.*", ConfigurationFilter.Inclusion.Include); // RandomGeneratorFactory$$Lambda
        // LazyClassPathLookupIterator calls Class.forName
        internalCallerFilter.addOrGetChildren("java.util.ServiceLoader$LazyClassPathLookupIterator", ConfigurationFilter.Inclusion.Include);
        internalCallerFilter.addOrGetChildren("javax.crypto.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("javax.lang.model.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("javax.net.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("javax.tools.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("jdk.internal.**", ConfigurationFilter.Inclusion.Exclude);
        // BootLoader calls BuiltinClassLoader.getResource
        internalCallerFilter.addOrGetChildren("jdk.internal.loader.BootLoader", ConfigurationFilter.Inclusion.Include);
        // The agent should not filter calls from native libraries (JDK17).
        internalCallerFilter.addOrGetChildren("jdk.internal.loader.NativeLibraries$NativeLibraryImpl", ConfigurationFilter.Inclusion.Include);
        internalCallerFilter.addOrGetChildren("jdk.jfr.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("jdk.net.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("jdk.nio.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("jdk.vm.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.invoke.**", ConfigurationFilter.Inclusion.Exclude);
        // BytecodeDescriptor calls Class.forName
        internalCallerFilter.addOrGetChildren("sun.invoke.util.BytecodeDescriptor", ConfigurationFilter.Inclusion.Include);
        internalCallerFilter.addOrGetChildren("sun.launcher.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.misc.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.net.**", ConfigurationFilter.Inclusion.Exclude);
        // Uses constructor reflection on exceptions
        internalCallerFilter.addOrGetChildren("sun.net.www.protocol.http.*", ConfigurationFilter.Inclusion.Include);
        internalCallerFilter.addOrGetChildren("sun.nio.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.reflect.**", ConfigurationFilter.Inclusion.Exclude);
        // The sun.reflect.misc package provides reflection utilities
        internalCallerFilter.addOrGetChildren("sun.reflect.misc.*", ConfigurationFilter.Inclusion.Include);
        internalCallerFilter.addOrGetChildren("sun.text.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.util.**", ConfigurationFilter.Inclusion.Exclude);
        // Bundles calls Bundles.of
        internalCallerFilter.addOrGetChildren("sun.util.resources.Bundles", ConfigurationFilter.Inclusion.Include);

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
        rootNode.addOrGetChildren("jdk.graal.compiler.**", ConfigurationFilter.Inclusion.Exclude);
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

    public boolean shouldIgnore(LazyValue<String> queriedClass, LazyValue<String> callerClass, boolean useLambdaHeuristics) {
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
            return (useLambdaHeuristics && queriedClass.get().contains(LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING)) ||
                            PROXY_CLASS_NAME_PATTERN.matcher(queriedClass.get()).matches();
        }
        return false;
    }

    public boolean shouldIgnore(LazyValue<String> queriedClass, LazyValue<String> callerClass) {
        return shouldIgnore(queriedClass, callerClass, true);
    }

    record JNICallDescriptor(String jniFunction, String declaringClass, String name, String signature, boolean required) {
        public boolean matches(String otherJniFunction, String otherDeclaringClass, String otherName, String otherSignature) {
            return jniFunction.equals(otherJniFunction) &&
                            (declaringClass == null || declaringClass.equals(otherDeclaringClass)) &&
                            (otherName == null || name.equals(otherName)) &&
                            (otherSignature == null || signature.equals(otherSignature));
        }
    }

    private static final JNICallDescriptor[] JNI_STARTUP_SEQUENCE = new JNICallDescriptor[]{
                    new JNICallDescriptor("GetStaticMethodID", "sun.launcher.LauncherHelper", "getApplicationClass", "()Ljava/lang/Class;", true),
                    new JNICallDescriptor("GetMethodID", "java.lang.Class", "getCanonicalName", "()Ljava/lang/String;", false),
                    new JNICallDescriptor("GetMethodID", "java.lang.String", "lastIndexOf", "(I)I", false),
                    new JNICallDescriptor("GetMethodID", "java.lang.String", "substring", "(I)Ljava/lang/String;", false),
                    new JNICallDescriptor("GetStaticMethodID", "java.lang.System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", false),
                    new JNICallDescriptor("GetStaticMethodID", "java.lang.System", "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false),
                    new JNICallDescriptor("GetStaticMethodID", null, "main", "([Ljava/lang/String;)V", true),
    };
    private static final int JNI_STARTUP_COMPLETE = JNI_STARTUP_SEQUENCE.length;
    private static final int JNI_STARTUP_MISMATCH_COMPLETE = JNI_STARTUP_COMPLETE + 1;

    /**
     * The JVM uses JNI calls internally when starting up. To avoid emitting configuration for these
     * calls, we ignore calls until an expected sequence of calls ({@link #JNI_STARTUP_SEQUENCE}) is
     * observed.
     */
    public boolean shouldIgnoreJniLookup(String jniFunction, LazyValue<String> queriedClass, LazyValue<String> name, LazyValue<String> signature, LazyValue<String> callerClass) {
        if (shouldIgnore(queriedClass, callerClass)) {
            throw new AssertionError("shouldIgnore must have been checked before shouldIgnoreJniLookup");
        }
        if (!heuristicsEnabled || launchPhase >= JNI_STARTUP_COMPLETE) {
            // Startup sequence completed (or we're not using the startup heuristics).
            return false;
        }
        if (!"GetStaticMethodID".equals(jniFunction) && !"GetMethodID".equals(jniFunction)) {
            // Ignore function calls for functions not tracked by the startup sequence.
            return true;
        }

        JNICallDescriptor expectedCall = JNI_STARTUP_SEQUENCE[launchPhase];
        while (!expectedCall.matches(jniFunction, queriedClass.get(), name.get(), signature.get())) {
            if ("sun.launcher.LauncherHelper".equals(queriedClass.get())) {
                // Ignore mismatched calls from sun.launcher.LauncherHelper.
                return true;
            }

            if (expectedCall.required) {
                // Mismatch on a required call. Mark startup as complete and start tracing JNI
                // calls. (We prefer to emit extraneous configuration than to lose configuration).
                launchPhase = JNI_STARTUP_MISMATCH_COMPLETE;
                return false;
            }

            // The call is optional (e.g., it only happens on some platforms). Skip it.
            launchPhase++;
            expectedCall = JNI_STARTUP_SEQUENCE[launchPhase];
        }
        return true;
    }

    public static boolean shouldIgnoreResourceLookup(LazyValue<String> resource) {
        return Set.of("META-INF/services/jdk.vm.ci.services.JVMCIServiceLocator", "META-INF/services/java.lang.System$LoggerFinder").contains(resource.get());
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
