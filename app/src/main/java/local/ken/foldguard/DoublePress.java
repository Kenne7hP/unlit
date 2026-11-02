package local.ken.foldguard;

/** Completed short presses only; the default is two, idle activation uses three. */
public final class DoublePress {
    private long down = -1, lastUp = -1;
    private boolean repeated;
    private final int required;
    private int completed;
    public DoublePress() { this(2); }
    public DoublePress(int required) {
        if (required < 2) throw new IllegalArgumentException("At least two presses required");
        this.required = required;
    }
    public void reset() { down = lastUp = -1; repeated = false; completed = 0; }
    public boolean event(int value, long now) {
        if (value == 1) { down = now; repeated = false; }
        if (value == 2) repeated = true;
        if (value != 0) return false;
        boolean shortPress = down >= 0 && !repeated && now >= down && now - down < 400;
        down = -1;
        if (!shortPress) { reset(); return false; }
        completed = lastUp >= 0 && now >= lastUp && now - lastUp <= 500 ? completed + 1 : 1;
        boolean match = completed == required;
        lastUp = now;
        if (match) reset();
        return match;
    }
}
