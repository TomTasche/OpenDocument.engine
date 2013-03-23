package at.tomtasche.reader.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.files.AppEngineFile;
import com.google.appengine.api.files.FileService;
import com.google.appengine.api.files.FileServiceFactory;
import com.google.appengine.api.files.FileWriteChannel;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@SuppressWarnings("serial")
public class FileServlet extends HttpServlet {

	private BlobstoreService blobstoreService;
	private Gson gson;

	@Override
	public void init() throws ServletException {
		super.init();

		blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
		gson = new Gson();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		BlobKey key = new BlobKey(req.getParameter("key"));

		blobstoreService.serve(key, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		try {
			ServletFileUpload upload = new ServletFileUpload();

			FileItemIterator iterator = upload.getItemIterator(req);
			while (iterator.hasNext()) {
				FileItemStream item = iterator.next();
				InputStream stream = item.openStream();

				if (!item.isFormField()) {
					FileService fileService = FileServiceFactory
							.getFileService();
					AppEngineFile file = fileService
							.createNewBlobFile("application/octet-stream");

					FileWriteChannel writeChannel = fileService
							.openWriteChannel(file, true);
					OutputStream output = Channels
							.newOutputStream(writeChannel);

					int bytesRead;
					byte[] buffer = new byte[1024];
					while ((bytesRead = stream.read(buffer)) != -1) {
						output.write(buffer, 0, bytesRead);
					}

					output.close();
					writeChannel.closeFinally();

					BlobKey key = fileService.getBlobKey(file);
					Queue queue = QueueFactory.getDefaultQueue();
					queue.add(TaskOptions.Builder.withUrl("/worker")
							.param("key", key.getKeyString())
							.countdownMillis(600000));

					JsonObject container = new JsonObject();
					container.addProperty("key", key.getKeyString());
					gson.toJson(container, resp.getWriter());
				}
			}
		} catch (Exception ex) {
			throw new ServletException(ex);
		}
	}
}
