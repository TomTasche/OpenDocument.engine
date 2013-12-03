package at.tomtasche.opendocument.cloud;

public enum FileType {
	DOCUMENT, HTML, DRIVE, VARIOUS;

	public String toString() {
		return super.toString().toLowerCase();
	}
}