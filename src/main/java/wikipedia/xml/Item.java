package wikipedia.xml;

import org.simpleframework.xml.Attribute;

/**
 * Wikiapi user contribs to article
 */
public final class Item {

    @Attribute
    private String userid;

    @Attribute
    private String user;

    @Attribute
    private String ns;

    @Attribute
    private String title;

    public String getUserid() {
        return userid;
    }
    public void setUserid(final String userid) {
        this.userid = userid;
    }
    public String getUser() {
        return user;
    }
    public void setUser(final String user) {
        this.user = user;
    }
    public String getNs() {
        return ns;
    }
    public void setNs(final String ns) {
        this.ns = ns;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(final String title) {
        this.title = title;
    }


}
