/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.types;

import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.Objects;

/**
 * Arguments for 'disassemble' request.
 */
public class DisassembleArguments extends JSONBase {

    DisassembleArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Memory reference to the base location containing the instructions to disassemble.
     */
    public String getMemoryReference() {
        return jsonData.getString("memoryReference");
    }

    public DisassembleArguments setMemoryReference(String memoryReference) {
        jsonData.put("memoryReference", memoryReference);
        return this;
    }

    /**
     * Optional offset (in bytes) to be applied to the reference location before disassembling. Can
     * be negative.
     */
    public Integer getOffset() {
        return jsonData.has("offset") ? jsonData.getInt("offset") : null;
    }

    public DisassembleArguments setOffset(Integer offset) {
        jsonData.putOpt("offset", offset);
        return this;
    }

    /**
     * Optional offset (in instructions) to be applied after the byte offset (if any) before
     * disassembling. Can be negative.
     */
    public Integer getInstructionOffset() {
        return jsonData.has("instructionOffset") ? jsonData.getInt("instructionOffset") : null;
    }

    public DisassembleArguments setInstructionOffset(Integer instructionOffset) {
        jsonData.putOpt("instructionOffset", instructionOffset);
        return this;
    }

    /**
     * Number of instructions to disassemble starting at the specified location and offset. An
     * adapter must return exactly this number of instructions - any unavailable instructions should
     * be replaced with an implementation-defined 'invalid instruction' value.
     */
    public int getInstructionCount() {
        return jsonData.getInt("instructionCount");
    }

    public DisassembleArguments setInstructionCount(int instructionCount) {
        jsonData.put("instructionCount", instructionCount);
        return this;
    }

    /**
     * If true, the adapter should attempt to resolve memory addresses and other values to symbolic
     * names.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getResolveSymbols() {
        return jsonData.has("resolveSymbols") ? jsonData.getBoolean("resolveSymbols") : null;
    }

    public DisassembleArguments setResolveSymbols(Boolean resolveSymbols) {
        jsonData.putOpt("resolveSymbols", resolveSymbols);
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        DisassembleArguments other = (DisassembleArguments) obj;
        if (!Objects.equals(this.getMemoryReference(), other.getMemoryReference())) {
            return false;
        }
        if (!Objects.equals(this.getOffset(), other.getOffset())) {
            return false;
        }
        if (!Objects.equals(this.getInstructionOffset(), other.getInstructionOffset())) {
            return false;
        }
        if (this.getInstructionCount() != other.getInstructionCount()) {
            return false;
        }
        if (!Objects.equals(this.getResolveSymbols(), other.getResolveSymbols())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 23 * hash + Objects.hashCode(this.getMemoryReference());
        if (this.getOffset() != null) {
            hash = 23 * hash + Integer.hashCode(this.getOffset());
        }
        if (this.getInstructionOffset() != null) {
            hash = 23 * hash + Integer.hashCode(this.getInstructionOffset());
        }
        hash = 23 * hash + Integer.hashCode(this.getInstructionCount());
        if (this.getResolveSymbols() != null) {
            hash = 23 * hash + Boolean.hashCode(this.getResolveSymbols());
        }
        return hash;
    }

    public static DisassembleArguments create(String memoryReference, Integer instructionCount) {
        final JSONObject json = new JSONObject();
        json.put("memoryReference", memoryReference);
        json.put("instructionCount", instructionCount);
        return new DisassembleArguments(json);
    }
}
