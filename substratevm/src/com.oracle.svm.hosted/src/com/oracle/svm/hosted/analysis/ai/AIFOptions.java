package com.oracle.svm.hosted.analysis.ai;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;

/**
 * Configuration options for the Abstract Interpretation Framework (AIF).
 */
public class AIFOptions {

    @Option(help = "Enable the Abstract Interpretation Framework for program analysis and optimization.")
    public static final OptionKey<Boolean> RunAbstractInterpretation = new OptionKey<>(false);

    @Option(help = "Enable interprocedural abstract interpretation analysis.")
    public static final OptionKey<Boolean> InterproceduralAnalysis = new OptionKey<>(true);

    @Option(help = "Enable intraprocedural abstract interpretation analysis.")
    public static final OptionKey<Boolean> IntraproceduralAnalysis = new OptionKey<>(false);

    @Option(help = "Maximum recursion depth for interprocedural analysis.")
    public static final OptionKey<Integer> MaxRecursionDepth = new OptionKey<>(5);

    @Option(help = "Maximum call stack depth for interprocedural analysis.")
    public static final OptionKey<Integer> MaxCallStackDepth = new OptionKey<>(10);

    @Option(help = "Maximum number of iterations before applying widening in fixpoint computation.")
    public static final OptionKey<Integer> MaxJoinIterations = new OptionKey<>(10);

    @Option(help = "Maximum number of widening iterations before forcing convergence in fixpoint computation.")
    public static final OptionKey<Integer> MaxWidenIterations = new OptionKey<>(5);

    @Option(help = "K value for K-CFA (k-call-site sensitivity). Higher values increase precision but also analysis time.")
    public static final OptionKey<Integer> KCFADepth = new OptionKey<>(2);

    @Option(help = "Enable bounds check elimination based on abstract interpretation results.")
    public static final OptionKey<Boolean> EnableBoundsCheckElimination = new OptionKey<>(true);

    @Option(help = "Enable constant propagation and folding based on abstract interpretation results.")
    public static final OptionKey<Boolean> EnableConstantPropagation = new OptionKey<>(true);

    @Option(help = "Enable dead branch elimination when conditions are proven to be always true or false.")
    public static final OptionKey<Boolean> EnableDeadBranchElimination = new OptionKey<>(true);

    @Option(help = "Enable method inlining when return values can be statically determined.")
    public static final OptionKey<Boolean> EnableConstantMethodInlining = new OptionKey<>(true);

    @Option(help = "Run graph cleanup and dead code elimination after applying analysis results.")
    public static final OptionKey<Boolean> EnableGraphCleanup = new OptionKey<>(true);

    @Option(help = "Run canonicalizer phase after abstract interpretation transformations.")
    public static final OptionKey<Boolean> RunCanonicalizerAfterAI = new OptionKey<>(true);

    @Option(help = "Skip analysis of java.lang.* methods to improve performance.")
    public static final OptionKey<Boolean> SkipJavaLangMethods = new OptionKey<>(true);

    @Option(help = "Skip analysis of JNI methods.")
    public static final OptionKey<Boolean> SkipJNIMethods = new OptionKey<>(true);

    @Option(help = "Skip analysis of Spring framework methods.")
    public static final OptionKey<Boolean> SkipSpringMethods = new OptionKey<>(true);

    @Option(help = "Skip analysis of Micronaut framework methods.")
    public static final OptionKey<Boolean> SkipMicronautMethods = new OptionKey<>(true);

    @Option(help = "Regex pattern for method names to exclude from analysis (e.g., '.*\\.internal\\..*').")
    public static final OptionKey<String> ExcludeMethodPattern = new OptionKey<>("");

    @Option(help = "Regex pattern for method names to include in analysis.")
    public static final OptionKey<String> IncludeMethodPattern = new OptionKey<>("");

    @Option(help = "Set verbosity level for abstract interpretation logging: SILENT, ERROR, WARN, INFO, DEBUG.")
    public static final OptionKey<String> AILogLevel = new OptionKey<>("INFO");

    @Option(help = "Enable logging to console for abstract interpretation.")
    public static final OptionKey<Boolean> AILogToConsole = new OptionKey<>(false);

    @Option(help = "Enable logging to file for abstract interpretation.")
    public static final OptionKey<Boolean> AILogToFile = new OptionKey<>(true);

    @Option(help = "File path for abstract interpretation log output.")
    public static final OptionKey<String> AILogFilePath = new OptionKey<>("ai_analysis.log");

    @Option(help = "Enable IGV (Ideal Graph Visualizer) dumps for abstract interpretation.")
    public static final OptionKey<Boolean> AIEnableIGVDump = new OptionKey<>(true);

    @Option(help = "Export analyzed graphs to JSON format for inspection.")
    public static final OptionKey<Boolean> AIExportGraphToJSON = new OptionKey<>(false);

    @Option(help = "Directory path for JSON graph exports.")
    public static final OptionKey<String> AIJSONExportPath = new OptionKey<>("ai_graphs");

    @Option(help = "Print detailed statistics about abstract interpretation analysis and optimizations.")
    public static final OptionKey<Boolean> PrintAIStatistics = new OptionKey<>(false);

    @Option(help = "Print summary of optimizations performed per method.")
    public static final OptionKey<Boolean> PrintOptimizationSummary = new OptionKey<>(false);

    @Option(help = "Print list of most-optimized methods.")
    public static final OptionKey<Boolean> PrintTopOptimizedMethods = new OptionKey<>(false);

    @Option(help = "Number of top-optimized methods to display in statistics.")
    public static final OptionKey<Integer> TopOptimizedMethodsCount = new OptionKey<>(10);

    @Option(help = "Enable parallel execution of interprocedural analysis from multiple root methods.", type = OptionType.Expert)
    public static final OptionKey<Boolean> ParallelInterproceduralAnalysis = new OptionKey<>(false);

    @Option(help = "Number of worker threads for parallel analysis. -1 uses available processors.", type = OptionType.Expert)
    public static final OptionKey<Integer> AnalysisThreadCount = new OptionKey<>(-1);

    @Option(help = "Enable narrowing on conditional edges (if-then-else) to improve precision.", type = OptionType.Expert)
    public static final OptionKey<Boolean> EnableConditionalNarrowing = new OptionKey<>(true);

    @Option(help = "Enable early summary creation for recursive methods.", type = OptionType.Expert)
    public static final OptionKey<Boolean> EnableEarlySummaries = new OptionKey<>(true);

    @Option(help = "Maximum size of interval domain before widening to infinity.", type = OptionType.Expert)
    public static final OptionKey<Long> IntervalWideningThreshold = new OptionKey<>(1000L);

    @Option(help = "Enable array length tracking in dataflow analysis for better bounds check elimination.", type = OptionType.Expert)
    public static final OptionKey<Boolean> TrackArrayLengths = new OptionKey<>(true);

    @Option(help = "Fail analysis on errors instead of continuing with conservative approximations.", type = OptionType.Expert)
    public static final OptionKey<Boolean> FailOnAnalysisError = new OptionKey<>(false);
}
