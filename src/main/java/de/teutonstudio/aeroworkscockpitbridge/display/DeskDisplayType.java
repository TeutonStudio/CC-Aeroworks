package de.teutonstudio.aeroworkscockpitbridge.display;

public enum DeskDisplayType {
    TWO_DIGIT(2),
    THREE_DIGIT(3);

    private final int digits;

    DeskDisplayType(int digits) {
        this.digits = digits;
    }

    public int digits() {
        return digits;
    }
}
