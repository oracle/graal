---
layout: docs
toc_group: pgo
link_title: Merging Profiles from Multiple Sources
permalink: /reference-manual/native-image/pgo/merging-profiles
---

# Merging Profiles from Multiple Sources

The PGO infrastructure enables you to combine multiple profiles into a single one using the Native Image Configure tool.
Merging profiles implies that the resulting profile will contain the union of all types, methods, and profile entries from the provided profiles.


## Usage

Here is the command that merges two profiles, `profile_1.iprof` and `profile_2.iprof`, into the `output_profile.iprof`:

```commandline
native-image-configure merge-pgo-profiles --input-file=profile_1.iprof --input-file=profile_2.iprof --output-file=output_profile.iprof
```

There is also a way to specify a directory as a source of profiles with `--input-dir=<path>`.
This will only search for profiles in the given directory, excluding subdirectories.

```commandline
native-image-configure merge-pgo-profiles --input-dir=my_profiles/ --output-file=output_profile.iprof
```
