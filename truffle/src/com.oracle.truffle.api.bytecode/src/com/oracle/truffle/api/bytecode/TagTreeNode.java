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

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.library.DynamicDispatchLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Class that implements tag tree {@link NodeLibrary} dispatch for tag tree items. This class is
 * only intended to be used when implementing a custom {@link GenerateBytecode#tagTreeNodeLibrary()
 * tag tree node library}.
 *
 * <p>
 * All implementations of this class are intended to be generated and are not supposed to be
 * implemented manually.
 *
 * @since 24.2
 */
@ExportLibrary(DynamicDispatchLibrary.class)
public abstract class TagTreeNode extends Node implements TagTree {

    /**
     * Internal constructor for generated code. Do not use.
     *
     * @since 24.2
     */
    protected TagTreeNode(Object token) {
        BytecodeRootNodes.checkToken(token);
    }

    /**
     * Allows to access the language instance associated with this node.
     *
     * @since 24.2
     */
    protected abstract Class<? extends TruffleLanguage<?>> getLanguage();

    /**
     * Returns the currently used {@link NodeLibrary} exports for this tag library.
     *
     * @since 24.2
     */
    @ExportMessage
    protected Class<?> dispatch() {
        return TagTreeNodeExports.class;
    }

    /**
     * Returns the bytecode node associated with this node.
     *
     * @since 24.2
     */
    public BytecodeNode getBytecodeNode() {
        return BytecodeNode.get(this);
    }

    /**
     * Creates a default scope implementation based of this tag tree node. The scope returned
     * represents the default scope implementation in use without any configuration in
     * {@link GenerateBytecode}. The scope respects your interpreter's choice of
     * {@link GenerateBytecode#enableBlockScoping() block/root scoping}. Use this method if the
     * default scope implementation is usable for your language but other methods in
     * {@link NodeLibrary} need to be implemented differently.
     * <p>
     * The scope used for a tag tree node can be customized by setting
     * {@link GenerateBytecode#tagTreeNodeLibrary()} and overriding
     * {@link NodeLibrary#getScope(Object, Frame, boolean)}.
     *
     * @since 24.2
     */
    public final Object createDefaultScope(Frame frame, boolean nodeEnter) {
        return new DefaultBytecodeScope(this, frame, nodeEnter);
    }

    /**
     * {@inheritDoc}
     *
     * @since 24.2
     */
    @Override
    public final String toString() {
        StringBuilder b = new StringBuilder();
        b.append(format(this));

        // source section is only accessible if adopted
        if (getParent() != null) {
            SourceSection section = getSourceSection();
            if (section != null) {
                b.append(" ");
                b.append(SourceInformation.formatSourceSection(section, 60));
            }
        }
        return b.toString();
    }

    static String format(TagTreeNode b) {
        return String.format("(%04x .. %04x %s)", b.getEnterBytecodeIndex(), b.getReturnBytecodeIndex(), b.getTagsString());
    }

    final String getTagsString() {
        StringBuilder b = new StringBuilder();
        String sep = "";
        for (Class<? extends Tag> tag : getTags()) {
            String tagId = Tag.getIdentifier(tag);
            if (tagId == null) {
                tagId = tag.getSimpleName();
            }
            b.append(sep);
            b.append(tagId);
            sep = ",";
        }
        return b.toString();
    }

}
