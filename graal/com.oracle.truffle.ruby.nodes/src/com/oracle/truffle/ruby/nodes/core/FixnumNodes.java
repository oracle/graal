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

import java.math.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.core.array.*;

@CoreClass(name = "Fixnum")
public abstract class FixnumNodes {

    @CoreMethod(names = "+@", maxArgs = 0)
    public abstract static class PosNode extends CoreMethodNode {

        public PosNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public PosNode(PosNode prev) {
            super(prev);
        }

        @Specialization
        public int pos(int value) {
            return value;
        }

    }

    @CoreMethod(names = "-@", maxArgs = 0)
    public abstract static class NegNode extends CoreMethodNode {

        public NegNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public NegNode(NegNode prev) {
            super(prev);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        public int neg(int value) {
            return ExactMath.subtractExact(0, value);
        }

        @Specialization
        public BigInteger negWithOverflow(int value) {
            return BigInteger.valueOf(value).negate();
        }

    }

    @CoreMethod(names = "+", minArgs = 1, maxArgs = 1)
    public abstract static class AddNode extends CoreMethodNode {

        public AddNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public AddNode(AddNode prev) {
            super(prev);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        public int add(int a, int b) {
            return ExactMath.addExact(a, b);
        }

        @Specialization
        public Object addWithOverflow(int a, int b) {
            return GeneralConversions.fixnumOrBignum(BigInteger.valueOf(a).add(BigInteger.valueOf(b)));
        }

        @Specialization
        public double add(int a, double b) {
            return a + b;
        }

        @Specialization
        public Object add(int a, BigInteger b) {
            return GeneralConversions.fixnumOrBignum(BigInteger.valueOf(a).add(b));
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

        @Specialization(rewriteOn = ArithmeticException.class)
        public int sub(int a, int b) {
            return ExactMath.subtractExact(a, b);
        }

        @Specialization
        public Object subWithOverflow(int a, int b) {
            return GeneralConversions.fixnumOrBignum(BigInteger.valueOf(a).subtract(BigInteger.valueOf(b)));
        }

        @Specialization
        public double sub(int a, double b) {
            return a - b;
        }

        @Specialization
        public Object sub(int a, BigInteger b) {
            return GeneralConversions.fixnumOrBignum(BigInteger.valueOf(a).subtract(b));
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

        @Specialization(rewriteOn = ArithmeticException.class)
        public int mul(int a, int b) {
            return ExactMath.multiplyExact(a, b);
        }

        @Specialization
        public Object mulWithOverflow(int a, int b) {
            return GeneralConversions.fixnumOrBignum(BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)));
        }

        @Specialization
        public double mul(int a, double b) {
            return a * b;
        }

        @Specialization
        public Object mul(int a, BigInteger b) {
            return GeneralConversions.fixnumOrBignum(BigInteger.valueOf(a).multiply(b));
        }

    }

    @CoreMethod(names = "**", minArgs = 1, maxArgs = 1)
    public abstract static class PowNode extends CoreMethodNode {

        public PowNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public PowNode(PowNode prev) {
            super(prev);
        }

        @Specialization
        public Object pow(int a, int b) {
            return GeneralConversions.fixnumOrBignum(BigInteger.valueOf(a).pow(b));
        }

        @Specialization
        public double pow(int a, double b) {
            return Math.pow(a, b);
        }

        @Specialization
        public Object pow(int a, BigInteger b) {
            final BigInteger bigA = BigInteger.valueOf(a);

            BigInteger result = BigInteger.ONE;

            for (BigInteger n = BigInteger.ZERO; b.compareTo(b) < 0; n = n.add(BigInteger.ONE)) {
                result = result.multiply(bigA);
            }

            return result;
        }

    }

    @CoreMethod(names = "/", minArgs = 1, maxArgs = 1)
    public abstract static class DivNode extends CoreMethodNode {

        public DivNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public DivNode(DivNode prev) {
            super(prev);
        }

        @Specialization
        public int div(int a, int b) {
            return a / b;
        }

        @Specialization
        public double div(int a, double b) {
            return a / b;
        }

        @Specialization
        public int div(@SuppressWarnings("unused") int a, @SuppressWarnings("unused") BigInteger b) {
            return 0;
        }
    }

    @CoreMethod(names = "%", minArgs = 1, maxArgs = 1)
    public abstract static class ModNode extends CoreMethodNode {

        public ModNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ModNode(ModNode prev) {
            super(prev);
        }

        @Specialization
        public int mod(int a, int b) {
            return a % b;
        }

        @Specialization
        public double mod(@SuppressWarnings("unused") int a, @SuppressWarnings("unused") double b) {
            throw new UnsupportedOperationException();
        }

        @Specialization
        public BigInteger mod(@SuppressWarnings("unused") int a, BigInteger b) {
            return b;
        }
    }

    @CoreMethod(names = "divmod", minArgs = 1, maxArgs = 1)
    public abstract static class DivModNode extends CoreMethodNode {

        public DivModNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public DivModNode(DivModNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray divMod(int a, int b) {
            int q;

            if (b < 0) {
                if (a < 0) {
                    q = -a / -b;
                } else {
                    q = -(a / -b);
                }
            } else {
                if (a < 0) {
                    q = -(-a / b);
                } else {
                    q = a / b;
                }
            }

            int r = a - q * b;

            if ((r < 0 && b > 0) || (r > 0 && b < 0)) {
                r += b;
                q -= 1;
            }

            final FixnumImmutablePairArrayStore store = new FixnumImmutablePairArrayStore(q, r);
            return new RubyArray(getContext().getCoreLibrary().getArrayClass(), store);
        }

        @Specialization
        public RubyArray divMod(@SuppressWarnings("unused") int a, @SuppressWarnings("unused") double b) {
            throw new UnsupportedOperationException();
        }

        @Specialization
        public RubyArray divMod(int a, BigInteger b) {
            return RubyBignum.divMod(getContext(), BigInteger.valueOf(a), b);
        }
    }

    @CoreMethod(names = "<", minArgs = 1, maxArgs = 1)
    public abstract static class LessNode extends CoreMethodNode {

        public LessNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public LessNode(LessNode prev) {
            super(prev);
        }

        @Specialization
        public boolean less(int a, int b) {
            return a < b;
        }

        @Specialization
        public boolean less(int a, double b) {
            return a < b;
        }

        @Specialization
        public boolean less(int a, BigInteger b) {
            return BigInteger.valueOf(a).compareTo(b) < 0;
        }
    }

    @CoreMethod(names = "<=", minArgs = 1, maxArgs = 1)
    public abstract static class LessEqualNode extends CoreMethodNode {

        public LessEqualNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public LessEqualNode(LessEqualNode prev) {
            super(prev);
        }

        @Specialization
        public boolean lessEqual(int a, int b) {
            return a <= b;
        }

        @Specialization
        public boolean lessEqual(int a, double b) {
            return a <= b;
        }

        @Specialization
        public boolean lessEqual(int a, BigInteger b) {
            return BigInteger.valueOf(a).compareTo(b) <= 0;
        }
    }

    @CoreMethod(names = {"==", "==="}, minArgs = 1, maxArgs = 1)
    public abstract static class EqualNode extends CoreMethodNode {

        public EqualNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public EqualNode(EqualNode prev) {
            super(prev);
        }

        @Specialization
        public boolean equal(int a, int b) {
            return a == b;
        }

        @Specialization
        public boolean equal(int a, double b) {
            return a == b;
        }

        @Specialization
        public boolean equal(int a, BigInteger b) {
            return BigInteger.valueOf(a).compareTo(b) == 0;
        }
    }

    @CoreMethod(names = "<=>", minArgs = 1, maxArgs = 1)
    public abstract static class CompareNode extends CoreMethodNode {

        public CompareNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public CompareNode(CompareNode prev) {
            super(prev);
        }

        @Specialization
        public int compare(int a, int b) {
            return Integer.compare(a, b);
        }

        @Specialization
        public int compare(int a, double b) {
            return Double.compare(a, b);
        }

        @Specialization
        public int compare(int a, BigInteger b) {
            return BigInteger.valueOf(a).compareTo(b);
        }
    }

    @CoreMethod(names = "!=", minArgs = 1, maxArgs = 1)
    public abstract static class NotEqualNode extends CoreMethodNode {

        public NotEqualNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public NotEqualNode(NotEqualNode prev) {
            super(prev);
        }

        @Specialization
        public boolean notEqual(int a, int b) {
            return a != b;
        }

        @Specialization
        public boolean notEqual(int a, double b) {
            return a != b;
        }

        @Specialization
        public boolean notEqual(int a, BigInteger b) {
            return BigInteger.valueOf(a).compareTo(b) != 0;
        }
    }

    @CoreMethod(names = ">=", minArgs = 1, maxArgs = 1)
    public abstract static class GreaterEqualNode extends CoreMethodNode {

        public GreaterEqualNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public GreaterEqualNode(GreaterEqualNode prev) {
            super(prev);
        }

        @Specialization
        public boolean greaterEqual(int a, int b) {
            return a >= b;
        }

        @Specialization
        public boolean greaterEqual(int a, double b) {
            return a >= b;
        }

        @Specialization
        public boolean greaterEqual(int a, BigInteger b) {
            return BigInteger.valueOf(a).compareTo(b) >= 0;
        }
    }

    @CoreMethod(names = ">", minArgs = 1, maxArgs = 1)
    public abstract static class GreaterNode extends CoreMethodNode {

        public GreaterNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public GreaterNode(GreaterNode prev) {
            super(prev);
        }

        @Specialization
        public boolean equal(int a, int b) {
            return a > b;
        }

        @Specialization
        public boolean equal(int a, double b) {
            return a > b;
        }

        @Specialization
        public boolean equal(int a, BigInteger b) {
            return BigInteger.valueOf(a).compareTo(b) > 0;
        }
    }

    @CoreMethod(names = "~", maxArgs = 0)
    public abstract static class ComplementNode extends CoreMethodNode {

        public ComplementNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ComplementNode(ComplementNode prev) {
            super(prev);
        }

        @Specialization
        public int complement(int n) {
            return ~n;
        }

    }

    @CoreMethod(names = "&", minArgs = 1, maxArgs = 1)
    public abstract static class BitAndNode extends CoreMethodNode {

        public BitAndNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public BitAndNode(BitAndNode prev) {
            super(prev);
        }

        @Specialization
        public int bitAnd(int a, int b) {
            return a & b;
        }

        @Specialization
        public Object bitAnd(int a, BigInteger b) {
            return GeneralConversions.fixnumOrBignum(BigInteger.valueOf(a).and(b));
        }
    }

    @CoreMethod(names = "|", minArgs = 1, maxArgs = 1)
    public abstract static class BitOrNode extends CoreMethodNode {

        public BitOrNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public BitOrNode(BitOrNode prev) {
            super(prev);
        }

        @Specialization
        public int bitOr(int a, int b) {
            return a | b;
        }

        @Specialization
        public Object bitOr(int a, BigInteger b) {
            return GeneralConversions.fixnumOrBignum(BigInteger.valueOf(a).or(b));
        }
    }

    @CoreMethod(names = "^", minArgs = 1, maxArgs = 1)
    public abstract static class BitXOrNode extends CoreMethodNode {

        public BitXOrNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public BitXOrNode(BitXOrNode prev) {
            super(prev);
        }

        @Specialization
        public int bitXOr(int a, int b) {
            return a ^ b;
        }

        @Specialization
        public Object bitXOr(int a, BigInteger b) {
            return GeneralConversions.fixnumOrBignum(BigInteger.valueOf(a).xor(b));
        }
    }

    @CoreMethod(names = "<<", minArgs = 1, maxArgs = 1)
    public abstract static class LeftShiftNode extends CoreMethodNode {

        public LeftShiftNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public LeftShiftNode(LeftShiftNode prev) {
            super(prev);
        }

        @Specialization
        public Object leftShift(int a, int b) {
            if (b > 0) {
                if (RubyFixnum.SIZE - Integer.numberOfLeadingZeros(a) + b > RubyFixnum.SIZE - 1) {
                    return GeneralConversions.fixnumOrBignum(BigInteger.valueOf(a).shiftLeft(b));
                } else {
                    return a << b;
                }
            } else {
                if (-b >= Integer.SIZE) {
                    return 0;
                } else {
                    return a >> -b;
                }
            }
        }

    }

    @CoreMethod(names = ">>", minArgs = 1, maxArgs = 1)
    public abstract static class RightShiftNode extends CoreMethodNode {

        public RightShiftNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public RightShiftNode(RightShiftNode prev) {
            super(prev);
        }

        @Specialization
        public int rightShift(int a, int b) {
            if (b > 0) {
                return a >> b;
            } else {
                if (-b >= RubyFixnum.SIZE) {
                    return 0;
                } else {
                    return a >> -b;
                }
            }
        }

    }

    @CoreMethod(names = "[]", minArgs = 1, maxArgs = 1)
    public abstract static class GetIndexNode extends CoreMethodNode {

        public GetIndexNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public GetIndexNode(GetIndexNode prev) {
            super(prev);
        }

        @Specialization
        public int getIndex(int self, int index) {
            if ((self & (1 << index)) == 0) {
                return 0;
            } else {
                return 1;
            }
        }

    }

    @CoreMethod(names = "chr", maxArgs = 0)
    public abstract static class ChrNode extends CoreMethodNode {

        public ChrNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ChrNode(ChrNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString chr(int n) {
            // TODO(CS): not sure about encoding here
            return getContext().makeString((char) n);
        }

    }

    @CoreMethod(names = "inspect", maxArgs = 0)
    public abstract static class InpsectNode extends CoreMethodNode {

        public InpsectNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public InpsectNode(InpsectNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString inspect(int n) {
            return getContext().makeString(Integer.toString(n));
        }

    }

    @CoreMethod(names = "nonzero?", maxArgs = 0)
    public abstract static class NonZeroNode extends CoreMethodNode {

        public NonZeroNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public NonZeroNode(NonZeroNode prev) {
            super(prev);
        }

        @Specialization
        public Object nonZero(int value) {
            if (value == 0) {
                return false;
            } else {
                return value;
            }
        }

    }

    @CoreMethod(names = "size", needsSelf = false, maxArgs = 0)
    public abstract static class SizeNode extends CoreMethodNode {

        public SizeNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public SizeNode(SizeNode prev) {
            super(prev);
        }

        @Specialization
        public int size() {
            return Integer.SIZE / Byte.SIZE;
        }

    }

    @CoreMethod(names = "step", needsBlock = true, minArgs = 2, maxArgs = 2)
    public abstract static class StepNode extends YieldingCoreMethodNode {

        public StepNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public StepNode(StepNode prev) {
            super(prev);
        }

        @Specialization
        public NilPlaceholder step(VirtualFrame frame, int from, int to, int step, RubyProc block) {
            for (int i = from; i <= to; i += step) {
                yield(frame, block, i);
            }

            return NilPlaceholder.INSTANCE;
        }

    }

    @CoreMethod(names = "times", needsBlock = true, maxArgs = 0)
    public abstract static class TimesNode extends YieldingCoreMethodNode {

        private final BranchProfile breakProfile = new BranchProfile();
        private final BranchProfile nextProfile = new BranchProfile();
        private final BranchProfile redoProfile = new BranchProfile();

        public TimesNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public TimesNode(TimesNode prev) {
            super(prev);
        }

        @Specialization
        public Object times(VirtualFrame frame, int n, RubyProc block) {
            outer: for (int i = 0; i < n; i++) {
                while (true) {
                    try {
                        yield(frame, block, i);
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

            return n;
        }

    }

    @CoreMethod(names = {"to_i", "to_int"}, maxArgs = 0)
    public abstract static class ToINode extends CoreMethodNode {

        public ToINode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ToINode(ToINode prev) {
            super(prev);
        }

        @Specialization
        public int toI(int n) {
            return n;
        }

    }

    @CoreMethod(names = "to_f", maxArgs = 0)
    public abstract static class ToFNode extends CoreMethodNode {

        public ToFNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ToFNode(ToFNode prev) {
            super(prev);
        }

        @Specialization
        public double toF(int n) {
            return n;
        }

    }

    @CoreMethod(names = "to_s", maxArgs = 0)
    public abstract static class ToSNode extends CoreMethodNode {

        public ToSNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ToSNode(ToSNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString toS(int n) {
            return getContext().makeString(Integer.toString(n));
        }

    }

    @CoreMethod(names = "upto", needsBlock = true, minArgs = 1, maxArgs = 1)
    public abstract static class UpToNode extends YieldingCoreMethodNode {

        private final BranchProfile breakProfile = new BranchProfile();
        private final BranchProfile nextProfile = new BranchProfile();
        private final BranchProfile redoProfile = new BranchProfile();

        public UpToNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public UpToNode(UpToNode prev) {
            super(prev);
        }

        @Specialization
        public Object upto(VirtualFrame frame, int from, int to, RubyProc block) {
            outer: for (int i = from; i <= to; i++) {
                while (true) {
                    try {
                        yield(frame, block, i);
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

}
