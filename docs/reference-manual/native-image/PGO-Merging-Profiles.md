---
layout: docs
toc_group: pgo
link_title: Merging Profiles from Multiple Sources
permalink: /reference-manual/native-image/optimizations-and-performance/PGO/merging-profiles/
---

# Merging Profiles from Multiple Sources

The PGO infrastructure enables you to combine multiple profiles into a single one using the [Native Image Configure Tool](AutomaticMetadataCollection.md#native-image-configure-tool).
Merging profiles implies that the resulting profile will contain the union of all types, methods, and profile entries from the provided profiles.

## Usage

To merge two profiles, _profile_1.iprof_ and _profile_2.iprof_, into a single file named _output_profile.iprof_, use the following command:
```bash
native-image-configure merge-pgo-profiles --input-file=profile_1.iprof --input-file=profile_2.iprof --output-file=output_profile.iprof
```

There is also a way to specify a directory as a source of profiles using the  `--input-dir=<path>` option.
Then it only searches for profiles in the given directory, excluding subdirectories.
```bash
native-image-configure merge-pgo-profiles --input-dir=my_profiles/ --output-file=output_profile.iprof
```

### Further Reading

* [Basic Usage of Profile-Guided Optimization](PGO-Basic-Usage.md)
* [Native Image Configure Tool](AutomaticMetadataCollection.md#native-image-configure-tool)