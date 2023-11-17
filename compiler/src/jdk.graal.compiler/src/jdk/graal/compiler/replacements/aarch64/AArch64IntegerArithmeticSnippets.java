/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Red Hat Inc. All rights reserved.
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

package jdk.graal.compiler.replacements.aarch64;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SnippetAnchorNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.SignedDivNode;
import jdk.graal.compiler.nodes.calc.SignedRemNode;
import jdk.graal.compiler.nodes.calc.UnsignedDivNode;
import jdk.graal.compiler.nodes.calc.UnsignedRemNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.Snippets;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;

/**
 * Division in AArch64 ISA does not generate a trap when dividing by zero, but instead sets the
 * result to 0. These snippets throw an ArithmeticException if the denominator is 0 and otherwise
 * forward to the LIRGenerator.
 */
public class AArch64IntegerArithmeticSnippets extends SnippetTemplate.AbstractTemplates implements Snippets {

    private final SnippetTemplate.SnippetInfo idiv;
    private final SnippetTemplate.SnippetInfo ldiv;
    private final SnippetTemplate.SnippetInfo irem;
    private final SnippetTemplate.SnippetInfo lrem;

    private final SnippetTemplate.SnippetInfo uidiv;
    private final SnippetTemplate.SnippetInfo uldiv;
    private final SnippetTemplate.SnippetInfo uirem;
    private final SnippetTemplate.SnippetInfo ulrem;

    @SuppressWarnings("this-escape")
    public AArch64IntegerArithmeticSnippets(OptionValues options, Providers providers) {
        super(options, providers);
        idiv = snippet(providers, AArch64IntegerArithmeticSnippets.class, "idivSnippet");
        ldiv = snippet(providers, AArch64IntegerArithmeticSnippets.class, "ldivSnippet");
        irem = snippet(providers, AArch64IntegerArithmeticSnippets.class, "iremSnippet");
        lrem = snippet(providers, AArch64IntegerArithmeticSnippets.class, "lremSnippet");

        uidiv = snippet(providers, AArch64IntegerArithmeticSnippets.class, "uidivSnippet");
        uldiv = snippet(providers, AArch64IntegerArithmeticSnippets.class, "uldivSnippet");
        uirem = snippet(providers, AArch64IntegerArithmeticSnippets.class, "uiremSnippet");
        ulrem = snippet(providers, AArch64IntegerArithmeticSnippets.class, "ulremSnippet");
    }

    public void lower(IntegerDivRemNode node, LoweringTool tool) {
        if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
            // wait for more precise stamp information
            return;
        }
        JavaKind kind = node.stamp(NodeView.DEFAULT).getStackKind();
        assert kind == JavaKind.Int || kind == JavaKind.Long : Assertions.errorMessage(node);
        SnippetTemplate.SnippetInfo snippet;
        if (node instanceof SafeNode) {
            // We already introduced the zero division check, nothing to do.
            return;
        } else if (node instanceof SignedDivNode) {
            snippet = kind == JavaKind.Int ? idiv : ldiv;
        } else if (node instanceof SignedRemNode) {
            snippet = kind == JavaKind.Int ? irem : lrem;
        } else if (node instanceof UnsignedDivNode) {
            snippet = kind == JavaKind.Int ? uidiv : uldiv;
        } else if (node instanceof UnsignedRemNode) {
            snippet = kind == JavaKind.Int ? uirem : ulrem;
        } else {
            throw GraalError.shouldNotReachHereUnexpectedValue(node); // ExcludeFromJacocoGeneratedReport
        }
        StructuredGraph graph = node.graph();
        SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
        args.add("x", node.getX());
        args.add("y", node.getY());

        IntegerStamp yStamp = (IntegerStamp) node.getY().stamp(NodeView.DEFAULT);
        boolean needsZeroCheck = node.canDeoptimize() && (node.getZeroGuard() == null && yStamp.contains(0));
        args.addConst("needsZeroCheck", needsZeroCheck);

        template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
    }

    @Snippet
    public static int idivSnippet(int x, int y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            checkForZero(y);
            return safeDiv(x, y);
        } else {
            return safeDiv(x, PiNode.piCastNonZero(y, SnippetAnchorNode.anchor()));
        }
    }

    @Snippet
    public static long ldivSnippet(long x, long y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            checkForZero(y);
            return safeDiv(x, y);
        } else {
            return safeDiv(x, PiNode.piCastNonZero(y, SnippetAnchorNode.anchor()));
        }
    }

    @Snippet
    public static int iremSnippet(int x, int y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            checkForZero(y);
            return safeRem(x, y);
        } else {
            return safeRem(x, PiNode.piCastNonZero(y, SnippetAnchorNode.anchor()));
        }
    }

    @Snippet
    public static long lremSnippet(long x, long y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            checkForZero(y);
            return safeRem(x, y);
        } else {
            return safeRem(x, PiNode.piCastNonZero(y, SnippetAnchorNode.anchor()));
        }
    }

    @Snippet
    public static int uidivSnippet(int x, int y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            checkForZero(y);
        }
        return safeUDiv(x, y);
    }

    @Snippet
    public static long uldivSnippet(long x, long y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            checkForZero(y);
        }
        return safeUDiv(x, y);
    }

    @Snippet
    public static int uiremSnippet(int x, int y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            checkForZero(y);
        }
        return safeURem(x, y);
    }

    @Snippet
    public static long ulremSnippet(long x, long y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            checkForZero(y);
        }
        return safeURem(x, y);
    }

    private static void checkForZero(int y) {
        if (BranchProbabilityNode.probability(BranchProbabilityNode.DEOPT_PROBABILITY, y == 0)) {
            // "/ by zero"
            DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.ArithmeticException);
        }
    }

    private static void checkForZero(long y) {
        if (BranchProbabilityNode.probability(BranchProbabilityNode.DEOPT_PROBABILITY, y == 0)) {
            // "/ by zero"
            DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.ArithmeticException);
        }
    }

    @NodeIntrinsic(SafeSignedDivNode.class)
    private static native int safeDiv(int x, int y);

    @NodeIntrinsic(SafeSignedDivNode.class)
    private static native long safeDiv(long x, long y);

    @NodeIntrinsic(SafeSignedRemNode.class)
    private static native int safeRem(int x, int y);

    @NodeIntrinsic(SafeSignedRemNode.class)
    private static native long safeRem(long x, long y);

    @NodeIntrinsic(SafeUnsignedDivNode.class)
    private static native int safeUDiv(int x, int y);

    @NodeIntrinsic(SafeUnsignedDivNode.class)
    private static native long safeUDiv(long x, long y);

    @NodeIntrinsic(SafeUnsignedRemNode.class)
    private static native int safeURem(int x, int y);

    @NodeIntrinsic(SafeUnsignedRemNode.class)
    private static native long safeURem(long x, long y);

    /**
     * Marker interface to distinguish untreated nodes from ones where we have installed the
     * additional checks.
     */
    private interface SafeNode {
    }

    @NodeInfo
    static class SafeSignedDivNode extends SignedDivNode implements SafeNode {
        public static final NodeClass<SafeSignedDivNode> TYPE = NodeClass.create(SafeSignedDivNode.class);

        protected SafeSignedDivNode(ValueNode x, ValueNode y) {
            super(TYPE, x, y, null);
        }

        @Override
        protected SignedDivNode createWithInputs(ValueNode forX, ValueNode forY, GuardingNode forZeroCheck, FrameState forStateBefore) {
            assert forZeroCheck == null;
            // note that stateBefore is irrelevant, as this "safe" variant will not deoptimize
            SafeSignedDivNode div = new SafeSignedDivNode(forX, forY);
            return div;
        }

        @Override
        public boolean canDeoptimize() {
            /*
             * All checks have been done. Returning false is the indicator that no FrameState is
             * necessary anymore for the node.
             */
            return false;
        }

        @Override
        public boolean canFloat() {
            return false;
        }
    }

    @NodeInfo
    static class SafeSignedRemNode extends SignedRemNode implements SafeNode {
        public static final NodeClass<SafeSignedRemNode> TYPE = NodeClass.create(SafeSignedRemNode.class);

        protected SafeSignedRemNode(ValueNode x, ValueNode y) {
            super(TYPE, x, y, null);
        }

        @Override
        protected SignedRemNode createWithInputs(ValueNode forX, ValueNode forY, GuardingNode forZeroCheck) {
            assert forZeroCheck == null;
            SafeSignedRemNode rem = new SafeSignedRemNode(forX, forY);
            return rem;
        }

        @Override
        public boolean canDeoptimize() {
            /*
             * All checks have been done. Returning false is the indicator that no FrameState is
             * necessary anymore for the node.
             */
            return false;
        }

        @Override
        public boolean canFloat() {
            return false;
        }
    }

    @NodeInfo
    static class SafeUnsignedDivNode extends UnsignedDivNode implements SafeNode {
        public static final NodeClass<SafeUnsignedDivNode> TYPE = NodeClass.create(SafeUnsignedDivNode.class);

        protected SafeUnsignedDivNode(ValueNode x, ValueNode y) {
            super(TYPE, x, y, null);
        }

        @Override
        public boolean canDeoptimize() {
            /*
             * All checks have been done. Returning false is the indicator that no FrameState is
             * necessary anymore for the node.
             */
            return false;
        }
    }

    @NodeInfo
    static class SafeUnsignedRemNode extends UnsignedRemNode implements SafeNode {
        public static final NodeClass<SafeUnsignedRemNode> TYPE = NodeClass.create(SafeUnsignedRemNode.class);

        protected SafeUnsignedRemNode(ValueNode x, ValueNode y) {
            super(TYPE, x, y, null);
        }

        @Override
        public boolean canDeoptimize() {
            /*
             * All checks have been done. Returning false is the indicator that no FrameState is
             * necessary anymore for the node.
             */
            return false;
        }
    }

}
