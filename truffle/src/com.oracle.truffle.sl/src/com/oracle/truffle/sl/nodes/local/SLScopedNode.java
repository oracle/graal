/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.nodes.SLExpressionNode;
import com.oracle.truffle.sl.nodes.SLRootNode;
import com.oracle.truffle.sl.nodes.controlflow.SLBlockNode;
import com.oracle.truffle.sl.runtime.SLContext;
import com.oracle.truffle.sl.runtime.SLNull;
import com.oracle.truffle.sl.runtime.SLStrings;

/**
 * The SL implementation of {@link NodeLibrary} provides fast access to local variables. It's used
 * by tools like debugger, profiler, tracer, etc. To provide good performance, we cache write nodes
 * that declare variables and use them in the interop contract.
 */
@ExportLibrary(value = NodeLibrary.class)
public abstract class SLScopedNode extends Node {

    /**
     * Index to the the {@link SLBlockNode#getDeclaredLocalVariables() block's variables} that
     * determine variables belonging into this scope (excluding parent scopes) on node enter. The
     * scope variables are in the interval &lt;0, visibleVariablesIndexOnEnter).
     */
    @CompilationFinal private volatile int visibleVariablesIndexOnEnter = -1;
    /**
     * Similar to {@link #visibleVariablesIndexOnEnter}, but determines variables on node exit. The
     * scope variables are in the interval &lt;0, visibleVariablesIndexOnExit).
     */
    @CompilationFinal private volatile int visibleVariablesIndexOnExit = -1;

    /**
     * For performance reasons, fix the library implementation for the particular node.
     */
    @ExportMessage
    boolean accepts(@Shared("node") @Cached(value = "this", adopt = false) SLScopedNode cachedNode) {
        return this == cachedNode;
    }

    /**
     * We do provide a scope.
     */
    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean hasScope(@SuppressWarnings("unused") Frame frame) {
        return true;
    }

    /**
     * The scope depends on the current node and the node's block. Cache the node and its block for
     * fast access. Depending on the block node, we create either block variables, or function
     * arguments (in the RootNode, but outside of a block).
     */
    @ExportMessage
    @SuppressWarnings("static-method")
    final Object getScope(Frame frame, boolean nodeEnter, @Shared("node") @Cached(value = "this", adopt = false) SLScopedNode cachedNode,
                    @Cached(value = "this.findBlock()", adopt = false, allowUncached = true) Node blockNode) {
        if (blockNode instanceof SLBlockNode) {
            return new VariablesObject(frame, cachedNode, nodeEnter, (SLBlockNode) blockNode);
        } else {
            return new ArgumentsObject(frame, (SLRootNode) blockNode);
        }
    }

    /**
     * Test if a function of that name exists. The functions are context-dependent, therefore do a
     * context lookup via {@link SLContext#getCurrent(Node)}.
     */
    @ExportMessage
    final boolean hasRootInstance(@SuppressWarnings("unused") Frame frame) {
        return hasRootInstanceSlowPath();
    }

    @TruffleBoundary
    private boolean hasRootInstanceSlowPath() {
        // The instance of the current RootNode is a function of the same name.
        return SLContext.get(this).getFunctionRegistry().getFunction(SLStrings.getSLRootName(getRootNode())) != null;
    }

    /**
     * Provide function instance of that name. The function is context-dependent, therefore do a
     * context lookup via {@link SLContext#getCurrent(Node)}.
     */
    @ExportMessage
    final Object getRootInstance(@SuppressWarnings("unused") Frame frame) throws UnsupportedMessageException {
        return getRootInstanceSlowPath();
    }

    @TruffleBoundary
    private Object getRootInstanceSlowPath() throws UnsupportedMessageException {
        // The instance of the current RootNode is a function of the same name.
        Object function = SLContext.get(this).getFunctionRegistry().getFunction(SLStrings.getSLRootName(getRootNode()));
        if (function != null) {
            return function;
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    /**
     * Find block of this node. Traverse the parent chain and find the first {@link SLBlockNode}. If
     * none is found, {@link RootNode} is returned.
     *
     * @return the block node, always non-null. Either SLBlockNode, or SLRootNode.
     */
    @NeverDefault
    public final Node findBlock() {
        Node parent = getParent();
        while (parent != null) {
            if (parent instanceof SLBlockNode) {
                break;
            }
            Node p = parent.getParent();
            if (p == null) {
                assert parent instanceof RootNode : String.format("Not adopted node under %s.", parent);
                return parent;
            }
            parent = p;
        }
        return parent;
    }

    /**
     * Set the index to the the {@link SLBlockNode#getDeclaredLocalVariables() block's variables}
     * that determine variables belonging into this scope (excluding parent scopes) on node enter.
     */
    public final void setVisibleVariablesIndexOnEnter(int index) {
        assert visibleVariablesIndexOnEnter == -1 : "The index is set just once";
        assert 0 <= index;
        visibleVariablesIndexOnEnter = index;
    }

    /**
     * Similar to {@link #setVisibleVariablesIndexOnEnter(int)}, but determines variables on node
     * exit.
     */
    public final void setVisibleVariablesIndexOnExit(int index) {
        assert visibleVariablesIndexOnExit == -1 : "The index is set just once";
        assert 0 <= index;
        visibleVariablesIndexOnExit = index;
    }

    /**
     * Provide the index that determines variables on node enter.
     */
    protected final int getVisibleVariablesIndexOnEnter() {
        return visibleVariablesIndexOnEnter;
    }

    /**
     * Scope of function arguments. This scope is provided by nodes just under a {@link SLRootNode},
     * outside of a {@link SLBlockNode block}.
     * <p>
     * The arguments declared by {@link SLRootNode#getDeclaredArguments() root node} are provided as
     * scope members.
     */
    @ExportLibrary(InteropLibrary.class)
    static final class ArgumentsObject implements TruffleObject {

        /**
         * The member caching limit.
         */
        static int LIMIT = 3;

        private final Frame frame;
        protected final SLRootNode root;

        /**
         * The arguments depend on the current frame and root node.
         */
        ArgumentsObject(Frame frame, SLRootNode root) {
            this.frame = frame;
            this.root = root;
        }

        /**
         * For performance reasons, fix the library implementation for the particular root node.
         */
        @ExportMessage
        boolean accepts(@Cached(value = "this.root", adopt = false) SLRootNode cachedRoot) {
            return this.root == cachedRoot;
        }

        /**
         * This is a scope object, providing arguments as members.
         */
        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isScope() {
            return true;
        }

        /**
         * The scope must provide an associated language.
         */
        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return SLLanguage.class;
        }

        /**
         * Provide the function name as the scope's display string.
         */
        @ExportMessage
        Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return root.getTSName();
        }

        /**
         * We provide a source section of this scope.
         */
        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasSourceLocation() {
            return true;
        }

        /**
         * @return Source section of the function.
         */
        @ExportMessage
        SourceSection getSourceLocation() {
            return root.getSourceSection();
        }

        /**
         * Scope must provide scope members.
         */
        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasMembers() {
            return true;
        }

        /**
         * We return an array of argument objects as scope members.
         */
        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            SLWriteLocalVariableNode[] writeNodes = root.getDeclaredArguments();
            return new KeysArray(writeNodes, writeNodes.length, writeNodes.length);
        }

        /**
         * Test if a member exists. We cache the result for fast access.
         */
        @ExportMessage(name = "isMemberReadable")
        static final class ExistsMember {

            /**
             * If the member is cached, provide the cached result. Call
             * {@link #doGeneric(ArgumentsObject, String)} otherwise.
             */
            @Specialization(limit = "LIMIT", guards = {"cachedMember.equals(member)"})
            @SuppressWarnings("unused")
            static boolean doCached(ArgumentsObject receiver, String member,
                            @Exclusive @Cached("member") String cachedMember,
                            // We cache the member existence for fast-path access
                            @Cached("doGeneric(receiver, member)") boolean cachedResult) {
                assert cachedResult == doGeneric(receiver, member);
                return cachedResult;
            }

            /**
             * Test if an argument with that name exists.
             */
            @Specialization(replaces = "doCached")
            @TruffleBoundary
            static boolean doGeneric(ArgumentsObject receiver, String member) {
                return receiver.hasArgumentIndex(member);
            }
        }

        /**
         * Test if a member is modifiable. We cache the result for fast access.
         */
        @ExportMessage(name = "isMemberModifiable")
        static final class ModifiableMember {

            @Specialization(limit = "LIMIT", guards = {"cachedMember.equals(member)"})
            @SuppressWarnings("unused")
            static boolean doCached(ArgumentsObject receiver, String member,
                            @Exclusive @Cached("member") String cachedMember,
                            // We cache the member existence for fast-path access
                            @Cached("receiver.hasArgumentIndex(member)") boolean cachedResult) {
                return cachedResult && receiver.frame != null;
            }

            /**
             * Test if an argument can be modified (it must exist and we must have a frame).
             */
            @Specialization(replaces = "doCached")
            @TruffleBoundary
            static boolean doGeneric(ArgumentsObject receiver, String member) {
                return receiver.findArgumentIndex(member) >= 0 && receiver.frame != null;
            }
        }

        /**
         * We can not insert new arguments.
         */
        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
            return false;
        }

        /**
         * Read an argument value. Cache the argument's index for fast access.
         */
        @ExportMessage(name = "readMember")
        static final class ReadMember {

            /**
             * If the member is cached, use the cached index and read the value at that index. Call
             * {@link #doGeneric(ArgumentsObject, String)} otherwise.
             */
            @Specialization(limit = "LIMIT", guards = {"cachedMember.equals(member)"})
            @SuppressWarnings("unused")
            static Object doCached(ArgumentsObject receiver, String member,
                            @Cached("member") String cachedMember,
                            // We cache the member's index for fast-path access
                            @Cached("receiver.findArgumentIndex(member)") int index) throws UnknownIdentifierException {
                return doRead(receiver, cachedMember, index);
            }

            /**
             * Find the member's index and read the value at that index.
             */
            @Specialization(replaces = "doCached")
            @TruffleBoundary
            static Object doGeneric(ArgumentsObject receiver, String member) throws UnknownIdentifierException {
                int index = receiver.findArgumentIndex(member);
                return doRead(receiver, member, index);
            }

            /**
             * Read the argument at the provided index from {@link Frame#getArguments()} array.
             */
            private static Object doRead(ArgumentsObject receiver, String member, int index) throws UnknownIdentifierException {
                if (index < 0) {
                    throw UnknownIdentifierException.create(member);
                }
                if (receiver.frame != null) {
                    return receiver.frame.getArguments()[index];
                } else {
                    return SLNull.SINGLETON;
                }
            }
        }

        /**
         * Write an argument value. Cache the argument's index for fast access.
         */
        @ExportMessage(name = "writeMember")
        static final class WriteMember {

            /**
             * If the member is cached, use the cached index and write the value at that index. Call
             * {@link #doGeneric(ArgumentsObject, String, Object)} otherwise.
             */
            @Specialization(limit = "LIMIT", guards = {"cachedMember.equals(member)"})
            @SuppressWarnings("unused")
            static void doCached(ArgumentsObject receiver, String member, Object value,
                            @Cached("member") String cachedMember,
                            // We cache the member's index for fast-path access
                            @Cached("receiver.findArgumentIndex(member)") int index) throws UnknownIdentifierException, UnsupportedMessageException {
                doWrite(receiver, member, index, value);
            }

            /**
             * Find the member's index and write the value at that index.
             */
            @Specialization(replaces = "doCached")
            @TruffleBoundary
            static void doGeneric(ArgumentsObject receiver, String member, Object value) throws UnknownIdentifierException, UnsupportedMessageException {
                int index = receiver.findArgumentIndex(member);
                doWrite(receiver, member, index, value);
            }

            /**
             * Write the argument value at the provided index into {@link Frame#getArguments()}
             * array.
             */
            private static void doWrite(ArgumentsObject receiver, String member, int index, Object value) throws UnknownIdentifierException, UnsupportedMessageException {
                if (index < 0) {
                    throw UnknownIdentifierException.create(member);
                }
                if (receiver.frame != null) {
                    receiver.frame.getArguments()[index] = value;
                } else {
                    throw UnsupportedMessageException.create();
                }
            }
        }

        boolean hasArgumentIndex(String member) {
            return findArgumentIndex(member) >= 0;
        }

        int findArgumentIndex(String member) {
            TruffleString memberTS = SLStrings.fromJavaString(member);
            SLWriteLocalVariableNode[] writeNodes = root.getDeclaredArguments();
            for (int i = 0; i < writeNodes.length; i++) {
                SLWriteLocalVariableNode writeNode = writeNodes[i];
                if (memberTS.equalsUncached(writeNode.getSlotName(), SLLanguage.STRING_ENCODING)) {
                    return i;
                }
            }
            return -1;
        }

    }

    /**
     * Scope of all variables accessible in the scope from the entered or exited node.
     */
    @ExportLibrary(InteropLibrary.class)
    static final class VariablesObject implements TruffleObject {

        /**
         * The member caching limit.
         */
        static int LIMIT = 4;

        private final Frame frame;          // the current frame
        protected final SLScopedNode node;  // the current node
        final boolean nodeEnter;            // whether the node was entered or is about to be exited
        @NeverDefault protected final SLBlockNode block;  // the inner-most block of the current
                                                          // node

        VariablesObject(Frame frame, SLScopedNode node, boolean nodeEnter, SLBlockNode blockNode) {
            this.frame = frame;
            this.node = node;
            this.nodeEnter = nodeEnter;
            this.block = blockNode;
        }

        /**
         * For performance reasons, fix the library implementation for the current node and enter
         * flag.
         */
        @ExportMessage
        boolean accepts(@Cached(value = "this.node", adopt = false) SLScopedNode cachedNode,
                        @Cached(value = "this.nodeEnter") boolean cachedNodeEnter) {
            return this.node == cachedNode && this.nodeEnter == cachedNodeEnter;
        }

        /**
         * This is a scope object, providing variables as members.
         */
        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isScope() {
            return true;
        }

        /**
         * The scope must provide an associated language.
         */
        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return SLLanguage.class;
        }

        /**
         * Provide either "block", or the function name as the scope's display string.
         */
        @ExportMessage
        @SuppressWarnings({"static-method", "unused"})
        Object toDisplayString(boolean allowSideEffects, @Shared("block") @Cached(value = "this.block", adopt = false) SLBlockNode cachedBlock,
                        @Shared("parentBlock") @Cached(value = "this.block.findBlock()", adopt = false, allowUncached = true) Node parentBlock) {
            // Cache the parent block for the fast-path access
            if (parentBlock instanceof SLBlockNode) {
                return "block";
            } else {
                return ((SLRootNode) parentBlock).getTSName();
            }
        }

        /**
         * There is a parent scope if we're in a block.
         */
        @ExportMessage
        @SuppressWarnings({"static-method", "unused"})
        boolean hasScopeParent(
                        @Shared("block") @Cached(value = "this.block", adopt = false) SLBlockNode cachedBlock,
                        @Shared("parentBlock") @Cached(value = "this.block.findBlock()", adopt = false, allowUncached = true) Node parentBlock) {
            // Cache the parent block for the fast-path access
            return parentBlock instanceof SLBlockNode;
        }

        /**
         * The parent scope object is based on the parent block node.
         */
        @ExportMessage
        @SuppressWarnings("unused")
        Object getScopeParent(
                        @Shared("block") @Cached(value = "this.block", adopt = false) SLBlockNode cachedBlock,
                        @Shared("parentBlock") @Cached(value = "this.block.findBlock()", adopt = false, allowUncached = true) Node parentBlock) throws UnsupportedMessageException {
            // Cache the parent block for the fast-path access
            if (!(parentBlock instanceof SLBlockNode)) {
                throw UnsupportedMessageException.create();
            }
            return new VariablesObject(frame, cachedBlock, true, (SLBlockNode) parentBlock);
        }

        /**
         * We provide a source section of this scope.
         */
        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasSourceLocation() {
            return true;
        }

        /**
         * The source section of this scope is either the block, or the function.
         */
        @ExportMessage
        @TruffleBoundary
        SourceSection getSourceLocation() {
            Node parentBlock = block.findBlock();
            if (parentBlock instanceof RootNode) {
                // We're in the function block
                assert parentBlock instanceof SLRootNode : String.format("In SLLanguage we expect SLRootNode, not %s", parentBlock.getClass());
                return ((SLRootNode) parentBlock).getSourceSection();
            } else {
                return block.getSourceSection();
            }
        }

        /**
         * Scope must provide scope members.
         */
        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasMembers() {
            return true;
        }

        /**
         * Test if a member exists. We cache the result for fast access.
         */
        @ExportMessage(name = "isMemberReadable")
        static final class ExistsMember {

            /**
             * If the member is cached, provide the cached result. Call
             * {@link #doGeneric(VariablesObject, String)} otherwise.
             */
            @Specialization(limit = "LIMIT", guards = {"cachedMember.equals(member)"})
            @SuppressWarnings("unused")
            static boolean doCached(VariablesObject receiver, String member,
                            @Cached("member") String cachedMember,
                            // We cache the member existence for fast-path access
                            @Cached("doGeneric(receiver, member)") boolean cachedResult) {
                assert cachedResult == doGeneric(receiver, member);
                return cachedResult;
            }

            /**
             * Test if a variable with that name exists. It exists if we have a corresponding write
             * node.
             */
            @Specialization(replaces = "doCached")
            @TruffleBoundary
            static boolean doGeneric(VariablesObject receiver, String member) {
                return receiver.hasWriteNode(member);
            }
        }

        /**
         * Test if a member is modifiable. We cache the result for fast access.
         */
        @ExportMessage(name = "isMemberModifiable")
        static final class ModifiableMember {

            @Specialization(limit = "LIMIT", guards = {"cachedMember.equals(member)"})
            @SuppressWarnings("unused")
            static boolean doCached(VariablesObject receiver, String member,
                            @Cached("member") String cachedMember,
                            // We cache the member existence for fast-path access
                            @Cached("receiver.hasWriteNode(member)") boolean cachedResult) {
                return cachedResult && receiver.frame != null;
            }

            /**
             * Test if a variable with that name exists and we have a frame.
             */
            @Specialization(replaces = "doCached")
            @TruffleBoundary
            static boolean doGeneric(VariablesObject receiver, String member) {
                return receiver.hasWriteNode(member) && receiver.frame != null;
            }
        }

        /**
         * We do not support insertion of new variables.
         */
        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
            return false;
        }

        /**
         * Read a variable value. Cache the frame slot by variable name for fast access.
         */
        @ExportMessage(name = "readMember")
        static final class ReadMember {

            /**
             * If the member is cached, use the cached frame slot and read the value from it. Call
             * {@link #doGeneric(VariablesObject, String)} otherwise.
             */
            @Specialization(limit = "LIMIT", guards = {"cachedMember.equals(member)"})
            @SuppressWarnings("unused")
            static Object doCached(VariablesObject receiver, String member,
                            @Cached("member") String cachedMember,
                            // We cache the member's frame slot for fast-path access
                            @Cached("receiver.findSlot(member)") int slot) throws UnknownIdentifierException {
                return doRead(receiver, cachedMember, slot);
            }

            /**
             * The uncached version finds the member's slot and read the value from it.
             */
            @Specialization(replaces = "doCached")
            @TruffleBoundary
            static Object doGeneric(VariablesObject receiver, String member) throws UnknownIdentifierException {
                int slot = receiver.findSlot(member);
                return doRead(receiver, member, slot);
            }

            private static Object doRead(VariablesObject receiver, String member, int slot) throws UnknownIdentifierException {
                if (slot == -1) {
                    throw UnknownIdentifierException.create(member);
                }
                if (receiver.frame != null) {
                    return receiver.frame.getValue(slot);
                } else {
                    return SLNull.SINGLETON;
                }
            }
        }

        /**
         * Write a variable value. Cache the write node by variable name for fast access.
         */
        @ExportMessage(name = "writeMember")
        static final class WriteMember {

            /*
             * If the member is cached, use the cached write node and use it to write the value.
             * Call {@link #doGeneric(VariablesObject, String, Object)} otherwise.
             */
            @Specialization(limit = "LIMIT", guards = {"cachedMember.equals(member)"})
            @SuppressWarnings("unused")
            static void doCached(VariablesObject receiver, String member, Object value,
                            @Cached("member") String cachedMember,
                            // We cache the member's write node for fast-path access
                            @Cached(value = "receiver.findWriteNode(member)", adopt = false) SLWriteLocalVariableNode writeNode) throws UnknownIdentifierException, UnsupportedMessageException {
                doWrite(receiver, cachedMember, writeNode, value);
            }

            /**
             * The uncached version finds the write node and use it to write the value.
             */
            @Specialization(replaces = "doCached")
            @TruffleBoundary
            static void doGeneric(VariablesObject receiver, String member, Object value) throws UnknownIdentifierException, UnsupportedMessageException {
                SLWriteLocalVariableNode writeNode = receiver.findWriteNode(member);
                doWrite(receiver, member, writeNode, value);
            }

            private static void doWrite(VariablesObject receiver, String member, SLWriteLocalVariableNode writeNode, Object value) throws UnknownIdentifierException, UnsupportedMessageException {
                if (writeNode == null) {
                    throw UnknownIdentifierException.create(member);
                }
                if (receiver.frame == null) {
                    throw UnsupportedMessageException.create();
                }
                writeNode.executeWrite((VirtualFrame) receiver.frame, value);
            }
        }

        /**
         * Get the variables. Cache the array of write nodes that declare variables in the scope(s)
         * and the indexes which determine visible variables.
         */
        @ExportMessage
        @SuppressWarnings("static-method")
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal,
                        @Cached(value = "this.block.getDeclaredLocalVariables()", adopt = false, neverDefault = false, dimensions = 1, allowUncached = true) SLWriteLocalVariableNode[] writeNodes,
                        @Cached(value = "this.getVisibleVariablesIndex()", allowUncached = true, neverDefault = false) int visibleVariablesIndex,
                        @Cached(value = "this.block.getParentBlockIndex()", allowUncached = true, neverDefault = false) int parentBlockIndex) {
            return new KeysArray(writeNodes, visibleVariablesIndex, parentBlockIndex);
        }

        int getVisibleVariablesIndex() {
            assert node.visibleVariablesIndexOnEnter >= 0;
            assert node.visibleVariablesIndexOnExit >= 0;
            return nodeEnter ? node.visibleVariablesIndexOnEnter : node.visibleVariablesIndexOnExit;
        }

        boolean hasWriteNode(String member) {
            return findWriteNode(member) != null;
        }

        int findSlot(String member) {
            SLWriteLocalVariableNode writeNode = findWriteNode(member);
            if (writeNode != null) {
                return writeNode.getSlot();
            } else {
                return -1;
            }
        }

        /**
         * Find write node, which declares variable of the given name. Search through the variables
         * declared in the block and its parents and return the first one that matches.
         *
         * @param member the variable name
         */
        SLWriteLocalVariableNode findWriteNode(String member) {
            TruffleString memberTS = SLStrings.fromJavaString(member);
            SLWriteLocalVariableNode[] writeNodes = block.getDeclaredLocalVariables();
            int parentBlockIndex = block.getParentBlockIndex();
            int index = getVisibleVariablesIndex();
            for (int i = 0; i < index; i++) {
                SLWriteLocalVariableNode writeNode = writeNodes[i];
                if (memberTS.equalsUncached(writeNode.getSlotName(), SLLanguage.STRING_ENCODING)) {
                    return writeNode;
                }
            }
            for (int i = parentBlockIndex; i < writeNodes.length; i++) {
                SLWriteLocalVariableNode writeNode = writeNodes[i];
                if (memberTS.equalsUncached(writeNode.getSlotName(), SLLanguage.STRING_ENCODING)) {
                    return writeNode;
                }
            }
            return null;
        }
    }

    /**
     * Array of visible variables. The variables are based on their declaration write nodes and are
     * represented as {@link Key} objects.
     */
    @ExportLibrary(InteropLibrary.class)
    static final class KeysArray implements TruffleObject {

        @CompilationFinal(dimensions = 1) private final SLWriteLocalVariableNode[] writeNodes;
        private final int variableIndex;
        private final int parentBlockIndex;

        /**
         * Creates a new array of visible variables.
         *
         * @param writeNodes all variables declarations in the scope, including parent scopes.
         * @param variableIndex index to the variables array, determining variables in the
         *            inner-most scope (from zero index up to the <code>variableIndex</code>,
         *            exclusive).
         * @param parentBlockIndex index to the variables array, determining variables in the parent
         *            block's scope (from <code>parentBlockIndex</code> to the end of the
         *            <code>writeNodes</code> array).
         */
        KeysArray(SLWriteLocalVariableNode[] writeNodes, int variableIndex, int parentBlockIndex) {
            this.writeNodes = writeNodes;
            this.variableIndex = variableIndex;
            this.parentBlockIndex = parentBlockIndex;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            // We see all parent's variables (writeNodes.length - parentBlockIndex) plus the
            // variables in the inner-most scope visible by the current node (variableIndex).
            return writeNodes.length - parentBlockIndex + variableIndex;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < (writeNodes.length - parentBlockIndex + variableIndex);
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            if (index < variableIndex) {
                // if we're in the inner-most scope, it's simply the variable on the index
                return new Key(writeNodes[(int) index]);
            } else {
                // else it's a variable declared in the parent's scope, we start at parentBlockIndex
                return new Key(writeNodes[(int) index - variableIndex + parentBlockIndex]);
            }
        }

    }

    /**
     * Representation of a variable based on a {@link SLWriteLocalVariableNode write node} that
     * declares the variable. It provides the variable name as a {@link Key#asString() string} and
     * the name node associated with the variable's write node as a {@link Key#getSourceLocation()
     * source location}.
     */
    @ExportLibrary(InteropLibrary.class)
    static final class Key implements TruffleObject {

        private final SLWriteLocalVariableNode writeNode;

        Key(SLWriteLocalVariableNode writeNode) {
            this.writeNode = writeNode;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isString() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        String asString() {
            // frame slot's identifier object is not safe to convert to String on fast-path.
            return writeNode.getSlotName().toJavaStringUncached();
        }

        @ExportMessage
        @TruffleBoundary
        TruffleString asTruffleString() {
            // frame slot's identifier object is not safe to convert to String on fast-path.
            return writeNode.getSlotName();
        }

        @ExportMessage
        boolean hasSourceLocation() {
            return writeNode.getNameNode().getSourceCharIndex() >= 0;
        }

        @ExportMessage
        @TruffleBoundary
        SourceSection getSourceLocation() throws UnsupportedMessageException {
            if (!hasSourceLocation()) {
                throw UnsupportedMessageException.create();
            }
            SLExpressionNode nameNode = writeNode.getNameNode();
            return writeNode.getRootNode().getSourceSection().getSource().createSection(nameNode.getSourceCharIndex(), nameNode.getSourceLength());
        }
    }
}
