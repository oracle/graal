/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import static jdk.graal.compiler.hotspot.ProfileReplaySupport.Options.LoadProfiles;
import static jdk.graal.compiler.hotspot.ProfileReplaySupport.Options.ProfileMethodFilter;
import static jdk.graal.compiler.hotspot.ProfileReplaySupport.Options.SaveProfiles;
import static jdk.graal.compiler.hotspot.ProfileReplaySupport.Options.StrictProfiles;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.debug.PathUtilities;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.java.StableMethodNameFormatter;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.AccessFieldNode;
import jdk.graal.compiler.nodes.spi.StableProfileProvider;
import jdk.graal.compiler.nodes.spi.StableProfileProvider.LambdaNameFormatter;
import jdk.graal.compiler.nodes.spi.StableProfileProvider.TypeFilter;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.util.json.JsonParser;
import jdk.graal.compiler.util.json.JsonPrettyWriter;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Support class encapsulating profile replay support. Contains functionality to save, load and
 * verify loaded profiles.
 */
public final class ProfileReplaySupport {

    public static class Options {
        // @formatter:off
        @Option(help = "Save per compilation profile information.", type = OptionType.Debug)
        public static final OptionKey<Boolean> SaveProfiles = new OptionKey<>(false);
        @Option(help = "Allow multiple compilations of the same method by overriding existing profiles.", type = OptionType.Debug)
        public static final OptionKey<Boolean> OverrideProfiles = new OptionKey<>(false);
        @Option(help = "Path for saving compilation profiles. " +
                       "If the value is omitted the debug dump path will be used.", type = OptionType.Debug)
        public static final OptionKey<String> SaveProfilesPath = new OptionKey<>(null);
        @Option(help = "Load per compilation profile information.", type = OptionType.Debug)
        public static final OptionKey<String> LoadProfiles = new OptionKey<>(null);
        @Option(help = "Restrict saving or loading of profiles based on this filter. " +
                       "See the MethodFilter option for the pattern syntax.", type = OptionType.Debug)
        public static final OptionKey<String> ProfileMethodFilter = new OptionKey<>(null);
        @Option(help = "Throw an error if an attempt is made to overwrite/update a profile loaded from disk.", type = OptionType.Debug)
        public static final OptionKey<Boolean> StrictProfiles = new OptionKey<>(true);
        @Option(help = "Print to stdout when a profile is loaded.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintProfileLoading = new OptionKey<>(false);
        @Option(help = "Print to stdout when a compilation performed with different profiles generates different " +
                       "frontend IR.", type = OptionType.Debug)
        public static final OptionKey<Boolean> WarnAboutGraphSignatureMismatch = new OptionKey<>(true);
        @Option(help = "Print to stdout when a compilation performed with different profiles generates different " +
                       "backend code.", type = OptionType.Debug)
        public static final OptionKey<Boolean> WarnAboutCodeSignatureMismatch = new OptionKey<>(true);
        @Option(help = "Print to stdout when requesting profiling info not present in a loaded profile.", type = OptionType.Debug)
        public static final OptionKey<Boolean> WarnAboutNotCachedLoadedAccess = new OptionKey<>(true);
        // @formatter:on
    }

    private final LambdaNameFormatter lambdaNameFormatter;
    /**
     * Tri-state capturing the expected result of the compilation. Potential values are
     * {@code null,True,False}.
     *
     * If we are running a regular compilation without loading profiles it will always be
     * {@code null}.
     *
     * If we are running a profile replay compilation this field will contain the result of reading
     * the entry of the profile file. The original value written represents the expression
     * {@code originalCodeResult!=null}. This means if the original compile produced a result
     * without errors it will be set to {@code True}, else {@code False}.
     */
    private final Boolean expectedResult;
    private final String expectedCodeSignature;
    private final String expectedGraphSignature;
    private final MethodFilter profileFilter;
    private final TypeFilter profileSaveFilter;

    private ProfileReplaySupport(LambdaNameFormatter lambdaNameFormatter, Boolean expectedResult, String expectedCodeSignature, String expectedGraphSignature, MethodFilter profileFilter,
                    TypeFilter profileSaveFilter) {
        this.lambdaNameFormatter = lambdaNameFormatter;
        this.expectedResult = expectedResult;
        this.expectedCodeSignature = expectedCodeSignature;
        this.expectedGraphSignature = expectedGraphSignature;
        this.profileFilter = profileFilter;
        this.profileSaveFilter = profileSaveFilter;
    }

    public Boolean getExpectedResult() {
        return expectedResult;
    }

    /**
     * Start the profile record/replay process. If {@link Options#SaveProfiles} is set, this method
     * installs {@link StableProfileProvider}s to record profiling information that can be
     * subsequently saved with {@link #profileReplayEpilogue}. If {@link Options#LoadProfiles} is
     * set, the method initializes {@link StableProfileProvider}s with profiles loaded from disk.
     * <p>
     * Note that there might be cases where profiles cannot be restored, even if they are present in
     * the on-disk representation. For example, the profiles of {@code invokedynamic} call sites
     * (e.g. lambda expressions) cannot be restored due to unpredictable type names of the lambda
     * objects. Depending on {@link Options#StrictProfiles}, the {@link StableProfileProvider}s will
     * either throw an exception or emit a warning in this case.
     * </p>
     */
    public static ProfileReplaySupport profileReplayPrologue(DebugContext debug, int entryBCI, ResolvedJavaMethod method,
                    StableProfileProvider profileProvider, TypeFilter profileSaveFilter) {
        if (SaveProfiles.getValue(debug.getOptions()) || LoadProfiles.getValue(debug.getOptions()) != null) {
            LambdaNameFormatter lambdaNameFormatter = new LambdaNameFormatter() {
                private final StableMethodNameFormatter stableFormatter = new StableMethodNameFormatter(true);

                @Override
                public boolean isLambda(ResolvedJavaMethod m) {
                    // Include method handles here as well
                    return LambdaUtils.isLambdaType(m.getDeclaringClass()) || StableMethodNameFormatter.isMethodHandle(m.getDeclaringClass());
                }

                @Override
                public String formatLamdaName(ResolvedJavaMethod m) {
                    return stableFormatter.apply(m);
                }
            };
            Boolean expectedResult = null;
            String expectedCodeSignature = null;
            String expectedGraphSignature = null;
            MethodFilter profileFilter = null;
            String filterString = ProfileMethodFilter.getValue(debug.getOptions());
            profileFilter = filterString == null || filterString.isEmpty() ? MethodFilter.matchAll() : MethodFilter.parse(filterString);
            if (LoadProfiles.getValue(debug.getOptions()) != null && profileFilter.matches(method)) {
                Path loadDir = Paths.get(LoadProfiles.getValue(debug.getOptions()));
                try (Stream<Path> files = Files.list(loadDir)) {
                    String s = PathUtilities.sanitizeFileName(method.format("%h.%n(%p)%r"));
                    boolean foundOne = false;
                    for (Path path : files.filter(x -> x.toString().contains(s)).filter(x -> x.toString().endsWith(".glog")).collect(Collectors.toList())) {
                        EconomicMap<String, Object> map = JsonParser.parseDict(new FileReader(path.toFile()));
                        if (entryBCI == (int) map.get("entryBCI")) {
                            foundOne = true;
                            expectedResult = (Boolean) map.get("result");
                            expectedCodeSignature = (String) map.get("codeSignature");
                            expectedGraphSignature = (String) map.get("graphSignature");
                            profileProvider.load(map, method.getDeclaringClass(), Options.WarnAboutNotCachedLoadedAccess.getValue(debug.getOptions()), lambdaNameFormatter);
                            if (StrictProfiles.getValue(debug.getOptions())) {
                                profileProvider.freeze();
                            }
                            if (Options.PrintProfileLoading.getValue(debug.getOptions())) {
                                TTY.println("Loaded profile data from " + path);
                            }
                            break;

                        }
                    }
                    if (Options.StrictProfiles.getValue(debug.getOptions()) && !foundOne) {
                        throw GraalError.shouldNotReachHere(String.format("No file for method %s found in %s, strict profiles, abort", s, loadDir)); // ExcludeFromJacocoGeneratedReport
                    }
                } catch (IOException e) {
                    boolean wasSet = profileReplayPrologueExceptionPrinted.getAndSet(true);
                    if (!wasSet) {
                        e.printStackTrace();
                    }
                }
            }
            return new ProfileReplaySupport(lambdaNameFormatter, expectedResult, expectedCodeSignature, expectedGraphSignature, profileFilter, profileSaveFilter);
        }
        return null;
    }

    /**
     * Guard to prevent flood of stack traces when CTRL-C'ing tasks such as
     * {@code mx benchmark compile-all:fuzzedClasses}.
     */
    private static final AtomicBoolean profileReplayPrologueExceptionPrinted = new AtomicBoolean();

    /**
     * Finishes a previously started profile record/replay (see {@link #profileReplayPrologue}.
     * Both, for record and replay, the method validates various expectations (see
     * {@link Options#WarnAboutCodeSignatureMismatch} and
     * {@link Options#WarnAboutGraphSignatureMismatch}). If {@link Options#SaveProfiles} is set, the
     * method additionally saves the previously collected profiles to the given profile path.
     */
    public void profileReplayEpilogue(DebugContext debug, CompilationResult result, StructuredGraph graph, StableProfileProvider profileProvider, CompilationIdentifier compilationId,
                    int entryBCI, ResolvedJavaMethod method) {
        if ((SaveProfiles.getValue(debug.getOptions()) || LoadProfiles.getValue(debug.getOptions()) != null) && profileFilter.matches(method)) {
            String codeSignature = null;
            String graphSignature = null;
            if (result != null) {
                try {
                    codeSignature = result.getCodeSignature();
                    assert graph != null;
                    String s = getCanonicalGraphString(graph);
                    graphSignature = CompilationResult.getSignature(s.getBytes(StandardCharsets.UTF_8));
                } catch (Throwable t) {
                    throw debug.handle(t);
                }
            }
            if (Options.WarnAboutCodeSignatureMismatch.getValue(debug.getOptions())) {
                if (expectedCodeSignature != null && !Objects.equals(codeSignature, expectedCodeSignature)) {
                    TTY.printf("%s %s codeSignature differs %s != %s%n", method.format("%H.%n(%P)%R"), entryBCI, codeSignature, expectedCodeSignature);
                }
            }
            if (Options.WarnAboutGraphSignatureMismatch.getValue(debug.getOptions())) {
                if (expectedGraphSignature != null && !Objects.equals(graphSignature, expectedGraphSignature)) {
                    TTY.printf("%s %s graphSignature differs %s != %s%n", method.format("%H.%n(%P)%R"), entryBCI, graphSignature, expectedGraphSignature);
                }
            }
            if (SaveProfiles.getValue(debug.getOptions())) {
                try {
                    EconomicMap<String, Object> map = EconomicMap.create();
                    map.put("identifier", compilationId.toString());
                    map.put("method", method.format("%H.%n(%P)%R"));
                    map.put("entryBCI", entryBCI);
                    map.put("codeSignature", codeSignature);
                    map.put("graphSignature", graphSignature);
                    map.put("result", result != null);
                    profileProvider.recordProfiles(map, profileSaveFilter, lambdaNameFormatter);
                    String path = null;
                    if (Options.SaveProfilesPath.getValue(debug.getOptions()) != null) {
                        String fileName = PathUtilities.sanitizeFileName(method.format("%h.%n(%p)%r") + ".glog");
                        String dirName = Options.SaveProfilesPath.getValue(debug.getOptions());
                        path = Paths.get(dirName).resolve(fileName).toString();
                        if (new File(path).exists() && !Options.OverrideProfiles.getValue(debug.getOptions())) {
                            throw new InternalError("Profile file for path " + path + " exists already");
                        }
                    } else {
                        path = debug.getDumpPath(".glog", false, false);
                    }
                    try (JsonPrettyWriter writer = new JsonPrettyWriter(new PrintWriter(PathUtilities.openOutputStream(path)))) {
                        writer.print(map);
                    }
                } catch (Throwable t) {
                    throw debug.handle(t);
                }
            }
        }
    }

    private static String getCanonicalGraphString(StructuredGraph graph) {
        SchedulePhase.runWithoutContextOptimizations(graph, SchedulePhase.SchedulingStrategy.EARLIEST);
        StructuredGraph.ScheduleResult scheduleResult = graph.getLastSchedule();
        NodeMap<Integer> canonicalId = graph.createNodeMap();
        int nextId = 0;
        StringBuilder result = new StringBuilder();
        for (BasicBlock<?> block : scheduleResult.getCFG().getBlocks()) {
            result.append("Block ").append(block).append(' ');
            if (block == scheduleResult.getCFG().getStartBlock()) {
                result.append("* ");
            }
            result.append("-> ");
            for (int i = 0; i < block.getSuccessorCount(); i++) {
                BasicBlock<?> succ = block.getSuccessorAt(i);
                result.append(succ).append(' ');
            }
            result.append(String.format("%n"));
            for (Node node : scheduleResult.getBlockToNodesMap().get(block)) {
                if (node instanceof ValueNode && node.isAlive()) {
                    if (!(node instanceof ConstantNode)) {
                        int id;
                        if (canonicalId.get(node) != null) {
                            id = canonicalId.get(node);
                        } else {
                            id = nextId++;
                            canonicalId.set(node, id);
                        }
                        String name = node.getClass().getSimpleName();
                        result.append("  ").append(id).append('|').append(name);
                        if (node instanceof AccessFieldNode) {
                            result.append('#');
                            result.append(((AccessFieldNode) node).field());
                        }
                        result.append("    (");
                        result.append(node.usages().filter(n -> !(n instanceof FrameState)).count());
                        result.append(')');
                        result.append(String.format("%n"));
                    }
                }
            }
        }
        return result.toString();
    }

}
