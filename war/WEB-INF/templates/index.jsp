<%@ page contentType="text/html;charset=UTF-8" language="java" %>

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
    var FILE_IDS = <%= request.getAttribute("ids") %>;
    for (i in FILE_IDS) {
    	get(FILE_IDS[i]);
    }
  </script>
</html>