# Notes on BE implementation

* Each instruction can decide if it participates in boxing elimination, but then must be consistent.
* Each instruction has a role of producer (if it pushes values) and consumer (if it pops). Each instruction can produce multiple values, and also consume multiple values. These can have differing BE behaviours (e.g. first consumed value is BE'd, but the second one isn't).

* If an instruction has at least one *produced* value that does BE, its object needs to implemebt `BoxableInterface`.

* During building
  * When emitting a producer, for each its produced value:
    * If it does BE, the value `(valueIndex << 16) | bci` is pushed on a value-tracking stack.
    * If it does not do BE, value `0xffff0000` is pushed instead. (`-1` may be a better choice)
  * When emitting a consumer, for each its consumed value:
    * If it does BE, pop from the value-tracking stack, and store this value somewhere
    * If it does not do BE, pop and discard a value from the value-tracking stack.

* During execution:
  * When popping a value:
    * If the value should be BE'd:
      * If you are expecting a primitive: (thi is implemented with `doPopPrimitiveXYZ`)
        * If your producer is BEing (value != 0xffff)
            * Speculatively read the primitive off the stack
            * If mispredicted, deopt and call `BoxableInterface#setBoxing(valueIndex, expectedKind)` on the `objs[bci]`
        * Else, pop an object, and try to cast it as the primitive
      * If you are expecting an object: (this is implemented with `doPopObject`)
        * If your producer is BEing (value != 0xffff)
            * Speculatively read the object off the stack
            * If mispredicted, deopt and call `setBoxing(valueIndex, -1)` to turn the producer generic.
        * Else, pop an object (it will always be object)
    * If the value should not be BE'd:
      * Just read as object, since nothing can BE the producer except you
  * When pushing a value:
    * If the value should not be BE'd:
      * Just push it as object
    * If the value should be BE'd:
      * Inspect the boxingState for the corresponding valueIndex. You **must** act in accordance to that, even if that means preemptively unboxing something.



Problems:
  * If a consumer first exec races with another first exec + a primitive exec, the producer may get stuck in the generic state, even though it should remain in primitive state (the first first exec will expect an object, but if the primitive exec already turned it into a primitive, it will instead go into generic).
  * The boxing elimination updates need 2 executions to propagate (since they only propagate on a *fault*, never in advance). Moving the `setBoxing` calls into E&S would help this.
  

# The problem of Object

The issue is with an operation like (assume we BE ints and longs):

```
@Spec int soPrimitive() { return 0; }
@Spec Object doBoxed() { return (long) 0L; }
```

Even though this operation can't produce a primitive `long`, it can still produce a *boxed* one. To solve this, we have 3 cases of custom instructions:

* Have only non-primitive (including `Object`) return values: they don't participate in BE (`resultBoxingElimination == false`)
* Have only primitive and non-`Object` return values: they participate in BE normally. We know they can never produce unexpected primitives. (`resultBoxingElimination == true`, the set of possible return values is in `possibleBoxingResults` with `Object` standing in for anything non-primitive)
  * During execution we don't have to check the non-existant types in boxingState since we can never produce them in the first place.
* Have a primitive and an `Object` return value: they can *potentially* return any primitive value. For this we use (`resultBoxingElimination == true` and `possibleBoxingResults == null`).
  * During execution check all BE'd types in boxingState, and corresponding `execute` method.