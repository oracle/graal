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
import com.oracle.truffle.espresso.meta.EspressoError;

class AgentLibrairies {

    private static final String AGENT_ONLOAD = "Agent_OnLoad";
    private static final String ONLOAD_SIGNATURE = "(pointer, pointer, pointer): sint32";

    private final EspressoContext context;

    private final List<AgentLibrary> agents = new ArrayList<>();
    private final InteropLibrary interop = InteropLibrary.getUncached();

    AgentLibrairies(EspressoContext context) {
        this.context = context;
    }

    TruffleObject bind(Method method, String mangledName) {
        for (AgentLibrary agent : agents) {
            try {
                return Method.bind(agent.lib, method, mangledName);
            } catch (UnknownIdentifierException e) {
                /* Not found in this library: Safe to ignore and check for the next one */
            }
        }
        return null;
    }

    void initialize() {
        Object ret;
        for (AgentLibrary agent : agents) {
            TruffleObject onLoad = lookupOnLoad(agent);
            if (onLoad == null || interop.isNull(onLoad)) {
                throw context.abort("Unable to locate " + AGENT_ONLOAD + " in agent " + agent.name);
            }
            try (RawBuffer optionBuffer = RawBuffer.getNativeString(agent.options)) {
                ret = interop.execute(onLoad, context.getVM().getJavaVM(), optionBuffer.pointer(), NativeEnv.RawPointer.nullInstance());
                assert interop.fitsInInt(ret);
                if (interop.asInt(ret) != JNI_OK) {
                    throw context.abort(AGENT_ONLOAD + " call for agent " + agent.name + " returned with error: " + interop.asInt(ret));
                }
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere();
            }
        }
    }

    void registerAgent(String agent) {
        /*
         * The agent string given should be of the form:
         * 
         * {+/-}<name><=options>
         * 
         * If the string starts with a `+`, then the name is an absolute path to the agent library,
         * otherwise, it is a library name to be loaded from standard library directories.
         */
        assert agent.length() > 0;
        String name;
        String options;
        boolean isAbsolutePath;
        char ch = agent.charAt(0);
        if (ch == '+') {
            isAbsolutePath = true;
        } else if (ch == '-') {
            isAbsolutePath = false;
        } else {
            // String starting with + or - should have been enforced by option parsing.
            throw EspressoError.shouldNotReachHere();
        }
        int eqIdx = agent.indexOf('=');
        if (eqIdx > 0) {
            name = agent.substring(1, eqIdx);
            options = agent.substring(eqIdx + 1);
        } else {
            name = agent.substring(1);
            options = "";
        }
        agents.add(new AgentLibrary(name, options, isAbsolutePath));
    }

    private TruffleObject lookupOnLoad(AgentLibrary agent) {
        TruffleObject library;
        // TODO: handle statically linked librairies
        if (agent.isAbsolutePath) {
            library = NativeLibrary.loadLibrary(Paths.get(agent.name));
        } else {
            // Lookup standard directory
            library = NativeEnv.loadLibraryInternal(context.getVmProperties().bootLibraryPath(), agent.name);
            if (interop.isNull(library)) {
                // Try library path directory
                library = NativeEnv.loadLibraryInternal(context.getVmProperties().javaLibraryPath(), agent.name);
            }
        }
        if (interop.isNull(library)) {
            throw context.abort("Could not locate library for agent " + agent.name);
        }
        agent.lib = library;

        TruffleObject onLoad;
        try {
            onLoad = NativeLibrary.lookupAndBind(library, AGENT_ONLOAD, ONLOAD_SIGNATURE);
        } catch (UnknownIdentifierException e) {
            return null;
        }
        return onLoad;
    }

    private static class AgentLibrary {

        final String name;
        final String options;

        final boolean isAbsolutePath;

        TruffleObject lib;

        AgentLibrary(String name, String options, boolean isAbsolutePath) {
            this.name = name;
            this.options = options;
            this.isAbsolutePath = isAbsolutePath;
        }

    }
}
