# Contributing

To submit pull requests for Sulong, you need to sign the Oracle
Contributor Agreement. There is a white-list for contributors who have
signed the OCA so please add a comment to your first pull request
indicating the name you used to sign the OCA if it isn't clear from
your github profile.

Pull requests can only be merged by committers and a committer cannot
merge his or her own pull requests.

When creating a pull request, make sure you designate an assignee to
ensure the request is processed in a timely manner.

Before submitting a pull request please run `mx gate` to validate that
your changes do not break tests and conform with the coding conventions.

## Commit messages

- Each commit (message) should contain (and describe) one logical change.

### PR request

- You should only address one feature or change per PR request.
- You do not need to explain self-explanatory changes such as updates of
the Graal version.
- For other changes describe at least why (1) the change or feature is
needed, (2) how the change or feature is implemented, and optionally (3)
what further implications the change has. You can either use the PR
request description field or the commit message.

### Both PR request titles and commit messages

- Write the summary line and description of what you have done in the
imperative mode, that is as if you were commanding someone. Start the
line with "Fix", "Add", "Change" instead of "Fixed", "Added", "Changed".
- Don't end the summary line with a period - it's a title and titles
don't end with a period.
- Start each title and commit message with a capital letter.

## Links

- [https://github.com/erlang/otp/wiki/writing-good-commit-messages](https://github.com/erlang/otp/wiki/writing-good-commit-messages)
- [http://who-t.blogspot.co.at/2009/12/on-commit-messages.html](http://who-t.blogspot.co.at/2009/12/on-commit-messages.html)
- [http://chris.beams.io/posts/git-commit/](http://chris.beams.io/posts/git-commit/)
