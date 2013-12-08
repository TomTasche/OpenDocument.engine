package at.tomtasche.opendocument.cloud;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.util.UUID;

import javax.activation.MimetypesFileTypeMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.tools.cloudstorage.GcsFileOptions;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsInputChannel;
import com.google.appengine.tools.cloudstorage.GcsOutputChannel;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@SuppressWarnings("serial")
public class FileServlet extends BaseServlet {

	private MemcacheService memcache;
	private GcsService gcsService;
	private Gson gson;

	public FileServlet() {
		memcache = MemcacheServiceFactory.getMemcacheService();
		gcsService = GcsServiceFactory.createGcsService();
		gson = new Gson();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		FileType type = getType(req);
		if (type == null) {
			resp.sendError(400);

			return;
		}

		String file = req.getParameter("file");
		String name = (String) memcache.get(file);

		resp.addHeader("Content-Disposition", "attachment; filename=" + name);

		GcsInputChannel inputChannel = gcsService.openReadChannel(
				new GcsFilename(type.toString() + FILES_SUFFIX, file), 0);

		InputStream stream = Channels.newInputStream(inputChannel);

		int bytesRead;
		byte[] data = new byte[1024];
		while ((bytesRead = stream.read(data)) != -1) {
			resp.getOutputStream().write(data, 0, bytesRead);
		}

		resp.flushBuffer();
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		FileType type = getType(req);
		if (type == null) {
			resp.sendError(400);

			return;
		}

		InputStream stream;
		if (req.getParameter("url") != null) {
			stream = new URL(req.getParameter("url")).openStream();
		} else {
			stream = req.getInputStream();
		}

		String name = req.getParameter("name");
		String mime = req.getParameter("mime");
		if (mime == null || mime.equals("null") || mime.length() == 0) {
			mime = MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(
					name);
		}

		String fileId = UUID.randomUUID().toString();

		String[] splitName = name.split("\\.");
		String filename;
		if (splitName.length == 0) {
			filename = fileId + ".unknown";
		} else {
			filename = fileId + "." + splitName[splitName.length - 1];
		}

		if (name == null || name.equals("null") || name.length() == 0) {
			name = fileId;
		}

		GcsFilename file = new GcsFilename(type.toString() + FILES_SUFFIX,
				filename);
		GcsFileOptions options = new GcsFileOptions.Builder().mimeType(mime)
				.acl("public-read").build();
		GcsOutputChannel writeChannel = gcsService.createOrReplace(file,
				options);

		OutputStream output = Channels.newOutputStream(writeChannel);

		int bytesRead;
		byte[] data = new byte[1024];
		while ((bytesRead = stream.read(data)) != -1) {
			output.write(data, 0, bytesRead);
		}

		writeChannel.close();

		output.close();

		Queue queue = QueueFactory.getDefaultQueue();
		queue.add(TaskOptions.Builder.withUrl("/file/worker")
				.param("file", filename).param("type", type.toString())
				// TODO: change according to FileType:
				// e.g. store documents longer than various
				.countdownMillis(600000));

		memcache.put(filename, name, Expiration.byDeltaSeconds(600000));

		JsonObject container = new JsonObject();
		container.addProperty("file", filename);
		gson.toJson(container, resp.getWriter());
	}
}
