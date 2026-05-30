package com.nationssmp.data;

/**
 * Represents a binding oath between two nations.
 * Both fields store the nation name (not UUID) for human-readable persistence.
 */
public class OathRecord {

    private final String initiatorNation;  // who spoke the oath
    private final String receiverNation;   // who was sworn to
    private final long   createdAt;        // System.currentTimeMillis()

    public OathRecord(String initiatorNation, String receiverNation) {
        this.initiatorNation = initiatorNation;
        this.receiverNation  = receiverNation;
        this.createdAt       = System.currentTimeMillis();
    }

    public OathRecord(String initiatorNation, String receiverNation, long createdAt) {
        this.initiatorNation = initiatorNation;
        this.receiverNation  = receiverNation;
        this.createdAt       = createdAt;
    }

    public String getInitiatorNation() { return initiatorNation; }
    public String getReceiverNation()  { return receiverNation; }
    public long   getCreatedAt()       { return createdAt; }

    /** True if the given nation name is one of the two parties. */
    public boolean involves(String nationName) {
        return initiatorNation.equalsIgnoreCase(nationName)
            || receiverNation.equalsIgnoreCase(nationName);
    }

    /** True if these two nations are allied with each other (order-independent). */
    public boolean isBetween(String a, String b) {
        return (initiatorNation.equalsIgnoreCase(a) && receiverNation.equalsIgnoreCase(b))
            || (initiatorNation.equalsIgnoreCase(b) && receiverNation.equalsIgnoreCase(a));
    }

    /** Given one party's name, return the other party's name. */
    public String getOtherParty(String nationName) {
        if (initiatorNation.equalsIgnoreCase(nationName)) return receiverNation;
        return initiatorNation;
    }
}
