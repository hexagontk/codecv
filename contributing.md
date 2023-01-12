
# Contributing
You can contribute code or documentation to the project. If you only intend to use the project, you
can also discuss your issues here.

## Make a Question
You can use the repository's Discussions tab to ask questions or resolve problems.

## Report a Bug
File an issue which comply with the [bug template] requirements.

[bug template]: https://github.com/hexagonkt/.github/blob/master/.github/ISSUE_TEMPLATE/bug.md

## Request a Feature
Create a new issue following the [enhancement template] rules.

[enhancement template]: https://github.com/hexagonkt/.github/blob/master/.github/ISSUE_TEMPLATE/enhancement.md

## Contribute an Improvement
1. You can check available tasks in the repository issues tab. Issues with the `help wanted` tag in
   the `Ready` column are recommended for a first time contribution.
2. Claim an issue you want to work in with a comment, after that I can assign it to you and move it
   to the `Working` column. If you want to contribute to a non tagged (or a not existing) tasks:
   write a comment, and we'll discuss the scope of the feature.
3. New features should be discussed within a post in the GitHub Ideas Discussions tab of the project
   before actual coding. You may do a PR directly, but you take the risk of it being not suitable
   and discarded.
4. For code, file names, tags and branches use either camel case or snake case only. I.e.: avoid `-`
   or `.` in file names if it is possible.
5. For a Pull Request to be accepted, follow the [pull request template] recommendations. Check the
   code follows the [Kotlin Coding Conventions], except final brace position in `else`, `catch` and
   `finally` (in its own line). If you use [IntelliJ] and [Editor Config] this will be checked for
   you.
6. Packages must have the same folder structure as in Java (to avoid problems with tools and Java
   module definition).
7. Follow the commit rules defined at the [commit guidelines].

[pull request template]: https://github.com/hexagonkt/.github/blob/master/pull_request_template.md
[IntelliJ]: https://www.jetbrains.com/idea
[Editor Config]: https://editorconfig.org
[Kotlin Coding Conventions]: https://kotlinlang.org/docs/reference/coding-conventions.html
[commit guidelines]: https://github.com/hexagonkt/.github/blob/master/commits.md

It is recommended that you create a Git pre-push script to check the code before pushing it. As
this command will be executed before pushing code to the repository (saving you time fixing
[GitHub Actions] build errors).

[GitHub Actions]: https://github.com/features/actions
