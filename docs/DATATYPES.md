# Datatypes

We map LLVM IR's data types to Java primitive and composite types. This
document describes how we map them, and also which Sulong Truffle nodes
produce values of these types.

## Void

We implement statements and the void data type by instantiating LLVMNode,
which has a void return type.

| LLVM data type | Java data type | Node class | Completeness |
|----------------|----------------|------------|--------------|
| void           | void           | LLVMNode   | complete     |

## Integer

We map the most common LLVM IR integer data types (i1, i8, 16, i32, and i64)
directly to Java primitive data types of the same bit length. For other
bit lengths (e.g., i33) we use the composite data type LLVMIVarBit.
Support for LLVMIVarBit is not yet complete.

| LLVM data type | Java data type | Node class      | Completeness |
|----------------|----------------|-----------------|--------------|
| i1             | boolean        | LLVMI1Node      | complete     |
| i8             | byte           | LLVMI8Node      | complete     |
| i16            | short          | LLVMI16Node     | complete     |
| i32            | int            | LLVMI32Node     | complete     |
| i64            | long           | LLVMI64Node     | complete     |
| ...            | LLVMIVarBit    | LLVMIVarBitNode | incomplete   |

## Floating Point

We support float and double as the most common data types on AMD64.
Since GCC and Clang compile C's long double to 80 bit floats on this
platform, we also provide basic support for the x86_fp80 data type by
emulating it with Java primitives.

| LLVM data type | Java data type | Node class         | Completeness |
|----------------|----------------|--------------------|--------------|
| half           |                |                    | unsupported  |
| float          | float          | LLVMFloatNode      | complete     |
| double         | double         | LLVMDoubleNode     | complete     |
| fp128          |                |                    | unsupported  |
| x86_fp80       | LLVM80BitFloat | LLVM80BitFloatNode | incomplete   |
| ppc_fp128      |                |                    | unsupported  |

## Pointer

We use LLVMAddress, which is a wrapper class for a machine pointer
(contained in a long), to support pointers.

| LLVM data type | Java data type | Node class      | Completeness |
|----------------|----------------|-----------------|--------------|
| *              | LLVMAddress    | LLVMAddressNode | complete     |

## Function

| LLVM data type | Java data type         | Node class       | Completeness |
|----------------|------------------------|------------------|--------------|
| function       | LLVMFunctionDescriptor | LLVMFunctionNode | complete     |

## Vector

| LLVM data type | Java data type | Node class            | Completeness |
|----------------|----------------|-----------------------|--------------|
| vector types   | LLVMVector     | LLVMVectorLiteralNode | incomplete   |
