# For Contributors

GraalVM welcomes contributors to the core platform and projects that extend that platform.
There have been significant contributions from both industry and academia so far and we thank you for considering to contribute your changes!

## AI-Assisted Contributions

GraalVM accepts AI-assisted contributions. See [CODING_ASSISTANTS.md](CODING_ASSISTANTS.md) for the project policy on coding assistants, including contributor responsibility, optional attribution, and the applicability of the [Oracle Contributor Agreement](https://oca.opensource.oracle.com/) to all contributions, whether AI-assisted or not.

## How to Contribute

- Learn [how to become a GraalVM contributor](https://www.graalvm.org/community/contributors/).
- See [CODING_ASSISTANTS.md](CODING_ASSISTANTS.md) for the policy on AI-assisted contributions.
- When contributing documentation or editing Markdown files, consult [docs/AGENTS.md](docs/AGENTS.md) for GraalVM documentation terminology, style, and Markdown conventions. Apply that guide to new or modified content only; do not refactor existing documentation in bulk just to match it.
- See [instructions for building GraalVM from source](vm/README.md).
  - Check individual README.md and CONTRIBUTING.md files in the subprojects to learn how to build and import them into your IDE (for example, [the compiler README.md](compiler/README.md).)
- Use [GitHub Discussions](https://github.com/oracle/graal/discussions) for questions related to working with the sources or extending the GraalVM ecosystem by creating new languages, tools, or embeddings.

## Reporting Bugs, Feature Requests, and Questions

- Open a GitHub issue for reproducible bugs and concrete feature requests.
- Include the affected GraalVM version, platform details, reproduction steps, and a minimal example whenever possible.
- Use [GitHub Discussions](https://github.com/oracle/graal/discussions) or the [GraalVM Slack](https://www.graalvm.org/slack-invitation/) for questions, design discussion, or requests for guidance that are not yet actionable bug reports or feature requests.
- Do not report security vulnerabilities in public issue trackers. Follow the instructions in [SECURITY.md](SECURITY.md).

## Pull Requests

Pull requests should make it easy for reviewers to understand what changed, why it changed, and how it was validated. In particular, contributors are expected to:

- summarize the change and its motivation clearly
- link the relevant issue or other prior discussion when there is one
- describe the testing performed, or explicitly state that no testing was run
- update documentation and tests when the change affects user-visible behavior, supported workflows, or developer expectations
- ensure they have the right to contribute all submitted material, including code, tests, documentation, screenshots, and other supporting content

The [Oracle Contributor Agreement](https://oca.opensource.oracle.com/) applies to all contributions accepted into the project. Use of coding assistants does not change that requirement.

### Contributor Roles

There are different roles for contributors of the project. Find a list of current contributors in [CENSUS.md](CENSUS.md).

* Committer
  * Has signed the [Oracle Contributor Agreement](https://oca.opensource.oracle.com/), which is a prerequisite for contributing to the project
  * At least one pull request authored by the individual has been merged

* Code Owner
  * Source code directories contain an OWNERS.toml metadata file to define one or more Code Owners.
  * If there is no such file, the property of the parent directory is inherited.
  * A change to the source code must be approved by at least one of the Code Owners.
  * Large modifications should be discussed with the Technical Area Lead for architectural design before a pull request is created.

* Technical Area Lead
  * Code Owner with primary responsibility for architecture and design of a specific area
  * Has the ability to veto changes in his area on technical grounds

* Security Lead
  * Organizes the vulnerability group for sharing security patches
  * Reviews pull requests for aspects relevant for security
  * Primary point of contact in case of discussing a potential security vulnerability

* Developer Advocacy Lead
  * Manages interactions within the GraalVM Community
  * Runs the Advisory Board
  * Primary point of contact for enquiries related to the GraalVM community or community support

* Project Lead
  * Defines an overall technical direction
  * Mediates disputes over code ownership
  * Appoints the Security Lead, Developer Advocacy Lead, and Technical Area Leads
  * Appointed by Oracle

### Backports

For versions of GraalVM with ongoing community maintenance and backport support, a Lead Maintainer is chosen among the project contributors. That lead maintainer is responsible for managing the corresponding community backport repository. See [here](https://github.com/oracle/graal/issues/8935) an example of a call for a lead maintainer.

### Advisory Board

Apart from source code contributors to the project, there is also a role for advisory board members that discuss project matters and direction as well as drive awareness and adoption of GraalVM technology. Find a description of the advisory board and its current members [here](https://www.graalvm.org/community/advisory-board/).

### Security

Entities interested in sharing reports of vulnerabilities and collaborate on security fixes should contact the Security Lead.
