/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.services.telephony;

import android.content.Context;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.telecom.CallAudioState;
import android.telecom.ConferenceParticipant;
import android.telecom.Connection;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.CarrierConfigManager;
import android.telephony.DisconnectCause;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.util.Pair;
/// M: IMS feature. @{
import android.telecom.VideoProfile;
/// @}
import android.text.TextUtils;

/// M: CC: ECC Retry @{
import android.telephony.PhoneNumberUtils;
/// @}

/// M: VILTE feature. @{
import android.widget.Toast;
/// @}

import com.android.ims.ImsCall;
import com.android.ims.ImsCallProfile;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection.Capability;
import com.android.internal.telephony.Connection.PostDialListener;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.gsm.SuppServiceNotification;

import com.android.internal.telephony.Phone;

import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneCallTracker;
/// M: IMS feature @{
import com.android.internal.telephony.imsphone.ImsPhoneConnection;
/// @}
import com.android.phone.ImsUtil;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneUtils;
import com.android.phone.R;

/// M: CC: to check whether the device has on-going ECC
import com.mediatek.internal.telephony.RadioManager;

/// M: CC: GSA HD Voice for 2/3G network support @{
import com.mediatek.services.telephony.SpeechCodecType;
/// @}

/// M: ALPS02136977. Prints debug logs for telephony.
import com.mediatek.telecom.FormattedLog;

/// M: IMS feature. @{
import com.mediatek.telecom.TelecomManagerEx;
/// @}

import java.lang.Override;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for CDMA and GSM connections.
 */
/// M: CC: Proprietary CRSS handling @{
// Declare as public for SuppMessageManager to use.
public abstract class TelephonyConnection extends Connection {
/// @}
    private static final String TAG = "TelephonyConn";

    // Sensitive log task
    private static final String PROP_FORCE_DEBUG_KEY = "persist.log.tag.tel_dbg";
    private static final boolean SENLOG = TextUtils.equals(Build.TYPE, "user");
    private static final boolean SDBG = TextUtils.equals(Build.TYPE, "user") ? false : true;
    private static final boolean TELDBG = (SystemProperties.getInt(PROP_FORCE_DEBUG_KEY, 0) == 1);

    private static final int MSG_PRECISE_CALL_STATE_CHANGED = 1;
    private static final int MSG_RINGBACK_TONE = 2;
    private static final int MSG_HANDOVER_STATE_CHANGED = 3;
    private static final int MSG_DISCONNECT = 4;
    private static final int MSG_MULTIPARTY_STATE_CHANGED = 5;
    private static final int MSG_CONFERENCE_MERGE_FAILED = 6;
    private static final int MSG_SUPP_SERVICE_NOTIFY = 7;

    /**
     * Mappings from {@link com.android.internal.telephony.Connection} extras keys to their
     * equivalents defined in {@link android.telecom.Connection}.
     */
    private static final Map<String, String> sExtrasMap = createExtrasMap();

    /// M: CC: ECC Retry
    private boolean mIsLocallyDisconnecting = false;

    private static final int MSG_SET_VIDEO_STATE = 8;
    private static final int MSG_SET_VIDEO_PROVIDER = 9;
    private static final int MSG_SET_AUDIO_QUALITY = 10;
    private static final int MSG_SET_CONFERENCE_PARTICIPANTS = 11;
    private static final int MSG_CONNECTION_EXTRAS_CHANGED = 12;
    private static final int MSG_SET_ORIGNAL_CONNECTION_CAPABILITIES = 13;
    private static final int MSG_ON_HOLD_TONE = 14;
    private static final int MSG_CDMA_VOICE_PRIVACY_ON = 15;
    private static final int MSG_CDMA_VOICE_PRIVACY_OFF = 16;

    private static final int MTK_EVENT_BASE = 1000;
    /// M: CC: Modem reset related handling
    private static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE       = MTK_EVENT_BASE;
    /// M: CC: GSA HD Voice for 2/3G network support
    private static final int EVENT_SPEECH_CODEC_INFO                = MTK_EVENT_BASE + 1;
    /// M: CC: For 3G VT only
    private static final int EVENT_VT_STATUS_INFO                   = MTK_EVENT_BASE + 2;
    /// M: For CDMA call accepted
    private static final int EVENT_CDMA_CALL_ACCEPTED               = MTK_EVENT_BASE + 3;

    /// M: For action during SRVCC. @{
    private enum SrvccPendingAction {
        SRVCC_PENDING_NONE,
        SRVCC_PENDING_ANSWER_CALL,
        SRVCC_PENDING_HOLD_CALL,
        SRVCC_PENDING_UNHOLD_CALL,
        SRVCC_PENDING_HANGUP_CALL
    }

    private SrvccPendingAction mPendingAction = SrvccPendingAction.SRVCC_PENDING_NONE;
    /// @}

    /// M: CC: GSA HD Voice for 2/3G network support @{
    private static final String PROPERTY_HD_VOICE_STATUS = "af.ril.hd.voice.status";
    private SpeechCodecType mSpeechType;
    /// @}

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PRECISE_CALL_STATE_CHANGED:
                    Log.v(TelephonyConnection.this, "MSG_PRECISE_CALL_STATE_CHANGED");
                    /// M: ALPS02136977. Prints debug messages for telephony. @{
                    if ((!mIsMultiParty || !isImsConnection())
                            && mOriginalConnection != null
                            && mConnectionState != mOriginalConnection.getState()) {
                        logDebugMsgWithNotifyFormat(
                                callStateToFormattedNotifyString(mOriginalConnection.getState()),
                                null);
                    }
                    /// @}
                    updateState();
                    break;
                case MSG_HANDOVER_STATE_CHANGED:
                    Log.v(TelephonyConnection.this, "MSG_HANDOVER_STATE_CHANGED");
                    AsyncResult ar = (AsyncResult) msg.obj;
                    com.android.internal.telephony.Connection connection =
                         (com.android.internal.telephony.Connection) ar.result;
                    if (mOriginalConnection != null) {
                        if (connection != null &&
                            ((connection.getAddress() != null &&
                            mOriginalConnection.getAddress() != null &&
                            mOriginalConnection.getAddress().contains(connection.getAddress())) ||
                            connection.getState() == mOriginalConnection.getStateBeforeHandover())) {
                            Log.d(TelephonyConnection.this,
                                    "SettingOriginalConnection " + mOriginalConnection.toString()
                                            + " with " + connection.toString());
                            /// M: ALPS02528198 Show toast when VILTE SRVCC @{
                            if (mOriginalConnection.getVideoState() !=
                                    VideoProfile.STATE_AUDIO_ONLY &&
                                    connection.getVideoState() == VideoProfile.STATE_AUDIO_ONLY) {
                                Context context = getPhone().getContext();
                                Toast.makeText(context,
                                        context.getString(R.string.vilte_srvcc_tip),
                                        Toast.LENGTH_LONG).show();
                                Log.d(TelephonyConnection.this,
                                        "Video call change to vocie call during SRVCC");
                            }
                            /// @}
                            /// M: ALPS03245602, remove the property volte after SRVCC. @{
                            removePropertyVoLte();
                            /// @}
                            setOriginalConnection(connection);
                            mWasImsConnection = false;
                            /// M: Try SRVCC pending action. @{
                            trySrvccPendingAction();
                            /// @}
                        }
                    } else {
                        Log.w(TelephonyConnection.this,
                                "MSG_HANDOVER_STATE_CHANGED: mOriginalConnection==null - invalid state (not cleaned up)");
                    }
                    break;
                case MSG_RINGBACK_TONE:
                    Log.v(TelephonyConnection.this, "MSG_RINGBACK_TONE");
                    // TODO: This code assumes that there is only one connection in the foreground
                    // call, in other words, it punts on network-mediated conference calling.
                    if (getOriginalConnection() != getForegroundConnection()) {
                        Log.v(TelephonyConnection.this, "handleMessage, original connection is " +
                                "not foreground connection, skipping");
                        return;
                    }
                    setRingbackRequested((Boolean) ((AsyncResult) msg.obj).result);
                    break;
                case MSG_DISCONNECT:
                    /// M: @{
                    int cause = mOriginalConnection == null
                            ? android.telephony.DisconnectCause.NOT_DISCONNECTED
                            : mOriginalConnection.getDisconnectCause();
                    log("handle MSG_DISCONNECT ... cause = " + cause);

                    if (mOriginalConnection != null && cause ==
                            android.telephony.DisconnectCause.IMS_EMERGENCY_REREG) {
                        try {
                            com.android.internal.telephony.Connection conn =
                                    getPhone().dial(mOriginalConnection.getOrigDialString(),
                                    VideoProfile.STATE_AUDIO_ONLY);
                            setOriginalConnection(conn);
                            notifyEcc();
                        } catch (CallStateException e) {
                            Log.e(TAG, e, "Fail to redial as ECC");
                        }
                    } else {
                        /// M: ALPS02136977. Prints debug messages for telephony. @{
                        if ((!mIsMultiParty || !isImsConnection())
                                && mOriginalConnection != null
                                && mConnectionState != mOriginalConnection.getState()) {
                            logDebugMsgWithNotifyFormat(callStateToFormattedNotifyString(
                                    mOriginalConnection.getState()), null);
                        }
                        /// @}
                        /// M: CC: ECC Retry @{
                        if (!mIsLocallyDisconnecting
                                && cause != android.telephony.DisconnectCause.NOT_DISCONNECTED
                                && cause != android.telephony.DisconnectCause.LOST_SIGNAL
                                && cause != android.telephony.DisconnectCause.NORMAL
                                && cause != android.telephony.DisconnectCause.LOCAL) {
                            log("ECC Retry : check whether need to retry, "
                                    + "mConnectionState:" + mConnectionState);
                            // Don't retry if treated as normal call in Telephony Framework
                            final boolean isDialedByEmergencyCommand = PhoneNumberUtils
                                    .isEmergencyNumber(mOriginalConnection.getAddress());
                            // Assume only one ECC exists
                            if (mTreatAsEmergencyCall
                                    && TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()
                                    && (mConnectionState.isDialing()
                                        || mConnectionState == Call.State.IDLE)
                                    && !TelephonyConnectionServiceUtil.getInstance()
                                            .eccRetryTimeout()
                                    && isDialedByEmergencyCommand) {
                                log("ECC Retry : Meet retry condition, state="
                                        + mConnectionState);
                                /// M: ALPS03292404, remove the property volte before ECC retry. @{
                                removePropertyVoLte();
                                /// @}
                                close(); // To removeConnection
                                TelephonyConnectionServiceUtil.getInstance().performEccRetry();
                                break;
                            }
                        }
                        /// @}
                        updateState();
                    }
                    /// @}
                    break;
                case MSG_MULTIPARTY_STATE_CHANGED:
                    boolean isMultiParty = (Boolean) msg.obj;
                    Log.i(this, "Update multiparty state to %s", isMultiParty ? "Y" : "N");
                    mIsMultiParty = isMultiParty;
                    if (isMultiParty) {
                        notifyConferenceStarted();
                    }
                    break;
                case MSG_CONFERENCE_MERGE_FAILED:
                    notifyConferenceMergeFailed();
                    break;
                case MSG_SUPP_SERVICE_NOTIFY:
                    /// M: Fix Google JE issue @{
                    // phone maybe null
                    Phone phone = getPhone();
                    if (phone != null) {
                        Log.v(TelephonyConnection.this, "MSG_SUPP_SERVICE_NOTIFY on phoneId : "
                                + phone.getPhoneId());
                    }
                    /// @}
                    SuppServiceNotification mSsNotification = null;
                    if (msg.obj != null && ((AsyncResult) msg.obj).result != null) {
                        mSsNotification =
                                (SuppServiceNotification)((AsyncResult) msg.obj).result;
                        if (mOriginalConnection != null && mSsNotification.history != null) {
                            Bundle lastForwardedNumber = new Bundle();
                            Log.v(TelephonyConnection.this,
                                    "Updating call history info in extras.");
                            lastForwardedNumber.putStringArrayList(
                                Connection.EXTRA_LAST_FORWARDED_NUMBER,
                                new ArrayList(Arrays.asList(mSsNotification.history)));
                            putExtras(lastForwardedNumber);
                        }
                    }
                    break;

                case MSG_SET_VIDEO_STATE:
                    int videoState = (int) msg.obj;
                    /// M: show toast when video change to voice for VILTE  @{
                    String optr = SystemProperties.get("persist.operator.optr");
                    Log.i(TelephonyConnection.this, "operator: " + optr
                            + " mWasImsConnection: " + mWasImsConnection);
                    if (mWasImsConnection && optr != null && optr.equals("OP01")) {
                        if (videoState == VideoProfile.STATE_AUDIO_ONLY &&
                                getVideoState() != VideoProfile.STATE_AUDIO_ONLY) {
                            Context context = getPhone().getContext();
                            Toast.makeText(context,
                                    context.getString(R.string.vilte_to_voice_call),
                                    Toast.LENGTH_LONG).show();
                            Log.d(TelephonyConnection.this,
                                    "Video call change to vocie call");
                        }
                    }
                    /// @}
                    setVideoState(videoState);

                    // A change to the video state of the call can influence whether or not it
                    // can be part of a conference, whether another call can be added, and
                    // whether the call should have the HD audio property set.
                    refreshConferenceSupported();
                    refreshDisableAddCall();
                    updateConnectionProperties();
                    break;

                case MSG_SET_VIDEO_PROVIDER:
                    VideoProvider videoProvider = (VideoProvider) msg.obj;
                    setVideoProvider(videoProvider);
                    break;

                case MSG_SET_AUDIO_QUALITY:
                    /// M: Use EVENT_SPEECH_CODEC_INFO
                    //int audioQuality = (int) msg.obj;
                    //setAudioQuality(audioQuality);
                    break;

                case MSG_SET_CONFERENCE_PARTICIPANTS:
                    List<ConferenceParticipant> participants = (List<ConferenceParticipant>) msg.obj;
                    updateConferenceParticipants(participants);
                    break;

                case MSG_CONNECTION_EXTRAS_CHANGED:
                    final Bundle extras = (Bundle) msg.obj;
                    updateExtras(extras);
                    break;

                case MSG_SET_ORIGNAL_CONNECTION_CAPABILITIES:
                    setOriginalConnectionCapabilities(msg.arg1);
                    break;

                case MSG_ON_HOLD_TONE:
                    AsyncResult asyncResult = (AsyncResult) msg.obj;
                    Pair<com.android.internal.telephony.Connection, Boolean> heldInfo =
                            (Pair<com.android.internal.telephony.Connection, Boolean>)
                                    asyncResult.result;

                    // Determines if the hold tone is starting or stopping.
                    boolean playTone = ((Boolean) (heldInfo.second)).booleanValue();

                    // Determine which connection the hold tone is stopping or starting for
                    com.android.internal.telephony.Connection heldConnection = heldInfo.first;

                    // Only start or stop the hold tone if this is the connection which is starting
                    // or stopping the hold tone.
                    if (heldConnection == mOriginalConnection) {
                        // If starting the hold tone, send a connection event to Telecom which will
                        // cause it to play the on hold tone.
                        if (playTone) {
                            sendConnectionEvent(EVENT_ON_HOLD_TONE_START, null);
                        } else {
                            sendConnectionEvent(EVENT_ON_HOLD_TONE_END, null);
                        }
                    }
                    break;

                case MSG_CDMA_VOICE_PRIVACY_ON:
                    Log.d(this, "MSG_CDMA_VOICE_PRIVACY_ON received");
                    setCdmaVoicePrivacy(true);
                    break;
                case MSG_CDMA_VOICE_PRIVACY_OFF:
                    Log.d(this, "MSG_CDMA_VOICE_PRIVACY_OFF received");
                    setCdmaVoicePrivacy(false);
                    break;

                /// M: CC: Modem reset related handling @{
                case EVENT_RADIO_OFF_OR_NOT_AVAILABLE:
                    notifyConnectionLost();
                    onLocalDisconnected();
                    break;
                /// @}
                /// M: CC: GSA HD Voice for 2/3G network support @{
                case EVENT_SPEECH_CODEC_INFO:
                    int value = (int) ((AsyncResult) msg.obj).result;
                    log("EVENT_SPEECH_CODEC_INFO : " + value);
                    if (isHighDefAudio(value)) {
                        setAudioQuality(com.android.internal.telephony.Connection.
                                AUDIO_QUALITY_HIGH_DEFINITION);
                    } else {
                        setAudioQuality(com.android.internal.telephony.Connection.
                                AUDIO_QUALITY_STANDARD);
                    }
                    break;
                /// @}
                /// M: CC: For 3G VT only @{
                case EVENT_VT_STATUS_INFO:
                    int status = ((int[]) ((AsyncResult) msg.obj).result)[0];
                    notifyVtStatusInfo(status);
                    break;
                /// @}
                /// M: For CDMA call accepted @{
                case EVENT_CDMA_CALL_ACCEPTED:
                    //Log.v(TelephonyConnection.this, "EVENT_CDMA_CALL_ACCEPTED");
                    updateConnectionCapabilities();
                    fireOnCdmaCallAccepted();
                    break;
                /// @}
            }
        }
    };

    /**
     * @return {@code true} if carrier video conferencing is supported, {@code false} otherwise.
     */
    public boolean isCarrierVideoConferencingSupported() {
        return mIsCarrierVideoConferencingSupported;
    }

    /**
     * A listener/callback mechanism that is specific communication from TelephonyConnections
     * to TelephonyConnectionService (for now). It is more specific that Connection.Listener
     * because it is only exposed in Telephony.
     */
    public abstract static class TelephonyConnectionListener {
        public void onOriginalConnectionConfigured(TelephonyConnection c) {}
        public void onOriginalConnectionRetry(TelephonyConnection c) {}

        /// M: VoLTE. @{
        /**
         * For VoLTE enhanced conference call, notify invite conf. participants completed.
         * @param isSuccess is success or not.
         */
        public void onConferenceParticipantsInvited(boolean isSuccess) {}

        /**
         * For VoLTE conference SRVCC, notify when new participant connections maded.
         * @param radioConnections new participant connections.
         */
        public void onConferenceConnectionsConfigured(
            ArrayList<com.android.internal.telephony.Connection> radioConnections) {}
        /// @}
    }

    private final PostDialListener mPostDialListener = new PostDialListener() {
        @Override
        public void onPostDialWait() {
            Log.v(TelephonyConnection.this, "onPostDialWait");
            if (mOriginalConnection != null) {
                setPostDialWait(mOriginalConnection.getRemainingPostDialString());
            }
        }

        @Override
        public void onPostDialChar(char c) {
            Log.v(TelephonyConnection.this, "onPostDialChar: %s", c);
            if (mOriginalConnection != null) {
                setNextPostDialChar(c);
            }
        }
    };

    /**
     * Listener for listening to events in the {@link com.android.internal.telephony.Connection}.
     */
    private final com.android.internal.telephony.Connection.Listener mOriginalConnectionListener =
            new com.android.internal.telephony.Connection.ListenerBase() {
        @Override
        public void onVideoStateChanged(int videoState) {
            mHandler.obtainMessage(MSG_SET_VIDEO_STATE, videoState).sendToTarget();
        }

        /*
         * The {@link com.android.internal.telephony.Connection} has reported a change in
         * connection capability.
         * @param capabilities bit mask containing voice or video or both capabilities.
         */
        @Override
        public void onConnectionCapabilitiesChanged(int capabilities) {
            mHandler.obtainMessage(MSG_SET_ORIGNAL_CONNECTION_CAPABILITIES,
                    capabilities, 0).sendToTarget();
        }

        /**
         * The {@link com.android.internal.telephony.Connection} has reported a change in the
         * video call provider.
         *
         * @param videoProvider The video call provider.
         */
        @Override
        public void onVideoProviderChanged(VideoProvider videoProvider) {
            mHandler.obtainMessage(MSG_SET_VIDEO_PROVIDER, videoProvider).sendToTarget();
        }

        /**
         * Used by {@link com.android.internal.telephony.Connection} to report a change in whether
         * the call is being made over a wifi network.
         *
         * @param isWifi True if call is made over wifi.
         */
        @Override
        public void onWifiChanged(boolean isWifi) {
            setWifi(isWifi);
        }

        /**
         * Used by the {@link com.android.internal.telephony.Connection} to report a change in the
         * audio quality for the current call.
         *
         * @param audioQuality The audio quality.
         */
        @Override
        public void onAudioQualityChanged(int audioQuality) {
            mHandler.obtainMessage(MSG_SET_AUDIO_QUALITY, audioQuality).sendToTarget();
        }
        /**
         * Handles a change in the state of conference participant(s), as reported by the
         * {@link com.android.internal.telephony.Connection}.
         *
         * @param participants The participant(s) which changed.
         */
        @Override
        public void onConferenceParticipantsChanged(List<ConferenceParticipant> participants) {
            mHandler.obtainMessage(MSG_SET_CONFERENCE_PARTICIPANTS, participants).sendToTarget();
        }

        /*
         * Handles a change to the multiparty state for this connection.
         *
         * @param isMultiParty {@code true} if the call became multiparty, {@code false}
         *      otherwise.
         */
        @Override
        public void onMultipartyStateChanged(boolean isMultiParty) {
            handleMultipartyStateChange(isMultiParty);
        }

        /**
         * Handles the event that the request to merge calls failed.
         */
        @Override
        public void onConferenceMergedFailed() {
            handleConferenceMergeFailed();
        }

        @Override
        public void onExtrasChanged(Bundle extras) {
            mHandler.obtainMessage(MSG_CONNECTION_EXTRAS_CHANGED, extras).sendToTarget();
        }

        /**
         * Handles the phone exiting ECM mode by updating the connection capabilities.  During an
         * ongoing call, if ECM mode is exited, we will re-enable mute for CDMA calls.
         */
        @Override
        public void onExitedEcmMode() {
            handleExitedEcmMode();
        }

        /**
         * Called from {@link ImsPhoneCallTracker} when a request to pull an external call has
         * failed.
         * @param externalConnection
         */
        @Override
        public void onCallPullFailed(com.android.internal.telephony.Connection externalConnection) {
            if (externalConnection == null) {
                return;
            }

            Log.i(this, "onCallPullFailed - pull failed; swapping back to call: %s",
                    externalConnection);

            // Inform the InCallService of the fact that the call pull failed (it may choose to
            // display a message informing the user of the pull failure).
            sendConnectionEvent(Connection.EVENT_CALL_PULL_FAILED, null);

            // Swap the ImsPhoneConnection we used to do the pull for the ImsExternalConnection
            // which originally represented the call.
            setOriginalConnection(externalConnection);

            // Set our state to active again since we're no longer pulling.
            setActiveInternal();
        }

        /**
         * Called from {@link ImsPhoneCallTracker} when a handover to WIFI has failed.
         */
        @Override
        public void onHandoverToWifiFailed() {
            sendConnectionEvent(TelephonyManager.EVENT_HANDOVER_TO_WIFI_FAILED, null);
        }

        /**
         * Informs the {@link android.telecom.ConnectionService} of a connection event raised by the
         * original connection.
         * @param event The connection event.
         * @param extras The extras.
         */
        @Override
        public void onConnectionEvent(String event, Bundle extras) {
            sendConnectionEvent(event, extras);
        }

        /// M: For VoLTE conference call. @{
        /**
         * For VoLTE enhanced conference call, notify invite conf. participants completed.
         * @param isSuccess is success or not.
         */
        @Override
        public void onConferenceParticipantsInvited(boolean isSuccess) {
            notifyConferenceParticipantsInvited(isSuccess);
        }

        /**
         * For VoLTE conference SRVCC, notify when new participant connections maded.
         * @param radioConnections new participant connections.
         */
        @Override
        public void onConferenceConnectionsConfigured(
                ArrayList<com.android.internal.telephony.Connection> radioConnections) {
            notifyConferenceConnectionsConfigured(radioConnections);
        }
        /// @}

        /// M: For remote call held changed. @{
        /**
         * For notify the remote held or resumed event to telecom.
         * @param isHeld is held or not
         */
        @Override
        public void onRemoteHeld(boolean isHeld) {
            Log.d(TelephonyConnection.this, "onRemoteHeld: " + isHeld);
            if (isHeld) {
                sendConnectionEvent(TelecomManagerEx.EVENT_ON_REMOTE_HOLD, null);
            } else {
                sendConnectionEvent(TelecomManagerEx.EVENT_ON_REMOTE_RESUME, null);
            }
        }

        @Override
        public void onAddressDisplayChanged() {
            updateAddress();
        }
        /// @}
    };

    protected com.android.internal.telephony.Connection mOriginalConnection;
    private Call.State mConnectionState = Call.State.IDLE;
    private Bundle mOriginalConnectionExtras = new Bundle();
    private boolean mIsStateOverridden = false;
    private Call.State mOriginalConnectionState = Call.State.IDLE;
    private Call.State mConnectionOverriddenState = Call.State.IDLE;

    private boolean mWasImsConnection;

    /**
     * Tracks the multiparty state of the ImsCall so that changes in the bit state can be detected.
     */
    private boolean mIsMultiParty = false;

    /**
     * The {@link com.android.internal.telephony.Connection} capabilities associated with the
     * current {@link #mOriginalConnection}.
     */
    private int mOriginalConnectionCapabilities;

    /// M: CC: DTMF request special handling @{
    // Stop DTMF when TelephonyConnection is disconnected
    protected boolean mDtmfRequestIsStarted = false;
    /// @}

    /**
     * Determines if the {@link TelephonyConnection} is using wifi.
     * This is used when {@link TelephonyConnection#updateConnectionProperties()} is called to
     * indicate whether a call has the {@link Connection#PROPERTY_WIFI} property.
     */
    private boolean mIsWifi;

    /**
     * Determines the audio quality is high for the {@link TelephonyConnection}.
     * This is used when {@link TelephonyConnection#updateConnectionProperties}} is called to
     * indicate whether a call has the {@link Connection#PROPERTY_HIGH_DEF_AUDIO} property.
     */
    private boolean mHasHighDefAudio;

    /**
     * Indicates that the connection should be treated as an emergency call because the
     * number dialed matches an internal list of emergency numbers. Does not guarantee whether
     * the network will treat the call as an emergency call.
     */
    private boolean mTreatAsEmergencyCall;

    /**
     * For video calls, indicates whether the outgoing video for the call can be paused using
     * the {@link android.telecom.VideoProfile#STATE_PAUSED} VideoState.
     */
    private boolean mIsVideoPauseSupported;

    /**
     * Indicates whether this connection supports being a part of a conference..
     */
    private boolean mIsConferenceSupported;

    /**
     * Indicates whether the carrier supports video conferencing; captures the current state of the
     * carrier config
     * {@link android.telephony.CarrierConfigManager#KEY_SUPPORT_VIDEO_CONFERENCE_CALL_BOOL}.
     */
    private boolean mIsCarrierVideoConferencingSupported;

    /**
     * Indicates whether or not this connection has CDMA Enhanced Voice Privacy enabled.
     */
    private boolean mIsCdmaVoicePrivacyEnabled;

    /**
     * Listeners to our TelephonyConnection specific callbacks
     */
    private final Set<TelephonyConnectionListener> mTelephonyListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<TelephonyConnectionListener, Boolean>(8, 0.9f, 1));

    protected TelephonyConnection(com.android.internal.telephony.Connection originalConnection,
            String callId) {
        /// M: CC: GSA HD Voice for 2/3G network support @{
        mSpeechType = SpeechCodecType.fromInt(0);
        /// @}
        setTelecomCallId(callId);
        if (originalConnection != null) {
            setOriginalConnection(originalConnection);
        }
    }

    /**
     * Creates a clone of the current {@link TelephonyConnection}.
     *
     * @return The clone.
     */
    public abstract TelephonyConnection cloneConnection();

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        // TODO: update TTY mode.
        if (getPhone() != null) {
            getPhone().setEchoSuppressionEnabled();
        }
    }

    @Override
    public void onStateChanged(int state) {
        Log.v(this, "onStateChanged, state: " + Connection.stateToString(state));
        updateStatusHints();
    }

    @Override
    public void onDisconnect() {
        Log.v(this, "onDisconnect");
        hangup(android.telephony.DisconnectCause.LOCAL);
    }

    /**
     * Notifies this Connection of a request to disconnect a participant of the conference managed
     * by the connection.
     *
     * @param endpoint the {@link Uri} of the participant to disconnect.
     */
    @Override
    public void onDisconnectConferenceParticipant(Uri endpoint) {
        Log.v(this, "onDisconnectConferenceParticipant %s", endpoint);

        if (mOriginalConnection == null) {
            return;
        }

        mOriginalConnection.onDisconnectConferenceParticipant(endpoint);
    }

    @Override
    public void onSeparate() {
        Log.v(this, "onSeparate");
        if (mOriginalConnection != null) {
            try {
                mOriginalConnection.separate();
            } catch (CallStateException e) {
                Log.e(this, e, "Call to Connection.separate failed with exception");
            }
        }
    }

    @Override
    public void onAbort() {
        Log.v(this, "onAbort");
        hangup(android.telephony.DisconnectCause.LOCAL);
    }

    @Override
    public void onHold() {
        performHold();
    }

    @Override
    public void onUnhold() {
        performUnhold();
    }

    @Override
    public void onAnswer(int videoState) {
        Log.v(this, "onAnswer");
        if (isValidRingingCall() && getPhone() != null) {
            try {
                getPhone().acceptCall(videoState);
            } catch (CallStateException e) {
                Log.e(this, e, "Failed to accept call.");
                /// M: Keep the pending action for SRVCC. @{
                if (e.getError() == CallStateException.ERROR_INVALID_DURING_SRVCC) {
                    mPendingAction = SrvccPendingAction.SRVCC_PENDING_ANSWER_CALL;
                }
                /// @}
            }
        }
    }

    @Override
    public void onReject() {
        Log.v(this, "onReject");
        if (isValidRingingCall()) {
            hangup(android.telephony.DisconnectCause.INCOMING_REJECTED);
        }
        super.onReject();
    }

    @Override
    public void onPostDialContinue(boolean proceed) {
        Log.v(this, "onPostDialContinue, proceed: " + proceed);
        if (mOriginalConnection != null) {
            if (proceed) {
                mOriginalConnection.proceedAfterWaitChar();
            } else {
                mOriginalConnection.cancelPostDial();
            }
        }
    }

    /// M: CC: Interface for ECT @{
    @Override
    public void onExplicitCallTransfer() {
        log("onExplicitCallTransfer");
        Phone phone = mOriginalConnection.getCall().getPhone();
        try {
            phone.explicitCallTransfer();
        } catch (CallStateException e) {
            Log.e(TAG, e, "Exception occurred while trying to do ECT.");
        }
    }

    // For IMS blind/assured ECT
    @Override
    public void onExplicitCallTransfer(String number, int type) {
        Log.v(this, "onExplicitCallTransfer");
        Phone phone = mOriginalConnection.getCall().getPhone();
        phone.explicitCallTransfer(number, type);
    }
    /// @}

    /// M: CC: HangupAll for FTA 31.4.4.2 @{
    @Override
    public void onHangupAll() {
        log("onHangupAll");
        if (mOriginalConnection != null) {
            try {
                Phone phone = getPhone();
                if (phone != null) {
                    phone.hangupAll();
                } else {
                    Log.w(TAG, "Attempting to hangupAll a connection without backing phone.");
                }
            } catch (CallStateException e) {
                Log.e(TAG, e, "Call to phone.hangupAll() failed with exception");
            }
        }
    }
    /// @}

    /// M: CC: TelephonyConnection destroy itself upon user hanging up [ALPS01850287] @{
    void onLocalDisconnected() {
        log("mOriginalConnection is null, local disconnect the call");
        /// M: CC: ECC Retry @{
        if (mTreatAsEmergencyCall) {
            if (TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
                log("ECC Retry : clear ECC param");
                TelephonyConnectionServiceUtil.getInstance().clearEccRetryParams();
            }
            /// M: CC: to check whether the device has on-going ECC
            TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
        }
        /// @}
        setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                android.telephony.DisconnectCause.LOCAL));
        close();
        updateConnectionCapabilities();
    }
    /// @}

    /**
     * Handles requests to pull an external call.
     */
    @Override
    public void onPullExternalCall() {
        if ((getConnectionProperties() & Connection.PROPERTY_IS_EXTERNAL_CALL) !=
                Connection.PROPERTY_IS_EXTERNAL_CALL) {
            Log.w(TAG, "onPullExternalCall - cannot pull non-external call");
            return;
        }

        if (mOriginalConnection != null) {
            mOriginalConnection.pullExternalCall();
        }
    }

    public void performHold() {
        Log.v(this, "performHold");
        // TODO: Can dialing calls be put on hold as well since they take up the
        // foreground call slot?
        if (Call.State.ACTIVE == mConnectionState) {
            Log.v(this, "Holding active call");
            try {
                Phone phone = mOriginalConnection.getCall().getPhone();
                Call ringingCall = phone.getRingingCall();

                // Although the method says switchHoldingAndActive, it eventually calls a RIL method
                // called switchWaitingOrHoldingAndActive. What this means is that if we try to put
                // a call on hold while a call-waiting call exists, it'll end up accepting the
                // call-waiting call, which is bad if that was not the user's intention. We are
                // cheating here and simply skipping it because we know any attempt to hold a call
                // while a call-waiting call is happening is likely a request from Telecom prior to
                // accepting the call-waiting call.
                // TODO: Investigate a better solution. It would be great here if we
                // could "fake" hold by silencing the audio and microphone streams for this call
                // instead of actually putting it on hold.
                if (ringingCall.getState() != Call.State.WAITING) {
                    phone.switchHoldingAndActive();
                }

                // TODO: Cdma calls are slightly different.
            } catch (CallStateException e) {
                Log.e(this, e, "Exception occurred while trying to put call on hold.");
                /// M: Keep the pending action for SRVCC. @{
                if (e.getError() == CallStateException.ERROR_INVALID_DURING_SRVCC) {
                    mPendingAction = SrvccPendingAction.SRVCC_PENDING_HOLD_CALL;
                }
                /// @}
            }
        } else {
            Log.w(this, "Cannot put a call that is not currently active on hold.");
        }
    }

    public void performUnhold() {
        Log.v(this, "performUnhold");
        if (Call.State.HOLDING == mConnectionState) {
            try {
                // Here's the deal--Telephony hold/unhold is weird because whenever there exists
                // more than one call, one of them must always be active. In other words, if you
                // have an active call and holding call, and you put the active call on hold, it
                // will automatically activate the holding call. This is weird with how Telecom
                // sends its commands. When a user opts to "unhold" a background call, telecom
                // issues hold commands to all active calls, and then the unhold command to the
                // background call. This means that we get two commands...each of which reduces to
                // switchHoldingAndActive(). The result is that they simply cancel each other out.
                // To fix this so that it works well with telecom we add a minor hack. If we
                // have one telephony call, everything works as normally expected. But if we have
                // two or more calls, we will ignore all requests to "unhold" knowing that the hold
                // requests already do what we want. If you've read up to this point, I'm very sorry
                // that we are doing this. I didn't think of a better solution that wouldn't also
                // make the Telecom APIs very ugly.

                if (!hasMultipleTopLevelCalls()) {
                    mOriginalConnection.getCall().getPhone().switchHoldingAndActive();
                } else {
                    /// M: ALPS02495477 @{
                    /// Swapping calls failed at resuming BG call will cause double holding
                    /// calls. Google design is not allowed to unhold for double holding calls.
                    /// We allow user to unhold call for error handling.
                    // Log.i(this, "Skipping unhold command for %s", this);
                    if (!hasTwoHoldingCall()) {
                        Log.i(this, "Skipping unhold command for %s", this);
                    }
                    /// @}
                }
            } catch (CallStateException e) {
                Log.e(this, e, "Exception occurred while trying to release call from hold.");
                /// M: Keep the pending action for SRVCC. @{
                if (e.getError() == CallStateException.ERROR_INVALID_DURING_SRVCC) {
                    mPendingAction = SrvccPendingAction.SRVCC_PENDING_UNHOLD_CALL;
                }
                /// @}
            }
        } else {
            Log.w(this, "Cannot release a call that is not already on hold from hold.");
        }
    }

    public void performConference(Connection otherConnection) {
        Log.d(this, "performConference - %s", this);
        if (getPhone() != null) {
            try {
                // We dont use the "other" connection because there is no concept of that in the
                // implementation of calls inside telephony. Basically, you can "conference" and it
                // will conference with the background call.  We know that otherConnection is the
                // background call because it would never have called setConferenceableConnections()
                // otherwise.
                getPhone().conference();
            } catch (CallStateException e) {
                Log.e(this, e, "Failed to conference call.");
            }
        }
    }

    /**
     * Builds connection capabilities common to all TelephonyConnections. Namely, apply IMS-based
     * capabilities.
     */
    protected int buildConnectionCapabilities() {
        int callCapabilities = 0;
        if (mOriginalConnection != null && mOriginalConnection.isIncoming()) {
            callCapabilities |= CAPABILITY_SPEED_UP_MT_AUDIO;
        }

        /// M: Note that AOSP supports 3GPP-CR C1-124200 (ECC can not be held) since N.
        if (!shouldTreatAsEmergencyCall() && isImsConnection() && canHoldImsCalls()) {
            callCapabilities |= CAPABILITY_SUPPORT_HOLD;
            if (getState() == STATE_ACTIVE || getState() == STATE_HOLDING) {
                callCapabilities |= CAPABILITY_HOLD;
            }
        }

        return callCapabilities;
    }

    protected final void updateConnectionCapabilities() {
        int newCapabilities = buildConnectionCapabilities();

        newCapabilities = applyOriginalConnectionCapabilities(newCapabilities);
        newCapabilities = changeBitmask(newCapabilities, CAPABILITY_CAN_PAUSE_VIDEO,
                mIsVideoPauseSupported && isVideoCapable());
        newCapabilities = changeBitmask(newCapabilities, CAPABILITY_CAN_PULL_CALL,
                isExternalConnection() && isPullable());
        newCapabilities = applyConferenceTerminationCapabilities(newCapabilities);

        if (getConnectionCapabilities() != newCapabilities) {
            setConnectionCapabilities(newCapabilities);
        }
    }

    protected int buildConnectionProperties() {
        int connectionProperties = 0;

        // If the phone is in ECM mode, mark the call to indicate that the callback number should be
        // shown.
        Phone phone = getPhone();
        if (phone != null && phone.isInEcm()) {
            connectionProperties |= PROPERTY_EMERGENCY_CALLBACK_MODE;
        }

        return connectionProperties;
    }

    /**
     * Updates the properties of the connection.
     */
    protected final void updateConnectionProperties() {
        int newProperties = buildConnectionProperties();

        newProperties = changeBitmask(newProperties, PROPERTY_HIGH_DEF_AUDIO,
                hasHighDefAudioProperty());
        newProperties = changeBitmask(newProperties, PROPERTY_WIFI, mIsWifi);
        newProperties = changeBitmask(newProperties, PROPERTY_IS_EXTERNAL_CALL,
                isExternalConnection());
        newProperties = changeBitmask(newProperties, PROPERTY_HAS_CDMA_VOICE_PRIVACY,
                mIsCdmaVoicePrivacyEnabled);
        /// M: For VoLTE @{
        newProperties = changeBitmask(newProperties, PROPERTY_VOLTE, isImsConnection());
        log("updateConnectionProperties: " + propertiesToString(newProperties));
        /// @}

        if (getConnectionProperties() != newProperties) {
            setConnectionProperties(newProperties);
        }
    }

    protected final void updateAddress() {
        updateConnectionCapabilities();
        updateConnectionProperties();
        if (mOriginalConnection != null) {
            Uri address = getAddressFromNumber(mOriginalConnection.getAddress());
            int presentation = mOriginalConnection.getNumberPresentation();
            if (!Objects.equals(address, getAddress()) ||
                    presentation != getAddressPresentation()) {
                Log.v(this, "updateAddress, address changed");
                if ((getConnectionProperties() & PROPERTY_IS_DOWNGRADED_CONFERENCE) != 0) {
                    address = null;
                }
                setAddress(address, presentation);
            }

            String name = filterCnapName(mOriginalConnection.getCnapName());
            int namePresentation = mOriginalConnection.getCnapNamePresentation();
            if (!Objects.equals(name, getCallerDisplayName()) ||
                    namePresentation != getCallerDisplayNamePresentation()) {
                Log.v(this, "updateAddress, caller display name changed");
                setCallerDisplayName(name, namePresentation);
            }

            if (PhoneNumberUtils.isEmergencyNumber(mOriginalConnection.getAddress())) {
                mTreatAsEmergencyCall = true;
            }

            // Changing the address of the connection can change whether it is an emergency call or
            // not, which can impact whether it can be part of a conference.
            refreshConferenceSupported();
        }
    }

    void onRemovedFromCallService() {
        // Subclass can override this to do cleanup.
    }

    void setOriginalConnection(com.android.internal.telephony.Connection originalConnection) {
        Log.v(this, "new TelephonyConnection, originalConnection: " + originalConnection);
        clearOriginalConnection();
        mOriginalConnectionExtras.clear();
        mOriginalConnection = originalConnection;
        mOriginalConnection.setTelecomCallId(getTelecomCallId());
        getPhone().registerForPreciseCallStateChanged(
                mHandler, MSG_PRECISE_CALL_STATE_CHANGED, null);
        getPhone().registerForHandoverStateChanged(
                mHandler, MSG_HANDOVER_STATE_CHANGED, null);
        getPhone().registerForRingbackTone(mHandler, MSG_RINGBACK_TONE, null);
        getPhone().registerForDisconnect(mHandler, MSG_DISCONNECT, null);
        getPhone().registerForSuppServiceNotification(mHandler, MSG_SUPP_SERVICE_NOTIFY, null);
        getPhone().registerForOnHoldTone(mHandler, MSG_ON_HOLD_TONE, null);
        getPhone().registerForInCallVoicePrivacyOn(mHandler, MSG_CDMA_VOICE_PRIVACY_ON, null);
        getPhone().registerForInCallVoicePrivacyOff(mHandler, MSG_CDMA_VOICE_PRIVACY_OFF, null);
        /// M: CC: Modem reset related handling
        getPhone().registerForRadioOffOrNotAvailable(
                mHandler, EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        /// M: CC: GSA HD Voice for 2/3G network support @{
        /// In order to align CS audio codec solution, we will register IMS connection to
        /// ImsPhone's default phone which is GSMPhone.
        if (getPhone() instanceof ImsPhone) {
            ((ImsPhone) getPhone()).getDefaultPhone().registerForSpeechCodecInfo(mHandler,
                    EVENT_SPEECH_CODEC_INFO, null);
        } else {
            getPhone().registerForSpeechCodecInfo(mHandler, EVENT_SPEECH_CODEC_INFO, null);
        }
        /// @}
        /// M: CC: For 3G VT only
        getPhone().registerForVtStatusInfo(mHandler, EVENT_VT_STATUS_INFO, null);
        /// M: For CDMA call accepted
        getPhone().registerForCdmaCallAccepted(mHandler, EVENT_CDMA_CALL_ACCEPTED, null);

        /// M: Makes SuppMessageManager register for ImsPhone
        registerSuppMessageManager(getPhone());

        mOriginalConnection.addPostDialListener(mPostDialListener);
        mOriginalConnection.addListener(mOriginalConnectionListener);

        // Set video state and capabilities
        setVideoState(mOriginalConnection.getVideoState());
        setOriginalConnectionCapabilities(mOriginalConnection.getConnectionCapabilities());
        setWifi(mOriginalConnection.isWifi());
        setVideoProvider(mOriginalConnection.getVideoProvider());
        setAudioQuality(mOriginalConnection.getAudioQuality());
        setTechnologyTypeExtra();

        // Post update of extras to the handler; extras are updated via the handler to ensure thread
        // safety. The Extras Bundle is cloned in case the original extras are modified while they
        // are being added to mOriginalConnectionExtras in updateExtras.
        Bundle connExtras = mOriginalConnection.getConnectionExtras();
            mHandler.obtainMessage(MSG_CONNECTION_EXTRAS_CHANGED, connExtras == null ? null :
                    new Bundle(connExtras)).sendToTarget();

        if (PhoneNumberUtils.isEmergencyNumber(mOriginalConnection.getAddress())) {
            mTreatAsEmergencyCall = true;
        }

        if (isImsConnection()) {
            mWasImsConnection = true;
        }
        mIsMultiParty = mOriginalConnection.isMultiparty();
        fireOnOriginalConnectionConfigured();

        Bundle extrasToPut = new Bundle();
        List<String> extrasToRemove = new ArrayList<>();
        if (mOriginalConnection.isActiveCallDisconnectedOnAnswer()) {
            extrasToPut.putBoolean(Connection.EXTRA_ANSWERING_DROPS_FG_CALL, true);
        } else {
            extrasToRemove.add(Connection.EXTRA_ANSWERING_DROPS_FG_CALL);
        }

        if (shouldSetDisableAddCallExtra()) {
            extrasToPut.putBoolean(Connection.EXTRA_DISABLE_ADD_CALL, true);
        } else {
            extrasToRemove.add(Connection.EXTRA_DISABLE_ADD_CALL);
        }
        putExtras(extrasToPut);
        removeExtras(extrasToRemove);

        // updateState can set mOriginalConnection to null if its state is DISCONNECTED, so this
        // should be executed *after* the above setters have run.
        updateState();
        if (mOriginalConnection == null) {
            Log.w(this, "original Connection was nulled out as part of setOriginalConnection. " +
                    originalConnection);
        }

        /// M: Fix Google JE issue @{
        // fireOnOriginalConnectionConfigured will use mOriginalConnection,
        // but updateState can set mOriginalConnection to null
        //fireOnOriginalConnectionConfigured();
        /// @}
    }

    /**
     * Filters the CNAP name to not include a list of names that are unhelpful to the user for
     * Caller ID purposes.
     */
    private String filterCnapName(final String cnapName) {
        if (cnapName == null) {
            return null;
        }
        PersistableBundle carrierConfig = getCarrierConfig();
        String[] filteredCnapNames = null;
        if (carrierConfig != null) {
            filteredCnapNames = carrierConfig.getStringArray(
                    CarrierConfigManager.FILTERED_CNAP_NAMES_STRING_ARRAY);
        }
        if (filteredCnapNames != null) {
            long cnapNameMatches = Arrays.asList(filteredCnapNames)
                    .stream()
                    .filter(filteredCnapName -> filteredCnapName.equals(cnapName.toUpperCase()))
                    .count();
            if (cnapNameMatches > 0) {
                Log.i(this, "filterCnapName: Filtered CNAP Name: " + cnapName);
                return "";
            }
        }
        return cnapName;
    }

    /**
     * Sets the EXTRA_CALL_TECHNOLOGY_TYPE extra on the connection to report back to Telecom.
     */
    private void setTechnologyTypeExtra() {
        if (getPhone() != null) {
            putExtra(TelecomManager.EXTRA_CALL_TECHNOLOGY_TYPE, getPhone().getPhoneType());
        }
    }

    private void refreshDisableAddCall() {
        if (shouldSetDisableAddCallExtra()) {
            putExtra(Connection.EXTRA_DISABLE_ADD_CALL, true);
        } else {
            removeExtras(Connection.EXTRA_DISABLE_ADD_CALL);
        }
    }

    private boolean shouldSetDisableAddCallExtra() {
        /// M: Fixed JE issue @{
        if (null == mOriginalConnection) {
            return false;
        }
        /// @}
        boolean carrierShouldAllowAddCall = mOriginalConnection.shouldAllowAddCallDuringVideoCall();
        if (carrierShouldAllowAddCall) {
            return false;
        }
        Phone phone = getPhone();
        if (phone == null) {
            return false;
        }
        boolean isCurrentVideoCall = false;
        boolean wasVideoCall = false;
        boolean isVowifiEnabled = false;
        if (phone instanceof ImsPhone) {
            ImsPhone imsPhone = (ImsPhone) phone;
            if (imsPhone.getForegroundCall() != null
                    && imsPhone.getForegroundCall().getImsCall() != null) {
                ImsCall call = imsPhone.getForegroundCall().getImsCall();
                isCurrentVideoCall = call.isVideoCall();
                wasVideoCall = call.wasVideoCall();
            }

            isVowifiEnabled = ImsUtil.isWfcEnabled(phone.getContext());
        }

        if (isCurrentVideoCall) {
            return true;
        } else if (wasVideoCall && mIsWifi && !isVowifiEnabled) {
            return true;
        }
        return false;
    }

    private boolean hasHighDefAudioProperty() {
        if (!mHasHighDefAudio) {
            return false;
        }

        boolean isVideoCall = VideoProfile.isVideo(getVideoState());

        PersistableBundle b = getCarrierConfig();
        boolean canWifiCallsBeHdAudio =
                b != null && b.getBoolean(CarrierConfigManager.KEY_WIFI_CALLS_CAN_BE_HD_AUDIO);
        boolean canVideoCallsBeHdAudio =
                b != null && b.getBoolean(CarrierConfigManager.KEY_VIDEO_CALLS_CAN_BE_HD_AUDIO);

        if (isVideoCall && !canVideoCallsBeHdAudio) {
            return false;
        }

        if (mIsWifi && !canWifiCallsBeHdAudio) {
            return false;
        }

        return true;
    }

    private boolean canHoldImsCalls() {
        PersistableBundle b = getCarrierConfig();
        // Return true if the CarrierConfig is unavailable
        return !doesDeviceRespectHoldCarrierConfig() || b == null ||
                b.getBoolean(CarrierConfigManager.KEY_ALLOW_HOLD_IN_IMS_CALL_BOOL);
    }

    private PersistableBundle getCarrierConfig() {
        Phone phone = getPhone();
        if (phone == null) {
            return null;
        }
        return PhoneGlobals.getInstance().getCarrierConfigForSubId(phone.getSubId());
    }

    /**
     * Determines if the device will respect the value of the
     * {@link CarrierConfigManager#KEY_ALLOW_HOLD_IN_IMS_CALL_BOOL} configuration option.
     *
     * @return {@code false} if the device always supports holding IMS calls, {@code true} if it
     *      will use {@link CarrierConfigManager#KEY_ALLOW_HOLD_IN_IMS_CALL_BOOL} to determine if
     *      hold is supported.
     */
    private boolean doesDeviceRespectHoldCarrierConfig() {
        Phone phone = getPhone();
        if (phone == null) {
            return true;
        }
        return phone.getContext().getResources().getBoolean(
                com.android.internal.R.bool.config_device_respects_hold_carrier_config);
    }

    /**
     * Whether the connection should be treated as an emergency.
     * @return {@code true} if the connection should be treated as an emergency call based
     * on the number dialed, {@code false} otherwise.
     */
    protected boolean shouldTreatAsEmergencyCall() {
        return mTreatAsEmergencyCall;
    }

    /// M: Reset the emergency call flag for ECC retry. @{
    protected void resetTreatAsEmergencyCall() {
        mTreatAsEmergencyCall = false;
    }
    /// @}

    /**
     * Un-sets the underlying radio connection.
     */
    void clearOriginalConnection() {
        if (mOriginalConnection != null) {
            if (getPhone() != null) {
                /// M: CC: DTMF request special handling @{
                // Stop DTMF when TelephonyConnection is disconnected
                if (mDtmfRequestIsStarted) {
                    onStopDtmfTone();
                    mDtmfRequestIsStarted = false;
                }
                /// @}
                getPhone().unregisterForPreciseCallStateChanged(mHandler);
                getPhone().unregisterForRingbackTone(mHandler);
                getPhone().unregisterForHandoverStateChanged(mHandler);
                getPhone().unregisterForDisconnect(mHandler);
                getPhone().unregisterForSuppServiceNotification(mHandler);
                getPhone().unregisterForOnHoldTone(mHandler);
                getPhone().unregisterForInCallVoicePrivacyOn(mHandler);
                getPhone().unregisterForInCallVoicePrivacyOff(mHandler);
                /// M: CC: Modem reset related handling
                getPhone().unregisterForRadioOffOrNotAvailable(mHandler);
                /// M: CC: GSA HD Voice for 2/3G network support @{
                if (getPhone() instanceof ImsPhone) {
                    ((ImsPhone) getPhone()).getDefaultPhone()
                            .unregisterForSpeechCodecInfo(mHandler);
                } else {
                    getPhone().unregisterForSpeechCodecInfo(mHandler);
                }
                /// @}
                /// M: CC: For 3G VT only
                getPhone().unregisterForVtStatusInfo(mHandler);
                /// M: For CDMA call accepted
                getPhone().unregisterForCdmaCallAccepted(mHandler);
                /// M: Makes SuppMessageManager unregister for ImsPhone.
                unregisterSuppMessageManager(getPhone());
            }
            mOriginalConnection.removePostDialListener(mPostDialListener);
            mOriginalConnection.removeListener(mOriginalConnectionListener);
            mOriginalConnection = null;
        }
    }

    /// M: CC: Note: The telephonyDisconnectCode is not used here.
    protected void hangup(int telephonyDisconnectCode) {
        /// M: CC: ECC Retry @{
        mIsLocallyDisconnecting = true;
        /// @}
        if (mOriginalConnection != null) {
            try {
                // Hanging up a ringing call requires that we invoke call.hangup() as opposed to
                // connection.hangup(). Without this change, the party originating the call will not
                // get sent to voicemail if the user opts to reject the call.
                if (isValidRingingCall()) {
                    Call call = getCall();
                    if (call != null) {
                        call.hangup();
                    } else {
                        Log.w(this, "Attempting to hangup a connection without backing call.");
                    }
                } else {
                    // We still prefer to call connection.hangup() for non-ringing calls in order
                    // to support hanging-up specific calls within a conference call. If we invoked
                    // call.hangup() while in a conference, we would end up hanging up the entire
                    // conference call instead of the specific connection.
                    mOriginalConnection.hangup();
                }
            } catch (CallStateException e) {
                Log.e(this, e, "Call to Connection.hangup failed with exception");
                /// M: Keep the pending action for SRVCC. @{
                mPendingAction = SrvccPendingAction.SRVCC_PENDING_HANGUP_CALL;
                /// M: CC: ECC Retry
                mIsLocallyDisconnecting = false;
                /// @}
            }
        } else {
            /// M: CC: ECC Retry @{
            if (mTreatAsEmergencyCall) {
                if (TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
                    Log.d(this, "ECC Retry : clear ECC param");
                    TelephonyConnectionServiceUtil.getInstance().clearEccRetryParams();
                }
                /// M: CC: to check whether the device has on-going ECC
                TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
            }
            /// @}
            if (getState() == STATE_DISCONNECTED) {
                Log.i(this, "hangup called on an already disconnected call!");
                close();
            } else {
                // There are a few cases where mOriginalConnection has not been set yet. For
                // example, when the radio has to be turned on to make an emergency call,
                // mOriginalConnection could not be set for many seconds.
                setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                        android.telephony.DisconnectCause.LOCAL,
                        "Local Disconnect before connection established."));
                close();
            }
        }
    }

    /// M: CC: Proprietary CRSS handling @{
    // Declare as public for SuppMessageManager to use.
    public com.android.internal.telephony.Connection getOriginalConnection() {
    /// @}
        return mOriginalConnection;
    }

    protected Call getCall() {
        if (mOriginalConnection != null) {
            return mOriginalConnection.getCall();
        }
        return null;
    }

    Phone getPhone() {
        Call call = getCall();
        if (call != null) {
            return call.getPhone();
        }
        return null;
    }

    private boolean hasMultipleTopLevelCalls() {
        int numCalls = 0;
        Phone phone = getPhone();
        if (phone != null) {
            if (!phone.getRingingCall().isIdle()) {
                numCalls++;
            }
            if (!phone.getForegroundCall().isIdle()) {
                numCalls++;
            }
            if (!phone.getBackgroundCall().isIdle()) {
                numCalls++;
            }
        }
        return numCalls > 1;
    }

    private com.android.internal.telephony.Connection getForegroundConnection() {
        if (getPhone() != null) {
            return getPhone().getForegroundCall().getEarliestConnection();
        }
        return null;
    }

     /**
     * Checks for and returns the list of conference participants
     * associated with this connection.
     */
    public List<ConferenceParticipant> getConferenceParticipants() {
        if (mOriginalConnection == null) {
            Log.v(this, "Null mOriginalConnection, cannot get conf participants.");
            return null;
        }
        return mOriginalConnection.getConferenceParticipants();
    }

    /**
     * Checks to see the original connection corresponds to an active incoming call. Returns false
     * if there is no such actual call, or if the associated call is not incoming (See
     * {@link Call.State#isRinging}).
     */
    private boolean isValidRingingCall() {
        if (getPhone() == null) {
            Log.v(this, "isValidRingingCall, phone is null");
            return false;
        }

        Call ringingCall = getPhone().getRingingCall();
        if (!ringingCall.getState().isRinging()) {
            Log.v(this, "isValidRingingCall, ringing call is not in ringing state");
            return false;
        }

        if (ringingCall.getEarliestConnection() != mOriginalConnection) {
            Log.v(this, "isValidRingingCall, ringing call connection does not match");
            return false;
        }

        Log.v(this, "isValidRingingCall, returning true");
        return true;
    }

    /// M: CC: Force updateState for Connection once its ConnectionService is set @{
    @Override
    protected void fireOnCallState() {
        updateState();
    }
    /// @}

    // Make sure the extras being passed into this method is a COPY of the original extras Bundle.
    // We do not want the extras to be cleared or modified during mOriginalConnectionExtras.putAll
    // below.
    protected void updateExtras(Bundle extras) {
        if (mOriginalConnection != null) {
            if (extras != null) {
                // Check if extras have changed and need updating.
                if (!areBundlesEqual(mOriginalConnectionExtras, extras)) {
                    if (Log.DEBUG) {
                        Log.d(TelephonyConnection.this, "Updating extras:");
                        for (String key : extras.keySet()) {
                            Object value = extras.get(key);
                            if (value instanceof String) {
                                Log.d(this, "updateExtras Key=" + Log.pii(key) +
                                             " value=" + Log.pii((String)value));
                            }
                        }
                    }
                    mOriginalConnectionExtras.clear();

                    mOriginalConnectionExtras.putAll(extras);

                    // Remap any string extras that have a remapping defined.
                    for (String key : mOriginalConnectionExtras.keySet()) {
                        if (sExtrasMap.containsKey(key)) {
                            String newKey = sExtrasMap.get(key);
                            mOriginalConnectionExtras.putString(newKey, extras.getString(key));
                            mOriginalConnectionExtras.remove(key);
                        }
                    }

                    // Ensure extras are propagated to Telecom.
                    putExtras(mOriginalConnectionExtras);
                } else {
                    Log.d(this, "Extras update not required");
                }
            } else {
                Log.d(this, "updateExtras extras: " + Log.pii(extras));
            }
        }
    }

    private static boolean areBundlesEqual(Bundle extras, Bundle newExtras) {
        if (extras == null || newExtras == null) {
            return extras == newExtras;
        }

        if (extras.size() != newExtras.size()) {
            return false;
        }

        for(String key : extras.keySet()) {
            if (key != null) {
                final Object value = extras.get(key);
                final Object newValue = newExtras.get(key);
                if (!Objects.equals(value, newValue)) {
                    return false;
                }
            }
        }
        return true;
    }

    void setStateOverride(Call.State state) {
        mIsStateOverridden = true;
        mConnectionOverriddenState = state;
        // Need to keep track of the original connection's state before override.
        mOriginalConnectionState = mOriginalConnection.getState();
        updateStateInternal();
    }

    void resetStateOverride() {
        mIsStateOverridden = false;
        updateStateInternal();
    }

    void updateStateInternal() {
        if (mOriginalConnection == null) {
            return;
        }

        Call.State newState;
        // If the state is overridden and the state of the original connection hasn't changed since,
        // then we continue in the overridden state, else we go to the original connection's state.
        if (mIsStateOverridden && mOriginalConnectionState == mOriginalConnection.getState()) {
            newState = mConnectionOverriddenState;
        } else {
            newState = mOriginalConnection.getState();
        }

        // Don't retry if treated as normal call in Telephony Framework
        final boolean isDialedByEmergencyCommand = PhoneNumberUtils.isEmergencyNumber(
                mOriginalConnection.getAddress());

        /// M: CC: ECC Retry @{
        if (mTreatAsEmergencyCall
                && !mIsLocallyDisconnecting
                && mOriginalConnection.getState() == Call.State.DISCONNECTED
                && (mConnectionState.isDialing()
                    || mConnectionState == Call.State.IDLE)
                && isDialedByEmergencyCommand) {
            int cause = mOriginalConnection.getDisconnectCause();
            log("ECC remote DISCONNECTED, state=" + mConnectionState + ", cause=" + cause);

            // Assume only one ECC exists
            if (cause != android.telephony.DisconnectCause.NORMAL
                    && cause != android.telephony.DisconnectCause.LOCAL
                    && cause != android.telephony.DisconnectCause.LOST_SIGNAL
                    && cause != android.telephony.DisconnectCause.NOT_DISCONNECTED
                    && TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()
                    && !TelephonyConnectionServiceUtil.getInstance().eccRetryTimeout()) {
                newState = mConnectionState;
                log("ECC Retry : Meet retry condition, keep state=" + newState);
            }
        }
        /// @}

        Log.v(this, "Update state from %s to %s for %s", mConnectionState, newState, this);

        if (mConnectionState != newState) {
            mConnectionState = newState;
            /// M: CC: GSA HD Voice for 2/3G network support @{
            // Force update when call state is changed to DIALING/ACTIVE/INCOMING(Foreground)
            switch (newState) {
                case ACTIVE:
                case DIALING:
                case ALERTING:
                case INCOMING:
                case WAITING:
                    if (isHighDefAudio(0)) {
                        setAudioQuality(com.android.internal.telephony.Connection.
                                AUDIO_QUALITY_HIGH_DEFINITION);
                    } else {
                        setAudioQuality(com.android.internal.telephony.Connection.
                                AUDIO_QUALITY_STANDARD);
                    }
                    break;
                default:
                    break;
            }
            /// @}

            switch (newState) {
                case IDLE:
                    break;
                case ACTIVE:
                    /// M: CC: ECC Retry @{
                    // Assume only one ECC exists
                    if (mTreatAsEmergencyCall
                            && TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
                        log("ECC Retry : clear ECC param");
                        TelephonyConnectionServiceUtil.getInstance().clearEccRetryParams();
                    }
                    /// @}
                    setActiveInternal();
                    break;
                case HOLDING:
                    setOnHold();
                    break;
                case DIALING:
                case ALERTING:
                    if (mOriginalConnection != null && mOriginalConnection.isPulledCall()) {
                        setPulling();
                    } else {
                        setDialing();
                    }
                    break;
                case INCOMING:
                case WAITING:
                    setRinging();
                    break;
                case DISCONNECTED:
                    /// M: CC: ECC Retry @{
                    // Assume only one ECC exists
                    if (mTreatAsEmergencyCall) {
                        if (TelephonyConnectionServiceUtil.getInstance().isEccRetryOn()) {
                            log("ECC Retry : clear ECC param");
                            TelephonyConnectionServiceUtil.getInstance().clearEccRetryParams();
                        }
                        /// M: CC: to check whether the device has on-going ECC
                        TelephonyConnectionServiceUtil.getInstance().setInEcc(false);
                    }
                    /// @}
                    // We can get into a situation where the radio wants us to redial the same
                    // emergency call on the other available slot. This will not set the state to
                    // disconnected and will instead tell the TelephonyConnectionService to create
                    // a new originalConnection using the new Slot.
                    if (mOriginalConnection.getDisconnectCause() ==
                            DisconnectCause.DIALED_ON_WRONG_SLOT) {
                        fireOnOriginalConnectionRetryDial();
                    } else {
                        setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                                mOriginalConnection.getDisconnectCause(),
                                mOriginalConnection.getVendorDisconnectCause()));
                        close();
                    }
                    break;
                case DISCONNECTING:
                    /// M: CC: ECC Retry @{
                    mIsLocallyDisconnecting = true;
                    /// @}
                    break;
            }
        }
    }

    void updateState() {
        if (mOriginalConnection == null) {
            return;
        }

        updateStateInternal();
        updateStatusHints();
        /// M: @{
        // Remove duplicate updateConnectionCapabilities(), for it's called in updateAddress()
        //updateConnectionCapabilities();
        //updateConnectionProperties();
        /// @}
        updateAddress();
        updateMultiparty();
    }

    /**
     * Checks for changes to the multiparty bit.  If a conference has started, informs listeners.
     */
    private void updateMultiparty() {
        if (mOriginalConnection == null) {
            return;
        }

        if (mIsMultiParty != mOriginalConnection.isMultiparty()) {
            mIsMultiParty = mOriginalConnection.isMultiparty();

            if (mIsMultiParty) {
                notifyConferenceStarted();
            }
        }
    }

    /**
     * Handles a failure when merging calls into a conference.
     * {@link com.android.internal.telephony.Connection.Listener#onConferenceMergedFailed()}
     * listener.
     */
    private void handleConferenceMergeFailed(){
        mHandler.obtainMessage(MSG_CONFERENCE_MERGE_FAILED).sendToTarget();
    }

    /**
     * Handles requests to update the multiparty state received via the
     * {@link com.android.internal.telephony.Connection.Listener#onMultipartyStateChanged(boolean)}
     * listener.
     * <p>
     * Note: We post this to the mHandler to ensure that if a conference must be created as a
     * result of the multiparty state change, the conference creation happens on the correct
     * thread.  This ensures that the thread check in
     * {@link com.android.internal.telephony.Phone#checkCorrectThread(android.os.Handler)}
     * does not fire.
     *
     * @param isMultiParty {@code true} if this connection is multiparty, {@code false} otherwise.
     */
    private void handleMultipartyStateChange(boolean isMultiParty) {
        Log.i(this, "Update multiparty state to %s", isMultiParty ? "Y" : "N");
        mHandler.obtainMessage(MSG_MULTIPARTY_STATE_CHANGED, isMultiParty).sendToTarget();
    }

    private void setActiveInternal() {
        if (getState() == STATE_ACTIVE) {
            Log.w(this, "Should not be called if this is already ACTIVE");
            return;
        }

        // When we set a call to active, we need to make sure that there are no other active
        // calls. However, the ordering of state updates to connections can be non-deterministic
        // since all connections register for state changes on the phone independently.
        // To "optimize", we check here to see if there already exists any active calls.  If so,
        // we issue an update for those calls first to make sure we only have one top-level
        // active call.
        if (getConnectionService() != null) {
            for (Connection current : getConnectionService().getAllConnections()) {
                if (current != this && current instanceof TelephonyConnection) {
                    TelephonyConnection other = (TelephonyConnection) current;
                    if (other.getState() == STATE_ACTIVE) {
                        other.updateState();
                    }
                }
            }
        }

        /// M: Add flag to indicate if the CDMA call is fake dialing @{
        // If the cdma connection is in FORCE DIALING status,
        // not update the state to ACTIVE, this will be done
        // after stop the FORCE DIALING
        if (isForceDialing()) {
            return;
        }
        /// @}
        setActive();
    }

    private void close() {
        Log.v(this, "close");
        clearOriginalConnection();
        destroy();
    }

    /**
     * Determines if the current connection is video capable.
     *
     * A connection is deemed to be video capable if the original connection capabilities state that
     * both local and remote video is supported.
     *
     * @return {@code true} if the connection is video capable, {@code false} otherwise.
     */
    private boolean isVideoCapable() {
        return can(mOriginalConnectionCapabilities, Capability.SUPPORTS_VT_LOCAL_BIDIRECTIONAL)
                && can(mOriginalConnectionCapabilities,
                Capability.SUPPORTS_VT_REMOTE_BIDIRECTIONAL);
    }

    /**
     * Determines if the current connection is an external connection.
     *
     * A connection is deemed to be external if the original connection capabilities state that it
     * is.
     *
     * @return {@code true} if the connection is external, {@code false} otherwise.
     */
    private boolean isExternalConnection() {
        return can(mOriginalConnectionCapabilities, Capability.IS_EXTERNAL_CONNECTION)
                && can(mOriginalConnectionCapabilities,
                Capability.IS_EXTERNAL_CONNECTION);
    }

    /**
     * Determines if the current connection is pullable.
     *
     * A connection is deemed to be pullable if the original connection capabilities state that it
     * is.
     *
     * @return {@code true} if the connection is pullable, {@code false} otherwise.
     */
    private boolean isPullable() {
        return can(mOriginalConnectionCapabilities, Capability.IS_EXTERNAL_CONNECTION)
                && can(mOriginalConnectionCapabilities, Capability.IS_PULLABLE);
    }

    /**
     * Sets whether or not CDMA enhanced call privacy is enabled for this connection.
     */
    private void setCdmaVoicePrivacy(boolean isEnabled) {
        if(mIsCdmaVoicePrivacyEnabled != isEnabled) {
            mIsCdmaVoicePrivacyEnabled = isEnabled;
            updateConnectionProperties();
        }
    }

    /**
     * Applies capabilities specific to conferences termination to the
     * {@code ConnectionCapabilities} bit-mask.
     *
     * @param capabilities The {@code ConnectionCapabilities} bit-mask.
     * @return The capabilities with the IMS conference capabilities applied.
     */
    private int applyConferenceTerminationCapabilities(int capabilities) {
        int currentCapabilities = capabilities;

        // An IMS call cannot be individually disconnected or separated from its parent conference.
        // If the call was IMS, even if it hands over to GMS, these capabilities are not supported.
        /// M: CC Fix Google bug. Check IMS current status for CS conference call @{
        //if (!mWasImsConnection) {
        if (!isImsConnection()) {
        /// @}
            currentCapabilities |= CAPABILITY_DISCONNECT_FROM_CONFERENCE;
            currentCapabilities |= CAPABILITY_SEPARATE_FROM_CONFERENCE;
        }

        return currentCapabilities;
    }

    /**
     * Stores the new original connection capabilities, and applies them to the current connection,
     * notifying any listeners as necessary.
     *
     * @param connectionCapabilities The original connection capabilties.
     */
    public void setOriginalConnectionCapabilities(int connectionCapabilities) {
        mOriginalConnectionCapabilities = connectionCapabilities;
        updateConnectionCapabilities();
        updateConnectionProperties();
    }

    /**
     * Called to apply the capabilities present in the {@link #mOriginalConnection} to this
     * {@link Connection}.  Provides a mapping between the capabilities present in the original
     * connection (see {@link com.android.internal.telephony.Connection.Capability}) and those in
     * this {@link Connection}.
     *
     * @param capabilities The capabilities bitmask from the {@link Connection}.
     * @return the capabilities bitmask with the original connection capabilities remapped and
     *      applied.
     */
    public int applyOriginalConnectionCapabilities(int capabilities) {
        // We only support downgrading to audio if both the remote and local side support
        // downgrading to audio.
        boolean supportsDowngradeToAudio = can(mOriginalConnectionCapabilities,
                Capability.SUPPORTS_DOWNGRADE_TO_VOICE_LOCAL |
                        Capability.SUPPORTS_DOWNGRADE_TO_VOICE_REMOTE);
        capabilities = changeBitmask(capabilities,
                CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO, !supportsDowngradeToAudio);

        capabilities = changeBitmask(capabilities, CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL,
                can(mOriginalConnectionCapabilities, Capability.SUPPORTS_VT_REMOTE_BIDIRECTIONAL));

        capabilities = changeBitmask(capabilities, CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL,
                can(mOriginalConnectionCapabilities, Capability.SUPPORTS_VT_LOCAL_BIDIRECTIONAL));

        /// M: Video ringtone @{
        boolean supportVideoRingtone = can(mOriginalConnectionCapabilities,
                Capability.SUPPORTS_VT_RINGTONE);
        capabilities = changeBitmask(capabilities,
                CAPABILITY_VIDEO_RINGTONE, supportVideoRingtone);
        Log.d(this, "supportVideoRingtone: " + supportVideoRingtone);
        /// @}

        return capabilities;
    }

    /**
     * Sets whether the call is using wifi. Used when rebuilding the capabilities to set or unset
     * the {@link Connection#PROPERTY_WIFI} property.
     */
    public void setWifi(boolean isWifi) {
        mIsWifi = isWifi;
        updateConnectionProperties();
        updateStatusHints();
        refreshDisableAddCall();
    }

    /**
     * Whether the call is using wifi.
     */
    boolean isWifi() {
        return mIsWifi;
    }

    /**
     * Sets the current call audio quality. Used during rebuild of the properties
     * to set or unset the {@link Connection#PROPERTY_HIGH_DEF_AUDIO} property.
     *
     * @param audioQuality The audio quality.
     */
    public void setAudioQuality(int audioQuality) {
        mHasHighDefAudio = audioQuality ==
                com.android.internal.telephony.Connection.AUDIO_QUALITY_HIGH_DEFINITION;
        updateConnectionProperties();
    }

    void resetStateForConference() {
        if (getState() == Connection.STATE_HOLDING) {
            resetStateOverride();
        }
    }

    boolean setHoldingForConference() {
        if (getState() == Connection.STATE_ACTIVE) {
            setStateOverride(Call.State.HOLDING);
            return true;
        }
        return false;
    }

    /**
     * For video calls, sets whether this connection supports pausing the outgoing video for the
     * call using the {@link android.telecom.VideoProfile#STATE_PAUSED} VideoState.
     *
     * @param isVideoPauseSupported {@code true} if pause state supported, {@code false} otherwise.
     */
    public void setVideoPauseSupported(boolean isVideoPauseSupported) {
        mIsVideoPauseSupported = isVideoPauseSupported;
    }

    /**
     * Sets whether this connection supports conference calling.
     * @param isConferenceSupported {@code true} if conference calling is supported by this
     *                                         connection, {@code false} otherwise.
     */
    public void setConferenceSupported(boolean isConferenceSupported) {
        mIsConferenceSupported = isConferenceSupported;
    }

    /**
     * @return {@code true} if this connection supports merging calls into a conference.
     */
    public boolean isConferenceSupported() {
        return mIsConferenceSupported;
    }

    /**
     * Whether the original connection is an IMS connection.
     * @return {@code True} if the original connection is an IMS connection, {@code false}
     *     otherwise.
     */
    protected boolean isImsConnection() {
        com.android.internal.telephony.Connection originalConnection = getOriginalConnection();
        return originalConnection != null &&
                originalConnection.getPhoneType() == PhoneConstants.PHONE_TYPE_IMS;
    }

    /**
     * Whether the original connection was ever an IMS connection, either before or now.
     * @return {@code True} if the original connection was ever an IMS connection, {@code false}
     *     otherwise.
     */
    public boolean wasImsConnection() {
        return mWasImsConnection;
    }

    private static Uri getAddressFromNumber(String number) {
        // Address can be null for blocked calls.
        if (number == null) {
            number = "";
        }
        return Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
    }

    /**
     * Changes a capabilities bit-mask to add or remove a capability.
     *
     * @param bitmask The bit-mask.
     * @param bitfield The bit-field to change.
     * @param enabled Whether the bit-field should be set or removed.
     * @return The bit-mask with the bit-field changed.
     */
    private int changeBitmask(int bitmask, int bitfield, boolean enabled) {
        if (enabled) {
            return bitmask | bitfield;
        } else {
            return bitmask & ~bitfield;
        }
    }

    private void updateStatusHints() {
        boolean isIncoming = isValidRingingCall();
        if (mIsWifi && (isIncoming || getState() == STATE_ACTIVE)) {
            int labelId = isIncoming
                    ? R.string.status_hint_label_incoming_wifi_call
                    : R.string.status_hint_label_wifi_call;

            Context context = getPhone().getContext();
            setStatusHints(new StatusHints(
                    context.getString(labelId),
                    Icon.createWithResource(
                            context.getResources(),
                            R.drawable.ic_signal_wifi_4_bar_24dp),
                    null /* extras */));
        } else {
            setStatusHints(null);
        }
    }

    /**
     * Register a listener for {@link TelephonyConnection} specific triggers.
     * @param l The instance of the listener to add
     * @return The connection being listened to
     */
    public final TelephonyConnection addTelephonyConnectionListener(TelephonyConnectionListener l) {
        mTelephonyListeners.add(l);
        // If we already have an original connection, let's call back immediately.
        // This would be the case for incoming calls.
        if (mOriginalConnection != null) {
            fireOnOriginalConnectionConfigured();
        }
        return this;
    }

    /**
     * Remove a listener for {@link TelephonyConnection} specific triggers.
     * @param l The instance of the listener to remove
     * @return The connection being listened to
     */
    public final TelephonyConnection removeTelephonyConnectionListener(
            TelephonyConnectionListener l) {
        if (l != null) {
            mTelephonyListeners.remove(l);
        }
        return this;
    }

    /**
     * Fire a callback to the various listeners for when the original connection is
     * set in this {@link TelephonyConnection}
     */
    private final void fireOnOriginalConnectionConfigured() {
        for (TelephonyConnectionListener l : mTelephonyListeners) {
            l.onOriginalConnectionConfigured(this);
        }
    }

    private final void fireOnOriginalConnectionRetryDial() {
        for (TelephonyConnectionListener l : mTelephonyListeners) {
            l.onOriginalConnectionRetry(this);
        }
    }

    /**
     * Handles exiting ECM mode.
     */
    protected void handleExitedEcmMode() {
        updateConnectionProperties();
    }

    /**
     * Determines whether the connection supports conference calling.  A connection supports
     * conference calling if it:
     * 1. Is not an emergency call.
     * 2. Carrier supports conference calls.
     * 3. If call is a video call, carrier supports video conference calls.
     * 4. If call is a wifi call and VoWIFI is disabled and carrier supports merging these calls.
     */
    private void refreshConferenceSupported() {
        boolean isVideoCall = VideoProfile.isVideo(getVideoState());
        Phone phone = getPhone();

        if (phone == null) {
            return;
        }

        boolean isIms = phone.getPhoneType() == PhoneConstants.PHONE_TYPE_IMS;
        boolean isVoWifiEnabled = false;
        if (isIms) {
            ImsPhone imsPhone = (ImsPhone) phone;
            isVoWifiEnabled = imsPhone.isWifiCallingEnabled();
        }
        PhoneAccountHandle phoneAccountHandle = isIms ? PhoneUtils
                .makePstnPhoneAccountHandle(phone.getDefaultPhone())
                : PhoneUtils.makePstnPhoneAccountHandle(phone);
        TelecomAccountRegistry telecomAccountRegistry = TelecomAccountRegistry
                .getInstance(getPhone().getContext());
        boolean isConferencingSupported = telecomAccountRegistry
                .isMergeCallSupported(phoneAccountHandle);
        mIsCarrierVideoConferencingSupported = telecomAccountRegistry
                .isVideoConferencingSupported(phoneAccountHandle);
        boolean isMergeOfWifiCallsAllowedWhenVoWifiOff = telecomAccountRegistry
                .isMergeOfWifiCallsAllowedWhenVoWifiOff(phoneAccountHandle);

        Log.v(this, "refreshConferenceSupported : isConfSupp=%b, isVidConfSupp=%b, " +
                "isMergeOfWifiAllowed=%b, isWifi=%b, isVoWifiEnabled=%b", isConferencingSupported,
                mIsCarrierVideoConferencingSupported, isMergeOfWifiCallsAllowedWhenVoWifiOff,
                isWifi(), isVoWifiEnabled);
        boolean isConferenceSupported = true;
        if (mTreatAsEmergencyCall) {
            isConferenceSupported = false;
            Log.d(this, "refreshConferenceSupported = false; emergency call");
        } else if (!isConferencingSupported) {
            isConferenceSupported = false;
            Log.d(this, "refreshConferenceSupported = false; carrier doesn't support conf.");
        } else if (isVideoCall && !mIsCarrierVideoConferencingSupported) {
            isConferenceSupported = false;
            Log.d(this, "refreshConferenceSupported = false; video conf not supported.");
        } else if (!isMergeOfWifiCallsAllowedWhenVoWifiOff && isWifi() && !isVoWifiEnabled) {
            isConferenceSupported = false;
            Log.d(this,
                    "refreshConferenceSupported = false; can't merge wifi calls when voWifi off.");
        } else {
            Log.d(this, "refreshConferenceSupported = true.");
        }

        if (isConferenceSupported != isConferenceSupported()) {
            setConferenceSupported(isConferenceSupported);
            notifyConferenceSupportedChanged(isConferenceSupported);
        }
    }
    /**
     * Provides a mapping from extras keys which may be found in the
     * {@link com.android.internal.telephony.Connection} to their equivalents defined in
     * {@link android.telecom.Connection}.
     *
     * @return Map containing key mappings.
     */
    private static Map<String, String> createExtrasMap() {
        Map<String, String> result = new HashMap<String, String>();
        result.put(ImsCallProfile.EXTRA_CHILD_NUMBER,
                android.telecom.Connection.EXTRA_CHILD_ADDRESS);
        result.put(ImsCallProfile.EXTRA_DISPLAY_TEXT,
                android.telecom.Connection.EXTRA_CALL_SUBJECT);
        return Collections.unmodifiableMap(result);
    }

    /**
     * Creates a string representation of this {@link TelephonyConnection}.  Primarily intended for
     * use in log statements.
     *
     * @return String representation of the connection.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[TelephonyConnection objId:");
        sb.append(System.identityHashCode(this));
        sb.append(" telecomCallID:");
        sb.append(getTelecomCallId());
        sb.append(" type:");
        if (isImsConnection()) {
            sb.append("ims");
        /// M: CC: Vzw/CTVolte ECC @{
        //} else if (this instanceof com.android.services.telephony.GsmConnection) {
        } else if (this instanceof com.android.services.telephony.GsmCdmaConnection &&
                ((GsmCdmaConnection) this).getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
            sb.append("gsm");
         /// M: CC: Vzw/CTVolte ECC @{
        //} else if (this instanceof CdmaConnection) {
        } else if (this instanceof com.android.services.telephony.GsmCdmaConnection &&
                ((GsmCdmaConnection) this).getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            sb.append("cdma");
        }
        sb.append(" state:");
        sb.append(Connection.stateToString(getState()));
        sb.append(" capabilities:");
        sb.append(capabilitiesToString(getConnectionCapabilities()));
        sb.append(" properties:");
        sb.append(propertiesToString(getConnectionProperties()));
        sb.append(" address:");
        sb.append(Rlog.pii(SDBG, getAddress()));
        sb.append(" originalConnection:");
        sb.append(mOriginalConnection);
        sb.append(" partOfConf:");
        if (getConference() == null) {
            sb.append("N");
        } else {
            sb.append("Y");
        }
        sb.append(" confSupported:");
        sb.append(mIsConferenceSupported ? "Y" : "N");
        sb.append("]");
        return sb.toString();
    }

    /// M: @{
    /**
     * Notify this connection is ECC.
     */
    public void notifyEcc() {
        Bundle bundleECC = new Bundle();
        bundleECC.putBoolean(TelecomManagerEx.EXTRA_VOLTE_MARKED_AS_EMERGENCY, true);
        setCallInfo(bundleECC);
    }
    /// @}

    /// M: For VoLTE enhanced conference call. @{
    void performInviteConferenceParticipants(List<String> numbers) {
        if (mOriginalConnection == null) {
            Log.e(TAG, new CallStateException(), "no orginal connection to inviteParticipants");
            return;
        }

        if (!isImsConnection()) {
            Log.e(TAG, new CallStateException(), "CS connection doesn't support invite!");
            return;
        }

        ((ImsPhoneConnection) mOriginalConnection).inviteConferenceParticipants(numbers);
    }

    /**
     * This function used to notify the onInviteConferenceParticipants() operation is done.
     * @param isSuccess is success or not
     * @hide
     */
    protected void notifyConferenceParticipantsInvited(boolean isSuccess) {
        for (TelephonyConnectionListener l : mTelephonyListeners) {
            l.onConferenceParticipantsInvited(isSuccess);
        }
    }

    /**
     * For conference SRVCC.
     * @param radioConnections new participant connections
     */
    private void notifyConferenceConnectionsConfigured(
            ArrayList<com.android.internal.telephony.Connection> radioConnections) {
        for (TelephonyConnectionListener l : mTelephonyListeners) {
            l.onConferenceConnectionsConfigured(radioConnections);
        }
    }
    /// @}

    /// M: register/unregister ImsPhone to SuppMessageManager @{
    private void registerSuppMessageManager(Phone phone) {
        if ((phone instanceof ImsPhone) != true) {
            return;
        }
        TelephonyConnectionServiceUtil.getInstance().registerSuppMessageForImsPhone(phone);
    }

    private void unregisterSuppMessageManager(Phone phone) {
        if ((phone instanceof ImsPhone) != true) {
            return;
        }
        TelephonyConnectionServiceUtil.getInstance().unregisterSuppMessageForImsPhone(phone);
    }
    /// @}

    /// M: ALPS02136977. Prints debug logs for telephony. @{
    @Override
    protected FormattedLog.Builder configDumpLogBuilder(FormattedLog.Builder builder) {
        if (builder == null) {
            return null;
        }

        // Dont dump info for muliti-party ims connection, since it is a conference host connection.
        if (mIsMultiParty && isImsConnection()) {
            // But there is an exception case for the conference host connection disconnected in
            // ImsConferenceController.startConference().
            android.telecom.DisconnectCause cause = DisconnectCauseUtil.toTelecomDisconnectCause(
                android.telephony.DisconnectCause.IMS_MERGED_SUCCESSFULLY);
            if (getState() != Connection.STATE_DISCONNECTED || getDisconnectCause() != null ||
                    !getDisconnectCause().equals(cause)) {
                return null;
            }
        }

        super.configDumpLogBuilder(builder);

        /// M: ALPS02288178. mAddress might be null. @{
        if (getAddress() == null) {
            if (mOriginalConnection != null
                    && mOriginalConnection.getAddress() != null) {
                builder.setCallNumber(Rlog.pii(SDBG, mOriginalConnection.getAddress()));
            }
        }
        ///@}

        builder.setServiceName("Telephony");
        if (isImsConnection()) {
            builder.setStatusInfo("type", "ims");
            //builder.setStatusInfo("isWifi", isWifi());
        /// M: CC: Vzw/CTVolte ECC @{
        //} else if (this instanceof com.android.services.telephony.GsmConnection) {
        } else if (this instanceof com.android.services.telephony.GsmCdmaConnection &&
                ((GsmCdmaConnection) this).getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
            builder.setStatusInfo("type", "gsm");
        /// M: CC: Vzw/CTVolte ECC @{
        //} else if (this instanceof CdmaConnection) {
        } else if (this instanceof com.android.services.telephony.GsmCdmaConnection &&
                ((GsmCdmaConnection) this).getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            builder.setStatusInfo("type", "cdma");
        }

        return builder;
    }

    /**
     * Logs unified debug log messages, for "Notify".
     * Format: [category][Module][Notify][Action][call-number][local-call-ID] Msg. String
     *
     * @param action the action name. (e.q. Dial, Hold, MT, Onhold, etc.)
     * @param msg the optional messages
     * @hide
     */
    private void logDebugMsgWithNotifyFormat(String action, String msg) {
        if (action == null) {
            return;
        }

        FormattedLog formattedLog = new FormattedLog.Builder()
                .setCategory("CC")
                .setServiceName("Telephony")
                .setOpType(FormattedLog.OpType.NOTIFY)
                .setActionName(action)
                .setCallNumber(Rlog.pii(SDBG, getAddress().getSchemeSpecificPart()))
                .setCallId(Integer.toString(System.identityHashCode(this)))
                .setExtraMessage(msg)
                .buildDebugMsg();
        if (formattedLog != null) {
            if (!SENLOG || TELDBG) {
                Log.v(this, formattedLog.toString());
            }
        }
    }

    /**
     * Translate from Call.State to notify action string.
     *
     * @param state Call.State
     * @return formatted notify action string.
     * @hide
     */
    static String callStateToFormattedNotifyString(Call.State state) {
        switch (state) {
            case ACTIVE:
                return "Active";
            case HOLDING:
                return "Onhold";
            case DIALING:
            case ALERTING:
                return "Alerting";
            case INCOMING:
            case WAITING:
                return "MT";
            case DISCONNECTED:
                return "Disconnected";

            case IDLE:
            case DISCONNECTING:
            default:
                return null;    // Ignore these states.
        }
    }
    /// @}

    /// M: Add flag to indicate if the CDMA call is fake dialing @{
    /**
     * Used for cdma special handle.
     * @return
     */
    boolean isForceDialing() {
        return false;
    }
    /// @}

    /// M: CC: ECC Retry @{
    protected void setEmergency(boolean isEmergency) {
        mTreatAsEmergencyCall = isEmergency;
        if (isEmergency) {
            log("ECC Retry : setEmergency to true");
        }
    }
    /// @}

    /// M: ALPS02495477 @{
    private boolean hasTwoHoldingCall() throws CallStateException {
        int numCalls = 0;
        Phone phone = getPhone();
        if (phone != null) {
            if (!phone.getRingingCall().isIdle()) {
                numCalls++;
            }
            if (!phone.getForegroundCall().isIdle()) {
                numCalls++;
            }
            if (!phone.getBackgroundCall().isIdle()) {
                numCalls++;
            }
        }
        // We allow user to unhold call for two holding Ims calls.
        if (numCalls == 2 && phone instanceof ImsPhone
                && mOriginalConnection instanceof ImsPhoneConnection
                && phone.getForegroundCall().getState() == Call.State.HOLDING
                && phone.getBackgroundCall().getState() == Call.State.HOLDING) {
            log("hasTwoHoldingCall(): unhold Ims call");
            ((ImsPhoneConnection) mOriginalConnection).unhold();
            return true;
        }
        return false;
    }
    /// @}

    /// M: Try SRVCC pending action. @{
    private void trySrvccPendingAction() {
        Log.d(this, "trySrvccPendingAction(): " + mPendingAction);
        switch(mPendingAction) {
            case SRVCC_PENDING_ANSWER_CALL:
                onAnswer(VideoProfile.STATE_AUDIO_ONLY);
                break;
            case SRVCC_PENDING_HOLD_CALL:
                performHold();
                break;
            case SRVCC_PENDING_UNHOLD_CALL:
                performUnhold();
                break;
            case SRVCC_PENDING_HANGUP_CALL:
                hangup(android.telephony.DisconnectCause.LOCAL);
                break;
            default:
                break;
        }
        mPendingAction = SrvccPendingAction.SRVCC_PENDING_NONE;
    }
    /// @}

    /// M: CC: GSA HD Voice for 2/3G network support @{
    /**
     * This function used to check whether the input value is of HD type.
     * @param value The speech codec type value
     * @return {@code true} if the codec type is of HD type and {@code false} otherwise.
     */
    private boolean isHighDefAudio(int value) {
        String op = null;
        String hdStat = null;
        op = android.os.SystemProperties.get("persist.operator.optr", "OM");
        log("isHighDefAudio, optr:" + op);
        op = op.concat("=");
        hdStat = android.os.SystemProperties.get(PROPERTY_HD_VOICE_STATUS, "");
        if (hdStat != null && !hdStat.equals("")) {
            log("HD voice status: " + hdStat);
            boolean findOp = hdStat.indexOf(op) != -1;
            boolean findOm = hdStat.indexOf("OM=") != -1;
            int start = 0;
            if (findOp && !op.equals("OM=")) {
                start = hdStat.indexOf(op) + op.length(); //OPXX=Y
            } else if (findOm) {
                start = hdStat.indexOf("OM=") + 3; //OM=Y
            }
            // Ex: ril.hd.voice.status="OM=Y;OP07=N;OP12=Y;"
            String isHd = hdStat.length() > (start + 1) ? hdStat.substring(start, start + 1) : "";
            if (isHd.equals("Y")) {
                return true;
            } else {
                return false;
            }
        } else {
            if (value != 0) {
                mSpeechType = SpeechCodecType.fromInt(value);
            }
            boolean isHd = mSpeechType.isHighDefAudio();
            log("isHighDefAudio = " + isHd);
            return isHd;
        }
    }
    /// @}

    private void log(String s) {
        Log.d(TAG, s);
    }

    /// M: ALPS03245602, remove property volte. @{
    private void removePropertyVoLte() {
        int newProperties = getConnectionProperties();
        newProperties = changeBitmask(newProperties, PROPERTY_VOLTE, false);
        Log.d(this, "removePropertyVoLte: %s", propertiesToString(newProperties));
        if (getConnectionProperties() != newProperties) {
            setConnectionProperties(newProperties);
        }
    }
    /// @}
}
