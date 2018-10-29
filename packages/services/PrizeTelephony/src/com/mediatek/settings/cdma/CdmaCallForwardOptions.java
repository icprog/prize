/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.settings.cdma;

import android.app.ActionBar;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;

import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneUtils;
import com.android.phone.R;
import com.android.phone.SubscriptionInfoHelper;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;

import java.util.ArrayList;

/**
 * CDMA Call forward Settings Activity.
 */
public class CdmaCallForwardOptions extends PreferenceActivity implements
        PhoneGlobals.SubInfoUpdateListener {
    private static final String LOG_TAG = "Settings/CdmaCallForwardOptions";

    private static final String BUTTON_CFU_KEY   = "button_cfu_key";
    private static final String BUTTON_CFB_KEY   = "button_cfb_key";
    private static final String BUTTON_CFNRY_KEY = "button_cfnry_key";
    private static final String BUTTON_CFNRC_KEY = "button_cfnrc_key";
    private static final String BUTTON_CFC_KEY = "button_cfc_key";

    private static final int DIALOG_CFU = 0;
    private static final int DIALOG_CFB = 1;
    private static final int DIALOG_CFNRY = 2;
    private static final int DIALOG_CFNRC = 3;
    private static final int DIALOG_CFC = 4;

    private static final int GET_CONTACTS = 100;
    private static final String NUM_PROJECTION[] = {Phone.NUMBER};

    private ArrayList<Preference> mPreferences = null;
    private static final String[] CF_HEADERS = {
        SystemProperties.get("ro.cdma.cfu.enable"), SystemProperties.get("ro.cdma.cfu.disable"),
        SystemProperties.get("ro.cdma.cfb.enable"), SystemProperties.get("ro.cdma.cfb.disable"),
        SystemProperties.get("ro.cdma.cfnr.enable"), SystemProperties.get("ro.cdma.cfnr.disable"),
        SystemProperties.get("ro.cdma.cfdf.enable"), SystemProperties.get("ro.cdma.cfdf.disable"),
        SystemProperties.get("ro.cdma.cfall.disable")
    };

    private Preference mButtonCFU;
    private Preference mButtonCFB;
    private Preference mButtonCFNRy;
    private Preference mButtonCFNRc;
    private Preference mButtonCFC;

    private EditText mEditNumber = null;
    private Bundle mBundle;
    private int mSubId = -1;

    /// M: add for hot swap
    private IntentFilter mIntentFilter;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        /**
         * Receive broadcast intents for hot swap.
         *
         * @param context the current context.
         * @param intent the broadcast intent.
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                if (intent.getBooleanExtra("state", false)) {
                    finish();
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mBundle = icicle;
        /*PRIZE-Change-DialerV8-wangzhong-2017_7_19-start*/
        /*addPreferencesFromResource(R.xml.mtk_cdma_callforward_options);*/
        addPreferencesFromResource(R.xml.prize_mtk_cdma_callforward_options);
        getListView().setPadding(0, getResources().getDimensionPixelOffset(R.dimen.prize_preferences_bg_margin2),
                0, getResources().getDimensionPixelOffset(R.dimen.prize_preferences_bg_margin2));
        getListView().setBackgroundColor(getResources().getColor(R.color.prize_preferences_lowest_bg));
        getListView().setDivider(null);
        android.graphics.drawable.ColorDrawable drawable = new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT);
        getListView().setSelector(drawable);
        /*PRIZE-Change-DialerV8-wangzhong-2017_7_19-end*/

        PreferenceScreen prefSet = getPreferenceScreen();
        mButtonCFU   = prefSet.findPreference(BUTTON_CFU_KEY);
        mButtonCFB   = prefSet.findPreference(BUTTON_CFB_KEY);
        mButtonCFNRy = prefSet.findPreference(BUTTON_CFNRY_KEY);
        mButtonCFNRc = prefSet.findPreference(BUTTON_CFNRC_KEY);
        mButtonCFC = prefSet.findPreference(BUTTON_CFC_KEY);

        /// M: for CTA, display different title for CN @{
        if (FeatureOption.isMtkCtaSet()
                && "CN".equals(getResources().getConfiguration().locale.getCountry())) {
            String buttonCFNRcTitle = getResources().getString(R.string.cdma_labelCFNRc_cta);
            if (buttonCFNRcTitle != null) {
                mButtonCFNRc.setTitle(buttonCFNRcTitle);
            }
        }
        /// @}

        mPreferences = new ArrayList<Preference>();
        mPreferences.add(mButtonCFU);
        mPreferences.add(mButtonCFB);
        mPreferences.add(mButtonCFNRy);
        mPreferences.add(mButtonCFNRc);
        mPreferences.add(mButtonCFC);
        SubscriptionInfoHelper subInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        mSubId = subInfoHelper.getPhone() != null ?
                subInfoHelper.getPhone().getSubId() : SubscriptionInfoHelper.NO_SUB_ID;
        Log.d(LOG_TAG, "onCreate: " + mSubId);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        // For hot swap
        PhoneGlobals.getInstance().addSubInfoUpdateListener(this);
        registerCallBacks();
        if (!PhoneUtils.isValidSubId(mSubId)) {
            finish();
            Log.d(LOG_TAG, "onCreate, mSubId is invalid = " + mSubId);
            return;
        }
    }

    /**
     * Destroy the preference activity.
     */
    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);
        PhoneGlobals.getInstance().removeSubInfoUpdateListener(this);
        super.onDestroy();
    }

    /**
     * Register the callbacks for hot swap.
     */
    private void registerCallBacks() {
        mIntentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        registerReceiver(mReceiver, mIntentFilter);
    }

    /**
     * Handler of subInfo changed for hot swap.
     */
    @Override
    public void handleSubInfoUpdate() {
        Log.d(LOG_TAG, "handleSubInfoUpdate");
        finish();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mButtonCFU) {
            showDialog(DIALOG_CFU);
        } else if (preference == mButtonCFB) {
            showDialog(DIALOG_CFB);
        } else if (preference == mButtonCFNRy) {
            showDialog(DIALOG_CFNRY);
        } else if (preference == mButtonCFNRc) {
            showDialog(DIALOG_CFNRC);
        } else if (preference == mButtonCFC) {
            setCallForward(CF_HEADERS[8]);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        switch (itemId) {
        case android.R.id.home:
            finish();
            return true;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        /*PRIZE-Change-DialerV8-wangzhong-2017_7_19-start*/
        /*final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.mtk_cdma_cf_dialog);
        dialog.setTitle(mPreferences.get(id).getTitle());

        final RadioGroup radioGroup = (RadioGroup) dialog.findViewById(R.id.group);

        ImageButton addContactBtn = (ImageButton) dialog.findViewById(R.id.select_contact);
        if (addContactBtn != null) {
            addContactBtn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    startContacts();
                }
            });
        }

        Button dialogSaveBtn = (Button) dialog.findViewById(R.id.save);
        if (dialogSaveBtn != null) {
            dialogSaveBtn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (radioGroup.getCheckedRadioButtonId() == -1) {
                        return;
                    }
                    String cf;
                    if (radioGroup.getCheckedRadioButtonId() == R.id.enable) {
                        *//**
                         * Get the CF type according to id.
                         * the CF_HEADERS list is "enable, disable" format
                         * so we can use the id to locate the SystemProperties
                         * we need to use.
                         *//*
                        int cfType = id * 2;
                        cf = CF_HEADERS[cfType] + mEditNumber.getText();
                    } else {
                        int cfType = id * 2 + 1;
                        cf = CF_HEADERS[cfType];
                    }
                    dialog.dismiss();
                    setCallForward(cf);
                }
            });
        }

        Button dialogCancelBtn = (Button) dialog.findViewById(R.id.cancel);
        if (dialogCancelBtn != null) {
            dialogCancelBtn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }
        return dialog;*/
        View contentView = android.view.LayoutInflater.from(this).inflate(R.layout.prize_mtk_cdma_cf_dialog, null);
        final RadioGroup radioGroup = (RadioGroup) contentView.findViewById(R.id.group);
        ImageButton addContactBtn = (ImageButton) contentView.findViewById(R.id.select_contact);
        if (addContactBtn != null) {
            addContactBtn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    startContacts();
                }
            });
        }
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this)
                .setTitle(mPreferences.get(id).getTitle())
                .setView(contentView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                        new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                if (radioGroup.getCheckedRadioButtonId() == -1) {
                                    return;
                                }
                                String cf;
                                if (radioGroup.getCheckedRadioButtonId() == R.id.enable) {
                                    /**
                                     * Get the CF type according to id.
                                     * the CF_HEADERS list is "enable, disable" format
                                     * so we can use the id to locate the SystemProperties
                                     * we need to use.
                                     */
                                    int cfType = id * 2;
                                    cf = CF_HEADERS[cfType] + mEditNumber.getText();
                                } else {
                                    int cfType = id * 2 + 1;
                                    cf = CF_HEADERS[cfType];
                                }
                                dialog.dismiss();
                                setCallForward(cf);
                            }
                        });
        return builder.create();
        /*PRIZE-Change-DialerV8-wangzhong-2017_7_19-end*/
    }

    @Override
    public void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
        // Do not initialize mEditNumber in onCreateDialog, it is only called
        // when Dialog is created.
        mEditNumber = (EditText) dialog.findViewById(R.id.EditNumber);
    }

    private void setCallForward(String cf) {
        if (mSubId == -1 || cf == null || cf.isEmpty()) {
            Log.d(LOG_TAG, "setCallForward null return");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:" + cf));
        int phoneId = SubscriptionManager.getPhoneId(mSubId);
        PhoneAccountHandle phoneAccountHandle = PhoneUtils.makePstnPhoneAccountHandle(phoneId);
        intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
        startActivity(intent);
    }

    private void startContacts() {
        Intent intent = new Intent("android.intent.action.PICK");
        intent.setType(Phone.CONTENT_TYPE);

        startActivityForResult(intent, GET_CONTACTS);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode,
            final Intent data) {

        if (resultCode != RESULT_OK || requestCode != GET_CONTACTS || data == null) {
            return;
        }

        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(data.getData(),
                    NUM_PROJECTION, null, null, null);
            if ((cursor != null) && (cursor.moveToFirst()) && mEditNumber != null) {
                mEditNumber.setText(cursor.getString(0));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}