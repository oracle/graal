/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test.ea;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.collections.Equivalence;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Regression test derived from jdk testing penetrating read elimination of new array nodes and
 * loadfield/store field to them.
 */
@SuppressWarnings("unused")
public class ReadEliminationRegressionTest extends GraalCompilerTest {

    @Test
    public void test() {
        ResolvedJavaMethod meth = getResolvedJavaMethod(TestMock.class, "writeBranch");
        TestMock mock = new TestMock();
        test(meth, mock, new Opcode(), new LabelImpl(new CA(), 0));
    }

    static class BufWriter {

        public int size() {
            return 0;
        }

        public void writeU1(int wide) {
        }

        public void writeIntBytes(int nBytes, int i) {
        }

    }

    interface Label {

    }

    interface LabelContext {
        Label newLabel();

        Label getLabel(int bci);

        void setLabelTarget(Label label, int bci);

        int labelToBci(Label label);
    }

    static final class LabelImpl implements Label {

        private int bci;
        private final LabelContext labelContext;

        LabelImpl(LabelContext labelContext, int bci) {
            this.labelContext = Objects.requireNonNull(labelContext);
            this.bci = bci;
        }

        public LabelContext labelContext() {
            return labelContext;
        }

        public int getBCI() {
            return bci;
        }

        public void setBCI(int bci) {
            this.bci = bci;
        }

        public Label label() {
            return this;
        }

    }

    interface BufferedCodeBuilder {

    }

    interface CodeAttribute extends LabelContext {
        int codeLength();

    }

    static class CA implements CodeAttribute {

        @Override
        public Label newLabel() {
            return new LabelImpl(null, -1);
        }

        @Override
        public Label getLabel(int bci) {
            return null;
        }

        @Override
        public void setLabelTarget(Label label, int bci) {
        }

        @Override
        public int labelToBci(Label label) {
            return 0;
        }

        @Override
        public int codeLength() {
            return 0;
        }

    }

    static class DeferredLabel {

        DeferredLabel(int pc, int nBytes, int instructionPc, Label label) {
        }

    }

    static class TestMock {
        private Object mruParent;
        final BufWriter bytecodesBufWriter;
        private int[] mruParentTable;
        private Map<CodeAttribute, int[]> parentMap;
        private final boolean transformFwdJumps;
        private final boolean transformBackJumps;

        List<DeferredLabel> deferredLabels;

        TestMock() {
            bytecodesBufWriter = new BufWriter();
            this.transformFwdJumps = false;
            this.transformBackJumps = true;
        }

        public int curPc() {
            return bytecodesBufWriter.size();
        }

        public int labelToBci(Label label) {
            LabelImpl lab = (LabelImpl) label;
            LabelContext context = lab.labelContext();
            lab.bci = label.hashCode();
            CodeAttribute parent = (CodeAttribute) context;
            parentMap = new EconomicHashMap<>(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
            int[] table = new int[parent.codeLength() + 1];
            mruParent = parent;
            mruParentTable = table;
            return mruParentTable[lab.getBCI()] - 1;
        }

        public void writeBranch(Opcode op, Label target) {
            int instructionPc = curPc();
            int targetBci = labelToBci(target);
            GraalDirectives.sideEffect(targetBci);
        }

    }

    static class Opcode {

    }

    @Test
    public void testArrayAllocBounds01() throws NoSuchMethodException, SecurityException {
        Constructor<Matcher> reflectMethod = Matcher.class.getDeclaredConstructor(Pattern.class, CharSequence.class);
        ResolvedJavaMethod m = getMetaAccess().lookupJavaMethod(reflectMethod);
        assert m != null;
        compile(m, null);
    }

}
