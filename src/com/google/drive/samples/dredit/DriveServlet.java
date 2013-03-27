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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import openoffice.CachedOpenDocumentFile;
import openoffice.OpenDocumentSpreadsheet;
import openoffice.OpenDocumentSpreadsheetTemplate;
import openoffice.OpenDocumentText;
import openoffice.OpenDocumentTextTemplate;
import openoffice.html.ods.TranslatorOds;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.files.AppEngineFile;
import com.google.appengine.api.files.FileService;
import com.google.appengine.api.files.FileServiceFactory;
import com.google.appengine.api.files.FileWriteChannel;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.drive.samples.dredit.model.ClientFile;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Servlet providing a small API for the DrEdit JavaScript client to use in
 * manipulating files. Each operation (GET, POST, PUT) issues requests to the
 * Google Drive API.
 * 
 * @author vicfryzel@google.com (Vic Fryzel)
 */
@SuppressWarnings("serial")
public class DriveServlet extends DrEditServlet {

	private Gson gson;

	@Override
	public void init() throws ServletException {
		super.init();

		gson = new Gson();
	}

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

			String content = null;
			CachedOpenDocumentFile documentFile = new CachedOpenDocumentFile(
					stream);
			try {
				if (isDocument(documentFile)) {
					BlobKey key = persistFileContent(documentFile
							.getInputStream());

					JsonObject container = new JsonObject();
					container
							.addProperty(
									"redirect",
									resp.encodeRedirectURL("http://docs.google.com/viewer?url="
											+ URLEncoder.encode(
													"https://opendocument-engine.appspot.com/file?key="
															+ key.getKeyString(),
													"UTF-8") + "&embedded=true"));
					gson.toJson(container, resp.getWriter());

					return;

					// final OpenDocumentText text = new OpenDocumentText(
					// documentFile);
					// final TranslatorOdt translatorOdt = new TranslatorOdt(
					// text);
					//
					// content = translatorOdt.translate().getHtmlDocument()
					// .toString();
				} else if (isSpreadsheet(documentFile)) {
					OpenDocumentSpreadsheet spreadsheet = new OpenDocumentSpreadsheet(
							documentFile);
					TranslatorOds translatorOds = new TranslatorOds(spreadsheet);

					content = translatorOds.translate().getHtmlDocument()
							.toString();
				} else {
					sendError(resp, 500, "Could not display file");
				}
			} catch (Exception e) {
				e.printStackTrace();

				Logger.getAnonymousLogger().log(Level.SEVERE, "error", e);

				sendError(resp, 500, "Could not display file");

				return;
			}

			resp.getWriter().print(new ClientFile(file, content).toJson());
		} else {
			sendError(resp, 404, "File not found");
		}
	}

	private boolean isSpreadsheet(final CachedOpenDocumentFile file)
			throws IOException {
		return file.getMimeType().startsWith(OpenDocumentSpreadsheet.MIMETYPE)
				|| file.getMimeType().startsWith(
						OpenDocumentSpreadsheetTemplate.MIMETYPE);
	}

	private boolean isDocument(final CachedOpenDocumentFile file)
			throws IOException {
		return file.getMimeType().startsWith(OpenDocumentText.MIMETYPE)
				|| file.getMimeType().startsWith(
						OpenDocumentTextTemplate.MIMETYPE);
	}

	private InputStream getFileContent(Drive service, File file)
			throws IOException {
		GenericUrl url = new GenericUrl(file.getDownloadUrl());
		HttpResponse response = service.getRequestFactory()
				.buildGetRequest(url).execute();

		return response.getContent();
	}

	private BlobKey persistFileContent(InputStream stream) throws IOException {
		FileService fileService = FileServiceFactory.getFileService();
		AppEngineFile engineFile = fileService.createNewBlobFile(
				"application/vnd.oasis.opendocument.text", "file.odt");

		FileWriteChannel writeChannel = fileService.openWriteChannel(
				engineFile, true);
		OutputStream output = Channels.newOutputStream(writeChannel);

		int bytesRead;
		byte[] buffer = new byte[1024];
		while ((bytesRead = stream.read(buffer)) != -1) {
			output.write(buffer, 0, bytesRead);
		}

		output.close();
		writeChannel.closeFinally();

		BlobKey key = fileService.getBlobKey(engineFile);
		Queue queue = QueueFactory.getDefaultQueue();
		// delete all files after 20 minutes
		queue.add(TaskOptions.Builder.withUrl("/worker")
				.param("key", key.getKeyString()).countdownMillis(12000000));

		return key;
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