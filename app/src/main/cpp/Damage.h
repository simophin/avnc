/*
 * Copyright (c) 2026  Gaurav Ujjwal.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
#ifndef AVNC_DAMAGE_H
#define AVNC_DAMAGE_H

/** Bounding box of changed pixels. Zero-initialization represents no damage. */
struct Damage {
    int left, top, right, bottom;

    bool empty() const { return right <= left || bottom <= top; }
    int width() const { return right - left; }
    int height() const { return bottom - top; }
    void clear() { left = top = right = bottom = 0; }

    // Callers validate framebuffer bounds before adding a rectangle.
    void add(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (empty()) {
            left = x; top = y; right = x + w; bottom = y + h;
        } else {
            if (x < left) left = x;
            if (y < top) top = y;
            if (x + w > right) right = x + w;
            if (y + h > bottom) bottom = y + h;
        }
    }
};
#endif
