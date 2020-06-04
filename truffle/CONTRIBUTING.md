# Contributing

Thanks for considering to contribute your changes to the Truffle project. We are grateful for
your support, as only your support makes Truffle the best platform for research in the area
of programming language implementation as well as an industry strength platform for
running polyglot programs at high performance.

Please fork our repository and experiment with it. There are many directions to explore from smaller changes like
bug fixes or improvements to Truffle's documentation to larger changes that either
speed execution up, improve the tooling, or give better control over the system.
Once you believe your contribution is valuable for others, create a pull request.
Please make sure to include a clear description of the intention of the change.

If you are adding, changing or removing API you will need to regenerate the signature snapshot:
```
$ mx build
$ mx sigtest --generate
```

# Reviews

The core Truffle repository is the production ready intersection of all the exploratory
work done in its forked copies. The primarily concern in this core repository is Truffle
APIs and their stability. Truffle is a framework used by many (often unknown) developers
around the globe. These developer rely on stability and robustness of the Truffle APIs
and implementation. To guarantee it we adhere to rules of
[API design][3] when making changes. We categorize the changes into three groups:
- trivial API change
- complex API change
- no API change

Each category requires different level of attention specified below. The process and its terminology
models the [review process][2] used by NetBeans. Its most important points are highlighted in this
document.

An API isn't just what is written in Javadoc, it is not just about the [method signatures][4].
The API includes properties, files, ports, protocols that are being read or written to.
It includes L10N, packages, versioning. It includes runtime behavior, threading,
memory management, etc. In short API is *everything somebody else can depend on*.

## Trivial API Change

It is very likely that by changing something in the Truffle repository, you are changing an API. If you
do so, it is important to stick with essential properties of a good API change:

- be backward compatible
- be well documented
- have sufficient test coverage
- be ready for future evolution

If your change satisfies these properties you are eligible for a **fast-track** review: create a pull request,
give everyone a chance to comment, react and adopt the request to received feedback. When the consensus is
reached assign to one of the reviewers to handle the final approval and integration.

Trivial change is usually about single method/class addition or other *little improvements* of existing APIs.
Additions of whole packages very likely fall into complex change category.

## Complex API Change

If an API change is inherently backward *incompatible*, if it is huge or if it requires a discussion,
it is subject to **standard** review. The purpose of such review is to seek wider consensus among involved parties. The ideal pull request intended to pass **standard** review should:

- include changed documentation to describe the intention, use-cases as envisioned at such early stage
- designate an assignee and other interested stakeholders
- organize a discussion
- summarize the outcome of the discussion in the pull request comment section

After this initial discussion the request's branch shall be filled with code addressing
the result of the discussion. When the code, tests, documentation is done there should be another,
*final review* where the reviewers decide whether to merge the change or not.

## No API Change

Adding tests verifying existing behavior, improving documentation to describe current behavior, refactoring
internal code without influencing functionality, mangling with private or package private members of API
classes and other *internal changes* do not need an API review at all.

It is still preferable to designate an assignee to review the intentions from a different
angle and merge the change. The assignee is responsible for adding the `accept` label to the
pull request once the review is completed and there are no required changes to be addressed.

Alternatively one can treat these "no API changes" as trivial API changes
and request **fast-track** review and give everyone a chance to participate.

# Applying the Change

To allow your pull request for Truffle to be accepted, you need to sign the [Oracle Contributor Agreement][1].
There is a white-list for contributors who have signed the OCA so please add a comment
to your first pull request indicating the name you used to sign the OCA if it isn't clear
from your github profile.

The Graal repository has a Travis gate set up to run on every pull request.
Please make sure to address all the issues in failing gates.

Once the review is successfully over, an `accept` label shall be attached to the pull request. 
At that point, it's up to someone from the truffle core team to shepherd the pull request.
The shepherding process includes ensuring that none of the public and internal tests are broken by the change and merging the change.
Commonly, the person to shepherd the PR should be the same person that reviewed and approved it.
If that person is unresponsive or unavailable to do so, feel free to cc @boris-spas to the PR.

[1]: http://www.oracle.com/technetwork/community/oca-486395.html
[2]: http://wiki.netbeans.org/APIReviews
[3]: http://wiki.apidesign.org/wiki/TheAPIBook
[4]: http://wiki.apidesign.org/wiki/TruffleSigtest
[5]: https://help.github.com/articles/assigning-issues-and-pull-requests-to-other-github-users/
