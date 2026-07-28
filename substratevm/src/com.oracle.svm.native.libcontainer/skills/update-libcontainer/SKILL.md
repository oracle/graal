---
name: update-libcontainer
description: Reimport Native Image's vendored libcontainer from a newer JDK while preserving local adaptations in a reviewable commit stack. Use when performing a full libcontainer update, replaying Native Image changes onto new upstream sources, resolving reimport conflicts, or reviewing such an update.
---

# Update libcontainer

## Read the Canonical Instructions

From the repository root, read _substratevm/src/com.oracle.svm.native.libcontainer/README.md_ completely before changing files.
Follow its full-reimport, provenance, namespace, and testing instructions.
Use this skill only for the additional history and conflict-resolution rules below.

## Establish the Previous Full-Import Baseline

Do not treat the current `@BasedOnJDKFile` annotations as authoritative import revisions.
Annotations may have been advanced without a full reimport to keep change-detection gates passing while the actual import was deferred.

Before changing the vendored sources:

1. List all current annotation revisions and identify the lowest revision in upstream commit chronology. Use it only as a candidate for the previous full-import revision; do not choose it by lexical tag ordering.
2. Inspect the Git history of the imported libcontainer files and find the most recent full-import stack. Distinguish a broad full import from annotation-only updates and focused adoptions of individual upstream changes.
3. Determine the JDK revision used by that full import. If it matches the lowest annotation candidate, treat the match as strong evidence. If it does not, stop and investigate older history rather than guessing.
4. Check out that exact old JDK revision from the same upstream repository that will supply the new revision.
5. In a temporary clean Graal worktree at the pre-update commit, remove the namespace with `mx svm_libcontainer_namespace remove` and save the resulting old adapted source tree.
6. In that temporary worktree, run `mx reimport-libcontainer-files --jdk-repo <old-jdk-checkout>` using the verified candidate revision.
7. Compare the reimported old JDK files with the saved namespace-free adapted tree. The differences must be limited to recognizable Native Image adaptations such as `NATIVE_IMAGE` guards, minimal local replacements, reduced dependencies, and required header or license normalization.

If the verification diff contains unrelated upstream implementation changes, the candidate is not a valid merge base.
Repeat the history investigation with an earlier candidate; do not begin the update until reimporting the old revision reproduces the vendored tree modulo Native Image adaptations.

Record the verified old full-import revision, its upstream repository URL, the supporting full-import commit, and the saved pristine old source tree.
Use this verified pristine tree as the merge base for all imported files.

## Preserve a Reviewable History

Record the upstream repository URL and the verified old and new JDK revisions before starting.
Do not mix Oracle JDK, OpenJDK, or labs-openjdk sources unless their equivalence for the imported files has been verified and documented.
Keep each mechanical transformation separate from judgment-heavy changes, in this order:

1. Remove the libcontainer namespace, commit only that transformation, and record the commit as the previous adapted source commit.
2. Reimport the new JDK files and commit the unadapted upstream snapshot.
3. Replay the previous Native Image adaptations with a three-way merge and commit the result, including conflict markers.
4. Resolve every conflict in a separate, immediately following commit.
5. Review newly imported code for Native Image reachability and commit any exclusions as a focused semantic change.
6. Materialize the previous effective Native Image sources in a generated review-only commit.
7. Replace them with the new effective Native Image sources in the primary semantic review commit.
8. Restore the canonical guarded sources in a generated review-only commit.
9. Restore the namespace and commit only that transformation.
10. Update `@BasedOnJDKFile` provenance in a separate commit.
11. Put build integration or other follow-up changes in focused commits after the reimport stack.

Do not squash the marker-bearing commit into its resolution.
The intermediate commit is deliberately not buildable; it records the exact overlap between the old adaptations and the new upstream sources for review.
The resolution commit must be adjacent so no final branch state retains unresolved markers.

## Replay Adaptations with Diff3

For each imported file, treat these versions as the three-way merge inputs:

- ours: the old Native Image-adapted file after namespace removal
- base: the matching file from the verified previous full-import JDK tree
- theirs: the file from the new JDK revision

Use diff3-style markers and descriptive labels, for example:

```shell
git merge-file -p --diff3 \
  -L "Native Image adaptations" \
  -L "$old_jdk_revision" \
  -L "$new_jdk_revision" \
  "$old_adapted_file" "$old_jdk_file" "$new_jdk_file" > "$vendored_file"
```

Apply the resulting files on top of the unadapted reimport commit.
Commit both cleanly merged adaptations and marker-bearing files together before resolving conflicts.

Inspect the replay as two separate diffs:

- old adapted tree to replayed tree: must retain the relevant upstream update
- unadapted new reimport to replayed tree: must contain only Native Image adaptations and explicit conflict resolutions

If the upstream reimport changed imported files but the replayed tree equals the old adapted tree, stop.
The merge base or replay procedure is wrong; do not continue with provenance updates, projections, or validation of that tree.

## Resolve Conflicts Deliberately

Review all three sides of every conflict.
Preserve relevant upstream changes and re-establish Native Image behavior, including `NATIVE_IMAGE` guards, local replacements, reduced dependencies, and deliberate deviations described in the canonical README.
Do not resolve conflicts mechanically by always choosing ours or theirs.

Handle copyright and license headers as semantic content.
Preserve applicable notices and updated years from upstream together with locally required license text.
Match the repository's established notice ordering, wording, punctuation, and complete rights-reservation suffixes by comparing neighboring vendored files and the previous adapted version.
Do not take an entire conflicted header from one side without checking what the other sides contribute.

After the resolution commit, verify that no merge markers remain:

```shell
rg -n '^(<<<<<<<|\|\|\|\|\|\|\||=======|>>>>>>>)' \
  substratevm/src/com.oracle.svm.native.libcontainer
```

## Verify Final Upstream Alignment

After restoring the final canonical guarded sources, remove the libcontainer namespace and compare every imported file with its counterpart from the target JDK revision.
Do this on canonical source files, not compiler-preprocessed output or the effective Native Image projection.

The final diff may contain only:

- `NATIVE_IMAGE` directives that wrap a complete target-JDK region without changing that region's upstream content;
- required normalized copyright or license-header differences; and
- explicitly documented Native Image adaptations that already existed in the verified previous adapted tree.

An added `NATIVE_IMAGE` guard may exclude an upstream declaration, definition, comment, include, or dependency closure from Native Image, but the upstream text inside the excluded or retained branch must still match the target JDK revision.
Do not retain an older upstream implementation inside a `#ifndef NATIVE_IMAGE` block, and do not omit a target-JDK implementation merely because the complete region is excluded from Native Image.

Treat every other deletion, replacement, or modification of target-JDK source as a blocker.
In particular, do not remove or revert a new upstream implementation merely because it was absent from the old Native Image tree; re-check the verified full-import baseline and replay procedure instead.
Record the reviewed diff, including every allowed exception, in the pull request description.

## Minimize the Native Image Surface

Treat newly imported top-level declarations and complete method definitions as excluded from Native Image by default.

After resolving reimport conflicts, review every newly added declaration, definition, field, constant, include, and code path.
Compile code for Native Image only when it has a concrete reachability path from an exported `svm_container_*` entry point.
Treat every exported C entry point as a root, even if it has no current Java caller, because it remains part of the libcontainer interface.

If a complete method, type, field, constant, helper, or include is not reachable from these roots, exclude it with `#ifndef NATIVE_IMAGE` around its complete declaration or definition.
Guard the complete dependency closure, including base-class virtual declarations, subclass overrides, complete method definitions, forwarding methods, constants, helper types, library includes, and diagnostic call sites as applicable.
Include immediately associated documentation comments in the same guard so the effective Native Image projection does not describe an excluded declaration or definition.

Never autonomously introduce a preprocessor conditional inside a function or method body.
Do not guard individual statements, branches, calls, or fragments of a required method, and do not preserve an old body as a `NATIVE_IMAGE` alternative to new upstream behavior.
Existing body-level Native Image adaptations may be replayed unchanged, but adding, moving, widening, or otherwise changing such a guard requires explicit user approval.

If a retained method calls a newly added helper that was excluded, restore the complete helper declaration and definition rather than guarding the call site.
If new upstream behavior inside a retained method appears unnecessary, retain it and ask the user before making any body-level deviation.
Preserve upstream fixes and behavior in all retained methods by default.

Do not use compiler optimization or archive extraction as proof that code is unreachable.
C++ virtual tables, externally visible functions, static initializers, and translation-unit granularity can retain otherwise unused code and its dependencies.
Establish reachability from the source call graph.

After adding guards, build all supported libcontainer variants and inspect `libsvm_container.a` for excluded definitions.
Link and run an image with container support, and inspect its native-library report to ensure unrelated libraries are not masking a dependency.

## Compare Native Dependencies

A static archive is a collection of unlinked object files and normally contains undefined symbols.
Raw `nm -u` output includes both external dependencies and references resolved by other members of the same archive; do not require it to be empty.

Build the old and new archives with the same compiler, libc, and flags.
For each supported variant, combine every archive member into one relocatable object so archive-internal references are resolved, then compare the remaining external symbols:

```shell
analysis_dir=$(mktemp -d)

ld -r --whole-archive <old-libsvm_container.a> --no-whole-archive \
  -o "$analysis_dir/old-libsvm_container.o"
ld -r --whole-archive <new-libsvm_container.a> --no-whole-archive \
  -o "$analysis_dir/new-libsvm_container.o"

nm -u -j -C "$analysis_dir/old-libsvm_container.o" | LC_ALL=C sort -u \
  > "$analysis_dir/old.undefined"
nm -u -j -C "$analysis_dir/new-libsvm_container.o" | LC_ALL=C sort -u \
  > "$analysis_dir/new.undefined"

diff -u "$analysis_dir/old.undefined" "$analysis_dir/new.undefined"
```

Treat every newly introduced external symbol as a review blocker.
Map it to the complete method or data definition that requires it and apply the whole-definition reachability rules above.
Do not add a native library autonomously merely to make the link pass.
If an allowed whole-definition exclusion cannot remove the dependency because it occurs in a retained method, preserve the upstream method body and obtain explicit user approval before adding the library or making a body-level deviation.

Reconcile the measured external symbols with every native dependency declaration and claim, including `@CLibrary` dependencies, automatic library registration, build configuration, and the canonical README.
Do not preserve a dependency merely because its declaration or documentation predates the update.
Remove stale dependency metadata in a focused follow-up commit when neither the combined-object symbol list nor another concrete retained behavior justifies it.
Confirm the Native Image native-library report agrees with the resulting declarations and does not list a library solely because of stale metadata.

Record the old and new external-symbol lists and the approved explanation for every dependency delta in the review guide.

## Generate the Effective Native Image Review

Keep the final vendored sources close to upstream; do not permanently delete guarded HotSpot code merely to simplify review.
Instead, use _scripts/project_native_image.py_ to create source-preserving projections that retain `NATIVE_IMAGE` branches and physically remove excluded branches and their directives.
The script preserves unrelated preprocessor conditions, includes, macros, comments, and formatting.
It intentionally supports only the direct `#ifdef NATIVE_IMAGE` and `#ifndef NATIVE_IMAGE` forms currently used by libcontainer, with optional `#else` and nested unrelated conditionals.
If it rejects a new form, inspect it and extend the script and tests rather than bypassing the failure.

Do not use ordinary compiler `-E` output for this review because include and macro expansion obscures the source diff.

During reachability review, materialize the previous adapted source tree and the conflict-resolved new source tree in temporary directories, then project both with the same script version.
Use the namespace-removal commit from step 1 as `<old-adapted-commit>`.
Use the conflict-resolution commit as `<new-source-commit>` while iterating.

```shell
native_sources=substratevm/src/com.oracle.svm.native.libcontainer/src
project_script=substratevm/src/com.oracle.svm.native.libcontainer/skills/update-libcontainer/scripts/project_native_image.py
review_root=$(mktemp -d)
mkdir "$review_root/old" "$review_root/new"

git archive <old-adapted-commit> "$native_sources" | tar -x -C "$review_root/old"
git archive <new-source-commit> "$native_sources" | tar -x -C "$review_root/new"

python3 "$project_script" "$review_root/old/$native_sources" "$review_root/old-projection"
python3 "$project_script" "$review_root/new/$native_sources" "$review_root/new-projection"
```

Compare the projections and map every effective addition back to the canonical guarded sources.
Add only permitted whole-declaration or whole-definition guards for unreachable code, regenerate the new projection, and repeat until its effective additions are all reachable from exported entry points.

After committing the reachability reduction, use that commit as both `<new-source-commit>` and `<new-canonical-commit>`, then regenerate both projections from clean trees.
Create these three consecutive commits before namespace restoration using exact tree replacement so file removals are included:

```shell
rsync -a --delete "$review_root/old-projection/" "$native_sources/"
git add "$native_sources"
git commit -m "Materialize previous Native Image libcontainer for review"

rsync -a --delete "$review_root/new-projection/" "$native_sources/"
git add "$native_sources"
git commit -m "Update effective Native Image libcontainer"

rsync -a --delete "$review_root/new/$native_sources/" "$native_sources/"
git add "$native_sources"
git commit -m "Restore canonical guarded libcontainer sources"
```

`Update effective Native Image libcontainer` is the primary behavioral review commit.

The baseline and restoration commits are generated review scaffolding and need not build.
Do not retain projected files beside the canonical files or use a projection as input to a later JDK import.
After the restoration commit, verify that the three commits have no net effect:

```shell
git diff --exit-code <new-canonical-commit> HEAD
```

## Document the Review Structure

Add a review guide to the pull request description.
Identify the old and new JDK revisions, then list each commit by its current commit ID and subject.
Update the IDs after rebasing or rewriting the stack.

Classify these commits as mechanical or generated:

- namespace removal
- unadapted upstream reimport
- three-way adaptation replay, including the intentionally checked-in conflict markers
- previous effective Native Image source materialization
- canonical guarded source restoration
- namespace restoration
- `@BasedOnJDKFile` provenance updates

State that the marker-bearing commit is intentionally non-buildable and exists to expose the merge inputs.
Reviewers normally do not need to review these commits semantically, but they can verify that the transformations and merge inputs are correct.

Call out these commits for focused semantic review:

- the conflict-resolution commit, including header resolutions
- the Native Image reachability reduction
- the effective Native Image update
- any functional, build-integration, or other non-mechanical follow-up commits

Direct reviewers to inspect the resolution commit against its marker-bearing parent for merge decisions, the reachability commit for guard boundaries, and the effective Native Image update for behavioral changes.

## Validate

Run the checks documented in the canonical README.
From _substratevm_, verify the annotation and namespace invariants together:

```shell
mx gate --tags check_libcontainer_annotations,check_libcontainer_namespace
```

Also run a normal incremental `mx build` before follow-up `mx` commands, `mx checkstyle`, relevant container tests, and the repository's applicable header and license checks.
If the update changes JDK inputs maintained in a paired repository, keep those checksum changes in their own commit and verify them with that repository's checksum command.

Run the projection tests whenever the projection script or any `NATIVE_IMAGE` conditional form changes:

```shell
python3 -m unittest discover \
  -s substratevm/src/com.oracle.svm.native.libcontainer/skills/update-libcontainer/tests \
  -v
```
