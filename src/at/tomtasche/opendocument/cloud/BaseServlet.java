package at.tomtasche.opendocument.cloud;

import java.util.Locale;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

@SuppressWarnings("serial")
public class BaseServlet extends HttpServlet {

	protected static final String FILES_SUFFIX = "-files";

	protected FileType getType(HttpServletRequest request) {
		String type = request.getParameter("type");
		if (type == null) {
			return null;
		}

		type = type.toUpperCase(Locale.ENGLISH);
		try {
			return FileType.valueOf(type);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
