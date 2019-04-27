package com.amazon.android.tv.tenfoot.ui.epg;

/**
 * Created by Kristoffer on 15-05-25.
 */
public interface EPGClickListener {

    void onChannelClicked(int channelPosition, EPGChannel epgChannel);

    void onEventClicked(EPGEvent epgEvent);

    void onEventSelected(EPGEvent epgEvent);

    void onResetButtonClicked();
}
