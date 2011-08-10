package wikipedia.xml;

import org.simpleframework.xml.Attribute;

/**
 * Bean for WikiAPI XML result
 */
public final class Usercontribs {

    @Attribute
    private String ucstart;

    public String getUcstart() {
        return ucstart;
    }

    public void setUcstart(final String ucstart) {
        this.ucstart = ucstart;
    }

}
