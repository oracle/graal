# Partially Open World Analysis

## Engineering Contract

Native Image normally analyzes a closed world.
When building a shared image layer or enabling runtime class loading, the analysis must account for code supplied after the current build.
Partially open world analysis preserves closed-world assumptions for components whose extension contracts permit them.

This document defines the engineering contract for partially open world analysis.
It states the requirements for permitted extensions and identifies the policy rules that implement parts of that contract.
For the surrounding analysis lifecycle, see [Static Analysis](04-STATIC-ANALYSIS.md).

## Extension Model

The **analysis boundary** separates code whose implementation is analyzed in the current build from code whose effects must be represented without analyzing its implementation.
An **extension** can be a subsequent image layer or code loaded at run time.
An **extension policy** describes what that extension is allowed to introduce or access.
An effect is **covered** when an analyzed method graph or a conservative summary represents all of its relevant runtime behavior.
An **entry summary**, for example, represents the possible arguments of calls whose call sites are unavailable to the analysis.

A method can be known to the builder while analysis of its body is deferred to a later image layer.
Conversely, at run time an analyzed method can receive and use a concrete value supplied by an extension.
The component that declares a class therefore does not, by itself, determine which effects the analysis covers.

For every permitted extension, analysis results must include all runtime possibilities relevant to their consumers, such as graph strengthening.
An optimization must remain valid when those extensions execute with the compiled image.
Analyzing a later layer must not invalidate assumptions used to compile code retained from an earlier layer.

### Closed-World Guarantees

A **closed-world guarantee** establishes that later extensions cannot invalidate a specified analysis assumption.
Each guarantee is relative to the current analysis boundary and the permitted extensions.
The individual guarantees are:

- **Type hierarchy completeness:** Every runtime type assignable to a specified type bound is known to the current analysis.
  This does not establish that the analysis represents instances of every type that an extension may allocate.
- **Method entry coverage:** Every permitted call and its arguments are represented by an analyzed call site or an explicit entry summary.
- **Invoke-target coverage:** All possible targets of an invocation, including those supplied by permitted extensions, are covered by analyzed code or explicit summaries.
  This applies to direct and indirect calls, regardless of how callers enter the method containing the invocation.
- **Field mutation coverage:** Every permitted write to a field is represented by an analyzed store or an explicit mutation summary.
  This does not establish completeness of the field's declared type hierarchy or restrict the provenance of stored values.

A final class can have externally callable methods and mutable fields.
A field classified as closed to direct extension writes can still receive extension-supplied values through analyzed stores.
A local allocation can provide a precise receiver even when its declared interface is extensible.
These cases require separate decisions within the analysis.

The policy specifies which closed-world assumptions the analysis may rely on.
Extension mechanisms must enforce the corresponding restrictions, and the analysis must account for all behavior those restrictions permit.

**Runtime core** names a component; **closed** describes a policy classification for a particular type hierarchy, method entry, or field.
Core elements are a subset of the elements for which the policy can retain closed-world assumptions.
For example, a final application type can have a closed hierarchy without belonging to the core or having closed method entries.
Being included in the image does not itself make code part of the core or establish any of these classifications.

For methods, entry coverage and invoke-target coverage are distinct guarantees.
Neither guarantee establishes hierarchy completeness for parameter and return types in the method signature.
A method whose entry is classified as closed can receive an extension-supplied value through an analyzed caller and return a value obtained from an extension callback.
The completeness of parameter and return flows is a separate analysis property.

### Related Analysis and Runtime Requirements

**External allocation.** Knowing the complete type hierarchy does not establish which types extensions may instantiate.
The analysis must represent those possible instances, including instances of known types that have no allocation site in analyzed code.

**Flow completeness.** The guarantees above constrain permitted extensions.
The analysis must also represent their possible effects at each program point.
A complete flow may still be imprecise; completeness alone does not establish an exact type, non-nullness, or a constant value.

**Runtime compatibility.** Type metadata, type checks, and method dispatch must support every type and implementation permitted by the extension policy.
[`ClosedTypeWorld` and `ClosedTypeWorldHubLayout`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/SubstrateOptions.java) configure closed-world analysis and runtime type representation separately.
For example, a final application layer can use closed analysis with an open hub layout inherited from shared layers.
The compiler must not apply an optimization unless the analysis results it relies on cover all relevant behavior permitted by the extension policy.

**Code and metadata inclusion/preservation.** Mechanisms such as [`ClassInclusionPolicy`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/ClassInclusionPolicy.java) and [`PreserveOptionsSupport`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/PreserveOptionsSupport.java) select retained code and metadata, affecting analysis roots and runtime availability.
Their selections must support permitted uses, but do not by themselves establish restrictions on extension.

### Representing Possible Runtime Types

For reference values, a **finite type approximation** describes possible non-null values by explicitly enumerating known runtime types.
Nullness is tracked separately; an object-sensitive analysis can additionally distinguish abstract objects of those types.
The enumerated set can grow as the analysis discovers types.

At analysis completion, an enumerated set is a conservative approximation only if it includes every runtime type that can occur at the represented program point under the permitted extensions.
Saturation indicates that a flow's recorded type set must not be used as an exhaustive description of its values.
It can result from a precision cutoff or from the need to represent values supplied by unknown extension code.

**Recovering a finite type approximation** means replacing such an incomplete set with an explicit set that covers all permitted runtime types at that point.
One mechanism uses an **all-instantiated flow**, which collects types registered as instantiated and assignable to a particular bound.
Recovery based solely on that bound requires both a complete assignable-type hierarchy and registration of every type that may occur, including types allocated only by extensions.
A successful checked cast can provide the bound, but does not by itself establish either condition.
An open bound alone cannot justify enumerating all possible types; a particular value can nevertheless have a precise type established by its analyzed allocation.

## Values Across Component Boundaries

The analysis represents these interactions in a shared points-to graph, linking method graphs through calls and memory flows.
Openness propagates according to each operation and its inputs.
A component boundary alone cannot and should not stop that propagation.

Suppose an analyzed method with a closed entry accepts an `Object`, stores it in a field, and later reads it.
An analyzed caller may pass a value received from an extension.
Even if extensions cannot write the field directly, the store must propagate the argument's uncertainty into the field and subsequent loads.
Entering a closed component does not justify discarding that uncertainty.

### Calls Into Extension Code

Consider a method whose callers are all analyzed.
One caller receives a callback object from an extension and passes it to this method.
The method's entry is closed: the analyzed call sites account for its incoming arguments, including this callback, so the method needs no external-entry summary.

Inside the method, the callback is invoked through an extensible interface.
Its implementation may be supplied by a later layer or loaded at run time, and may return an object of a type unknown to the current analysis.
The analysis must represent those possible return values at the callback invocation, even though the containing method's entry is closed.
If the method stores that result in a field, the analyzed store must propagate the extension value into the field, including a field closed to direct extension writes.

The same distinction applies to control flow.
An extension callback can return normally even when analysis knows no implementation of it, so execution after the invocation must remain reachable.
A void callback needs this normal-return coverage too, despite producing no value.
The analysis must also account for the callback's permitted mutations and exceptions.
Method entry coverage, callback results and continuations, and field mutation coverage are separate obligations in this single interaction.

The callback's return signature alone does not enumerate its possible runtime types.
A declared interface type is not necessarily an enforced bound: the analysis accounts for unchecked interface declarations through [`TypeFlow.dropUncheckedInterfaceBound`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/flow/TypeFlow.java).
For example, an unchecked declaration `I` supplies only an `Object` bound, and `I[]` supplies only an `Object[]` bound.
Field flows use this effective bound when deciding whether finite recovery is justified; the field metadata retains the original declaration.
Recovery based solely on the bound established by a successful checked cast must satisfy the conditions in [Representing Possible Runtime Types](#representing-possible-runtime-types).

### Indirect Field Writes

An analyzed method can receive an extension value and store it through an indirect access, such as `Unsafe` or reflection.
A later ordinary field read must account for that value, even when the field is closed to direct extension writes.
If the analysis omits individual indirect stores, its field mutation summary must cover the values those stores can write.

Enumerating known instantiated types is insufficient when a field can hold values of types introduced by an extension.
Such a field requires an open value approximation independently of the number of known types.
A closed value bound permits a finite approximation only under the conditions in [Representing Possible Runtime Types](#representing-possible-runtime-types).

## Architectural Requirements

These requirements describe the intended contract for every supported configuration.

### Requirements on Extension Mechanisms

The analysis relies on extension mechanisms to admit only extensions consistent with the following requirements.

1. **Justified closed-world assumptions:** Every policy classification of an element as closed follows from a language guarantee or an enforced restriction on permitted extensions.
   Layer construction and runtime class loading must enforce the restrictions that justify classifying a type hierarchy, method entry, or field as closed, including those in the runtime core.
2. **Persistent assumptions:** Later layers and runtime loading respect restrictions used by existing compiled code.
   Runtime type support, retained metadata, and persisted analysis assumptions agree with that contract.

### Analysis and Optimization Obligations

The analysis and its optimization consumers must provide the following guarantees for every extension permitted by the policy.

1. **Covered external effects:** Permitted entries, allocations, call results, writes, and control effects have conservative analysis representations even when no concrete extension implementation is known.
   If an invocation may target extension code, an empty set of known targets does not prove that it cannot return normally or throw.
2. **Propagation through analyzed code:** Arguments, returns, stores, loads, and callbacks preserve the information required by consumers across component boundaries.
   Field mutation coverage does not restrict the provenance of stored values.
3. **Justified recovery of finite type approximations:** Replacing an incomplete type set with all instantiated types under a bound requires hierarchy completeness and representation of every runtime-relevant type, including types instantiated only by extensions.
   Nullness and other independently tracked facts require their own justification.
4. **Preserved meaning across graph changes:** Late uses and observers, context clones, replacement invokes, and saturation-driven edge removal preserve the conservative meaning of results.
   Changing the precision cutoff or a permitted registration order may change precision, but must not exclude permitted runtime behavior.
5. **Valid use of analysis results:** Strengthening, devirtualization, reachability, field folding, dispatch construction, and metadata pruning use only facts that hold for every permitted extension.
   The absence of saturation does not establish completeness unless all permitted value sources and propagation paths are represented.

## Extension Policy

[`ExtensionPolicy`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/ExtensionPolicy.java) supplies the extension assumptions used by points-to analysis and graph strengthening.
Its Javadoc and implementations define the query contracts and classification rules.
[`PointsToAnalysis`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/PointsToAnalysis.java) owns the policy used for a build.

The policy supplies assumptions; flow construction and propagation must represent all behavior those assumptions permit.
Optimizations must also consider the information represented by each flow.
[`AnalysisPolicy`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/AnalysisPolicy.java) has a separate role: it controls analysis representation and context sensitivity.

The extension contract prohibits application code from extending the runtime core.
The partially open policy relies on enforced restrictions to retain closed-world assumptions for core types, method entries, and fields.
Those restrictions still permit extension-supplied values and callbacks through analyzed code, as described in [Values Across Component Boundaries](#values-across-component-boundaries).
