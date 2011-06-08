package wikipedia.network;

public class GraphEdge {

    private final String from;
    private final String to;

    public GraphEdge(final String from, final String to) {
        this.from = from;
        this.to = to;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }


}
