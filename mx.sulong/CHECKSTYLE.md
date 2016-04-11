Currently there are three different checkstyle files:
* com.oracle.truffle.llvm.test: the least restrictive which allows magic
 numbers and System.out.println/System.err.println
* com.oracle.truffle.llvm: allows System.out.println/System.err.println
 but no magic numbers
* com.oracle.truffle.llvm.nodes: the most restrictive which does not
 magic numbers, nor System.out.println/System.err.println
