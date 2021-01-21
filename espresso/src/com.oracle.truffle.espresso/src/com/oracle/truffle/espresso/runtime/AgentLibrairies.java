/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.runtime;

import static com.oracle.truffle.espresso.jni.JniEnv.JNI_OK;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.jni.NativeEnv;
import com.oracle.truffle.espresso.jni.NativeLibrary;
import com.oracle.truffle.espresso.jni.RawBuffer;

class AgentLibrairies {

    private static final String AGENT_ONLOAD = "Agent_OnLoad";
    private static final String ONLOAD_SIGNATURE = "(pointer, pointer, pointer): sint32";

    private final EspressoContext context;

    private final List<AgentLibrary> agents = new ArrayList<>();
    private InteropLibrary interop = InteropLibrary.getUncached();

    TruffleObject bind(Method method, String mangledName) {
        for (AgentLibrary agent : agents) {
            try {
                return Method.bind(agent.lib, method, mangledName);
            } catch (UnknownIdentifierException e) {
                /* Safe to ignore */
            }
        }
        return null;
    }

    AgentLibrairies(EspressoContext context) {
        this.context = context;
    }

    void initialize() {
        Object ret;
        for (AgentLibrary agent : agents) {
            TruffleObject onLoad = lookupOnLoad(agent);
            if (onLoad == null || interop.isNull(onLoad)) {
                throw abort();
            }
            try (RawBuffer optionBuffer = RawBuffer.getNativeString(agent.options)) {
                try {
                    ret = interop.execute(onLoad, context.getVM().getJavaVM(), optionBuffer.pointer(), NativeEnv.RawPointer.nullInstance());
                    if (!interop.fitsInInt(ret) || interop.asInt(ret) != JNI_OK) {
                        throw abort();
                    }
                } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                    throw abort();
                }
            }
        }
    }

    void registerAgent(String agent) {
        assert agent.length() > 0;
        String name;
        String options;
        boolean isAbsolutePath = (agent.charAt(0) == '+');
        int eqIdx = agent.indexOf('=');
        if (eqIdx > 0) {
            name = agent.substring(1, eqIdx);
            options = agent.substring(eqIdx + 1);
        } else {
            name = agent.substring(1);
            options = null;
        }
        agents.add(new AgentLibrary(name, options, isAbsolutePath));
    }

    private TruffleObject lookupOnLoad(AgentLibrary agent) {
        TruffleObject library;
        // TODO: handle statically linked librairies
        if (agent.isAbsolutePath) {
            library = NativeLibrary.loadLibrary(Paths.get(agent.name));
        } else {
            // Lookup standard dll directory
            library = NativeEnv.loadLibraryInternal(context.getVmProperties().bootLibraryPath(), agent.name);
            if (interop.isNull(library)) {
                // Try library path directory
                library = NativeEnv.loadLibraryInternal(context.getVmProperties().javaLibraryPath(), agent.name);
            }
        }
        if (interop.isNull(library)) {
            throw abort();
        }
        agent.lib = library;
        agent.isValid = true;

        TruffleObject onLoad;
        try {
            onLoad = NativeLibrary.lookupAndBind(library, AGENT_ONLOAD, ONLOAD_SIGNATURE);
        } catch (UnknownIdentifierException e) {
            return null;
        }
        return onLoad;
    }

    private EspressoExitException abort() {
        throw new EspressoExitException(1);
    }

    private static class AgentLibrary {

        final String name;
        final String options;

        final boolean isAbsolutePath;
        boolean isValid = false;
        boolean isStaticLib;

        TruffleObject lib;

        public AgentLibrary(String name, String options, boolean isAbsolutePath) {
            this.name = name;
            this.options = options;
            this.isAbsolutePath = isAbsolutePath;
        }

    }
}
