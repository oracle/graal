# Migrating from DynamicObjectLibrary to DynamicObject Nodes

`DynamicObjectLibrary` has been deprecated in 25.1, and replaced with new, lighter-weight node-based APIs, found as subclasses of `DynamicObject`.
Each `DynamicObjectLibrary` message has an equivalent node replacement.

| `DynamicObjectLibrary` message             | `DynamicObject` node equivalent                                        | Purpose                                                                       |
|--------------------------------------------|------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| `getOrDefault(obj, key, def)`              | `DynamicObject.GetNode.execute(obj, key, def)`                         | Gets the value of a property or a default value if absent                     |
| `getIntOrDefault(obj, key, def)`           | `DynamicObject.GetNode.executeInt(obj, key, def)`                      | Gets the value of a property as int or throws UnexpectedResultException       |
| `getLongOrDefault(obj, key, def)`          | `DynamicObject.GetNode.executeLong(obj, key, def)`                     | Gets the value of a property as long or throws UnexpectedResultException      |
| `getDoubleOrDefault(obj, key, def)`        | `DynamicObject.GetNode.executeDouble(obj, key, def)`                   | Gets the value of a property as double or throws UnexpectedResultException    |
| `put(obj, key, val)`                       | `DynamicObject.PutNode.execute(obj, key, val)`                         | Adds a new property of sets the value of an existing property                 |
| `putWithFlags(obj, key, val, flags)`       | `DynamicObject.PutNode.executeWithFlags(obj, key, val, flags)`         | Adds a new property or sets the value and flags of an existing property       |
| `putIfPresent(obj, key, val)`              | `DynamicObject.PutNode.executeIfPresent(obj, key, val)`                | Sets the value of a property, if present                                      |
| `putConstant(obj, key, val, flags)`        | `DynamicObject.PutConstantNode.executeWithFlags(obj, key, val, flags)` | Adds or replaces a property with a constant shape-bound value, use sparingly  |
| `containsKey(obj, key)`                    | `DynamicObject.ContainsKeyNode.execute(obj, key)`                      | Checks for the existence of a property                                        |
| `removeKey(obj, key)`                      | `DynamicObject.RemoveKeyNode.execute(obj, key)`                        | Removes a property                                                            |
| `getPropertyFlagsOrDefault(obj, key, def)` | `DynamicObject.GetPropertyFlagsNode.execute(obj, key, def)`            | Gets the flags of a property or a default value if absent                     |
| `setPropertyFlags(obj, key, flags)`        | `DynamicObject.SetPropertyFlagsNode.execute(obj, key, flags)`          | Updates the flags or a property                                               |
| `getKeyArray(obj)`                         | `DynamicObject.GetKeyArrayNode.execute(obj)`                           | Returns an array of the keys of all the non-hidden properties                 |
| `getProperty(obj, key)`                    | `DynamicObject.GetPropertyNode.execute(obj, key)`                      | Gets the property descriptor or null if absent                                |
| `getPropertyArray(obj)`                    | `DynamicObject.GetPropertyArrayNode.execute(obj)`                      | Returns an array of the property descriptors of all the non-hidden properties |
| `getShapeFlags(obj)`                       | `DynamicObject.GetShapeFlagsNode.execute(obj)`                         | Gets the language-specific shape flags                                        |
| `setShapeFlags(obj, flags)`                | `DynamicObject.SetShapeFlagsNode.execute(obj, flags)`                  | Sets the language-specific shape flags                                        |
| `getDynamicType(obj)`                      | `DynamicObject.GetDynamicTypeNode.execute(obj)`                        | Gets the language-specific dynamic type identifier                            |
| `setDynamicType(obj, type)`                | `DynamicObject.SetDynamicTypeNode.execute(obj, type)`                  | Sets the language-specific dynamic type identifier                            |
| `updateShape(obj)`                         | `DynamicObject.UpdateShapeNode.execute(obj)`                           | Updates the shape if the object has an obsolete shape                         |
| `resetShape(obj, rootShape)`               | `DynamicObject.ResetShapeNode.execute(obj, rootShape)`                 | Resets the object to an empty shape                                           |
| `markShared(obj)`                          | `DynamicObject.MarkSharedNode.execute(obj)`                            | Marks the object as shared                                                    |
| `isShared(obj)`                            | `DynamicObject.IsSharedNode.execute(obj)`                              | Queries the object's shared state                                             |
| `getShape(obj)`                            | `obj.getShape()`                                                       | No node equivalent, use direct API                                            |

Note: Unlike `DynamicObjectLibrary`, cached property keys are always compared by identity (`==`) rather than equality (`equals`).
If you rely on key equality, cache the key using an `equals` guard and pass the cached canonical key to the node.

## Code examples

### Reading a property

#### Getting the value of a property with a fixed key or a dynamic key that is already unique or interned

```java
abstract class GetUniqueKeyNode extends Node {

    abstract Object execute(DynamicObject receiver, Object key);

    @Specialization
    static Object doCached(MyDynamicObject receiver, Symbol key,
                    @Cached DynamicObject.GetNode getNode) {
        return getNode.execute(receiver, key, NULL_VALUE);
    }
}
```

#### Getting the value of a property with a dynamic key and key equality

```java
@ImportStatic(TruffleString.Encoding.class)
abstract class GetStringKeyNode extends Node {

    abstract Object execute(DynamicObject receiver, Object key);

    @Specialization(guards = "equalNode.execute(key, cachedKey, UTF_16)", limit = "3")
    static Object doCached(MyDynamicObject receiver, TruffleString key,
                    @Cached("key") TruffleString cachedKey,
                    @Cached TruffleString.EqualNode equalNode,
                    @Shared @Cached DynamicObject.GetNode getNode) {
        return getNode.execute(receiver, cachedKey, NULL_VALUE);
    }

    @Specialization(replaces = "doCached")
    static Object doGeneric(MyDynamicObject receiver, TruffleString key,
                    @Shared @Cached DynamicObject.GetNode getNode) {
        return getNode.execute(receiver, key, NULL_VALUE);
    }
}
```

Note that key interning is not required since only the cached code path compares keys by identity (`==`), the generic code path still uses `equals` to compare the property keys.

#### Getting the value of a property with boxing elimination

```java
abstract class GetUnboxedNode extends Node {

    abstract int executeInt(DynamicObject receiver, Object key) throws UnexpectedResultException;

    abstract Object execute(DynamicObject receiver, Object key);

    @Specialization(rewriteOn = UnexpectedResultException.class)
    static int doInt(MyDynamicObject receiver, Symbol key,
                    @Shared @Cached DynamicObject.GetNode getNode) throws UnexpectedResultException {
        return getNode.executeInt(receiver, key, NULL_VALUE);
    }

    @Specialization(replaces = "doInt")
    static Object doGeneric(MyDynamicObject receiver, Symbol key,
                    @Shared @Cached DynamicObject.GetNode getNode) {
        return getNode.execute(receiver, key, NULL_VALUE);
    }
}
```

### Writing a property

#### Adding a property or setting the value of a property with a fixed key or a dynamic key that is already unique or interned

```java
abstract class SetUniqueKeyNode extends Node {

    abstract void execute(DynamicObject receiver, Object key, Object value);

    @Specialization
    static void doCached(MyDynamicObject receiver, Symbol key, Object value,
                @Cached DynamicObject.PutNode putNode) {
        putNode.execute(receiver, key, value);
    }
}
```

#### Setting the value of a property with a dynamic key and key equality

```java
@ImportStatic(TruffleString.Encoding.class)
abstract class SetStringKeyNode extends Node {

    abstract void execute(DynamicObject receiver, Object key, Object value);

    @Specialization(guards = "equalNode.execute(key, cachedKey, UTF_16)", limit = "3")
    static void doCached(MyDynamicObject receiver, TruffleString key, Object value,
                @Cached("key") TruffleString cachedKey,
                @Cached TruffleString.EqualNode equalNode,
                @Cached DynamicObject.PutNode putNode) {
        putNode.execute(receiver, cachedKey, value);
    }

    @Specialization(replaces = "doCached")
    static void doGeneric(MyDynamicObject receiver, TruffleString key,
                @Shared @Cached DynamicObject.PutNode getNode) {
        putNode.execute(receiver, key, value);
    }
}
```

Note that key interning is not required since only the cached code path compares keys by identity (`==`), the generic code path still uses `equals` to compare the property keys.
