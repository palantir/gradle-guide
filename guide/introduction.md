<!-- PreviousNext:START -->
<table><tr>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="background-what-is-a-gradle-file.md">Background: What is a Gradle file?</a></td>
</tr></table>
<!-- PreviousNext:END -->
# Introduction

## What is this guide?

This guide is meant to help you write good Gradle plugins. It is not meant to be a comprehensive guide to Gradle, but rather a guide to the Gradle-specific idioms and patterns that we use at Palantir.


## In the lair of the complexity demon

Gradle is complicated. Of all my years programming, it's probably the most complicated tool I've had to look after. I think it's good to understand what makes Gradle complicated and how we can manage the complexity.

At its heart, a Gradle build is a set of mutable domain object types. These contain the information for dependencies, projects, tasks, metadata, remote repositories and a great deal more. A Gradle build likely involves 10s of plugins as well as the Gradle script files in the repo, and each of these modify this mutable state at different times. Shared mutable state is the root of all evil and the cause of most Gradle bugs and issues.

If you have ever done web development before the introduction of frameworks like React, the problem is broadly the same. You have a bunch of mutable state (the DOM) and code (javascript) that changes it at different times. Without being very disciplined about how you write this code, this can easily lead to bugs.

As in the web case, we ideally fix this by building such a framework, so it is near impossible (or at least much harder) to make these mistakes. In this guide we will walk through how to write Gradle plugins to minimise the risk of these bugs. 


<!-- PreviousNext:START -->
<table><tr>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="background-what-is-a-gradle-file.md">Background: What is a Gradle file?</a></td>
</tr></table>
<!-- PreviousNext:END -->