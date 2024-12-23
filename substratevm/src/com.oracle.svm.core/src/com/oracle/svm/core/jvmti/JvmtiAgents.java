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
package com.oracle.svm.core.jvmti;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport.NativeLibrary;
import com.oracle.svm.core.jni.functions.JNIFunctionTables;
import com.oracle.svm.core.jni.headers.JNIJavaVM;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.util.StringUtil;

import jdk.graal.compiler.api.replacements.Fold;

/** Loads/Unloads JVMTI agents that are located in shared object files. */
public class JvmtiAgents {
    private static final String AGENT_ON_LOAD = "Agent_OnLoad";
    private static final String AGENT_ON_UNLOAD = "Agent_OnUnload";

    private final ArrayList<NativeLibrary> agents = new ArrayList<>();

    @Platforms(Platform.HOSTED_ONLY.class)
    public JvmtiAgents() {
    }

    @Fold
    public static JvmtiAgents singleton() {
        return ImageSingletons.lookup(JvmtiAgents.class);
    }

    public void load() {
        String agentLib = SubstrateOptions.JVMTIAgentLib.getValue();
        if (agentLib != null) {
            loadAgent(agentLib, true);
        }

        String agentPath = SubstrateOptions.JVMTIAgentPath.getValue();
        if (agentPath != null) {
            loadAgent(agentPath, false);
        }
    }

    public void unload() {
        for (NativeLibrary lib : agents) {
            PointerBase function = lib.findSymbol(AGENT_ON_UNLOAD);
            if (function.isNonNull()) {
                callOnUnLoadFunction(function);
            }
        }
        agents.clear();
    }

    private void loadAgent(String agentAndOptions, boolean relative) {
        if (!agents.isEmpty()) {
            throw new AgentInitException("Only a single agent is supported at the moment.");
        }

        try {
            loadAgent0(agentAndOptions, relative);
        } catch (AgentInitException e) {
            Log.log().string(e.getMessage()).newline();
            if (!SubstrateOptions.SharedLibrary.getValue()) {
                System.exit(1);
            }
        }
    }

    private void loadAgent0(String agentAndOptions, boolean relative) {
        String[] values = StringUtil.split(agentAndOptions, "=", 2);
        String agent = values[0];
        String options = values.length > 1 ? values[1] : null;

        String agentFile = getAgentFile(agent, relative);
        NativeLibrary lib = PlatformNativeLibrarySupport.singleton().createLibrary(agentFile, false);
        if (!lib.load()) {
            throw new AgentInitException("Could not load agent library '" + agentFile + "'.");
        }

        PointerBase function = lib.findSymbol(AGENT_ON_LOAD);
        if (function.isNull()) {
            throw new AgentInitException("Could not find Agent_OnLoad function in agent library '" + agentFile + "'.");
        }

        if (!callOnLoadFunction(function, options)) {
            throw new AgentInitException("Initialization of agent library '" + agentFile + "' failed.");
        }
        agents.add(lib);
    }

    private static String getAgentFile(String agent, boolean relative) {
        File file;
        if (relative) {
            String sysPath = NativeLibrarySupport.getImageDirectory();
            String libname = System.mapLibraryName(agent);
            file = new File(sysPath, libname);
            if (!file.exists()) {
                throw new AgentInitException("Could not find agent library '" + agent + "' on the library path.");
            }
        } else {
            file = new File(agent);
            if (!file.exists()) {
                throw new AgentInitException("Could not find agent library '" + agent + "'.");
            }
        }

        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            throw new AgentInitException("Path of agent library '" + agent + "' is invalid: " + e.getMessage());
        }
    }

    private static boolean callOnLoadFunction(PointerBase function, String options) {
        JvmtiOnLoadFunctionPointer onLoad = (JvmtiOnLoadFunctionPointer) function;
        try (CTypeConversion.CCharPointerHolder holder = CTypeConversion.toCString(options)) {
            int result = onLoad.invoke(JNIFunctionTables.singleton().getGlobalJavaVM(), holder.get(), Word.nullPointer());
            /* Any value other than 0 is an error. */
            return result == 0;
        }
    }

    private static void callOnUnLoadFunction(PointerBase function) {
        JvmtiOnUnLoadFunctionPointer onLoad = (JvmtiOnUnLoadFunctionPointer) function;
        onLoad.invoke(JNIFunctionTables.singleton().getGlobalJavaVM(), Word.nullPointer());
    }

    private static class AgentInitException extends RuntimeException {
        @Serial private static final long serialVersionUID = 3111979452718981714L;

        AgentInitException(String message) {
            super(message);
        }
    }
}

interface JvmtiOnLoadFunctionPointer extends CFunctionPointer {
    @InvokeCFunctionPointer
    int invoke(JNIJavaVM vm, CCharPointer options, VoidPointer reserved);
}

interface JvmtiOnUnLoadFunctionPointer extends CFunctionPointer {
    @InvokeCFunctionPointer
    void invoke(JNIJavaVM vm, VoidPointer reserved);
}
