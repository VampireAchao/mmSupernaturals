package me.matterz.supernaturals.it.harness;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/**
 * Draws a stand-in's skin, 64x64 pixels at a time.
 *
 * <p>Every skin here is made of flat rectangles this file places by hand: no Mojang texture, no
 * account's artwork, nothing downloaded. A skin used to be somebody's real profile, borrowed for
 * the duration of a test run; that is not a thing to keep doing, and a stand-in is decoration -
 * so the decoration is drawn here, and this project owns it.
 *
 * <p>The rectangles are the six base-layer regions of a modern skin, because the rest of the
 * sheet is the hat and jacket layers: filling those too would put an opaque shell around the
 * model. Leaving them transparent is what makes the result a person rather than a solid block
 * with a face on it.
 *
 * <p>Deliberately crude. A tasteful vampire would be a drawing, and a drawing is an asset dispute
 * waiting for a reason to happen; two colours and a trim stripe cannot be mistaken for anyone's
 * character design.
 */
final class SkinArt {

    /** A skin sheet is 64x64; the base layer is the top half, the overlay layers the rest. */
    private static final int SIZE = 64;

    /** The six base-layer rectangles, as {x, y, width, height}. */
    private static final int[][] BASE_REGIONS = {
            {0, 0, 32, 16},    // head
            {0, 16, 16, 32},   // right leg
            {16, 16, 40, 32},  // body (plus the unused strip beside it)
            {40, 16, 56, 32},  // right arm
            {16, 48, 32, 64},  // left leg
            {32, 48, 48, 64},  // left arm
    };

    /** The front of the face, which is the one piece a client always shows. */
    private static final int FACE_X = 8;
    private static final int FACE_Y = 8;
    private static final int FACE_SIZE = 8;

    /** Where the two eyes go, in the first quarter of the face. */
    private static final int EYE_Y = 2;
    private static final int EYE_LEFT_X = 1;
    private static final int EYE_RIGHT_X = 5;
    private static final int EYE_SIZE = 2;

    /** The trim stripe across the front of the body. */
    private static final int SASH_X = 20;
    private static final int SASH_Y = 26;
    private static final int SASH_WIDTH = 8;

    private SkinArt() {
    }

    /** The PNG a client would fetch for a skin of these two colours. */
    static byte[] png(int base, int accent) {
        BufferedImage sheet = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int[] region : BASE_REGIONS) {
            fill(sheet, region[0], region[1], region[2], region[3], opaque(base));
        }
        fill(sheet, FACE_X, FACE_Y, FACE_X + FACE_SIZE, FACE_Y + FACE_SIZE, opaque(lighter(base)));
        int eyeX = FACE_X + EYE_LEFT_X;
        int eyeRightX = FACE_X + EYE_RIGHT_X;
        int eyeY = FACE_Y + EYE_Y;
        fill(sheet, eyeX, eyeY, eyeX + EYE_SIZE, eyeY + EYE_SIZE, opaque(accent));
        fill(sheet, eyeRightX, eyeY, eyeRightX + EYE_SIZE, eyeY + EYE_SIZE, opaque(accent));
        fill(sheet, SASH_X, SASH_Y, SASH_X + SASH_WIDTH, SASH_Y + 2, opaque(accent));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(sheet, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            // A ByteArrayOutputStream cannot fail; if this ever throws, the sheet is wrong.
            throw new UncheckedIOException("could not encode a skin", e);
        }
    }

    private static void fill(BufferedImage sheet, int fromX, int fromY, int toX, int toY, int argb) {
        for (int y = fromY; y < toY; y++) {
            for (int x = fromX; x < toX; x++) {
                sheet.setRGB(x, y, argb);
            }
        }
    }

    /** Opaque, so the region is actually drawn rather than left as a hole in the sheet. */
    private static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    /** The face is the same cloth, a shade brighter: enough to read as a face, not a style. */
    private static int lighter(int rgb) {
        int r = Math.min(255, ((rgb >> 16) & 0xFF) + 34);
        int g = Math.min(255, ((rgb >> 8) & 0xFF) + 34);
        int b = Math.min(255, (rgb & 0xFF) + 34);
        return (r << 16) | (g << 8) | b;
    }
}
