<!-- PreviousNext:START -->
<table><tr>
  <td>Previous: <a href="lifecycle-of-a-gradle-build.md">Lifecycle of a Gradle Build</a></td>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="testing-and-running-your-plugins-locally.md">Testing and running your plugins</a></td>
</tr></table>
<!-- PreviousNext:END -->

# Avoiding Unnecessary Configuration

> Further reading: [Avoiding Unnecessary Task Configuration](https://docs.gradle.org/current/userguide/task_configuration_avoidance.html)

Every single Gradle build invocation must go through the [Configuration phase](lifecycle-of-a-gradle-build.md#configuration-phase). This is where the `build.gradle` scripts and plugins for project are evaluated and tasks are created and configured, ready for Gradle to calculate dependencies and then execute the tasks. Any expensive actions in this phase will be run _every single time the user runs Gradle_ - as such we should make sure to avoid doing unnecessary work.

The best way to avoid doing extra work is by making configuration of `Task`s/`Configuration`s/other domain objects as _lazy_ as possible. This means that Gradle will not actually configure these objects unless it needs to. For example, if there's a task `foo` that we've lazily configured which isn't actually going to be run in the build - Gradle will not run the lazy configuration or even make ("realise") the `Task` object. The time savings may be small per task, but considering some repos have 1000s or 10,000s of tasks, the savings can be significant.

## Lazy task registration

When creating a task, you should use `register` instead of `create`:

#### Bad:

```java
FooTask task = project.getTasks().create("foo", FooTask.class);
task.getInput().set(something);
```

Using `create` here immediately _realises_ the task object. We then always configure the task (setting the input) even when the task is not going to be run. Any other lazy configuration of the task (eg in other plugins or scripts using `tasks) Instead, we should use `register`:

#### Good:

```java
TaskProvider<FooTask> task = project.getTasks().register("foo", FooTask.class, foo -> {
    foo.getInput().set(something);
});
```

This is better; we tell Gradle there is task called `foo`, but we give the configuration of the task lazily. If `foo` is not executed, we don't even realise or configure the task.

## Accessing tasks lazily

> Further reading: [Task Configuration Avoidance: Eager APIs to avoid](https://docs.gradle.org/current/userguide/task_configuration_avoidance.html#eager_apis_to_avoid)

To access tasks lazily, you need to make sure to call lazy APIs that return `TaskProvider` rather than `Task`.

### Getting single tasks

#### Bad:

```java
project.getTasks().getByName("foo");
project.getTasks().findByName("foo");
project.getTasks().getByPath(":foo");
project.getTasks().findByPath(":foo");
project.getTasks().withType(Foo.class).getByName(":foo");
```

These all return a `Task` so will realise the task and run all the lazy configuration on it immediately.

#### Good:

```java
project.getTasks().named("foo")
```

### Iterating over tasks

#### Bad:

```java
project.getTasks().whenTaskAdded(task -> ...);
project.getTasks().whenObjectAdded(task -> ...);
```

These will immediately realise the tasks when they are added (even if added using `register`) and run all the lazy configuration on them.

```java
project.getTasks().all(task -> ...);
project.getTasks().matching(task -> ...);
project.getTasks().withType(Foo.class).matching(task -> ...);
```

This will realise all tasks, even ones already registered.

#### Good

```java
project.getTasks().configureEach(task -> ...)
```

`configureEach` will lazily configure each task that already exists as well as new ones that get added. It's a bit like running `.configure` on each task.

To move from `matching`, you can just use an `if` statement inside the `configureEach` rather than prefiltering the tasks.


### Using `TaskProvider`s

The `register` approach now gives us a `TaskProvider`, which is a lazy handle to the task. Many  of the built-in Gradle APIs will let you pass a `TaskProvider` instead of a `Task` object.

If you want to give use an output property of a task as the input to another task, you can just transform the `TaskProvider` into another `Provider` using `map` or `flatMap`:

```java
abstract class MyTask extends DefaultTask {
    @OutputFile
    abstract RegularFileProperty getOutputFile();
}
```

```java
TaskProvider<MyTask> myTask = project.getTasks().register("myTask", MyTask.class);

otherTaskProvider.configure(task -> {
    task.getInput().set(myTask.flatMap(MyTask::getOutputFile));
});
```

This also has the benefit of wiring up the dependencies between the two tasks automatically (no need for `dependsOn`).

## Configurations

`Configuration` objects should also be lazily registered. This follows the same pattern as tasks above. For example, you `register` rather than `create` a `Configuration`:

```java
Provider<Configuration> configuration = project.getConfigurations().register("myConfiguration", configuration -> {
    configuration.getDependencies().add(project.getDependencies().create("com.example:foo:1.0"));
});
```

If you need to use the name of the configuration elsewhere, you can `NamedDomainObjectProvider`:

```java
NamedDomainObjectProvider<Configuration> configuration = project.getConfigurations().register("myConfiguration", configuration -> ...});

// Later
String configurationName = configuration.getName();
```

All of the `getByName`/`all` etc methods [described above for Tasks](#accessing-tasks-lazily) also apply for `Configuration`s.

## Other types

The advice for `Task`s also applies to other Gradle domain objects like `SourceSetContainer`, ArtifactRepositoryContainer` - anything that inherits from [DomainObjectCollection](https://docs.gradle.org/current/javadoc/org/gradle/api/DomainObjectCollection.html#:~:text=All%20Known%20Subinterfaces%3A).

<!-- PreviousNext:START -->
<table><tr>
  <td>Previous: <a href="lifecycle-of-a-gradle-build.md">Lifecycle of a Gradle Build</a></td>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="testing-and-running-your-plugins-locally.md">Testing and running your plugins</a></td>
</tr></table>
<!-- PreviousNext:END -->
