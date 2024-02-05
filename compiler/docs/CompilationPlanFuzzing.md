# Compilation plan fuzzing
This page covers the creation and usage of fuzzed compilation plans instead of the usual phase orderings specified by the different `CompilerConfiguration`s.
## Abstraction design
The following design can be found in the package `jdk.graal.compiler.core.phases.fuzzing`.
### Diagram
```
                                                                             Suites  >  FuzzedSuites
                                                                                              |
             AbstractCompilationPlan       >       MinimalFuzzedCompilationPlan  >  FullFuzzedCompilationPlan
            /           |           \                         3x|                           3x|
AbstractTierPlan AbstractTierPlan AbstractTierPlan  >  MinimalFuzzedTierPlan    >   FullFuzzedTierPlan
```
`A > B` means A is the superclass of B.
Vertical lines (`/`, `|` and `\`) means the lower end of the line is a field of the higher end of the line.

### `AbstractCompilationPlan`
An `AbstractCompilationPlan` represents a specific ordering of phases.
It is composed of three `AbstractTierPlan`s which represent the phase orderings for high tier, mid tier and low tier.

### `AbstractTierPlan`
An `AbstractTierPlan` represents the phase ordering for a tier (high, mid or low tier).
It contains a `PhaseSuite` which specifies the specific phase ordering of the plan.

### `MinimalFuzzedCompilationPlan`
A `MinimalFuzzedCompilationPlan` is a fuzzed compilation plan that is composed only of phases that apply a mandatory stage or that fulfills a future stage requirement. It is a phase ordering with the least number of phases possible.

A mandatory stage (see `GraphState.MandatoryStages`) is a stage that necessarily needs to be applied to the graph to produce a valid compilation. For example, `StageFlag.HIGH_TIER_LOWERING` is required in high tier to transition to mid tier.

A future stage requirement means the graph requires a stage to be applied. For example, if vector nodes are created, the graph requires a stage that lowers this kind of nodes.

A `MinimalFuzzedCompilationPlan` is composed of three `MinimalFuzzedTierPlan`s which represent the phase orderings for high tier, mid tier and low tier.

### `FullFuzzedCompilationPlan`
A `FullFuzzedCompilationPlan` represents a compilation plan created by fuzzing.
It is created by inserting optional phases in a `MinimalFuzzedCompilationPlan`.
It is composed of three `FullFuzzedTierPlan`s which represent the phase orderings for high tier, mid tier and low tier.

### `FuzzedSuites`
`FuzzedSuites` is a subclass of `Suites` and represents suites created using a `FullFuzzedCompilationPlan`.
Since it is a subclass of `Suites`, it can be used instead of `Suites` to provide fuzzed phase orderings.

## Fuzzing strategy
To create a fuzzed compilation plan, three fuzzed tier plans are constructed: one for high, mid and low tier.
They are assembled in this order to ensure the graph state resulting from a previous tier is carried to the next tier. This way, future stage requirements can be resolved by phases in later tiers.

### Minimal fuzzed compilation plan
The `MinimalFuzzedCompilationPlan` is created by constructing three `MinimalFuzzedTierPlan`s. These tier plans are assembled in three steps:
1. Insert in the fuzzed tier plan all the phases for which `BasePhase.mustApply(GraphState)` returns `true`.
  This resolves graph or stage requirements that were introduced by earlier tiers.
2. Loop over the phases that apply a mandatory stage and try to insert them in the tier plan.
  If the mandatory stages cannot be reached after a fixed number of attempts, an error is thrown.
3. Insert the phases which `BasePhase.mustApply(GraphState)` after applying the tier plan resulting from step 2.
  This leaves only future stage requirements remaining that cannot be fulfilled by the tier itself.

### Inserting phases that must apply
To fulfill future stage requirements, phases for which `BasePhase.mustApply(GraphState)` returns `true` are inserted in the suite. The procedure is as follow:
```
queue = queueContainingAllThePhases();
while(queue.contains(phaseThatMustApply)){
    // Try to insert the phase in the plan, starting from the end.
    for(position=endPosition; position >= startPosition; position--){
        if(!phaseMustApply(position)){
            break;
        }
        if(canInsertPhaseAtPosition(phaseThatMustApply, position)){
            insertPhaseAtPosition(phaseThatMustApply, position);
            break;
        }
    }
    // If the phase cannot be inserted, it means it must happen after another phase. Try again later.
    queue.putAtTheEnd(phaseThatMustApply);
}
```

### Full fuzzed compilation plan
The `FullFuzzedCompilationPlan` is created by constructing three `FullFuzzedTierPlan`s. These tier plans are assembled with the following steps:
1. Initialize the plan to given `MinimalFuzzedTierPlan`.
2. Insert in the fuzzed tier plan all the phases for which `BasePhase.mustApply(GraphState)` returns `true`.
  This resolves graph or stage requirements that were introduced by earlier tiers.
3. Insert optional phases that can be applied at most once. These phases are the ones that modify the graph state.
  Each phase is inserted by following the logic described by this pseudo-code:
  ```
  phase = pickRandomPhase()
  for randomPosition in positionsInThePlan {
      if(skipped){
          continue;
      }
      newPlan = currPlan.insertPhaseAtPosition(randomPosition, phase);
      if(newPlan.respectsOrderingConstraints()){
          currPlan = newPlan;
          break;
      }
  }
  ```
  The probability of skipping a phase is determined by a parameter of `FullFuzzedTierPlan`.

4. Insert optional phases that can be applied multiple times since they do not modify the graph state.
  Each phase is inserted by following the logic described by this pseudo-code:
  ```
  phase = pickRandomPhase()
  for position in positionsInThePlan {
        if(skipped){
            continue;
        }
        newPlan = currPlan.insertPhaseAtPosition(position, phase);
        if(newPlan.respectsOrderingConstraints()){
            currPlan = newPlan;
        }
   }
  ```
  The probability of skipping a phase is determined by a parameter of `FullFuzzedTierPlan`.
  Compared to step 3, we keep on trying to insert the phase in the plan even if the phase was already inserted successfully. Furthermore, we do not insert at a random position but follow the natural positions order.

5. Insert the phases which `BasePhase.mustApply(GraphState)` after applying the tier plan resulting from the previous step.
  This leaves only future stage requirements remaining that cannot be fulfilled by the tier itself.

## Reproducibility
### JTT tests
#### Create new fuzzed compilation plans
You can create fuzzed compilation plans for JTT tests. For this, use one of the following equivalent commands:
- `mx phaseplan-fuzz-jtt-tests`
- `mx gate --tags phaseplan-fuzz-jtt-tests`

It is possible to fix some parameters of the creation of fuzzed compilation plans:
- To test only the minimal fuzzed compilation plan:
  * `mx phaseplan-fuzz-jtt-tests --minimal`
  * `mx gate --extra-unittest-argument='--minimal' --tags phaseplan-fuzz-jtt-tests`
  * `mx phaseplan-fuzz-jtt-tests -Dtest.graal.compilationplan.fuzzing.minimal=true`
  * `mx gate --extra-unittest-argument='-Dtest.graal.compilationplan.fuzzing.minimal=true' --tags phaseplan-fuzz-jtt-tests`
  * `mx gate --extra-vm-argument='-Dtest.graal.compilationplan.fuzzing.minimal=true' --tags phaseplan-fuzz-jtt-tests`
- You can choose the seed to be used to create `Random` instances. The option is `-Dtest.graal.compilationplan.fuzzing.seed=<seed>` and the short version is `--seed=<seed>`. It can be used like the option for the minimal fuzzed compilation plan.
- To regulate the probability that a phase will be inserted at each position in the suite, you can use:
  *  `-Dtest.graal.skip.phase.insertion.odds=<number>` or `--skip-phase-odds=<number>`.
    - When we try to insert the phase, the phase will be inserted with probability `1/<number>` at each position in the current suite but only if the ordering constraints are respected.
    - The creation of the fuzzed compilation plan will use the same odds for each tier.
  * `-Dtest.graal.skip.phase.insertion.odds.high.tier=<number>` or `--high-tier-skip-phase=<number>`
    - This will determine the probability for the insertion in high tier.
    - If the odds for mid tier or low tier are not given, these odds will be used.
    - If the odds for high tier are not defined, the default odds defined in `FullFuzzedCompilationPlan` are used instead.
  * `-Dtest.graal.skip.phase.insertion.odds.mid.tier=<number>` or `--mid-tier-skip-phase=<number>`
    - This will determine the probability for the insertion in mid tier.
  * `-Dtest.graal.skip.phase.insertion.odds.low.tier=<number>` or `--low-tier-skip-phase=<number>`
    - This will determine the probability for the insertion in low tier.
- You can specify which test you want to run like this:
  * `mx phaseplan-fuzz-jtt-tests HP_life`
  * `mx gate --extra-unittest-argument='HP_life' --tags phaseplan-fuzz-jtt-tests`
- If you want to use the phases of a specific compiler configuration and respect its requirements, you should use:
  * `-Djdk.graal.CompilerConfiguration=<config>`

#### Load a phase plan
You can load a phase plan (one created by a fuzzed compilation plan or any other phase plan serialized using the `PhasePlanSerializer`) using the command:
```
mx unittest -Dtest.graal.phaseplan.file="/path/to/phaseplan"
```

### CompileTheWorld
#### Create new fuzzed compilation plans
You can use fuzzed plans for `CompileTheWorld`'s compilations by using the following commands:
```
mx gate --tags ctwphaseplanfuzzing
```
or
```
mx gate --extra-vm-argument='-DCompileTheWorld.FuzzPhasePlan=true' --tags ctw
```

These commands will make each thread create a new fuzzed compilation plan for each compilation they have to perform.

#### Load a phase plan
It is possible to load a phase plan (one created by a fuzzed compilation plan or any other phase plan serialized using the `PhasePlanSerializer`) and use it for the compilation of a method by using the command:
```
mx gate --extra-vm-argument='-DCompileTheWorld.LoadPhasePlan=/path/to/phaseplan' --extra-vm-argument='-DCompileTheWorld.MethodFilter=<methodName>' --extra-vm-argument='-Djdk.graal.CompilerConfiguration=<config>' --tags ctw
```
