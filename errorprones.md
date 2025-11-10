# gradle-guide Error Prone Permalinks

<table>
<thead>
<tr>
<td>Name</td>
<td>Detailed Link</td>
<td>Description</td>
</tr>
</thead>
<tbody>
<tr>
<td>

<a id="AvoidEagerApis" href="guide/avoiding-unnecessary-configuration.md#lazy-task-registration">`AvoidEagerApis`</a>

</td>
<td>
<a href="guide/avoiding-unnecessary-configuration.md#lazy-task-registration">Please read</a>
</td>
<td>

Avoid eager API methods, which force tasks/configurations to be realized. 

</td>
</tr>

<tr>
<td>

<a id="ConfigurationAvoidanceRegistration" href="guide/avoiding-unnecessary-configuration.md#lazy-task-registration">`ConfigurationAvoidanceRegistration`</a>

</td>
<td>
<a href="guide/avoiding-unnecessary-configuration.md#lazy-task-registration">Please read</a>
</td>
<td>

When registering a new `Task`, `Configuration` or other Gradle domain type, use `.register` instead of `.create` to avoid realising the object eagerly and performing unnecessary work which will slow down the build.

</td>
</tr>

<tr>
<td>

<a id="GradleManagedTypeGetPrefix" href="guide/managed-types-and-properties.md">`GradleManagedTypeGetPrefix`</a>

</td>
<td>
<a href="guide/managed-types-and-properties.md">Please read</a>
</td>
<td>

Abstract methods in Tasks or Extensions that return Gradle managed types should start with 'get'. This allows Gradle to handle property injection correctly. For example, use 'public abstract Property<String> getFoo();' instead of 'public abstract Property<String> foo();'. This enables Gradle to inject the property implementation automatically, removes boilerplate, and supports the Groovy DSL (e.g. `foo = 3`).

</td>
</tr>

<tr>
<td>

<a id="GradleTypesAsFields" href="guide/managed-types-and-properties.md">`GradleTypesAsFields`</a>

</td>
<td>
<a href="guide/managed-types-and-properties.md">Please read</a>
</td>
<td>

Do not declare Properties, FileCollections and other Gradle managed types as fields directly on Tasks or Extensions. Instead, declare an abstract getter method, e.g., 'public abstract Property<String> getFoo();'. This enables Gradle to inject the property implementation automatically, removes boilerplate, and supports the Groovy DSL (e.g. `foo = 3`).

</td>
</tr>

<tr>
<td>

<a id="IllegalMethodCalledDuringTaskExecution" href="guide/adopting-the-configuration-cache.md#solving-configuration-cache-problems">`IllegalMethodCalledDuringTaskExecution`</a>

</td>
<td>
<a href="guide/adopting-the-configuration-cache.md#solving-configuration-cache-problems">Please read</a>
</td>
<td>

Don't call `getProject()` in task actions. Instead, your tasks should take in the "smallest" type
required for the task's functionality. For example, instead getProject().version(), you should declare the
project version as an `@Input public abstract Property<String>`.

Doing so improves performance in two ways:
1. It makes your tasks compatible with the configuration cache
2. It increases task parallelism. When two tasks, such as printProjectName and printProjectVersion, both
require the same Project object as input, they cannot run in parallel to prevent concurrent access.
However, if their inputs are changed to Provider<String> name and Provider<String> version respectively,
the tasks become independent and can execute in parallel.


</td>
</tr>

<tr>
<td>

<a id="NonAbstractGradleType" href="guide/managed-types-and-properties.md">`NonAbstractGradleType`</a>

</td>
<td>
<a href="guide/managed-types-and-properties.md">Please read</a>
</td>
<td>

When defining a custom Task or Extension, you should make it an abstract class with abstract getter methods of each of the properties and other gradle containers (eg NamedDomainObjectSet). Gradle will then automatically create the properties and containers, removing a lot of boilerplate. Additionally, as you declare eg `public abstract Property<Integer> getFoo()`, this will automatically make the `foo = 3` groovy syntax work of the box.

</td>
</tr>
</tbody>
</table>
