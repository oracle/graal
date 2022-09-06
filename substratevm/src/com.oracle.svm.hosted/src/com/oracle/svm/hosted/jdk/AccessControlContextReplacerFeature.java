/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jdk;

import java.security.AccessControlContext;
import java.security.DomainCombiner;
import java.security.Permission;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.AccessControllerUtil;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticallyRegisteredFeature
@SuppressWarnings({"unused"})
class AccessControlContextReplacerFeature implements InternalFeature {

    static Map<String, AccessControlContext> allowedContexts = new HashMap<>();

    static void allowContextIfExists(String className, String fieldName) {
        try {
            // Checkstyle: stop
            Class<?> clazz = Class.forName(className);
            // Checkstyle: resume
            String description = className + "." + fieldName;
            try {
                AccessControlContext acc = ReflectionUtil.readStaticField(clazz, fieldName);
                allowedContexts.put(description, acc);
            } catch (ReflectionUtil.ReflectionUtilError e) {
                throw VMError.shouldNotReachHere("Following field isn't present in JDK" + JavaVersionUtil.JAVA_SPEC + ": " + description);
            }

        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere("Following class isn't present in JDK" + JavaVersionUtil.JAVA_SPEC + ": " + className);
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        /*
         * Following AccessControlContexts are allowed in the image heap since they cannot leak
         * sensitive information. They originate from JDK's static final fields, and they do not
         * feature CodeSources, DomainCombiners etc.
         *
         * New JDK versions will remove old contexts (since AccessControlContext is marked as
         * deprecated since JDK 17), so this method should be kept up-to-date. When a listed context
         * is removed from JDK, an error message will be thrown from
         * AccessControlContextReplacerFeature.allowContextIfExists, so maintaining this list should
         * not be difficult (just adding upper bound for JAVA_SPEC in if statements below).
         *
         * In addition to these contexts, only the very simple contexts are permitted in the image
         * heap (see isSimpleContext method).
         */
        allowContextIfExists("java.util.Calendar$CalendarAccessControlContext", "INSTANCE");
        allowContextIfExists("javax.management.monitor.Monitor", "noPermissionsACC");

        if (JavaVersionUtil.JAVA_SPEC >= 11) {
            allowContextIfExists("java.security.AccessController$AccHolder", "innocuousAcc");
            if (JavaVersionUtil.JAVA_SPEC < 19) {
                allowContextIfExists("java.util.concurrent.ForkJoinPool$DefaultForkJoinWorkerThreadFactory", "ACC");
            }
        }
        if (JavaVersionUtil.JAVA_SPEC < 17) {
            allowContextIfExists("java.util.concurrent.ForkJoinWorkerThread", "INNOCUOUS_ACC");
        }
        if (JavaVersionUtil.JAVA_SPEC >= 11 && JavaVersionUtil.JAVA_SPEC < 17) {
            allowContextIfExists("java.util.concurrent.ForkJoinPool$InnocuousForkJoinWorkerThreadFactory", "ACC");
        }
        if (JavaVersionUtil.JAVA_SPEC >= 17 && JavaVersionUtil.JAVA_SPEC < 19) {
            allowContextIfExists("java.util.concurrent.ForkJoinPool$WorkQueue", "INNOCUOUS_ACC");
            allowContextIfExists("java.util.concurrent.ForkJoinPool$DefaultCommonPoolForkJoinWorkerThreadFactory", "ACC");
        }
        access.registerObjectReplacer(AccessControlContextReplacerFeature::replaceAccessControlContext);
    }

    private static boolean isSimpleContext(AccessControlContext ctx) {
        /*
         * In addition to aforementioned allow-listed contexts we also allow inclusion of very
         * strict subset of contexts that couldn't possibly leak sensitive information in the image
         * heap. This set of rules is overly strict on purpose as we want to be on a safe side.
         *
         * Issues could arise only in cases where end-users manually marked classes that rely on
         * doPrivileged invocation in static initializers for initialization at build time. At that
         * point they will be presented with an error message from
         * Target_java_security_AccessController.checkContext that will inform them about potential
         * fixes.
         */
        ProtectionDomain[] context = ReflectionUtil.readField(AccessControlContext.class, "context", ctx);
        AccessControlContext privilegedContext = ReflectionUtil.readField(AccessControlContext.class, "privilegedContext", ctx);
        DomainCombiner combiner = ReflectionUtil.readField(AccessControlContext.class, "combiner", ctx);
        Permission[] permissions = ReflectionUtil.readField(AccessControlContext.class, "permissions", ctx);
        AccessControlContext parent = ReflectionUtil.readField(AccessControlContext.class, "parent", ctx);
        ProtectionDomain[] limitedContext = ReflectionUtil.readField(AccessControlContext.class, "limitedContext", ctx);

        if (context != null && context.length > 0) {
            return checkPD(context);
        }
        if (combiner != null) {
            return false;
        }
        if (parent != null) {
            return isSimpleContext(parent);
        }
        if (limitedContext != null && limitedContext.length > 0) {
            return checkPD(limitedContext);
        }
        if (privilegedContext != null) {
            return isSimpleContext(privilegedContext);
        }
        return true;
    }

    private static boolean checkPD(ProtectionDomain[] list) {
        for (ProtectionDomain pd : list) {
            if (pd.getCodeSource() != null) {
                return false;
            }
            if (pd.getPrincipals().length > 0) {
                return false;
            }
            if (pd.getPermissions() != null) {
                return false;
                /*
                 * Technically we could allow certain permissions but this could be fragile.
                 * Contexts from user code should be reinitialized at runtime anyways.
                 */
            }
        }
        return true;
    }

    private static Object replaceAccessControlContext(Object obj) {
        if (obj instanceof AccessControlContext && obj != AccessControllerUtil.DISALLOWED_CONTEXT_MARKER) {
            if (allowedContexts.containsValue(obj)) {
                return obj;
            } else if (isSimpleContext((AccessControlContext) obj)) {
                return obj;
            } else {
                return AccessControllerUtil.DISALLOWED_CONTEXT_MARKER;
            }
        }
        return obj;
    }
}
