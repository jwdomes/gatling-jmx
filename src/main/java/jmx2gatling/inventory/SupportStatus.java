package jmx2gatling.inventory;

public enum SupportStatus {
    SUPPORTED, PARTIAL, UNSUPPORTED;

    public String label() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
