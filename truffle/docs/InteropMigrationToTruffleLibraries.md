
# Interop with Truffle Libraries

The Truffle interop API consists 

With version TODO we introduced a new concept to Truffle called Truffle libraries [1]. Truffle libraries are now used for interoperability between languages. This means that all existing interop APIs are now deprecated and new APIs were provided. This document is intended as a migration guide for language and tool implementers.

[1] Link to TruffleLibraries.md

## Motivation

The current interop APIs are mature and well tested and already adopted by languages and tools. So why change it? Here is a list of problems that the current interop API suffers from which we intent to fix by migrating to Truffle Libraries. Some of these could be solved by evolving the current API,but it would require many small changes in order to evolve it in a compatible way.

### Call complexity
### Boxing
### Extensibility
### Manual cached dispatch
### Uncached dispatch
### Efficient exports / message resolution
### Error prone

## Usage Migration

### Overview


With the changes the 
It is recommended to read the documentation on Truffle Libraries first.

### Calling Interop Messages

### Exporting Interop Messages

## Interface Changes

### Booleans, Strings and Numbers instead of BOX/UNBOX

The probably biggest

### Members namespace instead of KEYS

### Array namespace instead of HAS_SIZE

### toNative

## Compatiblity

The old interop libraries remain compatible 

There are some incompatible changes that will be 


## Mapping

