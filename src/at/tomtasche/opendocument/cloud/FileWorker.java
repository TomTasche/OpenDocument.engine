package at.tomtasche.opendocument.cloud;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;

@SuppressWarnings("serial")
public class FileWorker extends BaseServlet {

	private GcsService gcsService;

	public FileWorker() {
		gcsService = GcsServiceFactory.createGcsService();
	}

	@Override
	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		FileType type = getType(request);
		if (type == null) {
			response.sendError(400);

			return;
		}

		gcsService.delete(new GcsFilename(type + FILES_SUFFIX, request
				.getParameter("file")));
	}
}
