/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.core;

import java.util.*;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.core.array.*;
import com.oracle.truffle.ruby.runtime.core.range.*;
import com.oracle.truffle.ruby.runtime.objects.*;

@CoreClass(name = "Array")
public abstract class ArrayNodes {

    @CoreMethod(names = "+", minArgs = 1, maxArgs = 1)
    public abstract static class AddNode extends CoreMethodNode {

        public AddNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public AddNode(AddNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray equal(RubyArray a, RubyArray b) {
            final RubyArray result = new RubyArray(getContext().getCoreLibrary().getArrayClass());
            result.setRangeArrayExclusive(0, 0, a);
            result.setRangeArrayExclusive(a.size(), a.size(), b);
            return result;
        }

    }

    @CoreMethod(names = "-", minArgs = 1, maxArgs = 1)
    public abstract static class SubNode extends CoreMethodNode {

        public SubNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public SubNode(SubNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray equal(RubyArray a, RubyArray b) {
            return a.relativeComplement(b);
        }

    }

    @CoreMethod(names = "*", minArgs = 1, maxArgs = 1)
    public abstract static class MulNode extends CoreMethodNode {

        public MulNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public MulNode(MulNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray mul(RubyArray array, int count) {
            // TODO(CS): use the same storage type

            final RubyArray result = new RubyArray(array.getRubyClass().getContext().getCoreLibrary().getArrayClass());

            for (int n = 0; n < count; n++) {
                for (int i = 0; i < array.size(); i++) {
                    result.push(array.get(i));
                }
            }

            return result;
        }

    }

    @CoreMethod(names = "==", minArgs = 1, maxArgs = 1)
    public abstract static class EqualNode extends CoreMethodNode {

        public EqualNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public EqualNode(EqualNode prev) {
            super(prev);
        }

        @Specialization
        public boolean equal(RubyArray a, RubyArray b) {
            // TODO(CS)
            return a.equals(b);
        }

    }

    @CoreMethod(names = "[]", minArgs = 1, maxArgs = 2)
    public abstract static class IndexNode extends ArrayCoreMethodNode {

        public IndexNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public IndexNode(IndexNode prev) {
            super(prev);
        }

        @Specialization(guards = "isEmptyStore", order = 1)
        public NilPlaceholder indexEmpty(@SuppressWarnings("unused") RubyArray array, @SuppressWarnings("unused") int index, @SuppressWarnings("unused") UndefinedPlaceholder unused) {
            return NilPlaceholder.INSTANCE;
        }

        @Specialization(guards = "isFixnumStore", rewriteOn = UnexpectedResultException.class, order = 2)
        public int indexFixnum(RubyArray array, int index, @SuppressWarnings("unused") UndefinedPlaceholder unused) throws UnexpectedResultException {
            final FixnumArrayStore store = (FixnumArrayStore) array.getArrayStore();
            return store.getFixnum(ArrayUtilities.normaliseIndex(store.size(), index));
        }

        @Specialization(guards = "isFixnumStore", order = 3)
        public Object indexMaybeFixnum(RubyArray array, int index, @SuppressWarnings("unused") UndefinedPlaceholder unused) {
            final FixnumArrayStore store = (FixnumArrayStore) array.getArrayStore();

            try {
                return store.getFixnum(ArrayUtilities.normaliseIndex(store.size(), index));
            } catch (UnexpectedResultException e) {
                return e.getResult();
            }
        }

        @Specialization(guards = "isFixnumImmutablePairStore", rewriteOn = UnexpectedResultException.class, order = 4)
        public int indexFixnumImmutablePair(RubyArray array, int index, @SuppressWarnings("unused") UndefinedPlaceholder unused) throws UnexpectedResultException {
            final FixnumImmutablePairArrayStore store = (FixnumImmutablePairArrayStore) array.getArrayStore();
            return store.getFixnum(ArrayUtilities.normaliseIndex(store.size(), index));
        }

        @Specialization(guards = "isFixnumImmutablePairStore", order = 5)
        public Object indexMaybeFixnumImmutablePair(RubyArray array, int index, @SuppressWarnings("unused") UndefinedPlaceholder unused) {
            final FixnumImmutablePairArrayStore store = (FixnumImmutablePairArrayStore) array.getArrayStore();

            try {
                return store.getFixnum(ArrayUtilities.normaliseIndex(store.size(), index));
            } catch (UnexpectedResultException e) {
                return e.getResult();
            }
        }

        @Specialization(guards = "isObjectStore", order = 6)
        public Object indexObject(RubyArray array, int index, @SuppressWarnings("unused") UndefinedPlaceholder unused) {
            final ObjectArrayStore store = (ObjectArrayStore) array.getArrayStore();
            return store.get(ArrayUtilities.normaliseIndex(store.size(), index));
        }

        @Specialization(guards = "isObjectImmutablePairStore", order = 7)
        public Object indexObjectImmutablePair(RubyArray array, int index, @SuppressWarnings("unused") UndefinedPlaceholder unused) {
            final ObjectImmutablePairArrayStore store = (ObjectImmutablePairArrayStore) array.getArrayStore();
            return store.get(ArrayUtilities.normaliseIndex(store.size(), index));
        }

        @Specialization(order = 8)
        public Object indexRange(RubyArray array, int begin, int rangeLength) {
            final int length = array.size();
            final int normalisedBegin = ArrayUtilities.normaliseIndex(length, begin);
            return array.getRangeExclusive(normalisedBegin, normalisedBegin + rangeLength);
        }

        @Specialization(order = 9)
        public Object indexRange(RubyArray array, FixnumRange range, @SuppressWarnings("unused") UndefinedPlaceholder unused) {
            if (range.doesExcludeEnd()) {
                return array.getRangeExclusive(range.getBegin(), range.getExclusiveEnd());
            } else {
                return array.getRangeInclusive(range.getBegin(), range.getInclusiveEnd());
            }
        }

    }

    @CoreMethod(names = "[]=", minArgs = 2, maxArgs = 3)
    public abstract static class IndexSetNode extends ArrayCoreMethodNode {

        public IndexSetNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public IndexSetNode(IndexSetNode prev) {
            super(prev);
        }

        @Specialization(guards = "isEmptyStore", order = 1)
        public Object indexSetEmpty(RubyArray array, int index, Object value, @SuppressWarnings("unused") UndefinedPlaceholder unused) {
            array.set(index, value);
            return value;
        }

        @Specialization(guards = "isFixnumStore", rewriteOn = GeneraliseArrayStoreException.class, order = 2)
        public int indexSetFixnum(RubyArray array, int index, int value, @SuppressWarnings("unused") UndefinedPlaceholder unused) throws GeneraliseArrayStoreException {
            final FixnumArrayStore store = (FixnumArrayStore) array.getArrayStore();
            final int normalisedIndex = ArrayUtilities.normaliseIndex(store.size(), index);
            store.setFixnum(normalisedIndex, value);
            return value;
        }

        @Specialization(guards = "isFixnumStore", order = 3)
        public int indexSetFixnumMayGeneralise(RubyArray array, int index, int value, @SuppressWarnings("unused") UndefinedPlaceholder unused) {
            final FixnumArrayStore store = (FixnumArrayStore) array.getArrayStore();
            final int normalisedIndex = ArrayUtilities.normaliseIndex(store.size(), index);

            try {
                store.setFixnum(normalisedIndex, value);
            } catch (GeneraliseArrayStoreException e) {
                array.set(normalisedIndex, value);
            }

            return value;
        }

        @Specialization(guards = "isObjectStore", order = 4)
        public Object indexSetObject(RubyArray array, int index, Object value, @SuppressWarnings("unused") UndefinedPlaceholder unused) {
            final ObjectArrayStore store = (ObjectArrayStore) array.getArrayStore();
            final int normalisedIndex = ArrayUtilities.normaliseIndex(store.size(), index);

            try {
                store.set(normalisedIndex, value);
            } catch (GeneraliseArrayStoreException e) {
                array.set(normalisedIndex, value);
            }

            return value;
        }

        @Specialization(order = 5)
        public RubyArray indexSetRange(RubyArray array, FixnumRange range, RubyArray value, @SuppressWarnings("unused") UndefinedPlaceholder unused) {
            if (range.doesExcludeEnd()) {
                array.setRangeArrayExclusive(range.getBegin(), range.getExclusiveEnd(), value);
            } else {
                array.setRangeArrayInclusive(range.getBegin(), range.getInclusiveEnd(), value);
            }

            return value;
        }

        @Specialization(order = 6)
        public Object indexSetRange(RubyArray array, FixnumRange range, Object value, @SuppressWarnings("unused") UndefinedPlaceholder unused) {
            if (range.doesExcludeEnd()) {
                array.setRangeSingleExclusive(range.getBegin(), range.getExclusiveEnd(), value);
            } else {
                array.setRangeSingleInclusive(range.getBegin(), range.getInclusiveEnd(), value);
            }

            return value;
        }

        @Specialization(order = 7)
        public RubyArray indexSetRange(RubyArray array, int begin, int rangeLength, RubyArray value) {
            array.setRangeArrayExclusive(begin, begin + rangeLength, value);
            return value;
        }

        @Specialization(order = 8)
        public Object indexSetRange(RubyArray array, int begin, int rangeLength, Object value) {
            if (value instanceof UndefinedPlaceholder) {
                if (array.getArrayStore() instanceof EmptyArrayStore) {
                    return indexSetEmpty(array, begin, rangeLength, UndefinedPlaceholder.INSTANCE);
                } else if (array.getArrayStore() instanceof FixnumArrayStore) {
                    return indexSetFixnumMayGeneralise(array, begin, rangeLength, UndefinedPlaceholder.INSTANCE);
                } else {
                    return indexSetObject(array, begin, rangeLength, UndefinedPlaceholder.INSTANCE);
                }
            }

            array.setRangeSingleExclusive(begin, begin + rangeLength, value);
            return value;
        }

    }

    @CoreMethod(names = "all?", needsBlock = true, maxArgs = 0)
    public abstract static class AllNode extends YieldingCoreMethodNode {

        public AllNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public AllNode(AllNode prev) {
            super(prev);
        }

        @Specialization
        public boolean all(VirtualFrame frame, RubyArray array, RubyProc block) {
            for (Object value : array.asList()) {
                if (!yieldBoolean(frame, block, value)) {
                    return false;
                }
            }

            return true;
        }

    }

    @CoreMethod(names = "any?", needsBlock = true, maxArgs = 0)
    public abstract static class AnyNode extends YieldingCoreMethodNode {

        public AnyNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public AnyNode(AnyNode prev) {
            super(prev);
        }

        @Specialization
        public boolean any(VirtualFrame frame, RubyArray array, RubyProc block) {
            for (Object value : array.asList()) {
                if (yieldBoolean(frame, block, value)) {
                    return true;
                }
            }

            return false;
        }

    }

    @CoreMethod(names = "compact", maxArgs = 0)
    public abstract static class CompactNode extends ArrayCoreMethodNode {

        public CompactNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public CompactNode(CompactNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray compat(RubyArray array) {
            final RubyArray result = new RubyArray(array.getRubyClass().getContext().getCoreLibrary().getArrayClass());

            for (Object value : array.asList()) {
                if (!(value instanceof NilPlaceholder || value instanceof RubyNilClass)) {
                    result.push(value);
                }
            }

            return result;
        }

    }

    @CoreMethod(names = "concat", minArgs = 1, maxArgs = 1)
    public abstract static class ConcatNode extends CoreMethodNode {

        public ConcatNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ConcatNode(ConcatNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray concat(RubyArray array, RubyArray other) {
            array.setRangeArrayExclusive(array.size(), array.size(), other);
            return array;
        }

    }

    @CoreMethod(names = "delete", minArgs = 1, maxArgs = 1)
    public abstract static class DeleteNode extends CoreMethodNode {

        public DeleteNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public DeleteNode(DeleteNode prev) {
            super(prev);
        }

        @Specialization
        public Object delete(RubyArray array, Object value) {
            boolean deleted = false;
            int n = 0;

            while (n < array.size()) {
                if (array.get(n) == value) {
                    array.deleteAt(n);
                    deleted = true;
                } else {
                    n++;
                }
            }

            if (deleted) {
                return value;
            } else {
                return NilPlaceholder.INSTANCE;
            }
        }

    }

    @CoreMethod(names = "delete_at", minArgs = 1, maxArgs = 1)
    public abstract static class DeleteAtNode extends CoreMethodNode {

        public DeleteAtNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public DeleteAtNode(DeleteAtNode prev) {
            super(prev);
        }

        @Specialization
        public Object deleteAt(RubyArray array, int index) {
            return array.deleteAt(index);
        }

    }

    @CoreMethod(names = "dup", maxArgs = 0)
    public abstract static class DupNode extends ArrayCoreMethodNode {

        public DupNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public DupNode(DupNode prev) {
            super(prev);
        }

        @Specialization
        public Object dup(RubyArray array) {
            return array.dup();
        }

    }

    @CoreMethod(names = "each", needsBlock = true, maxArgs = 0)
    public abstract static class EachNode extends YieldingCoreMethodNode {

        private final BranchProfile breakProfile = new BranchProfile();
        private final BranchProfile nextProfile = new BranchProfile();
        private final BranchProfile redoProfile = new BranchProfile();

        public EachNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public EachNode(EachNode prev) {
            super(prev);
        }

        @Specialization
        public Object each(VirtualFrame frame, RubyArray array, RubyProc block) {
            outer: for (int n = 0; n < array.size(); n++) {
                while (true) {
                    try {
                        yield(frame, block, array.get(n));
                        continue outer;
                    } catch (BreakException e) {
                        breakProfile.enter();
                        return e.getResult();
                    } catch (NextException e) {
                        nextProfile.enter();
                        continue outer;
                    } catch (RedoException e) {
                        redoProfile.enter();
                    }
                }
            }

            return NilPlaceholder.INSTANCE;
        }

    }

    @CoreMethod(names = "each_with_index", needsBlock = true, maxArgs = 0)
    public abstract static class EachWithIndexNode extends YieldingCoreMethodNode {

        public EachWithIndexNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public EachWithIndexNode(EachWithIndexNode prev) {
            super(prev);
        }

        @Specialization
        public NilPlaceholder eachWithIndex(VirtualFrame frame, RubyArray array, RubyProc block) {
            for (int n = 0; n < array.size(); n++) {
                try {
                    yield(frame, block, array.get(n), n);
                } catch (BreakException e) {
                    break;
                }
            }

            return NilPlaceholder.INSTANCE;
        }

    }

    @CoreMethod(names = "empty?", maxArgs = 0)
    public abstract static class EmptyNode extends ArrayCoreMethodNode {

        public EmptyNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public EmptyNode(EmptyNode prev) {
            super(prev);
        }

        @Specialization
        public boolean isEmpty(RubyArray array) {
            return array.isEmpty();
        }

    }

    @CoreMethod(names = "find", needsBlock = true, maxArgs = 0)
    public abstract static class FindNode extends YieldingCoreMethodNode {

        public FindNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public FindNode(FindNode prev) {
            super(prev);
        }

        @Specialization
        public Object find(VirtualFrame frame, RubyArray array, RubyProc block) {
            for (int n = 0; n < array.size(); n++) {
                try {
                    final Object value = array.get(n);

                    if (yieldBoolean(frame, block, value)) {
                        return value;
                    }
                } catch (BreakException e) {
                    break;
                }
            }

            return NilPlaceholder.INSTANCE;
        }
    }

    @CoreMethod(names = "first", maxArgs = 0)
    public abstract static class FirstNode extends ArrayCoreMethodNode {

        public FirstNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public FirstNode(FirstNode prev) {
            super(prev);
        }

        @Specialization
        public Object first(RubyArray array) {
            if (array.size() == 0) {
                return NilPlaceholder.INSTANCE;
            } else {
                return array.get(0);
            }
        }

    }

    @CoreMethod(names = "flatten", maxArgs = 0)
    public abstract static class FlattenNode extends ArrayCoreMethodNode {

        public FlattenNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public FlattenNode(FlattenNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray flatten(RubyArray array) {
            final RubyArray result = new RubyArray(array.getRubyClass().getContext().getCoreLibrary().getArrayClass());
            array.flattenTo(result);
            return result;
        }

    }

    @CoreMethod(names = "include?", minArgs = 1, maxArgs = 1)
    public abstract static class IncludeNode extends ArrayCoreMethodNode {

        public IncludeNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public IncludeNode(IncludeNode prev) {
            super(prev);
        }

        @Specialization
        public boolean include(RubyArray array, Object value) {
            return array.contains(value);
        }

    }

    @CoreMethod(names = {"inject", "reduce"}, needsBlock = true, minArgs = 0, maxArgs = 1)
    public abstract static class InjectNode extends YieldingCoreMethodNode {

        public InjectNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public InjectNode(InjectNode prev) {
            super(prev);
        }

        @Specialization
        public Object inject(VirtualFrame frame, RubyArray array, @SuppressWarnings("unused") UndefinedPlaceholder initial, RubyProc block) {
            Object accumulator = array.get(0);

            for (int n = 1; n < array.size(); n++) {
                accumulator = yield(frame, block, accumulator, array.get(n));
            }

            return accumulator;
        }

        @Specialization
        public Object inject(VirtualFrame frame, RubyArray array, Object initial, RubyProc block) {
            if (initial instanceof UndefinedPlaceholder) {
                return inject(frame, array, UndefinedPlaceholder.INSTANCE, block);
            }

            Object accumulator = initial;

            for (int n = 0; n < array.size(); n++) {
                accumulator = yield(frame, block, accumulator, array.get(n));
            }

            return accumulator;
        }

    }

    @CoreMethod(names = "insert", minArgs = 2, maxArgs = 2)
    public abstract static class InsertNode extends ArrayCoreMethodNode {

        public InsertNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public InsertNode(InsertNode prev) {
            super(prev);
        }

        @Specialization(guards = "isFixnumStore", rewriteOn = GeneraliseArrayStoreException.class)
        public int insert(RubyArray array, int index, int value) throws GeneraliseArrayStoreException {
            final FixnumArrayStore store = (FixnumArrayStore) array.getArrayStore();
            store.insertFixnum(ArrayUtilities.normaliseIndex(store.size(), index), value);
            return value;
        }

        @Specialization
        public Object insert(RubyArray array, int index, Object value) {
            array.insert(ArrayUtilities.normaliseIndex(array.size(), index), value);
            return value;
        }

    }

    @CoreMethod(names = {"inspect", "to_s"}, maxArgs = 0)
    public abstract static class InspectNode extends CoreMethodNode {

        public InspectNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public InspectNode(InspectNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString inspect(RubyArray array) {
            final RubyContext context = getContext();
            return getContext().makeString(inspect(context, array));
        }

        @SlowPath
        private static String inspect(RubyContext context, RubyArray array) {
            final StringBuilder builder = new StringBuilder();

            builder.append("[");

            for (int n = 0; n < array.size(); n++) {
                if (n > 0) {
                    builder.append(", ");
                }

                // TODO(CS): slow path send
                builder.append(context.getCoreLibrary().box(array.get(n)).send("inspect", null));
            }

            builder.append("]");

            return builder.toString();
        }

    }

    @CoreMethod(names = "join", minArgs = 1, maxArgs = 1)
    public abstract static class JoinNode extends ArrayCoreMethodNode {

        public JoinNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public JoinNode(JoinNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString join(RubyArray array, RubyString separator) {
            return array.getRubyClass().getContext().makeString(array.join(separator.toString()));
        }

    }

    @CoreMethod(names = "last", maxArgs = 0)
    public abstract static class LastNode extends ArrayCoreMethodNode {

        public LastNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public LastNode(LastNode prev) {
            super(prev);
        }

        @Specialization
        public Object last(RubyArray array) {
            final int size = array.size();
            if (size == 0) {
                return NilPlaceholder.INSTANCE;
            } else {
                return array.get(size - 1);
            }
        }

    }

    @CoreMethod(names = {"map", "collect"}, needsBlock = true, maxArgs = 0)
    public abstract static class MapNode extends YieldingCoreMethodNode {

        public MapNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public MapNode(MapNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray map(VirtualFrame frame, RubyArray array, RubyProc block) {
            final RubyArray result = new RubyArray(array.getRubyClass().getContext().getCoreLibrary().getArrayClass());

            for (int n = 0; n < array.size(); n++) {
                result.push(yield(frame, block, array.get(n)));
            }

            return result;
        }
    }

    @CoreMethod(names = {"map!", "collect!"}, needsBlock = true, maxArgs = 0)
    public abstract static class MapInPlaceNode extends YieldingCoreMethodNode {

        public MapInPlaceNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public MapInPlaceNode(MapInPlaceNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray mapInPlace(VirtualFrame frame, RubyArray array, RubyProc block) {
            for (int n = 0; n < array.size(); n++) {
                array.set(n, yield(frame, block, array.get(n)));
            }

            return array;
        }
    }

    @CoreMethod(names = "pop", maxArgs = 0)
    public abstract static class PopNode extends ArrayCoreMethodNode {

        public PopNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public PopNode(PopNode prev) {
            super(prev);
        }

        @Specialization
        public Object pop(RubyArray array) {
            final int size = array.size();

            if (size == 0) {
                return NilPlaceholder.INSTANCE;
            } else {
                return array.deleteAt(size - 1);
            }
        }

    }

    @CoreMethod(names = "product", isSplatted = true)
    public abstract static class ProductNode extends ArrayCoreMethodNode {

        public ProductNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ProductNode(ProductNode prev) {
            super(prev);
        }

        @Specialization
        public Object product(RubyArray array, Object... args) {
            final RubyArray[] arrays = new RubyArray[1 + args.length];
            arrays[0] = array;
            System.arraycopy(args, 0, arrays, 1, args.length);
            return RubyArray.product(array.getRubyClass().getContext().getCoreLibrary().getArrayClass(), arrays, arrays.length);
        }

    }

    @CoreMethod(names = {"push", "<<"}, isSplatted = true)
    public abstract static class PushNode extends CoreMethodNode {

        public PushNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public PushNode(PushNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray push(RubyArray array, Object... args) {
            for (int n = 0; n < args.length; n++) {
                array.push(args[n]);
            }

            return array;
        }

    }

    @CoreMethod(names = "reject!", needsBlock = true, maxArgs = 0)
    public abstract static class RejectInPlaceNode extends YieldingCoreMethodNode {

        public RejectInPlaceNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public RejectInPlaceNode(RejectInPlaceNode prev) {
            super(prev);
        }

        @Specialization
        public Object rejectInPlace(VirtualFrame frame, RubyArray array, RubyProc block) {
            boolean modified = false;
            int n = 0;

            while (n < array.size()) {
                if (yieldBoolean(frame, block, array.get(n))) {
                    array.deleteAt(n);
                    modified = true;
                } else {
                    n++;
                }
            }

            if (modified) {
                return array;
            } else {
                return NilPlaceholder.INSTANCE;
            }
        }

    }

    @CoreMethod(names = "select", needsBlock = true, maxArgs = 0)
    public abstract static class SelectNode extends YieldingCoreMethodNode {

        public SelectNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public SelectNode(SelectNode prev) {
            super(prev);
        }

        @Specialization
        public Object select(VirtualFrame frame, RubyArray array, RubyProc block) {
            final RubyArray result = new RubyArray(getContext().getCoreLibrary().getArrayClass());

            for (int n = 0; n < array.size(); n++) {
                final Object value = array.get(n);

                if (yieldBoolean(frame, block, value)) {
                    result.push(value);
                }
            }

            return result;
        }

    }

    @CoreMethod(names = "shift", maxArgs = 0)
    public abstract static class ShiftNode extends CoreMethodNode {

        public ShiftNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ShiftNode(ShiftNode prev) {
            super(prev);
        }

        @Specialization
        public Object shift(RubyArray array) {
            final int size = array.size();

            if (size == 0) {
                return NilPlaceholder.INSTANCE;
            } else {
                return array.deleteAt(0);
            }
        }

    }

    @CoreMethod(names = {"size", "length"}, maxArgs = 0)
    public abstract static class SizeNode extends ArrayCoreMethodNode {

        public SizeNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public SizeNode(SizeNode prev) {
            super(prev);
        }

        @Specialization
        public int size(RubyArray array) {
            return array.size();
        }

    }

    @CoreMethod(names = "sort", maxArgs = 0)
    public abstract static class SortNode extends CoreMethodNode {

        public SortNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public SortNode(SortNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray sort(RubyArray array) {
            final RubyContext context = array.getRubyClass().getContext();

            final Object[] objects = array.asList().toArray();

            Arrays.sort(objects, new Comparator<Object>() {

                @Override
                public int compare(Object a, Object b) {
                    final RubyBasicObject aBoxed = context.getCoreLibrary().box(a);
                    return (int) aBoxed.getLookupNode().lookupMethod("<=>").call(null, aBoxed, null, b);
                }

            });

            return RubyArray.specializedFromObjects(context.getCoreLibrary().getArrayClass(), objects);
        }

    }

    @CoreMethod(names = "unshift", isSplatted = true)
    public abstract static class UnshiftNode extends CoreMethodNode {

        public UnshiftNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public UnshiftNode(UnshiftNode prev) {
            super(prev);
        }

        @Specialization
        public Object unshift(RubyArray array, Object... args) {
            for (int n = 0; n < args.length; n++) {
                array.unshift(args[n]);
            }

            return array;
        }

    }

    @CoreMethod(names = "zip", isSplatted = true)
    public abstract static class ZipNode extends CoreMethodNode {

        public ZipNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ZipNode(ZipNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray zip(RubyArray array, Object... args) {
            final RubyContext context = getContext();
            final RubyClass arrayClass = context.getCoreLibrary().getArrayClass();

            final RubyArray result = new RubyArray(arrayClass);

            for (int n = 0; n < array.size(); n++) {
                final RubyArray tuple = new RubyArray(arrayClass);

                tuple.push(array.get(n));

                for (Object arg : args) {
                    final RubyArray argArray = (RubyArray) arg;
                    tuple.push(argArray.get(n));
                }

                result.push(tuple);
            }

            return result;
        }

    }

}
