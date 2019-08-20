/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.macho;

public final class MachODylibCommand extends MachOLoadCommand {

    private final String name;
    private final int timestamp;
    private final int currentVersion;
    private final int compatibilityVersion;

    private MachODylibCommand(int cmd, int cmdSize, String name, int timestamp, int currentVersion, int compatibilityVersion) {
        super(cmd, cmdSize);
        this.name = name;
        this.timestamp = timestamp;
        this.currentVersion = currentVersion;
        this.compatibilityVersion = compatibilityVersion;
    }

    public String getName() {
        return name;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public int getCompatibilityVersion() {
        return compatibilityVersion;
    }

    public static MachODylibCommand create(MachOReader buffer) {
        int pos = buffer.getPosition();

        int cmd = buffer.getInt();
        assert cmd == MachOLoadCommand.LC_LOAD_DYLIB;
        int cmdSize = buffer.getInt();
        int offset = buffer.getInt();
        int timestamp = buffer.getInt();
        int currentVersion = buffer.getInt();
        int compatibilityVersion = buffer.getInt();

        buffer.setPosition(pos + offset);
        String name = getString(buffer, cmdSize - offset);
        buffer.setPosition(pos + cmdSize);

        return new MachODylibCommand(cmd, cmdSize, name, timestamp, currentVersion, compatibilityVersion);
    }
}
