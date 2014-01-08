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

@CoreClass(name = "Bignum")
public abstract class BignumNodes {

    @CoreMethod(names = "+@", maxArgs = 0)
    public abstract static class PosNode extends CoreMethodNode {

        public PosNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public PosNode(PosNode prev) {
            super(prev);
        }

        @Specialization
        public BigInteger pos(BigInteger value) {
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

        @Specialization
        public BigInteger neg(BigInteger value) {
            return value.negate();
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

        @Specialization
        public Object add(BigInteger a, int b) {
            return a.add(BigInteger.valueOf(b));
        }

        @Specialization
        public double add(BigInteger a, double b) {
            return a.doubleValue() + b;
        }

        @Specialization
        public Object add(BigInteger a, BigInteger b) {
            return GeneralConversions.fixnumOrBignum(a.add(b));
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
        public Object sub(BigInteger a, int b) {
            return a.subtract(BigInteger.valueOf(b));
        }

        @Specialization
        public double sub(BigInteger a, double b) {
            return a.doubleValue() - b;
        }

        @Specialization
        public Object sub(BigInteger a, BigInteger b) {
            return GeneralConversions.fixnumOrBignum(a.subtract(b));
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
        public Object mul(BigInteger a, int b) {
            return a.multiply(BigInteger.valueOf(b));
        }

        @Specialization
        public double mul(BigInteger a, double b) {
            return a.doubleValue() * b;
        }

        @Specialization
        public Object mul(BigInteger a, BigInteger b) {
            return GeneralConversions.fixnumOrBignum(a.multiply(b));
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
        public BigInteger pow(BigInteger a, int b) {
            return a.pow(b);
        }

        @Specialization
        public double pow(BigInteger a, double b) {
            return Math.pow(a.doubleValue(), b);
        }

        @Specialization
        public BigInteger pow(BigInteger a, BigInteger b) {
            BigInteger result = BigInteger.ONE;

            for (BigInteger n = BigInteger.ZERO; b.compareTo(b) < 0; n = n.add(BigInteger.ONE)) {
                result = result.multiply(a);
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
        public Object div(BigInteger a, int b) {
            return a.divide(BigInteger.valueOf(b));
        }

        @Specialization
        public double div(BigInteger a, double b) {
            return a.doubleValue() / b;
        }

        @Specialization
        public Object div(BigInteger a, BigInteger b) {
            return GeneralConversions.fixnumOrBignum(a.divide(b));
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
        public Object mod(BigInteger a, int b) {
            return GeneralConversions.fixnumOrBignum(a.mod(BigInteger.valueOf(b)));
        }

        @Specialization
        public Object mod(@SuppressWarnings("unused") BigInteger a, @SuppressWarnings("unused") double b) {
            throw new UnsupportedOperationException();
        }

        @Specialization
        public Object mod(BigInteger a, BigInteger b) {
            return GeneralConversions.fixnumOrBignum(a.mod(b));
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

        @SuppressWarnings("unused")
        @Specialization
        public RubyArray divMod(VirtualFrame frame, BigInteger a, int b) {
            return RubyBignum.divMod(getContext(), a, BigInteger.valueOf(b));
        }

        @Specialization
        public RubyArray divMod(@SuppressWarnings("unused") BigInteger a, @SuppressWarnings("unused") double b) {
            throw new UnsupportedOperationException();
        }

        @Specialization
        public RubyArray divMod(BigInteger a, BigInteger b) {
            return RubyBignum.divMod(getContext(), a, b);
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
        public boolean less(BigInteger a, int b) {
            return a.compareTo(BigInteger.valueOf(b)) < 0;
        }

        @Specialization
        public boolean less(BigInteger a, double b) {
            return a.compareTo(BigInteger.valueOf((long) b)) < 0;
        }

        @Specialization
        public boolean less(BigInteger a, BigInteger b) {
            return a.compareTo(b) < 0;
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
        public boolean lessEqual(BigInteger a, int b) {
            return a.compareTo(BigInteger.valueOf(b)) <= 0;
        }

        @Specialization
        public boolean lessEqual(BigInteger a, double b) {
            return a.compareTo(BigInteger.valueOf((long) b)) <= 0;
        }

        @Specialization
        public boolean lessEqual(BigInteger a, BigInteger b) {
            return a.compareTo(b) <= 0;
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
        public boolean equal(BigInteger a, int b) {
            return a.compareTo(BigInteger.valueOf(b)) == 0;
        }

        @Specialization
        public boolean equal(BigInteger a, double b) {
            return a.compareTo(BigInteger.valueOf((long) b)) == 0;
        }

        @Specialization
        public boolean equal(BigInteger a, BigInteger b) {
            return a.compareTo(b) == 0;
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
        public int compare(BigInteger a, int b) {
            return a.compareTo(BigInteger.valueOf(b));
        }

        @Specialization
        public int compare(BigInteger a, double b) {
            return a.compareTo(BigInteger.valueOf((long) b));
        }

        @Specialization
        public int compare(BigInteger a, BigInteger b) {
            return a.compareTo(b);
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
        public boolean notEqual(BigInteger a, int b) {
            return a.compareTo(BigInteger.valueOf(b)) != 0;
        }

        @Specialization
        public boolean notEqual(BigInteger a, double b) {
            return a.compareTo(BigInteger.valueOf((long) b)) != 0;
        }

        @Specialization
        public boolean notEqual(BigInteger a, BigInteger b) {
            return a.compareTo(b) != 0;
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
        public boolean greaterEqual(BigInteger a, int b) {
            return a.compareTo(BigInteger.valueOf(b)) >= 0;
        }

        @Specialization
        public boolean greaterEqual(BigInteger a, double b) {
            return a.compareTo(BigInteger.valueOf((long) b)) >= 0;
        }

        @Specialization
        public boolean greaterEqual(BigInteger a, BigInteger b) {
            return a.compareTo(b) >= 0;
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
        public boolean equal(BigInteger a, int b) {
            return a.compareTo(BigInteger.valueOf(b)) > 0;
        }

        @Specialization
        public boolean equal(BigInteger a, double b) {
            return a.compareTo(BigInteger.valueOf((long) b)) > 0;
        }

        @Specialization
        public boolean equal(BigInteger a, BigInteger b) {
            return a.compareTo(b) > 0;
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
        public Object bitAnd(BigInteger a, int b) {
            return GeneralConversions.fixnumOrBignum(a.and(BigInteger.valueOf(b)));
        }

        @Specialization
        public Object bitAnd(BigInteger a, BigInteger b) {
            return GeneralConversions.fixnumOrBignum(a.and(b));
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
        public Object bitOr(BigInteger a, int b) {
            return GeneralConversions.fixnumOrBignum(a.or(BigInteger.valueOf(b)));
        }

        @Specialization
        public Object bitOr(BigInteger a, BigInteger b) {
            return GeneralConversions.fixnumOrBignum(a.or(b));
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
        public Object bitXOr(BigInteger a, int b) {
            return GeneralConversions.fixnumOrBignum(a.xor(BigInteger.valueOf(b)));
        }

        @Specialization
        public Object bitXOr(BigInteger a, BigInteger b) {
            return GeneralConversions.fixnumOrBignum(a.xor(b));
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
        public Object leftShift(BigInteger a, int b) {
            if (b >= 0) {
                return GeneralConversions.fixnumOrBignum(a.shiftLeft(b));
            } else {
                return GeneralConversions.fixnumOrBignum(a.shiftRight(-b));
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
        public Object leftShift(BigInteger a, int b) {
            if (b >= 0) {
                return GeneralConversions.fixnumOrBignum(a.shiftRight(b));
            } else {
                return GeneralConversions.fixnumOrBignum(a.shiftLeft(-b));
            }
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
        public RubyString inspect(BigInteger n) {
            return getContext().makeString(n.toString());
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
        public Object nonZero(BigInteger value) {
            if (value.compareTo(BigInteger.ZERO) == 0) {
                return false;
            } else {
                return value;
            }
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
        public Object times(VirtualFrame frame, BigInteger n, RubyProc block) {
            outer: for (BigInteger i = BigInteger.ZERO; i.compareTo(n) < 0; i = i.add(BigInteger.ONE)) {
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

    @CoreMethod(names = "to_s", maxArgs = 0)
    public abstract static class ToSNode extends CoreMethodNode {

        public ToSNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ToSNode(ToSNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString toS(BigInteger value) {
            return getContext().makeString(value.toString());
        }

    }

}
