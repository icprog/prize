/*
 * Copyright (c) 2013 The Android Open Source Project
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

package com.android.ims.internal;

import android.app.PendingIntent;

import com.android.ims.ImsCallProfile;
import com.android.ims.internal.IImsCallSession;
import com.android.ims.internal.IImsCallSessionListener;
import com.android.ims.internal.IImsConfig;
import com.android.ims.internal.IImsEcbm;
import com.android.ims.internal.IImsMultiEndpoint;
import com.android.ims.internal.IImsRegistrationListener;
import com.android.ims.internal.IImsUt;

import com.mediatek.gba.NafSessionKey;

import android.os.Message;

/**
 * {@hide}
 */
interface IImsService {
    int open(int phoneId, int serviceClass, in PendingIntent incomingCallIntent,
            in IImsRegistrationListener listener);
    void close(int serviceId);
    boolean isConnected(int serviceId, int serviceType, int callType);
    boolean isOpened(int serviceId);

    /**
     * Replace existing registration listener
     *
     */
    void setRegistrationListener(int serviceId, in IImsRegistrationListener listener);

    /**
     * Add new registration listener
     */
    void addRegistrationListener(int phoneId, int serviceClass,
            in IImsRegistrationListener listener);

    ImsCallProfile createCallProfile(int serviceId, int serviceType, int callType);

    IImsCallSession createCallSession(int serviceId, in ImsCallProfile profile,
            in IImsCallSessionListener listener);
    IImsCallSession getPendingCallSession(int serviceId, String callId);

    /**
     * Ut interface for the supplementary service configuration.
     */
    IImsUt getUtInterface(int serviceId);

    /**
     * Config interface to get/set IMS service/capability parameters.
     */
    IImsConfig getConfigInterface(int phoneId);

    /**
     * Used for turning on IMS when its in OFF state.
     */
    void turnOnIms(int phoneId);

    /**
     * Used for turning off IMS when its in ON state.
     * When IMS is OFF, device will behave as CSFB'ed.
     */
    void turnOffIms(int phoneId);

    /**
     * ECBM interface for Emergency Callback mode mechanism.
     */
    IImsEcbm getEcbmInterface(int serviceId);

   /**
     * Used to set current TTY Mode.
     */
    void setUiTTYMode(int serviceId, int uiTtyMode, in Message onComplete);

    /**
     * MultiEndpoint interface for DEP.
     */
    IImsMultiEndpoint getMultiEndpointInterface(int serviceId);

    /**
     * call interface for allowing/refusing the incoming call indication send to App.
     */
    void setCallIndication(int serviceId, String callId, String callNum, int seqNum,
            boolean isAllow);

    /**
     * Use to query ims enable/disable status.
     */
    int getImsState(int phoneId);

    /**
     * Use to query ims registration information.
     */
    boolean getImsRegInfo(int phoneId);

    /**
     * Use to query ims registration extension information.
     */
    String getImsExtInfo(int phoneId);

    /**
     * Use to hang up all calls.
     */
    void hangupAllCall();

    ///M: WFC: Use to get WFC registration status @ {
    /**
     * Use to get registration status.
     */
    int getRegistrationStatus();
    /// @}

    ///M: Used to deregister IMS @ {
    /**
     * Used to deregister IMS.
     */
    void deregisterIms(int phoneId);
    /// @}

    ///M: Used to notify radio state change @ {
    /**
     * Used to notify radio state change.
     */
    void updateRadioState(int radioState, int phoneId);
    /// @}

    ///M: Used to trigger GBA in native SS solution @ {
    /**
     * Used to trigger GBA in native SS solution.
     */
    NafSessionKey runGbaAuthentication(String nafFqdn,
            in byte[] nafSecureProtocolId, boolean forceRun, int netId, int phoneId);
    /// @}

    ///M: Used to change EVS setting @ {
    /**
     * Used to change EVS setting.
     */
    void setEvsEnabled(int enabled, int phoneId);
    /// @}
}
