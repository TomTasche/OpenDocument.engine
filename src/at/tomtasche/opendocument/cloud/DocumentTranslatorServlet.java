package at.tomtasche.opendocument.cloud;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import at.stefl.commons.lwxml.writer.LWXMLStreamWriter;
import at.stefl.commons.lwxml.writer.LWXMLWriter;
import at.stefl.opendocument.java.odf.OpenDocument;
import at.stefl.opendocument.java.odf.OpenDocumentFile;
import at.stefl.opendocument.java.translator.document.DocumentTranslator;
import at.stefl.opendocument.java.translator.document.TextTranslator;
import at.stefl.opendocument.java.translator.settings.ImageStoreMode;
import at.stefl.opendocument.java.translator.settings.TranslationSettings;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsInputChannel;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;

@SuppressWarnings("serial")
public class DocumentTranslatorServlet extends BaseServlet {

	private GcsService gcsService;

	public DocumentTranslatorServlet() {
		gcsService = GcsServiceFactory.createGcsService();
	}

	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		FileType type = getType(req);
		if (type == null) {
			type = FileType.DOCUMENT;
		}

		String filename = req.getParameter("file");
		if (filename == null) {
			filename = "intro.odt";
		}
		
		boolean retranslatable = new Boolean(req.getParameter("retranslatable"));

		GcsInputChannel inputChannel = gcsService.openReadChannel(
				new GcsFilename(type + FILES_SUFFIX, filename), 0);

		InputStream stream = Channels.newInputStream(inputChannel);

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		int bytesRead;
		byte[] data = new byte[1024];
		while ((bytesRead = stream.read(data)) != -1) {
			buffer.write(data, 0, bytesRead);
		}

		buffer.flush();

		resp.setCharacterEncoding("UTF-8");
		resp.setContentType("text/html; charset=UTF-8");

		OpenDocumentFile documentFile = new NotLocatedOpenDocumentFile(
				buffer.toByteArray());

		OpenDocument openDocument = documentFile.getAsDocument();

		TranslationSettings settings = new TranslationSettings();
		settings.setBackTranslateable(retranslatable);
		settings.setImageStoreMode(ImageStoreMode.INLINE);

		LWXMLWriter out = new LWXMLStreamWriter(resp.getOutputStream());

		DocumentTranslator translator;
		try {
			translator = new TextTranslator();

			translator.translate(openDocument, out, settings);
		} finally {
			out.close();
			documentFile.close();
		}

		resp.flushBuffer();
	}
}
