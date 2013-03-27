<%@ page contentType="text/html;charset=UTF-8" language="java" import="com.google.drive.samples.dredit.model.State" %>

<html>
  <head>
  	<title>OpenDocument Reader</title>
    <script src="/lib/jquery/jquery-1.7.2.min.js" type="text/javascript" charset="utf-8"></script>
    <script src="/js/script.js" type="text/javascript" charset="utf-8"></script>
  </head>
  <body>
  	Loading...
  </body>
  <script>
    <%
      State state = new State(request.getParameter("state"));
    %>
    
    var fileId = "<%= state.getFirstId() %>";
	get(fileId);
  </script>
</html>