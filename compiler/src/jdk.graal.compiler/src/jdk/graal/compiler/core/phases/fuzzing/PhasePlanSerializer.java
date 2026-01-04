/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.phases.fuzzing;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Formatter;
import java.util.List;
import java.util.Map;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.util.EconomicHashMap;

/**
 * Provides tools to save a serialized version of the {@link Suites} to a file and load the
 * {@link Suites} from the file. This tool can only load phase plans that are permutations and/or
 * subsets of the original suites and can therefore not load a non-default phase. No check is
 * performed to ensure the loaded phase plan is legal.
 */
public final class PhasePlanSerializer {

    @SuppressWarnings("unchecked")
    private static <C> String phaseToString(BasePhase<? super C> phase, int level, String tier) {
        Formatter buf = new Formatter();
        String indent = level == 0 ? "" : new String(new char[level]).replace('\0', ' ');
        buf.format("%s%s in %s with hashCode=%s", indent, phase.getClass().getName(), tier, phase.hashCode());
        if (phase instanceof PhaseSuite) {
            List<BasePhase<? super C>> subPhases = ((PhaseSuite<C>) phase).getPhases();
            for (BasePhase<? super C> subPhase : subPhases) {
                buf.format("%n%s", phaseToString(subPhase, level + 1, tier));
            }
        }
        return buf.toString();
    }

    private static <C> void savePhaseSuite(PhaseSuite<C> phaseSuite, DataOutputStream out, String tier) throws IOException {
        List<BasePhase<? super C>> phases = phaseSuite.getPhases();
        out.writeInt(phases.size());
        for (BasePhase<? super C> phase : phases) {
            out.writeUTF(phaseToString(phase, 0, tier));
        }
    }

    private static <C> PhaseSuite<C> loadPhaseSuite(DataInputStream in, Map<String, BasePhase<? super C>> lookup) throws IOException {
        PhaseSuite<C> phaseSuite = new PhaseSuite<>();
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            String key = in.readUTF();
            BasePhase<? super C> phase = lookup.get(key);
            if (phase == null) {
                GraalError.shouldNotReachHere("No phase could be found matching " + key); // ExcludeFromJacocoGeneratedReport
            }
            phaseSuite.appendPhase(phase);
        }
        return phaseSuite;
    }

    private static <C> void collect(Map<String, BasePhase<? super C>> lookup, PhaseSuite<C> phaseSuite, String tier) {
        for (BasePhase<? super C> phase : phaseSuite.getPhases()) {
            String key = phaseToString(phase, 0, tier);
            lookup.put(key, phase);
        }
    }

    /**
     * Serializes the given {@link Suites} and saves it in a file.
     */
    public static void savePhasePlan(String fileName, Suites phasePlan) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (DataOutputStream dos = new DataOutputStream(baos)) {
                savePhasePlan(dos, phasePlan);
            }
            Files.write(Paths.get(fileName), baos.toByteArray());
        } catch (IOException e) {
            GraalError.shouldNotReachHere(e, "Error saving phase plan to " + fileName); // ExcludeFromJacocoGeneratedReport
        }
    }

    /**
     * Serializes the given {@link Suites} and saves it in the given {@link DataOutputStream}.
     */
    public static void savePhasePlan(DataOutputStream dos, Suites phasePlan) throws IOException {
        savePhaseSuite(phasePlan.getHighTier(), dos, "high tier");
        savePhaseSuite(phasePlan.getMidTier(), dos, "mid tier");
        savePhaseSuite(phasePlan.getLowTier(), dos, "low tier");
    }

    /**
     * Creates {@link Suites} by loading the suites' serialized version contained in the given file.
     */
    public static <C> Suites loadPhasePlan(String fileName, Suites originalSuites) {
        try (DataInputStream in = new DataInputStream(new FileInputStream(fileName))) {
            return loadPhasePlan(in, originalSuites);
        } catch (IOException e) {
            throw new GraalError(e, "Error loading phase plan from %s", fileName);
        }
    }

    /**
     * Creates {@link Suites} by loading the suites' serialized version contained in the given
     * {@link DataInputStream}.
     */
    @SuppressWarnings("unchecked")
    public static <C> Suites loadPhasePlan(DataInputStream in, Suites originalSuites) throws IOException {
        Map<String, BasePhase<? super C>> lookup = new EconomicHashMap<>();
        collect(lookup, ((PhaseSuite<C>) originalSuites.getHighTier()), "high tier");
        collect(lookup, ((PhaseSuite<C>) originalSuites.getMidTier()), "mid tier");
        collect(lookup, ((PhaseSuite<C>) originalSuites.getLowTier()), "low tier");

        PhaseSuite<HighTierContext> highTier = (PhaseSuite<HighTierContext>) loadPhaseSuite(in, lookup);
        PhaseSuite<MidTierContext> midTier = (PhaseSuite<MidTierContext>) loadPhaseSuite(in, lookup);
        PhaseSuite<LowTierContext> lowTier = (PhaseSuite<LowTierContext>) loadPhaseSuite(in, lookup);
        return new Suites(highTier, midTier, lowTier);
    }
}
