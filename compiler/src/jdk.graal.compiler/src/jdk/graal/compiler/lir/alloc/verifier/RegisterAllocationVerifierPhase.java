package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.AllocationPhase;
import jdk.graal.compiler.options.EnumOptionKey;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.TargetDescription;

public class RegisterAllocationVerifierPhase extends AllocationPhase {
    public static class Options {
        @Option(help = "Verify that register allocation is indeed, correct", type = OptionType.Debug)
        public static final OptionKey<Boolean> EnableRAVerifier = new OptionKey<>(false);

        @Option(help = "Select which way you want to resolve phi arguments.", type = OptionType.Debug)
        public static final EnumOptionKey<PhiResolution> RAPhiResolution = new EnumOptionKey<>(PhiResolution.FromUsage);

        @Option(help = "Should constants be moved to variables when needed", type = OptionType.Debug)
        public static final OptionKey<Boolean> MoveConstants = new OptionKey<>(true);
    }

    public static String[] ignoredTestCases = {
            // Disable Truffle Related Tests
            // New instructions being added makes the verifier not work, investigate why prealloc is not run.
            "truffle", "Truffle", "polyglot", "Polyglot", "Root[]", "InstrumentationTestLanguage", "NFITest", "intCaller1", "callee"};

    public static boolean isIgnored(String compUnitName) {
        for (String ignoredTest : ignoredTestCases) {
            if (compUnitName.contains(ignoredTest)) {
                return true;
            }
        }
        return false;
    }

    private PhiResolution phiResolution;
    private boolean moveConstants;

    public RegisterAllocationVerifierPhase(OptionValues options) {
        this.phiResolution = Options.RAPhiResolution.getValue(options);
        this.moveConstants = Options.MoveConstants.getValue(options);
    }

    protected PreRegisterAllocationPhase preallocPhaseRAVerifier;

    public AllocationPhase getPreAllocPhase() {
        this.preallocPhaseRAVerifier = new PreRegisterAllocationPhase(phiResolution, moveConstants);
        return this.preallocPhaseRAVerifier;
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        var compUnitName = lirGenRes.getCompilationUnitName();
        // if (!compUnitName.contains("DirectByteBufferTest.unalignedWriteSnippet")) {
        //     return;
        // }

        if (RegisterAllocationVerifierPhase.isIgnored(compUnitName)) {
            // Skipping truffle test cases because they cause trouble with
            // prealloc and getVerifierInstructions, because blocks and
            // instructions that aren't made only by the RA are added.
            return;
        }

        assert this.preallocPhaseRAVerifier != null : "Phase before register allocation was not run, cannot verify it.";

        var instructions = this.preallocPhaseRAVerifier.getVerifierInstructions(lirGenRes.getLIR());
        var verifier = new RegisterAllocationVerifier(lirGenRes.getLIR(), instructions, this.phiResolution);

        try {
            verifier.run();
        } catch (RAVException e) {
            System.err.println("Verification failed - " + compUnitName);
        }
    }
}
