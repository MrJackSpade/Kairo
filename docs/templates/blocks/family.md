<table>
{{#familyRows}}
  <tr>
{{#apps}}
    <td align="center" width="33%"><a href="{{url}}"><img src="{{iconUrl}}" width="96" alt="{{repo}}"><br><b>{{repo}}</b></a><br>{{system}}</td>
{{/apps}}
  </tr>
{{/familyRows}}
</table>
{{^isFrontend}}

Every Kairo app is built on [Kairo]({{frontendUrl}}), the shared frontend for the game library, controls, and settings.
{{/isFrontend}}
