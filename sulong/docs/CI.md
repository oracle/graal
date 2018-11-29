# Continuous Integration (CI)

Before a commit to Sulong is merged, it first has to pass all our CI tests.
We have two checks in place:

1. The OCA check: In order to contribute to Sulong, [each contributor has
to sign and submit the Oracle Contributor Agreement (OCA)](CONTRIBUTING.md).
The OCA check confirms that a contributor has signed the OCA.

2. The Travis gate: We run a test gate in which we execute our test cases
and static analysis tools.

3. Internal tests: In addition to Travis CI we also use an additional, private,
test gate in which we run further tests on additional clang versions.
