<!-- PreviousNext:START -->
<table><tr>
  <td>Previous: <a href="managed-types-and-properties.md">Managed Types and Properties</a></td>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="testing-and-running-your-plugins-locally.md">Testing and running your plugins</a></td>
</tr></table>
<!-- PreviousNext:END -->

# Avoiding Unnecessary Configuration

> Further reading: [Avoiding Unnecessary Task Configuration](https://docs.gradle.org/current/userguide/task_configuration_avoidance.html)

## Lifecycle of a Gradle Build

* Gradle wrapper is run
* Gradle daemon started or reused
* Daemon starts build invocation
* `settings.gradle` is evaluated. Configures which projects are loaded.
* Each project has its `build.gradle` scripts evaluated, one-by-one (single threaded![^1]). 
  * Scripts apply plugins, which run codes
  * Both scripts and code create Gradle domain objects like `Configuration`s, `SourceSet`s and most importantly `Task`s.
  * `Task`s have input and output properties. At configuration time, these tasks are created and have 
* After configuration time, we are left with a set of `Task`s which have their inputs and output configured (may be lazy).
* Task dependency graph calculation: Gradle works out which tasks need to run before which other ones, starting from the requested tasks
  * The `dependsOn` and `mustRunAfter` properties for each task are evaluated
  * Where task input properties have been linked up to task output properties, Gradle is able to keep track of these task dependencies _without_ evaluating the property (part of the reason this is so easy to mess up).
* Task execution: Gradle actually executes the tasks, one by one in dependency order.
  * The task's `onlyIf`s, and `enabled` properties are inspected to see if it should run 
  * The input and output properties are evaluated
  * Gradle decides whether the task is up-to-date or whether it needs executing.
  * Any pre-existing output directories or files are deleted.
  * The task actions on the task are run

[^1]: [Isolated projects](https://docs.gradle.org/current/userguide/isolated_projects.html) is an upcoming Gradle feature that will enable project configuration to run in parallel, provided a very strict series of are adhered to that limits projects reading other project's configuration.

<!-- PreviousNext:START -->
<table><tr>
  <td>Previous: <a href="managed-types-and-properties.md">Managed Types and Properties</a></td>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="testing-and-running-your-plugins-locally.md">Testing and running your plugins</a></td>
</tr></table>
<!-- PreviousNext:END -->
