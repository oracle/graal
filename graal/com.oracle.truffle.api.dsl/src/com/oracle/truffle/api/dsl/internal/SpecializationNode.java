/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.internal;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.internal.SlowPathEvent.SlowPathEvent0;
import com.oracle.truffle.api.dsl.internal.SlowPathEvent.SlowPathEvent1;
import com.oracle.truffle.api.dsl.internal.SlowPathEvent.SlowPathEvent2;
import com.oracle.truffle.api.dsl.internal.SlowPathEvent.SlowPathEvent3;
import com.oracle.truffle.api.dsl.internal.SlowPathEvent.SlowPathEvent4;
import com.oracle.truffle.api.dsl.internal.SlowPathEvent.SlowPathEvent5;
import com.oracle.truffle.api.dsl.internal.SlowPathEvent.SlowPathEventN;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.NodeClass;
import com.oracle.truffle.api.nodes.NodeUtil.NodeField;

/**
 * Internal implementation dependent base class for generated specialized nodes.
 */
@NodeInfo(cost = NodeCost.NONE)
@SuppressWarnings("unused")
public abstract class SpecializationNode extends Node {

    @Child protected SpecializationNode next;

    final int index;

    public SpecializationNode() {
        this(-1);
    }

    public SpecializationNode(int index) {
        this.index = index;
    }

    @Override
    public final NodeCost getCost() {
        return NodeCost.NONE;
    }

    public void reset() {
        SpecializationNode start = findStart();
        SpecializationNode end = findEnd();
        if (start != end) {
            start.replace(end, "reset specialization");
        }
    }

    public static Node updateRoot(Node node) {
        updateRootImpl(((SpecializedNode) node).getSpecializationNode(), node);
        return node;
    }

    private static void updateRootImpl(SpecializationNode start, Node node) {
        NodeField[] fields = NodeClass.get(start.getClass()).getFields();
        for (int i = fields.length - 1; i >= 0; i--) {
            NodeField f = fields[i];
            if (f.getName().equals("root")) {
                f.putObject(start, node);
                break;
            }
        }
        if (start.next != null) {
            updateRootImpl(start.next, node);
        }
    }

    protected final SpecializationNode polymorphicMerge(SpecializationNode newNode, SpecializationNode merged) {
        if (merged == newNode && count() <= 2) {
            return removeSame(new SlowPathEvent0(this, "merged polymorphic to monomorphic", null));
        }
        return merged;
    }

    public final NodeCost getNodeCost() {
        switch (count()) {
            case 0:
            case 1:
                return NodeCost.UNINITIALIZED;
            case 2:
                return NodeCost.MONOMORPHIC;
            default:
                return NodeCost.POLYMORPHIC;
        }
    }

    protected abstract Node[] getSuppliedChildren();

    protected SpecializationNode merge(SpecializationNode newNode, Frame frame) {
        if (isIdentical(newNode, frame)) {
            return this;
        }
        return next != null ? next.merge(newNode, frame) : newNode;
    }

    protected SpecializationNode merge(SpecializationNode newNode, Frame frame, Object o1) {
        if (isIdentical(newNode, frame, o1)) {
            return this;
        }
        return next != null ? next.merge(newNode, frame, o1) : newNode;
    }

    protected SpecializationNode merge(SpecializationNode newNode, Frame frame, Object o1, Object o2) {
        if (isIdentical(newNode, frame, o1, o2)) {
            return this;
        }
        return next != null ? next.merge(newNode, frame, o1, o2) : newNode;
    }

    protected SpecializationNode merge(SpecializationNode newNode, Frame frame, Object o1, Object o2, Object o3) {
        if (isIdentical(newNode, frame, o1, o2, o3)) {
            return this;
        }
        return next != null ? next.merge(newNode, frame, o1, o2, o3) : newNode;
    }

    protected SpecializationNode merge(SpecializationNode newNode, Frame frame, Object o1, Object o2, Object o3, Object o4) {
        if (isIdentical(newNode, frame, o1, o2, o3, o4)) {
            return this;
        }
        return next != null ? next.merge(newNode, frame, o1, o2, o3, o4) : newNode;
    }

    protected SpecializationNode merge(SpecializationNode newNode, Frame frame, Object o1, Object o2, Object o3, Object o4, Object o5) {
        if (isIdentical(newNode, frame, o1, o2, o3, o4, o5)) {
            return this;
        }
        return next != null ? next.merge(newNode, frame, o1, o2, o3, o4, o5) : newNode;
    }

    protected SpecializationNode merge(SpecializationNode newNode, Frame frame, Object... args) {
        if (isIdentical(newNode, frame, args)) {
            return this;
        }
        return next != null ? next.merge(newNode, frame, args) : newNode;
    }

    protected boolean isSame(SpecializationNode other) {
        return getClass() == other.getClass();
    }

    protected boolean isIdentical(SpecializationNode newNode, Frame frame) {
        return isSame(newNode);
    }

    protected boolean isIdentical(SpecializationNode newNode, Frame frame, Object o1) {
        return isSame(newNode);
    }

    protected boolean isIdentical(SpecializationNode newNode, Frame frame, Object o1, Object o2) {
        return isSame(newNode);
    }

    protected boolean isIdentical(SpecializationNode newNode, Frame frame, Object o1, Object o2, Object o3) {
        return isSame(newNode);
    }

    protected boolean isIdentical(SpecializationNode newNode, Frame frame, Object o1, Object o2, Object o3, Object o4) {
        return isSame(newNode);
    }

    protected boolean isIdentical(SpecializationNode newNode, Frame frame, Object o1, Object o2, Object o3, Object o4, Object o5) {
        return isSame(newNode);
    }

    protected boolean isIdentical(SpecializationNode newNode, Frame frame, Object... args) {
        return isSame(newNode);
    }

    protected final int countSame(SpecializationNode node) {
        return findStart().countSameImpl(node);
    }

    private int countSameImpl(SpecializationNode node) {
        if (next != null) {
            return next.countSameImpl(node) + (isSame(node) ? 1 : 0);
        } else {
            return 0;
        }
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof SpecializationNode) {
            return ((SpecializationNode) obj).isSame(this);
        }
        return super.equals(obj);
    }

    @Override
    public final int hashCode() {
        return index;
    }

    private int count() {
        return next != null ? next.count() + 1 : 1;
    }

    private SpecializationNode findEnd() {
        SpecializationNode node = this;
        while (node.next != null) {
            node = node.next;
        }
        return node;
    }

    protected final Object removeThis(final CharSequence reason, Frame frame) {
        return removeThisImpl(reason).acceptAndExecute(frame);
    }

    protected final Object removeThis(final CharSequence reason, Frame frame, Object o1) {
        return removeThisImpl(reason).acceptAndExecute(frame, o1);
    }

    protected final Object removeThis(final CharSequence reason, Frame frame, Object o1, Object o2) {
        return removeThisImpl(reason).acceptAndExecute(frame, o1, o2);
    }

    protected final Object removeThis(final CharSequence reason, Frame frame, Object o1, Object o2, Object o3) {
        return removeThisImpl(reason).acceptAndExecute(frame, o1, o2, o3);
    }

    protected final Object removeThis(final CharSequence reason, Frame frame, Object o1, Object o2, Object o3, Object o4) {
        return removeThisImpl(reason).acceptAndExecute(frame, o1, o2, o3, o4);
    }

    protected final Object removeThis(final CharSequence reason, Frame frame, Object o1, Object o2, Object o3, Object o4, Object o5) {
        return removeThisImpl(reason).acceptAndExecute(frame, o1, o2, o3, o4, o5);
    }

    protected final Object removeThis(final CharSequence reason, Frame frame, Object... args) {
        return removeThisImpl(reason).acceptAndExecute(frame, args);
    }

    private SpecializationNode removeThisImpl(final CharSequence reason) {
        this.replace(this.next, reason);
        return findEnd().findStart();
    }

    protected final SpecializationNode removeSame(final CharSequence reason) {
        return atomic(new Callable<SpecializationNode>() {
            public SpecializationNode call() throws Exception {
                return removeSameImpl(SpecializationNode.this, reason);
            }
        });
    }

    /** Find the topmost of the specialization chain. */
    private SpecializationNode findStart() {
        SpecializationNode node = this;
        Node parent = this.getParent();
        while (parent instanceof SpecializationNode) {
            SpecializationNode parentCast = ((SpecializationNode) parent);
            if (parentCast.next != node) {
                break;
            }
            node = parentCast;
            parent = node.getParent();
        }
        return node;
    }

    private Node findRoot() {
        return findStart().getParent();
    }

    private SpecializedNode findSpecializedNode() {
        return (SpecializedNode) findEnd().findStart().getParent();
    }

    private static SpecializationNode removeSameImpl(SpecializationNode toRemove, CharSequence reason) {
        SpecializationNode start = toRemove.findStart();
        SpecializationNode current = start;
        while (current != null) {
            if (current.isSame(toRemove)) {
                NodeUtil.nonAtomicReplace(current, current.next, reason);
                if (current == start) {
                    start = start.next;
                }
            }
            current = current.next;
        }
        return toRemove.findEnd().findStart();
    }

    public Object acceptAndExecute(Frame frame) {
        throw new UnsupportedOperationException();
    }

    public Object acceptAndExecute(Frame frame, Object o1) {
        throw new UnsupportedOperationException();
    }

    public Object acceptAndExecute(Frame frame, Object o1, Object o2) {
        throw new UnsupportedOperationException();
    }

    public Object acceptAndExecute(Frame frame, Object o1, Object o2, Object o3) {
        throw new UnsupportedOperationException();
    }

    public Object acceptAndExecute(Frame frame, Object o1, Object o2, Object o3, Object o4) {
        throw new UnsupportedOperationException();
    }

    public Object acceptAndExecute(Frame frame, Object o1, Object o2, Object o3, Object o4, Object o5) {
        throw new UnsupportedOperationException();
    }

    public Object acceptAndExecute(Frame frame, Object... args) {
        throw new UnsupportedOperationException();
    }

    protected SpecializationNode createFallback() {
        return null;
    }

    protected SpecializationNode createPolymorphic() {
        return null;
    }

    protected SpecializationNode createNext(Frame frame) {
        throw new UnsupportedOperationException();
    }

    protected SpecializationNode createNext(Frame frame, Object o1) {
        throw new UnsupportedOperationException();
    }

    protected SpecializationNode createNext(Frame frame, Object o1, Object o2) {
        throw new UnsupportedOperationException();
    }

    protected SpecializationNode createNext(Frame frame, Object o1, Object o2, Object o3) {
        throw new UnsupportedOperationException();
    }

    protected SpecializationNode createNext(Frame frame, Object o1, Object o2, Object o3, Object o4) {
        throw new UnsupportedOperationException();
    }

    protected SpecializationNode createNext(Frame frame, Object o1, Object o2, Object o3, Object o4, Object o5) {
        throw new UnsupportedOperationException();
    }

    protected SpecializationNode createNext(Frame frame, Object... args) {
        throw new UnsupportedOperationException();
    }

    protected final Object uninitialized(Frame frame) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        SpecializationNode nextSpecialization = createNext(frame);
        if (nextSpecialization == null) {
            nextSpecialization = createFallback();
        }
        if (nextSpecialization == null) {
            return unsupported(frame);
        }
        return atomic(new InsertionEvent0(this, "insert new specialization", frame, nextSpecialization)).acceptAndExecute(frame);
    }

    protected final Object uninitialized(Frame frame, Object o1) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        SpecializationNode nextSpecialization = createNext(frame, o1);
        if (nextSpecialization == null) {
            nextSpecialization = createFallback();
        }
        if (nextSpecialization == null) {
            return unsupported(frame, o1);
        }
        return atomic(new InsertionEvent1(this, "insert new specialization", frame, o1, nextSpecialization)).acceptAndExecute(frame, o1);
    }

    protected final Object uninitialized(Frame frame, Object o1, Object o2) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        SpecializationNode nextSpecialization = createNext(frame, o1, o2);
        if (nextSpecialization == null) {
            nextSpecialization = createFallback();
        }
        if (nextSpecialization == null) {
            return unsupported(frame, o1, o2);
        }
        return atomic(new InsertionEvent2(this, "insert new specialization", frame, o1, o2, nextSpecialization)).acceptAndExecute(frame, o1, o2);
    }

    protected final Object uninitialized(Frame frame, Object o1, Object o2, Object o3) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        SpecializationNode nextSpecialization = createNext(frame, o1, o2, o3);
        if (nextSpecialization == null) {
            nextSpecialization = createFallback();
        }
        if (nextSpecialization == null) {
            return unsupported(frame, o1, o2, o3);
        }
        return atomic(new InsertionEvent3(this, "insert new specialization", frame, o1, o2, o3, nextSpecialization)).acceptAndExecute(frame, o1, o2, o3);
    }

    protected final Object uninitialized(Frame frame, Object o1, Object o2, Object o3, Object o4) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        SpecializationNode nextSpecialization = createNext(frame, o1, o2, o3, o4);
        if (nextSpecialization == null) {
            nextSpecialization = createFallback();
        }
        if (nextSpecialization == null) {
            return unsupported(frame, o1, o2, o3, o4);
        }
        return atomic(new InsertionEvent4(this, "insert new specialization", frame, o1, o2, o3, o4, nextSpecialization)).acceptAndExecute(frame, o1, o2, o3, o4);
    }

    protected final Object uninitialized(Frame frame, Object o1, Object o2, Object o3, Object o4, Object o5) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        SpecializationNode nextSpecialization = createNext(frame, o1, o2, o3, o4, o5);
        if (nextSpecialization == null) {
            nextSpecialization = createFallback();
        }
        if (nextSpecialization == null) {
            unsupported(frame, o1, o2, o3, o4, o5);
        }
        return atomic(new InsertionEvent5(this, "insert new specialization", frame, o1, o2, o3, o4, o5, nextSpecialization)).acceptAndExecute(frame, o1, o2, o3, o4, o5);
    }

    protected final Object uninitialized(Frame frame, Object... args) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        SpecializationNode nextSpecialization = createNext(frame, args);
        if (nextSpecialization == null) {
            nextSpecialization = createFallback();
        }
        if (nextSpecialization == null) {
            unsupported(frame, args);
        }
        return atomic(new InsertionEventN(this, "insert new specialization", frame, args, nextSpecialization)).acceptAndExecute(frame, args);
    }

    protected final Object remove(String reason, Frame frame) {
        return atomic(new RemoveEvent0(this, reason, frame)).acceptAndExecute(frame);
    }

    protected final Object remove(String reason, Frame frame, Object o1) {
        return atomic(new RemoveEvent1(this, reason, frame, o1)).acceptAndExecute(frame, o1);
    }

    protected final Object remove(String reason, Frame frame, Object o1, Object o2) {
        return atomic(new RemoveEvent2(this, reason, frame, o1, o2)).acceptAndExecute(frame, o1, o2);
    }

    protected final Object remove(String reason, Frame frame, Object o1, Object o2, Object o3) {
        return atomic(new RemoveEvent3(this, reason, frame, o1, o2, o3)).acceptAndExecute(frame, o1, o2, o3);
    }

    protected final Object remove(String reason, Frame frame, Object o1, Object o2, Object o3, Object o4) {
        return atomic(new RemoveEvent4(this, reason, frame, o1, o2, o3, o4)).acceptAndExecute(frame, o1, o2, o3, o4);
    }

    protected final Object remove(String reason, Frame frame, Object o1, Object o2, Object o3, Object o4, Object o5) {
        return atomic(new RemoveEvent5(this, reason, frame, o1, o2, o3, o4, o5)).acceptAndExecute(frame, o1, o2, o3, o4, o5);
    }

    protected final Object remove(String reason, Frame frame, Object... args) {
        return atomic(new RemoveEventN(this, reason, frame, args)).acceptAndExecute(frame, args);
    }

    protected Object unsupported(Frame frame) {
        throw new UnsupportedSpecializationException(findRoot(), getSuppliedChildren());
    }

    protected Object unsupported(Frame frame, Object o1) {
        throw new UnsupportedSpecializationException(findRoot(), getSuppliedChildren(), o1);
    }

    protected Object unsupported(Frame frame, Object o1, Object o2) {
        throw new UnsupportedSpecializationException(findRoot(), getSuppliedChildren(), o1, o2);
    }

    protected Object unsupported(Frame frame, Object o1, Object o2, Object o3) {
        throw new UnsupportedSpecializationException(findRoot(), getSuppliedChildren(), o1, o2, o3);
    }

    protected Object unsupported(Frame frame, Object o1, Object o2, Object o3, Object o4) {
        throw new UnsupportedSpecializationException(findRoot(), getSuppliedChildren(), o1, o2, o3, o4);
    }

    protected Object unsupported(Frame frame, Object o1, Object o2, Object o3, Object o4, Object o5) {
        throw new UnsupportedSpecializationException(findRoot(), getSuppliedChildren(), o1, o2, o3, o4, o5);
    }

    protected Object unsupported(Frame frame, Object... args) {
        throw new UnsupportedSpecializationException(findRoot(), getSuppliedChildren(), args);
    }

    static SpecializationNode insertSorted(SpecializationNode start, final SpecializationNode generated, final CharSequence message, final SpecializationNode merged) {
        if (merged == generated) {
            // new node
            if (start.count() == 2) {
                insertAt(start, start.createPolymorphic(), "insert polymorphic");
            }
            SpecializationNode current = start;
            while (current != null && current.index < generated.index) {
                current = current.next;
            }
            return insertAt(current, generated, message);
        } else {
            // existing node
            return start;
        }
    }

    static <T> SpecializationNode insertAt(SpecializationNode node, SpecializationNode insertBefore, CharSequence reason) {
        insertBefore.next = node;
        // always guaranteed to be executed inside of an atomic block
        return NodeUtil.nonAtomicReplace(node, insertBefore, reason);
    }

    @Override
    public final String toString() {
        Class<?> clazz = getClass();
        StringBuilder b = new StringBuilder();
        b.append(clazz.getSimpleName());

        appendFields(b, clazz);
        if (next != null) {
            b.append("\n -> ").append(next.toString());
        }
        return b.toString();
    }

    private void appendFields(StringBuilder b, Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        if (fields.length == 0) {
            return;
        }
        b.append("(");
        String sep = "";
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            b.append(sep);
            String name = field.getName();
            if (name.equals("root")) {
                continue;
            }
            b.append(field.getName());
            b.append(" = ");
            try {
                field.setAccessible(true);
                Object value = field.get(this);
                if (value instanceof Object[]) {
                    b.append(Arrays.toString((Object[]) field.get(this)));
                } else {
                    b.append(field.get(this));
                }
            } catch (IllegalArgumentException e) {
                b.append(e.toString());
            } catch (IllegalAccessException e) {
                b.append(e.toString());
            }
            sep = ", ";
        }
        b.append(")");
    }

    protected static void check(Assumption assumption) throws InvalidAssumptionException {
        if (assumption != null) {
            assumption.check();
        }
    }

    @ExplodeLoop
    protected static void check(Assumption[] assumptions) throws InvalidAssumptionException {
        if (assumptions != null) {
            CompilerAsserts.compilationConstant(assumptions.length);
            for (Assumption assumption : assumptions) {
                check(assumption);
            }
        }
    }

    protected static boolean isValid(Assumption assumption) {
        if (assumption != null) {
            return assumption.isValid();
        }
        return true;
    }

    protected static boolean isValid(Assumption[] assumptions) {
        if (assumptions != null) {
            for (Assumption assumption : assumptions) {
                if (!isValid(assumption)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static final class InsertionEvent0 extends SlowPathEvent0 implements Callable<SpecializationNode> {

        private final SpecializationNode next;

        public InsertionEvent0(SpecializationNode source, String reason, Frame frame, SpecializationNode next) {
            super(source, reason, frame);
            this.next = next;
        }

        public SpecializationNode call() throws Exception {
            SpecializationNode start = source.findStart();
            if (start.index == Integer.MAX_VALUE) {
                return insertAt(start, next, this);
            } else {
                return insertSorted(start, next, this, start.merge(next, frame));
            }
        }

    }

    private static final class InsertionEvent1 extends SlowPathEvent1 implements Callable<SpecializationNode> {

        private final SpecializationNode next;

        public InsertionEvent1(SpecializationNode source, String reason, Frame frame, Object o1, SpecializationNode next) {
            super(source, reason, frame, o1);
            this.next = next;
        }

        public SpecializationNode call() throws Exception {
            SpecializationNode start = source.findStart();
            if (start.index == Integer.MAX_VALUE) {
                return insertAt(start, next, this);
            } else {
                return insertSorted(start, next, this, start.merge(next, frame, o1));
            }
        }

    }

    private static final class InsertionEvent2 extends SlowPathEvent2 implements Callable<SpecializationNode> {

        private final SpecializationNode next;

        public InsertionEvent2(SpecializationNode source, String reason, Frame frame, Object o1, Object o2, SpecializationNode next) {
            super(source, reason, frame, o1, o2);
            this.next = next;
        }

        public SpecializationNode call() throws Exception {
            SpecializationNode start = source.findStart();
            if (start.index == Integer.MAX_VALUE) {
                return insertAt(start, next, this);
            } else {
                return insertSorted(start, next, this, start.merge(next, frame, o1, o2));
            }
        }

    }

    private static final class InsertionEvent3 extends SlowPathEvent3 implements Callable<SpecializationNode> {

        private final SpecializationNode next;

        public InsertionEvent3(SpecializationNode source, String reason, Frame frame, Object o1, Object o2, Object o3, SpecializationNode next) {
            super(source, reason, frame, o1, o2, o3);
            this.next = next;
        }

        public SpecializationNode call() throws Exception {
            SpecializationNode start = source.findStart();
            if (start.index == Integer.MAX_VALUE) {
                return insertAt(start, next, this);
            } else {
                return insertSorted(start, next, this, start.merge(next, frame, o1, o2, o3));
            }
        }

    }

    private static final class InsertionEvent4 extends SlowPathEvent4 implements Callable<SpecializationNode> {

        private final SpecializationNode next;

        public InsertionEvent4(SpecializationNode source, String reason, Frame frame, Object o1, Object o2, Object o3, Object o4, SpecializationNode next) {
            super(source, reason, frame, o1, o2, o3, o4);
            this.next = next;
        }

        public SpecializationNode call() throws Exception {
            SpecializationNode start = source.findStart();
            if (start.index == Integer.MAX_VALUE) {
                return insertAt(start, next, this);
            } else {
                return insertSorted(start, next, this, start.merge(next, frame, o1, o2, o3, o4));
            }
        }

    }

    private static final class InsertionEvent5 extends SlowPathEvent5 implements Callable<SpecializationNode> {

        private final SpecializationNode next;

        public InsertionEvent5(SpecializationNode source, String reason, Frame frame, Object o1, Object o2, Object o3, Object o4, Object o5, SpecializationNode next) {
            super(source, reason, frame, o1, o2, o3, o4, o5);
            this.next = next;
        }

        public SpecializationNode call() throws Exception {
            SpecializationNode start = source.findStart();
            if (start.index == Integer.MAX_VALUE) {
                return insertAt(start, next, this);
            } else {
                return insertSorted(start, next, this, start.merge(next, frame, o1, o2, o3, o4, o5));
            }
        }

    }

    private static final class InsertionEventN extends SlowPathEventN implements Callable<SpecializationNode> {

        private final SpecializationNode next;

        public InsertionEventN(SpecializationNode source, String reason, Frame frame, Object[] args, SpecializationNode next) {
            super(source, reason, frame, args);
            this.next = next;
        }

        public SpecializationNode call() throws Exception {
            SpecializationNode start = source.findStart();
            if (start.index == Integer.MAX_VALUE) {
                return insertAt(start, next, this);
            } else {
                return insertSorted(start, next, this, start.merge(next, frame, args));
            }
        }
    }

    private static final class RemoveEvent0 extends SlowPathEvent0 implements Callable<SpecializationNode> {

        public RemoveEvent0(SpecializationNode source, String reason, Frame frame) {
            super(source, reason, frame);
        }

        public SpecializationNode call() throws Exception {
            return removeSameImpl(source, this);
        }

    }

    private static final class RemoveEvent1 extends SlowPathEvent1 implements Callable<SpecializationNode> {

        public RemoveEvent1(SpecializationNode source, String reason, Frame frame, Object o1) {
            super(source, reason, frame, o1);
        }

        public SpecializationNode call() throws Exception {
            return removeSameImpl(source, this);
        }

    }

    private static final class RemoveEvent2 extends SlowPathEvent2 implements Callable<SpecializationNode> {

        public RemoveEvent2(SpecializationNode source, String reason, Frame frame, Object o1, Object o2) {
            super(source, reason, frame, o1, o2);
        }

        public SpecializationNode call() throws Exception {
            return removeSameImpl(source, this);
        }

    }

    private static final class RemoveEvent3 extends SlowPathEvent3 implements Callable<SpecializationNode> {

        public RemoveEvent3(SpecializationNode source, String reason, Frame frame, Object o1, Object o2, Object o3) {
            super(source, reason, frame, o1, o2, o3);
        }

        public SpecializationNode call() throws Exception {
            return removeSameImpl(source, this);
        }

    }

    private static final class RemoveEvent4 extends SlowPathEvent4 implements Callable<SpecializationNode> {

        public RemoveEvent4(SpecializationNode source, String reason, Frame frame, Object o1, Object o2, Object o3, Object o4) {
            super(source, reason, frame, o1, o2, o3, o4);
        }

        public SpecializationNode call() throws Exception {
            return removeSameImpl(source, this);
        }

    }

    private static final class RemoveEvent5 extends SlowPathEvent5 implements Callable<SpecializationNode> {

        public RemoveEvent5(SpecializationNode source, String reason, Frame frame, Object o1, Object o2, Object o3, Object o4, Object o5) {
            super(source, reason, frame, o1, o2, o3, o4, o5);
        }

        public SpecializationNode call() throws Exception {
            return removeSameImpl(source, this);
        }

    }

    private static final class RemoveEventN extends SlowPathEventN implements Callable<SpecializationNode> {

        public RemoveEventN(SpecializationNode source, String reason, Frame frame, Object[] args) {
            super(source, reason, frame, args);
        }

        public SpecializationNode call() throws Exception {
            return removeSameImpl(source, this);
        }
    }

}
