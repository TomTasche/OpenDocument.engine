package at.tomtasche.reader.engine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import at.stefl.opendocument.java.odf.OpenDocumentFile;
import at.stefl.opendocument.java.odf.ZipEntryNotFoundException;

public class NotLocatedOpenDocumentFile extends OpenDocumentFile {

	private byte[] data;

	private Map<String, ZipEntry> entryMap;

	public NotLocatedOpenDocumentFile(byte[] data) throws IOException {
		this.data = data;
	}

	private ByteArrayInputStream getDataStream() {
		return new ByteArrayInputStream(data);
	}

	@Override
	public long getFileSize(String name) {
		return entryMap.get(name).getSize();
	}

	@Override
	public boolean isFile(String name) throws IOException {
		if (entryMap == null)
			getFileNames();
		return entryMap.containsKey(name);
	}

	@Override
	public Set<String> getFileNames() throws IOException {
		if (entryMap == null) {
			ByteArrayInputStream dataStream = getDataStream();

			ZipInputStream zipStream = new ZipInputStream(dataStream);
			entryMap = new HashMap<String, ZipEntry>();

			ZipEntry entry = zipStream.getNextEntry();
			while (entry != null) {
				entryMap.put(entry.getName(), entry);

				entry = zipStream.getNextEntry();
			}

			zipStream.close();
			dataStream.close();
		}

		return entryMap.keySet();
	}

	@Override
	protected InputStream getRawFileStream(String name) throws IOException {
		ByteArrayInputStream dataStream = getDataStream();
		ZipInputStream zipStream = new ZipInputStream(dataStream);

		ZipEntry entry = zipStream.getNextEntry();
		while (entry != null) {
			if (entry.getName().equals(name)) {
				return zipStream;
			}

			entry = zipStream.getNextEntry();
		}

		throw new ZipEntryNotFoundException("entry does not exist: " + name);
	}

	@Override
	public void close() throws IOException {
		data = new byte[0];
	}

}