package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;

public class ChatReceiverImpl implements ChatReceiver {
    private volatile Callback mCallback;

    @Override
    public void addChatItem(ChatItem chatItem) {
        Callback callback = mCallback;
        if (callback != null) {
            callback.onChatItem(chatItem);
        }
    }

    @Override
    public synchronized void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    public synchronized void clearCallback(Callback owner) {
        if (mCallback == owner) {
            mCallback = null;
        }
    }
}
