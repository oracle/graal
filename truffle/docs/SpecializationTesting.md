---
layout: docs
toc_group: truffle
link_title: Testing DSL Specializations
permalink: /graalvm-as-a-platform/language-implementation-framework/SpecializationTesting/
---
# Testing Truffle DSL Specializations

This document discusses the tools for testing Truffle DSL specializations.

## Slow Path Specializations in Only Compilation Mode

The following example will be used in this guide:
```java
abstract class PowNode extends Node {
  public abstract double execute(double a, int exp);

  @Specialization(guards = "exp==1")
  double doOne(double a, int exp) {
    return a;
  }

  @Specialization(replaces = "doOne")
  int doGeneric(double a, int exp) {
    double res = 1;
    for (int i = 0; i < exp; i++)
      res *= a;
    return res;
  }
}
```

In order to test that `doGeneric` produces the correct result for argument `exp == 1`, you first need to execute this node with a different value.
For example, you can use `exp == 2` to activate the `doGeneric` specialization and only then with `1`, which will now
be handled by the `doGeneric` specialization instead of the `doOne` specialization.
With a real-world code, writing a test that covers specializations that replace other specializations can be much more complicated and it leads to fragile tests.
Changes in the production code may cause the test to suddenly cover different specializations.
This can easily happen unnoticed.

Truffle DSL provides a mode where the "fast-path" specializations (those that are "replaced" by some other specialization, `doOne` in our example) are ignored.
This allows you to simply increase test coverage by running the same tests, which now may cover different code paths.

When building a language with `mx`, pass the additional option:
```shell
mx build -c -A-Atruffle.dsl.GenerateSlowPathOnly=true
```

After the rebuild, the generated code will call only "slow-path" specializations.
Make sure that your IDE does not recompile the sources automatically in the meantime.
Note that if you compile your dependencies (e.g., Truffle) from source as part of your build, this option will apply to the code of those dependencies as well.
You may choose to apply this option only to some classes by using a filter:

```shell
mx build -c -A-Atruffle.dsl.GenerateSlowPathOnly=true -A-Atruffle.dsl.GenerateSlowPathOnlyFilter=org.my.truffle.language.package
```
