package machinery;

/** Position of the timed-state badge around a state node. */
public enum TimedBadgePosition {
    TOP("Top"),
    BOTTOM("Bottom"),
    LEFT("Left"),
    RIGHT("Right"),
    TOP_LEFT("Top-left"),
    TOP_RIGHT("Top-right"),
    BOTTOM_LEFT("Bottom-left"),
    BOTTOM_RIGHT("Bottom-right");

    private final String displayName;

    TimedBadgePosition(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static TimedBadgePosition fromName(String name) {
        if (name == null || name.isBlank()) {
            return TOP;
        }
        for (TimedBadgePosition position : values()) {
            if (position.name().equalsIgnoreCase(name)
                    || position.displayName.equalsIgnoreCase(name)) {
                return position;
            }
        }
        return TOP;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
