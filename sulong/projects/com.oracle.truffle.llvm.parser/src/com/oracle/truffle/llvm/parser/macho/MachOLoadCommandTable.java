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

import java.util.ArrayList;

public final class MachOLoadCommandTable {

    private final MachOLoadCommand[] loadCommands;
    private final MachOSegmentCommand[] segmentCommands;

    private MachOLoadCommandTable(MachOLoadCommand[] loadCommands, MachOSegmentCommand[] segmentCommands) {
        this.loadCommands = loadCommands;
        this.segmentCommands = segmentCommands;
    }

    public MachOSegmentCommand getSegment(String name) {
        for (int i = 0; i < segmentCommands.length; i++) {
            if (segmentCommands[i].getSegName().equals(name)) {
                return segmentCommands[i];
            }
        }
        return null;
    }

    public MachOLoadCommand[] getLoadCommands() {
        return loadCommands;
    }

    public static MachOLoadCommandTable create(MachOHeader header, MachOReader reader) {
        MachOLoadCommand[] loadCommands = new MachOLoadCommand[header.getNCmds()];
        ArrayList<MachOSegmentCommand> segmentCommands = new ArrayList<>();

        for (int i = 0; i < header.getNCmds(); i++) {
            loadCommands[i] = parseLoadCommand(reader);
            if (loadCommands[i] instanceof MachOSegmentCommand) {
                segmentCommands.add((MachOSegmentCommand) loadCommands[i]);
            }
        }

        return new MachOLoadCommandTable(loadCommands, segmentCommands.toArray(new MachOSegmentCommand[segmentCommands.size()]));
    }

    private static MachOLoadCommand parseLoadCommand(MachOReader buffer) {
        MachOLoadCommand cmd = null;

        int cmdID = buffer.getInt(buffer.getPosition());

        switch (cmdID) {
            case MachOLoadCommand.LC_SEGMENT:
            case MachOLoadCommand.LC_SEGMENT_64:
                cmd = MachOSegmentCommand.create(buffer);
                break;
            case MachOLoadCommand.LC_LOAD_DYLIB:
                cmd = MachODylibCommand.create(buffer);
                break;
            case MachOLoadCommand.LC_RPATH:
                cmd = MachORPathCommand.create(buffer);
                break;
            default:
                int cmdSize = buffer.getInt(buffer.getPosition() + MachOLoadCommand.CMD_ID_SIZE);
                cmd = new MachOLoadCommand(cmdID, cmdSize);
                buffer.setPosition(buffer.getPosition() + cmdSize);
        }

        return cmd;
    }

}
