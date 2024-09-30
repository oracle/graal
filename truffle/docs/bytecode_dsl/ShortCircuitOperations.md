# Short circuit operations

One limitation of regular operations is that they are *eager*: all of the child operations are evaluated before the operation itself evaluates.
Many languages define *short-circuiting* operators (e.g., Java's `&&`) which can evaluate a subset of their operands, terminating early when an operand meets a particular condition (e.g., when it is `true`).

The Bytecode DSL allows you to define [`@ShortCircuitOperation`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode./src/com/oracle/truffle/api/bytecode/ShortCircuitOperation.java)s to implement short-circuiting behaviour.
A short-circuit operation implements `AND` or `OR` semantics, executing each child operation until the first `false` or `true` value, respectively.
Since operands will not necessarily be `boolean`s (an operation may have its own notion of "truthy" and "falsy" values), each short-circuit operation defines a boolean converter operation that first coerces each operand to `boolean` before it is compared to `true`/`false`.

For example, suppose there exists a `CoerceToBoolean` operation to compute whether a value is "truthy" or "falsy" (e.g., `42` and `3.14f` are truthy, but `""` and `0` are falsy).
We can define an `AND` operation using `CoerceToBoolean` by annotating the root class with `@ShortCircuitOperation`:
```java
@GenerateBytecode(...)
@ShortCircuitOperation(
    name = "BoolAnd",
    operator = ShortCircuitOperation.Operator.AND_RETURN_CONVERTED,
    booleanConverter = CoerceToBoolean.class
)
public abstract class MyBytecodeRootNode extends RootNode implements BytecodeRootNode { ... }
```
This specification declares a `BoolAnd` operation that executes its child operations in sequence until an operand coerces to `false`.
It produces the converted `boolean` value of the last operand executed.
In pseudocode:

```python
value_1 = child_1.execute()
cond_1 = CoerceToBoolean(value_1)
if !cond_1:
    return false

value_2 = child_2.execute()
cond_2 = CoerceToBoolean(value_2)
if !cond_2:
    return false

# ...

return CoerceToBoolean(child_n.execute())
```

Observe that the `operator` for `BoolAnd` is `AND_RETURN_CONVERTED`.
This indicates not only that the operation is an `AND` operation, but also that it should produce the converted `boolean` value as its result (`RETURN_CONVERTED`).
This can be used, for example, where a `boolean` value is expected, like `a && b && c` in a Java if-statement.

Short circuit operations can also produce the original operand value that caused the operation to terminate (`RETURN_VALUE`).
For example, to emulate Python's `or` operator, where `a or b or c` evaluates to the first non-falsy operand, we can define a short-circuit operation as follows:

```java
@GenerateBytecode(...)
@ShortCircuitOperation(
    name = "FalsyCoalesce",
    operator = ShortCircuitOperation.Operator.OR_RETURN_VALUE,
    booleanConverter = CoerceToBoolean.class
)
public abstract class MyBytecodeRootNode extends RootNode implements BytecodeRootNode { ... }
```

This `FalsyCoalesce` operation behaves like the following pseudocode:

```python
value_1 = child_1.execute()
cond_1 = CoerceToBoolean(value_1)
if cond_1:
    return value_1

value_2 = child_2.execute()
cond_2 = CoerceToBoolean(value_2)
if cond_2:
    return value_2

# ...

return child_n.execute()
```

Observe how the original value is produced instead of the converted `boolean` value.
