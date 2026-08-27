# FS-003-reflection: Java Reflection Registration and Run-Time Access

The behavior selected by `--future-defaults=exact-reflection` is the Native Image default.
An application can use a class, method, constructor, or field at run time only when the executable
contains the metadata required for that operation.
Program reachability alone does not grant reflective access.
This follows §FD-001-hotspot-compatible-semantics-by-default.2 and
§FD-002-reachability-independent-runtime-semantics.2.

Registering a type makes it available to name-based class lookup and makes its `Class` metadata
available.
It does not make the type's constructors invocable, its methods invocable, its fields readable or
writable, or its instances serializable.
Those operations have separate registrations.

For example, this entry registers `example.Message` as a type:

```json
{
  "reflection": [
    {
      "type": "example.Message"
    }
  ]
}
```

`Class.forName("example.Message")` can then return the class, and methods on the returned `Class`
object can inspect it as described in [§2](#2-class-objects-and-type-registration).
Creating an instance through `Class.newInstance`, invoking a constructor or method, accessing a
field value, serializing an instance, and unsafe allocation still require their corresponding
metadata.

Missing required metadata causes `MissingReflectionRegistrationError`.
Once enough metadata is present to perform an operation, the operation has the Java Platform
behavior, including its standard result or exception.

This specification does not define resource inclusion.
Calls such as `Class.getResource` and `ClassLoader.getResource` use the separately specified
resource metadata even when their receiver class is registered for reflection.

The implementation is described by
[§AR-002-reflection](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/reflect/ReflectionDataBuilder.java).

## 1. Registration Model

This specification uses the [common notation](README.md#notation).
It names classes in `java.lang`, `java.lang.invoke`, and `java.lang.reflect` by their simple names
when the intended class is clear.

A type is **registered** if and only if active reflection metadata covers that type.
A method, constructor, or field is **queryable** if and only if active metadata permits a reflective
query to return its reflection object.
A method or constructor is **invocable** if and only if active metadata permits reflective
invocation of that member.
A field is **accessible** if and only if active metadata permits its value to be read or written.

Registration is cumulative.
Registering a stronger capability also supplies the weaker metadata needed to find the registered
element.
For example, registering a method for invocation also makes that method queryable.
The reverse does not hold: a queryable method need not be invocable.

Registration can come from _reachability-metadata.json_, the Feature API, the Tracing Agent,
`-H:Preserve`, or a build-time constant that Native Image recognizes.
These inputs have the same run-time meaning after their conditions are satisfied.

## 2. Class Objects and Type Registration

A `Class` object describes a Java type.
Having a `Class` object, registering that type, and registering the type's members are separate
facts.

### 2.1 Operations That Do Not Require Type Registration

No reflection registration is required to use a `Class` object that the application already has
without a name-based lookup.
Such an object can come from a class literal, `Object.getClass`, a primitive type, an existing array,
or a relationship returned from another `Class` object.

The following categories of operations are always available for such a `Class` object:

- identity, comparison, modifiers, and access flags;
- names and descriptions, including `getName`, `getTypeName`, `getSimpleName`, `getCanonicalName`,
  `descriptorString`, `describeConstable`, `toString`, and `toGenericString`;
- primitive name lookup through `Class.forPrimitiveName`;
- type-kind tests, including `isArray`, `isInterface`, `isPrimitive`, `isAnnotation`, `isEnum`,
  `isRecord`, `isSealed`, `isSynthetic`, `isHidden`, `isAnonymousClass`, `isLocalClass`, and
  `isMemberClass`;
- type relations such as `isInstance`, `isAssignableFrom`, `cast`, and `asSubclass`;
- direct type structure such as `getSuperclass`, `getInterfaces`, `getComponentType`,
  `componentType`, `getDeclaringClass`, `getEnclosingClass`, `getEnclosingMethod`,
  `getEnclosingConstructor`, `getNestHost`, and `isNestmateOf`;
- class loader, module, package, protection domain, and assertion status;
- generic type information, annotations, annotated super types, and enum constants.

These operations can return another `Class` object without registering the returned type for
name-based lookup or registering any of its members.
`Class.getResource` and `Class.getResourceAsStream` also require no reflection registration, but
whether they find a resource is defined by the separate resource specification.

### 2.2 Effects of Registering a Type

Registering a type permits every `Class` operation on that type except `Class.newInstance`.
In particular, registration permits:

- the `Class.forName` overloads to acquire the type by its binary name;
- `arrayType` to return the immediate array type;
- queries for declared and public methods, constructors, and fields;
- queries for declared and public member classes;
- queries for record components, permitted subclasses, nest members, and signers.

Public-member queries include inherited public members according to the Java Platform rules.
Declared-member queries cover only the registered type.

Type registration makes returned members queryable but not invocable or accessible.
For example, after registering only `example.Message`, `Message.class.getDeclaredMethods()` can
return the declared methods, but `Method.invoke` still requires invocation metadata for the selected
method.

`Class.newInstance` is the exception because it creates an object.
It requires the selected no-argument constructor to be invocable and otherwise fails as described
in [§6.1](#61-methods-and-constructors).

### 2.3 Arrays

Primitive and existing array classes need no type registration for their ordinary `Class`
operations.
Registering a non-void type also makes its immediate array class available through `Class.arrayType`.
It does not recursively register arrays of further dimensions.

Creating an array reflectively requires the requested array class to be available.
Registering an array type supplies that availability without registering the component type's
members.

## 3. Other Class Acquisition

All name-based acquisition paths use the same type registration boundary.
Acquiring a class does not initialize it unless the selected Java API requests initialization.

### 3.1 Class Loader Lookup

`ClassLoader.loadClass(String)` can return an image-built class when that type is registered for
reflection and is visible to the selected class loader.
The class loader's normal delegation and visibility rules still apply.

Defining a class at run time is separate from acquiring it.
When run-time class loading follows reflection configuration, the binary name passed to
`ClassLoader.defineClass`, or read from the class file by an unnamed `defineClass` overload, must be
registered before the definition is attempted.
After a class is defined, its defining loader can return it without build-time member metadata.
Registering an image-built type does not authorize defining a different class with the same name.

### 3.2 Method Handle Lookup

`MethodHandles.Lookup.findClass` and equivalent method-handle class resolution can acquire only a
registered image-built type that is visible to the lookup class.

Method-handle lookups for methods, constructors, and fields additionally require the corresponding
member registration.
Successfully resolving the declaring class is not sufficient to resolve an unregistered member.
Invoking the resulting handle is permitted when the member is invocable or accessible under
[§6](#6-reflective-invocation-and-field-access).

## 4. Proxies

A dynamic proxy type is identified by its ordered list of interfaces.
Two lists containing the same interfaces in a different order describe different proxy
registrations.

For example:

```json
{
  "reflection": [
    {
      "type": {
        "proxy": [
          "example.Request",
          "java.io.Serializable"
        ]
      }
    }
  ]
}
```

An active proxy registration permits `Proxy.getProxyClass` and `Proxy.newProxyInstance` for that
interface list and class loader when the Java Platform proxy rules are otherwise satisfied.
Registering the interfaces as ordinary types does not register their proxy.
Registering the proxy does not grant unrelated reflective access to the interfaces.

Serialization of a proxy is a separate capability described in [§7](#7-serialization).

## 5. Lambdas

A lambda registration selects generated lambda classes by:

- the declaring class;
- an optional declaring method name and parameter types; and
- the interfaces implemented by the generated lambda class.

For example:

```json
{
  "reflection": [
    {
      "type": {
        "lambda": {
          "declaringClass": "example.MessageFactory",
          "declaringMethod": {
            "name": "create",
            "parameterTypes": []
          },
          "interfaces": [
            "java.util.function.Supplier"
          ]
        }
      }
    }
  ]
}
```

If the declaring method is omitted, the selector applies across the declaring class.
A selector can match more than one generated lambda class; its registration applies to every match.

Ordinary execution of a reachable lambda does not require reflection metadata.
The registration is needed when the generated lambda class is acquired or inspected reflectively.
Serialization additionally requires [§7](#7-serialization).

## 6. Reflective Invocation and Field Access

Querying a member and using that member are separate operations.
Type registration supplies queries for all members of the type, while explicit member metadata
selects which returned members can be used.

### 6.1 Methods and Constructors

An invocable method can be called through `Method.invoke` and the corresponding method-handle
invocation paths.
An invocable constructor can create an instance through `Constructor.newInstance` and, for the
no-argument constructor selected by that API, `Class.newInstance`.

The invocation uses the Java Platform access, argument-conversion, dispatch, initialization, and
exception rules.
Registration does not bypass Java access control; `AccessibleObject.setAccessible` has its normal
effect only when module and language access permit it.

Query-only metadata permits `Class` to return the `Method` or `Constructor` object but does not
create an invocation accessor.
Attempting to invoke it produces `MissingReflectionRegistrationError`.

### 6.2 Fields

An accessible field can be read and written through the typed and untyped `Field` accessors and the
corresponding method-handle or variable-handle access paths.
The Java Platform receiver, conversion, final-field, initialization, and access-control rules still
apply.

Query-only metadata permits `Class` to return the `Field` object but does not permit reading or
writing its value.
Attempting either operation produces `MissingReflectionRegistrationError`.

## 7. Serialization

Serialization registration is reflection metadata because Java serialization creates objects and
accesses their state through reflection-specific construction support.
A type is serializable in a native executable only when active metadata registers it for
serialization and the type otherwise satisfies the Java serialization rules.

For example:

```json
{
  "reflection": [
    {
      "type": "example.Message",
      "serializable": true
    }
  ]
}
```

Serialization registration retains the constructors, generated accessors, and class information
needed for serialization and deserialization.
It does not make arbitrary application methods invocable or fields accessible through the public
reflection APIs.

The same `serializable` capability can be attached to a proxy or lambda type descriptor.
For a lambda, only lambda classes selected by that descriptor are serializable.
For a proxy, the ordered interface list must match the registered proxy descriptor.

## 8. Unsafe Operations

Unsafe operations are explicit capabilities and are not implied by type or member-query
registration.

### 8.1 Unsafe Allocation

`Unsafe.allocateInstance(Class)` can allocate a type only when active metadata marks that type as
`unsafeAllocated`.
This allocation does not invoke a constructor.
Equivalent application-visible allocation paths use the same capability.

VM-internal allocation of an object whose ordinary allocation was already proven reachable does
not require application reflection metadata.

### 8.2 Field Offsets

`Unsafe.objectFieldOffset(Field)` and `Unsafe.staticFieldOffset(Field)` require the field to be
accessible, not merely queryable.
The returned offset can then be used according to the applicable `Unsafe` contract.

Type registration alone therefore permits finding the `Field` object but does not permit obtaining
its offset.

## 9. Conditions

A condition is orthogonal to the kind of reflection metadata it guards.
The same condition rules apply to a type, method, constructor, field, proxy, lambda, serialization,
or unsafe-allocation registration.

### 9.1 Activation

Before its condition is satisfied, guarded metadata behaves as if it were absent.
A dynamic operation that requires it produces `MissingReflectionRegistrationError`.

### 9.2 Type Matching

A `typeReached` condition becomes satisfied immediately before the condition type is initialized or
when one of its subtypes is reached.
A class literal alone does not satisfy the condition.

### 9.3 Lifetime

After the condition is satisfied, the registration remains active for the rest of the process.

### 9.4 Composition

Multiple registrations for the same element combine cumulatively.
At a given operation, the available capability is the strongest capability supplied by the
registrations whose conditions are satisfied.
Activating a condition does not retroactively change an already completed operation, but subsequent
queries and accesses observe the newly active metadata.

## 10. Earlier Behavior

Earlier Native Image versions used reachability-driven reflection behavior unless exact reflection
was selected explicitly with `--future-defaults=exact-reflection` or a legacy exact-metadata option.
That behavior could make a member invocable or a field accessible because it was reachable for an
unrelated reason, and some missing registrations appeared as an empty result, `null`, or a standard
lookup exception.

Exact reflection is now the default.
Only active metadata grants dynamic access, and missing metadata is reported at the operation that
requires it.
Compatibility reporting modes may warn and continue with the earlier behavior, but they do not
change the registration contract defined by this specification.

## 11. Requirements

### 11.1 Type Registration Closure

Registering a type must make every `Class` operation except `Class.newInstance` available for that
type, including all metadata needed to distinguish an absent member from missing metadata.
It must not make any constructor or method invocable or any field accessible solely because the
type is registered.

### 11.2 Acquisition Consistency

`Class.forName`, `ClassLoader.loadClass`, and method-handle class lookup must use the same
registration result for an image-built type, subject to their Java Platform loader and access
rules.

### 11.3 Access Separation

The implementation must preserve the distinction between type registration, member queries,
member invocation or field access, serialization, and unsafe allocation.
An implementation component must not use analysis reachability as a substitute for one of these
registrations.

### 11.4 Condition Fidelity

Every run-time decision must test the active conditions for the capability it consumes.
Build-time retention needed to implement conditional metadata must not make that capability
available before its condition is satisfied.

### 11.5 Error Sufficiency

A missing-metadata diagnostic must identify the unavailable class or member and the capability
needed to complete the operation.
It must not perform the prohibited operation as a side effect of producing the diagnostic.
