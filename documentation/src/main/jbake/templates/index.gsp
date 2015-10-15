<%include "header.gsp"%>
	<% if (content.body) { %>
		${content.body}
	<% } else if (new File('.').canonicalFile.name == 'documentation') { %>
		${new File('./src/main/jbake/templates/index.html').text}
	<% } else { %>
		${new File('./documentation/src/main/jbake/templates/index.html').text}
	<% } %>
<%include "footer.gsp"%>