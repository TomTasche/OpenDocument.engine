package at.tomtasche.opendocument.cloud;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;

@SuppressWarnings("serial")
public class FileWorker extends HttpServlet {

	private GcsService gcsService;

	public FileWorker() {
		gcsService = GcsServiceFactory.createGcsService();
	}

	@Override
	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		if (request.getParameter("file") == null)
			return;

		gcsService.delete(new GcsFilename("document-files", request
				.getParameter("file")));
	}
}
