/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.management;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

public class ManagementAgentModule {

    private static final Method agentStartAgent;
    private static final Method agentError;

    static final String CONFIG_FILE_ACCESS_DENIED;
    static final String CONFIG_FILE_CLOSE_FAILED;
    static final String CONFIG_FILE_NOT_FOUND;
    static final String CONFIG_FILE_OPEN_FAILED;

    static {
        Optional<Module> agentModule = ModuleLayer.boot().findModule("jdk.management.agent");
        if (agentModule.isPresent()) {
            ManagementAgentModule.class.getModule().addReads(agentModule.get());
            var agentClass = ReflectionUtil.lookupClass(false, "jdk.internal.agent.Agent");
            agentStartAgent = ReflectionUtil.lookupMethod(agentClass, "startAgent");
            agentError = ReflectionUtil.lookupMethod(agentClass, "error", String.class, String.class);
            var agentConfigurationErrorClass = ReflectionUtil.lookupClass(false, "jdk.internal.agent.AgentConfigurationError");
            CONFIG_FILE_ACCESS_DENIED = ReflectionUtil.readStaticField(agentConfigurationErrorClass, "CONFIG_FILE_ACCESS_DENIED");
            CONFIG_FILE_CLOSE_FAILED = ReflectionUtil.readStaticField(agentConfigurationErrorClass, "CONFIG_FILE_CLOSE_FAILED");
            CONFIG_FILE_NOT_FOUND = ReflectionUtil.readStaticField(agentConfigurationErrorClass, "CONFIG_FILE_NOT_FOUND");
            CONFIG_FILE_OPEN_FAILED = ReflectionUtil.readStaticField(agentConfigurationErrorClass, "CONFIG_FILE_OPEN_FAILED");
        } else {
            agentStartAgent = null;
            agentError = null;
            CONFIG_FILE_ACCESS_DENIED = null;
            CONFIG_FILE_CLOSE_FAILED = null;
            CONFIG_FILE_NOT_FOUND = null;
            CONFIG_FILE_OPEN_FAILED = null;
        }
    }

    public static boolean isPresent() {
        return agentStartAgent != null;
    }

    static class IsPresent implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return isPresent();
        }
    }

    static void agentError(String key, String message) {
        try {
            agentError.invoke(null, key, message);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere("Unable to reflectively invoke jdk.internal.agent.Agent.error(String, String)", e);
        }
    }

    static void agentStartAgent() {
        try {
            agentStartAgent.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere("Unable to reflectively invoke jdk.internal.agent.Agent.startAgent()", e);
        }
    }
}
