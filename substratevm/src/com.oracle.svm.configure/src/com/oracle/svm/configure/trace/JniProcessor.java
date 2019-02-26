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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.oracle.svm.configure.config.JniConfiguration;
import com.oracle.svm.configure.config.JniMethod;

import jdk.vm.ci.meta.MetaUtil;

class JniProcessor extends AbstractProcessor {
    private final JniConfiguration configuration = new JniConfiguration();
    private boolean filter = true;
    private boolean previousIsGetApplicationClass = false;

    public void setFilterEnabled(boolean enabled) {
        filter = enabled;
    }

    public JniConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    void processEntry(Map<String, ?> entry) {
        String function = (String) entry.get("function");
        String clazz = (String) entry.get("class");
        String callerClass = (String) entry.get("caller_class");
        List<?> args = (List<?>) entry.get("args");
        if (filter && shouldFilter(function, clazz, callerClass, args)) {
            return;
        }
        switch (function) {
            case "DefineClass": {
                String name = singleElement(args);
                if (name.startsWith("com/sun/proxy/$Proxy")) {
                    break; // implementation detail of Proxy support
                }
                logWarning("Unsupported JNI function DefineClass used to load class " + name);
                break;
            }
            case "FindClass": {
                String name = singleElement(args);
                if (name.charAt(0) != '[') {
                    name = "L" + name + ";";
                }
                name = MetaUtil.internalNameToJava(name, true, false);
                configuration.getOrCreateType(name);
                break;
            }
            case "GetMethodID":
            case "GetStaticMethodID": {
                expectSize(args, 2);
                String name = (String) args.get(0);
                String signature = (String) args.get(1);
                configuration.getOrCreateType(clazz).getMethods().add(new JniMethod(name, signature));
                break;
            }
            case "GetFieldID":
            case "GetStaticFieldID": {
                expectSize(args, 2);
                String name = (String) args.get(0);
                configuration.getOrCreateType(clazz).getFields().add(name);
                break;
            }
        }
    }

    private boolean shouldFilter(String function, String clazz, String callerClass, List<?> args) {
        if (!isInLivePhase() || (callerClass != null && isInternalClass(callerClass))) {
            return true;
        }

        // Heuristic: filter LauncherHelper as well as a lookup of main(String[]) that
        // immediately follows a lookup of LauncherHelper.getApplicationClass()
        if ("sun.launcher.LauncherHelper".equals(clazz)) {
            previousIsGetApplicationClass = function.equals("GetStaticMethodID") &&
                            args.equals(Arrays.asList("getApplicationClass", "()Ljava/lang/Class;"));
            return true;
        }
        if (previousIsGetApplicationClass) {
            if (function.equals("GetStaticMethodID") && args.equals(Arrays.asList("main", "([Ljava/lang/String;)V"))) {
                return true;
            }
            previousIsGetApplicationClass = false;
        }

        /*
         * NOTE: JVM invocations cannot be reliably filtered with callerClass == null because these
         * could also be calls in a manually launched thread which is attached to JNI, but is not
         * executing Java code (yet).
         */
        return false;
    }
}
