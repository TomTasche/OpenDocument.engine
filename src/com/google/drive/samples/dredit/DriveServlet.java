/*
 * Copyright (c) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.drive.samples.dredit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import at.stefl.commons.lwxml.writer.LWXMLStreamWriter;
import at.stefl.commons.lwxml.writer.LWXMLWriter;
import at.stefl.opendocument.java.odf.OpenDocument;
import at.stefl.opendocument.java.odf.OpenDocumentFile;
import at.stefl.opendocument.java.odf.OpenDocumentPresentation;
import at.stefl.opendocument.java.odf.OpenDocumentSpreadsheet;
import at.stefl.opendocument.java.odf.OpenDocumentText;
import at.stefl.opendocument.java.translator.document.DocumentTranslator;
import at.stefl.opendocument.java.translator.document.PresentationTranslator;
import at.stefl.opendocument.java.translator.document.SpreadsheetTranslator;
import at.stefl.opendocument.java.translator.document.TextTranslator;
import at.stefl.opendocument.java.translator.settings.ImageStoreMode;
import at.stefl.opendocument.java.translator.settings.TranslationSettings;
import at.tomtasche.reader.engine.NotLocatedOpenDocumentFile;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.drive.samples.dredit.model.ClientFile;

/**
 * Servlet providing a small API for the DrEdit JavaScript client to use in
 * manipulating files. Each operation (GET, POST, PUT) issues requests to the
 * Google Drive API.
 * 
 * @author vicfryzel@google.com (Vic Fryzel)
 */
@SuppressWarnings("serial")
public class DriveServlet extends DrEditServlet {

	/**
	 * Given a {@code file_id} URI parameter, return a JSON representation of
	 * the given file.
	 */
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		Drive service = getDriveService(req, resp);
		String fileId = req.getParameter("file_id");

		if (fileId == null) {
			sendError(resp, 400,
					"The `file_id` URI parameter must be specified.");
			return;
		}

		File file = null;
		try {
			file = service.files().get(fileId).execute();
		} catch (GoogleJsonResponseException e) {
			if (e.getStatusCode() == 401) {
				// The user has revoked our token or it is otherwise bad.
				// Delete the local copy so that their next page load will
				// recover.
				deleteCredential(req, resp);
				sendError(resp, 401, "Unauthorized");
				return;
			}
		}

		resp.setContentType(JSON_MIMETYPE);
		if (file != null) {
			InputStream stream = getFileContent(service, file);

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
	
			try {
				OpenDocument openDocument = documentFile.getAsDocument();
	
				TranslationSettings settings = new TranslationSettings();
				settings.setBackTranslateable(false);
				settings.setImageStoreMode(ImageStoreMode.INLINE);
				settings.setSplitPages(false);
				
				DocumentTranslator translator;
				if (openDocument instanceof OpenDocumentText) {
					translator = new TextTranslator();
				} else if (openDocument instanceof OpenDocumentSpreadsheet) {
					translator = new SpreadsheetTranslator();
				} else if (openDocument instanceof OpenDocumentPresentation) {
					translator = new PresentationTranslator();
				} else {
					throw new IllegalStateException("unsupported document");
				}
	
				StringWriter writer = new StringWriter();
				LWXMLWriter out = new LWXMLStreamWriter(writer);

				translator.translate(openDocument, out, settings);
				
				resp.getWriter().print(new ClientFile(file, writer.toString()).toJson());
	
				resp.flushBuffer();
			} catch (Throwable e) {
				e.printStackTrace();

				Logger.getAnonymousLogger().log(Level.SEVERE, "error", e);

				sendError(resp, 500, "Could not display file");
			} finally {
				documentFile.close();
			}
		} else {
			sendError(resp, 404, "File not found");
		}
	}

	private InputStream getFileContent(Drive service, File file)
			throws IOException {
		GenericUrl url = new GenericUrl(file.getDownloadUrl());
		HttpResponse response = service.getRequestFactory()
				.buildGetRequest(url).execute();

		return response.getContent();
	}

	/**
	 * Build and return a Drive service object based on given request
	 * parameters.
	 * 
	 * @param req
	 *            Request to use to fetch code parameter or accessToken session
	 *            attribute.
	 * @param resp
	 *            HTTP response to use for redirecting for authorization if
	 *            needed.
	 * @return Drive service object that is ready to make requests, or null if
	 *         there was a problem.
	 */
	private Drive getDriveService(HttpServletRequest req,
			HttpServletResponse resp) {
		Credential credentials = getCredential(req, resp);

		return new Drive.Builder(TRANSPORT, JSON_FACTORY, credentials).build();
	}
}