#include "llvm/ADT/StringRef.h"
#include "llvm/Analysis/TargetLibraryInfo.h"
#include "llvm/Bitcode/BitcodeReader.h"
#include "llvm/Bitcode/BitcodeWriter.h"
#include "llvm/CodeGen/CommandFlags.inc"
#include "llvm/FuzzMutate/FuzzerCLI.h"
#include "llvm/FuzzMutate/IRMutator.h"
#include "llvm/FuzzMutate/Operations.h"
#include "llvm/FuzzMutate/RandomIRBuilder.h"
#include "llvm/IR/Constants.h"
#include "llvm/IR/LLVMContext.h"
#include "llvm/IR/LegacyPassManager.h"
#include "llvm/IR/Instructions.h"
#include "llvm/IR/InstIterator.h"
#include "llvm/IR/IRPrintingPasses.h"
#include "llvm/IR/Module.h"
#include "llvm/IR/Verifier.h"
#include "llvm/IRReader/IRReader.h"
#include "llvm/Support/DataTypes.h"
#include "llvm/Support/Debug.h"
#include "llvm/Support/SourceMgr.h"
#include "llvm/Support/TargetRegistry.h"
#include "llvm/Support/TargetSelect.h"
#include "llvm/Target/TargetMachine.h"
#include "llvm/Support/ToolOutputFile.h"

#define DEBUG_TYPE "isel-fuzzer"

using namespace llvm;

static cl::opt<char>
OptLevel("O",
         cl::desc("Optimization level. [-O0, -O1, -O2, or -O3] "
                  "(default = '-O2')"),
         cl::Prefix,
         cl::ZeroOrMore,
         cl::init(' '));

static cl::opt<std::string>
TargetTriple("mtriple", cl::desc("Override target triple for module"));
static cl::opt<unsigned>
NumberMutations("nrmutations", cl::desc("Number of reduction mutations to be performed"), cl::init(1));
static cl::opt<unsigned>
Seed("seed", cl::desc("Random seed used when selecting instructions for mutation"), cl::init(0));
static cl::opt<std::string>
OutputFilename("o", 
         cl::desc("Override output filename"),
         cl::value_desc("filename"),
         cl::init("-"));

static std::unique_ptr<TargetMachine> TM;
static std::unique_ptr<IRMutator> Mutator;

class InstReducerIRStrategy : public InstDeleterIRStrategy {
public:
  using InstDeleterIRStrategy::mutate;
 void mutate(Function &F, RandomIRBuilder &IB) override {
  auto RS = makeSampler<Instruction *>(IB.Rand);
  for (Instruction &Inst : instructions(F)) {
    // TODO: We can't handle these instructions.
    if (Inst.isTerminator() || Inst.isEHPad() ||
	Inst.isSwiftError() || isa<PHINode>(Inst))
      continue;

    RS.sample(&Inst, /*Weight=*/1);
  }
  if (RS.isEmpty())
    return;

  // Delete the instruction.
  mutate(*RS.getSelection(), IB);
  
 }
};

std::unique_ptr<IRMutator> createReductionMutator() {
  std::vector<TypeGetter> Types{
      Type::getInt1Ty,  Type::getInt8Ty,  Type::getInt16Ty, Type::getInt32Ty,
      Type::getInt64Ty, Type::getFloatTy, Type::getDoubleTy};

  std::vector<std::unique_ptr<IRMutationStrategy>> Strategies;
  Strategies.emplace_back(new InstReducerIRStrategy());

  return std::make_unique<IRMutator>(std::move(Types), std::move(Strategies));
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *Data, size_t Size) {
  if (Size <= 1)
    // We get bogus data given an empty corpus - ignore it.
    return 0;

  LLVMContext Context;
  auto M = parseAndVerify(Data, Size, Context);
  if (!M) {
    errs() << "error: input module is broken!\n";
    return 0;
  }

  srand(Seed);
  for(unsigned i = 0; i < NumberMutations; i++){
     Mutator->mutateModule(*M, rand(), 1000, 1000);
  }    

  legacy::PassManager PM;  

  std::unique_ptr<ToolOutputFile> Out;

  std::error_code EC;
  Out.reset(new ToolOutputFile(OutputFilename, EC, sys::fs::F_None));
  if (EC) {
    errs() << EC.message() << '\n';
    return 1;
  }

  PM.add(createPrintModulePass(Out->os()));
  PM.run(*M);
  Out->keep();

  return 0;
}

static void handleLLVMFatalError(void *, const std::string &Message, bool) {
  dbgs() << "LLVM ERROR: " << Message << "\n"
         << "Aborting to trigger fuzzer exit handling.\n";
  abort();
}

extern "C" LLVM_ATTRIBUTE_USED int LLVMFuzzerInitialize(int *argc,
                                                        char ***argv) {
  EnableDebugBuffering = true;

  InitializeAllTargets();
  InitializeAllTargetMCs();
  InitializeAllAsmPrinters();
  InitializeAllAsmParsers();

  handleExecNameEncodedBEOpts(*argv[0]);
  parseFuzzerCLOpts(*argc, *argv);

  if (TargetTriple.empty()) {
    errs() << *argv[0] << ": -mtriple must be specified\n";
    exit(1);
  }

  Triple TheTriple = Triple(Triple::normalize(TargetTriple));

  // Get the target specific parser.
  std::string Error;
  const Target *TheTarget =
      TargetRegistry::lookupTarget(MArch, TheTriple, Error);
  if (!TheTarget) {
    errs() << argv[0] << ": " << Error;
    return 1;
  }

  // Set up the pipeline like llc does.
  std::string CPUStr = getCPUStr(), FeaturesStr = getFeaturesStr();

  CodeGenOpt::Level OLvl = CodeGenOpt::Default;
  switch (OptLevel) {
  default:
    errs() << argv[0] << ": invalid optimization level.\n";
    return 1;
  case ' ': break;
  case '0': OLvl = CodeGenOpt::None; break;
  case '1': OLvl = CodeGenOpt::Less; break;
  case '2': OLvl = CodeGenOpt::Default; break;
  case '3': OLvl = CodeGenOpt::Aggressive; break;
  }
  TargetOptions Options = InitTargetOptionsFromCodeGenFlags();
  TM.reset(TheTarget->createTargetMachine(TheTriple.getTriple(), CPUStr,
                                          FeaturesStr, Options, getRelocModel(),
                                          getCodeModel(), OLvl));
  assert(TM && "Could not allocate target machine!");

  // Make sure we print the summary and the current unit when LLVM errors out.
  install_fatal_error_handler(handleLLVMFatalError, nullptr);

  // Finally, create our mutator.
  Mutator = createReductionMutator();
  return 0;
}

int main(int argc, char *argv[]) {
  return llvm::runFuzzerOnInputs(argc, argv, LLVMFuzzerTestOneInput,
                                 LLVMFuzzerInitialize);
}
