---
layout: docs toc_group: truffle link_title: Truffle Strings Guide permalink:
/graalvm-as-a-platform/language-implementation-framework/TruffleStrings/
---

# Truffle Strings Guide

Truffle Strings is Truffle's primitive String type, which can be shared between languages. Language implementers are
encouraged to use Truffle Strings as their language's string type for easier interoperability and better performance.

`TruffleString` supports a plethora of string encodings, but is especially optimized for the most commonly used:

* `UTF-8`
* `UTF-16`
* `UTF-32`
* `US-ASCII`
* `ISO-8859-1`
* `BYTES`

### TruffleString API

All operations exposed by `TruffleString` are provided as an inner `Node`, and as a static or instance methods. Users
should use the provided nodes where possible, as the static/instance methods are just shorthands for executing their
respective node's uncached version. All nodes are named `{NameOfOperation}Node`, and all convenience methods are
named `{nameOfOperation}Uncached`.

Some operations support lazy evaluation, such as lazy concatenation or lazy evaluation of certain string properties.
Most of these operations provide a parameter `boolean lazy`, which allows the user to enable or disable lazy evaluation
on a per-callsite basis.

Operations dealing with index values, such as `CodePointAtIndex`, are available in two variants: codepoint-based
indexing and byte-based indexing. Byte-based indexing is indicated by the `ByteIndex`-suffix or prefix in an operation's
name, otherwise indices are based on codepoints. For example, the index parameter of`CodePointAtIndex` is
codepoint-based, whereas `CodePointAtByteIndex` uses a byte-based index.

The list of currently available operations is listed below and grouped by category.

Creating a new TruffleString:

* [FromCodePoint](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.FromCodePointNode.html):
  Create a new TruffleString from a given codepoint.
* [FromLong](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.FromLongNode.html):
  Create a new TruffleString from a given long value.
* [FromByteArray](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.FromByteArrayNode.html):
  Create a new TruffleString from a given byte array.
* [FromCharArrayUTF16](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.FromCharArrayUTF16Node.html):
  Create a UTF-16 TruffleString from a given char array.
* [FromIntArrayUTF32](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.FromIntArrayUTF32Node.html):
  Create a UTF-32 TruffleString from a given int array.
* [FromJavaString](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.FromJavaStringNode.html):
  Create a TruffleString from a given `java.lang.String`.
* [FromNativePointer](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.FromNativePointerNode.html):
  Create a new TruffleString from a given native pointer.
* [Encoding#getEmpty](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.Encoding.html#getEmpty--):
  Get an empty TruffleString in that encoding.

Query string properties:

* [isEmpty](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/AbstractTruffleString.html#isEmpty--):
  Check if a string is empty.
* [CodePointLength](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.CodePointLengthNode.html):
  Get a string's length in codepoints.
* [byteLength](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/AbstractTruffleString.html#byteLength-com.oracle.truffle.api.strings.TruffleString.Encoding-):
  Get a string's length in bytes.
* [IsValid](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.IsValidNode.html):
  Check whether a string is encoded correctly.
* [GetCodeRange](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.GetCodeRange.html):
  Get coarse information about the string's content (are all codepoints in this string from the ASCII/LATIN-1/BMP
  range?).
* [GetByteCodeRange](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.GetByteCodeRangeNode.html):
  Get coarse information about the string's content, without taking 16/32-bit based encodings into account.
* [CodeRangeEquals](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.CodeRangeEqualsNode.html):
  Check whether a string's code range equals the given code range.
* [isCompatibleTo](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/AbstractTruffleString.html#isCompatibleTo-com.oracle.truffle.api.strings.TruffleString.Encoding-):
  Check if a string is compatible to / can be viewed in a given encoding.
* [isManaged](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/AbstractTruffleString.html#isManaged--):
  Check if a string is not backed by a native pointer.
* [isNative](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/AbstractTruffleString.html#isNative--):
  Check if a string is backed by a native pointer.
* [isImmutable](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/AbstractTruffleString.html#isImmutable--):
  Check if a string is an instance of `TruffleString`.
* [isMutable](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/AbstractTruffleString.html#isMutable--):
  Check if a string is an instance of `MutableTruffleString`.

Comparison:

* [Equal](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.EqualNode.html):
  Check if two strings are equal. Note that this operation is encoding-sensitive!
* [RegionEqual](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.RegionEqualNode.html):
  Check if two strings are equal in a given region defined by a codepoint-based offset and length.
* [RegionEqualByteIndex](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.RegionEqualByteIndexNode.html):
  Check if two strings are equal in a given region defined by a byte-based offset and length.
* [CompareBytes](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.CompareBytesNode.html):
  Compare two strings byte-by-byte.
* [CompareCharsUTF16](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.CompareCharsUTF16Node.html):
  Compare two UTF-16 strings char-by-char.
* [CompareIntsUTF32](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.CompareIntsUTF32Node.html):
  Compare two UTF-32 strings int-by-int.
* [HashCode](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.HashCodeNode.html):
  Get a string's hash code. The hash code is based on the string's bytes, so strings with the same codepoints but
  different encodings may have different hash codes.

Conversion:

* [SwitchEncoding](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.SwitchEncodingNode.html):
  Convert a string to a given encoding.
* [ForceEncoding](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.ForceEncodingNode.html):
  Create a string containing the same bytes as the given string, but assigned to the given encoding.
* [AsTruffleString](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.AsTruffleStringNode.html):
  Convert a MutableTruffleString to an immutable TruffleString.
* [AsManaged](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.AsManagedNode.html):
  Convert a TruffleString backed by a native pointer to one backed by a java byte array.
* [Materialize](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.MaterializeNode.html):
  Force evaluation of lazily calculated string properties and materialization of a string's backing array.
* [CopyToByteArray](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.CopyToByteArrayNode.html):
  Copy a string's content into a byte array.
* [GetInternalByteArray](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.GetInternalByteArrayNode.html):
  Get a string's internal byte array.
* [CopyToNativeMemory](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.CopyToNativeMemoryNode.html):
  Copy a string's content into a native pointer.
* [GetInternalNativePointer](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.GetInternalNativePointerNode.html):
  Get a native string's pointer object.
* [ToJavaString](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.ToJavaStringNode.html):
  Convert a string to a `java.lang.String`.
* [ParseInt](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.ParseIntNode.html):
  Parse a string's content as an int value.
* [ParseLong](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.ParseLongNode.html):
  Parse a string's content as a long value.
* [ParseDouble](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.ParseDoubleNode.html):
  Parse a string's content as a double value.

Accessing codepoints and bytes:

* [ReadByte](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.ReadByteNode.html):
  Read a single byte from a string.
* [ReadCharUTF16](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.ReadCharUTF16Node.html):
  Read a single char from a UTF-16 string.
* [CodePointAtIndex](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.CodePointAtIndexNode.html):
  Read a single codepoint from a string at a given codepoint-based index.
* [CodePointAtByteIndex](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.CodePointAtByteIndexNode.html):
  Read a single codepoint from a string at a given byte-based index.
* [CreateCodePointIterator](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.CreateCodePointIteratorNode.html):
  Return a `TruffleStringIterator` object suitable for iterating the string's codepoints.
* [CreateBackwardCodePointIterator](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.CreateBackwardCodePointIteratorNode.html):
  Return a `TruffleStringIterator` object suitable for iterating the string's codepoints, starting from the end of the
  string.
* [ByteLengthOfCodePoint](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.ByteLengthOfCodePointNode.html):
  Return the number of bytes occupied by the codepoint starting at a given byte index.
* [CodePointIndexToByteIndex](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.CodePointIndexToByteIndexNode.html):
  Convert a given codepoint index to a byte index on a given string.

Search:

* [ByteIndexOfAnyByte](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.ByteIndexOfAnyByteNode.html):
  Find the first occurrence of any of a set of given bytes in a string and return its byte-based index.
* [CharIndexOfAnyCharUTF16](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.CharIndexOfAnyCharUTF16Node.html):
  Find the first occurrence of any of a set of given chars in a UTF-16 string and return its char-based index.
* [IntIndexOfAnyIntUTF32](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.IntIndexOfAnyIntUTF32Node.html):
  Find the first occurrence of any of a set of given ints in a UTF-32 string and return its int-based index.
* [IndexOfCodePoint](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.IndexOfCodePointNode.html):
  Find the first occurrence of a given codepoint in a string and return its codepoint-based index.
* [ByteIndexOfCodePoint](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.ByteIndexOfCodePointNode.html):
  Find the first occurrence of a given codepoint in a string and return its byte-based index.
* [LastIndexOfCodePoint](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.LastIndexOfCodePointNode.html):
  Find the last occurrence of a given codepoint in a string and return its codepoint-based index.
* [LastByteIndexOfCodePoint](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.LastByteIndexOfCodePointNode.html):
  Find the last occurrence of a given codepoint in a string and return its byte-based index.
* [IndexOfString](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.IndexOfStringNode.html):
  Find the first occurrence of a given substring in a string and return its codepoint-based index.
* [ByteIndexOfString](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.ByteIndexOfStringNode.html):
  Find the first occurrence of a given substring in a string and return its byte-based index.
* [LastIndexOfString](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.LastIndexOfStringNode.html):
  Find the last occurrence of a given substring in a string and return its codepoint-based index.
* [LastByteIndexOfString](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.LastByteIndexOfStringNode.html):
  Find the last occurrence of a given substring in a string and return its byte-based index.

Combining:

* [Concat](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.ConcatNode.html):
  Concatenate two strings.
* [Substring](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.SubstringNode.html):
  Create a substring from a given string, bounded by a codepoint-based offset and length.
* [SubstringByteIndex](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.SubstringByteIndexNode.html):
  Create a substring from a given string, bounded by a byte-based offset and length.
* [Repeat](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.RepeatNode.html):
  Repeat a given string *n* times.

### Instantiation

A `TruffleString` can be created from a codepoint, a number, a primitive array or a `java.lang.String`.

Strings of any encoding can be created with `TruffleString.FromByteArrayNode`, which expects a byte array containing the
already encoded string. This operation can be non-copying, by setting the `copy` parameter to `false`. Caution:
TruffleStrings will assume the array content to be immutable, do not modify the array after passing it to the
non-copying variant of this operation!

```java
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.strings.TruffleString;

abstract static class SomeNode extends Node {

    @Specialization
    static TruffleString someSpecialization(
            @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
        byte[] array = {'a', 'b', 'c'};
        return fromByteArrayNode.execute(array, 0, array.length, TruffleString.Encoding.UTF_8, false);
    }
}
```

For easier creation of UTF-16 and UTF-32 strings independent of the system's endianness, TruffleString
provides `TruffleString.FromCharArrayUTF16Node` and `TruffleString.FromIntArrayUTF32Node`.

TruffleStrings may also be created via `TruffleStringBuilder`, which is TruffleString's equivalent
to `java.lang.StringBuilder`.

`TruffleStringBuilder` provides the following operations:

* [AppendByte](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleStringBuilder.AppendByteNode.html):
  Append a single byte to a string builder.
* [AppendCharUTF16](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleStringBuilder.AppendCharUTF16Node.html):
  Append a single char to a UTF-16 string builder.
* [AppendCodePoint](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleStringBuilder.AppendCodePointNode.html):
  Append a single codepoint to string builder.
* [AppendIntNumber](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleStringBuilder.AppendIntNumberNode.html):
  Append an integer number to a string builder.
* [AppendLongNumber](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleStringBuilder.AppendLongNumberNode.html):
  Append a long number to a string builder.
* [AppendString](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleStringBuilder.AppendStringNode.html):
  Append a TruffleString to a string builder.
* [AppendSubstringByteIndex](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleStringBuilder.AppendSubstringByteIndexNode.html):
  Append a substring, defined by a byte-based offset and length, to a string builder.
* [AppendJavaStringUTF16](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleStringBuilder.AppendJavaStringUTF16Node.html):
  Append a Java String substring, defined by a char-based offset and length, to a string builder.
* [ToString](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleStringBuilder.ToStringNode.html):
  Create a new TruffleString from a string builder.

Example:

```java
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

abstract static class SomeNode extends Node {

    @Specialization
    static TruffleString someSpecialization(
            @Cached TruffleStringBuilder.AppendCharUTF16Node appendCharNode,
            @Cached TruffleStringBuilder.AppendJavaStringUTF16Node appendJavaStringNode,
            @Cached TruffleStringBuilder.AppendIntNumberNode appendIntNumberNode,
            @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
            @Cached TruffleString.FromCharArrayUTF16Node fromCharArrayUTF16Node,
            @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
            @Cached TruffleStringBuilder.ToStringNode toStringNode) {
        TruffleStringBuilder sb = TruffleStringBuilder.create(TruffleString.Encoding.UTF_16);
        sb = appendCharNode.execute(sb, 'a');
        sb = appendJavaStringNode.execute(sb, "abc", /* fromIndex: */ 1, /* length: */ 2);
        sb = appendIntNumberNode.execute(sb, 123);
        TruffleString string = fromCharArrayUTF16Node.execute(new char[]{'x', 'y'}, /* fromIndex: */ 0, /* length: */ 2);
        sb = appendStringNode.execute(sb, string);
        sb = appendCodePointNode.execute(sb, 'z');
        return toStringNode.execute(sb); // string content: "abc123xyz"
    }
}
```

### Encodings

Every TruffleString string is encoded in a specific internal encoding, which is set during instantiation.

TruffleString is fully optimized for the following encodings:

* `UTF-8`
* `UTF-16`
* `UTF-32`
* `US-ASCII`
* `ISO-8859-1`
* `BYTES`

Many other encodings are supported, but not fully optimized. To use them, they must be enabled by
setting `needsAllEncodings = true` in
the [Truffle language registration](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Registration.html)
.

A TruffleString's internal encoding is not exposed. Instead of querying a string's encoding, languages should pass
an `expectedEncoding` parameter to all methods where the string's encoding matters (which is almost all operations).
This allows re-using string objects when converting between encodings, if a string is byte-equivalent in both encodings.
A string can be converted to a different encoding using `SwitchEncodingNode`, as shown in the following example:

```java
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

abstract static class SomeNode extends Node {

    @Specialization
    static void someSpecialization(
            @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
            @Cached TruffleString.ReadByteNode readByteNode,
            @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
            @Cached TruffleString.ReadByteNode utf8ReadByteNode) {

        // instantiate a new UTF-16 string
        TruffleString utf16String = fromJavaStringNode.execute("foo", TruffleString.Encoding.UTF_16);

        // read a byte with expectedEncoding = UTF-16.
        // if the string is not byte-compatible with UTF-16, this method will throw an IllegalArgumentException
        System.out.printf("%x%n", readByteNode.execute(utf16String, /* byteIndex */ 0, TruffleString.Encoding.UTF_16));

        // convert to UTF-8.
        // note that utf8String may be reference-equal to utf16String!
        TruffleString utf8String = switchEncodingNode.execute(utf16String, TruffleString.Encoding.UTF_8);

        // read a byte with expectedEncoding = UTF-8
        // if the string is not byte-compatible with UTF-8, this method will throw an IllegalArgumentException
        System.out.printf("%x%n", utf8ReadByteNode.execute(utf8String, /* byteIndex */ 0, TruffleString.Encoding.UTF_8));
    }
}
```

Byte-equivalency between encodings is determined *with* string compaction on UTF-16 and UTF-32, so e.g. a compacted
UTF-16 string is byte-equivalent to ISO-8859-1, and if all of its characters are in the ASCII range (see `CodeRange`),
it is also byte-equivalent to UTF-8.

To check if your code is switching encodings properly, run your unit tests with the system
property `truffle.strings.debug-strict-encoding-checks=true`. This disables re-using string objects when switching
encodings, and makes encoding checks more strict: all operations working on a single string will enforce an exact match,
whereas operations working on two strings will still allow byte-equivalent re-interpretations.

All TruffleString operations with more than one string parameter require the strings to be in an encoding compatible
with the result encoding. So either the strings need to be in the same encoding, or the caller must ensure that both
strings are
[compatible](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/AbstractTruffleString.html#isCompatibleTo-com.oracle.truffle.api.strings.TruffleString.Encoding-)
with the resulting encoding. This enable callers which already know the SwitchEncodingNodes would be noops to just skip
them for footprint reasons.

```java
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

abstract static class SomeNode extends Node {

    @Specialization
    static boolean someSpecialization(
            TruffleString a,
            TruffleString b,
            @Cached TruffleString.SwitchEncodingNode switchEncodingNodeA,
            @Cached TruffleString.SwitchEncodingNode switchEncodingNodeB,
            @Cached TruffleString.EqualNode equalNode) {
        TruffleString utf8A = switchEncodingNodeA.execute(a, TruffleString.Encoding.UTF_8);
        TruffleString utf8B = switchEncodingNodeB.execute(b, TruffleString.Encoding.UTF_8);
        return equalNode.execute(utf8A, utf8B, TruffleString.Encoding.UTF_8);
    }
}
```

### String Properties

TruffleString strings expose the following properties:

* `byteLength`: The string's length in bytes, exposed via the `byteLength` method.
* `codePointLength`: The string's length in codepoints, exposed via `CodePointLengthNode`.
* `isValid`: Can be queried via `IsValidNode` to check whether the string is encoded correctly.
* `codeRange`: Provides coarse information about the string's content, exposed via `GetCodeRangeNode`. This property can
  have the following values:
    * `ASCII`: All codepoints in this string are part of the Basic Latin Unicode block, also known as ASCII (0x00 -
      0x7f).
    * `LATIN-1`: All codepoints in this string are part of the ISO-8859-1 character set (0x00 - 0xff), which is
      equivalent to the union of the Basic Latin and the Latin-1 Supplement Unicode block. At least one codepoint in the
      string is greater than 0x7f. Only applicable to ISO-8859-1, UTF-16 and UTF-32.
    * `BMP`: All codepoints in this string are part of the Unicode Basic Multilingual Plane (BMP) (0x0000 - 0xffff). At
      least one codepoint in the string is greater than 0xff. Only applicable to UTF-16 and UTF-32.
    * `VALID`: This string is encoded correctly, and contains at least one codepoint outside the other applicable code
      ranges (e.g. for UTF-8, this means there is one codepoint outside the ASCII range, and for UTF-16 this means that
      there is one codepoint outside the BMP range).
    * `BROKEN`: This string is not encoded correctly. No further information about its contents can be determined.
* `hashCode`: The string's hash code, exposed via `HashCodeNode`. The hash code is dependent on the string's encoding;
  strings must always be converted to a common encoding before comparing their hash codes!

Example: Querying all properties exposed by TruffleString:

```java
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.strings.TruffleString;

abstract static class SomeNode extends Node {

    @Specialization
    static TruffleString someSpecialization(
            TruffleString string,
            @Cached TruffleString.CodePointLengthNode codePointLengthNode,
            @Cached TruffleString.IsValidNode isValidNode,
            @Cached TruffleString.GetCodeRangeNode getCodeRangeNode,
            @Cached TruffleString.HashCodeNode hashCodeNode) {
        System.out.println("byte length: " + string.byteLength(TruffleString.Encoding.UTF_8));
        System.out.println("codepoint length: " + codePointLengthNode.execute(string, TruffleString.Encoding.UTF_8));
        System.out.println("is valid: " + isValidNode.execute(string));
        System.out.println("code range: " + getCodeRangeNode.execute(string));
        System.out.println("hash code: " + hashCodeNode.execute(string, TruffleString.Encoding.UTF_8));
    }
}
```

### String equality and comparison

TruffleString objects should be checked for equality using `EqualNode`. Just like `HashCodeNode`, the equality
comparison is sensitive to the string's encoding, so before any comparison, strings should always be converted to a
common encoding. `Object#equals(Object)` behaves analogous to `EqualNode`, but since this method does not have
an `expectedEncoding` parameter, it will determine the string's common encoding automatically: If the string's encodings
are not equal, TruffleString will check whether one string is binary-compatible to the other string's encoding, and if
so, match their content. Otherwise, the strings are deemed not equal, no automatic conversion is applied!

Note that since TruffleString's `hashCode` and `equals` methods are sensitive to string encoding, TruffleString objects
must always be converted to a common encoding before e.g. using them as keys in a `HashMap`.

TruffleString also provides three comparison nodes `CompareBytesNode`, `CompareCharsUTF16Node`,
and `CompareIntsUTF32Node`, to compare strings respectively byte-by-byte, char-by-char, and int-by-int.

### Concatenation

Concatenation is done via `ConcatNode`. This operation requires both strings to be in `expectedEncoding`, which is also
the encoding of the resulting string. _Lazy concatenation_ is supported via the `lazy` parameter. When two strings are
concatenated lazily, the allocation and initialization of the new string's internal array is delayed until another
operation requires direct access to that array. Materialization of such "lazy concatenation strings" can be triggered
explicitly with a `MaterializeNode`. This is useful to do before accessing a string in a loop, such as in the following
example:

```java
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.strings.TruffleString;

abstract static class SomeNode extends Node {

    @Specialization
    static TruffleString someSpecialization(
            TruffleString utf8StringA,
            TruffleString utf8StringB,
            @Cached TruffleString.ConcatNode concatNode,
            @Cached TruffleString.MaterializeNode materializeNode,
            @Cached TruffleString.ReadByteNode readByteNode) {
        // lazy concatenation
        TruffleString lazyConcatenated = concatNode.execute(utf8StringA, utf8StringB, TruffleString.Encoding.UTF_8, /* lazy */ true);

        // explicit materialization
        TruffleString materialized = materializeNode.execute(lazyConcatenated, TruffleString.Encoding.UTF_8);

        int byteLength = materialized.byteLength(TruffleString.Encoding.UTF_8);
        for (int i = 0; i < byteLength; i++) {
            // string is guaranteed to be materialized here, so no slow materialization code can end up in this loop
            System.out.printf("%x%n", readByteNode.execute(materialized, i, TruffleString.Encoding.UTF_8));
        }
    }
}
```

### Substrings

Substrings can be created via `SubstringNode` and `SubstringByteIndexNode`, which use codepoint-based and byte-based
indices, respectively. Substrings can also be `lazy`, meaning that no new array is created for the resulting string, but
instead the parent string's array is re-used and just accessed with the offset and length passed to the substring node.
Currently, a lazy substring's internal array is never trimmed (i.e. replaced by a new array of the string's exact
length). Note that this behavior effectively creates a memory leak whenever a lazy substring is created! An extreme
example where this could be problematic: Given a string that is 100 megabyte in size, any lazy substring created from
this string will keep the 100 megabyte array alive, even when the original string is freed by the garbage collector. Use
lazy substrings with caution.

### Interoperability with java.lang.String

TruffleString provides `FromJavaStringNode` for converting a `java.lang.String` to TruffleString. To convert from
TruffleString to `java.lang.String`, use a `ToJavaStringNode`. This node will internally convert the string to UTF-16,
if necessary, and create a `java.lang.String` from that representation.

`Object#toString()` is implemented using the uncached version of `ToJavaStringNode` and should be avoided on fast paths.

```java
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.strings.TruffleString;

abstract static class SomeNode extends Node {

    @Specialization
    static void someSpecialization(
            @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
            @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
            @Cached TruffleString.ToJavaStringNode toJavaStringNode,
            @Cached TruffleString.ReadByteNode readByteNode) {
        TruffleString utf16String = fromJavaStringNode.execute("foo", TruffleString.Encoding.UTF_16);
        TruffleString utf8String = switchEncodingNode.execute(utf16String, TruffleString.Encoding.UTF_8);
        System.out.println(toJavaStringNode.execute(utf8String));
    }
}
```

TruffleString also exposes `#toStringDebug()` for debugging purposes. Do not use this method for anything other than
debugging, as its return value is unspecified and may change at any time.

### Differences to `java.lang.String`

The following items should be considered when switching from java.lang.String to TruffleString:

* The static overhead of TruffleString instances is larger than that of java.lang.String objects: A TruffleString object
  contains 2 pointers fields, 4 `int` fields and 4 `byte` fields, which will usually result in a total object size of 40
  bytes (object header of 12 bytes, 4 bytes per pointer with compressed oops, 8-byte memory alignment). A
  java.lang.String object contains one pointer field, one `int` field and one `byte` field, which in the same conditions
  results in a total object size of 24 bytes. This difference in memory footprint may negatively impact some cases where
  lots of small strings are generated.
* TruffleString does string compaction just like java.lang.String.
* If your language needs to convert strings to other encodings, e.g. UTF-8, which is very common in web applications,
  TruffleString can turn this operation into a no-op if the string doesn't contain special characters. For example,
  ASCII-only strings can be re-interpreted as almost any encoding, and converting an ASCII-only UTF-16 string to UTF-8
  is a no-op!
  In cases where transcoding a string is unavoidable, TruffleStrings will cache the transcoded string in the original
  string, so transcoding is only done once per string and encoding.
* In order to use 3rd party libraries, TruffleString object will have to be converted to java.lang.String and back. In
  order to make this as cheap as possible, TruffleString re-uses Java string's internal byte arrays when converting from
  java.lang.String to TruffleString, and caches Java strings created from TruffleString objects in the object itself.
* TruffleString offers additional features not present in java.lang.String:
    * Lazy concatenation and string views, which can significantly decrease the amount of array-copy operations your
      language may have to do.
    * String views into native memory, completely avoiding the need to copy native memory into java arrays before using
      it.
    * String content classification via the `codeRange` property, which allows specializations on strings that are
      ASCII-only et cetera. This can reduce the complexity of some string operations significantly.
* The performance of all TruffleString operations should be on par with or better than their
  java.lang.String-counterparts.

### Codepoint Iterators

TruffleString provides `TruffleStringIterator` as a means of iterating over a string's codepoints. This method should be
preferred over using `CodePointAtIndexNode` in a loop, especially on variable-width encodings such as UTF-8,
since `CodePointAtIndexNode` may have to re-calculate the byte index equivalent of the given codepoint index on every
call.

Example:

```java
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringIterator;

abstract static class SomeNode extends Node {

    @Specialization
    static void someSpecialization(
            TruffleString string,
            @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
            @Cached TruffleStringIterator.NextNode nextNode,
            @Cached TruffleString.CodePointLengthNode codePointLengthNode,
            @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {

        // iterating over a string's codepoints using TruffleStringIterator
        TruffleStringIterator iterator = createCodePointIteratorNode.execute(string, TruffleString.Encoding.UTF_8);
        while (iterator.hasNext()) {
            System.out.printf("%x%n", nextNode.execute(iterator));
        }

        // suboptimal variant: using CodePointAtIndexNode in a loop
        int codePointLength = codePointLengthNode.execute(string, TruffleString.Encoding.UTF_8);
        for (int i = 0; i < codePointLength; i++) {
            // performance problem: codePointAtIndexNode may have to calculate the byte index corresponding 
            // to codepoint index i for every loop iteration
            System.out.printf("%x%n", codePointAtIndexNode.execute(string, i, TruffleString.Encoding.UTF_8));
        }
    }
}
```

### Mutable Strings

TruffleString also provides a mutable string variant called `MutableTruffleString`, which is also accepted in all nodes
of `TruffleString`. `MutableTruffleString` is *not thread-safe* and allows overwriting bytes in its internal byte array
or native pointer via `WriteByteNode`. The internal array or native pointer's content may also be modified externally,
but the corresponding `MutableTruffleString` must be notified of this via `notifyExternalMutation()`.
`MutableTruffleString` is *not* a Truffle interop type, and must be converted to an immutable `TruffleString` via
`TruffleString.AsTruffleString` before passing a language boundary.
