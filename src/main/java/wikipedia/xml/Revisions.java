package wikipedia.xml;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

/**
 * XML Element returned from Wikipedia API
 * see http://en.wikipedia.org/w/api.php
 */
@Root(strict = false)
public final class Revisions {

    @Element(required = false)
    private String text;

    public String getText() {
        return text;
    }

    public void setText(final String text) {
        this.text = text;
    }

    @Attribute
    private String rvstartid;

    public String getRvstartid() {
        return rvstartid;
    }

    public void setRvstartid(final String rvstartid) {
        this.rvstartid = rvstartid;
    }
}
