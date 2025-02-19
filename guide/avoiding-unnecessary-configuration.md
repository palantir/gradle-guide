<!-- PreviousNext:START -->
<table><tr>
  <td>Previous: <a href="lifecycle-of-a-gradle-build.md">Lifecycle of a Gradle Build</a></td>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="testing-and-running-your-plugins-locally.md">Testing and running your plugins</a></td>
</tr></table>
<!-- PreviousNext:END -->

# Avoiding Unnecessary Configuration

> Further reading: [Avoiding Unnecessary Task Configuration](https://docs.gradle.org/current/userguide/task_configuration_avoidance.html)

Every single Gradle build invocation must go through the [Configuration phase](lifecycle-of-a-gradle-build.md#configuration-phase). This is where the `build.gradle` scripts and plugins for project are evaluated and tasks are registered and configured, ready for Gradle to calculate dependencies and then execute the tasks. Any expensive actions in this phase will be run _every single time the user runs Gradle_ - as such we should make sure to avoid doing unnecessary work.

The best way to avoid doing extra work is by making the realisation (when the objects are created) and configuration of `Task`s/`Configuration`s/other domain objects as _lazy_ as possible. This means that Gradle will not actually configure these objects unless it needs to. For example, if there's a task `foo` that we've lazily configured which isn't actually going to be run in the build - Gradle will not run the lazy configuration or even make ("realise") the `Task` object. The time savings may be small per task, but considering some repos have 1000s or 10,000s of tasks, the savings can be significant.

## Lazy task registration

When registering a task, you should use `register` instead of `create`:

#### Bad:

Using `create` here immediately _realises_ the `Task` object. The task is always configured (setting the input) even when the task is not going to be run.

```java
FooTask task = project.getTasks().create("foo", FooTask.class);
task.getInput().set(something);
```
Any other lazy configuration of the task (eg in other plugins or scripts using `tasks).

#### Good:

Instead, we should use `register`:

```java
TaskProvider<FooTask> task = project.getTasks().register("foo", FooTask.class, foo -> {
    foo.getInput().set(something);
});
```

This is better; we tell Gradle there is task called `foo`, but we give the configuration of the task lazily. If `foo` is not executed, we don't even realise or configure the task.

### Using `TaskProvider`s

The `register` approach now gives us a `TaskProvider`, which is a lazy handle to the task. Many  of the built-in Gradle APIs will let you pass a `TaskProvider` instead of a `Task` object.

If you want to give use an output property of a task as the input to another task, you can just transform the `TaskProvider` into another `Provider` using `map` or `flatMap`. You can call `.configure` on a `TaskProvider` to lazily configure the task without realising it (avoid calling `.get()` on `TaskProvider`s).

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

## Accessing tasks lazily

> Further reading: [Task Configuration Avoidance: Eager APIs to avoid](https://docs.gradle.org/current/userguide/task_configuration_avoidance.html#eager_apis_to_avoid)

To access tasks lazily, you need to make sure to call lazy APIs that return `TaskProvider` rather than `Task`.

### Getting single tasks

#### Bad:

These all return a `Task` so will realise the task and run all the lazy configuration on it immediately:

```java
project.getTasks().getByName("foo");
project.getTasks().findByName("foo");
project.getTasks().getByPath(":foo");
project.getTasks().findByPath(":foo");
project.getTasks().withType(Foo.class).getByName(":foo");
```

#### Good:

The `named` method returns a `TaskProvider`, allowing lazy configuration.

```java
TaskProvider<?> task = project.getTasks().named("foo");

TaskProvider<FooTask> task = project.getTasks()
        .withType(Foo.class)
        .named("foo");
```

### Iterating over tasks

#### Bad:

These will immediately realise newly registered tasks when they are added (even if added using `register`) and run all the lazy configuration on them:

```java
project.getTasks().whenTaskAdded(task -> ...);
project.getTasks().whenObjectAdded(task -> ...);
```

These will realise _all_ tasks, even ones already registered:

```java
project.getTasks().all(task -> ...);
project.getTasks().matching(task -> ...);
project.getTasks().withType(Foo.class).matching(task -> ...);
```

#### Good:

`configureEach` will lazily configure each task that already exists as well as new ones that get added:

```java
project.getTasks().configureEach(task -> ...)
```

It's a bit like running `.configure` on each `TaskProvider` without realising the tasks.

To move from `matching`, you can just use an `if` statement inside the `configureEach` rather than prefiltering the tasks.

## Lazy `Configuration` registration

`Configuration` objects should also be lazily registered. This follows the same pattern as tasks above. For example, you `register` rather than `create` a `Configuration`:

```java
Provider<Configuration> configuration = project.getConfigurations().register("myConfiguration", conf -> {
    conf.getDependencies().add(project.getDependencies().create("com.example:foo:1.0"));
});
```

If you need to use the name of the configuration elsewhere, you can `NamedDomainObjectProvider`:

```java
NamedDomainObjectProvider<Configuration> configuration = project.getConfigurations().register("myConfiguration", conf -> ...});

// Later
String configurationName = configuration.getName();
```

All of the `getByName`/`all` etc methods [described above for Tasks](#accessing-tasks-lazily) also apply for `Configuration`s.

## Other types

The advice for `Task`s also applies to other Gradle domain objects like `SourceSetContainer`, `ArtifactRepositoryContainer` - anything that inherits from [DomainObjectCollection](https://docs.gradle.org/current/javadoc/org/gradle/api/DomainObjectCollection.html#:~:text=All%20Known%20Subinterfaces%3A).

<!-- PreviousNext:START -->
<hr>
<table><tr>
  <td>Previous: <a href="lifecycle-of-a-gradle-build.md">Lifecycle of a Gradle Build</a></td>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="testing-and-running-your-plugins-locally.md">Testing and running your plugins</a></td>
</tr></table>
<!-- PreviousNext:END -->
