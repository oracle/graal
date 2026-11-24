/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.code.CompilationResult.CodeComment;
import jdk.graal.compiler.code.CompilationResult.CodeMark;
import jdk.graal.compiler.code.CompilationResult.JumpTable;
import jdk.graal.compiler.code.HexCodeFile;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.code.site.ImplicitExceptionDispatch;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.meta.InvokeTarget;

public class CompilationResultTest {
    @Test
    public void clearCallDebugInfoAfterClose() {
        for (boolean direct : new boolean[]{false, true}) {
            CompilationResult result = new CompilationResult("debug-info-test");
            byte[] code = new byte[32];
            result.setTargetCode(code, 24);
            result.recordSourceMapping(0, 24, null);
            InvokeTarget target = new InvokeTarget() {
            };
            Call call = result.recordCall(8, 5, target, new DebugInfo(null), direct);
            result.recordCallInvalidForDeoptimization(call);
            assertFalse(result.isValidCallDeoptimizationState(call));
            var debugInfo = call.debugInfo;
            result.close(null);

            result.clearInfopointDebugInfo();

            Call cleared = (Call) result.getInfopointsForCodeEmission().getFirst();
            assertNotSame(call, cleared);
            assertSame(target, cleared.target);
            assertEquals(call.pcOffset, cleared.pcOffset);
            assertEquals(call.size, cleared.size);
            assertEquals(call.direct, cleared.direct);
            assertEquals(call.reason, cleared.reason);
            assertNull(cleared.debugInfo);
            assertThrows(IllegalStateException.class, result::getInfopoints);
            assertThrows(IllegalStateException.class, () -> result.isValidCallDeoptimizationState(call));
            assertThrows(IllegalStateException.class, () -> result.isValidCallDeoptimizationState(cleared));
            assertSame(debugInfo, call.debugInfo);
            assertSame(code, result.getTargetCode());
            assertEquals(24, result.getTargetCodeSize());
            assertEquals(1, result.getSourceMappings().size());
        }
    }

    @Test
    public void debugCleanupPreservesPreviouslyReturnedViews() {
        CompilationResult result = new CompilationResult("retained-debug-info");
        DebugInfo debugInfo = new DebugInfo(null);
        Call call = result.recordCall(0, 5, null, debugInfo, true);
        result.recordImplicitException(8, 16, debugInfo);
        List<Infopoint> retained = result.getInfopoints();
        List<Infopoint> original = List.copyOf(retained);
        var emissionView = result.getInfopointsForCodeEmission();
        result.close(null);

        result.clearInfopointDebugInfo();

        assertThrows(IllegalStateException.class, result::getInfopoints);
        List<Infopoint> stripped = result.getInfopointsForCodeEmission();
        for (int i = 0; i < original.size(); i++) {
            assertSame(original.get(i), retained.get(i));
            assertSame(original.get(i), emissionView.get(i));
            assertSame(debugInfo, retained.get(i).debugInfo);
            assertNotSame(original.get(i), stripped.get(i));
            assertNull(stripped.get(i).debugInfo);
        }
        result.clearInfopointDebugInfo();
        assertSame(stripped.getFirst(), result.getInfopointsForCodeEmission().getFirst());
        result.clearCodeEmissionState();
        assertSame(call, retained.getFirst());
        assertSame(debugInfo, retained.getFirst().debugInfo);
    }

    @Test
    public void resetRestoresDebugInfoAccess() {
        CompilationResult result = new CompilationResult("reset-debug-info");
        var start = result.getSitesWatermark(0, 0);
        Call call = result.recordCall(0, 1, null, new DebugInfo(null), false);
        var end = result.getSitesWatermark(0, 0);
        result.clearInfopointDebugInfo();
        assertThrows(IllegalStateException.class, result::getInfopoints);
        assertThrows(IllegalStateException.class, () -> result.recordCallInvalidForDeoptimization(call));
        assertThrows(IllegalStateException.class, () -> result.duplicateSites(0, start, 1, end, 1));
        result.resetForEmittingCode();
        assertTrue(result.getInfopoints().isEmpty());
        Call replacement = result.recordCall(0, 1, null, new DebugInfo(null), false);
        result.recordCallInvalidForDeoptimization(replacement);
        assertFalse(result.isValidCallDeoptimizationState(replacement));
    }

    @Test
    public void clearImplicitExceptionDebugInfoAfterClose() {
        for (int dispatchOffset : new int[]{8, 24}) {
            CompilationResult result = new CompilationResult("debug-info-test");
            result.recordImplicitException(8, dispatchOffset, new DebugInfo(null));
            Infopoint original = result.getInfopointsForCodeEmission().getFirst();
            result.close(null);

            result.clearInfopointDebugInfo();

            ImplicitExceptionDispatch cleared = (ImplicitExceptionDispatch) result.getInfopointsForCodeEmission().getFirst();
            assertNotSame(original, cleared);
            assertEquals(8, cleared.pcOffset);
            assertEquals(dispatchOffset, cleared.dispatchOffset);
            assertEquals(original.reason, cleared.reason);
            assertNull(cleared.debugInfo);
        }
    }

    @Test
    public void preserveSitesWithoutDebugInfo() {
        CompilationResult result = new CompilationResult("debug-info-test");
        result.recordCall(4, 5, null, null, false);
        result.recordImplicitException(12, 16, null);
        result.recordInfopoint(20, null, InfopointReason.SAFEPOINT);
        List<Infopoint> original = List.copyOf(result.getInfopointsForCodeEmission());
        result.close(null);

        result.clearInfopointDebugInfo();

        assertEquals(original.size(), result.getInfopointsForCodeEmission().size());
        for (int i = 0; i < original.size(); i++) {
            assertSame(original.get(i), result.getInfopointsForCodeEmission().get(i));
        }
    }

    @Test
    public void preserveOtherInfopoints() {
        CompilationResult result = new CompilationResult("debug-info-test");
        DebugInfo debugInfo = new DebugInfo(null);
        Infopoint plain = new Infopoint(4, debugInfo, InfopointReason.SAFEPOINT);
        CustomInfopoint custom = new CustomInfopoint(8, debugInfo, new Object());
        result.addInfopoint(plain);
        result.addInfopoint(custom);
        result.close(null);

        result.clearInfopointDebugInfo();

        assertEquals(2, result.getInfopointsForCodeEmission().size());
        assertSame(plain, result.getInfopointsForCodeEmission().get(0));
        assertSame(debugInfo, plain.debugInfo);
        assertSame(custom, result.getInfopointsForCodeEmission().get(1));
        assertSame(debugInfo, custom.debugInfo);
        assertSame(custom.payload, ((CustomInfopoint) result.getInfopointsForCodeEmission().get(1)).payload);
    }

    @Test
    public void clearKnownCustomInfopoint() {
        CompilationResult result = new CompilationResult("custom-debug-info-test") {
            @Override
            protected Infopoint withoutDebugInfo(Infopoint infopoint) {
                if (infopoint instanceof CustomInfopoint custom) {
                    return new CustomInfopoint(custom.pcOffset, null, custom.payload);
                }
                return super.withoutDebugInfo(infopoint);
            }
        };
        DebugInfo debugInfo = new DebugInfo(null);
        Object payload = new Object();
        result.recordCall(4, 5, null, debugInfo, false);
        result.addInfopoint(new CustomInfopoint(12, debugInfo, payload));
        Infopoint unknown = new Infopoint(16, debugInfo, InfopointReason.SAFEPOINT);
        result.addInfopoint(unknown);
        result.close(null);

        result.clearInfopointDebugInfo();

        assertNull(result.getInfopointsForCodeEmission().get(0).debugInfo);
        CustomInfopoint cleared = (CustomInfopoint) result.getInfopointsForCodeEmission().get(1);
        assertEquals(12, cleared.pcOffset);
        assertEquals(InfopointReason.SAFEPOINT, cleared.reason);
        assertSame(payload, cleared.payload);
        assertNull(cleared.debugInfo);
        assertSame(unknown, result.getInfopointsForCodeEmission().get(2));
        assertSame(debugInfo, unknown.debugInfo);
    }

    private static final class CustomInfopoint extends Infopoint {
        private final Object payload;

        private CustomInfopoint(int pcOffset, DebugInfo debugInfo, Object payload) {
            super(pcOffset, debugInfo, InfopointReason.SAFEPOINT);
            this.payload = payload;
        }
    }

    @SuppressWarnings("unlikely-arg-type")
    @Test
    public void testCodeComment() {
        CodeComment comment = new CodeComment(0, "test");
        CodeComment commentSame = new CodeComment(0, "test");
        CodeComment commentDifferent = new CodeComment(1, "other");
        assertTrue(comment.equals(comment));
        assertTrue(comment.equals(commentSame));
        assertFalse(comment.equals(commentDifferent));
        assertFalse(comment.equals(this));
        assertTrue(comment.toString().length() > 0); // test for NPE
    }

    @SuppressWarnings("unlikely-arg-type")
    @Test
    public void testJumpTable() {
        JumpTable table = new JumpTable(0, 0, 8, JumpTable.EntryFormat.OFFSET_ONLY);
        JumpTable tableSame = new JumpTable(0, 0, 8, JumpTable.EntryFormat.OFFSET_ONLY);
        JumpTable tableDifferent = new JumpTable(0, 0, 8, JumpTable.EntryFormat.VALUE_AND_OFFSET);
        assertTrue(table.equals(table));
        assertTrue(table.equals(tableSame));
        assertFalse(table.equals(tableDifferent));
        assertFalse(table.equals(this));
        assertTrue(table.toString().length() > 0); // test for NPE
    }

    @Test
    public void testHexCodeFileJumpTableRoundTrip() {
        HexCodeFile hcf = new HexCodeFile(new byte[8], 0, "amd64", 64);
        hcf.jumpTables.add(new HexCodeFile.JumpTable(0, 0, 8, HexCodeFile.JumpTable.EntryFormat.OFFSET));
        String text = hcf.toString();
        assertTrue(text.contains("JumpTable 0 OFFSET 0 8"));

        HexCodeFile parsed = HexCodeFile.parse(text, 0, text, "test");
        assertEquals(1, parsed.jumpTables.size());
        HexCodeFile.JumpTable table = parsed.jumpTables.get(0);
        assertEquals(0, table.position);
        assertEquals(0, table.low);
        assertEquals(8, table.high);
        assertEquals(HexCodeFile.JumpTable.EntryFormat.OFFSET, table.entryFormat);
    }

    @SuppressWarnings("unlikely-arg-type")
    @Test
    public void testCompilationResult() {
        CompilationResult result = new CompilationResult("testresult");
        result.setEntryBCI(0);
        CompilationResult resultSimilar = new CompilationResult("testresult");
        resultSimilar.setEntryBCI(0);
        CompilationResult resultDifferent = new CompilationResult("differentResult");
        resultDifferent.setEntryBCI(1000);
        assertTrue(result.equals(result));
        assertFalse(result.equals(resultSimilar));
        assertFalse(result.equals(resultDifferent));
        assertFalse(result.equals(this));
        assertTrue(result.toString().length() > 0); // test for NPE
    }

    @Test
    public void testClearSourceMappingsAfterClose() {
        CompilationResult result = new CompilationResult("testresult");
        result.setTargetCode(new byte[8], 8);
        result.recordSourceMapping(0, 8, null);
        var mappings = result.getSourceMappings();
        result.close(null);
        result.clearSourceMappings();
        assertEquals(1, mappings.size());
        assertEquals(8, result.getTargetCodeSize());
        assertEquals(8, result.getTargetCode().length);
        assertThrows(IllegalStateException.class, result::getSourceMappings);
        assertThrows(IllegalStateException.class, result::clearSourceMappings);
    }

    @Test
    public void testResetRestoresSourceMappings() {
        CompilationResult result = new CompilationResult("testresult");
        result.recordSourceMapping(0, 1, null);
        var oldMappings = result.getSourceMappings();
        result.clearSourceMappings();
        result.resetForEmittingCode();
        result.recordSourceMapping(0, 2, null);
        assertEquals(1, result.getSourceMappings().size());
        assertEquals(2, result.getSourceMappings().getFirst().getEndOffset());
        assertEquals(1, oldMappings.size());
        assertEquals(1, oldMappings.getFirst().getEndOffset());
    }

    @Test
    public void testClearCodeEmissionStateAfterClose() {
        CompilationResult result = new CompilationResult("testresult");
        result.setTargetCode(new byte[16], 8);
        result.recordInfopoint(0, null, InfopointReason.SAFEPOINT);
        result.recordExceptionHandler(1, 2);
        result.recordMark(3, new TestMarkId());
        result.addAnnotation(new CodeComment(4, "test"));
        result.recordDataPatch(5, new DataSectionReference());
        Call call = result.recordCall(6, 1, null, null, false);
        result.recordCallInvalidForDeoptimization(call);
        result.recordSourceMapping(0, 8, null);
        var mapping = result.getSourceMappings().getFirst();
        assertEquals(1, result.getDataPatches().size());
        var infopoints = result.getInfopoints();
        var patches = result.getDataPatches();
        var handlers = result.getExceptionHandlers();
        var marks = result.getMarks();
        var annotations = result.getCodeAnnotations();
        result.close(null);

        result.clearCodeEmissionState();

        assertEquals(2, infopoints.size());
        assertEquals(1, patches.size());
        assertEquals(1, handlers.size());
        assertEquals(1, marks.size());
        assertEquals(1, annotations.size());
        assertEquals(8, result.getTargetCodeSize());
        assertThrows(IllegalStateException.class, result::getTargetCode);
        assertThrows(IllegalStateException.class, result::getInfopoints);
        assertThrows(IllegalStateException.class, result::getInfopointsForCodeEmission);
        assertThrows(IllegalStateException.class, result::getDataPatches);
        assertThrows(IllegalStateException.class, result::getExceptionHandlers);
        assertThrows(IllegalStateException.class, result::getMarks);
        assertThrows(IllegalStateException.class, result::getCodeAnnotations);
        assertThrows(IllegalStateException.class, result::getCodeSignature);
        assertThrows(IllegalStateException.class, () -> result.isValidCallDeoptimizationState(call));
        assertThrows(IllegalStateException.class, result::clearInfopoints);
        assertThrows(IllegalStateException.class, result::clearInfopointDebugInfo);
        assertThrows(IllegalStateException.class, result::clearCodeEmissionState);
        assertThrows(IllegalStateException.class, result::resetForEmittingCode);
        // Source mappings have an independent final consumer, such as perf profiling.
        assertSame(mapping, result.getSourceMappings().getFirst());
        result.clearSourceMappings();
        assertThrows(IllegalStateException.class, result::getSourceMappings);
    }

    @Test
    public void testMutationsRejectReleasedCodeEmissionState() {
        CompilationResult result = new CompilationResult("released-open-result");
        var start = result.getSitesWatermark(0, 0);
        Call call = result.recordCall(0, 1, null, null, false);
        var end = result.getSitesWatermark(0, 0);
        result.clearCodeEmissionState();

        assertThrows(IllegalStateException.class, () -> result.setTargetCode(new byte[8], 8));
        assertThrows(IllegalStateException.class, () -> result.addAnnotation(new CodeComment(0, "late annotation")));
        assertThrows(IllegalStateException.class, () -> result.recordMark(0, new TestMarkId()));
        assertThrows(IllegalStateException.class, () -> result.recordDataPatch(0, new DataSectionReference()));
        assertThrows(IllegalStateException.class, () -> result.recordDataPatchWithNote(0, new DataSectionReference(), "late patch"));
        assertThrows(IllegalStateException.class, () -> result.recordExceptionHandler(0, 1));
        assertThrows(IllegalStateException.class, () -> result.recordCall(0, 1, null, null, false));
        assertThrows(IllegalStateException.class, () -> result.recordCallInvalidForDeoptimization(call));
        assertThrows(IllegalStateException.class, () -> result.recordInfopoint(0, null, InfopointReason.SAFEPOINT));
        assertThrows(IllegalStateException.class, () -> result.recordImplicitException(0, 1, null));
        assertThrows(IllegalStateException.class, () -> result.getSitesWatermark(0, 0));
        assertThrows(IllegalStateException.class, () -> result.duplicateSites(0, start, 1, end, 1));
    }

    @Test
    public void testResetRestoresCodeEmissionState() {
        CompilationResult result = new CompilationResult("testresult");
        Call oldCall = result.recordCall(0, 1, null, null, false);
        result.recordCallInvalidForDeoptimization(oldCall);
        result.recordDataPatch(1, new DataSectionReference());
        result.recordSourceMapping(0, 8, null);
        result.setTargetCode(new byte[8], 8);
        result.clearCodeEmissionState();
        result.clearSourceMappings();

        // Open results can be reset for another emission attempt after releasing either payload.
        result.resetForEmittingCode();
        assertEquals(0, result.getTargetCodeSize());
        assertTrue(result.getInfopoints().isEmpty());
        assertTrue(result.getDataPatches().isEmpty());
        assertTrue(result.getExceptionHandlers().isEmpty());
        assertTrue(result.getMarks().isEmpty());
        assertTrue(result.getCodeAnnotations().isEmpty());
        assertTrue(result.getSourceMappings().isEmpty());
        assertTrue(result.isValidCallDeoptimizationState(oldCall));

        byte[] code = new byte[16];
        result.setTargetCode(code, 12);
        result.recordCall(0, 1, null, null, false);
        result.recordDataPatch(1, new DataSectionReference());
        result.recordExceptionHandler(2, 3);
        result.recordMark(4, new TestMarkId());
        result.addAnnotation(new CodeComment(5, "second emission"));
        result.recordSourceMapping(0, 12, null);
        result.close(null);
        assertSame(code, result.getTargetCode());
        assertEquals(12, result.getTargetCodeSize());
        assertEquals(1, result.getInfopoints().size());
        assertEquals(1, result.getDataPatches().size());
        assertEquals(1, result.getExceptionHandlers().size());
        assertEquals(1, result.getMarks().size());
        assertEquals(1, result.getCodeAnnotations().size());
        assertEquals(1, result.getSourceMappings().size());
        result.clearCodeEmissionState();
        assertThrows(IllegalStateException.class, result::getTargetCode);
    }

    @Test
    public void testResetPreservesRetainedCollections() {
        CompilationResult result = new CompilationResult("testresult");
        result.recordInfopoint(0, null, InfopointReason.SAFEPOINT);
        result.recordDataPatch(1, new DataSectionReference());
        result.recordExceptionHandler(2, 3);
        result.recordMark(4, new TestMarkId());
        result.addAnnotation(new CodeComment(5, "first emission"));
        result.recordSourceMapping(0, 8, null);
        var views = List.of(result.getInfopoints(), result.getDataPatches(), result.getExceptionHandlers(),
                        result.getMarks(), result.getCodeAnnotations(), result.getSourceMappings());
        var originals = views.stream().map(List::getFirst).toList();

        result.resetForEmittingCode();
        result.recordInfopoint(8, null, InfopointReason.SAFEPOINT);
        result.recordDataPatch(9, new DataSectionReference());
        result.recordExceptionHandler(10, 11);
        result.recordMark(12, new TestMarkId());
        result.addAnnotation(new CodeComment(13, "second emission"));
        result.recordSourceMapping(0, 16, null);

        for (int i = 0; i < views.size(); i++) {
            assertEquals(1, views.get(i).size());
            assertSame(originals.get(i), views.get(i).getFirst());
        }
        assertEquals(16, result.getSourceMappings().getFirst().getEndOffset());
    }

    private static final class TestMarkId implements CompilationResult.MarkId {
        @Override
        public String getName() {
            return "test";
        }

        public static final Object id = new Object();

        @Override
        public Object getId() {
            return id;
        }
    }

    @SuppressWarnings("unlikely-arg-type")
    @Test
    public void testCodeMark() {
        CompilationResult.MarkId id = new TestMarkId();
        CodeMark mark = new CodeMark(0, id);
        CodeMark markSame = new CodeMark(0, id);
        CodeMark markDifferent = new CodeMark(1000, new TestMarkId());
        assertTrue(mark.equals(mark));
        assertTrue(mark.equals(markSame));
        assertFalse(mark.equals(markDifferent));
        assertFalse(mark.equals(this));
        assertTrue(mark.toString().length() > 0); // test for NPE
    }
}
