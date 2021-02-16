/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.sl.nodes.interop;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A container class used to store per-node attributes used by the instrumentation framework.
 */
public abstract class NodeObjectDescriptor implements TruffleObject {

    private final String name;

    private NodeObjectDescriptor(String name) {
        assert name != null;
        this.name = name;
    }

    public static NodeObjectDescriptor readVariable(String name) {
        return new ReadDescriptor(name);
    }

    public static NodeObjectDescriptor writeVariable(String name, SourceSection sourceSection) {
        return new WriteDescriptor(name, sourceSection);
    }

    Object readMember(String member, @Cached BranchProfile error) throws UnknownIdentifierException {
        if (isMemberReadable(member)) {
            return name;
        } else {
            error.enter();
            throw UnknownIdentifierException.create(member);
        }
    }

    abstract boolean isMemberReadable(String member);

    @ExportLibrary(InteropLibrary.class)
    static final class ReadDescriptor extends NodeObjectDescriptor {

        private static final TruffleObject KEYS_READ = new NodeObjectDescriptorKeys(StandardTags.ReadVariableTag.NAME);

        ReadDescriptor(String name) {
            super(name);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasMembers() {
            return true;
        }

        @Override
        @ExportMessage
        boolean isMemberReadable(String member) {
            return StandardTags.ReadVariableTag.NAME.equals(member);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return KEYS_READ;
        }

        @Override
        @ExportMessage
        Object readMember(String member, @Cached BranchProfile error) throws UnknownIdentifierException {
            return super.readMember(member, error);
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class WriteDescriptor extends NodeObjectDescriptor {

        private static final TruffleObject KEYS_WRITE = new NodeObjectDescriptorKeys(StandardTags.WriteVariableTag.NAME);

        private final Object nameSymbol;

        WriteDescriptor(String name, SourceSection sourceSection) {
            super(name);
            this.nameSymbol = new NameSymbol(name, sourceSection);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasMembers() {
            return true;
        }

        @Override
        @ExportMessage
        boolean isMemberReadable(String member) {
            return StandardTags.WriteVariableTag.NAME.equals(member);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return KEYS_WRITE;
        }

        @Override
        @ExportMessage
        Object readMember(String member, @Cached BranchProfile error) throws UnknownIdentifierException {
            super.readMember(member, error); // To verify readability
            return nameSymbol;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class NameSymbol implements TruffleObject {

        private final String name;
        private final SourceSection sourceSection;

        NameSymbol(String name, SourceSection sourceSection) {
            this.name = name;
            this.sourceSection = sourceSection;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isString() {
            return true;
        }

        @ExportMessage
        String asString() {
            return name;
        }

        @ExportMessage
        boolean hasSourceLocation() {
            return sourceSection != null;
        }

        @ExportMessage
        SourceSection getSourceLocation() throws UnsupportedMessageException {
            if (sourceSection != null) {
                return sourceSection;
            } else {
                throw UnsupportedMessageException.create();
            }
        }
    }
}
