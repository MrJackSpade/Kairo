<h3 align="center">{{#isFrontend}}Kairo apps{{/isFrontend}}{{^isFrontend}}Other Kairo apps{{/isFrontend}}</h3>
<table align="center">
{{#familyRows}}
  <tr>
{{#apps}}
    <td align="center" width="33%"><a href="{{url}}"><img src="{{iconUrl}}" width="96" alt="{{repo}}"><br><b>{{repo}}</b></a><br>{{system}}</td>
{{/apps}}
  </tr>
{{/familyRows}}
</table>
{{^isFrontend}}
<p align="center">Every Kairo app is built on <a href="{{frontendUrl}}">Kairo</a>, the shared frontend for the game library, controls, and settings.</p>
{{/isFrontend}}
