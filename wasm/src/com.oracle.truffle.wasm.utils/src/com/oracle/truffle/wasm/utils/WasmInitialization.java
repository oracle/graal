/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm.utils;

import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.wasm.WasmContext;
import com.oracle.truffle.wasm.WasmModule;
import com.oracle.truffle.wasm.memory.WasmMemory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class WasmInitialization implements Consumer<WasmContext>, TruffleObject {
    private final Map<String, Long> globalValues;
    private final Map<String, String> memoryValues;

    private WasmInitialization(Map<String, Long> globalValues, Map<String, String> memoryValues) {
        this.globalValues = globalValues;
        this.memoryValues = memoryValues;
    }

    public static WasmInitialization create(String initContent) {
        if (initContent == null) {
            return null;
        }

        final String[] lines = initContent.split("\n");
        Map<String, Long> globals = new LinkedHashMap<>();
        Map<String, String> memory = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.startsWith("[")) {
                // Memory store.
                String[] parts = line.split("=");
                String address = parts[0].substring(1, parts[0].length() - 1);
                String value = parts[1];
                memory.put(address, value);
            } else {
                // Global store.
                String[] parts = line.split("=");
                String name = parts[0];
                Long value = Long.parseLong(parts[1]);
                globals.put(name, value);
            }
        }

        return new WasmInitialization(globals, memory);
    }

    public void accept(WasmContext context) {
        try {
            final WasmModule module = context.modules().get("env");
            for (Map.Entry<String, Long> entry : globalValues.entrySet()) {
                final String name = entry.getKey();
                final Long value = entry.getValue();
                if (!module.isLinked() || module.global(name).isMutable()) {
                    module.writeMember(name, value);
                }
            }
            final WasmMemory memory = (WasmMemory) module.readMember("memory");
            for (Map.Entry<String, String> entry : memoryValues.entrySet()) {
                final String addressGlobal = entry.getKey();
                final long address = getValue(addressGlobal);
                final String valueGlobal = entry.getValue();
                final long value = getValue(valueGlobal);
                // The memory array writes are indexed with 64-bit words.
                // Therefore, we need to divide the byte-based address index with 8.
                memory.writeArrayElement(address / 8, value);
            }
        } catch (UnknownIdentifierException | UnsupportedMessageException | InvalidArrayIndexException e) {
            throw new RuntimeException(e);
        }
    }

    private long getValue(String s) {
        if (Character.isDigit(s.charAt(0))) {
            return Integer.parseInt(s);
        } else {
            return globalValues.get(s);
        }
    }
}
