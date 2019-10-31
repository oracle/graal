/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.local;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.sl.nodes.SLEvalRootNode;
import com.oracle.truffle.sl.nodes.SLRootNode;
import com.oracle.truffle.sl.nodes.SLStatementNode;
import com.oracle.truffle.sl.nodes.controlflow.SLBlockNode;
import com.oracle.truffle.sl.runtime.SLNull;

/**
 * Simple language lexical scope. There can be a block scope, or function scope.
 */
public final class SLLexicalScope {

    private final Node current;
    private final SLBlockNode block;
    private final SLBlockNode parentBlock;
    private final RootNode root;
    private SLLexicalScope parent;
    private Map<String, FrameSlot> varSlots;

    /**
     * Create a new block SL lexical scope.
     *
     * @param current the current node
     * @param block a nearest block enclosing the current node
     * @param parentBlock a next parent block
     */
    private SLLexicalScope(Node current, SLBlockNode block, SLBlockNode parentBlock) {
        this.current = current;
        this.block = block;
        this.parentBlock = parentBlock;
        this.root = null;
    }

    /**
     * Create a new functional SL lexical scope.
     *
     * @param current the current node, or <code>null</code> when it would be above the block
     * @param block a nearest block enclosing the current node
     * @param root a functional root node for top-most block
     */
    private SLLexicalScope(Node current, SLBlockNode block, RootNode root) {
        this.current = current;
        this.block = block;
        this.parentBlock = null;
        this.root = root;
    }

    @SuppressWarnings("all") // The parameter node should not be assigned
    public static SLLexicalScope createScope(Node node) {
        SLBlockNode block = getParentBlock(node);
        if (block == null) {
            // We're in the root.
            block = findChildrenBlock(node);
            if (block == null) {
                // Corrupted SL AST, no block was found
                RootNode root = node.getRootNode();
                assert root instanceof SLEvalRootNode || root instanceof SLRootNode : "Corrupted SL AST under " + node;
                return new SLLexicalScope(null, null, (SLBlockNode) null);
            }
            node = null; // node is above the block
        }
        // Test if there is a parent block. If not, we're in the root scope.
        SLBlockNode parentBlock = getParentBlock(block);
        if (parentBlock == null) {
            return new SLLexicalScope(node, block, block.getRootNode());
        } else {
            return new SLLexicalScope(node, block, parentBlock);
        }
    }

    private static SLBlockNode getParentBlock(Node node) {
        SLBlockNode block;
        Node parent = node.getParent();
        // Find a nearest block node.
        while (parent != null && !(parent instanceof SLBlockNode)) {
            parent = parent.getParent();
        }
        if (parent != null) {
            block = (SLBlockNode) parent;
        } else {
            block = null;
        }
        return block;
    }

    private static SLBlockNode findChildrenBlock(Node node) {
        SLBlockNode[] blockPtr = new SLBlockNode[1];
        node.accept(new NodeVisitor() {
            @Override
            public boolean visit(Node n) {
                if (n instanceof SLBlockNode) {
                    blockPtr[0] = (SLBlockNode) n;
                    return false;
                } else {
                    return true;
                }
            }
        });
        return blockPtr[0];
    }

    public SLLexicalScope findParent() {
        if (parentBlock == null) {
            // This was a root scope.
            return null;
        }
        if (parent == null) {
            Node node = block;
            SLBlockNode newBlock = parentBlock;
            // Test if there is a next parent block. If not, we're in the root scope.
            SLBlockNode newParentBlock = getParentBlock(newBlock);
            if (newParentBlock == null) {
                parent = new SLLexicalScope(node, newBlock, newBlock.getRootNode());
            } else {
                parent = new SLLexicalScope(node, newBlock, newParentBlock);
            }
        }
        return parent;
    }

    /**
     * @return the function name for function scope, "block" otherwise.
     */
    public String getName() {
        if (root != null) {
            return root.getName();
        } else {
            return "block";
        }
    }

    /**
     * @return the node representing the scope, the block node for block scopes and the
     *         {@link RootNode} for functional scope.
     */
    public Node getNode() {
        if (root != null) {
            return root;
        } else {
            return block;
        }
    }

    public Object getVariables(Frame frame) {
        Map<String, FrameSlot> vars = getVars();
        Object[] args = null;
        // Use arguments when the current node is above the block
        if (current == null) {
            args = (frame != null) ? frame.getArguments() : null;
        }
        return new VariablesMapObject(vars, args, frame);
    }

    public Object getArguments(Frame frame) {
        if (root == null) {
            // No arguments for block scope
            return null;
        }
        // The slots give us names of the arguments:
        Map<String, FrameSlot> argSlots = collectArgs(block);
        // The frame's arguments array give us the argument values:
        Object[] args = (frame != null) ? frame.getArguments() : null;
        // Create a TruffleObject having the arguments as properties:
        return new VariablesMapObject(argSlots, args, frame);
    }

    private Map<String, FrameSlot> getVars() {
        if (varSlots == null) {
            if (current != null) {
                varSlots = collectVars(block, current);
            } else if (block != null) {
                // Provide the arguments only when the current node is above the block
                varSlots = collectArgs(block);
            } else {
                varSlots = Collections.emptyMap();
            }
        }
        return varSlots;
    }

    private boolean hasParentVar(String name) {
        SLLexicalScope p = this;
        while ((p = p.findParent()) != null) {
            if (p.getVars().containsKey(name)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, FrameSlot> collectVars(Node varsBlock, Node currentNode) {
        // Variables are slot-based.
        // To collect declared variables, traverse the block's AST and find slots associated
        // with SLWriteLocalVariableNode. The traversal stops when we hit the current node.
        Map<String, FrameSlot> slots = new LinkedHashMap<>(4);
        NodeUtil.forEachChild(varsBlock, new NodeVisitor() {
            @Override
            public boolean visit(Node node) {
                if (node == currentNode) {
                    return false;
                }
                // Do not enter any nested blocks.
                if (!(node instanceof SLBlockNode)) {
                    boolean all = NodeUtil.forEachChild(node, this);
                    if (!all) {
                        return false;
                    }
                }
                // Write to a variable is a declaration unless it exists already in a parent scope.
                if (node instanceof SLWriteLocalVariableNode) {
                    SLWriteLocalVariableNode wn = (SLWriteLocalVariableNode) node;
                    String name = Objects.toString(wn.getSlot().getIdentifier());
                    if (!hasParentVar(name)) {
                        slots.put(name, wn.getSlot());
                    }
                }
                return true;
            }
        });
        return slots;
    }

    private static Map<String, FrameSlot> collectArgs(Node block) {
        // Arguments are pushed to frame slots at the beginning of the function block.
        // To collect argument slots, search for SLReadArgumentNode inside of
        // SLWriteLocalVariableNode.
        Map<String, FrameSlot> args = new LinkedHashMap<>(4);
        NodeUtil.forEachChild(block, new NodeVisitor() {

            private SLWriteLocalVariableNode wn; // The current write node containing a slot

            @Override
            public boolean visit(Node node) {
                // When there is a write node, search for SLReadArgumentNode among its children:
                if (node instanceof SLWriteLocalVariableNode) {
                    wn = (SLWriteLocalVariableNode) node;
                    boolean all = NodeUtil.forEachChild(node, this);
                    wn = null;
                    return all;
                } else if (wn != null && (node instanceof SLReadArgumentNode)) {
                    FrameSlot slot = wn.getSlot();
                    String name = Objects.toString(slot.getIdentifier());
                    assert !args.containsKey(name) : name + " argument exists already.";
                    args.put(name, slot);
                    return true;
                } else if (wn == null && (node instanceof SLStatementNode)) {
                    // A different SL node - we're done.
                    return false;
                } else {
                    return NodeUtil.forEachChild(node, this);
                }
            }
        });
        return args;
    }

    @ExportLibrary(InteropLibrary.class)
    static final class VariablesMapObject implements TruffleObject {

        final Map<String, ? extends FrameSlot> slots;
        final Object[] args;
        final Frame frame;

        private VariablesMapObject(Map<String, ? extends FrameSlot> slots, Object[] args, Frame frame) {
            this.slots = slots;
            this.args = args;
            this.frame = frame;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new KeysArray(slots.keySet().toArray(new String[0]));
        }

        @ExportMessage
        @TruffleBoundary
        void writeMember(String member, Object value) throws UnsupportedMessageException, UnknownIdentifierException {
            if (frame == null) {
                throw UnsupportedMessageException.create();
            }
            FrameSlot slot = slots.get(member);
            if (slot == null) {
                throw UnknownIdentifierException.create(member);
            } else {
                Object info = slot.getInfo();
                if (args != null && info != null) {
                    args[(Integer) info] = value;
                } else {
                    frame.setObject(slot, value);
                }
            }
        }

        @ExportMessage
        @TruffleBoundary
        Object readMember(String member) throws UnknownIdentifierException {
            if (frame == null) {
                return SLNull.SINGLETON;
            }
            FrameSlot slot = slots.get(member);
            if (slot == null) {
                throw UnknownIdentifierException.create(member);
            } else {
                Object value;
                Object info = slot.getInfo();
                if (args != null && info != null) {
                    value = args[(Integer) info];
                } else {
                    value = frame.getValue(slot);
                }
                return value;
            }
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
            return false;
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberModifiable(String member) {
            return slots.containsKey(member);
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberReadable(String member) {
            return frame == null || slots.containsKey(member);
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class KeysArray implements TruffleObject {

        private final String[] keys;

        KeysArray(String[] keys) {
            this.keys = keys;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < keys.length;
        }

        @ExportMessage
        long getArraySize() {
            return keys.length;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return keys[(int) index];
        }

    }

}
