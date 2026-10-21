# Contributing

Thanks for wanting to help with Tokn. Bug reports, ideas and pull requests
are all welcome. This is a free-time project, so a few ground rules keep it
manageable.

## Before you open a pull request

- For anything bigger than a small fix, open an issue first so we can agree
  on the approach before you put time into it.
- Keep a PR to one change. Unrelated cleanups go in a separate PR.
- Make sure `./gradlew assembleDebug` and the unit tests pass. If you fix a
  bug, a test that would have caught it is appreciated.
- Explain in the description what was wrong, how it showed up, and why your
  change fixes it.
- Security issues go through private reporting, not a public PR. See
  [SECURITY.md](SECURITY.md).
- Translations happen on [Crowdin](https://crowdin.com/project/tokn). Do not
  edit `values-<lang>/strings.xml` by hand, only `values/strings.xml` for
  new English strings.

## AI tools and automated findings

Using AI coding assistants is fine, I use them too (see the README). What
matters is that you understand the change, have checked it against the code
around it, and can answer questions about it yourself. You are the author,
not the tool.

The same goes for findings from linters, scanners and other analysis
tools. A warning on its own is not a bug. If you send a fix for one, show
what actually goes wrong in Tokn, and check that the warning applies to how
the code is used here. Changes that only exist to make a tool happy, without
a real effect, will most likely be closed.

Real findings are always welcome, whatever tool surfaced them.
