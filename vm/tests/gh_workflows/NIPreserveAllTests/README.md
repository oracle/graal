# Native Image Preserve All Tests

The Native Image Preserve All tests represent a way of automatically testing the Native Image Preserve All feature with the newest JDKs and the most popular Maven libraries, on a weekly schedule. The tests are run as a weekly GitHub action, defined in [ni-preserve-all.yml](/.github/workflows/ni-layers.yml). 

These tests work by running layer-building jobs for each library in [popular-maven-libraries.json](popular-maven-libraries.json), which is automatically generated (Vojin Jovanovic) and updated periodically. The libraries that fail to build are added to [excluded-popular-maven-libraries.json](excluded-popular-maven-libraries.json) and are excluded from future buildings until their issues are fixed.