# Runtime module system support

The core of the runtime module system support consists of a set of substitutions and a feature (`com.oracle.svm.hosted.ModuleLayerFeature`) that provides runtime module-graphs.

The purpose of the runtime module support:
- Correct information when querying module instances
- Correct runtime module-aware access checks
- Correct runtime module graphs
- Dynamic module system – defining `Module` and `ModuleLayer` instances at runtime


## Substitutions

- `com.oracle.svm.core.jdk.Target_java_lang_Module`:
    - `addReads0()`, `addExports0()`, etc - module graph modifications
    - `defineModule0()` - crucial for dynamic module support
- `com.oracle.svm.core.jdk.Target_java_lang_ModuleLayer`:
    - `boot()` - replaces the hosted boot module layer with our own

Some data structures also need to be substituted/resetted as to not pull in hosted modules (see runtime module synthesizing):
- `com.oracle.svm.core.jdk.Target_java_lang_Module_ReflectionData`


## ModuleLayerFeature

Synthesizes the runtime boot module layer and all reachable module layers initialized at image-build time. It also replicates build-time module relations at runtime (i.e., reads, opens and exports).

Why don't we reuse build-time modules/layers?
- Module graphs are not the same – even if they were, we would like to optimize and not include modules that we do not need (as long as it does not break compatibility)
- Hosted module instances capture state (e.g., class loader), and patching them and all relevant data structures is harder than synthesizing new instances

Synthesizing runtime module layers and modules (`com.oracle.svm.hosted.ModuleLayerFeature#afterAnalysis`):
- Calculate runtime root module set:
- Find all reachable named modules
- Collect all extra modules (addmods, resources)
- Find all synthetic modules
- Find all reachable runtime module layers and synthesize them in parent-to-child order using the root module set for the boot module layer
- Replicate module graph modifications
- Replicate native access information


### Runtime boot module layer

Calculating root modules – done in the same way as in ` jdk.internal.module.ModuleBootstrap#boot2`, for compatibility.

Substitution for `ModuleLayer#boot2` returns the synthesized boot module layer from the ` com.oracle.svm.core.jdk.RuntimeModuleSupport`

An object replacer makes sure that hosted module instances are replaced with their runtime counterparts.


## Tests
- `mx hellomodule` - smoke test
- `vm-enterprise/tests/native-image/module-graph` - test compatibility and correctness of the runtime module graph
