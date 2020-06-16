/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.listeners;

import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDString;
import com.oracle.truffle.llvm.parser.metadata.MDSubprogram;
import com.oracle.truffle.llvm.parser.model.IRScope;
import com.oracle.truffle.llvm.parser.scanner.RecordBuffer;

/**
 * This class is used to find an MDSubprogram that has been attached to a LLVM function.
 */

public class MetadataSubprogramOnly extends Metadata {

    MetadataSubprogramOnly(Types types, IRScope scope) {
        super(types, scope);
    }

    /**
     * Parses the given opCode while looking for an MDSubprogam.
     *
     * @throws MDSubprogramParsedException if the MDSubprogram is found.
     */
    @Override
    protected void parseOpcode(RecordBuffer buffer, long[] args, int opCode) {
        super.parseOpcode(buffer, args, opCode);
        if (opCode == METADATA_SUBPROGRAM) {
            MDBaseNode mdBaseNode = metadata.getOrNull(metadata.size() - 1);
            if (mdBaseNode instanceof MDSubprogram) {
                MDSubprogram mdSubprogram = (MDSubprogram) mdBaseNode;
                String linkageName = MDString.getIfInstance(mdSubprogram.getLinkageName());
                String displayName = MDString.getIfInstance(mdSubprogram.getName());
                scope.exitLocalScope();
                throw new MDSubprogramParsedException(linkageName, displayName);
            }
        }
    }

    public static class MDSubprogramParsedException extends ControlFlowException {

        private static final long serialVersionUID = 1L;
        public final String linkageName;
        public final String displayName;

        public MDSubprogramParsedException(String linkageName, String displayName) {
            this.linkageName = linkageName;
            this.displayName = displayName;
        }
    }

}
