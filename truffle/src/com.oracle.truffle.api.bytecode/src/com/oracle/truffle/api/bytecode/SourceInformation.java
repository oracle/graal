/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode;

import com.oracle.truffle.api.source.SourceSection;

/**
 * Introspection class modeling source section information for a range of bytecodes.
 * <p>
 * Note: Introspection classes are intended to be used for debugging purposes only. These APIs may
 * change in the future.
 *
 * @see BytecodeNode#getSourceInformation()
 * @see BytecodeLocation#getSourceInformation()
 */
public abstract class SourceInformation {

    /**
     * Internal constructor for generated code. Do not use.
     *
     * @since 24.2
     */
    protected SourceInformation(Object token) {
        BytecodeRootNodes.checkToken(token);
    }

    /**
     * Returns the start bytecode index for this source information object (inclusive).
     *
     * @since 24.2
     */
    public abstract int getStartBytecodeIndex();

    /**
     * Returns the end bytecode index for this source information object (exclusive).
     *
     * @since 24.2
     */
    public abstract int getEndBytecodeIndex();

    /**
     * Returns the source section associated with this source information object.
     * <p>
     * The result is never <code>null</code>, with the possible exception of the root of a
     * {@link SourceInformationTree} (see {@link BytecodeNode#getSourceInformationTree}).
     *
     * @since 24.2
     */
    public abstract SourceSection getSourceSection();

    /**
     * {@inheritDoc}
     *
     * @since 24.2
     */
    @Override
    public String toString() {
        SourceSection sourceSection = getSourceSection();
        String sourceText;
        if (sourceSection == null) {
            sourceText = "<none>";
        } else {
            sourceText = formatSourceSection(sourceSection, 60);
        }
        return String.format("[%04x .. %04x] %s", getStartBytecodeIndex(), getEndBytecodeIndex(), sourceText);
    }

    static final String formatSourceSection(SourceSection section, int maxCharacters) {
        String characters;
        if (section.getSource().hasCharacters()) {
            characters = limitCharacters(section.getCharacters(), maxCharacters).toString();
            characters = characters.replace("\n", "\\n");
        } else {
            characters = "";
        }
        return String.format("%s %3s:%-3s-%3s:%-3s   %s",
                        limitCharacters(section.getSource().getName(), 40),
                        section.getStartLine(),
                        section.getStartColumn(),
                        section.getEndLine(),
                        section.getEndColumn(),
                        characters);
    }

    private static CharSequence limitCharacters(CharSequence characters, int maxCharacters) {
        if (characters.length() > maxCharacters) {
            return characters.subSequence(0, maxCharacters - 3).toString() + "...";
        }
        return characters;
    }

}
