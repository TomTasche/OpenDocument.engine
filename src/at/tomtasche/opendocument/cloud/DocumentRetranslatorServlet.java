package at.tomtasche.opendocument.cloud;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import at.stefl.commons.lwxml.writer.LWXMLStreamWriter;
import at.stefl.commons.lwxml.writer.LWXMLWriter;
import at.stefl.opendocument.java.odf.OpenDocument;
import at.stefl.opendocument.java.odf.OpenDocumentFile;
import at.stefl.opendocument.java.translator.Retranslator;
import at.stefl.opendocument.java.translator.settings.ImageStoreMode;
import at.stefl.opendocument.java.translator.settings.TranslationSettings;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsInputChannel;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;

@SuppressWarnings("serial")
public class DocumentRetranslatorServlet extends HttpServlet {

	private GcsService gcsService;

	public DocumentRetranslatorServlet() {
		gcsService = GcsServiceFactory.createGcsService();
	}

	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		GcsInputChannel htmlInputChannel = gcsService.openReadChannel(
				new GcsFilename("html-files", "intro.html"), 0);

		InputStream htmlStream = Channels.newInputStream(htmlInputChannel);

		GcsInputChannel documentInputChannel = gcsService.openReadChannel(
				new GcsFilename("document-files", "intro.odt"), 0);

		InputStream documentStream = Channels
				.newInputStream(documentInputChannel);

		ByteArrayOutputStream documentBuffer = new ByteArrayOutputStream();

		int bytesRead;
		byte[] data = new byte[1024];
		while ((bytesRead = documentStream.read(data)) != -1) {
			documentBuffer.write(data, 0, bytesRead);
		}

		documentBuffer.flush();

		resp.addHeader("Content-Disposition", "attachment; filename=" + "opendocument-modified.odt");

		resp.setCharacterEncoding("UTF-8");
		resp.setContentType("text/html; charset=UTF-8");

		OpenDocumentFile documentFile = new NotLocatedOpenDocumentFile(
				documentBuffer.toByteArray());

		OpenDocument openDocument = documentFile.getAsDocument();

		TranslationSettings settings = new TranslationSettings();
		settings.setBackTranslateable(true);
		settings.setImageStoreMode(ImageStoreMode.INLINE);

		LWXMLWriter out = new LWXMLStreamWriter(resp.getOutputStream());

		try {
			Retranslator.retranslate(openDocument, htmlStream,
					resp.getOutputStream());
		} finally {
			out.close();
			documentFile.close();
		}

		resp.flushBuffer();
	}
}
