---
layouts docs
toc_group: truffle
link_title: Members Interop Migration
permalink: /graalvm-as-a-platform/language-implementation-framework/MembersInteropMigration/
---
# Members Interop Migration

This document is targeted at guest language and tool implementers, who have an existing interop implementations.
For fully detailed reference documentation see the [InteropLibrary Javadoc](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html).

## Interop Protocol Changes

### Member Object

Member identifiers were changed from a String to an interop member object. A member object represents a unique identification of guest language member like a field or a method. Overloaded methods could not be distinguished by a String name, but every overloaded method has a distinct member object.

Following new messages were introduced to represent a member:

```
boolean isMember(Object)
Object getMemberSimpleName(Object)
Object getMemberQualifiedName(Object)
boolean isMemberKindField(Object)
boolean isMemberKindMethod(Object)
boolean isMemberKindMetaObject(Object)
boolean hasMemberSignature(Object)
Object getMemberSignature(Object)
```

Member objects are provided by a new `Object getMemberObjects(Object)` message, which replaces the deprecated `Object getMembers(Object)`.
Internal members are not distinguished any more. Additional implementation members can be a part of the upcoming tooling view instead.

### Member Signature

The members may have an optional signature associated, to be able to distinguish overloads and to find out which arguments are expected in case of methods.
The signature is an array, where every element is represented by following messages:
```
boolean isSignatureElement(Object)
boolean hasSignatureElementName(Object)
Object getSignatureElementName(Object)
boolean hasSignatureElementMetaObject(Object)
Object getSignatureElementMetaObject(Object)
```
The first array element represents the return value, the rest represent arguments.

### Messages Taking Member as an Argument

Existing messages that take a String message are deprecated. They are replaced with messages of the same name taking an object member, or an interop string.

Therefore the new messages can still be called with a String (or newly also a `TruffleString`) member,
but the specific member resolution, in case of overloads, will still be up to the language implementation.
When called with an object member, that specific member identification will be used.

### Static Receiver

The `Object getMemberObjects(Object)` message, as well as the deprecated `Object getMembers(Object)` return instance members.
To get the static members, which are independent on a particular instance, a static receiver was introduced:
```
boolean hasStaticReceiver(Object receiver)
Object getStaticReceiver(Object receiver)
```
`getMemberObjects(Object)` called on the static receiver returns static members.

### Declared Members

Members declared by a meta-objec can be found by a new `getDeclaredMembers(Object)` message:
```
boolean hasDeclaredMembers(Object)
Object getDeclaredMembers(Object)
```
These messages are valid on a meta-object only. They allow an interop reflection.

## Examples

- A simple member implementation with corresponding interop messages can be found at [SimpleMemberExample.java](https://github.com/oracle/graal/tree/master/truffle/src/com.oracle.truffle.api.test/src/com/oracle/truffle/api/test/interop/examples/SimpleMemberExample.java).
- A complete implementation including declared members on metaobjects can be found at [ExtensiveMemberExample.java](https://github.com/oracle/graal/tree/master/truffle/src/com.oracle.truffle.api.test/src/com/oracle/truffle/api/test/interop/examples/ExtensiveMemberExample.java).

