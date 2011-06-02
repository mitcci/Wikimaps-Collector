package wikipedia.xml;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Text;

public class Rev {

    @Text
    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    @Attribute(required = false)
	private String user;

	@Attribute(required = false)
	private String minor;

	@Attribute(required = false)
	private String timestamp;

	@Attribute(required = false)
	private int size;

	@Attribute(required = false)
    private String space;

	public String getSpace() {
        return space;
    }

    public void setSpace(final String space) {
        this.space = space;
    }

    @Attribute(required = false)
	private String anon;

	@Attribute(required = false)
	private String userhidden;

	public String getUserhidden() {
        return userhidden;
    }

    public void setUserhidden(final String userhidden) {
        this.userhidden = userhidden;
    }

    public String getAnon() {
        return anon;
    }

    public void setAnon(final String anon) {
        this.anon = anon;
    }

    public int getSize() {
        return size;
    }

    public void setSize(final int size) {
        this.size = size;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final String timestamp) {
        this.timestamp = timestamp;
    }

    public String getMinor() {
		return minor;
	}

	public void setMinor(final String minor) {
		this.minor = minor;
	}

	public void setUser(final String user) {
		this.user = user;
	}

	public String getUser() {
		return user;
	}
}