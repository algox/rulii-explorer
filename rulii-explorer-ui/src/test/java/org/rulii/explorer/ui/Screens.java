/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright (c) 1999-2026, Algorithmx Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rulii.explorer.ui;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Pixel comparison for the screenshot tests: the fraction of pixels whose colour differs by more
 * than a small tolerance, so antialiasing noise passes and layout changes fail.
 */
final class Screens {

    private static final int TOLERANCE = 24;

    private Screens() {
        super();
    }

    static double differingFraction(Path expected, Path actual) throws IOException {
        BufferedImage a = ImageIO.read(expected.toFile());
        BufferedImage b = ImageIO.read(actual.toFile());
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return 1.0;
        long differing = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int p = a.getRGB(x, y);
                int q = b.getRGB(x, y);
                if (Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)) > TOLERANCE
                        || Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)) > TOLERANCE
                        || Math.abs((p & 255) - (q & 255)) > TOLERANCE) {
                    differing++;
                }
            }
        }
        return (double) differing / ((long) a.getWidth() * a.getHeight());
    }
}
