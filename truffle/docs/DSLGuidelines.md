---
layout: docs
toc_group: truffle
link_title: Truffle DSL Guidelines
permalink: /graalvm-as-a-platform/language-implementation-framework/DSLGuidelines/
---

# Truffle DSL Guidelines

The general high-level guideline for partially evaluated (PE) code is to **minimize code duplication during
the PE process**. This not only helps the Truffle compilation that uses partial evaluation, but also
[host inlining](https://github.com/oracle/graal/blob/master/truffle/docs/HostCompilation.md), which
follows similar rules to PE when compiling the interpreter.

The general high-level guideline for any Truffle interpreter code is to have as little code as possible
in order to **minimize the native-image size**. This applies to runtime code and PE code, but is even
more important for PE code, since native-image also AOT compiles it, but the host-inlining greatly
increases the amount of code the AOT compilation produces and on top of that native-image also needs
to retain serialized Graal IR graphs of it for runtime compilation.

## Avoid subclassing for minor changes

Example:
```java
class MyBaseNode extends Node {
  // ...

  @Specialization
  doIt1(Object arg) { /* ... */ }

  @Specialization
  doIt2(Object arg) { /* ... */ }

  // ...
  abstract int doSomething();
}

class Node1 {
  @Override
  final int doSomething() { return 42; }
}

class Node2 {
  @Override
  final int doSomething() { return -1; }
}
```

Why: native-image footprint.

Truffle DSL will generate multiple `execute` and `executeAndSpecialize` methods,
which will contain the same code. Native-image does not deduplicate the code.

Solution: extract the common logic to an inline node and use delegation instead of inheritance.

## Avoid duplicated Specializations

Avoid specializations with (almost) the same method body.

When two or more `@Specialization`s differ only in guards or in some minor detail. This includes `@Specialization`s
that delegate to the same helper method. Example:

```java
class MyNode extends Node {
  // ...

  @Specialization
  void doIt1(MyObject1 o) { helper(o); }

  @Specialization
  void doIt2(MyObject2 o) { helper(o); }

  void helper(Object o) { /* some code */ }

  // ... maybe more @Specializations
}
```

Why:
* native-image footprint
* code duplication during PE

We want to reduce code duplication as seen by PE process and not by the developer. Refactoring the code
to a helper method does not help PE, because it explores every call separately.

For instance, with host inlining and our code example, the cost of fully inlining `Node#execute` will be
(omitting some details):

`size(Node#execute) + size(Node#doIt1) + size(Node#doIt2) + 2 * size(Node#helper)`

Solution: refactor the code to avoid the duplication. The concrete approach differs depending on concrete
situation. There is no one-size-fits-all solution. General advice is to try to merge the `@Specialization`s
that contain the code duplication. For our example:

```java
class MyNode extends Node {
  // ...
  static boolean isMyObject1or2(Object o) {
    return o instanceof MyObject1 || o instanceof MyObject2;
  }

  @Specialization(guards = "isMyObject1or2(o)")
  void doIt1(Object o) { helper(o); }
}
```

Some additional tips that may not always be applicable:

* If there is a concern that the individual "merged" checks are not profiled, one create an inline node
to profile the individual checks. For our example:
```java
@GenerateInline
@GenerateCached(false)
class GuardNode extends Node {
  public boolean execute(Node inliningTarget, Object o);

  @Specialization
  static boolean o1(MyObject1 o) { return true; }

  @Specialization
  static boolean o2(MyObject2 o) { return true; }

  @Fallback
  static boolean o2(Object o) { return false; }
}

class MyNode extends Node {
  // ...

  @Specialization(guards = "guardNode.execute(this, o)")
  void doIt1(Object o,
             @Cached GuardNode guardNode) { helper(o); }
}
```

Note that if the guard needs to be used for multiple specializations, or will be used by generated fallback guard,
we are duplicating the guard logic in the same way as we were duplicating the logic inside the specializations.
This may be acceptable as guards tend to be simple, but the user needs to assess if that is a good trade-off.

* Push any checks that would be done in the guard(s) into the `@Specialization` body and profile them using cheap inline profiles.

## Avoid duplicated calls to helper methods/nodes

Example:
```
@Specialization
void foo(boolean b, Object o) {
  if (b) {
    helper.execute(o, 42);
  } else {
    helper.execute(o, -1);
  }
}
```


Why: code duplication during PE

The PE process has to explore each call separately and only in later phases the Graal compiler may deduplicate the code.

Solution: common-out the calls if possible
```
  int num = b ? 42 : -1;
  helper.execute(o, num);
```

## Mixing @Shared and non-@Shared inline nodes/profiles

Avoid mixing `@Shared` and non-`@Shared` inline nodes/profiles in one `@Specialization` if Truffle DSL generates
"data-class" for the `@Specialization`.

Example:
```java
@GenerateInline(false)
class MyNode extends Node {
  // ...
  @Specialization
  void doIt(...,
      @Bind("this") Node node,
      /* more @Cached arguments such that data-class is generated */
      @Exclusive @Cached InlinedBranchProfile b1,
      @Shared @Cached InlinedBranchProfile b2)
```

Why: Truffle DSL generates code that is less efficient in the interpreter.

In our example: non-shared inline profile has its data stored in the data-class object, but the shared inline profile
has its data stored in the instance of `MyNode`. However, both profiles receive the same `node` argument,
which will be an instance of the generated data-class, so the shared profile must call `node.getParent()` to access
its data stored in `MyNode`. In general, such inline nodes/profiles may need to traverse multiple parent pointers
until they reach their data.

Note: this does not concern any non-inline nodes, it is OK to mix those, and it is OK to mix them with inline nodes,
however, inline nodes used in one `@Specialization` should be either all shared or all exclusive.

Solution: change the `@Shared` nodes/profiles to `@Exclusive` or refactor the code such that sharing is not
necessary anymore. Usage of `@Shared` (not only inline nodes/profiles) can be a sign of
[duplicated `@Specialization`s](#avoid-duplicated-specializations), and refactoring the `@Specialization`s will resolve the problem.
If the footprint benefit outweighs the possible interpreter performance degradation, this guideline can be ignored.

Generic solution that trades off some code readability for good interpreter performance and lower footprint at the
same time is to split the code into two nodes, where the outer node takes only the shared inline nodes/profiles
and the inner node takes only the non-shared. The outer node can execute some common logic and also forward the
shared nodes along with their inlining target node to the inner node.

## Avoid unused large inline nodes

Avoid inlining large nodes that are used only on rarely executed code-paths.

Example:
```
@Specialization
void s1(Object arg,
    @Bind("this") Node inliningTarget,
    @Cached LargeInlineNode n) {

    if (arg == null) {
        // unlikely situation
        n.execute(inliningTarget, ...);
    }
}
```

Why: memory footprint.

All the fields of `LargeInlineNode` node will be inlined into the caller node (or Specialization data-class)
increasing its memory footprint significantly.

For code-paths that are not performance sensitive in the interpreter, better alternative is
the [lazily initialized nodes](https://github.com/oracle/graal/blob/master/truffle/docs/DSLNodeObjectInlining.md#lazy-initialized-nodes-with-dsl-inlining).

For Code-paths that are performance sensitive in the interpreter:
* The footprint increase may be justified by the performance
* Use the handwritten lazily initialized `@Child` field pattern if applicable
* If possible restructure the code to avoid such situation

## Avoid generating cached, uncached, and inline variant of one node

If possible avoid having all three variants, because it increases the PE code footprint.