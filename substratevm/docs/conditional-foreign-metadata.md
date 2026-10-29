# FS-002-conditional-foreign-metadata: Conditional Foreign Metadata Tooling

The Tracing Agent and configuration tools preserve the meaning of Foreign Function and Memory (FFM) API registrations in reachability metadata.
This applies to `foreign.downcalls`, `foreign.upcalls`, and `foreign.directUpcalls`.

## 1. Conditional Agent Output

When you enable conditional configuration, each observed foreign registration remains in the output with the `typeReached` condition inferred from its calling context.
The same inference rules apply to foreign and other metadata recorded under that context.
For a library class `lib.Native` that records two downcalls, an adapted upcall, a direct upcall, and reflection, conditional output retains all four foreign registrations under `lib.Native`, alongside the reflection metadata.
Plain mode continues to record those registrations unconditionally.

## 2. Partial Runs and Propagation

A configuration containing only foreign registrations is nonempty, including when it contains only one of the three foreign categories.
Serializing and parsing partial configuration with origins preserves foreign-only method nodes and their registrations.
Foreign-only differences between calling contexts propagate through method nodes using the same inference rules as other metadata.
Partial runs followed by `native-image-utils generate-conditional` produce the same foreign registrations and inferred conditions as a single conditional run over those observations, without repairing the partial trace by hand.

## 3. Generation and Merging

`native-image-utils generate` preserves foreign conditions, descriptors, target methods, and linker options accepted by the configuration tooling.
Registrations conditioned on different types remain distinct.
Combining equivalent conditional and unconditional registrations preserves their meaning and must not turn the conditional registration into an indistinguishable unconditional duplicate.
This contract does not introduce minimization across different conditions.
Unconditional registrations and unrelated metadata retain their existing meaning.

## 4. Filtering

Conditional configuration filtering retains foreign registrations whose condition types are included and removes those whose condition types are excluded.
A direct upcall additionally requires its target class to be included, following the class-filtering convention for reflection targets.
Descriptors and linker options of retained entries remain intact.

## 5. Direct Upcall Variants

Direct upcalls with and without function descriptors serialize as valid JSON and round-trip through the configuration tools, including with nonempty linker-option maps accepted by those tools.
Within the same condition, target method, and linker options, a descriptorless registration subsumes registrations with individual descriptors.
A registration under another condition or with different linker options is not subsumed by that rule.
The tooling's preservation of option maps does not extend which options the image builder accepts for upcalls.
