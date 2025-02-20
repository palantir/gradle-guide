# gradle-guide Error Prone Permalinks

<table>
<thead>
<tr>
<td>Name</td>
<td>Description</td>
<td>Detailed Link</td>
</tr>
</thead>
<tbody>
<tr>
<td>

<a id="ConfigurationAvoidanceRegistration">`ConfigurationAvoidanceRegistration`</a>

</td>
<td>
When registering a new `Task`, `Configuration` or other Gradle domain type, use `.register` instead of `.create` to avoid realising the object eagerly and performing unnecessary work which will slow down the build.
</td>
<td>
<a href="guide/avoiding-unnecessary-configuration.md#lazy-task-registration">More Info</a>
</td>
</tr>
</tbody>
</table>
