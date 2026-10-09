/*
 * Copyright (c) 2022  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

#ifndef AVNC_CLIENTEX_H
#define AVNC_CLIENTEX_H

#include <jni.h>
#include "Cursor.h"
#include "Damage.h"

/**
 * We attach some additional data to every rfbClient.
 * ClientEx is used as wrapper for this data.
 */
struct ClientEx {
    // Reference to managed `VncClient`
    jobject managedClient;

    // Although frame width & height are maintained in rfbClient, those values
    // are modified before our MallocFrameBuffer callback is triggered, and
    // we cannot protect them with a mutex. So we maintain the framebuffer
    // size here, protected with mutex.
    int fbRealWidth;
    int fbRealHeight;

    // The decoder owns frameBuffer. GL only reads this published snapshot,
    // preventing uploads from racing with writes (including Tight decoding).
    uint8_t *renderBuffer;
    Damage pendingDamage; // Receiver thread only: pixels decoded in this message
    Damage uploadDamage;  // Protected by mutex: published pixels not yet uploaded
    uint8_t *uploadBuffer; // Packed subrectangle for GLES 2 (no unpack row length)
    size_t uploadCapacity;
    int textureWidth, textureHeight;
    int cursorTextureWidth, cursorTextureHeight;
    bool cursorDirty;

    // Cursor data used for client-side cursor rendering
    Cursor *cursor;

    // Protects modification to framebuffer & cursor
    MUTEX(mutex);
};

const int ClientExTag = 1;

ClientEx *getClientExtension(rfbClient *client) {
    return (ClientEx *) rfbClientGetClientData(client, (void *) &ClientExTag);
}

void setClientExtension(rfbClient *client, ClientEx *ex) {
    rfbClientSetClientData(client, (void *) &ClientExTag, ex);
}

/**
 * Returns reference to managed `VncClient` associated with given rfbClient.
 */
jobject getManagedClient(rfbClient *client) {
    return getClientExtension(client)->managedClient;
}

/**
 * Associate given rfbClient & managed `VncClient`.
 */
void setManagedClient(rfbClient *client, jobject managedClient) {
    getClientExtension(client)->managedClient = managedClient;
}

/**
 * Create new ClientEx and assign it to given client.
 */
ClientEx *assignClientExtension(rfbClient *client) {
    auto ex = (ClientEx *) calloc(1, sizeof(ClientEx));
    if (ex) {
        INIT_MUTEX(ex->mutex);
        ex->cursor = nullptr;
        setClientExtension(client, ex);
    }
    return ex;
}

/**
 * Free all resources related to client extension.
 */
void freeClientExtension(rfbClient *client) {
    auto ex = getClientExtension(client);
    if (ex) {
        TINI_MUTEX(ex->mutex);
        freeCursor(ex->cursor);
        free(ex->renderBuffer);
        free(ex->uploadBuffer);
        free(ex);
        setClientExtension(client, nullptr);
    }
}

#endif //AVNC_CLIENTEX_H
