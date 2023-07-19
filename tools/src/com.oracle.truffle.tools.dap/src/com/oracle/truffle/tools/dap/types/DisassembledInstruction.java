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

import org.graalvm.shadowed.org.json.JSONObject;

import java.util.Objects;

/**
 * Represents a single disassembled instruction.
 */
public class DisassembledInstruction extends JSONBase {

    DisassembledInstruction(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The address of the instruction. Treated as a hex value if prefixed with '0x', or as a decimal
     * value otherwise.
     */
    public String getAddress() {
        return jsonData.getString("address");
    }

    public DisassembledInstruction setAddress(String address) {
        jsonData.put("address", address);
        return this;
    }

    /**
     * Optional raw bytes representing the instruction and its operands, in an
     * implementation-defined format.
     */
    public String getInstructionBytes() {
        return jsonData.optString("instructionBytes", null);
    }

    public DisassembledInstruction setInstructionBytes(String instructionBytes) {
        jsonData.putOpt("instructionBytes", instructionBytes);
        return this;
    }

    /**
     * Text representing the instruction and its operands, in an implementation-defined format.
     */
    public String getInstruction() {
        return jsonData.getString("instruction");
    }

    public DisassembledInstruction setInstruction(String instruction) {
        jsonData.put("instruction", instruction);
        return this;
    }

    /**
     * Name of the symbol that corresponds with the location of this instruction, if any.
     */
    public String getSymbol() {
        return jsonData.optString("symbol", null);
    }

    public DisassembledInstruction setSymbol(String symbol) {
        jsonData.putOpt("symbol", symbol);
        return this;
    }

    /**
     * Source location that corresponds to this instruction, if any. Should always be set (if
     * available) on the first instruction returned, but can be omitted afterwards if this
     * instruction maps to the same source file as the previous instruction.
     */
    public Source getLocation() {
        return jsonData.has("location") ? new Source(jsonData.optJSONObject("location")) : null;
    }

    public DisassembledInstruction setLocation(Source location) {
        jsonData.putOpt("location", location != null ? location.jsonData : null);
        return this;
    }

    /**
     * The line within the source location that corresponds to this instruction, if any.
     */
    public Integer getLine() {
        return jsonData.has("line") ? jsonData.getInt("line") : null;
    }

    public DisassembledInstruction setLine(Integer line) {
        jsonData.putOpt("line", line);
        return this;
    }

    /**
     * The column within the line that corresponds to this instruction, if any.
     */
    public Integer getColumn() {
        return jsonData.has("column") ? jsonData.getInt("column") : null;
    }

    public DisassembledInstruction setColumn(Integer column) {
        jsonData.putOpt("column", column);
        return this;
    }

    /**
     * The end line of the range that corresponds to this instruction, if any.
     */
    public Integer getEndLine() {
        return jsonData.has("endLine") ? jsonData.getInt("endLine") : null;
    }

    public DisassembledInstruction setEndLine(Integer endLine) {
        jsonData.putOpt("endLine", endLine);
        return this;
    }

    /**
     * The end column of the range that corresponds to this instruction, if any.
     */
    public Integer getEndColumn() {
        return jsonData.has("endColumn") ? jsonData.getInt("endColumn") : null;
    }

    public DisassembledInstruction setEndColumn(Integer endColumn) {
        jsonData.putOpt("endColumn", endColumn);
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
        DisassembledInstruction other = (DisassembledInstruction) obj;
        if (!Objects.equals(this.getAddress(), other.getAddress())) {
            return false;
        }
        if (!Objects.equals(this.getInstructionBytes(), other.getInstructionBytes())) {
            return false;
        }
        if (!Objects.equals(this.getInstruction(), other.getInstruction())) {
            return false;
        }
        if (!Objects.equals(this.getSymbol(), other.getSymbol())) {
            return false;
        }
        if (!Objects.equals(this.getLocation(), other.getLocation())) {
            return false;
        }
        if (!Objects.equals(this.getLine(), other.getLine())) {
            return false;
        }
        if (!Objects.equals(this.getColumn(), other.getColumn())) {
            return false;
        }
        if (!Objects.equals(this.getEndLine(), other.getEndLine())) {
            return false;
        }
        if (!Objects.equals(this.getEndColumn(), other.getEndColumn())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 83 * hash + Objects.hashCode(this.getAddress());
        if (this.getInstructionBytes() != null) {
            hash = 83 * hash + Objects.hashCode(this.getInstructionBytes());
        }
        hash = 83 * hash + Objects.hashCode(this.getInstruction());
        if (this.getSymbol() != null) {
            hash = 83 * hash + Objects.hashCode(this.getSymbol());
        }
        if (this.getLocation() != null) {
            hash = 83 * hash + Objects.hashCode(this.getLocation());
        }
        if (this.getLine() != null) {
            hash = 83 * hash + Integer.hashCode(this.getLine());
        }
        if (this.getColumn() != null) {
            hash = 83 * hash + Integer.hashCode(this.getColumn());
        }
        if (this.getEndLine() != null) {
            hash = 83 * hash + Integer.hashCode(this.getEndLine());
        }
        if (this.getEndColumn() != null) {
            hash = 83 * hash + Integer.hashCode(this.getEndColumn());
        }
        return hash;
    }

    public static DisassembledInstruction create(String address, String instruction) {
        final JSONObject json = new JSONObject();
        json.put("address", address);
        json.put("instruction", instruction);
        return new DisassembledInstruction(json);
    }
}
