package utility;

import java.awt.Point;

/** Shared snapping helpers for canvas components. */
public final class SnapUtils {
    private SnapUtils() {
    }

    public static int halfGridStep(int grid) {
        return Math.max(1, grid / 2);
    }

    public static Point snapComponentTopLeftToHalfGrid(int x, int y, int width, int height, int grid) {
        int half = halfGridStep(grid);
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        int snappedCenterX = Math.round((float) centerX / half) * half;
        int snappedCenterY = Math.round((float) centerY / half) * half;
        return new Point(snappedCenterX - width / 2, snappedCenterY - height / 2);
    }
}
