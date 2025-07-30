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
import java.util.Map;

import org.graalvm.options.OptionMap;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.impl.ContextAccessImpl;
import com.oracle.truffle.espresso.jni.RawBuffer;
import com.oracle.truffle.espresso.jvmti.JvmtiPhase;
import com.oracle.truffle.espresso.meta.EspressoError;

final class AgentLibraries extends ContextAccessImpl {

    private static final String AGENT_ONLOAD = "Agent_OnLoad";
    private static final NativeSignature ONLOAD_SIGNATURE = NativeSignature.create(NativeType.INT, NativeType.POINTER, NativeType.POINTER, NativeType.POINTER);

    private final List<AgentLibrary> agents = new ArrayList<>(0);
    private final InteropLibrary interop = InteropLibrary.getUncached();

    AgentLibraries(EspressoContext context) {
        super(context);
    }

    TruffleObject lookupSymbol(String mangledName) {
        for (AgentLibrary agent : agents) {
            TruffleObject symbol = getNativeAccess().lookupSymbol(agent.lib, mangledName);
            if (symbol != null) {
                return symbol;
            }
        }
        return null;
    }

    boolean isEmpty() {
        return agents.isEmpty();
    }

    void initialize() {
        Object ret;

        getVM().getJvmti().enterPhase(JvmtiPhase.ONLOAD);

        for (AgentLibrary agent : agents) {
            TruffleObject onLoad = lookupOnLoad(agent);
            if (onLoad == null) {
                throw getContext().abort("Unable to locate " + AGENT_ONLOAD + " in agent " + agent.name);
            }
            try (RawBuffer optionBuffer = RawBuffer.getNativeString(agent.options)) {
                ret = interop.execute(onLoad, getContext().getVM().getJavaVM(), optionBuffer.pointer(), RawPointer.nullInstance());
                assert interop.fitsInInt(ret);
                if (interop.asInt(ret) != JNI_OK) {
                    throw getContext().abort(AGENT_ONLOAD + " call for agent " + agent.name + " returned with error: " + interop.asInt(ret));
                }
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        getVM().getJvmti().enterPhase(JvmtiPhase.PRIMORDIAL);
    }

    void registerAgents(OptionMap<String> map, boolean isAbsolutePath) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            registerAgent(name, value, isAbsolutePath);
        }
    }

    void registerAgent(String name, String value, boolean isAbsolutePath) {
        agents.add(new AgentLibrary(name, value, isAbsolutePath));
    }

    private TruffleObject lookupOnLoad(AgentLibrary agent) {
        TruffleObject library;
        // TODO: handle statically linked libraries
        if (agent.isAbsolutePath) {
            library = getNativeAccess().loadLibrary(Paths.get(agent.name));
        } else {
            // Lookup standard directory
            library = getNativeAccess().loadLibrary(getContext().getVmProperties().bootLibraryPath(), agent.name, false);
            if (library == null) {
                // Try library path directory
                library = getNativeAccess().loadLibrary(getContext().getVmProperties().javaLibraryPath(), agent.name, false);
            }
        }
        if (library == null) {
            throw getContext().abort("Could not locate library for agent " + agent.name);
        }
        agent.lib = library;

        return getNativeAccess().lookupAndBindSymbol(library, AGENT_ONLOAD, ONLOAD_SIGNATURE, true, true);
    }

    public void noSupportWarning(TruffleLogger logger) {
        assert !agents.isEmpty();
        logger.warning("Native agent support is currently disabled in Espresso.");
        for (AgentLibrary agent : agents) {
            logger.warning("Ignoring passed native agent: " + agent.name);
        }
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
