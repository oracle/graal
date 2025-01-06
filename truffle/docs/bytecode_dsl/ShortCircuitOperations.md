# Short circuit operations

One limitation of regular operations is that they are *eager*: all of the child operations are evaluated before the operation itself evaluates.
Many languages define *short-circuiting* operators (e.g., Java's `&&`) which can evaluate a subset of their operands, terminating early when an operand meets a particular condition (e.g., when it is `true`).

The Bytecode DSL allows you to define [`@ShortCircuitOperation`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/ShortCircuitOperation.java)s to implement short-circuiting behaviour.
A short-circuit operation implements `AND` or `OR` semantics, executing each child operation until the first `false` or `true` value, respectively.

### Basic example

By default, the operands to a short-circuit operation must be `boolean` so that they can be compared with `false`/`true`.
Below we define a simple short-circuit `BoolOr` operation by annotating the root class with `@ShortCircuitOperation`:
```java
@GenerateBytecode(...)
@ShortCircuitOperation(
    name = "BoolOr",
    operator = ShortCircuitOperation.Operator.OR_RETURN_VALUE
)
public abstract class MyBytecodeRootNode extends RootNode implements BytecodeRootNode { ... }
```

`BoolOr` uses the `OR_RETURN_VALUE` operator.
This means it executes its operands until the first `true` value, and then produces that value as a result.
In pseudocode, it implements:

```python
if (boolean) child_1.execute() == true:
    return true

if (boolean) child_2.execute() == true:
    return true

# ... more children

return child_n.execute()
```
Each operand is casted to `boolean` to perform the comparisons, so a non-`boolean` operand will cause a `ClassCastException` or `NullPointerException` to be thrown.
The last operand is not compared against `true`/`false`, so no cast is performed.
It is the language implementation's responsibility to ensure the last operand produces a `boolean` (or to properly handle a non-`boolean` result in the consuming operation).

### Boolean converters

Sometimes you don't expect the operands to be booleans, or the short-circuit operation has its own notion of "truthy" and "falsy" values.
For example, Python's `or` operator evaluates to the first non-"falsy" operand, where values like `0` and `""` are falsy, and values like `42` and `"hello"` are not.
In such cases, you can supply a `booleanConverter` that coerces each operand to `boolean` to perform the boolean comparison.

Suppose there already exists a `CoerceToBoolean` operation that coerces values to booleans.
We can emulate Python's `or` operator with a `FalsyCoalesce` operation that returns the first non-"falsy" operand:

```java
@GenerateBytecode(...)
@ShortCircuitOperation(
    name = "FalsyCoalesce",
    operator = ShortCircuitOperation.Operator.OR_RETURN_VALUE,
    booleanConverter = CoerceToBoolean.class
)
public abstract class MyBytecodeRootNode extends RootNode implements BytecodeRootNode { ... }
```

This specification declares a `FalsyCoalesce` operation that executes its child operations in sequence, producing the first operand that coerces to `true`.
In pseudocode, it implements:

```python
value_1 = child_1.execute()
if CoerceToBoolean(value_1) == true:
    return value_1

value_2 = child_2.execute()
if CoerceToBoolean(value_2) == true:
    return value_2

# ... more children

return child_n.execute()
```

Observe that `FalsyCoalesce` produces the original operand value, and not the converted `boolean` value.
This is because it uses the `OR_RETURN_VALUE` operator.
For both `AND` and `OR` operators there are variations that instead produce the converted boolean value: `AND_RETURN_CONVERTED` and `OR_RETURN_CONVERTED`.

Below, we define a `CoerceAnd` operation that uses `CoerceToBoolean` to convert its operands to `boolean` and produces the converted value:
```java
@GenerateBytecode(...)
@ShortCircuitOperation(
    name = "CoerceAnd",
    operator = ShortCircuitOperation.Operator.AND_RETURN_CONVERTED,
    booleanConverter = CoerceToBoolean.class
)
public abstract class MyBytecodeRootNode extends RootNode implements BytecodeRootNode { ... }
```
`CoerceAnd` executes its child operations in sequence until an operand coerces to `false`.
It produces the converted `boolean` value of the last operand executed.
In pseudocode:

```python
if CoerceToBoolean(child_1.execute()) == false:
    return false

if CoerceToBoolean(child_2.execute()) == false:
    return false

# ... more children

return CoerceToBoolean(child_n.execute())
```
