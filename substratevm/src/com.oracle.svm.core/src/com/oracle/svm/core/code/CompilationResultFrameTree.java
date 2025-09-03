/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.code;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.code.SourceMapping;
import jdk.graal.compiler.debug.DebugContext;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class CompilationResultFrameTree {

    public abstract static class SourcePositionSupplier implements Comparable<SourcePositionSupplier> {

        private final BytecodePosition bytecodePosition;
        private final int callDepth;

        protected SourcePositionSupplier(BytecodePosition bytecodePosition, int callDepth) {
            this.bytecodePosition = bytecodePosition;
            this.callDepth = callDepth;
        }

        public abstract int getStartOffset();

        public abstract int getSize();

        public int getEndOffset() {
            return getStartOffset() + getSize() - 1;
        }

        public BytecodePosition getBytecodePosition() {
            return bytecodePosition;
        }

        public static StackTraceElement getStackTraceElement(BytecodePosition pos) {
            return pos.getMethod().asStackTraceElement(pos.getBCI());
        }

        public final StackTraceElement getStackTraceElement() {
            return getStackTraceElement(getBytecodePosition());
        }

        public final String getPosStr() {
            return String.format("[%d..%d]", getStartOffset(), getEndOffset());
        }

        public final String getStackFrameStr() {
            StackTraceElement stackTraceElement = getStackTraceElement();
            if (stackTraceElement.getFileName() != null && stackTraceElement.getLineNumber() > 0) {
                return stackTraceElement.toString();
            } else {
                return getBytecodePosition().getMethod().format("%h.%n(%p)");
            }
        }

        public int getCallDepth() {
            return callDepth;
        }

        protected static int getCallDepth(BytecodePosition startPos) {
            int depth = 0;
            BytecodePosition pos = startPos;
            while (pos != null) {
                depth += 1;
                pos = pos.getCaller();
            }
            return depth;
        }

        @Override
        public final String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getClass().getName());
            sb.append(" ");
            sb.append(getPosStr());
            sb.append(" with ");
            sb.append(getStackFrameStr());
            sb.append(" depth ");
            sb.append(getCallDepth());
            return sb.toString();
        }

        @Override
        public int compareTo(SourcePositionSupplier o) {
            if (getStartOffset() < o.getStartOffset()) {
                return -1;
            } else if (getStartOffset() > o.getStartOffset()) {
                return 1;
            }
            return 0;
        }
    }

    public static final class InfopointSourceWrapper extends SourcePositionSupplier {
        public final Infopoint infopoint;

        public static InfopointSourceWrapper create(Infopoint infopoint, int maxDepth) {
            if (infopoint.debugInfo == null || infopoint.debugInfo.getBytecodePosition() == null) {
                return null;
            }
            BytecodePosition pos = infopoint.debugInfo.getBytecodePosition();
            int depth = getCallDepth(pos);
            if (depth > maxDepth) {
                return null;
            }
            return new InfopointSourceWrapper(pos, depth, infopoint);
        }

        private InfopointSourceWrapper(BytecodePosition bytecodePosition, int callDepth, Infopoint infopoint) {
            super(bytecodePosition, callDepth);
            this.infopoint = infopoint;
        }

        @Override
        public int getStartOffset() {
            return infopoint.pcOffset;
        }

        @Override
        public int getSize() {
            if (infopoint instanceof Call) {
                return ((Call) infopoint).size;
            }
            return 1;
        }

        @Override
        public int compareTo(SourcePositionSupplier o) {
            int res = super.compareTo(o);
            if (res != 0) {
                return res;
            }
            if (o instanceof InfopointSourceWrapper) {
                InfopointSourceWrapper other = (InfopointSourceWrapper) o;
                /* Use same order as with infopoints on same pcOffset */
                return infopoint.reason.compareTo(other.infopoint.reason);
            }
            return 1; /* make Infopoints go first */
        }
    }

    public static final class SourceMappingWrapper extends SourcePositionSupplier {
        public final SourceMapping sourceMapping;

        public static SourceMappingWrapper create(SourceMapping sourceMapping, int maxDepth) {
            if (sourceMapping.getSourcePosition() == null) {
                return null;
            }
            BytecodePosition pos = sourceMapping.getSourcePosition();
            int depth = getCallDepth(pos);
            if (depth > maxDepth) {
                return null;
            }
            if (sourceMapping.getStartOffset() > sourceMapping.getEndOffset()) {
                JVMCIError.shouldNotReachHere("Invalid SourceMapping " + getSourceMappingString(sourceMapping));
            }
            return new SourceMappingWrapper(pos, depth, sourceMapping);
        }

        static String getSourceMappingString(SourceMapping sourceMapping) {
            BytecodePosition pos = sourceMapping.getSourcePosition();
            SourceMappingWrapper tmp = new SourceMappingWrapper(pos, getCallDepth(pos), sourceMapping);
            return tmp.getPosStr() + " with " + tmp.getStackFrameStr();
        }

        private SourceMappingWrapper(BytecodePosition bytecodePosition, int callDepth, SourceMapping sourceMapping) {
            super(bytecodePosition, callDepth);
            this.sourceMapping = sourceMapping;
        }

        @Override
        public int getStartOffset() {
            return sourceMapping.getStartOffset();
        }

        public boolean hasCode() {
            return sourceMapping.getEndOffset() > sourceMapping.getStartOffset();
        }

        @Override
        public int getSize() {
            if (!hasCode()) {
                /*
                 * This SourceMapping corresponds to some node that doesn't produce code. We still
                 * need to give it size so that it can be used in FrameTree construction. If it
                 * overlaps with another SourceMapping we will choose the one that has code.
                 */
                return 1;
            }
            /* SourceMapping is defined as half open range */
            return sourceMapping.getEndOffset() - sourceMapping.getStartOffset();
        }

        @Override
        public int compareTo(SourcePositionSupplier o) {
            int res = super.compareTo(o);
            if (res != 0) {
                return res;
            }
            if (o instanceof SourceMappingWrapper) {
                int thisSize = getSize();
                int thatSize = o.getSize();
                // sort zero length source mappings earlier
                if (thisSize == 0) {
                    if (thatSize > 0) {
                        return -1;
                    }
                } else if (thatSize == 0) {
                    if (thisSize > 0) {
                        return 1;
                    }
                }
                return 0;
            }
            return -1; /* make Infopoints go first */
        }
    }

    public static class FrameNode {

        public final BytecodePosition frame;
        public final SourcePositionSupplier sourcePos;
        public final FrameNode parent;

        FrameNode nextSibling;

        FrameNode(FrameNode parent, BytecodePosition frame, SourcePositionSupplier sourcePos) {
            this.parent = parent;
            this.frame = frame;
            this.sourcePos = sourcePos;
        }

        boolean hasEqualFrameChain(BytecodePosition otherFrame) {
            return hasEqualCaller(frame, otherFrame) && (parent == null ? otherFrame.getCaller() == null : parent.hasEqualFrameChain(otherFrame.getCaller()));
        }

        static boolean hasEqualCaller(BytecodePosition thisFrame, BytecodePosition thatFrame) {
            return thisFrame.getMethod().equals(thatFrame.getMethod()) && getCallerBCI(thisFrame) == getCallerBCI(thatFrame);
        }

        static int getCallerBCI(BytecodePosition frame) {
            BytecodePosition callerFrame = frame.getCaller();
            if (callerFrame != null) {
                return callerFrame.getBCI();
            }
            return -1; // For bottom frame
        }

        public int getStartPos() {
            return sourcePos.getStartOffset();
        }

        public int getSpan() {
            if (nextSibling != null) {
                return nextSibling.getStartPos() - getStartPos();
            }
            if (parent != null) {
                return parent.getStartPos() + parent.getSpan() - getStartPos();
            }
            throw JVMCIError.shouldNotReachHere();
        }

        public final int getEndPos() {
            return getStartPos() + getSpan() - 1;
        }

        public final String getPosStr() {
            return String.format("[%d..%d]", getStartPos(), getEndPos());
        }

        @Override
        public String toString() {
            return String.format("-%s, span %d, bci %d, method %s", getPosStr(), getSpan(), frame.getBCI(), frame.getMethod().format("%h.%n(%p)"));
        }

        public String getLocalsStr() {
            String localsInfo = "";
            if (frame instanceof BytecodeFrame) {
                BytecodeFrame bcf = (BytecodeFrame) frame;
                Local[] locals = getLocalsBySlot(frame.getMethod());
                StringBuilder sb = new StringBuilder();
                if (locals == null) {
                    return localsInfo;
                }
                int combinedLength = Integer.min(locals.length, bcf.numLocals);
                for (int i = 0; i < combinedLength; i++) {
                    JavaValue value = bcf.getLocalValue(i);
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append("li(");
                    Local local = locals[i];
                    if (local != null) {
                        sb.append(local.getName());
                        sb.append("=");
                    }
                    sb.append(value);
                    sb.append(")");
                }
                localsInfo = sb.toString();
            }
            return localsInfo;
        }

        private static Local[] getLocalsBySlot(ResolvedJavaMethod method) {
            LocalVariableTable lvt = method.getLocalVariableTable();
            Local[] nonEmptySortedLocals = null;
            if (lvt != null) {
                Local[] locals = lvt.getLocals();
                if (locals != null && locals.length > 0) {
                    nonEmptySortedLocals = Arrays.copyOf(locals, locals.length);
                    Arrays.sort(nonEmptySortedLocals, (Local l1, Local l2) -> l1.getSlot() - l2.getSlot());
                }
            }
            return nonEmptySortedLocals;
        }

        public void visit(Visitor visitor, Object... varargs) {
            visitor.apply(this, varargs);
        }

        @SuppressWarnings("unused")
        public void visitChildren(Visitor visitor, Object... varargs) {
        }

        public StackTraceElement getStackTraceElement() {
            return SourcePositionSupplier.getStackTraceElement(frame);
        }
    }

    public static class CallNode extends FrameNode {
        FrameNode firstChild;

        CallNode(FrameNode parent, BytecodePosition frame, SourcePositionSupplier sourcePos) {
            super(parent, frame, sourcePos);
        }

        CallNode getCallNodeChild(BytecodePosition otherFrame, SourcePositionSupplier otherSourcePos) {
            FrameNode node = firstChild;
            while (node != null) {
                if (node instanceof CallNode && node.hasEqualFrameChain(otherFrame) && otherSourcePos.getStartOffset() <= node.getEndPos()) {
                    return (CallNode) node;
                }
                node = node.nextSibling;
            }
            return null;
        }

        private <N extends FrameNode> N addChild(N newNode) {
            assert newNode.parent == this : this;
            /* Add the new node at the end */

            /* Simple case, add as first child */
            if (firstChild == null) {
                firstChild = newNode;
                return newNode;
            }

            /* Else, add at as sibling of last child node */
            FrameNode node = firstChild;
            FrameNode lastNode = node;
            while (node != null) {
                lastNode = node;
                node = node.nextSibling;
            }
            lastNode.nextSibling = newNode;
            return newNode;
        }

        CallNode addCallNode(BytecodePosition f, SourcePositionSupplier srcPos) {
            return addChild(new CallNode(this, f, srcPos));
        }

        FrameNode addFrameInfo(BytecodePosition f, SourcePositionSupplier srcPos) {
            return addChild(new FrameNode(this, f, srcPos));
        }

        @Override
        public void visitChildren(Visitor visitor, Object... varargs) {
            FrameNode node = firstChild;
            while (node != null) {
                visitor.apply(node, varargs);
                node = node.nextSibling;
            }
        }

        @Override
        public String toString() {
            String prefix = "+%s, span %d, method %s, caller bci %d";
            String methodName = frame.getMethod().format("%h.%n(%p)");
            return String.format(prefix, getPosStr(), getSpan(), methodName, getCallerBCI(frame));
        }
    }

    public static final class Builder {
        private Builder.RootNode root = null;
        private final int targetCodeSize;
        private final int maxDepth;
        private final DebugContext debug;
        private final boolean useSourceMappings;
        private final boolean verify;

        int indexLeft;
        int indexRight;

        final class RootNode extends CallNode {
            RootNode(BytecodePosition firstFrame, SourcePositionSupplier firstSourcePos) {
                super(null, firstFrame, firstSourcePos);
            }

            @Override
            public int getStartPos() {
                return 0;
            }

            @Override
            public int getSpan() {
                return targetCodeSize;
            }

            @Override
            public String toString() {
                String prefix = "R%s, span %d, method %s";
                String methodName = frame.getMethod().format("%h.%n(%p)");
                return String.format(prefix, getPosStr(), getSpan(), methodName);
            }
        }

        public Builder(DebugContext debug, int targetCodeSize, int maxDepth, boolean useSourceMappings, boolean verify) {
            this.targetCodeSize = targetCodeSize;
            this.maxDepth = maxDepth;
            this.useSourceMappings = useSourceMappings;
            this.verify = verify;
            this.debug = debug;
        }

        @SuppressWarnings("try")
        public CallNode build(CompilationResult compilationResult) {
            try (DebugContext.Scope s = debug.scope("FrameTree.Builder", compilationResult)) {
                if (debug.isLogEnabled()) {
                    debug.log(DebugContext.VERBOSE_LEVEL, "Building FrameTree for %s", compilationResult);
                }
                List<Infopoint> infopoints = compilationResult.getInfopoints();
                List<SourceMapping> sourceMappings = compilationResult.getSourceMappings();
                List<SourcePositionSupplier> sourcePosData = new ArrayList<>(infopoints.size() + sourceMappings.size());
                InfopointSourceWrapper infopointForRoot = null;
                for (Infopoint infopoint : infopoints) {
                    InfopointSourceWrapper wrapper = InfopointSourceWrapper.create(infopoint, maxDepth);
                    if (wrapper != null) {
                        sourcePosData.add(wrapper);
                        infopointForRoot = wrapper;
                    } else {
                        if (debug.isLogEnabled(DebugContext.DETAILED_LEVEL)) {
                            debug.log(" Discard Infopoint %s", infopoint);
                        }
                    }
                }
                if (useSourceMappings) {
                    for (SourceMapping sourceMapping : sourceMappings) {
                        SourceMappingWrapper wrapper = SourceMappingWrapper.create(sourceMapping, maxDepth);
                        if (wrapper != null) {
                            if (wrapper.getStartOffset() > targetCodeSize - 1) {
                                if (debug.isLogEnabled(DebugContext.DETAILED_LEVEL)) {
                                    debug.log(" Discard SourceMapping outside code-range %s", SourceMappingWrapper.getSourceMappingString(sourceMapping));
                                }
                                continue;
                            }
                            sourcePosData.add(wrapper);
                        } else {
                            if (debug.isLogEnabled(DebugContext.DETAILED_LEVEL)) {
                                debug.log(" Discard SourceMapping %s", SourceMappingWrapper.getSourceMappingString(sourceMapping));
                            }
                        }
                    }
                }

                sourcePosData.sort(Comparator.naturalOrder());

                if (useSourceMappings) {
                    nullifyOverlappingSourcePositions(sourcePosData);
                }

                if (debug.isLogEnabled(DebugContext.DETAILED_LEVEL)) {
                    debug.log("Sorted input data:");
                    for (SourcePositionSupplier sourcePositionSupplier : sourcePosData) {
                        if (sourcePositionSupplier != null) {
                            debug.log(" %s", sourcePositionSupplier);
                        }
                    }
                }

                if (infopointForRoot != null) {
                    /*
                     * Use some random infopoint bottom frame to construct root node. This will
                     * safeguard against bogus SourceMapping (they will not find a place in the
                     * FrameTree). Note that if we could trust SourceMappings we would not need
                     * that.
                     */
                    BytecodePosition bcp = infopointForRoot.getBytecodePosition();
                    while (bcp.getCaller() != null) {
                        bcp = bcp.getCaller();
                    }
                    visitFrame(infopointForRoot, bcp, null);
                } else {
                    // Checkstyle: Allow raw info or warning printing - begin
                    debug.log("Warning: Constructing FrameTree from SourceMappings only is (currently) unsafe");
                    // Checkstyle: Allow raw info or warning printing - end
                }

                /*
                 * Using sorted infopoints ensures that the sorting of children Nodes will be
                 * correct right from the beginning. No need to sort children individually.
                 */
                for (SourcePositionSupplier sourcePos : sourcePosData) {
                    /* Visit frames bottom-up to build the tree */
                    if (sourcePos != null) {
                        visitFrame(sourcePos, sourcePos.getBytecodePosition(), null);
                    }
                }

                if (root != null) {
                    if (debug.isLogEnabled(DebugContext.DETAILED_LEVEL)) {
                        StringBuilder sb = new StringBuilder();
                        CompilationResultFrameTree.dump(root, sb::append, false, false, 0);
                        debug.log("%s", sb);
                    }

                    if (verify) {
                        StringBuilder sb = debug.isLogEnabled() ? new StringBuilder() : null;
                        boolean verifyResult = CompilationResultFrameTree.verify(root, str -> {
                            if (sb != null) {
                                sb.append(str);
                            }
                        });
                        if (debug.isLogEnabled()) {
                            debug.log("%s", sb);
                        }
                        VMError.guarantee(verifyResult, "FrameTree verification failed");
                    }
                }
            } catch (Throwable e) {
                throw debug.handle(e);
            }

            return root;
        }

        static boolean overlappingSourcePosition(SourcePositionSupplier left, SourcePositionSupplier right) {
            if (left.getEndOffset() >= right.getStartOffset()) {
                return true;
            }
            return false;
        }

        private void nullifyLeft(List<SourcePositionSupplier> sourcePosData) {
            sourcePosData.set(indexLeft, null);
            for (int i = indexLeft; i < indexRight; ++i) {
                VMError.guarantee(sourcePosData.get(i) == null, "Why is there a non-null entry between left and right?");
            }
            indexLeft = indexRight;
            indexRight += 1;

        }

        private void nullifyRight(List<SourcePositionSupplier> sourcePosData) {
            sourcePosData.set(indexRight++, null);
        }

        void nullifyOverlappingSourcePositions(List<SourcePositionSupplier> sourcePosData) {
            indexLeft = 0;
            indexRight = 1;

            while (indexRight < sourcePosData.size()) {
                SourcePositionSupplier leftPos = sourcePosData.get(indexLeft);
                SourcePositionSupplier rightPos = sourcePosData.get(indexRight);

                if (overlappingSourcePosition(leftPos, rightPos)) {
                    if (debug.isLogEnabled(DebugContext.DETAILED_LEVEL)) {
                        debug.log("Handle Overlapping SourcePositions: %s | %s", leftPos, rightPos);
                    }

                    /* Handle infopoint overlapping */
                    if (leftPos instanceof InfopointSourceWrapper && rightPos instanceof InfopointSourceWrapper) {
                        Infopoint leftIP = ((InfopointSourceWrapper) leftPos).infopoint;
                        Infopoint rightIP = ((InfopointSourceWrapper) rightPos).infopoint;
                        /* In case of overlapping prefer the BYTECODE_POSITION infopoint */
                        if (leftIP.reason == InfopointReason.BYTECODE_POSITION) {
                            nullifyRight(sourcePosData);
                        } else if (rightIP.reason == InfopointReason.BYTECODE_POSITION) {
                            nullifyLeft(sourcePosData);
                        } else {
                            JVMCIError.shouldNotReachHere("Unhandled overlapping Infopoints");
                        }
                        continue;
                    }

                    /* Handle SourceMapping overlapping */
                    if (leftPos instanceof SourceMappingWrapper && rightPos instanceof SourceMappingWrapper) {
                        SourceMappingWrapper left = (SourceMappingWrapper) leftPos;
                        SourceMappingWrapper right = (SourceMappingWrapper) rightPos;

                        VMError.guarantee(!left.hasCode() || !right.hasCode(), "Non-empty SourceMappings never overlap");

                        /* Prefer SourceMapping with code */
                        if (left.hasCode()) {
                            nullifyRight(sourcePosData);
                            continue;
                        }

                        if (right.hasCode()) {
                            nullifyLeft(sourcePosData);
                            continue;
                        }

                        /* If both have no code let callDepth decide or pick left */
                        if (right.getCallDepth() > left.getCallDepth()) {
                            nullifyLeft(sourcePosData);
                        } else {
                            nullifyRight(sourcePosData);
                        }
                        continue;
                    }

                    /* In mixed cases, favor Infopoints */
                    if (leftPos instanceof InfopointSourceWrapper) {
                        nullifyRight(sourcePosData);
                        continue;
                    }
                    if (rightPos instanceof InfopointSourceWrapper) {
                        nullifyLeft(sourcePosData);
                        continue;
                    }
                    JVMCIError.shouldNotReachHere("Unhandled overlapping SourcePositions");
                } else {
                    indexRight += 1;
                    do {
                        indexLeft += 1;
                    } while (sourcePosData.get(indexLeft) == null);
                }
            }
        }

        private CallNode visitFrame(SourcePositionSupplier sourcePos, BytecodePosition frame, BytecodePosition callee) {
            BytecodePosition caller = frame.getCaller();
            if (caller != null) {
                CallNode parent = visitFrame(sourcePos, caller, frame);
                /* Find out if a CallNode for the current frame was already added. */
                CallNode child = parent.getCallNodeChild(frame, sourcePos);
                if (child == null) {
                    /* Create a new node for frame and add it at the right place */
                    return parent.addCallNode(frame, sourcePos);
                }
                if (callee == null) {
                    child.addFrameInfo(frame, sourcePos);
                }
                /* We already have a child for this frame */
                return child;
            } else {
                /* We are at the bottom frame - create a root Node */
                if (root == null) {
                    root = new Builder.RootNode(frame, sourcePos);
                    return root;
                }

                boolean hasEqualCaller = FrameNode.hasEqualCaller(root.frame, frame);
                if (debug.isLogEnabled() && !hasEqualCaller) {
                    debug.log("Bottom frame mismatch for %s", sourcePos);
                }
                if (callee == null && hasEqualCaller) {
                    root.addFrameInfo(frame, sourcePos);
                }
                return root;
            }
        }
    }

    public interface Visitor {
        void apply(FrameNode node, Object... args);
    }

    public static boolean verify(FrameNode node, Consumer<String> printer) {
        FrameTreeVerifier frameTreeVerifier = new FrameTreeVerifier(printer);
        node.visit(frameTreeVerifier);
        return frameTreeVerifier.passed();
    }

    private static final class FrameTreeVerifier implements Visitor {
        private final Consumer<String> printer;
        private int issues = 0;

        FrameTreeVerifier(Consumer<String> printer) {
            this.printer = printer;
        }

        boolean passed() {
            return issues == 0;
        }

        @Override
        public void apply(FrameNode node, Object... args) {
            if (node.getStartPos() > node.getEndPos()) {
                printer.accept("Error: Node startPos > endPos: ");
                printer.accept(node.toString());
                printer.accept(System.lineSeparator());
                issues += 1;
            }
            if (node.nextSibling != null) {
                if (!(node.getEndPos() < node.nextSibling.getStartPos())) {
                    printer.accept("Error: Overlapping nodes: ");
                    printer.accept(node.toString());
                    printer.accept(" with ");
                    printer.accept(node.nextSibling.toString());
                    printer.accept(System.lineSeparator());
                    issues += 1;
                }
            }
            node.visitChildren(this);
        }
    }

    public static void dump(FrameNode node, Consumer<String> printer, boolean onlyCallTree, boolean showInfopoints, int maxDepth) {
        if (node != null) {
            printer.accept(System.lineSeparator());
            node.visit(new FrameTreeDumper(printer, onlyCallTree, showInfopoints, maxDepth), 0);
            printer.accept(System.lineSeparator());
        }
    }

    private static final class FrameTreeDumper implements Visitor {
        private final Consumer<String> printer;
        private final boolean onlyCallTree;
        private final boolean showSourcePos;
        private final boolean showLocals;
        private final int maxDepth;

        FrameTreeDumper(Consumer<String> printer, boolean onlyCallTree, boolean showSourcePos, int maxDepth) {
            this.printer = printer;
            this.onlyCallTree = onlyCallTree;
            this.showSourcePos = showSourcePos;
            this.showLocals = true;
            this.maxDepth = maxDepth;
        }

        private void indent(int level) {
            printer.accept(new String(new char[level * 4]).replace("\0", " "));
        }

        @Override
        public void apply(FrameNode node, Object... args) {
            if (onlyCallTree && !(node instanceof CallNode)) {
                return;
            }
            int level = (int) args[0];
            if (maxDepth > 0 && level > maxDepth) {
                return;
            }
            indent(level);
            printer.accept(node.toString());
            if (showSourcePos) {
                printer.accept(System.lineSeparator());
                indent(level);
                printer.accept(" sourcePos: " + node.sourcePos.toString());
            } else {
                StackTraceElement stackTraceElement = node.getStackTraceElement();
                printer.accept(" at " + stackTraceElement.getFileName() + ":" + stackTraceElement.getLineNumber());
            }
            if (showLocals) {
                printer.accept(" locals: ");
                printer.accept(node.getLocalsStr());
            }
            printer.accept(System.lineSeparator());
            node.visitChildren(this, level + 1);
        }
    }
}
