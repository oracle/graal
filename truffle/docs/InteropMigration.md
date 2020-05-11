
# Truffle Interop 2.0

It is recommended to read the [Truffle Library Tutorial](https://github.com/oracle/graal/blob/master/truffle/docs/TruffleLibraries.md) first, before proceeding. This document is targeted at guest language and tool implementers.

## Motivation 

In Truffle Version 1.0 RC15 we introduced a new API called Truffle Libraries. Truffle Libraries allow using polymorphism with support for profiling/caching. With Interop 2.0 we plan to use Truffle Libraries for the interoperability protocol. The current interop APIs are mature and well tested and already adopted by languages and tools. So why change them and introduce Interop 2.0? 

Here is a list of arguments to justify the change:

* *Footprint:* In the current interop API every message send goes through a CallTarget and the arguments are boxed into an Object[]. This makes current interop inefficient for interpreter calls and it requires additional memory. Truffle Libraries use simple nodes and type specialized call signatures that don't require argument array boxing or call targets. 
* *Uncached Dispatch:* There is no way to execute current interop messages from the slow-path without allocating a temporary node. Truffle Libraries automatically generate an uncached version of every exported message. This allows the use of interop messages from slow-path/runtime without allocating any temporary data structures.
* *Reuse dispatch for multiple messages:* In current interop, the dispatch to exported messages is repeated for each message that is sent. If multiple messages need to be sent and the receiver type becomes polymorphic this produces bad code. Interop libraries instances can be specialized for input values. This allows to do the dispatch once and invoke multiple messages without repeating the dispatch. This leads to more efficient code in polymorphic cases.
* *Support for default implementations:* Current interop can only be used for implementations of `TruffleObject`. Truffle Libraries can be used with any receiver type. For example, it is possible to invoke the isExecutable message on primitive numbers and it just returns `false`.
* *Error Proneness:* There were some common issues with message resolutions that Truffle Libraries try to avoid by not making them possible, such as mixing up receiver types or implementing a wrong type check. The new assertion feature for Truffle Libraries allows specifying message specific assertions that allow verifying invariants, pre, and post-conditions.
* *Redundancy in documentation:* Current interop documents the messages in the Message constant and in the ForeignAccess static accessor method. This leads to mostly redundant documentation. With truffle interop, there is only one place for the documentation, which is the instance method in the library class.
* *Generality:* Truffle Libraries can be used for language representation abstractions, as it is now efficient enough in terms of memory consumption and interpreter performance. The current interop API could not be realistically be used that way because of that.
* *Address Protocol Issues:* There are some design issues with the current interop API that interop 2.0 tries to address (see later).

## Compatibility

The change from interop 1.0 to 2.0 was done in a compatible way. Therefore, the old interop should continue to work and adoption can be incremental. This means that if one language still calls using the old interop API and the other language has already adopted the new interop API, a compatibility bridge will map the APIs. If you are curious about how this works, look for the class `DefaultTruffleObjectExports` for new interop calls to old interop. And `LegacyToLibraryNode` for old interop calls to new interop. Note that using the compatibility bridge may cause performance regressions. That is why languages should migrate as early as possible.

## Interop protocol changes

Interop 2.0 comes with many protocol changes. This section is intended to provide rationales for these changes. For fully detailed reference documentation see the [InteropLibrary](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html) Javadoc. Note that also every deprecated API describes its migration path in the Javadoc tagged by @deprecated.

### Replace IS_BOXED and UNBOX with explicit types.

There are some problems with the IS_BOXED/UNBOX design:

* In order to find out if a value is of a particular type, e.g. a String, the value needs to be unboxed first. Unboxing may be an expensive operation leading to inefficient code just to check the type of a value. 
* The old API cannot be used for values that did not implement TruffleObject. Therefore, the handling of primitive numbers needed to be separated from the TruffleObject case, making the UNBOX design necessary to reuse existing code. TruffleLibraries support primitive receiver types.
* The design of UNBOX relies on the specified set of primitive types that it returns. It is hard to introduce additional new interop types this way, as language refers to the primitive types directly.

The following new messages were introduced in InteropLibrary as a replacement:

```
boolean isBoolean(Object)
boolean asBoolean(Object)
boolean isString(Object)
String  asString(Object)
boolean isNumber(Object)
boolean fitsInByte(Object)
boolean fitsInShort(Object)
boolean fitsInInt(Object)
boolean fitsInLong(Object)
boolean fitsInFloat(Object)
boolean fitsInDouble(Object)
byte asByte(Object)
short asShort(Object)
int asInt(Object)
long asLong(Object)
float asFloat(Object)
double asDouble(Object)
```

The InteropLibrary specifies default implementations for the receiver types `Boolean`, `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double`, `Character` and `String`. This design is extendable to support new values like big numbers or a custom String abstraction as Java primitive types are no longer directly used. It is no longer recommended to use primitive types in specializations directly as the set of interop primitive types may change in the future. Instead, always use the interop library to check for a particular type, e.g. use `fitsInInt` instead of `instanceof Integer`.

By using the new messages it is possible to emulate the original UNBOX message like this:

```java
@Specialization(limit="5")
Object doUnbox(Object value, @CachedLibrary("value") InteropLibrary interop) {
    if (interop.isBoolean(value)) {
      return interop.asBoolean(value);
    } else if (interop.isString(value)) {
      return interop.asString(value);
    } else if (interop.isNumber(value)) {
      if (interop.fitsInByte(value)) {
        return interop.asByte(value);
      } else if (interop.fitsInShort(value)) {
        return interop.asShort(value);      
      } else if (interop.fitsInInt(value)) {
        return interop.asInt(value);      
      } else if (interop.fitsInLong(value)) {
        return interop.asLong(value);      
      } else if (interop.fitsInFloat(value)) {
        return interop.asFloat(value);      
      } else if (interop.fitsInDouble(value)) {
        return interop.asDouble(value);      
      } 
    }
    throw UnsupportedMessageException.create();
}
```
Note it is not recommended to unbox all primitive types like this. Instead a language should only unbox to the primitive types it actually uses. Ideally an unbox operation is not needed and the interop library is directly used to implement the operation, like this:

```java
@Specialization(guards = {
                "leftValues.fitsInLong(l)",
                "rightValues.fitsInLong(r)"}, limit="5")
long doAdd(Object l, Object r, 
             @CachedLibrary("l") InteropLibrary leftValues, 
             @CachedLibrary("r") InteropLibrary rightValues) {
       return leftValues.asLong(l) + rightValues.asLong(r);
}
```


### Explicit Namespaces for Array and Member elements

The generic READ and WRITE messages were originally designed with primarily JavaScript use-cases in mind. With the adoption of interop by more languages, it became apparent that there is a need for explicit namespaces for arrays and object members. Over time, the interpretation of READ and WRITE was changed to represent array accesses when used with numbers and object member accesses when used with strings. The HAS_SIZE message was reinterpreted as whether the value contains array elements with additional guarantees, e.g. that array elements were iterable between index 0 and size.

For better interop between languages, there is a need for an explicit Hash/Map/Dictionary entry namespace. Originally it was intended to reuse the generic READ/WRITE namespace for this. For JavaScript, this was possible, as the dictionary and member namespaces were equivalent. Most languages, however, separate Map entries from Object members, which leads to ambiguous keys. It is not possible for the source language (the protocol implementer) to know how this conflict needs to be resolved. Instead, by having explicit namespaces we can let the target language (the protocol caller) decide how to resolve the ambiguity. For example, whether dictionary or member elements should take precedence can now be decided in the target language operation.

The following interop messages were changed:
```
READ, WRITE, REMOVE, HAS_SIZE, GET_SIZE, HAS_KEYS, KEYS
```

The updated protocol with separate member and array namespace in [InteropLibrary](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html) looks like this:

#### Object namespace:

```java
hasMembers(Object)
getMembers(Object, boolean)
readMember(Object, String)
writeMember(Object, String, Object)
removeMember(Object, String)
invokeMember(Object, String, Object...)
```

#### Array namespace:

```java
hasArrayElements(Object)
readArrayElement(Object, long)
getArraySize(Object)
writeArrayElement(Object, long, Object)
removeArrayElement(Object, long)
```

Array access messages no longer throw `UnknownIdentifierException` but `InvalidArrayIndexException`. This was a bug in the original design, where the accessed number needed to be converted to the identifier string in the `UnknownIdentifierException`. 


### Replaced KeyInfo with individual messages

In the previous section, we did not mention the KEY_INFO message. The KEY_INFO message was useful to query all properties of a member or array element. While this was a convenient small API, it was often inefficient as it required the implementer to return all the key info properties. At the same time, it is rare that the caller really needed all key info properties. With Interop 2.0 we removed the KEY_INFO message. Instead, we introduced explicit messages for each namespace, to address this issue.


#### Object namespace:

```java
isMemberReadable(Object, String)
isMemberModifiable(Object, String)
isMemberInsertable(Object, String)
isMemberRemovable(Object, String)
isMemberInvocable(Object, String)
isMemberInternal(Object, String)
isMemberWritable(Object, String)
isMemberExisting(Object, String)
hasMemberReadSideEffects(Object, String)
hasMemberWriteSideEffects(Object, String)
```

#### Array namespace:

```java
isArrayElementReadable(Object, long)
isArrayElementModifiable(Object, long)
isArrayElementInsertable(Object, long)
isArrayElementRemovable(Object, long)
isArrayElementWritable(Object, long)
isArrayElementExisting(Object, long)
```

Note that the array namespace does no longer support querying for read or write side-effects. We might reintroduce these messages, but at the moment there was no use-case. Also, the array namespace does not allow invocations.


### Remove return type for TO_NATIVE

The TO_NATIVE message was renamed to toNative in the InteropLibrary with the difference that it no longer returns a value, but performs the native transition as a side-effect if supported by the receiver. This allows the caller of the message to simplify their code. We found that in no cases the toNative transition required to return a different value. The default behavior fo toNative was changed to not return any value.

### Minor Changes

The following messages were mostly unchanged. The NEW message was renamed to instantiate to be consistent with isInstantiable. 

```
Message.IS_NULL         -> InteropLibrary.isNull
Message.EXECUTE         -> InteropLibrary.execute
Message.IS_INSTANTIABLE -> InteropLibrary.isInstantiable
Message.NEW             -> InteropLibrary.instantiate
Message.IS_EXECUTABLE   -> InteropLibrary.isExecutable
Message.EXECUTE         -> InteropLibrary.execute
Message.IS_POINTER      -> InteropLibrary.isPointer
Message.AS_POINTER      -> InteropLibrary.asPointer
```


### Stronger Assertions

Many new assertions were introduced as part of the migration. The concrete pre-post and invariant conditions are described in the Javadoc. Unlike the old interop nodes, cached libraries can *only* be used when adopted as part of the AST.

### No more unchecked checked exceptions.

With Interop 2.0 `InteropException.raise` was deprecated. While possible, it is considered an anti-pattern to rethrow checked exceptions as unchecked exceptions. With TruffleLibraries the target language nodes are directly inserted into the AST of the caller so there is no longer a limiting `CallTarget` that does not support checked exceptions. Together with additional support for checked Exceptions from Truffle DSL, it should no longer be necessary to use the raise methods. Instead, a new create factory method was introduced for all interop exception types. 

It is planned to remove stack traces from interop exceptions in order to improve their efficiency, as interop exceptions are intended to be always immediately caught and never be rethrown. This was deferred until the compatibility layer can be removed. 


## Migration

With the use of Truffle Libraries for interop, most existing interop APIs had to be deprecated.
The following comparison of Interop 1.0 with Interop 2.0 is designed to help to migrate existing uses of interop.

<hr/>

### Fast-Path Sending Interop Messages

This is the fast-path way of sending interop messages embedded in an operation node. This is the most common way of sending interop messages.

#### Interop 1.0: ####

```java
@ImportStatic({Message.class, ForeignAccess.class})
abstract static class ForeignExecuteNode extends Node {

    abstract Object execute(Object function, Object[] arguments);

    @Specialization(guards = "sendIsExecutable(isExecutableNode, function)")
    Object doDefault(TruffleObject function, Object[] arguments,
                    @Cached("IS_EXECUTABLE.createNode()") Node isExecutableNode,
                    @Cached("EXECUTE.createNode()") Node executeNode) {
        try {
            return ForeignAccess.sendExecute(executeNode, function, arguments);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            // ... convert errors to guest language errors ...
        }
    }
}
```

#### Interop 2.0:####

```java
abstract static class ForeignExecuteNode extends Node {

    abstract Object execute(Object function, Object[] arguments);

    @Specialization(guards = "functions.isExecutable(function)", limit = "2")
    Object doDefault(Object function, Object[] arguments,
                    @CachedLibrary("function") InteropLibrary functions) {
        try {
            return functions.execute(function, arguments);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            // ... convert errors to guest language errors ...
        }
    }
}
```

Note the following differences:

* To invoke messages we call instance methods on `TruffleLibrary` instead of calling a static method on `ForeignAccess`.
* The old interop required to create one node for each operation. With the new version, only one specialized interop library is created. 
* In the old API we needed to specialize the receiver type for `TruffleObject`. The new interop library can be invoked with any interop value. By default `isExecutable` will return `false` for values that don't export the interop library. E.g. it is now valid to call the library with boxed primitive receiver values. 
* Instead of using `@Cached` in the old interop, in the new interop we use `@CachedLibrary`.
* The new `@CachedLibrary` annotation specifies the value the library specializes on. This allows the DSL to specialize the library instance to that value. This again allows the dispatch on the receiver value to be performed once for all message invocations. In the old interop version, the nodes could not be specialized to values. Therefore the dispatch needed to be repeated for every interop message send.
* The specialized library instance requires to specify a `limit` for the specialization method. If this limit overflows the uncached version of the library will be used that does not perform any profiling/caching. The old interop API assumed a constant specialization limit of `8` per interop node.
* The new interop API allows using a dispatched version of the library by specifying `@CachedLibrary(limit="2")` instead. This allows the interop library to be used with any value, but it has the disadvantage of duplicating the inline cache for every message invocation, like with the old interop API. It is therefore recommended to use specialized libraries whenever it is possible.

<hr/>

### Slow-Path Sending Interop Messages

It is sometimes necessary to call interop messages from the runtime without the context of a node. 

#### Interop 1.0: ####

```java
ForeignAccess.sendRead(Message.READ.createNode(), object, "property")
```

#### Interop 2.0:####

```java
InteropLibrary.getFactory().getUncached().read(object, "property");
```

Note the following differences:

* The old interface allocated a node for each invocation.
* The new library uses the uncached version of the library that does not require any allocation or boxing for each invocation.
* With `InteropLibrary.getFactory().getUncached(object)` an uncached and specialized version of a library can be looked up. This can be used to avoid repeated export lookups if multiple uncached interop messages need to be sent to the same receiver.

<hr/>

### Custom Fast-Path Sending Interop Messages

Sometimes Truffle DSL cannot be used and the nodes need to be written manually. Both APIs allow you to do so.

#### Interop 1.0: ####

```java

final class ForeignExecuteNode extends Node {

    @Child private Node isExecutableNode = Message.IS_EXECUTABLE.createNode();
    @Child private Node executeNode = Message.EXECUTE.createNode();

    Object execute(Object function, Object[] arguments) {
        if (function instanceof TruffleObject) {
            TruffleObject tFunction = (TruffleObject) function;
            if (ForeignAccess.sendIsExecutable(isExecutableNode, tFunction)) {
                try {
                    return ForeignAccess.sendExecute(executeNode, tFunction, arguments);
                } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                    // TODO handle errors
                }
            }
        }
        // throw user error
    }
}

```

#### Interop 2.0:####

```java
static final class ForeignExecuteNode extends Node {

    @Child private InteropLibrary functions = InteropLibrary.getFactory().createDispatched(5);

    Object execute(Object function, Object[] arguments) {
        if (functions.isExecutable(function)) {
            try {
                return functions.execute(function, arguments);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                // handle errors
                return null;
            }
        }
        // throw user error
    }
}
```

Note the following differences:

* The new interop creates nodes through the `LibraryFactory<InteropLibrary>` accessible through `InteropLibrary.getFactory()`. The old interop creates dispatching nodes through the `Message` instance.
* The dispatch limit can be specified for the new interop libraries. The old interop API always assumed a constant limit of `8`.
* For the new interop we do not need to check for the type `TruffleObject` as Truffle Libraries can be used with any receiver type. For non-function values `isExecutable` will just return `false`.


<hr/>

### Implementing / Exporting Interop Messages

To implement / export interop library messages see the following example.

#### Interop 1.0: ####

```java

@MessageResolution(receiverType = KeysArray.class)
final class KeysArray implements TruffleObject {

    private final String[] keys;

    KeysArray(String[] keys) {
        this.keys = keys;
    }

    @Resolve(message = "HAS_SIZE")
    abstract static class HasSize extends Node {

        public Object access(KeysArray receiver) {
            return true;
        }
    }

    @Resolve(message = "GET_SIZE")
    abstract static class GetSize extends Node {

        public Object access(KeysArray receiver) {
            return receiver.keys.length;
        }
    }

    @Resolve(message = "READ")
    abstract static class Read extends Node {

        public Object access(KeysArray receiver, int index) {
            try {
                return receiver.keys[index];
            } catch (IndexOutOfBoundsException e) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(String.valueOf(index));
            }
        }
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return KeysArrayForeign.ACCESS;
    }

    static boolean isInstance(TruffleObject array) {
        return array instanceof KeysArray;
    }
}

```

#### Interop 2.0:####

```java
@ExportLibrary(InteropLibrary.class)
final class KeysArray implements TruffleObject {

    private final String[] keys;

    KeysArray(String[] keys) {
        this.keys = keys;
    }

    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
        return index >= 0 && index < keys.length;
    }

    @ExportMessage
    long getArraySize() {
        return keys.length;
    }

    @ExportMessage
    Object readArrayElement(long index) throws InvalidArrayIndexException {
        if (!isArrayElementReadable(index) {
            throw InvalidArrayIndexException.create(index);
        }
        return keys[(int) index];
    }
}
```

Note the following differences:

* Instead of @MessageResolution we use @ExportLibrary.
* Both versions need to implement TruffleObject. The new interop API only requires a TruffleObject type for compatibility reasons.
* Instead of @Resolve the @ExportMessage annotation is used. The latter annotation can infer the name of the message from the method name. If the method name is ambiguous, e.g. when multiple libraries are exported, then the name and library can be specified explicitly.
* There is no need to specify classes for exports/resolves. However, it is still possible to do so if an export needs multiple specializations. See the TruffleLibrary tutorial for details.
* Exceptions are now thrown as checked exceptions.
* It is no longer needed to implement getForeignAccess(). The implementation discovers implementations for receiver types automatically.
* It is no longer needed to implement `isInstance`. The implementation can is now derived from the class signature. Note that the check can be more efficient if the receiver type is declared final. For non-final receiver types, it is recommended to specify exported methods as `final`.


<hr/>

### Integration with DynamicObject

The old interop allowed to specify a foreign access factory through `ObjectType.getForeignAccessFactory()`. This method is now deprecated and a new method `ObjectType.dispatch()` was introduced. Instead of a foreign access factory, the dispatch method needs to return a class that exports the InteropLibrary with an explicit receiver.

#### Interop 1.0: ####

```java
public final class SLObjectType extends ObjectType {

    public static final ObjectType SINGLETON = new SLObjectType();

    private SLObjectType() {
    }

    public static boolean isInstance(TruffleObject obj) {
        return SLContext.isSLObject(obj);
    }

    @Override
    public ForeignAccess getForeignAccessFactory(DynamicObject obj) {
        return SLObjectMessageResolutionForeign.ACCESS;
    }
}

@MessageResolution(receiverType = SLObjectType.class)
public class SLObjectMessageResolution {

    @Resolve(message = "WRITE")
    public abstract static class SLForeignWriteNode extends Node {...}

    @Resolve(message = "READ")
    public abstract static class SLForeignReadNode extends Node {...}
    ...

```

#### Interop 2.0:####

```java
@ExportLibrary(value = InteropLibrary.class, receiverType = DynamicObject.class)
public final class SLObjectType extends ObjectType {

    public static final ObjectType SINGLETON = new SLObjectType();

    private SLObjectType() {
    }

    @Override
    public Class<?> dispatch() {
        return SLObjectType.class;
    }

    @ExportMessage
    static boolean hasMembers(DynamicObject receiver) {
        return true;
    }

    @ExportMessage
    static boolean removeMember(DynamicObject receiver, String member) throws UnknownIdentifierException {...}
    
    ... other exports omitted ...
 }
```


Note the following differences:

* The object type can be reused as the export class.
* The isInstance method no longer needs to be specified.
* The new interop requires to specify the receiver type to DynamicObject.


<hr/>

### Extending Interop

Truffle languages rarely need to extend interop, but they might need to extend their own language specific protocol.  

#### Interop 1.0: ####

* Add new KnownMessage subclass called `FooBar`.
* Add a new method `sendFooBar` to `ForeignAccess`.
* Add a new method to `ForeignAccess.Factory`: `createFooBar`.
* Modify the interop annotation processor to generate the code for `createFooBar`.

#### Interop 2.0:####

* Add a new method `fooBar` in `InteropLibrary`. Everything else is done automatically.


