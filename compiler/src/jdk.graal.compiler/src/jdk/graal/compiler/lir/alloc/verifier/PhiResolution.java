package jdk.graal.compiler.lir.alloc.verifier;

public enum PhiResolution {
    FromJump,
    FromUsage,
    FromPredecessors
}
