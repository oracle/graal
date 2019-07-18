package com.oracle.truffle.st;

import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Example for simple version of an expression coverage instrument.
 *
 * The instrument {@link #coverageMap keeps track} of all loaded {@link SourceSection}s and all
 * coverd (i.e. executed) {@link SourceSection}s for each {@link Source}. At the end of the
 * execution this information can be used to calculate coverage.
 *
 * The instrument is registered with the Truffle framework using the {@link Registration}
 * annotation. The annotation specifies a unique {@link Registration#id}, a human readable
 * {@link Registration#name} and {@link Registration#version} for the instrument. It also specifies
 * all service classes that the instrument exports to other instruments and, exceptionally, tests.
 * In this case the instrument itself is exported as a service and used in the
 * SimpleCoverageInstrumentTest.
 * 
 * NOTE: Fot the registration annotation to work the truffle dsl processor must be used (i.e. Must
 * be a dependency. This is so in this maven project, as can be seen in the pom file.
 */
@Registration(id = SimpleCoverageInstrument.ID, name = "Simple Code Coverage", version = "0.1", services = SimpleCoverageInstrument.class)
public final class SimpleCoverageInstrument extends TruffleInstrument {

    // @formatter:off
    /**
     * Look at {@link #onCreate(Env)} and {@link #getOptionDescriptors()} for more info.
     */
    @Option(name = "", help = "Enable Simple Coverage (default: false).", category = OptionCategory.USER, stability = OptionStability.STABLE) 
    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);

    /**
     * Look at {@link #onCreate(Env)} and {@link #getOptionDescriptors()} for more info.
     */
    @Option(name = "PrintCoverage", help = "Print coverage to stdout on process exit (default: true).", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<Boolean> PRINT_COVERAGE = new OptionKey<>(true);
    // @formatter:on

    public static final String ID = "simple-code-coverage";

    /**
     * The instrument keeps a mapping between a {@link Source} and {@link Coverage coverage} data
     * for that source. Coverage tracks loaded and covered {@link SourceSection} during execution.
     */
    final Map<Source, Coverage> coverageMap = new HashMap<>();

    public synchronized Map<Source, Coverage> getCoverageMap() {
        return Collections.unmodifiableMap(coverageMap);
    }

    /**
     * Each instrument must override the
     * {@link TruffleInstrument#onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env)}
     * method.
     *
     * This method is used to properly initialize the instrument. A common practice is to use the
     * {@link Option} system to enable and configure the instrument, as is done in this method.
     * Defining {@link Option}s as is shown in {@link #ENABLED} and {@link #PRINT_COVERAGE}, and
     * their usage can be seen in the SimpleCoverageInstrumentTest when the context is being
     * created. Using them from the command line is shown in the simpletool.sh script.
     *
     * @param env the environment for the instrument. Allows us to read the {@link Option}s, input
     *            and output streams to be used for reading and writing, as well as
     *            {@link Env#registerService(java.lang.Object) registering} and
     *            {@link Env#lookup(com.oracle.truffle.api.InstrumentInfo, java.lang.Class) looking
     *            up} services.
     */
    @Override
    protected void onCreate(final Env env) {
        final OptionValues options = env.getOptions();
        if (ENABLED.getValue(options)) {
            enable(env);
            if (PRINT_COVERAGE.getValue(options)) {
                ensurePrintCoverage(env);
            }
            env.registerService(this);
        }
    }

    /**
     * Enable the instrument.
     *
     * In this method we enable and configure the instrument. We do this by first creating a
     * {@link SourceSectionFilter} instance in order to specify exactly which parts of the source
     * code we are interested in. In this particular case, we are interested in expressions. Since
     * Truffle Instruments are language agnostic, they rely on language implementers to tag AST
     * nodes with adequate tags. This, we tell our {@link SourceSectionFilter.Builder} that we are
     * care about AST nodes {@link SourceSectionFilter.Builder#tagIs(java.lang.Class...) tagged}
     * with {@link ExpressionTag}. We also tell it we don't care about AST nodes
     * {@link SourceSectionFilter.Builder#includeInternal(boolean) internal} to languages.
     *
     * After than, we use the {@link Env enviroment} to obtain the {@link Instrumenter}, which
     * allows us to specify in which way we wish to instrument the AST.
     *
     * Firstly, we
     * {@link Instrumenter#attachLoadSourceListener(com.oracle.truffle.api.instrumentation.SourceFilter, com.oracle.truffle.api.instrumentation.LoadSourceListener, boolean)
     * attach attach} our own {@link GatherSourceSectionsListener listener} to loading source
     * section events. Each the a {@link SourceSection} is loaded, our listener is notified, so our
     * instrument is always aware of all loaded code. Note that we have specified the filter defined
     * earlier as a constraint, so we are not notified if internal code is loaded.
     *
     * Secondly, we
     * {@link Instrumenter#attachExecutionEventFactory(com.oracle.truffle.api.instrumentation.SourceSectionFilter, com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory)
     * attach} our {@link CoverageEventFactory node factory} using the same filter. This factory
     * produces {@link Node Truffle Nodes} that will be inserted into the AST at positions specified
     * by the filter. Each of the inserted nodes will, once executed, remove the corresponding
     * source section from the {@link #coverageMap set of unexecuted source sections}.
     *
     * @param env The environment, used to get the {@link Instrumenter}
     */
    private void enable(final Env env) {
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class).includeInternal(false).build();
        Instrumenter instrumenter = env.getInstrumenter();
        instrumenter.attachLoadSourceSectionListener(filter, new GatherSourceSectionsListener(this), true);
        instrumenter.attachExecutionEventFactory(filter, new CoverageEventFactory(this));
    }

    /**
     * Ensures that the coverage info gathered by the instrument is printed at the end of execution.
     *
     * This is done by adding a shutdown hook to the runtime, so that when the execution is over,
     * the {@link #printResults(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env)
     * results are printed}
     *
     * @param env
     */
    private void ensurePrintCoverage(final Env env) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> printResults(env)));
    }

    /**
     * Print the coverage results for each source.
     *
     * The printing is one the the {@link Env#out output stream} specified by the {@link Env
     * enviroment}.
     *
     * @param env
     */
    private synchronized void printResults(final Env env) {
        final PrintStream printStream = new PrintStream(env.out());
        for (Source source : coverageMap.keySet()) {
            final String path = source.getPath();
            final int lineCount = source.getLineCount();
            final List<Integer> notYetCoveredLineNumbers = nonCoveredLineNumbers(source);
            final int notYetCoveredSize = notYetCoveredLineNumbers.size();
            double coveredPercentage = 100 * ((double) lineCount - notYetCoveredSize) / lineCount;
            printStream.println("==");
            printStream.println("Coverage of " + path + " is " + String.format("%.2f%%", coveredPercentage));
            for (int i = 1; i <= source.getLineCount(); i++) {
                String covered = notYetCoveredLineNumbers.contains(i) ? "-" : "+";
                printStream.println(String.format("%s %s", covered, source.getCharacters(i)));
            }
        }
    }

    /**
     * @param source
     * @return A sorted list of line numbers for not-yet-covered lines of source code in the given
     *         {@link Source}
     */
    public synchronized List<Integer> nonCoveredLineNumbers(final Source source) {
        return coverageMap.get(source).nonCoveredLineNumbers();
    }

    /**
     * Which {@link OptionDescriptors} are used for this instrument.
     *
     * If the {@link TruffleInstrument} uses {@link Option}s, it is nesesery to specify which
     * {@link Option}s. The {@link OptionDescriptors} is automatically generated from this class due
     * to the {@link Option} annotation. In our case, this is the
     * {@code SimpleCodeCoverageInstrumentOptionDescriptors} class.
     *
     * @return The class generated by the {@link Option.Group} annotation
     */
    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new SimpleCoverageInstrumentOptionDescriptors();
    }

    /**
     * Called when a new {@link SourceSection} is loaded. We can update our {@link #coverageMap}.
     * 
     * @param sourceSection the newly loaded {@link SourceSection}
     */
    synchronized void addLoaded(SourceSection sourceSection) {
        final Coverage coverage = coverageMap.computeIfAbsent(sourceSection.getSource(), (Source s) -> new Coverage());
        coverage.addLoaded(sourceSection);
    }

    /**
     * Called after a {@link SourceSection} is executed, and thus covered. We can update our
     * {@link #coverageMap}.
     * 
     * @param sourceSection the executed {@link SourceSection}
     */
    synchronized void addCovered(SourceSection sourceSection) {
        final Coverage coverage = coverageMap.get(sourceSection.getSource());
        coverage.addCovered(sourceSection);
    }

}
