# Espresso Continuations

The continuations feature of the Espresso VM allows you to control the program stack. When a continuation is _paused_,
the stack is unwound and copied onto the heap as ordinary Java objects. When a continuation is _resumed_ those objects
are put back onto the stack, along with all the needed metadata to resume execution at the pause point. The heap objects
can potentially be serialized to resume execution in a different JVM running the same code (e.g. after a restart).

## Usage

See the JavaDoc of `com.oracle.truffle.espresso.continuations.Continuation`.

## Internal implementation notes

*This section is only relevant for people working on Espresso itself.*

Continuations interact with the VM via private intrinsics registered on the `Continuation` class. 

A continuation starts by calling into the VM. Execution resurfaces in the guest world at the private `run` method of
`Continuation`, which then invokes the user's given entry point.

Pausing throws a host-side exception that is caught by the `BytecodeNode` interpreter loop. The stack frame details are
copied into a new guest-side object called a `HostFrameRecord` (HFR), and the exception is then rethrown. The HFRs are
chained together in an intrusive linked list. Once execution reaches the private `run` method of `Continuation` the
head of the HFR list is put into a field of `Continuation` and control is returned to the guest.

Resuming requires rewinding the stack and is more complicated. We must pass the list of HFRs through a series of invokes
in the bytecode interpreter, starting from `Continuation.run`. The first challenge is how to pass the HFRs down the host
stack. A Truffle `CallTarget` (guest method) can only take an array of arguments when executed, so the head HFR is
wrapped in an internal type and passed as the only argument to an invoke of the private `run` method. In `BytecodeNode`
we notice the special case of a single argument of an internal type (not a primitive or `StaticObject`) and the HFR
contents unpacked. The data in it is copied back into the `VirtualFrame` and (host) local variables of the
bytecode interpreter. The *next* HFR in the list is then saved as a field of the node.

If we simply re-execute the `invoke*` bytecode that the frame's bytecode index (bci) is pointing at then we will have
a problem: the arguments on the guest stack were already popped by the time we unwound. If we try and re-execute the
invoke in the regular way the arguments won't be there. This is avoided by changing the implementation of the 
`invoke*` bytecode such that if we have a next HFR, the bytecode isn't quickened as normal. Instead the 
popping/clearing the stack is avoided and the arguments passed to the next call will actually just be the next HFR. 
This occurs recursively until the next HFR pointer is null, at which point the stack is fully unwound and execution
will continue as normal.

-----

export LLVM_JAVA_HOME=$PWD/downloads/labsjdk-ce-21.0.1-jvmci-23.1-b22-sulong/Contents/Home
alias MX="mx --env jvm-llvm"
export CLASSPATH=$HOME/Projects/hello-maestro/out/production/hello-maestro/
alias r="MX build && echo -e '\n\n------\n\n' && MX espresso --vm.ea --log.file=/tmp/truffle-log -ea Main"
alias d="MX build && echo -e '\n\n------\n\n' && MX espresso '--vm.agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:8000' --vm.ea --log.file=/tmp/truffle-log Main"
